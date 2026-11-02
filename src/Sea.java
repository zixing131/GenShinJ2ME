import java.io.IOException;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;

/** Water and foam use the same collision surface as the traveler and the terrain. */
final class Sea {
    private final Mesh[] coast=new Mesh[128];
    private final Mesh lake;
    private final Land land;
    private final Texture2D ripples;
    private final Appearance water=new Appearance();
    Sea(Land land,Fog fog,PolygonMode both,boolean[] wet)throws IOException{
        this.land=land;
        ripples=new Texture2D(new Image2D(Image2D.RGB,Image.createImage("/detail/water.png")));
        ripples.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_LINEAR);water.setTexture(0,ripples);
        water.setFog(fog);water.setPolygonMode(both);
        for(int i=0;i<coast.length;i++)if(wet[i])coast[i]=surface(i%8*32,i/8*32,32,32,Scenery.SEA);
        lake=surface(Land.LAKE_X-12,Land.LAKE_Z-10,24,20,Land.LAKE_LEVEL);
    }
    private Mesh surface(float x,float z,int width,int depth,float level){
        int n=17;short[] pos=new short[n*n*3],uv=new short[n*n*2];byte[] color=new byte[n*n*3];
        int[] ix=new int[(n-1)*n*2],lengths=new int[n-1];
        for(int j=0;j<n;j++)for(int i=0;i<n;i++){
            int k=j*n+i;float gx=x+width*i/16f,gz=z+depth*j/16f,d=level-land.ground(gx,gz);
            pos[k*3]=(short)(gx*32);pos[k*3+1]=(short)(level*32);pos[k*3+2]=(short)(gz*32);
            uv[k*2]=(short)(gx*19);uv[k*2+1]=(short)(gz*24);
            float deep=Land.clamp(d/3,0,1),foam=Land.clamp((.34f-d)*2.5f,0,1);
            color[k*3]=(byte)(97-58*deep+105*foam);color[k*3+1]=(byte)(214-49*deep+29*foam);color[k*3+2]=(byte)(202-7*deep+38*foam);
            if(j<n-1){lengths[j]=n*2;ix[j*n*2+2*i]=k;ix[j*n*2+2*i+1]=k+n;}
        }
        VertexBuffer v=new VertexBuffer();v.setPositions(Scenery.shorts(pos,3),1f/32,null);
        v.setTexCoords(0,Scenery.shorts(uv,2),1f/64,null);v.setColors(Scenery.colors(color));
        return new Mesh(v,new TriangleStripArray(ix,lengths),water);
    }
    void draw(Scenery scene,float t){
        ripples.setTranslation(t*.026f%1,t*.045f%1,0);
        for(int i=0;i<coast.length;i++)if(coast[i]!=null && scene.visible(i%8*32+16,i/8*32+16,126,31))scene.gpu.render(coast[i],null);
        if(scene.visible(Land.LAKE_X,Land.LAKE_Z,122,22))scene.gpu.render(lake,null);
    }
}
