"""Build a Bedrock audio overlay from CoreSuite's Java pack; no texture conversion."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import uuid
import zipfile


def build(source, output, version):
    source, output = Path(source).resolve(), Path(output).resolve()
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise ValueError('version must be three nonnegative integers, e.g. 1.0.0')
    numbers = list(map(int, version.split('.')))
    if any(n > 65535 for n in numbers):
        raise ValueError('version components must not exceed 65535')
    if output == source or source in output.parents:
        raise ValueError('output must be outside source')
    definitions = json.loads((source / 'assets/coresuite/sounds.json').read_text(encoding='utf-8-sig'))
    sounds, files = {}, {}
    for event, definition in definitions.items():
        if not re.fullmatch(r'[a-z0-9_./-]+', event):
            raise ValueError('invalid sound identifier')
        converted = []
        for entry in definition.get('sounds', []):
            entry = {'name': entry} if isinstance(entry, str) else entry
            if entry.get('type', 'file') != 'file':
                raise ValueError('event aliases require explicit conversion: ' + event)
            name = entry['name']
            namespace, name = name.split(':', 1) if ':' in name else ('coresuite', name)
            if not re.fullmatch(r'[a-z0-9_-]+', namespace) or not re.fullmatch(r'[a-z0-9_/-]+', name):
                raise ValueError('invalid audio path')
            path = source / 'assets' / namespace / 'sounds' / (name + '.ogg')
            if source not in path.resolve().parents or path.is_symlink():
                raise ValueError('audio must stay within source')
            data = path.read_bytes()
            if not data.startswith(b'OggS'):
                raise ValueError('audio is not an Ogg file: ' + name)
            target = 'sounds/' + namespace + '/' + name
            files[target + '.ogg'] = data
            sound = {'name': target, 'stream': entry.get('stream', True)}
            for key in ('volume', 'pitch', 'weight'):
                if key in entry: sound[key] = entry[key]
            converted.append(sound)
        if not converted:
            raise ValueError('empty sound event: ' + event)
        category = definition.get('category', 'record')
        if category not in {'ambient','block','hostile','music','neutral','player','record','ui','weather'}:
            raise ValueError('unsupported category: ' + category)
        sounds['coresuite:' + event] = {'category': category, 'sounds': converted}
    if not sounds:
        raise ValueError('no CoreSuite sounds found')
    stable = lambda name: str(uuid.uuid5(uuid.NAMESPACE_URL, 'https://meermc/bedrock-audio/' + name))
    manifest = {'format_version': 2, 'header': {'name': 'MeerMC Boss Music',
        'description': 'CoreSuite boss music and rift audio; use alongside the existing Bedrock texture pack.',
        'uuid': stable('header'), 'version': numbers, 'min_engine_version': [1,21,0]},
        'modules': [{'type': 'resources', 'uuid': stable('resources'), 'version': numbers}]}
    texture = source / 'assets/coresuite/textures/item/astral_grimoire.png'
    if texture.is_file():
        if texture.is_symlink() or not texture.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):
            raise ValueError('invalid magic texture')
        files['textures/items/coresuite_astral_grimoire.png'] = texture.read_bytes()
        files['textures/item_texture.json'] = json.dumps({'resource_pack_name':'coresuite_magic',
            'texture_name':'atlas.items','texture_data':{'coresuite.astral_grimoire':
            {'textures':'textures/items/coresuite_astral_grimoire'}}},indent=2).encode()
        manifest['header']['name'] = 'MeerMC Music & Magic'
    files['manifest.json'] = json.dumps(manifest, indent=2).encode()
    files['sounds/sound_definitions.json'] = json.dumps({'format_version': '1.14.0', 'sound_definitions': sounds}, indent=2).encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, date_time=(2020,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    result = {'events': len(sounds), 'audio_files': sum(name.endswith('.ogg') for name in files), 'version': version,
              'sha256': hashlib.sha256(output.read_bytes()).hexdigest()}
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', default='pack')
    parser.add_argument('--output', default='dist/meermc-bedrock-audio.mcpack')
    parser.add_argument('--version', required=True)
    parser.add_argument('--repository')
    parser.add_argument('--release')
    args = parser.parse_args()
    if args.repository or args.release:
        if not args.repository or not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',args.repository):
            parser.error('valid --repository required for a release')
        if not args.release or not re.fullmatch(r'[A-Za-z0-9._-]{1,100}',args.release):
            parser.error('valid --release required')
    print(json.dumps(build(args.source, args.output, args.version), indent=2))
    if args.repository:
        archive=Path(args.output);data=archive.read_bytes()
        (archive.parent/'bedrock-manifest.properties').write_text(
            f'version={args.release}\nurl=https://github.com/{args.repository}/releases/download/{args.release}/meermc-bedrock-audio.mcpack\n'
            f'sha1={hashlib.sha1(data).hexdigest()}\nsize={len(data)}\n',encoding='utf-8')
