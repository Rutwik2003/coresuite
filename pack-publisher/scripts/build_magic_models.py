"""Author native cuboid item models and matching pixel icons from one design table.
Local asset build only: never uploads or publishes a resource pack.
"""
from pathlib import Path
import json, struct, zlib
ROOT = Path(__file__).resolve().parent.parent
# The 5x5 cover symbols are deliberately distinct, including in monochrome.
DESIGNS = [
 ('astral_grimoire',71001,'Astral Grimoire','purple','#492f70',['00100','01110','11011','01110','00100']),
 ('sealed_tome',71002,'Sealed Tome','brown','#71523b',['01110','10001','00110','00000','00100']),
 ('tome_mana_dart',71010,'Mana Dart','cyan','#166574',['00100','00100','11111','01110','00100']),
 ('tome_fireball',71011,'Ember Lance','red','#922f24',['00100','01100','01110','11111','01110']),
 ('tome_water_cannon',71012,'Tidal Arrow','blue','#294e92',['00010','00101','11000','00101','00010']),
 ('tome_frost_lance',71013,'Frost Lance','light_blue','#4295b2',['10101','01110','11111','01110','10101']),
 ('tome_zoltraak',71014,'Zoltraak','black','#242337',['10001','01010','00100','01010','10001']),
 ('tome_dimension_door',71015,'Dimension Step','magenta','#863477',['11111','10001','10001','10001','11011']),
 ('tome_aura_of_the_hearth',71016,'Aura of the Hearth','orange','#ac5c26',['00000','01010','11111','01110','00100']),
 ('tome_harvesters_touch',71017,"Harvester's Touch",'green','#46682d',['00100','10101','01110','00100','00100'])]

def write(path, data):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')

def cube(a,b,texture):
 return {'from':a,'to':b,'faces':{face:{'texture':'#'+texture,'uv':[0,0,16,16]} for face in ('north','south','east','west','up','down')}}

def png(rects):
 # Rasterize the same authored rectangle primitives as the editable SVG icon.
 pixels=bytearray(32*32*4)
 for x,y,w,h,color in rects:
  rgba=bytes.fromhex(color.lstrip('#'))+bytes([255])
  for yy in range(y,y+h):
   for xx in range(x,x+w): pixels[(yy*32+xx)*4:(yy*32+xx)*4+4]=rgba
 raw=b''.join(bytes([0])+pixels[y*128:(y+1)*128] for y in range(32))
 def chunk(k,v): return struct.pack('>I',len(v))+k+v+struct.pack('>I',zlib.crc32(k+v)&0xffffffff)
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')

def build():
 roots=[ROOT/'resourcepack',ROOT/'meermc-resourcepack/pack']
 for root in roots:
  for name,cmd,title,color,hexcolor,glyph in DESIGNS:
   elements=[cube([3,2,6],[13,14,6.6],'cover'),cube([3,2,8.5],[13,14,9.1],'cover'),cube([3.5,2.5,6.6],[12.5,13.5,8.5],'pages'),cube([3,2,6.6],[4,14,8.5],'cover')]
   for x in (3,11.5):
    for y in (2,12.5):elements.append(cube([x,y,9.1],[x+1.5,y+1.5,9.3],'metal'))
   for row,line in enumerate(glyph):
    for col,on in enumerate(line):
     if on=='1':elements.append(cube([5.5+col,10.5-row,9.1],[6.5+col,11.5-row,9.25],'rune'))
   elements.append(cube([10,1,7],[11,3,7.2],'ribbon'))
   model={'textures':{'cover':'minecraft:block/'+color+'_concrete','pages':'minecraft:block/birch_planks','metal':'minecraft:block/gold_block','rune':'minecraft:block/white_concrete','ribbon':'minecraft:block/red_concrete','particle':'minecraft:block/'+color+'_concrete'},'gui_light':'front','display':{
    'gui':{'rotation':[15,195,-10],'translation':[0,0,0],'scale':[.9,.9,.9]},
    'firstperson_righthand':{'rotation':[0,195,8],'translation':[0,1,0],'scale':[.48,.48,.48]},
    'firstperson_lefthand':{'rotation':[0,165,-8],'translation':[0,1,0],'scale':[.48,.48,.48]},
    'thirdperson_righthand':{'rotation':[0,180,0],'translation':[0,2,0],'scale':[.55,.55,.55]},
    'thirdperson_lefthand':{'rotation':[0,180,0],'translation':[0,2,0],'scale':[.55,.55,.55]},
    'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.45,.45,.45]},
    'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.8,.8,.8]}},'elements':elements}
   write(root/f'assets/coresuite/models/item/{name}.json',model)
   rects=[(5,3,22,27,'#161322'),(6,4,20,24,hexcolor),(6,25,20,4,'#ded5b7'),(6,27,20,1,'#a3977c'),(5,3,4,27,'#332638'),(9,5,15,1,'#b08a49'),(9,23,15,1,'#b08a49'),(20,27,2,4,'#a63245')]
   for row,line in enumerate(glyph):
    for col,on in enumerate(line):
     if on=='1':rects.append((12+col*2,10+row*2,2,2,'#ecdfaa'))
   for x,y in ((9,5),(23,5),(9,22),(23,22)):rects.append((x,y,2,2,'#d6b66a'))
   path=root/f'assets/coresuite/textures/item/{name}.png';path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(png(rects))
   svg='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" shape-rendering="crispEdges">'+''.join(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{c}"/>' for x,y,w,h,c in rects)+'</svg>'
   art=ROOT/f'docs/grimoire/art/{name}.svg';art.parent.mkdir(parents=True,exist_ok=True);art.write_text(svg)
  entries=[]
  for name,cmd,*_ in DESIGNS:
   entries.append((cmd,'coresuite:item/'+name));entries.append((cmd+1,'minecraft:item/book_vanilla'))
  # Exact integer ranges with vanilla fallbacks between our IDs.
  entries=dict(entries)
  for name,cmd,*_ in DESIGNS:entries[cmd]='coresuite:item/'+name
  write(root/'assets/minecraft/models/item/book.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/book'},'overrides':[{'predicate':{'custom_model_data':cmd},'model':model} for cmd,model in sorted(entries.items())]})
  write(root/'assets/minecraft/items/book.json',{'model':{'type':'minecraft:range_dispatch','property':'minecraft:custom_model_data','index':0,'fallback':{'type':'minecraft:model','model':'minecraft:item/book_vanilla'},'entries':[{'threshold':cmd,'model':{'type':'minecraft:model','model':model}} for cmd,model in sorted(entries.items())]}})
 mappings={'format_version':2,'items':{'minecraft:book':[{'type':'legacy','custom_model_data':cmd,'bedrock_identifier':'coresuite:'+name,'display_name':title,'bedrock_options':{'icon':'coresuite.'+name,'allow_offhand':True}} for name,cmd,title,*_ in DESIGNS]}}
 write(ROOT/'deployment/geyser/custom_mappings/coresuite_magic.json',mappings)
 write(ROOT/'src/main/resources/integrations/coresuite_magic.json',mappings)
 print('Built',len(DESIGNS),'native models, SVG sources, icons and crossplay mappings in both local pack trees.')
if __name__=='__main__':build()
