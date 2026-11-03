# -*- coding: utf-8 -*-
"""v0.31 效果图：把左边方向盘换成「模拟摇杆」形态。"""
from PIL import Image, ImageDraw, ImageFont
import math, os

K = 2.75
BG=(15,17,21); BODY=(44,48,66); EDGE=(54,58,78); SURF2=(31,37,48); KEY=(38,45,58)
ACCENT=(76,141,255); TEXT=(233,236,242); DIM=(152,161,179); OK=(61,220,132); WHITE=(211,218,231)
F="C:/Windows/Fonts/msyh.ttc"; FB="C:/Windows/Fonts/msyhbd.ttc"
def f(sz,b=False): return ImageFont.truetype(FB if b else F,int(sz*K))
def P(v): return int(round(v*K))
def txt(d,xy,s,sz,color=TEXT,b=False,anchor="mm"): d.text((P(xy[0]),P(xy[1])),s,font=f(sz,b),fill=color,anchor=anchor)
def rr(d,box,r,fill=None,outline=None,width=1):
    x0,y0,x1,y1=[P(v) for v in box]
    d.rounded_rectangle([x0,y0,x1,y1],radius=P(r),fill=fill,outline=outline,width=int(max(1,width*K)))
def ring(d,cx,cy,r,color=WHITE,w=1.2):
    d.ellipse([P(cx-r),P(cy-r),P(cx+r),P(cy+r)],outline=color,width=int(max(1,w*K)))
def disc_cup(d,cx,cy,r): d.ellipse([P(cx-r),P(cy-r),P(cx+r),P(cy+r)],fill=(24,29,41),outline=WHITE)
def stick(d,cx,cy,r,dx=0.0,dy=0.0,label=None,sub=None,tag=None):
    disc_cup(d,cx,cy,r)
    # 四向刻度
    for a in (0,90,180,270):
        x0=cx+math.sin(math.radians(a))*r*0.90; y0=cy-math.cos(math.radians(a))*r*0.90
        x1=cx+math.sin(math.radians(a))*r*0.74; y1=cy-math.cos(math.radians(a))*r*0.74
        d.line([P(x0),P(y0),P(x1),P(y1)],fill=WHITE,width=int(1.3*K))
    # 摇杆头
    hx=cx+dx*r*0.52; hy=cy+dy*r*0.52; hr=r*0.54
    d.ellipse([P(hx-hr),P(hy-hr),P(hx+hr),P(hy+hr)],fill=WHITE)
    d.ellipse([P(cx-r*0.06),P(cy-r*0.06),P(cx+r*0.06),P(cy+r*0.06)],fill=(120,128,145))
    if label: txt(d,(cx,cy+r+13),label,12,TEXT,True)
    if sub: txt(d,(cx,cy+r+29),sub,9.5,DIM)
    if tag: txt(d,(cx,cy-r-13),tag,10,ACCENT)
def abxy(d,cx,cy,r,off,ringR,col):
    disc_cup(d,cx,cy,ringR)
    for (name,dx,dy) in (("Y",0,-1),("X",-1,0),("B",1,0),("A",0,1)):
        x=cx+dx*off; y=cy+dy*off
        d.ellipse([P(x-r),P(y-r),P(x+r),P(y+r)],fill=col[name])
        txt(d,(x,y),name,15,WHITE,True)
def header(d,W):
    rr(d,(14,30,62,78),12,fill=SURF2)
    ring(d,38,54,9,TEXT,2.2); d.line([P(38),P(45),P(38),P(54)],fill=TEXT,width=int(2.2*K))
    txt(d,(72,44),"已连接",15,TEXT,True,"lm"); txt(d,(72,64),"iQOO Pad2",11,DIM,anchor="lm")
    x=W-16
    for name in ("帮助","伴侣","旋转"):
        w=50 if name=="帮助" else 54
        rr(d,(x-w,34,x,74),10,fill=SURF2); txt(d,(x-w/2,54),name,12); x-=(w+8)
    return 88
