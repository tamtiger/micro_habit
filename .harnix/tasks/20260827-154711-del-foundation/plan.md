# Plan — DEL-01 Foundation

## Ready checkpoint

Outcome là hoàn thiện toàn bộ canonical DEL-01, không dừng ở skeleton hiện có. Baseline khóa tại SDK `26/36/36`, AGP `8.12.1`, Gradle `8.13`, JDK `17`; toolchain và API-36 AVD chỉ được dùng tạm, không cài system-wide. Navigation graph thuộc `:app`, shared codec thuộc `:domain`, Room/Keystore thuộc `:data`, Android clock adapter thuộc `:platform`, còn `:ui` chỉ render bằng semantic tokens. Không có quyết định vật chất nào còn mở trước khi implement.

## Checklist

- [x] `S1` — Khóa module graph, build stack và Gradle Wrapper
- [x] `S2` — Hoàn thiện app shell, navigation, manifest và locale
- [x] `S3` — Implement encrypted Room clock state và clock foundation
- [x] `S4` — Implement shared closed WireV1 và event codec
- [x] `S5` — Hoàn thiện static, boundary, manifest và changelog gates
- [ ] `S6` — Chạy full build/device verification, dọn toolchain tạm và persist evidence

### Slice `S1`

Criteria: `AC-DEL01-001`, `AC-DEL01-007`

Checks: `CHK-DEL01-WRAPPER`

Paths: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`, `app/build.gradle.kts`, `ui/build.gradle.kts`, `domain/build.gradle.kts`, `data/build.gradle.kts`, `platform/build.gradle.kts`, `scripts/verify-wrapper.ps1`

1. Viết test/gate wrapper trước để xác nhận trạng thái hiện tại thất bại đúng vì thiếu wrapper.
2. Tải và xác minh checksum Gradle `8.13` bằng toolchain tạm đã được cấp quyền.
3. Dùng Gradle `wrapper` task để sinh đủ bốn artifact chính thức; khóa `distributionSha256Sum` và kiểm SHA-256 của Wrapper JAR.
4. Chuẩn hóa version catalog/build files cho AGP `8.12.1`, JDK `17`, Kotlin và Android SDK `26/36/36`.
5. Giữ dependency direction `:app -> :ui/:domain/:data/:platform`, `:ui/:data/:platform -> :domain`, `:domain -> Kotlin/JDK`.
6. Thêm thư mục toolchain/cache tạm vào `.gitignore`; không đưa binary tạm hoặc AVD vào source control.
7. Chạy focused wrapper gate và chỉ đánh dấu slice hoàn tất sau khi checksum, scripts và properties đều xanh.

### Slice `S2`

Criteria: `AC-DEL01-002`, `AC-DEL01-003`, `AC-DEL01-005`, `AC-DEL01-010`, `AC-DEL01-011`

Checks: `CHK-DEL01-ARC109`, `CHK-DEL01-ARC109-R2`

Paths: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/vn/nhip2phut/app/MainActivity.kt`, `app/src/main/kotlin/vn/nhip2phut/app/Nhip2PhutApplication.kt`, `app/src/main/kotlin/vn/nhip2phut/app/AppContainer.kt`, `app/src/main/kotlin/vn/nhip2phut/app/navigation/**`, `app/src/main/res/values/**`, `app/src/main/res/values-b+vi+VN/**`, `app/src/androidTest/**`, `ui/src/main/kotlin/**`, `ui/src/main/res/values/**`, `ui/src/main/res/values-b+vi+VN/**`, `scripts/verify-merged-manifest.ps1`

1. Viết focused tests cho application-scoped `AppContainer`, route khởi đầu, resource parity và shell render.
2. Bảo đảm `MainActivity` đặt `FLAG_SECURE` trước `setContent`, lấy đúng `AppContainer` từ `Nhip2PhutApplication` và truyền vào app-owned navigation shell.
3. Tạo typed destinations và `NavHost` trong `:app`; `:ui` chỉ export screen composables, state và callbacks.
4. Thay hard-coded presentation values bằng semantic color, typography, shape và spacing tokens.
5. Tạo resource qualifier exact `values-b+vi+VN` và kiểm key parity với resource default.
6. Khóa manifest offline, backup disabled, cleartext disabled, launcher/component export policy và exact permission allowlist.
7. Đăng ký `verifyDebugMergedManifest` bằng AGP merged-manifest artifact provider và kiểm artifact đã merge thay vì dò build path.
8. Chạy cả frozen check và check `-R2`; check `-R2` là cú pháp canonical, còn frozen command chạy qua PowerShell alias chỉ tồn tại trong process và trỏ tới cùng checked-in wrapper.

