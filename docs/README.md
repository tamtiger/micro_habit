# Bộ đặc tả triển khai — Nhịp 2 Phút

- **Baseline:** 1.0
- **Ngày khóa:** 2026-08-27
- **Mục tiêu:** đủ rõ để estimate, implement, test và chuẩn bị feasibility pilot Android

## 1. Quyết định đã khóa

| Chủ đề | Quyết định MVP |
|---|---|
| Platform | Android-first, locale chính `vi-VN` |
| Intended use | General wellness cho người từ 18 tuổi; không phải thiết bị y tế |
| Routine | 6 routine offline, mỗi routine 2–5 phút |
| Recommendation | Rule engine deterministic, versioned; không AI/biometric/confidence score |
| Data | Local-only, không account/backend/remote analytics; không quyền `INTERNET` |
| Reminder | Ngày + giờ làm việc và 1–2 giờ nhắc cố định; snooze thủ công |
| Safety lifecycle | Hold có lý do đến đầu ngày kế tiếp theo zone nguồn; timezone/clock không được làm hết sớm |
| Terminal feedback | Mọi phiên kết thúc phải giải quyết pain gate trước phiên kế tiếp; hai feedback còn lại có thể để sau |
| Permission | Chỉ notification permission khi cần; không health/calendar/location/activity recognition |
| Monetization | Không paywall trong MVP; export/delete luôn miễn phí |
| Validation | Single-arm feasibility pilot 14 ngày, `n=24`; không kết luận causal/safety |

Mọi đề xuất làm thay đổi một hàng trong bảng này là scope change, phải có decision record, risk review và estimate mới.

## 2. Chỉ mục

| Tài liệu | Câu hỏi mà tài liệu trả lời | ID chính |
|---|---|---|
| [`../Product Brief.md`](../Product%20Brief.md) | Vì sao làm, cho ai, phạm vi nào? | — |
| [`01-product-requirements.md`](01-product-requirements.md) | App phải làm gì và điều kiện chấp nhận là gì? | `FR-*` |
| [`02-ux-flows-and-copy.md`](02-ux-flows-and-copy.md) | Người dùng đi qua flow nào, thấy state/copy nào? | `UX-*` |
| [`03-safety-rule-engine.md`](03-safety-rule-engine.md) | Input nào dẫn tới output nào; hard stop hoạt động ra sao? | `SAF-*` |
| [`04-content-contract.md`](04-content-contract.md) | Routine/movement/asset phải có metadata và sign-off nào? | `CNT-*` |
| [`05-data-privacy-security.md`](05-data-privacy-security.md) | Thu gì, lưu ở đâu, giữ/xóa/export/bảo vệ thế nào? | `DATA-*`, `SEC-*` |
| [`06-technical-architecture.md`](06-technical-architecture.md) | Module, schema, interface, scheduler và migration được triển khai ra sao? | `ARC-*` |
| [`07-analytics-and-validation.md`](07-analytics-and-validation.md) | Event, metric, denominator và pilot protocol là gì? | `MET-*` |
| [`08-qa-and-release-gates.md`](08-qa-and-release-gates.md) | Test gì và khi nào được phép phát hành? | `QA-*` |
| [`09-delivery-plan.md`](09-delivery-plan.md) | Thứ tự build, dependency, owner và milestone là gì? | `DEL-*` |

## 3. Thứ tự thẩm quyền

Nếu hai tài liệu mâu thuẫn, áp dụng theo thứ tự:

1. `03-safety-rule-engine.md` và safety gate trong `08-qa-and-release-gates.md`;
2. `05-data-privacy-security.md`;
3. `01-product-requirements.md`;
4. `02-ux-flows-and-copy.md` và `04-content-contract.md`;
5. `06-technical-architecture.md`;
6. `07-analytics-and-validation.md` và `09-delivery-plan.md`.

Không tự chọn một phía khi phát hiện xung đột. Tạo issue, ghi hai ID liên quan và cập nhật baseline sau khi owner quyết định.

## 4. Traceability

- Requirement dùng ID ổn định: `FR`, `UX`, `SAF`, `CNT`, `DATA`, `SEC`, `ARC`, `MET`, `QA`, `DEL`.
- Pull request/commit triển khai phải liệt kê các ID được xử lý.
- Test case phải trỏ tới ít nhất một requirement ID.
- Thay đổi safety/data/content phải ghi version trước và sau.
- Không tái sử dụng ID đã xóa; đánh dấu `Superseded` và trỏ sang ID mới.

## 5. Trạng thái và phê duyệt

Mỗi tài liệu dùng một trong bốn trạng thái:

- `Draft`: còn câu hỏi ảnh hưởng implementation.
- `Implementation baseline`: đủ để code/test; thay đổi cần review.
- `Approved for pilot`: các external gate tương ứng đã ký duyệt.
- `Superseded`: không còn hiệu lực.

Bộ docs này là **implementation baseline**. Nó chưa tự động là approval phát hành. Các artifact bên ngoài còn bắt buộc:

1. identity/credential và chữ ký đúng role của Content Author, Technique Reviewer và Clinical Safety Reviewer; author phải khác clinical reviewer;
2. privacy policy URL và store listing cuối;
3. Google Play Health Apps declaration/disclaimer review;
4. documented determination về human-subject research và ethics/IRB-equivalent approval hoặc exemption phù hợp;
5. informed-consent, adverse-event escalation và secure-transfer procedure cho pilot;
6. video/caption/text asset đã duyệt.

Những mục này là release blocker có chủ đích, không phải chỗ để developer tự điền nội dung lâm sàng/pháp lý.

## 6. Glossary UI

| Khái niệm | Từ dùng trong UI | Không dùng trong UI |
|---|---|---|
| Một nội dung 2–5 phút | Phiên vận động / bài | Routine, workout snack |
| Notification | Lời nhắc | Cue, prompt |
| `RECOVER` | Hồi lại | Recovery score |
| `MAINTAIN` | Giữ nhịp | Readiness trung bình |
| `BUILD` | Tăng nhịp | Cơ thể sẵn sàng |
| RPE | Mức gắng sức cảm nhận | Intensity score |
| `qualified_break_day` | Ngày có phiên phù hợp | Ngày khỏe/hiệu quả |
| Safety stop | Tạm dừng hôm nay / Dừng và tìm trợ giúp | App phát hiện bệnh/chấn thương |

Trong code/docs kỹ thuật có thể dùng enum tiếng Anh, nhưng mọi user-facing copy phải theo glossary và copy inventory.

## 7. Definition of implementation-ready

Một story chỉ được nhận vào sprint khi:

- có requirement ID và owner;
- input/output/error/empty state đã rõ;
- dependency và data effect đã xác định;
- acceptance criteria quan sát được;
- copy/asset chưa approved chỉ được dùng trong fixture gắn `NON_PRODUCTION_NOT_CLINICALLY_APPROVED`; RC/release không nhận placeholder;
- có test approach, bao gồm negative path khi phù hợp.

Một story chỉ hoàn tất khi code, automated test, accessibility check, docs và data/safety review liên quan cùng hoàn tất.

## 8. Change control tối thiểu

Change request phải ghi:

1. vấn đề và bằng chứng;
2. requirement ID bị tác động;
3. thay đổi user-visible/data/safety;
4. migration và backward compatibility;
5. test cần thêm/sửa;
6. content/privacy/store re-review có cần hay không;
7. estimate và release target mới.

Thay đổi rule precedence, red-flag copy, retention/delete semantics, permission hoặc intended-use boundary luôn cần cross-review Product + Engineering + QA + chuyên gia/pháp lý phù hợp.
