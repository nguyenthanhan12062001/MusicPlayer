package com.music.annnnn.ui.library;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.music.annnnn.R;
import com.music.annnnn.data.local.LocalMusicScanner;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.player.PlaybackController;
import com.music.annnnn.ui.adapter.PlaylistAdapter;
import com.music.annnnn.ui.adapter.SongAdapter;
import com.music.annnnn.ui.common.SongMenuHelper;
import com.music.annnnn.ui.detail.DetailActivity;
import com.music.annnnn.ui.main.MainActivity;
import com.music.annnnn.ui.search.SearchActivity;
import com.music.annnnn.viewmodel.MainViewModel;

import java.util.ArrayList;
import java.util.List;

public class LibraryFragment extends Fragment {

    private MainViewModel viewModel;
    private PlaybackController player;

    private TextView tvCountFav;
    private TextView tvCountDownloaded;
    private TextView tvCountAlbums;
    private TextView tvCountArtists;
    private TextView tvCountRecent;
    private TextView tvCountAll;

    private PlaylistAdapter playlistAdapter;
    private SongAdapter songAdapter;
    private List<SongEntity> allSongsCache = new ArrayList<>();

    private final ActivityResultLauncher<String[]> audioPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importAudio(uri);
                }
            });

    private final ActivityResultLauncher<String> audioScanPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    runDeviceMusicScan();
                } else if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.toast_scan_permission_denied, Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        player = viewModel.getPlaybackController();

        tvCountFav = view.findViewById(R.id.tv_count_favorites);
        tvCountDownloaded = view.findViewById(R.id.tv_count_downloaded);
        tvCountAlbums = view.findViewById(R.id.tv_count_albums);
        tvCountArtists = view.findViewById(R.id.tv_count_artists);
        tvCountRecent = view.findViewById(R.id.tv_count_recent);
        tvCountAll = view.findViewById(R.id.tv_count_all_songs);

        view.findViewById(R.id.btn_library_search).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SearchActivity.class)));
        view.findViewById(R.id.library_avatar).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToTab(R.id.nav_profile);
            }
        });

        // Quick listen chips near header (moved from removed Discover tab)
        view.findViewById(R.id.chip_quick_shuffle).setOnClickListener(v -> {
            if (!allSongsCache.isEmpty()) {
                player.playShuffled(allSongsCache, getString(R.string.quick_shuffle));
            } else {
                Toast.makeText(requireContext(), R.string.detail_empty_play_toast, Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(R.id.chip_quick_favorites).setOnClickListener(v ->
                DetailActivity.openSection(requireContext(), DetailActivity.TYPE_FAVORITES, getString(R.string.library_favorites), 0, null));
        view.findViewById(R.id.chip_quick_downloaded).setOnClickListener(v ->
                DetailActivity.openSection(requireContext(), DetailActivity.TYPE_DOWNLOADED, getString(R.string.library_downloaded), 0, null));

        view.findViewById(R.id.btn_scan_device_music).setOnClickListener(v -> {
            if (LocalMusicScanner.hasAudioScanPermission(requireContext())) {
                runDeviceMusicScan();
            } else {
                audioScanPermissionLauncher.launch(LocalMusicScanner.getRequiredAudioPermission());
            }
        });

        view.findViewById(R.id.btn_import_local_audio).setOnClickListener(v ->
                audioPickerLauncher.launch(new String[]{"audio/*"}));

        view.findViewById(R.id.btn_create_playlist).setOnClickListener(v ->
                SongMenuHelper.showCreatePlaylistDialog(requireContext(), viewModel.getRepository()));

        view.findViewById(R.id.card_lib_favorites).setOnClickListener(v ->
                DetailActivity.openSection(requireContext(), DetailActivity.TYPE_FAVORITES, getString(R.string.library_favorites), 0, null));
        view.findViewById(R.id.card_lib_downloaded).setOnClickListener(v ->
                DetailActivity.openSection(requireContext(), DetailActivity.TYPE_DOWNLOADED, getString(R.string.library_downloaded), 0, null));
        view.findViewById(R.id.card_lib_recent).setOnClickListener(v ->
                DetailActivity.openSection(requireContext(), DetailActivity.TYPE_RECENT, getString(R.string.library_recent), 0, null));
        view.findViewById(R.id.card_lib_all_songs).setOnClickListener(v ->
                DetailActivity.openSection(requireContext(), DetailActivity.TYPE_ALL_SONGS, getString(R.string.library_all_songs), 0, null));
        view.findViewById(R.id.card_lib_albums).setOnClickListener(v ->
                DetailActivity.openSection(requireContext(), DetailActivity.TYPE_ALBUM_LIST, getString(R.string.library_albums), 0, null));
        view.findViewById(R.id.card_lib_artists).setOnClickListener(v ->
                DetailActivity.openSection(requireContext(), DetailActivity.TYPE_ARTIST_LIST, getString(R.string.library_artists), 0, null));

        RecyclerView rvPlaylists = view.findViewById(R.id.rv_my_playlists);
        rvPlaylists.setLayoutManager(new LinearLayoutManager(requireContext()));
        playlistAdapter = new PlaylistAdapter(item ->
                DetailActivity.openSection(requireContext(), DetailActivity.TYPE_PLAYLIST, item.playlist.name, item.playlist.id, null));
        rvPlaylists.setAdapter(playlistAdapter);

        RecyclerView rvSongs = view.findViewById(R.id.rv_library_songs);
        rvSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        songAdapter = new SongAdapter(new SongAdapter.Listener() {
            @Override
            public void onSongClick(SongEntity song, int position) {
                player.playSongList(songAdapter.getItems(), position, getString(R.string.library_source_title));
            }

            @Override
            public void onFavoriteClick(SongEntity song) {
                viewModel.toggleFavorite(song);
            }

            @Override
            public void onMoreClick(SongEntity song) {
                SongMenuHelper.showSongMenu((AppCompatActivity) requireActivity(), song);
            }
        });
        rvSongs.setAdapter(songAdapter);

        observeLibrary();
    }

    private void observeLibrary() {
        viewModel.observeFavoriteCount().observe(getViewLifecycleOwner(), c ->
                tvCountFav.setText(getString(R.string.count_songs_format, c != null ? c : 0)));
        viewModel.observeDownloadedCount().observe(getViewLifecycleOwner(), c ->
                tvCountDownloaded.setText(getString(R.string.count_songs_format, c != null ? c : 0)));
        viewModel.observeRecentCount().observe(getViewLifecycleOwner(), c ->
                tvCountRecent.setText(getString(R.string.count_songs_format, c != null ? c : 0)));
        viewModel.observeAllAlbums().observe(getViewLifecycleOwner(), albums ->
                tvCountAlbums.setText(getString(R.string.count_albums_format, albums != null ? albums.size() : 0)));
        viewModel.observeArtists().observe(getViewLifecycleOwner(), artists ->
                tvCountArtists.setText(getString(R.string.count_artists_format, artists != null ? artists.size() : 0)));
        viewModel.observeAllSongs().observe(getViewLifecycleOwner(), songs -> {
            allSongsCache = songs != null ? songs : new ArrayList<>();
            tvCountAll.setText(getString(R.string.count_songs_format, allSongsCache.size()));
            songAdapter.submitList(allSongsCache);
        });
        viewModel.observeAllPlaylistsWithCount().observe(getViewLifecycleOwner(), list ->
                playlistAdapter.submitList(list));
        player.getCurrentSong().observe(getViewLifecycleOwner(), song ->
                songAdapter.setActiveSongId(song != null ? song.id : -1L));
        player.getIsPlaying().observe(getViewLifecycleOwner(), playing ->
                songAdapter.setPlaybackPlaying(Boolean.TRUE.equals(playing)));
    }

    private void runDeviceMusicScan() {
        Toast.makeText(requireContext(), R.string.toast_scanning_device, Toast.LENGTH_SHORT).show();
        viewModel.scanDeviceMusic(result -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (result != null && result.addedCount > 0) {
                    Toast.makeText(requireContext(),
                            getString(R.string.toast_scan_found_format, result.addedCount),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), R.string.toast_scan_no_new, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void importAudio(Uri uri) {
        Toast.makeText(requireContext(), R.string.toast_importing_song, Toast.LENGTH_SHORT).show();
        viewModel.importLocalAudio(uri, () -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), R.string.toast_imported_song, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
