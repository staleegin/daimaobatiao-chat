import AppKit
import ImageIO

// MARK: - 动画素材（去掉白底的表情帧）

struct Anim {
    let frames: [CGImage]
    let delays: [Double]
}

/// 从图片边缘开始把连通的白色背景抠成透明，猫身上被黑色描边围住的白色不会被抠掉
func cutout(_ src: CGImage) -> CGImage {
    let w = src.width, h = src.height
    var px = [UInt8](repeating: 0, count: w * h * 4)
    let cs = CGColorSpaceCreateDeviceRGB()
    guard let ctx = CGContext(data: &px, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: cs,
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return src }
    ctx.draw(src, in: CGRect(x: 0, y: 0, width: w, height: h))

    func isBg(_ i: Int) -> Bool {
        let a = px[i * 4 + 3]
        if a < 20 { return true }
        let r = Int(px[i * 4]), g = Int(px[i * 4 + 1]), b = Int(px[i * 4 + 2])
        return min(r, g, b) > 228 && max(r, g, b) - min(r, g, b) < 28
    }
    // 描边有小缺口时背景会漏进猫身体里：先把描边加粗几像素堵住缺口，再从边缘填充背景
    let r = max(2, min(w, h) / 80)
    var ink = [Bool](repeating: false, count: w * h)
    for i in 0..<(w * h) where !isBg(i) {
        let x = i % w, y = i / w
        for yy in max(0, y - r)...min(h - 1, y + r) {
            for xx in max(0, x - r)...min(w - 1, x + r) { ink[yy * w + xx] = true }
        }
    }
    var bg = [Bool](repeating: false, count: w * h)
    var stack: [Int] = []
    // 只从上、左、右三边开始填：很多表情的身体被画面底边切掉，从底边进去会把白肚子当成背景
    // （位图内存里第 0 行是图片的上边）
    for x in 0..<w { stack.append(x) }
    for y in 0..<h { stack.append(y * w); stack.append(y * w + w - 1) }
    while let i = stack.popLast() {
        if bg[i] || ink[i] || !isBg(i) { continue }
        bg[i] = true
        let x = i % w, y = i / w
        if x > 0 { stack.append(i - 1) }
        if x < w - 1 { stack.append(i + 1) }
        if y > 0 { stack.append(i - w) }
        if y < h - 1 { stack.append(i + w) }
    }
    // 再把背景往描边方向推回去几像素，去掉加粗留下的白边
    for _ in 0..<(r + 1) {
        var grow: [Int] = []
        for i in 0..<(w * h) where !bg[i] && isBg(i) {
            let x = i % w, y = i / w
            if (x > 0 && bg[i - 1]) || (x < w - 1 && bg[i + 1]) || (y > 0 && bg[i - w]) || (y < h - 1 && bg[i + w]) {
                grow.append(i)
            }
        }
        for i in grow { bg[i] = true }
    }
    for i in 0..<(w * h) {
        if bg[i] {
            px[i * 4] = 0; px[i * 4 + 1] = 0; px[i * 4 + 2] = 0; px[i * 4 + 3] = 0
        } else {
            // 紧挨背景的浅色像素做半透明，让边缘柔和一点
            let x = i % w, y = i / w
            let nearBg = (x > 0 && bg[i - 1]) || (x < w - 1 && bg[i + 1]) || (y > 0 && bg[i - w]) || (y < h - 1 && bg[i + w])
            if nearBg && min(px[i * 4], px[i * 4 + 1], px[i * 4 + 2]) > 200 {
                for k in 0..<4 { px[i * 4 + k] = px[i * 4 + k] / 2 }
            }
        }
    }
    return ctx.makeImage() ?? src
}

final class Library {
    private let dir: URL
    private var cache: [String: Anim] = [:]

    init(resources: URL) { dir = resources.appendingPathComponent("stickers") }

