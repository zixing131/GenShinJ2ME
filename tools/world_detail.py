"""Bake near-field M3G assets from PC 1.0 static meshes. Manifest poses are registered
against the matching world-space HLOD vertices; translations are in quarter-scale metres."""
import argparse, collections, json, math, struct
from pathlib import Path
import numpy as np
from scipy.spatial import cKDTree
from scipy.sparse import coo_matrix
from scipy.sparse.csgraph import connected_components
from PIL import Image
from import_assets import RES, texture

def mesh(raw, file, name):
    data=(raw/file).read_bytes()
    needle=struct.pack('<I',len(name))+name.encode()
    start=0
    while True:
        start=data.index(needle,start)+len(needle)
        h=(start+3)&~3
        ns,=struct.unpack_from('<I',data,h)
        if not 1<=ns<32:continue
        counts=[struct.unpack_from('<I',data,h+8+i*48)[0] for i in range(ns)]
        total=sum(counts)
        if not 0<total<1500000:continue
        ib=data.find(struct.pack('<I',total*2),h+4+48*ns,h+192+48*ns)
        if ib>=0:break
    ix=np.frombuffer(data,'<u2',total,ib+4).reshape(-1,3)
    vtx=(ib+4+total*2+3)&~3
    nv,nc=struct.unpack_from('<II',data,vtx+8)
    stride=struct.unpack_from('<I',data,vtx+16+4*nc)[0]//nv
    assert nc==8 and ix.max()<nv
    base=vtx+20+4*nc
    def channel(idx):
        stream,offset,fmt,dims=struct.unpack_from('4B',data,vtx+16+idx*4)
        assert stream==0 and fmt in (0,1)
        dt='<f4' if fmt==0 else '<f2'
        return np.ndarray((nv,dims),dt,data,base+offset,strides=(stride,np.dtype(dt).itemsize)).astype(float)
    return channel(0),channel(1),channel(3),ix,counts

def pack_utf(out,text):
    s=text.encode('ascii');out.write(struct.pack('>H',len(s))+s)

