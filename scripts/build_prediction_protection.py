#!/usr/bin/env python3
"""Build small safe-side word-form membership filters; false positives only suppress correction."""
import argparse,bz2,collections,hashlib,json,math,struct
from pathlib import Path
from build_prediction_data import normalize,valid,input_sentences,tokens,LANGS
MASK=(1<<64)-1
def hash64(word):
    h=0xcbf29ce484222325
    for b in word.encode("utf-8"):h=((h^b)*0x100000001b3)&MASK
    z=h^0x9e3779b97f4a7c15
    z=((z^(z>>30))*0xbf58476d1ce4e5b9)&MASK
    z=((z^(z>>27))*0x94d049bb133111eb)&MASK
    z=(z^(z>>31))|1
    return h,z
def indexes(word,bits,k):
    a,b=hash64(word)
    for i in range(k):yield ((a+i*b)&MASK)%bits
def read_vocab(path):
    with path.open("rb") as f:
        assert f.read(4)==b"AKN1"
        def readv():
            result=shift=0
            while True:
                b=f.read(1)[0];result|=(b&127)<<shift
                if b<128:return result
                shift+=7
        n=readv();words=[]
        for _ in range(n):
            words.append(f.read(readv()).decode("utf-8"));readv()
        return words
def ru_words(vocab,source,exclude):
    import pymorphy3
    morph=pymorphy3.MorphAnalyzer(lang="ru")
    result=set();lemmas=set()
    for w in vocab:
        if not morph.word_is_known(w):continue
        for parse in morph.parse(w):
            if not getattr(parse,"is_known",True):continue
            key=(parse.normal_form,parse.tag.POS)
            if key in lemmas:continue
            lemmas.add(key)
            for form in parse.lexeme:
                word=normalize("RU",form.word)
                if valid("RU",word):result.add(word)
    print("RU lexemes",len(lemmas),"forms",len(result),flush=True)
    observed=set()
    for _,seq,held in input_sentences("RU",source,exclude):
        if not held:observed.update(seq)
    for w in observed:
        if valid("RU",w) and morph.word_is_known(w):result.add(w)
    return result
def ud_words(lang,paths):
    result=set()
    for p in paths:
        with p.open(encoding="utf-8") as f:
            for line in f:
                if line.startswith("#") or not line.strip():continue
                cols=line.split("\t")
                if len(cols)!=10 or "." in cols[0]:continue
                # Explicitly annotated misspellings are not legitimate protection forms.
                if "Typo=Yes" in cols[5] or "CorrectForm=" in cols[9]:continue
                w=normalize(lang,cols[1])
                if valid(lang,w):result.add(w)
    return result
def write_filter(path,words):
    # 12 bits/key, 7 hashes, target FP about 0.3%; any FP preserves rather than corrupts text.
    bits=max(8,((len(words)*12+7)//8)*8);k=7;buf=bytearray(bits//8)
    for w in words:
        for i in indexes(w,bits,k):buf[i//8]|=1<<(i%8)
    for w in words:
        assert all(buf[i//8]&(1<<(i%8)) for i in indexes(w,bits,k))
    path.write_bytes(struct.pack(">4sII",b"AKP1",bits,k)+buf)
    return {"forms":len(words),"bits":bits,"hashes":k,"bytes":path.stat().st_size,
            "estimated_false_positive":(1-math.exp(-k*len(words)/bits))**k}
def main():
    p=argparse.ArgumentParser();p.add_argument("--sources",type=Path,required=True);p.add_argument("--assets",type=Path,required=True);p.add_argument("--holdout",type=Path,required=True);p.add_argument("--only",choices=LANGS);args=p.parse_args()
    manifest=json.loads((args.sources/"sources.json").read_text())
    for name,m in manifest["files"].items():assert hashlib.sha256((args.sources/name).read_bytes()).hexdigest()==m["sha256"],name
    exclude=set()
    for line in args.holdout.read_text(encoding="utf-8").splitlines():
        lang,phrase=line.split("\t",1)
        if lang=="RU":
            for seq in tokens(lang,phrase):exclude.add(" ".join(seq))
    stats_file=args.sources/"protection-stats.json"
    stats=json.loads(stats_file.read_text()) if args.only and stats_file.exists() else {}
    for lang in LANGS:
        if args.only and lang!=args.only:continue
        vocab=read_vocab(args.assets/(lang.lower()+".akn"))
        if lang=="RU":words=ru_words(vocab,args.sources/"rus_sentences.tsv.bz2",exclude)
        elif lang=="EN":words=ud_words(lang,sorted(args.sources.glob("en_ewt-ud-*.conllu")))
        else:words=ud_words(lang,sorted(args.sources.glob("he_iahlt*-ud-*.conllu")))
        stats[lang]=write_filter(args.assets/(lang.lower()+".akp"),words);print(lang,stats[lang],flush=True)
    stats_file.write_text(json.dumps(stats,indent=2))
if __name__=="__main__":main()