    func anim(_ id: String) -> Anim {
        if let a = cache[id] { return a }
        let ext = id.hasPrefix("D") ? "gif" : (FileManager.default.fileExists(atPath: dir.appendingPathComponent("\(id).png").path) ? "png" : "jpg")
        var frames: [CGImage] = [], delays: [Double] = []
        if let src = CGImageSourceCreateWithURL(dir.appendingPathComponent("\(id).\(ext)") as CFURL, nil) {
            for i in 0..<CGImageSourceGetCount(src) {
                guard let img = CGImageSourceCreateImageAtIndex(src, i, nil) else { continue }
                frames.append(cutout(img))
                var d = 0.1
                if let p = CGImageSourceCopyPropertiesAtIndex(src, i, nil) as? [CFString: Any],
                   let g = p[kCGImagePropertyGIFDictionary] as? [CFString: Any] {
                    d = (g[kCGImagePropertyGIFUnclampedDelayTime] as? Double) ?? (g[kCGImagePropertyGIFDelayTime] as? Double) ?? 0.1
                }
                delays.append(max(d, 0.04))
            }
        }
        let a = Anim(frames: frames, delays: delays)
        cache[id] = a
        return a
    }
}

// MARK: - 表情分组和台词

enum Set_ {
    static let walk = ["D009", "D025"]
    static let idle = ["D008", "D024", "D021", "D006", "D017", "D018", "D002"]
    static let pet = ["D004", "D021", "D003"]
    static let feed = ["D001"]
    static let angry = ["D019", "D014"]
    static let sleep = ["S067"]
    static let drag = ["D012"]
    static let happy = ["D006", "D001", "D021"]
}

enum Lines {
    static let greet = ["咪～八条上岗了。", "来啦？就知道你会想咪。"]
    static let pet = ["再摸一下，就一下。", "嗯…允许你多摸三秒。", "咪才没有觉得很舒服。", "左边也要摸。"]
    static let petTooMuch = ["摸够了没！", "再摸就收费了，一下一根小鱼干。", "咪的毛要被你摸秃了！"]
    static let feed = ["小鱼干！你是好人。", "吃完再说，别打扰咪。", "就这点？不够塞牙缝的。"]
    static let drag = ["放咪下来！", "咪恐高！", "要去哪儿？"]
    static let land = ["稳稳落地。", "哼，下次轻点。"]
    static let walk = ["巡逻中。", "咪去那边看看。", "散步时间。"]
    static let idle = ["盯——", "咪在摸鱼，你也摸会儿？", "你在忙啥？带咪一个。", "咪饿了，只是说说。"]
    static let sleep = ["困了，别吵。", "Zzz…", "咪先睡一会儿。"]
    static let wake = ["谁叫咪？", "咪没睡，咪在思考。"]
    static let remind = ["坐太久啦，起来伸个懒腰。", "喝口水，咪看着你喝。", "眼睛累了吧？看看远处。"]
}

// MARK: - 视图

final class PetView: NSView {
    weak var pet: Pet?
    override var isFlipped: Bool { false }
    override func acceptsFirstMouse(for event: NSEvent?) -> Bool { true }
    override func mouseDown(with event: NSEvent) { pet?.mouseDown(event) }
    override func mouseDragged(with event: NSEvent) { pet?.mouseDragged(event) }
    override func mouseUp(with event: NSEvent) { pet?.mouseUp(event) }
    override func menu(for event: NSEvent) -> NSMenu? { pet?.makeMenu() }
}

// MARK: - 桌宠

final class Pet: NSObject {
    enum Mode { case idle, walk, hop, react, sleep, drag, fall }

    private let lib: Library
    private let window: NSPanel
    private let view = PetView()
    private let sprite = CALayer()
    private let bubbleBox = NSView()
    private let bubble = NSTextField(labelWithString: "")

    private var size: CGFloat = UserDefaults.standard.object(forKey: "size") as? CGFloat ?? 140
    private var remindOn = UserDefaults.standard.object(forKey: "remind") as? Bool ?? true
    private var stayStill = UserDefaults.standard.bool(forKey: "still")

    private var mode: Mode = .fall
    private var modeEnds = 0.0
    private var anim = Anim(frames: [], delays: [])
    private var frame = 0
    private var frameT = 0.0
    private var t = 0.0
    private var last = CACurrentMediaTime()
    private var dir: CGFloat = -1
    private var vy: CGFloat = 0
    private var squash: CGFloat = 0
    private var petCount = 0
    private var lastPet = 0.0
    private var lastTouch = Date()
    private var lastRemind = Date()
    private var bubbleUntil = 0.0
    private var dragStart = NSPoint.zero, winStart = NSPoint.zero, dragged = false

    private var winW: CGFloat { size * 1.5 }
    private var winH: CGFloat { size + 70 }

