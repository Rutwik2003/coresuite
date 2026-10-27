# Reward codes and playtime

## Reward codes

Module: `reward-codes`. Configure `modules/reward-codes.yml`.

The file supports a legacy top-level code and named entries under `codes`. Before enabling it, replace example codes and review every payout. Do not enable a publicly documented example code on a live economy.

```yaml
codes:
  YOUR_EVENT_CODE:
    enabled: true
    max-uses: 100
    commands:
      - "cratekey give %player% common 1"
```

This is a fragment to merge with your file. The key must exist in ExcellentCrates. Test redemption, repeated redemption and exhausted availability. See the command reference for administrator entry points; use live help for subcommand arguments.

Console reward commands execute with console privileges. Never copy an untrusted command into a reward list. A success message is presentation text, not proof a third-party plugin successfully issued the reward.

## Playtime rewards

Module: `playtime-rewards`. Configure `modules/playtime-rewards.yml`.

Each milestone can define `required-seconds`, icon, display name, lore, money, commands and messages. Time thresholds are in seconds: 300 hours is 1,080,000 seconds.

```yaml
rewards:
  example_300h:
    required-seconds: 1080000
    material: CLOCK
    name: "&b300 Hours"
    money: 10000
    commands: []
    grants: "&f$10,000"
    claim-message: "&aMilestone claimed!"
```

The amount above is an illustration, not a recommended economy balance. Keep display text synchronized with actual money/commands. Review AFK timeout and verify which time is counted before promising real-world hour rewards.

Players use `/playtime` and its reward interface. Administration uses the registered playtime commands and permissions; time corrections and resets should be backed up and checked against claim history. Do not assume resetting time automatically reopens previously claimed rewards.

## Shared reward checklist

Test online and offline targets where supported, full inventories, missing dependencies, missing crate IDs, reconnects and clean restarts. Verify actual inventory/keys/balance rather than relying only on the success message. Do not manually replay uncertain commands without checking whether they already succeeded.
