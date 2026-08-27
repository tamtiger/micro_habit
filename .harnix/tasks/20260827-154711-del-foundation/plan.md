# Plan — DEL-01 Foundation

## Checklist

- [x] `S1` — Gradle/module skeleton
- [x] `S2` — App shell và manifest offline
- [x] `S3` — Domain/data/platform foundation codec-storage seed
- [x] `S4` — Verification script và changelog top-entry
- [ ] `S5` — Verify, finish task và commit phase

### Slice `S1`

Criteria: `AC-DEL01-001`

Checks: `CHK-DEL01-STATIC`

Paths: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `ui/build.gradle.kts`, `domain/build.gradle.kts`, `data/build.gradle.kts`, `platform/build.gradle.kts`

Tạo multi-module Android/Kotlin skeleton, khai báo dependency direction theo `ARC-012`, và đặt baseline SDK tập trung để script có thể khóa `26/36/36`.

### Slice `S2`

Criteria: `AC-DEL01-002`, `AC-DEL01-003`

Checks: `CHK-DEL01-STATIC`

Paths: `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/**`, `app/src/main/res/**`, `ui/src/main/kotlin/**`

Tạo app shell, `MainActivity`, `AppContainer`, Compose screen tối thiểu và manifest offline allowlist với `FLAG_SECURE` trước `setContent`.

### Slice `S3`

Criteria: `AC-DEL01-004`

Checks: `CHK-DEL01-STATIC`

Paths: `domain/src/main/kotlin/**`, `data/src/main/kotlin/**`, `platform/src/main/kotlin/**`, `domain/src/test/kotlin/**`, `data/src/test/kotlin/**`

Seed canonical model/codec registry/storage interfaces để phase sau mở rộng mà không tách schema switch; thêm tests/fixtures cho canonical token và duplicate key guard ở mức foundation.

### Slice `S4`

Criteria: `AC-DEL01-005`, `AC-DEL01-006`

Checks: `CHK-DEL01-STATIC`, `CHK-DEL01-GRADLE`, `CHK-DEL01-GIT`

Paths: `scripts/verify-foundation.ps1`, `CHANGELOG.md`

Tạo verification script và cập nhật `CHANGELOG.md` với entry mới ở đầu cho `DEL-01`.

### Slice `S5`

Criteria: `AC-DEL01-001`, `AC-DEL01-002`, `AC-DEL01-003`, `AC-DEL01-004`, `AC-DEL01-005`, `AC-DEL01-006`

Checks: `CHK-DEL01-STATIC`, `CHK-DEL01-GRADLE`, `CHK-DEL01-GIT`

Paths: `.harnix/tasks/**`, `CHANGELOG.md`, `scripts/**`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/**`, `gradlew`, `gradlew.bat`, `app/**`, `ui/**`, `domain/**`, `data/**`, `platform/**`

Chạy static verification và Gradle Wrapper `verifyFoundation`, persist evidence, finish Harnix task và commit phase với message `del-01: add Android foundation`.