# PaperExport format, version 1

This document is the portable specification for `.paperexport`. Its MIME type
is `application/vnd.paperexport+zip`. A file is a ZIP container, not a renamed
JSON document. All text is UTF-8 JSON. Entries use `/` separators and have no
leading slash. Readers must ignore ZIP directory entries and reject duplicate
file names (case-insensitive), absolute paths, `.` or `..` path segments,
backslashes, NUL and colon characters, encrypted entries, and executable
extensions. The format is data only; readers must never evaluate content.

Version 1 packages are portable across supported Paper **1.21.x–26.x**
servers. The fixed `minecraft_version` and `paper_version` values of
`1.21.11` identify the bundled asset compilation baseline; they do not limit
the server version. The runtime rebuilds a server-specific pack on load.
Readers must reject a different `format_version`; they may report older or
newer versions distinctly. The JSON Schemas under `schemas/` describe the
fields; the cross-field rules below are also required.

## Container layout

```text
manifest.json                     required
model/model.json                  required
entity/entity.json                required
textures/<name>.png               referenced by manifest
sounds/<name>.ogg                  optional, referenced by manifest
animations/<name>.json            referenced by manifest
resourcepack/pack.mcmeta          required
resourcepack/assets/**            compiled assets
preview/<name>.png                optional preview images
metadata/export.json              optional informational metadata
```

No other top-level areas are allowed in v1. All referenced paths must exist.
An implementation should cap compressed input at 32 MiB, 512 files, each
expanded entry at 16 MiB, total expansion at 96 MiB, and each JSON at 2 MiB.
PNG width and height must be 1–2048 pixels and match the manifest. Sound files
must be Ogg and at most 8 MiB each, with at most 32 named sounds. A reader
should validate both central-directory declared sizes before inflation and
actual output sizes afterward. It should verify the ZIP CRC.

## Manifest

```json
{
  "format":"paperexport", "format_version":1,
  "id":"example:creature", "name":"Example Creature",
  "author":"PaperExport contributors", "description":"Example entity",
  "minecraft_version":"1.21.11", "paper_version":"1.21.11",
  "exporter_version":"1.0.0",
  "model":"model/model.json", "entity":"entity/entity.json",
  "animations":{"idle":"animations/idle.json"},
  "textures":{"skin":{"path":"textures/skin.png","width":16,"height":16}},
  "sounds":{"roar":{"path":"sounds/roar.ogg"}},
  "resource_pack":"resourcepack/"
}
```

`id` is `<namespace>:<path>`, lowercase ASCII. The namespace uses
`[a-z0-9_.-]+`; the path uses `[a-z0-9_./-]+` with no empty, `.` or `..`
segments. IDs must be globally unique among installed packages. Animation
texture and sound map keys use `[a-z0-9_.-]+` and exactly match their file names.
Manifest strings are at most 256 characters. Readers must reject missing,
wrong-type, out-of-range and non-finite values. Unknown JSON properties are
reserved for a later version and should be ignored by a tolerant v1 reader.

## Model

`model/model.json` is an object with `texture_size: [width,height]` and
`bones: []`, plus optional `hitboxes: []`. It uses a right-handed coordinate system: X right, Y up, Z south;
16 model units equal one Minecraft block. Each bone has `id`, `name`, `parent`
(bone ID or `null`), `pivot: [x,y,z]`, `rotation: [x,y,z]` in degrees,
`scale: [x,y,z]`, and `cubes: []`. The root's parent is null. IDs are unique;
the parent graph is acyclic. An empty bone is a pivot that still affects its
children.

Each cube has a name, `from` and `to` model-coordinate triples (`from[i] <
to[i]`) and a `faces` map whose keys are `north`, `south`, `east`, `west`, `up`,
`down`. A face has `texture` (a manifest texture key), `uv: [u0,v0,u1,v1]`
in 0–16 model UV coordinates, and optional `rotation` of 0, 90, 180 or 270
degrees. A missing face is invisible. Transparent PNG pixels remain transparent.

The compiler groups all cubes in a bone into one Minecraft item model. It
subtracts the bone pivot from every cube coordinate and adds `[8,8,8]`, then
uses the transformed result in an `elements` array. Minecraft's allowed
element coordinate range is `[-16,32]`; a cube outside that range fails
validation. This conversion places the bone pivot at the center of its item
model. The renderer uses one ItemDisplay per nonempty bone. It computes each
bone's relative translation from `bone.pivot - parent.pivot`, then applies
rest and animated rotation and scale through matrix multiplication. Rotations
must never be combined by simply adding Euler angles across parent/child
bones. The root follows the controller position and yaw. Minecraft model front
is north (negative Z), so the runtime adds 180 degrees before applying
controller yaw. A bone whose ID or name is `head` turns toward its mob target
or a nearby player, clamped to 65 degrees yaw and 40 degrees pitch. Animated
head motion remains additive.

Each Blockbench cube named exactly `pe_hitbox` exports one entry in
`model.hitboxes` instead of a visible cube. An entry has a unique `id`, a
`bone` ID, and `from`/`to` triples. Multiple entries may reference one bone.
The runtime creates a nonpersistent `Interaction` entity for each entry and
updates its axis-aligned bounding box from the animated bone matrix. The
horizontal width covers the larger X/Z extent; deep or rotated boxes can be
larger than their visible cube. Attacks are forwarded to the controller and
raise `PaperExportHitboxEvent`; plugins can cancel or handle interactions.
These regions do not alter the vanilla controller's physical collision.

