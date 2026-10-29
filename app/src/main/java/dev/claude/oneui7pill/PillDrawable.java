package dev.claude.oneui7pill;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/**
 * One UI 7 style battery pill with animations:
 *  - plug-in: green fill wipes in from the left, bolt pops in with an overshoot
 *  - while charging: a soft light sweep glides over the fill every few seconds
 *  - level changes glide smoothly, colour changes (charging / saver / low) cross-fade
 * Animations only redraw while they are actually running (no constant redraw = no battery cost).
 */
public class PillDrawable extends Drawable {

    public interface ColorSource { int foreground(); }

    // ---- tweakables ----
    public static final float WIDTH_DP = 26f;
    public static final float HEIGHT_DP = 14f;
    private static final int COLOR_POWER_SAVE = 0xFFFFC83D;
    private static final int COLOR_LOW = 0xFFFF4D4D;

    // animation timing (ms)
    private static final long INTRO_MS = 1300;          // whole plug-in animation
    private static final long WIPE_MS = 900;            // fill wipe part of the intro
    private static final long BOLT_DELAY_MS = 150;
    private static final long BOLT_POP_MS = 450;
    private static final long SHIMMER_PERIOD_MS = 4200; // charging light sweep cycle
    private static final long SWEEP_MS = 1100;          // length of one sweep
    private static final long LEVEL_ANIM_MS = 600;
    private static final long COLOR_ANIM_MS = 350;

    private final float density;
    private final ColorSource colorSource;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path roundPath = new Path();
    private final Path boltPath = new Path();
    private final RectF rect = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private final Shader shimmerShader = new LinearGradient(0f, 0f, 1f, 0f,
            new int[] { 0x00FFFFFF, 0x99FFFFFF, 0x00FFFFFF }, new float[] { 0f, 0.5f, 1f },
            Shader.TileMode.CLAMP);
    private final Runnable tick = new Runnable() {
        @Override public void run() { invalidateSelf(); }
    };

    // state
    private int level = 100;
    private boolean charging = false;
    private boolean powerSave = false;
    private int alpha = 255;
    private boolean initialized = false;

    // user config (see PillConfig)
    private float scale = 1f;
    private int lowLevel = 15;
    private int chargeColor = 0xFF3DDC84;
    private boolean animate = true;
    private boolean showBolt = true;
    private boolean showNumber = true;

    // animation state
    private float levelFrom = 100f, levelTo = 100f;
    private long levelStart = 0;
    private long chargeAnchor = 0;
    private boolean playIntro = false;
    private int colorKind = -1;
    private int colorFrom = Color.WHITE;
    private long colorStart = 0;
    private int lastColor = Color.WHITE;
    private float boltCx, boltCy;

