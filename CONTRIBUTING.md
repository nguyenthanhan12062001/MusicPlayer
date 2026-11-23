# Hướng dẫn đóng góp

Cảm ơn bạn quan tâm đến dự án Music! Dưới đây là các nguyên tắc khi đóng góp.

## Tuyên bố quan trọng

- **Không chỉnh sửa source code Java/Kotlin** trừ khi được duyệt rõ ràng.
- **Không thay đổi AndroidManifest.xml, Gradle, dependencies hoặc cấu hình build.**
- **Không refactor, không di chuyển hoặc xóa source code.**
- Các thay đổi chỉ được tập trung vào tài liệu (README, CHANGELOG, CONTRIBUTING), hình ảnh giới thiệu và cấu hình Git (`.gitignore`).

## Cách đóng góp tài liệu

1. Fork repository này.
2. Tạo nhánh mới từ `main`: `git checkout -b feature/your-change`
3. Thực hiện thay đổi trên file tài liệu.
4. Commit với thông điệp rõ ràng, tiếng Việt hoặc tiếng Anh.
5. Push nhánh và mở Pull Request.

## Tiêu chí đánh giá Pull Request

- Nội dung mô tả phải đúng với tính năng thực tế của ứng dụng (đối chiếu source code).
- Không quảng cáo tính năng chưa triển khai.
- Không chứa thông tin nhạy cảm (API key, keystore, mật khẩu…).
- Không làm thay đổi source code, cấu hình build hoặc package structure.

## Báo lỗi

Nếu phát hiện sai sót trong tài liệu (tính năng bị mô tả sai, thiếu ảnh, đường dẫn hỏng…), vui lòng mở Issue với mô tả chi tiết.
