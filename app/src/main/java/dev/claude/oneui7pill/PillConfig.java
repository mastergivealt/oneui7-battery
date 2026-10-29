package dev.claude.oneui7pill;

import android.content.ContentResolver;
import android.provider.Settings;

/** Settings live in Settings.System (written by the module app, read live by SystemUI). */
public final class PillConfig {
    private PillConfig() {}

    public static final String P = "oneui7pill_";
    public static final String K_ENABLED = P + "enabled";
    public static final String K_ANIM = P + "anim";
    public static final String K_BOLT = P + "bolt";
    public static final String K_INSIDE = P + "inside";
    public static final String K_SCALE = P + "scale";
    public static final String K_LOW = P + "low";
    public static final String K_COLOR = P + "color";

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

    public static volatile boolean uiOn = false;
    /** Percent of the stock value per UiHook group (100 = stock). */
    public static final int[] UI_PCT = { 100, 100, 100, 100, 100, 100 };

    public static int chargeColor() {
        int i = colorIdx;
        if (i < 0 || i >= COLORS.length) i = 0;
        return COLORS[i];
    }

    public static void load(ContentResolver cr) {
        enabled = Settings.System.getInt(cr, K_ENABLED, 1) != 0;
        anim = Settings.System.getInt(cr, K_ANIM, 1) != 0;
        bolt = Settings.System.getInt(cr, K_BOLT, 1) != 0;
        inside = Settings.System.getInt(cr, K_INSIDE, 1) != 0;
        scalePct = Math.max(70, Math.min(140, Settings.System.getInt(cr, K_SCALE, 100)));
        low = Math.max(5, Math.min(30, Settings.System.getInt(cr, K_LOW, 15)));
        colorIdx = Math.max(0, Math.min(COLORS.length - 1, Settings.System.getInt(cr, K_COLOR, 0)));

        uiOn = Settings.System.getInt(cr, K_UI_ON, 0) != 0;
        for (int i = 0; i < UI_KEYS.length; i++) {
            int v = Settings.System.getInt(cr, UI_KEYS[i], 100);
            UI_PCT[i] = Math.max(UI_MIN[i], Math.min(UI_MAX[i], v));
        }
    }
}
