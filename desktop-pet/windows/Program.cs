using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text.Json;

namespace BatiaoPet;

static class Program
{
    [STAThread]
    static void Main()
    {
        // 只允许开一只八条
        using var mutex = new Mutex(true, "BatiaoPet.SingleInstance", out bool first);
        if (!first) return;
        ApplicationConfiguration.Initialize();
        Application.Run(new PetForm());
    }
}

// MARK: - 素材（表情帧打包在 exe 里）

sealed class Anim
{
    public Bitmap[] Frames = Array.Empty<Bitmap>();
    public double[] Delays = Array.Empty<double>();
}

sealed class Library
{
    readonly Assembly asm = Assembly.GetExecutingAssembly();
    readonly Dictionary<string, string> names;
    readonly Dictionary<string, Anim> cache = new();
    public readonly Dictionary<string, string[]> Groups;
    readonly Dictionary<string, double[]> delays;

    public Library()
    {
        // 资源名里的目录分隔符在不同系统上编译时可能是 / 或 \，统一成 /
        names = asm.GetManifestResourceNames().ToDictionary(n => n.Replace('\\', '/'), n => n);
        using var s = asm.GetManifestResourceStream(names["assets/manifest.json"]);
        using var doc = JsonDocument.Parse(s);
        Groups = doc.RootElement.GetProperty("groups").EnumerateObject()
            .ToDictionary(p => p.Name, p => p.Value.EnumerateArray().Select(v => v.GetString()).ToArray());
        delays = doc.RootElement.GetProperty("delays").EnumerateObject()
            .ToDictionary(p => p.Name, p => p.Value.EnumerateArray().Select(v => v.GetDouble()).ToArray());
    }

    public Anim Get(string id)
    {
        if (cache.TryGetValue(id, out var a)) return a;
        var d = delays[id];
        var frames = new Bitmap[d.Length];
        for (int i = 0; i < d.Length; i++)
        {
            using var s = asm.GetManifestResourceStream(names[$"assets/{id}/{i:000}.png"]);
            frames[i] = new Bitmap(s);
        }
        a = new Anim { Frames = frames, Delays = d.Select(x => Math.Max(x, 0.04)).ToArray() };
        cache[id] = a;
        return a;
    }

    public string Pick(string group) => Groups[group][Random.Shared.Next(Groups[group].Length)];
}

// MARK: - 台词

static class Lines
{
    public static readonly string[] Greet = { "咪～八条上岗了。", "来啦？就知道你会想咪。" };
    public static readonly string[] Pet = { "再摸一下，就一下。", "嗯…允许你多摸三秒。", "咪才没有觉得很舒服。", "左边也要摸。" };
    public static readonly string[] PetTooMuch = { "摸够了没！", "再摸就收费了，一下一根小鱼干。", "咪的毛要被你摸秃了！" };
    public static readonly string[] Feed = { "小鱼干！你是好人。", "吃完再说，别打扰咪。", "就这点？不够塞牙缝的。" };
    public static readonly string[] Drag = { "放咪下来！", "咪恐高！", "要去哪儿？" };
    public static readonly string[] Land = { "稳稳落地。", "哼，下次轻点。" };
    public static readonly string[] Walk = { "巡逻中。", "咪去那边看看。", "散步时间。" };
    public static readonly string[] Idle = { "盯——", "咪在摸鱼，你也摸会儿？", "你在忙啥？带咪一个。", "咪饿了，只是说说。" };
    public static readonly string[] Sleep = { "困了，别吵。", "Zzz…", "咪先睡一会儿。" };
    public static readonly string[] Wake = { "谁叫咪？", "咪没睡，咪在思考。" };
    public static readonly string[] Remind = { "坐太久啦，起来伸个懒腰。", "喝口水，咪看着你喝。", "眼睛累了吧？看看远处。" };
    public static string Any(string[] a) => a[Random.Shared.Next(a.Length)];
}

// MARK: - 设置（存在 %APPDATA%\BatiaoPet\settings.json）

sealed class Settings
{
    public int Size { get; set; } = 140;
    public bool Remind { get; set; } = true;
    public bool Still { get; set; }