def bake(raw):
    meta=json.loads(Path(__file__).with_name('detail_sources.json').read_text())
    folder=RES/'detail';folder.mkdir(exist_ok=True)
    models=sorted(meta['models']);textures={}; geometries={}
    for i,name in enumerate(models):
        file,mats=meta['models'][name]
        pos,norm,uv,ix,counts=mesh(raw,file,name)
        if len(mats)==len(counts)-1 and name=="Area_MdBuild_House24_Lod1":mats=mats+[mats[-1]]
        assert len(mats)==len(counts)
        if 'BigStones_5_-4' in name:
            # Distant cliff shells have no traversable underside in the reduced height field.
            # Keep complete outcrops clear of the tutorial strand; individual source boulders
            # anchor the near field there without exposing the backs of the HLOD surfaces.
            unique, inverse=np.unique(np.round(pos,1),axis=0,return_inverse=True)
            ids=inverse[ix]
            graph=coo_matrix((np.ones(ids.size),(ids.ravel(),np.roll(ids,1,axis=1).ravel())),shape=(len(unique),len(unique)))
            _,component=connected_components(graph,directed=False)
            wx=unique[:,0]+2560;wz=unique[:,2]-2048
            on_path=np.minimum((wx-2747)**2+(wz+1719)**2,(wx-2727)**2+(wz+1659)**2)<60**2
            ix=ix[~np.isin(component[ids[:,0]],component[on_path])]
            counts=[ix.size]
        plant=name.startswith('Stages_MdGrass')
        scale=1 if plant else .25
        shade=np.clip(222+norm[:,1]*25+norm[:,0]*13-norm[:,2]*12,150,255)
        geometries[name]=pos*scale,ix
        with (folder/('m%d.bin'%i)).open('wb') as dest:
            dest.write(struct.pack('>H',len(counts)))
            at=0
            for count,(tf,tn,sx,sy,ox,oy) in zip(counts,mats):
                key=(tf,plant or 'lvy' in tn.lower())
                if key not in textures:
                    tid=len(textures);textures[key]=tid
                    im=texture((raw/tf).read_bytes(),tn)
                    if not key[1]:
                        hi=im.convert('RGB').resize((512,512),Image.Resampling.LANCZOS)
                        hi.quantize(colors=256).save(folder/('h%d.png'%tid),optimize=True)
                    im=im.resize((256,256),Image.Resampling.LANCZOS)
                    if key[1]:
                        im.putalpha(im.getchannel('A').point(lambda v:255 if v>96 else 0))
                    else:im=im.convert('RGB')
                    im.save(folder/('t%d.png'%tid),optimize=True)
                faces=ix[at:at+count//3];at+=count//3
                ids,indices=np.unique(faces,return_inverse=True)
                st=uv[ids,:2]*[sx,sy]+[ox,oy]
                st-=np.floor(st.mean(0))
                assert np.abs(st).max()<127
                dest.write(struct.pack('>BBHH',textures[key],key[1],len(ids),len(faces)))
                for pt,coord,l in zip(pos[ids]*scale,st,shade[ids]):
                    dest.write(struct.pack('>5hB',*(round(v*128) for v in pt),*(round(v*256) for v in coord),round(l)))
                dest.write(indices.astype('>u2').tobytes())
    # Assign the baked city triangles to their corresponding detailed building.
    inst=meta['instances'];clouds=[]
    for name,(x,y,z,yaw) in inst:
        v,t=geometries[name];a=math.radians(yaw);c,s=math.cos(a),math.sin(a)
        p=v@np.array([[c,0,-s],[0,1,0],[s,0,c]])+[x,y,z];tri=p[t]
        clouds.append(cKDTree(np.vstack((p,tri.mean(1),((tri+np.roll(tri,1,axis=1))/2).reshape(-1,3)))))
    blob=(RES/'city.bin').read_bytes();p=2;groups=[]
    for _ in range(struct.unpack_from('>H',blob)[0]):
        x,z,n,nt=struct.unpack_from('>hhHH',blob,p);p+=8
        buf=blob[p:p+11*n];p+=11*n
        v=np.array([struct.unpack_from('>3h',buf,k*11) for k in range(n)])/32
        tri=np.frombuffer(blob,'>u2',nt*3,p).reshape(-1,3);p+=6*nt
        mid=v[tri].mean(1);dist,owner=np.full(nt,.43),np.full(nt,-1)
        for j,cloud in enumerate(clouds):
            d=cloud.query(mid)[0];mask=d<dist;dist[mask]=d[mask];owner[mask]=j
        # Apply the original tiled paving material only to near-horizontal street surfaces.
        normal=np.cross(v[tri[:,1]]-v[tri[:,0]],v[tri[:,2]]-v[tri[:,0]])
        street=np.frombuffer((RES/'streets.bin').read_bytes(),'>i2',169*217).reshape(217,169)
        gx=np.clip(np.rint((mid[:,0]-14)*2).astype(int),0,168);gz=np.clip(np.rint((mid[:,2]-258)*2).astype(int),0,216)
        pave=(owner<0)&(abs(street[gz,gx]/32-mid[:,1])<.90)&(np.abs(normal[:,1])>.91*np.linalg.norm(normal,axis=1))
        # Preserve grass and decorative surfaces that are identifiable in the original atlas.
        atlas=Image.open(RES/'city.png')
        uv=np.array([struct.unpack_from('>2h',buf,k*11+6) for k in range(n)])/4096
        col=np.array([atlas.getpixel(tuple(map(int,pt)))[:3] for pt in (uv[tri].mean(1)*255)%256])
        neutral=(col[:,0].astype(int)-col[:,1]<42)&(col[:,1].astype(int)-col[:,0]<12)
        owner[pave & neutral]=-2
        owner[pave & neutral & (col[:,0]-col[:,2]>13)]=-3
        for j in np.unique(owner):
            faces=tri[owner==j];ids,indices=np.unique(faces,return_inverse=True)
            chunk=bytearray()
            for k in ids:
                rec=bytearray(buf[int(k)*11:int(k)*11+11])
                if j in (-2,-3):struct.pack_into('>2h',rec,6,round((v[k,0]-x)*64),round((v[k,2]-z)*64))
                chunk.extend(rec)
            groups.append(struct.pack('>hhhHH',x,z,j,len(ids),len(faces))+chunk+indices.astype('>u2').tobytes())
    with (folder/'shell.bin').open('wb') as dest:dest.write(struct.pack('>H',len(groups))+b''.join(groups))
    texture((raw/'08881051.3.bin').read_bytes(),'Area_Mdcity_Ground15_Diffuse').convert('RGB').resize((256,256),Image.Resampling.LANCZOS).save(folder/'paving.png')
    texture((raw/'00991764.20.bin').read_bytes(),'Area_Mdcity_Ground13_Diffuse').convert('RGB').resize((256,256),Image.Resampling.LANCZOS).save(folder/'square.png')
    trees=[]
    for file,name in meta['trees']:
        v,_,uv,faces,_=mesh(raw,file,name)
        # The 1.0 tree draw batch stores a four-vertex billboard per source instance.
        for start in range(0,len(faces)-1,2):
            ids=np.unique(faces[start:start+2])
            if len(ids)!=4:continue
            box=v[ids];lo=box.min(0);hi=box.max(0);center=(lo+hi)/2
            if hi[1]-lo[1]<1:continue
            u,vv=np.minimum(3,(uv[ids].mean(0)*4).astype(int))
            trees.append(((center[0]-2048)/4,(lo[1]-190)/4,(center[2]+2048)/4,(hi[1]-lo[1])/4,int(u+4*vv)))
    with (folder/'map.bin').open('wb') as dest:
        dest.write(struct.pack('>HHH',len(textures),len(models),len(inst)))
        for name in models:pack_utf(dest,name)
        for name,pose in inst:dest.write(struct.pack('>B4f',models.index(name),*pose))
        dest.write(struct.pack('>H',len(trees)))
        for x,y,z,size,tile in trees:dest.write(struct.pack('>4hB',round(x*32),round(y*32),round(z*32),round(size*128),tile))
    texture((raw/'11861322.33.bin').read_bytes(),'Stages_CyTree_Lod2_Diffuse_NoStream').resize((256,256),Image.Resampling.LANCZOS).save(folder/'trees.png')
    print(len(models),'near mesh assets,',len(inst),'matched buildings,',len(trees),'source trees')

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('raw',type=Path);bake(ap.parse_args().raw)
