import com.example.gryntsivgames.durak.*;
import java.util.*;
public class DSave { public static void main(String[] a){ Random r=new Random(3);
 for(int g=0;g<500;g++){ int n=2+r.nextInt(5); String[] nm=new String[n]; for(int i=0;i<n;i++) nm[i]="P"+i;
  DEngine e=new DEngine(nm,-1,0,g%2==0,r); DBot b=new DBot(); int steps=r.nextInt(40);
  for(int s=0;s<steps&&!e.over;s++){ for(int k=0;k<n;k++){ int p=(e.cur()+k)%n; DBot.Act x=b.choose(e,p,1); String res=null;
    if(x.type==DBot.THROW)res=e.attack(p,x.card); else if(x.type==DBot.DEFEND)res=e.defend(p,x.card,x.target); else if(x.type==DBot.TRANSFER)res=e.transfer(p,x.card); else if(x.type==DBot.TAKE)res=e.take(p); else if(x.type==DBot.PASS)res=e.pass(p); else continue; break; } }
  String d=e.save(); DEngine f=DEngine.restore(d,nm,r); if(!f.save().equals(d)) throw new RuntimeException("mismatch "+g); }
 System.out.println("save ok"); } }