    init(lib: Library) {
        self.lib = lib
        window = NSPanel(contentRect: .zero, styleMask: [.borderless, .nonactivatingPanel], backing: .buffered, defer: false)
        super.init()
        window.isOpaque = false
        window.backgroundColor = .clear
        window.hasShadow = false
        window.level = .floating
        window.collectionBehavior = [.canJoinAllSpaces, .stationary, .fullScreenAuxiliary]
        window.hidesOnDeactivate = false
        window.contentView = view
        view.pet = self
        view.wantsLayer = true

        sprite.contentsGravity = .resizeAspect
        sprite.anchorPoint = CGPoint(x: 0.5, y: 0)
        sprite.shadowColor = NSColor.black.cgColor
        sprite.shadowOpacity = 0.18
        sprite.shadowRadius = 6
        sprite.shadowOffset = CGSize(width: 0, height: -2)
        view.layer?.addSublayer(sprite)

        bubbleBox.wantsLayer = true
        bubbleBox.layer?.backgroundColor = NSColor(red: 1, green: 0.965, blue: 0.914, alpha: 0.97).cgColor
        bubbleBox.layer?.cornerRadius = 13
        bubbleBox.layer?.borderWidth = 2
        bubbleBox.layer?.borderColor = NSColor(red: 0.94, green: 0.54, blue: 0.29, alpha: 1).cgColor
        bubble.font = NSFont(name: "PingFangSC-Semibold", size: 13) ?? .boldSystemFont(ofSize: 13)
        bubble.textColor = NSColor(red: 0.23, green: 0.18, blue: 0.16, alpha: 1)
        bubble.alignment = .center
        bubble.maximumNumberOfLines = 2
        bubble.lineBreakMode = .byWordWrapping
        bubbleBox.addSubview(bubble)
        bubbleBox.isHidden = true
        view.addSubview(bubbleBox)

        layoutViews()
        let v = screen.visibleFrame
        window.setFrameOrigin(NSPoint(x: v.maxX - winW - 60, y: v.minY + 260))
        window.orderFrontRegardless()

        play(Set_.drag.randomElement()!)
        say(Lines.greet.randomElement()!)
        let timer = Timer(timeInterval: 1.0 / 60, repeats: true) { [weak self] _ in self?.step() }
        RunLoop.main.add(timer, forMode: .common)
    }

    private var screen: NSScreen { window.screen ?? NSScreen.main ?? NSScreen.screens[0] }
    private var floorY: CGFloat { screen.visibleFrame.minY - 8 }

    private func layoutViews() {
        var f = window.frame
        f.size = NSSize(width: winW, height: winH)
        window.setFrame(f, display: true)
        view.frame = NSRect(x: 0, y: 0, width: winW, height: winH)
        sprite.bounds = CGRect(x: 0, y: 0, width: size, height: size)
        sprite.position = CGPoint(x: winW / 2, y: 4)
        bubbleBox.frame = NSRect(x: 4, y: size + 14, width: winW - 8, height: 52)
        bubble.frame = bubbleBox.bounds.insetBy(dx: 8, dy: 6)
    }

    // MARK: 播放和说话

    private func play(_ id: String) {
        anim = lib.anim(id)
        frame = 0
        frameT = 0
    }

    private func say(_ text: String) {
        bubble.stringValue = text
        bubbleBox.isHidden = false
        bubbleUntil = t + 4
    }

    private func set(_ m: Mode, for seconds: Double) {
        mode = m
        modeEnds = t + seconds
    }

    // MARK: 每帧

