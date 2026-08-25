import yaml

with open('src/main/resources/modules/custom-fishing.yml', 'r') as f:
    data = yaml.safe_load(f)

data['legendary-quest']['pity-threshold'] = 2500

with open('src/main/resources/modules/custom-fishing.yml', 'w') as f:
    yaml.dump(data, f, sort_keys=False)

print('Success')
