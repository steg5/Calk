package com.example.gryntsivgames.sea;
import android.graphics.*; import java.awt.image.BufferedImage; import javax.imageio.ImageIO; import java.io.File;
public class Shot {
  static class BV extends BattleView { BV(){super(null);} public int getWidth(){return 2295;} public int getHeight(){return 1080;} public void draw(Canvas c){ onDraw(c);} }
  static class PV extends PlaceView { PV(){super(null);} public int getWidth(){return 2295;} public int getHeight(){return 1080;} public void draw(Canvas c){ onDraw(c);} }
  static void shot(int n, String f, boolean restore) throws Exception {
    java.util.Random r=new java.util.Random(5);
    BV v=new BV(); v.n=n; v.me=0; v.sel=restore?0:1; v.rightIdx=n>2?2:1; v.turn=1;
    v.boards=new Board[n]; v.names=new String[n]; v.alive=new boolean[n];
    String[] nm={"Степан","Корсар","Адмірал","Боцман","Штурман","Дельфін"};
    for(int i=0;i<n;i++){ v.boards[i]=new Board(); v.boards[i].randomize(r); for(int k=0;k<25;k++) v.boards[i].shoot(r.nextInt(100)); v.names[i]=nm[i]; v.alive[i]=true; }
    if(n>3) v.alive[3]=false;
    v.tgtOfTurn=2; v.msg="Корсар → Адмірал: потоплено"; v.myTurn=false;
    if(restore){ v.rMode=2; v.rShip=0; v.gr=3; v.gc=3; v.gOk=true; }
    BufferedImage im=new BufferedImage(2295,1080,BufferedImage.TYPE_INT_ARGB); v.draw(new Canvas(im)); ImageIO.write(im,"png",new File(f));
  }
  public static void main(String[] a) throws Exception {
    Scheme.set(3); shot(6,"/tmp/b6.png",false); Scheme.set(0); shot(2,"/tmp/b2.png",false); shot(4,"/tmp/b4r.png",true);
  }
}
