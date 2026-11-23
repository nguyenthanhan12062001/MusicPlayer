package com.music.annnnn.ui.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.music.annnnn.R;
import com.music.annnnn.data.local.AppDatabase;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.data.repository.MusicRepository;
import com.music.annnnn.player.PlaybackController;
import com.music.annnnn.ui.adapter.QueueAdapter;
import com.music.annnnn.ui.common.BaseMusicActivity;
import com.music.annnnn.ui.common.SongMenuHelper;
import com.music.annnnn.util.CoverLoader;
import com.music.annnnn.util.DiscSpinAnimator;
import com.music.annnnn.util.ThemeStudioManager;
import com.music.annnnn.util.TimeFormatter;

import java.io.File;

public class FullPlayerActivity extends BaseMusicActivity {

    private PlaybackController player;
    private MusicRepository repository;

    private View gradientBg;
    private TextView tvSource;
    private TextView tvTitle;
    private TextView tvArtist;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private SeekBar seekBar;
    private ImageButton btnFavorite;
    private ImageButton btnShuffle;
    private FloatingActionButton btnPlayPause;
    private ImageButton btnRepeat;

    private ImageView ivArtworkPage;
    private DiscSpinAnimator fullDiscAnimator;
    private TextView tvLyricsPage;
    private QueueAdapter queueAdapter;

