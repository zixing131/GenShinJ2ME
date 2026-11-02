"""Bake changing sky perspective, wet sand and a repeating water highlight for the beach."""
import argparse
from pathlib import Path
import struct
import numpy as np
from PIL import Image
from import_assets import texture, RES

def bake(raw):
    rng=np.random.default_rng(174)
    w,h=1536,180
    gradient=np.linspace([63,147,205],[189,223,222],h)
    sky=Image.fromarray(np.repeat(gradient[:,None,:],w,axis=1).astype('uint8')).convert('RGBA')
    for layer,(file,name) in enumerate([('05156224.43','Enviro_Clouds_Middle_Particle_Atlas'),('06860169.3','Enviro_Clouds_Particle_Atlas')]):
        b=(raw/(file+'.bin')).read_bytes()
        pos=(b.index(name.encode())+len(name)+3)&~3
        _,_,sw,sh,n,fmt=struct.unpack_from('<6I',b,pos)
        assert fmt==4 and n==sw*sh*4
        atlas=Image.frombytes('RGBA',(sw,sh),b[-n:]).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        for j in range(15 if layer==0 else 8):
            i=int(rng.integers(8));x=(i%2)*512;y=(i//2)*256
            values=np.asarray(atlas.crop((x,y,x+512,y+256))).astype(float)/255
            bright=np.clip(.22+.66*values[:,:,0]+.9*values[:,:,1]-.15*values[:,:,2],0,1)
            rgb=np.array([149,181,208])+(np.array([255,249,231])-[149,181,208])*bright[:,:,None]
            rgba=np.dstack((rgb,values[:,:,3]*(192+layer*45))).astype('uint8')
            width=int(rng.integers(105,198));im=Image.fromarray(rgba).resize((width,width//2),Image.Resampling.LANCZOS)
            left=int(rng.integers(w));top=int(rng.integers(-30,64))
            for offset in [left,left-w]:sky.alpha_composite(im,(offset,top))
    sky.convert('RGB').quantize(colors=256).save(RES/'detail/sky.png',optimize=True)
    # Tileable elongated ripples; vertex color supplies the shallow-to-deep gradient.
    v,u=np.mgrid[:128,:128]*2*np.pi/128
    s=np.sin(v*4+.7*np.sin(u*2+v))+.42*np.sin(7*v+2*u+.6*np.cos(u-v))
    tint=228+22*np.clip((s-.82)*2.1,0,1)
    Image.fromarray(np.stack((tint,tint,tint),axis=2).astype('uint8')).save(RES/'detail/water.png')
    # Preserve the 1.0 base map's sand/grass boundaries, and retain individual pebbles in the shore material.
    mat=texture((raw/'15161656.2.bin').read_bytes(),'Stages_Mdterrain_SandDamp01_Diffuse').convert('RGB').resize((56,56),Image.Resampling.LANCZOS)
    base=texture((raw/'11925519.24.bin').read_bytes(),'BigWorldTerrain_2_-2.bin_BaseMap').convert('RGB')
    for row in range(2):
        for col in range(2,4):
            path=RES/('land%02d.png'%(row*4+col))
            # Recompute from source so successive imports are deterministic.
            original=np.asarray(base.crop((256*col,256*row,256*col+256,256*row+256))).astype(float)
            daylight=original*np.array([1.32,1.49,1.19])+[21,26,18]
            yy,xx=np.mgrid[:256,:256];fine=np.asarray(mat).astype(float)[(yy+256*row)%56,(xx+256*col)%56]
            sand=np.clip((original[:,:,0]-original[:,:,1]+5)/13,0,1)[:,:,None]
            shaded=daylight*(1-sand)+(daylight*[1.09,.94,.95]+(fine-fine.mean((0,1)))*1.1+[12,3,12])*sand
            Image.fromarray(np.clip(shaded,0,255).astype('uint8')).save(path,optimize=True)
if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('raw',type=Path);bake(ap.parse_args().raw)
