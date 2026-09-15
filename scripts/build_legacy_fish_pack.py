"""Build the supplied fish artwork for Java 1.21-1.21.3; never rewrites the source pack."""
import argparse, json, zipfile, re
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--update-mapping', action='store_true', help='Append IDs for newly supplied models, preserving all existing IDs')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
source = root / 'resourcepack'
mapping_file = root / 'src/main/resources/compat/fish-models.properties'
mapping = {}
if mapping_file.exists():
    for line in mapping_file.read_text().splitlines():
        if line and not line.startswith('#'):
            key, value = line.split('='); mapping[key.replace('\\:', ':')] = int(value)
def static_model(node):
    kind = node.get('type', '').removeprefix('minecraft:')
    if kind == 'model': return node['model']
    if kind == 'select': return static_model(node['fallback'])
    if kind == 'condition': return static_model(node['on_false'])
    raise ValueError('No safe legacy static fallback for ' + kind)
models = {}
for path in sorted((source / 'assets/barely_default/items').glob('fish_*.json')):
    models['barely_default:' + path.stem] = static_model(json.loads(path.read_text())['model'])
configured = set(re.findall(r"item-model:\s*['\"]?([a-z0-9_]+:[a-z0-9_/]+)", (root / 'src/main/resources/modules/custom-fishing.yml').read_text()))
configured.add('barely_default:fish_unknown')
if configured - set(models): raise ValueError('Configured fish model definitions missing: ' + str(configured - set(models)))
models = {key: value for key, value in models.items() if key in configured}
missing = sorted(set(models) - set(mapping))
if missing and not args.update_mapping: raise SystemExit('New fish models need --update-mapping: ' + ', '.join(missing))
next_id = max(mapping.values(), default=999999) + 1
for key in missing: mapping[key] = next_id; next_id += 1
if len(set(mapping.values())) != len(mapping): raise ValueError('Duplicate custom model IDs')
if args.update_mapping:
    mapping_file.parent.mkdir(parents=True, exist_ok=True)
    mapping_file.write_text('# Stable legacy fish IDs. Append only; existing items retain these numbers.\n' + ''.join(key.replace(':', '\\:') + '=' + str(value) + '\n' for key, value in sorted(mapping.items())))
entries = {}
for path in source.rglob('*'):
    if not path.is_file(): continue
    relative = path.relative_to(source).as_posix()
    if relative.startswith('assets/') and '/items/' not in relative or relative == 'pack.png':
        data = path.read_bytes()
        if '/models/' in relative and relative.endswith('.json'):
            model = json.loads(data)
            model.pop('overrides', None)  # Legacy fish uses the ordinary static artwork, without optional plated-food variants.
            data = json.dumps(model).encode()
        entries[relative] = data
for key, model in models.items():
    namespace, name = key.split(':', 1)
    model_namespace, model_path = model.split(':', 1) if ':' in model else ('minecraft', model)
    reference = f'assets/{model_namespace}/models/{model_path}.json'
    if reference not in entries:
        if key not in ('barely_default:fish_ash_eater', 'barely_default:fish_cavefish'): raise ValueError('Missing fish model: ' + reference)
        print('Retired model has no supplied artwork; using unknown fish: ' + key)
        model = models[key] = 'barely_default:item/fish_unknown'
        reference = 'assets/barely_default/models/item/fish_unknown.json'
    alias = f'assets/{namespace}/models/item/{name}.json'
    if alias != reference: entries[alias] = json.dumps({'parent': model}).encode()
for material in ('cod', 'salmon', 'pufferfish', 'tropical_fish'):
    entries[f'assets/coresuite/models/item/legacy/{material}.json'] = json.dumps({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/' + material}}).encode()
    overrides = []
    for key in sorted(models, key=lambda value: mapping[value]):
        overrides.append({'predicate': {'custom_model_data': mapping[key]}, 'model': models[key]})
        # Predicate thresholds are >=; reset the immediately following value.
        overrides.append({'predicate': {'custom_model_data': mapping[key] + 1}, 'model': 'coresuite:item/legacy/' + material})
    entries[f'assets/minecraft/models/item/{material}.json'] = json.dumps({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/' + material}, 'overrides': overrides}).encode()
# Keep and validate only models reachable from fish overrides and item-model aliases.
reachable = set()
def model_entry(reference):
    namespace, path = reference.split(':', 1) if ':' in reference else ('minecraft', reference)
    return f'assets/{namespace}/models/{path}.json'
def visit(name):
    if name in reachable: return
    if name not in entries:
        if name in ('assets/minecraft/models/item/generated.json', 'assets/minecraft/models/builtin/generated.json'): return
        raise ValueError('Missing reachable model: ' + name)
    reachable.add(name)
    data = json.loads(entries[name])
    if 'parent' in data: visit(model_entry(data['parent']))
    for texture in data.get('textures', {}).values():
        if texture.startswith('#'): continue
        namespace, path = texture.split(':', 1) if ':' in texture else ('minecraft', texture)
        if namespace != 'minecraft' and f'assets/{namespace}/textures/{path}.png' not in entries:
            raise ValueError('Missing reachable texture: ' + texture)
    for override in data.get('overrides', []): visit(model_entry(override['model']))
for key in models:
    namespace, name = key.split(':', 1)
    visit(f'assets/{namespace}/models/item/{name}.json')
for material in ('cod', 'salmon', 'pufferfish', 'tropical_fish'):
    visit(f'assets/minecraft/models/item/{material}.json')
entries = {name: data for name, data in entries.items() if '/models/' not in name or name in reachable}
entries['pack.mcmeta'] = json.dumps({'pack': {'pack_format': 34, 'supported_formats': [34, 42], 'description': 'CoreSuite fish - Java 1.21 through 1.21.3 (static legacy variants)'}}).encode()
output = root / 'build/resourcepacks/CoreSuite-FishPack-1.21-1.21.3.zip'
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name, data in sorted(entries.items()): archive.writestr(name, data)
print(f'Built {output}: {len(models)} mapped fish models, {len(entries)} files')
