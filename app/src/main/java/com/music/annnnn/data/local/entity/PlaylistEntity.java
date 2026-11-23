package com.music.annnnn.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "playlists")
public class PlaylistEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    @NonNull
    public String description = "";

    @NonNull
    public String creatorName = "Zing MP3";

    @NonNull
    public String coverResName = "img_cover_vpop";

    public boolean isUserCreated;
    public long createdAt;

    public PlaylistEntity() {}

    public PlaylistEntity(@NonNull String name,
                          @NonNull String description,
                          @NonNull String creatorName,
                          @NonNull String coverResName,
                          boolean isUserCreated) {
        this.name = name;
        this.description = description;
        this.creatorName = creatorName;
        this.coverResName = coverResName;
        this.isUserCreated = isUserCreated;
        this.createdAt = System.currentTimeMillis();
    }
}
