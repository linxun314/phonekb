from PIL import Image
import numpy as np
def analyze(name):
    im = np.array(Image.open(name).convert('RGB')).astype(int)
    H,W,_ = im.shape
    def mask(c,tol=4): return (np.abs(im[:,:,0]-c[0])<=tol)&(np.abs(im[:,:,1]-c[1])<=tol)&(np.abs(im[:,:,2]-c[2])<=tol)
    surf = mask((23,27,34))
    key  = mask((38,45,58))
    surf2= mask((31,37,48))
    rows = surf.sum(axis=1); cols = surf.sum(axis=0)
    ys = np.where(rows> W*0.25)[0]
    print(name, 'card rows', (ys.min(), ys.max()) if len(ys) else None)
    # rows where left card exists and right card exists: find column runs on a row without buttons
    for y in [ys.min()+5, (ys.min()+ys.max())//2]:
        xs = np.where(surf[y])[0]
        if len(xs)==0: continue
        # split into runs
        runs=[]; start=xs[0]; prev=xs[0]
        for x in xs[1:]:
            if x-prev>8: runs.append((start,prev)); start=x
            prev=x
        runs.append((start,prev))
        print('  row',y,'runs',runs)
    # KEY rects: connected components via simple row-run bounding
    # label KEY regions using scanline union-find lite: just collect bboxes by grid clustering
    pts = np.argwhere(key)
    # cluster by proximity 20px using sorted flood via numpy grid
    clusters=[]
    for (yy,xx) in pts[::1]:
        placed=False
        for c in clusters:
            if abs(xx-c[0])<40 and abs(yy-c[1])<40:
                c[0]=(c[0]*c[4]+xx)/(c[4]+1); c[1]=(c[1]*c[4]+yy)/(c[4]+1)
                c[2]=min(c[2],xx); c[3]=min(c[3],yy); c[4]+=1; c[5]=max(c[5],xx); c[6]=max(c[6],yy)
                placed=True; break
        if not placed: clusters.append([xx,yy,xx,yy,1,xx,yy])
    big=[c for c in clusters if c[4]>500]
    print('  KEY clusters >500px:', [(round(c[0]),round(c[1]),c[2],c[3],c[5],c[6],c[4]) for c in sorted(big,key=lambda c:(c[3],c[2]))][:12])
for n in ['gp-p.png','gp-l.png']: analyze(n)