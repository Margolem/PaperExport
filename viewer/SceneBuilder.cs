using System.IO;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Media.Media3D;

namespace PaperExport.Viewer;

public sealed class SceneBuilder
{
    public ModelVisual3D LightsVisual { get; } = new();
    public ModelVisual3D ModelVisual { get; } = new();
    public ModelVisual3D GridVisual { get; } = new();
    public ModelVisual3D HitboxVisual { get; } = new();
    private Model3DGroup? grid;
    private Model3DGroup? hitbox;
    private readonly Dictionary<string, BoneView> boneViews = [];
    private readonly Dictionary<string, BoneView> hitboxViews = [];
    private bool showPivots;
    public Point3D Center { get; private set; } = new(0, 0.8, 0);
    public double Size { get; private set; } = 2;

    public SceneBuilder()
    {
        var lights = new Model3DGroup();
        lights.Children.Add(new AmbientLight(Color.FromRgb(180, 180, 180)));
        lights.Children.Add(new DirectionalLight(Colors.White, new Vector3D(-1, -2, -1)));
        LightsVisual.Content = lights;
        SetGrid(true);
    }

    public void Build(PaperPackage package)
    {
        boneViews.Clear();
        hitboxViews.Clear();
        var root = new Model3DGroup();
        var brushes = package.Manifest.Textures.ToDictionary(
            item => item.Key,
            item => TextureBrush(package.Files[item.Value.Path]));
        var groups = new Dictionary<string, Model3DGroup>(StringComparer.Ordinal);
        foreach (var bone in package.Model.Bones)
        {
            var group = new Model3DGroup();
            groups.Add(bone.Id, group);
            var scale = new ScaleTransform3D();
            var rotation = new QuaternionRotation3D(Quaternion.Identity);
            var offset = new TranslateTransform3D();
            var transforms = new Transform3DGroup();
            transforms.Children.Add(scale);
            transforms.Children.Add(new RotateTransform3D(rotation));
            transforms.Children.Add(offset);
            group.Transform = transforms;
            foreach (var cube in bone.Cubes)
            {
                foreach (var (side, face) in cube.Faces)
                {
                    group.Children.Add(TexturedFace(cube, bone, side, face, brushes[face.Texture]));
                }
            }
            var pivot = Axes();
            if (showPivots) group.Children.Add(pivot);
            boneViews.Add(bone.Id, new BoneView(group, scale, rotation, offset, pivot));
        }
        foreach (var bone in package.Model.Bones)
            (bone.Parent is null ? root : groups[bone.Parent]).Children.Add(groups[bone.Id]);
        ModelVisual.Content = root;
        if (package.Model.Hitboxes.Count == 0)
        {
            var h = package.Entity.Hitbox;
            hitbox = WireBox(h.Width, h.Height, h.Width, new Point3D(0, h.VerticalOffset + h.Height / 2, 0));
        }
        else
        {
            var hitboxRoot = new Model3DGroup();
            var hitboxGroups = new Dictionary<string, Model3DGroup>(StringComparer.Ordinal);
            foreach (var bone in package.Model.Bones)
            {
                var group = new Model3DGroup();
                var scale = new ScaleTransform3D();
                var rotation = new QuaternionRotation3D(Quaternion.Identity);
                var offset = new TranslateTransform3D();
                var transforms = new Transform3DGroup();
                transforms.Children.Add(scale);
                transforms.Children.Add(new RotateTransform3D(rotation));
                transforms.Children.Add(offset);
                group.Transform = transforms;
                hitboxGroups.Add(bone.Id, group);
                hitboxViews.Add(bone.Id, new BoneView(group, scale, rotation, offset, new Model3DGroup()));
            }
            foreach (var region in package.Model.Hitboxes)
            {
                var bone = package.Model.Bones.First(b => b.Id == region.Bone);
                var center = new Point3D((region.From[0] + region.To[0] - 2 * bone.Pivot[0]) / 32,
                    (region.From[1] + region.To[1] - 2 * bone.Pivot[1]) / 32,
                    (region.From[2] + region.To[2] - 2 * bone.Pivot[2]) / 32);
                hitboxGroups[region.Bone].Children.Add(WireBox((region.To[0] - region.From[0]) / 16,
                    (region.To[1] - region.From[1]) / 16, (region.To[2] - region.From[2]) / 16, center));
            }
            foreach (var bone in package.Model.Bones)
                (bone.Parent is null ? hitboxRoot : hitboxGroups[bone.Parent]).Children.Add(hitboxGroups[bone.Id]);
            hitbox = hitboxRoot;
        }
        HitboxVisual.Content = null;
        var points = package.Model.Bones.SelectMany(b => b.Cubes)
            .SelectMany(c => new[] { c.From, c.To }).ToArray();
        if (points.Length > 0)
        {
            double minX = points.Min(p => p[0]) / 16, maxX = points.Max(p => p[0]) / 16;
            double minY = points.Min(p => p[1]) / 16, maxY = points.Max(p => p[1]) / 16;
            double minZ = points.Min(p => p[2]) / 16, maxZ = points.Max(p => p[2]) / 16;
            Center = new Point3D((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
            Size = Math.Max(1.5, Math.Sqrt(Math.Pow(maxX - minX, 2) + Math.Pow(maxY - minY, 2) + Math.Pow(maxZ - minZ, 2)));
        }
        ApplyPose(package, null, 0);
    }

    public void SetGrid(bool visible)
    {
        grid ??= Grid();
        GridVisual.Content = visible ? grid : null;
    }
    public void SetHitbox(bool visible) => HitboxVisual.Content = visible ? hitbox : null;
    public void SetPivots(bool visible)
    {
        showPivots = visible;
        foreach (var view in boneViews.Values)
        {
            if (visible && !view.Group.Children.Contains(view.Pivot)) view.Group.Children.Add(view.Pivot);
            if (!visible) view.Group.Children.Remove(view.Pivot);
        }
    }
    public void ApplyPose(PaperPackage package, PeAnimation? animation, double time)
    {
        foreach (var bone in package.Model.Bones)
        {
            var view = boneViews[bone.Id];
            var track = animation?.Tracks.FirstOrDefault(t => t.Bone == bone.Id);
            var localTime = animation is null ? 0 : animation.Loop == "loop" ? time % animation.Length : Math.Min(time, animation.Length);
            var position = Sample(track?.Position, localTime, [0, 0, 0]);
            var rotation = SampleRotation(track?.Rotation, localTime);
            var scale = Sample(track?.Scale, localTime, [1, 1, 1]);
            var parent = bone.Parent is null ? null : package.Model.Bones.First(b => b.Id == bone.Parent);
            view.Offset.OffsetX = (bone.Pivot[0] - (parent?.Pivot[0] ?? 0) + position[0]) / 16;
            view.Offset.OffsetY = (bone.Pivot[1] - (parent?.Pivot[1] ?? 0) + position[1]) / 16;
            view.Offset.OffsetZ = (bone.Pivot[2] - (parent?.Pivot[2] ?? 0) + position[2]) / 16;
            view.Rotation.Quaternion = Quaternion.Multiply(Euler(bone.Rotation), rotation);
            view.Scale.ScaleX = bone.Scale[0] * scale[0];
            view.Scale.ScaleY = bone.Scale[1] * scale[1];
            view.Scale.ScaleZ = bone.Scale[2] * scale[2];
            if (hitboxViews.TryGetValue(bone.Id, out var hitboxView))
            {
                hitboxView.Offset.OffsetX = view.Offset.OffsetX;
                hitboxView.Offset.OffsetY = view.Offset.OffsetY;
                hitboxView.Offset.OffsetZ = view.Offset.OffsetZ;
                hitboxView.Rotation.Quaternion = view.Rotation.Quaternion;
                hitboxView.Scale.ScaleX = view.Scale.ScaleX;
                hitboxView.Scale.ScaleY = view.Scale.ScaleY;
                hitboxView.Scale.ScaleZ = view.Scale.ScaleZ;
            }
        }
    }
    private static double[] Sample(List<PeKey>? keys, double time, double[] fallback)
    {
        if (keys is null || keys.Count == 0) return fallback;
        if (time <= keys[0].Time) return keys[0].Value;
        for (int i = 1; i < keys.Count; i++)
        {
            if (time > keys[i].Time) continue;
            var a = keys[i - 1];
            var b = keys[i];
            var factor = a.Interpolation == "step" ? 0 : (time - a.Time) / (b.Time - a.Time);
            return Enumerable.Range(0, 3).Select(axis => a.Value[axis] + (b.Value[axis] - a.Value[axis]) * factor).ToArray();
        }
        return keys[^1].Value;
    }
    private static Quaternion SampleRotation(List<PeKey>? keys, double time)
    {
        if (keys is null || keys.Count == 0) return Quaternion.Identity;
        if (time <= keys[0].Time) return Euler(keys[0].Value);
        for (int i = 1; i < keys.Count; i++)
        {
            if (time > keys[i].Time) continue;
            var a = keys[i - 1];
            var b = keys[i];
            var factor = a.Interpolation == "step" ? 0 : (time - a.Time) / (b.Time - a.Time);
            return Quaternion.Slerp(Euler(a.Value), Euler(b.Value), factor);
        }
        return Euler(keys[^1].Value);
    }
    private static Quaternion Euler(double[] degrees) => Quaternion.Multiply(
        new Quaternion(new Vector3D(0, 0, 1), degrees[2]),
        Quaternion.Multiply(new Quaternion(new Vector3D(0, 1, 0), degrees[1]),
            new Quaternion(new Vector3D(1, 0, 0), degrees[0])));
    private static ImageBrush TextureBrush(byte[] png)
    {
        using var stream = new MemoryStream(png, writable: false);
        var bitmap = new BitmapImage();
        bitmap.BeginInit();
        bitmap.CacheOption = BitmapCacheOption.OnLoad;
        bitmap.StreamSource = stream;
        bitmap.EndInit();
        bitmap.Freeze();
        // Each face is a separate mesh. A relative viewport fits the whole PNG
        // into that face's UV bounds, repeating it on every side of a cube.
        // The model UVs are already normalized against the entire atlas.
        var brush = new ImageBrush(bitmap)
        {
            Stretch = Stretch.Fill,
            ViewportUnits = BrushMappingMode.Absolute,
            Viewport = new Rect(0, 0, 1, 1)
        };
        RenderOptions.SetBitmapScalingMode(brush, BitmapScalingMode.NearestNeighbor);
        brush.Freeze();
        return brush;
    }
    private static GeometryModel3D TexturedFace(PeCube cube, PeBone bone, string side, PeFace face, ImageBrush brush)
    {
        double x0 = (cube.From[0] - bone.Pivot[0]) / 16, x1 = (cube.To[0] - bone.Pivot[0]) / 16;
        double y0 = (cube.From[1] - bone.Pivot[1]) / 16, y1 = (cube.To[1] - bone.Pivot[1]) / 16;
        double z0 = (cube.From[2] - bone.Pivot[2]) / 16, z1 = (cube.To[2] - bone.Pivot[2]) / 16;
        Point3D[] corners = side switch
        {
            "east" => [new(x1,y0,z1),new(x1,y0,z0),new(x1,y1,z0),new(x1,y1,z1)],
            "west" => [new(x0,y0,z0),new(x0,y0,z1),new(x0,y1,z1),new(x0,y1,z0)],
            "up" => [new(x0,y1,z1),new(x1,y1,z1),new(x1,y1,z0),new(x0,y1,z0)],
            "down" => [new(x0,y0,z0),new(x1,y0,z0),new(x1,y0,z1),new(x0,y0,z1)],
            "south" => [new(x0,y0,z1),new(x1,y0,z1),new(x1,y1,z1),new(x0,y1,z1)],
            _ => [new(x1,y0,z0),new(x0,y0,z0),new(x0,y1,z0),new(x1,y1,z0)]
        };
        var normalized = NormalizeUv(face.Uv);
        var u0 = normalized[0];
        var v0 = normalized[1];
        var u1 = normalized[2];
        var v1 = normalized[3];
        Point[] uv = [new(u0,v1),new(u1,v1),new(u1,v0),new(u0,v0)];
        var turns = (face.Rotation / 90) % 4;
        if (turns != 0) uv = Enumerable.Range(0, 4).Select(i => uv[(i + turns) % 4]).ToArray();
        var mesh = new MeshGeometry3D
        {
            Positions = new Point3DCollection(corners),
            TextureCoordinates = new PointCollection(uv),
            TriangleIndices = new Int32Collection([0, 1, 2, 0, 2, 3])
        };
        var material = new DiffuseMaterial(brush);
        // Minecraft item-model faces are single sided; drawing their backs makes
        // translucent textures show through from the wrong direction.
        return new GeometryModel3D(mesh, material);
    }
    // Minecraft model JSON maps a 0–16 UV grid over the entire PNG.
    public static double[] NormalizeUv(double[] uv) => uv.Select(value => value / 16).ToArray();
    private static Model3DGroup Axes()
    {
        var group = new Model3DGroup();
        group.Children.Add(SolidBox(0.16, 0.008, 0.008, new Point3D(0.08, 0, 0), Colors.Red));
        group.Children.Add(SolidBox(0.008, 0.16, 0.008, new Point3D(0, 0.08, 0), Colors.Green));
        group.Children.Add(SolidBox(0.008, 0.008, 0.16, new Point3D(0, 0, 0.08), Colors.Blue));
        return group;
    }
    private static Model3DGroup Grid()
    {
        var group = new Model3DGroup();
        for (int i = -10; i <= 10; i++)
        {
            var color = i == 0 ? Color.FromRgb(165, 165, 165) : Color.FromRgb(215, 215, 215);
            group.Children.Add(SolidBox(20, 0.002, 0.006, new Point3D(0, -0.003, i), color));
            group.Children.Add(SolidBox(0.006, 0.002, 20, new Point3D(i, -0.003, 0), color));
        }
        return group;
    }
    private static Model3DGroup WireBox(double width, double height, double depth, Point3D center)
    {
        var group = new Model3DGroup();
        var color = Color.FromRgb(220, 130, 25);
        foreach (var x in new[] { -width / 2, width / 2 })
        foreach (var z in new[] { -depth / 2, depth / 2 })
            group.Children.Add(SolidBox(0.008, height, 0.008, new Point3D(center.X+x, center.Y, center.Z+z), color));
        foreach (var y in new[] { center.Y - height / 2, center.Y + height / 2 })
        {
            foreach (var z in new[] { -depth / 2, depth / 2 })
                group.Children.Add(SolidBox(width, 0.008, 0.008, new Point3D(center.X, y, center.Z+z), color));
            foreach (var x in new[] { -width / 2, width / 2 })
                group.Children.Add(SolidBox(0.008, 0.008, depth, new Point3D(center.X+x, y, center.Z), color));
        }
        return group;
    }
    private static GeometryModel3D SolidBox(double width, double height, double depth, Point3D center, Color color)
    {
        double x0 = center.X - width / 2, x1 = center.X + width / 2;
        double y0 = center.Y - height / 2, y1 = center.Y + height / 2;
        double z0 = center.Z - depth / 2, z1 = center.Z + depth / 2;
        Point3D[] corners =
        [
            new(x0,y0,z0),new(x1,y0,z0),new(x1,y1,z0),new(x0,y1,z0),
            new(x0,y0,z1),new(x1,y0,z1),new(x1,y1,z1),new(x0,y1,z1)
        ];
        int[] faces = [0,3,2,1, 4,5,6,7, 0,4,7,3, 1,2,6,5, 3,7,6,2, 0,1,5,4];
        var mesh = new MeshGeometry3D();
        for (int i = 0; i < faces.Length; i += 4)
        {
            int start = mesh.Positions.Count;
            for (int j = 0; j < 4; j++) mesh.Positions.Add(corners[faces[i + j]]);
            foreach (var index in new[] { 0, 1, 2, 0, 2, 3 }) mesh.TriangleIndices.Add(start + index);
        }
        var material = new DiffuseMaterial(new SolidColorBrush(color));
        return new GeometryModel3D(mesh, material) { BackMaterial = material };
    }
    private sealed record BoneView(Model3DGroup Group, ScaleTransform3D Scale,
        QuaternionRotation3D Rotation, TranslateTransform3D Offset, Model3DGroup Pivot);
}
