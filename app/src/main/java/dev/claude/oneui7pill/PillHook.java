package dev.claude.oneui7pill;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hooks com.android.systemui.battery.BatteryMeterView and swaps its icon for a One UI 7 style pill.
 * Settings are read live from Settings.System (keys "oneui7pill_*") written by the module app.
 * With "enabled" = off the stock icon is restored. All hooks are try/catch-wrapped.
 */
public class PillHook implements IXposedHookLoadPackage {

    private static final String TAG = "[OneUI7Pill] ";
    private static final String VIEW_CLASS = "com.android.systemui.battery.BatteryMeterView";
    private static final String HOLDER_KEY = "oneui7pill_holder";

    private static final String[] APPLY_AFTER = {
            "updateDrawable", "updatePercentText", "updateShowPercent", "setPercentShowMode",
            "scaleBatteryMeterViews", "onAttachedToWindow", "onDarkChanged", "updateColors",
            "setColorsFromContext", "onBatteryLevelChanged", "onPowerSaveChanged",
            "onDensityOrFontScaleChanged", "onConfigurationChanged", "setForceShowPercent"
    };
    private static final String[] RELEASE_AFTER = { "onDetachedFromWindow" };
    // stock methods we call to make SystemUI redraw its own state when a setting changes
    private static final String[] STOCK_REFRESH = {
            "updateDrawable", "scaleBatteryMeterViews", "updateShowPercent", "updatePercentText"
    };

    private static final Map<View, Boolean> VIEWS =
            Collections.synchronizedMap(new WeakHashMap<View, Boolean>());
    private static boolean configReady = false;
    private static ContentObserver observer; // strong ref on purpose
    private static volatile ContentResolver resolver;
    private static volatile long lastLoad;

    private static final java.util.concurrent.atomic.AtomicBoolean LOADING =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static Handler mainHandler;

