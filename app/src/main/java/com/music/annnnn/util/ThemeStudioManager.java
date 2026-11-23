package com.music.annnnn.util;

import android.content.Context;
import android.content.res.Configuration;
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
 * Manages Soft Neumorphic & Minimalist UI theme presets (6 Light soft neumorphic presets and 6 Soft Dark presets).
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
            // Light Presets (6 Soft Neumorphic light pastel gradients matching sample reference image)
            new ThemePreset("neumorphic_classic", R.string.theme_preset_classic, 0xFFE8EDF5, 0xFFEEF2F7, 0xFFF4F7FB, 0xFF6366F1, 0xFF4F46E5, false),
            new ThemePreset("air_mint", R.string.theme_preset_air_mint, 0xFFEAF4EE, 0xFFF0FAF3, 0xFFF6FFF8, 0xFF10B981, 0xFF059669, false),
            new ThemePreset("soft_lavender", R.string.theme_preset_lavender, 0xFFF0EEF8, 0xFFF5F3FC, 0xFFFAF8FF, 0xFF8B5CF6, 0xFF7C3AED, false),
            new ThemePreset("ocean_breeze", R.string.theme_preset_ocean_breeze, 0xFFEBF3F9, 0xFFF0F7FC, 0xFFF6FAFE, 0xFF0284C7, 0xFF0369A1, false),
            new ThemePreset("sunset_peach", R.string.theme_preset_sunset_peach, 0xFFF9F2EE, 0xFFFCF6F2, 0xFFFEFAF6, 0xFFF97316, 0xFFEA580C, false),
            new ThemePreset("rose_quartz", R.string.theme_preset_rose_quartz, 0xFFF9EEF4, 0xFFFCF2F7, 0xFFFEF6FA, 0xFFEC4899, 0xFFDB2777, false),

            // Dark Presets (6 soft dark, deep, non-gloomy presets with soft neumorphic depth)
            new ThemePreset("midnight_obsidian", R.string.theme_preset_midnight, 0xFF121824, 0xFF1A2332, 0xFF0F172A, 0xFF38BDF8, 0xFF0284C7, true),
            new ThemePreset("charcoal_slate", R.string.theme_preset_charcoal, 0xFF161B22, 0xFF21262D, 0xFF111418, 0xFF5CC990, 0xFF38A36F, true),
            new ThemePreset("deep_espresso", R.string.theme_preset_espresso, 0xFF1C1614, 0xFF2A211E, 0xFF16110F, 0xFFFB923C, 0xFFEA580C, true),
            new ThemePreset("mystic_indigo", R.string.theme_preset_indigo, 0xFF161625, 0xFF22223B, 0xFF10101D, 0xFFA78BFA, 0xFF7C3AED, true),
            new ThemePreset("emerald_night", R.string.theme_preset_emerald, 0xFF101B15, 0xFF182A20, 0xFF0C1410, 0xFF4ADE80, 0xFF22C55E, true),
            new ThemePreset("bordeaux_wine", R.string.theme_preset_bordeaux, 0xFF1C1417, 0xFF2B1F24, 0xFF150F11, 0xFFF43F5E, 0xFFE11D48, true)
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

    public static boolean isDarkModeActive(Context context) {
        PreferencesManager prefs = new PreferencesManager(context);
        int themeMode = prefs.getThemeMode();
        if (themeMode == 2) return true;
        if (themeMode == 1) return false;
        int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static ThemePreset getActivePreset(Context context) {
        PreferencesManager prefs = new PreferencesManager(context);
        ThemePreset preset = getPresetById(prefs.getThemePresetId());
        boolean isDark = isDarkModeActive(context);
        if (preset.isDark != isDark) {
            List<ThemePreset> available = isDark ? getDarkPresets() : getLightPresets();
            if (!available.isEmpty()) {
                return available.get(0);
            }
        }
        return preset;
    }

    public static ThemePreset getPresetById(String id) {
        if (id != null) {
            for (ThemePreset p : PRESETS) {
                if (p.id.equals(id)) {
                    return p;
                }
            }
        }
        return PRESETS.get(0);
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
        ThemePreset preset = previewPreset != null ? previewPreset : getActivePreset(context);
        int intensity = prefs.getGlassIntensity(); // 15..95

        if (foliageView != null) {
            foliageView.setImageResource(R.drawable.img_botanical_bg);
            float foliageAlpha = preset.isDark ? (0.16f + (intensity / 100f) * 0.3f) : (0.06f + (intensity / 100f) * 0.12f);
            foliageView.setAlpha(foliageAlpha);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                foliageView.setRenderEffect(RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP));
            }
        }

        if (gradientOverlayView != null) {
            int topAlpha = Math.round((0.95f - (intensity / 100f) * 0.12f) * 255f);
            int midAlpha = Math.round((0.90f - (intensity / 100f) * 0.15f) * 255f);
            int botAlpha = Math.round((0.98f - (intensity / 100f) * 0.08f) * 255f);

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
            int center = ColorUtils.setAlphaComponent(preset.accentColor, preset.isDark ? 50 : 25);
            int mid = ColorUtils.setAlphaComponent(preset.accentSecondaryColor, preset.isDark ? 20 : 10);
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
