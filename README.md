# PaperExport

![PaperExport logo](assets/paperexport.png)

PaperExport is an independent pipeline for modeled Paper entities
that render on unmodified Minecraft Java clients with a server resource pack.
It is **not affiliated with Mojang Studios or PaperMC**. The repository has a
Blockbench plugin, a Paper 1.21.x–26.x server plugin, an offline Windows viewer,
and a versioned `.paperexport` ZIP format.

## What is it?

Blockbench groups become display bones, cubes in the same group become one
resource-pack item model, and Paper spawns one ItemDisplay per visible bone
around a vanilla controller entity. The vanilla controller provides AI,
physics, damage and collision. The viewer opens the package without Minecraft.
No client mod is required.

## Features

* One-file `.paperexport` packages with bounded ZIP and schema validation.
* Blockbench custom model format, Paper Entity settings, animation export,
  texture inclusion, generated resource-pack assets, and thumbnail capture.
* Paper registry, spawn/kill/reload commands, pack merge and SHA-1, persistent
  controller IDs, transient rigs, central animation tick, hierarchical JOML
  transforms, and safe sound/particle events.
* Multiple named `pe_hitbox` regions that follow animated bones, plus a
  PaperExport service API and hitbox event for other server plugins.
* Native C# WPF viewer with orbit, zoom, pan, hierarchy, textures, animation
  playback, hitbox/pivots, and package inspection. It uses no HTML or Electron.
* One PaperExport logo across the viewer, Blockbench plugin, and server pack.

## Requirements

* Paper **1.21–1.21.11** on Java **21+**, or **26.1–26.3** on Java **25+**.
* A Minecraft client matching the server version. The runtime builds that
  version's resource pack when it starts.
* Blockbench with local plugin installation enabled.
* To build: Node.js 22+, npm 10+, a JDK that can target Java 21, and the
  .NET 10 SDK on Windows for the native viewer. The included Gradle wrapper
  downloads its own Gradle distribution.

## Installing the Blockbench plugin

