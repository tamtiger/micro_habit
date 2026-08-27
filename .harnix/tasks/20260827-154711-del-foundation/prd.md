# PRD — DEL-01 Foundation

## Mục tiêu

Hoàn thiện DEL-01 canonical thành Android foundation chạy được: module/SDK, app shell Navigation Compose, encrypted Room và clock foundation, shared closed WireV1/event codec, Gradle Wrapper cùng static/unit/instrumented/merged-manifest gates.

## Kết quả mong đợi

DEL-01 cung cấp một nền móng Android có thể build và kiểm chứng độc lập trước khi triển khai nghiệp vụ DEL-02:

- Cấu trúc module và dependency direction được khóa bằng cấu hình build cùng static gate.
- App shell có composition root application-scoped, Navigation Compose thuộc `:app`, màn hình thuần render thuộc `:ui`, semantic design tokens và locale exact `vi-VN`.
- `nhip2phut.db` schema v1 có production table `clock_state` được mã hóa bằng AES-GCM/AndroidKeyStore với canonical AAD và hành vi fail-closed.
- `:domain` chứa một shared closed codec v1 cho toàn bộ WireV1 root và đủ 48 typed event contracts; mọi entry point dùng cùng registry.
- Gradle Wrapper 8.13, unit/lint/assemble/static/merged-manifest/API-36 instrumented gates chạy được bằng toolchain tạm đã được người dùng cho phép.
- `CHANGELOG.md` phản ánh DEL-01 ở entry mới nhất.

## Ngoài phạm vi

- Không implement onboarding, check-in, rule engine hoặc transaction nghiệp vụ của DEL-02.
- Không implement scheduler/player/weekly aggregation/export/delete business flow; DEL-01 chỉ tạo typed DTO/codec, clock_state encrypted storage và reusable foundation contracts.
- Không thêm account, network runtime, AI, backend, remote analytics, billing hoặc permission ngoài allowlist.
- Không cài toolchain system-wide, không đổi baseline SDK 26/36/36 và không commit/push tự động.

## Quyết định đã khóa

- Baseline giữ nguyên `minSdk=26`, `targetSdk=36`, `compileSdk=36`.
- Build stack dùng AGP `8.12.1`, Gradle Wrapper `8.13` và JDK `17`.
- Toolchain tải về chỉ tồn tại tạm trong phạm vi project hoặc thư mục tạm đã xác định; checksum publisher được xác minh trước khi chạy.
- API 36 exact và system image/AVD API 36 chỉ được cài tạm khi cần cho instrumented gate, sau đó dọn đúng phạm vi.
- Navigation graph và application composition root thuộc `:app`; `:ui` không sở hữu navigation runtime hay truy cập storage/platform adapter.
- Shared closed codec thuộc `:domain` và giữ module này thuần Kotlin/JDK.
- DEL-01 chỉ tạo production table `clock_state`; không tạo trước bảng nghiệp vụ DEL-02.
- Magic cho crypto envelope/AAD v1 là ASCII byte-exact `N2PENC01`.
- Bốn required check có hậu tố `-R2` là command canonical dùng `.\gradlew.bat`; các check không có hậu tố được giữ để bảo toàn lịch sử immutable của TaskRecord.

## Acceptance criteria

### AC `AC-DEL01-001`

Repo có Android multi-module skeleton theo ARC dependency direction gồm :app, :ui, :domain, :data, :platform và baseline minSdk=26,targetSdk=36,compileSdk=36 được khai báo tập trung.

### AC `AC-DEL01-002`

App manifest và foundation config giữ offline posture: không INTERNET/health/calendar/location/activity/billing/CALL_PHONE; chỉ allowlist permission nền tảng cho notification và boot reconcile khi cần.

### AC `AC-DEL01-003`

App shell có MainActivity đặt FLAG_SECURE trước setContent, AppContainer composition root, navigation shell Compose và resource vi-VN tối thiểu.

### AC `AC-DEL01-004`

Foundation có closed codec/storage seed cho canonical enum/time/LocalStamp/event registry và test/verification phát hiện default sai, duplicate JSON key hoặc schema switch tách rời.

### AC `AC-DEL01-005`

Có script kiểm tra foundation/CI gate cục bộ cho SDK baseline, module boundary, manifest permission allowlist, FLAG_SECURE, CHANGELOG top-entry và repo status.

### AC `AC-DEL01-006`

CHANGELOG.md được tạo/cập nhật với entry mới nhất ở đầu, ghi rõ thay đổi của phase DEL-01.

### AC `AC-DEL01-007`

Repo có Gradle Wrapper 8.13 chính thức gồm gradlew, gradlew.bat, gradle-wrapper.jar và gradle-wrapper.properties; distribution/JAR SHA-256 được khóa và verification phát hiện wrapper thiếu hoặc sai.

### AC `AC-DEL01-008`

Foundation có encrypted Room production table clock_state trong nhip2phut.db schema v1, AES-GCM/AndroidKeyStore với canonical AAD và fail-closed, migration harness không destructive, cùng fake clock/timezone và round-trip/tamper tests.

### AC `AC-DEL01-009`

Một shared closed codec v1 trong domain bao phủ ProfileWireV1, sáu entity WireV1, WeeklySummaryWireV1, root chín arrays và typed registry đủ 48 event; exporter/importer/validator dùng cùng registry và generated negatives reject mọi shape không canonical.

### AC `AC-DEL01-010`

Foundation verification chạy module-boundary, lint, unit, assemble, merged-manifest allowlist và API-36 instrumented gates; không source/binary permission, component, ACTION_CALL hoặc dependency direction ngoài allowlist.

### AC `AC-DEL01-011`

App shell đặt navigation graph tại app, truyền application-scoped AppContainer, dùng semantic design tokens và có resource qualifier exact vi-VN với parity key so với default.

## Chiến lược kiểm chứng

- Wrapper và checksum: `CHK-DEL01-WRAPPER`.
- Module graph và forbidden import: `CHK-DEL01-ARC101`.
- Static foundation và repository scope: `CHK-DEL01-STATIC`, `CHK-DEL01-GIT`.
- Build/lint/unit/assemble tổng hợp: `CHK-DEL01-GRADLE`.
- Merged manifest: `CHK-DEL01-ARC109`, `CHK-DEL01-ARC109-R2`.
- Closed codec và event registry: `CHK-DEL01-CODEC`, `CHK-DEL01-CODEC-R2`.
- Storage, crypto và clock unit suites: `CHK-DEL01-STORAGE`, `CHK-DEL01-STORAGE-R2`.
- App shell và Room/Keystore trên API 36: `CHK-DEL01-DEVICE`, `CHK-DEL01-DEVICE-R2`.

## Điều kiện sẵn sàng implement

Không còn lựa chọn sản phẩm hoặc quyền hạn nào có thể thay đổi outcome. Người dùng đã cho phép tải mạng và cài toolchain tạm trong phạm vi đã nêu; baseline, module ownership, crypto framing, schema v1, codec ownership, device target và cleanup policy đều đã được khóa.
