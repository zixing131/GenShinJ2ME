"""Import two adjacent Mondstadt terrain tiles and UI art from PC 1.0 blkstuff output."""
import argparse
from collections import defaultdict
import struct
import zipfile
from pathlib import Path

from PIL import Image, ImageChops, ImageOps

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "res"
TILES = (("11925519.20", "11925519.24"), ("04929933.22", "04929933.26"))
ICONS = (
    ("01045585.34", "UI_AvatarIcon_PlayerBoy", "aether", 84),
    ("01045585.38", "UI_AvatarIcon_PlayerGirl", "lumine", 84),
    ("01045585.118", "UI_AvatarIcon_Ambor", "amber", 64),
    ("07794345.113", "UI_Img_Paimon1", "paimon", 64),
)

def texture(data, name):
    # A sprite and its Texture2D can share the same name. Only the latter has
    # the platform, dimensions, image length and block-compression fields.
    start = 0
    while True:
        pos = data.index(name.encode(), start)
        start = pos + len(name)
        offset = (start + 3) & ~3
        if offset + 28 > len(data):
            continue
        target, _, w, h, size, fmt, _ = struct.unpack_from("<7i", data, offset)
        if target == 4 and fmt in (10, 12, 25) and 0 < size <= len(data):
            assert struct.unpack_from(">I", data, 4)[0] + size == len(data)
            return Image.frombytes("RGBA", (w, h), data[-size:], "bcn", ({25:7, 12:3, 10:1}[fmt],))

