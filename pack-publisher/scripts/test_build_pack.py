import json
from pathlib import Path
import uuid
import unittest
from build_pack import build

class PackBuilderTest(unittest.TestCase):
    def setUp(self):
        base = (Path.cwd() / 'build' / 'pack-tests').resolve()
        base.mkdir(parents=True, exist_ok=True)
        self.root = base / uuid.uuid4().hex
        self.root.mkdir()
        self.source = self.root / 'pack'
        self.source.mkdir()
        (self.source / 'pack.mcmeta').write_text('{"pack":{"pack_format":34}}')
    def test_deterministic_pack_and_manifest(self):
        build(self.source, self.root/'one', 'owner/pack', 'v1')
        build(self.source, self.root/'two', 'owner/pack', 'v1')
        self.assertEqual((self.root/'one/meermc-pack.zip').read_bytes(), (self.root/'two/meermc-pack.zip').read_bytes())
        self.assertIn('/releases/download/v1/', (self.root/'one/manifest.properties').read_text())
    def test_missing_sound_aborts(self):
        assets = self.source/'assets/coresuite'
        assets.mkdir(parents=True)
        (assets/'sounds.json').write_text(json.dumps({'boss':{'sounds':['coresuite:missing']}}))
        with self.assertRaisesRegex(ValueError, 'missing audio'):
            build(self.source, self.root/'out', 'owner/pack', 'v1')
    def test_secrets_and_nested_outputs_rejected(self):
        with self.assertRaises(ValueError):
            build(self.source, self.source/'dist', 'owner/pack', 'v1')
        (self.source/'.env').write_text('test')
        with self.assertRaisesRegex(ValueError, 'hidden files'):
            build(self.source, self.root/'out', 'owner/pack', 'v1')
if __name__ == '__main__':
    unittest.main()
