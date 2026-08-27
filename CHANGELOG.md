# Changelog

## 2026-08-27 — DEL-01 Foundation

- Thêm skeleton Android multi-module `:app`, `:ui`, `:domain`, `:data`, `:platform` theo dependency direction đã khóa.
- Khóa baseline SDK ở version catalog: `minSdk=26`, `targetSdk=36`, `compileSdk=36`.
- Thêm manifest offline posture với `POST_NOTIFICATIONS` và `RECEIVE_BOOT_COMPLETED`; không khai báo `INTERNET` hoặc permission ngoài allowlist.
- Thêm `MainActivity` đặt `FLAG_SECURE` trước `setContent`, app shell Compose tối thiểu và resource `vi-VN`.
- Seed domain/data/platform foundation: canonical enum/time, `LocalStamp`/clock port, registry 48 event v1, duplicate JSON key guard và storage envelope contract.
- Thêm script `scripts/verify-foundation.ps1` để kiểm tra foundation gate cục bộ.