### Slice `S3`

Criteria: `AC-DEL01-008`

Checks: `CHK-DEL01-STORAGE`, `CHK-DEL01-STORAGE-R2`

Paths: `domain/src/main/kotlin/vn/nhip2phut/domain/time/**`, `domain/src/test/kotlin/vn/nhip2phut/domain/time/**`, `data/build.gradle.kts`, `data/src/main/kotlin/vn/nhip2phut/data/crypto/**`, `data/src/main/kotlin/vn/nhip2phut/data/storage/**`, `data/src/test/kotlin/**`, `data/src/androidTest/**`, `data/schemas/**`, `platform/src/main/kotlin/vn/nhip2phut/platform/time/**`, `platform/src/test/kotlin/vn/nhip2phut/platform/time/**`

1. Viết RED tests cho canonical AAD bytes, version/type/range validation, nonce uniqueness, tamper rejection, missing-key behavior, Room mapping và fake-clock discontinuity.
2. Định nghĩa pure contracts cho clock snapshot, clock state, generation, fake clock và timezone fixtures.
3. Implement crypto envelope v1 với magic ASCII `N2PENC01`, AES-GCM, nonce 12 byte, tag 128 bit và canonical AAD gắn table/column/primary key.
4. Implement AndroidKeyStore key provider với alias versioned, randomized encryption và fail-closed khi alias/version/tag không hợp lệ.
5. Tạo `nhip2phut.db` Room schema v1 chỉ với production table `clock_state`, singleton key `1`, encrypted payload và required crypto/schema metadata.
6. Không dùng destructive migration; export Room schema và dùng migration harness để tạo, reopen và kiểm schema v1.
7. Sửa Android clock adapter để dùng boot count, elapsed realtime, wall mapping, zone và durable generation; không dùng elapsed nanos làm boot marker.
8. Thêm on-device Room/Keystore round-trip, reopen và tamper tests; refactor khi toàn bộ focused suite vẫn xanh.
9. Chạy cả frozen storage check và `CHK-DEL01-STORAGE-R2` trên cùng checked-in wrapper.

### Slice `S4`

Criteria: `AC-DEL01-004`, `AC-DEL01-009`

Checks: `CHK-DEL01-CODEC`, `CHK-DEL01-CODEC-R2`

Paths: `domain/build.gradle.kts`, `domain/src/main/kotlin/vn/nhip2phut/domain/model/**`, `domain/src/main/kotlin/vn/nhip2phut/domain/events/**`, `domain/src/main/kotlin/vn/nhip2phut/domain/wire/v1/**`, `domain/src/test/kotlin/vn/nhip2phut/domain/events/**`, `domain/src/test/kotlin/vn/nhip2phut/domain/wire/v1/**`

1. Viết generated RED matrices cho missing/additional/duplicate key, alias, wrong type, coercion, enum case, nullability, XOR/conditional branch, mirror, event mask và root-array order.
2. Implement strict JSON v1 và canonical scalar codecs cho enum, UUID, integer, date/time, `LocalStamp` và nullable/discriminated shapes.
3. Implement `ProfileWireV1`, `WorkScheduleWireV1`, `CheckInWireV1`, `DecisionWireV1`, `SessionWireV1`, `FeedbackWireV1`, `ReminderWireV1` và `WeeklySummaryWireV1`.
4. Implement root với exact chín arrays `profile`, `work_schedule`, `check_ins`, `decisions`, `sessions`, `feedback`, `reminders`, `events`, `weekly_summaries`.
5. Thay loose property representation bằng 48 typed event property contracts và một `EventContractRegistryV1` đóng, chứa envelope mask, property codec, conditional rule, reference plan, companion plan và idempotency plan.
6. Cung cấp một codec/registry entry point dùng chung cho exporter, importer và validator; không có production API nhận loose property map hoặc serializer riêng tách schema switch.
7. Thêm parity/golden tests xác nhận đủ đúng 48 events, không duplicate name và mọi event khớp chính xác một registry row.
8. Chạy cả frozen codec check và `CHK-DEL01-CODEC-R2`; refactor chỉ khi generated suite tiếp tục xanh.

