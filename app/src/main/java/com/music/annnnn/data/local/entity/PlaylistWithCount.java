package com.music.annnnn.data.local.entity;

import androidx.room.Embedded;

public class PlaylistWithCount {
    @Embedded
    public PlaylistEntity playlist;
    public int songCount;
}
