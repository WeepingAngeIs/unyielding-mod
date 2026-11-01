"""Bake authoring per-face UVs to Cobblemon 1.8.4's integer box UV format.

Runs during the cloud build. A uniform 16x authoring scale, compensated in
a root-bone pose transform, retains eye detail instead of reducing it to 3 pixels.
Mawonna assets are never touched.
"""
import copy, io, json, math
from PIL import Image

SCALE=16
def layout(size,u=0,v=0):
 x,y,z=size
 return {'east':(u,v+z,z,y),'north':(u+z,v+z,x,y),
         'west':(u+z+x,v+z,z,y),'south':(u+z+x+z,v+z,x,y),
         'up':(u+z+x,v+z,-x,-z),'down':(u+z+2*x,v,-x,z)}

def convert(files):
 prefix='assets/mawonna/'
 gp=prefix+'bedrock/pokemon/models/pink_corvisquire/pink_corvisquire.geo.json'
 tp=prefix+'textures/pokemon/pink_corvisquire/pink_corvisquire.png'
 ap=prefix+'bedrock/pokemon/animations/pink_corvisquire/pink_corvisquire.animation.json'
 pp=prefix+'bedrock/pokemon/posers/pink_corvisquire.json'
 sp='data/cobblemon/species/custom/covislight.json'
 geo=json.loads(files[gp]); model=geo['minecraft:geometry'][0]
 original=Image.open(io.BytesIO(files[tp])).convert('RGBA')
 atlas=Image.new('RGBA',(4096,4096)); px=2;py=2;row=0;count=0
 def scaled(v):
  if isinstance(v,list):return [scaled(x) for x in v]
  if isinstance(v,dict):return {k:(x if k in ('lerp_mode',) else scaled(x)) for k,x in v.items()}
  if isinstance(v,str):return '('+v+')*16'
  return v*SCALE
 for bone in model['bones']:
  if 'pivot' in bone:bone['pivot']=scaled(bone['pivot'])
  for loc in bone.get('locators',{}).values():
   if isinstance(loc,dict) and 'offset' in loc:loc['offset']=scaled(loc['offset'])
  for key,loc in list(bone.get('locators',{}).items()):
   if isinstance(loc,list):bone['locators'][key]=scaled(loc)
  for cube in bone.get('cubes',[]):
   size=cube['size']; uv=cube['uv']
   source=layout(size,*uv) if isinstance(uv,list) else {f:(*d['uv'],*d['uv_size']) for f,d in uv.items()}
   target_size=scaled(size)
   # Minecraft's box net uses float dimensions; rasterise at pixel centres.
   width=math.ceil(2*(target_size[0]+target_size[2]))+4
   height=math.ceil(target_size[1]+target_size[2])+4
   if px+width>4096:px=2;py+=row;row=0
   assert py+height<4096,'Texture atlas overflow'
   net=layout(target_size,px,py)
   for face,(u,v,w,h) in net.items():
    if not w or not h or face not in source:continue
    su,sv,sw,sh=source[face]
    for yy in range(math.floor(min(v,v+h)),math.ceil(max(v,v+h))):
     fy=(yy+.5-v)/h
     if not 0<=fy<=1:continue
     for xx in range(math.floor(min(u,u+w)),math.ceil(max(u,u+w))):
      fx=(xx+.5-u)/w
      if not 0<=fx<=1:continue
      sx=max(0,min(original.width-1,math.floor(su+fx*sw)))
      sy=max(0,min(original.height-1,math.floor(sv+fy*sh)))
      atlas.putpixel((xx,yy),original.getpixel((sx,sy)))
   cube['uv']=[px,py]
   for key in ('origin','size','pivot','inflate'):
    if key in cube:cube[key]=scaled(cube[key])
   px+=width;row=max(row,height);count+=1
 model['description']['texture_width']=4096;model['description']['texture_height']=4096
 for key in ('visible_bounds_width','visible_bounds_height','visible_bounds_offset'):
  if key in model['description']:model['description'][key]=scaled(model['description'][key])
 animations=json.loads(files[ap])
 for clip in animations['animations'].values():
  for bone_name,channels in clip.get('bones',{}).items():
   if bone_name != 'corvisquire' and 'position' in channels:channels['position']=scaled(channels['position'])
 poser=json.loads(files[pp])
 # Compensate inside the model so existing server species/baseScale still work.
 # The root pivot is zero and no gameplay animation translates this root.
 assert next(b for b in model['bones'] if b['name']=='corvisquire')['pivot']==[0,0,0]
 for pose in poser['poses'].values():
  pose.setdefault('transformedParts',[]).append({'part':'corvisquire','scale':1/SCALE})
 for path,obj in [(gp,geo),(ap,animations),(pp,poser)]:files[path]=json.dumps(obj,indent=2).encode()
 buf=io.BytesIO();atlas.save(buf,format='PNG');files[tp]=buf.getvalue()
 assert all(isinstance(c['uv'],list) for b in model['bones'] for c in b.get('cubes',[]))
 return {'converted_cubes':count,'atlas_size':4096,'geometry_scale':SCALE,'world_scale_compensated':True,'server_baseScale_unchanged':True}
