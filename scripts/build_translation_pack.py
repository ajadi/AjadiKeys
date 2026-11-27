#!/usr/bin/env python3
"""Compile pinned SentencePiece vocabularies and package local model files. Requires sentencepiece 0.2.1."""
import argparse, hashlib, json, struct, urllib.request, zipfile
from pathlib import Path
import sentencepiece as spm

parser=argparse.ArgumentParser()
parser.add_argument('--source',type=Path,default=Path('.toolchain/translation-models-v1'))
parser.add_argument('--output',type=Path,default=Path('dist/AjadiKeys-translation-models-v1.zip'))
parser.add_argument('--manifest',type=Path,default=Path('app/src/main/assets/translation/model_manifest.json'))
args=parser.parse_args()
root=args.source; data=json.loads((root/'manifest.json').read_text(encoding='utf-8'))
model_order=['en-ru','ru-en','en-he','he-en']; entries=[]; paths={}; vectors=[]

def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def record(name,path):
    entries.append({'name':name,'size':path.stat().st_size,'sha256':sha(path)});paths[name]=path

def write_string(f,s):
    b=s.encode('utf-8');assert len(b)<=4096;f.write(struct.pack('>I',len(b)));f.write(b)

def pack_tokenizer(folder):
    c=json.loads((folder/'config.json').read_text(encoding='utf-8'));v=json.loads((folder/'vocab.json').read_text(encoding='utf-8'))
    source=spm.SentencePieceProcessor(model_file=str(folder/'source.spm'))
    out=folder/'tokenizer.bin';count=c['decoder_vocab_size'];reverse=['']*count
    for token,i in v.items():reverse[i]=token
    assert all(reverse) and len(v)==count
    unknown=v['<unk>']; pieces=[]
    for i in range(source.vocab_size()):
        if source.is_unknown(i) or source.is_control(i) or source.is_unused(i) or source.is_byte(i):continue
        text=source.id_to_piece(i);pieces.append((text,source.get_score(i),v.get(text,unknown)))
    # All fields BE, exact HF model IDs, not the SentencePiece internal indices.
    with out.open('wb') as f:
        f.write(struct.pack('>9I',0x414a5431,c['eos_token_id'],c['pad_token_id'],unknown,c['decoder_start_token_id'],c['decoder_attention_heads'],c['d_model']//c['decoder_attention_heads'],c['decoder_layers'],count))
        for token in reverse:write_string(f,token)
        f.write(struct.pack('>I',len(pieces)))
        for text,score,index in pieces:write_string(f,text);f.write(struct.pack('>fI',score,index))
    return out

for pair in model_order:
    folder=root/pair
    for name,info in data[pair]['files'].items():
        path=folder/name
        if not path.exists() or path.stat().st_size!=info['size'] or sha(path)!=info['sha256']:
            raise RuntimeError(f'Pinned asset verification failed: {pair}/{name}')
    record(pair+'/encoder.onnx',folder/'encoder.onnx');record(pair+'/decoder.onnx',folder/'decoder.onnx')
    record(pair+'/tokenizer.bin',pack_tokenizer(folder))

license_file=root/'MODEL-LICENSES.txt'
lines=['Ajadi Keys offline translation model pack v1','Quantized ONNX conversions of OPUS-MT / Helsinki NLP Marian neural translation models.','The original weights and conversion authors retain their respective licenses.','Tokenizers are repacked from their original SentencePiece and Hugging Face vocabularies for local Java inference.','OPUS-MT: https://github.com/Helsinki-NLP/Opus-MT','Tiedemann & Thottingal (2020), OPUS-MT: Building open translation services for the world.','']
for pair in model_order:
    p=data[pair];lines += [pair+': '+p['license'], 'Base: https://huggingface.co/'+p['base']+'/tree/'+p['base_revision'], 'ONNX conversion: https://huggingface.co/'+p['repo']+'/tree/'+p['revision'],'']
lines += ['Apache-2.0: https://www.apache.org/licenses/LICENSE-2.0 (copy included)','CC-BY-4.0: https://creativecommons.org/licenses/by/4.0/','No new training or user data used.']
license_file.write_text('\n'.join(lines)+'\n',encoding='utf-8');record('MODEL-LICENSES.txt',license_file)
apache=root/'APACHE-2.0.txt'
if not apache.exists():
    with urllib.request.urlopen('https://www.apache.org/licenses/LICENSE-2.0.txt',timeout=30) as r:apache.write_bytes(r.read())
record('APACHE-2.0.txt',apache)
manifest={'version':1,'pairs':model_order,'entries':entries,'source':{pair:{k:v for k,v in data[pair].items() if k!='files'} for pair in model_order}}
args.manifest.parent.mkdir(parents=True,exist_ok=True);args.manifest.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
args.output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(args.output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True) as z:
    for name,path in paths.items():z.write(path,name)
print('Pack bytes',args.output.stat().st_size,'SHA-256',sha(args.output),'installed bytes',sum(x['size'] for x in entries))
