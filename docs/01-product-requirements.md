# Nhịp 2 Phút — Yêu cầu sản phẩm MVP

- Phiên bản: 1.0
- Trạng thái: `Implementation baseline`; external release/pilot sign-off vẫn bắt buộc
- Nền tảng: Android-first
- Ngôn ngữ phát hành: `vi-VN`
- Đối tượng: Người dùng từ đủ 18 tuổi
- Định vị: General wellness; không phải thiết bị, dịch vụ hay tư vấn y tế

Tài liệu này là baseline requirement cho MVP; `Product Brief.md` vẫn là nguồn product intent. Khi phát hiện khác biệt, áp dụng thứ tự thẩm quyền và quy trình issue tại [README.md](./README.md), không tự ghi đè âm thầm. Luồng/copy nằm tại [02-ux-flows-and-copy.md](./02-ux-flows-and-copy.md); phép đo/pilot nằm tại [07-analytics-and-validation.md](./07-analytics-and-validation.md).

## 1. Mục tiêu và lời hứa sản phẩm

Nhịp 2 Phút giúp người làm việc máy tính tạo một quãng vận động ngắn bằng check-in thủ công và routine 2–5 phút có thể thực hiện tại nơi làm việc. App không quan sát hoặc khẳng định đã phát hiện một quãng ngồi liên tục.

Lời hứa MVP:

> Check-in trong khoảng 20 giây, hiểu rõ vì sao app đưa ra một nhịp, thực hiện một bài 2–5 phút hoặc nghỉ mà không bị phạt.

MVP phải:

- hoạt động đầy đủ khi offline;
- dùng quy tắc xác định trước, không dùng AI;
- không yêu cầu tài khoản, wearable, dữ liệu sức khỏe hệ thống hay thanh toán;
- lưu toàn bộ dữ liệu trên thiết bị;
- không chấm điểm cơ thể, chẩn đoán, điều trị hoặc hứa hẹn hiệu quả sức khỏe;
- cho phép bỏ qua, dừng, nghỉ hoặc chọn mức nhẹ hơn mà không có streak hay ngôn ngữ gây áp lực.

North-star metric là `qualified_break_days`, được định nghĩa chính xác tại mục 11 và tài liệu analytics.

## 2. Người dùng và ranh giới an toàn

### 2.1. Người dùng mục tiêu

- Từ đủ 18 tuổi.
- Làm việc với máy tính và muốn có những quãng vận động ngắn trong ngày.
- Có thể tự đọc, hiểu và trả lời check-in bằng tiếng Việt.
- Có thể tự quyết định dừng vận động khi không thoải mái.

### 2.2. Không phục vụ trong MVP

MVP không được định vị hoặc cá nhân hóa cho:

- người dưới 18 tuổi;
- phục hồi chấn thương, hậu phẫu hoặc vật lý trị liệu;
- thai kỳ/hậu sản cần hướng dẫn riêng;
- phục hồi tim phổi;
- quản lý bệnh mạn hoặc tình trạng đã được chuyên gia y tế giới hạn vận động;
- tối ưu thành tích thể thao.

Onboarding phải có age gate. Daily check-in phải chặn routine khi có red flag, bệnh cấp tính, đau/chấn thương mới hoặc tăng lên, hoặc giới hạn vận động do chuyên gia y tế đặt ra.

### 2.3. Red flag

> Trạng thái duyệt: logic `red_flag` là implementation baseline, nhưng wording/list user-facing dưới đây là `PENDING_EXTERNAL_SIGN_OFF`. Release chỉ được dùng message key được cover bởi root `globalSafetyContentDigestSha256` và `globalSafetySignOff` hợp lệ theo `CNT-015`/`CNT-050`; tài liệu này không tự chứng nhận copy lâm sàng/pháp lý.

`red_flag=true` khi người dùng xác nhận hiện có ít nhất một trong các dấu hiệu sau:

- đau hoặc cảm giác đè nặng vùng ngực;
- chóng mặt nhiều hoặc ngất;
- khó thở bất thường hoặc nghiêm trọng;
- tim đập nhanh hoặc loạn nhịp bất thường;
- đau cấp tính hoặc triệu chứng tăng mạnh.

App chỉ yêu cầu dừng và tìm trợ giúp phù hợp; không diễn giải nguyên nhân. App không được cho phép bắt đầu routine trong luồng hiện tại sau khi `red_flag=true`.

Release phải lấy số hiển thị, hướng dẫn, nhãn CTA và thông báo không có dialer từ `globalSafetyContent.emergencyDial` đã ký. `instructionTemplateKey` chỉ thay đúng placeholder `{emergency_number}` bằng `dialTargetDigits`; CTA dispatch `ACTION_DIAL` tới cùng digits, không `ACTION_CALL`, không tự gọi và không xin phone permission. Thiếu/mismatch contract, digest hoặc dial target là release blocker; không hard-code một số khác trong binary/copy.

## 3. Phạm vi MVP

### 3.1. Trong phạm vi

- Ứng dụng Android bằng tiếng Việt (`vi-VN`).
- Onboarding: age gate, giới hạn sản phẩm, lịch làm việc, 1–2 giờ nhắc cố định và quyền notification.
- Daily check-in với đúng năm trường bắt buộc tại mục 6.
- Rule engine xác định một domain result tại mục 7, gồm sáu outcome nghiệp vụ và hai trạng thái fail-closed.
- Ba mode hiển thị: `Hồi lại`, `Giữ nhịp`, `Tăng nhịp`.
- Sáu routine đóng gói trong app, hoạt động offline, mỗi routine dài 2–5 phút.
- Người dùng chỉ chọn mode đang được gợi ý hoặc mode nhẹ hơn.
- Phát routine, tạm dừng, tiếp tục, bỏ qua và dừng.
- Feedback sau routine với đúng ba trường tại mục 9.
- Lịch nhắc local theo các ngày và giờ cố định do người dùng chọn.
- Snooze thủ công 15, 30 hoặc 60 phút nếu giờ mới còn nằm trong lịch làm việc.
- Tổng kết tuần chỉ dùng số đếm; tỷ lệ chỉ hiển thị khi mẫu số từ 5 trở lên.
- Export thủ công và xóa toàn bộ dữ liệu miễn phí.
- Event log local phục vụ tổng kết và pilot; không gửi tự động.

### 3.2. Ngoài phạm vi

- iOS, web, tablet-specific UI hoặc đa ngôn ngữ.
- Tài khoản, đăng nhập, cloud sync, backend hoặc network request.
- AI, mô hình học máy, score 0–100, suy luận tương quan hoặc nội dung sinh tự động.
- Health Connect, HealthKit, wearable hoặc quyền đọc dữ liệu sức khỏe hệ thống.
- Calendar, trạng thái họp, vị trí, phát hiện lái xe hoặc tự suy đoán bối cảnh.
- Reminder thích ứng, tự giảm/tăng tần suất hoặc tự thay đổi giờ nhắc.
- Remote analytics, crash reporting qua mạng, quảng cáo hoặc SDK theo dõi.
- Paywall, subscription, in-app purchase hoặc entitlement trả phí.
- Social, leaderboard, streak, calo, cân nặng hoặc chương trình tập dài.
- Rehab, hướng dẫn theo bệnh/tình trạng y khoa hoặc claim phòng ngừa/điều trị.

Ứng dụng MVP không khai báo quyền Internet. Nếu một dependency yêu cầu network hoặc tự gửi telemetry, dependency đó không được dùng trong bản phát hành.

## 4. Thuật ngữ và quy ước thời gian

| Thuật ngữ | Định nghĩa chuẩn |
|---|---|
| Ngày địa phương | Ngày theo timezone hiện tại của thiết bị tại thời điểm sự kiện. |
| Ngày làm việc đã chọn | Một thứ trong tuần được người dùng bật trong lịch làm việc. |
| Khung làm việc | Khoảng half-open `[work_start, work_end)` trong cùng một ngày địa phương. Tại `now >= work_end`, receiver/start phải skip hoặc expire. MVP không hỗ trợ ca qua đêm. |
| Giờ nhắc cố định | 1–2 giá trị giờ/phút theo wall clock địa phương, nằm trong khung làm việc. |
| Check-in hiện hành | Check-in mới nhất trong ngày, `now < work_end` và freshness resolver còn `FRESH` theo durable elapsed/monotonic evidence. |
| Xác nhận lại | Hiển thị lại toàn bộ năm trường hiện tại để người dùng chủ động giữ hoặc sửa từng giá trị, sau đó chạy lại rule engine. |
| Phiên hoàn thành | Một phiên đã bắt đầu và đi hết nội dung/timer, sau đó người dùng chọn `Hoàn thành`. |
| Nhẹ hơn | Theo thứ tự `Hồi lại < Giữ nhịp < Tăng nhịp`. |
| Safety hold | Domain entity `SafetyHold`, chặn routine với kind `RED_FLAG`, `ACUTE_ILLNESS`, `NEW_OR_WORSENING_PAIN_OR_INJURY`, `MEDICALLY_RESTRICTED` hoặc `POST_SESSION_NEW_OR_WORSE_PAIN`; giữ audit expiry theo zone lúc tạo và effective clock evidence. |
| Pending pain gate | Session `COMPLETED\|ABANDONED` nhưng câu `new_or_worse_pain` chưa được trả lời; start gate trả `PENDING_SAFETY_FEEDBACK` và không cho routine mới. |
| Day mode cap | Mức cao nhất được phép trong phần còn lại của origin day; giảm một bậc sau feedback `effort=too_hard`, giữ audit expiry và effective clock evidence. |

Giới hạn 6 giờ được tính bằng elapsed time. Authorization dùng durable monotonic freshness evidence và trở thành non-`FRESH` tại equality của TTL; `reconfirm_after` chỉ là wall-clock audit/UI field, không phải bằng chứng cho phép hoặc hết hạn. `work_end`/local-date vẫn là wall guard half-open: tại `now >= work_end` không được start. Thay đổi timezone làm app tính lại lịch theo wall clock mới nhưng không sửa timestamp lịch sử.

## 5. Yêu cầu onboarding và lịch làm việc

### FR-001 — Age gate

App phải hỏi người dùng có từ đủ 18 tuổi hay không trước khi tạo dữ liệu sử dụng. Nếu trả lời không, app hiển thị màn hình không đủ điều kiện và không cho tiếp tục.

Chỉ confirmation `đủ 18 tuổi=true` được persist. Lựa chọn chưa đủ tuổi chỉ tồn tại trong RAM để render safe-exit; không tạo profile/event và không lưu ngày sinh hay tuổi cụ thể.

**Acceptance — Given/When/Then**

- Given app mới cài, when người dùng chọn `Chưa đủ 18 tuổi`, then không màn hình check-in, routine hay reminder nào có thể truy cập.
- Given người dùng chưa xác nhận tuổi, when đóng và mở lại app, then age gate vẫn là màn hình bắt buộc.

### FR-002 — Xác nhận phạm vi và eligibility tối thiểu

Eligibility logic là implementation baseline; exact user-facing attestation/safe-exit copy là `PENDING_EXTERNAL_SIGN_OFF` và chỉ được release khi key nằm trong root global safety digest/sign-off hợp lệ theo `CNT-015`/`CNT-050`.

Người dùng đủ tuổi phải đọc giới hạn: app dành cho general wellness, không thay thế tư vấn y tế, phải dừng khi thấy không ổn và không dùng để rehab hoặc làm trái giới hạn vận động đã được đưa ra. Sau đó app yêu cầu một self-attestation đóng, không hỏi chẩn đoán: người dùng xác nhận có thể tự thực hiện vận động general-wellness nhẹ–vừa, hiện không có red flag/bệnh cấp/đau hoặc chấn thương mới hay tăng/giới hạn vận động y tế, và không cần hướng dẫn cá nhân cho các mục ngoài intended use tại §2.2.

Nếu người dùng chọn `Không` hoặc `Không chắc`, app hiển thị safe-exit, không cho tới lịch/check-in/routine bằng Back hoặc deep link. Chỉ confirmation đủ điều kiện và version nội dung đã chấp nhận được persist; lựa chọn không đủ điều kiện/không chắc chỉ giữ trong RAM của màn hiện tại, không lưu raw reason.

**Acceptance — Given/When/Then**

- Given người dùng đủ 18 nhưng chọn ngoài phạm vi hoặc không chắc, when tiếp tục, then app mở safe-exit và không tạo lịch/check-in/session.
- Given người dùng xác nhận đủ điều kiện, when hoàn tất bước này nhưng chưa lưu lịch hợp lệ, then app chỉ staging confirmation cùng eligibility/scope content identity trong RAM, chưa tạo Profile/entity/event; khi lần `Lưu lịch` đầu tiên commit thì các giá trị này mới được persist atomically theo `FR-003`/`FR-005` và vẫn không lưu chẩn đoán hay chi tiết sức khỏe.

### FR-003 — Thiết lập lịch làm việc

Người dùng phải chọn:

- ít nhất 1 và tối đa 7 ngày trong tuần;
- một `work_start` và một `work_end` dùng chung cho các ngày đã chọn, với `work_start < work_end`;
- 1 hoặc 2 giờ nhắc cố định, mỗi giờ nằm trong `[work_start, work_end)` và không trùng nhau.

Ba wire value `work_start`, `work_end` và từng phần tử `reminder_times[]` dùng duy nhất ASCII zero-padded `HH:mm`, khớp regex `^(?:[01][0-9]|2[0-3]):[0-5][0-9]$`. Domain time bắt buộc second/nanosecond bằng 0; parse rồi serialize phải byte-identical. `reminder_times[]` có 1–2 phần tử distinct, sort tăng theo local wall time; storage, event và export không nhận alias, timezone suffix hoặc `HH:mm:ss`.

