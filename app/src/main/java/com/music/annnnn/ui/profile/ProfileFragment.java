package com.music.annnnn.ui.profile;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.music.annnnn.R;
import com.music.annnnn.data.local.AppDatabase;
import com.music.annnnn.data.local.PreferencesManager;
import com.music.annnnn.ui.detail.DetailActivity;
import com.music.annnnn.ui.main.MainActivity;
import com.music.annnnn.util.ThemeStudioManager;
import com.music.annnnn.util.TimeFormatter;
import com.music.annnnn.viewmodel.MainViewModel;

import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private MainViewModel viewModel;
    private PreferencesManager prefs;

    private TextView tvName;
    private TextView tvStatPlays;
    private TextView tvStatTime;
    private TextView tvStatDownloaded;
    private TextView tvPlayerSummary;
    private TextView tvAudioSummary;
    private TextView tvStorageSummary;
    private TextView tvLanguageSummary;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        prefs = viewModel.getPreferences();

        tvName = view.findViewById(R.id.tv_profile_name);
        tvStatPlays = view.findViewById(R.id.tv_stat_plays);
        tvStatTime = view.findViewById(R.id.tv_stat_time);
        tvStatDownloaded = view.findViewById(R.id.tv_stat_downloaded);
        tvPlayerSummary = view.findViewById(R.id.tv_player_settings_summary);
        tvAudioSummary = view.findViewById(R.id.tv_audio_settings_summary);
        tvStorageSummary = view.findViewById(R.id.tv_storage_summary);
        tvLanguageSummary = view.findViewById(R.id.tv_language_summary);

        if (prefs.hasCustomUserName() && !TextUtils.isEmpty(prefs.getUserName())) {
            tvName.setText(prefs.getUserName());
        } else {
            tvName.setText(R.string.profile_default_name);
        }
        view.findViewById(R.id.card_user_profile).setOnClickListener(v -> showEditNameDialog());

        view.findViewById(R.id.row_profile_playlists).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToTab(R.id.nav_library);
            }
        });
        view.findViewById(R.id.row_profile_favorites).setOnClickListener(v ->
                DetailActivity.openSection(requireContext(), DetailActivity.TYPE_FAVORITES, getString(R.string.library_favorites), 0, null));
        view.findViewById(R.id.row_profile_history).setOnClickListener(v ->
                DetailActivity.openSection(requireContext(), DetailActivity.TYPE_RECENT, getString(R.string.library_recent), 0, null));

        setupThemeModeToggle(view);
        view.findViewById(R.id.row_player_settings).setOnClickListener(v -> showPlayerSettingsDialog());
        view.findViewById(R.id.row_audio_settings).setOnClickListener(v -> showAudioSettingsDialog());
        view.findViewById(R.id.row_storage_manage).setOnClickListener(v -> showStorageDialog());
        view.findViewById(R.id.row_language_settings).setOnClickListener(v -> showLanguageDialog());
        view.findViewById(R.id.row_about_app).setOnClickListener(v -> showAboutDialog());

        refreshSummaries();
        observeStats();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (tvStatTime != null && prefs != null) {
            tvStatTime.setText(TimeFormatter.formatListeningTime(requireContext(), prefs.getTotalListenSeconds()));
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && isAdded() && prefs != null) {
            tvStatTime.setText(TimeFormatter.formatListeningTime(requireContext(), prefs.getTotalListenSeconds()));
            refreshStorageSummaryAsync();
        }
    }

    private void setupThemeModeToggle(View view) {
        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggle_theme_group);
        if (toggleGroup == null) return;

        int mode = prefs.getThemeMode();
        if (mode == 1) {
            toggleGroup.check(R.id.btn_theme_light);
        } else if (mode == 2) {
            toggleGroup.check(R.id.btn_theme_dark);
        } else {
            toggleGroup.check(R.id.btn_theme_system);
        }

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int newMode = 0;
            if (checkedId == R.id.btn_theme_light) {
                newMode = 1;
            } else if (checkedId == R.id.btn_theme_dark) {
                newMode = 2;
            } else if (checkedId == R.id.btn_theme_system) {
                newMode = 0;
            }
            if (prefs.getThemeMode() != newMode) {
                prefs.setThemeMode(newMode);
                ThemeStudioManager.notifyThemeChanged();
            }
        });
    }

    private void observeStats() {
        viewModel.observeTotalPlayCount().observe(getViewLifecycleOwner(), count ->
                tvStatPlays.setText(String.valueOf(count != null ? count : 0)));
        viewModel.observeDownloadedCount().observe(getViewLifecycleOwner(), count ->
                tvStatDownloaded.setText(String.valueOf(count != null ? count : 0)));
        tvStatTime.setText(TimeFormatter.formatListeningTime(requireContext(), prefs.getTotalListenSeconds()));
    }

    private void refreshSummaries() {
        String crossfadeText = getString(prefs.isCrossfadeEnabled()
                ? R.string.profile_crossfade_on
                : R.string.profile_crossfade_off);
        tvPlayerSummary.setText(getString(
                R.string.profile_player_summary_format,
                String.valueOf(prefs.getPlaybackSpeed()),
                crossfadeText
        ));
        String eqDisplay = resolveEqDisplayName(prefs.getEqPreset());
        String bassState = getString(prefs.isBassBoostEnabled()
                ? R.string.profile_state_on
                : R.string.profile_state_off);
        tvAudioSummary.setText(getString(R.string.profile_audio_summary_format, eqDisplay, bassState));
        tvLanguageSummary.setText(resolveLanguageDisplayName(prefs.getEffectiveLanguageTag()));
        refreshStorageSummaryAsync();
    }

    private void refreshStorageSummaryAsync() {
        if (TextUtils.isEmpty(tvStorageSummary.getText())) {
            tvStorageSummary.setText(R.string.profile_calculating_storage);
        }
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            long bytes = viewModel.getRepository().calculateUsedStorageBytes();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (isAdded() && tvStorageSummary != null) {
                    tvStorageSummary.setText(getString(R.string.profile_storage_summary_format, TimeFormatter.formatBytes(bytes)));
                }
            });
        });
    }

    private String resolveEqDisplayName(String presetKeyOrLegacy) {
        if ("pop".equalsIgnoreCase(presetKeyOrLegacy) || "Pop".equalsIgnoreCase(presetKeyOrLegacy)) {
            return getString(R.string.eq_pop);
        }
        if ("bass".equalsIgnoreCase(presetKeyOrLegacy) || "Bass mạnh".equalsIgnoreCase(presetKeyOrLegacy)) {
            return getString(R.string.eq_bass);
        }
        if ("acoustic".equalsIgnoreCase(presetKeyOrLegacy) || "Acoustic".equalsIgnoreCase(presetKeyOrLegacy)) {
            return getString(R.string.eq_acoustic);
        }
        return getString(R.string.eq_balanced);
    }

    private void showEditNameDialog() {
        EditText input = new EditText(requireContext());
        input.setText(tvName.getText());
        input.setPadding(48, 32, 48, 24);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_edit_name_title)
                .setView(input)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(name)) {
                        prefs.setUserName(name);
                        tvName.setText(name);
                    }
                })
                .show();
    }

    private void showPlayerSettingsDialog() {
        final String[] options = {
                getString(R.string.player_speed_075),
                getString(R.string.player_speed_100),
                getString(R.string.player_speed_125),
                getString(R.string.player_speed_150),
                getString(prefs.isCrossfadeEnabled()
                        ? R.string.player_crossfade_disable
                        : R.string.player_crossfade_enable)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_player_settings)
                .setItems(options, (d, which) -> {
                    if (which == 0) prefs.setPlaybackSpeed(0.75f);
                    else if (which == 1) prefs.setPlaybackSpeed(1.0f);
                    else if (which == 2) prefs.setPlaybackSpeed(1.25f);
                    else if (which == 3) prefs.setPlaybackSpeed(1.5f);
                    else prefs.setCrossfadeEnabled(!prefs.isCrossfadeEnabled());
                    viewModel.getPlaybackController().applyAudioSettings();
                    refreshSummaries();
                    Toast.makeText(requireContext(), R.string.toast_player_settings_updated, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showAudioSettingsDialog() {
        final String[] keys = {"balanced", "pop", "bass", "acoustic"};
        final String[] presets = {
                getString(R.string.eq_balanced),
                getString(R.string.eq_pop),
                getString(R.string.eq_bass),
                getString(R.string.eq_acoustic),
                getString(prefs.isBassBoostEnabled()
                        ? R.string.audio_bass_disable
                        : R.string.audio_bass_enable)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_audio_settings)
                .setItems(presets, (d, which) -> {
                    if (which < 4) {
                        prefs.setEqPreset(keys[which]);
                    } else {
                        prefs.setBassBoostEnabled(!prefs.isBassBoostEnabled());
                    }
                    viewModel.getPlaybackController().applyAudioSettings();
                    refreshSummaries();
                    Toast.makeText(requireContext(), R.string.toast_audio_settings_applied, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showStorageDialog() {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            long bytes = viewModel.getRepository().calculateUsedStorageBytes();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.profile_storage)
                        .setMessage(getString(R.string.storage_dialog_message_format, TimeFormatter.formatBytes(bytes)))
                        .setNegativeButton(R.string.action_close, null)
                        .setPositiveButton(R.string.storage_clear_cache, (d, w) -> {
                            AppDatabase.DATABASE_EXECUTOR.execute(() -> {
                                viewModel.getRepository().clearCacheFiles();
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    if (isAdded()) {
                                        refreshStorageSummaryAsync();
                                        Toast.makeText(requireContext(), R.string.toast_cache_cleared, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            });
                        })
                        .show();
            });
        });
    }

    private void showLanguageDialog() {
        final String[] tags = {"", "vi", "en", "id", "ja", "ko", "zh-CN", "fr", "es"};
        final String[] labels = {
                getString(R.string.language_system) + " (" + getSystemLanguageDisplayName() + ")",
                "Tiếng Việt",
                "English",
                "Bahasa Indonesia",
                "日本語",
                "한국어",
                "中文 (简体)",
                "Français",
                "Español"
        };
        String currentTag = prefs.getEffectiveLanguageTag();
        int checkedItem = 0;
        for (int i = 0; i < tags.length; i++) {
            if (tags[i].equalsIgnoreCase(currentTag)) {
                checkedItem = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_select_language)
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                    dialog.dismiss();
                    String chosenTag = tags[which];
                    if (!chosenTag.equalsIgnoreCase(currentTag)) {
                        prefs.setLanguageTag(chosenTag);
                        tvLanguageSummary.setText(resolveLanguageDisplayName(chosenTag));
                        Toast.makeText(requireContext(), R.string.language_changed_toast, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private String resolveLanguageDisplayName(String tag) {
        String normalized = PreferencesManager.normalizeLanguageTag(tag);
        if ("vi".equals(normalized)) return "Tiếng Việt";
        if ("en".equals(normalized)) return "English";
        if ("id".equals(normalized)) return "Bahasa Indonesia";
        if ("ja".equals(normalized)) return "日本語";
        if ("ko".equals(normalized)) return "한국어";
        if ("zh-CN".equals(normalized)) return "中文 (简体)";
        if ("fr".equals(normalized)) return "Français";
        if ("es".equals(normalized)) return "Español";
        return getString(R.string.language_system) + " (" + getSystemLanguageDisplayName() + ")";
    }

    private String getSystemLanguageDisplayName() {
        Locale sys = android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0);
        if (sys == null) sys = Locale.getDefault();
        String norm = PreferencesManager.normalizeLanguageTag(sys.toLanguageTag());
        if ("vi".equals(norm)) return "Tiếng Việt";
        if ("en".equals(norm)) return "English";
        if ("id".equals(norm)) return "Bahasa Indonesia";
        if ("ja".equals(norm)) return "日本語";
        if ("ko".equals(norm)) return "한국어";
        if ("zh-CN".equals(norm)) return "中文 (简体)";
        if ("fr".equals(norm)) return "Français";
        if ("es".equals(norm)) return "Español";
        String display = sys.getDisplayLanguage(sys);
        return TextUtils.isEmpty(display) ? sys.getLanguage() : display;
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_about)
                .setMessage(R.string.about_app_message)
                .setPositiveButton(R.string.action_close, null)
                .show();
    }
}
