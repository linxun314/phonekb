# -*- coding: utf-8 -*-
"""Render a handful of still frames from make_video_30s for visual checking."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_video_30s as mv

TIMES = [1.5, 3.2, 6.0, 11.0, 16.0, 20.5, 24.5, 28.5]
outdir = os.path.join(mv.BUILD, "frames")
os.makedirs(outdir, exist_ok=True)
for t in TIMES:
    im = mv.render_at(t)
    p = os.path.join(outdir, "f%05.1f.png" % t)
    im.save(p)
    print("saved", p, im.size)
