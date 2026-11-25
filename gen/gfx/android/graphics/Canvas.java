package android.graphics;
import java.awt.*; import java.awt.geom.*; import java.awt.image.BufferedImage;
public class Canvas { public Graphics2D g; public BufferedImage img;
 public Canvas(BufferedImage i){img=i; g=i.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);}
 java.util.ArrayDeque<AffineTransform> st=new java.util.ArrayDeque<>();
 public int save(){st.push(g.getTransform());return 0;} public void restore(){g.setTransform(st.pop());}
 public void translate(float x,float y){g.translate(x,y);} public void scale(float x,float y){g.scale(x,y);} public void rotate(float d,float x,float y){g.rotate(Math.toRadians(d),x,y);}
 void set(Paint p){ int c=p.color; g.setColor(new Color((c>>16)&255,(c>>8)&255,c&255,(c>>>24)&255)); g.setStroke(new BasicStroke(p.sw,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); }
 void draw(Shape s,Paint p){set(p); if(p.style==Paint.Style.FILL)g.fill(s); else g.draw(s);} 
 public void drawColor(int c){g.setColor(new Color(c));g.fillRect(-10000,-10000,20000,20000);} 
 public void drawRoundRect(RectF r,float a,float b,Paint p){draw(new RoundRectangle2D.Float(r.l,r.t,r.r-r.l,r.b-r.t,a*2,b*2),p);} 
 public void drawCircle(float x,float y,float r,Paint p){draw(new Ellipse2D.Float(x-r,y-r,2*r,2*r),p);} 
 public void drawOval(RectF r,Paint p){draw(new Ellipse2D.Float(r.l,r.t,r.r-r.l,r.b-r.t),p);} 
 public void drawPath(Path q,Paint p){draw(q.g,p);} 
 public void drawLine(float a,float b,float c,float d,Paint p){set(p);g.draw(new Line2D.Float(a,b,c,d));} 
 public void drawRect(float l,float t,float r,float b,Paint p){draw(new Rectangle2D.Float(l,t,r-l,b-t),p);} 
 public void drawText(String s,float x,float y,Paint p){set(p);g.setFont(new Font("DejaVu Sans",Font.BOLD,(int)p.ts)); FontMetrics m=g.getFontMetrics(); float w=m.stringWidth(s); if(p.al==Paint.Align.CENTER)x-=w/2; else if(p.al==Paint.Align.RIGHT)x-=w; g.drawString(s,x,y);} 
}
