"""Project the walkable surfaces of Mondstadt's HLOD onto a half-metre navigation grid."""
import math,struct
from PIL import Image
from import_assets import RES

def bake():
    b=(RES/'city.bin').read_bytes();tex=Image.open(RES/'city.png');p=2
    w,h=169,217
    floor=[32767]*(w*h);roof=[-32768]*(w*h)
    for _ in range(struct.unpack_from('>H',b)[0]):
        _,_,n,t=struct.unpack_from('>hhHH',b,p);p+=8
        vs=[struct.unpack_from('>5hB',b,p+i*11) for i in range(n)];p+=11*n
        for _ in range(t):
            ids=struct.unpack_from('>3H',b,p);p+=6
            a,d,c=[vs[i] for i in ids]
            # Upward-facing triangles in the exported world coordinates.
            det=(d[2]-c[2])*(a[0]-c[0])+(c[0]-d[0])*(a[2]-c[2])
            if det>=-2:continue
            color=tex.getpixel((int((a[3]+d[3]+c[3])/48)%256,int((a[4]+d[4]+c[4])/48)%256))
            isroof=color[0]>color[1]*1.32 and color[0]>color[2]*1.20
            for j in range(max(0,math.ceil(min(a[2],d[2],c[2])/16-516)),min(h-1,math.floor(max(a[2],d[2],c[2])/16-516))+1):
                for i in range(max(0,math.ceil(min(a[0],d[0],c[0])/16-28)),min(w-1,math.floor(max(a[0],d[0],c[0])/16-28))+1):
                    x=(i+28)*16-c[0];z=(j+516)*16-c[2]
                    u=((d[2]-c[2])*x+(c[0]-d[0])*z)/det
                    v=((c[2]-a[2])*x+(a[0]-c[0])*z)/det
                    if u<0 or v<0 or u+v>1:continue
                    y=round(c[1]+u*(a[1]-c[1])+v*(d[1]-c[1]));k=j*w+i
                    if isroof:roof[k]=max(roof[k],y)
                    else:floor[k]=min(floor[k],y)
    with (RES/'streets.bin').open('wb') as out:
        for i,y in enumerate(floor):
            out.write(struct.pack('>h',-32768 if y==32767 else y))
        out.write(bytes(1 if r>-32768 and (y==32767 or r-y>38) else 0 for r,y in zip(roof,floor)))
if __name__=='__main__':bake()
