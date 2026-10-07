# PaperExport Runtime

`./gradlew test jar apiJar` builds one Java 21 plugin for Paper 1.21–1.21.11
and 26.1–26.3. Paper 26.x itself requires Java 25. Install
`build/libs/PaperExport-Paper-1.1.0.jar` in `plugins/`. Drop packages in
`plugins/PaperExport/entities/`, then run `/pe reload`.

Use `resource-pack.mode: hosted` for built-in hosting (default bind is loopback,
port 8766), or use external mode and set `resource-pack.url` to an HTTPS URL.
`/pe doctor` reports the pack URL and package problems; `/pe pack send` resends it.
`/pe debug` reports
active instances and mean update time. Package-defined damage events require
`events.allow-package-damage: true`. See the root README for limitations.
