package com.music.annnnn.ui.download;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.music.annnnn.R;
import com.music.annnnn.data.download.MediaEngineInitializer;
import com.music.annnnn.data.download.MultiSourceAnalyzer;
import com.music.annnnn.data.local.PreferencesManager;
import com.music.annnnn.data.local.entity.DownloadTaskEntity;
import com.music.annnnn.player.PlaybackController;
import com.music.annnnn.ui.adapter.DownloadTaskAdapter;
import com.music.annnnn.ui.player.FullPlayerActivity;
import com.music.annnnn.util.CoverLoader;
import com.music.annnnn.viewmodel.DownloadViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DownloadFragment extends Fragment {

    private DownloadViewModel viewModel;
    private PreferencesManager prefs;
    private DownloadTaskAdapter adapter;

    private TextInputEditText etUrl;
    private TextView tvEngineStatus;
    private TextView tvFolderPath;
    private MaterialButton btnResetFolder;
    private ProgressBar progressAnalyzing;
    private TextView tvAnalyzeError;
    private View layoutAnalyzedResult;
    private ImageView ivThumb;
    private TextView tvYtTitle;
    private TextView tvYtChannel;
    private TextView tvYtDuration;
    private TextView tvSourceBadge;
    private TextView tvDownloadEmpty;
    private ChipGroup chipGroupFormat;
    private ChipGroup chipGroupQuality;
    private Chip chipFmtMp4;
    private TabLayout tabDownloadStatus;

    private MultiSourceAnalyzer.MediaAnalysisResult currentMediaResult;

    private int currentTab = 0; // 0=Downloading, 1=Completed, 2=Failed
    private List<DownloadTaskEntity> activeList = new ArrayList<>();
    private List<DownloadTaskEntity> completedList = new ArrayList<>();
    private List<DownloadTaskEntity> failedList = new ArrayList<>();

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                if (!isAdded()) return;
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                        && TextUtils.isEmpty(prefs.getDownloadTreeUri())
                        && Boolean.FALSE.equals(results.get(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
                    Toast.makeText(requireContext(), R.string.toast_storage_permission_legacy, Toast.LENGTH_SHORT).show();
                }
                enqueueDownloadAndShowTab();
            });

    private final ActivityResultLauncher<Uri> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null && isAdded()) {
                    int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(uri, flags);
                    } catch (Exception ignored) {
                    }
                    String label = resolveFolderDisplayName(uri);
                    prefs.setDownloadFolder(uri.toString(), label);
                    updateDownloadFolderUi();
                    Toast.makeText(requireContext(),
                            getString(R.string.toast_download_folder_updated, label),
                            Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_download, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(DownloadViewModel.class);
        prefs = viewModel.getPreferences();

        etUrl = view.findViewById(R.id.et_youtube_url);
        tvEngineStatus = view.findViewById(R.id.tv_engine_status);
        tvFolderPath = view.findViewById(R.id.tv_download_folder_path);
        btnResetFolder = view.findViewById(R.id.btn_reset_download_folder);
        progressAnalyzing = view.findViewById(R.id.progress_analyzing);
        tvAnalyzeError = view.findViewById(R.id.tv_analyze_error);
        layoutAnalyzedResult = view.findViewById(R.id.layout_analyzed_result);
        ivThumb = view.findViewById(R.id.iv_yt_thumbnail);
        tvYtTitle = view.findViewById(R.id.tv_yt_title);
        tvYtChannel = view.findViewById(R.id.tv_yt_channel);
        tvYtDuration = view.findViewById(R.id.tv_yt_duration);
        tvSourceBadge = view.findViewById(R.id.tv_yt_source_badge);
        tvDownloadEmpty = view.findViewById(R.id.tv_download_empty);
        chipGroupFormat = view.findViewById(R.id.chip_group_format);
        chipGroupQuality = view.findViewById(R.id.chip_group_quality);
        chipFmtMp4 = view.findViewById(R.id.chip_fmt_mp4);

        view.findViewById(R.id.btn_paste_url).setOnClickListener(v -> pasteFromClipboard());
        view.findViewById(R.id.btn_analyze_url).setOnClickListener(v -> {
            String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(requireContext(), R.string.download_enter_url_toast, Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.analyzeUrl(url);
        });

        view.findViewById(R.id.btn_choose_download_folder).setOnClickListener(v ->
                folderPickerLauncher.launch(null));
        btnResetFolder.setOnClickListener(v -> {
            prefs.clearDownloadFolder();
            updateDownloadFolderUi();
            Toast.makeText(requireContext(), R.string.toast_download_folder_reset, Toast.LENGTH_SHORT).show();
        });
        updateDownloadFolderUi();

        chipGroupFormat.setOnCheckedStateChangeListener((group, checkedIds) -> {
            String fmt = "MP3";
            if (checkedIds.contains(R.id.chip_fmt_m4a)) fmt = "M4A";
            else if (checkedIds.contains(R.id.chip_fmt_opus)) fmt = "OPUS";
            else if (checkedIds.contains(R.id.chip_fmt_flac)) fmt = "FLAC";
            else if (checkedIds.contains(R.id.chip_fmt_mp4)) fmt = "MP4";
            viewModel.setSelectedFormat(fmt);
            rebuildQualityChips(fmt);
        });

        view.findViewById(R.id.btn_start_yt_download).setOnClickListener(v -> startDownloadWithPermissionCheck());

        setupDownloadManager(view);
        observeViewModel();
    }

    private void updateDownloadFolderUi() {
        if (!isAdded()) return;
        String customUri = prefs.getDownloadTreeUri();
        String customLabel = prefs.getDownloadFolderLabel();
        boolean hasCustom = !TextUtils.isEmpty(customUri);
        String display = hasCustom && !TextUtils.isEmpty(customLabel)
                ? customLabel
                : getString(R.string.download_folder_default);
        tvFolderPath.setText(getString(R.string.download_folder_label_format, display));
        btnResetFolder.setVisibility(hasCustom ? View.VISIBLE : View.GONE);
    }

    private String resolveFolderDisplayName(Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            if (!TextUtils.isEmpty(docId)) {
                int colon = docId.indexOf(':');
                if (colon >= 0) {
                    String root = docId.substring(0, colon);
                    String rel = docId.substring(colon + 1);
                    if ("primary".equalsIgnoreCase(root)) {
                        return TextUtils.isEmpty(rel) ? "Internal Storage" : rel;
                    }
                    return TextUtils.isEmpty(rel) ? root : (root + "/" + rel);
                }
                return docId;
            }
        } catch (Exception ignored) {
        }
        String lastSeg = treeUri.getLastPathSegment();
        return !TextUtils.isEmpty(lastSeg) ? lastSeg : getString(R.string.download_choose_folder);
    }

    private void startDownloadWithPermissionCheck() {
        if (!isAdded()) return;
        Context ctx = requireContext();
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && TextUtils.isEmpty(prefs.getDownloadTreeUri())
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (!needed.isEmpty()) {
            permissionLauncher.launch(needed.toArray(new String[0]));
        } else {
            enqueueDownloadAndShowTab();
        }
    }

    private void enqueueDownloadAndShowTab() {
        viewModel.startDownload();
        if (tabDownloadStatus != null && currentTab != 0) {
            TabLayout.Tab firstTab = tabDownloadStatus.getTabAt(0);
            if (firstTab != null) {
                firstTab.select();
            }
        }
    }

    private void rebuildQualityChips(String format) {
        if (!isAdded()) return;
        chipGroupQuality.removeAllViews();
        List<String> qualities = new ArrayList<>();
        if ("MP4".equalsIgnoreCase(format)) {
            if (currentMediaResult != null && !currentMediaResult.videoQualities.isEmpty()) {
                qualities.addAll(currentMediaResult.videoQualities);
            } else {
                qualities.add("720p");
                qualities.add("480p");
                qualities.add("360p");
            }
        } else if ("FLAC".equalsIgnoreCase(format)) {
            qualities.add("Lossless");
        } else {
            if (currentMediaResult != null && !currentMediaResult.audioQualities.isEmpty()) {
                qualities.addAll(currentMediaResult.audioQualities);
            } else {
                qualities.add("320 kbps");
                qualities.add("192 kbps");
                qualities.add("128 kbps");
            }
        }

        for (int i = 0; i < qualities.size(); i++) {
            String q = qualities.get(i);
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(q);
            chip.setCheckable(true);
            chip.setChipBackgroundColorResource(R.color.bg_chip);
            chipGroupQuality.addView(chip);
            if (i == 0) {
                chip.setChecked(true);
                viewModel.setSelectedQuality(q);
            }
        }

        chipGroupQuality.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip checkedChip = group.findViewById(checkedIds.get(0));
            if (checkedChip != null) {
                viewModel.setSelectedQuality(checkedChip.getText().toString());
            }
        });
    }

    private void pasteFromClipboard() {
        if (!isAdded()) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip()) {
            ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).coerceToText(requireContext());
                if (!TextUtils.isEmpty(text)) {
                    etUrl.setText(text.toString());
                    etUrl.setSelection(text.length());
                    return;
                }
            }
        }
        Toast.makeText(requireContext(), R.string.download_clipboard_empty, Toast.LENGTH_SHORT).show();
    }

    private void setupDownloadManager(View root) {
        RecyclerView rv = root.findViewById(R.id.rv_download_tasks);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DownloadTaskAdapter(new DownloadTaskAdapter.Listener() {
            @Override
            public void onActionClick(DownloadTaskEntity task) {
                if (DownloadTaskEntity.STATUS_DOWNLOADING.equals(task.status)
                        || DownloadTaskEntity.STATUS_QUEUED.equals(task.status)) {
                    viewModel.pauseDownload(task.id);
                } else {
                    viewModel.resumeOrRetryDownload(task.id);
                }
            }

            @Override
            public void onDeleteClick(DownloadTaskEntity task) {
                viewModel.cancelDownload(task.id);
            }

            @Override
            public void onCompletedItemClick(DownloadTaskEntity task) {
                if (!isAdded()) return;
                if (DownloadTaskEntity.MEDIA_VIDEO.equals(task.mediaType)) {
                    Toast.makeText(requireContext(),
                            R.string.download_video_saved_toast,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.findDownloadedSong(task.filePath, song -> {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (song != null) {
                            PlaybackController.getInstance(requireContext())
                                    .playSongList(Collections.singletonList(song), 0, getString(R.string.library_downloaded));
                            startActivity(new Intent(requireContext(), FullPlayerActivity.class));
                        } else {
                            Toast.makeText(requireContext(),
                                    R.string.download_audio_saved_toast,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }
        });
        rv.setAdapter(adapter);

        tabDownloadStatus = root.findViewById(R.id.tab_download_status);
        tabDownloadStatus.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                refreshDownloadList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void observeViewModel() {
        viewModel.getIsAnalyzing().observe(getViewLifecycleOwner(), loading ->
                progressAnalyzing.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));

        viewModel.getAnalyzeError().observe(getViewLifecycleOwner(), err -> {
            if (TextUtils.isEmpty(err)) {
                tvAnalyzeError.setVisibility(View.GONE);
            } else {
                tvAnalyzeError.setVisibility(View.VISIBLE);
                tvAnalyzeError.setText(err);
            }
        });

        viewModel.getAnalyzedMedia().observe(getViewLifecycleOwner(), info -> {
            currentMediaResult = info;
            if (info == null) {
                layoutAnalyzedResult.setVisibility(View.GONE);
            } else {
                layoutAnalyzedResult.setVisibility(View.VISIBLE);
                tvYtTitle.setText(info.title);
                tvYtChannel.setText(info.channelName);
                tvYtDuration.setText(info.isPlaylist
                        ? info.durationText
                        : getString(R.string.download_duration_format, info.durationText));
                tvSourceBadge.setText(info.isPlaylist
                        ? getString(R.string.download_playlist_badge_format, info.sourceName, info.playlistCount)
                        : info.sourceName);
                CoverLoader.loadSongArtwork(ivThumb, info.thumbnailUrl);
                chipFmtMp4.setVisibility(info.hasVideoStreams ? View.VISIBLE : View.GONE);
                chipGroupFormat.check(R.id.chip_fmt_mp3);
                viewModel.setSelectedFormat("MP3");
                rebuildQualityChips("MP3");
                if (MediaEngineInitializer.isYtdlpReady()) {
                    tvEngineStatus.setText(MediaEngineInitializer.getEngineSummary());
                }
            }
        });

        viewModel.getStatusEvent().observe(getViewLifecycleOwner(), msg -> {
            if (!TextUtils.isEmpty(msg) && isAdded()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                viewModel.clearStatusEvent();
            }
        });

        viewModel.observeActiveDownloads().observe(getViewLifecycleOwner(), list -> {
            activeList = list != null ? list : new ArrayList<>();
            if (currentTab == 0) refreshDownloadList();
        });

        viewModel.observeCompletedDownloads().observe(getViewLifecycleOwner(), list -> {
            completedList = list != null ? list : new ArrayList<>();
            if (currentTab == 1) refreshDownloadList();
        });

        viewModel.observeFailedDownloads().observe(getViewLifecycleOwner(), list -> {
            failedList = list != null ? list : new ArrayList<>();
            if (currentTab == 2) refreshDownloadList();
        });
    }

    private void refreshDownloadList() {
        List<DownloadTaskEntity> target;
        if (currentTab == 0) target = activeList;
        else if (currentTab == 1) target = completedList;
        else target = failedList;
        adapter.submitList(target);
        tvDownloadEmpty.setVisibility(target.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
