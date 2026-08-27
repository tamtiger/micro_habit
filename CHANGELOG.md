# Changelog

## 2026-08-27 — DEL-01 Foundation

- Thêm skeleton Android multi-module `:app`, `:ui`, `:domain`, `:data`, `:platform` theo dependency direction đã khóa.
- Khóa baseline SDK ở version catalog: `minSdk=26`, `targetSdk=36`, `compileSdk=36`.
- Thêm manifest offline posture với `POST_NOTIFICATIONS` và `RECEIVE_BOOT_COMPLETED`; không khai báo `INTERNET` hoặc permission ngoài allowlist.
- Hoàn thiện app-owned Navigation Compose shell, application-scoped `AppContainer`, semantic design tokens và resource qualifier exact `vi-VN`.
- Thêm shared closed WireV1 codec trong `:domain`: Profile, sáu entity, WeeklySummary, root chín arrays và typed registry đủ 48 event.
- Thêm `nhip2phut.db` Room schema v1 với encrypted `clock_state`, canonical AES-GCM AAD, AndroidKeyStore, migration harness và fake clock/timezone fixtures.
- Thêm official Gradle Wrapper 8.13 cùng checksum khóa, static/module-boundary/merged-manifest/unit/lint/assemble và API-36 connected gates.

