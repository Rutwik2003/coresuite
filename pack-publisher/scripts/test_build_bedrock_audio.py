import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from build_bedrock_audio import build


class BedrockAudioTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name);self.source=self.root/'pack'
        self.assets=self.source/'assets/coresuite';(self.assets/'sounds/boss').mkdir(parents=True)
        (self.assets/'sounds/boss/test.ogg').write_bytes(b'OggSfixture')
        (self.assets/'sounds.json').write_text(json.dumps({'boss.test':{'sounds':[{'name':'coresuite:boss/test','stream':True}]}}))
    def test_preserves_names_and_packages_referenced_audio(self):
        target=self.root/'pack.mcpack';result=build(self.source,target,'1.0.0')
        with zipfile.ZipFile(target) as z:
            defs=json.loads(z.read('sounds/sound_definitions.json'))['sound_definitions']
            self.assertIn('coresuite:boss.test',defs)
            self.assertEqual(z.read(defs['coresuite:boss.test']['sounds'][0]['name']+'.ogg'),b'OggSfixture')
            self.assertEqual(json.loads(z.read('manifest.json'))['header']['version'],[1,0,0])
        self.assertEqual(result['events'],1)
        original=target.read_bytes();build(self.source,target,'1.0.0');self.assertEqual(original,target.read_bytes())
    def test_missing_audio_fails_without_creating_pack(self):
        (self.assets/'sounds/boss/test.ogg').unlink()
        with self.assertRaises(FileNotFoundError):build(self.source,self.root/'pack.mcpack','1.0.0')
        self.assertFalse((self.root/'pack.mcpack').exists())
    def test_path_traversal_rejected(self):
        (self.assets/'sounds.json').write_text(json.dumps({'boss.test':{'sounds':['coresuite:../../secret']}}))
        with self.assertRaises(ValueError):build(self.source,self.root/'pack.mcpack','1.0.0')


if __name__ == '__main__':unittest.main()
