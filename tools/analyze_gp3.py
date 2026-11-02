from PIL import Image
import numpy as np
for name in ['gp-p.png','gp-l.png']:
    im = np.array(Image.open(name).convert('RGB')).astype(int)
    H,W,_ = im.shape
    def isc(px,c,tol=4): return all(abs(int(px[i])-c[i])<=tol for i in range(3))
    print(name, W, H)
    # raw card corners: check top edge rows at specific columns using exact SURFACE at x positions
    cols = [int(W*0.06), int(W*0.20), int(W*0.60), int(W*0.90)] if W>H else [int(W*0.06), int(W*0.30), int(W*0.55), int(W*0.90)]
    for x in cols:
        ys=[y for y in range(H) if isc(im[y,x],(23,27,34))]
        print('  col',x,'surf y', (min(ys),max(ys)) if ys else None)
    for y in [int(H*0.25), int(H*0.75)]:
        xs=[x for x in range(W) if isc(im[y,x],(23,27,34))]
        print('  row',y,'surf x', (min(xs),max(xs)) if xs else None)