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
import android.os.Build;
import android.os.SystemClock;

/**
 * One UI 7 battery pill (v0.5).
 *
 * Look, matched to the One UI 7 status bar:
 *  - rounded pill, fill grows from the left with a straight (square) edge, lighter track behind it
 *  - the number is ONE color (the inverse of the icon color) drawn across the whole pill, bold, centered
 *  - charging: green fill, white bolt in front of the number, pill gets wider to fit the bolt
 *  - power saving: leaf in front of the number (normal colors), pill gets wider
 *  - low battery: red fill
 * Optional (settings): split-color number (old look), yellow power-saving fill, neutral charging fill.
 *
 * Animations only redraw while they are actually running (no constant redraw = no battery cost).
 */
public class PillDrawable extends Drawable {

    public interface ColorSource { int foreground(); }

    // ---- tweakables ----
    public static final float HEIGHT_DP = 14f;
    public static final float WIDTH_DP = 26f;        // number only
    public static final float WIDTH_ICON_DP = 32f;   // bolt / leaf + number
    private static final int COLOR_POWER_SAVE = 0xFFFFC83D; // only used by the "yellow" saver style
    private static final int COLOR_LOW = 0xFFFF4B4B;
    private static final int INK_DARK = 0xFF1C1C1E;     // number color on a light icon

    // animation timing (ms)
    private static final long INTRO_MS = 1300;
    private static final long WIPE_MS = 900;
    private static final long BOLT_DELAY_MS = 150;
    private static final long BOLT_POP_MS = 450;
    private static final long SHIMMER_PERIOD_MS = 4200;
    private static final long SWEEP_MS = 1100;
    private static final long LEVEL_ANIM_MS = 600;
    private static final long COLOR_ANIM_MS = 350;

    private final float density;
    private final ColorSource colorSource;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path roundPath = new Path();
    private final Path boltPath = new Path();
    private final Path leafPath = new Path();
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
    private boolean splitText = false;     // false = One UI 7 single color
    private boolean yellowSaver = false;   // false = One UI 7 leaf
    private boolean neutralCharge = false; // false = green fill

    // animation state
    private float levelFrom = 100f, levelTo = 100f;
    private long levelStart = 0;
    private long chargeAnchor = 0;
    private boolean playIntro = false;
    private int colorKind = -1;
    private int colorFrom = Color.WHITE;
    private long colorStart = 0;
    private int lastColor = Color.WHITE;
    private float iconCx, iconCy;

