import java.io.File;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import javax.imageio.ImageIO;
import org.recompile.mobile.*;

/** Desktop-only integration run, using a FreeJ2ME-Plus runtime and a private RMS directory. */
public class EmulatorCheck {
    static MobilePlatform phone;
    static File shots;
    static PlayCapture capture;
    static void hold(char key,int ms)throws Exception{
        MobilePlatform.keyPressed(key);Thread.sleep(ms);MobilePlatform.keyReleased(key);Thread.sleep(100);
    }
    static Object canvas,game;
    static Object value(Object o,String n)throws Exception{
        Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void key(char c)throws Exception{
        MobilePlatform.keyPressed(c);Thread.sleep(115);MobilePlatform.keyReleased(c);Thread.sleep(125);
    }
    static void shot(String name)throws Exception{
        Thread.sleep(180);check(value(canvas,"error")==null,"render: "+value(canvas,"error"));
        BufferedImage im=phone.getLcdFrontbuffer().getCanvas();
        ImageIO.write(im,"png",new File(shots,name+".png"));
    }
    static void at(int x,int z)throws Exception{
        // Move to the fixture location through the same movement and turning simulation used in play.
        synchronized(canvas){
            Method tick=game.getClass().getDeclaredMethod("tick",float.class,int.class,int.class);tick.setAccessible(true);
            for(int i=0;i<5000;i++){
                float ax=(x-2048)*.25f-(Float)value(game,"x"),az=(z+2048)*.25f-(Float)value(game,"z");
                if(Math.hypot(ax,az)<.27)return;
                double angle=Math.toDegrees(Math.atan2(ax,az))-(Float)value(game,"yaw");
                while(angle>180)angle-=360;while(angle< -180)angle+=360;
                int keys=Math.abs(angle)>4?(angle>0?4:8):1;
                tick.invoke(game,.04f,keys,0);
            }
        }
        throw new AssertionError("destination unreachable "+x+" "+z);
    }
    static void fight(int from)throws Exception{
        for(int i=0;i<75 && stage()==from;i++){key('5');if(i%8==7)key('1');}
        check(stage()==from+1,"battle "+from+" now "+stage());
    }
    static int stage()throws Exception{return (Integer)value(game,"stage");}
    static void pages()throws Exception{while(value(game,"pages")!=null)key('5');}
    static void run(String[] args)throws Exception{
        shots=new File(args[1]);shots.mkdirs();phone=new MobilePlatform(Integer.parseInt(args[2]),Integer.parseInt(args[3]));
        Mobile.setPlatform(phone,()->{});Mobile.maskIndex=0;phone.dataPath=new File("rms").getAbsolutePath()+File.separator;
        check(phone.load(new File(args[0]).toURI().toString()),"load jar");phone.loader.start();
        for(int i=0;i<120;i++){
            Thread.sleep(100);
            if(Mobile.getDisplay()!=null&&(canvas=Mobile.getDisplay().getCurrent())!=null && value(canvas,"error")!=null)
                throw new AssertionError(value(canvas,"error"));
            if(canvas!=null && value(canvas,"scene")!=null)break;
        }
        if(Boolean.getBoolean("playCapture"))capture=new PlayCapture(phone,shots);
        game=value(canvas,"s");shot("01-title");key('5');shot("02-prologue");
        if(Boolean.getBoolean("fullOpening")){
            Thread.sleep(22400);shot("02b-confrontation");
            for(int i=0;i<120 && (Integer)value(canvas,"mode")==7;i++)Thread.sleep(500);
            check((Integer)value(canvas,"mode")==1,"opening naturally reaches selection");
        }else key('5');
        shot("03-select");
        if(args.length<5)key('6');key('5');shot("04-separation");key('5');shot("05-beach");
        if(capture!=null){
            capture.start("01-coast");Thread.sleep(280);hold('6',330);hold('2',850);hold('4',520);
            hold('2',520);key('3');hold('2',450);key('7');hold('2',700);key('7');capture.stop();
        }
        check((Integer)value(game,"hero")==(args.length<5?1:0),"selected twin");at(2741,-1707);key('5');check(stage()==1,"meet Paimon");shot("06-paimon");
        if(capture!=null){capture.start("02-paimon");Thread.sleep(700);key('5');Thread.sleep(1250);key('5');Thread.sleep(900);capture.stop();}
        pages();
        at(2727,-1659);key('5');pages();at(2601,-1420);key('5');pages();at(2568,-1336);key('5');pages();
        check(stage()==4,"statue");key('0');shot("07-map");key('0');
        at(2578,-1318);key('1');key('9');fight(4);pages();check(stage()==5,"wind tutorial");
        at(2511,-1240);key('5');shot("08-forest");pages();at(2499,-1229);key('5');pages();
        at(2471,-1185);key('5');shot("09-knight");
        if(capture!=null){capture.start("03-amber");Thread.sleep(850);key('5');Thread.sleep(1300);key('5');Thread.sleep(850);capture.stop();}
        pages();check(stage()==8,"meet Amber");at(2470,-1172);fight(8);
        at(2425,-1133);fight(9);at(2396,-1080);shot("10-camp");
        if(capture!=null){capture.start("04-bow");for(int i=0;i<5;i++){key('5');Thread.sleep(230);}capture.stop();}
        fight(10);
        at(2392,-1061);key('5');pages();at(2248,-979);key('5');Thread.sleep(400);shot("11-cityfilm");key('5');pages();
        at(2240,-924);at(2240,-908);at(2252,-892);at(2320,-772);shot("12-terrace");key('5');pages();key('3');Thread.sleep(100);key('3');
        check((Boolean)value(game,"launched"),"glider unlocked and opened");
        Thread.sleep(1800);at(2276,-844);at(2248,-876);if((Boolean)value(game,"glide"))key('3');Thread.sleep(1450);shot("13-plaza");
        if(capture!=null){capture.start("05-city");Thread.sleep(350);hold('4',640);hold('2',240);hold('8',240);hold('6',780);Thread.sleep(200);capture.stop();at(2248,-876);}
        key('5');pages();check(stage()==15,"city chapter");
        at(2248,-896);at(2244,-900);at(2240,-916);at(2240,-928);at(2260,-960);at(2448,-1128);
        at(2450,-1144);key('5');check((Integer)value(game,"runes")==0,"wrong rune reset");
        at(2416,-1148);key('5');at(2428,-1114);key('5');at(2450,-1144);key('5');at(2390,-1100);key('5');
        check((Boolean)value(game,"opened"),"optional puzzle chest");
        key('#');key('8');key('8');key('8');key('5');key('5');
        check(stage()==15 && (Integer)value(canvas,"mode")==2 && (Boolean)value(game,"opened"),"continue from RMS");
        System.out.println("PASS: title, selection, travel, dialogue, combat, puzzle, chest, city and RMS continue.");
    }
    public static void main(String[] args){
        try{run(args);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}
    }
}
