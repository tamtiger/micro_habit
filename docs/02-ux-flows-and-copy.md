# Nhịp 2 Phút — UX flows và copy `vi-VN`

- Phiên bản: 1.0
- Trạng thái: `Implementation baseline`; safety/content copy cần external sign-off trước release
- Phạm vi: Android MVP, giao diện tiếng Việt
- Requirement nguồn: [01-product-requirements.md](./01-product-requirements.md)

Tài liệu này khóa luồng, trạng thái và copy chức năng. Có thể chỉnh dấu câu hoặc xuống dòng để phù hợp layout, nhưng không được đổi ý nghĩa an toàn, thứ tự chặn, tên enum/mode hoặc thêm claim sức khỏe nếu chưa cập nhật requirement.

## 1. Nguyên tắc trải nghiệm

### UX-001 — Một hành động chính

Mỗi màn hình chỉ có một CTA chính rõ ràng. Home ưu tiên `Check-in`, `Xác nhận lại` hoặc `Bắt đầu bài`, tùy state; không ưu tiên biểu đồ hay streak.

### UX-002 — Không phán xét

Dùng `Bỏ qua`, `Nghỉ hôm nay`, `Để lúc khác`; không dùng `thất bại`, `mất streak`, `lười`, `bỏ cuộc` hoặc thông báo gây áp lực.

### UX-003 — An toàn trước cường độ

Safety gate luôn đứng trước câu hỏi trạng thái. Khi bị chặn, không hiển thị routine thay thế, CTA tăng mức hoặc nội dung “thử nhẹ hơn”.

### UX-004 — Minh bạch, không score

Mọi recommendation có mục `Vì sao` dùng template xác định trước. Không có score, confidence, AI badge, correlation hoặc ngôn ngữ chẩn đoán.

### UX-005 — Offline rõ ràng

Không cần trạng thái mạng trong core flow vì mọi chức năng chạy offline. Không hiển thị đăng nhập, sync, mua gói, kết nối wearable, calendar hoặc quyền vị trí.

## 2. Kiến trúc thông tin

Bottom navigation có ba mục:

1. `Hôm nay`: check-in, mode, recommendation, safety state.
2. `Tuần này`: tổng kết số đếm và các tỷ lệ đủ mẫu.
3. `Cài đặt`: lịch làm việc, thông báo, giới hạn sản phẩm, export, xóa dữ liệu.

Trong onboarding không hiển thị bottom navigation. Routine player và các màn hình safety là full-screen để giảm phân tâm.

## 3. Danh mục màn hình và trạng thái

| Screen ID | Tên | Trạng thái chính |
|---|---|---|
| `SCR-001` | Chào mừng | Chưa onboarding |
| `SCR-002` | Age gate | Đủ tuổi / không đủ tuổi |
| `SCR-003` | Giới hạn sản phẩm và eligibility | Chưa trả lời / đủ điều kiện / safe-exit |
| `SCR-004` | Lịch làm việc | Hợp lệ / lỗi validation |
| `SCR-005` | Xin quyền thông báo | Chưa hỏi / cấp / từ chối |
| `SCR-006` | Xác nhận lại phạm vi an toàn | `SCOPE_REACK_REQUIRED` / accepted / safe-exit |
| `SCR-010` | Home | Cần check-in / check-in hiện hành / cần xác nhận lại / ngoài giờ / rest suppression / pending pain / safety hold |
| `SCR-011` | Red-flag gate | Có / không |
| `SCR-012` | Trạng thái hiện tại | Bốn field bắt buộc |
| `SCR-013` | Dừng khẩn | `URGENT_STOP` |
| `SCR-014` | Tạm nghỉ | `PAUSE_TODAY` |
| `SCR-015` | Nghỉ hôm nay | `REST_ONLY` |
| `SCR-016` | Dữ liệu chưa đủ | `INCOMPLETE` |
| `SCR-020` | Nhịp hôm nay | `RECOVER` / `MAINTAIN` / `BUILD` |
| `SCR-021` | Chọn bài | Cùng mode hoặc nhẹ hơn |
| `SCR-022` | Chuẩn bị | Pre-flight an toàn |
| `SCR-023` | Routine player | Running / paused / step skipped / stop confirm |
| `SCR-023R` | Khôi phục phiên active | Resume / End / fail-closed abandon |
| `SCR-024A` | Pain safety gate | Pending / yes / no |
| `SCR-024B` | Feedback còn lại | Effort/context pending hoặc complete |
| `SCR-025` | Safety hold | `BLOCKED_FOR_TODAY`, copy theo persisted hold kind |
| `SCR-030` | Notification snooze actions | Subset 15/30/60 khả dụng tại post; tap luôn recheck |
| `SCR-040` | Tuần này | Empty / counts / rates đủ mẫu |
| `SCR-050` | Cài đặt | Lịch / thông báo / dữ liệu |
| `SCR-051` | Export | Explain / progress / success / failure |
| `SCR-052` | Xóa dữ liệu | Confirm step 1 / step 2 / complete |
| `SCR-053` | Chính sách quyền riêng tư | Bundled full text / external-browser unavailable |

## 4. Onboarding

### UX-010 — Chào mừng

**Tiêu đề**

> Ngắt nhịp 2–5 phút giữa ngày làm việc

**Nội dung**

> Check-in nhanh, nhận một bài vận động ngắn hoặc chọn nghỉ. Không cần tài khoản, mạng hay đồng hồ thông minh.

**CTA chính:** `Bắt đầu`  
**CTA phụ:** không có.

### UX-011 — Age gate

Toàn bộ copy trong route này là candidate `PENDING_EXTERNAL_SIGN_OFF` và bind theo typed slot: câu hỏi=`ageGate.questionKey`, mô tả=`ageGate.descriptionKey`, hai lựa chọn=`adultOptionLabelKey`/`minorOptionLabelKey`; safe-exit dùng đúng `ageGate.safeExit.titleKey/bodyKey/closeActionLabelKey`. Release không được hard-code hoặc đổi role giữa các slot.

**Câu hỏi**

> Bạn đã đủ 18 tuổi?

**Mô tả**

> Phiên bản này chỉ dành cho người từ đủ 18 tuổi.

**Lựa chọn:** `Tôi đã đủ 18 tuổi` / `Tôi chưa đủ 18 tuổi`.

Nếu chưa đủ tuổi:

**Tiêu đề:** `Bạn chưa thể dùng phiên bản này`  
**Nội dung:** `Nhịp 2 Phút hiện chỉ dành cho người từ đủ 18 tuổi.`  
**CTA:** `Đóng app`.

Không có link bỏ qua hoặc nút Back đi vòng age gate.

Lựa chọn `Tôi chưa đủ 18 tuổi` chỉ tồn tại trong RAM để render safe-exit; không tạo profile/event hoặc lưu tuổi cụ thể.

### UX-012 — Giới hạn sản phẩm

> Scope/eligibility/safe-exit wording dưới đây là implementation copy candidate `PENDING_EXTERNAL_SIGN_OFF`; route này bind exact typed `scopeEligibility`: `titleKey`; ordered `bodyKeys[generalWellnessLimitKey, excludedUseCasesKey, stopWarningKey]`; `acknowledgementLabelKey`; `questionKey`; yes label/description; no-or-unsure label; `continueActionLabelKey`; và nested `safeExit.titleKey/bodyKey/closeActionLabelKey`. Toàn bộ key được cover bởi root `globalSafetyContentDigestSha256` và sign-off hợp lệ theo `CNT-015`/`CNT-050`.

**Tiêu đề**

> Trước khi bắt đầu

**Nội dung**

> Nhịp 2 Phút hỗ trợ vận động general wellness và không thay thế tư vấn, chẩn đoán hoặc điều trị y tế.
>
> Không dùng app để phục hồi chấn thương, hậu phẫu, thai kỳ/hậu sản cần cá nhân hóa, quản lý bệnh mạn hoặc làm trái giới hạn vận động đã được chuyên gia y tế đưa ra.
>
> Luôn dừng nếu bạn thấy đau mới, đau tăng, chóng mặt, khó thở bất thường hoặc cảm thấy không ổn.

**Checkbox bắt buộc:** `Tôi đã đọc và hiểu giới hạn này.`  

Sau checkbox, hỏi một self-attestation đóng:

**Câu hỏi:** `Phiên bản này có phù hợp với bạn lúc này?`

- `Có` — `Tôi có thể tự thực hiện vận động general-wellness nhẹ đến vừa; hiện không có dấu hiệu cảnh báo, bệnh cấp, đau/chấn thương mới hoặc tăng, hay giới hạn vận động từ chuyên gia; tôi không cần app cho các mục đích cá nhân hóa nêu trên.`
- `Không hoặc tôi không chắc`.

Không hỏi tên bệnh, chẩn đoán, thai status, vị trí đau hoặc lý do cụ thể. **CTA chính:** `Tiếp tục` — chỉ enable sau checkbox và lựa chọn `Có`.

Nếu chọn `Không hoặc tôi không chắc`:

**Tiêu đề:** `Phiên bản này chưa phù hợp với bạn lúc này`  
**Nội dung:** `Đừng dùng app để bắt đầu vận động. Nếu bạn cần hướng dẫn phù hợp với tình trạng hoặc giới hạn hiện tại, hãy hỏi chuyên gia y tế phù hợp.`  
**CTA:** `Đóng app`.

Safe-exit không có đường vòng qua Back/deep link. Trên eligible path, confirmation `Có`, exact content identity và acceptance LocalStamp chỉ được staging trong RAM cho tới successful initial `Lưu lịch`; trước commit đó chưa có Profile/entity/event. Lựa chọn safe-exit và lý do không được persist.

Tại initial `Lưu lịch`, staged acceptance mới append một immutable record `kind=onboarding` vào `profile.safety_acknowledgements[]` với `content_version=ContentManifest.manifestVersion`, `content_digest=globalSafetyContentDigestSha256` và staged full LocalStamp, rồi atomically đặt `current_safety_acknowledgement_id` cùng profile, initial schedule/active pointer và toàn bộ onboarding event bundle. Không dùng app version, manifest root digest hoặc routine digest thay thế.

### UX-013 — Lịch làm việc

**Tiêu đề:** `Bạn muốn được nhắc khi nào?`

**Field:**

- `Ngày làm việc`: chip `T2` đến `CN`, chọn 1–7 ngày.
- `Bắt đầu`: time picker.
- `Kết thúc`: time picker, phải sau giờ bắt đầu trong cùng ngày.
- `Giờ nhắc`: một giờ bắt buộc; `+ Thêm giờ nhắc` để có giờ thứ hai. Mỗi giờ phải `>= Bắt đầu` và `< Kết thúc`.

**Helper:** `App chỉ nhắc vào ngày đã chọn, theo giờ địa phương trên điện thoại.`

**Validation copy:**

