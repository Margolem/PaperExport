# PaperExport Runtime

`./gradlew test jar apiJar` builds one Java 21 plugin for Paper 1.21–1.21.11
and 26.1–26.3. Paper 26.x itself requires Java 25. Install
`build/libs/PaperExport-Paper-1.0.0.jar` in `plugins/`. Drop packages in
`plugins/PaperExport/entities/`, run `/pe reload`, and host the generated
`plugins/PaperExport/generated/resourcepack.zip` at a public HTTPS URL.

Set `resource-pack.url` in config.yml to send it on join. `/pe debug` reports
active instances and mean update time. Package-defined damage events require
`events.allow-package-damage: true`. See the root README for limitations.
