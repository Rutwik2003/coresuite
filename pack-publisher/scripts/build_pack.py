"""Build a reproducible pack and a manifest for an immutable GitHub release."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile


def build(source, output, repository, version):
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('repository must be owner/name')
    if not re.fullmatch(r'[A-Za-z0-9._-]{1,100}', version):
        raise ValueError('invalid release version')
    source, output = Path(source).resolve(), Path(output).resolve()
    if source == output or source in output.parents:
        raise ValueError('output must be outside the pack source')
    metadata = source / 'pack.mcmeta'
    if not isinstance(json.loads(metadata.read_text(encoding='utf-8-sig')).get('pack'), dict):
        raise ValueError('missing pack metadata')
    files = sorted(p for p in source.rglob('*') if p.is_file())
    for path in files:
        if path.is_symlink() or source not in path.resolve().parents:
            raise ValueError('symlinks are not pack assets')
        relative = path.relative_to(source)
        if any(part.startswith('.') for part in relative.parts):
            raise ValueError('hidden files are not pack assets: ' + str(relative))
        if relative.parts[0] not in ('assets', 'pack.mcmeta', 'pack.png', 'LICENSE', 'LICENSE.txt', 'CREDITS.txt'):
            raise ValueError('unexpected file in pack: ' + str(relative))
        if path.suffix == '.json':
            json.loads(path.read_text(encoding='utf-8-sig'))
    # Verify custom sound references before publishing broken BGM.
    for sounds in source.glob('assets/*/sounds.json'):
        namespace = sounds.parent.name
        for event in json.loads(sounds.read_text(encoding='utf-8-sig')).values():
            for sound in event.get('sounds', []):
                if isinstance(sound, dict) and sound.get('type') == 'event':
                    continue
                name = sound if isinstance(sound, str) else sound['name']
                ns, name = name.split(':', 1) if ':' in name else (namespace, name)
                if ns != 'minecraft' and not (source / 'assets' / ns / 'sounds' / (name + '.ogg')).is_file():
                    raise ValueError('missing audio: ' + ns + ':' + name)
    output.mkdir(parents=True, exist_ok=True)
    archive = output / 'meermc-pack.zip'
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zipped:
        for path in files:
            info = zipfile.ZipInfo(path.relative_to(source).as_posix(), date_time=(2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            zipped.writestr(info, path.read_bytes())
    data = archive.read_bytes()
    if len(data) > 128 * 1024 * 1024:
        raise ValueError('pack exceeds 128 MiB')
    digest = hashlib.sha1(data).hexdigest()
    (output / 'manifest.properties').write_text(
        f'version={version}\nurl=https://github.com/{repository}/releases/download/{version}/meermc-pack.zip\n'
        f'sha1={digest}\nsize={len(data)}\n', encoding='utf-8')
    print(f'Built {len(files)} files, {len(data)} bytes, SHA-1 {digest}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', default='pack')
    parser.add_argument('--output', default='dist')
    parser.add_argument('--repository', required=True)
    parser.add_argument('--version', required=True)
    args = parser.parse_args()
    build(args.source, args.output, args.repository, args.version)
