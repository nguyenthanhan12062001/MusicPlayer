package com.music.annnnn.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.music.annnnn.R;
import com.music.annnnn.data.local.entity.AlbumEntity;
import com.music.annnnn.data.local.entity.ArtistSummary;
import com.music.annnnn.data.local.entity.PlaylistEntity;
import com.music.annnnn.data.local.entity.SearchHistoryEntity;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.player.PlaybackController;
import com.music.annnnn.ui.adapter.SearchAdapter;
import com.music.annnnn.ui.common.BaseMusicActivity;
import com.music.annnnn.ui.common.SongMenuHelper;
import com.music.annnnn.ui.detail.DetailActivity;
import com.music.annnnn.viewmodel.SearchViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SearchActivity extends BaseMusicActivity {

    private SearchViewModel viewModel;
    private PlaybackController player;
    private SearchAdapter adapter;

    private EditText etQuery;
    private ImageButton btnClear;
    private View layoutPreSearch;
    private View layoutResults;
    private View headerHistory;
    private ChipGroup chipGroupHistory;
    private ChipGroup chipGroupSuggestions;
    private TextView tvEmpty;

    private int selectedTab = 0; // 0=All, 1=Songs, 2=Artists, 3=Albums, 4=Playlists
    private List<SongEntity> lastSongs = new ArrayList<>();
    private List<ArtistSummary> lastArtists = new ArrayList<>();
    private List<AlbumEntity> lastAlbums = new ArrayList<>();
    private List<PlaylistEntity> lastPlaylists = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        bindBotanicalBackdrop(
                findViewById(R.id.bg_botanical_foliage),
                findViewById(R.id.bg_theme_gradient),
                findViewById(R.id.bg_ambient_orb)
        );

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.search_content_insets), (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        player = PlaybackController.getInstance(this);

        etQuery = findViewById(R.id.et_search_query);
        btnClear = findViewById(R.id.btn_search_clear);
        layoutPreSearch = findViewById(R.id.layout_pre_search);
        layoutResults = findViewById(R.id.layout_search_results);
        headerHistory = findViewById(R.id.header_search_history);
        chipGroupHistory = findViewById(R.id.chip_group_history);
        chipGroupSuggestions = findViewById(R.id.chip_group_suggestions);
        tvEmpty = findViewById(R.id.tv_search_empty);

        findViewById(R.id.btn_search_back).setOnClickListener(v -> finish());
        btnClear.setOnClickListener(v -> etQuery.setText(""));
        findViewById(R.id.btn_clear_search_history).setOnClickListener(v -> viewModel.clearHistory());

        setupSuggestions();
        setupRecyclerAndTabs();
        setupInputWatcher();
        observeSearch();
    }

    private void setupSuggestions() {
        List<String> suggestions = Arrays.asList("Lo-Fi", "Acoustic", "V-Pop", "Ballad", "Remix", "Chill", "Piano");
        for (String term : suggestions) {
            Chip chip = new Chip(this);
            chip.setText(term);
            chip.setChipBackgroundColorResource(R.color.bg_chip);
            chip.setOnClickListener(v -> {
                etQuery.setText(term);
                etQuery.setSelection(term.length());
                viewModel.commitSearch(term);
            });
            chipGroupSuggestions.addView(chip);
        }
    }

    private void setupRecyclerAndTabs() {
        RecyclerView rv = findViewById(R.id.rv_search_results);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchAdapter(new SearchAdapter.Listener() {
            @Override
            public void onSongClick(SongEntity song) {
                viewModel.commitSearch(etQuery.getText().toString());
                player.playSongList(Collections.singletonList(song), 0, getString(R.string.search_source_title));
            }

            @Override
            public void onSongMoreClick(SongEntity song) {
                SongMenuHelper.showSongMenu(SearchActivity.this, song);
            }

            @Override
            public void onArtistClick(ArtistSummary artist) {
                viewModel.commitSearch(etQuery.getText().toString());
                DetailActivity.openSection(SearchActivity.this, DetailActivity.TYPE_ARTIST, artist.artist, 0, artist.artist);
            }

            @Override
            public void onAlbumClick(AlbumEntity album) {
                viewModel.commitSearch(etQuery.getText().toString());
                DetailActivity.openSection(SearchActivity.this, DetailActivity.TYPE_ALBUM, album.title, album.id, null);
            }

            @Override
            public void onPlaylistClick(PlaylistEntity playlist) {
                viewModel.commitSearch(etQuery.getText().toString());
                DetailActivity.openSection(SearchActivity.this, DetailActivity.TYPE_PLAYLIST, playlist.name, playlist.id, null);
            }
        });
        rv.setAdapter(adapter);

        TabLayout tabs = findViewById(R.id.tab_search_categories);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                rebuildResults();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupInputWatcher() {
        etQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString() : "";
                boolean empty = TextUtils.isEmpty(q.trim());
                btnClear.setVisibility(empty ? View.GONE : View.VISIBLE);
                layoutPreSearch.setVisibility(empty ? View.VISIBLE : View.GONE);
                layoutResults.setVisibility(empty ? View.GONE : View.VISIBLE);
                viewModel.setQuery(q);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        etQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.commitSearch(etQuery.getText().toString());
                return true;
            }
            return false;
        });
    }

    private void observeSearch() {
        viewModel.observeHistory().observe(this, history -> {
            chipGroupHistory.removeAllViews();
            boolean hasHistory = history != null && !history.isEmpty();
            headerHistory.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
            chipGroupHistory.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
            if (history != null) {
                for (SearchHistoryEntity item : history) {
                    Chip chip = new Chip(this);
                    chip.setText(item.query);
                    chip.setChipBackgroundColorResource(R.color.bg_chip);
                    chip.setOnClickListener(v -> {
                        etQuery.setText(item.query);
                        etQuery.setSelection(item.query.length());
                    });
                    chipGroupHistory.addView(chip);
                }
            }
        });

        viewModel.observeSongResults().observe(this, list -> {
            lastSongs = list != null ? list : new ArrayList<>();
            rebuildResults();
        });
        viewModel.observeArtistResults().observe(this, list -> {
            lastArtists = list != null ? list : new ArrayList<>();
            rebuildResults();
        });
        viewModel.observeAlbumResults().observe(this, list -> {
            lastAlbums = list != null ? list : new ArrayList<>();
            rebuildResults();
        });
        viewModel.observePlaylistResults().observe(this, list -> {
            lastPlaylists = list != null ? list : new ArrayList<>();
            rebuildResults();
        });
    }

    private void rebuildResults() {
        List<SearchAdapter.SearchEntry> entries = new ArrayList<>();
        if ((selectedTab == 0 || selectedTab == 1) && !lastSongs.isEmpty()) {
            if (selectedTab == 0) entries.add(SearchAdapter.SearchEntry.header(getString(R.string.search_tab_songs)));
            for (SongEntity s : lastSongs) entries.add(SearchAdapter.SearchEntry.song(s));
        }
        if ((selectedTab == 0 || selectedTab == 2) && !lastArtists.isEmpty()) {
            if (selectedTab == 0) entries.add(SearchAdapter.SearchEntry.header(getString(R.string.search_tab_artists)));
            for (ArtistSummary a : lastArtists) entries.add(SearchAdapter.SearchEntry.artist(a));
        }
        if ((selectedTab == 0 || selectedTab == 3) && !lastAlbums.isEmpty()) {
            if (selectedTab == 0) entries.add(SearchAdapter.SearchEntry.header(getString(R.string.search_tab_albums)));
            for (AlbumEntity al : lastAlbums) entries.add(SearchAdapter.SearchEntry.album(al));
        }
        if ((selectedTab == 0 || selectedTab == 4) && !lastPlaylists.isEmpty()) {
            if (selectedTab == 0) entries.add(SearchAdapter.SearchEntry.header(getString(R.string.search_tab_playlists)));
            for (PlaylistEntity p : lastPlaylists) entries.add(SearchAdapter.SearchEntry.playlist(p));
        }
        adapter.submitEntries(entries);
        tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
