package dev.claude.oneui7pill;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Plain settings screen: writes Settings.System keys that SystemUI reads live. */
public class MainActivity extends Activity {

    private TextView status;
    private TextView permBanner;
    private Button permButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermission();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private java.util.Map<String, Integer> cache;

    private int get(String key, int def) {
        if (cache == null) {
            cache = PillConfig.readAll(getContentResolver());
            if (cache == null) cache = new java.util.HashMap<String, Integer>();
        }
        Integer v = cache.get(key);
        return v != null ? v : def;
    }

    private void put(String key, int value) {
        try {
            PillConfig.write(getContentResolver(), key, value);
            java.util.Map<String, Integer> now = PillConfig.readAll(getContentResolver());
            int back = (now != null && now.containsKey(key)) ? now.get(key) : Integer.MIN_VALUE;
            if (now != null) cache = now;
            if (status != null) {
                status.setText(back == value
                        ? "Saved: " + key + " = " + back
                        : "NOT saved: wrote " + value + " but it reads back " + back);
            }
        } catch (Throwable t) {
            String msg = "Could not save: " + t;
            if (status != null) status.setText(msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }

    private void refreshPermission() {
        boolean ok = true; // no special permission needed any more
        permBanner.setVisibility(ok ? View.GONE : View.VISIBLE);
        permButton.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(24), dp(20), dp(32));
        sv.addView(col);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("One UI 7 Battery Pill");
        title.setTextSize(24f);
        col.addView(title, matchWrap());

        TextView sub = new TextView(this);
        sub.setText("Changes apply live in SystemUI. If something does not update, restart SystemUI.");
        sub.setPadding(0, dp(4), 0, dp(16));
        col.addView(sub, matchWrap());

        status = new TextView(this);
        status.setText("Stored inside this app (read by SystemUI through its content provider). "
                + "Nothing saved yet this session.");
        status.setPadding(0, 0, 0, dp(12));
        col.addView(status, matchWrap());

        permBanner = new TextView(this);
        permBanner.setText("Needs the \"Modify system settings\" permission to talk to SystemUI.");
        col.addView(permBanner, matchWrap());
        permButton = new Button(this);
        permButton.setText("Grant permission");
        permButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        });
        col.addView(permButton, matchWrap());

        addSwitch(col, "Enable pill (off = stock icon)", PillConfig.K_ENABLED, 1);
        addSwitch(col, "Animations", PillConfig.K_ANIM, 1);
        addSwitch(col, "Charging bolt", PillConfig.K_BOLT, 1);
        addSwitch(col, "Percentage inside the pill", PillConfig.K_INSIDE, 1);

        addSeek(col, "Size", PillConfig.K_SCALE, 70, 140, 100, "%", null);
        addSeek(col, "Low battery color below", PillConfig.K_LOW, 5, 30, 15, "%", null);
        addSeek(col, "Charging color", PillConfig.K_COLOR, 0, PillConfig.COLORS.length - 1, 0, "",
                PillConfig.COLOR_NAMES);

        TextView uiTitle = new TextView(this);
        uiTitle.setText("Notifications & Quick Settings");
        uiTitle.setTextSize(20f);
        uiTitle.setPadding(0, dp(28), 0, dp(4));
        col.addView(uiTitle, matchWrap());

        TextView uiNote = new TextView(this);
        uiNote.setText("Rescales SystemUI sizes (100% = stock). Off by default. These values are cached by "
                + "SystemUI, so restart SystemUI (or reboot) after changing them. "
                + "Turn the switch off and restart to get the stock look back.");
        uiNote.setPadding(0, 0, 0, dp(8));
        col.addView(uiNote, matchWrap());

        addSwitch(col, "Enable notification & QS tweaks", PillConfig.K_UI_ON, 0);
        String[] uiLabels = {
                "Notification card corners", "Notification group spacing", "Notification side margin",
                "Quick Settings corners", "Brightness slider corners", "Quick Settings side padding"
        };
        for (int i = 0; i < uiLabels.length; i++) {
            addSeek(col, uiLabels[i], PillConfig.UI_KEYS[i], PillConfig.UI_MIN[i], PillConfig.UI_MAX[i],
                    100, "%", null);
        }

        Button reset = new Button(this);
        reset.setText("Reset to defaults");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                put(PillConfig.K_UI_ON, 0);
                for (String k : PillConfig.UI_KEYS) put(k, 100);
                put(PillConfig.K_ENABLED, 1);
                put(PillConfig.K_ANIM, 1);
                put(PillConfig.K_BOLT, 1);
                put(PillConfig.K_INSIDE, 1);
                put(PillConfig.K_SCALE, 100);
                put(PillConfig.K_LOW, 15);
                put(PillConfig.K_COLOR, 0);
                recreate();
            }
        });
        col.addView(reset, matchWrap());
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void addSwitch(LinearLayout col, String label, final String key, int def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setPadding(0, dp(10), 0, dp(10));
        sw.setChecked(get(key, def) != 0);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                put(key, checked ? 1 : 0);
            }
        });
        col.addView(sw, matchWrap());
    }

    private void addSeek(LinearLayout col, final String label, final String key, final int min,
                         int max, int def, final String unit, final String[] names) {
        final TextView tv = new TextView(this);
        tv.setPadding(0, dp(14), 0, 0);
        col.addView(tv, matchWrap());

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        int cur = Math.max(min, Math.min(max, get(key, def)));
        sb.setProgress(cur - min);
        tv.setText(describe(label, cur, unit, names, min));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tv.setText(describe(label, p + min, unit, names, min));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) {
                put(key, s.getProgress() + min);
            }
        });
        col.addView(sb, matchWrap());
    }

    private static String describe(String label, int value, String unit, String[] names, int min) {
        if (names != null) {
            int i = Math.max(0, Math.min(names.length - 1, value - min));
            return label + ": " + names[i];
        }
        return label + ": " + value + unit;
    }
}
