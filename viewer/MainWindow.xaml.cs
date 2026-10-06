using System.Diagnostics;
using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Media.Media3D;
using Microsoft.Win32;

namespace PaperExport.Viewer;

public partial class MainWindow : Window
{
    private readonly SceneBuilder scene = new();
    private readonly Stopwatch clock = Stopwatch.StartNew();
    private PaperPackage? package;
    private PeAnimation? animation;
    private bool playing;
    private bool updatingTimeline;
    private double elapsed;
    private double lastFrame;
    private double yaw = Math.PI - 0.65, pitch = 0.35, distance = 4;
    private Point3D target = new(0, 0.8, 0);
    private Point lastMouse;
    private bool rotating, panning;

    public MainWindow()
    {
        InitializeComponent();
        SceneViewport.Children.Add(scene.LightsVisual);
        SceneViewport.Children.Add(scene.GridVisual);
        SceneViewport.Children.Add(scene.ModelVisual);
        SceneViewport.Children.Add(scene.HitboxVisual);
        CommandBindings.Add(new CommandBinding(ApplicationCommands.Open, (_, _) => OpenDialog()));
        InputBindings.Add(new KeyBinding(ApplicationCommands.Open, Key.O, ModifierKeys.Control));
        CompositionTarget.Rendering += RenderFrame;
        Loaded += (_, _) =>
        {
            var args = Environment.GetCommandLineArgs().Skip(1).ToArray();
            var file = args
                .FirstOrDefault(arg => arg.EndsWith(".paperexport", StringComparison.OrdinalIgnoreCase));
            if (file is not null) OpenPackage(file);
            else ResetCamera();
            var screenshot = Array.IndexOf(args, "--screenshot");
            if (screenshot >= 0 && screenshot + 1 < args.Length)
                _ = SaveScreenshot(args[screenshot + 1]);
        };
        Closed += (_, _) => CompositionTarget.Rendering -= RenderFrame;
    }