    static string PathOf => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "BatiaoPet", "settings.json");

    public static Settings Load()
    {
        try { return JsonSerializer.Deserialize<Settings>(File.ReadAllText(PathOf)) ?? new Settings(); }
        catch { return new Settings(); }
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(System.IO.Path.GetDirectoryName(PathOf));
            File.WriteAllText(PathOf, JsonSerializer.Serialize(this));
        }
        catch { }
    }
}

// MARK: - 桌宠窗口（分层窗口，按像素透明，透明处点击会穿透）

sealed class PetForm : Form
{
    enum Mode { Idle, Walk, Hop, React, Sleep, Drag, Fall }

    readonly Library lib = new();
    readonly Settings cfg = Settings.Load();
    readonly System.Windows.Forms.Timer timer = new() { Interval = 15 };
    readonly Stopwatch clock = Stopwatch.StartNew();

    Mode mode = Mode.Fall;
    double modeEnds, t, last, frameT, lastPet, bubbleUntil;
    Anim anim;
    int frame, petCount;
    float px, py, vy, dir = -1, squash;   // px/py：窗口左上角，屏幕像素；vy 向上为正
    DateTime lastTouch = DateTime.Now, lastRemind = DateTime.Now;
    string bubble;
    Point dragStart, winStart;
    bool mouseDown, dragged, doubleClick;

    float Dpi => DeviceDpi / 96f;
    int Sz => (int)(cfg.Size * Dpi);
    int WinW => (int)(Sz * 1.5f);
    int WinH => Sz + (int)(70 * Dpi);

    public PetForm()
    {
        FormBorderStyle = FormBorderStyle.None;
        ShowInTaskbar = false;
        StartPosition = FormStartPosition.Manual;
        TopMost = true;

        var wa = Screen.PrimaryScreen.WorkingArea;
        px = wa.Right - WinW - 60;
        py = wa.Bottom - WinH - 260;
        Bounds = new Rectangle((int)px, (int)py, WinW, WinH);

        Play(lib.Pick("drag"));
        Say(Lines.Any(Lines.Greet));
        timer.Tick += (_, _) => Step();
        timer.Start();
    }

    protected override CreateParams CreateParams
    {
        get
        {
            var cp = base.CreateParams;
            cp.ExStyle |= 0x00080000 /* LAYERED */ | 0x00000080 /* TOOLWINDOW */ | 0x00000008 /* TOPMOST */ | 0x08000000 /* NOACTIVATE */;
            return cp;
        }
    }

    protected override bool ShowWithoutActivation => true;

    Rectangle WorkArea => Screen.FromPoint(new Point((int)px + WinW / 2, (int)py + WinH / 2)).WorkingArea;
    float FloorY => WorkArea.Bottom - WinH + 8 * Dpi;

    void Play(string id) { anim = lib.Get(id); frame = 0; frameT = 0; }
    void Say(string text) { bubble = text; bubbleUntil = t + 4; }
    void Set(Mode m, double seconds) { mode = m; modeEnds = t + seconds; }

    // MARK: 每帧

