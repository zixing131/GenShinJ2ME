"""Skin offline against the PC avatar's hierarchy, inverse bind poses and four-weight vertices.
Use the original action's ACL accessory tracks and bake the PC humanoid muscle curves onto
the matching joints. The phone only uploads a quantized pose when the displayed frame changes.
"""
import ctypes as C
import math
import re
import struct
import zlib
from pathlib import Path
import numpy as np
from scipy.spatial.transform import Rotation
from import_assets import RES
from muscle_curves import BODY,curves

# idle, walk, sprint, melee/bow, jump, glider. Some 1.0 clips only carry humanoid muscles.
FILES={
 'aether':['04410383.98','02272204.21','02272204.16','02272204.152','02272204.58','15892553.70'],
 'lumine':['15132163.16','15132163.37','15132163.35','08194931.42','15132163.73','04410383.14'],
 'amber':['03541362.31','03541362.52','08640463.127','03541362.75','08640463.116','08640463.89'],
}
FRAMES=(6,12,12,8,5,6)

class Result(C.Structure):
    _fields_=[('values',C.POINTER(C.c_float)),('nv',C.c_int),('times',C.POINTER(C.c_float)),('nt',C.c_int)]

def costume_tracks(file,lib):
    data=file.read_bytes()
    # The serialized .asb can have an AssetBundle object before or after the AnimationClip.
    at=data.find(bytes.fromhex('10ac10ac'))
    if at<0:return None, []
    size,=struct.unpack_from('<I',data,at-8)
    assert 48<size<=len(data)-at+8
    buf=C.create_string_buffer(data[at-8:at-8+size]);out=Result()
    lib.DecompressClip(C.byref(buf),C.byref(out))
    assert out.nt>0 and out.nv%out.nt==0
    poses=np.ctypeslib.as_array(out.values,(out.nv,)).reshape(out.nt,-1,10).copy()
    lib.Dispose(C.byref(out))
    # The 1.0 generic bindings for each ACL bone occur as adjacent Q, T, S entries.
    triple=rb'(.{4})\x02\x00{15}\x04\x00{7}\1\x01\x00{15}\x04\x00{7}\1\x03\x00{15}\x04\x00{7}'
    hashes=[struct.unpack('<I',m[1])[0] for m in re.finditer(triple,data,re.S)]
    assert len(hashes)==poses.shape[1],(file,len(hashes),poses.shape)
    return poses,hashes

def skeleton(asset):
    m=re.search(rb'Avatar_\w+_ModelAvatar\x00?',asset);start=m.start()
    p=(start+struct.unpack_from('<I',asset,start-4)[0]+3)&~3
    p+=4;n,=struct.unpack_from('<I',asset,p);p+=4
    parent=np.frombuffer(asset,'<i4',n*2,p).reshape(-1,2)[:,0];p+=n*8+4
    ids=np.frombuffer(asset,'<u4',n,p);p+=4*n
    axes,=struct.unpack_from('<I',asset,p);p+=4+axes*76
    p+=4+n*40
    assert struct.unpack_from('<I',asset,p)[0]==n
    rest=np.frombuffer(asset,'<f4',n*10,p+4).reshape(n,10).copy()
    names={}
    for m in re.finditer(b'Bip001',asset):
        if m.start()<8:continue
        h,length=struct.unpack_from('<II',asset,m.start()-8)
        if length>400:continue
        s=asset[m.start():m.start()+length]
        if zlib.crc32(s)==h:names[h]=s.decode().split('/')[-1]
    return parent,ids,rest,[names.get(h,'') for h in ids]

def skin_part(data,part,n,channel,ids):
    needle=struct.pack('<I',len(part))+part.encode();i=data.rfind(needle,0,channel)
    h=(i+len(needle)+3)&~3;ns,=struct.unpack_from('<I',data,h)
    # Our selected face/body LOD3 mesh has no blendshape data before the bind poses.
    off=h+4+ns*48+16
    nb,=struct.unpack_from('<I',data,off)
    assert 0<nb<256 and struct.unpack_from('<I',data,off+4+64*nb)[0]==nb,(part,nb)
    inv=np.frombuffer(data,'<f4',nb*16,off+4).reshape(-1,4,4)
    hashes=np.frombuffer(data,'<u4',nb,off+8+64*nb)
    weights_at=channel-12-32*n
    assert struct.unpack_from('<I',data,weights_at-4)[0]==n
    w=np.ndarray((n,4),'<f4',data,weights_at,strides=(32,4))
    ix=np.ndarray((n,4),'<i4',data,weights_at+16,strides=(32,4))
    byhash={h:j for j,h in enumerate(ids)}
    return inv,np.array([byhash[h] for h in hashes]),w,ix

