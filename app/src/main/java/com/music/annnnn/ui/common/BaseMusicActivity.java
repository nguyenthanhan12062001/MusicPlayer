package com.music.annnnn.ui.common;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.music.annnnn.R;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.data.repository.MusicRepository;
import com.music.annnnn.player.PlaybackController;
import com.music.annnnn.util.ThemeStudioManager;

/**
 * Shared base Activity providing:
 * 1. Dark Botanical Glassmorphism backdrop binding + live Theme Studio updates
 * 2. Zero-permission Photo Picker (PickVisualMedia) for changing song artwork
 */
public abstract class BaseMusicActivity extends AppCompatActivity {

    private long pendingCoverSongId = -1L;

    private final ActivityResultLauncher<PickVisualMediaRequest> songCoverPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                long songId = pendingCoverSongId;
                pendingCoverSongId = -1L;
                if (uri == null || songId <= 0L) return;

                MusicRepository.getInstance(this).updateSongCoverFromUri(songId, uri, savedUrl ->
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (!TextUtils.isEmpty(savedUrl)) {
                                PlaybackController.getInstance(this).syncCurrentSongCover(songId, savedUrl);
                                Toast.makeText(this, R.string.toast_cover_updated, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, R.string.toast_cover_update_failed, Toast.LENGTH_SHORT).show();
                            }
                        }));
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void launchSongCoverPicker(SongEntity song) {
        if (song == null) return;
        pendingCoverSongId = song.id;
        songCoverPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    protected void bindBotanicalBackdrop(
            @Nullable ImageView foliageView,
            @Nullable View gradientOverlayView,
            @Nullable View ambientOrbView
    ) {
        ThemeStudioManager.applyBotanicalBackdrop(this, foliageView, gradientOverlayView, ambientOrbView);
        ThemeStudioManager.observeThemeVersion().observe(this, version -> {
            ThemeStudioManager.applyBotanicalBackdrop(this, foliageView, gradientOverlayView, ambientOrbView);
            onThemePresetUpdated(ThemeStudioManager.getActivePreset(this));
        });
    }

    protected void onThemePresetUpdated(ThemeStudioManager.ThemePreset preset) {
        // Optional hook for activities with custom accent widgets (e.g. LiquidBottomNavView)
    }
}
