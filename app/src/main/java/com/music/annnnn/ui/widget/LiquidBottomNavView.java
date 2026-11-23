package com.music.annnnn.ui.widget;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;

import com.music.annnnn.R;

/**
 * Centered 3-Tab Bottom Navigation Bar with custom Liquid Motion animation:
 * - Viscous cubic-Bezier liquid bubble indicator that stretches & flows between tabs
 * - Organic icon squash/stretch deformation, tilt, and droplet bounce during transitions
 * - Dark Botanical Glassmorphism floating capsule dock
 */
public class LiquidBottomNavView extends FrameLayout {

    public interface OnTabSelectedListener {
        boolean onTabSelected(int tabId);
    }

    private static class TabHolder {
        final int id;
        final LinearLayout container;
        final ImageView icon;
        final TextView label;

        TabHolder(int id, LinearLayout container, ImageView icon, TextView label) {
            this.id = id;
            this.container = container;
            this.icon = icon;
            this.label = label;
        }
    }

    private final LinearLayout dockRow;
    private final TabHolder[] tabs = new TabHolder[3];

    private int selectedIndex = 0;
    private int fromIndex = 0;
    private int toIndex = 0;
    private float animFraction = 1f;
    private ValueAnimator liquidAnimator;

    private final Paint dockBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dockStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blobGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blobHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blobPath = new Path();
    private final RectF dockRect = new RectF();

    private int accentPrimary = 0xFF5CC990;
    private int accentSecondary = 0xFF95D5B2;
    private int colorActiveText = 0xFF062016;
    private int colorInactive = 0xFF84A392;
    private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();

    private OnTabSelectedListener listener;

    public LiquidBottomNavView(@NonNull Context context) {
        this(context, null);
    }

    public LiquidBottomNavView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiquidBottomNavView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        colorInactive = ContextCompat.getColor(context, R.color.icon_inactive);
        colorActiveText = ContextCompat.getColor(context, R.color.text_on_accent);
        accentPrimary = ContextCompat.getColor(context, R.color.zing_purple);
        accentSecondary = ContextCompat.getColor(context, R.color.zing_gradient_end);

        dockBgPaint.setStyle(Paint.Style.FILL);
        dockBgPaint.setColor(0xD90A1D16);

        dockStrokePaint.setStyle(Paint.Style.STROKE);
        dockStrokePaint.setStrokeWidth(dp(1.1f));
        dockStrokePaint.setColor(0x4D9EC7AC);

        blobPaint.setStyle(Paint.Style.FILL);
        blobGlowPaint.setStyle(Paint.Style.FILL);
        blobHighlightPaint.setStyle(Paint.Style.STROKE);
        blobHighlightPaint.setStrokeWidth(dp(1f));
        blobHighlightPaint.setColor(0x55FFFFFF);