| Điều kiện | Copy |
|---|---|
| Chưa chọn ngày | `Chọn ít nhất một ngày làm việc.` |
| Kết thúc không sau bắt đầu | `Giờ kết thúc phải sau giờ bắt đầu và trong cùng một ngày.` |
| Reminder ngoài khung | `Giờ nhắc phải nằm trong khung làm việc.` |
| Hai reminder trùng nhau | `Hai giờ nhắc cần khác nhau.` |

**CTA chính:** `Lưu lịch`.

Tap hợp lệ gọi một transaction zero-or-all tạo Profile, first acknowledgement/current pointer, initial enabled ScheduleVersion/active pointer, staged eligible events và đúng các event `scope_acknowledged`, `work_schedule_saved(change_source=onboarding)` và `onboarding_completed`. Nếu commit lỗi, ở lại màn lịch với retry local; không mở Home/permission, không để dữ liệu nửa vời. Chỉ sau successful commit mới điều hướng tới `UX-014`.

### UX-014 — Permission primer

**Tiêu đề:** `Cho phép nhắc vào giờ bạn đã chọn?`  
**Nội dung:** `App chỉ dùng thông báo cho các giờ nhắc trên thiết bị bạn vừa đặt. Bạn vẫn dùng đầy đủ app nếu không cho phép.`  
**CTA chính:** adapter dialog-launchable dùng `Cho phép thông báo`; settings-required dùng `Mở cài đặt Android`.  
**CTA phụ:** `Để sau`.

Màn này là post-onboarding: Profile, lịch và activation anchor đã commit trước frame đầu. `Để sau`, unavailable, denied hoặc process death trong permission flow không rollback/ghi lại onboarding và không đổi activation anchor.

Với dialog-launchable, khi bấm CTA app phải lưu durable prompt attempt + prompted event trước rồi mới gọi ActivityResult launcher. Nếu lưu lỗi, không mở system prompt và hiển thị retry local. Callback không cấp quyền có thể là Deny hoặc Dismiss; app không tự gắn nhãn hành động nào, chỉ dùng copy chung:

> Đã lưu lịch. Thông báo đang tắt; bạn vẫn có thể tự mở app bất cứ lúc nào trong khung làm việc.

**CTA:** `Vào Hôm nay`.

Không mở lại system prompt tự động ở lần chạy sau, kể cả app chết trước callback. `Để sau` không tự launch. Khi attempt còn `PENDING`, disable CTA launcher/retry và không tạo attempt thứ hai. Nếu process bị recreate trước callback, app đánh dấu attempt cũ `INTERRUPTED`, không hiển thị nó như Deny/Dismiss; lúc đó explicit CTA `Thử bật lời nhắc` mới enable và tạo attempt ID mới trước launcher nếu dialog còn launchable. Late callback cũ không đổi state/attempt mới. Bất kỳ automatic dialog attempt cũ nào cũng ngăn auto-prompt vĩnh viễn tới full delete.

Với settings-required/unavailable, CTA mở Settings **không** tạo PromptAttempt hoặc `notification_permission_prompted`, nên không bao giờ để PENDING. Khi quay lại cùng process, UI đọc OS state và ghi một observation `source=settings` dù state không đổi; callback resume lặp không nhân event. Nếu process bị recreate trong lúc ở Settings, không render interrupted attempt vì không có attempt; app chỉ đọc runtime state/generic resume observation. Settings không được tự mở lại: người dùng phải bấm CTA lần nữa.

### UX-015 — Global safety re-ack

Khi bundled manifest/global sign-off hợp lệ nhưng current acknowledgement không khớp exact `manifestVersion+globalSafetyContentDigestSha256`, UI route `SCOPE_REACK_REQUIRED` trước check-in/start. Route này chỉ được xét sau active hold, pending pain và active-session recovery; data/pointer/digest không xác thực được là fail-closed data error, không phải re-ack bình thường.

Toàn bộ heading/body/attestation/CTA safety dưới đây là implementation copy candidate. Route re-ack dùng `scopeEligibility.reackTitleKey`, cùng ordered `bodyKeys`, acknowledgement/question/yes/no slots của `scopeEligibility`, `reackContinueActionLabelKey`, và nested `scopeEligibility.safeExit`; release chỉ render exact typed slot được cover bởi current root digest và `CNT-015`/`CNT-050` sign-off.

**Tiêu đề:** `Nội dung an toàn đã được cập nhật`

**Nội dung:** render lại đúng ordered `scopeEligibility.bodyKeys` và self-attestation typed slots hiện hành; không diff tự do hoặc paraphrase bản cũ.

**CTA chính:** `Tôi đồng ý và tiếp tục` — chỉ enable sau người dùng đọc/xác nhận và chọn eligibility `Có`. Khi commit, append immutable acknowledgement `kind=reack`, atomically cập nhật `current_safety_acknowledgement_id`, rồi trở lại flow đã mở.

**Lựa chọn:** `Không hoặc tôi không chắc` bind `scopeEligibility.noOrUnsureOptionLabelKey` và route nested `scopeEligibility.safeExit`; không lưu raw reason hoặc coi acknowledgement cũ là current.

Re-ack không chạy lại age gate, không sửa `onboarding_completed_at`/activation evidence, không reset study day và không phát lại onboarding. Back/deep link không đi vòng màn này.

## 5. Home và daily check-in

### UX-020 — Home state

Ưu tiên route Home: active `SafetyHold` → pending pain → active-session recovery → `SCOPE_REACK_REQUIRED`; chỉ sau đó mới xét schedule ID, work window và freshness.

| State | Tiêu đề/nội dung | CTA chính |
|---|---|---|
| Trước `work_start` | `Chưa đến giờ làm việc` / `Bạn có thể check-in từ {work_start}.` | Không có CTA check-in/routine |
| Trong giờ, chưa check-in | `Bạn thấy thế nào lúc này?` / `Trả lời nhanh để chọn nhịp hôm nay.` | `Check-in 20 giây` |
| Check-in còn hiệu lực | Hiển thị mode, “Vì sao” và routine đề xuất | `Bắt đầu bài` |
| Freshness resolver trả non-`FRESH` do elapsed TTL/clock evidence | `Đã đến lúc xác nhận lại` / `Xác nhận lại trạng thái hiện tại trước khi chọn bài tiếp theo.` | `Xác nhận lại` |
| Trong current window nhưng Decision thuộc local date trước | `Bắt đầu một check-in mới` / `Câu trả lời trước thuộc một ngày khác.` | `Check-in lại` (`local_date_changed`) |
| Active schedule ID khác `schedule_version_id` của Decision | `Lịch làm việc vừa thay đổi` / `Hãy check-in lại để dùng khung làm việc hiện tại.` | `Check-in lại` |
| Global safety acknowledgement cần cập nhật | `Cần xác nhận nội dung an toàn mới` / `Xem lại phạm vi sử dụng trước khi check-in hoặc mở bài.` | `Xem và xác nhận` |
| Tại hoặc sau `work_end` | `Ngày làm việc đã kết thúc` / `Check-in hôm nay đã hết hiệu lực. Hẹn bạn vào khung làm việc tiếp theo.` | Không có CTA routine |
| `REST_ONLY` suppression | `Bạn đã chọn nghỉ hôm nay` / `Các lời nhắc còn lại đã được bỏ qua. Bạn vẫn có thể check-in lại nếu trạng thái thay đổi.` | `Check-in lại` |
| Pending pain gate | `Cần hoàn tất kiểm tra an toàn` / `Hãy trả lời câu hỏi bắt buộc về việc bạn có đau mới hoặc đau tăng sau phiên trước hay không.` | `Trả lời ngay` |
| Safety hold | Tiêu đề/copy theo kind tại `UX-034` | `Xem hướng dẫn an toàn` |

Ngày không được chọn vẫn cho phép người dùng tự check-in trong khung giờ đã cấu hình, nhưng không có thông báo và completion không được tính vào `qualified_break_days`.

CTA `Trả lời ngay` của pending-pain Home row bind exact `playerSafety.pendingPainGate.entryActionLabelKey`; không hard-code một CTA khác ở entry surface.

### UX-021 — Red-flag gate

Màn hình này luôn là bước đầu của check-in và reconfirmation.

Binding cố định: heading=`redFlagGate.questionKey`; năm dòng dấu hiệu theo đúng tuple order `symptomKeys[chestPainOrPressureKey, severeDizzinessOrFaintingKey, abnormalBreathlessnessKey, abnormalRapidOrIrregularHeartbeatKey, acuteOrRapidlyWorseningSymptomKey]`; lựa chọn Có/Không=`anyPresentOptionLabelKey`/`nonePresentOptionLabelKey`. Không sort, gộp hoặc dùng option label làm symptom copy.

**Tiêu đề**

> Bạn có dấu hiệu nào sau đây ngay lúc này?

**Danh sách**

- Đau hoặc cảm giác đè nặng vùng ngực.
- Chóng mặt nhiều hoặc ngất.
- Khó thở bất thường hoặc nghiêm trọng.
- Tim đập nhanh hoặc loạn nhịp bất thường.
- Đau cấp tính hoặc triệu chứng tăng mạnh.

**Lựa chọn:** `Có, ít nhất một dấu hiệu` / `Không có dấu hiệu nào`.

Chọn `Có` chuyển ngay sang `SCR-013`; không hiển thị các câu còn lại.

### UX-022 — Trạng thái hiện tại

**Tiêu đề:** `Trạng thái của bạn lúc này`

> Câu hỏi/nhãn `acute_issue` là implementation candidate `PENDING_EXTERNAL_SIGN_OFF`. Release bind exact typed `acuteIssueGate.questionKey` và fixed `acuteIssueGate.optionBindings` đã được cover bởi root `globalSafetyContentDigestSha256`/sign-off `CNT-015`/`CNT-050`; không hard-code clinical substitute, sort hoặc bind label theo text.

Hỏi `acute_issue` trước. Nếu chọn một binding có value khác `none`, chuyển ngay sang `PAUSE_TODAY`; không hỏi energy/stiffness/intent. Nếu value là `none`, ba nhóm còn lại mới xuất hiện và đều cần một lựa chọn trước khi CTA enabled.

| Field | Câu hỏi | Lựa chọn hiển thị → enum |
|---|---|---|
| `acute_issue` | exact `acuteIssueGate.questionKey` | fixed tuple: `[0].labelKey` → `[0].value=none`; `[1].labelKey` → `[1].value=acute_illness`; `[2].labelKey` → `[2].value=new_or_worsening_pain_or_injury`; `[3].labelKey` → `[3].value=medically_restricted` |
| `energy` | `Năng lượng của bạn?` | `Thấp` → `low`; `Ổn` → `okay`; `Tốt` → `good` |
| `stiffness` | `Bạn thấy cứng người mức nào?` | `Không` → `none`; `Nhẹ` → `mild`; `Rõ rệt` → `notable` |
| `intent` | `Bạn muốn vận động mức nào?` | `Nghỉ` → `rest`; `Nhẹ nhàng` → `gentle`; `Vừa phải` → `moderate` |