    void Step()
    {
        double now = clock.Elapsed.TotalSeconds;
        double dt = Math.Min(now - last, 0.05);
        last = now;
        t += dt;

        if (anim.Frames.Length > 0)
        {
            frameT += dt;
            while (frameT >= anim.Delays[frame]) { frameT -= anim.Delays[frame]; frame = (frame + 1) % anim.Frames.Length; }
        }
        if (bubble != null && t > bubbleUntil) bubble = null;

        var wa = WorkArea;
        float bob = 0, tilt = 0, sx = 1, sy = 1, ft = (float)t, fdt = (float)dt;

        switch (mode)
        {
            case Mode.Walk:
                px += dir * 55 * Dpi * fdt;
                if (px < wa.Left - WinW * 0.15f) { px = wa.Left - WinW * 0.15f; dir = 1; }
                if (px > wa.Right - WinW * 0.85f) { px = wa.Right - WinW * 0.85f; dir = -1; }
                bob = MathF.Abs(MathF.Sin(ft * 9)) * 6 * Dpi;
                tilt = MathF.Sin(ft * 9) * 0.06f;
                if (t > modeEnds) Decide();
                break;
            case Mode.Idle:
            case Mode.React:
                { float b = MathF.Sin(ft * 2.6f); sy = 1 + 0.03f * b; sx = 1 - 0.015f * b; }
                if (mode == Mode.Idle) tilt = MathF.Sin(ft * 0.8f) * 0.03f;
                if (t > modeEnds) Decide();
                break;
            case Mode.Sleep:
                { float b = MathF.Sin(ft * 1.2f); sy = 1 + 0.045f * b; sx = 1 - 0.02f * b; }
                if ((int)t % 9 == 0 && bubble == null) Say("Zzz…");
                break;
            case Mode.Hop:
            case Mode.Fall:
                vy -= 2200 * Dpi * fdt;
                py -= vy * fdt;
                if (mode == Mode.Hop) px += dir * 70 * Dpi * fdt;
                sy = 1 + Math.Clamp(vy / (4000 * Dpi), -0.12f, 0.12f); sx = 2 - sy;
                if (py >= FloorY)
                {
                    py = FloorY;
                    if (mode == Mode.Fall && vy < -900 * Dpi) Say(Lines.Any(Lines.Land));
                    vy = 0; squash = 1;
                    Decide();
                }
                break;
            case Mode.Drag:
                tilt = MathF.Sin(ft * 6) * 0.18f;
                break;
        }

        if (mode != Mode.Drag && mode != Mode.Hop && mode != Mode.Fall && py < FloorY - 1) { Set(Mode.Fall, 10); vy = 0; }
        if (squash > 0)
        {
            squash = Math.Max(0, squash - fdt * 4);
            sy *= 1 - 0.18f * squash; sx *= 1 + 0.12f * squash;
        }

        Render(bob, tilt, sx, sy);

        if (cfg.Remind && (DateTime.Now - lastRemind).TotalSeconds > 3600 && mode != Mode.Drag)
        {
            lastRemind = DateTime.Now;
            Hop(); Play(lib.Pick("happy")); Say(Lines.Any(Lines.Remind));
        }
        else if (mode != Mode.Sleep && mode != Mode.Drag && (DateTime.Now - lastTouch).TotalSeconds > 600 && py >= FloorY - 1)
        {
            mode = Mode.Sleep; Play(lib.Pick("sleep")); Say(Lines.Any(Lines.Sleep));
        }
    }

    void Decide()
    {
        if (mode == Mode.Sleep) return;
        double r = Random.Shared.NextDouble();
        if (!cfg.Still && r < 0.5)
        {
            dir = Random.Shared.Next(2) == 0 ? 1 : -1;
            Set(Mode.Walk, 3 + Random.Shared.NextDouble() * 5);
            Play(lib.Pick("walk"));
            if (Random.Shared.Next(5) == 0) Say(Lines.Any(Lines.Walk));
        }
        else if (!cfg.Still && r < 0.6) Hop();
        else
        {
            Set(Mode.Idle, 3 + Random.Shared.NextDouble() * 4);
            Play(lib.Pick("idle"));
            if (Random.Shared.Next(5) == 0) Say(Lines.Any(Lines.Idle));
        }
    }

    void Hop() { mode = Mode.Hop; vy = 620 * Dpi; if (cfg.Still) dir = 0; }

    // MARK: 绘制

