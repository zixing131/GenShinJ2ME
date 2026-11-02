"""Convert original 1.0 character LOD meshes and diffuse atlases for M3G."""
import argparse, math, re, struct
from pathlib import Path
from PIL import Image
from import_assets import texture, RES

def human(hair_body):
    return {"Body_LOD3":hair_body,"Face_LOD3":[2],"Face_Eye_LOD3":[2],"Brow_LOD3":[2]}

ACTORS={
 "aether":("07533199.0",["08124229.29","08124229.37","08124229.33"],human([0,1])),
 "lumine":("05980420.11",["05980420.4","05096291.11","05980420.7"],human([0,1,1])),
 "amber":("15736225.1",["03541362.101","03541362.107","03541362.104"],human([0,1])),
 "bard":("07702396.2",["15364749.9","14626793.20","00739910.2"],human([0,1,1])),
 "paimon":("12374600.7",["00411486.3","06550698.27","00411486.5","06550698.20"],{"Body":[1,0],"Cloak":[3],"Face":[2]}),
 "hili":("08647133.201",["15041144.8"],{"Body_LOD3":[0]}),
 "dragon":("00033726.7",["15364749.5","15364749.14","00739910.6","01786737.9"],{"Monster_Dvalin_Body_LOD3":[0,1,3],"Monster_Dvalin_Wing_LOD3":[2]}),
}
def export(raw,name,spec,acl=None):
    model,files,parts=spec
    data=(raw/(model+".bin")).read_bytes()
    atlas=Image.new("RGB",(256,256))
    close=Image.new("RGB",(512,512)) if name in ("aether","lumine","amber","paimon") else None
    side=256 if len(files)==1 else 128
    for i,file in enumerate(files):
        b=(raw/(file+".bin")).read_bytes()
        texname=re.search(rb'[a-zA-Z0-9_]+_Diffuse',b).group().decode()
        source=texture(b,texname).convert('RGB')
        im=source.resize((side,side),Image.Resampling.LANCZOS)
        if close is not None:
            close.paste(source.resize((side*2,side*2),Image.Resampling.LANCZOS),(i%2*side*2,i//2*side*2))
        atlas.paste(im,(i%2*side,i//2*side))
    atlas.save(RES/(name+'-tex.png'))
    if close is not None:close.save(RES/(name+'-close.png'),optimize=True)
    points=[];tris=[];lookup={};refs=[];part_info={}
    for part,materials in parts.items():
        key=struct.pack('<I',len(part))+part.encode()
        for match in re.finditer(re.escape(key),data):
            header=(match.end()+3)&~3
            subs,=struct.unpack_from('<I',data,header)
            if 0<subs<=4:break
        assert 0<subs<=4,(name,part,subs)
        counts=[struct.unpack_from('<I',data,header+8+i*48)[0] for i in range(subs)]
        nindex=sum(counts)
        index_at=data.index(struct.pack('<I',nindex*2),header+4+subs*48)+4
        indices=struct.unpack_from('<%dH'%nindex,data,index_at)
        channel=data.index(bytes((0,0,0,3,0,12,0,3)),index_at+2*nindex)
        n,channels=struct.unpack_from('<II',data,channel-8)
        assert max(indices)<n and channels==8
        part_info[part]=(n,channel)
        stream=channel+4*channels+4
        chan=data[channel:channel+32]
        assert chan[12:16]==bytes((1,4,0,2))
        uvbase=stream+((40*n+15)&~15)
        stride=max(c[1]+({0:4,1:2,2:1}[c[2]])*c[3] for c in (chan[k:k+4] for k in range(0,32,4)) if c[0]==1)
        at=0
        for sub,num in enumerate(counts):
            tile=materials[min(sub,len(materials)-1)]
            local={}
            for k in range(at,at+num,3):
                face=[]
                for idx in indices[k:k+3]:
                    if idx not in local:
                        x,y,z,nx,ny,nz=struct.unpack_from('<6f',data,stream+idx*40)
                        u,v=struct.unpack_from('<ff',data,uvbase+idx*stride+4)
                        assert all(math.isfinite(q) for q in (x,y,z,u,v))
                        u=(tile%2+min(.998,max(.002,u)))*side/256
                        v=(tile//2+min(.998,max(.002,v)))*side/256
                        shade=min(255,max(164,round(226+ny*20+nx*10-nz*10)))
                        cluster=1.6 if name=='dragon' else 20 if name=='paimon' else 150
                        uv_grid=22 if name in ('dragon','paimon') else 100
                        identity=(tuple(round(q*cluster) for q in (x,y,z)),round(u*uv_grid),round(v*uv_grid),tile)
                        ref=lookup.get(identity)
                        if ref is None:
                            ref=len(points);lookup[identity]=ref
                            points.append((x,y,z,u,v,shade));refs.append((part,idx))
                        local[idx]=ref
                    face.append(local[idx])
                if len(set(face))==3:tris.append(face)
            at+=num
    with (RES/(name+'.mesh')).open('wb') as out:
        out.write(struct.pack('>HH',len(points),len(tris)))
        for x,y,z,u,v,s in points:
            out.write(struct.pack('>5hB',round(x*1024),round(y*1024),round(z*1024),round(u*4096),round(v*4096),s))
        for t in tris:out.write(struct.pack('>3H',*t))
    if acl and name in ("aether","lumine","amber"):
        from actor_motion import bake
        bake(raw,name,data,part_info,refs,points,acl)
    print(name,len(points),len(tris))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('raw',type=Path);p.add_argument("--acl",type=Path);args=p.parse_args()
    for name,spec in ACTORS.items():export(args.raw,name,spec,args.acl)