**Helper dưới intent:** `Bạn luôn có thể chọn nghỉ hoặc chọn nhịp nhẹ hơn.`  
**CTA chính:** `Xem nhịp hôm nay`.

Không có giá trị preselected trong check-in mới. Khi reconfirm một check-in hiện hành, hiển thị chip `Đã chọn trước đó`, prefill các field đã lưu nhưng vẫn yêu cầu người dùng đi qua red/acute gate và bấm `Xác nhận trạng thái hiện tại`.

## 6. Safety và rest outcomes

> Trạng thái duyệt: toàn bộ safety wording trong mục này và acute gate tại `UX-022` là implementation copy candidate, `PENDING_EXTERNAL_SIGN_OFF`; không được hiểu là đã được duyệt lâm sàng/pháp lý. Route phải bind exact typed `redFlagGate`, `acuteIssueGate`, `urgentStop`, `pauseToday`, literal `holdRouteBindings`, `corruptHoldFailClosed` và `nextDayRecheck`; không dùng bucket/key array hoặc suy route/enum từ text. Release build chỉ render slot được cover bởi root `globalSafetyContentDigestSha256` và `globalSafetySignOff` hợp lệ theo `CNT-015`/`CNT-050`. Nếu chưa có bản duyệt hợp lệ, build phải fail closed và không phát hành.

### UX-030 — `URGENT_STOP`

Binding cố định: title=`urgentStop.titleKey`; body giới hạn/không đánh giá=`urgentStop.limitationBodyKey`; CTA đóng=`urgentStop.closeActionLabelKey`. Hướng dẫn/số/CTA khẩn cấp là object dùng chung `emergencyDial`, render riêng ngay trước CTA; không nhét key emergency vào `urgentStop` hoặc hard-code trong body.

**Tiêu đề:** `Dừng lại và tìm trợ giúp ngay`

**Nội dung**

> Đừng bắt đầu bài vận động. App không thể đánh giá nguyên nhân hay mức độ nghiêm trọng.

Ngay trước CTA, render candidate từ `emergencyDial.instructionTemplateKey`: `Nếu triệu chứng nghiêm trọng, đang tăng lên hoặc bạn không chắc mình an toàn, hãy gọi số trợ giúp khẩn cấp {emergency_number} hoặc nhờ người ở gần hỗ trợ.`; thay đúng một placeholder bằng `dialTargetDigits`. Nhãn CTA bind `actionLabelKey`. **CTA chính:** candidate `Mở ứng dụng Điện thoại` — dispatch `Intent(ACTION_DIAL, Uri.fromParts("tel", dialTargetDigits, null))` tới chính số đang hiển thị; không `ACTION_CALL`, không tự gọi và không xin phone permission. Nếu không resolve được dialer, render `unavailableMessageKey`.  
**CTA phụ:** candidate `Đóng app`, bind `urgentStop.closeActionLabelKey`.

Không hiển thị mode, routine, `thử bài nhẹ`, snooze hay completion.

Khi tạo `URGENT_STOP`, app persist safety hold kind `RED_FLAG`; mở lại trước expiry vẫn render đúng message key/digest của nội dung này và không cho re-answer để bypass.

### UX-031 — `PAUSE_TODAY`

Binding cố định: title=`pauseToday.titleKey`; reason lần lượt `pauseToday.reasonKeys.acuteIllnessKey|newOrWorseningPainOrInjuryKey|medicallyRestrictedKey`; body chung=`pauseToday.bodyKey`; CTA=`pauseToday.homeActionLabelKey`. Reason phải lấy từ exact acute enum, không từ string hiển thị.

**Tiêu đề:** `Tạm dừng vận động hôm nay`

**Reason theo enum:**

- `acute_illness`: `Bạn cho biết đang bị bệnh cấp tính.`
- `new_or_worsening_pain_or_injury`: `Bạn cho biết có đau/chấn thương mới hoặc đang tăng.`
- `medically_restricted`: `Bạn cho biết đang được chuyên gia y tế giới hạn vận động.`

**Nội dung chung**

> Hôm nay app sẽ không đề xuất phiên vận động. Hãy nghỉ và làm theo hướng dẫn của chuyên gia y tế nếu bạn có hướng dẫn riêng. Nếu xuất hiện dấu hiệu nghiêm trọng hoặc tăng nhanh, hãy tìm trợ giúp khẩn cấp.

**CTA:** `Về Hôm nay`.

Khi tạo `PAUSE_TODAY`, app persist hold kind đúng enum `ACUTE_ILLNESS`, `NEW_OR_WORSENING_PAIN_OR_INJURY` hoặc `MEDICALLY_RESTRICTED`; mở lại trước expiry dùng đúng reason copy tương ứng.

### UX-032 — `REST_ONLY`

**Tiêu đề:** `Nghỉ hôm nay là một lựa chọn hợp lệ`  
**Nội dung:** `Bạn đã chọn nghỉ. App sẽ bỏ qua các lời nhắc còn lại trong ngày đã ghi nhận và không có streak hay điểm bị mất.`  
**CTA chính:** `Về Hôm nay`  
**CTA phụ:** `Check-in lại`.

Không dùng mode `Hồi lại` thay cho `REST_ONLY`; nghỉ là outcome riêng và không chứa routine. `REST_ONLY` không phải safety hold. `Check-in lại` chỉ supersede state cũ sau result commit: mode clear suppression và chỉ lập lại fixed reminder còn tương lai; Rest thay suppression; urgent/pause thay bằng safety hold và tiếp tục không nhắc. `INCOMPLETE`, lỗi dữ liệu hoặc save fail giữ Rest state cũ. Không phát bù slot đã qua hoặc tạo side effect trùng khi retry.

### UX-033 — `INCOMPLETE`

Trong form chưa commit, field thiếu/sai render validation/runtime `INCOMPLETE`; acute enum non-`none` hợp lệ vẫn đi thẳng tới `PAUSE_TODAY` dù field sau chưa hỏi. Không persist Decision cho draft này. Restored/migrated CheckIn thiếu/sai canonical schema là `CONTRACT_ERROR`/migration failure và dùng operational fail-closed state, không coerce thành persisted `INCOMPLETE`.

**Tiêu đề:** `Cần kiểm tra lại câu trả lời`  
**Nội dung:** `Một hoặc nhiều lựa chọn chưa hợp lệ. App chưa mở bài vì chưa thể xác minh đủ câu trả lời.`  
**CTA chính:** `Kiểm tra lại` — trở về field đầu tiên thiếu/sai.  
**CTA phụ:** `Về Hôm nay`.

Persisted `INCOMPLETE` chỉ có thể xuất phát từ committed Full CheckIn hợp lệ khi daily-constraint bundle đã decrypt/auth/decode thành công nhưng inner `day_mode_cap` slot present có enum/shape sai, với `invalid_fields=[day_mode_cap]`. Không focus một control không tồn tại và không dùng flow form ở trên. Render fail-closed data state:

**Tiêu đề:** `Chưa thể đọc giới hạn an toàn`  
**Nội dung:** `App chưa thể xác minh mức vận động được phép, nên sẽ không mở bài.`  
**CTA chính:** `Thử lại`  
**CTA phụ:** `Quản lý dữ liệu` — mở section export/xóa trong Cài đặt.

Không clear/default cap hoặc tạo routine. AES-GCM tag/key/envelope failure, bundle decode/auth failure hoặc SafetyHold không xác minh được route cùng operational fail-closed UI nhưng domain gate là `CONTRACT_ERROR` trước engine; không tạo `INCOMPLETE` hoặc bỏ qua hold. Export có thể thất bại nếu record không đọc được; `Xóa toàn bộ dữ liệu` vẫn khả dụng qua hai bước xác nhận.

### UX-034 — Render `BLOCKED_FOR_TODAY` theo hold kind

`SCR-025` không dùng một thông điệp chung. Nó đọc persisted `SafetyHold.kind`, resolve đúng literal trong `holdRouteBindings`, rồi render approved typed route:

| Hold kind | Signed binding literal | Typed route |
|---|---|---|
| `RED_FLAG` | `holdRouteBindings.redFlag="urgentStop"` | `urgentStop` / `UX-030` |
| `ACUTE_ILLNESS` | `holdRouteBindings.acuteIllness="pauseToday.acuteIllness"` | `pauseToday` + acute illness reason / `UX-031` |
| `NEW_OR_WORSENING_PAIN_OR_INJURY` | `holdRouteBindings.newOrWorseningPainOrInjury="pauseToday.newOrWorseningPainOrInjury"` | `pauseToday` + pain/injury reason / `UX-031` |
| `MEDICALLY_RESTRICTED` | `holdRouteBindings.medicallyRestricted="pauseToday.medicallyRestricted"` | `pauseToday` + medically restricted reason / `UX-031` |
| `POST_SESSION_NEW_OR_WORSE_PAIN` | `holdRouteBindings.postSessionNewOrWorsePain="playerSafety.painResponse"` | `playerSafety.painResponse` / `UX-053` |

Render hold đến khi clock-integrity resolver xác nhận effective expiry. Trong cùng boot, monotonic deadline là authority và hết tại equality dù wall clock bị lùi; persisted `expires_at_utc` là audit value. Sau reboot/clock discontinuity, UI có thể fail closed lâu hơn nhưng không clear sớm chỉ từ wall time mới. Sau effective expiry, mọi CTA bài trước hết route tới check-in mới theo zone hiện tại.

Sau effective expiry, màn candidate bắt buộc bind typed `nextDayRecheck`:

- title=`nextDayRecheck.titleKey`: `Hãy check-in lại trước khi chọn bài`;
- body=`nextDayRecheck.bodyKey`: `Trạng thái trước đã hết hiệu lực. Hãy trả lời một check-in mới cho thời điểm hiện tại.`;
- CTA=`nextDayRecheck.checkInActionLabelKey`: `Check-in mới`.

Không reuse title/body/CTA của hold đã hết hạn hoặc tự ghép copy từ current date.

Nếu hold kind/source không đọc hoặc xác thực được, không fallback sang pain/acute copy. Validator yêu cầu `holdRouteBindings.corruptHoldFailClosed="corruptHoldFailClosed"`; UI render exact `corruptHoldFailClosed.titleKey/bodyKey/retryActionLabelKey/manageDataActionLabelKey` (candidate `Chưa thể đọc trạng thái chặn` / `App chưa thể xác minh trạng thái hiện tại, nên sẽ không mở bài.` / `Thử lại` / `Quản lý dữ liệu`); domain start gate dùng `CONTRACT_ERROR`, không tạo outcome/Decision giả.

## 7. Recommendation và chọn mode/routine

### UX-040 — Tên và mô tả mode

| Mode | Mô tả cố định |
|---|---|
| `Hồi lại` | `Nhịp nhẹ, ưu tiên chuyển động thoải mái.` |
| `Giữ nhịp` | `Nhịp vừa để tạo một quãng vận động trong ngày.` |
| `Tăng nhịp` | `Nhịp vừa có thêm tải lực, chỉ khi bạn chủ động muốn.` |

