package com.music.annnnn.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "songs",
        indices = {
                @Index("filePath"),
                @Index("artist"),
                @Index("albumId"),
                @Index("chartRegion"),
                @Index("isFavorite"),
                @Index("isDownloaded"),
                @Index("lastPlayedAt")
        }
)
public class SongEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";

    @NonNull
    public String artist = "";

    public long albumId;

    @NonNull
    public String albumName = "";

    public long durationMs;

    @NonNull
    public String filePath = "";

    @NonNull
    public String coverResName = "img_cover_vpop";

    public String coverUrl;

    @NonNull
    public String lyrics = "";

    @NonNull
    public String genre = "V-Pop";

    @NonNull
    public String mood = "Thư giãn";

    public boolean isFavorite;
    public boolean isDownloaded;
    public int chartRank;
    public String chartRegion; // "VN", "US-UK", "KPOP", or null
    public int chartTrend; // >0 up, 0 same, <0 down
    public int playCount;
    public long lastPlayedAt;
    public long createdAt;

    public SongEntity() {}

    public SongEntity(@NonNull String title,
                      @NonNull String artist,
                      long albumId,
                      @NonNull String albumName,
                      long durationMs,
                      @NonNull String filePath,
                      @NonNull String coverResName,
                      String coverUrl,
                      @NonNull String lyrics,
                      @NonNull String genre,
                      @NonNull String mood,
                      boolean isFavorite,
                      boolean isDownloaded,
                      int chartRank,
                      String chartRegion,
                      int chartTrend) {
        this.title = title;
        this.artist = artist;
        this.albumId = albumId;
        this.albumName = albumName;
        this.durationMs = durationMs;
        this.filePath = filePath;
        this.coverResName = coverResName;
        this.coverUrl = coverUrl;
        this.lyrics = lyrics;
        this.genre = genre;
        this.mood = mood;
        this.isFavorite = isFavorite;
        this.isDownloaded = isDownloaded;
        this.chartRank = chartRank;
        this.chartRegion = chartRegion;
        this.chartTrend = chartTrend;
        this.playCount = 0;
        this.lastPlayedAt = 0;
        this.createdAt = System.currentTimeMillis();
    }
}
