# Design — DEL-01 Foundation

## 1. Mục đích

Thiết kế này khóa kiến trúc cho DEL-01 để repository chuyển từ skeleton sang một foundation Android build được, test được và có boundary rõ ràng. Nó không triển khai flow nghiệp vụ DEL-02 nhưng phải cung cấp đủ reusable contracts cho navigation, encrypted operational clock state, canonical WireV1/event codec và release gates.

## 2. Module ownership

Dependency graph được khóa như sau:

- `:app` phụ thuộc `:ui`, `:domain`, `:data`, `:platform`.
- `:ui` chỉ phụ thuộc `:domain` và Compose presentation libraries.
- `:data` phụ thuộc `:domain`, Room và Android crypto/storage APIs.
- `:platform` phụ thuộc `:domain` và Android platform APIs.
- `:domain` chỉ dùng Kotlin/JDK cùng pure-JVM serialization dependencies cần thiết.
- Không có lateral dependency `:ui -> :data/:platform`, `:data -> :platform/:ui`, hoặc `:platform -> :data/:ui`.

Ownership cụ thể:

- `:app`: `Application`, `AppContainer`, `MainActivity`, Navigation Compose graph, route ownership và wiring adapter.
- `:ui`: stateless/state-hoisted screen composables, semantic design tokens, accessibility semantics và presentation resources.
- `:domain`: canonical scalar/time contracts, WireV1 DTOs, strict codec, dataset validator contracts và typed event registry.
- `:data`: Room database, `clock_state` DAO/repository, crypto envelope, canonical AAD và AndroidKeyStore adapter.
- `:platform`: Android clock snapshot, boot/zone/time-change signals và platform-only adapters.

Static module-boundary gate kiểm cả Gradle dependency graph lẫn forbidden imports để source không vượt ownership này.

## 3. App shell và navigation

`Nhip2PhutApplication` tạo đúng một application-scoped `AppContainer`. `MainActivity` lấy container từ application, gọi `window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)` trước `setContent`, sau đó truyền container vào `Nhip2PhutNavHost`.

Navigation graph nằm trong `:app`:

- `AppDestination` là closed typed destination contract.
- `Nhip2PhutNavHost` tạo `NavHostController`, start destination và route wiring.
- `:ui` export screen composables nhận immutable UI state và callbacks; nó không import navigation runtime, Room, DAO, Keystore hoặc AlarmManager.
- Route đầu của DEL-01 chỉ chứng minh shell/wiring hoạt động; không dựng onboarding/check-in flow.

Manifest giữ offline posture:

- Chỉ `POST_NOTIFICATIONS` và `RECEIVE_BOOT_COMPLETED` được phép khi component foundation thực sự sử dụng.
- Không có `INTERNET`, network state, health, calendar, location, activity recognition, billing, broad storage hoặc `CALL_PHONE`.
- `MainActivity` chỉ exported vì launcher intent.
- Receiver nội bộ exported false và chỉ nhận protected system actions đã allowlist.
- Backup/device transfer và cleartext traffic bị tắt theo security contract.
- Merged-manifest gate kiểm artifact sau dependency merge, không chỉ source manifest.

## 4. Semantic design tokens và localization

`:ui` định nghĩa semantic tokens thay vì hard-code giá trị theo từng screen: color roles, typography roles, shape roles và spacing roles. `Nhip2PhutTheme` là điểm duy nhất ánh xạ tokens sang Material theme.

Default resources và exact BCP-47 Android qualifier `values-b+vi+VN` có cùng tập key. Verification fail nếu default thiếu key, Vietnamese thiếu key hoặc một bên có key ngoài parity set. Existing generic `values-vi` không được dùng để thay thế bằng chứng exact `vi-VN`.

## 5. Shared closed WireV1 codec

Codec nằm dưới `domain/.../wire/v1` và không phụ thuộc Android.

### 5.1 Thành phần

