package com.music.annnnn.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.music.annnnn.data.local.LocalMusicScanner;
import com.music.annnnn.data.local.PreferencesManager;
import com.music.annnnn.data.local.entity.AlbumEntity;
import com.music.annnnn.data.local.entity.ArtistSummary;
import com.music.annnnn.data.local.entity.PlaylistEntity;
import com.music.annnnn.data.local.entity.PlaylistWithCount;
import com.music.annnnn.data.local.entity.RadioEntity;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.data.repository.MusicRepository;
import com.music.annnnn.player.PlaybackController;

import java.util.List;
import java.util.function.Consumer;

public class MainViewModel extends AndroidViewModel {

    private final MusicRepository repository;
    private final PlaybackController playbackController;
    private final MutableLiveData<String> selectedChartRegion = new MutableLiveData<>("VN");
    private final LiveData<List<SongEntity>> chartSongs;
    private boolean hasTriggeredInitialScan = false;

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.repository = MusicRepository.getInstance(application);
        this.playbackController = PlaybackController.getInstance(application);
        this.chartSongs = Transformations.switchMap(selectedChartRegion, repository::observeChartSongs);
    }

    public MusicRepository getRepository() {
        return repository;
    }

    public PlaybackController getPlaybackController() {
        return playbackController;
    }

    public PreferencesManager getPreferences() {
        return repository.getPreferencesManager();
    }

    public LiveData<List<SongEntity>> observeAllSongs() {
        return repository.observeAllSongs();
    }

    public LiveData<List<SongEntity>> observeFavoriteSongs() {
        return repository.observeFavoriteSongs();
    }

    public LiveData<List<SongEntity>> observeDownloadedSongs() {
        return repository.observeDownloadedSongs();
    }

    public LiveData<List<SongEntity>> observeRecentSongs() {
        return repository.observeRecentSongs();
    }

    public LiveData<List<AlbumEntity>> observeAllAlbums() {
        return repository.observeAllAlbums();
    }

    public LiveData<List<PlaylistWithCount>> observeAllPlaylistsWithCount() {
        return repository.observeAllPlaylistsWithCount();
    }

    public LiveData<List<PlaylistWithCount>> observeUserPlaylistsWithCount() {
        return repository.observeUserPlaylistsWithCount();
    }

    public LiveData<List<PlaylistEntity>> observeFeaturedPlaylists() {
        return repository.observeFeaturedPlaylists();
    }

    public LiveData<List<RadioEntity>> observeAllRadios() {
        return repository.observeAllRadios();
    }

    public LiveData<List<ArtistSummary>> observeArtists() {
        return repository.observeArtists();
    }

    public LiveData<Integer> observeFavoriteCount() {
        return repository.observeFavoriteCount();
    }

    public LiveData<Integer> observeDownloadedCount() {
        return repository.observeDownloadedCount();
    }

    public LiveData<Integer> observeRecentCount() {
        return repository.observeRecentCount();
    }

    public LiveData<Integer> observeTotalPlayCount() {
        return repository.observeTotalPlayCount();
    }

    public LiveData<String> getSelectedChartRegion() {
        return selectedChartRegion;
    }

    public void setChartRegion(String region) {
        selectedChartRegion.setValue(region);
    }

    public LiveData<List<SongEntity>> observeChartSongs() {
        return chartSongs;
    }

    public LiveData<List<SongEntity>> observeChartByRegion(String region) {
        return repository.observeChartSongs(region);
    }

    public void toggleFavorite(SongEntity song) {
        if (song == null) return;
        boolean next = !song.isFavorite;
        repository.toggleFavorite(song.id, next);
        playbackController.syncCurrentSongFavorite(song.id, next);
    }

    public void createPlaylist(String name, String description) {
        repository.createPlaylist(name, description);
    }

    public void addSongToPlaylist(long playlistId, long songId) {
        repository.addSongToPlaylist(playlistId, songId);
    }

    public void updateSongMetadata(long songId, String title, String artist, String album, String genre) {
        repository.updateSongMetadata(songId, title, artist, album, genre);
    }

    public void deleteSong(SongEntity song) {
        repository.deleteSong(song);
    }

    public void importLocalAudio(Uri uri, Runnable onDone) {
        repository.importLocalAudioUri(uri, onDone);
    }

    public void scanDeviceMusic(Consumer<LocalMusicScanner.ScanResult> onResult) {
        repository.scanDeviceMusic(onResult);
    }

    public void scanDeviceMusicOnceOnStartup() {
        if (hasTriggeredInitialScan) return;
        hasTriggeredInitialScan = true;
        repository.scanDeviceMusic(null);
    }
}
