import java.io.*;
import java.util.Random;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;

/** Stream reusable source meshes; retain a far-city owner for every replaced draw group. */
final class DetailScene {
    private final Scenery scene;
    private final Land land;
    private final PolygonMode both;
    private final Group[] assets;
    private final Appearance[] materials;
    private final String[] names;
    private final int[] type;
    private final float[][] placement;
    private final boolean[] near;
    private final short[][] trees;
    private final Mesh[] crowns=new Mesh[16];
    private final float[][] patches=new float[1100][4];
    private int patchCount,hiCount;
    private final boolean highTextures;
    private final int grass,bush,fountain,lantern,cliff,rocks,boulder;
    private final Transform tr=new Transform();
    DetailScene(Scenery scene,Land land,PolygonMode both)throws IOException{
        this.scene=scene;this.land=land;this.both=both;
        Integer limit=(Integer)Graphics3D.getProperties().get("maxTextureDimension");
        highTextures=limit!=null && limit.intValue()>=512;
        DataInputStream in=open("map.bin");
        try{
            materials=new Appearance[in.readUnsignedShort()];names=new String[in.readUnsignedShort()];assets=new Group[names.length];
            int count=in.readUnsignedShort();type=new int[count];near=new boolean[count];placement=new float[count][4];
            for(int i=0;i<names.length;i++)names[i]=in.readUTF();
            for(int i=0;i<count;i++){type[i]=in.readUnsignedByte();for(int j=0;j<4;j++)placement[i][j]=in.readFloat();}
            trees=new short[in.readUnsignedShort()][5];
            for(int i=0;i<trees.length;i++){for(int j=0;j<4;j++)trees[i][j]=in.readShort();trees[i][4]=(short)in.readUnsignedByte();}
        }finally{in.close();}
        fountain=find("Fountain03");lantern=find("Lamp13");grass=find("Grass_28");bush=find("Grass_31");
        boulder=find("MDSRock06");cliff=find("5_-4_HLOD");rocks=find("5_-3_HLOD");
        Appearance tree=material("trees.png",true);
        for(int i=0;i<16;i++){
            int u=i%4,v=i/4;VertexBuffer vb=new VertexBuffer();
            vb.setPositions(Scenery.shorts(new short[]{-1,0,0,-1,2,0,1,0,0,1,2,0},3),.5f,null);
            short[] uv={(short)(u+1),(short)v,(short)(u+1),(short)(v+1),(short)u,(short)v,(short)u,(short)(v+1)};
            vb.setTexCoords(0,Scenery.shorts(uv,2),.25f,null);
            crowns[i]=new Mesh(vb,new TriangleStripArray(0,new int[]{4}),tree);
        }
        Random r=new Random(1709);
        // Separate low beach wrack, grass on the upper bank and meadow clumps. Keep the strand open.
        for(int i=0;i<3600 && patchCount<patches.length;i++){
            float x=135+unit(r)*74,z=66+unit(r)*81,y=land.ground(x,z);
            if(y<1.07f || y>11 || land.ground(x+1,z)-y>1.1f)continue;
            float trail=175-(z-82)*.19f;
            if(Math.abs(x-trail)<1.65f && z<110)continue;
            float[] a=patches[patchCount++];a[0]=x;a[1]=z;a[2]=(.26f+unit(r)*.58f)*(y<1.85f?.60f:1);a[3]=unit(r)*360;
        }
    }
    private static float unit(Random r){return (r.nextInt()&65535)/65535f;}
    private int find(String part){for(int i=0;i<names.length;i++)if(names[i].indexOf(part)>=0)return i;throw new IllegalArgumentException(part);}
    private DataInputStream open(String file){return new DataInputStream(getClass().getResourceAsStream("/detail/"+file));}
    Appearance material(String file,boolean cut)throws IOException{
        Texture2D tex=new Texture2D(new Image2D(cut?Image2D.RGBA:Image2D.RGB,Image.createImage("/detail/"+file)));
        tex.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_LINEAR);
        Appearance a=new Appearance();a.setTexture(0,tex);a.setFog(scene.fog);a.setPolygonMode(both);
        if(cut){CompositingMode c=new CompositingMode();c.setAlphaThreshold(.45f);a.setCompositingMode(c);}
        return a;
    }
    private Group load(int id)throws IOException{
        if(assets[id]!=null)return assets[id];
        DataInputStream in=open("m"+id+".bin");Group group=new Group();
        try{
            int parts=in.readUnsignedShort();
            for(int i=0;i<parts;i++){
                int tid=in.readUnsignedByte();boolean cut=in.readUnsignedByte()!=0;
                if(materials[tid]==null){
                    boolean hi=!cut && highTextures && hiCount<16 && Runtime.getRuntime().freeMemory()>9*1024*1024;
                    materials[tid]=material((hi?"h":"t")+tid+".png",cut);
                    if(hi)hiCount++;
                }
                int n=in.readUnsignedShort(),tris=in.readUnsignedShort();
                short[] pos=new short[n*3],uv=new short[n*2];byte[] rgb=new byte[n*3];
                for(int j=0;j<n;j++){
                    for(int k=0;k<3;k++)pos[j*3+k]=in.readShort();
                    uv[j*2]=in.readShort();uv[j*2+1]=in.readShort();byte l=in.readByte();
                    rgb[j*3]=rgb[j*3+1]=rgb[j*3+2]=l;
                }
                int[] indices=new int[tris*3],strips=new int[tris];
                for(int j=0;j<indices.length;j++)indices[j]=in.readUnsignedShort();
                for(int j=0;j<tris;j++)strips[j]=3;
                VertexBuffer vb=new VertexBuffer();vb.setPositions(Scenery.shorts(pos,3),1f/128,null);
                vb.setTexCoords(0,Scenery.shorts(uv,2),1f/256,null);vb.setColors(Scenery.colors(rgb));
                group.addChild(new Mesh(vb,new TriangleStripArray(indices,strips),materials[tid]));
            }
        }finally{in.close();}
        assets[id]=group;return group;
    }
    private void place(Node mesh,float x,float y,float z,float size,float angle){
        tr.setIdentity();tr.postTranslate(x,y,z);if(angle!=0)tr.postRotate(angle,0,1,0);
        if(size!=1)tr.postScale(size,size,size);scene.gpu.render(mesh,tr);
    }
    void prepare(float x,float z){
        for(int i=0;i<near.length;i++)near[i]=Journey.d2(x,z,placement[i][0],placement[i][2])<27*27;
    }
    boolean shows(int owner){return owner<0||!near[owner];}
    void draw(float cx,float cz,float yaw,float time,boolean meeting){
        try{
            for(int i=0;i<type.length;i++)if(near[i]){
                float[] a=placement[i];if(scene.visible(a[0],a[2],60,18))place(load(type[i]),a[0],a[1],a[2],1,a[3]);
            }
            float fx=Journey.ox(Journey.FOUNTAIN)+2.2f,fz=Journey.oz(Journey.FOUNTAIN)+.4f;
            if(scene.visible(fx,fz,55,3))place(load(fountain),fx,land.ground(fx,fz),fz,1.5f,0);
            for(int i=0;i<9;i++){
                float x=47+(i%2)*7+(i/2)*1.6f,z=283+(i/2)*5.3f;
                if(!land.blocked(x,z)&&scene.visible(x,z,45,3))place(load(lantern),x,land.ground(x,z),z,1.5f,i%2*180);
            }
            if(cx>102&&cz<163)place(load(cliff),128,-47.5f,0,1,0);
            if(cx>102&&cz>100&&cz<272)place(load(rocks),128,-47.5f,128,1,0);
            for(int i=0;i<trees.length;i++){
                short[] a=trees[i];float x=a[0]/32f,z=a[2]/32f,size=a[3]/128f;
                if(!scene.visible(x,z,89,4)||Journey.d2(cx,cz,x,z)<(meeting?310:16))continue;
                place(crowns[a[4]],x,land.ground(x,z)-.05f,z,size,yaw);
            }
            if(cx>108 && cz<177){
                for(int i=0;i<64;i++){
                    float x=161+i%8*4.2f+(float)Math.sin(i*2.4)*1.9f,z=71+i/8*4.6f+(float)Math.cos(i*1.9)*1.2f;
                    if(!scene.visible(x,z,26,1)||Math.abs(x-(175-(z-82)*.19f))<2)continue;
                    float size=1.8f+i%4;
                    place(load(boulder),x,land.ground(x,z)-.045f,z,size,i*53);
                }
            }
            if(cx>108 && cz<177)for(int i=0;i<patchCount;i++){
                float[] a=patches[i];if(!scene.visible(a[0],a[1],19,1))continue;
                place(load(i%4==0?bush:grass),a[0],land.ground(a[0],a[1]),a[1],a[2],a[3]+3*(float)Math.sin(time*1.5+i));
            }
        }catch(IOException e){throw new IllegalStateException(e.toString());}
    }
}
