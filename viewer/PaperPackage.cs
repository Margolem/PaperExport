using System.IO.Compression;
using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace PaperExport.Viewer;

public sealed class PaperPackage
{
    public required string FileName { get; init; }
    public required PeManifest Manifest { get; init; }
    public required PeModel Model { get; init; }
    public required PeEntity Entity { get; init; }
    public required Dictionary<string, PeAnimation> Animations { get; init; }
    public required Dictionary<string, byte[]> Files { get; init; }
    public int DisplayNodes => Model.Bones.Count(b => b.Cubes.Count > 0);
}

public sealed class PeManifest
{
    public string Format { get; set; } = "";
    [JsonPropertyName("format_version")] public int FormatVersion { get; set; }
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string Author { get; set; } = "";
    public string Description { get; set; } = "";
    [JsonPropertyName("minecraft_version")] public string MinecraftVersion { get; set; } = "";
    [JsonPropertyName("paper_version")] public string PaperVersion { get; set; } = "";
    public string Model { get; set; } = "";
    public string Entity { get; set; } = "";
    [JsonPropertyName("resource_pack")] public string ResourcePack { get; set; } = "";
    public Dictionary<string, string> Animations { get; set; } = [];
    public Dictionary<string, PeTexture> Textures { get; set; } = [];
    public Dictionary<string, PeSound> Sounds { get; set; } = [];
}
public sealed class PeSound { public string Path { get; set; } = ""; }
public sealed class PeTexture
{
    public string Path { get; set; } = "";
    public int Width { get; set; }
    public int Height { get; set; }
}
public sealed class PeModel
{
    [JsonPropertyName("texture_size")] public double[] TextureSize { get; set; } = [];
    public List<PeBone> Bones { get; set; } = [];
    public List<PeHitboxRegion> Hitboxes { get; set; } = [];
}
public sealed class PeHitboxRegion
{
    public string Id { get; set; } = "";
    public string Bone { get; set; } = "";
    public double[] From { get; set; } = [];
    public double[] To { get; set; } = [];
}
public sealed class PeBone
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string? Parent { get; set; }
    public double[] Pivot { get; set; } = [];
    public double[] Rotation { get; set; } = [];
    public double[] Scale { get; set; } = [];
    public List<PeCube> Cubes { get; set; } = [];
}
public sealed class PeCube
{
    public string Name { get; set; } = "";
    public double[] From { get; set; } = [];
    public double[] To { get; set; } = [];
    public Dictionary<string, PeFace> Faces { get; set; } = [];
}
public sealed class PeFace
{
    public double[] Uv { get; set; } = [];
    public string Texture { get; set; } = "";
    public int Rotation { get; set; }
}
public sealed class PeEntity
{
    [JsonPropertyName("base_entity")] public string BaseEntity { get; set; } = "";
    public string Behavior { get; set; } = "";
    public PeStats Stats { get; set; } = new();
    public PeHitbox Hitbox { get; set; } = new();
    [JsonPropertyName("sound_cues")] public Dictionary<string,string> SoundCues { get; set; } = [];
    public PeBoss? Boss { get; set; }
}
public sealed class PeBoss
{
    public bool Enabled { get; set; }
    public string Title { get; set; } = "";
    [JsonPropertyName("bar_color")] public string BarColor { get; set; } = "";
    [JsonPropertyName("bar_style")] public string BarStyle { get; set; } = "";
    public double Range { get; set; }
    public List<PePhase> Phases { get; set; } = [];
}
public sealed class PePhase
{
    [JsonPropertyName("below_health")] public double BelowHealth { get; set; }
    public string? Animation { get; set; }
    public string? Sound { get; set; }
}
public sealed class PeStats
{
    [JsonPropertyName("max_health")] public double MaxHealth { get; set; }
}
public sealed class PeHitbox
{
    public double Width { get; set; }
    public double Height { get; set; }
    [JsonPropertyName("vertical_offset")] public double VerticalOffset { get; set; }
}
public sealed class PeAnimation
{
    public string Name { get; set; } = "";
    public double Length { get; set; }
    public string Loop { get; set; } = "";
    public List<PeTrack> Tracks { get; set; } = [];
}
public sealed class PeTrack
{
    public string Bone { get; set; } = "";
    public List<PeKey>? Position { get; set; }
    public List<PeKey>? Rotation { get; set; }
    public List<PeKey>? Scale { get; set; }
}
public sealed class PeKey
{
    public double Time { get; set; }
    public double[] Value { get; set; } = [];
    public string Interpolation { get; set; } = "linear";
}

