# PRD — DEL-01 Foundation

## Outcome

DEL-01 tạo nền móng Android MVP có thể phát triển các phase sau: cấu trúc module, baseline SDK, app shell offline, boundary kiểm thử được, seed codec/storage và validation script.

### AC `AC-DEL01-001`

Repo có Android multi-module skeleton theo ARC dependency direction gồm `:app`, `:ui`, `:domain`, `:data`, `:platform`; SDK baseline `minSdk=26`, `targetSdk=36`, `compileSdk=36` được khai báo tập trung, có Gradle Wrapper/build gate và có script kiểm tra.

### AC `AC-DEL01-002`

Manifest và config giữ offline posture: không khai báo `INTERNET`, health, calendar, location, activity recognition, billing, storage broad permission hoặc `CALL_PHONE`; chỉ cho phép `POST_NOTIFICATIONS` và `RECEIVE_BOOT_COMPLETED` khi cần cho notification/reminder foundation.

### AC `AC-DEL01-003`

App shell có `MainActivity` đặt `FLAG_SECURE` trước `setContent`, composition root `AppContainer`, Compose navigation shell tối thiểu và resource `vi-VN` để các phase sau gắn flow.

### AC `AC-DEL01-004`

Foundation có seed closed codec/storage cho enum/time/LocalStamp/event registry và test/fixture kiểm tra canonical token, duplicate JSON key, schema version và boundary fail-closed.

### AC `AC-DEL01-005`

Có script kiểm tra foundation/CI gate cục bộ cho SDK baseline, module boundary, manifest permission allowlist, `FLAG_SECURE`, codec/test files và `CHANGELOG` top-entry.

### AC `AC-DEL01-006`

`CHANGELOG.md` được tạo/cập nhật với entry mới nhất ở đầu, ghi rõ thay đổi phase `DEL-01`.