    void Render(float bob, float tilt, float sx, float sy)
    {
        int w = WinW, h = WinH, sz = Sz;
        using var bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        using (var g = Graphics.FromImage(bmp))
        {
            g.Clear(Color.Transparent);
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;

            if (bubble != null)
            {
                var r = new RectangleF(4 * Dpi, 4 * Dpi, w - 8 * Dpi, 52 * Dpi);
                using var path = Rounded(r, 13 * Dpi);
                using var fill = new SolidBrush(Color.FromArgb(248, 255, 246, 233));
                using var pen = new Pen(Color.FromArgb(240, 138, 75), 2 * Dpi);
                g.FillPath(fill, path);
                g.DrawPath(pen, path);
                using var font = new Font("Microsoft YaHei UI", 10f * Dpi, FontStyle.Bold, GraphicsUnit.Point);
                using var ink = new SolidBrush(Color.FromArgb(58, 46, 42));
                using var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
                g.DrawString(bubble, font, ink, RectangleF.Inflate(r, -8 * Dpi, -4 * Dpi), sf);
            }

            if (anim.Frames.Length > 0)
            {
                var img = anim.Frames[frame];
                // 以脚底中心为原点做颠、歪、翻转和伸缩
                g.TranslateTransform(w / 2f, h - 4 * Dpi - bob);
                g.RotateTransform(-tilt * 180f / MathF.PI);
                g.ScaleTransform(sx * (dir > 0 ? -1 : 1), sy);
                float k = Math.Min((float)sz / img.Width, (float)sz / img.Height);
                float iw = img.Width * k, ih = img.Height * k;
                g.DrawImage(img, -iw / 2, -ih, iw, ih);
            }
        }
        Push(bmp, (int)px, (int)py);
    }

    static GraphicsPath Rounded(RectangleF r, float rad)
    {
        var p = new GraphicsPath();
        float d = rad * 2;
        p.AddArc(r.Left, r.Top, d, d, 180, 90);
        p.AddArc(r.Right - d, r.Top, d, d, 270, 90);
        p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
        p.AddArc(r.Left, r.Bottom - d, d, d, 90, 90);
        p.CloseFigure();
        return p;
    }

    // 把带透明通道的图交给系统显示（分层窗口）
    void Push(Bitmap bmp, int x, int y)
    {
        IntPtr screenDc = GetDC(IntPtr.Zero), memDc = CreateCompatibleDC(screenDc);
        IntPtr hBmp = bmp.GetHbitmap(Color.FromArgb(0)), old = SelectObject(memDc, hBmp);
        try
        {
            var size = new SIZE { cx = bmp.Width, cy = bmp.Height };
            var src = new POINT();
            var dst = new POINT { x = x, y = y };
            var blend = new BLENDFUNCTION { BlendOp = 0, SourceConstantAlpha = 255, AlphaFormat = 1 };
            UpdateLayeredWindow(Handle, screenDc, ref dst, ref size, memDc, ref src, 0, ref blend, 2);
        }
        finally
        {
            SelectObject(memDc, old);
            DeleteObject(hBmp);
            DeleteDC(memDc);
            ReleaseDC(IntPtr.Zero, screenDc);
        }
    }

    // MARK: 鼠标

    protected override void OnMouseDown(MouseEventArgs e)
    {
        if (e.Button == MouseButtons.Left)
        {
            mouseDown = true; dragged = false; doubleClick = e.Clicks >= 2;
            dragStart = Cursor.Position; winStart = new Point((int)px, (int)py);
        }
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        if (!mouseDown) return;
        var p = Cursor.Position;
        int dx = p.X - dragStart.X, dy = p.Y - dragStart.Y;
        if (!dragged && Math.Abs(dx) + Math.Abs(dy) > 4)
        {
            dragged = true; mode = Mode.Drag;
            Play(lib.Pick("drag")); Say(Lines.Any(Lines.Drag));
            lastTouch = DateTime.Now;
        }
        if (dragged) { px = winStart.X + dx; py = winStart.Y + dy; }
    }

    protected override void OnMouseUp(MouseEventArgs e)
    {
        if (e.Button == MouseButtons.Right) { MakeMenu().Show(Cursor.Position); return; }
        if (e.Button != MouseButtons.Left || !mouseDown) return;
        mouseDown = false;
        lastTouch = DateTime.Now;
        if (dragged) { mode = Mode.Fall; vy = 0; Play(lib.Pick("walk")); return; }
        if (mode == Mode.Sleep) { Set(Mode.React, 2.5); Play(lib.Pick("happy")); Say(Lines.Any(Lines.Wake)); return; }
        if (doubleClick) Feed(); else Petted();
    }

