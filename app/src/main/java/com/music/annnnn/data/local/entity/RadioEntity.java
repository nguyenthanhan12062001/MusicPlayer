package com.music.annnnn.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "radios")
public class RadioEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";

    @NonNull
    public String subtitle = "";

    @NonNull
    public String description = "";

    @NonNull
    public String category = "FEATURED"; // FEATURED, MOOD, GENRE, ARTIST, COLLECTION

    @NonNull
    public String coverResName = "img_cover_radio";

    public int listenerCount;

    @NonNull
    public String filterKeyword = "";

    public RadioEntity() {}

    public RadioEntity(@NonNull String title,
                       @NonNull String subtitle,
                       @NonNull String description,
                       @NonNull String category,
                       @NonNull String coverResName,
                       int listenerCount,
                       @NonNull String filterKeyword) {
        this.title = title;
        this.subtitle = subtitle;
        this.description = description;
        this.category = category;
        this.coverResName = coverResName;
        this.listenerCount = listenerCount;
        this.filterKeyword = filterKeyword;
    }
}
