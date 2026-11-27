#!/usr/bin/env python3
"""Fetch exact build-time public prediction data. Does not run in the Android app."""
import argparse,hashlib,json,urllib.request
from pathlib import Path
def main():
    p=argparse.ArgumentParser();p.add_argument("--manifest",type=Path,default=Path("qa/prediction/sources.json"));p.add_argument("--dest",type=Path,required=True)
    args=p.parse_args();root=args.dest.resolve();root.mkdir(parents=True,exist_ok=True)
    data=json.loads(args.manifest.read_text(encoding="utf-8"))
    for name,meta in data["files"].items():
        assert Path(name).name==name and meta["url"].startswith("https://")
        path=root/name
        def valid(p):return p.is_file() and p.stat().st_size==meta["bytes"] and hashlib.sha256(p.read_bytes()).hexdigest()==meta["sha256"]
        if path.exists():
            if not valid(path):raise RuntimeError("Existing source differs: "+name)
        else:
            tmp=root/(name+".part")
            req=urllib.request.Request(meta["url"],headers={"User-Agent":"AjadiKeys public model data builder"})
            with urllib.request.urlopen(req,timeout=120) as r,tmp.open("wb") as f:
                while True:
                    b=r.read(1024*1024)
                    if not b:break
                    f.write(b)
            if not valid(tmp):raise RuntimeError("Source snapshot changed: "+name+"; locate the original pinned snapshot")
            tmp.replace(path)
        print("verified",name,meta["bytes"])
    (root/"sources.json").write_text(json.dumps(data,indent=2),encoding="utf-8")
if __name__=="__main__":main()