    void Petted()
    {
        lastTouch = DateTime.Now;
        petCount = t - lastPet < 3 ? petCount + 1 : 1;
        lastPet = t;
        if (petCount >= 5)
        {
            petCount = 0;
            Set(Mode.React, 3); Play(lib.Pick("angry")); Say(Lines.Any(Lines.PetTooMuch));
        }
        else
        {
            Set(Mode.React, 2.8); Play(lib.Pick("pet")); Say(Lines.Any(Lines.Pet));
            squash = 0.6f;
        }
    }

    void Feed()
    {
        lastTouch = DateTime.Now;
        Set(Mode.React, 3.5); Play(lib.Pick("feed")); Say(Lines.Any(Lines.Feed));
    }

    void ToggleSleep()
    {
        lastTouch = DateTime.Now;
        if (mode == Mode.Sleep) { Set(Mode.React, 2.5); Play(lib.Pick("happy")); Say(Lines.Any(Lines.Wake)); return; }
        mode = Mode.Sleep; Play(lib.Pick("sleep")); Say(Lines.Any(Lines.Sleep));
    }

    ContextMenuStrip MakeMenu()
    {
        var m = new ContextMenuStrip();
        m.Items.Add("摸摸八条", null, (_, _) => Petted());
        m.Items.Add("喂小鱼干", null, (_, _) => Feed());
        m.Items.Add(mode == Mode.Sleep ? "叫醒八条" : "让八条睡觉", null, (_, _) => ToggleSleep());
        var still = new ToolStripMenuItem("待在原地，不要乱跑") { Checked = cfg.Still };
        still.Click += (_, _) =>
        {
            cfg.Still = !cfg.Still; cfg.Save();
            Say(cfg.Still ? "好，咪就待在这儿。" : "咪去溜达了。");
            if (mode == Mode.Walk) Set(Mode.Idle, 3);
        };
        m.Items.Add(still);
        m.Items.Add(new ToolStripSeparator());
        var size = new ToolStripMenuItem("大小");
        foreach (var (title, v) in new[] { ("小", 100), ("中", 140), ("大", 190) })
        {
            var i = new ToolStripMenuItem(title) { Checked = cfg.Size == v };
            i.Click += (_, _) => { cfg.Size = v; cfg.Save(); };
            size.DropDownItems.Add(i);
        }
        m.Items.Add(size);
        var remind = new ToolStripMenuItem("每小时提醒休息") { Checked = cfg.Remind };
        remind.Click += (_, _) =>
        {
            cfg.Remind = !cfg.Remind; cfg.Save(); lastRemind = DateTime.Now;
            Say(cfg.Remind ? "好，咪每小时提醒你起来动动。" : "行吧，咪不管你了。");
        };
        m.Items.Add(remind);
        m.Items.Add(new ToolStripSeparator());
        m.Items.Add("退出", null, (_, _) => Application.Exit());
        return m;
    }

    // MARK: Win32

    [StructLayout(LayoutKind.Sequential)] struct POINT { public int x, y; }
    [StructLayout(LayoutKind.Sequential)] struct SIZE { public int cx, cy; }
    [StructLayout(LayoutKind.Sequential, Pack = 1)]
    struct BLENDFUNCTION { public byte BlendOp, BlendFlags, SourceConstantAlpha, AlphaFormat; }

    [DllImport("user32.dll", SetLastError = true)]
    static extern bool UpdateLayeredWindow(IntPtr hwnd, IntPtr hdcDst, ref POINT pptDst, ref SIZE psize, IntPtr hdcSrc, ref POINT pprSrc, int crKey, ref BLENDFUNCTION pblend, int dwFlags);
    [DllImport("user32.dll")] static extern IntPtr GetDC(IntPtr hWnd);
    [DllImport("user32.dll")] static extern int ReleaseDC(IntPtr hWnd, IntPtr hDC);
    [DllImport("gdi32.dll")] static extern IntPtr CreateCompatibleDC(IntPtr hDC);
    [DllImport("gdi32.dll")] static extern bool DeleteDC(IntPtr hdc);
    [DllImport("gdi32.dll")] static extern IntPtr SelectObject(IntPtr hDC, IntPtr hObject);
    [DllImport("gdi32.dll")] static extern bool DeleteObject(IntPtr hObject);
}
