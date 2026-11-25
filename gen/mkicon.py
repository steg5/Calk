from PIL import Image, ImageDraw, ImageFont, ImageFilter
S=4;W=192*S
im=Image.new("RGBA",(W,W),(0,0,0,0))
bgl=Image.new("RGBA",(W,W))
px=bgl.load()
for y in range(W):
    for x in range(W):
        k=(x+y)/(2*W)
        px[x,y]=(int(10+20*k),int(30+50*k),int(70+90*k),255)
mask=Image.new("L",(W,W),0)
ImageDraw.Draw(mask).rounded_rectangle([0,0,W-1,W-1],radius=int(W*0.2),fill=255)
im.paste(bgl,(0,0),mask)
d=ImageDraw.Draw(im)
# хвилі
for i in range(3):
    y=int(W*(0.80+0.06*i))
    d.line([(x,y+int(6*S*__import__('math').sin(x/(W/14.0)+i))) for x in range(0,W,4)],fill=(120,190,255,110),width=S*3)
def fit(txt,target):
    lo,hi=10,int(W*0.6)
    while hi-lo>1:
        m=(lo+hi)//2
        f=ImageFont.truetype("/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf",m)
        b=d.textbbox((0,0),txt,font=f,stroke_width=S*2)
        if b[2]-b[0]<=target: lo=m
        else: hi=m
    return ImageFont.truetype("/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf",lo)
def line(txt,cy,fill):
    f=fit(txt,int(W*0.96))
    b=d.textbbox((0,0),txt,font=f,stroke_width=S*2)
    x=(W-(b[2]-b[0]))//2-b[0]; y=cy-(b[3]-b[1])//2-b[1]
    d.text((x+S*2,y+S*3),txt,font=f,fill=(0,0,0,140),stroke_width=S*2,stroke_fill=(0,0,0,140))
    d.text((x,y),txt,font=f,fill=fill,stroke_width=S*2,stroke_fill=(8,24,52,255))
line("Gryntsiv",int(W*0.33),(255,213,79,255))
line("Games",int(W*0.69),(255,255,255,255))
im=im.resize((192,192),Image.LANCZOS)
im.save("ic_small.png",optimize=True)
