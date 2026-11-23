package com.music.annnnn.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "albums")
public class AlbumEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";

    @NonNull
    public String artist = "";

    @NonNull
    public String description = "";

    public int releaseYear;

    @NonNull
    public String coverResName = "img_cover_vpop";

    @NonNull
    public String genre = "V-Pop";

    public AlbumEntity() {}

    public AlbumEntity(@NonNull String title,
                       @NonNull String artist,
                       @NonNull String description,
                       int releaseYear,
                       @NonNull String coverResName,
                       @NonNull String genre) {
        this.title = title;
        this.artist = artist;
        this.description = description;
        this.releaseYear = releaseYear;
        this.coverResName = coverResName;
        this.genre = genre;
    }
}
