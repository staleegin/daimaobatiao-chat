package com.daimaobatiao.pet;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

// 抠好的表情帧（由 Mac 版 export 模式导出，放在 apk 的 assets 里）
final class Library {
    static final class Anim {
        Bitmap[] frames;
        double[] delays;
    }

    private final AssetManager am;
    private final Map<String, String[]> groups = new HashMap<>();
    private final Map<String, double[]> delays = new HashMap<>();
    private final Map<String, Anim> cache = new HashMap<>();
    private final Random rnd = new Random();

    Library(AssetManager am) {
        this.am = am;
        try {
            JSONObject root = new JSONObject(read("manifest.json"));
            JSONObject g = root.getJSONObject("groups");
            for (Iterator<String> it = g.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONArray a = g.getJSONArray(k);
                String[] ids = new String[a.length()];
                for (int i = 0; i < ids.length; i++) ids[i] = a.getString(i);
                groups.put(k, ids);
            }
            JSONObject d = root.getJSONObject("delays");
            for (Iterator<String> it = d.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONArray a = d.getJSONArray(k);
                double[] v = new double[a.length()];
                for (int i = 0; i < v.length; i++) v[i] = Math.max(a.getDouble(i), 0.04);
                delays.put(k, v);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Anim get(String id) {
        Anim a = cache.get(id);
        if (a != null) return a;
        double[] d = delays.get(id);
        a = new Anim();
        a.delays = d;
        a.frames = new Bitmap[d.length];
        for (int i = 0; i < d.length; i++) {
            try (InputStream s = am.open(id + "/" + String.format(Locale.US, "%03d.png", i))) {
                a.frames[i] = BitmapFactory.decodeStream(s);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        cache.put(id, a);
        return a;
    }

    String pick(String group) {
        String[] ids = groups.get(group);
        return ids[rnd.nextInt(ids.length)];
    }

    private String read(String name) throws Exception {
        try (InputStream s = am.open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