Không hỗ trợ khung qua 00:00. Lần `Lưu lịch` đầu tiên là **một onboarding transaction duy nhất**: từ staged age/eligibility/scope state và schedule hợp lệ, app tạo `AppProfile`, acknowledgement onboarding/current pointer, `WorkScheduleVersion.enabled=true` + active pointer, mọi staged eligible event, `scope_acknowledged`, `work_schedule_saved(change_source=onboarding, previous_schedule_version_id=null, active_decision_invalidated=false)` và `onboarding_completed`. Thiếu/fail bất kỳ entity, pointer, event, ref, HMAC hoặc retention write nào phải rollback toàn bộ; không tồn tại profile/lịch/onboarding một nửa và chưa được mở permission flow. Nếu người dùng sửa lịch sau đó, các notification local trong tương lai phải được hủy và lập lại ngay; lịch sử không bị viết lại.

Mỗi `CheckIn` và `Decision` snapshot non-null `schedule_version_id` đang active lúc check-in commit. Bất kỳ version change nào — kể cả chỉ sửa giờ nhắc hoặc toggle `enabled` — làm decision cũ không còn authorize session mới. Start phải so ID trong transaction với active schedule, xác minh `now` nằm trong current active window, và trả `RECONFIRM_REQUIRED(reason=schedule_changed)` khi mismatch; check-in mới dùng version active và `RoutineSession.schedule_version_id` phải bằng cùng ID. Active session đã tạo tiếp tục recovery theo version snapshot của nó; schedule edit không viết lại session lịch sử.

Trong Cài đặt, người dùng có thể tắt/bật `Lời nhắc trong app`. Mỗi thay đổi tạo `WorkScheduleVersion` mới nhưng giữ workdays/window/1–2 fixed times. `enabled=false` hủy occurrence tương lai và không lập notification mới; manual check-in/routine cùng selected-workday snapshot/north-star semantics vẫn giữ nguyên. Bật lại chỉ lập fixed slot còn ở tương lai, không post bù. Đây là state độc lập với quyền notification của Android: schedule có thể enabled nhưng permission off. Trạng thái quyền hiện tại luôn đọc runtime từ OS; không persist **authoritative current-state copy** trong `AppProfile`/preferences. Tuy vậy, mỗi observation allowlisted `notification_permission_updated` là encrypted local event history và được persist/export trong array `events` để audit/metric; observation lặp cùng state sau explicit Settings return vẫn hợp lệ nhưng không phải transition. Event không được dùng thay OS state khi render hoặc schedule.

**Acceptance — Given/When/Then**

- Given lịch chưa hợp lệ, when người dùng chọn giờ kết thúc không sau giờ bắt đầu hoặc giờ nhắc nằm ngoài khung, then app không lưu và chỉ rõ trường cần sửa.
- Given schedule payload chứa `9:00`, `09:00:00`, khoảng trắng, duplicate/unsorted reminder hoặc domain time có second/nanosecond khác 0, when parse/import/save, then reject thay vì normalize; canonical `09:00` round-trip byte-identical.
- Given lịch hợp lệ đã lưu, when timezone thiết bị đổi, then lần nhắc kế tiếp dùng cùng giá trị wall clock tại timezone mới và không phát bù reminder đã qua.
- Given schedule enabled đang được tắt trong Cài đặt, when transaction commit, then app tạo version `enabled=false`, hủy occurrence tương lai, không đổi lịch sử/workday qualification và không post bù khi bật lại.
- Given có Decision hiện hành từ schedule version A, when bất kỳ schedule edit tạo version B, then Home/start yêu cầu check-in lại với reason `schedule_changed`; session mới không thể mang A hoặc trộn Decision A với B.
- Given initial schedule hợp lệ, when người dùng bấm `Lưu lịch`, then Profile + acknowledgement/current pointer + initial schedule/active pointer + staged eligible events + scope/schedule/onboarding companion events commit zero-or-all; permission primer chỉ có thể render sau full commit.

### FR-004 — Quyền notification

App chỉ render permission primer **sau khi** initial onboarding/schedule transaction ở `FR-003` đã commit đầy đủ, và chỉ xin quyền notification sau khi người dùng xem giải thích rồi chủ động bấm CTA. Permission adapter phải tách hai branch trước side effect. Chỉ khi runtime dialog thực sự launchable, app atomically persist encrypted `NotificationPromptAttemptV1` + `notification_permission_prompted`, rồi mới gọi ActivityResult launcher; commit fail thì không launch. Attempt có exact `state=PENDING|RESOLVED|INTERRUPTED`, opaque `origin_process_instance_id` và full LocalStamp `attempted_at`; tối đa một row `PENDING`. Nullable matrix: PENDING có `resolved_at`, `prompt_result`, `interruption_reason` đều null; RESOLVED có resolved-at/result non-null và interruption null; INTERRUPTED có resolved-at + `interruption_reason=process_recreated_before_callback`, result null. Automatic onboarding dialog chỉ được gọi nếu chưa từng có **bất kỳ** automatic attempt, bất kể state.

Nếu adapter trả prompt unavailable/settings-required, CTA mở Android Settings trực tiếp và tạo **không** PromptAttempt, **không** `notification_permission_prompted`, không để PENDING. Khi quay lại cùng process từ chính navigation này, app query OS và ghi đúng một observation `notification_permission_updated(source=settings,state=<current>,attempt_id=null,prompt_result=null)`, kể cả state không đổi; lifecycle callback trùng không ghi event thứ hai. Nếu process bị recreate trong lúc Settings mở, không có attempt cần interrupt; resume chỉ có thể ghi generic `source=resume_check` observation theo runtime policy. Settings-only path không được coi là automatic prompt attempt, không auto-open Settings ở lần launch sau và chỉ mở lại khi người dùng chủ động bấm CTA.

Android không cung cấp tín hiệu đáng tin cậy để tách Deny khỏi Dismiss. Callback false được ghi `notification_permission_updated(state=denied, source=system_prompt, attempt_id, prompt_result=not_granted)`; callback true dùng `state=granted`/`prompt_result=granted`. Không gắn nhãn riêng Dismiss. Mỗi dialog attempt có đúng một prompted event và tối đa một system-result event. New process atomically chuyển old `PENDING` sang `INTERRUPTED` trước khi render; không bịa update/result. Late callback chỉ resolve đúng attempt vẫn `PENDING` của origin process; callback cho interrupted/unknown attempt bị ignore/reject, không rebound sang retry. CTA retry dialog disabled khi còn PENDING; sau RESOLVED/INTERRUPTED, explicit retry tạo attempt ID mới nếu dialog vẫn launchable. Observation từ settings/resume dùng `source=settings|resume_check`, `state=granted|denied|unavailable` và bắt buộc `attempt_id=null`, `prompt_result=null`. Từ chối/không cấp quyền không làm mất check-in/routine. Current state luôn đọc OS; attempt row giữ đến full delete, còn event/attempt chỉ chống nag loop và audit.

### FR-005 — Hoàn tất và quay lại onboarding

Onboarding hoàn tất tại successful initial `Lưu lịch` transaction khi `FR-001`, `FR-002` và `FR-003` đều hợp lệ; activation anchor/`onboarding_completed_at` lấy ClockSnapshot của commit này. Permission primer/dialog là optional post-commit flow và không thuộc completion transaction hay activation anchor. Người dùng có thể sửa lịch, xem lại giới hạn sản phẩm và trạng thái quyền notification trong Cài đặt.

### FR-005A — Global safety acknowledgement và re-ack

Mỗi acknowledgement đã chấp nhận được append vào `AppProfile.safety_acknowledgements[]` với `acknowledgement_id`, `kind=onboarding|reack`, `content_version`, `content_digest` và full LocalStamp. `content_version` phải bằng bundled `ContentManifest.manifestVersion`; `content_digest` phải bằng bundled `globalSafetyContent.globalSafetyContentDigestSha256`. `AppProfile.current_safety_acknowledgement_id` non-null trỏ đúng một record trong history; missing/dangling/duplicate là data error. App giữ toàn bộ history còn retention; không dùng app version, root manifest digest hoặc routine digest thay thế.

Sau khi xác minh bundled manifest/global sign-off, resolver trả `CURRENT` chỉ khi current acknowledgement khớp byte-exact version+digest. Một authenticated acknowledgement cũ không khớp bundle hợp lệ trả `REACK_REQUIRED`; dữ liệu acknowledgement thiếu/corrupt hoặc bundle không xác thực trả `DATA_ERROR` và fail closed, không giả thành re-ack thành công.

Mọi entry point check-in/start áp thứ tự: active `SafetyHold` → pending pain → active-session recovery → global-safety acknowledgement. `REACK_REQUIRED` trả start-gate/UI `SCOPE_REACK_REQUIRED` và render lại phạm vi, eligibility attestation cùng approved key hiện hành trước khi mở check-in/routine. Chọn `Có` append một record `kind=reack` và cập nhật current audit atomically; chọn `Không/Không chắc` đi safe-exit, không persist raw reason. Re-ack không sửa `onboarding_completed_at` hoặc four-field activation anchor, không phát lại onboarding completion và không khởi động lại study day/activation window.

Generic reminder vẫn có thể post khi acknowledgement stale vì notification không chứa safety recommendation. Tap body/action chỉ mở Home, chạy guard và route `SCOPE_REACK_REQUIRED`; không tạo session hoặc bỏ qua re-ack.

**Acceptance — Given/When/Then**

- Given bundle mới có valid `manifestVersion` hoặc global safety digest khác current acknowledgement, when người dùng mở Home/check-in/start, then app route `SCOPE_REACK_REQUIRED` sau ba guard ưu tiên và không tạo CheckIn/Session trước khi re-ack commit.
- Given người dùng hoàn tất re-ack, when audit được lưu, then history có record mới đúng version/digest/LocalStamp nhưng original onboarding/activation anchor byte-identical.
- Given notification generic đã post khi acknowledgement stale, when người dùng tap, then Home mở re-ack; notification không tự tạo session.

### FR-006 — Manual use trên ngày không được chọn

Check-in và routine thủ công được phép trên mọi local date nhưng chỉ khi `now ∈ [work_start, work_end)`. Start transaction tính duy nhất `is_selected_workday_at_start = referenced WorkScheduleVersion.selected_weekdays.contains(started_at.local_date ISO day 1..7)` và persist/event-mirror cờ đó; không nhận boolean từ UI, không phụ thuộc `enabled`/active schedule/current zone về sau. Ngày không được chọn vì vậy có snapshot false, không có notification và không bao giờ góp vào `qualified_break_days`. Toàn bộ red/acute hold, pending pain gate, day mode cap, TTL và authorization vẫn áp dụng như ngày được chọn.

**Acceptance — Given/When/Then**

- Given hôm nay không phải selected workday và `now` nằm trong work interval, when người dùng mở app, then manual check-in/routine khả dụng nhưng session snapshot false và ngày không qualify north star.
- Given hôm nay không phải selected workday, when đến fixed reminder time, then không notification được post.

## 6. Daily check-in

### FR-010 — Schema bắt buộc

Mỗi check-in dùng đúng các field sản phẩm sau và không có giá trị `unknown`. Để tạo mode/routine, cả năm field phải hợp lệ; các field phía sau cổng red/acute được phép `null` trong bản ghi chặn sớm theo `FR-011` vì không tham gia quyết định:

| Field | Kiểu/enum | Ý nghĩa |
|---|---|---|
| `red_flag` | boolean | Có ít nhất một dấu hiệu tại mục 2.3 ngay lúc trả lời. |
| `acute_issue` | `none \| acute_illness \| new_or_worsening_pain_or_injury \| medically_restricted` | Tình trạng cấp tính hoặc giới hạn hiện tại. |
| `energy` | `low \| okay \| good` | Năng lượng tự cảm nhận. |
| `stiffness` | `none \| mild \| notable` | Mức cứng người tự cảm nhận. |
| `intent` | `rest \| gentle \| moderate` | Mong muốn vận động hiện tại. |

Không thu thập giấc ngủ, stress, vị trí đau, chẩn đoán, ghi chú tự do hoặc dữ liệu wearable trong MVP.

Persisted CheckIn dùng required discriminator `answers_kind=red_flag_stop|acute_stop|full`: `red_flag_stop` chỉ cho `red_flag=true` và mọi field sau null; `acute_stop` chỉ cho red=false, acute non-`none` hợp lệ và ba field sau null; `full` chỉ cho red=false, acute=`none` và đủ energy/stiffness/intent. Field thừa/non-null trái branch, unknown discriminator hoặc enum sai là contract/migration error, không được default/coerce.

### FR-011 — Thứ tự hỏi và chặn sớm

`red_flag` phải được hỏi trước. Nếu `true`, app lập tức tạo outcome `URGENT_STOP` và safety hold kind `RED_FLAG`, không cần hỏi bốn field còn lại, không tạo recommendation và không hiển thị thư viện routine.

Nếu `red_flag=false`, hỏi `acute_issue` tiếp theo. Khi `acute_issue` là một enum non-`none` hợp lệ, app lập tức tạo `PAUSE_TODAY` và safety hold theo mapping `acute_illness→ACUTE_ILLNESS`, `new_or_worsening_pain_or_injury→NEW_OR_WORSENING_PAIN_OR_INJURY`, `medically_restricted→MEDICALLY_RESTRICTED`; `energy`, `stiffness`, `intent` có thể là `null` vì không còn được dùng để quyết định. Chỉ khi `acute_issue=none`, ba field còn lại mới bắt buộc trước submit.

Check-in bị chặn sớm bởi red flag/acute issue không phải check-in hiện hành. Hold đã tạo không thể bị bypass bằng Back, deep link, re-answer hoặc process restore.

### FR-012 — Hiệu lực và xác nhận lại