### Slice `S5`

Criteria: `AC-DEL01-001`, `AC-DEL01-002`, `AC-DEL01-003`, `AC-DEL01-004`, `AC-DEL01-005`, `AC-DEL01-006`, `AC-DEL01-010`

Checks: `CHK-DEL01-STATIC`, `CHK-DEL01-GIT`, `CHK-DEL01-ARC101`

Paths: `CHANGELOG.md`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/**`, `ui/**`, `domain/**`, `data/**`, `platform/**`, `scripts/verify-foundation.ps1`, `scripts/verify-module-boundaries.ps1`, `scripts/verify-merged-manifest.ps1`, `scripts/verify-wrapper.ps1`

1. Mở rộng foundation script để kiểm SDK baseline, module includes/dependencies, official wrapper, `FLAG_SECURE`, locale parity, codec registry và `CHANGELOG` top entry.
2. Tạo module-boundary script kiểm `:domain` không có Android import/dependency, `:ui` không truy cập DAO/Room/Keystore/AlarmManager và không có lateral dependency ngoài graph.
3. Kiểm source manifest cùng merged manifest để phát hiện permission/component/action do source hoặc dependency kéo vào; cấm `ACTION_CALL` và mọi permission ngoài allowlist.
4. Cập nhật `CHANGELOG.md` với DEL-01 ở đầu, mô tả module shell, navigation, encrypted clock state, shared codec và verification gates.
5. Chạy static, boundary và git status gates; git gate chỉ kiểm phạm vi thay đổi, không tạo commit.
6. Bảo toàn mọi user-owned change không thuộc DEL-01 và không sửa lịch sử commit.

### Slice `S6`

Criteria: `AC-DEL01-001`, `AC-DEL01-003`, `AC-DEL01-004`, `AC-DEL01-005`, `AC-DEL01-008`, `AC-DEL01-010`, `AC-DEL01-011`

Checks: `CHK-DEL01-GRADLE`, `CHK-DEL01-DEVICE`, `CHK-DEL01-DEVICE-R2`

Paths: `.gitignore`, `CHANGELOG.md`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/**`, `gradlew`, `gradlew.bat`, `app/**`, `ui/**`, `domain/**`, `data/**`, `platform/**`, `scripts/**`

1. Cấu hình root `verifyFoundation` để chạy `:domain:test`, `:data:testDebugUnitTest`, `:platform:testDebugUnitTest`, `:app:testDebugUnitTest`, app lint, assemble và merged-manifest verification bằng dependency task rõ ràng.
2. Cấu hình device gate cho app shell và data Room/Keystore tests.
3. Cài exact API 36 package cùng một API-36 system image/temporary AVD nếu chưa có device phù hợp; xác minh device API trước khi test.
4. Với từng required check, lấy Harnix snapshot ngay trước và ngay sau command; chỉ persist pass khi hai digest khớp.
5. Chạy `CHK-DEL01-GRADLE`, sau đó frozen device check và `CHK-DEL01-DEVICE-R2` trên cùng official wrapper.
6. Chạy lại các focused/static/wrapper/merged-manifest checks nếu full build tạo hoặc thay đổi completion-relevant input.
7. Dọn đúng portable JDK/Gradle cache, SDK package/system image và temporary AVD đã tạo cho task; giữ checked-in Gradle Wrapper.
8. Kiểm git status cuối, map fresh evidence tới từng acceptance criterion, chuyển sang verification/finish theo Harnix workflow.
9. Không commit hoặc push; nếu cần commit, trình diff và commit message rồi chờ người dùng cho phép riêng.