Không dùng “thấp/trung bình/cao”, màu traffic-light hoặc phần trăm phục hồi.

### UX-041 — “Vì sao” deterministic

`SCR-020` có heading `Vì sao app đề xuất nhịp này?` và một trong các template sau:

| Outcome/điều kiện | `reason_codes` exact | Copy chuẩn |
|---|---|---|
| `RECOVER`, energy low + stiffness notable | `SAF_ENERGY_LOW`, `SAF_STIFFNESS_NOTABLE` | `Bạn đang thấy năng lượng thấp và cứng người rõ rệt, nên app đề xuất nhịp nhẹ.` |
| `RECOVER`, chỉ energy low | `SAF_ENERGY_LOW` | `Bạn đang thấy năng lượng thấp, nên app đề xuất nhịp nhẹ.` |
| `RECOVER`, chỉ stiffness notable | `SAF_STIFFNESS_NOTABLE` | `Bạn đang thấy cứng người rõ rệt, nên app đề xuất nhịp nhẹ.` |
| `BUILD` | `SAF_BUILD_CONDITIONS` | `Bạn thấy năng lượng tốt, không cứng hoặc chỉ cứng nhẹ, và muốn vận động vừa phải. App đề xuất Tăng nhịp.` |
| `MAINTAIN` | `SAF_MAINTAIN_DEFAULT` | `Bạn không chọn nghỉ, năng lượng hiện tại không thấp và mức cứng người không rõ rệt, nên app đề xuất Giữ nhịp.` |

Template `MAINTAIN` chỉ nhận các tổ hợp còn lại sau rule priority; nếu một giá trị lẽ ra dẫn đến `RECOVER`, QA phải coi đó là lỗi rule/copy, không hiển thị template sai.

**Dòng cố định:** `Đây là gợi ý general wellness từ câu trả lời của bạn, không phải đánh giá y tế.`

Nếu day mode cap làm mode hiệu lực nhẹ hơn outcome gốc, thêm ngay sau `Vì sao`:

> Phiên trước được bạn đánh giá Quá sức, nên mức cao nhất cho đến khi giới hạn này hết hiệu lực là {Giữ nhịp/Hồi lại}. Sau đó app sẽ cần một check-in mới.

Trường hợp này thêm `SAF_DAY_MODE_CAP_APPLIED` sau reason gốc. Không thay reason của outcome gốc và không gọi đây là tự học/cá nhân hóa.

### UX-042 — Quyền chọn nhẹ hơn

Trên recommendation:

- `RECOVER`: không hiển thị selector mode; chỉ có các bài `Hồi lại`.
- `MAINTAIN`: selector có `Giữ nhịp` và `Hồi lại`.
- `BUILD`: selector có `Tăng nhịp`, `Giữ nhịp`, `Hồi lại`.

**Helper:** `Bạn có thể giữ nhịp được đề xuất hoặc chọn nhẹ hơn.`

Không hiển thị mode nặng hơn dưới dạng disabled vì có thể tạo áp lực. Back/deep link không được khôi phục mode trái rule.

### UX-043 — Danh sách routine

Card routine có: tên, mode bằng text, thời lượng, mô tả một dòng, yêu cầu điểm tựa nếu có và CTA `Chọn bài này`.

Binding bắt buộc: tên card, heading pre-flight và heading player đều resolve cùng exact `Routine.titleKey`; mô tả card và overview resolve `Routine.summaryKey`. Bảng dưới chỉ mô tả canonical output mong đợi của approved key, không phải string fallback hard-code từ routine ID. Key missing/unapproved/digest mismatch làm card unavailable và không Start.

| ID | Tên | Mô tả card |
|---|---|---|
| `REC-01` | Thả lỏng tại ghế | `2 phút · Hồi lại · Nội dung offline.` |
| `REC-02` | Đi bộ chậm | `3 phút · Hồi lại · Nội dung offline.` |
| `MAI-01` | Reset bàn làm việc | `2 phút · Giữ nhịp · Nội dung offline.` |
| `MAI-02` | Mobility đứng | `4 phút · Giữ nhịp · Nội dung offline.` |
| `BUI-01` | Sức mạnh với ghế | `4 phút · Tăng nhịp · Nội dung offline.` |
| `BUI-02` | Cardio yên lặng | `5 phút · Tăng nhịp · Nội dung offline.` |

Chi tiết động tác, yêu cầu điểm tựa, cue và regression lấy từ content specification đã được chuyên gia ký duyệt; UX không tự suy diễn từ tên routine.

CTA phụ ở recommendation: `Đổi bài` và `Nghỉ lúc này`. `Nghỉ lúc này` đóng luồng, không đổi check-in thành `REST_ONLY` và không tạo completion.

## 8. Chuẩn bị và routine player

### UX-050 — Pre-flight

Heading pre-flight bind `Routine.titleKey`; overview ngay dưới bind `Routine.summaryKey`. Safety checklist/warning chung bind exact typed `preflightSafety.titleKey` và ordered tuple `preflightSafety.checklistKeys[clearSpaceKey, stableSupportKey, comfortableRangeKey, stopWarningKey]`; không sort hoặc bỏ dòng. Release yêu cầu các slot được cover bởi `globalSafetyContentDigestSha256` và sign-off `CNT-015`/`CNT-050`.

Ngay sau global checklist, render một block per-routine từ `Routine.safetyContent` theo **đúng thứ tự**:

1. exact `comfortableRangeInstructionKey`;
2. từng `setupSafetyKeys[]` theo array order;
3. từng `contraindicationKeys[]` theo array order chỉ khi `contraindicationDisposition=LISTED`; khi `NONE_BEYOND_GLOBAL`, array phải rỗng và UI không tự viết dòng thay thế;
4. từng `stopRuleKeys[]` theo array order;
5. exact `escalationMessageKey`.

Không sort, collapse mặc định, paraphrase hoặc dùng global checklist thay block này. `status` khác APPROVED, disposition/nullability sai, array bắt buộc rỗng/non-empty sai, key/review/digest/sign-off thiếu hoặc mismatch là content-contract error và Start bị chặn.

Ngay sau toàn block trong cả visual order và semantic/focus order hiển thị checkbox/toggle explicit với stable UI copy: `Tôi đã đọc hướng dẫn an toàn của bài này.` Mặc định unchecked. Đây là acknowledgement tạm gắn với exact routine/content identity: không persist/infer/event; đổi routine, thay content identity hoặc process loss trước Start phải clear. Không khóa focus theo scroll position và không suy “đã đọc” chỉ từ scroll; enforcement duy nhất là acknowledgement vẫn unchecked cho tới khi người dùng chủ động chọn.

Các prompt context riêng từng routine bind `Routine.context.preflightRequirementKeys` đã được per-routine review/sign-off theo `CNT-013`/`CNT-050`; không dùng một nguồn thay thế nguồn kia.

**Tiêu đề:** `Trước khi bắt đầu`

**Checklist copy**

- `Dọn một khoảng nhỏ quanh bạn.`
- `Chỉ dùng ghế, bàn hoặc tường nếu chúng chắc chắn.`
- `Di chuyển trong biên độ thoải mái; bạn có thể giảm tốc độ hoặc bỏ qua bước.`
- `Dừng ngay nếu có đau mới/đau tăng, chóng mặt, khó thở bất thường hoặc cảm thấy không ổn.`

Đọc đủ bốn context field theo thứ tự cố định `stableChair → stableDeskOrWall → standingSpace → walkingPath`:

- field `REQUIRED`: render exact approved `preflightRequirementKeys[field]` cùng lựa chọn `Có` / `Không`;
- field `NOT_REQUIRED`: không render prompt;
- `PENDING_REVIEW`, key null khi REQUIRED, key non-null khi NOT_REQUIRED hoặc key/sign-off/digest không hợp lệ: chặn Start như content-contract error.

Chỉ enable Start khi acknowledgement của safety block đang checked **và** mọi prompt REQUIRED được chọn `Có`. Bất kỳ `Không` nào chỉ mở selector để người dùng tự chọn bài cùng mode hoặc nhẹ hơn; không override, không persist câu trả lời, không tự chọn/fallback bài và không suy ra context cho lần sau. Khi người dùng chọn routine khác, discard acknowledgement và toàn bộ confirmation tạm của routine trước, render safety block của routine mới từ item đầu rồi mới tới context prompt đầu tiên theo fixed order; không reuse answer cũ dù hai routine cùng hỏi một field. Process loss trước Start cũng clear toàn bộ state tạm.

**CTA chính:** `Bắt đầu {N} phút`  
**CTA phụ:** `Chọn bài khác`.

### UX-051 — Player

Stop flow bind exact `playerSafety.stopDialog.titleKey/questionKey/continueRoutineActionLabelKey`; hai lựa chọn bind shared `playerSafety.painAnswerLabels.yesKey/noKey`. Không reuse title/question của terminal hoặc pending pain gate.

Player luôn hiển thị nội dung hiện hành:

- tên routine từ exact `Routine.titleKey` và bước hiện tại;
- dosage hiện tại và timer còn lại bằng text;
- cue ngắn và link `Xem hướng dẫn dạng chữ`;
- `Cách dễ hơn` và media `Replay` khi current signed step/demo tương ứng tồn tại.

Control lifecycle là exact, không render tất cả ở mọi phase:

- Trong `STEP_TIMER` hoặc `STEP_TRANSITION`, `Dừng bài` hiện; Pause hiện khi `substate=PLAYING`, Resume (`Tiếp tục`) hiện khi `substate=PAUSED`.
- `Bỏ qua bước` chỉ hiện/enable trong `STEP_TIMER` với `current_step_remaining_ms>0`; tại equality hoặc mọi phase khác không còn action skip.
- Trong `COMPLETION_CTA_WAIT`, Stop, Pause/Resume và Skip đều **không hiện**; chỉ completion content/CTA cùng media/instruction không làm đổi checkpoint nếu còn được render.

Player render từ một checkpoint duy nhất với `phase=STEP_TIMER|STEP_TRANSITION|COMPLETION_CTA_WAIT` và nullable `substate=PLAYING|PAUSED`; không tính timer thứ hai từ video/wall clock. Step `DURATION` đếm theo signed `seconds`. Step `REPETITIONS` hiển thị mục tiêu `{reps} lần` nhưng vẫn đếm và tự chuyển bước theo signed `estimatedSeconds`; không có CTA “đã đủ reps”. Trong `STEP_TIMER`, text giây còn lại dùng `ceilDiv(current_step_remaining_ms,1.000)`, nên 1/999/1.000 ms đều hiển thị 1. Tại equality zero, reducer chuyển atomically sang transition, step kế hoặc CTA hoàn thành; UI không giữ/render một step timer zero.

`STEP_TRANSITION` chỉ tồn tại khi signed `transitionAfterSeconds>0`, hiển thị trạng thái chuyển bước và tự advance tại equality; transition bằng 0 đi thẳng sang step kế. Callback đến muộn chỉ được consume remaining của current phase: phần quá boundary không làm hụt transition/step chưa render, và phase mới bắt đầu đủ budget từ snapshot hiện tại. Pause/background không cộng active time; transition và `COMPLETION_CTA_WAIT` cũng không cộng `accumulated_active_ms`.