- `CheckIn.schedule_version_id` và `Decision.schedule_version_id` phải bằng active schedule ID; mismatch hợp lệ trả `RECONFIRM_REQUIRED(reason=schedule_changed)` và không tái evaluate input cũ âm thầm.
- Sau schedule check, entry point ở ngoài current active window `[work_start, work_end)` trả `EXPIRED`; không tạo session hoặc dùng reason reconfirm để mở bài ngoài giờ.
- Trong current active window nhưng source `local_date` khác current local date trả `RECONFIRM_REQUIRED(reason=local_date_changed)`; check-in cũ không được start.
- Với cùng schedule/window/date, boot mismatch, elapsed rollback hoặc arithmetic overflow trả `RECONFIRM_REQUIRED(reason=clock_unknown)`; generation/zone mismatch hoặc wall-minus-elapsed mapping drift quá 2.000 ms trả `RECONFIRM_REQUIRED(reason=timezone_or_time_change)`; nếu evidence liên tục còn lại chạm TTL equality thì trả `RECONFIRM_REQUIRED(reason=ttl)`. TTL authorization là half-open `[confirmed_at, min(confirmed_at + 6 giờ elapsed, work_end))`; `reconfirm_after` chỉ dùng hiển thị/audit. Các clock change không được clear sớm hold/cap/suppression.
- Xác nhận lại phải prefill năm field và yêu cầu một thao tác submit chủ động; không tự mặc định an toàn.
- Mỗi lần xác nhận lại tạo một version mới, giữ liên kết với check-in ban đầu và chạy rule engine lại từ đầu.
- Một safety hold luôn ưu tiên hơn check-in hiện hành và trả về `BLOCKED_FOR_TODAY`.
- `REST_ONLY` không tạo safety hold. Người dùng có thể chủ động tạo check-in mới sau đó trong cùng khung làm việc.
- Sau khi hold/cap hết hạn, recommendation cũ không được khôi phục; người dùng phải tạo check-in mới theo zone hiện tại.

**Acceptance — Given/When/Then**

- Given durable elapsed evidence của check-in đã chạm đúng TTL 6 giờ và vẫn trước `work_end`, when người dùng mở recommendation, then freshness resolver trả non-`FRESH` và app yêu cầu xác nhận lại toàn bộ field trước khi tiếp tục.
- Given check-in mới 2 giờ nhưng thời điểm hiện tại `now >= work_end`, when người dùng mở app, then check-in cũ không được dùng để bắt đầu routine.
- Given có active decision rồi timezone/system time đổi, when mở bài, then app yêu cầu reconfirm và vẫn giữ mọi persisted hold/cap chưa hết theo clock-integrity resolver.
- Given active schedule ID khác `schedule_version_id` của decision, when mở recommendation/start, then app yêu cầu check-in mới với reason `schedule_changed`; session không được tạo từ decision cũ.
- Given entry point nằm ngoài current active work window, when authorize start, then trả `EXPIRED`; given vẫn trong window nhưng source date khác, TTL equality, observed clock change hoặc continuity unknown, then trả `RECONFIRM_REQUIRED` lần lượt với `local_date_changed|ttl|timezone_or_time_change|clock_unknown`.

### FR-013 — Thời gian luồng chính

Trong moderated usability test trên thiết bị mục tiêu với người dùng đã onboarding và case routine-eligible (`red_flag=false`, `acute_issue=none`):

- check-in duration từ `check_in_started` (tap `Check-in`) đến `check_in_submitted` có median ≤20 giây và p90 ≤30 giây;
- total time-to-routine từ chính `check_in_started` đến `routine_started` cùng flow/decision có p90 ≤45 giây.

Mỗi lần mở form sinh random `check_in_flow_id` độc lập với entity và dùng cùng giá trị trên `check_in_started`/`check_in_submitted`; event started không có `check_in_id`. Chỉ submit thành công mới tạo canonical CheckIn và ghi actual `check_in_id` trên submitted event. Vì vậy flow bị bỏ dở vẫn là funnel sample hợp lệ mà không tạo dangling FK. CheckIn chỉ có một named LocalStamp `confirmed_at`, lấy từ ClockSnapshot của transaction submit/commit; event `check_in_submitted` phải dùng envelope quartet byte-equal stamp này, không có entity field `submitted_at` thứ hai. Started event chụp `timing_start_boot_marker` + `timing_start_elapsed_realtime_ms`; submitted event ghi exact XOR `duration_ms` hoặc `timing_invalid_reason=same_boot_unavailable|elapsed_rollback|overflow|background_over_10m`. Chỉ tính delta monotonic cùng process-instance/boot bằng checked arithmetic, không dùng wall time.

Background accumulator bắt đầu ở `MainActivity.ON_STOP` (`STARTED→<STARTED`) và kết thúc ở `ON_START` (`<STARTED→STARTED`); `ON_PAUSE` không mở interval. Cộng tất cả interval từ flow start đến endpoint, kể cả config recreation trong cùng process. Duration vẫn là `endElapsed-startElapsed` và **không trừ** background; accumulator chỉ là exclusion gate. Equality `600_000 ms` vẫn hợp lệ, lớn hơn mới `background_over_10m`. Reason precedence cố định: `same_boot_unavailable → elapsed_rollback → overflow → background_over_10m`. Encrypted flow timing tracker phải sống qua submit đến actual routine start; process-instance/tracker continuity mất thì dùng `same_boot_unavailable` dù boot marker chưa đổi. `routine_started` giữ cùng flow ID và XOR `total_duration_ms|total_timing_invalid_reason` để thấy cả background sau submit.

Mọi raw JSON field kết thúc `_ms` là integer int64 trong `[0, Long.MAX_VALUE]`. Total timing nối `check_in_flow_id → check_in_id → Decision → RoutineSession`; không dùng `check_in_submitted→routine_started` thay cho total acceptance. Safety/rest/pending/lock flows không có routine start và bị loại khỏi total timing nhưng có event/count riêng. Runtime `INCOMPLETE` không được giả thành persisted decision; form non-completion và invalid/corrupt state được báo trong funnel/data-quality/QA.

## 7. Rule engine và quyền chọn mode

### FR-020 — Decision table chuẩn

Rule engine phải deterministic, chạy local và áp dụng điều kiện theo đúng thứ tự sau. Input canonical sau parse/validation là `RuleInputV1`; serialized fields dùng đúng tên tại `FR-010` cộng `safety_lock_active` và `day_mode_cap`. Dòng đầu tiên khớp sẽ kết thúc đánh giá. Hai field state này là state local của ngày hiện tại, không phải field check-in.

| Ưu tiên | Điều kiện | Outcome |
|---:|---|---|
| 0 | `safety_lock_active = true` | `BLOCKED_FOR_TODAY` |
| 1 | `red_flag = true` | `URGENT_STOP` |
| 2 | `red_flag` thiếu/sai kiểu | `INCOMPLETE` |
| 3 | `acute_issue` là enum hợp lệ khác `none` | `PAUSE_TODAY` |
| 4 | `acute_issue` thiếu/sai enum | `INCOMPLETE` |
| 5 | `energy`, `stiffness` hoặc `intent` thiếu/sai enum, hoặc authenticated constraint bundle parse thành công nhưng inner `day_mode_cap` slot hiện diện với enum/shape sai | `INCOMPLETE` |
| 6 | `intent = rest` | `REST_ONLY` |
| 7 | `energy = low OR stiffness = notable` | `RECOVER` |
| 8 | `energy = good AND stiffness IN (none, mild) AND intent = moderate` | `BUILD` |
| 9 | Mọi tổ hợp hợp lệ còn lại | `MAINTAIN` |

Pseudocode chuẩn:

```text
if safety_lock_active:
  BLOCKED_FOR_TODAY
else if red_flag == true:
  URGENT_STOP
else if red_flag is missing or invalid:
  INCOMPLETE
else if acute_issue in [acute_illness,
                        new_or_worsening_pain_or_injury,
                        medically_restricted]:
  PAUSE_TODAY
else if acute_issue is missing or invalid:
  INCOMPLETE
else if energy, stiffness, or intent is missing/invalid
     or authenticated day_mode_cap slot is present but inner enum/shape is invalid:
  INCOMPLETE
else if intent == rest:
  REST_ONLY
else if energy == low or stiffness == notable:
  RECOVER
else if energy == good and stiffness in [none, mild] and intent == moderate:
  BUILD
else:
  MAINTAIN

if result has a mode and day_mode_cap exists:
  effective_mode = min(mode(result), day_mode_cap)
```

Trước khi gọi engine, implementation phải decrypt và xác thực nguyên daily-constraint envelope/bundle rồi resolve safety hold. AES-GCM tag/key/envelope failure, bundle decode/auth failure hoặc không thể xác minh SafetyHold là `CONTRACT_ERROR`/data unavailable và fail closed trước rule; không được coi hold vắng mặt hoặc tạo Decision `INCOMPLETE`. Chỉ bundle đã xác thực và decode được, với inner cap slot hiện diện nhưng enum/shape cap sai, mới đi qua dòng 5.

Không có weight, score, confidence, random model hoặc rule học từ lịch sử.

### FR-021 — Mapping outcome

| Outcome | UI | Routine được phép |
|---|---|---|
| `BLOCKED_FOR_TODAY` | Safety hold; không hiển thị mode | Không có |
| `URGENT_STOP` | Màn hình dừng khẩn; không hiển thị mode | Không có |
| `INCOMPLETE` | Lỗi form thì quay lại field; cap/state nội bộ lỗi thì fail-closed data screen | Không có |
| `PAUSE_TODAY` | Màn hình tạm nghỉ hôm nay; không hiển thị mode | Không có |
| `REST_ONLY` | Màn hình nghỉ hôm nay; không hiển thị mode | Không có |
| `RECOVER` | Mode `Hồi lại` | Chỉ `Hồi lại` |
| `MAINTAIN` | Mode `Giữ nhịp` | `Giữ nhịp` hoặc `Hồi lại` |
| `BUILD` | Mode `Tăng nhịp` | `Tăng nhịp`, `Giữ nhịp` hoặc `Hồi lại` |

Người dùng không thể chọn mode nặng hơn bằng UI, deep link, state restore hoặc sửa dữ liệu điều hướng. Backend không tồn tại; kiểm tra vẫn phải được thực hiện tại domain layer trước khi tạo `RoutineSession`.

`BLOCKED_FOR_TODAY` là runtime result khi đọc một `SafetyHold` đang active; mỗi lần mở lại chỉ render hold đã persist, không tạo `Decision` mới. `Decision` chỉ được persist khi có một canonical `CheckIn` source đã commit và luôn có `check_in_id` non-null; red/acute check-in dùng discriminated early-stop schema với field chưa hỏi là null và vẫn tạo decision+hold atomically. Persisted `INCOMPLETE` chỉ hợp lệ cho một committed Full CheckIn (`red_flag=false`, `acute_issue=none`, ba field còn lại hợp lệ) khi daily-constraint bundle đã decrypt/auth/decode thành công nhưng inner `day_mode_cap` slot present có enum/shape sai; `invalid_fields=[day_mode_cap]`. Draft form thiếu/sai chỉ render validation/runtime result; restored/migrated CheckIn thiếu/sai schema hoặc constraint envelope/auth failure là `CONTRACT_ERROR`/migration failure và không được coerce thành CheckIn/Decision orphan.

### FR-022 — Giải thích quyết định

Mỗi outcome phải có lời giải thích template-based dựa đúng vào field đã submit. Lời giải thích không được dùng từ chẩn đoán, nguyên nhân, khả năng phục hồi hoặc chắc chắn y khoa. Copy chuẩn nằm trong tài liệu UX.

### FR-023 — Tính thuần nhất và version

Mỗi recommendation phải lưu `rule_version`. Với cùng input và cùng `rule_version`, output phải giống nhau. MVP dùng `rule_version=1` tương ứng decision table trên.

### FR-024 — Day mode cap sau `too_hard`

Mỗi terminal-session feedback (`completed|stopped|abandoned`) có `effort=too_hard` và `new_or_worse_pain=no` làm giảm mức tối đa còn lại trong ngày đúng một bậc: `Tăng nhịp → Giữ nhịp → Hồi lại`; `Hồi lại` giữ nguyên. Basis là cap hiện hành nếu có, nếu không là `runtime_effective_mode_at_start` snapshot của session — authorization ceiling sau cap tại transaction start, không phải `routine_mode` nhẹ hơn do user chọn. Reducer chỉ tạo/cập nhật cap khi origin-day constraint còn active; tại/sau effective expiry vẫn lưu feedback nhưng không tạo cap ngày mới.

Active `day_mode_cap` chỉ là `MAINTAIN|RECOVER`; không có cap hoặc cap hết hiệu lực được normalize thành null. Trong bundle đã xác thực/decode, inner cap có `BUILD`, unknown enum hoặc shape sai là present-but-invalid và phải đi tới `INCOMPLETE`, không được coi như null. Decrypt/tag/key/envelope/bundle-auth failure là `CONTRACT_ERROR` trước engine, không phải cap-invalid.

Mỗi lần render recommendation/selector và mỗi Start attempt phải đọc lại authenticated active cap, tạo runtime projection `runtime_effective_mode=min(Decision.effective_mode, active_cap)` và không mutate immutable Decision. Recommendation mặc định và selector chỉ dùng projection này; cap xuất hiện sau Decision có thể làm Build cũ render/chọn tối đa Maintain. `recommendation_shown`/`routine_selected` phải giữ nullable exact `runtime_day_mode_cap_snapshot`: non-null iff cap làm runtime nhẹ hơn `Decision.effective_mode`, null nếu bằng nhau; snapshot và logical refs tới cả mode-trigger/expiry-source Session commit atomically với event. Không được mirror mù Decision đã stale hoặc tái dựng projection từ operational cap sau purge.

Session chụp terminal `LocalStamp` (`occurred_at_utc`, `local_date`, `zone_id`, offset), `session_origin_day_expires_at_utc` và clock evidence tại terminal transition. `DayModeCap.source_session_id` chỉ là expiry-source và adopt nguyên terminal stamp/expiry/evidence của Session đó; `mode_trigger_session_id` là Session có feedback lần gần nhất thực sự hạ `max_mode`. Không ghép feedback instant với session-origin date/zone. Feedback commit instant chỉ nằm trong feedback/update audit và `day_mode_cap_updated` event. Thay timezone hoặc chỉnh wall clock không được recompute để rút ngắn/bypass. Khi integrity không xác minh được, implementation được fail closed và giữ state lâu hơn một cách bảo thủ. Cap phải được giải thích trên UI, không đổi outcome gốc và không thay decision table. Nếu cùng feedback có `new_or_worse_pain=yes`, safety hold/`BLOCKED_FOR_TODAY` ưu tiên và không tạo cap từ feedback đó.

