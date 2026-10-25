package com.daimaobatiao.pet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Random;

// 八条常驻在屏幕上：悬浮窗 + 前台服务（通知栏那条「八条在陪你」）
public class PetService extends Service implements Choreographer.FrameCallback {
    static final String ACTION_PET = "pet", ACTION_FEED = "feed", ACTION_SLEEP = "sleep", ACTION_STOP = "stop",
            ACTION_SIZE = "size", ACTION_STILL = "still", ACTION_REMIND = "remind";
    static volatile boolean running;

    enum Mode { IDLE, WALK, HOP, REACT, SLEEP, DRAG, FALL }

    float d;
    private WindowManager wm;
    private PetView pet;
    private TextView bubble;
    private WindowManager.LayoutParams lp, blp;
    private Library lib;
    private SharedPreferences prefs;
    private final Random rnd = new Random();

    private Mode mode = Mode.FALL;
    private double t, modeEnds, frameT, lastPet, bubbleUntil;
    private long lastNanos;
    private Library.Anim anim;
    private int frame, petCount;
    private float px, py, vy, dir = -1, squash;   // px/py：窗口左上角，屏幕像素；vy 向上为正
    private long lastTouch = System.currentTimeMillis(), lastRemind = System.currentTimeMillis();
    private boolean bubbleShown;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        d = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("pet", MODE_PRIVATE);
        startInForeground();
        lib = new Library(getAssets());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        pet = new PetView(this);
        lp = new WindowManager.LayoutParams(winW(), winH(), type, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        Point s = screen();
        px = s.x - winW() - 16 * d;
        py = s.y * 0.4f;
        lp.x = (int) px; lp.y = (int) py;

        // 气泡不接收点击；安卓 12 起，不接收点击的悬浮窗要半透明，下面的应用才点得到
        bubble = new TextView(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(255, 246, 233));
        bg.setStroke((int) (2 * d), Color.rgb(240, 138, 75));
        bg.setCornerRadius(13 * d);
        bubble.setBackground(bg);
        bubble.setTextColor(Color.rgb(58, 46, 42));
        bubble.setTypeface(Typeface.DEFAULT_BOLD);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        bubble.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
        bubble.setMaxWidth((int) (220 * d));
        bubble.setVisibility(View.GONE);
        blp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                type, flags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
        blp.gravity = Gravity.TOP | Gravity.START;
        blp.alpha = 0.8f;

        try {
            wm.addView(pet, lp);
            wm.addView(bubble, blp);
        } catch (RuntimeException e) {
            // 没有悬浮窗权限
            stopSelf();
            return;
        }
        running = true;
        play(lib.pick("drag"));
        say(Lines.any(Lines.GREET));
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running || intent == null || intent.getAction() == null) return START_STICKY;
        switch (intent.getAction()) {
            case ACTION_PET: tap(); break;
            case ACTION_FEED: feed(); break;
            case ACTION_SLEEP: toggleSleep(); break;
            case ACTION_STOP: stopSelf(); break;
            case ACTION_SIZE:
                lp.width = winW(); lp.height = winH();
                wm.updateViewLayout(pet, lp);
                break;
            case ACTION_STILL:
                say(still() ? "好，咪就待在这儿。" : "咪去溜达了。");
                if (mode == Mode.WALK) set(Mode.IDLE, 3);
                break;
            case ACTION_REMIND:
                lastRemind = System.currentTimeMillis();
                say(prefs.getBoolean("remind", true) ? "好，咪每小时提醒你起来动动。" : "行吧，咪不管你了。");
                break;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        try { wm.removeView(pet); } catch (Exception ignored) { }
        try { wm.removeView(bubble); } catch (Exception ignored) { }
        super.onDestroy();
    }

