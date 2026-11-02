import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Display;

public final class Windward extends MIDlet {
    private Field field;
    protected void startApp(){
        if(field==null)field=new Field(this);
        Display.getDisplay(this).setCurrent(field);field.resumeGame();
    }
    protected void pauseApp(){if(field!=null)field.suspendGame();}
    protected void destroyApp(boolean unconditional){if(field!=null)field.stopGame();}
    void leave(){destroyApp(true);notifyDestroyed();}
}
