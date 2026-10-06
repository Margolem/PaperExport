using System.IO.Compression;
using PaperExport.Viewer;

var folder = new DirectoryInfo(AppContext.BaseDirectory);
while (folder is not null && !File.Exists(Path.Combine(folder.FullName, "tests", "fixtures", "minimal.paperexport")))
    folder = folder.Parent;
if (folder is null) throw new FileNotFoundException("Could not find tests/fixtures/minimal.paperexport");
var fixture = Path.Combine(folder.FullName, "tests", "fixtures", "minimal.paperexport");
var loaded = PackageReader.Load(fixture);
if (loaded.Manifest.Id != "fixture:rig" || loaded.Model.Bones.Count != 6 || loaded.Model.Hitboxes.Count != 2 ||
    loaded.Animations.Count != 5 || loaded.DisplayNodes != 6 ||
    loaded.Manifest.Sounds.Count != 1 || loaded.Entity.Boss?.Enabled != true)
    throw new Exception("Native viewer did not load the complete test package.");
var attack = Path.Combine(Path.GetTempPath(), $"paperexport-viewer-{Guid.NewGuid():N}.paperexport");
try
{
    using (var zip = ZipFile.Open(attack, ZipArchiveMode.Create))
        zip.CreateEntry("../outside.json");
    try
    {
        PackageReader.Load(attack);
        throw new Exception("Native viewer accepted a traversal ZIP.");
    }
    catch (InvalidDataException) { }
}
finally { File.Delete(attack); }
Console.WriteLine($"Native viewer read {loaded.Manifest.Id}: {loaded.Model.Bones.Count} bones, {loaded.Animations.Count} animations, {loaded.Manifest.Textures.Count} texture, {loaded.Manifest.Sounds.Count} sound and boss settings.");
Console.WriteLine("ZIP traversal rejected.");
