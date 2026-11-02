import javax.microedition.lcdui.*;

final class Field extends Canvas implements Runnable {
    static final int TITLE=0,PICK=1,PLAY=2,CHART=3,PAUSE=4,HELP=5,OVERWRITE=6,FILM=7;
    static final int NAVY=0x203947,DEEP=0x152d3b,PARCH=0xf3e9cf,GOLD=0xe5c580,TEAL=0x71d2be;
    final Windward app;
    Land land;Journey s;Scenery scene;Ink ink;
    Image boy,girl,paimon,amber,chart,logo,heavens,pickBoy,pickGirl,fishing;
    final Cinema cinema=new Cinema();int reel;
    int mode=TITLE,cursor,pick,held,pressed,touchKey;
    boolean touchMode,hasSave,inWorld,paused,done,started;
    String error;
    float time;
    Field(Windward midlet){app=midlet;setFullScreenMode(true);}
    private Image img(String name)throws java.io.IOException{return Image.createImage("/"+name+".png");}
    private void init()throws Exception{
        ink=new Ink();boy=img("aether");girl=img("lumine");paimon=img("paimon");amber=img("amber");heavens=img("heavens");fishing=img("fishing");pickBoy=img("pickboy");pickGirl=img("pickgirl");chart=img("chart");logo=img("title");
        land=new Land();s=new Journey(land);hasSave=s.load();if(!hasSave)s.reset(0);
        scene=new Scenery(land);
    }
    synchronized void resumeGame(){paused=false;if(mode==FILM)cinema.resume();if(!started){started=true;new Thread(this).start();}}
    synchronized void suspendGame(){paused=true;cinema.pause();held=pressed=touchKey=0;if(inWorld)s.save();}
    synchronized void stopGame(){done=true;suspendGame();cinema.close();}
    protected synchronized void hideNotify(){held=pressed=touchKey=0;if(inWorld && s!=null)s.save();}
    public void run(){
        try{init();}catch(Throwable e){error="M3G / "+e.toString();}
        long last=System.currentTimeMillis();
        while(true){
            long now=System.currentTimeMillis();float dt=Land.clamp((now-last)/1000f,.001f,.1f);last=now;
            synchronized(this){
                if(done)return;
                if(!paused && error==null){time+=dt;step(dt,pressed);pressed=0;}
            }
            repaint();serviceRepaints();
            try{Thread.sleep(Math.max(5,40-(System.currentTimeMillis()-now)));}catch(InterruptedException ignored){}
        }
    }
    void mode(int next){mode=next;held=pressed=touchKey=0;cursor=0;}
    private void step(float dt,int keys){
        boolean ok=(keys&Journey.ACT)!=0,back=(keys&Journey.MENU)!=0;
        boolean up=(keys&(Journey.FORWARD|Journey.LEFT))!=0,down=(keys&(Journey.BACK|Journey.RIGHT))!=0;
        if(mode==FILM){
            try{if(ok||back||!cinema.tick(dt))finishFilm();}catch(java.io.IOException ex){error=ex.toString();}
        }else if(mode==PLAY){
            if(s.cityFilm){s.cityFilm=false;film(2);return;}
            if(back){s.save();mode(PAUSE);}else if((keys&Journey.MAP)!=0 && s.pages==null)mode(CHART);
            else s.tick(dt,held,keys);
        }else if(mode==TITLE){
            if(hasSave&&(up||down))cursor=1-cursor;
            if(ok){if(hasSave && cursor==0 && s.load()){inWorld=true;mode(PLAY);}else if(hasSave)mode(OVERWRITE);else film(0);}
        }else if(mode==PICK){
            if(up||down)pick=1-pick;
            if(back)mode(TITLE);
            else if(ok)film(1);
        }else if(mode==OVERWRITE){if(back)mode(TITLE);else if(ok)film(0);}
        else if(mode==CHART){if(back||ok||(keys&Journey.MAP)!=0)mode(PLAY);
            else if((keys&Journey.SKILL)!=0 && s.stage>Journey.STATUE){s.recover();s.note("已传送到星落湖神像");s.save();mode(PLAY);}}
        else if(mode==PAUSE){
            if(up)cursor=(cursor+4)%5;if(down)cursor=(cursor+1)%5;
            if(back)mode(PLAY);else if(ok){
                if(cursor==0)mode(PLAY);else if(cursor==1)mode(CHART);else if(cursor==2)mode(HELP);
                else if(cursor==3){s.save();if(s.saveOK){hasSave=true;inWorld=false;mode(TITLE);}}else app.leave();
            }
        }else if(mode==HELP && (back||ok))mode(PAUSE);
    }
    private void film(int which){
        reel=which;
        try{cinema.open(which==0?"arrival":which==2?"mond":pick==0?"brother":"sister");mode(FILM);}
        catch(java.io.IOException ex){error=ex.toString();}
    }
    private void finishFilm(){cinema.close();if(reel==0)mode(PICK);else if(reel==1)startNew();else mode(PLAY);}
    private void startNew(){s.reset(pick);s.save();hasSave=s.saveOK;inWorld=true;mode(PLAY);
        s.note("海风送来了一个陌生的声音");}
    private int key(int code){
        switch(code){
            case KEY_NUM2:return Journey.FORWARD;case KEY_NUM8:return Journey.BACK;
            case KEY_NUM4:return Journey.LEFT;case KEY_NUM6:return Journey.RIGHT;case KEY_NUM5:return Journey.ACT;
            case KEY_NUM9:return Journey.SCAN;case KEY_STAR:return Journey.START;case KEY_NUM1:return Journey.SKILL;case KEY_NUM3:return Journey.JUMP;
            case KEY_NUM7:return Journey.SPRINT;case KEY_NUM0:case -6:return Journey.MAP;
            case KEY_POUND:case -7:return Journey.MENU;
        }
        int a;try{a=getGameAction(code);}catch(IllegalArgumentException ex){return 0;}
        return a==UP?Journey.FORWARD:a==DOWN?Journey.BACK:a==LEFT?Journey.LEFT:a==RIGHT?Journey.RIGHT:a==FIRE?Journey.ACT:0;
    }
    protected synchronized void keyPressed(int code){int k=key(code);pressed|=k&~held;held|=k;}
    protected synchronized void keyReleased(int code){held&=~key(code);}
    private static void rect(Graphics g,int c,int x,int y,int w,int h){g.setColor(c);g.fillRect(x,y,w,h);}
    private static void diamond(Graphics g,int c,int x,int y,int r){
        g.setColor(c);g.fillTriangle(x-r,y,x,y-r,x+r,y);g.fillTriangle(x-r,y,x+r,y,x,y+r);
    }
    private void bar(Graphics g,int x,int y,int w,int value,int max,int c){
        rect(g,0x425562,x,y,w,4);rect(g,c,x,y,w*value/max,4);
    }
    protected synchronized void paint(Graphics g){
        int w=getWidth(),h=getHeight();
        if(error!=null||scene==null){rect(g,DEEP,0,0,w,h);g.setColor(0xffffff);g.drawString(error==null?"Loading Mondstadt ...":error,4,h/2,Graphics.TOP|Graphics.LEFT);return;}
        if(mode==PLAY)
            try{scene.draw(g,w,h,s,time,false);}catch(Throwable e){error=e.toString();return;}
        else rect(g,DEEP,0,0,w,h);
        if(mode==FILM)movie(g,w,h);else if(mode==TITLE)title(g,w,h);
        else if(mode==PICK||mode==OVERWRITE)select(g,w,h);
        else if(mode==PLAY){if(s.pages!=null)dialog(g,w,h);else hud(g,w,h);}
        else if(mode==CHART)map(g,w,h);
        else if(mode==PAUSE||mode==HELP)menu(g,w,h);
    }
    private void movie(Graphics g,int w,int h){
        int fh=Math.min(w*9/16,h-70),fw=fh*16/9,top=(h-fh)/2-5;
        rect(g,DEEP,0,0,w,h);ink.text(g,reel==2?"序章   自由之都":"序幕   流浪者的足迹",10,7,1);
        cinema.fit(g,cinema.frame,(w-fw)/2,top,fw,fh);rect(g,GOLD,(w-fw)/2,top+fh,fw,1);
        String caption=cinema.subtitle();
        if(reel==2 && caption.length()==0)caption="风与牧歌的城邦，蒙德。";
        ink.wrap(g,caption,10,top+fh+6,w-20,0);ink.text(g,"5 跳过",w-56,h-19,1);
    }
    private void title(Graphics g,int w,int h){
        rect(g,DEEP,0,0,w,24);ink.text(g,"TEYVAT   /   01",9,4,1);ink.text(g,"MONDSTADT",w-74,4,0);
        int sceneH=h-(hasSave?99:78)-24;
        cinema.fit(g,heavens,(w-Math.max(w,sceneH*16/9))/2,24,Math.max(w,sceneH*16/9),sceneH);
        int top=Math.max(26,h/6-8);
        g.drawImage(logo,w/2,top,Graphics.TOP|Graphics.HCENTER);
        int foot=hasSave?99:78;rect(g,NAVY,0,h-foot,w,foot);rect(g,GOLD,0,h-foot,w,1);
        ink.center(g,"序 章   捕 风 的 异 乡 人",w/2,h-foot+4,1);
        if(hasSave){button(g,24,h-72,w-48,"继续旅程",cursor==0);button(g,24,h-49,w-48,"新的旅程",cursor==1);}
        else button(g,24,h-51,w-48,"踏入蒙德",true);
        ink.center(g,"5 确认   ·   离线冒险",w/2,h-20,0);
    }
    private void button(Graphics g,int x,int y,int w,String text,boolean active){
        rect(g,active?PARCH:DEEP,x,y,w,21);diamond(g,active?0x90805e:0xb0a582,x+12,y+10,3);ink.center(g,text,x+w/2,y+2,active?2:0);
    }
    private void select(Graphics g,int w,int h){
        rect(g,DEEP,0,0,w,46);ink.text(g,"PROLOGUE   /   00",10,3,1);ink.text(g,"选择你的旅行者",10,23,0);
        int ph=Math.min(144,Math.max(82,h-125));
        int cw=Math.min((w-22)/2,102),left=(w-2*cw-6)/2,y=Math.max(48,(h-ph-40)/2);
        for(int i=0;i<2;i++){
            int x=left+i*(cw+6);rect(g,i==pick?GOLD:0x577383,x,y,cw,ph+20);rect(g,i==pick?0x657b81:NAVY,x+2,y+2,cw-4,ph+16);
            Image portrait=i==0?pickBoy:pickGirl;g.setClip(x+2,y+2,cw-4,ph-2);g.drawImage(portrait,x+cw/2,y+1,Graphics.TOP|Graphics.HCENTER);g.setClip(0,0,w,h);
            rect(g,i==pick?PARCH:DEEP,x+2,y+ph-2,cw-4,21);ink.center(g,i==0?"空  AETHER":"荧  LUMINE",x+cw/2,y+ph,i==pick?2:0);
        }
        rect(g,NAVY,0,h-55,w,55);ink.center(g,"请选择一位旅行者。",w/2,h-52,0);
        button(g,15,h-28,w-30,"4 / 6 切换     5 启程",true);
        if(mode==OVERWRITE){rect(g,DEEP,5,h/2-28,w-10,57);ink.center(g,"开始新旅程并覆盖存档？",w/2,h/2-20,1);ink.center(g,"5 确认     # 返回",w/2,h/2+3,0);}
    }
    private String region(){
        if(s.z<133)return "鹰翔海滩";if(s.z<207)return "星落湖";if(s.z<259)return "低语森林";if(s.x<99 && s.z>268 && s.z<364)return "蒙德城";return "蒙德城郊";
    }
    private void hud(Graphics g,int w,int h){
        int tx=Math.min(w-50,213);rect(g,NAVY,4,4,tx,35);
        ink.text(g,region()+"  /  "+(s.stage==Journey.END?"游记":"序章"),9,5,1);ink.text(g,Journey.TASK[s.stage],9,21,0);
        if(s.stage<Journey.END){
            int target=s.target();float vx=Journey.ox(target)-s.x,vz=Journey.oz(target)-s.z;
            // At the ruins, orient toward the group. The inscription supplies the order.

            float dist=(float)Math.sqrt(vx*vx+vz*vz);float a=s.yaw*.017453293f;
            float right=-vx*(float)Math.cos(a)+vz*(float)Math.sin(a);
            float fw=vx*(float)Math.sin(a)+vz*(float)Math.cos(a);
            int cx=w-23,cy=20;g.setColor(DEEP);g.fillArc(cx-18,cy-17,36,34,0,360);
            int ax=(int)(right/Math.max(1,dist)*12),ay=(int)(-fw/Math.max(1,dist)*12);
            g.setColor(GOLD);g.drawLine(cx,cy,cx+ax,cy+ay);diamond(g,GOLD,cx+ax,cy+ay,3);
            rect(g,NAVY,w-47,38,46,16);ink.center(g,((int)(dist*4))+"m",w-24,38,0);
        }
        for(int i=0;i<s.ehp.length;i++)if(s.activeEnemy(i) && s.distance(s.ex[i],s.ez[i])<12){
            String foe=i<3?"火史莱姆":i==3?"风史莱姆":"丘丘人";int bw=Math.min(104,w-16);
            rect(g,NAVY,(w-bw)/2,50,bw,22);ink.center(g,foe,w/2,51,0);
            bar(g,(w-bw)/2+5,67,bw-10,s.ehp[i],i<3?28:i==3?15:i==8?80:48,0xc97561);break;
        }
        int by=h-21;rect(g,DEEP,0,by,w,21);ink.text(g,(s.asAmber?"安柏":s.hero==0?"空":"荧")+" "+s.hp,4,by+2,0);
        bar(g,49,by+5,Math.min(51,w/5),s.hp,s.healthMax(),TEAL);bar(g,49,by+12,Math.min(51,w/5),(int)s.stamina,100,GOLD);
        ink.text(g,"0 地图   # 菜单",w-110,by+2,0);
        if(s.pages==null){
            int near=s.nearby();String prompt=null;
            if(near>=0)prompt="5 "+(near>=Journey.BERRY?"采集落落莓":near>=Journey.WISP?"拾取风之微粒":Journey.OBJECTS[near]);
            if(s.noticeTime>0)prompt=s.notice;
            if(!s.saveOK)prompt="存档写入失败";
            if(prompt!=null){int pw=ink.width(prompt)+12;rect(g,NAVY,(w-pw)/2,h-45,pw,20);ink.center(g,prompt,w/2,h-43,1);}
            if(touchMode)touchBar(g,w,h);
            else if(prompt==null){ink.center(g,s.asAmber?"5 射箭    * 切换旅行者":s.stage>Journey.STATUE?(s.skillCd>0?"风涡冷却中":"1 风涡    3 跳跃    5 挥剑"):"2 前进   4 / 6 转向   7 疾跑",w/2,h-42,0);}
        }
    }
    private void dialog(Graphics g,int w,int h){
        int rows=(ink.width(s.message)+w-21)/(w-20)+1,dh=Math.min(h-45,Math.max(84,rows*16+45)),y=h-dh;
        rect(g,DEEP,0,y,w,dh);rect(g,GOLD,0,y,w,2);ink.text(g,s.speaker,10,y+5,1);
        ink.wrap(g,s.message,10,y+26,w-20,0);ink.text(g,"5 继续",w-58,h-18,1);
        if(s.stage==Journey.ANCHOR && s.dialogIndex==1){int fh=w*9/32;cinema.fit(g,fishing,0,Math.max(39,y-fh-1),w,fh);return;}
        if(s.speaker.equals("林间相会"))return;
        Image face=s.speaker.equals("派蒙")?paimon:s.speaker.equals("安柏")?amber:s.hero==0?boy:girl;
        g.drawImage(face,w-4,y,Graphics.BOTTOM|Graphics.RIGHT);
    }
    private void map(Graphics g,int w,int h){
        int ch=Math.min(224,h-53),cw=112,mx=7,my=26;
        int crop=(int)Land.clamp(s.z/512*224-ch/2,0,224-ch);
        ink.text(g,"蒙德  /  探索手记",8,4,1);
        g.drawRegion(chart,0,crop,cw,ch,0,mx,my,Graphics.TOP|Graphics.LEFT);
        for(int i=0;i<Journey.OBJECTS.length;i++){if(!s.present(i))continue;int x=mx+(int)(Journey.ox(i)/256*cw),y=my+(int)(Journey.oz(i)/512*224)-crop;
            if(y>=my&&y<my+ch)diamond(g,i==s.target()?GOLD:0xf2eaca,x,y,i==s.target()?4:2);}
        int x=mx+(int)(s.x/256*cw),y=my+(int)(s.z/512*224)-crop;diamond(g,DEEP,x,y,5);diamond(g,TEAL,x,y,3);
        int rx=mx+cw+7,rw=w-rx-3;
        ink.text(g,"当前目标",rx,my,1);ink.wrap(g,Journey.TASK[s.stage],rx,my+20,rw,0);
        ink.text(g,"风之微粒",rx,my+60,1);ink.text(g,Journey.count(s.wisps)+" / 6",rx,my+78,0);
        if(h>190){ink.text(g,"战斗记录",rx,my+102,1);ink.text(g,s.kills+" 击败",rx,my+120,0);}
        if(h>240)ink.wrap(g,Journey.CHAPTER[s.stage],rx,my+148,rw,1);
        ink.center(g,s.stage>Journey.STATUE?"1 神像传送    0 返回":"0 返回旅程",w/2,h-19,0);
    }
    private void menu(Graphics g,int w,int h){
        ink.text(g,"FIELD JOURNAL",10,6,1);rect(g,GOLD,10,27,w-20,1);
        if(mode==HELP){
            String[] tips={"2 / 8   前进、后退","4 / 6   向左、向右转","5   交谈、拾取、挥剑","1 风涡  9 爆发  * 换人","3   跳跃；空中再按滑翔","7   切换疾跑","0   地图与神像传送","#   暂停与保存"};
            for(int i=0;i<tips.length;i++)ink.text(g,tips[i],11,32+i*18,0);
            ink.center(g,"5 返回",w/2,h-20,1);
        }else{
            String[] items={"返回旅程","蒙德地图","操作手册","保存并回标题","退出游戏"};
            for(int i=0;i<items.length;i++)button(g,18,44+i*25,w-36,items[i],cursor==i);
            ink.center(g,s.saveOK?"旅程已记录":"存档写入失败",w/2,h-22,1);
        }
    }
    private static final int[] TOUCH={Journey.LEFT,Journey.FORWARD,Journey.RIGHT,Journey.ACT,Journey.SKILL,Journey.JUMP,Journey.SPRINT,Journey.BACK};
    private static final String[] LABEL={"转左","前进","转右","行动","风","跃","跑","退"};
    private void touchBar(Graphics g,int w,int h){
        if(s.stage>=Journey.SLIMES){rect(g,NAVY,w-54,56,54,20);ink.text(g,"爆  换",w-51,58,0);}
        int width=w/8;for(int i=0;i<8;i++){rect(g,(held&TOUCH[i])!=0?0x688979:NAVY,i*width,h-66,width-1,18);ink.center(g,LABEL[i],i*width+width/2,h-65,0);}
    }
    protected synchronized void pointerPressed(int x,int y){
        int w=getWidth(),h=getHeight();if(scene==null)return;touchMode=true;
        if(mode==PLAY && s.pages!=null){pressed|=Journey.ACT;return;}
        if(mode==PLAY){
            if(y>=56 && y<82 && x>w-54){pressed|=x>w-27?Journey.START:Journey.SCAN;return;}
            if(y>=h-21){pressed|=x>w*3/4?Journey.MENU:Journey.MAP;return;}
            if(y>=h-68&&y<h-45){touchKey=TOUCH[Math.min(7,x/(Math.max(1,w/8)))];held|=touchKey;pressed|=touchKey;}
            else if(y>=h-46)pressed|=Journey.ACT;
        }else if(mode==FILM){pressed|=Journey.ACT;}else if(mode==TITLE){if(hasSave)cursor=y<h-51?0:1;pressed|=Journey.ACT;}
        else if(mode==PICK){if(y<h-46)pick=x<w/2?0:1;else pressed|=Journey.ACT;}
        else if(mode==PAUSE){if(y>=44 && y<169){cursor=(y-44)/25;pressed|=Journey.ACT;}}
        else if(mode==CHART){pressed|=s.stage>Journey.STATUE&&x<w/2&&y>=h-25?Journey.SKILL:Journey.MAP;}
        else if(mode==OVERWRITE){pressed|=x<w/2?Journey.ACT:Journey.MENU;}
        else pressed|=Journey.ACT;
    }
    protected synchronized void pointerReleased(int x,int y){held&=~touchKey;touchKey=0;}
}