    private func step() {
        let now = CACurrentMediaTime()
        let dt = min(now - last, 0.05)
        last = now
        t += dt

        // 动图换帧
        if !anim.frames.isEmpty {
            frameT += dt
            while frameT >= anim.delays[frame] {
                frameT -= anim.delays[frame]
                frame = (frame + 1) % anim.frames.count
            }
        }
        if !bubbleBox.isHidden && t > bubbleUntil { bubbleBox.isHidden = true }

        var o = window.frame.origin
        let v = screen.visibleFrame
        var bob: CGFloat = 0, tilt: CGFloat = 0
        var sx: CGFloat = 1, sy: CGFloat = 1

        switch mode {
        case .walk:
            o.x += dir * 55 * CGFloat(dt)
            if o.x < v.minX - winW * 0.15 { o.x = v.minX - winW * 0.15; dir = 1 }
            if o.x > v.maxX - winW * 0.85 { o.x = v.maxX - winW * 0.85; dir = -1 }
            bob = abs(sin(CGFloat(t) * 9)) * 6
            tilt = sin(CGFloat(t) * 9) * 0.06
            if t > modeEnds { decide() }
        case .idle, .react:
            let b = sin(CGFloat(t) * 2.6)
            sy = 1 + 0.03 * b; sx = 1 - 0.015 * b
            if mode == .idle { tilt = sin(CGFloat(t) * 0.8) * 0.03 }
            if t > modeEnds { decide() }
        case .sleep:
            let b = sin(CGFloat(t) * 1.2)
            sy = 1 + 0.045 * b; sx = 1 - 0.02 * b
            if Int(t) % 9 == 0 && bubbleBox.isHidden { say("Zzz…") }
        case .hop, .fall:
            vy -= 2200 * CGFloat(dt)
            o.y += vy * CGFloat(dt)
            if mode == .hop { o.x += dir * 70 * CGFloat(dt) }
            sy = 1 + min(max(vy / 4000, -0.12), 0.12); sx = 2 - sy
            if o.y <= floorY {
                o.y = floorY
                if mode == .fall && vy < -900 { say(Lines.land.randomElement()!) }
                vy = 0
                squash = 1
                decide()
            }
        case .drag:
            tilt = sin(CGFloat(t) * 6) * 0.18
        }

        if mode != .drag && mode != .hop && mode != .fall && o.y > floorY + 1 { set(.fall, for: 10); vy = 0 }

        // 落地时压扁一下再弹回
        if squash > 0 {
            squash = max(0, squash - CGFloat(dt) * 4)
            sy *= 1 - 0.18 * squash; sx *= 1 + 0.12 * squash
        }

        window.setFrameOrigin(o)

        CATransaction.begin()
        CATransaction.setDisableActions(true)
        if !anim.frames.isEmpty { sprite.contents = anim.frames[frame] }
        var tf = CATransform3DMakeTranslation(0, bob, 0)
        tf = CATransform3DRotate(tf, tilt, 0, 0, 1)
        tf = CATransform3DScale(tf, sx * (dir > 0 ? -1 : 1), sy, 1)
        sprite.transform = tf
        CATransaction.commit()

        // 提醒和犯困
        if remindOn && Date().timeIntervalSince(lastRemind) > 3600 && mode != .drag {
            lastRemind = Date()
            hop(); play(Set_.happy.randomElement()!); say(Lines.remind.randomElement()!)
        } else if mode != .sleep && mode != .drag && Date().timeIntervalSince(lastTouch) > 600 && o.y <= floorY + 1 {
            mode = .sleep
            play(Set_.sleep.randomElement()!)
            say(Lines.sleep.randomElement()!)
        }
    }

    /// 自己决定下一步做什么
    private func decide() {
        if mode == .sleep { return }
        let r = Double.random(in: 0..<1)
        if !stayStill && r < 0.5 {
            dir = Bool.random() ? 1 : -1
            set(.walk, for: Double.random(in: 3...8))
            play(Set_.walk.randomElement()!)
            if Int.random(in: 0..<5) == 0 { say(Lines.walk.randomElement()!) }
        } else if !stayStill && r < 0.6 {
            hop()
        } else {
            set(.idle, for: Double.random(in: 3...7))
            play(Set_.idle.randomElement()!)
            if Int.random(in: 0..<5) == 0 { say(Lines.idle.randomElement()!) }
        }
    }

    private func hop() {
        mode = .hop
        vy = 620
        if stayStill { dir = 0 }
    }

    // MARK: 鼠标

    func mouseDown(_ e: NSEvent) {
        dragStart = NSEvent.mouseLocation
        winStart = window.frame.origin
        dragged = false
    }

    func mouseDragged(_ e: NSEvent) {
        let p = NSEvent.mouseLocation
        let dx = p.x - dragStart.x, dy = p.y - dragStart.y
        if !dragged && abs(dx) + abs(dy) > 4 {
            dragged = true
            mode = .drag
            play(Set_.drag.randomElement()!)
            say(Lines.drag.randomElement()!)
            lastTouch = Date()
        }
        if dragged { window.setFrameOrigin(NSPoint(x: winStart.x + dx, y: winStart.y + dy)) }
    }

    func mouseUp(_ e: NSEvent) {
        lastTouch = Date()
        if dragged {
            mode = .fall; vy = 0
            play(Set_.walk.randomElement()!)
            return
        }
        if mode == .sleep {
            set(.react, for: 2.5); play(Set_.happy.randomElement()!); say(Lines.wake.randomElement()!)
            return
        }
        if e.clickCount >= 2 { feed() } else { petted() }
    }

