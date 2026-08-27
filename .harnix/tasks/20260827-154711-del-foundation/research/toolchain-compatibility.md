# Research — Toolchain compatibility DEL-01

- Task ID: `20260827-154711-del-foundation`
- Ngày truy cập: `2026-08-27`
- Unknown: Toolchain nào được hỗ trợ cho `AGP 8.12.1`, `compileSdk=36`, và phải pin/verify Gradle Wrapper thế nào?

## Nguồn chính thức

1. Android Developers — AGP 8.12 release notes: https://developer.android.com/build/releases/agp-8-12-0-release-notes
2. Gradle — Wrapper documentation: https://docs.gradle.org/current/userguide/gradle_wrapper.html
3. Gradle — Distribution and Wrapper JAR checksums: https://gradle.org/release-checksums/
4. Android Developers — sdkmanager: https://developer.android.com/tools/sdkmanager
5. Android Developers — SDK Platform release notes: https://developer.android.com/tools/releases/platforms
6. Microsoft Learn — Microsoft Build of OpenJDK downloads: https://learn.microsoft.com/en-us/java/openjdk/download

## Evidence trong repository

- `gradle/libs.versions.toml` pin `agp=8.12.1`, Kotlin `2.2.10`, `compileSdk=36`, `targetSdk=36`, `minSdk=26`.
- Repo chưa có `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` hoặc `gradle-wrapper.properties`.
- Máy chưa có JDK/Gradle/cache. Android SDK hiện có `platforms/android-36.1` nhưng thiếu exact `platforms/android-36`; có emulator binary nhưng chưa có system image/AVD.

## Facts

- AGP 8.12 hỗ trợ tối đa API 36; minimum/default là Gradle `8.13`, JDK `17`, SDK Build Tools `35.0.0`. AGP `8.12.1` thuộc cùng compatibility line.
- Gradle `8.13` binary ZIP có SHA-256 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`; official Wrapper JAR có SHA-256 `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.
- Gradle yêu cầu sinh Wrapper bằng task `:wrapper`; bộ file chuẩn gồm `gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, `gradle-wrapper.properties`. `distributionSha256Sum` phải khóa checksum distribution.
- `sdkmanager` cài exact package bằng identifier như `platforms;android-36`; chạy emulator API 36 cần một system image tương ứng.
- Microsoft cung cấp OpenJDK `17.0.20.1` LTS dạng Windows x64 ZIP cùng checksum, phù hợp giải pháp portable không system-wide.

## Inference và quyết định

- Không upgrade AGP/Kotlin/Room chỉ để giải toolchain. Dùng Microsoft OpenJDK `17.0.20.1` portable, Gradle `8.13` binary distribution, và sinh Wrapper chính thức với hai checksum ở trên.
- Cài exact `platforms;android-36`; giữ baseline `26/36/36`. Build Tools hiện có đã cao hơn minimum của AGP nên không cần đổi build config.
- JDK, bootstrap Gradle, `GRADLE_USER_HOME`, AVD và download cache nằm trong vùng tạm; chỉ Wrapper chuẩn được giữ trong source. Package SDK/system image được thêm cho verification rồi gỡ đúng target đã cài khi kết thúc.
- Chạy unit/build trước; chỉ tạo AVD API 36 khi `connectedDebugAndroidTest` thực sự cần, tránh download không cần thiết.

## Phương án loại

- Không đổi `compileSdk` sang `36.1`: trái baseline đã khóa và không cần thiết.
- Không dùng Gradle mới hơn `8.13`: tăng compatibility surface mà không mang lại giá trị cho DEL-01.
- Không hand-write hoặc tải riêng Wrapper JAR: Wrapper phải được Gradle sinh rồi verify với checksum chính thức.

## Giới hạn và follow-up

Exact stable x86_64 system-image identifier sẽ lấy từ `sdkmanager --list --channel=0` sau khi JDK portable sẵn sàng. Đây là chi tiết môi trường verification, không thay đổi contract sản phẩm hoặc baseline SDK.
