from pathlib import Path
import concurrent.futures, hashlib, json, urllib.request, time
import argparse
parser=argparse.ArgumentParser(); parser.add_argument('--out',type=Path,default=Path('.toolchain/translation-models-v1')); args=parser.parse_args()
root=args.out;root.mkdir(parents=True,exist_ok=True)
models={
'en-ru':('Xenova/opus-mt-en-ru','e050376e960175e8ddc5cff85025dc8436cddd68','Helsinki-NLP/opus-mt-en-ru'),
'ru-en':('Xenova/opus-mt-ru-en','afe8c6c738ec81b6d033fd8f44f9678a639a7c67','Helsinki-NLP/opus-mt-ru-en'),
'en-he':('fintutto/opus-mt-en-he','bf377d21c2aefc17981607c2ce1047db37381f72','Helsinki-NLP/opus-mt-en-he'),
'he-en':('wlwatkins/opus-mt-tc-big-he-en','d80dfa09db3fcc30220f3fee1e7025e4dcb82506','Helsinki-NLP/opus-mt-tc-big-he-en')}
files={'onnx/encoder_model_quantized.onnx':'encoder.onnx','onnx/decoder_model_merged_quantized.onnx':'decoder.onnx','source.spm':'source.spm','target.spm':'target.spm','vocab.json':'vocab.json','config.json':'config.json','tokenizer_config.json':'tokenizer_config.json','generation_config.json':'generation_config.json','README.md':'upstream-readme.md'}
def fetch_json(url):
    with urllib.request.urlopen(url,timeout=45) as r:return json.load(r)
meta={};jobs=[]
for pair,(repo,rev,base) in models.items():
    m=fetch_json(f'https://huggingface.co/api/models/{repo}/revision/{rev}?blobs=true');assert m['sha']==rev
    b=fetch_json(f'https://huggingface.co/api/models/{base}')
    siblings={x['rfilename']:x for x in m['siblings']}
    meta[pair]={'repo':repo,'revision':rev,'base':base,'base_revision':b['sha'],'license':b.get('cardData',{}).get('license'),'files':{}}
    for src,dst in files.items():
        if src not in siblings:raise RuntimeError(f'{repo} missing {src}')
        s=siblings[src];jobs.append((pair,repo,rev,src,dst,s['size'],s.get('lfs',{}).get('sha256')))
def download(job):
    pair,repo,rev,src,dst,size,expected=job
    dest=root/pair/dst;dest.parent.mkdir(exist_ok=True)
    def digest(path):
        with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
    if dest.exists() and dest.stat().st_size==size:
        h=digest(dest)
        if not expected or expected==h:return pair,dst,{'size':size,'sha256':h,'upstream':src}
    for attempt in range(3):
        try:
            temp=dest.with_suffix(dest.suffix+'.part');h=hashlib.sha256();count=0
            url=f'https://huggingface.co/{repo}/resolve/{rev}/{src}?download=true'
            with urllib.request.urlopen(url,timeout=120) as source,temp.open('wb') as output:
                while chunk:=source.read(1024*1024):
                    count+=len(chunk);h.update(chunk);output.write(chunk)
            checksum=h.hexdigest()
            if count!=size or (expected and checksum!=expected):raise RuntimeError(f'Integrity mismatch {pair}/{dst} {count}/{size}')
            temp.replace(dest)
            print(f'{pair}/{dst} {count} bytes verified',flush=True)
            return pair,dst,{'size':count,'sha256':checksum,'upstream':src}
        except Exception as e:
            print(f'{pair}/{dst} retry {attempt+1}: {str(e)[:150]}',flush=True)
            if attempt==2:raise
            time.sleep(2)
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    for pair,dst,info in pool.map(download,jobs):meta[pair]['files'][dst]=info
(root/'manifest.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
print('All model assets downloaded and verified',flush=True)
