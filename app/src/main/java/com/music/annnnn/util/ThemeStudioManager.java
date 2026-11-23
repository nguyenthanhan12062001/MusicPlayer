package com.music.annnnn.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.music.annnnn.R;
import com.music.annnnn.data.local.PreferencesManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Manages Linear Gradient presets (6 Light presets and exactly 2 Dark presets: AMOLED & Charcoal Gray)
 * and Botanical / Glassmorphism backdrop rendering.
 */
public final class ThemeStudioManager {

    public static class ThemePreset {
        public final String id;
        @StringRes
        public final int nameRes;
        public final int startColor;
        public final int midColor;
        public final int endColor;
        public final int accentColor;
        public final int accentSecondaryColor;
        public final boolean isDark;

        public ThemePreset(String id, @StringRes int nameRes,
                           int startColor, int midColor, int endColor,
                           int accentColor, int accentSecondaryColor, boolean isDark) {
            this.id = id;
            this.nameRes = nameRes;
            this.startColor = startColor;
            this.midColor = midColor;
            this.endColor = endColor;
            this.accentColor = accentColor;
            this.accentSecondaryColor = accentSecondaryColor;
            this.isDark = isDark;
        }
    }

    private static final List<ThemePreset> PRESETS = Collections.unmodifiableList(Arrays.asList(
            // Light Presets (6 airy pastel gradients)
            new ThemePreset("air_mint", R.string.theme_preset_air_mint, 0xFFF2FBF6, 0xFFE0F4EA, 0xFFD0EBDD, 0xFF38A36F, 0xFF287A54, false),
            new ThemePreset("soft_lavender", R.string.theme_preset_lavender, 0xFFF8F6FC, 0xFFEFEBF8, 0xFFE5DFF3, 0xFF8B5CF6, 0xFF7C3AED, false),
            new ThemePreset("ocean_breeze", R.string.theme_preset_ocean_breeze, 0xFFF0F7FC, 0xFFE1EFEF, 0xFFD0E4F5, 0xFF0284C7, 0xFF0369A1, false),
            new ThemePreset("sunset_peach", R.string.theme_preset_sunset_peach, 0xFFFAF5F0, 0xFFF5EAE2, 0xFFEFE0D5, 0xFFF97316, 0xFFEA580C, false),
            new ThemePreset("vanilla_gold", R.string.theme_preset_vanilla_gold, 0xFFFAF7F2, 0xFFF2EAD8, 0xFFEAE0C8, 0xFFCA8A04, 0xFFA16207, false),
            new ThemePreset("rose_quartz", R.string.theme_preset_rose_quartz, 0xFFFAF0F5, 0xFFF5E0EC, 0xFFEFE0E9, 0xFFEC4899, 0xFFDB2777, false),

            // Dark Presets (Exactly 2: AMOLED Black & Charcoal Gray)
            new ThemePreset("amoled_black", R.string.theme_preset_amoled, 0xFF000000, 0xFF080808, 0xFF020202, 0xFF5CC990, 0xFF34D399, true),
            new ThemePreset("charcoal_gray", R.string.theme_preset_charcoal, 0xFF141414, 0xFF1E1E1E, 0xFF181818, 0xFF38BDF8, 0xFF818CF8, true)
    ));

    private static final MutableLiveData<Integer> THEME_VERSION = new MutableLiveData<>(0);

    private ThemeStudioManager() {}

    public static List<ThemePreset> getPresets() {
        return PRESETS;
    }

    public static List<ThemePreset> getLightPresets() {
        List<ThemePreset> list = new ArrayList<>();
        for (ThemePreset p : PRESETS) {
            if (!p.isDark) list.add(p);
        }
        return list;
    }

    public static List<ThemePreset> getDarkPresets() {
        List<ThemePreset> list = new ArrayList<>();
        for (ThemePreset p : PRESETS) {
            if (p.isDark) list.add(p);
        }
        return list;
    }

    public static ThemePreset getActivePreset(Context context) {
        PreferencesManager prefs = new PreferencesManager(context);
        return getPresetById(prefs.getThemePresetId());
    }

    public static ThemePreset getPresetById(String id) {
        if (id != null) {
            for (ThemePreset p : PRESETS) {
                if (p.id.equals(id)) {
                    return p;
                }
            }
        }
        return PRESETS.get(6); // default to amoled_black if null
    }

    public static void selectPreset(Context context, String presetId) {
        new PreferencesManager(context).setThemePresetId(presetId);
        notifyThemeChanged();
    }

    public static void setGlassIntensity(Context context, int intensity) {
        new PreferencesManager(context).setGlassIntensity(intensity);
        notifyThemeChanged();
    }

    public static void notifyThemeChanged() {
        Integer cur = THEME_VERSION.getValue();
        THEME_VERSION.postValue((cur != null ? cur : 0) + 1);
    }

    public static LiveData<Integer> observeThemeVersion() {
        return THEME_VERSION;
    }

    public static void applyBotanicalBackdrop(
            Context context,
            ImageView foliageView,
            View gradientOverlayView,
            View ambientOrbView
    ) {
        applyBotanicalBackdrop(context, foliageView, gradientOverlayView, ambientOrbView, null);
    }

    public static void applyBotanicalBackdrop(
            Context context,
            ImageView foliageView,
            View gradientOverlayView,
            View ambientOrbView,
            ThemePreset previewPreset
    ) {
        PreferencesManager prefs = new PreferencesManager(context);
        ThemePreset preset = previewPreset != null ? previewPreset : getPresetById(prefs.getThemePresetId());
        int intensity = prefs.getGlassIntensity(); // 15..95

        if (foliageView != null) {
            foliageView.setImageResource(R.drawable.img_botanical_bg);
            float foliageAlpha = preset.isDark ? (0.16f + (intensity / 100f) * 0.3f) : (0.08f + (intensity / 100f) * 0.15f);
            foliageView.setAlpha(foliageAlpha);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                foliageView.setRenderEffect(RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP));
            }
        }

        if (gradientOverlayView != null) {
            int topAlpha = Math.round((0.92f - (intensity / 100f) * 0.15f) * 255f);
            int midAlpha = Math.round((0.85f - (intensity / 100f) * 0.18f) * 255f);
            int botAlpha = Math.round((0.96f - (intensity / 100f) * 0.10f) * 255f);

            int c1 = ColorUtils.setAlphaComponent(preset.startColor, topAlpha);
            int c2 = ColorUtils.setAlphaComponent(preset.midColor, midAlpha);
            int c3 = ColorUtils.setAlphaComponent(preset.endColor, botAlpha);

            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{c1, c2, c3}
            );
            gradientOverlayView.setBackground(gd);
        }

        if (ambientOrbView != null) {
            int center = ColorUtils.setAlphaComponent(preset.accentColor, preset.isDark ? 50 : 30);
            int mid = ColorUtils.setAlphaComponent(preset.accentSecondaryColor, preset.isDark ? 20 : 12);
            int edge = Color.TRANSPARENT;
            GradientDrawable orb = new GradientDrawable();
            orb.setShape(GradientDrawable.OVAL);
            orb.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            orb.setGradientRadius(380f);
            orb.setColors(new int[]{center, mid, edge});
            ambientOrbView.setBackground(orb);
        }
    }

    public static GradientDrawable createSwatchDrawable(ThemePreset preset) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{preset.accentColor, preset.midColor, preset.accentSecondaryColor}
        );
        gd.setShape(GradientDrawable.OVAL);
        return gd;
    }
}
