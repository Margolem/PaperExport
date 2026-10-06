# PaperExport Blockbench plugin

Build with `npm install` at the repository root and `npm run build -w blockbench-plugin`.
Keep `dist/paperexport.js` and `dist/icon.png` together when installing the
local Blockbench plugin. The JavaScript filename must
remain `paperexport.js` to match Blockbench's plugin ID. Choose **File → New →
PaperMC Custom Entity**, create groups and cubes, set the Paper Entity panel, then
use **File → Export → Export Paper Entity**.

Each group becomes an animated bone. Cubes must be inside groups. v1 rejects
individual cube rotations, MoLang expressions, and non-linear keyframe easing
with an actionable message because vanilla item models cannot preserve them.
Rotate the group instead. Multiple cubes in a group become one display model.
Face UVs use the project's **Texture Size** grid. You can use a higher
resolution PNG without changing that grid; PaperExport converts the UVs using
the grid and preserves it in the package. Reopen a package exported with an
older plugin to refresh its Blockbench preview before editing.