Nếu đã có active cap với origin/deadline khác candidate mới, mức vẫn hạ từ active cap nhưng constraint không được chọn deadline sớm hơn. Chỉ candidate có effective deadline **strictly later** mới thay origin/evidence/expiry `source_session_id`; nếu hai deadline bằng nhau, giữ existing expiry provenance và ghi `deadline_source=same`. Khi `max_mode` thực sự hạ, set `mode_trigger_session_id` bằng current feedback Session; nhánh existing `RECOVER→RECOVER` chỉ merge deadline thì giữ mode trigger cũ, trừ lúc tạo cap đầu tiên. Event/update snapshot vẫn giữ `trigger_session_id` của invocation hiện tại, nên field này có thể khác resulting cap `mode_trigger_session_id`.

### FR-025 — Expiry của hold/cap

Safety hold dùng cùng contract: `expires_at_utc` là audit instant tại đầu ngày địa phương kế tiếp trong `zone_id`, tính bằng timezone rules tại lúc tạo; `zone_id`, `local_date`, `expires_at_utc` immutable. Trong cùng boot có clock continuity, effective monotonic deadline là authority và state inactive tại equality của deadline đó, kể cả wall clock đã bị lùi nên `now_utc < expires_at_utc`. Đổi timezone/DST/wall clock không được recompute để rút ngắn/bypass. Sau reboot/discontinuity, resolver dùng persisted clock evidence và có thể kéo dài fail-closed bảo thủ; không clear chỉ từ wall time mới. Sau effective expiry được xác minh, app yêu cầu check-in mới theo zone hiện tại.

### FR-026 — Reminder suppression của `REST_ONLY`

`REST_ONLY` tạo một suppression cho origin day, với `zone_id` và `expires_at_utc` theo cùng cách tại `FR-025`. App hủy/skip mọi reminder còn lại trước expiry; không phát bù. Đây không phải safety hold: người dùng có thể chủ động chọn `Check-in lại` trong khung làm việc. Check-in mới chỉ supersede suppression cũ khi commit result hợp lệ: outcome có mode thì clear và lập lại fixed reminder còn tương lai; `REST_ONLY` mới thay bằng suppression mới; `URGENT_STOP|PAUSE_TODAY` tạo hold và tiếp tục không nhắc. `INCOMPLETE`, contract/migration error hoặc transaction fail giữ suppression cũ. Slot đã qua không được lập lại; retry cùng Decision không tạo side effect/event trùng.

`REST_ONLY` không tạo streak loss và không chặn manual check-in. Suppression kết thúc theo cùng effective monotonic/clock-integrity contract tại `FR-025`; persisted UTC expiry dùng cho audit, không phải phép so wall-clock duy nhất.

**Acceptance — Given/When/Then**

- Given `red_flag=true` cùng bất kỳ giá trị nào khác, when rule chạy, then kết quả luôn là `URGENT_STOP` và không có routine.
- Given safety hold đang active cùng bất kỳ check-in nào, when rule chạy, then kết quả luôn là `BLOCKED_FOR_TODAY`.
- Given `red_flag=false` và `acute_issue=acute_illness` nhưng ba field sau bị thiếu, when rule chạy, then kết quả vẫn là `PAUSE_TODAY` và tạo hold kind `ACUTE_ILLNESS`.
- Given `red_flag=false`, `acute_issue=none` và một field energy/stiffness/intent thiếu hoặc ngoài enum, when rule chạy, then kết quả là `INCOMPLETE` và không có routine.
- Given daily-constraint bundle xác thực/decode thành công nhưng inner `day_mode_cap` slot có shape/enum sai, when rule chạy, then kết quả là `INCOMPLETE`; không giả định cap null và không có routine.
- Given AES-GCM tag/key/envelope/bundle decode hoặc authentication thất bại, when entry point chạy, then fail closed với `CONTRACT_ERROR` trước engine; không bỏ qua SafetyHold, không persist Decision `INCOMPLETE` và không có routine.
- Given `red_flag=false`, `acute_issue=none`, `energy=low`, `stiffness=none`, `intent=moderate`, when rule chạy, then kết quả là `RECOVER`, không phải `BUILD`.
- Given outcome `MAINTAIN`, when người dùng cố mở routine `Tăng nhịp` qua deep link, then app từ chối tạo session và trở về danh sách `Giữ nhịp`/`Hồi lại`.
- Given outcome `BUILD` và day mode cap là `Giữ nhịp`, when tạo recommendation, then outcome gốc vẫn là `BUILD` nhưng mode hiệu lực là `Giữ nhịp`.

## 8. Thư viện routine offline

### FR-030 — Contract chung

MVP có đúng sáu routine dưới đây. Nội dung text, timer và media cần thiết được đóng gói trong app; không tải từ mạng. Mỗi routine phải:

- dài từ 2:00 đến 5:00 phút;
- không cần nằm sàn, nhảy, dụng cụ tập hoặc thay đồ;
- có hướng dẫn di chuyển trong biên độ thoải mái;
- có phương án dễ hơn cho từng động tác tải lực;
- nhắc chỉ dùng ghế/bàn/tường chắc chắn khi làm điểm tựa;
- có transcript đầy đủ thay cho âm thanh/hình ảnh;
- có stop rule: dừng ngay nếu đau mới/tăng lên, chóng mặt, khó thở bất thường hoặc cảm thấy không ổn;
- được một chuyên gia exercise/physio review và ký duyệt nội dung trước release.

### FR-031 — Danh mục chuẩn

| ID | Tên hiển thị | Mode | Thời lượng | RPE định hướng |
|---|---|---|---:|---:|
| `REC-01` | Thả lỏng tại ghế | Hồi lại | 2:00 | 1–2 |
| `REC-02` | Đi bộ chậm | Hồi lại | 3:00 | 1–2 |
| `MAI-01` | Reset bàn làm việc | Giữ nhịp | 2:00 | 2–4 |
| `MAI-02` | Mobility đứng | Giữ nhịp | 4:00 | 2–4 |
| `BUI-01` | Sức mạnh với ghế | Tăng nhịp | 4:00 | 4–6 |
| `BUI-02` | Cardio yên lặng | Tăng nhịp | 5:00 | 4–6 |

ID, tên, mode và thời lượng là contract sản phẩm. Tài liệu này không quy định sequence động tác cuối cùng. Mỗi routine phải có một content specification riêng gồm step, duration, cue, regression, điều kiện điểm tựa, stop rule, transcript/media và chữ ký duyệt của chuyên gia trước khi content lock. Không được thêm động tác impact cao hoặc kéo routine ngoài 2–5 phút mà không cập nhật requirement/version.

### FR-032 — Chọn routine deterministic

Recommendation mặc định chỉ chọn trong hai routine của mode kết quả. Ưu tiên routine chưa từng hoàn thành; nếu cả hai cùng trạng thái, chọn routine có `last_completed_at` cũ hơn; nếu vẫn hòa, chọn ID nhỏ hơn. Lịch sử chỉ dùng để luân phiên nội dung, không nâng/hạ mode.

Tại màn hình đổi bài, người dùng thấy toàn bộ routine của mode hiện tại và các mode nhẹ hơn. Nếu không có ghế/tường chắc chắn, người dùng có thể chọn một routine hợp lệ khác; app không tự suy đoán bối cảnh.

Mọi surface bind content identity thay vì hard-code/fallback từ ID: `Routine.titleKey` là tên hiển thị trên card, heading pre-flight và heading player; `Routine.summaryKey` là mô tả card/overview; khi `Cách dễ hơn` mở, heading section lấy đúng `EasierVariation.titleKey`. Key phải resolve approved entry nằm trong signed routine digest.

Pre-flight trước hết render global checklist đã ký, rồi **một block safety riêng của routine theo đúng thứ tự**: `comfortableRangeInstructionKey`; toàn bộ `setupSafetyKeys[]` theo array order; `contraindicationKeys[]` theo array order chỉ khi `contraindicationDisposition=LISTED` (không render khi `NONE_BEYOND_GLOBAL`); toàn bộ `stopRuleKeys[]` theo array order; cuối cùng `escalationMessageKey`. Không sort, gộp, paraphrase hoặc bỏ item. Ngay sau toàn block phải có một acknowledgement rõ ràng do người dùng chủ động chọn; acknowledgement chỉ là UI state tạm của đúng routine/content identity, không persist/infer/event và mặc định false. Đổi routine hoặc process loss trước Start xóa acknowledgement này.

Mỗi Start command, kể cả deep link, stale UI hoặc retry, phải mang một one-shot process-scoped `PreflightAttestationV1` do current pre-flight cấp. Attestation bind exact current process/pre-flight instance, routine ID, full content identity (`schema_version`, `content_version`, `routine_revision`, `manifest_digest_sha256`), `acknowledgement=true` và ordered exact set context field đang `REQUIRED` đã trả lời `Có`; field `NOT_REQUIRED` không được có. Start use case compare-and-remove nonce đúng một lần khỏi process-memory store **ngay trước** DB write transaction; transaction sau đó lock và revalidate current DB/content/routine/required-context binding trước khi insert Session. Missing/forged/stale/wrong routine-content, thiếu/thừa/sai thứ tự context, nonce reuse hoặc process loss trả `CONTRACT_ERROR` và không tạo Session/event; token đã consume không được phục hồi sau race/storage rollback. Attestation chỉ ở RAM, không persist vào Session/preferences/saved state, không export và không phát product event.

Sau đó pre-flight đọc bốn field context đã ký theo fixed order `stableChair`, `stableDeskOrWall`, `standingSpace`, `walkingPath`. `REQUIRED` render đúng approved `preflightRequirementKeys` và cần câu trả lời Có; `NOT_REQUIRED` không render. Start chỉ enable khi safety acknowledgement của routine hiện tại đã true **và** mọi context REQUIRED đều Có. Bất kỳ Không nào chỉ mở selector để người dùng tự chọn bài cùng/nhẹ hơn, không persist/infer context, override hay auto-fallback. Khi người dùng chọn bài khác, app bỏ acknowledgement và toàn bộ confirmation tạm của bài trước, rồi chạy lại safety block/pre-flight riêng của routine mới từ đầu; không reuse câu trả lời. `PENDING_REVIEW`, safety status/disposition/array/key/sign-off/digest sai hoặc thiếu là content contract error và không Start.

### FR-033 — Player và trạng thái phiên

Persisted lifecycle của `RoutineSession` chỉ có `ACTIVE`, `COMPLETED`, `STOPPED`, `ABANDONED`. Khi lifecycle còn `ACTIVE`, checkpoint player persist exact phase `STEP_TIMER|STEP_TRANSITION|COMPLETION_CTA_WAIT`; nullable `substate=PLAYING|PAUSED` chỉ hợp lệ ở hai phase đầu và bắt buộc null tại CTA wait. Pause/Resume và Dừng chỉ tồn tại ở `STEP_TIMER|STEP_TRANSITION`; Bỏ qua chỉ ở `STEP_TIMER` với remaining dương; cả ba absent tại CTA wait. `Cách dễ hơn`/Replay chỉ điều khiển signed instruction/demo hiện hành, không lifecycle. Phiên chỉ `COMPLETED` khi đã vào `COMPLETION_CTA_WAIT` và người dùng chọn `Hoàn thành`. Mọi terminal state phải đi qua pain gate tại `FR-043` trước session kế tiếp.

Với step `DURATION`, `planned_step_ms=seconds×1.000`; với `REPETITIONS`, `planned_step_ms=estimatedSeconds×1.000`, còn `reps` chỉ là dosage hiển thị — không có nút “đã đủ reps” và timer vẫn tự advance. `planned_transition_ms=transitionAfterSeconds×1.000`; mọi phép tính checked int64. Trong `STEP_TIMER`, persisted `current_step_remaining_ms` luôn thuộc `(0, planned_step_ms]` và UI hiển thị `ceilDiv(remaining_ms,1.000)`; equality `0` phải atomically chuyển sang `STEP_TRANSITION` nếu transition dương, sang step kế nếu transition bằng 0, hoặc `COMPLETION_CTA_WAIT` sau step cuối. `STEP_TRANSITION` tương tự chỉ persist remaining dương và tự advance tại equality; boundary zero không được persist/render như một phase còn chạy.

Mỗi monotonic callback chỉ consume tối đa remaining của **phase hiện tại**; phần lateness vượt boundary không carry sang transition/step chưa render, và phase mới neo tại current snapshot. Chỉ thời gian thực sự consume trong `STEP_TIMER` khi đang chạy cộng vào `accumulated_active_ms`; transition, pause, app background, CTA wait và planned remainder của step bị skip không được cộng. `elapsed_ms` của player và `routine_completed.duration_ms` mirror bộ đếm này; giá trị terminal freeze bất biến, không fallback wall clock hoặc auto-complete khi checkpoint/counter sai.

Mọi tick/Pause/Resume/Skip/Stop/Complete của cùng Session đi qua một per-session player-mutation coordinator rồi Room CAS. Tap Stop chụp `ClockSnapshot`, reconcile đúng current phase dưới coordinator; nếu equality đã normalize tới `COMPLETION_CTA_WAIT` thì timer thắng, Stop trả `STOP_NOT_AVAILABLE` và không mở dialog. Nếu vẫn ở timer/transition, transaction persist `substate=PAUSED`, clear segment anchor và exact checkpoint trước khi render pain dialog; PLAYING→PAUSED ghi đúng một `routine_paused`, còn đã PAUSED không ghi event lặp. Thời gian chờ dialog không tiến phase/counter. `Tiếp tục bài` chỉ resume với fresh monotonic anchor + `routine_resumed` nếu dialog cùng process bắt đầu từ PLAYING; nếu bắt đầu từ PAUSED thì đóng dialog nhưng giữ PAUSED. Pain answer terminalize từ frozen checkpoint. Process loss làm mất prior-substate UI token và recovery đọc exact PAUSED checkpoint; không tự resume.

