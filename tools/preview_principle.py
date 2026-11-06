# -*- coding: utf-8 -*-
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_video_principle as mvp
out = os.path.join(mvp.BUILD, "frames2")
os.makedirs(out, exist_ok=True)
for t in [2.2, 7.4, 10.6, 16.0, 20.6, 24.6, 28.6]:
    im = mvp.render_at(t)
    p = os.path.join(out, "g%05.1f.png" % t)
    im.save(p)
    print("saved", p)
