import yaml

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "r", encoding="utf-8") as f:
    try:
        data = yaml.safe_load(f)
        print("YAML is valid!")
    except Exception as e:
        print(f"YAML parsing failed: {e}")
