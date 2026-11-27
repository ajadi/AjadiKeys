#!/usr/bin/env python3
"""Build bounded non-neural count tables and morphology from pinned public sources.
Raw sentences are never included in the APK; held-out public sentences stay in the local work directory.
Run with Python 3.11+ and pinned pymorphy3 tools listed in prediction_sources.json.
"""
import argparse,bz2,collections,hashlib,heapq,json,math,re,struct,unicodedata
from pathlib import Path

LANGS={"RU":"rus","EN":"eng","HE":"heb"}
LETTERS={"RU":"а-яё","EN":"a-z","HE":"א-ת"}
POS={p:i+1 for i,p in enumerate(("NOUN","PROPN","ADJ","DET","PRON","VERB","AUX","ADV","ADP","NUM","CCONJ","SCONJ","PART","INTJ"))}
CASES={p:1<<(4+i) for i,p in enumerate(("Nom","Gen","Dat","Acc","Ins","Loc"))}
GENDERS={p:1<<(10+i) for i,p in enumerate(("Masc","Fem","Neut"))}
NUMBERS={"Sing":1<<13,"Plur":1<<14,"Dual":1<<14}
PERSONS={str(i+1):1<<(15+i) for i in range(3)}
FORMS={p:1<<(18+i) for i,p in enumerate(("Fin","Inf","Part","Ger"))}
TENSES={p:1<<(22+i) for i,p in enumerate(("Past","Pres","Fut"))}
DEFINITE={"Def":1<<25,"Ind":1<<26}
FEATURES={"Case":CASES,"Gender":GENDERS,"Number":NUMBERS,"Person":PERSONS,"VerbForm":FORMS,"Tense":TENSES,"Definite":DEFINITE}
def normalize(lang,w):
    w=unicodedata.normalize("NFC",w.lower().replace("’","'").replace("׳","'"))
    if lang=="HE":w="".join(c for c in w if unicodedata.category(c)!="Mn")
    return w
def valid(lang,w):
    return len(w)<=40 and bool(re.fullmatch(f"[{LETTERS[lang]}]+(?:['-][{LETTERS[lang]}]+)*",w))
def tokens(lang,s):
    s=normalize(lang,s)
    # Same within-sentence comma behavior as the IME; don't bridge sentence endings.
    return [re.findall(f"(?<!\\w)[{LETTERS[lang]}]+(?:['-][{LETTERS[lang]}]+)*(?!\\w)",part)
            for part in re.split(r"[.!?;:\n\r]",s)]
def input_sentences(lang,source,exclude):
    with bz2.open(source,"rt",encoding="utf-8") as f:
        for line in f:
            p=line.rstrip("\n").split("\t",2)
            if len(p)!=3 or not p[0].isdigit() or p[1]!=LANGS[lang] or len(p[2])>350:continue
            sid=int(p[0])
            for words in tokens(lang,p[2]):
                if not 2<=len(words)<=25 or any(len(w)>40 for w in words):continue
                if " ".join(words) in exclude:continue
                hold=sid%997==13
                yield sid,words,hold
def varint(n):
    assert n>=0
    b=bytearray()
    while n>=128:b.append((n&127)|128);n>>=7
    b.append(n);return b
def group_counts(counts,limit,max_followers):
    # Keep recurring transitions, reserving budget for diverse predictive contexts.
    groups={}
    for k,n in counts.items():
        if n>=2:
            ctx=k>>16;groups.setdefault(ctx,[]).append((n,k&65535))
    totals=collections.Counter()
    for k,n in counts.items():
        ctx=k>>16
        if ctx in groups:totals[ctx]+=n
    candidates=[]
    for ctx,items in groups.items():
        total=totals[ctx]
        top=heapq.nlargest(max_followers,items)
        for rank,(n,w) in enumerate(top):
            priority=n/(total**.28)/((rank+1)**.30)
            candidates.append((priority,n,-ctx,-w))
    selected=heapq.nlargest(limit,candidates)
    out={}
    for _,n,c,w in selected:out.setdefault(-c,[]).append((-w,n))
    for ctx,items in out.items():items.sort(key=lambda x:(-x[1],x[0]))
    return out,totals
def write_table(f,groups,totals,order):
    f.write(varint(len(groups)))
    for ctx,items in sorted(groups.items()):
        if order==3:f.write(varint(ctx>>16));f.write(varint(ctx&65535))
        else:f.write(varint(ctx))
        f.write(varint(totals[ctx]));f.write(varint(len(items)))
        for w,n in items:f.write(varint(w));f.write(varint(n))
