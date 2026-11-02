import java.io.*;
import javax.microedition.lcdui.Image;

/** Coordinates are quarter-scale game metres. +Z is south on the field chart. */
final class Land {
    static final int W=256, D=512;
    final short[] heights=new short[65*129];
    private final short[] streets=new short[169*217];
    private final byte[] buildings=new byte[169*217];
    private int street(float x,float z){
        int i=(int)Math.floor((x-14)*2+.5f),j=(int)Math.floor((z-258)*2+.5f);
        return i>=0 && i<169 && j>=0 && j<217?j*169+i:-1;
    }
    boolean blocked(float x,float z){int k=street(x,z);return k>=0 && buildings[k]!=0;}
    Land() throws IOException {
        DataInputStream in=new DataInputStream(getClass().getResourceAsStream("/heights.bin"));
        try { for(int i=0;i<heights.length;i++) heights[i]=in.readShort(); } finally { in.close(); }
        in=new DataInputStream(getClass().getResourceAsStream("/streets.bin"));
        try{for(int i=0;i<streets.length;i++)streets[i]=in.readShort();in.readFully(buildings);}finally{in.close();}
    }
    static float x(int original){return (original-2048)*.25f;}
    static float z(int original){return (original+2048)*.25f;}
    float ground(float x,float z){
        int cell=street(x,z);float paving=cell<0?-1024:streets[cell]/32f;
        x=clamp(x*.25f,0,63.9999f); z=clamp(z*.25f,0,127.9999f);
        int ix=(int)x, iz=(int)z, k=iz*65+ix;
        float u=x-ix,v=z-iz;
        if(u+v<=1) return Math.max(paving,(heights[k]+u*(heights[k+1]-heights[k])+v*(heights[k+65]-heights[k]))/32);
        return Math.max(paving,(heights[k+66]+(1-u)*(heights[k+65]-heights[k+66])+(1-v)*(heights[k+1]-heights[k+66]))/32);
    }
    static final float LAKE_X=x(2567),LAKE_Z=z(-1332),LAKE_LEVEL=4.94f;
    float water(float x,float z){return Math.abs(x-LAKE_X)<12 && Math.abs(z-LAKE_Z)<10?LAKE_LEVEL:1.06f;}
    static float clamp(float n,float a,float b){return n<a?a:n>b?b:n;}
}
