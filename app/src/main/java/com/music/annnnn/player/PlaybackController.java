package com.music.annnnn.player;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.BassBoost;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.music.annnnn.R;
import com.music.annnnn.data.local.PreferencesManager;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.data.repository.MusicRepository;
import com.music.annnnn.service.MusicPlaybackService;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class PlaybackController {

    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static volatile PlaybackController INSTANCE;

    private final Context appContext;
    private final MusicRepository repository;
    private final PreferencesManager prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private MediaPlayer mediaPlayer;
    private BassBoost bassBoost;

    private final MutableLiveData<SongEntity> currentSong = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Long> positionMs = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> durationMs = new MutableLiveData<>(0L);
    private final MutableLiveData<List<SongEntity>> queue = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(-1);
    private final MutableLiveData<Boolean> shuffleEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> repeatMode = new MutableLiveData<>(REPEAT_NONE);
    private final MutableLiveData<String> sourceTitle = new MutableLiveData<>();

    private long lastTickTimestamp = 0L;
    private long pendingListenMs = 0L;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && Boolean.TRUE.equals(isPlaying.getValue())) {
                try {
                    long pos = mediaPlayer.getCurrentPosition();
                    positionMs.setValue(pos);
                    long now = System.currentTimeMillis();
                    if (lastTickTimestamp > 0) {
                        pendingListenMs += (now - lastTickTimestamp);
                        if (pendingListenMs >= 5000L) {
                            long sec = pendingListenMs / 1000L;
                            prefs.addListenSeconds(sec);
                            pendingListenMs %= 1000L;
                        }
                    }
                    lastTickTimestamp = now;
                } catch (Exception ignored) {
                }
                mainHandler.postDelayed(this, 500);
            }
        }
    };

    private void flushPendingListenTime() {
        long now = System.currentTimeMillis();
        if (lastTickTimestamp > 0 && Boolean.TRUE.equals(isPlaying.getValue())) {
            pendingListenMs += Math.max(0L, now - lastTickTimestamp);
        }
        lastTickTimestamp = 0L;
        if (pendingListenMs >= 1000L) {
            prefs.addListenSeconds(pendingListenMs / 1000L);
            pendingListenMs %= 1000L;
        }
    }

    private PlaybackController(Context context) {
        this.appContext = context.getApplicationContext();
        this.repository = MusicRepository.getInstance(appContext);
        this.prefs = repository.getPreferencesManager();
        this.shuffleEnabled.setValue(prefs.isShuffleEnabled());
        this.repeatMode.setValue(prefs.getRepeatMode());
        this.sourceTitle.setValue(appContext.getString(R.string.player_tab_cover));
    }

    public static PlaybackController getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (PlaybackController.class) {
                if (INSTANCE == null) {
                    INSTANCE = new PlaybackController(context);
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<SongEntity> getCurrentSong() {
        return currentSong;
    }

    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    public LiveData<Long> getPositionMs() {
        return positionMs;
    }

    public LiveData<Long> getDurationMs() {
        return durationMs;
    }

    public LiveData<List<SongEntity>> getQueue() {
        return queue;
    }

    public LiveData<Integer> getCurrentIndex() {
        return currentIndex;
    }

    public LiveData<Boolean> getShuffleEnabled() {
        return shuffleEnabled;
    }

    public LiveData<Integer> getRepeatMode() {
        return repeatMode;
    }

    public LiveData<String> getSourceTitle() {
        return sourceTitle;
    }

    public void playSongList(List<SongEntity> songs, int startIndex, String sourceName) {
        if (songs == null || songs.isEmpty()) return;
        int safeIndex = Math.max(0, Math.min(startIndex, songs.size() - 1));
        queue.setValue(new ArrayList<>(songs));
        sourceTitle.setValue(TextUtils.isEmpty(sourceName) ? appContext.getString(R.string.player_tab_cover) : sourceName);
        playAtIndex(safeIndex);
    }

    public void playShuffled(List<SongEntity> songs, String sourceName) {
        if (songs == null || songs.isEmpty()) return;
        List<SongEntity> copy = new ArrayList<>(songs);
        Collections.shuffle(copy);
        shuffleEnabled.setValue(true);
        prefs.setShuffleEnabled(true);
        playSongList(copy, 0, sourceName);
    }

    public void addToQueue(SongEntity song) {
        if (song == null) return;
        List<SongEntity> currentQ = queue.getValue();
        List<SongEntity> updated = currentQ != null ? new ArrayList<>(currentQ) : new ArrayList<>();
        updated.add(song);
        queue.setValue(updated);
        if (currentSong.getValue() == null) {
            playAtIndex(0);
        }
    }

    public void removeFromQueue(int index) {
        List<SongEntity> currentQ = queue.getValue();
        if (currentQ == null || index < 0 || index >= currentQ.size()) return;
        Integer curIdxObj = currentIndex.getValue();
        int curIdx = curIdxObj != null ? curIdxObj : -1;
        List<SongEntity> updated = new ArrayList<>(currentQ);
        updated.remove(index);
        queue.setValue(updated);
        if (updated.isEmpty()) {
            stopAndClear();
        } else if (index == curIdx) {
            playAtIndex(Math.min(index, updated.size() - 1));
        } else if (index < curIdx) {
            currentIndex.setValue(curIdx - 1);
        }
    }

    public void playAtIndex(int index) {
        List<SongEntity> list = queue.getValue();
        if (list == null || index < 0 || index >= list.size()) return;
        SongEntity song = list.get(index);
        currentIndex.setValue(index);
        currentSong.setValue(song);
        repository.recordSongPlay(song.id);
        startMediaPlayer(song);
    }

    private void startMediaPlayer(SongEntity song) {
        releasePlayer();
        positionMs.setValue(0L);
        durationMs.setValue(song.durationMs > 0 ? song.durationMs : 30000L);

        if (TextUtils.isEmpty(song.filePath)) {
            isPlaying.setValue(false);
            return;
        }
        boolean isUri = song.filePath.startsWith("content://") || song.filePath.startsWith("file://");
        if (!isUri && !new File(song.filePath).exists()) {
            isPlaying.setValue(false);
            return;
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            if (isUri) {
                mediaPlayer.setDataSource(appContext, Uri.parse(song.filePath));
            } else {
                mediaPlayer.setDataSource(song.filePath);
            }
            mediaPlayer.setOnPreparedListener(mp -> {
                durationMs.setValue((long) mp.getDuration());
                applyAudioSettings();
                mp.start();
                isPlaying.setValue(true);
                lastTickTimestamp = System.currentTimeMillis();
                startProgressTicker();
                updateForegroundService();
            });
            mediaPlayer.setOnCompletionListener(mp -> handleTrackCompletion());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                isPlaying.setValue(false);
                stopProgressTicker();
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            isPlaying.setValue(false);
        }
    }

    public void applyAudioSettings() {
        if (mediaPlayer == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PlaybackParams params = mediaPlayer.getPlaybackParams();
                params.setSpeed(prefs.getPlaybackSpeed());
                mediaPlayer.setPlaybackParams(params);
            }
        } catch (Exception ignored) {
        }

        try {
            if (bassBoost != null) {
                bassBoost.release();
                bassBoost = null;
            }
            if (prefs.isBassBoostEnabled()) {
                bassBoost = new BassBoost(0, mediaPlayer.getAudioSessionId());
                if (bassBoost.getStrengthSupported()) {
                    bassBoost.setStrength((short) 700);
                }
                bassBoost.setEnabled(true);
            }
        } catch (Exception ignored) {
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer == null) {
            SongEntity cur = currentSong.getValue();
            if (cur != null) {
                startMediaPlayer(cur);
            }
            return;
        }
        try {
            if (mediaPlayer.isPlaying()) {
                flushPendingListenTime();
                mediaPlayer.pause();
                isPlaying.setValue(false);
                stopProgressTicker();
            } else {
                mediaPlayer.start();
                isPlaying.setValue(true);
                lastTickTimestamp = System.currentTimeMillis();
                startProgressTicker();
            }
            updateForegroundService();
        } catch (Exception ignored) {
        }
    }

    public void seekTo(long targetMs) {
        if (mediaPlayer == null) return;
        try {
            mediaPlayer.seekTo((int) Math.max(0, targetMs));
            positionMs.setValue(targetMs);
        } catch (Exception ignored) {
        }
    }

    public void playNext() {
        List<SongEntity> list = queue.getValue();
        if (list == null || list.isEmpty()) return;
        int cur = currentIndex.getValue() != null ? currentIndex.getValue() : 0;
        if (Boolean.TRUE.equals(shuffleEnabled.getValue()) && list.size() > 1) {
            int nextIdx = cur;
            while (nextIdx == cur) {
                nextIdx = random.nextInt(list.size());
            }
            playAtIndex(nextIdx);
            return;
        }
        int nextIdx = cur + 1;
        if (nextIdx >= list.size()) {
            nextIdx = 0;
        }
        playAtIndex(nextIdx);
    }

    public void playPrevious() {
        List<SongEntity> list = queue.getValue();
        if (list == null || list.isEmpty()) return;
        long pos = positionMs.getValue() != null ? positionMs.getValue() : 0L;
        if (pos > 3000L) {
            seekTo(0L);
            return;
        }
        int cur = currentIndex.getValue() != null ? currentIndex.getValue() : 0;
        int prevIdx = cur - 1;
        if (prevIdx < 0) {
            prevIdx = list.size() - 1;
        }
        playAtIndex(prevIdx);
    }

    public void toggleShuffle() {
        boolean next = !Boolean.TRUE.equals(shuffleEnabled.getValue());
        shuffleEnabled.setValue(next);
        prefs.setShuffleEnabled(next);
    }

    public void cycleRepeatMode() {
        int cur = repeatMode.getValue() != null ? repeatMode.getValue() : REPEAT_NONE;
        int next = (cur + 1) % 3;
        repeatMode.setValue(next);
        prefs.setRepeatMode(next);
    }

    public void syncCurrentSongFavorite(long songId, boolean isFavorite) {
        SongEntity cur = currentSong.getValue();
        if (cur != null && cur.id == songId) {
            cur.isFavorite = isFavorite;
            currentSong.setValue(cur);
        }
        List<SongEntity> list = queue.getValue();
        if (list != null) {
            for (SongEntity s : list) {
                if (s.id == songId) {
                    s.isFavorite = isFavorite;
                }
            }
        }
    }

    public void syncCurrentSongCover(long songId, String coverUrl) {
        SongEntity cur = currentSong.getValue();
        if (cur != null && cur.id == songId) {
            cur.coverUrl = coverUrl;
            currentSong.setValue(cur);
        }
        List<SongEntity> list = queue.getValue();
        if (list != null) {
            boolean touched = false;
            for (SongEntity s : list) {
                if (s.id == songId) {
                    s.coverUrl = coverUrl;
                    touched = true;
                }
            }
            if (touched) {
                queue.setValue(new ArrayList<>(list));
            }
        }
    }

    public void syncCurrentSongMetadata(long songId, String title, String artist, String album, String genre) {
        SongEntity cur = currentSong.getValue();
        if (cur != null && cur.id == songId) {
            cur.title = title;
            cur.artist = artist;
            cur.albumName = album;
            cur.genre = genre;
            currentSong.setValue(cur);
        }
        List<SongEntity> list = queue.getValue();
        if (list != null) {
            boolean touched = false;
            for (SongEntity s : list) {
                if (s.id == songId) {
                    s.title = title;
                    s.artist = artist;
                    s.albumName = album;
                    s.genre = genre;
                    touched = true;
                }
            }
            if (touched) {
                queue.setValue(new ArrayList<>(list));
            }
        }
    }

    private void handleTrackCompletion() {
        int repeat = repeatMode.getValue() != null ? repeatMode.getValue() : REPEAT_NONE;
        if (repeat == REPEAT_ONE) {
            seekTo(0L);
            if (mediaPlayer != null) {
                mediaPlayer.start();
                isPlaying.setValue(true);
            }
            return;
        }

        List<SongEntity> list = queue.getValue();
        if (list == null || list.isEmpty()) {
            isPlaying.setValue(false);
            stopProgressTicker();
            return;
        }

        int cur = currentIndex.getValue() != null ? currentIndex.getValue() : 0;
        if (Boolean.TRUE.equals(shuffleEnabled.getValue()) && list.size() > 1) {
            playNext();
        } else if (cur + 1 < list.size()) {
            playAtIndex(cur + 1);
        } else if (repeat == REPEAT_ALL) {
            playAtIndex(0);
        } else {
            isPlaying.setValue(false);
            positionMs.setValue(0L);
            stopProgressTicker();
            updateForegroundService();
        }
    }

    private void startProgressTicker() {
        stopProgressTicker();
        mainHandler.post(progressRunnable);
    }

    private void stopProgressTicker() {
        mainHandler.removeCallbacks(progressRunnable);
    }

    private void stopAndClear() {
        releasePlayer();
        currentSong.setValue(null);
        currentIndex.setValue(-1);
        isPlaying.setValue(false);
        positionMs.setValue(0L);
        durationMs.setValue(0L);
        appContext.stopService(new Intent(appContext, MusicPlaybackService.class));
    }

    private void releasePlayer() {
        flushPendingListenTime();
        stopProgressTicker();
        if (bassBoost != null) {
            try {
                bassBoost.release();
            } catch (Exception ignored) {
            }
            bassBoost = null;
        }
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
    }

    private void updateForegroundService() {
        SongEntity song = currentSong.getValue();
        if (song == null) return;
        Intent intent = new Intent(appContext, MusicPlaybackService.class);
        intent.setAction(MusicPlaybackService.ACTION_UPDATE);
        try {
            if (Boolean.TRUE.equals(isPlaying.getValue())) {
                appContext.startForegroundService(intent);
            } else {
                appContext.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }
}