def matrices(local,parent):
    m=np.tile(np.eye(4),(len(parent),1,1))
    m[:,:3,:3]=Rotation.from_quat(local[:,3:7]).as_matrix()*local[:,None,7:10]
    m[:,:3,3]=local[:,:3]
    for j,p in enumerate(parent):
        if p>=0:m[j]=m[p]@m[j]
    return m

def rotations(sample,neutral,mode,amber):
    # Humanoid muscle channels begin after motion, root and four 7-channel IK goals.
    # Retarget angular excursions in model space relative to the same rig's idle.
    def delta(k):
        rest=neutral.get(42+k,0)
        return float(np.clip(sample.get(42+k,rest)-rest,-1.6,1.6))
    out={}
    for k,joint in ((0,'Spine'),(3,'Spine1'),(6,'Spine2'),(9,'Neck'),(12,'Head')):
        out['Bip001 '+joint]=(delta(k)*32,delta(k+2)*42,delta(k+1)*24)
    for side,sgn,leg,arm in [('L',1,21,37),('R',-1,29,46)]:
        def joint(name,x=0,y=0,z=0):
            out['Bip001 '+side+' '+name]=(x,y,z)
        joint('Thigh',delta(leg)*66,sgn*delta(leg+2)*34,sgn*delta(leg+1)*42)
        joint('Calf',max(-4,-delta(leg+3)*69))
        joint('Foot',delta(leg+5)*40,sgn*delta(leg+6)*24)
        joint('Clavicle',delta(arm+1)*20,0,sgn*delta(arm)*18)
        joint('UpperArm',-delta(arm+3)*75,sgn*delta(arm+4)*42,sgn*delta(arm+2)*87)
        joint('Forearm',delta(arm+5)*78,sgn*delta(arm+6)*36)
        joint('Hand',delta(arm+7)*27,sgn*delta(arm+8)*18)
    return out

def bake(raw,name,data,part_info,refs,points,library):
    parent,ids,rest,names=skeleton(data);base=matrices(rest,parent)
    groups=[]
    for part,(nv,ch) in part_info.items():
        out,idx=zip(*[(j,i) for j,(p,i) in enumerate(refs) if p==part])
        inv,bones,w,ix=skin_part(data,part,nv,ch,ids)
        groups.append((list(out),inv,bones,w[list(idx)],ix[list(idx)]))
    xyz=np.array([(*p[:3],1) for p in points]);dst=np.empty_like(xyz[:,:3])
    hand=names.index('Bip001 L Hand' if name=='amber' else 'Bip001 R Hand')
    neutral=curves(raw/(BODY[name][0]+".bin"),1)[0]
    lib=C.CDLL(str(library));hmap={h:j for j,h in enumerate(ids)}
    with (RES/(name+'.anim')).open('wb') as f:
        f.write(struct.pack('>HH',len(points),sum(FRAMES)))
        for mode,(count,file) in enumerate(zip(FRAMES,FILES[name])):
            clip,tracks=costume_tracks(raw/(file+'.bin'),lib)
            body=curves(raw/(BODY[name][mode]+".bin"),count)
            assert body and any(42<=k<97 for k in body[0]),(name,mode)
            for fr in range(count):
                phase=fr/count
                local=rest.copy()
                if clip is not None:
                    sample=clip[round(phase*(len(clip)-1))]
                    for v,h in zip(sample,tracks):
                        if h in hmap:local[hmap[h]]=np.r_[v[4:7],v[:4],v[7:10]]
                deform=rotations(body[fr],neutral,mode,name=='amber')
                # Apply joint deltas in the character's rest space, then inherit parent motion.
                for j,n in enumerate(names):
                    if n not in deform:continue
                    r=base[j,:3,:3]
                    delta=Rotation.from_euler('xyz',deform[n],degrees=True).as_matrix()
                    local[j,3:7]=(Rotation.from_quat(local[j,3:7])*Rotation.from_matrix(np.linalg.solve(r,delta@r))).as_quat()
                world=matrices(local,parent)
                for output,inv,bones,w,ix in groups:
                    if np.any(ix<0) or np.any(ix>=len(inv)):raise ValueError('invalid bone index')
                    mats=(world[bones]@inv)[ix]
                    dst[output]=np.einsum('nk,nkij,nj->ni',w,mats[:,:,:3,:],xyz[output])
                assert np.isfinite(dst).all() and np.abs(dst).max()<4,(name,mode,np.abs(dst).max())
                # Anchor planted foot; jump/glide vertical travel is controlled by physics.
                offset=dst[:,1].min();dst[:,1]-=offset
                socket=world[hand,:3,3].copy();socket[1]-=offset
                f.write(np.rint(socket*1024).astype('>i2').tobytes())
                f.write(np.rint(dst*1024).astype('>i2').tobytes())
