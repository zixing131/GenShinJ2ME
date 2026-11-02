import java.io.*;
import javax.microedition.lcdui.*;

/** A small bitmap alphabet keeps Chinese labels readable on emulators with Latin-only fonts. */
final class Ink {
    final String glyphs;
    final Image[] sheet=new Image[3];
    static final int PAPER=0,GOLD=1,DARK=2;
    Ink() throws IOException{
        DataInputStream in=new DataInputStream(getClass().getResourceAsStream("/glyphs.bin"));
        try{glyphs=in.readUTF();}finally{in.close();}
        for(int i=0;i<3;i++)sheet[i]=Image.createImage("/ink"+i+".png");
    }
    int advance(char c){return c<128?7:13;}
    int width(String s){int w=0;for(int i=0;i<s.length();i++)w+=advance(s.charAt(i));return w;}
    void text(Graphics g,String s,int x,int y,int style){
        for(int i=0;i<s.length();i++){
            char ch=s.charAt(i);int index=glyphs.indexOf(ch);
            if(index>=0)g.drawRegion(sheet[style],index%32*14,index/32*16,14,16,0,x,y,Graphics.TOP|Graphics.LEFT);
            x+=advance(ch);
        }
    }
    void center(Graphics g,String s,int x,int y,int style){text(g,s,x-width(s)/2,y,style);}
    void wrap(Graphics g,String s,int x,int y,int w,int style){
        int at=x;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);if(at+advance(c)>x+w){at=x;y+=16;}
            text(g,s.substring(i,i+1),at,y,style);at+=advance(c);
        }
    }
}
