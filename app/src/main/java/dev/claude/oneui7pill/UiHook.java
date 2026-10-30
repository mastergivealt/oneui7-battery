package dev.claude.oneui7pill;

import android.content.res.Resources;
import android.util.SparseIntArray;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Notification / Quick Settings tweaks (v0.4).
 *
 * No SystemUI class names are needed: we hook Resources.getDimension*() inside the SystemUI
 * process and rescale only dimens whose NAME matches the table below (names come from the
 * probe-4 report of the A136B / One UI 6.1 SystemUI). Every unknown id is ignored, every
 * callback is try/catch-wrapped, and the whole feature is OFF until the user switches it on.
 *
 * Limit: this only catches dimens SystemUI reads from code. Values read while XML layouts or
 * drawables are inflated are not touched. SystemUI caches most values, so a SystemUI restart
 * is needed after a change.
 */
final class UiHook {
    private UiHook() {}

    private static final String TAG = "[OneUI7Pill] ";
    private static final String SYSUI = "com.android.systemui";

    // group ids (index into PillConfig.UI_PCT)
    static final int NOTIF_RADIUS = 0;
    static final int NOTIF_GROUP_GAP = 1;
    static final int NOTIF_SIDE = 2;
    static final int QS_RADIUS = 3;
    static final int BRIGHT_RADIUS = 4;
    static final int QS_SIDE = 5;
    static final int SB_SIDE = 6;
    static final int SB_GAP = 7;
    static final int SB_CLOCK = 8;
    static final int GROUPS = 9;

    private static final Map<String, Integer> NAMES = new HashMap<String, Integer>();
    static {
        // notification card corners (+ shade scrim / panel corners)
        n(NOTIF_RADIUS, "notification_corner_radius", "notification_corner_radius_small",
                "notification_scrim_corner_radius", "sec_notification_scrim_corner_radius",
                "notification_panel_background_radius");
        // spacing inside notification groups
        n(NOTIF_GROUP_GAP, "notification_children_padding", "notification_children_container_margin_top",
                "notification_children_container_top_padding",
                "notification_children_collapsed_bottom_padding");
        // left/right margin of the notification list
        n(NOTIF_SIDE, "notification_side_paddings", "notification_shade_content_margin_horizontal");
        // quick settings panel / detail buttons
        n(QS_RADIUS, "qs_corner_radius", "qs_detail_button_background_radius");
        // brightness slider
        n(BRIGHT_RADIUS, "sec_brightness_slider_corner_radius", "sec_brightness_background_corner_radius");
        // quick settings side padding
        n(QS_SIDE, "qs_content_horizontal_padding");

        // v0.6 status bar. These are the AOSP names; Samsung may use others. Turn on the
        // discovery log in the app to see which status bar dimens your SystemUI really reads.
        n(SB_SIDE, "status_bar_padding_start", "status_bar_padding_end",
                "status_bar_contents_padding_start", "status_bar_contents_padding_end",
                "status_bar_content_padding_start", "status_bar_content_padding_end",
                "status_bar_side_padding", "status_bar_left_side_padding", "status_bar_right_side_padding");
        n(SB_GAP, "status_bar_icon_horizontal_margin", "status_bar_icon_padding",
                "signal_cluster_margin_start", "status_bar_system_icon_spacing",
                "status_bar_icons_padding_start");
        n(SB_CLOCK, "status_bar_clock_size", "status_bar_clock_text_size");
    }

    private static void n(int group, String... names) {
        for (String s : names) NAMES.put(s, group);
    }

    /** Per-Resources cache: resource id -> group (or -1 = not ours). */
    private static final Map<Resources, SparseIntArray> CACHE =
            Collections.synchronizedMap(new WeakHashMap<Resources, SparseIntArray>());

    static void install() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    handle(param);
                } catch (Throwable ignored) {
                    // never let a tweak break SystemUI
                }
            }
        };
        int n = 0;
        String[] methods = { "getDimension", "getDimensionPixelSize", "getDimensionPixelOffset" };
        for (String m : methods) {
            try {
                n += XposedBridge.hookAllMethods(Resources.class, m, hook).size();
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hook " + m + " failed: " + t);
            }
        }
        XposedBridge.log(TAG + "UI tweaks: hooked " + n + " Resources methods");
    }

    private static void handle(XC_MethodHook.MethodHookParam param) {
        if (!PillHook.configReady()) PillHook.initFromApp();
        PillHook.reloadIfStale();
        if (PillConfig.sbLog) logStatusBarDimen(param);
        if (!PillConfig.uiOn) return;

        Object[] a = param.args;
        if (a == null || a.length != 1 || !(a[0] instanceof Integer)) return;
        if (!(param.thisObject instanceof Resources)) return;
        int id = (Integer) a[0];
        if ((id >>> 24) != 0x7f) return; // app resources only, skip framework ids fast

        Resources res = (Resources) param.thisObject;
        int group = groupOf(res, id);
        if (group < 0) return;

        int pct = PillConfig.UI_PCT[group];
        if (pct == 100) return;

        Object r = param.getResult();
        if (r instanceof Float) {
            param.setResult((Float) r * pct / 100f);
        } else if (r instanceof Integer) {
            int stock = (Integer) r;
            int v = Math.round(stock * pct / 100f);
            if (stock > 0 && pct > 0 && v < 1) v = 1;
            param.setResult(v);
        }
    }

    // ---- discovery log: every status-bar-looking dimen SystemUI reads, once, with its value ----
    private static final java.util.Set<String> LOGGED =
            Collections.synchronizedSet(new java.util.HashSet<String>());
    private static final String[] LOG_WORDS = {
            "status_bar", "statusbar", "signal", "clock", "system_icon", "mobile", "wifi",
            "battery", "icon_size", "volte", "network"
    };

    private static void logStatusBarDimen(XC_MethodHook.MethodHookParam param) {
        Object[] a = param.args;
        if (a == null || a.length != 1 || !(a[0] instanceof Integer)) return;
        if (!(param.thisObject instanceof Resources)) return;
        int id = (Integer) a[0];
        if ((id >>> 24) != 0x7f) return;
        Resources res = (Resources) param.thisObject;
        try {
            if (!"dimen".equals(res.getResourceTypeName(id))) return;
            String name = res.getResourceEntryName(id);
            boolean hit = false;
            for (String w : LOG_WORDS) if (name.contains(w)) { hit = true; break; }
            if (!hit || !LOGGED.add(name)) return;
            XposedBridge.log(TAG + "dimen " + name + " = " + param.getResult()
                    + " (via " + param.method.getName() + ")");
        } catch (Throwable ignored) { }
    }

    private static int groupOf(Resources res, int id) {
        SparseIntArray c;
        synchronized (CACHE) {
            c = CACHE.get(res);
            if (c == null) {
                c = new SparseIntArray();
                CACHE.put(res, c);
            }
        }
        int g = c.get(id, -2);
        if (g != -2) return g;

        g = -1;
        try {
            if (SYSUI.equals(res.getResourcePackageName(id))
                    && "dimen".equals(res.getResourceTypeName(id))) {
                Integer x = NAMES.get(res.getResourceEntryName(id));
                if (x != null) g = x;
            }
        } catch (Throwable ignored) {
            g = -1;
        }
        synchronized (c) { c.put(id, g); }
        return g;
    }
}