`Bỏ qua bước` chỉ enable trong `STEP_TIMER` khi remaining dương. Reducer reconcile timer trước, rồi đúng một lần append ordered unique `{step_id, active_elapsed_ms}`, trong đó `active_elapsed_ms=planned_step_ms-current_step_remaining_ms` tại snapshot ngay trước skip; không cộng remainder và đi qua cùng next-phase reducer như timer completion. Race tại equality thuộc timer: không ghi skip. `routine_completed.step_skip_count` phải bằng `skipped_steps.size`, không đếm lại từ event.

Mỗi current step có control `Cách dễ hơn`, collapsed mặc định. Khi mở, heading section bind exact `EasierVariation.titleKey`, còn player thay/expand instruction và demo hiện tại bằng exact signed `EasierVariationStep` map tới source step; người dùng luôn có thể collapse/quay lại bản gốc. Variation kế thừa dosage, `transitionAfterSeconds`, timer và step transition của base step; không đổi mode, routine ID, session ID, elapsed progress hoặc completion semantics. Lựa chọn này chỉ là UI state tạm thời: không persist qua recovery, không suy ra preference/context cho step/session sau và không cần product event. Missing/mismatch variation mapping, instruction, demo hoặc signature là content-contract error, không tự tạo phương án dễ hơn.

Control `Replay` chỉ điều khiển media minh họa hiện tại: seek exact signed demo đang chọn về media position 0 rồi play. Nó không pause/resume/restart player phase, không đổi current/transition remaining, `accumulated_active_ms`, cadence ordinal, skip list, routine/session/status hoặc timer; không persist và không phát product event. Lỗi replay media chỉ báo lỗi asset tại chỗ, không mutate checkpoint.

Transaction tạo Session phải snapshot start `ElapsedAnchorEvidence` gồm exact `start_boot_marker`, `start_elapsed_realtime_ms`, `start_clock_generation`, `start_wall_minus_elapsed_ms`; local event `routine_started` mirror 1:1 bốn field. Đây là evidence xếp study-day elapsed so với activation anchor, không phải authorization clock evidence hoặc device identifier.

Active-session guard chặn mọi session mới. Recovery state phải giữ exact phase, step index, hai remaining, accumulated active counter, ordered skipped records, substate, monotonic anchor, `last_announced_cadence_ordinal` và content identity; không suy step progress từ session-total counter. Khi relaunch trong cùng local date, trước `work_end`, content/version/checksum còn hợp lệ và checkpoint đọc được, app route recovery để người dùng `Tiếp tục` hoặc `Kết thúc phiên`; resume đúng phase/remaining và timer không tự hoàn thành. Reboot/clock evidence không đủ hoặc đã qua `work_end`/origin date được phép atomically chuyển session thành `ABANDONED` + pending pain. Content bundle unavailable/checksum/identity mismatch cũng chỉ được làm vậy khi Session đã authenticate/decrypt, schema và checkpoint/cross-invariant vẫn hợp lệ để freeze/export nguyên giá trị. Session/checkpoint auth/decrypt/schema/invariant corrupt không được fabricate/reset một terminal checkpoint: repository giữ active guard, ghi zero normal product event, route typed `DATA_ERROR` + explicit full-reset/delete recovery; start khác vẫn bị chặn.

Tại actual start transaction, `source=reminder` + non-null `reminder_occurrence_id` chỉ được giữ khi navigation context đến từ successful notification body/`Bắt đầu` tap đã validate, occurrence resolve được với `status=DELIVERED`, `first_opened_at` non-null, và `occurrence.schedule_version_id == active == CheckIn == Decision == Session schedule_version_id`. Nếu bất kỳ điều kiện attribution nào không đạt, normalize thành `source=home` + ID null nhưng vẫn tiếp tục authorization bình thường; attribution không phải safety/start gate. Cửa sổ 60 phút chỉ dùng metric prompt-to-start, không đổi source đã persist.

**Acceptance — Given/When/Then**

- Given thiết bị không có mạng, when mở bất kỳ routine nào, then toàn bộ text, transcript, media và timer vẫn hoạt động.
- Given recommendation `Hồi lại`, when mở danh sách đổi bài, then chỉ `REC-01` và `REC-02` xuất hiện như lựa chọn bắt đầu.
- Given player bị kill ở phút 1 nhưng recovery evidence còn hợp lệ, when mở lại cùng ngày/trước `work_end`, then app route Resume/End, không tự completion và không cho session mới.
- Given active session qua reboot không còn clock evidence hoặc content corrupt, when mở lại, then app atomically ghi `ABANDONED + PENDING` và route pain gate trước session khác.
- Given current step có signed easier variation hợp lệ, when người dùng mở rồi đóng `Cách dễ hơn`, then instruction/demo đổi đúng pair và trở lại base, còn dosage/timer/transition/mode/routine/session không đổi và không ghi preference/event.
- Given step repetitions có `estimatedSeconds=30`, when timer consume đủ đúng 30 giây, then player tự chuyển phase dù người dùng chưa báo đủ reps; reps vẫn chỉ là hướng dẫn hiển thị.
- Given callback đến muộn hơn remaining của current phase, when reducer reconcile, then chỉ current phase chạm boundary và phase kế bắt đầu nguyên budget tại snapshot mới; không carry lateness.
- Given lệnh skip đua với timer tại equality, when transaction resolve, then timer completion thắng, không append skipped record/event; skip trước equality append đúng `{step_id,active_elapsed_ms}` một lần rồi chạy next-phase reducer.
- Given signed per-routine safety block đã render nhưng acknowledgement chưa chọn hoặc một context REQUIRED chưa Có, when người dùng bấm Start, then không Session được tạo; đổi routine/process loss xóa acknowledgement và block mới render lại từ đầu.
- Given player đang ở bất kỳ phase nào, when người dùng chọn Replay, then chỉ current signed demo seek về 0/play; checkpoint/timer/active counter/cadence/skip/session/event byte-identical.

## 9. Feedback và phản ứng an toàn

### FR-040 — Schema feedback

Sau mọi session terminal `completed|stopped|abandoned`, app có schema feedback đúng ba field:

| Field | Enum | Hiển thị |
|---|---|---|
| `effort` | `easy \| moderate \| too_hard` | `Nhẹ (RPE 1–3)` / `Vừa (RPE 4–6)` / `Quá sức (RPE 7–10)` |
| `new_or_worse_pain` | `yes \| no` | Có đau mới hoặc đau tăng lên không? |
| `context_fit` | `yes \| no` | Bài này có phù hợp với bối cảnh làm việc không? |

MVP không yêu cầu điểm RPE chính xác, vị trí đau hoặc ghi chú tự do. `new_or_worse_pain` là safety gate bắt buộc và được hỏi/lưu trước. `effort` và `context_fit` có thể được defer/commit độc lập cho mọi terminal state: update chỉ chuyển từng field từ null sang selected value; field không chọn giữ null, `Để sau` không ghi giá trị mới. Feedback chỉ complete khi đủ cả ba field. Qualification không đòi `effort`: completed session trên selected workday có thể góp vào `qualified_break_days` ngay khi pain=no và context=yes, dù effort còn null.

### FR-041 — `new_or_worse_pain=yes`

Khi feedback là `yes`:

1. Không đề xuất routine khác trong phiên.
2. Tạo safety hold kind `POST_SESSION_NEW_OR_WORSE_PAIN` theo `local_date`/`zone_id` tại thời điểm câu trả lời pain=yes được commit, kể cả câu trả lời ở ngày sau; expiry là đầu ngày kế tiếp của answer day theo `FR-025`.
3. Hiển thị hướng dẫn dừng vận động và danh sách red flag.
4. Không tự diễn giải nguyên nhân và không gợi ý bài nhẹ hơn.
5. Mọi lần mở/Back/deep link trước expiry chỉ hiển thị copy theo hold kind; sau expiry phải thực hiện check-in mới.

### FR-042 — Feedback khác

- `effort=too_hard` tạo/cập nhật day mode cap theo `FR-024` và UI giải thích rõ. Cap không có hiệu lực vào ngày địa phương tiếp theo.
- Commit `effort=too_hard` phải atomically lưu field và reducer side effect/event khi pain=no + origin constraint còn active; context có thể vẫn null. Commit context-only không chạy effort reducer.
- `context_fit=no` chỉ được tính vào tổng kết; không tự thay đổi reminder hay routine.
- Không feedback nào tạo streak, badge hoặc lời phê phán.

### FR-043 — Pain gate và terminal transition

Mọi session phải resolve `new_or_worse_pain=yes|no`, nhưng lifecycle không được tạo trạng thái `STOPPED+PENDING`:

- Với `completed`, thao tác `Hoàn thành` atomically commit `COMPLETED+PENDING`, rồi hỏi pain trước effort/context.
- Với `abandoned`, recovery fail-close atomically commit `ABANDONED+PENDING`. `session_guard` local giữ pending state qua crash/restart.
- Khi người dùng chọn dừng, Stop transaction ở `FR-033` đã reconcile/freeze checkpoint thành PAUSED; session vẫn `ACTIVE` trong lúc dialog hỏi trực tiếp phiên vừa rồi có đau mới/đau tăng hay không, và dialog wait không tính elapsed. Không hỏi pain có phải nguyên nhân dừng. Trả lời `no` atomically commit `STOPPED+RESOLVED_NO`; trả lời `yes` atomically commit `STOPPED+RESOLVED_HOLD` và tạo hold theo `FR-041`, cả hai dùng frozen counter/checkpoint.
- Nếu process đóng trước khi trả lời stop dialog, session vẫn `ACTIVE` và relaunch đi qua recovery tại `FR-033`; chỉ recovery fail-close mới chuyển thành `ABANDONED+PENDING`.
- Trước bất kỳ routine mới nào, start gate kiểm tra theo thứ tự active `SafetyHold` → pending pain của `COMPLETED|ABANDONED` → active-session recovery → global-safety re-ack → contract/schedule/window/freshness/outcome/mode. Pending pain trả `PENDING_SAFETY_FEEDBACK` và route `PENDING_PAIN_GATE`; acknowledgement stale trả `SCOPE_REACK_REQUIRED`. Schedule mismatch trả `RECONFIRM_REQUIRED(schedule_changed)`; ngoài current window trả `EXPIRED`; các nhánh reconfirm còn lại dùng exact reason tại `FR-012`. Stop dialog chưa trả lời thuộc active-session recovery, không phải pending pain. Các value này không phải outcome của rule engine.
- Với pending pain, trả lời `yes` atomically tạo hold theo `FR-041`, chuyển `pain_gate_status=RESOLVED_HOLD` rồi clear guard; trả lời `no` chuyển `pain_gate_status=RESOLVED_NO` và clear guard. Enum duy nhất là `PENDING|RESOLVED_NO|RESOLVED_HOLD`.
- Nếu transaction lưu answer/hold thất bại, giữ nguyên guard/state trước đó: completed/abandoned vẫn pending, còn stop flow vẫn `ACTIVE`; không được persist terminal state nửa chừng.
- Back, deep link, notification hoặc restore không được bỏ qua active/pending gate.

Với mọi terminal session, `effort/context_fit` có thể được điền sau khi pain=no; thiếu hai field này không chặn routine tiếp theo. Stopped/abandoned session không bao giờ qualify north star dù feedback đủ.

Late-answer semantics: pain gate không tự hết hạn. Pain=yes ở ngày sau vẫn tạo hold cho phần còn lại của answer day. Với pain=no, effort=too_hard được trả lời hoặc điền muộn khi resolver đã xác nhận effective session-origin expiry thì chỉ lưu feedback, không tạo cap mới. Sau khi gate resolve ở ngày sau, check-in mới vẫn bắt buộc trước routine.

**Acceptance — Given/When/Then**

- Given routine đã hoàn thành và `new_or_worse_pain=yes`, when người dùng cố bắt đầu routine khác cùng ngày, then domain layer chặn và hiển thị safety hold.
- Given session `COMPLETED|ABANDONED` chưa trả lời pain, when người dùng mở routine qua Home hoặc notification, then start gate trả `PENDING_SAFETY_FEEDBACK` và route về câu hỏi pain trước khi tạo session.
- Given stop dialog chưa được trả lời và process đóng, when relaunch với recovery evidence hợp lệ, then session vẫn `ACTIVE`, app chỉ cho Resume/End; khi trả lời stop dialog, terminal status và resolved pain status được commit atomically, không tồn tại `STOPPED+PENDING`.
- Given completed session trả lời pain=no nhưng defer effort/context, when bắt đầu routine khác, then không bị safety gate chặn; session cũ chưa qualify north star.
- Given mode hiệu lực đang là `Tăng nhịp`, feedback `effort=too_hard` và pain=no, when người dùng bắt đầu luồng mới cùng ngày, then mức tối đa là `Giữ nhịp`.
- Given một day mode cap từ hôm trước, when sang ngày địa phương mới và người dùng check-in, then cap đã hết và mode do decision table mới quyết định.
- Given pending pain được trả lời yes vào ngày sau, when answer commit, then hold dùng answer-day zone/date và chặn đến expiry của answer day.
- Given pain=no và effort=too_hard commit khi resolver xác nhận effective session origin-day expiry đã tới, when reducer chạy, then feedback được lưu nhưng không tạo cap ngày mới.

## 10. Reminder local

### FR-050 — Lập lịch

Reminder chỉ được lập trên selected workday tại fixed time `< work_end`. Occurrence ID codec duy nhất:

- Fixed preimage là exact ASCII bytes (UTF-8 byte-identical) `fixed-v1|<lowercase UUID schedule>|<0-based slot decimal>|<YYYY-MM-DD>|fixed|<generation decimal>`.
- Snooze preimage là `snooze-v1|<lowercase UUID parent>|<ordinal decimal>`.
- Decimal không leading zero, trừ giá trị `0`. Hash SHA-256; lấy 16 byte đầu, đặt high nibble byte 6 thành UUID version `8`, top bits byte 8 thành RFC variant `10`, rồi encode canonical lowercase hyphenated UUID.

Fixed logical key là `(schedule_version_id,slot_index,local_date,kind=fixed)`, slot 0-based, `generation>=0` initial 0. Mọi fixed row require `creation_reason=initial|slot_reeligible`; generation 0/không predecessor chỉ dùng `initial`. Mỗi delivered source chỉ tạo được một snooze child, nên child dùng literal `ordinal=0` dưới chính `parent_occurrence_id` đó và **không có** `creation_reason`. Receiver kiểm lại wall clock; tại `now >= work_end` skip/expire.

`ReminderOccurrence.status` chỉ nhận `SCHEDULED|DELIVERED|SNOOZED|MERGED|CANCELLED|BLOCKED_PERMISSION|SKIPPED_LATE|SKIPPED_WORK_END|SKIPPED_SAFETY_HOLD|SKIPPED_REST|SKIPPED_SESSION_GUARD|SKIPPED_NOT_SELECTED_WORKDAY`. Receiver map guard đang active sang đúng `SKIPPED_*`; permission không có dùng `BLOCKED_PERMISSION`. `CANCELLED` chỉ dùng khi event-bearing scheduler chủ động hủy occurrence pending vì schedule edit, timezone hoặc guard/state change; overlap loser dùng riêng `MERGED`, không đồng thời `CANCELLED`. Full-delete đi qua keyless PendingIntent registry/marker sau commit point, không mở Room, không transition occurrence và không emit `reminder_cancelled`.

Mọi terminal row/event là immutable và không được “đưa về” pending. Generic reconcile dùng `initial` khi logical slot chưa có row, reuse row `SCHEDULED`, và chỉ được tạo `generation=max+1`, `creation_reason=slot_reeligible` khi latest generation là `CANCELLED|BLOCKED_PERMISSION` còn slot eligible trong tương lai; ID mới có `supersedes_occurrence_id` trỏ terminal row trước. Latest `MERGED` là tombstone đã consume exact logical date/slot: cold start, resume, boot, timezone/package reconcile hay snooze chain sau đó đều không được tạo generation mới cho key đó, dù fixed due cũ còn tương lai. Latest `DELIVERED|SKIPPED_*` hoặc slot đã qua cũng không tạo row generic mới. Reconcile phải bỏ qua key đã consume và scan bounded tới selected date kế tiếp cho cùng slot; nó không được dừng cả slot chỉ vì candidate gần nhất `NotEligible`. `MERGED` loser giữ non-null `merged_into_occurrence_id` trỏ winner; tối đa một fixed generation pending cho mỗi logical key và một future pending fixed occurrence cho mỗi slot.

Mỗi fixed row mới (`initial|slot_reeligible`) phải được insert atomically cùng đúng một `reminder_scheduled` + exact refs/retention; bất kỳ event/HMAC/ref/retention failure nào rollback row. Reuse existing `SCHEDULED` emit zero event. Platform scheduling chỉ đọc full post-pair pending set sau transaction, không đặt alarm từ một row insert chưa có ledger companion.

Khi session trở thành `ACTIVE` hoặc guard chuyển sang pending pain, scheduler chủ động cancel reminder pending với reason `active_session`/`pending_pain`. Pain=no resolve guard chỉ reschedule fixed slot còn ở tương lai; pain=yes đi theo safety hold và không reschedule trong hold. Không trường hợp nào post bù slot đã qua.

Late guard: tính `lateness = now - due_at`. Nếu `lateness > 60 phút`, occurrence thành `SKIPPED_LATE`; tại đúng 60 phút occurrence chỉ được post khi mọi guard khác vẫn hợp lệ và `now < work_end`. Không có catch-up sau đó.

Mỗi occurrence persist non-null full-LocalStamp `due_at`. Transition post notification phải atomically đặt `status=DELIVERED` và non-null `delivered_at`; tap đầu tiên và delete-intent đầu tiên lần lượt set-if-null `first_opened_at`/`dismissed_at` cùng idempotent event. Occurrence chưa từng delivered bắt buộc cả ba nullable stamp này null; nếu open/dismiss cùng tồn tại thì đều `>= delivered_at`. Retry/duplicate callback không được sửa first stamp hoặc ghi event thứ hai; timestamp transition khác chỉ nằm trong event tương ứng.

Nếu quyền notification bị từ chối/thu hồi, lịch vẫn được lưu nhưng không coi reminder là delivered; Home/Cài đặt hiển thị trạng thái và đường dẫn do người dùng chủ động mở system settings.

### FR-051 — Snooze

Người dùng chỉ được snooze thủ công 15, 30 hoặc 60 phút. Notification chỉ render duration action có preview `delivered_at + duration < work_end` cùng ngày; action không đạt bị omit. Tap vẫn phải recheck authority bằng `snoozed_at + duration < work_end`; target equality/muộn hơn hoặc action stale bị reject với zero child/event/alarm. Nếu không duration nào còn preview, notification chỉ có Start/body và vẫn vuốt bỏ được mà không phạt.

Notification source đã post luôn giữ row `DELIVERED` sau thao tác snooze; không chuyển source thành `SNOOZED`. Transaction tạo child occurrence mới ở status `SNOOZED`, có `parent_occurrence_id=source`, literal `ordinal=0` và target. Chỉ child được chuyển tiếp về `DELIVERED|MERGED|CANCELLED|BLOCKED_PERMISSION|SKIPPED_*`; `reminder_snoozed` phải tham chiếu cả source và child.

Mỗi delivered source có tối đa một snooze child và chỉ callback duration thắng đầu tiên được tạo child `ordinal=0`; duration callback khác của cùng notification trở thành stale sau cleanup và trả `SNOOZE_NOT_ELIGIBLE`. MVP không có command/UI sửa pending snooze. Khi child đó thực sự `DELIVERED`, notification/action identity mới của chính child có thể tạo một grandchild `ordinal=0` dưới parent mới; đây là chain source→child mới, không thay child cũ hoặc hồi sinh fixed tombstone. Mỗi child mới chỉ pair với **fixed occurrence kế tiếp chưa consume**; fixed-fixed hoặc snooze-snooze bị validator từ chối trước merge. Nếu target trùng/cách fixed kế tiếp không quá 30 phút, due khác nhau giữ earlier; equality giữ snooze. Loser thành `MERGED` và lưu winner ID.

Toàn bundle row/event là một DB transaction dưới scheduler-mutation lease dùng chung. Transaction còn yêu cầu exact duration-action PendingIntent của source vẫn có trong validated registry và source notification tag đang active; đây là one-shot consumption guard, nên duplicate/queued callback sau cleanup trả `SNOOZE_NOT_ELIGIBLE`, không tạo child thứ hai. Nó khóa/read source, active schedule pointer/version, SessionGuard và daily constraints tại một coherent `ClockSnapshot`; chỉ nhận khi source `DELIVERED`, source chưa có child, source schedule chính là active version enabled, permission cho phép, không active session/pending pain/active hold/rest, và target vẫn cùng source day với `now < target < work_end`. Bất kỳ guard nào sai trả typed rejection với zero child/event/alarm. Với race, blocker/edit thắng trước thì snooze bị reject; snooze thắng trước thì blocker/edit sau terminalize child bằng exact cancellation reason/event trước khi release lease.

Mỗi child mới luôn có đúng một `reminder_scheduled` trỏ child và đúng một `reminder_snoozed` trỏ source→child; loser mới chuyển `MERGED` có đúng một `reminder_merged`. Không có nhánh thay child, khôi phục fixed hoặc ordinal kế tiếp trên cùng source. Không tạo cancel cho row đã terminal, không tạo merge khi không có pair. Event, exact ref-set và retention extension lỗi thì rollback toàn bộ row/status/event. Sau commit, platform reconcile enumerate full post-pair pending set gồm mọi fixed `SCHEDULED` và snooze `SNOOZED`; no-overlap có thể giữ cả hai. Kill/reboot trước AlarmManager call không được làm child mắc vĩnh viễn hoặc dùng alarm để bù ledger thiếu event.

### FR-052 — Không suy đoán bối cảnh

MVP không đọc calendar, cảm biến xe, location hoặc app đang chạy. Copy không được khẳng định người dùng đang họp, lái xe hay rảnh.

**Acceptance — Given/When/Then**

- Given `work_end=17:00` và hiện tại 16:20, when mở snooze, then 15 và 30 phút khả dụng, 60 phút bị disable.
- Given hôm nay không phải ngày làm việc đã chọn, when đến một giờ nhắc đã lưu, then app không post notification.
- Given receiver chạy đúng 60 phút sau due time và vẫn trước `work_end`, when các guard khác hợp lệ, then occurrence có thể post; tại 60 phút + 1 ms phải `SKIPPED_LATE`.
- Given fixed và snooze cùng due time, when merge, then snooze được giữ; pair cùng kind không được vào merge.

## 11. Tổng kết tuần và north star

### FR-060 — Khoảng tuần

Tuần hiển thị theo half-open interval `[thứ Hai 00:00, thứ Hai kế tiếp 00:00)` trên `local_date` đã lưu tại từng sự kiện/session. Event giữ zone và offset lúc phát sinh; đổi timezone không phân loại lại lịch sử.

### FR-061 — `qualified_break_days`

Một ngày địa phương được tính tối đa một lần khi:

1. ngày đó là ngày làm việc đã chọn tại thời điểm routine bắt đầu;
2. có ít nhất một routine `completed` trong ngày;
3. feedback của chính routine đó có `context_fit=yes`; và
4. feedback có `new_or_worse_pain=no`.

`qualified_break_days` là số ngày riêng biệt thỏa cả bốn điều kiện trong tuần. Không yêu cầu hai phiên/ngày, không phụ thuộc notification và không trừ điểm khi mute/snooze/bỏ qua.

### FR-062 — Nội dung tổng kết

Chỉ hiển thị số đếm:

- ngày có phiên phù hợp (`qualified_break_days`);
- phiên vận động đã bắt đầu/hoàn thành;
- feedback theo từng lựa chọn đã trả lời; không hiển thị count pending/missing;
- reminder đã mở, snooze và bỏ qua.

Tỷ lệ chỉ được hiển thị khi mẫu số của tỷ lệ đó từ 5 trở lên; nếu thấp hơn, hiển thị `Chưa đủ dữ liệu để tính tỷ lệ`. Persist/export dùng exact `WeeklySummaryWireV1` ở ARC §11/MET-030: stable summary ID, 13 count, ba typed rate có numerator/denominator + nullable integer percent/reason, week identity và last-computed stamp; không cho exporter tự chọn flat/nested shape. Không hiển thị correlation, xu hướng nhân quả, dự báo, score, AI summary hoặc so sánh với người khác.

## 12. Dữ liệu, export và xóa

### FR-070 — Local-only

Tất cả preference, check-in, recommendation, routine session, feedback, reminder occurrence và event log nằm trên thiết bị. Dữ liệu có cấu trúc phải được mã hóa khi lưu bằng khóa được bảo vệ bởi Android Keystore. Asset routine đóng gói không cần mã hóa như dữ liệu cá nhân.

Không SDK quảng cáo/analytics/crash qua mạng; không permission Internet; không clipboard logging; không ghi raw field nhạy cảm vào log hệ thống.

Mọi màn hình app bật Android `FLAG_SECURE`. Trong `Cài đặt > Về sản phẩm`, hiển thị đúng copy: `Để bảo vệ dữ liệu riêng tư, app chặn ảnh chụp và chia sẻ màn hình khi đang mở.` Đây chỉ là thông tin trạng thái, không tạo Help/Support route mới.

### FR-071 — Data contract tối thiểu

