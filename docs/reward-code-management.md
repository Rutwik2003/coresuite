# Managing reward codes

Requires `coresuite.rewardcodes.admin` or `coresuite.reward.admin`.
Players redeem with `/reward <code>`.

## Create a code with several rewards

```text
/rewardadmin create SUMMER 100
/rewardadmin addcommand SUMMER uc give key 1 %player%
/rewardadmin addcommand SUMMER crates key give %player% Rare 1
/rewardadmin info SUMMER
/rewardadmin enable SUMMER
```

The code starts disabled until you enable it. The limit is the total number of
successful reservations, across players; `0` means unlimited. Each player may
redeem a code once. The existing IP restriction still applies.

## Edit an existing code

```text
/rewardadmin codes
/rewardadmin info SUMMER
/rewardadmin addcommand SUMMER give %player% diamond 4
/rewardadmin setcommand SUMMER 2 crates key give %player% Rare 2
/rewardadmin removecommand SUMMER 3
/rewardadmin setlimit SUMMER 200
/rewardadmin disable SUMMER
```

Command numbers start at 1 and are shown by `info`. Each `addcommand` adds one
whole console command, including its arguments; it never replaces earlier
commands. Use `%player%` or `%player_name%` for the recipient. Edits save to
`modules/reward-codes.yml` immediately. The original default code is editable too.
The older `/rewardcodes admin ...` prefix also supports these editor actions.

Creating a duplicate code is rejected. Disabling or editing a code preserves its
redemption history. Console command failure is logged for administrator review;
some commands may already have paid out, so do not reset and retry blindly.
