from PIL import Image
for name in ['gp-p.png','gp-l.png']:
    im = Image.open(name).convert('RGB'); W,H = im.size; px = im.load()
    BG=(15,17,21); SURF=(23,27,34)
    def isbg(c,e=6): return all(abs(c[i]-BG[i])<=e for i in range(3))
    def issurf(c,e=4): return all(abs(c[i]-SURF[i])<=e for i in range(3))
    y = H//2
    xs=[x for x in range(W) if issurf(px[x,y])]
    print(name, W, H, 'surf-row', (min(xs),max(xs)) if xs else None)
    for label,xcol in [('left', int(W*0.08)), ('right', int(W*0.65))]:
        ys=[yy for yy in range(H) if issurf(px[xcol,yy])]
        print('  card', label, 'x', xcol, 'yrange', (min(ys),max(ys)) if ys else None)
    cols={'X':(34,64,95),'Y':(90,71,22),'A':(31,81,54),'B':(91,38,43)}
    for k,c in cols.items():
        pts=[]
        for yy in range(H):
            for xx in range(W):
                p=px[xx,yy]
                if all(abs(p[i]-c[i])<=6 for i in range(3)): pts.append((xx,yy))
        if pts:
            xs=[p[0] for p in pts]; ys=[p[1] for p in pts]
            print('  ',k,'bbox',(min(xs),min(ys),max(xs),max(ys)),'center',((min(xs)+max(xs))//2,(min(ys)+max(ys))//2))