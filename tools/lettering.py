"""Bake only glyphs used in this MIDlet, so Chinese works with Latin-only phone fonts."""
import re, struct, os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
root=Path(__file__).resolve().parents[1]
latin=ImageFont.truetype('/System/Library/Fonts/Menlo.ttc',11)
font=ImageFont.truetype(os.getenv('CJK_FONT','/System/Library/Fonts/STHeiti Medium.ttc'),13)
chars=set(chr(i) for i in range(32,127))
for file in (root/'src').glob('*.java'):
    for literal in re.findall(r'"([^"\n]*)"',file.read_text()):
        chars.update(literal)
for sub in (root/'res').glob('*.sub'):
    raw=sub.read_bytes();pos=2
    for i in range(struct.unpack_from('>H',raw)[0]):
        n=struct.unpack_from('>H',raw,pos+8)[0];chars.update(raw[pos+10:pos+10+n].decode());pos+=10+n
text=''.join(sorted(chars))
encoded=text.encode()
(root/'res/glyphs.bin').write_bytes(struct.pack('>H',len(encoded))+encoded)
for n,fill in enumerate(('#fff8e6','#f4d79c','#293d45')):
    im=Image.new('RGBA',(32*14,((len(text)+31)//32)*16));d=ImageDraw.Draw(im)
    for i,c in enumerate(text):
        x=i%32*14;y=i//32*16;d.text((x,y+1 if ord(c)<128 else y),c,font=latin if ord(c)<128 else font,fill=fill,stroke_width=0)
    im.save(root/('res/ink%d.png'%n),optimize=True)
title=Image.new('RGBA',(208,77));d=ImageDraw.Draw(title)
f=ImageFont.truetype(os.getenv('TITLE_FONT','/System/Library/Fonts/Supplemental/Songti.ttc'),48)
d.text((104,24),'原  神',font=f,fill='#fff9e7',stroke_width=2,stroke_fill='#425e63',anchor='mm')
d.line((15,60,54,60),fill='#e5cc96');d.line((154,60,193,60),fill='#e5cc96')
d.text((104,60),'W I N D W A R D',font=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',10),anchor='mm',fill='white',stroke_width=1,stroke_fill='#46626a')
title.save(root/'res/title.png')
