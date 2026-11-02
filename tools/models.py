"""Bake faceted, vertex-lit miniatures for the JSR-184 renderer."""
from math import sin, cos, pi, sqrt
from pathlib import Path
from struct import pack

models = {}
def model(name):
    global faces
    faces = models[name] = []

def face(a,b,c,color):
    u=[b[k]-a[k] for k in range(3)]
    v=[c[k]-a[k] for k in range(3)]
    n=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
    length=sqrt(sum(t*t for t in n))
    if length<0.000001:return
    shade=.85+.23*(n[1]+.45*n[0]-.25*n[2])/length
    rgb=[min(255,max(0,int(int(color[k:k+2],16)*shade))) for k in (0,2,4)]
    faces.append(((a,b,c),rgb))

def loft(x,z,rings,color,n=8,phase=0):
    pts=[[(x+rx*cos(2*pi*j/n+phase),y,z+rz*sin(2*pi*j/n+phase)) for j in range(n)] for y,rx,rz in rings]
    for a,b in zip(pts,pts[1:]):
        for j in range(n):
            k=(j+1)%n
            face(a[j],b[j],b[k],color); face(a[j],b[k],a[k],color)
    for j in range(1,n-1):face(pts[-1][0],pts[-1][j+1],pts[-1][j],color)

def box(x,y,z,w,h,d,c):
    loft(x,z,[(y,w*.7071,d*.7071),(y+h,w*.7071,d*.7071)],c,4,pi/4)

def gem(x,y,z,r,h,c,n=7):
    loft(x,z,[(y,0,0),(y+h*.34,r,r*.83),(y+h*.68,r*.9,r*.76),(y+h,0,0)],c,n)

model('stone');gem(0,-.24,0,1.2,1.34,'a2a5a2',6)
model('statue')
for y,r,h,c in [(0,1.5,.35,'c5bd9f'),(.35,1.2,.3,'d8cdb0'),(.65,.82,2.9,'a4b3a8'),(3.55,.95,.3,'ddd4b3')]:loft(0,0,[(y,r,r),(y+h,r*.91,r*.91)],c,8)
loft(0,0,[(3.8,.46,.45),(4.9,.33,.29),(5.35,.48,.3)],'ced7c9')
gem(0,5.22,0,.32,.69,'e2decb')
for s in [-1,1]:
    face((0,4.7,-.1),(s*1.15,6.15,-.26),(s*.82,5.0,-.36),'d8e2d8')
gem(0,5,.48,.28,.63,'75f8e1')
model('rune')
loft(0,0,[(0,.81,.8),(.25,.7,.7),(.35,.50,.50),(1.6,.46,.46),(1.88,.60,.60)],'9ba6a0',6)
gem(0,1.94,0,.38,.96,'66e1d9',6)
model('crystal');gem(0,0,0,.32,1.03,'69e6ce',5)
model('berry')
for i in range(4):gem(.32*cos(i*1.6),.07,.32*sin(i*1.6),.42,.7,'6e9951')
for i in range(3):gem(.30*cos(i*2),.58,.30*sin(i*2),.13,.26,'e79758')
model('slime')
loft(0,0,[(.03,.66,.55),(.24,.89,.77),(.64,.85,.74),(.99,.61,.52),(1.15,.22,.22)],'e98845',12)
for x in [-.31,.31]:gem(x,.51,.68,.085,.26,'253f6d',6)
gem(0,1.02,0,.17,.43,'f9c95d')
model('airslime')
loft(0,0,[(.04,.26,.27),(.25,.62,.59),(.68,.58,.53),(.95,.28,.25)],'79dccc',10)
for x in [-.7,.7]:gem(x,.42,0,.28,.55,'c5f6dc')
for x in [-.22,.22]:gem(x,.38,.49,.065,.20,'397787',6)
model('chest');box(0,0,0,1.16,.62,.8,'a56f3c');box(0,.62,0,1.21,.24,.84,'bd9051')
for x in [-.40,.40]:box(x,.01,0,.085,.87,.87,'efd08a')
model('fountain')
loft(0,0,[(0,1.5,1.5),(.25,1.55,1.55),(.37,1.42,1.42)],'c1c3b7',12)
loft(0,0,[(.38,1.32,1.32),(.40,1.32,1.32)],'76c9cb',12)
loft(0,0,[(.39,.35,.35),(1.0,.18,.18),(1.28,.68,.68),(1.38,.68,.68)],'d4cbb9',9)
gem(0,1.36,0,.10,.82,'97dfe4',8)
model('teardrop');gem(0,0,0,.24,.68,'c34345',6)
model('bow')
for side in [-1,1]:
    face((0,0,0),(.13,.06*side,0),(-.15,.61*side,0),'bf9455')
    face((-.15,.61*side,0),(-.25,.77*side,0),(.02,.14*side,0),'d0ad67')
    face((-.25,.77*side,0),(-.245,0,0),(-.23,0,0),'f1daae')
model('wings')
for side in [-1,1]:
    for k in range(6):
        reach=1.65-k*.19;tip=(side*reach,.26-k*.11,-.48-k*.12)
        face((side*.08,.17,0),tip,(side*(reach-.21),.21-k*.12,-.62-k*.1),'536e8a' if k%2==0 else '7890a3')
        face((side*.08,.17,0),tip,(side*reach*.72,.25-k*.10,-.36-k*.08),'d6bc83')
model('arrow')
box(0,0,0,.035,.035,.92,'ac8659')
face((-.09,.01,.34),(.09,.01,.34),(0,.015,.64),'c9dadb')
face((0,-.08,-.45),(0,.08,-.45),(0,0,-.22),'eee4c9')
model('sword');box(0,0,0,.10,.35,.11,'8a6753');box(0,.33,0,.45,.10,.14,'d5b26a')
loft(0,0,[(.41,.105,.06),(1.42,.09,.045),(1.63,0,0)],'d9e9e8',4)
model('wind')
for j in range(24):
    a=j*2*pi/24;b=a+.205;r=1+.08*sin(3*a)
    face((r*cos(a),.23*sin(2*a),r*sin(a)),(cos(b),.23*sin(2*b),sin(b)),(.89*cos(a),.08+.23*sin(2*a),.89*sin(a)),'9df6e3')

with (Path(__file__).resolve().parents[1]/'res'/'models.bin').open('wb') as out:
    out.write(pack('>H',len(models)))
    for name,tris in models.items():
        out.write(pack('>H',len(name))+name.encode()+pack('>H',len(tris)))
        for pts,col in tris:
            for p in pts:out.write(pack('>3h',*(round(t*256) for t in p)))
            out.write(bytes(col))
