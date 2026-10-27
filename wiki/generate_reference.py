"""Generate documentation snapshots from a reviewed public-preview build, not server data."""
from pathlib import Path
import re
import html
import yaml

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / 'wiki/docs'
RES = ROOT / 'build/resources/main'
if not RES.exists():
    raise SystemExit('Build with -PpublicPreview=true first.')
master = yaml.safe_load((RES / 'config.yml').read_text(encoding='utf-8'))
if any(master['modules'].values()):
    raise SystemExit('Refusing to document non-public defaults: build publicPreview first.')
descriptor = yaml.safe_load((RES / 'plugin.yml').read_text(encoding='utf-8'))
def cell(value):
    return html.escape(str(value).replace('<command>', 'COMMAND')).replace('|', '&#124;').replace('\n', '<br>')
def write(path, content):
    dest = DOCS / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(content, encoding='utf-8')

commands = ['# Command reference', '', 'Generated from the current plugin descriptor. These are registered entry points; module handlers may expose additional subcommands or permission checks. A registered command does not mean its module is enabled. Replace COMMAND in usage text with the command name.', '', '| Command | Description | Declared usage | Permission | Aliases |', '|---|---|---|---|---|']
for name, spec in descriptor['commands'].items():
    commands.append('| ' + ' | '.join(cell(v) for v in ['/'+name, spec.get('description',''), spec.get('usage','Use in-game help/tab completion'), spec.get('permission','Not declared here; handler checks may apply'), ', '.join(spec.get('aliases',[]))]) + ' |')
write('reference/commands.md', '\n'.join(commands)+'\n')
permissions = ['# Permission reference', '', 'Generated from plugin.yml. `true` means granted by default; `op` means operator default. Permissions declared only inside handlers may not appear here. Avoid granting wildcard administration to ordinary players.', '', '| Node | Default | Description | Children |', '|---|---|---|---|']
for name, spec in descriptor.get('permissions',{}).items():
    permissions.append('| ' + ' | '.join(cell(v) for v in [name, spec.get('default','Server default'), spec.get('description',''), spec.get('children',{})])+' |')
write('reference/permissions.md','\n'.join(permissions)+'\n')

purposes = {
'admin':'Administrative server tools. Restrict access to trusted staff and test destructive operations on staging.',
'announce':'Server announcement presentation and delivery. Replace messages with your own branding.',
'archaeology-rush':'Archaeology fragments, relic progression and curator rewards. Test completed brushing and repeat-use exploit prevention.',
'auto-messenger':'Scheduled chat announcements. Replace example links and choose a non-intrusive interval.',
'bank-notes':'Money and experience withdrawal items. Test redemption, balance messages and item duplication resistance.',
'chat-games':'Timed chat challenges and rewards. Review answer handling, timing and payout commands.',
'chat-mentions':'Player mention formatting and feedback. Test alongside your chat formatter.',
'clear-lag':'Scheduled entity cleanup. Review entity exclusions and warnings; this is not a universal lag fix.',
'combat-tag':'Combat restrictions and tagging. Test death, disconnect and teleport behavior.',
'creator':'Creator codes and configurable perks. Review rank commands and expiry before granting access.',
'custom-fishing':'Custom catches, bags, upgrades, quests and tournaments. See the fishing guide for acceptance tests.',
'discord':'Discord bot and notification integration. Configure your own credentials, channels and privacy expectations.',
'gambling':'Casino games and player wagers. Requires an economy; audit accounts can alter actual odds and must be reviewed transparently.',
'grimoire':'Experimental wand and spell progression. See the magic guide and test with fresh profiles.',
'guide':'Player guidance and configured guide rewards. Verify all commands and completion requirements.',
'harvest-market':'Seasonal crop markets, mutations and farming rewards. Test payouts against your other shops to avoid loops.',
'jump-pad':'Configured launch pads. Test landing safety, permissions and anti-cheat interactions.',
'kill-podium':'Kill podium presentation. Review location/entity behavior before enabling.',
'maintenance':'Maintenance plugin state integration. Test server status notifications when maintenance changes.',
'orders-board':'Player resource orders and escrow-backed fulfillment. Test partial fills, cancellations and concurrent actions.',
'playtime-rewards':'Playtime tracking and milestone rewards. Keep display text synchronized with actual payouts.',
'profile':'Player profile presentation. Verify dependencies and visible information.',
'referral':'Referral tracking and rewards. Test repeat claims and self-referral prevention.',
'resource-pack':'Optional pack verification and delivery. Configure your own licensed compatible pack; see the pack guide.',
'reward-codes':'Redeemable reward codes. Replace public example codes and review console commands.',
'side-quests':'Experimental quest objectives and progression alongside magic and encounters.',
'store':'Configured store presentation and commands. Replace product/group names and review external links.',
'vulcan-punish':'Configured anti-cheat punishment integration. Audit actions before enabling on players.',
'website':'Server data/API integration. Configure a secret, restricted bind address and your own frontend.',
'wishing-well':'Community contributions and configured prize draws. Test deposits, scheduling and payout persistence.',
'world-bosses':'Four configurable boss encounters, rifts, contribution rewards and equipment shop. Replace example arena coordinates.',
'world-encounters':'Experimental traveling encounters and scholars. Test spawn safety, trades and research progression.',
'world-manager':'World-related tools. Review locations and permissions before using on production worlds.'}