## Animations

Each manifest animation is an object with `name`, `length` in seconds,
`loop` (`once`, `hold`, `loop`), numeric `priority`, `tracks`, and `events`.
A track has a `bone` ID and optional `position`, `rotation`, `scale` arrays.
Each key has `time` in `[0,length]`, a three-number `value`, and optional
`interpolation` (`linear` or `step`; linear is default). Times must increase
strictly within a track. Position values use model units, rotation values
degrees, scale values are multipliers. Missing channels use `[0,0,0]` for
position/rotation and `[1,1,1]` for scale. Rotation interpolation uses
quaternion slerp. `hold` stays at the last pose, `once` returns to the normal
locomotion state, and `loop` wraps time. Priority defaults in the exporter to
death 100, hurt 80, attack 60, locomotion 30, idle 0.

An event has `time`, `type` (`sound`, `particle`, `damage`, `custom_event`) and
`data` with primitive values. These are declarations, never scripts.
Administrators decide which events the runtime may act on. Command events,
JavaScript, shell content, and arbitrary server code are prohibited. The
current runtime triggers animation state changes from controller movement,
damage and attack. It dispatches allowlisted sound and particle events.
Package damage events are disabled by default and require
`events.allow-package-damage: true`; radius and damage are capped at runtime.
`custom_event` carries a namespaced `data.key` and calls only handlers
explicitly registered by a server plugin through the PaperExport API.

## Custom sounds

The optional manifest `sounds` map references `sounds/<name>.ogg` files.
The compiler copies each file to
`resourcepack/assets/<namespace>/sounds/paperexport/<entity-path>/<name>.ogg`
and adds a definition under `resourcepack/assets/<namespace>/sounds.json`.
The resulting event key is
`<namespace>:paperexport.<entity-path>.<name>`. Packages that share a
namespace have their sound definitions merged when the server pack is built.
An animation `sound` event uses `data.key` with that full event key.

## Entity

`entity/entity.json` has `base_entity`, `behavior`, `stats`, and `hitbox`.
Supported base types are `ZOMBIE`, `SKELETON`, `HUSK`, `STRAY`, `PIG`, `COW`,
`ARMOR_STAND`, `INTERACTION`. Behaviors are `passive`, `neutral`, `hostile`,
`stationary`, `flying`, `swimming`, `boss`, `npc`, `custom`. The runtime uses
the vanilla base entity's AI and collision, and disables AI for `stationary`.
Other behavior labels are descriptive in v1; they do not replace vanilla AI.
`INTERACTION` is a cosmetic controller without LivingEntity health/attributes.

`stats` contains numeric `max_health`, `movement_speed`, `attack_damage`,
`follow_range`, `knockback_resistance`, `armor`, `armor_toughness`, `scale`,
and booleans `gravity`, `invulnerable`, `silent`, `persistent`. The runtime
applies supported Bukkit attributes to LivingEntity controllers. The hitbox
object has `width`, `height`, `vertical_offset`, `interaction_range` and is
used by the viewer for visualization. **Paper cannot set an arbitrary vanilla
mob collision box through these APIs; runtime collision is the actual base
entity's hitbox.** The Blockbench panel should not imply otherwise.

Optional `sound_cues` maps `spawn`, `ambient`, `attack`, `hurt`, and
`death` to local manifest sound names. The runtime plays the corresponding
custom sound during those events. The optional `boss` object contains
`enabled`, `title`, `bar_color`, `bar_style`, `range` (4–128 blocks), and
`phases` (up to eight). Each phase has a descending `below_health` fraction
from 0.01 to 0.99 and optional `animation` and `sound` names. A transient
boss bar follows the controller's current health and is shown to nearby
players. Phases fire once per live instance when health crosses a threshold.
The base entity still supplies the actual combat AI.

## Compiled resource pack

The ZIP contains a complete fragment under `resourcepack/`, including
`pack.mcmeta` with `min_format` and `max_format` `[75,0]`. The Paper runtime
replaces that metadata for the running server. For entity
`ns:creature`, bone `head`, and texture `skin`, it contains:

```text
resourcepack/assets/ns/items/paperexport/creature/head.json
resourcepack/assets/ns/models/entity/creature/head.json
resourcepack/assets/ns/textures/item/paperexport/creature/skin.png
resourcepack/assets/ns/sounds/paperexport/creature/roar.ogg
resourcepack/assets/ns/sounds.json
```

The item definition is `{"model":{"type":"minecraft:model", "model":
"ns:entity/creature/head"}}`. The item model's textures reference
`ns:item/paperexport/creature/skin`, keeping all textures in the 1.21.11
items atlas. The Paper runtime sets `ItemMeta#setItemModel` to
`ns:paperexport/creature/head` on a paper item displayed with the `FIXED`
transform on 1.21.4 and later. On 1.21–1.21.3, the runtime generates a
`minecraft:paper` model with numeric `CustomModelData` overrides and omits
the newer item definitions. It merges all installed fragments into one pack,
rejects duplicate paths, writes `resourcepack.zip`, and computes a SHA-1 for
client delivery.

## Viewer and portability

Viewers use the original model, textures and animations, not the compiled
Minecraft JSON, to show a preview. Texture filtering is nearest neighbor.
The compiled game view can differ at face UV edges and lighting. No archive
entry may contain an absolute path or refer to a local file outside the ZIP.
All required textures travel inside the package.
