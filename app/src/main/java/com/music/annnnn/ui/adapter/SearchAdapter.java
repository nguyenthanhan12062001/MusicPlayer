package com.music.annnnn.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.music.annnnn.R;
import com.music.annnnn.data.local.entity.AlbumEntity;
import com.music.annnnn.data.local.entity.ArtistSummary;
import com.music.annnnn.data.local.entity.PlaylistEntity;
import com.music.annnnn.data.local.entity.SongEntity;
import com.music.annnnn.util.CoverLoader;

import java.util.ArrayList;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_SONG = 1;
    public static final int TYPE_ARTIST = 2;
    public static final int TYPE_ALBUM = 3;
    public static final int TYPE_PLAYLIST = 4;

    public static class SearchEntry {
        public int type;
        public String headerTitle;
        public SongEntity song;
        public ArtistSummary artist;
        public AlbumEntity album;
        public PlaylistEntity playlist;

        public static SearchEntry header(String title) {
            SearchEntry e = new SearchEntry();
            e.type = TYPE_HEADER;
            e.headerTitle = title;
            return e;
        }

        public static SearchEntry song(SongEntity s) {
            SearchEntry e = new SearchEntry();
            e.type = TYPE_SONG;
            e.song = s;
            return e;
        }

        public static SearchEntry artist(ArtistSummary a) {
            SearchEntry e = new SearchEntry();
            e.type = TYPE_ARTIST;
            e.artist = a;
            return e;
        }

        public static SearchEntry album(AlbumEntity al) {
            SearchEntry e = new SearchEntry();
            e.type = TYPE_ALBUM;
            e.album = al;
            return e;
        }

        public static SearchEntry playlist(PlaylistEntity p) {
            SearchEntry e = new SearchEntry();
            e.type = TYPE_PLAYLIST;
            e.playlist = p;
            return e;
        }
    }

    public interface Listener {
        void onSongClick(SongEntity song);

        void onSongMoreClick(SongEntity song);

        void onArtistClick(ArtistSummary artist);

        void onAlbumClick(AlbumEntity album);

        void onPlaylistClick(PlaylistEntity playlist);
    }

    private final List<SearchEntry> entries = new ArrayList<>();
    private final Listener listener;

    public SearchAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitEntries(List<SearchEntry> newEntries) {
        List<SearchEntry> target = newEntries != null ? new ArrayList<>(newEntries) : new ArrayList<>();
        List<SearchEntry> oldList = new ArrayList<>(entries);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return target.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                SearchEntry o = oldList.get(oldPos);
                SearchEntry n = target.get(newPos);
                if (o.type != n.type) return false;
                if (o.type == TYPE_HEADER) return TextUtils.equals(o.headerTitle, n.headerTitle);
                if (o.type == TYPE_SONG && o.song != null && n.song != null) return o.song.id == n.song.id;
                if (o.type == TYPE_ARTIST && o.artist != null && n.artist != null) return TextUtils.equals(o.artist.artist, n.artist.artist);
                if (o.type == TYPE_ALBUM && o.album != null && n.album != null) return o.album.id == n.album.id;
                if (o.type == TYPE_PLAYLIST && o.playlist != null && n.playlist != null) return o.playlist.id == n.playlist.id;
                return false;
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                SearchEntry o = oldList.get(oldPos);
                SearchEntry n = target.get(newPos);
                if (o.type == TYPE_HEADER) return TextUtils.equals(o.headerTitle, n.headerTitle);
                if (o.type == TYPE_SONG && o.song != null && n.song != null) {
                    return TextUtils.equals(o.song.title, n.song.title)
                            && TextUtils.equals(o.song.artist, n.song.artist)
                            && TextUtils.equals(o.song.coverUrl, n.song.coverUrl);
                }
                if (o.type == TYPE_ARTIST && o.artist != null && n.artist != null) {
                    return o.artist.songCount == n.artist.songCount;
                }
                if (o.type == TYPE_ALBUM && o.album != null && n.album != null) {
                    return TextUtils.equals(o.album.title, n.album.title) && TextUtils.equals(o.album.artist, n.album.artist);
                }
                if (o.type == TYPE_PLAYLIST && o.playlist != null && n.playlist != null) {
                    return TextUtils.equals(o.playlist.name, n.playlist.name);
                }
                return false;
            }
        });
        entries.clear();
        entries.addAll(target);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        return entries.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_search_header, parent, false));
        }
        return new ItemVH(inf.inflate(R.layout.item_song_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SearchEntry entry = entries.get(position);
        if (entry.type == TYPE_HEADER) {
            ((HeaderVH) holder).tvHeader.setText(entry.headerTitle);
            return;
        }

        ItemVH vh = (ItemVH) holder;
        android.content.Context ctx = vh.itemView.getContext();
        vh.btnFav.setVisibility(View.GONE);
        if (entry.type == TYPE_SONG && entry.song != null) {
            vh.tvTitle.setText(entry.song.title);
            vh.tvSubtitle.setText(ctx.getString(R.string.search_tab_songs) + " • " + entry.song.artist);
            CoverLoader.loadSongArtwork(vh.ivCover, entry.song.coverUrl);
            vh.btnMore.setVisibility(View.VISIBLE);
            vh.itemView.setOnClickListener(v -> listener.onSongClick(entry.song));
            vh.btnMore.setOnClickListener(v -> listener.onSongMoreClick(entry.song));
        } else if (entry.type == TYPE_ARTIST && entry.artist != null) {
            vh.tvTitle.setText(entry.artist.artist);
            vh.tvSubtitle.setText(ctx.getString(R.string.search_tab_artists) + " • " + ctx.getString(R.string.count_songs_format, entry.artist.songCount));
            CoverLoader.loadCover(vh.ivCover, null, entry.artist.coverResName);
            vh.btnMore.setVisibility(View.GONE);
            vh.itemView.setOnClickListener(v -> listener.onArtistClick(entry.artist));
        } else if (entry.type == TYPE_ALBUM && entry.album != null) {
            vh.tvTitle.setText(entry.album.title);
            vh.tvSubtitle.setText(ctx.getString(R.string.search_tab_albums) + " • " + entry.album.artist);
            CoverLoader.loadCover(vh.ivCover, null, entry.album.coverResName);
            vh.btnMore.setVisibility(View.GONE);
            vh.itemView.setOnClickListener(v -> listener.onAlbumClick(entry.album));
        } else if (entry.type == TYPE_PLAYLIST && entry.playlist != null) {
            vh.tvTitle.setText(entry.playlist.name);
            vh.tvSubtitle.setText(ctx.getString(R.string.search_tab_playlists) + " • " + entry.playlist.creatorName);
            CoverLoader.loadCover(vh.ivCover, null, entry.playlist.coverResName);
            vh.btnMore.setVisibility(View.GONE);
            vh.itemView.setOnClickListener(v -> listener.onPlaylistClick(entry.playlist));
        }
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvHeader;

        HeaderVH(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tv_search_section_title);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        final ShapeableImageView ivCover;
        final TextView tvTitle;
        final TextView tvSubtitle;
        final ImageButton btnFav;
        final ImageButton btnMore;

        ItemVH(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_song_cover);
            tvTitle = itemView.findViewById(R.id.tv_song_title);
            tvSubtitle = itemView.findViewById(R.id.tv_song_artist);
            btnFav = itemView.findViewById(R.id.btn_song_favorite);
            btnMore = itemView.findViewById(R.id.btn_song_more);
        }
    }
}