    private boolean userSeeking = false;
    private String lastGradientKey = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_player);

        bindBotanicalBackdrop(
                findViewById(R.id.bg_botanical_foliage),
                findViewById(R.id.bg_theme_gradient),
                findViewById(R.id.bg_ambient_orb)
        );

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.player_content_insets), (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        player = PlaybackController.getInstance(this);
        repository = MusicRepository.getInstance(this);

        gradientBg = findViewById(R.id.player_gradient_bg);
        tvSource = findViewById(R.id.tv_player_source);
        tvTitle = findViewById(R.id.tv_player_title);
        tvArtist = findViewById(R.id.tv_player_artist);
        tvCurrentTime = findViewById(R.id.tv_player_current_time);
        tvTotalTime = findViewById(R.id.tv_player_total_time);
        seekBar = findViewById(R.id.seekbar_player);
        btnFavorite = findViewById(R.id.btn_player_favorite);
        btnShuffle = findViewById(R.id.btn_player_shuffle);
        btnPlayPause = findViewById(R.id.btn_player_play_pause);
        btnRepeat = findViewById(R.id.btn_player_repeat);

        setupViewPager();
        setupControls();
        observeState();
    }

    @Override
    protected void onThemePresetUpdated(ThemeStudioManager.ThemePreset preset) {
        lastGradientKey = "";
        SongEntity cur = player != null ? player.getCurrentSong().getValue() : null;
        if (cur != null) {
            applyDynamicGradient(cur);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (fullDiscAnimator != null && player != null) {
            fullDiscAnimator.setPlaying(Boolean.TRUE.equals(player.getIsPlaying().getValue()));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (fullDiscAnimator != null) {
            fullDiscAnimator.setPlaying(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (fullDiscAnimator != null) {
            fullDiscAnimator.release();
        }
        super.onDestroy();
    }

    private void setupViewPager() {
        ViewPager2 viewPager = findViewById(R.id.vp_player_pages);
        TabLayout tabs = findViewById(R.id.tab_player_pages);

        queueAdapter = new QueueAdapter(new QueueAdapter.Listener() {
            @Override
            public void onQueueItemClick(int position) {
                player.playAtIndex(position);
            }

            @Override
            public void onQueueRemoveClick(int position) {
                player.removeFromQueue(position);
            }
        });

        viewPager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LayoutInflater inf = LayoutInflater.from(parent.getContext());
                if (viewType == 0) {
                    View v = inf.inflate(R.layout.page_player_artwork, parent, false);
                    ivArtworkPage = v.findViewById(R.id.iv_full_player_cover);
                    if (fullDiscAnimator != null) {
                        fullDiscAnimator.release();
                    }
                    fullDiscAnimator = new DiscSpinAnimator(ivArtworkPage, 14000L);
                    SongEntity cur = player.getCurrentSong().getValue();
                    CoverLoader.loadSongArtwork(ivArtworkPage, cur != null ? cur.coverUrl : null);
                    fullDiscAnimator.setPlaying(Boolean.TRUE.equals(player.getIsPlaying().getValue()));
                    return new RecyclerView.ViewHolder(v) {};
                } else if (viewType == 1) {
                    View v = inf.inflate(R.layout.page_player_lyrics, parent, false);
                    tvLyricsPage = v.findViewById(R.id.tv_full_player_lyrics);
                    SongEntity cur = player.getCurrentSong().getValue();
                    if (cur != null) {
                        tvLyricsPage.setText(TextUtils.isEmpty(cur.lyrics) ? getString(R.string.player_no_lyrics) : cur.lyrics);
                    }
                    return new RecyclerView.ViewHolder(v) {};
                } else {
                    View v = inf.inflate(R.layout.page_player_queue, parent, false);
                    RecyclerView rvQueue = v.findViewById(R.id.rv_full_player_queue);
                    rvQueue.setLayoutManager(new LinearLayoutManager(FullPlayerActivity.this));
                    rvQueue.setAdapter(queueAdapter);
                    return new RecyclerView.ViewHolder(v) {};
                }
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        new TabLayoutMediator(tabs, viewPager, (tab, pos) -> {
            if (pos == 0) tab.setText(R.string.player_tab_cover);
            else if (pos == 1) tab.setText(R.string.player_tab_lyrics);
            else tab.setText(R.string.player_tab_queue);
        }).attach();
    }

    private void setupControls() {
        findViewById(R.id.btn_player_collapse).setOnClickListener(v -> finish());
        findViewById(R.id.btn_player_more).setOnClickListener(v -> {
            SongEntity cur = player.getCurrentSong().getValue();
            if (cur != null) {
                SongMenuHelper.showSongMenu(this, cur);
            }
        });

        btnFavorite.setOnClickListener(v -> {
            SongEntity cur = player.getCurrentSong().getValue();
            if (cur != null) {
                boolean next = !cur.isFavorite;
                repository.toggleFavorite(cur.id, next);
                player.syncCurrentSongFavorite(cur.id, next);
            }
        });

        btnPlayPause.setOnClickListener(v -> player.togglePlayPause());
        findViewById(R.id.btn_player_next).setOnClickListener(v -> player.playNext());
        findViewById(R.id.btn_player_prev).setOnClickListener(v -> player.playPrevious());
        btnShuffle.setOnClickListener(v -> player.toggleShuffle());
        btnRepeat.setOnClickListener(v -> player.cycleRepeatMode());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(TimeFormatter.formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                userSeeking = false;
                player.seekTo(sb.getProgress());
            }
        });
    }

    private void observeState() {
        player.getSourceTitle().observe(this, src -> tvSource.setText(src));

        player.getCurrentSong().observe(this, song -> {
            if (song == null) {
                finish();
                return;
            }
            tvTitle.setText(song.title);
            tvArtist.setText(song.artist);
            updateFavoriteButton(song.isFavorite);
            if (ivArtworkPage != null) {
                CoverLoader.loadSongArtwork(ivArtworkPage, song.coverUrl);
            }
            if (tvLyricsPage != null) {
                tvLyricsPage.setText(TextUtils.isEmpty(song.lyrics) ? getString(R.string.player_no_lyrics) : song.lyrics);
            }
            applyDynamicGradient(song);
        });

        player.getIsPlaying().observe(this, playing -> {
            boolean isPlay = Boolean.TRUE.equals(playing);
            btnPlayPause.setImageResource(isPlay ? R.drawable.ic_pause : R.drawable.ic_play);
            if (fullDiscAnimator != null) {
                fullDiscAnimator.setPlaying(isPlay);
            }
        });

        player.getDurationMs().observe(this, dur -> {
            long d = dur != null ? dur : 0L;
            seekBar.setMax((int) Math.max(1L, d));
            tvTotalTime.setText(TimeFormatter.formatDuration(d));
        });

        player.getPositionMs().observe(this, pos -> {
            if (!userSeeking) {
                long p = pos != null ? pos : 0L;
                seekBar.setProgress((int) p);
                tvCurrentTime.setText(TimeFormatter.formatDuration(p));
            }
        });

        player.getShuffleEnabled().observe(this, enabled -> {
            boolean on = Boolean.TRUE.equals(enabled);
            btnShuffle.setColorFilter(ContextCompat.getColor(
                    this, on ? R.color.zing_purple : R.color.icon_inactive));
        });

        player.getRepeatMode().observe(this, mode -> {
            int m = mode != null ? mode : PlaybackController.REPEAT_NONE;
            btnRepeat.setImageResource(m == PlaybackController.REPEAT_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
            btnRepeat.setColorFilter(ContextCompat.getColor(
                    this, m == PlaybackController.REPEAT_NONE ? R.color.icon_inactive : R.color.zing_purple));
        });

        player.getQueue().observe(this, q -> {
            Integer idx = player.getCurrentIndex().getValue();
            queueAdapter.submitQueue(q, idx != null ? idx : -1);
        });
        player.getCurrentIndex().observe(this, idx ->
                queueAdapter.submitQueue(player.getQueue().getValue(), idx != null ? idx : -1));
    }

    private void updateFavoriteButton(boolean isFav) {
        btnFavorite.setImageResource(isFav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        btnFavorite.setColorFilter(ContextCompat.getColor(
                this, isFav ? R.color.zing_pink : R.color.icon_inactive));
    }

    private void applyDynamicGradient(SongEntity song) {
        if (song == null) return;
        final String key = !TextUtils.isEmpty(song.coverUrl)
                ? song.coverUrl
                : ThemeStudioManager.getActivePreset(this).id;
        if (TextUtils.equals(key, lastGradientKey)) {
            return;
        }
        lastGradientKey = key;

        final int presetAccent = ThemeStudioManager.getActivePreset(this).accentColor;
        final String coverPath = song.coverUrl;

        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            int glowColor = presetAccent;
            if (!TextUtils.isEmpty(coverPath) && coverPath.startsWith("/")) {
                File f = new File(coverPath);
                if (f.exists()) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 4;
                    Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
                    if (bmp != null) {
                        Palette palette = Palette.from(bmp).maximumColorCount(8).generate();
                        glowColor = palette.getVibrantColor(palette.getDominantColor(presetAccent));
                        bmp.recycle();
                    }
                }
            }
            final int topGlow = ColorUtils.setAlphaComponent(glowColor, 95);
            final int midGlow = ColorUtils.setAlphaComponent(glowColor, 26);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !TextUtils.equals(key, lastGradientKey)) return;
                GradientDrawable gd = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{topGlow, midGlow, Color.TRANSPARENT}
                );
                gradientBg.setBackground(gd);
            });
        });
    }
}