public static class PackageReader
{
    private const int MaxArchive = 32 * 1024 * 1024;
    private const int MaxExpanded = 96 * 1024 * 1024;
    private const int MaxFile = 16 * 1024 * 1024;
    private const int MaxJson = 2 * 1024 * 1024;
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };
    private static readonly Regex IdPattern = new(@"^[a-z0-9_.-]+:[a-z0-9_./-]+$", RegexOptions.CultureInvariant);
    private static readonly Regex SegmentPattern = new(@"^[a-z0-9_.-]+$", RegexOptions.CultureInvariant);
    private static readonly HashSet<string> ExecutableExtensions = new(StringComparer.OrdinalIgnoreCase)
    { ".jar", ".class", ".dll", ".exe", ".bat", ".cmd", ".ps1", ".sh", ".js", ".mjs", ".cjs", ".py", ".so", ".dylib" };

    public static PaperPackage Load(string path)
    {
        if (!path.EndsWith(".paperexport", StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("Choose a .paperexport file.");
        var info = new FileInfo(path);
        if (!info.Exists || info.Length is < 22 or > MaxArchive)
            throw new InvalidDataException("Archive is empty or exceeds 32 MiB.");
        var files = new Dictionary<string, byte[]>(StringComparer.Ordinal);
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        using (var archive = ZipFile.OpenRead(path))
        {
            if (archive.Entries.Count is < 1 or > 512) throw new InvalidDataException("ZIP file count exceeds limits.");
            long total = 0;
            foreach (var entry in archive.Entries)
            {
                var name = entry.FullName;
                if (name.EndsWith('/'))
                {
                    if (!SafePath(name[..^1])) throw new InvalidDataException($"Unsafe ZIP directory: {name}");
                    continue;
                }
                if (!SafePath(name) || !seen.Add(name) || ExecutableExtensions.Contains(Path.GetExtension(name)))
                    throw new InvalidDataException($"Unsafe or duplicate ZIP path: {name}");
                if (!AllowedEntry(name)) throw new InvalidDataException($"Unexpected archive entry: {name}");
                if (entry.Length > MaxFile || entry.CompressedLength > MaxArchive)
                    throw new InvalidDataException($"Entry exceeds size limits: {name}");
                total += entry.Length;
                if (total > MaxExpanded) throw new InvalidDataException("Expanded archive exceeds 96 MiB.");
                using var source = entry.Open();
                using var target = new MemoryStream();
                source.CopyTo(target);
                var bytes = target.ToArray();
                if (bytes.LongLength != entry.Length || Crc32(bytes) != entry.Crc32)
                    throw new InvalidDataException($"Corrupt ZIP entry: {name}");
                files.Add(name, bytes);
            }
        }
        T ReadJson<T>(string name)
        {
            if (!files.TryGetValue(name, out var bytes)) throw new InvalidDataException($"Missing {name}");
            if (bytes.Length > MaxJson) throw new InvalidDataException($"JSON too large: {name}");
            try { return JsonSerializer.Deserialize<T>(bytes, JsonOptions) ?? throw new JsonException(); }
            catch (JsonException error) { throw new InvalidDataException($"Invalid JSON: {name}", error); }
        }
        var manifest = ReadJson<PeManifest>("manifest.json");
        if (manifest.Format != "paperexport" || manifest.FormatVersion != 1)
            throw new InvalidDataException($"Unsupported PaperExport format version: {manifest.FormatVersion}");
        if (!IdPattern.IsMatch(manifest.Id) || manifest.Id.Contains("..") || manifest.Id.Contains("//"))
            throw new InvalidDataException("Invalid namespaced entity ID.");
        if (manifest.MinecraftVersion != manifest.PaperVersion ||
            manifest.MinecraftVersion is not ("1.21.11" or "1.21-26.3"))
            throw new InvalidDataException("Unsupported Minecraft/Paper compatibility marker.");
        if (manifest.Model != "model/model.json" || manifest.Entity != "entity/entity.json" || manifest.ResourcePack != "resourcepack/")
            throw new InvalidDataException("Invalid fixed package paths.");
        var model = ReadJson<PeModel>(manifest.Model);
        var entity = ReadJson<PeEntity>(manifest.Entity);
        if (model.Bones.Count is < 1 or > 128 || model.TextureSize.Length != 2)
            throw new InvalidDataException("Invalid model hierarchy.");
        var ids = new HashSet<string>(StringComparer.Ordinal);
        foreach (var bone in model.Bones)
        {
            if (!SegmentPattern.IsMatch(bone.Id) || !ids.Add(bone.Id) ||
                !Vector(bone.Pivot) || !Vector(bone.Rotation) || !Vector(bone.Scale) || bone.Cubes.Count > 256)
                throw new InvalidDataException($"Invalid bone: {bone.Id}");
            foreach (var cube in bone.Cubes)
            {
                if (!Vector(cube.From) || !Vector(cube.To) || Enumerable.Range(0, 3).Any(i => cube.From[i] >= cube.To[i]))
                    throw new InvalidDataException($"Invalid cube in bone {bone.Id}");
                foreach (var (side, face) in cube.Faces)
                    if (!new[] { "north", "south", "east", "west", "up", "down" }.Contains(side) ||
                        !manifest.Textures.ContainsKey(face.Texture) || face.Uv.Length != 4 ||
                        face.Uv.Any(v => !double.IsFinite(v)))
                        throw new InvalidDataException($"Invalid {side} face in bone {bone.Id}");
            }
        }
        if (model.Hitboxes.Count > 64) throw new InvalidDataException("Too many pe_hitbox regions.");
        var hitboxIds = new HashSet<string>(StringComparer.Ordinal);
        foreach (var hitbox in model.Hitboxes)
            if (!SegmentPattern.IsMatch(hitbox.Id) || !hitboxIds.Add(hitbox.Id) || !ids.Contains(hitbox.Bone) ||
                !Vector(hitbox.From) || !Vector(hitbox.To) || Enumerable.Range(0, 3).Any(i => hitbox.From[i] >= hitbox.To[i]))
                throw new InvalidDataException($"Invalid pe_hitbox: {hitbox.Id}");
        foreach (var bone in model.Bones)
        {
            var visited = new HashSet<string> { bone.Id };
            for (var parent = bone.Parent; parent is not null;)
            {
                if (!ids.Contains(parent) || !visited.Add(parent)) throw new InvalidDataException($"Invalid bone parent: {bone.Id}");
                parent = model.Bones.First(b => b.Id == parent).Parent;
            }
        }
        foreach (var (name, texture) in manifest.Textures)
        {
            if (!SegmentPattern.IsMatch(name) || texture.Path != $"textures/{name}.png" ||
                texture.Width is < 1 or > 2048 || texture.Height is < 1 or > 2048 ||
                !files.TryGetValue(texture.Path, out var png) || !PngSize(png, out int width, out int height) ||
                width != texture.Width || height != texture.Height)
                throw new InvalidDataException($"Invalid or missing texture: {name}");
        }
        foreach (var (name, sound) in manifest.Sounds)
        {
            if (!SegmentPattern.IsMatch(name) || sound.Path != $"sounds/{name}.ogg" ||
                !files.TryGetValue(sound.Path, out var ogg) || ogg.Length is < 32 or > 8 * 1024 * 1024 ||
                !ogg.AsSpan(0, 4).SequenceEqual("OggS"u8))
                throw new InvalidDataException($"Invalid or missing Ogg sound: {name}");
        }
        if (entity.Hitbox.Width <= 0 || entity.Hitbox.Height <= 0 || !double.IsFinite(entity.Stats.MaxHealth))
            throw new InvalidDataException("Invalid entity settings.");
        foreach (var cue in entity.SoundCues)
            if (!new[] { "spawn", "ambient", "attack", "hurt", "death" }.Contains(cue.Key) ||
                !manifest.Sounds.ContainsKey(cue.Value))
                throw new InvalidDataException($"Unknown sound cue: {cue.Key}");
        var animations = new Dictionary<string, PeAnimation>(StringComparer.Ordinal);
        foreach (var (name, reference) in manifest.Animations)
        {
            if (!SegmentPattern.IsMatch(name) || reference != $"animations/{name}.json")
                throw new InvalidDataException($"Invalid animation path: {name}");
            var animation = ReadJson<PeAnimation>(reference);
            if (animation.Name != name || !double.IsFinite(animation.Length) || animation.Length <= 0)
                throw new InvalidDataException($"Invalid animation: {name}");
            foreach (var track in animation.Tracks)
                if (!ids.Contains(track.Bone) || !KeysValid(track.Position, animation.Length) ||
                    !KeysValid(track.Rotation, animation.Length) || !KeysValid(track.Scale, animation.Length))
                    throw new InvalidDataException($"Invalid animation track: {name}/{track.Bone}");
            animations.Add(name, animation);
        }
        if (!files.ContainsKey("resourcepack/pack.mcmeta"))
            throw new InvalidDataException("Missing resource pack metadata.");
        var parts = manifest.Id.Split(':', 2);
        foreach (var sound in manifest.Sounds.Keys)
            if (!files.ContainsKey($"resourcepack/assets/{parts[0]}/sounds/paperexport/{parts[1]}/{sound}.ogg"))
                throw new InvalidDataException($"Missing resource pack sound: {sound}");
        if (manifest.Sounds.Count > 0 && !files.ContainsKey($"resourcepack/assets/{parts[0]}/sounds.json"))
            throw new InvalidDataException("Missing resource pack sounds.json.");
        return new PaperPackage { FileName = Path.GetFileName(path), Manifest = manifest, Model = model, Entity = entity, Animations = animations, Files = files };
    }

    private static bool KeysValid(List<PeKey>? keys, double length)
    {
        if (keys is null) return true;
        if (keys.Count > 4096) return false;
        double previous = -1;
        foreach (var key in keys)
        {
            if (!double.IsFinite(key.Time) || key.Time < 0 || key.Time > length || key.Time <= previous ||
                !Vector(key.Value) || key.Interpolation is not ("linear" or "step")) return false;
            previous = key.Time;
        }
        return true;
    }
    private static bool Vector(double[] values) => values.Length == 3 && values.All(double.IsFinite);
    private static bool SafePath(string name) => name.Length is > 0 and <= 240 && !name.StartsWith('/') &&
        !name.Contains('\\') && !name.Contains(':') && name.Split('/').All(part => part is not ("" or "." or "..") &&
            part.All(c => char.IsAsciiLetterOrDigit(c) || c is '_' or '.' or '-'));
    private static bool AllowedEntry(string name) => name is "manifest.json" or "model/model.json" or
        "entity/entity.json" or "resourcepack/pack.mcmeta" or "metadata/export.json" ||
        Regex.IsMatch(name, @"^animations/[a-z0-9_.-]+\.json$") ||
        Regex.IsMatch(name, @"^textures/[a-z0-9_.-]+\.png$") ||
        Regex.IsMatch(name, @"^sounds/[a-z0-9_.-]+\.ogg$") ||
        Regex.IsMatch(name, @"^preview/[a-z0-9_.-]+\.png$") ||
        Regex.IsMatch(name, @"^resourcepack/assets/[a-z0-9_./-]+\.(json|png|ogg|mcmeta)$");
    private static bool PngSize(byte[] data, out int width, out int height)
    {
        width = height = 0;
        if (data.Length < 24 || !data.AsSpan(0, 8).SequenceEqual(new byte[] { 137, 80, 78, 71, 13, 10, 26, 10 }) ||
            !data.AsSpan(12, 4).SequenceEqual("IHDR"u8)) return false;
        width = System.Buffers.Binary.BinaryPrimitives.ReadInt32BigEndian(data.AsSpan(16, 4));
        height = System.Buffers.Binary.BinaryPrimitives.ReadInt32BigEndian(data.AsSpan(20, 4));
        return width is > 0 and <= 2048 && height is > 0 and <= 2048;
    }
    private static uint Crc32(ReadOnlySpan<byte> data)
    {
        uint crc = 0xFFFFFFFF;
        foreach (byte value in data)
        {
            crc ^= value;
            for (int bit = 0; bit < 8; bit++) crc = (crc >> 1) ^ ((crc & 1) != 0 ? 0xEDB88320u : 0u);
        }
        return ~crc;
    }
}
