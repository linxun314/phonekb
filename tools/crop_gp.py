from PIL import Image
im = Image.open(r'C:\Users\22453\Desktop\phoneKB\gp42-l-200.png')
im.crop((0, 150, 1300, 800)).save(r'C:\Users\22453\Desktop\phoneKB\tmp-crop-l200-left.png')
im.crop((1100, 150, 2400, 800)).save(r'C:\Users\22453\Desktop\phoneKB\tmp-crop-l200-right.png')
print('ok')