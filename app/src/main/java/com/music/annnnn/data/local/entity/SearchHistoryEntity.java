package com.music.annnnn.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_history", indices = {@Index(value = "query", unique = true)})
public class SearchHistoryEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String query = "";

    public long searchedAt;

    public SearchHistoryEntity() {}

    public SearchHistoryEntity(@NonNull String query, long searchedAt) {
        this.query = query;
        this.searchedAt = searchedAt;
    }
}
