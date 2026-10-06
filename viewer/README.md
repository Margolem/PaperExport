# PaperExport Viewer

This is a native Windows desktop application written in C# with WPF. It uses
Windows controls and WPF's 3D viewport. It has no HTML, browser process, or
Electron runtime.

The Model tab shows the rig, animation playback, textures, sounds, and
hitboxes. The Files tab lists package contents. Both older 1.21.11 packages
and current portable packages open in the same viewer.

Build and run:

```powershell
dotnet run --project viewer/PaperExport.Viewer.csproj -- path/to/mob.paperexport
```

Publish a self-contained Windows executable:

```powershell
dotnet publish viewer/PaperExport.Viewer.csproj -c Release -o viewer/release-build
```

Use File → Open, drag and drop a package, or pass a `.paperexport` path as an
argument. The viewer reads ZIP data in memory, checks CRCs and resource limits,
and never executes package contents.

To make it available under Windows **Open with**, run
`powershell -NoProfile -File viewer/register-file-association.ps1` after
publishing. Windows may still ask you to choose PaperExport Viewer as the
default app for `.paperexport` files.
