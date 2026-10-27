# Fishing, bags and progression

Module: `custom-fishing`. Configuration: `modules/custom-fishing.yml`.

Fishing includes custom catches, collections, rod upgrades, bait, fish bags, tournaments and Angler-related progression. Start with the configured reward economy and asset setup before enabling it publicly.

## Owner setup

1. Read catch tables, rarity weights, sale rewards and command loot in the module reference.
2. Replace crate IDs and remove commands for plugins you do not install.
3. Configure upgrade costs and progression so rewards fit your economy.
4. Verify your resource pack maps the configured custom items. Missing textures do not necessarily mean catch data is missing.
5. Test normal fishing, bag storage and trading as a non-OP player.

Some fishing reward rows encode fields separated by `|`. Preserve the field order; change only the intended command or weight. The command adapter migrates legacy crate commands in supported rows without deliberately changing the other fields.

## Player workflow

Use `/fish` to access the module's available menus. Catch fish, inspect collections, manage storage and follow offered quests. The bag is gameplay storage, not a second source of free items: always test item conservation when moving stacks.

For the Wandering Angler, test both inventory-held and bag-held fish. A successful exchange should consume the requested quantity exactly once. Quest selection dialogue and ordinary repeat conversations should not unexpectedly reset progression.

## Important tests

- Move fish between inventory and every bag page; reconnect and verify quantities.
- Spam next/previous page controls and confirm they do not become unrelated actions.
- Test full inventories and partial stacks.
- Confirm custom fish can be distinguished from renamed ordinary fish.
- Complete a tournament and check rewards exactly once.
- Verify upgrades persist through restart.

There is no owner-only maximum-rod command in the current source; the previous owner command was removed.

## Assets

The linked All The Fish pack is third-party ARR content. Free access does not grant redistribution permission. Obtain it from the original author under their terms; do not repackage it with CoreSuite without permission. See [project status](project.md).
