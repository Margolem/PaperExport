# PaperExport architecture

PaperExport v1 is a data format and three clients of that format: a Blockbench
authoring plugin, a Paper runtime, and an offline viewer. The format is the
boundary between the applications. It contains no executable code.

## Verified platform decisions (6 October 2026)

* Blockbench exposes `Plugin.register`, `ModelFormat`, `Codec`, actions, panels,
  textures, groups and animation timelines. The plugin uses those APIs and
  compiles to one JavaScript file. The built-in JSZip is used for the ZIP file.
* Paper's display entities support arbitrary transformation matrices and client
  interpolation. An `ItemDisplay` has no physical hitbox; a vanilla controller
  entity owns health, AI and collision. Bone models are separate item models so
  one display is needed per animated bone, not per cube.
* The runtime selects pack metadata by the server's Minecraft version. Paper
  1.21–1.21.3 uses legacy `CustomModelData` overrides on paper items. Since
  1.21.4, definitions live under `assets/<namespace>/items/`, selected by
  `ItemMeta#setItemModel`. Since 1.21.9, pack metadata uses `min_format` and
  `max_format` instead of `pack_format`.
* The runtime targets Java 21 bytecode and uses the 1.21.11 Paper API for
  compilation. Paper 26.x servers require Java 25. Version-specific API
  differences are resolved by the runtime.

## Geometry and coordinates

The portable model keeps Blockbench-style model units (16 units = one block),
right-handed X/right, Y/up, Z/south. A bone has a parent ID, pivot, rest
rotation, rest scale and cubes. Cube vertices are stored in model coordinates.
At compile time, cube coordinates are made relative to the bone pivot. The
runtime computes `parentWorld * translate(pivot-parentPivot) * animation *
restRotation * restScale`. Display transformations use JOML matrices. The
viewer applies the same tree. Cubes in a bone are combined in one item model.

Minecraft's block/item model JSON has element rotation restrictions. The
exporter rejects individual cube rotations it cannot represent losslessly;
bone rotations remain unrestricted because they run on display entities.
This explicit validation avoids quietly producing a different model.

## Package and trust boundary

`manifest.json` identifies format version 1, a namespaced entity ID, entry
paths, and target version. `model/model.json` contains bones and cubes;
`entity/entity.json` contains declarative stats and behavior;
`animations/*.json` contain numeric tracks and safe event declarations;
`textures/*.png` and `resourcepack/**` contain assets. Every archive path is
relative and normalized. Readers enforce compressed and expanded size limits,
file count, duplicate name checks, JSON limits, texture dimensions and schema
checks. Unrecognized format versions fail closed. No package content executes.

## Runtime lifecycle

At startup/reload: scan packages independently, validate, build a new registry
and combined resource pack in temporary files, then atomically publish them.
Conflicting IDs or pack paths reject the conflicting package. Existing spawned
entities retain their immutable definition until despawn; reload reports that
changed instances need respawning. A central tick advances animation and
updates display matrices. Controller and displays carry PDC identity tags.
Displays and Interaction hitboxes are transient and are reconstructed from
controllers after chunk load. Each controller owns only one rig.

Resource pack delivery is external HTTPS by default, disabled when no URL is
configured. A SHA-1 is calculated for clients. Administrators must configure
a reachable resource-pack URL themselves.

## Source of truth and testing

`schemas/` and `docs/PAPEREXPORT_FORMAT.md` define v1. The TypeScript shared
library validates and writes packages. Java mirrors the schema and enforces
the same archive limits. A small `tests/fixtures/minimal.paperexport` archive is read by both.
Tests cover invalid archives, versioning, model references, animation sampling,
hierarchical transforms, and pack conflicts. Live Blockbench and Minecraft
visual acceptance still require manual application tests.

## Official references

* [Blockbench plugin guide](https://blockbench.net/wiki/docs/plugin/)
* [Blockbench Codec](https://web.blockbench.net/docs/classes/generated_io_codec.Codec.html)
* [Blockbench ModelFormat](https://web.blockbench.net/docs/classes/generated_io_format.ModelFormat.html)
* [Blockbench formats](https://blockbench.net/wiki/blockbench/formats/)
* [Paper display entities](https://docs.papermc.io/paper/dev/display-entities/)
* [Paper PDC](https://docs.papermc.io/paper/dev/pdc/)
* [Paper scheduler](https://docs.papermc.io/paper/dev/scheduler/)
* [Paper data components](https://docs.papermc.io/paper/dev/data-component-api/)
* [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/)
* [Minecraft 1.21.11 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11)
* [Minecraft 1.21.4 item definitions](https://feedback.minecraft.net/hc/en-us/articles/32385811139085-Minecraft-Java-Edition-1-21-4-The-Garden-Awakens)
