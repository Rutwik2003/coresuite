# Version compatibility update

For Java 1.21–1.21.3, run from the repository root:

```powershell
python scripts/build_legacy_fish_pack.py
```

Use `build/resourcepacks/CoreSuite-FishPack-1.21-1.21.3.zip` with the updated plugin. The ZIP has `pack.mcmeta` at its root. The generator preserves the source pack and validates all reachable custom models/textures for 190 configured fish and the unknown-fish icon. Stable IDs are maintained in `src/main/resources/compat/fish-models.properties`; do not renumber existing IDs. After adding fish artwork/configuration, use `--update-mapping` to append new IDs, then rebuild the plugin and pack together.

The legacy pack uses static fallback artwork; it does not reproduce newer seasonal, name-based plated-food variants or Bedrock rendering. Paper 1.21/1.21.1 receives matching CustomModelData, while 1.21.2/1.21.3 uses supplied item-model aliases. Fish items created before this fallback existed do not automatically receive new metadata. Client visual testing is still required before deploying a pack globally.

The source pack is intended for newer clients using `assets/<namespace>/items` definitions. Mojang introduced `item_model` in [1.21.2](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-2) and replaced the item model format in [1.21.4](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4).

The older seven-texture setup example below is retained for reference; it does not describe the current full fish catalogue.

---

# CoreSuite Custom Fishing - Resource Pack Setup Guide

## How It Works

The custom fishing module now uses **CustomModelData** to give each fish species a unique custom texture.
Each caught fish is a real vanilla fish item (COD, SALMON, etc.) but with a `CustomModelData` tag that
tells the resource pack to render a custom texture instead of the default one.

## Resource Pack Structure

```
resourcepack/
├── pack.mcmeta
└── assets/
    ├── minecraft/models/item/       ← Overrides vanilla fish models
    │   ├── cod.json                 (CustomModelData 10001, 10006, 10007)
    │   ├── salmon.json              (CustomModelData 10002)
    │   ├── pufferfish.json          (CustomModelData 10003)
    │   └── tropical_fish.json       (CustomModelData 10004, 10005)
    └── coresuite/
        ├── models/item/             ← Custom model definitions
        │   ├── fish_cod.json
        │   ├── fish_salmon.json
        │   ├── fish_pufferfish.json
        │   ├── fish_bluegill.json
        │   ├── fish_koi.json
        │   ├── fish_golden_tuna.json
        │   └── fish_kraken.json
        └── textures/item/           ← YOUR CUSTOM TEXTURES GO HERE
            ├── fish_cod.png         (16x16 or 32x32 PNG)
            ├── fish_salmon.png
            ├── fish_pufferfish.png
            ├── fish_bluegill.png
            ├── fish_koi.png
            ├── fish_golden_tuna.png
            └── fish_kraken.png
```

## Step-by-Step Setup

### 1. Create Your Fish Textures
Replace the placeholder PNG files in `resourcepack/assets/coresuite/textures/item/` with your
own custom 16x16 or 32x32 pixel art fish textures. Each PNG must be named exactly as listed above.

You can create these using:
- **Piskel** (free, browser-based): https://www.piskelapp.com
- **Aseprite** (paid, professional): https://www.aseprite.org
- **LibreSprite** (free Aseprite fork): https://libresprite.github.io

### 2. Zip the Resource Pack
```
cd resourcepack
# Select ALL contents (pack.mcmeta + assets/) and zip them
# The zip should NOT contain a "resourcepack" parent folder
```
**Important:** `pack.mcmeta` must be at the ROOT of the zip, not inside a subfolder.

### 3. Upload to mc-packs.net
1. Go to https://mc-packs.net
2. Upload your `.zip` file
3. Copy the **Download URL** and **SHA-1 hash** provided

### 4. Configure server.properties
Add these lines to your `server.properties`:
```
resource-pack=https://download.mc-packs.net/YOUR_PACK_ID
resource-pack-sha1=YOUR_SHA1_HASH
require-resource-pack=true
resource-pack-prompt=CoreSuite Custom Fishing Textures
```

### 5. Restart Server
Restart your server. Players will be prompted to download the resource pack when they join.

## Adding New Fish Species

To add a new species:
1. Pick a `CustomModelData` ID (e.g. 10008)
2. Add a line to `custom-fishing.yml`:
   ```
   - "5.0|&bAnglerfish|10.0|50.0|30.0|COD|10008"
   ```
3. Create `fish_anglerfish.json` in `assets/coresuite/models/item/`
4. Create `fish_anglerfish.png` in `assets/coresuite/textures/item/`
5. Add the override to `assets/minecraft/models/item/cod.json`
6. Re-zip, re-upload, update SHA-1