| Entity | Trường định danh/tối thiểu |
|---|---|
| `AppProfile` | exact wire `installation_id`, literal `adult_confirmed=true`, `eligibility_scope_confirmed=true`, `locale=vi-VN`; nonempty append-ordered `safety_acknowledgements[]` với ID/kind/version/digest/full LocalStamp và non-null `current_safety_acknowledgement_id` bằng append-last ID; `onboarding_completed_at` full LocalStamp cùng `activation_boot_marker`, `activation_elapsed_realtime_ms`, `activation_clock_generation`, `activation_wall_minus_elapsed_ms`. False/alias/extra/default bị reject |
| `WorkScheduleVersion` | id, enabled, selected weekdays; exact ASCII `work_start`, `work_end`, sorted-distinct `reminder_times[]` theo codec `HH:mm` tại `FR-003`; `effective_from`, nullable `replaced_at` |
| `CheckIn` | id, parent_id nếu reconfirm, non-null `schedule_version_id`, required `answers_kind=red_flag_stop\|acute_stop\|full` và field đúng discriminated shape, duy nhất named LocalStamp `confirmed_at` làm submit/commit/freshness/retention authority, `rule_version`; immutable freshness evidence sáu field exact nêu dưới. Không có entity/export field `submitted_at`; `check_in_submitted` envelope mirror exact `confirmed_at` |
| `Decision` | id, `check_in_id` và `schedule_version_id` non-null, outcome của committed check-in, base/effective mode nullable, ordered `reason_codes[]`, ordered `invalid_fields[]` chỉ non-empty cho `INCOMPLETE`, rule_version; byte-equal freshness evidence với CheckIn, audit-only `reconfirm_after`/`valid_until_work_end`; ba nullable immutable field `created_safety_hold_snapshot`, `created_rest_suppression_snapshot`, `evaluation_day_mode_cap_snapshot`; không tạo row mới khi render active hold |
| `Routine` | routine_id; approved `titleKey`, `summaryKey`; content `schema_version`; `content_version` là catalog `manifestVersion` SemVer; `routine_revision` SemVer; `manifest_digest_sha256`; mode, duration, steps/easier variation gồm `EasierVariation.titleKey`, exact per-routine safety/context/accessibility contracts và asset references/checksums |
| `RoutineSession` | id, `decision_id`/`schedule_version_id` non-null; `routine_id` + immutable (`schema_version`, `content_version`, `routine_revision`, `manifest_digest_sha256`); signed selected `routine_mode`, `decision_effective_mode_at_start`, `runtime_effective_mode_at_start`, nullable `runtime_day_mode_cap_snapshot_at_start`; `source=home\|reminder` + nullable `reminder_occurrence_id`; start/end/status/workday snapshot; exact `start_*`/`completion_*` evidence; terminal pain gate/answer. ACTIVE giữ exact checkpoint nullable `substate=PLAYING\|PAUSED`, `phase`, `step_index`, `current_step_remaining_ms`, `transition_remaining_ms`, `accumulated_active_ms`, ordered unique `skipped_steps[{step_id,active_elapsed_ms}]`, nullable segment anchor, checkpoint elapsed/boot, `last_announced_cadence_ordinal` và content identity. `routine_mode <= runtime_effective_mode_at_start`; runtime ceiling là no-existing-cap basis. `ACTIVE` chưa có terminal pain gate; `PENDING` chỉ với `COMPLETED\|ABANDONED`, `STOPPED` chỉ `RESOLVED_NO\|RESOLVED_HOLD`; source reminder cần validated tap + occurrence `DELIVERED`/`first_opened_at` + cùng schedule ID, Home luôn null |
| `Feedback` | session_id; exact mapping `PENDING↔new_or_worse_pain=null`, `RESOLVED_NO↔no`, `RESOLVED_HOLD↔yes`; effort/context nullable/deferred; nullable immutable `created_post_session_safety_hold_snapshot` và `day_mode_cap_update_snapshot`; nullable `pain_answered_at` + non-null `updated_at`, đều là full LocalStamp. Record pending khởi tạo `updated_at` tại terminal commit; mỗi pain/optional-feedback commit sau đó cập nhật `updated_at`; không có `submitted_at` riêng |
| `ReminderOccurrence` | canonical UUIDv8-derived `id` theo `FR-050`, `kind=fixed\|snooze` và non-null `schedule_version_id`. `fixed` require `slot_index`, `local_date`, `generation`, `creation_reason=initial\|slot_reeligible` và parent/ordinal null; `snooze` require `parent_occurrence_id`, literal `ordinal=0`, `supersedes_occurrence_id=null` và fixed slot/generation/creation_reason absent. Exact full-LocalStamp `due_at`, nullable full-LocalStamp `delivered_at`, `first_opened_at`, `dismissed_at`; exact status; `merged_into_occurrence_id` non-null iff `MERGED` |
| `SafetyHold` | kind=`RED_FLAG\|ACUTE_ILLNESS\|NEW_OR_WORSENING_PAIN_OR_INJURY\|MEDICALLY_RESTRICTED\|POST_SESSION_NEW_OR_WORSE_PAIN`, `source_type=check_in\|session`, `source_id`, created stamp=`occurred_at_utc+local_date+zone_id+utc_offset_minutes`, expires_at_utc, clock evidence, `rule_version=1` |
| `SessionGuard` | singleton có nullable `active_session_id` và nullable `pending_pain_session_id`, tối đa một field non-null; dùng atomic start gate/crash recovery và ngăn session thứ hai |
| `NotificationPromptAttemptV1` | operational `attempt_id`, `trigger=automatic_onboarding\|explicit_user_retry`, `state=PENDING\|RESOLVED\|INTERRUPTED`, `origin_process_instance_id`, `attempted_at`, nullable `resolved_at`, `prompt_result`, `interruption_reason` theo exact union tại `FR-004`; tối đa một PENDING, giữ đến full delete và không export row trực tiếp |
| `DayModeCap` | terminal-origin stamp của adopted expiry `source_session_id` gồm `occurred_at_utc+local_date+zone_id+utc_offset_minutes`, `max_mode`, non-null `mode_trigger_session_id`, `expires_at_utc`, clock evidence, `rule_version=1`; feedback/event giữ update instant riêng |
| `RestDaySuppression` | source_decision_id, immutable origin stamp gồm `occurred_at_utc+local_date+zone_id+utc_offset_minutes`, expires_at_utc, clock evidence, `rule_version=1`; decision mới supersede enforcement state nhưng không sửa snapshot nguồn |
| `WeeklySummary` | exact `WeeklySummaryWireV1`: `summary_id`, Monday `week_start_local_date`, immutable `week_zone_id`, last-computed LocalStamp, 13 nonnegative count và ba exact rate object; one stable row/ID per week-start, recompute không slide cutoff |
| `LocalEvent` | schema tại tài liệu analytics |

Mọi entity ID là UUID ngẫu nhiên local, ngoại trừ `ReminderOccurrence.id` deterministic theo codec tại `FR-050`; cả hai vẫn là opaque canonical UUID. Không lưu tên, email, số điện thoại, vị trí, advertising ID hoặc device ID.

`installation_id` được sinh bằng CSPRNG đúng trong transaction tạo eligible profile đầu tiên, rồi mirror vào mọi LocalEvent và profile export. Staged onboarding events chỉ commit sau transaction này và dùng cùng ID. Full delete xóa ID; onboarding đủ điều kiện tiếp theo sinh ID mới, không khôi phục/reuse giá trị cũ.

Codec nguồn hold là explicit: domain `ConstraintSourceType.CHECK_IN|SESSION` serialize storage/export/event thành lowercase `source_type=check_in|session`; reader từ chối alias/case khác và xác minh `source_id` đúng loại.

Freshness evidence trên CheckIn/Decision dùng exact wire fields `confirmed_boot_marker`, `confirmed_elapsed_realtime_ms`, `ttl_monotonic_deadline_ms`, `confirmed_clock_generation`, `confirmed_zone_id`, `confirmed_wall_minus_elapsed_ms`; hai record phải mirror byte-equal từ cùng submit transaction. `reconfirm_after`/`valid_until_work_end` là audit instant, không thay durable resolver hoặc current-window/date reason mapping tại `FR-012`.

Mọi constraint audit snapshot là deep value copy bất biến và dùng exact five-field clock evidence: `origin_boot_marker`, `created_elapsed_realtime_ms`, `monotonic_deadline_ms`, `remaining_elapsed_ms_at_last_checkpoint`, `original_duration_ms`. Snapshot hold gồm `kind`, `source_type`, `source_id`; snapshot cap gồm `max_mode`, `mode_trigger_session_id`, expiry `source_session_id`; snapshot rest gồm `source_decision_id`. Cả ba luôn kèm `rule_version=1`, full LocalStamp bốn field và `expires_at_utc`.

Invariant side effect:

- `Decision` là immutable sau commit. `URGENT_STOP|PAUSE_TODAY` có đúng `created_safety_hold_snapshot`; `REST_ONLY` có đúng `created_rest_suppression_snapshot`; decision có reason `SAF_DAY_MODE_CAP_APPLIED` có `evaluation_day_mode_cap_snapshot`. Field không áp dụng phải null.
- Nếu một active cap xuất hiện hoặc nhẹ hơn snapshot của Decision trước lúc start, `RoutineSession.runtime_day_mode_cap_snapshot_at_start` giữ full `applied_cap`, `decision_effective_mode_before_runtime_cap` và `runtime_effective_mode_at_start`; không mutate Decision.
- Feedback reducer giữ `created_post_session_safety_hold_snapshot` khi commit `RESOLVED_HOLD`. Mỗi cap update thực sự commit giữ `day_mode_cap_update_snapshot` gồm `trigger_session_id`, `expiry_source_session_id`, `basis_mode`, nullable `previous_max_mode`, full `resulting_cap` và `deadline_source=existing_later|candidate_later|same`. `trigger_session_id` là invocation Session; nguồn stamp/expiry/evidence của resulting cap là `expiry_source_session_id`; resulting cap `mode_trigger_session_id` chỉ bằng invocation khi max mode vừa hạ hoặc cap đầu tiên vừa được tạo.
- Mỗi side effect keyed theo source session/decision chỉ commit một lần; retry trả snapshot cũ. Enforcement row hằng ngày có thể bị supersede/purge, nhưng không được null/rewrite snapshot lịch sử hoặc tái dựng snapshot từ state hiện hành.

`RoutineSession.decision_id` không được null ở bất kỳ lifecycle state nào; Session/Decision/CheckIn phải có cùng schedule version ID. Khi session còn `ACTIVE` hoặc pain gate còn `PENDING`, retention phải giữ cả `Decision`, `CheckIn` và `WorkScheduleVersion` nguồn; chỉ sau khi pain resolve mới áp dụng purge order thông thường, luôn giữ FK hợp lệ.

Không retained event hoặc active constraint nào được outlive entity nguồn. Late feedback extend `Session+Decision+CheckIn+ScheduleVersion`; active hold từ check-in giữ CheckIn+Decision+ScheduleVersion, rest suppression giữ Decision+CheckIn+ScheduleVersion, cap/post-session hold giữ Session+Decision+CheckIn+ScheduleVersion. Mọi retained cap snapshot, kể cả trong projection event, giữ graph của cả `mode_trigger_session_id` và expiry `source_session_id`, dedupe nếu bằng nhau. Required mirror event còn có reverse companion edge đóng: source kéo đúng onboarding/ack, check-in/decision+side-effect, session start/skip/terminal/pain/feedback+side-effect, reminder create/snooze/delivery/interaction/resolution và weekly-generation event theo `ARC-024`; late extension kéo các companion này trước khi event tiếp tục kéo source graph. Ordinary event ref chỉ truyền event→entity, không truyền ngược, nên universal AppProfile ref không biến mọi history thành full-delete retention. `ReminderOccurrence` giữ ít nhất 90 ngày hoặc đến authority muộn nhất của event, companion hay `RoutineSession(source=reminder)` tham chiếu nó; occurrence đồng thời giữ `WorkScheduleVersion` nguồn. Purge dựng exact companion-only deletion set tới least fixed point bằng cách lặp source→mọi required event→mọi peer source của event; peer mới lại kéo toàn companion của nó. Nó không traverse ordinary refs, xóa event trước source và chỉ xóa schedule sau khi không còn CheckIn/Decision/Session/occurrence tham chiếu. Full delete của người dùng vẫn xóa ngay toàn bộ graph.

Retention dùng encrypted `RetentionAuthorityV1 = Finite(RetentionCutoffV1) | UntilFullDeleteFromAppProfile`, không sentinel max-date. Finite cutoff giữ `policy_version=1`, allowlisted `source_kind` gồm `companion_reference`, `source_id`, full origin LocalStamp, positive `calendar_days` và immutable `deadline_at_utc`; prefilter non-null. Full branch chỉ từ profile/ack, có prefilter null và maintenance không purge. Cutoff 90 ngày được tính đúng một lần tại start-of-day của `origin.local_date + 90 calendar days` trong `origin.zone_id` theo ZoneRules/DST; không dùng timezone/ngày hiện tại, `90×24h` hoặc recompute trượt. Equality mới eligible và vẫn phải không còn reference/companion blocker. Directed work queue chỉ adopt full branch hoặc finite candidate có deadline strictly muộn hơn; candidate sớm hơn/equal giữ current. Weekly summary cố định tại `week_start_local_date + 13 weeks` (`91` ngày) và recompute không dịch cutoff. Full delete đã xác nhận vẫn ưu tiên và xóa ngay.

### FR-072 — Export miễn phí

Trong Cài đặt, người dùng có thể chủ động export không giới hạn và không cần tài khoản/thanh toán. App tạo đúng một file JSON UTF-8 qua Android Storage Access Framework. Root có `metadata` object gồm đúng `export_schema_version=1`, `exported_at_utc`, `app_version`, SemVer `content_version` của catalog `manifestVersion` đang đóng gói, `rule_version=1`, integer `retention_policy_version=1`, `record_counts`; và chín array: `profile`, `work_schedule`, `check_ins`, `decisions`, `sessions`, `feedback`, `reminders`, `events`, `weekly_summaries`. `profile` có tối đa một record và nested acknowledgement history/current audit; counts phải khớp snapshot. Mỗi session export giữ content identity lịch sử và exact four-field start/completion elapsed evidence; `routine_started`/`routine_completed` mirror respective evidence. Không suy các giá trị này từ metadata hoặc raw UTC delta.

App hiển thị trước rằng file có thể chứa dữ liệu tự đánh giá nhạy cảm và là plaintext sau khi nằm ngoài vùng lưu trữ của app. App chỉ ghi vào vị trí người dùng chọn, không upload và không tự mở share sheet. Export không thay đổi/xóa dữ liệu gốc.

Constraint không có collection thứ mười. `decisions` export nguyên ba exact snapshot field bất biến; `sessions` export nullable runtime-cap-at-start snapshot; `feedback` export post-session hold/cap-update reducer snapshot; `events` export nullable projection cap snapshot theo exact event contract. Mỗi nested constraint giữ đủ rule version, kind/mode/mode-trigger/expiry-source, full LocalStamp, expiry và five-field clock evidence nêu tại `FR-071`. Export đọc trực tiếp các snapshot đã commit; tuyệt đối không tái dựng từ enforcement row hằng ngày sau purge hoặc sửa Decision lịch sử.

### FR-073 — Xóa toàn bộ miễn phí

Sau hai bước xác nhận, app phải:

- hủy toàn bộ notification/snooze đang chờ;
- xóa database, event log, preferences và khóa dữ liệu dành riêng cho app;
- đưa app về age gate;
- không xóa được các file export mà người dùng đã lưu ngoài vùng app; copy phải nói rõ điều này.

Xóa không có paywall và không cần network.

### FR-074 — Chính sách quyền riêng tư khả dụng offline

Trong `Cài đặt > Về sản phẩm`, `Chính sách quyền riêng tư` phải mở toàn văn và số phiên bản đã được duyệt, đóng gói trong app và đọc được offline. Text, version và digest của bản đóng gói phải khớp release artifact đã ký duyệt; thiếu hoặc sai digest là release blocker.

