# PaperExport 1.1.0

This release improves authoring fidelity, animation timing, and local testing.

- Export arbitrary cube rotations, inflation, and flat geometry from Blockbench.
- Retain entity settings and embedded Ogg sounds in saved `.bbmodel` projects.
- Preserve imported animation events, negative/custom priorities, bone rest
  scales, and named hitboxes. Metadata uses Blockbench's registered properties.
- Fire animation events at time zero and every crossed loop boundary. Step keys
  change at their exact timestamp in Java, TypeScript, and the C# viewer.
- Report animation priority rejection through the integration API. Ignore
  cancelled damage when triggering hurt/attack cues.
- Host generated packs directly from the plugin with hash-specific URLs;
  external HTTPS and disabled modes remain available.
- Add `/pe doctor` and `/pe pack send`.
- Add viewer auto reload, F5, rest pose, and front/back views. Reload retries
  partially written archives and preserves the camera and playback state.
- Frame viewer geometry using transformed bounds, including rotated bones.

The package format remains v1. Existing mobs need to be respawned after `/pe
reload` to adopt changed model definitions. Curved animation interpolation and
Molang expressions still require conversion to numeric linear/step keys.

Blockbench persistence uses its documented
[Property API](https://blockbench.net/wiki/docs/property/) and project codec events.

Verification: 16 TypeScript/Blockbench tests, 21 Java tests, C# parser and live
reload checks, and an installed Blockbench save/reopen/export check passed.
Paper 1.21.11 and 26.2 loaded all four example packages; hosted ZIP downloads
matched the generated SHA-1 before and after reload/rebuild. A new HD sample
spawned with 27 displays. Player-side visual and combat testing remains manual.
