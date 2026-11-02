"""Select the prologue's lines by hash from PC 1.0 data/chs/textmapbin_chs.asb."""
import argparse
import json
import re
from pathlib import Path

GROUPS={
    'BEACH':[('旅行者',1350163315),('派蒙',3715548691),('派蒙',1330713363)],
    'ANCHOR':[('派蒙',1838231531),('派蒙',3949614395)],
    'VISTA':[('派蒙',3895202715),('派蒙',3826372643)],
    'STATUE':[('派蒙',3620648835)],
    'TEAR':[('派蒙',1407393453),('派蒙',4163369355)],
    'KNIGHT':[('安柏',4001772707),('安柏',3781784827),('派蒙',2779458283),('安柏',188840655),('安柏',2185194371),('安柏',2545848859)],
    'REPORT':[('安柏',699098067)],
    'GATE':[('安柏',2431352019),('安柏',1059653995),('安柏',4157181707)],
    'TERRACE':[('安柏',3896386851),('安柏',4086355747),('安柏',492159859)]
}
def main(raw):
    blob=raw.read_bytes().translate(bytes(i^143 for i in range(256)))
    out=['/** Selected PC 1.0 CHS records. Regenerate with tools/dialogue.py. */','final class Prologue {']
    for name,group in GROUPS.items():
        strings=[]
        for who,key in group:
            ident=str(key).encode()
            p=blob.index(bytes([1,3,len(ident)])+ident)+3+len(ident)
            length=0
            for shift in range(0,28,7):
                v=blob[p];p+=1;length|=(v&127)<<shift
                if v<128:break
            text=re.sub(r'</?color(?:=#[a-fA-F0-9]+)?>','',blob[p:p+length].decode())
            strings.append(json.dumps(who+'|'+text,ensure_ascii=False))
        out.append('    static final String[] '+name+'={'+',\n        '.join(strings)+'};')
    (Path(__file__).resolve().parents[1]/'src'/'Prologue.java').write_text('\n'.join(out+['}','']))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('textmap',type=Path);main(p.parse_args().textmap)