        dockRow = new LinearLayout(context);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);

        LayoutParams rowParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) dp(62));
        rowParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        addView(dockRow, rowParams);

        tabs[0] = createTab(R.id.nav_library, R.drawable.ic_library, R.string.nav_library, "nav_tab_library", 0);
        tabs[1] = createTab(R.id.nav_download, R.drawable.ic_download, R.string.nav_download, "nav_tab_download", 1);
        tabs[2] = createTab(R.id.nav_profile, R.drawable.ic_profile, R.string.nav_profile, "nav_tab_profile", 2);

        for (TabHolder tab : tabs) {
            dockRow.addView(tab.container);
        }

        applyStaticTabColors();
    }

    private TabHolder createTab(int id, @DrawableRes int iconRes, @StringRes int titleRes, String testTag, int index) {
        Context ctx = getContext();
        LinearLayout slot = new LinearLayout(ctx);
        slot.setId(id);
        slot.setTag(testTag);
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setGravity(Gravity.CENTER);
        slot.setClickable(true);
        slot.setFocusable(true);
        slot.setContentDescription(ctx.getString(titleRes));
        slot.setClipChildren(false);
        slot.setClipToPadding(false);

        int slotWidth = (int) dp(98);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(slotWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        slot.setLayoutParams(lp);

        ImageView icon = new ImageView(ctx);
        int iconSize = (int) dp(22);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        icon.setLayoutParams(iconLp);
        icon.setImageResource(iconRes);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        TextView label = new TextView(ctx);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelLp.topMargin = (int) dp(2);
        label.setLayoutParams(labelLp);
        label.setText(titleRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        label.setMaxLines(1);
        try {
            label.setTypeface(ResourcesCompat.getFont(ctx, R.font.plus_jakarta_sans));
        } catch (Exception ignored) {
        }

        slot.addView(icon);
        slot.addView(label);

        slot.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            selectIndex(index, true, true);
        });

        return new TabHolder(id, slot, icon, label);
    }

    public void setOnItemSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    public void setSelectedItemId(int itemId) {
        setSelectedItemId(itemId, true);
    }

    public void setSelectedItemId(int itemId, boolean animate) {
        int idx = indexForId(itemId);
        if (idx >= 0) {
            selectIndex(idx, animate, true);
        }
    }

    public void setAccentColors(int primaryAccent, int secondaryAccent) {
        this.accentPrimary = primaryAccent;
        this.accentSecondary = secondaryAccent;
        this.colorActiveText = ColorUtils.calculateLuminance(primaryAccent) > 0.42
                ? 0xFF062016
                : 0xFFFFFFFF;
        applyStaticTabColors();
        invalidate();
    }

    private int indexForId(int id) {
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i].id == id) return i;
        }
        return -1;
    }

    private void selectIndex(int targetIndex, boolean animate, boolean notifyListener) {
        if (targetIndex < 0 || targetIndex >= tabs.length) return;
        if (notifyListener && listener != null) {
            boolean accepted = listener.onTabSelected(tabs[targetIndex].id);
            if (!accepted) return;
        }
        if (targetIndex == selectedIndex && animFraction >= 0.99f) {
            applyStaticTabColors();
            return;
        }

        fromIndex = selectedIndex;
        toIndex = targetIndex;
        selectedIndex = targetIndex;

        if (liquidAnimator != null) {
            liquidAnimator.cancel();
        }

        if (!animate || getWidth() == 0) {
            animFraction = 1f;
            applyStaticTabColors();
            invalidate();
            return;
        }

        liquidAnimator = ValueAnimator.ofFloat(0f, 1f);
        liquidAnimator.setDuration(460L);
        liquidAnimator.setInterpolator(new DecelerateInterpolator(1.1f));
        liquidAnimator.addUpdateListener(anim -> {
            animFraction = (float) anim.getAnimatedValue();
            updateIconDeformations(animFraction);
            invalidate();
        });
        liquidAnimator.start();
    }

    private void updateIconDeformations(float t) {
        float wave = (float) Math.sin(Math.PI * t);
        float direction = toIndex >= fromIndex ? 1f : -1f;

        for (int i = 0; i < tabs.length; i++) {
            TabHolder tab = tabs[i];
            if (i == toIndex) {
                float bounce = t < 0.72f
                        ? (1f + 0.16f * wave)
                        : (1.06f + 0.08f * (float) Math.sin((t - 0.72f) / 0.28f * Math.PI));
                float stretchX = 1f + 0.22f * wave;
                float squashY = 1f - 0.14f * wave;
                float settle = t > 0.65f ? (t - 0.65f) / 0.35f : 0f;

                tab.icon.setScaleX(lerp(stretchX, 1.08f, settle) * (bounce / 1.06f));
                tab.icon.setScaleY(lerp(squashY, 1.08f, settle) * (bounce / 1.06f));
                tab.icon.setRotation(direction * 10f * wave * (1f - settle));
                tab.icon.setTranslationY(-dp(2.5f) * (0.4f + 0.6f * t));

                int tint = (int) argbEvaluator.evaluate(Math.min(1f, t * 1.25f), colorInactive, colorActiveText);
                tab.icon.setColorFilter(tint);
                tab.label.setTextColor(tint);
                tab.label.setAlpha(0.8f + 0.2f * t);
            } else if (i == fromIndex) {
                float squeeze = 1f - 0.15f * wave;
                tab.icon.setScaleX(squeeze);
                tab.icon.setScaleY(1f + 0.08f * wave * (1f - t));
                tab.icon.setRotation(-direction * 6f * wave * (1f - t));
                tab.icon.setTranslationY(-dp(2.5f) * (1f - t));

                int tint = (int) argbEvaluator.evaluate(Math.min(1f, t * 1.4f), colorActiveText, colorInactive);
                tab.icon.setColorFilter(tint);
                tab.label.setTextColor(tint);
                tab.label.setAlpha(0.86f);
            } else {
                tab.icon.setScaleX(1f);
                tab.icon.setScaleY(1f);
                tab.icon.setRotation(0f);
                tab.icon.setTranslationY(0f);
                tab.icon.setColorFilter(colorInactive);
                tab.label.setTextColor(colorInactive);
                tab.label.setAlpha(0.84f);
            }
        }
    }

    private void applyStaticTabColors() {
        for (int i = 0; i < tabs.length; i++) {
            TabHolder tab = tabs[i];
            boolean active = i == selectedIndex;
            int color = active ? colorActiveText : colorInactive;
            tab.icon.setColorFilter(color);
            tab.icon.setScaleX(active ? 1.08f : 1f);
            tab.icon.setScaleY(active ? 1.08f : 1f);
            tab.icon.setRotation(0f);
            tab.icon.setTranslationY(active ? -dp(1.5f) : 0f);
            tab.label.setTextColor(color);
            tab.label.setAlpha(active ? 1f : 0.84f);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxDockWidth = Math.min((int) dp(312), Math.max((int) dp(240), availableWidth - (int) dp(36)));
        int slotW = maxDockWidth / tabs.length;
        for (TabHolder tab : tabs) {
            ViewGroup.LayoutParams lp = tab.container.getLayoutParams();
            lp.width = slotW;
            tab.container.setLayoutParams(lp);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (dockRow.getWidth() > 0 && dockRow.getHeight() > 0) {
            float dockLeft = dockRow.getLeft() - dp(8);
            float dockTop = dockRow.getTop();
            float dockRight = dockRow.getRight() + dp(8);
            float dockBottom = dockRow.getBottom();
            float dockRadius = (dockBottom - dockTop) / 2f;

            dockRect.set(dockLeft, dockTop, dockRight, dockBottom);
            canvas.drawRoundRect(dockRect, dockRadius, dockRadius, dockBgPaint);
            canvas.drawRoundRect(dockRect, dockRadius, dockRadius, dockStrokePaint);

            drawLiquidBlob(canvas);
        }
        super.dispatchDraw(canvas);
    }

    private void drawLiquidBlob(Canvas canvas) {
        TabHolder fromTab = tabs[fromIndex];
        TabHolder toTab = tabs[toIndex];

        float fromCx = dockRow.getLeft() + fromTab.container.getLeft() + fromTab.container.getWidth() / 2f;
        float toCx = dockRow.getLeft() + toTab.container.getLeft() + toTab.container.getWidth() / 2f;

        float slotW = toTab.container.getWidth();
        float halfW = (slotW - dp(10)) / 2f;
        float cy = dockRow.getTop() + dockRow.getHeight() / 2f;
        float halfH = (dockRow.getHeight() - dp(12)) / 2f;

        float t = animFraction;
        float leadP = cubicOut(t);
        float trailP = cubicInOutDelayed(t);
        float midWave = (float) Math.sin(Math.PI * t);

        float leftEdge;
        float rightEdge;
        if (toCx >= fromCx) {
            leftEdge = lerp(fromCx, toCx, trailP) - halfW;
            rightEdge = lerp(fromCx, toCx, leadP) + halfW;
        } else {
            leftEdge = lerp(fromCx, toCx, leadP) - halfW;
            rightEdge = lerp(fromCx, toCx, trailP) + halfW;
        }

        // Elastic overshoot settle at destination
        float overshoot = t > 0.72f ? (float) Math.sin((t - 0.72f) / 0.28f * Math.PI) * dp(4f) : 0f;
        leftEdge -= overshoot * 0.5f;
        rightEdge += overshoot * 0.5f;

        // Fluid surface-tension pinch in the middle while stretched
        float neckPinch = dp(5.5f) * midWave;
        float midX = (leftEdge + rightEdge) / 2f;
        float topY = cy - halfH;
        float botY = cy + halfH;
        float corner = Math.min(halfH, (rightEdge - leftEdge) * 0.36f);

        blobPath.reset();
        blobPath.moveTo(leftEdge + corner, topY);
        if (Math.abs(rightEdge - leftEdge) > halfW * 2.15f && neckPinch > 0.5f) {
            blobPath.cubicTo(
                    lerp(leftEdge + corner, midX, 0.55f), topY + neckPinch * 0.85f,
                    lerp(rightEdge - corner, midX, 0.55f), topY + neckPinch * 0.85f,
                    rightEdge - corner, topY
            );
        } else {
            blobPath.lineTo(rightEdge - corner, topY);
        }

        // Right liquid cap
        blobPath.cubicTo(
                rightEdge + dp(2f) * midWave, topY,
                rightEdge + dp(2f) * midWave, botY,
                rightEdge - corner, botY
        );

        // Bottom curved edge
        if (Math.abs(rightEdge - leftEdge) > halfW * 2.15f && neckPinch > 0.5f) {
            blobPath.cubicTo(
                    lerp(rightEdge - corner, midX, 0.55f), botY - neckPinch * 0.85f,
                    lerp(leftEdge + corner, midX, 0.55f), botY - neckPinch * 0.85f,
                    leftEdge + corner, botY
            );
        } else {
            blobPath.lineTo(leftEdge + corner, botY);
        }

        // Left liquid cap
        blobPath.cubicTo(
                leftEdge - dp(2f) * midWave, botY,
                leftEdge - dp(2f) * midWave, topY,
                leftEdge + corner, topY
        );
        blobPath.close();

        blobPaint.setShader(new LinearGradient(
                leftEdge, topY, rightEdge, botY,
                accentPrimary, accentSecondary, Shader.TileMode.CLAMP
        ));

        blobGlowPaint.setColor(ColorUtils.setAlphaComponent(accentPrimary, 52));
        canvas.save();
        canvas.translate(0, dp(2));
        canvas.drawPath(blobPath, blobGlowPaint);
        canvas.restore();

        canvas.drawPath(blobPath, blobPaint);
        canvas.drawPath(blobPath, blobHighlightPaint);

        // Trailing micro-droplet while moving fast across tabs
        if (midWave > 0.2f && fromIndex != toIndex) {
            float dropCx = toCx >= fromCx ? leftEdge - dp(7) : rightEdge + dp(7);
            float dropR = dp(3.5f) * midWave;
            blobGlowPaint.setColor(ColorUtils.setAlphaComponent(accentSecondary, (int) (170 * midWave)));
            canvas.drawCircle(dropCx, cy, dropR, blobGlowPaint);
        }
    }

    private float cubicOut(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private float cubicInOutDelayed(float t) {
        float shifted = Math.max(0f, (t - 0.14f) / 0.86f);
        return shifted * shifted * (3f - 2f * shifted);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float dp(float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()
        );
    }
}
