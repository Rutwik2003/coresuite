import json
import unittest
from PIL import Image
from pathlib import Path
from build_magic_models import ROOT, DESIGNS

class MagicAssetsTest(unittest.TestCase):
 def test_custom_covers_have_valid_animation_frames_and_no_vanilla_materials(self):
  root=ROOT/'resourcepack'
  for name,*_ in DESIGNS:
   model=json.loads((root/f'assets/coresuite/models/item/{name}.json').read_text())
   self.assertTrue(all(value.startswith('coresuite:') for value in model['textures'].values()))
   texture=root/f'assets/coresuite/textures/item/{name}_cover.png'
   with Image.open(texture) as im:self.assertEqual(im.size,(128,1024))
   self.assertEqual(json.loads(Path(str(texture)+'.mcmeta').read_text())['animation']['frametime'],5)
 def test_builder_targets_repository_root_and_all_models_have_geometry(self):
  self.assertEqual(ROOT,Path(__file__).resolve().parents[2])
  root=ROOT/'resourcepack'
  for name,*_ in DESIGNS:
   model=json.loads((root/f'assets/coresuite/models/item/{name}.json').read_text())
   self.assertNotIn('parent',model)
   self.assertTrue(model['elements'])
   self.assertLessEqual(model['display']['firstperson_righthand']['scale'][0],.5)
   for element in model['elements']:
    self.assertTrue(all(a<b for a,b in zip(element['from'],element['to'])))
 def test_modern_legacy_and_geyser_ids_agree(self):
  root=ROOT/'resourcepack'
  mappings=json.loads((ROOT/'deployment/geyser/custom_mappings/coresuite_magic.json').read_text())['items']
  for material,items in mappings.items():
   base=material.split(':')[1]
   modern=json.loads((root/f'assets/minecraft/items/{base}.json').read_text())['model']['entries']
   legacy=json.loads((root/f'assets/minecraft/models/item/{base}.json').read_text())['overrides']
   for item in items:
    cmd=item['custom_model_data']
    current=next(e['model']['model'] for e in modern if e['threshold']==cmd)
    self.assertEqual(current,next(e['model'] for e in legacy if e['predicate']['custom_model_data']==cmd))
    icon=item['bedrock_options']['icon'].split('.')[1]
    self.assertTrue((root/f'assets/coresuite/textures/item/{icon}.png').exists())
