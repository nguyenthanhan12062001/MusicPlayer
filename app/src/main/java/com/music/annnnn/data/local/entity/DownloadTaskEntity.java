package com.music.annnnn.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "download_tasks")
public class DownloadTaskEntity {
    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_DOWNLOADING = "DOWNLOADING";
    public static final String STATUS_CONVERTING = "CONVERTING";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String MEDIA_AUDIO = "AUDIO";
    public static final String MEDIA_VIDEO = "VIDEO";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String youtubeUrl = "";

    @NonNull
    public String videoId = "";

    @NonNull
    public String sourceName = "YouTube";

    @NonNull
    public String mediaType = MEDIA_AUDIO;

    public boolean isPlaylist;
    public int playlistCount;
    public int currentItemIndex;

    @NonNull
    public String title = "";

    @NonNull
    public String channelName = "";

    public String thumbnailUrl;

    @NonNull
    public String durationText = "00:00";

    public long durationMs;

    @NonNull
    public String quality = "320 kbps";

    @NonNull
    public String format = "MP3";

    public String formatId;

    @NonNull
    public String status = STATUS_QUEUED;

    public int progress;
    public long etaSeconds;
    public long downloadedBytes;
    public long totalBytes;
    public long speedBytesPerSec;
    public String speedText;
    public String sizeText;
    public String processId;
    public String tempDirPath;
    public String filePath;
    public String mediaStoreUri;
    public String streamUrl;
    public String errorMessage;
    public long createdAt;

    public DownloadTaskEntity() {
        this.createdAt = System.currentTimeMillis();
    }
}