- `StrictJsonV1`: parser/decoder phát hiện duplicate key trước khi bind DTO và từ chối unknown/missing field.
- `WireScalarsV1`: codecs cho UUID, integer range, canonical enum token, local date/time, instant và `LocalStamp`.
- DTOs: `ProfileWireV1`, `WorkScheduleWireV1`, `CheckInWireV1`, `DecisionWireV1`, `SessionWireV1`, `FeedbackWireV1`, `ReminderWireV1` và `WeeklySummaryWireV1`.
- Dataset root có exact chín arrays: `profile`, `work_schedule`, `check_ins`, `decisions`, `sessions`, `feedback`, `reminders`, `events`, `weekly_summaries`.
- `EventContractRegistryV1` chứa đủ đúng 48 event specs.
- `ClosedCodecV1` là entry point duy nhất cho encode/decode.
- `DatasetConformanceV1` dùng chính DTO/registry đó để validate cross-record shape.

### 5.2 Event contract

Mỗi event có một typed properties class riêng và một `EventSpecV1<P>` duy nhất chứa canonical event name, exact envelope nullable-slot mask, typed property codec, required/forbidden property set, conditional/XOR rules, entity reference plan, companion reference plan, idempotency preimage plan và mirror rules.

Không có production API nhận `Map<String, Any?>`, loose JSON properties hoặc một schema switch thứ hai. Exporter, importer và validator cùng gọi `ClosedCodecV1` và cùng tra `EventContractRegistryV1`.

### 5.3 Strictness

Decoder từ chối missing/additional/duplicate key, alias, enum token sai case, coercion, nullability flip, sai discriminated union/XOR/conditional branch, property ngoài exact event, event khớp zero hoặc nhiều registry row, root array sai shape và mirror/reference/companion rule không khớp. Generated tests duyệt registry cùng mọi mutation class này để schema change buộc phải cập nhật một closed source of truth.

## 6. Encrypted Room clock foundation

### 6.1 Database schema

Database production tên `nhip2phut.db`, Room schema version `1`. DEL-01 chỉ thêm table `clock_state`; không tạo trước bảng profile, check-in, decision, session hoặc event.

`clock_state` là singleton với các cột:

- `singleton_id = 1`.
- `crypto_version`, `key_version` và `payload_schema_version` là plaintext dispatch metadata được AAD authenticate.
- `encrypted_payload` là envelope chứa nonce cùng ciphertext/tag của durable `clockGeneration`, last boot marker, zone ID, elapsed checkpoint và wall-minus-elapsed mapping.

Encrypted blob không được log, đưa vào exception message hoặc chuyển thành String. Room schema được export vào repository. Không gọi `fallbackToDestructiveMigration`. Migration harness tạo/open/reopen schema v1 và sẵn sàng gắn migration thật khi có version mới; DEL-01 không invent migration `1 -> 2`.

### 6.2 Crypto envelope

Thuật toán là `AES/GCM/NoPadding`:

- Magic ASCII byte-exact: `N2PENC01`.
- Nonce: 12 byte mới từ `SecureRandom` cho mỗi encryption.
- Authentication tag: 128 bit.
- Key ưu tiên AES-256, chỉ dùng AES-128 khi thiết bị hợp lệ không hỗ trợ 256.
- Key nằm trong AndroidKeyStore với alias `nhip2phut_data_v{keyVersion}`, randomized encryption required và không export được.
- Missing alias khi đã có ciphertext, unknown version, malformed envelope hoặc authentication failure đều fail closed; implementation không tự sinh key mới để ghi đè dữ liệu cũ.

Canonical AAD được encode theo thứ tự: `magic`, `crypto_version`, `key_version`, `payload_schema_version`, `table_name`, `column_name`, `record_primary_key`. Mỗi top-level component dùng `uint32_be(byte_length) || bytes`; ba version payload là exact 4-byte `uint32_be`. String dùng UTF-8 byte-exact. Primary key bắt đầu bằng type tag; `clock_state` dùng `0x02 || signed_int64_be(1)`. Tuple v1 gắn exact table `clock_state`, column `encrypted_payload` và singleton key `1`, nên chuyển ciphertext sang row/column hoặc sửa header sẽ không authenticate.

Pure AAD/envelope code có JVM golden tests; AndroidKeyStore và Room round-trip/tamper behavior có instrumented tests.

## 7. Clock contracts

`:domain` định nghĩa immutable `ClockSnapshot` cùng interfaces cho coherent time read. Snapshot chứa wall instant, `elapsedRealtimeMillis`, verified boot marker, durable `clockGeneration`, `ZoneId`, UTC offset và wall-minus-elapsed mapping khi cần.

