import os
from PIL import Image

# 16x16 pixel art design for a black fish silhouette with a white '?'
# 0 = transparent, 1 = black, 2 = white
pixels = [
    [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
    [0,0,0,0,0,0,1,1,1,0,0,0,0,0,0,0],
    [0,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0],
    [0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0],
    [0,0,1,1,1,1,2,2,1,1,1,1,1,1,1,1],
    [0,1,1,1,1,2,1,1,2,1,1,1,1,1,1,1],
    [0,1,1,1,1,1,1,1,2,1,1,1,1,1,1,1],
    [1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,1],
    [1,1,1,1,1,1,1,2,1,1,1,1,1,1,1,1],
    [0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],
    [0,0,1,1,1,1,1,2,1,1,1,1,1,1,1,1],
    [0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0],
    [0,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0],
    [0,0,0,0,0,0,1,1,1,0,0,0,0,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
    [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]
]

img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for y in range(16):
    for x in range(16):
        if pixels[y][x] == 1:
            img.putpixel((x, y), (20, 20, 20, 255)) # Dark gray/black
        elif pixels[y][x] == 2:
            img.putpixel((x, y), (255, 255, 255, 255)) # White

tex_path = r"C:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\resourcepack\assets\barely_default\textures\item\fish_unknown.png"
img.save(tex_path)
print(f"Saved texture to {tex_path}")
