using System.IO.Compression;
using PaperExport.Viewer;

var folder = new DirectoryInfo(AppContext.BaseDirectory);
while (folder is not null && !File.Exists(Path.Combine(folder.FullName, "tests", "fixtures", "minimal.paperexport")))
    folder = folder.Parent;
if (folder is null) throw new FileNotFoundException("Could not find tests/fixtures/minimal.paperexport");
var fixture = Path.Combine(folder.FullName, "tests", "fixtures", "minimal.paperexport");
var loaded = PackageReader.Load(fixture);
if (!SceneBuilder.NormalizeUv([8, 0, 16, 16]).SequenceEqual([0.5, 0, 1, 1]))
    throw new Exception("Viewer UVs must use Minecraft's 0–16 model grid.");
if (loaded.Manifest.Id != "fixture:rig" || loaded.Model.Bones.Count != 6 || loaded.Model.Hitboxes.Count != 2 ||
    loaded.Animations.Count != 5 || loaded.DisplayNodes != 6 ||
    loaded.Manifest.Sounds.Count != 1 || loaded.Entity.Boss?.Enabled != true ||
    loaded.Manifest.PaperVersion != "1.21-26.3")
    throw new Exception("Native viewer did not load the complete test package.");
foreach (var path in args)
{
    var sample = PackageReader.Load(path);
    if (sample.Manifest.PaperVersion != "1.21-26.3" || sample.Model.Hitboxes.Count == 0)
        throw new Exception($"Native viewer did not load current package fields: {path}");
    Console.WriteLine($"Native viewer read {sample.Manifest.Id}: {sample.Model.Hitboxes.Count} hitboxes.");
}
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

Exception? viewerFailure = null;
var uiThread = new Thread(() =>
{
    var copy = Path.Combine(Path.GetTempPath(), $"paperexport-reload-{Guid.NewGuid():N}.paperexport");
    MainWindow? window = null;
    try
    {
        File.Copy(fixture, copy);
        window = new MainWindow();
        var flags = System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance;
        object? Call(string name, params object?[] values) => typeof(MainWindow).GetMethod(name, flags)!.Invoke(window, values);
        void Set(string name, object value) => typeof(MainWindow).GetField(name, flags)!.SetValue(window, value);
        object? Get(string name) => typeof(MainWindow).GetField(name, flags)!.GetValue(window);
        void Pump(int milliseconds)
        {
            var frame = new System.Windows.Threading.DispatcherFrame();
            var timer = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromMilliseconds(milliseconds) };
            timer.Tick += (_, _) => { timer.Stop(); frame.Continue = false; };
            timer.Start(); System.Windows.Threading.Dispatcher.PushFrame(frame);
        }
        if (!Equals(Call("OpenPackage", copy, false, true), true)) throw new Exception("Viewer could not open reload fixture");
        Set("playing", false); Set("elapsed", .4); Set("yaw", .75);
        // Simulate an exporter that truncates before writing the finished archive.
        File.WriteAllText(copy, "incomplete"); Pump(550);
        if (((PaperPackage)Get("package")!).Manifest.Id != loaded.Manifest.Id) throw new Exception("Failed reload discarded the previous model");
        using (var memory = new MemoryStream())
        {
            memory.Write(File.ReadAllBytes(fixture));
            using (var archive = new ZipArchive(memory, ZipArchiveMode.Update, leaveOpen: true))
            {
                var entry = archive.GetEntry("manifest.json")!;
                string json; using (var reader = new StreamReader(entry.Open())) json = reader.ReadToEnd();
                var manifest = System.Text.Json.Nodes.JsonNode.Parse(json)!; manifest["name"] = "Live reload verified";
                entry.Delete(); using var writer = new StreamWriter(archive.CreateEntry("manifest.json").Open()); writer.Write(manifest.ToJsonString());
            }
            File.WriteAllBytes(copy, memory.ToArray());
        }
        Pump(1500);
        if (((PaperPackage)Get("package")!).Manifest.Name != "Live reload verified") throw new Exception("Watcher did not reload the saved archive");
        if (!Equals(Get("playing"), false) || !Equals(Get("elapsed"), .4) || !Equals(Get("yaw"), .75)) throw new Exception("Reload lost playback/camera state");
        Call("Rest_Click", window, new System.Windows.RoutedEventArgs());
        if (Get("animation") is not null || !Equals(Get("playing"), false)) throw new Exception("Rest pose did not stop animation");
        Call("Front_Click", window, new System.Windows.RoutedEventArgs());
        if (!Equals(Get("yaw"), Math.PI)) throw new Exception("Front view must face model -Z");
        Call("Back_Click", window, new System.Windows.RoutedEventArgs());
        if (!Equals(Get("yaw"), 0d)) throw new Exception("Back view must face model +Z");
    }
    catch (Exception error) { viewerFailure = error; }
    finally { window?.Close(); File.Delete(copy); }
});
uiThread.SetApartmentState(ApartmentState.STA); uiThread.Start(); uiThread.Join();
if (viewerFailure is not null) throw new Exception("Viewer interaction regression", viewerFailure);
Console.WriteLine("Live reload survives partial writes and retains camera/playback; rest/front/back controls verified.");
