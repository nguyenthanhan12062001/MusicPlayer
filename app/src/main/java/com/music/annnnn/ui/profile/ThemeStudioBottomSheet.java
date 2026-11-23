package com.music.annnnn.ui.profile;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.music.annnnn.R;
import com.music.annnnn.data.local.PreferencesManager;
import com.music.annnnn.util.ThemeStudioManager;

import java.util.List;

public class ThemeStudioBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "ThemeStudioBottomSheet";

    private ImageView ivPreviewFoliage;
    private View viewPreviewGradient;
    private TextView tvPreviewTitle;
    private TextView tvIntensityValue;
    private TextView tvPresetsHeader;
    private ThemePresetAdapter gridAdapter;
    private MaterialButton btnSaveTheme;

    private ThemeStudioManager.ThemePreset selectedPreset;
    private int selectedIntensity = 55;
    private boolean isDarkMode = false;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.bottom_sheet_theme_studio, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivPreviewFoliage = view.findViewById(R.id.iv_studio_preview_foliage);
        viewPreviewGradient = view.findViewById(R.id.view_studio_preview_gradient);
        tvPreviewTitle = view.findViewById(R.id.tv_studio_preview_title);
        tvIntensityValue = view.findViewById(R.id.tv_glass_intensity_value);
        tvPresetsHeader = view.findViewById(R.id.tv_presets_header);
        SeekBar seekBar = view.findViewById(R.id.seekbar_glass_intensity);
        RecyclerView rvGrid = view.findViewById(R.id.rv_studio_grid);
        btnSaveTheme = view.findViewById(R.id.btn_save_theme);

        PreferencesManager prefs = new PreferencesManager(requireContext());
        int themeMode = prefs.getThemeMode();
        if (themeMode == 2) {
            isDarkMode = true;
        } else if (themeMode == 1) {
            isDarkMode = false;
        } else {
            int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = (currentNightMode == Configuration.UI_MODE_NIGHT_YES);
        }

        selectedPreset = ThemeStudioManager.getActivePreset(requireContext());
        // Ensure active preset matches current mode if mismatched
        if (selectedPreset.isDark != isDarkMode) {
            List<ThemeStudioManager.ThemePreset> available = isDarkMode ? ThemeStudioManager.getDarkPresets() : ThemeStudioManager.getLightPresets();
            if (!available.isEmpty()) {
                selectedPreset = available.get(0);
            }
        }

        selectedIntensity = prefs.getGlassIntensity();

        rvGrid.setLayoutManager(new GridLayoutManager(requireContext(), isDarkMode ? 2 : 4));
        updateGridAdapter(rvGrid);

        if (tvPresetsHeader != null) {
            tvPresetsHeader.setText(isDarkMode ? "Presets Tối (Đen AMOLED & Xám Charcoal)" : "Presets Sáng (Dải màu Pastel)");
        }

        seekBar.setProgress(Math.max(0, Math.min(80, selectedIntensity - 15)));
        tvIntensityValue.setText(selectedIntensity + "%");

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                selectedIntensity = 15 + progress;
                tvIntensityValue.setText(selectedIntensity + "%");
                if (fromUser && isAdded()) {
                    refreshPreview();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        btnSaveTheme.setOnClickListener(v -> {
            if (selectedPreset != null) {
                ThemeStudioManager.selectPreset(requireContext(), selectedPreset.id);
                ThemeStudioManager.setGlassIntensity(requireContext(), selectedIntensity);
                Toast.makeText(requireContext(), "Đã lưu & áp dụng giao diện mới", Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });

        refreshPreview();
    }

    private void updateGridAdapter(RecyclerView rvGrid) {
        List<ThemeStudioManager.ThemePreset> list = isDarkMode ?
                ThemeStudioManager.getDarkPresets() : ThemeStudioManager.getLightPresets();

        gridAdapter = new ThemePresetAdapter(
                list,
                selectedPreset != null ? selectedPreset.id : "",
                true,
                preset -> {
                    selectedPreset = preset;
                    refreshPreview();
                }
        );
        rvGrid.setAdapter(gridAdapter);
    }

    private void refreshPreview() {
        if (!isAdded() || selectedPreset == null) return;
        tvPreviewTitle.setText(selectedPreset.nameRes);
        ThemeStudioManager.applyBotanicalBackdrop(
                requireContext(),
                ivPreviewFoliage,
                viewPreviewGradient,
                null,
                selectedPreset
        );
    }
}
