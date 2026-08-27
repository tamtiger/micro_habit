# Nhịp 2 Phút

> Trợ lý tạo những khoảng nghỉ vận động ngắn, kín đáo và dễ bắt đầu trong ngày làm việc.

## Trạng thái

- Baseline: Implementation baseline v1.0
- Cập nhật: 2026-08-27
- Thị trường/ngôn ngữ MVP: Việt Nam, `vi-VN`
- Nền tảng MVP: Android-first
- Định vị: general wellness; không phải thiết bị, dịch vụ hay tư vấn y tế
- Phạm vi repo hiện tại: product brief và bộ đặc tả triển khai; chưa chứa source Android

## Tổng quan

Nhịp 2 Phút giúp người làm việc máy tính hoàn thành một phiên vận động 2-5 phút dựa trên check-in tự khai báo và bộ quy tắc xác định trước. MVP không yêu cầu tài khoản, wearable, kết nối mạng, Health Connect, calendar, location hay activity recognition.

Sản phẩm không chấm điểm cơ thể, không đưa ra recovery score, không chẩn đoán, không điều trị và không tuyên bố đo được thời gian ngồi liên tục. Mọi gợi ý phải nói rõ input nào dẫn đến kết quả và luôn tôn trọng safety hold, acute issue, red flag và phản hồi đau/khó chịu mới hoặc tăng lên.

## Tài liệu chính

| Tài liệu | Nội dung |
|---|---|
| [`Product Brief.md`](Product%20Brief.md) | Mục tiêu sản phẩm, người dùng, phạm vi MVP, safety boundary, validation và release gate |
| [`docs/README.md`](docs/README.md) | Chỉ mục bộ đặc tả triển khai và thứ tự thẩm quyền khi có xung đột |
| [`docs/01-product-requirements.md`](docs/01-product-requirements.md) | Yêu cầu sản phẩm, acceptance criteria và Definition of Done MVP |
| [`docs/02-ux-flows-and-copy.md`](docs/02-ux-flows-and-copy.md) | Luồng UX, state và copy `vi-VN` |
| [`docs/03-safety-rule-engine.md`](docs/03-safety-rule-engine.md) | Input, decision table, reason code và hard-stop behavior |
| [`docs/04-content-contract.md`](docs/04-content-contract.md) | Contract routine, movement, asset, digest và sign-off nội dung |
| [`docs/05-data-privacy-security.md`](docs/05-data-privacy-security.md) | Data inventory, encryption, retention, export/delete và privacy policy |
| [`docs/06-technical-architecture.md`](docs/06-technical-architecture.md) | Kiến trúc Android, module, schema, scheduler, migration và test gate |
| [`docs/07-analytics-and-validation.md`](docs/07-analytics-and-validation.md) | Event dictionary, metric, denominator và protocol pilot |
| [`docs/08-qa-and-release-gates.md`](docs/08-qa-and-release-gates.md) | QA matrix, static gate và release blocker |
| [`docs/09-delivery-plan.md`](docs/09-delivery-plan.md) | Kế hoạch build 6-8 tuần, dependency, owner và milestone |

## Vòng lặp MVP

1. Người dùng hoàn tất age gate, eligibility/safety acknowledgement và lịch làm việc.
2. App chỉ xin quyền notification sau khi onboarding/schedule transaction đã commit.
3. Người dùng check-in bằng năm trường bắt buộc: `red_flag`, `acute_issue`, `energy`, `stiffness`, `intent`.
4. Rule engine versioned trả về một trong các outcome đóng; chỉ `RECOVER`, `MAINTAIN` và `BUILD` có thể dẫn tới routine.
5. Người dùng thực hiện routine 2-5 phút, có thể pause, resume, skip step hoặc stop.
6. Feedback sau phiên giải quyết pain gate trước khi bắt đầu phiên tiếp theo.
7. Tổng kết tuần chỉ hiển thị thống kê mô tả; export và xóa toàn bộ dữ liệu luôn miễn phí.

## Guardrail phát triển

- Ưu tiên safety và release gate trước privacy/security, product requirements, UX/copy và kiến trúc.
- Rule engine phải deterministic, pure và fail closed khi input/contract không hợp lệ.
- MVP không có account, backend, remote analytics, AI/generative text, paywall hoặc SDK quảng cáo/crash reporting.
- Release build không được khai báo quyền `INTERNET`, health, calendar, location, activity recognition, exact alarm, billing hay `CALL_PHONE`.
- Tất cả routine, movement, global safety copy và privacy artifact cần digest/sign-off hợp lệ trước pilot hoặc production release.
- Thay đổi hành vi safety, data collection, content contract, permission hoặc claim sản phẩm phải có review và cập nhật baseline liên quan.

## Chuẩn bị implementation

Khi bắt đầu code Android, dùng tài liệu kiến trúc làm contract:

- Module dự kiến: `:app`, `:ui`, `:domain`, `:data`, `:platform`.
- Stack production: Kotlin, Gradle Kotlin DSL, Jetpack Compose, Material 3, Room, coroutines/Flow, AlarmManager inexact và Android Keystore.
- Baseline Android tại thời điểm khóa tài liệu: `minSdk=26`, `targetSdk=36`, `compileSdk=36`.
- Mỗi story nên tham chiếu requirement ID ổn định (`FR-*`, `SAF-*`, `ARC-*`, `DATA-*`, `QA-*`, `DEL-*`) và có test/evidence tương ứng.

## Chuẩn bị commit

Commit gợi ý:

```text
docs: add Nhịp 2 Phút baseline documentation
```

Trước khi commit, kiểm tra:

```powershell
git status --short
git diff --check
git commit -m "docs: add Nhịp 2 Phút baseline documentation"
```
