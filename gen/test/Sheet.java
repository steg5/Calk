package com.example.gryntsivgames.sea;
import android.graphics.*; import java.awt.image.BufferedImage; import javax.imageio.ImageIO; import java.io.File;
public class Sheet {
  public static void main(String[] a) throws Exception {
    float cell=70; int W=1500,H=1250;
    BufferedImage im=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
    Canvas c=new Canvas(im); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    c.drawColor(0xFF1565C0);
    for(int th=0;th<5;th++){
      float y=20+th*240;
      float x=20;
      int[] lens={4,3,2,1};
      for(int i=0;i<4;i++){ Painter.draw(c,p,th,x,y,cell,lens[i],true,i); x+=lens[i]*cell+30; }
      x=20;
      Painter.draw(c,p,th,x,y+110,cell,3,true,1); x+=3*cell+30;
      Painter.draw(c,p,th,x,y+110,cell,2,false,2); x+=cell+30;
      Painter.draw(c,p,th,x,y+110,cell,4,false,0); x+=cell+30;
      Painter.draw(c,p,th,x,y+110,cell,2,true,3);
    }
    // дошка
    Board b=new Board(); b.randomize(new java.util.Random(3)); b.shoot(b.ships[0].r*10+b.ships[0].c);
    Painter.board(c,p,b,900,60,40,true,0,-1,true);
    ImageIO.write(im,"png",new File("/tmp/sheet.png"));
  }
}
