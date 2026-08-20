import re

files = [
    r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java",
    r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\FishingCollectionManager.java",
    r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\FishingLeaderboardManager.java"
]

for file in files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Replace itemModel with customModelData support
    content = content.replace(
        'String itemModel = config.getString(spPath + "item-model", "");',
        'String itemModel = config.getString(spPath + "item-model", "");\n                        int cmd = config.getInt(spPath + "custom-model-data", -1);'
    )
    content = content.replace(
        'String itemModel = module.getConfig().getString(spPath + "item-model", "");',
        'String itemModel = module.getConfig().getString(spPath + "item-model", "");\n                            int cmd = module.getConfig().getInt(spPath + "custom-model-data", -1);'
    )
    
    content = content.replace(
        'if (itemModel != null && !itemModel.isEmpty()) {\n                                    meta.setItemModel(NamespacedKey.fromString(itemModel));\n                                }',
        'if (itemModel != null && !itemModel.isEmpty()) {\n                                    meta.setItemModel(NamespacedKey.fromString(itemModel));\n                                }\n                                if (cmd != -1) {\n                                    meta.setCustomModelData(cmd);\n                                }'
    )
    
    content = content.replace(
        'if (!itemModel.isEmpty()) {\n                            meta.setItemModel(NamespacedKey.fromString(itemModel));\n                        }',
        'if (!itemModel.isEmpty()) {\n                            meta.setItemModel(NamespacedKey.fromString(itemModel));\n                        }\n                        if (cmd != -1) {\n                            meta.setCustomModelData(cmd);\n                        }'
    )
    
    with open(file, 'w', encoding='utf-8') as f:
        f.write(content)
