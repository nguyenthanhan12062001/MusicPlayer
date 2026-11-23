package com.music.annnnn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.music.annnnn.data.local.entity.AlbumEntity;
import com.music.annnnn.data.local.entity.ArtistSummary;
import com.music.annnnn.data.local.entity.PlaylistEntity;
import com.music.annnnn.data.local.entity.SearchHistoryEntity;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.data.repository.MusicRepository;

import java.util.List;

public class SearchViewModel extends AndroidViewModel {

    private final MusicRepository repository;
    private final MutableLiveData<String> queryLiveData = new MutableLiveData<>("");

    private final LiveData<List<SongEntity>> songResults;
    private final LiveData<List<ArtistSummary>> artistResults;
    private final LiveData<List<AlbumEntity>> albumResults;
    private final LiveData<List<PlaylistEntity>> playlistResults;

    public SearchViewModel(@NonNull Application application) {
        super(application);
        this.repository = MusicRepository.getInstance(application);

        this.songResults = Transformations.switchMap(queryLiveData, repository::searchSongs);
        this.artistResults = Transformations.switchMap(queryLiveData, repository::searchArtists);
        this.albumResults = Transformations.switchMap(queryLiveData, repository::searchAlbums);
        this.playlistResults = Transformations.switchMap(queryLiveData, repository::searchPlaylists);
    }

    public void setQuery(String query) {
        queryLiveData.setValue(query == null ? "" : query.trim());
    }

    public void commitSearch(String query) {
        if (query != null && !query.trim().isEmpty()) {
            repository.saveSearchQuery(query.trim());
        }
    }

    public void clearHistory() {
        repository.clearSearchHistory();
    }

    public LiveData<String> getQuery() {
        return queryLiveData;
    }

    public LiveData<List<SearchHistoryEntity>> observeHistory() {
        return repository.observeSearchHistory();
    }

    public LiveData<List<SongEntity>> observeSongResults() {
        return songResults;
    }

    public LiveData<List<ArtistSummary>> observeArtistResults() {
        return artistResults;
    }

    public LiveData<List<AlbumEntity>> observeAlbumResults() {
        return albumResults;
    }

    public LiveData<List<PlaylistEntity>> observePlaylistResults() {
        return playlistResults;
    }

    public MusicRepository getRepository() {
        return repository;
    }
}