def import_city(read):
    # PC HLOD has one submesh. Its source Transform translates the mesh into
    # world space. Read the transform from the prefab rather than its name.
    source = read("13755452.146")
    name = b"Stages_Mesh_Area_Mengde_City_HLOD"
    base = source.index(name)-4
    first = base+136
    nbytes, = struct.unpack_from("<I",source,first-4)
    indices=struct.unpack_from("<%dH"%(nbytes//2),source,first)
    header=(first+nbytes+3)&~3
    count,channels=struct.unpack_from("<II",source,header+8)
    size_at=header+16+channels*4
    stride=struct.unpack_from("<I",source,size_at)[0]//count
    assert count==16769 and stride==48 and len(indices)==30843
    prefab=read("13755452.145")
    # Child's unique local rotation identity and world offset in this 1.0 prefab.
    translation=struct.pack("<3f",500.1,-1.8,.26)
    assert translation in prefab
    vertices=[]
    for i in range(count):
        start=size_at+4+stride*i
        x,y,z,nx,ny,nz=struct.unpack_from("<6f",source,start)
        u,v=struct.unpack_from("<ee",source,start+28)
        vertices.append(((x+500.1-2048)/4,(y-1.8-190)/4,(z+.26+2048)/4,u,v,
                         int(max(130,min(255,218+ny*24+nx*13-nz*10)))))
    chunks=defaultdict(list)
    for i in range(0,len(indices),3):
        tri=indices[i:i+3]
        x=sum(vertices[j][0] for j in tri)/3
        z=sum(vertices[j][2] for j in tri)/3
        chunks[int(x//16),int(z//16)].append(tri)
    with (RES/"city.bin").open("wb") as out:
        out.write(struct.pack(">H",len(chunks)))
        for (cx,cz),triangles in sorted(chunks.items()):
            ids=sorted({j for tri in triangles for j in tri})
            local={old:new for new,old in enumerate(ids)}
            out.write(struct.pack(">hhHH",cx*16+8,cz*16+8,len(ids),len(triangles)))
            for old in ids:
                x,y,z,u,v,light=vertices[old]
                out.write(struct.pack(">5hB",round(x*32),round(y*32),round(z*32),round(u*4096),round(v*4096),light))
            for tri in triangles:out.write(struct.pack(">3H",*(local[j] for j in tri)))
    tex=texture(read("13755452.148"),"Stages_Texture_Area_Mengde_City_HLOD_Combined_Diffuse")
    tex.convert("RGB").save(RES/"city-detail.jpg",quality=90)
    tex.convert("RGB").resize((256,256),Image.Resampling.LANCZOS).save(RES/"city.png")

def main(raw, apk):
    def read(file):
        return (raw / (file + ".bin")).read_bytes()
    RES.mkdir(exist_ok=True)
    import_city(read)
    atlas = Image.new("RGB", (256, 512))
    grids = []
    for i, (terrain, base) in enumerate(TILES):
        data = read(terrain)
        name = "BigWorldTerrain_2_%d.bin" % (i - 2)
        assert name.encode() in data and struct.unpack_from("<II", data, len(data)-28) == (513,513)
        count = 513**2
        at = data.rindex(struct.pack("<I", count), 0, len(data)-2*count+1)
        grid = struct.unpack_from("<%dH" % count, data, at+4)
        grids.append(grid)
        im = texture(read(base), name+"_BaseMap").convert("RGB")
        atlas.paste(im.resize((256,256), Image.Resampling.LANCZOS), (0, i*256))
        daylight=Image.merge("RGB",tuple(channel.point(lambda v,gain=gain,lift=lift:min(255,round(v*gain+lift)))
                          for channel,gain,lift in zip(im.split(),(1.32,1.49,1.19),(21,26,18))))
        # Bake the source terrain materials into the base map at a smaller repeat interval.
        grain=texture(read("15161656.0"),"Stages_Mdterrain12_Diffuse").convert("L").resize((48,48),Image.Resampling.LANCZOS)
        grain=ImageOps.grayscale(ImageOps.autocontrast(grain)).point(lambda n:round(111+n*.13))
        for row in range(4):
            for col in range(4):
                patch=daylight.crop((col*256,row*256,col*256+256,row*256+256))
                detail=Image.new("L",(256,256))
                for yy in range(-(row*256%48),256,48):
                    for xx in range(-(col*256%48),256,48):detail.paste(grain,(xx,yy))
                patch=ImageChops.add(patch,detail.convert("RGB"),offset=-128)
                patch.quantize(colors=192).save(RES/("land%02d.png"%(i*16+row*4+col)),optimize=True)
    trees=texture(read("11861322.33"),"Stages_CyTree_Lod2_Diffuse_NoStream")
    for name,col,row in [("pine",0,0),("oak",1,3)]:
        im=trees.crop((col*256,row*256,(col+1)*256,(row+1)*256)).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        # RGB at transparent borders is undefined in BC3. Use a hard cutout on the phone.
        im=im.resize((128,128),Image.Resampling.LANCZOS)
        im.putalpha(im.getchannel("A").point(lambda a:255 if a>96 else 0))
        im.save(RES/(name+".png"))
    # Unity TerrainData uses X-major samples. Adjacent Z edges must agree.
    assert max(abs(grids[0][x*513+512]-grids[1][x*513]) for x in range(513)) < 8
    with (RES / "heights.bin").open("wb") as out:
        for z in range(129):
            tile = min(z//64, 1)
            for x in range(65):
                v = grids[tile][x*8*513 + (z-64*tile)*8]
                out.write(struct.pack(">h", round((v*1024/32767-190)*8)))
    # Chart uses the same orientation as the playable world. Tint submerged samples.
    for y in range(512):
        for x in range(256):
            v = grids[y//256][x*2*513 + y%256*2]/32
            r,g,b = atlas.getpixel((x,y))
            atlas.putpixel((x,y),(52,123,143) if v<194.5 else (min(255,r+43),min(255,g+52),min(255,b+34)))
    atlas.resize((112,224),Image.Resampling.LANCZOS).save(RES / "chart.png")
    for file, name, target, side in [("06319028.13","UI_Gacha_AvatarIcon_PlayerBoy","pickboy",148),("06319028.17","UI_Gacha_AvatarIcon_PlayerGirl","pickgirl",148),("00754283.13","UI_CutScene_001a","fishing",320)] + list(ICONS):
        im = texture(read(file),name).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        im.thumbnail((side,side),Image.Resampling.LANCZOS)
        im.save(RES / (target+".png"),optimize=True)
    with zipfile.ZipFile(apk) as archive:
        (RES/"icon.png").write_bytes(archive.read("res/drawable-mdpi-v4/app_icon.png"))

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("raw",type=Path,help="blkstuff batch output subdirectory 00")
    ap.add_argument("apk",type=Path)
    args = ap.parse_args()
    main(args.raw,args.apk)