`:platform` implement Android clock bằng `System.currentTimeMillis`, `SystemClock.elapsedRealtime`, OS boot count và current zone. `elapsedRealtimeNanos()` không được dùng làm boot marker.

`clockGeneration` được lưu trong encrypted `clock_state` và tăng khi time/timezone signal yêu cầu reconcile. Nếu boot marker không đọc/xác minh được, elapsed lùi, state decrypt thất bại hoặc wall mapping drift ngoài contract, resolver trả discontinuity/unknown thay vì fallback sang wall-only authorization.

Reusable `FakeClock` cho phép test advance wall/elapsed, thay zone, mô phỏng reboot, increment generation, wall rollback, mapping drift và freeze deterministic snapshots.

## 8. Toolchain và Gradle Wrapper

Build compatibility được khóa tại AGP `8.12.1`, Gradle Wrapper `8.13`, JDK `17`, `compileSdk=36`, `targetSdk=36` và `minSdk=26`.

Wrapper được sinh bằng Gradle wrapper task và giữ đủ `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`. `distributionSha256Sum` là `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`. Wrapper JAR SHA-256 là `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.

Portable Microsoft OpenJDK 17 và Gradle bootstrap được tải vào phạm vi tạm, xác minh publisher checksum trước khi giải nén/chạy. Project-scoped Gradle cache, exact API 36 package, system image và temporary AVD được dọn sau verification. Không thay đổi system-wide PATH, registry hoặc baseline SDK.

## 9. Verification architecture

### 9.1 Static gates

- `verify-wrapper.ps1`: đủ wrapper files, exact version, distribution checksum và Wrapper JAR checksum.
- `verify-module-boundaries.ps1`: module includes, dependency direction và forbidden imports.
- `verify-foundation.ps1`: SDK baseline, offline manifest source posture, `FLAG_SECURE`, AppContainer/nav/resources, codec/storage files, tests và changelog top entry.
- `verify-merged-manifest.ps1`: exact permission/component/action allowlist trên merged artifact.

`verifyDebugMergedManifest` nhận input từ AGP `SingleArtifact.MERGED_MANIFEST` provider. Gate không glob build directory và không tin source manifest thay cho merged output.

### 9.2 Gradle gates

Root `verifyFoundation` tổng hợp `:domain:test`, `:data:testDebugUnitTest`, `:platform:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` và `:app:verifyDebugMergedManifest`.

Device verification chạy `:app:connectedDebugAndroidTest` và `:data:connectedDebugAndroidTest` trên device API 36. App tests kiểm shell/AppContainer/navigation/resource behavior; data tests kiểm Room schema/reopen, AndroidKeyStore round-trip, AAD binding và tamper fail-closed.

### 9.3 Frozen command correction

TaskRecord giữ bốn initial Gradle commands thiếu path separator vì required-check definitions đã immutable. Bốn check `-R2` là command canonical dùng `.\gradlew.bat`. Khi cần ghi evidence cho frozen checks, verification process tạo PowerShell alias tên `.gradlew.bat` chỉ trong process, trỏ thẳng tới checked-in `gradlew.bat` đã được checksum gate xác nhận, rồi chạy exact frozen command. Không tạo wrapper thứ hai, không đổi persistent `PATH` và không thay command definition.

## 10. Failure behavior

- Codec failure trả typed conformance error và không silently default/coerce.
- Crypto/Room failure không sinh authorized clock state mới từ dữ liệu không authenticate.
- Unknown schema/version dừng tại boundary.
- Merged manifest violation fail build.
- Device unavailable hoặc sai API không được chuyển thành pass; gate chỉ xanh sau API-36 execution thật.
- Input thay đổi giữa pre/post Harnix snapshot làm evidence không hợp lệ và check phải chạy lại.
- Cleanup chỉ xóa exact temporary directories/packages/AVD đã tạo cho task.

## 11. Non-goals

- Không implement onboarding, check-in, rule engine hoặc transaction nghiệp vụ của DEL-02.
- Không implement scheduler/player/weekly aggregation/export/delete business flow; DEL-01 chỉ tạo typed DTO/codec, clock_state encrypted storage và reusable foundation contracts.
- Không thêm account, network runtime, AI, backend, remote analytics, billing hoặc permission ngoài allowlist.
- Không cài toolchain system-wide, không đổi baseline SDK 26/36/36 và không commit/push tự động.
