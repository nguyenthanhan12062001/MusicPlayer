package com.music.annnnn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.music.annnnn.data.local.entity.AlbumEntity;
import com.music.annnnn.data.local.entity.PlaylistEntity;
import com.music.annnnn.data.local.entity.RadioEntity;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.data.repository.MusicRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DetailViewModel extends AndroidViewModel {

    public static final int SORT_DEFAULT = 0;
    public static final int SORT_TITLE = 1;
    public static final int SORT_ARTIST = 2;
    public static final int SORT_DURATION = 3;

    private final MusicRepository repository;
    private final MutableLiveData<Integer> sortMode = new MutableLiveData<>(SORT_DEFAULT);
    private final MediatorLiveData<List<SongEntity>> sortedSongs = new MediatorLiveData<>();
    private LiveData<List<SongEntity>> sourceSongs;

    public DetailViewModel(@NonNull Application application) {
        super(application);
        this.repository = MusicRepository.getInstance(application);
        sortedSongs.addSource(sortMode, mode -> applySort());
    }

    public void bindSource(LiveData<List<SongEntity>> source) {
        if (sourceSongs != null) {
            sortedSongs.removeSource(sourceSongs);
        }
        sourceSongs = source;
        sortedSongs.addSource(source, list -> applySort());
    }

    private void applySort() {
        List<SongEntity> raw = sourceSongs != null ? sourceSongs.getValue() : null;
        if (raw == null) {
            sortedSongs.setValue(new ArrayList<>());
            return;
        }
        List<SongEntity> copy = new ArrayList<>(raw);
        int mode = sortMode.getValue() != null ? sortMode.getValue() : SORT_DEFAULT;
        if (mode == SORT_TITLE) {
            Collections.sort(copy, (a, b) -> a.title.compareToIgnoreCase(b.title));
        } else if (mode == SORT_ARTIST) {
            Collections.sort(copy, (a, b) -> a.artist.compareToIgnoreCase(b.artist));
        } else if (mode == SORT_DURATION) {
            Collections.sort(copy, (a, b) -> Long.compare(b.durationMs, a.durationMs));
        }
        sortedSongs.setValue(copy);
    }

    public LiveData<List<SongEntity>> observeSortedSongs() {
        return sortedSongs;
    }

    public void setSortMode(int mode) {
        sortMode.setValue(mode);
    }

    public int getSortMode() {
        return sortMode.getValue() != null ? sortMode.getValue() : SORT_DEFAULT;
    }

    public LiveData<AlbumEntity> observeAlbum(long albumId) {
        return repository.observeAlbumById(albumId);
    }

    public LiveData<PlaylistEntity> observePlaylist(long playlistId) {
        return repository.observePlaylistById(playlistId);
    }

    public LiveData<RadioEntity> observeRadio(long radioId) {
        return repository.observeRadioById(radioId);
    }

    public MusicRepository getRepository() {
        return repository;
    }
}
