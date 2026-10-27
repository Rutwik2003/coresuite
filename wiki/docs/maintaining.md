# Maintaining this wiki

Documentation lives in `wiki/docs/` as portable Markdown. MkDocs Material builds a static searchable website. It can later be hosted elsewhere without rewriting the articles.

## Local build

```text
python -m venv .venv
python -m pip install -r wiki/requirements.txt
python -m mkdocs build --strict -f wiki/mkdocs.yml
python -m mkdocs serve -f wiki/mkdocs.yml
```

Activate the virtual environment before the pip/build commands. The generated site goes to `build/wiki-site/`. Never publish the repository root or private server files as the website.

The initial documentation is published from the dedicated `codex/wiki` branch. It does not merge or publish unfinished changes from the development `test` branch. Commit documentation updates to the wiki branch to rebuild Pages.

Command and permission tables come from the descriptor; module setting names and types come from public-preview resources. Actual configuration values are excluded. Regenerate and review the references when the plugin changes. Descriptor usage text is not always a complete list of subcommands: executable help and handler behavior take precedence.

## Move to another host

Build the site, publish `build/wiki-site/`, and change `site_url` in `wiki/mkdocs.yml`. Keep redirects from the old URL when possible. The source articles remain Markdown; no player database or plugin server is needed to serve the wiki.
