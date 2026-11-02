"""Transcode the original 1.0 cinematics into seek-free MIDP streams."""
import argparse
import io
import re
import struct
import subprocess
import tempfile
from pathlib import Path
from PIL import Image

RES=Path(__file__).resolve().parents[1]/'res'
FPS=12
SCENES={'arrival':'MDAQ001_OPNew_Part1','brother':'MDAQ001_OPNew_Part2_PlayerBoy',
        'sister':'MDAQ001_OPNew_Part2_PlayerGirl','mond':'Cs_MDAQ018_MDCityShow'}

def ffmpeg(*args):
    subprocess.run(['ffmpeg','-v','error','-y',*map(str,args)],check=True)

def ms(stamp):
    h,m,s,f=map(int,re.split('[:,.]',stamp))
    return ((h*60+m)*60+s)*1000+f

def main(clips,srt_dir):
    for target,src in SCENES.items():
        keyname=src if target=='mond' else 'MDAQ001_OP'
        key=0
        for c in keyname:key=(key*3+ord(c))&0xffffffffffffff
        ffmpeg('-hca_lowkey',key&0xffffffff,'-hca_highkey',key>>32,'-i',clips/(src+'_0.hca'),
               '-ac',1,'-ar',16000,'-c:a','pcm_u8',RES/(target+'.wav'))
        captions=srt_dir/(src+'_CHS.srt')
        cues=[]
        if captions.is_file():
            for block in re.split(r'\n\s*\n',captions.read_text(encoding='utf-8-sig').strip()):
                lines=block.splitlines()
                begin,end=lines[1].split(' --> ')
                cues.append((ms(begin),ms(end),''.join(lines[2:]).encode()))
        with (RES/(target+'.sub')).open('wb') as dest:
            dest.write(struct.pack('>H',len(cues)))
            for begin,end,enc in cues:dest.write(struct.pack('>IIH',begin,end,len(enc))+enc)
        with tempfile.TemporaryDirectory() as tmp:
            ffmpeg('-threads',3,'-i',clips/(src+'.ivf'),'-an','-vf','fps=12,scale=320:180:flags=lanczos',
                   '-c:v','mjpeg','-pix_fmt','yuvj420p','-q:v',6,Path(tmp)/'%05d.jpg')
            with (RES/(target+'.film')).open('wb') as out:
                for i,file in enumerate(sorted(Path(tmp).glob('*.jpg'))):
                    if i%(FPS*3)==0:
                        # PNG-only devices still see the scene and hear the complete audio timeline.
                        buf=io.BytesIO()
                        with Image.open(file) as im:im.quantize(colors=192).save(buf,'PNG')
                        data=buf.getvalue()
                    else:data=file.read_bytes()
                    out.write(struct.pack('>I',len(data))+data)
                out.write(struct.pack('>I',0))
    ffmpeg('-ss',15,'-i',clips/(SCENES['arrival']+'.ivf'),'-vf','scale=480:270','-frames:v',1,RES/'heavens.png')

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('clips',type=Path,help='Decoded IVF and channel 0 HCA files')
    parser.add_argument('srt_dir',type=Path,help='PC StreamingAssets/VideoAssets')
    args=parser.parse_args()
    main(args.clips,args.srt_dir)