def build_ngrams(lang,raw,base,out,exclude,holdout):
    words=collections.Counter();held={};train_n=0;train_words=0
    for sid,seq,test in input_sentences(lang,raw,exclude):
        if test:
            if 3<=len(seq)<=12:held.setdefault(sid,seq)
            continue
        words.update(seq);train_n+=1;train_words+=len(seq)
    for sid,seq in sorted(held.items(),key=lambda x:(x[0]*2654435761)&0xffffffff)[:120]:
        holdout.write(lang+"\t"+" ".join(seq)+"\n")
    # Keep the original dictionary and expand with common natural chat forms.
    originals=set()
    for line in base.read_text(encoding="utf-8").splitlines():
        w=normalize(lang,line.split("\t")[0])
        if valid(lang,w):originals.add(w)
    vocabulary=originals|{w for w,n in words.most_common(23000) if n>=3 and valid(lang,w)}
    vocab=[""]+sorted(vocabulary)
    assert len(vocab)<65536
    ids={w:i for i,w in enumerate(vocab)}
    b=collections.Counter();t=collections.Counter()
    for sid,seq,test in input_sentences(lang,raw,exclude):
        if test:continue
        p2=-1;p1=0
        for w in seq:
            idx=ids.get(w,-1)
            if idx>0:
                if p1>=0:b[(p1<<16)|idx]+=1
                if p2>=0 and p1>0:t[(p2<<32)|(p1<<16)|idx]+=1
            p2,p1=p1,idx
    print(lang,"counted",train_n,"sentences",train_words,"tokens, unique",len(b),len(t),flush=True)
    bi,bt=group_counts(b,65000,32);del b
    tri,tt=group_counts(t,60000,12);del t
    path=out/(lang.lower()+".akn")
    with path.open("wb") as f:
        f.write(b"AKN1");f.write(varint(len(vocab)-1))
        for w in vocab[1:]:
            encoded=w.encode("utf-8");f.write(varint(len(encoded)));f.write(encoded);f.write(varint(words[w]))
        write_table(f,bi,bt,2);write_table(f,tri,tt,3)
    stats={"sentences":train_n,"tokens":train_words,"vocabulary":len(vocab)-1,
           "bigrams":sum(map(len,bi.values())),"trigrams":sum(map(len,tri.values())),
           "heldout_sentences":min(120,len(held)),"bytes":path.stat().st_size}
    print(lang,"built",stats,flush=True)
    return vocabulary,stats
def tag_code(pos,feats):
    value=POS.get(pos,0)
    if value==0:return 0
    for key,mapping in FEATURES.items():
        for item in feats.get(key,"").split(","):value|=mapping.get(item,0)
    return value
def ru_morph(vocab):
    import pymorphy3
    morph=pymorphy3.MorphAnalyzer(lang="ru")
    posmap={"NOUN":"NOUN","ADJF":"ADJ","ADJS":"ADJ","COMP":"ADJ","VERB":"VERB","INFN":"VERB","PRTF":"ADJ","PRTS":"ADJ","GRND":"VERB","NUMR":"NUM","ADVB":"ADV","NPRO":"PRON","PRED":"ADV","PREP":"ADP","CONJ":"CCONJ","PRCL":"PART","INTJ":"INTJ"}
    cm={"nomn":"Nom","gent":"Gen","gen1":"Gen","gen2":"Gen","datv":"Dat","accs":"Acc","acc2":"Acc","ablt":"Ins","loct":"Loc","loc1":"Loc","loc2":"Loc","voct":"Nom"}
    gm={"masc":"Masc","femn":"Fem","neut":"Neut"};nm={"sing":"Sing","plur":"Plur"};pm={"1per":"1","2per":"2","3per":"3"};tm={"past":"Past","pres":"Pres","futr":"Fut"}
    result={}
    for w in sorted(vocab):
        if not morph.word_is_known(w):continue
        tags=[]
        for parse in morph.parse(w):
            tag=parse.tag;pos=posmap.get(tag.POS)
            if not pos:continue
            form={"VERB":"Fin","INFN":"Inf","PRTF":"Part","PRTS":"Part","GRND":"Ger"}.get(tag.POS,"")
            c=tag_code(pos,{"Case":cm.get(tag.case,""),"Gender":gm.get(tag.gender,""),"Number":nm.get(tag.number,""),"Person":pm.get(tag.person,""),"Tense":tm.get(tag.tense,""),"VerbForm":form})
            if c and c not in tags:tags.append(c)
        if tags:result[w]=tags[:12]
    return result
