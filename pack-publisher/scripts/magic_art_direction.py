"""Deterministic, authored engraved-book textures and native model finishing.

Requires Pillow. No downloads, borrowed artwork, uploads or release operations.
"""
from pathlib import Path
import math, json, random
from PIL import Image, ImageDraw, ImageFont

GOLD=(205,169,99)
INK=(13,15,28)
PALE=(248,233,191)

def circle(d, center, radius, color, width=1):
 x,y=center; d.ellipse((x-radius,y-radius,x+radius,y+radius),outline=color,width=width)

def cover(base, glyph, phase, index):
 im=Image.new('RGB',(128,128));px=im.load();rng=random.Random(71001+index)
 for y in range(128):
  for x in range(128):
   edge=min(x,y,127-x,127-y);shade=.23+.22*min(edge/20,1)+.12*(1-y/128)
   noise=rng.randint(-5,5)
   px[x,y]=tuple(max(0,min(255,int(c*shade)+noise)) for c in base)
 d=ImageDraw.Draw(im)
 # Layered leather binding, brass rulework, engraved corners and book stitching.
 d.rectangle((2,2,125,125),outline=(58,45,40),width=3)
 d.rectangle((7,7,120,120),outline=GOLD,width=1)
 d.rectangle((11,11,116,116),outline=(105,80,52),width=1)
 for y in range(15,115,6):d.line((4,y,5,y+2),fill=PALE)
 for x,y,sx,sy in [(12,12,1,1),(115,12,-1,1),(12,115,1,-1),(115,115,-1,-1)]:
  d.polygon([(x,y),(x+sx*24,y),(x+sx*7,y+sy*7),(x,y+sy*24)],fill=GOLD)
  d.line([(x+sx*3,y+sy*18),(x+sx*3,y+sy*3),(x+sx*18,y+sy*3)],fill=PALE)
  d.ellipse((min(x,x+sx*5)-2,min(y,y+sy*5)-2,max(x,x+sx*5)+1,max(y,y+sy*5)+1),fill=(105,74,38))
 # Fine concentric astrolabe with rotating illuminated tick marks.
 for r,c in [(44,(93,74,64)),(41,GOLD),(35,(111,91,78)),(32,GOLD)]:circle(d,(64,64),r,c)
 accent=tuple(min(255,int(c*.65+88)) for c in base)
 for i in range(24):
  a=i*math.tau/24; r1=36 if i%3 else 34;r2=40
  col=PALE if (i-phase*3)%24<3 else GOLD
  d.line((64+math.cos(a)*r1,64+math.sin(a)*r1,64+math.cos(a)*r2,64+math.sin(a)*r2),fill=col,width=1)
 for i in range(8):
  a=i*math.tau/8+math.pi/8
  x,y=64+math.cos(a)*47,64+math.sin(a)*47
  d.polygon([(x,y-2),(x+2,y),(x,y+2),(x-2,y)],fill=accent if i==phase else GOLD)
 # Bespoke elemental emblems, drawn as vectors rather than enlarged pixel letters.
 if index==3: # rising flame
  d.polygon([(64,40),(66,53),(74,48),(73,61),(83,69),(76,82),(62,88),(48,78),(46,67),(56,54),(55,65)],fill=GOLD)
  d.polygon([(63,56),(68,69),(74,70),(70,80),(61,83),(53,74)],fill=accent)
 elif index==4: # tidal teardrop with foaming wave
  d.polygon([(64,40),(80,62),(82,74),(75,83),(63,87),(51,83),(46,74),(49,61)],fill=GOLD)
  d.ellipse((50,61,78,82),fill=accent);d.arc((49,57,75,80),10,210,fill=PALE,width=3)
 elif index==5: # branched ice crystal
  for i in range(6):
   a=i*math.tau/6;u,v=math.cos(a),math.sin(a)
   d.line((64,64,64+u*23,64+v*23),fill=PALE,width=2)
   for turn in (-1,1):
    d.line((64+u*14,64+v*14,64+u*10-v*turn*6,64+v*10+u*turn*6),fill=accent,width=2)
  circle(d,(64,64),5,GOLD,2)
 elif index==7: # dimensional arch and vanishing stair
  d.arc((46,40,82,76),180,360,fill=GOLD,width=3)
  d.line((46,58,46,84,82,84,82,58),fill=GOLD,width=3)
  d.arc((52,46,76,75),180,360,fill=accent,width=2)
  for j in range(4):d.line((52+j*2,79-j*5,76-j*2,79-j*5),fill=PALE)
 elif index==8: # hearth chalice and protective spark
  d.polygon([(45,62),(83,62),(76,76),(67,80),(67,85),(76,87),(52,87),(61,85),(61,80),(51,76)],fill=GOLD)
  d.polygon([(64,40),(72,54),(64,61),(56,54)],fill=accent)
  d.line((49,66,77,66),fill=PALE,width=2)
 elif index==9: # living branch
  d.line((57,87,67,43),fill=GOLD,width=2)
  for j in range(4):
   y=52+j*8;x=65-j*2
   d.polygon([(x,y+5),(x-12,y-5),(x-13,y+3),(x,y+10)],fill=accent)
   d.polygon([(x,y+2),(x+14,y-7),(x+11,y+2),(x,y+6)],fill=GOLD)
 elif index==6: # piercing celestial lance
  d.polygon([(64,38),(69,58),(84,64),(69,69),(64,90),(59,69),(44,64),(59,58)],fill=PALE)
  d.line((46,83,82,45),fill=accent,width=2);circle(d,(64,64),12,GOLD)
 elif index==1: # wax sigil
  d.ellipse((49,48,80,80),fill=(128,43,60),outline=GOLD,width=2)
  d.polygon([(52,76),(50,90),(60,84),(65,89),(66,78)],fill=(128,43,60))
  d.arc((56,54,73,73),25,330,fill=PALE,width=2)
 else: # astrolabe / focused arcane diamond
  d.polygon([(64,39),(78,64),(64,89),(50,64)],outline=GOLD,fill=INK)
  d.polygon([(64,48),(72,64),(64,80),(56,64)],fill=accent)
  d.line((64,49,64,78),fill=PALE,width=2)
  if index==0:
   d.arc((42,51,86,77),0,360,fill=PALE,width=2)
   d.arc((50,40,78,88),0,360,fill=GOLD,width=1)
 # Individual chapter marks and decorative linework rather than tiny fake text.
 for i in range(index%5+1):
  x=64-(index%5)*3+i*6;d.line((x,16,x,20),fill=PALE)
 for y in (23,104):
  d.line((42,y,86,y),fill=GOLD)
  d.polygon([(61,y),(64,y-3),(67,y),(64,y+3)],fill=PALE)
 return im

