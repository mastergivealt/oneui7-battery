package dev.claude.oneui7pill;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

/** Settings live in the module app (ConfigProvider), written by the app, read live by SystemUI. */
public final class PillConfig {
    private PillConfig() {}

    public static final String P = "oneui7pill_";
    public static final String AUTH = "dev.claude.oneui7pill.cfg";
    public static final Uri URI = Uri.parse("content://" + AUTH + "/all");
    public static final String K_ENABLED = P + "enabled";
    public static final String K_ANIM = P + "anim";
    public static final String K_BOLT = P + "bolt";
    public static final String K_INSIDE = P + "inside";
    public static final String K_SCALE = P + "scale";
    public static final String K_LOW = P + "low";
    public static final String K_COLOR = P + "color";
    // v0.5
    public static final String K_TEXT_STYLE = P + "text_style";   // 0 = One UI 7 (single inverted color), 1 = split
    public static final String K_SAVER_STYLE = P + "saver_style"; // 0 = One UI 7 (leaf), 1 = yellow fill
    public static final String K_CHARGE_STYLE = P + "charge_style"; // 0 = green fill, 1 = neutral fill + bolt

    // v0.4: notification / quick settings tweaks (all default to stock)
    public static final String K_UI_ON = P + "ui_on";
    public static final String K_NOTIF_RADIUS = P + "notif_radius";
    public static final String K_NOTIF_GAP = P + "notif_gap";
    public static final String K_NOTIF_SIDE = P + "notif_side";
    public static final String K_QS_RADIUS = P + "qs_radius";
    public static final String K_BRIGHT_RADIUS = P + "bright_radius";
    public static final String K_QS_SIDE = P + "qs_side";

    /** Order matches the group ids in UiHook. */
    public static final String[] UI_KEYS = {
            K_NOTIF_RADIUS, K_NOTIF_GAP, K_NOTIF_SIDE, K_QS_RADIUS, K_BRIGHT_RADIUS, K_QS_SIDE
    };
    public static final int[] UI_MIN = { 0, 0, 50, 0, 0, 50 };
    public static final int[] UI_MAX = { 250, 200, 200, 250, 250, 200 };

    public static final int[] COLORS = { 0xFF3DDC84, 0xFF4DA3FF, 0xFF2DD4BF, 0xFFB388FF, 0xFFFF6FAE };
    public static final String[] COLOR_NAMES = { "Green", "Blue", "Teal", "Purple", "Pink" };

    public static volatile boolean enabled = true;
    public static volatile boolean anim = true;
    public static volatile boolean bolt = true;
    public static volatile boolean inside = true;
    public static volatile int scalePct = 100;
    public static volatile int low = 15;
    public static volatile int colorIdx = 0;
    public static volatile int textStyle = 0;
    public static volatile int saverStyle = 0;
    public static volatile int chargeStyle = 0;

    public static volatile boolean uiOn = false;
    /** Percent of the stock value per UiHook group (100 = stock). */
    public static final int[] UI_PCT = { 100, 100, 100, 100, 100, 100 };

    public static int chargeColor() {
        int i = colorIdx;
        if (i < 0 || i >= COLORS.length) i = 0;
        return COLORS[i];
    }

    /** All stored values, or null if the provider could not be reached. */
    public static Map<String, Integer> readAll(ContentResolver cr) {
        Cursor c = null;
        try {
            c = cr.query(URI, null, null, null, null);
            if (c == null) return null;
            Map<String, Integer> m = new HashMap<String, Integer>();
            while (c.moveToNext()) m.put(c.getString(0), c.getInt(1));
            return m;
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.close();
        }
    }

    public static void write(ContentResolver cr, String key, int value) {
        ContentValues cv = new ContentValues();
        cv.put("key", key);
        cv.put("value", value);
        if (cr.insert(URI, cv) == null) throw new IllegalStateException("provider rejected " + key);
    }

    private static int get(Map<String, Integer> m, String k, int d) {
        Integer v = m.get(k);
        return v != null ? v : d;
    }

    /** @return false if the provider was unreachable (previous values are kept). */
    public static boolean load(ContentResolver cr) {
        Map<String, Integer> m = readAll(cr);
        if (m == null) return false;
        enabled = get(m, K_ENABLED, 1) != 0;
        anim = get(m, K_ANIM, 1) != 0;
        bolt = get(m, K_BOLT, 1) != 0;
        inside = get(m, K_INSIDE, 1) != 0;
        scalePct = Math.max(70, Math.min(140, get(m, K_SCALE, 100)));
        low = Math.max(5, Math.min(30, get(m, K_LOW, 15)));
        colorIdx = Math.max(0, Math.min(COLORS.length - 1, get(m, K_COLOR, 0)));
        textStyle = Math.max(0, Math.min(1, get(m, K_TEXT_STYLE, 0)));
        saverStyle = Math.max(0, Math.min(1, get(m, K_SAVER_STYLE, 0)));
        chargeStyle = Math.max(0, Math.min(1, get(m, K_CHARGE_STYLE, 0)));

        uiOn = get(m, K_UI_ON, 0) != 0;
        for (int i = 0; i < UI_KEYS.length; i++) {
            UI_PCT[i] = Math.max(UI_MIN[i], Math.min(UI_MAX[i], get(m, UI_KEYS[i], 100)));
        }
        return true;
    }
}
