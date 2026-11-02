"""Sample the streamed cubics, dense samples and constants of a PC 2017.4 humanoid clip."""
import math,re,struct
import numpy as np

BODY={
 'aether':['15892553.106','15767076.12','15892553.117','02363641.7','02363641.55','15892553.25'],
 'lumine':['04410383.59','04410383.77','04410383.60','08194931.42','09133218.13','09133218.78'],
 'amber':['04410383.59','04410383.77','04410383.60','03541362.75','09133218.13','09133218.78'],
}
def curves(file,frames):
    b=file.read_bytes()
    first=b.index(bytes.fromhex('ffff7fff'),4096)
    words=struct.unpack_from('<I',b,first-4)[0]
    end=first+words*4
    stream,nf,nc,hz,start,count=struct.unpack_from('<IIIffI',b,end)
    assert nf*nc==count
    p=end+24;dense=np.frombuffer(b,'<f4',count,p).reshape(nf,nc);p+=4*count
    constants=struct.unpack_from('<I',b,p)[0];const=np.frombuffer(b,'<f4',constants,p+4)
    keys=[[] for i in range(stream)];p=first
    while p<end:
        t,num=struct.unpack_from('<fI',b,p);p+=8
        for _ in range(num):
            i,*coef=struct.unpack_from('<I4f',b,p);p+=20;keys[i].append((t,coef))
    nchan=stream+nc+constants
    # Find the binding table by its exact 2017.4 record stride and valid class IDs.
    for p in range((end+24+4*count+4*constants+3)&~3,len(b)-32,4):
        n=struct.unpack_from('<I',b,p)[0]
        if not 8<n<800 or p+4+28*n>len(b):continue
        fields=[struct.unpack_from('<II12xI',b,p+4+28*i) for i in range(n)]
        if all(ty in (4,95,137,114) for _,_,ty in fields) and any(ty==95 and attr>=42 for _,attr,ty in fields):break
    else:return None
    # In mixed ACL clips, packed transform curves precede or follow the standard humanoid data.
    if bytes.fromhex('10ac10ac') in b[first:end+4*count+300000] and sum(t==95 for _,_,t in fields)<=nchan:
        # Human channels have no transform path; their relative order is preserved.
        human=[entry for entry in fields if entry[2]==95]
        if len(human)==nchan:fields=human
    mapping={};offset=0
    for path,attr,ty in fields:
        if ty==95:mapping[attr]=offset
        offset+=4 if ty==4 and attr==2 else 3 if ty==4 and attr in (1,3,4) else 1
    if offset!=nchan:return None
    result=[]
    for frame in range(frames):
        phase=frame/frames
        d=phase*(nf-1);time=start+d/hz
        sv=[]
        for channel in keys:
            valid=[(t,cs) for t,cs in channel if t<=time];t,cs=(valid[-1] if valid else channel[0])
            dt=0 if abs(t)>1e10 else time-t
            a,b2,c,v=cs;sv.append(((a*dt+b2)*dt+c)*dt+v)
        samples=np.r_[sv,dense[int(d)]*(1-d%1)+dense[min(nf-1,int(d)+1)]*(d%1),const]
        result.append({attr:samples[i] for attr,i in mapping.items()})
    return result