Keep [paperexport.js](https://github.com/Margolem/PaperExport/releases/download/v1.0.2/paperexport.js) and [icon.png](https://github.com/Margolem/PaperExport/releases/download/v1.0.2/icon.png) in the
same folder, then install `paperexport.js` as a local
Blockbench plugin using **File → Plugins → Load Plugin from File**. In Blockbench,
choose **File → New → PaperMC Custom Entity**. The Paper Entity panel appears
on the right.

## Creating your first entity

Make groups for body, head, arms and legs, then put cubes inside those groups.
Import or paint PNG textures and use Blockbench's animation timeline to create
`idle`, `walk`, and `attack`. Set a lowercase namespace and entity ID, name,
base entity, behavior and stats in the Paper Entity panel. Use **Validate Paper
Entity** to inspect errors, then **Export Paper Entity (.paperexport)**.
Set the project's **Texture Size** to the UV grid you painted on. PNG pixel
resolution can differ; PaperExport keeps the grid consistent in Blockbench,
the viewer, and Minecraft.

To add attack regions, create cubes named exactly `pe_hitbox` inside bones.
You may add several cubes with that name. They are exported as invisible
interaction hitboxes and excluded from the visible resource-pack model.
The separate [PaperExport Examples](https://github.com/Margolem/PaperExport-Examples)
plugin includes a test dummy, a ghost giant, and a 128x128 HD model sample.

## Exporting `.paperexport`

Export creates one ZIP-based `.paperexport` containing the model, PNGs,
animations, entity settings, a thumbnail, and compiled pack assets. The
runtime adapts those assets for the installed Paper version.
It rejects unsupported cube rotations, nonnumeric expressions and animation
interpolation other than linear/step instead of silently changing the model.
It combines cubes per bone and removes redundant animation keys. See the
[format specification](docs/PAPEREXPORT_FORMAT.md).

## Installing PaperExport Runtime

1. Copy [PaperExport-Paper-1.0.2.jar](https://github.com/Margolem/PaperExport/releases/download/v1.0.2/PaperExport-Paper-1.0.2.jar) to
   `plugins/` on a supported Paper server. The same JAR works across the range.
2. Start once. PaperExport creates `plugins/PaperExport/entities/`,
   `generated/`, `resourcepack/`, `cache/`, `logs/`, and `config.yml`.
3. Stop the server or use `/pe reload` after changing packages.

## Adding an entity

1. Install `PaperExport.jar`.
2. Start the server once.
3. Drop `monster.paperexport` into `plugins/PaperExport/entities/`.
4. Restart or run `/pe reload`.
5. The merged pack appears at `plugins/PaperExport/generated/resourcepack.zip`.
6. Host that ZIP on HTTPS and set `resource-pack.url` in `config.yml`, then
   `/pe reload`. Players must accept the resource pack when joining.
7. Run `/pe spawn namespace:monster`.

`/pe list`, `/pe info <id>`, `/pe spawn <id> [1–32]`, `/pe kill <id>`,
`/pe reload`, `/pe validate`, `/pe pack`, and `/pe debug` are available.
Permissions: `paperexport.admin`, `paperexport.spawn`, `paperexport.reload`,
`paperexport.debug`. The pack mode `external` sends an HTTPS URL plus the
generated SHA-1; `disabled` only writes the ZIP. Embedded hosting is not
implemented. Existing instances retain their definition after reload and
should be respawned when models change.

## Viewer

Run `dotnet run --project viewer/PaperExport.Viewer.csproj -- path/to/mob.paperexport`
for development. `build-all.bat` publishes a self-contained Windows executable
at [PaperExport-Viewer-Windows.exe](https://github.com/Margolem/PaperExport/releases/download/v1.0.2/PaperExport-Viewer-Windows.exe).
Open a `.paperexport` through File → Open, drag it onto the window, or pass
its path as an argument. To add the viewer to Windows **Open with**, run
`rtk powershell -NoProfile -File viewer/register-file-association.ps1`.
Windows may ask you to choose it as the default app the first time.

## Local test server

`node scripts/setup-test-server.mjs` downloads and verifies the latest stable
Paper **1.21.11** build into `test-server/`. Pass a version, for example
`node scripts/setup-test-server.mjs 26.2`, to use `test-servers/26.2/`.
If only beta builds exist, add `--pre-release` explicitly. Review the
Minecraft EULA in that server's `eula.txt`, then run its `start.ps1`. The
local server binds to `127.0.0.1:25566`. See
[test-server/README.md](test-server/README.md).

## Plugin API

Compile your plugin against `dist/PaperExport-API-1.0.2.jar` with `compileOnly`
and declare `depend: [PaperExport]` in `plugin.yml`. At runtime, obtain
`PaperExportApi` from Bukkit's services manager. The API is registered only by
the installed PaperExport plugin. It can list, spawn, animate, sound, despawn,
and reload packages, and register custom animation event handlers. Listen for
`PaperExportHitboxEvent` to identify the player, controller, region ID, and
attack or interaction action. The API uses the server main thread.

## File format

The [v1 specification](docs/PAPEREXPORT_FORMAT.md) documents paths, schemas,
coordinates, animations, asset naming, limits, and version checks. JSON
Schemas are in [schemas](schemas/). The [architecture](docs/ARCHITECTURE.md)
records platform research and design choices.

## Building from source

On Windows, run `build-all.bat`; on Unix, `./build-all.sh`. This runs npm and
Gradle tests, builds the Blockbench plugin and Paper runtime, and copies
artifacts to `dist/`. Windows additionally tests
and publishes the native C# viewer. Individual commands:

```text
npm ci
npm test
npm run build
cd paper-runtime && ./gradlew test jar
dotnet run --project viewer-native-tests/PaperExport.Viewer.Tests.csproj -c Release
dotnet publish viewer/PaperExport.Viewer.csproj -c Release -o viewer/release-build
```

## Troubleshooting

* **Purple/black missing model:** Install and accept the exact generated
  resource pack. Confirm the pack hash logged by `/pe pack` matches the ZIP.
* **Package skipped:** Read the filename and reason in the Paper console, then
  run Blockbench validation and re-export. A bad package never blocks others.
* **Entity moves but looks wrong:** Check the Minecraft client matches the server and
  restart the client after changing packs. Respawn old instances after reload.
* **No automatic pack download:** Set a reachable HTTPS URL. PaperExport cannot
  make a local file reachable through NAT or firewalls.

## Performance

The runtime uses one scheduled update for all active entities, creates one
display per nonempty bone, and pauses cosmetic updates beyond a configurable
distance. `/pe debug` reports actual active counts and measured average update
time. Server capacity depends on bone count, player view distance and hardware;
benchmark your own server.

## Security and current limitations

Package paths, sizes, JSON, textures, IDs and references are validated. No
scripts or executable files are accepted. Package `damage` events are off by
default. Sound and particle events are limited to data interpreted by the
server. The legacy entity hitbox width/height is preview metadata. `pe_hitbox`
regions create attack and interaction targets through Paper Interaction entities;
their axis-aligned width covers the larger horizontal side of each cube.
The base entity still supplies physical collision. Behavior presets retain the base
entity's vanilla AI, except `stationary` disables it. `INTERACTION` is cosmetic
and has no living health. Client visuals still need in-game verification; the
automated tests cover the package roundtrip and transform math, but cannot
replace an in-game visual and combat acceptance run. Automated server smoke
tests covered 1.21.1, 1.21.4, 1.21.10, 26.2, and 26.3 beta.