    private void startInForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("pet", "八条桌宠", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "pet")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("八条在陪你")
                .setContentText("长按八条打开设置")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "睡觉 / 叫醒", serviceIntent(ACTION_SLEEP, 1)).build())
                .addAction(new Notification.Action.Builder(null, "退出", serviceIntent(ACTION_STOP, 2)).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
    }

    private PendingIntent serviceIntent(String action, int code) {
        return PendingIntent.getService(this, code, new Intent(this, PetService.class).setAction(action), PendingIntent.FLAG_IMMUTABLE);
    }

    // MARK: 尺寸和位置

    float sz() { return prefs.getInt("size", 110) * d; }
    int winW() { return (int) (sz() * 1.25f); }
    int winH() { return (int) (sz() * 1.12f); }
    int winX() { return lp.x; }
    int winY() { return lp.y; }
    private boolean still() { return prefs.getBoolean("still", false); }

    private Point screen() {
        Point p = new Point();
        wm.getDefaultDisplay().getRealSize(p);
        return p;
    }

    private float floorY() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int nav = id > 0 ? getResources().getDimensionPixelSize(id) : 0;
        return screen().y - nav - winH() + 4 * d;
    }

    private void play(String id) { anim = lib.get(id); frame = 0; frameT = 0; }
    private void set(Mode m, double seconds) { mode = m; modeEnds = t + seconds; }

    private void say(String text) {
        bubble.setText(text);
        bubble.setVisibility(View.VISIBLE);
        bubbleShown = true;
        bubbleUntil = t + 4;
    }

    // MARK: 每帧

    @Override
    public void doFrame(long nanos) {
        if (!running) return;
        double dt = lastNanos == 0 ? 0.016 : Math.min((nanos - lastNanos) / 1e9, 0.05);
        lastNanos = nanos;
        t += dt;

        frameT += dt;
        while (frameT >= anim.delays[frame]) { frameT -= anim.delays[frame]; frame = (frame + 1) % anim.frames.length; }
        if (bubbleShown && t > bubbleUntil) { bubbleShown = false; bubble.setVisibility(View.GONE); }

        Point s = screen();
        float fl = floorY(), W = winW();
        float bob = 0, tilt = 0, sx = 1, sy = 1, ft = (float) t, fdt = (float) dt;

        switch (mode) {
            case WALK:
                px += dir * 45 * d * fdt;
                if (px < -W * 0.15f) { px = -W * 0.15f; dir = 1; }
                if (px > s.x - W * 0.85f) { px = s.x - W * 0.85f; dir = -1; }
                bob = (float) Math.abs(Math.sin(ft * 9)) * 5 * d;
                tilt = (float) Math.sin(ft * 9) * 0.06f;
                if (t > modeEnds) decide();
                break;
            case IDLE:
            case REACT: {
                float b = (float) Math.sin(ft * 2.6f);
                sy = 1 + 0.03f * b; sx = 1 - 0.015f * b;
                if (mode == Mode.IDLE) tilt = (float) Math.sin(ft * 0.8f) * 0.03f;
                if (t > modeEnds) decide();
                break;
            }
            case SLEEP: {
                float b = (float) Math.sin(ft * 1.2f);
                sy = 1 + 0.045f * b; sx = 1 - 0.02f * b;
                if (((int) t) % 9 == 0 && !bubbleShown) say("Zzz…");
                break;
            }
            case HOP:
            case FALL:
                vy -= 2200 * d * fdt;
                py -= vy * fdt;
                if (mode == Mode.HOP) px += dir * 70 * d * fdt;
                sy = 1 + Math.max(-0.12f, Math.min(0.12f, vy / (4000 * d))); sx = 2 - sy;
                if (py >= fl) {
                    py = fl;
                    if (mode == Mode.FALL && vy < -900 * d) say(Lines.any(Lines.LAND));
                    vy = 0; squash = 1;
                    decide();
                }
                break;
            case DRAG:
                tilt = (float) Math.sin(ft * 6) * 0.18f;
                break;
        }

        if (mode != Mode.DRAG && mode != Mode.HOP && mode != Mode.FALL && py < fl - 1) { mode = Mode.FALL; vy = 0; }
        if (mode != Mode.DRAG && py > fl) py = fl;   // 转屏后地面变高
        if (squash > 0) {
            squash = Math.max(0, squash - fdt * 4);
            sy *= 1 - 0.18f * squash; sx *= 1 + 0.12f * squash;
        }

        pet.set(anim.frames[frame], bob, tilt, sx, sy, dir > 0);
        int nx = Math.round(px), ny = Math.round(py);
        if (nx != lp.x || ny != lp.y) { lp.x = nx; lp.y = ny; wm.updateViewLayout(pet, lp); }
        if (bubbleShown) placeBubble(s);

        long now = System.currentTimeMillis();
        if (prefs.getBoolean("remind", true) && now - lastRemind > 3600_000 && mode != Mode.DRAG) {
            lastRemind = now;
            hop(); play(lib.pick("happy")); say(Lines.any(Lines.REMIND));
        } else if (mode != Mode.SLEEP && mode != Mode.DRAG && now - lastTouch > 600_000 && py >= fl - 1) {
            mode = Mode.SLEEP; play(lib.pick("sleep")); say(Lines.any(Lines.SLEEP));
        }

        Choreographer.getInstance().postFrameCallback(this);
    }

    // 气泡放在八条头顶，不超出屏幕
    private void placeBubble(Point s) {
        bubble.measure(View.MeasureSpec.makeMeasureSpec((int) (220 * d), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int bw = bubble.getMeasuredWidth(), bh = bubble.getMeasuredHeight();
        int bx = Math.round(px + winW() / 2f - bw / 2f);
        bx = Math.max((int) (4 * d), Math.min(s.x - bw - (int) (4 * d), bx));
        int by = Math.round(py + winH() - sz() - bh);
        by = Math.max((int) (24 * d), by);
        if (bx != blp.x || by != blp.y) { blp.x = bx; blp.y = by; wm.updateViewLayout(bubble, blp); }
    }

    private void decide() {
        if (mode == Mode.SLEEP) return;
        double r = rnd.nextDouble();
        if (!still() && r < 0.5) {
            dir = rnd.nextBoolean() ? 1 : -1;
            set(Mode.WALK, 3 + rnd.nextDouble() * 5);
            play(lib.pick("walk"));
            if (rnd.nextInt(5) == 0) say(Lines.any(Lines.WALK));
        } else if (!still() && r < 0.6) {
            hop();
        } else {
            set(Mode.IDLE, 3 + rnd.nextDouble() * 4);
            play(lib.pick("idle"));
            if (rnd.nextInt(5) == 0) say(Lines.any(Lines.IDLE));
        }
    }

    private void hop() { mode = Mode.HOP; vy = 620 * d; if (still()) dir = 0; }

    // MARK: 互动

    void tap() {
        lastTouch = System.currentTimeMillis();
        if (mode == Mode.SLEEP) { wake(); return; }
        petCount = t - lastPet < 3 ? petCount + 1 : 1;
        lastPet = t;
        if (petCount >= 5) {
            petCount = 0;
            set(Mode.REACT, 3); play(lib.pick("angry")); say(Lines.any(Lines.PET_TOO_MUCH));
        } else {
            set(Mode.REACT, 2.8); play(lib.pick("pet")); say(Lines.any(Lines.PET));
            squash = 0.6f;
        }
    }

    void feed() {
        lastTouch = System.currentTimeMillis();
        set(Mode.REACT, 3.5); play(lib.pick("feed")); say(Lines.any(Lines.FEED));
    }

    private void wake() { set(Mode.REACT, 2.5); play(lib.pick("happy")); say(Lines.any(Lines.WAKE)); }

    private void toggleSleep() {
        lastTouch = System.currentTimeMillis();
        if (mode == Mode.SLEEP) { wake(); return; }
        mode = Mode.SLEEP; play(lib.pick("sleep")); say(Lines.any(Lines.SLEEP));
    }

    void beginDrag() {
        lastTouch = System.currentTimeMillis();
        mode = Mode.DRAG; play(lib.pick("drag")); say(Lines.any(Lines.DRAG));
    }

    void dragTo(float x, float y) { px = x; py = y; }

    void endDrag() {
        lastTouch = System.currentTimeMillis();
        mode = Mode.FALL; vy = 0; play(lib.pick("walk"));
    }

    void openSettings() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
