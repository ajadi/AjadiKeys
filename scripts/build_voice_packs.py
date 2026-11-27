#!/usr/bin/env python3
"""Download the two exact Whisper model artifacts and create offline import ZIPs."""
import argparse, hashlib, pathlib, urllib.request, zipfile
from concurrent.futures import ThreadPoolExecutor

REVISION="5359861c739e955e79d9a303bcbc70fb988958b1"
LICENSE_REVISION="86098128c0b4f24f0e2aa2994de830614b474227"
MODELS={
    "base":(59707625,"422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"),
    "small":(190085487,"ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb"),
}
parser=argparse.ArgumentParser()
parser.add_argument("--source",type=pathlib.Path,default=pathlib.Path(".toolchain/voice-models-v1"))
parser.add_argument("--output",type=pathlib.Path,default=pathlib.Path("dist"))
args=parser.parse_args()
args.source.mkdir(parents=True,exist_ok=True);args.output.mkdir(parents=True,exist_ok=True)

def sha(p):
    with p.open("rb") as source:return hashlib.file_digest(source,"sha256").hexdigest()
license_file=args.source/"LICENSE-MIT.txt"
if not license_file.is_file():
    url=f"https://raw.githubusercontent.com/openai/whisper/{LICENSE_REVISION}/LICENSE"
    with urllib.request.urlopen(url,timeout=45) as response:license_file.write_bytes(response.read(65536))

def build(entry):
    name,(size,expected)=entry
    model=args.source/f"ggml-{name}-q5_1.bin"
    if not model.is_file() or model.stat().st_size!=size or sha(model)!=expected:
        temp=model.with_suffix(".part")
        url=f"https://huggingface.co/ggerganov/whisper.cpp/resolve/{REVISION}/{model.name}?download=true"
        count=0;digest=hashlib.sha256()
        with urllib.request.urlopen(url,timeout=120) as response,temp.open("wb") as output:
            while data:=response.read(1024*1024):
                count+=len(data)
                if count>size:raise RuntimeError("Unexpected download length")
                output.write(data);digest.update(data)
        assert count==size and digest.hexdigest()==expected, "Model hash or length mismatch"
        temp.replace(model)
    provenance=(f"Whisper multilingual {name}, Q5_1 quantization\n"
                f"https://huggingface.co/ggerganov/whisper.cpp/tree/{REVISION}\n"
                f"Original model: https://github.com/openai/whisper (MIT)\n"
                f"Original file: {model.name}\nSHA-256: {expected}\nSize: {size}\n")
    out=args.output/f"AjadiKeys-voice-{name}-v1.zip"
    with zipfile.ZipFile(out,"w",compression=zipfile.ZIP_STORED) as archive:
        archive.write(model,"model.bin")
        archive.writestr("LICENSE-MIT.txt",license_file.read_bytes())
        archive.writestr("SOURCE.txt",provenance)
    print(out,out.stat().st_size,sha(out),flush=True)
with ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(build,MODELS.items()))