`Bỏ qua bước` chỉ enable trong `STEP_TIMER` khi remaining dương và mỗi step chỉ nhận một skip. Tap sẽ reconcile timer trước, persist ordered `{step_id,active_elapsed_ms}` rồi chạy cùng next-phase reducer; không cộng phần còn lại. Nếu tap đua đúng boundary, timer completion thắng và UI không báo/ghi skip. Trong transition/CTA wait, control bị ẩn hoặc disabled với semantics không khả dụng.

`Cách dễ hơn` là toggle collapsed mặc định cho current step. Mở control dùng exact `EasierVariation.titleKey` làm heading section rồi expand/swap cue, instruction dạng chữ và demo sang exact signed `EasierVariationStep` map với source step; control đổi state/label accessibility thành expanded và luôn cho phép `Quay lại cách ban đầu`. Timer vẫn chạy/paused theo state hiện tại và variation kế thừa nguyên dosage/transition của base step; không restart step, đổi mode/routine/session hoặc thay completion progress. Khi sang step khác, recovery hoặc phiên khác, không suy/persist lựa chọn trước và không phát product event. Content mapping/title/instruction/demo/signature thiếu hoặc sai phải chặn asset thay thế như content-contract error; UI không tự viết regression.

`Replay` nằm trong media demo của current base/easier step. Tap chỉ seek **current signed demo asset đang chọn** về media position 0 rồi play; không phải “chạy lại bước/bài”. Replay không pause/resume exercise timer, không đổi phase/current/transition remaining, active counter, cadence ordinal, skip list, routine/session/status hoặc completion; không persist và không phát event. Media seek/play fail hiển thị lỗi asset cục bộ nhưng checkpoint vẫn nguyên.

Không autoplay âm thanh. Nếu có âm thanh, mặc định tắt và luôn có caption/transcript.

Khi chọn `Dừng bài`, phải hỏi safety gate trước khi rời player:

Tap được serialize cùng timer/Pause/Skip. App reconcile tới exact tap snapshot trước; nếu final-step equality đã đưa player vào `COMPLETION_CTA_WAIT`, Stop trở thành stale, không mở dialog và UI hiện completion CTA. Nếu Stop còn hợp lệ, checkpoint được persist PAUSED + segment null trước khi dialog xuất hiện. Nếu trước đó PLAYING, active counter chỉ cộng đến tap và ghi pause transition; nếu đã PAUSED thì giữ nguyên. Dialog dù mở lâu cũng không chạy timer, transition, cadence hoặc active counter.

**Dialog:** `Trước khi dừng`  
**Câu hỏi:** `Trong phiên vừa rồi, bạn có đau mới hoặc đau tăng lên không?`  
**Lựa chọn:** `Có` / `Không`  
**CTA phụ:** `Tiếp tục bài` — nếu dialog bắt đầu từ PLAYING trong cùng process, resume bằng fresh monotonic anchor; nếu bắt đầu từ PAUSED, chỉ đóng dialog và trở lại Player vẫn paused. Process loss không nhớ prior UI state và recovery luôn dùng persisted PAUSED checkpoint, không tự resume.

Chọn `Có` atomically kết thúc session thành `STOPPED` từ frozen checkpoint, lưu pain=yes + `RESOLVED_HOLD`, tạo hold và route `UX-053`. Chọn `Không` kết thúc session thành `STOPPED` từ cùng frozen counter, persist `RESOLVED_NO` rồi mở feedback bước B; người dùng có thể chọn `Để sau` để về Home. Nếu app/process đóng khi câu hỏi chưa được trả lời, không tự terminalize: session vẫn `ACTIVE` với persisted PAUSED checkpoint và lần mở lại đi qua recovery `UX-051A`. Chỉ readable/schema-valid recovery fail reason mới được atomically chuyển `ABANDONED + PENDING`; corrupt Session/checkpoint giữ guard và route data-error/full-reset, không fabricate terminal state.

Khi reducer vào `COMPLETION_CTA_WAIT` sau step cuối:

**Tiêu đề:** `Bạn đã đi hết bài`  
**CTA chính:** `Hoàn thành` — lưu session completed với pending pain gate rồi mở `SCR-024A`.

### UX-051A — Khôi phục active session

Khi relaunch và recovery state hợp lệ trong cùng origin local date, trước `work_end`, UI khôi phục byte-exact phase, step index, current/transition remaining, accumulated active time, ordered skip records, substate và cadence ordinal; không suy progress từ tổng thời gian hoặc reset về đầu step:

**Tiêu đề:** `Bạn có một phiên chưa kết thúc`  
**Nội dung:** `Bạn có thể tiếp tục từ trạng thái đã lưu hoặc kết thúc phiên. App sẽ không tự đánh dấu hoàn thành.`  
**CTA chính:** `Tiếp tục phiên`  
**CTA phụ:** `Kết thúc phiên` — mở pain question trực tiếp như stop flow.

Active guard không cho mở bài/session khác. Nếu reboot/clock evidence không đủ hoặc đã qua origin date/`work_end`, app không hiển thị Resume: atomically ghi `ABANDONED + PENDING` rồi route `UX-054`. Content unavailable/checksum/identity mismatch chỉ dùng cùng nhánh khi Session đã authenticate/decrypt và checkpoint/cross-invariant pass để freeze/export. Auth/decrypt/schema/phase/counter/catalog-cross-invariant corrupt giữ active guard, zero normal terminal event và render typed data-error + explicit `Xóa toàn bộ dữ liệu`; không fabricate `ABANDONED`/checkpoint.

## 9. Feedback và safety hold

> Copy của pain gate, stop dialog, pain response và pending-pain screen cũng là `PENDING_EXTERNAL_SIGN_OFF` theo `CNT-015`/`CNT-050`. Các route bind exact typed `playerSafety.painAnswerLabels`, `stopDialog`, `terminalPainGate`, `pendingPainGate`, `painResponse`; copy sau expiry dùng typed `nextDayRecheck` tại `UX-034`. Release chỉ render slot được cover bởi root `globalSafetyContentDigestSha256` và `globalSafetySignOff` hợp lệ; nội dung dưới đây chưa tự mang nghĩa được duyệt lâm sàng/pháp lý.

### UX-052 — Feedback theo hai bước

Pain gate chỉ dùng enum domain `PENDING|RESOLVED_NO|RESOLVED_HOLD`; UI không tạo alias `RESOLVED_YES` hoặc trạng thái `STOPPED+PENDING`.

#### Bước A — Pain safety gate bắt buộc cho completed/abandoned

Binding: title/question=`playerSafety.terminalPainGate.titleKey/questionKey`; Có/Không=`playerSafety.painAnswerLabels.yesKey/noKey`.

**Tiêu đề:** `Trước khi tiếp tục`  
**Câu hỏi:** `Bạn có đau mới hoặc đau tăng lên sau phiên vừa rồi không?`  
**Lựa chọn:** `Có` / `Không`.

Không có CTA `Để sau` trên màn hình này. Nếu người dùng đóng app, câu trả lời vẫn pending và mọi start/deep link sau đó route lại đây. `Có` tạo hold rồi route `UX-053`. `Không` persist `RESOLVED_NO` rồi mở bước B. Stop flow thu cùng câu trả lời trong `UX-051` trước khi atomically commit `STOPPED`; không dùng trạng thái `STOPPED+PENDING`.

#### Bước B — Effort/context có thể defer

Hiển thị sau completed/stopped/abandoned session có pain=no. Với mọi terminal state, effort/context có thể defer; `too_hard` vẫn hạ day mode cap. Stopped/abandoned session không qualify north star.

**Tiêu đề:** `Phiên vừa rồi thế nào?`

1. `Mức gắng sức?`
   - `Nhẹ · RPE 1–3`
   - `Vừa · RPE 4–6`
   - `Quá sức · RPE 7–10`
2. `Bài này có phù hợp với bối cảnh làm việc không?`
   - `Có`
   - `Không`

Lần đầu hiển thị cả hai field còn null; khi mở lại chỉ hiển thị field vẫn thiếu. **CTA chính:** `Lưu phản hồi` — enable khi ít nhất một field chưa trả lời có selection; transaction chỉ commit selected field theo transition null→value, field không chọn giữ null. **CTA phụ:** `Để sau` — không commit giá trị tùy chọn mới, kể cả selection UI chưa lưu; terminal/pain state trước đó vẫn giữ. Home chỉ prompt local cho field còn thiếu, không gửi notification bổ sung.

Vì vậy người dùng có thể chỉ lưu `context_fit=yes` và để `effort=null`; completed selected-workday session đó có thể qualify. Nếu chỉ lưu `effort=too_hard`, context vẫn null nên chưa qualify; reducer hạ cap phải commit atomically cùng effort update khi pain=no và origin constraint còn active.

Nếu `effort=too_hard`, pain=no, app hạ day mode cap một bậc và hiển thị:

> Cảm ơn bạn đã phản hồi. Mức cao nhất đến khi giới hạn này hết hiệu lực là {Giữ nhịp/Hồi lại}. Sau đó app sẽ cần một check-in mới.

Nếu đang ở `Hồi lại`, dùng:

> Cảm ơn bạn đã phản hồi. Hồi lại đã là nhịp nhẹ nhất; app sẽ giữ mức này đến khi giới hạn hiện tại hết hiệu lực.

Day mode cap chỉ áp dụng cho luồng mới sau feedback, không sửa session đã kết thúc. Cap kế thừa audit `session_origin_day_expires_at_utc` và effective clock deadline của origin day; timezone/wall clock không được dùng để rút ngắn/bypass.

Nếu effort=too_hard được lưu khi resolver xác nhận đã tại/sau effective origin-day expiry, không tạo cap cho ngày mới; hiển thị:

> Đã lưu phản hồi. Phiên này đã qua khoảng ngày được ghi nhận, nên app không thay đổi mức hôm nay.

### UX-053 — Pain response và safety hold

Nếu `new_or_worse_pain=yes`, sau submit chuyển full-screen:

Binding cố định: title=`playerSafety.painResponse.titleKey`; ba paragraph theo ordered `bodyKeys[reportedPainKey, urgentSymptomsKey, limitationKey]`; CTA Home=`homeActionLabelKey`. Shared `emergencyDial` render riêng ngay trước CTA dial, không cần key lặp trong `painResponse`.

**Tiêu đề:** `Hôm nay hãy dừng vận động`

**Nội dung**

> Bạn cho biết có đau mới hoặc đau tăng lên. App sẽ không đề xuất thêm phiên vận động hôm nay.
>
> Nếu có đau/đè nặng vùng ngực, chóng mặt nhiều hoặc ngất, khó thở bất thường/nghiêm trọng, tim đập bất thường, đau cấp tính hoặc triệu chứng tăng mạnh, hãy tìm trợ giúp khẩn cấp. Nếu không, hãy nghỉ và cân nhắc trao đổi với chuyên gia y tế nếu triệu chứng tiếp tục hoặc khiến bạn lo lắng.
>
> App không thể đánh giá nguyên nhân.

