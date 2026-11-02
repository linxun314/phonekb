
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mock_gp6 import landscape, portrait

base = "C:/Users/22453/Desktop/phoneKB/"
print(landscape(base + "mock-gp6-l-nopanel.png", "left", panel=False))
print(portrait(base + "mock-gp6-p-nopanel.png", "left", panel=False))
