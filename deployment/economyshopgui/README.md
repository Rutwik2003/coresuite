# Approved EconomyShopGUI update

Only these files are included:

- `shops/Building/natural.yml`: original pages plus 26 sulfur/cinnabar blocks on page 4, buy-only.
- `shops/Z_EverythingElse.yml`: six copper slab sell prices reduced from 40 to 30; three waxed copper purchase prices increased from 300 to 400.

Back up live files, stop the server, and copy these two files into the EconomyShopGUI plugin folder with the same relative paths. Restart and verify loading and menus. These are full files based on the supplied snapshot, so compare any newer live edits before replacing them. Do not copy this folder over a complete shop installation as a replacement.

No armor/tool rebalancing, rare gear additions, potion changes, spawner edits, database changes or global shop configuration changes are included. The discarded broad update must not be deployed.

See `docs/shop-audit/README.md` at the repository root for prices, findings and event proposals. Deployment ZIPs and original backups remain local under `.local/archive/scratch/shop-audit/`.