Ngay trước CTA, render shared signed `emergencyDial.instructionTemplateKey` và thay `{emergency_number}` bằng `dialTargetDigits`; instruction/action/unavailable keys được root typed contract + digest/sign-off cover và không phải duplicate vào route slot khác.

**CTA chính:** label từ `emergencyDial.actionLabelKey` (candidate `Mở ứng dụng Điện thoại`) — dispatch `ACTION_DIAL` tới chính `dialTargetDigits` đang hiển thị; không `ACTION_CALL`, tự gọi hoặc xin phone permission. Nếu không có dialer, render signed `unavailableMessageKey`; không hard-code số/CTA khác.  
**CTA phụ:** `Về Hôm nay`.

Cùng thời hạn hold, Home render **nguyên exact signed route** `playerSafety.painResponse`: title, ba `bodyKeys` đúng thứ tự, shared `emergencyDial` instruction/action/unavailable path và Home action label theo binding trên. Không rút gọn, paraphrase, collapse paragraph hoặc hard-code clinical copy; chỉ CTA bài/routine bị cấm. Hold hết theo effective monotonic/clock-integrity contract tại `UX-034`; sau reboot/discontinuity app có thể fail closed lâu hơn. Sau effective expiry, check-in mới vẫn bắt buộc.

### UX-054 — Pending pain gate khi quay lại

Start guard trả `PENDING_SAFETY_FEEDBACK`; UI route trực tiếp `PENDING_PAIN_GATE`. Đây là start-gate route, không phải outcome thứ chín của rule engine.

Binding: title/body/question/entry CTA=`playerSafety.pendingPainGate.titleKey/bodyKey/questionKey/entryActionLabelKey`; Có/Không=`playerSafety.painAnswerLabels.yesKey/noKey`.

**Tiêu đề:** `Cần hoàn tất kiểm tra an toàn`  
**Nội dung:** `Hãy trả lời câu hỏi bắt buộc về việc bạn có đau mới hoặc đau tăng sau phiên trước hay không.`  
**Câu hỏi:** `Bạn có đau mới hoặc đau tăng lên không?`  
**Lựa chọn:** `Có` / `Không`.

`Có` route `UX-053`; `Không` clear pending guard. Không có `Bỏ qua`, không tạo session mới trước khi answer commit thành công.

Pain gate không tự hết hạn. Nếu người dùng trả lời `Có` vào ngày sau, hold được tạo theo local date/zone tại thời điểm trả lời và chặn phần còn lại của answer day. Nếu trả lời `Không` vào ngày sau, gate clear nhưng app vẫn yêu cầu check-in mới; effort=too_hard điền tại/sau origin expiry chỉ được lưu, không hạ mức ngày mới.

## 10. Notification và snooze

### UX-060 — Notification copy

**Title:** `Đến lúc ngắt nhịp`  
**Body:** `Dành 2–5 phút để check-in và chọn một bài phù hợp.`  
**Action:** `Bắt đầu`; cộng từng action `15 phút`, `30 phút`, `60 phút` còn có target preview `< work_end` tại lúc post. Action không đủ preview bị omit, không render disabled.

Tap body notification hoặc action `Bắt đầu` đều chỉ deep-link vào Home; không entry point nào tự tạo session. Cả hai chạy guards theo thứ tự: active safety hold → pending pain → active-session recovery → global-safety re-ack → contract/schedule/window/freshness/outcome/mode. Active hold trả `BLOCKED_FOR_TODAY`; pending pain route `PENDING_PAIN_GATE`; acknowledgement stale route `SCOPE_REACK_REQUIRED`. Schedule mismatch route `RECONFIRM_REQUIRED(schedule_changed)`; ngoài current active work window route `EXPIRED`; trong window, source date khác/TTL/observed clock change/continuity unknown route reconfirm lần lượt với `local_date_changed|ttl|timezone_or_time_change|clock_unknown`. Generic notification vẫn được phép post khi acknowledgement stale, nhưng tap không được bypass re-ack. `reconfirm_after` chỉ là audit/UI display, không thay durable freshness evidence.

Successful tap chỉ mang validated navigation context tới Home. Khi người dùng thực sự bấm Start, app chỉ giữ source reminder nếu occurrence vẫn resolve `DELIVERED`, đã có `first_opened_at` và cùng active/CheckIn/Decision/Session schedule version; stale/forged/khác schedule được normalize im lặng thành Home/null, không hiện lỗi và không chặn bài hợp lệ. Ví dụ reminder A đã mở nhưng user sửa lịch rồi check-in theo B: session B bắt đầu từ Home/null. Khoảng 60 phút chỉ quyết định prompt-to-start metric, không quyết định source/session authorization.

### UX-061 — Snooze actions

Expanded notification render trực tiếp subset action `15 phút` / `30 phút` / `60 phút` mà `delivered_at + duration < work_end` cùng local date. Nếu subset rỗng, notification chỉ còn action `Bắt đầu`; người dùng luôn có thể vuốt bỏ. Không có custom sheet hoặc confirmation copy phụ thuộc background UI.

Preview không authorize action: lúc tap, receiver dùng current clock/zone/schedule/permission/hold/rest/session/pain state để kiểm lại `now + duration < work_end`. Action đã stale hoặc callback trùng bị consume an toàn, không tạo child/event/alarm; Home lần mở sau phản ánh current state. Một snooze hợp lệ cancel notification nguồn và tạo occurrence mới; khi occurrence snooze đó thực sự deliver, nó có action set/identity mới và có thể được snooze tiếp.

Khi target hợp lệ trùng/cách fixed reminder kế tiếp không quá 30 phút, scheduler giữ due sớm hơn; equality giữ snooze. Khi không overlap, cả fixed và snooze vẫn pending. UX không tuyên bố thành công trước transaction commit.

Vuốt bỏ notification không tạo warning, streak loss hoặc reminder bù.

Nếu Android giao receiver trễ hơn due time quá 60 phút, app skip occurrence im lặng và không gửi catch-up. Tại đúng 60 phút chỉ post nếu vẫn trước `work_end` và mọi safety/rest/permission guard hợp lệ. Khi fixed và user-requested snooze trùng giờ, giữ snooze; pair fixed-fixed/snooze-snooze không bao giờ được gộp.

### UX-062 — Permission off

Tại Home chỉ hiển thị một banner không chặn:

> Thông báo đang tắt. Lịch của bạn vẫn được lưu.

CTA `Xem cài đặt` mở màn hình setting nội bộ trước; người dùng chủ động chọn `Mở cài đặt Android`. Navigation này là settings-only branch: không PromptAttempt/prompted/PENDING; same-process return đọc OS và ghi đúng một `source=settings` observation, kể cả không đổi quyền.

## 11. Tổng kết tuần

### UX-070 — Khoảng thời gian

Header: `Tuần này · {dd/MM}–{dd/MM}`. Tuần bắt đầu thứ Hai. Không có so sánh với người khác hoặc mũi tên tăng/giảm hàm ý tốt/xấu.

### UX-071 — Cards số đếm

Hiển thị theo thứ tự:

1. `Ngày có phiên phù hợp` — `{qualified_break_days} ngày`.
2. `Phiên vận động` — `{completed} hoàn thành · {started} bắt đầu`.
3. `Phản hồi` — count Nhẹ/Vừa/Quá sức; pain Có/Không; phù hợp Có/Không.
4. `Lời nhắc` — count `đã mở` / `đã nhắc lại` / `đã vuốt bỏ`; không gộp `SKIPPED_*` vào “đã vuốt bỏ”.

Helper cho card đầu:

> Một ngày được tính khi đó là ngày làm việc bạn đã chọn và có ít nhất một phiên hoàn thành với phản hồi “phù hợp: có” và “đau mới/đau tăng: không”.

### UX-072 — Tỷ lệ

Chỉ hiển thị tỷ lệ khi mẫu số tương ứng từ 5 trở lên:

- `Hoàn thành sau khi bắt đầu: {x}%`
- `Phù hợp bối cảnh: {x}%`
- `Phản hồi đau mới/đau tăng: {x}%`

Nếu mẫu số dưới 5:

> Chưa đủ dữ liệu để tính tỷ lệ (cần ít nhất 5 lần).

Không hiển thị “ngày X thường đi kèm Y”, correlation, dự báo, recommendation tự động hay natural-language summary.

### UX-073 — Empty state

**Tiêu đề:** `Tuần này chưa có phiên vận động hoàn thành`  
**Nội dung:** `Bạn có thể bắt đầu bằng một check-in trong khung làm việc. Nghỉ hoặc bỏ qua không làm mất streak vì app không dùng streak.`  
**CTA:** `Về Hôm nay`.

## 12. Cài đặt và dữ liệu

### UX-080 — Cài đặt

Sections:

- `Lịch làm việc`: toggle `Lời nhắc trong app`, ngày, bắt đầu/kết thúc, 1–2 giờ nhắc.
- `Thông báo`: trạng thái quyền và CTA hệ thống chủ động.
- `Dữ liệu trên thiết bị`: `Export dữ liệu`, `Xóa toàn bộ dữ liệu`.
- `Về sản phẩm`: giới hạn general wellness, phiên bản app/content/rule, `Chính sách quyền riêng tư`.

Ngay trong `Về sản phẩm`, hiển thị text: `Để bảo vệ dữ liệu riêng tư, app chặn ảnh chụp và chia sẻ màn hình khi đang mở.` Không thêm route Help/Support cho dòng này.

Không có Account, Subscription, Connect Health, Cloud Sync, AI hoặc Calendar.

Toggle mặc định bật sau onboarding. Khi tắt, hiển thị helper `App sẽ ngừng các lời nhắc sắp tới. Bạn vẫn có thể tự check-in trong khung làm việc; ngày làm việc đã chọn không thay đổi.` và commit một schedule version mới `enabled=false`. Khi bật lại, helper `App chỉ lập các giờ nhắc còn ở tương lai, không gửi bù.`; nếu quyền Android đang tắt, toggle schedule vẫn bật nhưng banner `UX-062` giải thích notification chưa thể hiển thị. Tắt/bật không xóa giờ đã lưu, lịch sử hoặc qualified-day eligibility. Mọi save/toggle tạo version mới và làm Decision hiện hành cần check-in lại; success đưa Home về state `Lịch làm việc vừa thay đổi`, không tiếp tục bằng recommendation cũ.

### UX-081 — Export

**Tiêu đề:** `Export dữ liệu của bạn`

**Nội dung**

> App sẽ tạo một file JSON UTF-8 gồm profile, lịch làm việc, check-in, quyết định, phiên vận động, phản hồi, lời nhắc, event log và tổng kết tuần. File có thể chứa thông tin tự đánh giá nhạy cảm.
>
> File là plaintext khi nằm ngoài app. App không tải file lên mạng; bạn tự chọn nơi lưu và tự quyết định có chia sẻ hay không.

