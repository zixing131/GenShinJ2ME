import java.io.*;
import java.util.Hashtable;
import java.util.Random;
import javax.microedition.lcdui.*;
import javax.microedition.m3g.*;

final class Scenery {
    private final Land land;
    final Graphics3D gpu=Graphics3D.getInstance();
    private final Camera camera=new Camera();
    private final Transform transform=new Transform();
    private final Background clear=new Background();
    final Fog fog=new Fog();
    private final Appearance paint=new Appearance();
    private final Mesh[] tiles=new Mesh[128];
    private final boolean[] wet=new boolean[128];
    private final Hashtable models=new Hashtable();
    private final Appearance[] surfaces=new Appearance[32];
    private final Texture2D[] maps=new Texture2D[32];
    private final Hashtable clips=new Hashtable();
    private DetailScene details;
    private short[] cityOwner;
    private Mesh rock,shade;
    private Image cloudSky;
    private Sea sea;
    private Mesh[] city;
    private short[] cityX,cityZ;
    private final float[] px=new float[900],pz=new float[900],scale=new float[900],turn=new float[900];
    private final Mesh[] prop=new Mesh[900];
    private int props;
    private float cx,cz,dx,dz;
    private int lastWidth,lastHeight;
    static final float SEA=1.06f;
    Scenery(Land map) throws IOException{
        land=map;clear.setColorClearEnable(false);
        fog.setMode(Fog.LINEAR);fog.setLinear(42,102);fog.setColor(0xb4d9ce);
        PolygonMode both=new PolygonMode();both.setCulling(PolygonMode.CULL_NONE);
        paint.setPolygonMode(both);paint.setFog(fog);
        cloudSky=Image.createImage("/detail/sky.png");
        CompositingMode shadowBlend=new CompositingMode();shadowBlend.setBlending(CompositingMode.ALPHA);shadowBlend.setDepthWriteEnable(false);
        Appearance shadowStyle=new Appearance();shadowStyle.setPolygonMode(both);shadowStyle.setCompositingMode(shadowBlend);
        VertexBuffer disc=new VertexBuffer();short[] points=new short[3*12*3];int[] sides=new int[12];
        for(int i=0;i<12;i++){sides[i]=3;for(int j=0;j<2;j++){double a=(i+j)*Math.PI/6;int k=i*9+3+j*3;points[k]=(short)(110*Math.cos(a));points[k+2]=(short)(73*Math.sin(a));}}
        disc.setPositions(shorts(points,3),1f/256,null);disc.setDefaultColor(0x63506464);shade=new Mesh(disc,new TriangleStripArray(0,sides),shadowStyle);
        readModels();readActors(both);foliage("oak",both);foliage("pine",both);buildTerrain(both);details=new DetailScene(this,map,both);readCity(both);populate();rock=(Mesh)models.get("stone");
    }
    static VertexArray shorts(short[] v,int dims){
        VertexArray a=new VertexArray(v.length/dims,dims,2);a.set(0,v.length/dims,v);return a;
    }
    static VertexArray colors(byte[] v){
        VertexArray a=new VertexArray(v.length/3,3,1);a.set(0,v.length/3,v);return a;
    }
    private void readModels() throws IOException{
        DataInputStream in=new DataInputStream(getClass().getResourceAsStream("/models.bin"));
        try{
            int n=in.readUnsignedShort();
            while(n-->0){
                String name=in.readUTF();int tris=in.readUnsignedShort();short[] pos=new short[tris*9];byte[] rgb=new byte[pos.length];int[] strips=new int[tris];
                for(int t=0;t<tris;t++){
                    for(int j=0;j<9;j++)pos[t*9+j]=in.readShort();
                    for(int j=0;j<3;j++){byte c=in.readByte();for(int k=0;k<3;k++)rgb[t*9+k*3+j]=c;}
                    strips[t]=3;
                }
                VertexBuffer v=new VertexBuffer();v.setPositions(shorts(pos,3),1f/256,null);v.setColors(colors(rgb));
                models.put(name,new Mesh(v,new TriangleStripArray(0,strips),paint));
            }
        }finally{in.close();}
    }
    private void readActors(PolygonMode both) throws IOException{
        String[] names={"aether","lumine","paimon","amber","bard","dragon","hili"};
        for(int i=0;i<names.length;i++){
            String name=names[i];
            Appearance style=new Appearance();style.setPolygonMode(both);style.setFog(fog);
            Integer limit=(Integer)Graphics3D.getProperties().get("maxTextureDimension");
            boolean hi=i<4 && limit!=null && limit.intValue()>=512 && Runtime.getRuntime().freeMemory()>8*1024*1024;
            Texture2D texture=new Texture2D(new Image2D(Image2D.RGB,Image.createImage("/"+name+(hi?"-close.png":"-tex.png"))));
            texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_LINEAR);style.setTexture(0,texture);
            DataInputStream in=new DataInputStream(getClass().getResourceAsStream("/"+name+".mesh"));
            try{
                int n=in.readUnsignedShort(),tris=in.readUnsignedShort();
                short[] pos=new short[n*3],uv=new short[n*2];byte[] light=new byte[n*3];
                for(int v=0;v<n;v++){
                    for(int j=0;j<3;j++)pos[v*3+j]=in.readShort();
                    uv[v*2]=in.readShort();uv[v*2+1]=in.readShort();byte l=in.readByte();
                    light[v*3]=light[v*3+1]=light[v*3+2]=l;
                }
                int[] ix=new int[tris*3],strips=new int[tris];
                for(int j=0;j<ix.length;j++)ix[j]=in.readUnsignedShort();
                for(int j=0;j<tris;j++)strips[j]=3;
                IndexBuffer index=new TriangleStripArray(ix,strips);
                VertexArray coords=shorts(uv,2),shades=colors(light);
                VertexArray vertices=shorts(pos,3);
                VertexBuffer vb=new VertexBuffer();vb.setPositions(vertices,1f/1024,null);
                vb.setTexCoords(0,coords,1f/4096,null);vb.setColors(shades);
                models.put(name,new Mesh(vb,index,style));
                if(i==0 || i==1 || i==3)clips.put(name,new PoseClip(name,vertices,n));
            }finally{in.close();}
        }
    }
    private void foliage(String name,PolygonMode both)throws IOException{
        Appearance a=new Appearance();a.setPolygonMode(both);a.setFog(fog);
        Texture2D tex=new Texture2D(new Image2D(Image2D.RGBA,Image.createImage("/"+name+".png")));
        tex.setBlending(Texture2D.FUNC_REPLACE);tex.setWrapping(Texture2D.WRAP_CLAMP,Texture2D.WRAP_CLAMP);
        a.setTexture(0,tex);CompositingMode cut=new CompositingMode();cut.setAlphaThreshold(.45f);a.setCompositingMode(cut);
        VertexBuffer vb=new VertexBuffer();vb.setPositions(shorts(new short[]{-3,0,0,-3,6,0,3,0,0,3,6,0},3),1,null);
        vb.setTexCoords(0,shorts(new short[]{0,1,0,0,1,1,1,0},2),1,null);
        models.put(name,new Mesh(vb,new TriangleStripArray(0,new int[]{4}),a));
    }
    private void groundTextures(){
        for(int i=0;i<32;i++){
            int tx=i%4*64+32,tz=i/4*64+32;
            if(visible(tx,tz,150,56)){
                if(maps[i]==null)try{
                    String id=i<10?"0"+i:""+i;
                    Texture2D tex=new Texture2D(new Image2D(Image2D.RGB,Image.createImage("/land"+id+".png")));
                    tex.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_LINEAR);tex.setWrapping(Texture2D.WRAP_CLAMP,Texture2D.WRAP_CLAMP);
                    surfaces[i].setTexture(0,tex);maps[i]=tex;
                }catch(IOException ex){throw new IllegalStateException("Missing terrain "+i);}
            }else if(maps[i]!=null && Journey.d2(cx,cz,tx,tz)>175*175){surfaces[i].setTexture(0,null);maps[i]=null;}
        }
    }
    private void buildTerrain(PolygonMode both) throws IOException{
        int[] indices=new int[8*18],strips=new int[8];
        for(int row=0;row<8;row++){
            strips[row]=18;
            for(int i=0;i<9;i++){indices[row*18+2*i]=row*9+i;indices[row*18+2*i+1]=(row+1)*9+i;}
        }
        IndexBuffer index=new TriangleStripArray(indices,strips);
        for(int patch=0;patch<32;patch++){surfaces[patch]=new Appearance();surfaces[patch].setPolygonMode(both);surfaces[patch].setFog(fog);}
        for(int half=0;half<2;half++){
            for(int z=0;z<8;z++)for(int x=0;x<8;x++){
                int tile=(z+half*8)*8+x;
                short[] pos=new short[81*3],uv=new short[81*2];byte[] light=new byte[81*3];
                for(int j=0;j<9;j++)for(int i=0;i<9;i++){
                    int gx=x*8+i,gz=(z+half*8)*8+j,k=j*9+i;short h=land.heights[gz*65+gx];
                    pos[k*3]=(short)(gx*128);pos[k*3+1]=h;pos[k*3+2]=(short)(gz*128);
                    wet[tile]|=h<SEA*32;
                    uv[k*2]=(short)((x%2)*8+i);uv[k*2+1]=(short)((z%2)*8+j);
                    int nx=Math.min(64,gx+1),nz=Math.min(128,gz+1);
                    int l=(int)Land.clamp(242+(land.heights[gz*65+nx]-h)*.19f+(land.heights[nz*65+gx]-h)*.1f,152,255);
                    light[k*3]=light[k*3+1]=light[k*3+2]=(byte)l;
                }
                VertexBuffer vb=new VertexBuffer();vb.setPositions(shorts(pos,3),1f/32,null);vb.setTexCoords(0,shorts(uv,2),1f/16,null);vb.setColors(colors(light));
                tiles[tile]=new Mesh(vb,index,surfaces[half*16+z/2*4+x/2]);
            }
        }
        sea=new Sea(land,fog,both,wet);
    }
    private void readCity(PolygonMode both) throws IOException{
        Image atlas=null;
        Integer max=(Integer)Graphics3D.getProperties().get("maxTextureDimension");
        if(max!=null && max.intValue()>=1024 && Runtime.getRuntime().freeMemory()>7*1024*1024){
            try{atlas=Image.createImage("/city-detail.jpg");}catch(IOException unsupported){}
        }
        if(atlas==null)atlas=Image.createImage("/city.png");
        Texture2D tex=new Texture2D(new Image2D(Image2D.RGB,atlas));
        tex.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_LINEAR);
        Appearance surface=new Appearance();surface.setPolygonMode(both);surface.setFog(fog);surface.setTexture(0,tex);
        DataInputStream in=new DataInputStream(getClass().getResourceAsStream("/detail/shell.bin"));
        try{
            Appearance paving=details.material("paving.png",false),square=details.material("square.png",false);
            int chunks=in.readUnsignedShort();cityOwner=new short[chunks];city=new Mesh[chunks];cityX=new short[chunks];cityZ=new short[chunks];
            for(int c=0;c<chunks;c++){
                cityX[c]=in.readShort();cityZ[c]=in.readShort();cityOwner[c]=in.readShort();int n=in.readUnsignedShort(),tris=in.readUnsignedShort();
                short[] pos=new short[n*3],uv=new short[n*2];byte[] light=new byte[n*3];
                for(int v=0;v<n;v++){
                    for(int j=0;j<3;j++)pos[v*3+j]=in.readShort();
                    uv[v*2]=in.readShort();uv[v*2+1]=in.readShort();
                    byte shade=in.readByte();light[v*3]=light[v*3+1]=light[v*3+2]=shade;
                }
                int[] ix=new int[tris*3],strips=new int[tris];
                for(int j=0;j<ix.length;j++)ix[j]=in.readUnsignedShort();
                for(int j=0;j<tris;j++)strips[j]=3;
                VertexBuffer vb=new VertexBuffer();vb.setPositions(shorts(pos,3),1f/32,null);
                vb.setTexCoords(0,shorts(uv,2),cityOwner[c]<=-2?1f/256:1f/4096,null);vb.setColors(colors(light));
                city[c]=new Mesh(vb,new TriangleStripArray(ix,strips),cityOwner[c]==-3?square:cityOwner[c]==-2?paving:surface);
            }
        }finally{in.close();}
    }
    private void add(String name,float x,float z,float s,float angle){
        px[props]=x;pz[props]=z;scale[props]=s;turn[props]=angle;prop[props++]=(Mesh)models.get(name);
    }
    private void populate() throws IOException{
        Random rng=new Random(7093);Image chart=Image.createImage("/chart.png");int[] rgb=new int[112*224];
        chart.getRGB(rgb,0,112,0,0,112,224);
        for(int i=0;i<2100 && props<825;i++){
            float x=(rng.nextInt()&65535)/256f,z=(rng.nextInt()&65535)/128f;
            int color=rgb[Math.min(223,(int)(z*224/512))*112+Math.min(111,(int)(x*112/256))];
            int r=(color>>16)&255,g=(color>>8)&255;
            if(land.ground(x,z)<1.65 || (x>21&&x<72&&z>264&&z<334))continue;
            boolean near=false;for(int k=0;k<Journey.OBJECTS.length;k++)if(Journey.d2(x,z,Journey.ox(k),Journey.oz(k))<28)near=true;
            if(near)continue;
            float s=.67f+(rng.nextInt()&255)/220f;
            if(z<256 && x>120 && g>r+8)continue;
            if(g>r+8)add((i%3==0)?"oak":"pine",x,z,s,i*51);
            else if(i%5==0)add("stone",x,z,s,i*43);
        }
        for(int i=0;i<24;i++){
            float angle=i*2.39996f,rad=8+(i%3)*4;
            add(i%3==0?"pine":"oak",Journey.ox(Journey.FOREST)+(float)Math.cos(angle)*rad,Journey.oz(Journey.FOREST)+(float)Math.sin(angle)*rad,1.1f+(i%4)*.15f,0);
        }
        for(int i=0;i<5;i++)add("stone",Journey.ox(Journey.STATUE)+4+i,Journey.oz(Journey.STATUE)+3-i*.6f,.55f,31*i);
    }
    boolean visible(float x,float z,float range,float margin){
        float u=x-cx,v=z-cz,front=u*dx+v*dz;
        return u*u+v*v<range*range && front> -margin && Math.abs(u*dz-v*dx)<(front+margin)*1.8f+margin;
    }
    private void place(Mesh mesh,float x,float y,float z,float size,float yaw,float tilt){
        transform.setIdentity();transform.postTranslate(x,y,z);
        if(yaw!=0)transform.postRotate(yaw,0,1,0);if(tilt!=0)transform.postRotate(tilt,0,0,1);
        if(size!=1)transform.postScale(size,size,size);gpu.render(mesh,transform);
    }
    private void object(String name,float x,float y,float z,float size,float yaw,float tilt){
        if(visible(x,z,98,6))place((Mesh)models.get(name),x,y,z,size,yaw,tilt);
    }
    private void onGround(String name,float x,float z,float size,float rot){
        object(name,x,land.ground(x,z),z,size,rot,0);
    }
    private void sky(Graphics g,int w,int h,float time,float yaw){
        for(int i=0;i<36;i++){int c=i*80/35;g.setColor(68+c,153+c/2,200+c/3);g.fillRect(0,i*h/63,w,h/63+1);}
        g.setColor(0xb4d9ce);g.fillRect(0,16*h/28,w,h-16*h/28+1);
        int sw=cloudSky.getWidth(),sh=cloudSky.getHeight(),offset=(int)(yaw*sw/360+time*1.05f)%sw;
        if(offset<0)offset+=sw;
        int skyH=Math.min(sh,h*4/7);
        for(int left=0;left<w;){int width=Math.min(sw-offset,w-left);g.drawRegion(cloudSky,offset,0,width,skyH,0,left,0,Graphics.TOP|Graphics.LEFT);left+=width;offset=0;}
    }
    void draw(Graphics g,int w,int h,Journey s,float time,boolean title){
        float yaw=title?171+(float)Math.sin(time*.08)*8:s.yaw;
        float a=yaw*.017453293f;dx=(float)Math.sin(a);dz=(float)Math.cos(a);
        float base=Math.max(land.ground(s.x,s.z),land.water(s.x,s.z));
        cx=title?49:s.x-dx*5.3f;cz=title?244:s.z-dz*5.3f;
        if(title){yaw=0+10*(float)Math.sin(time*.065);a=yaw*.017453293f;dx=(float)Math.sin(a);dz=(float)Math.cos(a);}
        if(!title)for(int j=1;j<=6;j++){
            float dist=j*.89f;
            if(land.blocked(s.x-dx*dist,s.z-dz*dist)){
                dist=Math.max(.65f,dist-.88f);cx=s.x-dx*dist;cz=s.z-dz*dist;break;
            }
        }
        float cy=title?13:Math.max(base+s.air+3.0f,land.ground(cx,cz)+1.1f);
        boolean meeting=s.pages!=null && s.speaker.equals("林间相会");
        if(meeting){cx=Journey.ox(Journey.FOREST)+8;cz=Journey.oz(Journey.FOREST)-20;cy=land.ground(cx,cz)+3.9f;yaw=-20;dx=-.342f;dz=.940f;}
        boolean beachTalk=s.pages!=null && s.stage==Journey.ANCHOR;
        boolean amberTalk=s.pages!=null && s.stage==Journey.AIR_SLIME;
        if(beachTalk||amberTalk){
            int subject=beachTalk?Journey.PAIMON:Journey.AMBER;
            yaw=s.yaw+128;a=yaw*.017453293f;dx=(float)Math.sin(a);dz=(float)Math.cos(a);
            float fx=(s.x+Journey.ox(subject))/2,fz=(s.z+Journey.oz(subject))/2;
            cx=fx-dx*4.1f;cz=fz-dz*4.1f;cy=Math.max(base+2.25f,land.ground(cx,cz)+.65f);
        }
        groundTextures();sky(g,w,h,time,yaw);
        gpu.bindTarget(g,true,0);
        try{
            gpu.setViewport(0,0,w,h);gpu.clear(clear);
            if(lastWidth!=w||lastHeight!=h){camera.setPerspective(59,(float)w/h,.35f,125);lastWidth=w;lastHeight=h;}
            transform.setIdentity();transform.postTranslate(cx,cy,cz);transform.postRotate(180+yaw,0,1,0);transform.postRotate(title?-9:-15,1,0,0);gpu.setCamera(camera,transform);
            for(int t=0;t<tiles.length;t++){
                int tx=(t%8)*32,tz=(t/8)*32;if(!visible(tx+16,tz+16,126,31))continue;
                gpu.render(tiles[t],null);
            }
            for(int i=0;i<props;i++){
                if(!visible(px[i],pz[i],96,7))continue;
                float depth=(px[i]-cx)*dx+(pz[i]-cz)*dz;
                float lateral=Math.abs((px[i]-cx)*dz-(pz[i]-cz)*dx);
                if(depth<(meeting?32:9) && lateral<(meeting?13:4)*scale[i])continue;
                place(prop[i],px[i],land.ground(px[i],pz[i]),pz[i],scale[i],prop[i]==rock?turn[i]:yaw,0);
            }
            details.prepare(cx,cz);
            for(int i=0;i<city.length;i++)if(details.shows(cityOwner[i]) && visible(cityX[i],cityZ[i],122,32))gpu.render(city[i],null);
            sea.draw(this,time);
            details.draw(cx,cz,yaw,time,meeting);
            onGround("statue",Journey.ox(Journey.STATUE),Journey.oz(Journey.STATUE),1.08f,175);
            onGround("rune",Journey.ox(Journey.ANCHOR),Journey.oz(Journey.ANCHOR),.82f,0);
            if(s.stage>=Journey.SLIMES)for(int i=Journey.RUNE;i<=Journey.CHEST;i++){
                float x=Journey.ox(i),z=Journey.oz(i);
                onGround(i==Journey.CHEST?"chest":"rune",x,z,1,180);
                for(int j=0;j<s.runes;j++)if(i-Journey.RUNE==Journey.ORDER[j])object("wind",x,land.ground(x,z)+1.9f,z,1.4f,time*60,0);
            }
            if(s.stage==Journey.FOREST||meeting){
                float x=Journey.ox(Journey.FOREST),z=Journey.oz(Journey.FOREST);
                boolean depart=meeting && s.dialogIndex==1;
                object("dragon",x-3,land.ground(x-3,z+7)+(depart?8:0),z+7,1,132,depart?-12:0);
                if(!depart)onGround("bard",x+1.6f,z+3,1.15f,-22);
            }
            if(s.stage==Journey.TEAR && !meeting)onGround("teardrop",Journey.ox(Journey.TEAR),Journey.oz(Journey.TEAR),1.6f,time*34);
            if((s.pages!=null && s.stage==Journey.AIR_SLIME))onGround("amber",Journey.ox(Journey.AMBER),Journey.oz(Journey.AMBER),1.14f,165);
            if(s.stage==Journey.AMBER || s.stage==Journey.REPORT || s.stage==Journey.TERRACE)
                onGround("amber",Journey.ox(s.stage),Journey.oz(s.stage),1.14f,165);
            for(int i=0;i<Journey.WX.length;i++)if((s.wisps&(1<<i))==0){
                float x=Land.x(Journey.WX[i]),z=Land.z(Journey.WZ[i]);object("crystal",x,land.ground(x,z)+.65f+.19f*(float)Math.sin(time*2+i),z,.8f,time*60,0);
            }
            for(int i=0;i<Journey.BX.length;i++)if((s.berries&(1<<i))==0)onGround("berry",Land.x(Journey.BX[i]),Land.z(Journey.BZ[i]),1.1f,0);
            for(int i=0;i<s.ehp.length;i++)if(s.activeEnemy(i)){
                float x=s.ex[i],z=s.ez[i],hop=(float)Math.abs(Math.sin(time*3+i))*.36f;
                object(i>=4?"hili":i==3?"airslime":"slime",x,land.ground(x,z)+(i==3?3.3f:0)+hop*(i>=4?.17f:1),z,i<4?1.05f:1.25f,s.yaw+180,0);
            }
            if(!title && !meeting){
                float y=base+s.air;
                String character=s.asAmber && !(s.pages!=null && s.stage==Journey.AIR_SLIME)?"amber":s.hero==0?"aether":"lumine";
                PoseClip clip=(PoseClip)clips.get(character);
                int action=s.glide?PoseClip.GLIDE:s.air>0?PoseClip.JUMP:s.flash>0?PoseClip.ATTACK:
                    s.moving?(s.sprint?PoseClip.RUN:PoseClip.WALK):PoseClip.IDLE;
                float cycle=time*(s.sprint?2.0f:s.moving?1.32f:.46f);
                float phase=action==PoseClip.ATTACK?1-s.flash/.26f:action==PoseClip.JUMP?Land.clamp(.5f-s.vy/12,0,1):cycle-(int)cycle;
                clip.set(action,phase);
                if(base>land.water(s.x,s.z)+.02f)place(shade,s.x,base+.045f,s.z,1.18f/(1+s.air*.12f),s.yaw,0);
                object(character,s.x,y,s.z,1.24f,s.yaw,0);
                if(action==PoseClip.ATTACK){
                    float hx=clip.hand(0),hz=clip.hand(2);
                    object(s.asAmber?"bow":"sword",s.x+dz*hx+dx*hz,y+clip.hand(1),s.z-dx*hx+dz*hz,.82f,s.yaw,
                        s.asAmber?0:105-phase*185);
                }else if(!s.glide && s.pages==null){
                    object(s.asAmber?"bow":"sword",s.x+.12f*dz-.20f*dx,y+1.38f,s.z-.12f*dx-.20f*dz,.74f,s.yaw,154);
                }
                if(s.shot>0){float v=(.32f-s.shot)*40;object("arrow",s.x+dx*v,y+1.2f+v*.15f,s.z+dz*v,1,s.yaw,0);}
                if(s.wind>0)object("wind",s.x,y+.42f,s.z,1.0f+(1.05f-s.wind)*6,s.wind*480,0);
                if(s.glide)object("wings",s.x,y+1.49f,s.z,1.05f,s.yaw,0);
                if(s.stage<=Journey.STATUE || (s.pages!=null && s.speaker.equals("派蒙"))){
                    float fx=Journey.ox(0),fz=Journey.oz(0);
                    if(s.stage>0 && !beachTalk){
                        int destination=s.target();float gx=Journey.ox(destination)-s.x,gz=Journey.oz(destination)-s.z;
                        float dist=Math.max(1,(float)Math.sqrt(gx*gx+gz*gz));
                        fx=s.x+gx/dist*2.8f+dz*.7f;fz=s.z+gz/dist*2.8f-dx*.7f;
                    }
                    object("paimon",fx,Math.max(land.water(fx,fz),land.ground(fx,fz))+1.05f+.16f*(float)Math.sin(time*3),fz,.96f,s.yaw,0);
                }
            }
        }finally{gpu.releaseTarget();}
    }
}
