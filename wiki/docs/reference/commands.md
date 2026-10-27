# Command reference

Generated from the current plugin descriptor. These are registered entry points; module handlers may expose additional subcommands or permission checks. A registered command does not mean its module is enabled. Replace COMMAND in usage text with the command name.

| Command | Description | Declared usage | Permission | Aliases |
|---|---|---|---|---|
| /pack | Check or retry your server resource pack | /COMMAND [status&#124;retry] | Not declared here; handler checks may apply |  |
| /grimoire | Prepare your discovered spells | Use in-game help/tab completion | coresuite.grimoire.use | spellbook |
| /magic | Spells, research and the quest journal | Use in-game help/tab completion | coresuite.grimoire.use |  |
| /magicadmin | Administer the magic system | Use in-game help/tab completion | coresuite.magic.admin |  |
| /encounters | Find traveling scholars | Use in-game help/tab completion | coresuite.grimoire.use |  |
| /quests | Open your magic quest journal | Use in-game help/tab completion | coresuite.grimoire.use |  |
| /coresuite | CoreSuite administration | /COMMAND [help&#124;modules&#124;reload&#124;version] | coresuite.admin | cs |
| /csadmin | CoreSuite Admin Commands | /COMMAND &lt;subcommand&gt; | coresuite.admin | csa |
| /playtime | View your playtime and rewards | /COMMAND [rewards&#124;gui&#124;top&#124;help&#124;stats] | coresuite.playtime.use |  |
| /playtimereward | Admin management for playtime rewards | /COMMAND &lt;subcommand&gt; | coresuite.playtime.admin |  |
| /withdraw | Withdraw money as a bank note | /COMMAND &lt;amount&gt; | coresuite.banknotes.withdraw |  |
| /claimnote | Claim all bank notes in inventory | /COMMAND | coresuite.banknotes.withdraw |  |
| /bank | Open the bank withdrawal GUI | /COMMAND | coresuite.banknotes.withdraw |  |
| /withdrawlog | View bank note transaction logs | /COMMAND [page] | coresuite.banknotes.withdraw |  |
| /withdrawadmin | Admin bank note commands | /COMMAND give &lt;player&gt; &lt;amount&gt; | coresuite.banknotes.admin |  |
| /chatgame | Chat games administration | /COMMAND &lt;start&#124;force&#124;stop&#124;toggle&#124;reload&#124;top&#124;revealanswer&gt; | coresuite.chatgames.admin |  |
| /chatgametop | View chat games leaderboard | /COMMAND | Not declared here; handler checks may apply |  |
| /reward | Redeem a reward code | /COMMAND &lt;code&gt; | coresuite.rewardcodes.use |  |
| /rewardcodes | Reward codes main command | /COMMAND | coresuite.rewardcodes.use |  |
| /rewardadmin | Manage reward codes | /COMMAND &lt;subcommand&gt; | coresuite.rewardcodes.admin |  |
| /referral | Manage your referral code | /COMMAND [create&#124;use&#124;info&#124;leaderboard] | coresuite.referral.use |  |
| /automsg | Manage auto messages | /COMMAND &lt;reload&#124;toggle&#124;list&#124;add&#124;remove&gt; | coresuite.automessage.admin |  |
| /togglementions | Toggle chat mentions | /COMMAND | coresuite.mentions.use |  |
| /clearlag | Clear dropped entities | /clearlag | coresuite.clearlag.use |  |
| /announce | Announce a message on screen to all players | /announce &lt;message&gt; | coresuite.announce.use |  |
| /hub | Teleport to the hub | /COMMAND | coresuite.worldmanager.hub |  |
| /joinsurvival | Join the survival world | /COMMAND | coresuite.worldmanager.survival |  |
| /joinpvp | Join the PvP world | /COMMAND | coresuite.worldmanager.pvp |  |
| /setlobbyspawn | Set the lobby spawn point | /COMMAND | coresuite.worldmanager.admin |  |
| /launchpad | Manage jump pads | /COMMAND &lt;setpad&#124;setdest&#124;delete&#124;list&gt; [name] | coresuite.jumppad.admin |  |
| /fish | View your fishing stats and leaderboard | /COMMAND [top] | coresuite.fishing.use | fishlevel |
| /podiumupdate | Force update kill podium NPCs | /COMMAND | coresuite.podium.admin |  |
| /vulcanpunish | Punish a flagged player | /COMMAND &lt;player&gt; | coresuite.vulcan.punish |  |
| /vulcanwarn | Warn a player for anticheat | /COMMAND &lt;player&gt; &lt;reason&gt; | coresuite.vulcan.punish |  |
| /vulcancheck | Check player anticheat record | /COMMAND &lt;player&gt; | coresuite.vulcan.punish |  |
| /vulcanhistory | View player anticheat history | /COMMAND &lt;player&gt; | coresuite.vulcan.punish |  |
| /vulcanreset | Reset player anticheat record | /COMMAND &lt;player&gt; | coresuite.vulcan.punish |  |
| /vulcansetoffenses | Set player offense tier | /COMMAND &lt;player&gt; &lt;tier&gt; | coresuite.vulcan.punish |  |
| /vulcanbypass | Manage anticheat bypass list | /COMMAND &lt;add&#124;remove&#124;list&gt; [player] | coresuite.vulcan.punish |  |
| /store | Open the store GUI | /COMMAND | Not declared here; handler checks may apply | crate |
| /profile | View your player profile and stats | /COMMAND | coresuite.profile.use |  |
| /storeadmin | Store admin commands | /COMMAND approve &lt;player&gt; &lt;packageKey&gt; | coresuite.store.admin |  |
| /gamble | Open the Royal Casino GUI | /COMMAND [cf&#124;crash&#124;roulette&#124;slots&#124;top] | coresuite.gambling.use | casino |
| /cf | Open PVP Coinflip menu or create a duel | /COMMAND [create &lt;amount&gt; &lt;heads&#124;tails&gt;] | coresuite.gambling.use | coinflip |
| /creator | Manage creator features | /COMMAND [grant&#124;code&#124;redeem] | coresuite.creator.use |  |
| /link | Link your Minecraft account to Discord | /COMMAND | coresuite.website.link | discordlink, verify |
| /rank | View server ranks and web store link | /COMMAND | Not declared here; handler checks may apply | ranks, buy |
| /website | Manage and diagnose Website Integration module | /COMMAND [status&#124;reload&#124;links] | coresuite.admin |  |
| /orders | Open the Orders Board — post item requests or fulfill others | /COMMAND [post&#124;mine&#124;help&#124;admin] | coresuite.orders.use | orderboard, ob |
| /well | Open the Wishing Well — throw money in for a chance to win the pot | /COMMAND [throw &lt;amount&gt;&#124;top&#124;history&#124;admin] | coresuite.well.use | wishingwell |
| /artifacts | View your artifact collection and turn in complete sets | /COMMAND [sets&#124;curator&#124;admin] | coresuite.artifacts.use | archaeology, artefacts |
| /harvest | Open the Harvest Market — sell crops for bonus seasonal prices | /COMMAND [season&#124;top&#124;admin] | coresuite.harvest.use | harvestmarket, market |
| /discord | View Discord invite or manage Discord bridge | /COMMAND [status&#124;update&#124;reload] | coresuite.discord.use | dc |
| /boss | View World Boss status, active coordinates, and Garrick&#x27;s exchange | /COMMAND [status&#124;lore&#124;shop] | coresuite.boss.use | worldboss, wb |
| /bossadmin | Manage World Bosses (spawn, killall, reload, summonitem, setnpc) | /COMMAND &lt;spawn&#124;killall&#124;reload&#124;summonitem&#124;setnpc&#124;give&gt; | coresuite.boss.admin | wba, worldbossadmin |
| /guide | Open the interactive Server Guide &amp; Manual | /COMMAND [topic&#124;claim&#124;help] | coresuite.guide.use | tutorial, serverguide, helpguide |
| /guideadmin | Manage Player Guide (reload, reset, claim, info) | /COMMAND &lt;reload&#124;reset&#124;claim&#124;info&gt; [player] | coresuite.guide.admin | ga, guidea |