    private void OpenDialog()
    {
        var dialog = new OpenFileDialog
        {
            Title = "Open PaperExport package",
            Filter = "PaperExport package (*.paperexport)|*.paperexport",
            CheckFileExists = true
        };
        if (dialog.ShowDialog(this) == true) OpenPackage(dialog.FileName);
    }
    private void OpenPackage(string path)
    {
        try
        {
            var loaded = PackageReader.Load(path);
            package = loaded;
            scene.Build(loaded);
            scene.SetHitbox(HitboxCheck.IsChecked == true);
            scene.SetPivots(PivotsCheck.IsChecked == true);
            target = scene.Center;
            ResetCamera();
            EmptyMessage.Visibility = Visibility.Collapsed;
            EntityName.Text = loaded.Manifest.Name;
            EntityId.Text = loaded.Manifest.Id;
            EntityStats.Text = "Paper 1.21–26.3\n" +
                $"Health\t{loaded.Entity.Stats.MaxHealth:g}\n" +
                $"Behavior\t{loaded.Entity.Behavior}\n" +
                $"Base\t{loaded.Entity.BaseEntity}\n" +
                $"Parts\t{loaded.DisplayNodes}\n" +
                $"Hitboxes\t{loaded.Model.Hitboxes.Count}\n" +
                $"Textures\t{loaded.Manifest.Textures.Count}\n" +
                $"Format\tv{loaded.Manifest.FormatVersion}";
            FillHierarchy(loaded);
            FillTextures(loaded);
            AnimationList.Items.Clear();
            foreach (var item in loaded.Animations.Values)
                AnimationList.Items.Add(new AnimationItem(item));
            if (AnimationList.Items.Count > 0) AnimationList.SelectedIndex = 0;
            else { animation = null; playing = false; elapsed = 0; }
            ValidationText.Text = $"✓ Manifest and archive valid\n" +
                $"✓ {loaded.Model.Bones.Count} bones\n" +
                $"✓ {loaded.Manifest.Textures.Count} textures\n" +
                $"✓ {loaded.Animations.Count} animation references\n" +
                (!loaded.Animations.ContainsKey("death") ? "⚠ No death animation\n" : "") +
                $"Runtime estimate: {(loaded.DisplayNodes > 32 ? "HIGH" : loaded.DisplayNodes > 12 ? "MEDIUM" : "LOW")}";
            SoundsText.Text = loaded.Manifest.Sounds.Count == 0 ? "None" :
                string.Join("\n", loaded.Manifest.Sounds.Keys.Select(name => name +
                    (loaded.Entity.SoundCues.Where(cue => cue.Value == name).Select(cue => " (" + cue.Key + ")").FirstOrDefault() ?? "")));
            PackageFiles.Items.Clear();
            foreach (var entry in loaded.Files.Keys.Order(StringComparer.Ordinal)) PackageFiles.Items.Add(entry);
            FilePreview.Clear();
            StatusText.Text = $"{loaded.FileName} · {loaded.DisplayNodes} display nodes · Valid";
            Title = $"{loaded.FileName} - PaperExport Viewer";
        }
        catch (Exception error)
        {
            StatusText.Text = error.Message;
            MessageBox.Show(this, error.Message, "Could not open package", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }
    private void FillHierarchy(PaperPackage loaded)
    {
        HierarchyTree.Items.Clear();
        var nodes = loaded.Model.Bones.ToDictionary(
            bone => bone.Id,
            bone => new TreeViewItem { Header = $"{bone.Name} · {bone.Cubes.Count}", IsExpanded = true });
        foreach (var bone in loaded.Model.Bones)
            if (bone.Parent is null) HierarchyTree.Items.Add(nodes[bone.Id]);
            else nodes[bone.Parent].Items.Add(nodes[bone.Id]);
    }
    private void FillTextures(PaperPackage loaded)
    {
        TexturePanel.Children.Clear();
        foreach (var (name, info) in loaded.Manifest.Textures)
        {
            var bitmap = Bitmap(loaded.Files[info.Path]);
            var image = new Image { Source = bitmap, Width = 65, Height = 65, Stretch = Stretch.Uniform };
            RenderOptions.SetBitmapScalingMode(image, BitmapScalingMode.NearestNeighbor);
            var button = new Button
            {
                Width = 82, Margin = new Thickness(0, 0, 5, 5), Padding = new Thickness(3),
                Content = new StackPanel
                {
                    Children =
                    {
                        image,
                        new TextBlock { Text = name, FontSize = 10, TextAlignment = TextAlignment.Center }
                    }
                }
            };
            button.Click += (_, _) => ShowTexture(name, bitmap);
            TexturePanel.Children.Add(button);
        }
    }
    private static BitmapImage Bitmap(byte[] png)
    {
        using var stream = new MemoryStream(png, false);
        var image = new BitmapImage();
        image.BeginInit();
        image.CacheOption = BitmapCacheOption.OnLoad;
        image.StreamSource = stream;
        image.EndInit();
        image.Freeze();
        return image;
    }
    private void ShowTexture(string name, BitmapImage bitmap)
    {
        var image = new Image { Source = bitmap, Stretch = Stretch.Uniform, Margin = new Thickness(15) };
        RenderOptions.SetBitmapScalingMode(image, BitmapScalingMode.NearestNeighbor);
        new Window
        {
            Title = $"Texture: {name}", Owner = this, Width = 420, Height = 420,
            Background = Brushes.White, Content = image
        }.Show();
    }
    private async Task SaveScreenshot(string path)
    {
        await Task.Delay(1500);
        var bitmap = new RenderTargetBitmap(
            Math.Max(1, (int)ActualWidth), Math.Max(1, (int)ActualHeight), 96, 96, PixelFormats.Pbgra32);
        bitmap.Render(this);
        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(bitmap));
        using var stream = File.Create(path);
        encoder.Save(stream);
        Close();
    }
    private void ResetCamera()
    {
        if (package is not null) target = scene.Center;
        yaw = Math.PI - 0.65;
        pitch = 0.35;
        distance = Math.Max(2.5, scene.Size * 2.2);
        UpdateCamera();
    }
    private void UpdateCamera()
    {
        var horizontal = Math.Cos(pitch) * distance;
        var point = new Point3D(
            target.X + Math.Sin(yaw) * horizontal,
            target.Y + Math.Sin(pitch) * distance,
            target.Z + Math.Cos(yaw) * horizontal);
        SceneCamera.Position = point;
        SceneCamera.LookDirection = target - point;
        SceneCamera.UpDirection = new Vector3D(0, 1, 0);
    }
    private void RenderFrame(object? sender, EventArgs e)
    {
        var now = clock.Elapsed.TotalSeconds;
        var dt = Math.Clamp(now - lastFrame, 0, 0.1);
        lastFrame = now;
        if (package is null || animation is null) return;
        if (playing)
        {
            var speed = double.TryParse((SpeedBox.SelectedItem as ComboBoxItem)?.Tag?.ToString(), out var value) ? value : 1;
            elapsed += dt * speed;
            if (elapsed >= animation.Length)
            {
                if (LoopCheck.IsChecked == true) elapsed %= animation.Length;
                else { elapsed = animation.Length; playing = false; PlayButton.Content = "Play"; }
            }
        }
        scene.ApplyPose(package, animation, elapsed);
        updatingTimeline = true;
        Timeline.Value = elapsed;
        updatingTimeline = false;
        TimeText.Text = $"{elapsed:0.00} / {animation.Length:0.00} s";
    }
    private void Animation_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        animation = (AnimationList.SelectedItem as AnimationItem)?.Animation;
        elapsed = 0;
        playing = animation is not null;
        PlayButton.Content = playing ? "Pause" : "Play";
        Timeline.Maximum = animation?.Length ?? 1;
    }
    private void Timeline_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (updatingTimeline || package is null || animation is null) return;
        elapsed = Timeline.Value;
        playing = false;
        PlayButton.Content = "Play";
        scene.ApplyPose(package, animation, elapsed);
    }
    private void PackageFile_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (package is null || PackageFiles.SelectedItem is not string path) return;
        if (path.EndsWith(".json") || path.EndsWith(".mcmeta"))
        {
            try
            {
                using var json = JsonDocument.Parse(package.Files[path]);
                FilePreview.Text = JsonSerializer.Serialize(json.RootElement, new JsonSerializerOptions { WriteIndented = true });
            }
            catch { FilePreview.Text = "Invalid JSON"; }
        }
        else FilePreview.Text = path.EndsWith(".png") ? "PNG image (see Textures)" : "Binary resource";
    }
    private void Viewport_MouseDown(object sender, MouseButtonEventArgs e)
    {
        if (package is null) return;
        lastMouse = e.GetPosition(ViewportHost);
        rotating = e.ChangedButton == MouseButton.Left;
        panning = e.ChangedButton == MouseButton.Right || e.ChangedButton == MouseButton.Middle;
        if (rotating || panning) ViewportHost.CaptureMouse();
    }
    private void Viewport_MouseMove(object sender, MouseEventArgs e)
    {
        if (!rotating && !panning) return;
        var point = e.GetPosition(ViewportHost);
        var dx = point.X - lastMouse.X;
        var dy = point.Y - lastMouse.Y;
        lastMouse = point;
        if (rotating)
        {
            yaw += dx * 0.008;
            pitch = Math.Clamp(pitch + dy * 0.008, -1.45, 1.45);
        }
        if (panning)
        {
            var right = new Vector3D(Math.Cos(yaw), 0, -Math.Sin(yaw));
            var up = new Vector3D(-Math.Sin(yaw) * Math.Sin(pitch), Math.Cos(pitch), -Math.Cos(yaw) * Math.Sin(pitch));
            target += right * (-dx * distance / 700) + up * (dy * distance / 700);
        }
        UpdateCamera();
    }
    private void Viewport_MouseUp(object sender, MouseButtonEventArgs e)
    {
        rotating = panning = false;
        ViewportHost.ReleaseMouseCapture();
    }
    private void Viewport_MouseWheel(object sender, MouseWheelEventArgs e)
    {
        distance = Math.Clamp(distance * Math.Pow(0.9, e.Delta / 120.0), 0.3, 100);
        UpdateCamera();
    }
    private void Window_DragOver(object sender, DragEventArgs e)
    {
        e.Effects = e.Data.GetDataPresent(DataFormats.FileDrop) ? DragDropEffects.Copy : DragDropEffects.None;
        e.Handled = true;
    }
    private void Window_Drop(object sender, DragEventArgs e)
    {
        if (e.Data.GetData(DataFormats.FileDrop) is string[] files && files.Length > 0) OpenPackage(files[0]);
    }
    private void Open_Click(object sender, RoutedEventArgs e) => OpenDialog();
    private void Exit_Click(object sender, RoutedEventArgs e) => Close();
    private void Reset_Click(object sender, RoutedEventArgs e) => ResetCamera();
    private void Play_Click(object sender, RoutedEventArgs e)
    {
        if (animation is null) return;
        playing = !playing;
        PlayButton.Content = playing ? "Pause" : "Play";
    }
    private void Grid_Checked(object sender, RoutedEventArgs e) => scene.SetGrid(GridCheck.IsChecked == true);
    private void Hitbox_Checked(object sender, RoutedEventArgs e) => scene.SetHitbox(HitboxCheck.IsChecked == true);
    private void Pivots_Checked(object sender, RoutedEventArgs e) => scene.SetPivots(PivotsCheck.IsChecked == true);
    private sealed record AnimationItem(PeAnimation Animation)
    {
        public override string ToString() => $"{Animation.Name} · {Animation.Length:0.00}s";
    }
}
