package dev.claude.oneui7pill;

import android.view.View;
import android.view.ViewParent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedBridge;

/**
 * View-level status bar tweaks (v0.7). No dimen names needed.
 *
 * One UI 7 has clearly larger left/right margins in the status bar. The stock margin comes from
 * the paddingStart/paddingEnd of the status bar contents view, which is set while the XML layout
 * is inflated (so Resources.getDimension hooks never see it). We find that view from any status
 * bar child (battery view / clock) and add extra padding on both sides. If SystemUI sets its own
 * padding again (rotation, cutout, density change) we notice and add our extra on top again.
 * Extra = 0 puts the stock padding back.
 */
final class StatusBarTweaks {
    private StatusBarTweaks() {}

    private static final String TAG = "[OneUI7Pill] ";
    private static final String CONTENTS_ID = "status_bar_contents";

    private static final class State implements View.OnLayoutChangeListener, Runnable {
        final View view;
        int baseL, baseR, appliedL, appliedR;
        boolean valid;
        boolean pending;

        State(View v) { view = v; }

        @Override
        public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
            // padding changes must not happen inside a layout pass -> do it right after
            if (!pending) {
                pending = true;
                v.post(this);
            }
        }

        @Override
        public void run() {
            pending = false;
            try { applyTo(this); } catch (Throwable th) { XposedBridge.log(TAG + "status bar margin failed: " + th); }
        }
    }

    private static final Map<View, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<View, State>());
    private static int chainLogs = 0;
    private static boolean foundLogged = false;

    /** Call with any view that lives inside the status bar (battery meter, clock ...). */
    static void onViewAttached(View any) {
        try {
            View contents = findContents(any);
            if (contents == null) {
                logChain(any);
                return;
            }
            State st;
            synchronized (STATES) {
                st = STATES.get(contents);
                if (st == null) {
                    st = new State(contents);
                    STATES.put(contents, st);
                    contents.addOnLayoutChangeListener(st);
                    if (!foundLogged) {
                        foundLogged = true;
                        XposedBridge.log(TAG + "status bar contents found: " + describe(contents));
                    }
                }
            }
            applyTo(st);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "status bar tweak failed: " + t);
        }
    }

    /** Re-applies the current setting to every known status bar (settings changed). */
    static void reapplyAll() {
        List<State> list;
        synchronized (STATES) { list = new ArrayList<State>(STATES.values()); }
        for (State st : list) {
            try { applyTo(st); } catch (Throwable ignored) { }
        }
    }

    private static void applyTo(State st) {
        View v = st.view;
        float density = v.getResources().getDisplayMetrics().density;
        int extra = Math.round(PillConfig.sbExtraDp * density);

        int curL = v.getPaddingLeft();
        int curR = v.getPaddingRight();
        int baseL, baseR;
        if (st.valid && curL == st.appliedL && curR == st.appliedR) {
            baseL = st.baseL;       // still our value -> keep the remembered stock padding
            baseR = st.baseR;
        } else {
            baseL = curL;           // SystemUI set its own padding -> that is the new stock value
            baseR = curR;
        }
        int newL = baseL + extra;
        int newR = baseR + extra;
        st.baseL = baseL; st.baseR = baseR;
        st.appliedL = newL; st.appliedR = newR;
        st.valid = true;
        if (newL != curL || newR != curR) {
            v.setPadding(newL, v.getPaddingTop(), newR, v.getPaddingBottom());
        }
    }

    // ------------------------------------------------------------------------------------

    private static View findContents(View start) {
        View cur = start;
        View prev = null;
        for (int i = 0; i < 16 && cur != null; i++) {
            if (CONTENTS_ID.equals(idName(cur))) return cur;
            String cn = cur.getClass().getName();
            if (prev != null && cn.contains("PhoneStatusBarView") && !cn.contains("Keyguard")) {
                return prev; // direct child of the status bar root
            }
            ViewParent p = cur.getParent();
            prev = cur;
            cur = (p instanceof View) ? (View) p : null;
        }
        return null;
    }

    private static String idName(View v) {
        try {
            int id = v.getId();
            if (id == View.NO_ID) return null;
            return v.getResources().getResourceEntryName(id);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String describe(View v) {
        String n = idName(v);
        return v.getClass().getSimpleName() + (n != null ? "#" + n : "");
    }

    /** Debug aid: if the status bar is not found, log the parent chain (max 3 times). */
    private static void logChain(View start) {
        if (chainLogs >= 3) return;
        chainLogs++;
        StringBuilder sb = new StringBuilder(TAG + "status bar contents NOT found from " + describe(start) + " | chain:");
        View cur = start;
        for (int i = 0; i < 10 && cur != null; i++) {
            sb.append(' ').append(describe(cur)).append(" <");
            ViewParent p = cur.getParent();
            cur = (p instanceof View) ? (View) p : null;
        }
        XposedBridge.log(sb.toString());
    }
}
