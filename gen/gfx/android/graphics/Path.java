package android.graphics;
public class Path { public java.awt.geom.Path2D.Float g = new java.awt.geom.Path2D.Float();
 public void reset(){g.reset();} public void moveTo(float x,float y){g.moveTo(x,y);} public void lineTo(float x,float y){g.lineTo(x,y);}
 public void quadTo(float a,float b,float c,float d){g.quadTo(a,b,c,d);} public void close(){g.closePath();} }