def finish(root, designs):
 texture=root/'assets/coresuite/textures/item';models=root/'assets/coresuite/models/item'
 texture.mkdir(parents=True,exist_ok=True)
 # Shared purpose-drawn metal and parchment; no vanilla block textures on the focus models.
 for name,base in [('arcane_brass',(191,147,67)),('arcane_pages',(224,213,181)),('arcane_obsidian',(32,26,45))]:
  im=Image.new('RGB',(32,32));d=ImageDraw.Draw(im)
  for y in range(32):
   k=(y%4)*-7 if name=='arcane_pages' else int(24*math.cos(y/31*math.pi))
   d.line((0,y,31,y),fill=tuple(max(0,min(255,c+k)) for c in base))
  im.save(texture/(name+'.png'))
 for index,(name,cmd,title,color,hexcolor,glyph) in enumerate(designs):
  base=tuple(bytes.fromhex(hexcolor[1:]));frames=[cover(base,glyph,f,index) for f in range(8)]
  strip=Image.new('RGB',(128,1024))
  for i,frame in enumerate(frames):strip.paste(frame,(0,i*128))
  strip.save(texture/(name+'_cover.png'))
  (texture/(name+'_cover.png.mcmeta')).write_text(json.dumps({'animation':{'frametime':5,'interpolate':True}})+'\n')
  # Flat icon uses exactly the same cover design, framed with pages and a ribbon.
  icon=Image.new('RGBA',(128,128));d=ImageDraw.Draw(icon)
  d.rounded_rectangle((14,8,111,119),radius=5,fill=(15,12,24),outline=GOLD,width=2)
  d.rectangle((21,110,107,121),fill=(218,207,174))
  for y in (113,116,119):d.line((22,y,106,y),fill=(137,124,99))
  icon.paste(frames[0].resize((86,101),Image.Resampling.LANCZOS),(21,9))
  d.rectangle((16,14,20,109),fill=(39,26,43))
  d.polygon([(83,110),(90,110),(90,127),(86,123),(83,127)],fill=base)
  icon.save(texture/(name+'.png'))
  model=json.loads((models/(name+'.json')).read_text())
  model['textures'].update(cover='coresuite:item/'+name+'_cover',pages='coresuite:item/arcane_pages',metal='coresuite:item/arcane_brass',rune='coresuite:item/arcane_brass',ribbon='coresuite:item/arcane_obsidian',particle='coresuite:item/'+name+'_cover')
  # Remove chunky pixel glyph cubes; the engraved seal is on the face itself.
  model['elements']=[e for e in model['elements'] if e['faces']['south']['texture']!='#rune']
  # Add two brass spine ribs and a clasp with an inset seal stone.
  def box(a,b,t):return {'from':a,'to':b,'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}}
  for y in (4,10.5):model['elements'].append(box([2.85,y,6.55],[3.2,y+.5,8.6],'metal'))
  model['elements'].append(box([12.2,7.25,6.4],[13.35,8.75,9.35],'metal'))
  # Keep the front readable in hand and avoid covering the crosshair.
  model['display']['gui']={'rotation':[12,195,-8],'translation':[0,0,0],'scale':[.94,.94,.94]}
  (models/(name+'.json')).write_text(json.dumps(model,indent=2)+'\n')
 # Sculpted obsidian focus with forked brass cage, beveled crystal and pommel.
 wand=json.loads((models/'astral_wand.json').read_text())
 wand['textures']={'shaft':'coresuite:item/arcane_obsidian','gold':'coresuite:item/arcane_brass','gem':'coresuite:item/wand_crystal','particle':'coresuite:item/wand_crystal'}
 crystal=Image.new('RGB',(32,256))
 for frame in range(8):
  d=ImageDraw.Draw(crystal);y=frame*32
  d.rectangle((0,y,31,y+31),fill=(71,42,110));d.polygon([(16,y),(30,y+16),(16,y+31),(2,y+16)],fill=(132+frame*5,95+frame*6,210+frame*5))
  d.polygon([(16,y+2),(16,y+28),(5,y+16)],fill=(203,175,245));d.line((16,y+3,25,y+16),fill=(255,241,255),width=2)
 crystal.save(texture/'wand_crystal.png');(texture/'wand_crystal.png.mcmeta').write_text('{"animation":{"frametime":5,"interpolate":true}}\n')
 wand['elements']=[box([7.35,0,7.35],[8.65,10.5,8.65],'shaft'),box([6.9,.7,6.9],[9.1,1.3,9.1],'gold')]
 for y in (3,5.5,8):wand['elements'].append(box([7.1,y,7.1],[8.9,y+.4,8.9],'gold'))
 for x in (5.5,9.7):wand['elements'].append(box([x,9,7.3],[x+.8,13,8.7],'gold'))
 gem=box([6.5,11,6.5],[9.5,15,9.5],'gem');gem['rotation']={'origin':[8,13,8],'axis':'y','angle':45,'rescale':False};wand['elements'].append(gem)
 wand['elements'].append(box([6.2,9.5,6.2],[9.8,10.2,9.8],'gold'))
 (models/'astral_wand.json').write_text(json.dumps(wand,indent=2)+'\n')
 icon=Image.new('RGBA',(128,128));d=ImageDraw.Draw(icon)
 d.line((59,120,59,42),fill=(83,66,110),width=9)
 d.line((57,120,57,42),fill=(141,119,158),width=2)
 for y in (60,78,96,114):d.rectangle((54,y,64,y+3),fill=GOLD)
 d.line([(47,51),(42,30),(45,17)],fill=GOLD,width=4);d.line([(71,51),(76,30),(73,17)],fill=GOLD,width=4)
 d.polygon([(59,3),(70,23),(59,43),(48,23)],fill=(141,101,217),outline=PALE)
 d.polygon([(59,5),(59,39),(50,23)],fill=(216,188,251));d.rectangle((49,47,69,51),fill=GOLD)
 icon.save(texture/'astral_wand.png')

def preview(root,designs,out):
 sheet=Image.new('RGB',(1100,480),(14,16,25));d=ImageDraw.Draw(sheet)
 font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',17)
 title=ImageFont.truetype('C:/Windows/Fonts/seguisb.ttf',26)
 d.text((30,18),'MEERMC  /  THE ASTRAL ARCHIVE',font=title,fill=PALE)
 d.text((30,54),'Authored cover art • engraved brass • animated seals • obsidian focus',font=font,fill=(153,162,185))
 items=[(x[0],x[2]) for x in designs]+[('astral_wand','Astral Focus')]
 for i,(name,label) in enumerate(items):
  x=25+(i%6)*180;y=96+(i//6)*187
  d.rounded_rectangle((x,y,x+165,y+174),radius=12,fill=(24,27,41),outline=(55,49,65))
  icon=Image.open(root/f'assets/coresuite/textures/item/{name}.png').convert('RGBA');sheet.paste(icon,(x+18,y+5),icon)
  d.text((x+8,y+143),label,font=font,fill=(221,218,224))
 out.parent.mkdir(parents=True,exist_ok=True);sheet.save(out)
