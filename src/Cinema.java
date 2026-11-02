import java.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.media.*;

/** Sequential Motion JPEG with periodic PNG fallback frames. Only the current frame is held in memory. */
final class Cinema {
    private DataInputStream source;
    private byte[] buffer=new byte[0];
    private Player sound;
    private InputStream audio;
    private boolean jpeg=true;
    private static final int FPS=12;
    private String[] lines;
    private int[] begin,end;
    Image frame; int number;float timer;
    void open(String name)throws IOException{
        close();source=new DataInputStream(getClass().getResourceAsStream("/"+name+".film"));number=0;timer=0;next(true);
        DataInputStream sub=new DataInputStream(getClass().getResourceAsStream("/"+name+".sub"));
        try{int n=sub.readUnsignedShort();lines=new String[n];begin=new int[n];end=new int[n];
            for(int i=0;i<n;i++){begin[i]=sub.readInt();end[i]=sub.readInt();lines[i]=sub.readUTF();}
        }finally{sub.close();}
        try{
            audio=getClass().getResourceAsStream("/"+name+".wav");
            sound=Manager.createPlayer(audio,"audio/x-wav");sound.start();
        }catch(Exception unsupported){closeSound();}
    }
    boolean tick(float dt)throws IOException{
        timer+=dt;
        if(sound!=null && sound.getState()==Player.STARTED){
            long t=sound.getMediaTime();if(t>=0)timer=Math.max(timer,t/1000000f);
        }
        int wanted=(int)(timer*FPS)+1;
        while(number<wanted)if(!next(number+1==wanted))return false;
        return true;
    }
    String subtitle(){
        int t=(number-1)*1000/FPS;
        for(int i=0;i<lines.length;i++)if(t>=begin[i]&&t<=end[i])return lines[i];
        return "";
    }
    void pause(){if(sound!=null)try{sound.stop();}catch(Exception ignored){}}
    void resume(){if(sound!=null)try{sound.start();}catch(Exception ignored){}}
    private boolean next(boolean decode)throws IOException{
        int n=source.readInt();if(n==0){close();return false;}
        if(n<0||n>262144)throw new IOException("Bad frame");
        if(buffer.length<n)buffer=new byte[n+256];source.readFully(buffer,0,n);
        boolean png=buffer[0]==(byte)0x89;
        if(png && (decode||!jpeg))frame=Image.createImage(buffer,0,n);
        else if(decode && jpeg && !png){
            try{frame=Image.createImage(buffer,0,n);}
            catch(IllegalArgumentException unsupported){jpeg=false;}
        }
        number++;return true;
    }
    private void closeSound(){
        if(sound!=null){sound.close();sound=null;}
        if(audio!=null)try{audio.close();}catch(IOException ignored){}audio=null;
    }
    void close(){closeSound();if(source!=null)try{source.close();}catch(IOException ignored){}source=null;frame=null;last=null;}
    private Image last;
    private int[] rowSource=new int[0],pixels=new int[0];
    private int prevW,prevH;
    void fit(Graphics g,Image image,int x,int y,int w,int h){
        if(image==null||w<=0||h<=0)return;
        int sw=image.getWidth(),sh=image.getHeight();
        if(sw==w&&sh==h){g.drawImage(image,x,y,Graphics.TOP|Graphics.LEFT);return;}
        if(last!=image||prevW!=w||prevH!=h){
            if(pixels.length<w*h)pixels=new int[w*h];
            if(rowSource.length<sw)rowSource=new int[sw];
            for(int j=0;j<h;j++){
                image.getRGB(rowSource,0,sw,0,j*sh/h,sw,1);
                for(int i=0;i<w;i++)pixels[j*w+i]=rowSource[i*sw/w];
            }
            last=image;prevW=w;prevH=h;
        }
        g.drawRGB(pixels,0,w,x,y,w,h,false);
    }
}