    @objc func petted() {
        lastTouch = Date()
        petCount = t - lastPet < 3 ? petCount + 1 : 1
        lastPet = t
        if petCount >= 5 {
            petCount = 0
            set(.react, for: 3); play(Set_.angry.randomElement()!); say(Lines.petTooMuch.randomElement()!)
        } else {
            set(.react, for: 2.8); play(Set_.pet.randomElement()!); say(Lines.pet.randomElement()!)
            squash = 0.6
        }
    }

    @objc func feed() {
        lastTouch = Date()
        set(.react, for: 3.5); play(Set_.feed.randomElement()!); say(Lines.feed.randomElement()!)
    }

    @objc func toggleSleep() {
        lastTouch = Date()
        if mode == .sleep { set(.react, for: 2.5); play(Set_.happy.randomElement()!); say(Lines.wake.randomElement()!); return }
        mode = .sleep; play(Set_.sleep.randomElement()!); say(Lines.sleep.randomElement()!)
    }

    @objc func toggleStill() {
        stayStill.toggle()
        UserDefaults.standard.set(stayStill, forKey: "still")
        say(stayStill ? "好，咪就待在这儿。" : "咪去溜达了。")
        if mode == .walk { set(.idle, for: 3) }
    }

    @objc func setSize(_ sender: NSMenuItem) {
        size = CGFloat(sender.tag)
        UserDefaults.standard.set(size, forKey: "size")
        layoutViews()
    }

    @objc func toggleRemind() {
        remindOn.toggle()
        UserDefaults.standard.set(remindOn, forKey: "remind")
        lastRemind = Date()
        say(remindOn ? "好，咪每小时提醒你起来动动。" : "行吧，咪不管你了。")
    }

    @objc func quit() { NSApp.terminate(nil) }

    func makeMenu() -> NSMenu {
        let m = NSMenu()
        func item(_ title: String, _ sel: Selector) -> NSMenuItem {
            let i = NSMenuItem(title: title, action: sel, keyEquivalent: ""); i.target = self; return i
        }
        m.addItem(item("摸摸八条", #selector(petted)))
        m.addItem(item("喂小鱼干", #selector(feed)))
        m.addItem(item(mode == .sleep ? "叫醒八条" : "让八条睡觉", #selector(toggleSleep)))
        let s = item("待在原地，不要乱跑", #selector(toggleStill)); s.state = stayStill ? .on : .off
        m.addItem(s)
        m.addItem(.separator())
        let sizes = NSMenu()
        for (title, v) in [("小", 100), ("中", 140), ("大", 190)] {
            let i = item(title, #selector(setSize(_:))); i.tag = v; i.state = Int(size) == v ? .on : .off
            sizes.addItem(i)
        }
        let sizeItem = NSMenuItem(title: "大小", action: nil, keyEquivalent: ""); sizeItem.submenu = sizes
        m.addItem(sizeItem)
        let r = item("每小时提醒休息", #selector(toggleRemind)); r.state = remindOn ? .on : .off
        m.addItem(r)
        m.addItem(.separator())
        m.addItem(item("退出", #selector(quit)))
        return m
    }
}

// MARK: - 启动

let resources = Bundle.main.resourceURL ?? URL(fileURLWithPath: ".")
let library = Library(resources: resources)

// 调试：导出抠图效果，检查用
if let i = CommandLine.arguments.firstIndex(of: "--preview"), i + 2 < CommandLine.arguments.count {
    let res = URL(fileURLWithPath: CommandLine.arguments[i + 1]), out = CommandLine.arguments[i + 2]
    let lib = Library(resources: res)
    let ids = CommandLine.arguments.count > i + 3 ? Array(CommandLine.arguments[(i + 3)...])
        : Array(Set(Set_.walk + Set_.idle + Set_.pet + Set_.feed + Set_.angry + Set_.sleep + Set_.drag)).sorted()
    for id in ids {
        let frames = lib.anim(id).frames
        // 每个动图取开头、中间、结尾三帧
        for (k, f) in Set([0, frames.count / 2, max(frames.count - 1, 0)]).sorted().enumerated() where f < frames.count {
            let rep = NSBitmapImageRep(cgImage: frames[f])
            try? rep.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: "\(out)/\(id)_\(k).png"))
        }
    }
    exit(0)
}

let app = NSApplication.shared
app.setActivationPolicy(.accessory)
let pet = Pet(lib: library)
app.run()