**CTA chính:** `Chọn nơi lưu`  
**CTA phụ:** `Hủy`.

Success: `Đã export dữ liệu tới vị trí bạn chọn.`  
Failure: `Chưa thể tạo file. Dữ liệu gốc không thay đổi. Nơi lưu có thể còn một file chưa hoàn chỉnh; hãy xóa file đó, chọn vị trí khác và thử lại.`

Không dùng share sheet tự động sau success.

### UX-082 — Xóa toàn bộ

**Bước 1 — Tiêu đề:** `Xóa toàn bộ dữ liệu trên app?`

> Thao tác này xóa lịch, check-in, phiên vận động, phản hồi, event log và hủy các lời nhắc đang chờ. Không thể hoàn tác.
>
> Các file bạn đã export ra ngoài app sẽ không bị xóa.

**CTA chính:** `Tiếp tục xóa`  
**CTA phụ:** `Giữ dữ liệu`.

**Bước 2 — Tiêu đề:** `Xác nhận lần cuối`  
**Checkbox:** `Tôi hiểu dữ liệu trong app sẽ bị xóa vĩnh viễn.`  
**CTA destructive:** `Xóa vĩnh viễn` — chỉ enable sau checkbox.  
**CTA phụ:** `Hủy`.

Success đưa về age gate và không giữ snackbar chứa dữ liệu cũ.

### UX-083 — Chính sách quyền riêng tư

Chọn `Chính sách quyền riêng tư` mở màn hình native chứa toàn văn và số phiên bản đã duyệt từ asset bundled; màn hình phải dùng được ở airplane mode. Không dùng WebView.

**Tiêu đề:** `Chính sách quyền riêng tư`  
**Metadata:** `Phiên bản {policy_version}`  
**CTA phụ:** `Mở bản công khai`.

CTA phụ hiển thị xác nhận ngắn `Bạn sẽ mở trình duyệt ngoài.` rồi dispatch URL đã duyệt bằng external browser intent. Nếu không có ứng dụng xử lý URL: `Không thể mở trình duyệt. Bạn vẫn có thể đọc đầy đủ chính sách tại đây.` Không spinner, network error hoặc nội dung remote xuất hiện trong app. Text/version/digest bundled và public URL phải khớp release artifact; mismatch là release blocker, không phải runtime fallback.

## 13. Lỗi và edge states

### UX-090 — Copy lỗi chuẩn

| Tình huống | Copy | Hành động |
|---|---|---|
| Nội dung bài thiếu/hỏng | `Bài này chưa thể mở trên thiết bị. Hãy chọn một bài khác cùng nhịp hoặc nhẹ hơn.` | `Chọn bài khác` |
| Không thể lưu check-in | `Chưa lưu được check-in. Câu trả lời chưa được dùng để chọn bài.` | `Thử lại` |
| Không thể lưu pending pain của completed/abandoned | `Chưa lưu được câu trả lời an toàn. Bạn chưa thể bắt đầu phiên khác.` | `Thử lại` / `Thoát app`; gate vẫn pending |
| Không thể commit stop + pain answer | `Chưa lưu được việc dừng phiên. Phiên này vẫn chưa kết thúc.` | `Thử lại` / `Tiếp tục bài` / `Thoát app`; session vẫn `ACTIVE`, relaunch đi recovery và không tạo `STOPPED+PENDING` |
| Không thể lưu effort/context | `Chưa lưu được phản hồi tùy chọn. Phiên vận động vẫn được giữ.` | `Thử lại` / `Để sau` |
| Không thể lập notification | `Chưa thể lập lời nhắc. Bạn vẫn có thể tự mở app.` | `Xem cài đặt` |
| Check-in hết hạn khi đang chọn bài | `Check-in đã hết hiệu lực. Xác nhận lại trước khi bắt đầu.` | `Xác nhận lại` |
| Check-in thuộc local date trước nhưng hiện đang trong work window | `Câu trả lời trước thuộc một ngày khác. Hãy check-in lại.` | `Check-in lại` |
| Timezone/system time đổi | `Giờ trên thiết bị đã thay đổi. Các lần nhắc tới sẽ theo giờ địa phương mới; hãy xác nhận lại check-in trước khi mở bài.` | `Xác nhận lại` nếu có decision active, nếu không `Đã hiểu` |
| Schedule đã đổi sau check-in | `Lịch làm việc đã thay đổi. Hãy check-in lại để dùng khung giờ hiện tại.` | `Check-in lại` |
| Acknowledgement data/digest không xác thực | `Chưa thể xác minh nội dung an toàn đã chấp nhận. App sẽ không mở check-in hoặc bài.` | `Thử lại` / `Quản lý dữ liệu` |
| Storage picker bị hủy | Không hiển thị lỗi; trở về Cài đặt | Không có |
| Export thiếu chỗ trống | `Không đủ dung lượng tại vị trí đã chọn. Dữ liệu gốc không thay đổi.` | `Chọn vị trí khác` |

Không bao giờ fallback sang routine khi rule/safety state chưa đọc được. Trong lỗi dữ liệu an toàn, app fail closed: không cho bắt đầu và cung cấp export/xóa trong Cài đặt.

## 14. Accessibility và interaction

### UX-100 — Semantics

- Radio/chip phải đọc cả câu hỏi, lựa chọn và trạng thái selected bằng TalkBack.
- Mode được đọc `Nhịp đề xuất: Hồi lại/Giữ nhịp/Tăng nhịp`, không chỉ đọc màu/icon.
- Sáu approved key trong `Routine.accessibility` bind 1:1, không alias/fallback: `screenReaderTitleKey` → semantic pane title/level-1 heading trên cả pre-flight và Player; `routineOverviewKey` → accessible overview ngay sau title trên pre-flight, trong khi visible overview vẫn dùng `Routine.summaryKey`; `postureAndSetupKey` → semantic heading/description ngay trước per-routine safety sequence; `stopButtonLabelKey` → accessibility label của `Dừng bài` chỉ trong `STEP_TIMER|STEP_TRANSITION`; `pauseButtonLabelKey` → accessible name của action Pause chỉ khi cùng phase và `PLAYING`; khi cùng phase và `PAUSED`, Resume dùng fixed app resource `player_resume_action` (`Tiếp tục`) và không reuse signed Pause key; `skipButtonLabelKey` → accessibility label chỉ cho `Bỏ qua bước` ở `STEP_TIMER` remaining dương. Stop/Pause/Resume/Skip đều absent khỏi accessibility tree tại `COMPLETION_CTA_WAIT`.
- Visual card/pre-flight/player title vẫn lấy `Routine.titleKey`, card/overview lấy `Routine.summaryKey`; không dùng hai key này thay `screenReaderTitleKey`/`routineOverviewKey`.
- Khi một step bắt đầu, TalkBack announce exact signed `RoutineStep.screenReaderInstructionKey` của step đó rồi timer state hiện hành; không dùng visual `instructionKey`, free-text concat hoặc một progress-key pool.
- Timer formatter code-native v1 dùng duy nhất: remaining `0` map phòng vệ thành `Còn 0 giây`; `minutes=0` → `Còn {seconds} giây`; `seconds=0` → `Còn {minutes} phút`; còn lại → `Còn {minutes} phút {seconds} giây`. Integer decimal không leading zero; lấy đúng `remainingSeconds` đang render từ canonical player timer, không tính timer thứ hai từ wall/media/event. Reducer chuyển phase atomically tại zero nên accessibility adapter **không dispatch zero announcement**.
- Announcement timer chỉ xảy ra khi step bắt đầu và tại cadence accumulated `PLAYING`: ordinal `k>=1` đến hạn ở `k*30_000 ms`, mỗi ordinal tối đa một lần qua `lastAnnouncedCadenceOrdinal`. Pause/background/step transition/CTA wait không tiến cadence; resume/recovery không replay. Late tick vượt nhiều ordinal chỉ đọc một current-timer announcement rồi claim ordinal cao nhất. Cadence trùng step-start thành một combined step+timer announcement; step equality chuyển phase mà không đọc zero. Live region `polite`, không cướp focus và không announce mỗi tick.
- Mọi hình/video có transcript; decorative asset bị loại khỏi accessibility tree.
- `Cách dễ hơn` expose role button/toggle, current step, state `thu gọn/đang mở` và focus vào heading instruction thay thế khi mở; `Quay lại cách ban đầu` đưa focus về control. Demo/instruction base và easier đều có transcript/alt text đã ký.
- Error focus chuyển tới heading lỗi đầu tiên; safety heading nhận focus khi route tới màn hình chặn.

### UX-101 — Layout

- Touch target tối thiểu 48×48 dp.
- Font scale 200% vẫn xem được toàn bộ safety copy và CTA qua scroll; CTA không che nội dung.
- Contrast text/control đạt WCAG AA tương ứng; mode không phân biệt chỉ bằng màu.
- Tôn trọng reduced motion; không dùng animation rung/lắc ở safety screen.
- Landscape và màn hình nhỏ không làm mất `Dừng bài`.

### UX-102 — Haptic/audio

Haptic và âm thanh là tùy chọn bổ trợ, không phải tín hiệu duy nhất. Không dùng âm thanh báo động cho `URGENT_STOP`; ưu tiên copy rõ và focus đúng để tránh hoảng loạn.

## 15. Ma trận UX acceptance

