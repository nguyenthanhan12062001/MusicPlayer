package com.music.annnnn.ui.detail;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.music.annnnn.R;
import com.music.annnnn.data.local.entity.AlbumEntity;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.player.PlaybackController;
import com.music.annnnn.ui.adapter.ArtistAdapter;
import com.music.annnnn.ui.adapter.HorizontalCardAdapter;
import com.music.annnnn.ui.adapter.SongAdapter;
import com.music.annnnn.ui.common.BaseMusicActivity;
import com.music.annnnn.ui.common.SongMenuHelper;
import com.music.annnnn.util.CoverLoader;
import com.music.annnnn.util.TimeFormatter;
import com.music.annnnn.viewmodel.DetailViewModel;

import java.util.ArrayList;
import java.util.List;

public class DetailActivity extends BaseMusicActivity {

    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_KEYWORD = "extra_keyword";

    public static final String TYPE_ALBUM = "ALBUM";
    public static final String TYPE_PLAYLIST = "PLAYLIST";
    public static final String TYPE_RADIO = "RADIO";
    public static final String TYPE_ARTIST = "ARTIST";
    public static final String TYPE_FAVORITES = "FAVORITES";
    public static final String TYPE_DOWNLOADED = "DOWNLOADED";
    public static final String TYPE_RECENT = "RECENT";
    public static final String TYPE_ALL_SONGS = "ALL_SONGS";
    public static final String TYPE_KEYWORD = "KEYWORD";
    public static final String TYPE_ALBUM_LIST = "ALBUM_LIST";
    public static final String TYPE_ARTIST_LIST = "ARTIST_LIST";

