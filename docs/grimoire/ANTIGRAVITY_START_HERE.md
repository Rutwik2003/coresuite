# Continue CoreSuite in Antigravity

Prepared 3 October 2026. This project is already linked to the same folder; no import, repository clone, API key or MCP connection is needed for a file-based handoff.

## Owner: start a task

1. Open the existing CoreSuite project in Antigravity. Confirm its workspace root is `C:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit`.
2. Start a fresh agent conversation so it reads the new root `GEMINI.md`. If necessary reopen the workspace. Ask the agent to name the context files it actually read; do not assume discovery worked merely because the files exist.
3. Select Gemini 3.8 Flash or Gemini 3.1 Pro in Antigravity's model selector. Model selection is in the app, not controlled by GEMINI.md. Either can use this handoff; quality still depends on review and testing. A reasonable workflow is one model implementing a small change and a separate review pass examining the diff, without simultaneous writes.
4. Paste the starting prompt below. Don't have Codex and Antigravity editing this shared checkout at the same time.

### Starting prompt — copy this

```text
Continue my existing CoreSuite plugin project in this folder.

Read GEMINI.md, docs/grimoire/MAGIC_REDESIGN_HANDOFF_2026-10-03.md in full,
and docs/grimoire/IMPLEMENTATION_LOG.md. Inspect git status and the current
implementation before editing. Preserve my changes.

Implement Phase A of the magic redesign: a two-spell reserved-empty-slot
combat hotbar prototype behind an experimental setting that defaults off.
Activation must not move or overwrite real items. Real food, potions,
armour and offhand totems must remain usable. Retain classic controls as
a fallback. Include lifecycle cleanup and meaningful safety tests needed
for this prototype; do not publish a partial unsafe inventory feature.

Work on test. Do not push/publish the separate resource-pack repository,
deploy to the live server, or replace files in a running server. Do not
implement all later phases at once. Run the appropriate build/tests and
update IMPLEMENTATION_LOG.md with results and the next precise task.

Start by briefly stating the existing state, files you will change and
the inventory invariants you will preserve, then proceed with the work.
Explicitly separate automated checks from Java/Bedrock tests I must run.
```

### Review prompt — after implementation

```text
Review the current diff against the magic handoff and Phase A invariants.
Trace activation, survival-item use, duplicate use packets, inventory
transactions, death, quit, world changes, restart and plugin disable.
Look for real item loss/duplication, escaped controls, double mana costs,
unsafe async Bukkit calls and interference with vanilla use/protection.
Read the code and tests; do not accept the previous agent's success claims
without evidence. Report findings with file references, then fix confirmed
issues within this phase and rerun affected tests. Keep the pack unpublished.
```

### Continue prompt — later session

```text
Read GEMINI.md, the full magic redesign handoff and IMPLEMENTATION_LOG.md.
Inspect my current changes. Continue the next incomplete, authorized phase
from the recorded checkpoint; do not redo completed work or skip its
acceptance gates. Keep the live resource pack unchanged. Update the log
with exact evidence, limitations and the next step before finishing.
```

## What has actually been connected

- Root GEMINI.md: workspace instructions/context supported by Antigravity.
- Detailed handoff: researched design and implementation plan already in Git.
- Implementation log: durable progress across models and new conversations.

No agent was launched, no model was selected on your behalf, and no conversation history was automatically transferred. The handoff documents contain the relevant project context. Antigravity's actual ingestion must be confirmed in its own conversation.

Existing `.agents/` historical notes were left untouched; they describe earlier work and should not override the current handoff or newer user instructions.

## Official references

- [Workspace GEMINI.md discovery](https://www.antigravity.google/docs/rules/)
- [Available models and model selection](https://www.antigravity.google/docs/models/)

Verified in official documentation on 3 October 2026. Installed app/account availability may differ; use the model names your selector actually offers.