| ID | Given | When | Then |
|---|---|---|---|
| `UX-AC-01` | Người dùng chưa đủ 18 | Chọn câu trả lời tương ứng | Chỉ thấy safe-exit; không thể đi tiếp bằng Back/deep link. |
| `UX-AC-01A` | Người dùng đủ 18 nhưng chọn ngoài intended use hoặc không chắc | Trả lời eligibility | Chỉ thấy safe-exit; không lịch/check-in/routine; lựa chọn/lý do không được persist. |
| `UX-AC-02` | Check-in mới | Mở form | Không câu trạng thái nào preselected; red flag được hỏi trước. |
| `UX-AC-03` | Red flag=yes | Chọn câu trả lời | Dừng ngay, tạo hold `RED_FLAG`; mở lại render urgent copy, không hỏi lại để bypass. |
| `UX-AC-04` | Durable elapsed evidence đã chạm đúng TTL 6 giờ | Mở recommendation | Freshness resolver non-`FRESH`; toàn bộ field được prefill nhưng cần submit xác nhận chủ động. |
| `UX-AC-05` | Outcome `REST_ONLY` | Render kết quả | Không mode/bài; reminder còn lại bị skip. Check-in mới có mode chỉ reschedule slot tương lai; Rest/safety vẫn không nhắc. |
| `UX-AC-06` | Outcome `MAINTAIN` | Mở selector | Chỉ có Giữ nhịp và Hồi lại; không thấy Tăng nhịp. |
| `UX-AC-06A` | Draft input thiếu/sai theo precedence | Form validation/runtime `INCOMPLETE` | Không persist CheckIn/Decision hoặc mở bài; focus field đầu tiên cần sửa; acute non-none hợp lệ vẫn ưu tiên Pause. |
| `UX-AC-06B` | Safety hold đang active | Mở app/deep link | Domain trả `BLOCKED_FOR_TODAY`; copy khớp exact hold kind, không mặc định post-session pain. |
| `UX-AC-06C` | `invalid_fields` chứa `day_mode_cap` | Render `INCOMPLETE` | Hiện fail-closed data state với Retry/Quản lý dữ liệu; không focus field giả, clear/default cap hoặc mở bài. |
| `UX-AC-06D` | Hold kind/source không xác thực được | Mở app/deep link | Start gate `CONTRACT_ERROR`; operational data-state, không generic pain/acute copy, routine hoặc Decision giả. |
| `UX-AC-06E` | Daily-constraint envelope/tag/key/bundle auth/decode lỗi | Mở check-in/start | `CONTRACT_ERROR` trước engine; không persist `INCOMPLETE`, bỏ qua hold hoặc mở bài. |
| `UX-AC-07` | Routine cần điểm tựa | Chọn không có điểm tựa chắc | Không thể Start; trở về chọn bài hợp lệ. |
| `UX-AC-08` | Terminal session pain=yes | Lưu | Full-screen stop guidance xuất hiện, hold persist trước navigation và mọi routine bị chặn đến expiry. |
| `UX-AC-08A` | Tăng nhịp vừa nhận feedback Quá sức, pain=no | Mở luồng mới trước cap expiry | Mode tối đa là Giữ nhịp, có giải thích; sau expiry cần check-in mới. |
| `UX-AC-08B` | Completed/abandoned nhưng pain pending | Mở Home/deep link/notification | Route `UX-054`; không session mới trước khi answer commit. |
| `UX-AC-08C` | Pain=no, effort/context deferred | Mở phiên mới | Safety guard không chặn; phiên cũ chưa qualify `qualified_break_days`. |
| `UX-AC-08D` | Completed trên selected workday, pain=no, context=yes, effort còn null | Tính summary | Ngày có thể qualify; effort không thuộc north-star predicate. |
| `UX-AC-08E` | Stop dialog chưa trả lời rồi process đóng | Relaunch | Session vẫn `ACTIVE`; recovery hợp lệ chỉ cho Resume/End, invalid mới commit `ABANDONED+PENDING`; không có `STOPPED+PENDING`. |
| `UX-AC-09` | Snooze target tại post đúng/vượt `work_end`, hoặc action preview sau đó thành stale | Render/tap notification | Action không đủ preview bị omit; action đã render nhưng không còn eligible lúc tap trả zero child/event/alarm và source notification được cleanup. |
| `UX-AC-10` | Tỷ lệ có mẫu số 4 | Mở tuần | Không hiển thị phần trăm; hiển thị copy cần ít nhất 5 lần. |
| `UX-AC-11` | Notification bị từ chối | Dùng app | Không modal lặp; Home vẫn dùng được và chỉ có banner không chặn. |
| `UX-AC-12` | TalkBack + font 200% | Hoàn tất core flow | Tất cả câu hỏi, safety copy, timer và CTA có thể đọc/điều khiển, không bị che. |
| `UX-AC-13` | Airplane mode từ lần cài đầu | Chạy mọi screen trong phạm vi | Không xuất hiện spinner/network error/đăng nhập. |
| `UX-AC-14` | Người dùng chọn delete | Chưa xác nhận bước 2 | Chưa xóa; chỉ xóa sau checkbox và CTA cuối. |
| `UX-AC-15` | Hold/cap active rồi timezone/wall clock đổi | Resolver đánh giá clock evidence | Same-boot monotonic equality kết thúc state; discontinuity có thể extend bảo thủ, không clear sớm/đổi reason; sau effective expiry yêu cầu check-in mới. |
| `UX-AC-16` | Thiết bị chưa từng có mạng | Mở `Chính sách quyền riêng tư` | Toàn văn/version đã duyệt hiển thị từ asset bundled; CTA public chỉ mở trình duyệt ngoài và không dùng WebView. |
| `UX-AC-17` | `Lời nhắc trong app` đang bật với slot tương lai | Tắt rồi bật lại | Tắt tạo version disabled/cancel slot; bật tạo version enabled và chỉ schedule slot còn tương lai, không post bù hoặc đổi selected-workday qualification. |
| `UX-AC-18` | Notification đã post | Tap body hoặc action `Bắt đầu` | Chỉ mở Home và chạy đủ guard; chưa tạo session cho tới khi user đi qua flow/start authorization. |
| `UX-AC-19` | Schedule version B được lưu sau Decision của version A | Mở Home/start | Route `Check-in lại`/reason `schedule_changed`; không tạo session trộn A và B. |
| `UX-AC-20` | Bundled approved global safety version/digest khác current acknowledgement | Mở check-in/start hoặc tap notification | Sau hold/pain/recovery guard, route `SCOPE_REACK_REQUIRED`; re-ack append history nhưng không đổi activation anchor. |
| `UX-AC-21` | Một routine có context REQUIRED/NOT_REQUIRED hợp lệ | Mở pre-flight | Chỉ REQUIRED hiện đúng fixed order; bất kỳ `Không` mở selector, không persist/infer/auto-fallback; mọi `Có` mới enable Start. |
| `UX-AC-22` | `URGENT_STOP` có signed EmergencyDialContract | Render/dial | Số hiển thị và ACTION_DIAL dùng cùng `dialTargetDigits`; không ACTION_CALL/permission; no-dialer dùng approved unavailable key. |
| `UX-AC-23` | Completed selected-workday, pain=no, cả effort/context còn null | Chỉ chọn context=yes rồi lưu | CTA enable; chỉ context null→yes, effort vẫn null, ngày có thể qualify và không cap. |
| `UX-AC-24` | Current step có signed `EasierVariationStep` | Mở/đóng `Cách dễ hơn` bằng touch hoặc TalkBack | Chỉ instruction/demo hiện tại đổi đúng mapping; timer/dosage/transition, mode/routine/session/progress bất biến; state không persist/infer/event và accessibility báo expanded/collapsed. |
| `UX-AC-25` | Start entry ở ngoài window; hoặc trong window với source date/TTL/clock change/clock unknown | Authorize | Ngoài window render `EXPIRED`; bốn nhánh trong window route reconfirm đúng `local_date_changed\|ttl\|timezone_or_time_change\|clock_unknown`, không tạo session. |
| `UX-AC-26` | Reminder A đã tap nhưng occurrence invalid/non-delivered/chưa có first-open hoặc schedule đã thành B | Bấm Start sau flow hợp lệ | Không attribution error/block; session normalize Home/null. Chỉ validated opened DELIVERED occurrence cùng schedule giữ reminder ID; 60 phút chỉ dùng metric. |
| `UX-AC-27` | Pre-flight routine A có một Có rồi một Không; user chọn routine B cùng/nhẹ hơn | Mở chuẩn bị cho B | Confirmation của A bị bỏ; B bắt đầu từ REQUIRED prompt đầu tiên theo fixed order, không reuse/infer answer, và Start chỉ enable sau mọi REQUIRED của B là Có. |
| `UX-AC-28` | Chưa có attempt; hoặc PENDING bị process recreate/late callback | Bấm CTA/khởi động lại | CTA commit attempt trước launcher và disabled khi PENDING; new process mark INTERRUPTED không result, không auto-prompt; explicit retry tạo ID mới, late callback cũ bị bỏ; callback false dùng copy chung, không nói Deny/Dismiss. |
| `UX-AC-29` | Mở bất kỳ màn hình rồi vào `Về sản phẩm` | Thử capture/đọc disclosure | Screenshot/screen share bị chặn và exact privacy copy hiển thị tại About; không có Help/Support route mới. |
| `UX-AC-30` | Adapter cho biết runtime dialog không launchable và cần Settings | Bấm CTA rồi quay lại cùng process mà quyền có thể đổi hoặc không đổi | Mở Settings trực tiếp; không attempt/prompted/PENDING; query runtime và ghi một `source=settings` observation; không auto-open lại hoặc tính vào initial prompt. |
| `UX-AC-31` | Player qua cadence 30 giây, pause/background/recovery và step boundary trùng cadence | Dùng TalkBack | Announce signed step instruction + canonical timer chỉ ở step start/cadence; mỗi ordinal tối đa một lần, non-PLAYING không tiến, recovery không replay, boundary chỉ một combined announcement, không zero announcement và focus không nhảy. |
| `UX-AC-32` | Repetitions/duration step, transition, late callback, skip race hoặc relaunch hợp lệ | Tương tác player | UI tự advance theo signed seconds/estimatedSeconds, không carry lateness qua phase; skip chỉ trước equality và persist active-elapsed một lần; relaunch resume exact phase/remaining/counter/skips, không reset/fabricate progress. |
| `UX-AC-33` | Approved per-routine safety/context contract | Mở pre-flight, switch routine/process recreate rồi Start | Render exact comfortable/setup/conditional-contraindication/stop/escalation sequence; acknowledgement nằm sau block và bị clear khi switch/loss; Start chỉ enable khi ack checked + mọi REQUIRED Có. |
| `UX-AC-34` | Current base/easier signed demo đang phát hoặc player ở bất kỳ phase/substate nào | Tap `Replay` | Chỉ current media seek 0/play; timer/checkpoint/counter/cadence/skips/session/event bất biến và không bị hiểu là replay step/routine. |
| `UX-AC-35` | Approved title/summary/easier/accessibility keys | Render card/pre-flight/player/easier bằng TalkBack qua ba player phase | Key bind đúng exact surface/element; Stop + Pause/Resume chỉ ở timer/transition, Skip chỉ ở timer remaining dương và cả ba absent tại CTA wait. Signed Pause key chỉ ở `PLAYING`, `PAUSED` dùng `player_resume_action`; missing/wrong key fail content path, không fallback/alias/reuse. |
| `UX-AC-36` | Approved `acuteIssueGate` có exact four-element option tuple | Render/submit `acute_issue` | Question/labels resolve đúng signed slots và values giữ byte-exact order `none`, `acute_illness`, `new_or_worsening_pain_or_injury`, `medically_restricted`; UI không hard-code/sort/suy enum từ text, non-none short-circuit đúng PAUSE. |

## 16. Checklist handoff design/content

- Mọi string có key ổn định; không hard-code logic dựa trên text hiển thị.
- Designer bàn giao normal, pressed, focus, disabled, error và TalkBack order cho mọi input/CTA.
- Safety screen không có illustration vui nhộn, gamification hoặc CTA cạnh tranh.
- Asset bài khớp `REC-01`, `REC-02`, `MAI-01`, `MAI-02`, `BUI-01`, `BUI-02`, duration và transcript trong PRD/content specification.
- Content reviewer ký duyệt cue động tác, regression, stop rule và copy store trước release.
- Screenshot store chỉ dùng luồng general wellness; không có medical claim, score, AI, wearable hoặc paywall.
