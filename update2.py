import yaml

with open('src/main/resources/modules/custom-fishing.yml', 'r') as f:
    data = yaml.safe_load(f)

data['legendary-quest']['map-drop-chance'] = 0.00005

with open('src/main/resources/modules/custom-fishing.yml', 'w') as f:
    yaml.dump(data, f, sort_keys=False)

print('Success')