    public static void openSection(Context context, String type, String title, long id, String keyword) {
        Intent intent = new Intent(context, DetailActivity.class);
        intent.putExtra(EXTRA_TYPE, type);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_KEYWORD, keyword);
        context.startActivity(intent);
    }

    private DetailViewModel viewModel;
    private PlaybackController player;
    private SongAdapter songAdapter;

    private ShapeableImageView ivCover;
    private TextView tvToolbarTitle;
    private TextView tvTitle;
    private TextView tvCreator;
    private TextView tvDescription;
    private TextView tvMeta;
    private TextView tvEmpty;

    private String type;
    private String initialTitle;
    private long targetId;
    private String keyword;
    private boolean isUserPlaylist = false;
    private List<SongEntity> currentSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        bindBotanicalBackdrop(
                findViewById(R.id.bg_botanical_foliage),
                findViewById(R.id.bg_theme_gradient),
                findViewById(R.id.bg_ambient_orb)
        );

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detail_content_insets), (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        type = getIntent().getStringExtra(EXTRA_TYPE);
        initialTitle = getIntent().getStringExtra(EXTRA_TITLE);
        targetId = getIntent().getLongExtra(EXTRA_ID, 0L);
        keyword = getIntent().getStringExtra(EXTRA_KEYWORD);

        viewModel = new ViewModelProvider(this).get(DetailViewModel.class);
        player = PlaybackController.getInstance(this);

        ivCover = findViewById(R.id.iv_detail_cover);
        tvToolbarTitle = findViewById(R.id.tv_detail_toolbar_title);
        tvTitle = findViewById(R.id.tv_detail_title);
        tvCreator = findViewById(R.id.tv_detail_creator);
        tvDescription = findViewById(R.id.tv_detail_description);
        tvMeta = findViewById(R.id.tv_detail_meta);
        tvEmpty = findViewById(R.id.tv_detail_empty);

        tvToolbarTitle.setText(initialTitle);
        tvTitle.setText(initialTitle);

        findViewById(R.id.btn_detail_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_detail_sort).setOnClickListener(v -> showSortDialog());
        findViewById(R.id.btn_detail_more).setOnClickListener(v -> showDetailMoreDialog());

        findViewById(R.id.btn_detail_play).setOnClickListener(v -> {
            if (!currentSongs.isEmpty()) {
                player.playSongList(currentSongs, 0, tvTitle.getText().toString());
            } else {
                Toast.makeText(this, R.string.detail_empty_play_toast, Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btn_detail_shuffle).setOnClickListener(v -> {
            if (!currentSongs.isEmpty()) {
                player.playShuffled(currentSongs, tvTitle.getText().toString());
            }
        });
        findViewById(R.id.btn_detail_add_queue).setOnClickListener(v -> {
            if (!currentSongs.isEmpty()) {
                for (SongEntity s : currentSongs) {
                    player.addToQueue(s);
                }
                Toast.makeText(this, getString(R.string.detail_added_queue_toast, currentSongs.size()), Toast.LENGTH_SHORT).show();
            }
        });

        setupContent();
    }

    private void setupContent() {
        RecyclerView rv = findViewById(R.id.rv_detail_songs);
        rv.setLayoutManager(new LinearLayoutManager(this));

        if (TYPE_ALBUM_LIST.equals(type)) {
            tvCreator.setText(R.string.detail_album_creator);
            tvDescription.setText(R.string.detail_album_desc);
            CoverLoader.loadCover(ivCover, null, "bg_vinyl_disc");
            HorizontalCardAdapter albumGridAdapter = new HorizontalCardAdapter((item, pos) ->
                    openSection(this, TYPE_ALBUM, item.title, item.id, null));
            rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
            rv.setAdapter(albumGridAdapter);
            viewModel.getRepository().observeAllAlbums().observe(this, albums -> {
                List<HorizontalCardAdapter.CardItem> cards = new ArrayList<>();
                if (albums != null) {
                    for (AlbumEntity a : albums) {
                        cards.add(new HorizontalCardAdapter.CardItem(a.id, a.title, a.artist, a.coverResName, null, null));
                    }
                }
                albumGridAdapter.submitList(cards);
                tvMeta.setText(getString(R.string.count_albums_format, cards.size()));
            });
            return;
        }

        if (TYPE_ARTIST_LIST.equals(type)) {
            tvCreator.setText(R.string.detail_artist_list_creator);
            tvDescription.setText(R.string.detail_artist_list_desc);
            CoverLoader.loadCover(ivCover, null, "bg_vinyl_disc");
            ArtistAdapter artistAdapter = new ArtistAdapter(artist ->
                    openSection(this, TYPE_ARTIST, artist.artist, 0, artist.artist));
            rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
            rv.setAdapter(artistAdapter);
            viewModel.getRepository().observeArtists().observe(this, artists -> {
                artistAdapter.submitList(artists);
                tvMeta.setText(getString(R.string.count_artists_format, artists != null ? artists.size() : 0));
            });
            return;
        }

        songAdapter = new SongAdapter(new SongAdapter.Listener() {
            @Override
            public void onSongClick(SongEntity song, int position) {
                player.playSongList(songAdapter.getItems(), position, tvTitle.getText().toString());
            }

            @Override
            public void onFavoriteClick(SongEntity song) {
                viewModel.getRepository().toggleFavorite(song.id, !song.isFavorite);
                player.syncCurrentSongFavorite(song.id, !song.isFavorite);
            }

            @Override
            public void onMoreClick(SongEntity song) {
                SongMenuHelper.showSongMenu(DetailActivity.this, song);
            }
        });
        rv.setAdapter(songAdapter);

        bindMetadataAndSongs();

        viewModel.observeSortedSongs().observe(this, songs -> {
            currentSongs = songs != null ? songs : new ArrayList<>();
            songAdapter.submitList(currentSongs);
            tvEmpty.setVisibility(currentSongs.isEmpty() ? View.VISIBLE : View.GONE);
            long totalMs = 0;
            for (SongEntity s : currentSongs) totalMs += s.durationMs;
            tvMeta.setText(getString(R.string.detail_meta_songs_duration_format,
                    currentSongs.size(), TimeFormatter.formatDuration(totalMs)));
        });

        player.getCurrentSong().observe(this, song ->
                songAdapter.setActiveSongId(song != null ? song.id : -1L));
        player.getIsPlaying().observe(this, playing ->
                songAdapter.setPlaybackPlaying(Boolean.TRUE.equals(playing)));
    }

    private void bindMetadataAndSongs() {
        if (TYPE_ALBUM.equals(type)) {
            viewModel.observeAlbum(targetId).observe(this, album -> {
                if (album != null) {
                    tvTitle.setText(album.title);
                    tvToolbarTitle.setText(album.title);
                    tvCreator.setText(album.artist + " • " + album.releaseYear);
                    tvDescription.setText(album.description);
                    CoverLoader.loadCover(ivCover, null, album.coverResName);
                }
            });
            viewModel.bindSource(viewModel.getRepository().observeSongsByAlbum(targetId));
        } else if (TYPE_PLAYLIST.equals(type)) {
            viewModel.observePlaylist(targetId).observe(this, pl -> {
                if (pl != null) {
                    isUserPlaylist = pl.isUserCreated;
                    tvTitle.setText(pl.name);
                    tvToolbarTitle.setText(pl.name);
                    tvCreator.setText(getString(R.string.detail_playlist_creator_format, pl.creatorName));
                    tvDescription.setText(pl.description);
                    CoverLoader.loadCover(ivCover, null, pl.coverResName);
                }
            });
            viewModel.bindSource(viewModel.getRepository().observeSongsInPlaylist(targetId));
        } else if (TYPE_RADIO.equals(type)) {
            viewModel.observeRadio(targetId).observe(this, radio -> {
                if (radio != null) {
                    tvTitle.setText(radio.title);
                    tvToolbarTitle.setText(radio.title);
                    tvCreator.setText(radio.subtitle);
                    tvDescription.setText(radio.description);
                    CoverLoader.loadCover(ivCover, null, radio.coverResName);
                }
            });
            String kw = TextUtils.isEmpty(keyword) ? "" : keyword;
            viewModel.bindSource(viewModel.getRepository().observeSongsByKeyword(kw));
        } else if (TYPE_ARTIST.equals(type)) {
            tvCreator.setText(R.string.detail_artist_creator);
            tvDescription.setText(getString(R.string.detail_artist_desc_format, initialTitle));
            CoverLoader.loadCover(ivCover, null, "bg_vinyl_disc");
            viewModel.bindSource(viewModel.getRepository().observeSongsByArtist(keyword));
        } else if (TYPE_FAVORITES.equals(type)) {
            tvCreator.setText(R.string.detail_fav_creator);
            tvDescription.setText(R.string.detail_fav_desc);
            CoverLoader.loadCover(ivCover, null, "bg_vinyl_disc");
            viewModel.bindSource(viewModel.getRepository().observeFavoriteSongs());
        } else if (TYPE_DOWNLOADED.equals(type)) {
            tvCreator.setText(R.string.detail_dl_creator);
            tvDescription.setText(R.string.detail_dl_desc);
            CoverLoader.loadCover(ivCover, null, "bg_vinyl_disc");
            viewModel.bindSource(viewModel.getRepository().observeDownloadedSongs());
        } else if (TYPE_RECENT.equals(type)) {
            tvCreator.setText(R.string.detail_recent_creator);
            tvDescription.setText(R.string.detail_recent_desc);
            CoverLoader.loadCover(ivCover, null, "bg_vinyl_disc");
            viewModel.bindSource(viewModel.getRepository().observeRecentSongs());
        } else if (TYPE_KEYWORD.equals(type)) {
            tvCreator.setText(R.string.detail_keyword_creator);
            tvDescription.setText(getString(R.string.detail_keyword_desc_format, keyword));
            CoverLoader.loadCover(ivCover, null, "bg_vinyl_disc");
            viewModel.bindSource(viewModel.getRepository().observeSongsByKeyword(keyword));
        } else {
            tvCreator.setText(R.string.detail_all_creator);
            tvDescription.setText(R.string.detail_all_desc);
            CoverLoader.loadCover(ivCover, null, "bg_vinyl_disc");
            viewModel.bindSource(viewModel.getRepository().observeAllSongs());
        }
    }

    private void showSortDialog() {
        final String[] options = {
                getString(R.string.sort_default),
                getString(R.string.sort_title_az),
                getString(R.string.sort_artist_az),
                getString(R.string.sort_duration_desc)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sheet_sort_songs)
                .setSingleChoiceItems(options, viewModel.getSortMode(), (dialog, which) -> {
                    viewModel.setSortMode(which);
                    dialog.dismiss();
                })
                .show();
    }

    private void showDetailMoreDialog() {
        List<String> opts = new ArrayList<>();
        opts.add(getString(R.string.action_play_all));
        opts.add(getString(R.string.action_shuffle_play));
        opts.add(getString(R.string.action_add_queue));
        if (TYPE_PLAYLIST.equals(type) && isUserPlaylist) {
            opts.add(getString(R.string.detail_delete_playlist));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(tvTitle.getText())
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    if (which == 0 && !currentSongs.isEmpty()) {
                        player.playSongList(currentSongs, 0, tvTitle.getText().toString());
                    } else if (which == 1 && !currentSongs.isEmpty()) {
                        player.playShuffled(currentSongs, tvTitle.getText().toString());
                    } else if (which == 2 && !currentSongs.isEmpty()) {
                        for (SongEntity s : currentSongs) player.addToQueue(s);
                        Toast.makeText(this, R.string.toast_added_queue, Toast.LENGTH_SHORT).show();
                    } else if (which == 3 && TYPE_PLAYLIST.equals(type) && isUserPlaylist) {
                        viewModel.getRepository().deletePlaylist(targetId);
                        Toast.makeText(this, R.string.toast_deleted_playlist, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .show();
    }
}
