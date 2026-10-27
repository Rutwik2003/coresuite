# Pre-launch testing checklist

## Platform and permissions

- Start with no optional integrations and every module disabled.
- Enable one module at a time with its dependencies.
- Test as a normal player, authorized staff and console.
- Confirm denied permissions really prevent privileged actions.
- Check conflicts with other plugins registering the same command.

## Economy and persistence

- Capture balance/items before and after purchases and rewards.
- Test insufficient funds, full inventory and invalid reward IDs.
- Reconnect and restart; verify claims, balances and progression survive.
- Repeat clicks and commands; ensure one action cannot pay twice.
- Restore a backup in a separate folder.

## Gameplay

- Test all four bosses, solo and with a group, including exits and inactivity cleanup.
- Test fishing bags, trade consumption, tournaments and rod upgrades.
- Test order fulfillment and cancellation with multiple accounts before production.
- Test reward codes, playtime milestones and AFK behavior.
- Test protection regions and inventory/world transitions.
- Test magic separately while experimental.

## Pack and integration checks

- Test Java with packs Enabled and Prompt, including reconnects and failed download recovery.
- Test Bedrock independently using Geyser's compatible pack.
- Confirm Discord/website outputs go only to your configured destinations.
- Check logs for exceptions during clean shutdown.

Unit tests and smoke startup checks complement these tests; they do not replace real gameplay and third-party integration verification.