COL={"A":(31,81,54),"B":(91,38,43),"X":(34,64,95),"Y":(90,71,22)}

def land(dragged):
    W,H=873,393
    im=Image.new("RGB",(P(W),P(H)),BG); d=ImageDraw.Draw(im)
    txt(d,(14,14),"10:47",11,TEXT,anchor="lm"); rr(d,(W-46,8,W-18,22),5,fill=KEY); rr(d,(W-43,11,W-33,19),2,fill=OK)
    y=header(d,W); rr(d,(16,y,W-16,H-8),18,fill=BODY,outline=EDGE,width=1.4)
    by=y+8
    for box,lab in (((30,by,126,by+30),"L1"),((132,by,228,by+30),"LT"),((W-228,by,W-132,by+30),"RT"),((W-126,by,W-30,by+30),"R1")):
        rr(d,box,min(10,(box[3]-box[1])/2),fill=KEY); txt(d,((box[0]+box[2])/2,(box[1]+box[3])/2),lab,12)
    rr(d,(W/2-86,by,W/2-8,by+30),10,fill=KEY); txt(d,(W/2-47,by+15),"SELECT",11)
    rr(d,(W/2+2,by,W/2+80,by+30),10,fill=KEY); txt(d,(W/2+41,by+15),"START",11)
    cy=y+168
    stick(d,122,cy,82,0.72 if dragged else 0, -0.72 if dragged else 0,
          "移动 · 模拟摇杆" if not dragged else "移动 · 拖动中",
          "按住拖动 · 摇杆头跟手" if not dragged else "45° 方向 · 输出 X/Y 模拟轴",
          tag=None)
    abxy(d,W-120,cy,20,42,74,COL)
    txt(d,(W-120,cy+74+13),"ABXY",12,TEXT,True)
    return im

def port(dragged):
    W,H=393,873
    im=Image.new("RGB",(P(W),P(H)),BG); d=ImageDraw.Draw(im)
    txt(d,(14,14),"10:47",11,TEXT,anchor="lm"); rr(d,(W-46,8,W-18,22),5,fill=KEY)
    y=header(d,W)-4
    txt(d,(W/2,y+12),"键盘   触控板   媒体   手柄   双手   单手",11,DIM); y+=34
    rr(d,(16,y,W-16,H-10),18,fill=BODY,outline=EDGE,width=1.4)
    rr(d,(30,y+10,118,y+36),10,fill=KEY); txt(d,(74,y+23),"移动盘 ∨",10)
    rr(d,(124,y+10,200,y+36),10,fill=KEY); txt(d,(162,y+23),"触控 关",10)
    cy=y+120
    stick(d,108,cy,66,0.72 if dragged else 0,-0.72 if dragged else 0,
          "移动 · 摇杆" if not dragged else "拖动中：右上",
          "按住拖动 · 松手回中" if not dragged else "")
    abxy(d,W-108,cy,16,34,60,COL)
    yy=cy+124
    for box,lab in (((30,yy,148,yy+30),"L1"),((30,yy+38,148,yy+68),"LT"),((W-148,yy,W-30,yy+30),"R1"),((W-148,yy+38,W-30,yy+68),"RT")):
        rr(d,box,10,fill=KEY); txt(d,((box[0]+box[2])/2,(box[1]+box[3])/2),lab,11)
    rr(d,(W/2-96,H-56,W/2-8,H-26),10,fill=KEY); txt(d,(W/2-52,H-41),"SELECT",10)
    rr(d,(W/2+4,H-56,W/2+92,H-26),10,fill=KEY); txt(d,(W/2+48,H-41),"START",10)
    return im

out="C:/Users/22453/Desktop/phoneKB"
land(False).save(os.path.join(out,"mock-v31-stick-l.png"))
land(True).save(os.path.join(out,"mock-v31-stick-l-drag.png"))
port(False).save(os.path.join(out,"mock-v31-stick-p.png"))
port(True).save(os.path.join(out,"mock-v31-stick-p-drag.png"))
print("ok")