    /** Reads the config off the main thread; optionally refreshes the icons afterwards. */
    private static void loadAsync(final boolean refresh) {
        final ContentResolver cr = resolver;
        if (cr == null || !LOADING.compareAndSet(false, true)) return;
        lastLoad = android.os.SystemClock.uptimeMillis();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    boolean ok = PillConfig.load(cr);
                    if (ok && refresh && mainHandler != null) {
                        mainHandler.post(new Runnable() {
                            @Override public void run() { refreshAll(); }
                        });
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "load failed: " + t);
                } finally {
                    LOADING.set(false);
                }
            }
        }, "oneui7pill-load").start();
    }

    /** Fallback if the ContentObserver never fires: re-read settings at most every 5 s. */
    static void reloadIfStale() {
        if (resolver == null) return;
        if (android.os.SystemClock.uptimeMillis() - lastLoad < 5000) return;
        loadAsync(true);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) return;

        try {
            UiHook.install(); // v0.4 notification / QS tweaks (inert until switched on in the app)
        } catch (Throwable t) {
            XposedBridge.log(TAG + "UiHook install failed: " + t);
        }

        // v0.7: the status bar clock is another place to find the status bar (for side margins)
        try {
            Class<?> clock = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.policy.Clock", lpparam.classLoader);
            if (clock != null) {
                int n = XposedBridge.hookAllMethods(clock, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (param.thisObject instanceof View) {
                                ensureConfig(((View) param.thisObject).getContext());
                                StatusBarTweaks.onViewAttached((View) param.thisObject);
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "clock hook failed: " + t);
                        }
                    }
                }).size();
                XposedBridge.log(TAG + "hooked " + n + " Clock.onAttachedToWindow");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Clock hook failed: " + t);
        }

        Class<?> cls = XposedHelpers.findClassIfExists(VIEW_CLASS, lpparam.classLoader);
        if (cls == null) {
            XposedBridge.log(TAG + "BatteryMeterView not found - nothing hooked (stock icon stays).");
            return;
        }

        XC_MethodHook applyHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.thisObject instanceof View) apply((View) param.thisObject);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "apply failed: " + t);
                }
            }
        };
        XC_MethodHook releaseHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object h = XposedHelpers.getAdditionalInstanceField(param.thisObject, HOLDER_KEY);
                    if (h instanceof Holder) ((Holder) h).unregister();
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "release failed: " + t);
                }
            }
        };

        int hooked = 0;
        for (String name : APPLY_AFTER) {
            hooked += XposedBridge.hookAllMethods(cls, name, applyHook).size();
        }
        for (String name : RELEASE_AFTER) {
            hooked += XposedBridge.hookAllMethods(cls, name, releaseHook).size();
        }
        XposedBridge.log(TAG + "hooked " + hooked + " methods on BatteryMeterView");
    }

    // ------------------------------------------------------------------------------------
    // live settings

    static boolean configReady() {
        return configReady;
    }

    /** Lets Resources hooks start the live-settings observer before any battery view exists. */
    static void initFromApp() {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app instanceof Context) ensureConfig((Context) app);
        } catch (Throwable ignored) { }
    }

    private static synchronized void ensureConfig(Context ctx) {
        if (configReady) return;
        configReady = true;
        try {
            final ContentResolver cr = ctx.getContentResolver();
            resolver = cr;
            mainHandler = new Handler(Looper.getMainLooper());
            observer = new ContentObserver(mainHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    try {
                        loadAsync(true);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "settings change failed: " + t);
                    }
                }
            };
            cr.registerContentObserver(Uri.parse("content://" + PillConfig.AUTH), true, observer);
            loadAsync(true); // defaults are used until this finishes
        } catch (Throwable t) {
            XposedBridge.log(TAG + "config init failed (defaults used): " + t);
        }
    }

    private static void refreshAll() {
        StatusBarTweaks.reapplyAll();
        List<View> list;
        synchronized (VIEWS) { list = new ArrayList<View>(VIEWS.keySet()); }
        for (View v : list) {
            try {
                for (String m : STOCK_REFRESH) safeCall(v, m);
                apply(v);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "refresh failed: " + t);
            }
        }
    }

    private static void safeCall(View v, String method) {
        try { XposedHelpers.callMethod(v, method); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------------------------

    private static void apply(View root) {
        ensureConfig(root.getContext());
        reloadIfStale();
        VIEWS.put(root, Boolean.TRUE);
        StatusBarTweaks.onViewAttached(root);

        Holder h = (Holder) XposedHelpers.getAdditionalInstanceField(root, HOLDER_KEY);

        if (!PillConfig.enabled) {              // disabled -> leave stock icon alone
            if (h != null && h.active) {
                h.active = false;
                h.unregister();
            }
            return;
        }

        ImageView icon = findIcon(root);
        if (icon == null) return;
        TextView percent = findPercent(root);

        if (h == null) {
            h = new Holder(root.getContext());
            XposedHelpers.setAdditionalInstanceField(root, HOLDER_KEY, h);
        }
        h.active = true;
        h.percentView = percent;
        h.registerIfNeeded();

        PillDrawable d = h.drawable;
        d.configure(PillConfig.scalePct / 100f, PillConfig.low, PillConfig.chargeColor(),
                PillConfig.anim, PillConfig.bolt, PillConfig.inside,
                PillConfig.textStyle, PillConfig.saverStyle, PillConfig.chargeStyle);

        h.iconRef = new java.lang.ref.WeakReference<ImageView>(icon);
        if (icon.getDrawable() != d) icon.setImageDrawable(d);
        icon.setScaleType(ImageView.ScaleType.FIT_XY);
        fitIcon(icon, d);

        if (PillConfig.inside && percent != null && percent.getVisibility() != View.GONE) {
            percent.setVisibility(View.GONE);
        }
        d.invalidateSelf();
    }

    /** The pill is wider with a bolt / leaf, so the ImageView size follows the drawable's state. */
    private static void fitIcon(ImageView icon, PillDrawable d) {
        ViewGroup.LayoutParams lp = icon.getLayoutParams();
        if (lp != null && (lp.width != d.widthPx() || lp.height != d.heightPx())) {
            lp.width = d.widthPx();
            lp.height = d.heightPx();
            icon.setLayoutParams(lp);
        }
    }

    private static ImageView findIcon(View root) {
        try {
            Object o = XposedHelpers.getObjectField(root, "mBatteryIconView");
            if (o instanceof ImageView) return (ImageView) o;
        } catch (Throwable ignored) { }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (g.getChildAt(i) instanceof ImageView) return (ImageView) g.getChildAt(i);
            }
        }
        return null;
    }

    private static TextView findPercent(View root) {
        try {
            Object o = XposedHelpers.getObjectField(root, "mBatteryPercentView");
            if (o instanceof TextView) return (TextView) o;
        } catch (Throwable ignored) { }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (g.getChildAt(i) instanceof TextView) return (TextView) g.getChildAt(i);
            }
        }
        return null;
    }

    /** Per-BatteryMeterView state: the drawable plus a battery receiver while attached. */
    private static final class Holder extends BroadcastReceiver implements PillDrawable.ColorSource {
        final Context context;
        final PillDrawable drawable;
        TextView percentView;
        java.lang.ref.WeakReference<ImageView> iconRef; // weak: the view is the key of the holder map
        boolean registered;
        boolean active;

        Holder(Context ctx) {
            this.context = ctx;
            this.drawable = new PillDrawable(ctx.getResources().getDisplayMetrics().density, this);
        }

        @Override
        public int foreground() {
            TextView tv = percentView;
            return tv != null ? tv.getCurrentTextColor() : 0xFFFFFFFF;
        }

        void registerIfNeeded() {
            if (registered) return;
            try {
                IntentFilter f = new IntentFilter();
                f.addAction(Intent.ACTION_BATTERY_CHANGED);
                f.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
                Intent sticky;
                if (Build.VERSION.SDK_INT >= 33) {
                    sticky = context.registerReceiver(this, f, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    sticky = context.registerReceiver(this, f);
                }
                registered = true;
                if (sticky != null) onReceive(context, sticky);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "register failed: " + t);
            }
        }

        void unregister() {
            if (!registered) return;
            try { context.unregisterReceiver(this); } catch (Throwable ignored) { }
            registered = false;
        }

        @Override
        public void onReceive(Context c, Intent i) {
            try {
                int level = 100;
                boolean charging = false;
                Intent b = i;
                if (!Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
                    b = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                }
                if (b != null) {
                    int raw = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    if (raw >= 0 && scale > 0) level = Math.round(raw * 100f / scale);
                    int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
                    int plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                    charging = plugged != 0 && (status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL);
                }
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                boolean saver = pm != null && pm.isPowerSaveMode();
                drawable.setState(level, charging, saver);
                ImageView iv = iconRef != null ? iconRef.get() : null;
                if (iv != null) fitIcon(iv, drawable); // width changes with bolt / leaf
            } catch (Throwable t) {
                XposedBridge.log(TAG + "onReceive failed: " + t);
            }
        }
    }
}