App có thể hiển thị CTA phụ `Mở bản công khai`. CTA này chỉ gửi URL đã duyệt cho trình duyệt ngoài bằng Android intent; app không tải policy trong WebView, không tự gọi mạng và không cần permission Internet. Nếu không có app ngoài xử lý URL, bản offline vẫn đọc được và app hiển thị lỗi không chặn.

**Acceptance — Given/When/Then**

- Given thiết bị ở airplane mode, when người dùng export, then file đầy đủ vẫn được tạo tại vị trí họ chọn.
- Given người dùng xác nhận xóa toàn bộ, when mở lại app, then không check-in/session/feedback/reminder nào còn truy cập được và onboarding bắt đầu lại.
- Given thiết bị chưa từng có mạng, when mở `Chính sách quyền riêng tư`, then toàn văn/version đã duyệt hiển thị từ asset bundled.
- Given người dùng chọn `Mở bản công khai`, when có trình duyệt ngoài xử lý URL, then app chỉ dispatch external intent và không render nội dung mạng trong app.

## 13. Yêu cầu phi chức năng

### FR-080 — Offline và khả năng phục hồi

Mọi luồng trong phạm vi phải hoạt động khi thiết bị chưa từng có mạng. Lỗi lập lịch, lỗi export hoặc process death không được làm mất dữ liệu đã commit. Migration dữ liệu phải có version và rollback an toàn hoặc fail closed với lựa chọn export/xóa.

### FR-081 — Accessibility

- Mọi control có label TalkBack và thứ tự focus hợp lý.
- Font hỗ trợ kích thước hệ thống ít nhất đến 200% mà không che CTA an toàn.
- Touch target tối thiểu 48×48 dp.
- Không chỉ dùng màu để biểu đạt mode, lỗi hoặc safety.
- Transcript và timer dạng text luôn có; âm thanh/rung không phải tín hiệu duy nhất.
- Animation tôn trọng cài đặt giảm chuyển động của hệ thống.
- Các CTA `Dừng`, `Tìm trợ giúp`, `Có đau mới/đau tăng` không bị đặt ở vị trí dễ bấm nhầm với tiếp tục.

Sáu message-key field của `Routine.accessibility` có binding duy nhất, không alias/fallback:

- `screenReaderTitleKey` → semantic pane title/level-1 heading trên cả pre-flight và Player;
- `routineOverviewKey` → accessible description/semantic label của overview ngay sau title trên pre-flight; visible overview text vẫn là `Routine.summaryKey`;
- `postureAndSetupKey` → accessible heading/description đặt ngay trước per-routine safety sequence;
- `stopButtonLabelKey` → TalkBack label của control `Dừng bài`;
- `pauseButtonLabelKey` → TalkBack name của action Pause chỉ khi `substate=PLAYING`; khi `substate=PAUSED`, action Resume dùng fixed app resource `player_resume_action` (`Tiếp tục`) và tuyệt đối không reuse signed Pause key;
- `skipButtonLabelKey` → TalkBack label của control `Bỏ qua bước`.

Mọi key phải resolve approved entry trong routine digest. `Routine.titleKey` vẫn là visual title, không thay cho `screenReaderTitleKey`; step announcement vẫn dùng exact `RoutineStep.screenReaderInstructionKey`.

### FR-082 — Copy và claim

Store listing, onboarding, notification, routine và summary chỉ dùng ngôn ngữ general wellness. Cấm các từ/cấu trúc: chẩn đoán, chữa, điều trị, phòng bệnh, phát hiện stress/chấn thương, phục hồi X%, app biết cơ thể cần gì, hoặc khẳng định nhân quả từ dữ liệu nhật ký.

### FR-083 — Content integrity

Content identity gồm content `schema_version`, catalog `content_version` tương ứng `manifestVersion`, routine `routine_revision` và `manifest_digest_sha256`; ba version là SemVer và digest là 64 ký tự lowercase hex. Loader phải xác minh schema, manifest digest, routine revision, tham chiếu và checksum asset trước khi cho start, rồi snapshot nguyên identity vào session/export. Nếu asset/identity thiếu, sai hoặc không tương thích `rule_version=1`, routine không được bắt đầu; app mở selector để người dùng tự chọn một routine hợp lệ cùng hoặc nhẹ hơn, không stream nội dung thay thế. Default selector không tự fallback sang mode khác khi hai routine của effective mode đều unavailable. Mọi thay đổi step/stop rule phải bump routine revision và catalog manifest version/digest theo content contract.

## 14. Ma trận release acceptance

| ID | Given | When | Then |
|---|---|---|---|
| `FR-AC-01` | App mới cài, airplane mode | Hoàn thành onboarding | Toàn bộ onboarding và Home hoạt động; không network error. |
| `FR-AC-02` | Mọi tổ hợp enum hợp lệ | Chạy rule version 1 | Output khớp decision table và ổn định qua các lần chạy. |
| `FR-AC-03` | Red flag hoặc acute issue | Submit check-in | Không tạo recommendation/session; tạo reason-specific safety hold với expiry cố định. |
| `FR-AC-04` | Outcome bất kỳ | Chọn routine | Chỉ cùng mode hoặc nhẹ hơn được domain layer chấp nhận. |
| `FR-AC-05` | Sáu routine bundled | Chạy offline | Mỗi bài hoàn tất trong 2–5 phút và không cần tải asset. |
| `FR-AC-06` | Session `COMPLETED\|ABANDONED` chưa trả lời pain | Mở routine khác | Start gate trả `PENDING_SAFETY_FEEDBACK`; chưa tạo session. Pain=yes tạo hold, pain=no clear guard; stop flow không tạo `STOPPED+PENDING`. |
| `FR-AC-07` | Reminder ngoài ngày/giờ hợp lệ | Scheduler chạy | Không notification ngoài lịch; không phát bù. |
| `FR-AC-08` | Dữ liệu tuần <5 mẫu cho một tỷ lệ | Mở summary | Không hiển thị phần trăm; hiển thị trạng thái chưa đủ dữ liệu. |
| `FR-AC-09` | Có một completion tốt và một completion pain=yes cùng ngày | Tính north star | Ngày được tính nếu completion tốt có feedback context=yes, pain=no; completion pain=yes vẫn kích hoạt safety hold từ thời điểm đó. |
| `FR-AC-10` | Người dùng không cấp notification | Dùng app 7 ngày | Check-in, routine, feedback, summary, export và delete vẫn đầy đủ. |
| `FR-AC-11` | Không tài khoản/thanh toán | Export hoặc delete | Thao tác luôn khả dụng miễn phí. |
| `FR-AC-12` | Bộ test static/network | Kiểm tra release APK | Không permission Internet và không endpoint/SDK telemetry. |
| `FR-AC-13` | `REST_ONLY` vừa tạo | Scheduler/check-in mới chạy | Reminder còn lại bị skip; mode clear + chỉ reschedule fixed slot tương lai, Rest thay suppression, urgent/pause thay bằng hold; Incomplete/error giữ suppression cũ và retry không tạo side effect trùng. |
| `FR-AC-14` | Hold/cap được tạo rồi timezone/wall clock đổi | Re-evaluate bằng clock-integrity evidence | Same-boot monotonic equality kết thúc state; reboot/discontinuity có thể extend bảo thủ nhưng không clear sớm; sau effective expiry cần check-in mới. |
| `FR-AC-15` | Release APK và privacy artifact đã ký | So text/version/digest/URL | Asset bundled khớp artifact, mở được offline; URL chỉ mở bằng external browser intent, không WebView/network trong app. |
| `FR-AC-16` | Decision/check-in thuộc schedule A, active schedule là B | Start | Trả `RECONFIRM_REQUIRED(reason=schedule_changed)`; không tạo Session cho tới check-in B, và ba entity cùng schedule ID. |
| `FR-AC-17` | Bundled global safety version/digest khác current acknowledgement | Mở check-in/start hoặc tap reminder | Sau hold/pain/recovery guard, route `SCOPE_REACK_REQUIRED`; commit append history/pointer nhưng không đổi activation anchor. |
| `FR-AC-18` | Routine có context REQUIRED/NOT_REQUIRED và EmergencyDialContract đã ký | Pre-flight/urgent screen | Chỉ REQUIRED hỏi fixed order, Không chỉ mở selector; số hiển thị/ACTION_DIAL dùng cùng target, không ACTION_CALL/auto-call. |
| `FR-AC-19` | Daily constraint purge hoặc invalid persisted input | Export/evaluate | Immutable source snapshots vẫn đầy đủ; chỉ valid Full CheckIn + authenticated inner cap shape/enum invalid persist `INCOMPLETE`; auth/envelope/other schema failure fail contract trước engine. |
| `FR-AC-20` | Session start evidence và `routine_started` mirror | Xếp study day | Chỉ same boot/generation, non-rollback elapsed và mapping drift ≤2,000 ms được xếp theo elapsed; discontinuity là `unknown_clock`, không fallback UTC. |
| `FR-AC-21` | Delivered reminder được snooze; child sau khi deliver được snooze tiếp và pair fixed bị merge | Reconcile | Mỗi source giữ DELIVERED và có tối đa một child ordinal 0; callback cùng source lần hai bị reject; terminal fixed không resurrect, loser lưu winner ID và slot scan tiếp selected date sau. |
| `FR-AC-22` | Current step có signed `EasierVariationStep` | Toggle `Cách dễ hơn`/quay lại base | Instruction+demo map đúng source step; dosage/transition/timer và mode/routine/session bất biến; không persistence/inference/event. |
| `FR-AC-23` | Delivered reminder A đã mở, sau đó active schedule/check-in/decision là B | Start routine hợp lệ | Attribution A không block start nhưng normalize `source=home`, `reminder_occurrence_id=null`; chỉ validated DELIVERED/opened occurrence cùng schedule mới giữ reminder source. |
| `FR-AC-24` | Check-in/player có pause, background đúng/trên 10 phút, skip và chờ CTA sau timer | Ghi/tính timing | Flow dùng same-process/boot monotonic XOR duration/invalid reason; đúng 10 phút vẫn tính, trên 10 phút là `background_over_10m`; player chỉ cộng `PLAYING`, không wall/fabricated time và terminal freeze. |
| `FR-AC-25` | Pre-flight routine A có một REQUIRED đã Có rồi một REQUIRED chọn Không | Chọn routine B cùng/nhẹ hơn | Bỏ confirmation tạm của A; mở pre-flight của B từ REQUIRED đầu tiên theo fixed order và không reuse/infer answer cũ. |
| `FR-AC-26` | Adapter trả dialog-launchable hoặc settings-required; hoặc app chết/lưu lỗi/late callback quanh dialog | Bấm CTA/relaunch | Dialog branch commit PENDING+prompted trước launcher, xử lý interrupt/retry/false=`not_granted`; Settings branch mở trực tiếp, không attempt/prompted/PENDING, same-process return ghi one `source=settings` observation kể cả no-change và bị loại khỏi initial-prompt metric. |
| `FR-AC-27` | Bất kỳ màn hình app và `Cài đặt > Về sản phẩm` | Thử screenshot/screen share và đọc disclosure | `FLAG_SECURE` chặn capture; About hiển thị exact privacy copy, không mở route Help/Support mới. |
| `FR-AC-28` | Check-in submit transaction thành công | Persist/export/event | CheckIn có đúng một `confirmed_at`; event `check_in_submitted` envelope quartet byte-equal stamp này; không entity/export alias `submitted_at`. |
| `FR-AC-29` | DURATION/repetitions step, transition, late callback, skip race và recovery checkpoint | Chạy/relaunch player | Auto-advance theo signed seconds/estimatedSeconds; callback không carry qua phase; skip chỉ trước equality và ghi ordered active-elapsed record một lần; recovery resume exact phase/remaining/counter/skips, không zero boundary hay fabricated time. |
| `FR-AC-30` | Routine safety content APPROVED và context contract hợp lệ | Mở pre-flight/chuyển routine/process recreate/deep-link hoặc retry Start | Render exact five-part signed sequence + explicit ephemeral acknowledgement; Start chỉ consume một process-scoped attestation bind full identity, ack=true và ordered exact REQUIRED-Yes set. Missing/stale/reuse/switch/loss trả `CONTRACT_ERROR`, không Session/event/persist/export attestation. |
| `FR-AC-31` | Player ở STEP_TIMER, transition, pause hoặc CTA wait | Chọn `Replay` | Chỉ current signed demo seek 0/play; checkpoint, timer, counter, cadence, skips, session và event log không đổi. |
| `FR-AC-32` | Approved Routine/content/accessibility contract | Render card, pre-flight, player và easier section bằng TalkBack | title/summary/easier title cùng sáu accessibility key bind đúng element; missing/wrong/alias key fail content validation, không fallback từ ID/text khác. |

## 15. Definition of Done cho MVP

MVP chỉ được coi là hoàn tất khi:

- mọi requirement/acceptance case thực sự được định nghĩa trong tài liệu này (từ `FR-001` đến `FR-083`, gồm ID có suffix, và `FR-AC-01` đến `FR-AC-32`) đều có test hoặc evidence; không suy ra requirement từ số thứ tự bị bỏ trống;
- decision table có exhaustive unit test cho đúng 1.296 tổ hợp Cartesian hợp lệ và fixture single-invalid/auth failure riêng;
- sáu routine đã có chuyên gia ký duyệt nội dung và QA asset offline;
- safety copy và store copy được review theo định vị general wellness;
- accessibility check bằng TalkBack, font 200% và reduced motion hoàn tất;
- export được parse lại theo schema, delete được xác minh không còn dữ liệu app;
- privacy text/version/digest và public URL khớp release artifact đã ký; full text mở được offline;
- pilot event export vượt qua schema/allowlist, không có direct-identifier field; raw file vẫn được phân loại pseudonymous sensitive vì có installation ID/timestamp/schedule và không được gọi là anonymous/de-identified;
- không có account, network, AI, wearable, paywall, calendar hoặc driving detection trong release build.