    public PillDrawable(float density, ColorSource colorSource) {
        this.density = density;
        this.colorSource = colorSource;
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    public int widthPx() { return Math.round(WIDTH_DP * density * scale); }
    public int heightPx() { return Math.round(HEIGHT_DP * density * scale); }

    public void configure(float scale, int lowLevel, int chargeColor,
                          boolean animate, boolean showBolt, boolean showNumber) {
        this.scale = scale;
        this.lowLevel = lowLevel;
        this.chargeColor = chargeColor;
        this.animate = animate;
        this.showBolt = showBolt;
        this.showNumber = showNumber;
        invalidateSelf();
    }

    public void setState(int newLevel, boolean newCharging, boolean newPowerSave) {
        long now = SystemClock.uptimeMillis();
        int l = Math.max(0, Math.min(100, newLevel));
        if (!initialized) {
            initialized = true;
            level = l; levelFrom = l; levelTo = l; levelStart = now - LEVEL_ANIM_MS;
            charging = newCharging; powerSave = newPowerSave;
            chargeAnchor = now - INTRO_MS;     // already charging at start: no intro, shimmer only
            playIntro = false;
            invalidateSelf();
            return;
        }
        if (l != level) {
            levelFrom = displayLevel(now);
            levelTo = l;
            levelStart = now;
            level = l;
        }
        if (newCharging && !charging) {        // just plugged in
            chargeAnchor = now;
            playIntro = true;
        }
        charging = newCharging;
        powerSave = newPowerSave;
        invalidateSelf();
    }

    private float displayLevel(long now) {
        long dt = now - levelStart;
        if (dt >= LEVEL_ANIM_MS) return levelTo;
        float t = dt / (float) LEVEL_ANIM_MS;
        return levelFrom + (levelTo - levelFrom) * easeOutCubic(t);
    }

    @Override public int getIntrinsicWidth() { return widthPx(); }
    @Override public int getIntrinsicHeight() { return heightPx(); }

    // ---- helpers ----
    private static float easeOutCubic(float t) {
        float u = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - u * u * u;
    }
    private static float easeInOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
    private static float easeOutBack(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
    private static boolean isLight(int c) {
        double lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
        return lum > 0.6;
    }
    private static int lerpColor(int a, int b, float t) {
        int ra = Color.red(a), ga = Color.green(a), ba = Color.blue(a);
        int rb = Color.red(b), gb = Color.green(b), bb = Color.blue(b);
        int r = Math.round(ra + (rb - ra) * t);
        int g = Math.round(ga + (gb - ga) * t);
        int bl = Math.round(ba + (bb - ba) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        final long now = SystemClock.uptimeMillis();
        boolean active = false;

        int fg = colorSource != null ? colorSource.foreground() : Color.WHITE;
        if (Color.alpha(fg) == 0) fg = Color.WHITE;
        fg |= 0xFF000000;

        // --- colour (cross-fades when the state kind changes) ---
        int kind = charging ? 1 : powerSave ? 2 : level <= lowLevel ? 3 : 0;
        if (colorKind == -1) {
            colorKind = kind;
            colorStart = now - COLOR_ANIM_MS;
        } else if (kind != colorKind) {
            colorFrom = lastColor;
            colorKind = kind;
            colorStart = now;
        }
        int target = kind == 1 ? chargeColor
                : kind == 2 ? COLOR_POWER_SAVE
                : kind == 3 ? COLOR_LOW
                : fg;
        float ct = animate ? (now - colorStart) / (float) COLOR_ANIM_MS : 1f;
        int fillColor;
        if (ct >= 1f) {
            fillColor = target;
        } else {
            fillColor = lerpColor(colorFrom, target, easeOutCubic(ct));
            active = true;
        }
        lastColor = fillColor;
        int onFill = isLight(fillColor) ? Color.BLACK : Color.WHITE;

        // --- level (glides) ---
        float disp = animate ? displayLevel(now) : levelTo;
        if (animate && now - levelStart < LEVEL_ANIM_MS) active = true;
        float frac = disp / 100f;

        // --- plug-in intro ---
        float introMs = -1f;
        if (animate && charging && playIntro) {
            long dt = now - chargeAnchor;
            if (dt < INTRO_MS) { introMs = dt; active = true; }
        }
        if (introMs >= 0f) frac *= easeOutCubic(introMs / (float) WIPE_MS);
        float boltScale = 1f;
        if (introMs >= 0f) boltScale = easeOutBack((introMs - BOLT_DELAY_MS) / (float) BOLT_POP_MS);
        if (introMs >= 0f && introMs < BOLT_DELAY_MS) boltScale = 0f;

        // --- shimmer while charging ---
        boolean sweeping = false;
        float sweepT = 0f;
        long nextWake = -1;
        if (animate && charging && introMs < 0f) {
            long since = now - (chargeAnchor + INTRO_MS);
            if (since >= 0) {
                long ph = since % SHIMMER_PERIOD_MS;
                if (ph < SWEEP_MS) {
                    sweeping = true;
                    sweepT = ph / (float) SWEEP_MS;
                    active = true;
                } else {
                    nextWake = now + (SHIMMER_PERIOD_MS - ph);
                }
            }
        }

        // --- geometry ---
        float w = b.width();
        float h = b.height();
        float r = h / 2f;
        rect.set(b.left, b.top, b.right, b.bottom);
        roundPath.reset();
        roundPath.addRoundRect(rect, r, r, Path.Direction.CW);

        // track
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(fg);
        trackPaint.setAlpha((int) (90 * alpha / 255f));
        canvas.drawPath(roundPath, trackPaint);

        float fillRight = b.left + w * Math.max(0f, Math.min(1f, frac));

        // fill, clipped to the pill shape
        canvas.save();
        canvas.clipPath(roundPath);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
        fillPaint.setAlpha(alpha);
        canvas.drawRect(b.left, b.top, fillRight, b.bottom, fillPaint);
        canvas.restore();

        // light sweep over the fill
        if (sweeping && fillRight > b.left + 1f) {
            float bandW = w * 0.55f;
            float x = b.left - bandW + (w + bandW) * easeInOut(sweepT);
            shaderMatrix.setScale(bandW, 1f);
            shaderMatrix.postTranslate(x, 0f);
            shimmerShader.setLocalMatrix(shaderMatrix);
            shimmerPaint.setShader(shimmerShader);
            shimmerPaint.setAlpha(alpha);
            canvas.save();
            canvas.clipPath(roundPath);
            canvas.clipRect(b.left, b.top, fillRight, b.bottom);
            canvas.drawRect(b.left, b.top, b.right, b.bottom, shimmerPaint);
            canvas.restore();
        }

        // --- number (+ bolt) ---
        textPaint.setTextSize(h * 0.68f);
        String s = String.valueOf(level);
        float textW = showNumber ? textPaint.measureText(s) : 0f;
        boolean drawBolt = charging && showBolt;
        float boltW = drawBolt ? h * 0.32f : 0f;
        float gap = (drawBolt && showNumber) ? h * 0.06f : 0f;
        float total = boltW + gap + textW;
        float startX = b.left + (w - total) / 2f;
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = b.top + h / 2f - (fm.ascent + fm.descent) / 2f;

        if (drawBolt) {
            buildBolt(startX, b.top + h * 0.20f, boltW, h * 0.60f);
            boltCx = startX + boltW / 2f;
            boltCy = b.top + h / 2f;
        }

        canvas.save();
        canvas.clipRect(b.left, b.top, fillRight, b.bottom);
        drawContent(canvas, s, startX + boltW + gap, baseline, onFill, boltScale);
        canvas.restore();

        canvas.save();
        canvas.clipRect(fillRight, b.top, b.right, b.bottom);
        drawContent(canvas, s, startX + boltW + gap, baseline, fg, boltScale);
        canvas.restore();

        // --- schedule next frame only while something is animating ---
        unscheduleSelf(tick);
        if (active) {
            invalidateSelf();
        } else if (nextWake > 0) {
            scheduleSelf(tick, nextWake);
        }
    }

    private void drawContent(Canvas canvas, String s, float x, float baseline, int color, float boltScale) {
        if (showNumber) {
            textPaint.setColor(color);
            textPaint.setAlpha(alpha);
            canvas.drawText(s, x, baseline, textPaint);
        }
        if (charging && showBolt && boltScale > 0.01f) {
            boltPaint.setStyle(Paint.Style.FILL);
            boltPaint.setColor(color);
            boltPaint.setAlpha(alpha);
            canvas.save();
            canvas.scale(boltScale, boltScale, boltCx, boltCy);
            canvas.drawPath(boltPath, boltPaint);
            canvas.restore();
        }
    }

    private void buildBolt(float x, float y, float w, float h) {
        boltPath.reset();
        boltPath.moveTo(x + w * 0.60f, y);
        boltPath.lineTo(x, y + h * 0.56f);
        boltPath.lineTo(x + w * 0.46f, y + h * 0.56f);
        boltPath.lineTo(x + w * 0.38f, y + h);
        boltPath.lineTo(x + w, y + h * 0.40f);
        boltPath.lineTo(x + w * 0.54f, y + h * 0.40f);
        boltPath.close();
    }

    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { /* colours are managed internally */ }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
