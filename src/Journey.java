import java.io.*;
import javax.microedition.rms.*;

final class Journey {
    static final int FORWARD=1,BACK=2,LEFT=4,RIGHT=8,ACT=16,SKILL=32,JUMP=64,SPRINT=128,MAP=256,MENU=512,SCAN=1024,START=2048;
    static final int PAIMON=0,ANCHOR=1,VISTA=2,STATUE=3,SLIMES=4,FOREST=5,TEAR=6,AMBER=7,
        AIR_SLIME=8,SCOUT=9,CAMP=10,REPORT=11,GATE=12,TERRACE=13,FOUNTAIN=14,RUNE=15,CHEST=18,END=15;
    static final int WISP=32,BERRY=48;
    static final String[] OBJECTS={"派蒙","传送锚点","眺望星落湖","七天神像","火史莱姆","林中异动","赤色结晶","侦察骑士","空中的史莱姆","游荡的丘丘人","丘丘人营地","安柏","蒙德城门","城中高台","喷泉广场","叶之碑","羽之碑","星之碑","封印宝箱"};
    static final int[] OX={2741,2727,2601,2568,2578,2511,2499,2471,2470,2425,2396,2392,2248,2320,2248,2416,2450,2428,2390};
    static final int[] OZ={-1707,-1659,-1420,-1336,-1318,-1240,-1229,-1185,-1172,-1133,-1080,-1061,-979,-772,-876,-1148,-1144,-1114,-1100};
    static final int[] ORDER={0,2,1};
    static final String[] TASK={"与海滩上的派蒙交谈","跟随派蒙，开启锚点","到高处眺望星落湖","游向湖心的七天神像","用风元素击败史莱姆","前往低语森林深处","调查赤色结晶","与拦路的少女交谈","用安柏射落风史莱姆","消灭游荡的丘丘人","清理丘丘人营地","与安柏交谈","随安柏前往蒙德城","到安柏指示的高处","张开风之翼，飞向广场","自由探索蒙德"};
    static final String[] CHAPTER={"流浪者的足迹","流浪者的足迹","鸟瞰风物","鸟瞰风物","异常的权柄","林间相会","林间相会","随风而来的骑士","随风而来的骑士","与轻风同行","与轻风同行","与轻风同行","自由之都","自由之都","自由之都","蒙德游记"};
    static final int[] WX={2680,2584,2488,2348,2148,2296};
    static final int[] WZ={-1560,-1342,-1208,-1136,-882,-768};
    static final int[] BX={2715,2592,2502,2460,2410,2259};
    static final int[] BZ={-1668,-1322,-1218,-1182,-1090,-977};
    final Land land;
    float x,z,yaw,air,vy,stamina,attackCd,skillCd,flash,wind,noticeTime,saveTime;
    int hero,stage,runes,wisps,berries,hp,kills;
    boolean sprint,glide,moving,hit,opened,asAmber,cityFilm,launched,saveOK=true;
    float shot,charge,burstCd;
    final float[] ex=new float[9],ez=new float[9],enemyDelay=new float[9];
    final int[] ehp=new int[9];
    String speaker="",message="",notice="";
    int dialogIndex;
    String[] pages;
    Journey(Land l){land=l;}
    static float ox(int i){return Land.x(OX[i]);}
    static float oz(int i){return Land.z(OZ[i]);}
    static int enemySite(int i){return i<3?SLIMES:i==3?AIR_SLIME:i==4?SCOUT:i<8?CAMP:FOUNTAIN;}
    static float homeX(int i){if(i==8)return Land.x(2510);return ox(enemySite(i))+(i<3?i-1:i>=5&&i<8?i-6:0)*2.1f;}
    static float homeZ(int i){if(i==8)return Land.z(-820);return oz(enemySite(i))+(i%2)*1.4f;}
    void reset(int pick){
        hero=pick;stage=runes=wisps=berries=kills=0;hp=100;x=Land.x(2747);z=Land.z(-1719);
        yaw=-27;air=vy=attackCd=skillCd=wind=flash=0;stamina=100;glide=sprint=moving=opened=asAmber=cityFilm=launched=false;shot=charge=burstCd=0;pages=null;noticeTime=0;
        for(int i=0;i<ehp.length;i++){ex[i]=homeX(i);ez[i]=homeZ(i);ehp[i]=i<3?28:i==3?15:i==8?80:48;enemyDelay[i]=1;}
    }
    int target(){return stage<END?stage:FOUNTAIN;}
    boolean present(int i){
        if(i>=RUNE)return stage>=SLIMES;
        if(i==STATUE||i==ANCHOR)return true;
        return stage==i;
    }
    static float d2(float x,float z,float bx,float bz){float dx=x-bx,dz=z-bz;return dx*dx+dz*dz;}
    float distance(float a,float b){return (float)Math.sqrt(d2(x,z,a,b));}
    static int count(int bits){int n=0;while(bits!=0){n+=bits&1;bits>>>=1;}return n;}
    int healthMax(){return 100+count(wisps)*5;}
    void say(String who,String[] lines){speaker=who;pages=lines;dialogIndex=0;showPage();}
    private void showPage(){
        message=pages[dialogIndex];int split=message.indexOf('|');
        if(split>=0){speaker=message.substring(0,split);message=message.substring(split+1);}
    }
    void advance(){if(++dialogIndex==pages.length)pages=null;else showPage();}
    void note(String s){notice=s;noticeTime=2.4f;}
    int nearby(){
        for(int i=0;i<OBJECTS.length;i++){
            if(!present(i)||i==SLIMES||i==AIR_SLIME||i==SCOUT||i==CAMP)continue;
            if(distance(ox(i),oz(i))<(i==FOREST?6f:3.4f))return i;
        }
        for(int i=0;i<WX.length;i++)if((wisps&(1<<i))==0 && distance(Land.x(WX[i]),Land.z(WZ[i]))<3.3f)return WISP+i;
        for(int i=0;i<BX.length;i++)if((berries&(1<<i))==0 && distance(Land.x(BX[i]),Land.z(BZ[i]))<3.3f)return BERRY+i;
        return -1;
    }
    void interact(int i){
        if(i<0){attack(false);return;}
        if(i>=BERRY){berries|=1<<(i-BERRY);hp=Math.min(healthMax(),hp+28);note("采到落落莓，恢复生命");save();return;}
        if(i>=WISP){wisps|=1<<(i-WISP);hp+=5;note("拾取风之微粒，生命上限提高");save();return;}
        if(i>=RUNE){
            if(i==CHEST){if(runes==3 && !opened){opened=true;hp=healthMax();note("获得宝箱，恢复生命");}else note(opened?"宝箱已经打开":"碑文：叶离枝，星引路，羽归巢");}
            else if(runes==3)note("三座碑正与风共鸣");
            else if(i-RUNE==ORDER[runes]){runes++;wind=.65f;note(runes==3?"封印解开了":"风碑共鸣 "+runes+"/3");}
            else{runes=0;note("碑文：叶离枝，星引路，羽归巢");}
            save();return;
        }
        if(i!=stage){if(i==STATUE && stage>STATUE){hp=healthMax();note("神像的微风恢复了生命");}
            else note("沿着派蒙指引的路前进");return;}
        switch(i){
            case PAIMON:say("派蒙",Prologue.BEACH);break;
            case ANCHOR:say("派蒙",Prologue.ANCHOR);break;
            case VISTA:say("派蒙",Prologue.VISTA);break;
            case STATUE:hp=healthMax();say("派蒙",Prologue.STATUE);break;
            case FOREST:say("林间相会",new String[]{"树影下，绿色衣装的少年伸出手，轻声安抚面前的巨龙。","旅行者身上的风元素忽然亮起。龙受惊怒吼，振翼而去，少年也消失在林中。"});break;
            case TEAR:say("派蒙",Prologue.TEAR);break;
            case AMBER:say("安柏",Prologue.KNIGHT);asAmber=true;break;
            case REPORT:say("安柏",Prologue.REPORT);asAmber=false;break;
            case GATE:cityFilm=true;say("安柏",Prologue.GATE);break;
            case TERRACE:say("安柏",Prologue.TERRACE);asAmber=false;break;
            case FOUNTAIN:if(!launched){note("先在高处起跳，空中按 3 展翼");return;}
                say("派蒙",new String[]{"平安落地！从海边一路走来，我们终于到了风的城邦。","本次试玩在这里结束。你可以继续在蒙德自由探索。"});break;
            default:return;
        }
        stage++;
        if(stage==SLIMES)note("1 风涡   按住蓄力   9 爆发");
        if(stage==AIR_SLIME)note("按 5 射落风史莱姆");
        if(stage==FOUNTAIN)note("3 跳跃，空中再按 3 展翼");
        save();
    }
    void attack(boolean skill){strike(skill?1:0);}
    void strike(int type){
        if(asAmber && type>0){note("安柏：按 5 射箭，* 切换旅行者");return;}
        if(type>0 && stage<SLIMES){note("先与七天神像共鸣");return;}
        if(type==2?burstCd>0:type==1?skillCd>0:attackCd>0)return;
        int amount=type==2?68:type==1?(charge>.45f?55:34):asAmber?24:18;
        if(type==2){burstCd=8;wind=1.0f;}else if(type==1){skillCd=2.1f;wind=.7f;}else{attackCd=.38f;flash=.26f;if(asAmber)shot=.32f;}
        int nearest=-1;float best=type==2?11:type==1?7:asAmber?18:3.6f;
        for(int i=0;i<ehp.length;i++)if(activeEnemy(i)){
            float d=distance(ex[i],ez[i]);
            if(d<best){if(type>0 && i!=3)damage(i,amount);else if(i!=3||asAmber){best=d;nearest=i;}}
        }
        if(nearest>=0)damage(nearest,amount);
        if(stage==SLIMES && ehp[0]+ehp[1]+ehp[2]==0){stage++;say("派蒙",new String[]{"试过风元素的力量了，向蒙德出发吧！咦，刚才天上飞过去的是什么？"});}
        else if(stage==AIR_SLIME && ehp[3]==0){stage++;note("跟随安柏前往巡逻地点");}
        else if(stage==SCOUT && ehp[4]==0){stage++;note("前面就是丘丘人营地");}
        else if(stage==CAMP && ehp[5]+ehp[6]+ehp[7]==0){stage++;note("营地安全了，去找安柏");}
        if(hit)save();
    }
    void damage(int i,int amount){
        ehp[i]=Math.max(0,ehp[i]-amount);enemyDelay[i]=.65f;hit=true;
        if(ehp[i]==0){kills++;note(i<4?"史莱姆已消散":"丘丘人已被击败");}
    }
    boolean activeEnemy(int i){return ehp[i]>0 && (i==8?stage==END:stage==enemySite(i)) && (i>=3||i==0||ehp[i-1]==0);}
    void recover(){
        hp=healthMax();air=vy=0;stamina=100;sprint=glide=false;
        int loc=stage>STATUE?STATUE:PAIMON;x=ox(loc);z=oz(loc)-2;
        for(int i=0;i<ehp.length;i++){ex[i]=homeX(i);ez[i]=homeZ(i);}
        note("微风送你回到了安全的地方");
    }
    void tick(float dt,int held,int pressed){
        if(pages!=null){if((pressed&ACT)!=0)advance();return;}
        shot=Math.max(0,shot-dt);burstCd=Math.max(0,burstCd-dt);attackCd=Math.max(0,attackCd-dt);skillCd=Math.max(0,skillCd-dt);wind=Math.max(0,wind-dt);
        flash=Math.max(0,flash-dt);noticeTime-=dt;hit=false;
        if((pressed&SPRINT)!=0)sprint=!sprint;
        if((held&LEFT)!=0)yaw+=105*dt;
        if((held&RIGHT)!=0)yaw-=105*dt;
        if(yaw>180)yaw-=360;if(yaw< -180)yaw+=360;
        boolean swim=land.ground(x,z)<land.water(x,z)-.22f && air<=0;
        if((pressed&JUMP)!=0 && !swim){
            if(air==0 && stamina>=8){vy=5.6f;stamina-=8;air=.01f;}
            else if(air>.15f && stage>=FOUNTAIN){glide=!glide;if(glide)launched=true;}
        }
        if(air>0){
            vy-=dt*10;if(glide && stamina>0){vy=Math.max(-.95f,vy);stamina-=dt*5;}
            air+=vy*dt;if(air<=0){air=vy=0;glide=false;}
        }
        int dir=((held&FORWARD)!=0?1:0)-((held&BACK)!=0?1:0);
        moving=dir!=0 || glide;
        if(moving){
            float a=yaw*.017453293f,speed=swim?3.8f:glide?8:sprint?12:7;
            float step=dt*speed*(glide?1:dir);
            float nx=Land.clamp(x+(float)Math.sin(a)*step,1,255),nz=Land.clamp(z+(float)Math.cos(a)*step,1,511);
            if(air<1 && land.blocked(nx,nz) && !land.blocked(x,z)){
                if(!land.blocked(x,nz))nx=x;else if(!land.blocked(nx,z))nz=z;else{nx=x;nz=z;}
            }
            float oldGround=land.ground(x,z);
            if(glide)air=Math.max(0,air+oldGround-land.ground(nx,nz));
            if(air==0)glide=false;
            x=nx;z=nz;
        }
        stamina=Land.clamp(stamina+dt*(sprint&&moving&&!swim?-8:glide?0:9),0,100);
        if(stamina<1){sprint=false;glide=false;}
        if((held&SKILL)!=0)charge+=dt;
        if((pressed&SKILL)!=0 && (held&SKILL)==0){strike(1);charge=0;}
        else if(charge>0 && (held&SKILL)==0){strike(1);charge=0;}
        if(pages==null && (pressed&SCAN)!=0)strike(2);
        if((pressed&START)!=0 && stage>AIR_SLIME && stage!=FOUNTAIN)asAmber=!asAmber;
        if(pages==null && (pressed&ACT)!=0){
            boolean combat=false;
            for(int i=0;i<ehp.length;i++)if(activeEnemy(i) && distance(ex[i],ez[i])<(asAmber?18:3.6f))combat=true;
            int focus=nearby();
            if(combat && (focus<0||focus==STATUE||focus==ANCHOR))strike(0);else interact(focus);
        }
        if(pages!=null)return;
        for(int i=0;i<ehp.length;i++)if(activeEnemy(i)){
            if(i==3)continue;enemyDelay[i]-=dt;float d=distance(ex[i],ez[i]);
            boolean chase=d<11 && d2(x,z,homeX(i),homeZ(i))<22*22;
            float tx=chase?x:homeX(i),tz=chase?z:homeZ(i);
            float len=(float)Math.sqrt(d2(tx,tz,ex[i],ez[i]));
            if(len>(chase?1.35f:.1f)){float fraction=Math.min(1,dt*2.4f/len);ex[i]+=(tx-ex[i])*fraction;ez[i]+=(tz-ez[i])*fraction;}
            if(d<2.4 && air<1.4 && enemyDelay[i]<=0){hp-=i<3?7:11;enemyDelay[i]=1.5f;note("受到撞击");if(hp<=0){recover();save();break;}}
        }
        saveTime+=dt;if(saveTime>12){saveTime=0;save();}
    }
    private static final String SLOT="windward2";
    byte[] snapshot() throws IOException{
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(buffer);
        out.writeInt(0x4d440102);out.writeByte(hero);out.writeByte(stage);out.writeByte(runes);
        out.writeFloat(x);out.writeFloat(z);out.writeFloat(yaw);out.writeShort(hp);
        out.writeByte(wisps);out.writeByte(berries);out.writeByte(kills);out.writeBoolean(opened);out.writeBoolean(asAmber);out.writeBoolean(launched);
        for(int i=0;i<ehp.length;i++)out.writeByte(ehp[i]);out.close();return buffer.toByteArray();
    }
    void save(){
        RecordStore rs=null;
        try{byte[] data=snapshot();rs=RecordStore.openRecordStore(SLOT,true);
            if(rs.getNumRecords()==0)rs.addRecord(data,0,data.length);else rs.setRecord(1,data,0,data.length);
            saveOK=true;
        }catch(Exception e){saveOK=false;}finally{close(rs);}
    }
    private static void close(RecordStore rs){if(rs!=null)try{rs.closeRecordStore();}catch(Exception ignored){}}
    boolean load(){
        RecordStore rs=null;
        try{rs=RecordStore.openRecordStore(SLOT,false);DataInputStream in=new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
            if(in.readInt()!=0x4d440102)return false;int pick=in.readUnsignedByte();reset(pick);
            stage=in.readUnsignedByte();runes=in.readUnsignedByte();x=in.readFloat();z=in.readFloat();yaw=in.readFloat();hp=in.readUnsignedShort();
            wisps=in.readUnsignedByte();berries=in.readUnsignedByte();kills=in.readUnsignedByte();opened=in.readBoolean();asAmber=in.readBoolean();launched=in.readBoolean();
            for(int i=0;i<ehp.length;i++)ehp[i]=in.readUnsignedByte();
            return hero<2 && stage<=END && runes<=3 && x>=0 && x<=256 && z>=0 && z<=512 && hp>0 && hp<=healthMax();
        }catch(Exception e){return false;}finally{close(rs);}
    }
}