    public PillDrawable(float density, ColorSource colorSource) {
        this.density = density;
        this.colorSource = colorSource;
        if (Build.VERSION.SDK_INT >= 28) {
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, 700, false));
        } else {
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        }
        textPaint.setTextAlign(Paint.Align.LEFT);
        buildLeaf();
    }

    /** True when a bolt or leaf is shown in front of the number (pill is wider then). */
    private boolean hasIcon() {
        return (charging && showBolt) || (powerSave && !charging && !yellowSaver);
    }

    public int widthPx() {
        float dp = hasIcon() ? WIDTH_ICON_DP : WIDTH_DP;
        return Math.round(dp * density * scale);
    }
    public int heightPx() { return Math.round(HEIGHT_DP * density * scale); }

    public void configure(float scale, int lowLevel, int chargeColor,
                          boolean animate, boolean showBolt, boolean showNumber,
                          int textStyle, int saverStyle, int chargeStyle) {
        this.scale = scale;
        this.lowLevel = lowLevel;
        this.chargeColor = chargeColor;
        this.animate = animate;
        this.showBolt = showBolt;
        this.showNumber = showNumber;
        this.splitText = textStyle == 1;
        this.yellowSaver = saverStyle == 1;
        this.neutralCharge = chargeStyle == 1;
        invalidateSelf();
    }

    public void setState(int newLevel, boolean newCharging, boolean newPowerSave) {
        long now = SystemClock.uptimeMillis();
        int l = Math.max(0, Math.min(100, newLevel));
        if (!initialized) {
            initialized = true;
            level = l; levelFrom = l; levelTo = l; levelStart = now - LEVEL_ANIM_MS;
            charging = newCharging; powerSave = newPowerSave;
            chargeAnchor = now - INTRO_MS;
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
        if (newCharging && !charging) {
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
        final boolean fgLight = isLight(fg);

        // --- fill color (cross-fades when the state kind changes) ---
        // 1 = charging (green), 2 = yellow power saving (optional), 3 = low (red), 0 = normal
        int kind = charging ? 1 : (powerSave && yellowSaver) ? 2 : level <= lowLevel ? 3 : 0;
        if (colorKind == -1) {
            colorKind = kind;
            colorStart = now - COLOR_ANIM_MS;
        } else if (kind != colorKind) {
            colorFrom = lastColor;
            colorKind = kind;
            colorStart = now;
        }
        int target = kind == 1 ? (neutralCharge ? fg : chargeColor)
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

        // number / icon colors
        final int ink = fgLight ? INK_DARK : Color.WHITE;           // inverse of the icon color
        final int single = (charging && !neutralCharge) ? Color.WHITE : ink; // One UI 7: one color
        final int onFill = isLight(fillColor) ? INK_DARK : Color.WHITE;      // split style, inside fill
        final int onTrack = fg;                                              // split style, on track

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
        float iconScale = 1f;
        if (introMs >= 0f) iconScale = easeOutBack((introMs - BOLT_DELAY_MS) / (float) BOLT_POP_MS);
        if (introMs >= 0f && introMs < BOLT_DELAY_MS) iconScale = 0f;

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

        // track (lighter on a dark status bar, softer on a light one)
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(fg);
        trackPaint.setAlpha((int) ((fgLight ? 150 : 95) * alpha / 255f));
        canvas.drawPath(roundPath, trackPaint);

        float fillRight = b.left + w * Math.max(0f, Math.min(1f, frac));

        // fill, clipped to the pill shape, straight right edge
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

        // --- number + icon layout (centered as one group) ---
        boolean drawBolt = charging && showBolt;
        boolean drawLeaf = powerSave && !charging && !yellowSaver;
        boolean icon = drawBolt || drawLeaf;
        float iconW = icon ? h * (drawLeaf ? 0.52f : 0.40f) : 0f;
        float iconH = icon ? h * (drawLeaf ? 0.52f : 0.66f) : 0f;
        float gap = (icon && showNumber) ? h * 0.10f : 0f;
        float pad = h * 0.14f;

        float textSize = h * 0.74f;
        textPaint.setTextSize(textSize);
        String s = String.valueOf(level);
        float textW = showNumber ? textPaint.measureText(s) : 0f;
        float avail = w - 2f * pad - iconW - gap;
        if (showNumber && textW > avail && avail > 0f) {      // "100" etc: shrink to fit
            textSize *= avail / textW;
            textPaint.setTextSize(textSize);
            textW = textPaint.measureText(s);
        }
        float total = iconW + gap + textW;
        float startX = b.left + (w - total) / 2f;
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = b.top + h / 2f - (fm.ascent + fm.descent) / 2f;
        float textX = startX + iconW + gap;

        if (icon) {
            float iy = b.top + (h - iconH) / 2f;
            if (drawBolt) buildBolt(startX, iy, iconW, iconH);
            else placeLeaf(startX, iy, iconW, iconH);
            iconCx = startX + iconW / 2f;
            iconCy = b.top + h / 2f;
        }

        if (!splitText) {
            drawContent(canvas, s, textX, baseline, single, drawBolt, drawLeaf, iconScale);
        } else {
            canvas.save();
            canvas.clipRect(b.left, b.top, fillRight, b.bottom);
            drawContent(canvas, s, textX, baseline, onFill, drawBolt, drawLeaf, iconScale);
            canvas.restore();
            canvas.save();
            canvas.clipRect(fillRight, b.top, b.right, b.bottom);
            drawContent(canvas, s, textX, baseline, onTrack, drawBolt, drawLeaf, iconScale);
            canvas.restore();
        }

        // --- schedule next frame only while something is animating ---
        unscheduleSelf(tick);
        if (active) {
            invalidateSelf();
        } else if (nextWake > 0) {
            scheduleSelf(tick, nextWake);
        }
    }

    private void drawContent(Canvas canvas, String s, float x, float baseline, int color,
                             boolean bolt, boolean leaf, float iconScale) {
        if (showNumber) {
            textPaint.setColor(color);
            textPaint.setAlpha(alpha);
            canvas.drawText(s, x, baseline, textPaint);
        }
        if ((bolt || leaf) && iconScale > 0.01f) {
            iconPaint.setStyle(Paint.Style.FILL);
            iconPaint.setColor(color);
            iconPaint.setAlpha(alpha);
            canvas.save();
            canvas.scale(iconScale, iconScale, iconCx, iconCy);
            canvas.drawPath(bolt ? boltPath : leafPath, iconPaint);
            canvas.restore();
        }
    }

    private void buildBolt(float x, float y, float w, float h) {
        boltPath.reset();
        boltPath.moveTo(x + w * 0.62f, y);
        boltPath.lineTo(x, y + h * 0.56f);
        boltPath.lineTo(x + w * 0.46f, y + h * 0.56f);
        boltPath.lineTo(x + w * 0.38f, y + h);
        boltPath.lineTo(x + w, y + h * 0.40f);
        boltPath.lineTo(x + w * 0.54f, y + h * 0.40f);
        boltPath.close();
    }

    // unit-square leaf, scaled/translated into place by placeLeaf()
    private final Path leafUnit = new Path();
    private void buildLeaf() {
        leafUnit.reset();
        leafUnit.moveTo(0.10f, 0.92f);                                    // stem tip
        leafUnit.cubicTo(0.00f, 0.50f, 0.30f, 0.10f, 0.95f, 0.05f);       // upper edge to tip
        leafUnit.cubicTo(1.00f, 0.62f, 0.62f, 0.96f, 0.16f, 0.86f);       // lower edge back
        leafUnit.close();
    }
    private void placeLeaf(float x, float y, float w, float h) {
        Matrix m = new Matrix();
        m.setScale(w, h);
        m.postTranslate(x, y);
        leafPath.reset();
        leafUnit.transform(m, leafPath);
    }

    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { /* colours are managed internally */ }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
