package com.music.annnnn.ui.main;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.music.annnnn.R;
import com.music.annnnn.data.local.LocalMusicScanner;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.player.PlaybackController;
import com.music.annnnn.ui.common.BaseMusicActivity;
import com.music.annnnn.ui.download.DownloadFragment;
import com.music.annnnn.ui.library.LibraryFragment;
import com.music.annnnn.ui.player.FullPlayerActivity;
import com.music.annnnn.ui.profile.ProfileFragment;
import com.music.annnnn.ui.widget.LiquidBottomNavView;
import com.music.annnnn.util.CoverLoader;
import com.music.annnnn.util.DiscSpinAnimator;
import com.music.annnnn.util.ThemeStudioManager;
import com.music.annnnn.viewmodel.MainViewModel;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseMusicActivity {

    public static final String EXTRA_OPEN_DOWNLOAD_TAB = "extra_open_download_tab";
    private static final String STATE_ACTIVE_TAB = "state_active_tab";

    public static Intent createOpenDownloadIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_OPEN_DOWNLOAD_TAB, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    private MainViewModel viewModel;
    private PlaybackController player;

    private LiquidBottomNavView bottomNav;
    private View miniPlayerContainer;
    private ShapeableImageView miniCover;
    private TextView miniTitle;
    private TextView miniArtist;
    private ImageButton miniBtnFavorite;
    private ImageButton miniBtnPlayPause;
    private ImageButton miniBtnNext;
    private LinearProgressIndicator miniProgress;
    private DiscSpinAnimator miniDiscAnimator;

    private int activeTabId = R.id.nav_library;

    private final ActivityResultLauncher<String[]> startupPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                if (LocalMusicScanner.hasAudioScanPermission(this)) {
                    viewModel.scanDeviceMusicOnceOnStartup();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        player = viewModel.getPlaybackController();

        View contentInsets = findViewById(R.id.main_content_insets);
        bottomNav = findViewById(R.id.bottom_navigation);
        miniPlayerContainer = findViewById(R.id.mini_player_container);

        bindBotanicalBackdrop(
                findViewById(R.id.bg_botanical_foliage),
                findViewById(R.id.bg_theme_gradient),
                findViewById(R.id.bg_ambient_orb)
        );
        ThemeStudioManager.ThemePreset initialPreset = ThemeStudioManager.getActivePreset(this);
        bottomNav.setAccentColors(initialPreset.accentColor, initialPreset.accentSecondaryColor);

        ViewCompat.setOnApplyWindowInsetsListener(contentInsets, (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, 0);
            bottomNav.setPadding(0, dpToPx(2), 0, sysBars.bottom + dpToPx(6));
            return WindowInsetsCompat.CONSUMED;
        });

        setupMiniPlayer();
        setupNavigation(savedInstanceState);
        observePlayback();
        requestStartupPermissionsAndAutoScan();
    }

    @Override
    protected void onThemePresetUpdated(ThemeStudioManager.ThemePreset preset) {
        if (bottomNav != null && preset != null) {
            bottomNav.setAccentColors(preset.accentColor, preset.accentSecondaryColor);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_DOWNLOAD_TAB, false)) {
            switchToTab(R.id.nav_download);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (miniDiscAnimator != null && player != null) {
            miniDiscAnimator.setPlaying(Boolean.TRUE.equals(player.getIsPlaying().getValue()));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (miniDiscAnimator != null) {
            miniDiscAnimator.setPlaying(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (miniDiscAnimator != null) {
            miniDiscAnimator.release();
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_ACTIVE_TAB, activeTabId);
    }

    private void setupMiniPlayer() {
        miniCover = findViewById(R.id.mini_cover);
        miniTitle = findViewById(R.id.mini_title);
        miniArtist = findViewById(R.id.mini_artist);
        miniBtnFavorite = findViewById(R.id.mini_btn_favorite);
        miniBtnPlayPause = findViewById(R.id.mini_btn_play_pause);
        miniBtnNext = findViewById(R.id.mini_btn_next);
        miniProgress = findViewById(R.id.mini_progress);
        miniDiscAnimator = new DiscSpinAnimator(miniCover, 9000L);

        miniPlayerContainer.setOnClickListener(v ->
                startActivity(new Intent(this, FullPlayerActivity.class))
        );

        miniBtnPlayPause.setOnClickListener(v -> player.togglePlayPause());
        miniBtnNext.setOnClickListener(v -> player.playNext());
        miniBtnFavorite.setOnClickListener(v -> {
            SongEntity current = player.getCurrentSong().getValue();
            if (current != null) {
                viewModel.toggleFavorite(current);
            }
        });
    }

    private void setupNavigation(Bundle savedInstanceState) {
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_DOWNLOAD_TAB, false)) {
            activeTabId = R.id.nav_download;
        } else if (savedInstanceState != null) {
            int saved = savedInstanceState.getInt(STATE_ACTIVE_TAB, R.id.nav_library);
            activeTabId = isValidTabId(saved) ? saved : R.id.nav_library;
        } else {
            activeTabId = R.id.nav_library;
        }

        bottomNav.setOnItemSelectedListener(this::showTabFragment);

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(activeTabId, false);
        } else {
            bottomNav.setSelectedItemId(activeTabId, false);
            showTabFragment(activeTabId);
        }
    }

    private boolean isValidTabId(int id) {
        return id == R.id.nav_library || id == R.id.nav_download || id == R.id.nav_profile;
    }

    private boolean showTabFragment(int menuItemId) {
        String targetTag = "tab_" + menuItemId;
        FragmentManager fm = getSupportFragmentManager();
        Fragment target = fm.findFragmentByTag(targetTag);
        if (target == null) {
            target = createFragmentForMenu(menuItemId);
            if (target == null) return false;
        }

        FragmentTransaction tx = fm.beginTransaction();
        tx.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        for (Fragment f : fm.getFragments()) {
            if (f != null && f != target && f.isAdded() && !f.isHidden()) {
                tx.hide(f);
            }
        }
        if (target.isAdded()) {
            tx.show(target);
        } else {
            tx.add(R.id.nav_host_container, target, targetTag);
        }
        tx.commit();
        activeTabId = menuItemId;
        return true;
    }

    public void switchToTab(int menuItemId) {
        if (bottomNav != null && isValidTabId(menuItemId)) {
            bottomNav.setSelectedItemId(menuItemId);
        }
    }

    private Fragment createFragmentForMenu(int id) {
        if (id == R.id.nav_library) return new LibraryFragment();
        if (id == R.id.nav_download) return new DownloadFragment();
        if (id == R.id.nav_profile) return new ProfileFragment();
        return null;
    }

    private void observePlayback() {
        player.getCurrentSong().observe(this, song -> {
            if (song == null) {
                miniPlayerContainer.setVisibility(View.GONE);
                if (miniDiscAnimator != null) miniDiscAnimator.setPlaying(false);
            } else {
                miniPlayerContainer.setVisibility(View.VISIBLE);
                miniTitle.setText(song.title);
                miniArtist.setText(song.artist);
                CoverLoader.loadSongArtwork(miniCover, song.coverUrl);
                updateFavoriteIcon(song.isFavorite);
                if (miniDiscAnimator != null) {
                    miniDiscAnimator.setPlaying(Boolean.TRUE.equals(player.getIsPlaying().getValue()));
                }
            }
        });

        player.getIsPlaying().observe(this, playing -> {
            boolean isPlay = Boolean.TRUE.equals(playing);
            miniBtnPlayPause.setImageResource(isPlay ? R.drawable.ic_pause : R.drawable.ic_play);
            if (miniDiscAnimator != null) {
                miniDiscAnimator.setPlaying(isPlay && player.getCurrentSong().getValue() != null);
            }
        });

        player.getPositionMs().observe(this, pos -> updateMiniProgress());
        player.getDurationMs().observe(this, dur -> updateMiniProgress());
    }

    private void updateFavoriteIcon(boolean fav) {
        miniBtnFavorite.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        miniBtnFavorite.setColorFilter(ContextCompat.getColor(
                this,
                fav ? R.color.zing_pink : R.color.icon_inactive
        ));
    }

    private void updateMiniProgress() {
        Long pos = player.getPositionMs().getValue();
        Long dur = player.getDurationMs().getValue();
        if (pos != null && dur != null && dur > 0) {
            int progress = (int) Math.min(1000, (pos * 1000L) / dur);
            miniProgress.setProgress(progress);
        } else {
            miniProgress.setProgress(0);
        }
    }

    private void requestStartupPermissionsAndAutoScan() {
        if (LocalMusicScanner.hasAudioScanPermission(this)) {
            viewModel.scanDeviceMusicOnceOnStartup();
        }

        List<String> toRequest = new ArrayList<>();
        String audioPerm = LocalMusicScanner.getRequiredAudioPermission();
        if (ContextCompat.checkSelfPermission(this, audioPerm) != PackageManager.PERMISSION_GRANTED) {
            toRequest.add(audioPerm);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            toRequest.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!toRequest.isEmpty()) {
            startupPermLauncher.launch(toRequest.toArray(new String[0]));
        }
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        ));
    }
}
