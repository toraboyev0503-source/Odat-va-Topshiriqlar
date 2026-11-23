#!/usr/bin/env python3
"""Generate Android icons from the user supplied hourglass artwork."""
from pathlib import Path
from PIL import Image, ImageChops
import io
import base64
root = Path("upstream/uhabits-android/src/main/res")
src = Image.open(io.BytesIO(base64.b64decode(Path("ci/vazifalar_phase5_icon.b64").read_text()))).convert("RGBA")
for density, scale in (("mdpi",1),("hdpi",1.5),("xhdpi",2),("xxhdpi",3),("xxxhdpi",4)):
    dest=root/("mipmap-"+density)
    dest.mkdir(parents=True,exist_ok=True)
    px=round(48*scale)
    tile=src.resize((px,px),Image.Resampling.LANCZOS)
    tile.save(dest/"ic_launcher.png")
    tile.save(dest/"ic_launcher_round.png")
    size=round(108*scale)
    content=round(size*.84)
    resized=src.resize((content,content),Image.Resampling.LANCZOS)
    offset=(size-content)//2
    foreground=Image.new("RGBA",(size,size),(0,0,0,0))
    foreground.alpha_composite(resized,(offset,offset))
    foreground.save(dest/"ic_launcher_foreground.png")
    red, green, blue, _ = resized.split()
    alpha=ImageChops.subtract(blue,red).point(lambda x:255 if x>13 else 0)
    mono_piece=Image.new("RGBA",(content,content),"white")
    mono_piece.putalpha(alpha)
    mono=Image.new("RGBA",(size,size),(0,0,0,0))
    mono.alpha_composite(mono_piece,(offset,offset))
    mono.save(dest/"ic_launcher_monochrome.png")
splash=root/"drawable-nodpi"
splash.mkdir(parents=True,exist_ok=True)
src.resize((256,256),Image.Resampling.LANCZOS).save(splash/"tasks_splash_icon.png")
print("Generated 21 launcher and splash PNG assets")
