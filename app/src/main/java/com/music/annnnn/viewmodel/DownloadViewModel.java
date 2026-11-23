package com.music.annnnn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.music.annnnn.R;
import com.music.annnnn.data.download.MultiSourceAnalyzer;
import com.music.annnnn.data.local.PreferencesManager;
import com.music.annnnn.data.local.entity.DownloadTaskEntity;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.data.repository.MusicRepository;

import java.util.List;
import java.util.function.Consumer;

public class DownloadViewModel extends AndroidViewModel {

    private final MusicRepository repository;
    private final MultiSourceAnalyzer analyzer;

    private final MutableLiveData<Boolean> isAnalyzing = new MutableLiveData<>(false);
    private final MutableLiveData<MultiSourceAnalyzer.MediaAnalysisResult> analyzedMedia = new MutableLiveData<>(null);
    private final MutableLiveData<String> analyzeError = new MutableLiveData<>(null);
    private final MutableLiveData<String> selectedFormat = new MutableLiveData<>("MP3");
    private final MutableLiveData<String> selectedQuality = new MutableLiveData<>("320 kbps");
    private final MutableLiveData<String> statusEvent = new MutableLiveData<>(null);

    public DownloadViewModel(@NonNull Application application) {
        super(application);
        this.repository = MusicRepository.getInstance(application);
        this.analyzer = repository.getMultiSourceAnalyzer();
        this.selectedQuality.setValue(repository.getPreferencesManager().getDefaultQuality());
    }

    public PreferencesManager getPreferences() {
        return repository.getPreferencesManager();
    }

    public LiveData<Boolean> getIsAnalyzing() {
        return isAnalyzing;
    }

    public LiveData<MultiSourceAnalyzer.MediaAnalysisResult> getAnalyzedMedia() {
        return analyzedMedia;
    }

    public LiveData<String> getAnalyzeError() {
        return analyzeError;
    }

    public LiveData<String> getSelectedFormat() {
        return selectedFormat;
    }

    public LiveData<String> getSelectedQuality() {
        return selectedQuality;
    }

    public LiveData<String> getStatusEvent() {
        return statusEvent;
    }

    public void clearStatusEvent() {
        statusEvent.setValue(null);
    }

    public void setSelectedFormat(String format) {
        selectedFormat.setValue(format);
    }

    public void setSelectedQuality(String quality) {
        selectedQuality.setValue(quality);
    }

    public void analyzeUrl(String url) {
        isAnalyzing.postValue(true);
        analyzeError.postValue(null);
        analyzedMedia.postValue(null);

        analyzer.analyze(getApplication(), url, new MultiSourceAnalyzer.AnalyzeCallback() {
            @Override
            public void onSuccess(MultiSourceAnalyzer.MediaAnalysisResult result) {
                isAnalyzing.postValue(false);
                analyzedMedia.postValue(result);
            }

            @Override
            public void onError(String message) {
                isAnalyzing.postValue(false);
                analyzeError.postValue(message);
            }
        });
    }

    public void startDownload() {
        MultiSourceAnalyzer.MediaAnalysisResult info = analyzedMedia.getValue();
        if (info == null) return;
        String format = selectedFormat.getValue() != null ? selectedFormat.getValue() : "MP3";
        String quality = selectedQuality.getValue() != null ? selectedQuality.getValue() : "320 kbps";

        repository.enqueueMediaDownload(info, format, quality, enqueued -> {
            if (Boolean.TRUE.equals(enqueued)) {
                statusEvent.postValue(info.isPlaylist
                        ? getApplication().getString(R.string.dl_vm_added_playlist, info.playlistCount)
                        : getApplication().getString(R.string.dl_vm_added_queue));
            } else {
                statusEvent.postValue(getApplication().getString(R.string.dl_vm_duplicate));
            }
        });
    }

    public void pauseDownload(long taskId) {
        repository.pauseDownload(taskId);
    }

    public void resumeOrRetryDownload(long taskId) {
        repository.resumeOrRetryDownload(taskId);
    }

    public void cancelDownload(long taskId) {
        repository.cancelDownload(taskId);
    }

    public void findDownloadedSong(String filePath, Consumer<SongEntity> callback) {
        repository.loadSongByFilePath(filePath, callback);
    }

    public LiveData<List<DownloadTaskEntity>> observeActiveDownloads() {
        return repository.observeActiveDownloads();
    }

    public LiveData<List<DownloadTaskEntity>> observeCompletedDownloads() {
        return repository.observeCompletedDownloads();
    }

    public LiveData<List<DownloadTaskEntity>> observeFailedDownloads() {
        return repository.observeFailedDownloads();
    }
}
