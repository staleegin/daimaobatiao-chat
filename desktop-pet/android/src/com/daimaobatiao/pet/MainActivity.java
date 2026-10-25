package com.daimaobatiao.pet;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

// 打开 App 看到的设置页；长按八条也会打开这里
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(255, 246, 233), ACCENT = Color.rgb(240, 138, 75),
            INK = Color.rgb(58, 46, 42), SOFT = Color.rgb(140, 120, 110);

    private SharedPreferences prefs;
    private LinearLayout box;
    private float d;
    private boolean wantStart;
    private CheckBox stillBox, remindBox;
    private RadioGroup sizeGroup;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("pet", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * d);
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box);
        setContentView(scroll);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        head.addView(icon, new LinearLayout.LayoutParams((int) (56 * d), (int) (56 * d)));
        TextView title = text("八条桌宠", 24, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding((int) (12 * d), 0, 0, 0);
        head.addView(title);
        box.addView(head);

        box.addView(text("八条会浮在屏幕上，在底部走来走去。刷别的 App 时它也在。", 14, SOFT));

        LinearLayout row = row();
        row.addView(button("让八条出来", true, v -> startPet()), weight());
        row.addView(button("让八条回去", false, v -> {
            stopService(new Intent(this, PetService.class));
            toast("八条回去休息了。");
        }), weight());
        box.addView(row);

        section("怎么玩");
        box.addView(text("单击：摸摸（连着摸太多次它会生气）\n双击：喂小鱼干\n按住拖动：把它拎起来，松手会掉下去\n长按：打开这个设置页", 14, INK));
        LinearLayout acts = row();
        acts.addView(button("摸摸", false, v -> send(PetService.ACTION_PET)), weight());
        acts.addView(button("喂小鱼干", false, v -> send(PetService.ACTION_FEED)), weight());
        acts.addView(button("睡觉 / 叫醒", false, v -> send(PetService.ACTION_SLEEP)), weight());
        box.addView(acts);

        section("设置");
        stillBox = check("待在原地，不要乱跑", "still", false, PetService.ACTION_STILL);
        remindBox = check("每小时提醒休息", "remind", true, PetService.ACTION_REMIND);
        TextView sizeLabel = text("大小", 14, INK);
        sizeLabel.setPadding(0, (int) (8 * d), 0, 0);
        box.addView(sizeLabel);
        sizeGroup = new RadioGroup(this);
        sizeGroup.setOrientation(RadioGroup.HORIZONTAL);
        String[] names = {"小", "中", "大"};
        int[] values = {85, 110, 150};
        for (int i = 0; i < 3; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(names[i]);
            r.setTextColor(INK);
            r.setId(values[i]);
            sizeGroup.addView(r);
        }
        sizeGroup.setOnCheckedChangeListener((g, id) -> {
            if (prefs.getInt("size", 110) == id) return;
            prefs.edit().putInt("size", id).apply();
            send(PetService.ACTION_SIZE);
        });
        box.addView(sizeGroup);

        section("八条过一会儿就不见了？");
        box.addView(text("手机为了省电会关掉后台的 App。按下面两个按钮，把八条桌宠设成不限制：", 14, INK));
        LinearLayout keep = row();
        keep.addView(button("电池不限制", false, v -> askBattery()), weight());
        keep.addView(button("应用设置", false, v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())))), weight());
        box.addView(keep);
        box.addView(text("不同品牌的位置大概是：\n"
                + "· 小米 / 红米：应用设置里打开「自启动」，省电策略选「无限制」\n"
                + "· 华为 / 荣耀：设置 → 电池 → 应用启动管理 → 八条桌宠改成手动管理，三个开关都打开\n"
                + "· OPPO / 一加 / realme：应用设置 → 耗电管理 → 允许后台运行\n"
                + "· vivo / iQOO：设置 → 电池 → 后台耗电管理 → 允许后台高耗电\n"
                + "· 也可以在「最近任务」里把八条桌宠锁住（下拉或长按卡片加锁）", 13, SOFT));

        TextView credit = text("表情包作者：呆猫八条（抖音号 3086296040）。喜欢八条就去关注它吧。", 13, SOFT);
        credit.setPadding(0, (int) (24 * d), 0, 0);
        box.addView(credit);
    }

    @Override
    protected void onResume() {
        super.onResume();
        stillBox.setChecked(prefs.getBoolean("still", false));
        remindBox.setChecked(prefs.getBoolean("remind", true));
        sizeGroup.check(prefs.getInt("size", 110));
        // 从悬浮窗权限页回来后接着放八条出来
        if (wantStart && Settings.canDrawOverlays(this)) {
            wantStart = false;
            startPet();
        }
    }

    private void startPet() {
        if (!Settings.canDrawOverlays(this)) {
            wantStart = true;
            toast("找到「八条桌宠」，打开「显示在其他应用上层」（悬浮窗）权限，再返回这里。");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (PetService.running) { toast("八条已经在屏幕上啦。"); return; }
        startForegroundService(new Intent(this, PetService.class));
        moveTaskToBack(true);
    }

    private void send(String action) {
        if (!PetService.running) {
            if (!PetService.ACTION_SIZE.equals(action) && !PetService.ACTION_STILL.equals(action) && !PetService.ACTION_REMIND.equals(action))
                toast("先点「让八条出来」。");
            return;
        }
        startService(new Intent(this, PetService.class).setAction(action));
    }

    private void askBattery() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) { toast("已经是不限制了。"); return; }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // MARK: 界面小工具

    private TextView text(String s, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setLineSpacing(0, 1.25f);
        v.setPadding(0, (int) (6 * d), 0, (int) (6 * d));
        return v;
    }

    private void section(String s) {
        TextView v = text(s, 17, INK);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, (int) (22 * d), 0, (int) (4 * d));
        box.addView(v);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, (int) (8 * d), 0, 0);
        return r;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, (int) (46 * d), 1);
        p.setMargins((int) (3 * d), 0, (int) (3 * d), 0);
        return p;
    }

    private Button button(String s, boolean primary, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.WHITE : INK);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(23 * d);
        bg.setColor(primary ? ACCENT : Color.WHITE);
        if (!primary) bg.setStroke((int) (1 * d), Color.rgb(240, 220, 205));
        b.setBackground(bg);
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    private CheckBox check(String s, String key, boolean def, String action) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(INK);
        c.setChecked(prefs.getBoolean(key, def));
        c.setOnCheckedChangeListener((v, on) -> {
            if (prefs.getBoolean(key, def) == on) return;
            prefs.edit().putBoolean(key, on).apply();
            send(action);
        });
        box.addView(c);
        return c;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
