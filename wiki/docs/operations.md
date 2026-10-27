# Storage, upgrades and backups

CoreSuite supports SQLite and MySQL configuration. SQLite defaults to `data/coresuite.db` under the plugin data folder. Storage work and module saves must finish during shutdown; do not kill the Java process immediately after issuing stop.

## Backup procedure

1. Stop the server cleanly and wait for the process to exit.
2. Copy the entire CoreSuite data directory, configuration and matching plugin JAR to a dated backup.
3. For MySQL, take a consistent database backup as well. File backups do not include remote database contents.
4. Keep backups outside a public Git repository. Test restoration in a separate server folder.

Copying only a live SQLite `.db` file can miss WAL data. A clean stopped-server backup avoids that pitfall.

## Upgrade procedure

Record current plugin/Paper/dependency versions, back up, replace the JAR while stopped, start, inspect migrations and test affected features. Retain the prior JAR and data backup as a pair. Downgrading only the JAR after schema changes is not guaranteed safe.

Changing `storage.type` is not a promise of automatic data transfer. Verify the supported migration path before switching a live server. Use a dedicated MySQL account with only the permissions needed for the plugin database.

## Performance

Use a representative spark profile, MSPT, GC and entity/chunk measurements. A thread showing a high percentage in a profiler is not by itself proof that it consumed a whole CPU core. Resource-pack status logs do not establish the cause of network lag.

Reduce unnecessary enabled features, excessive visual settings and expensive external polling only after identifying the workload. Do not advertise this plugin as a general server optimizer.