def ud_morph(lang,vocab,paths):
    observed=collections.defaultdict(collections.Counter)
    def add(form,row,definite=False):
        if "Typo=Yes" in row[5] or "CorrectForm=" in row[9]:return
        w=normalize(lang,form)
        if w not in vocab:return
        feats=dict(x.split("=",1) for x in row[5].split("|") if "=" in x)
        if definite:feats["Definite"]="Def"
        c=tag_code(row[3],feats)
        if c:observed[w][c]+=1
    def sentence(rows):
        tokens={int(r[0]):r for r in rows if r[0].isdigit()}
        processed=set()
        for row in rows:
            if "-" not in row[0]:continue
            a,b=map(int,row[0].split("-",1));sub=[tokens[i] for i in range(a,b+1) if i in tokens]
            if not sub:continue
            processed.update(range(a,b+1))
            lexical=[r for r in sub if r[3] in ("NOUN","PROPN","ADJ","VERB","AUX")]
            chosen=lexical[0] if lexical else next((r for r in sub if r[3]=="PRON"),sub[-1])
            definite=lang=="HE" and any(r[3]=="DET" and r[2]=="ה" for r in sub)
            add(row[1],chosen,definite)
        for i,row in tokens.items():
            # Record component morphology too, but never train a word sequence using segmented clitics.
            add(row[1],row)
    for path in paths:
        rows=[]
        with path.open(encoding="utf-8") as f:
            for line in f:
                line=line.rstrip("\n")
                if not line:
                    if rows:sentence(rows);rows=[]
                elif not line.startswith("#"):
                    parts=line.split("\t")
                    if len(parts)==10 and "." not in parts[0]:rows.append(parts)
            if rows:sentence(rows)
    return {w:[c for c,n in counts.most_common(10)] for w,counts in observed.items()}
def write_morph(lang,entries,out):
    path=out/(lang.lower()+".morph.tsv")
    with path.open("w",encoding="utf-8",newline="\n") as f:
        for w,tags in sorted(entries.items()):f.write(w+"\t"+",".join(format(t,"x") for t in tags)+"\n")
    return {"words":len(entries),"bytes":path.stat().st_size}
def main():
    p=argparse.ArgumentParser();p.add_argument("--sources",type=Path,required=True);p.add_argument("--assets",type=Path,required=True);p.add_argument("--holdout",type=Path,required=True);p.add_argument("--work",type=Path,required=True);p.add_argument("--only",choices=LANGS)
    args=p.parse_args();args.work.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((args.sources/"sources.json").read_text(encoding="utf-8"))
    for name,meta in manifest["files"].items():
        data=args.sources/name
        assert data.stat().st_size==meta["bytes"] and hashlib.sha256(data.read_bytes()).hexdigest()==meta["sha256"],name
    exclusion=collections.defaultdict(set)
    for line in args.holdout.read_text(encoding="utf-8").splitlines():
        lang,phrase=line.split("\t",1)
        for seq in tokens(lang,phrase):exclusion[lang].add(" ".join(seq))
    stats={}
    with (args.work/"tatoeba_holdout.tsv").open("w",encoding="utf-8",newline="\n") as test:
        for lang in LANGS:
            if args.only and lang!=args.only:continue
            vocab,entry=build_ngrams(lang,args.sources/(LANGS[lang]+"_sentences.tsv.bz2"),args.assets/(lang.lower()+".tsv"),args.assets,exclusion[lang],test)
            if lang=="RU":forms=ru_morph(vocab)
            elif lang=="EN":forms=ud_morph(lang,vocab,sorted(args.sources.glob("en_ewt-ud-*.conllu")))
            else:forms=ud_morph(lang,vocab,sorted(args.sources.glob("he_iahlt*-ud-*.conllu")))
            entry["morphology"]=write_morph(lang,forms,args.assets);stats[lang]=entry;print(lang,"morphology",entry["morphology"],flush=True)
    (args.work/"stats.json").write_text(json.dumps(stats,indent=2),encoding="utf-8")
if __name__=="__main__":main()
