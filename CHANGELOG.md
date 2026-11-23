# Changelog

Tất cả các thay đổi đáng chú ý cho dự án này sẽ được ghi lại trong file này.

## [1.0.0] - Phiên bản đầu tiên

### Thêm mới
- Trình phát nhạc offline (MediaPlayer, BassBoost, điều khiển phát/tạm dừng/bài trước/bài sau)
- Mini player cố định dưới cùng màn hình
- Foreground service với notification điều khiển nhanh
- Thư viện: tất cả bài hát, bài nghe gần đây, bài đã tải, bài yêu thích, playlist, album, artist, radio
- Tìm kiếm đa tab (All / Songs / Artists / Albums / Playlists) với lịch sử tìm kiếm
- Tải nhạc đa nguồn (YouTube, SoundCloud, TikTok, Playlist…) qua yt-dlp-android
- Download Manager: theo dõi Đang tải / Đã hoàn thành / Thất bại
- Chọn định dạng (MP3/MP4) và chất lượng tải
- Profile cá nhân với thống kê (lượt nghe, thời gian, dung lượng offline)
- Equalizer (BassBoost), tốc độ phát (1.0×, crossfade)
- Theme Studio: 12 preset (6 sáng, 6 tối), gradient glow tự động từ album art
- Hỗ trợ 8 ngôn ngữ + hệ thống
- Import audio từ URI
- Quét nhạc trên thiết bị

### Công nghệ
- Java 11, Android SDK (minSdk 26, targetSdk 36)
- AndroidX (AppCompat, Material, RecyclerView, ViewPager2, Fragment, Palette, Media)
- Room 2.7.0 (CSDB cục bộ)
- Glide 4.16.0, OkHttp 4.10.0, Gson 2.11.0
- yt-dlp-android 0.18.1 (library, ffmpeg, aria2c)
- Kiến trúc: Repository + ViewModel + LiveData (Manual DI)
