import java.io.File;
import java.io.OutputStream;
import java.awt.image.BufferedImage;
import org.recompile.mobile.MobilePlatform;

/** Record the emulator's live LCD while normal keypad events are being processed. */
final class PlayCapture {
    private final MobilePlatform phone;
    private final File output;
    private Thread writer;
    private volatile boolean running;
    private Throwable problem;
    PlayCapture(MobilePlatform phone,File output){this.phone=phone;this.output=output;}
    void start(final String name)throws Exception{
        final BufferedImage lcd=phone.getLcdFrontbuffer().getCanvas();
        final int w=lcd.getWidth(),h=lcd.getHeight();
        final Process ffmpeg=new ProcessBuilder("ffmpeg","-loglevel","error","-y","-f","rawvideo","-pixel_format","rgb24",
                "-video_size",w+"x"+h,"-framerate","15","-i","pipe:0","-an","-c:v","libx264","-preset","veryfast",
                "-crf","16","-pix_fmt","yuv420p",new File(output,name+".mp4").toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        running=true;problem=null;
        writer=new Thread(()->{
            try(OutputStream sink=ffmpeg.getOutputStream()){
                int[] pixels=new int[w*h];byte[] rgb=new byte[w*h*3];long begin=System.nanoTime();int frame=0;
                while(running){
                    synchronized(phone.getLcdFrontbuffer()){lcd.getRGB(0,0,w,h,pixels,0,w);}
                    for(int i=0;i<pixels.length;i++){int c=pixels[i];rgb[3*i]=(byte)(c>>16);rgb[3*i+1]=(byte)(c>>8);rgb[3*i+2]=(byte)c;}
                    sink.write(rgb);
                    long wait=begin+(++frame)*1000000000L/15-System.nanoTime();
                    if(wait>0)Thread.sleep(wait/1000000L,(int)(wait%1000000L));
                }
            }catch(Throwable e){problem=e;}
            try{if(ffmpeg.waitFor()!=0)problem=new Exception("FFmpeg capture failed");}catch(InterruptedException e){problem=e;}
        });
        writer.start();
    }
    void stop()throws Exception{running=false;writer.join();if(problem!=null)throw new Exception(problem);}
}
