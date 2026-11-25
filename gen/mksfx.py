import wave, struct, math, random, subprocess, os
R=22050
random.seed(7)
def w(name, samples):
    m=max(1e-9,max(abs(s) for s in samples))
    data=b''.join(struct.pack('<h', int(max(-1,min(1,s/m*0.9))*32767)) for s in samples)
    f=wave.open('/tmp/%s.wav'%name,'wb'); f.setnchannels(1); f.setsampwidth(2); f.setframerate(R); f.writeframes(data); f.close()
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i','/tmp/%s.wav'%name,'-c:a','libvorbis','-q:a','3','sfx/%s.ogg'%name],check=True)
def lp(x,a):
    y=[];p=0
    for s in x:
        p+=a*(s-p); y.append(p)
    return y
def noise(n): return [random.uniform(-1,1) for _ in range(n)]
def env(n,att,dec): return [min(1,i/(att*R+1))*math.exp(-i/(dec*R)) for i in range(n)]
# shot: thump + noise burst
n=int(R*0.45); e=env(n,0.002,0.09)
nz=lp(noise(n),0.35)
w('shot',[ (nz[i]*0.8+math.sin(2*math.pi*(120-80*i/n)*i/R)*1.0)*e[i] for i in range(n)])
# hit: explosion low rumble
n=int(R*0.7); e=env(n,0.003,0.16); nz=lp(noise(n),0.15)
w('hit',[ (nz[i]*1.6+math.sin(2*math.pi*(70-30*i/n)*i/R))*e[i] for i in range(n)])
# miss: сплеск у воду
n=int(R*0.8)
plop=[math.sin(2*math.pi*(520*math.exp(-i/(0.05*R))+140)*i/R)*math.exp(-i/(0.05*R)) for i in range(n)]
nz=lp(noise(n),0.55)
sw=[nz[i]*min(1,i/(0.02*R))*math.exp(-i/(0.16*R))*0.9 for i in range(n)]
bub=[0.0]*n
for st in (0.12,0.2,0.29,0.38):
    f0=700+random.random()*700
    s0=int(st*R)
    for i in range(int(0.05*R)):
        if s0+i<n: bub[s0+i]+=math.sin(2*math.pi*(f0*(1+i/(0.05*R)))*i/R)*math.exp(-i/(0.015*R))*0.35
w('miss',[plop[i]*1.0+sw[i]+bub[i] for i in range(n)])
# sunk: big boom + descending tone
n=int(R*1.3); e=env(n,0.003,0.3); nz=lp(noise(n),0.1)
w('sunk',[ (nz[i]*1.5+math.sin(2*math.pi*(90-60*i/n)*i/R)+0.4*math.sin(2*math.pi*(400-300*i/n)*i/R)*math.exp(-i/(0.2*R)))*e[i] for i in range(n)])
# win arpeggio
s=[];
for k,f in enumerate([523,659,784,1047]):
    n=int(R*0.18); s+= [math.sin(2*math.pi*f*i/R)*math.exp(-i/(0.12*R)) for i in range(n)]
n=int(R*0.6); s+=[(math.sin(2*math.pi*1047*i/R)+math.sin(2*math.pi*1319*i/R))*math.exp(-i/(0.3*R)) for i in range(n)]
w('win',s)
s=[]
for f in [392,330,262,196]:
    n=int(R*0.25); s+=[math.sin(2*math.pi*f*i/R)*math.exp(-i/(0.2*R)) for i in range(n)]
w('lose',s)
# place click
n=int(R*0.08); w('place',[math.sin(2*math.pi*700*i/R)*math.exp(-i/(0.015*R)) for i in range(n)])
# btn
n=int(R*0.07); w('btn',[math.sin(2*math.pi*520*i/R)*math.exp(-i/(0.02*R)) for i in range(n)])
