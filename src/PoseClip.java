import java.io.*;
import javax.microedition.m3g.VertexArray;

/** Pre-skinned positions share the actor's indices, UVs and texture for every action. */
final class PoseClip {
    static final int IDLE=0,WALK=1,RUN=2,ATTACK=3,JUMP=4,GLIDE=5;
    private static final int[] START={0,6,18,30,38,43},COUNT={6,12,12,8,5,6};
    private final short[][] positions,hand;
    private final VertexArray vertices;
    private int last=-1,previous,next,fraction;
    private final short[] blended;
    PoseClip(String name,VertexArray target,int n)throws IOException{
        vertices=target;blended=new short[n*3];
        DataInputStream in=new DataInputStream(getClass().getResourceAsStream("/"+name+".anim"));
        try{
            if(in.readUnsignedShort()!=n || in.readUnsignedShort()!=49)throw new IOException("pose count");
            positions=new short[49][n*3];hand=new short[49][3];
            for(int i=0;i<49;i++){
                for(int j=0;j<3;j++)hand[i][j]=in.readShort();
                for(int j=0;j<n*3;j++)positions[i][j]=in.readShort();
            }
        }finally{in.close();}
    }
    void set(int action,float phase){
        boolean loop=action!=ATTACK && action!=JUMP;
        float position=Land.clamp(phase,0,1)*(loop?COUNT[action]:COUNT[action]-1);
        int key=Math.min(COUNT[action]-1,(int)position);
        previous=START[action]+key;
        next=START[action]+(loop?(key+1)%COUNT[action]:Math.min(key+1,COUNT[action]-1));
        fraction=(int)((position-key)*16);
        int stamp=previous*17+fraction;
        if(stamp==last)return;
        short[] a=positions[previous],b=positions[next];
        for(int i=0;i<a.length;i++)blended[i]=(short)(a[i]+((b[i]-a[i])*fraction>>4));
        vertices.set(0,vertices.getVertexCount(),blended);last=stamp;
    }
    float hand(int axis){return (hand[previous][axis]+((hand[next][axis]-hand[previous][axis])*fraction>>4))*1.24f/1024;}
}