index=['# Module directory','','Every module below starts disabled in a fresh public preview. Follow the setup guides before enabling it. Setting tables document keys and data types only; server-specific values and configuration snapshots are deliberately excluded.','','| Module | Purpose |','|---|---|']
def settings(value, prefix=''):
    if isinstance(value, dict) and value:
        for key, child in value.items():
            # Map names can contain server-defined codes, products, rewards or locations.
            # These are source schema identifiers, not installed server data.
            yield from settings(child, f'{prefix}.{key}' if prefix else str(key))
    elif isinstance(value, list):
        yield prefix, 'list', 'Ordered entries; see the installed commented config for entry format.'
    else:
        kind = 'boolean' if isinstance(value,bool) else 'number' if isinstance(value,(int,float)) else 'mapping' if isinstance(value,dict) else 'text'
        help_text = {'boolean':'Enable or disable this specific setting; the master module toggle still applies.', 'number':'Set a numeric value; use the units and limits in your installed config.', 'mapping':'Named entries; populate using the supported configuration structure.', 'text':'Set your own identifier, message or value. Match installed dependencies where relevant.'}[kind]
        yield prefix, kind, help_text
for file in sorted((RES/'modules').glob('*.yml')):
    name=file.stem
    raw=file.read_text(encoding='utf-8')
    parsed=yaml.safe_load(raw) or {}
    purpose=purposes.get(name,'Configurable CoreSuite module.')
    index.append(f'| [{name}]({name}.md) | {purpose} |')
    page=f'# {name}\n\n{purpose}\n\n**Master switch:** `modules.{name}` in `config.yml`.\n\n**Installed file:** `plugins/CoreSuite/modules/{name}.yml`.\n\n## Configure and enable\n\n1. Back up your existing file.\n2. Review the sections below, especially world locations, external services, costs and commands.\n3. Install required integrations; do not assume console reward commands work without their target plugin.\n4. Set the master toggle and restart in staging. Check `/coresuite modules` and the console.\n5. Test ordinary-player access and persistence before production.\n\n## Configuration sections\n\n'
    page += '\n'.join(f'- `{key}`' for key in parsed) + '\n\n## Setting reference\n\nOnly names and data types are published here. For defaults, units and detailed comments, inspect the configuration generated by your installed build. No live server values, credentials, locations or command payloads are included.\n\n| Setting | Type | Guidance |\n|---|---|---|\n'
    page += '\n'.join('| '+ ' | '.join(cell(v) for v in row)+' |' for row in settings(parsed))+'\n'
    write(f'modules/{name}.md',page)
write('modules/index.md','\n'.join(index)+'\n')
print(f'Generated {len(descriptor["commands"])} command entries, {len(descriptor["permissions"])} permissions and {len(purposes)} module guides.')
