# Nhịp 2 Phút — Product Brief

> Trợ lý tạo những khoảng nghỉ vận động ngắn, kín đáo và dễ bắt đầu trong ngày làm việc.

- **Trạng thái:** Implementation baseline v1.0
- **Cập nhật:** 2026-08-27
- **Thị trường/ngôn ngữ MVP:** Việt Nam, `vi-VN`
- **Nền tảng MVP:** Android-first
- **Định vị:** General wellness; không phải thiết bị y tế
- **Thời lượng MVP:** 6–8 tuần, theo giả định nguồn lực tại §16

## 1. Vai trò của tài liệu

Tài liệu này khóa mục tiêu, phạm vi và các quyết định sản phẩm của MVP. Đặc tả chi tiết nằm trong [`docs/`](docs/README.md).

Khi có xung đột, thứ tự ưu tiên là:

1. safety và release gate;
2. quyền riêng tư và bảo mật;
3. product requirements;
4. UX/copy;
5. kiến trúc triển khai.

Mọi thay đổi hành vi của rule engine, safety flow, data collection hoặc claim phải tăng version tài liệu liên quan và được review lại trước khi phát hành.

## 2. Tóm tắt sản phẩm

Nhịp 2 Phút giúp người làm việc máy tính hoàn thành một **phiên vận động 2–5 phút** dựa trên check-in tự khai báo và bối cảnh làm việc.

> **Check-in ngắn, nhận một gợi ý có lý do, vận động 2–5 phút rồi quay lại làm việc.**

MVP ghi nhận các phiên vận động đã hoàn thành; app **không tuyên bố đo được thời gian ngồi liên tục** và không suy diễn rằng một phiên đã chắc chắn “phá vỡ sedentary bout”.

### Lời hứa sản phẩm

- Không cần tài khoản, wearable hoặc kết nối mạng.
- Không chấm điểm cơ thể và không tạo recovery score 0–100.
- Không yêu cầu thay đồ, nằm sàn, tạo tiếng động lớn hoặc dùng dụng cụ.
- Nghỉ, bỏ qua, snooze hoặc tắt lời nhắc không bị phạt.
- Luôn nói rõ input nào dẫn đến gợi ý.
- Người dùng luôn có thể chọn bài cùng mức hoặc nhẹ hơn; luồng sử dụng không có CTA ghi đè safety hold đang hiệu lực.

## 3. Vấn đề và giả thuyết

### Vấn đề

Người làm việc máy tính thường biết mình nên vận động nhưng vẫn bỏ qua lời nhắc vì:

- nội dung lặp lại hoặc không phù hợp thời điểm;
- phải tự chọn bài giữa một thư viện lớn;
- bài quá dài, gây mồ hôi, tiếng động hoặc cần không gian;
- ngại một hệ thống chấm điểm làm tăng cảm giác tội lỗi;
- trạng thái hiện tại không phù hợp với cường độ được đề xuất.

### Giả thuyết sản phẩm

Một check-in chủ quan ngắn cộng với thư viện đóng và lời nhắc do người dùng tự đặt có thể tạo nhiều **qualified movement-break days** hơn một thư viện bài tập không có hướng dẫn theo bối cảnh. Pilot single-arm của MVP chưa kiểm định so sánh này; đó là giả thuyết cho thử nghiệm đối chứng sau feasibility.

MVP chỉ kiểm chứng tính khả dụng, mức phù hợp và hành vi sử dụng. MVP không được dùng để kết luận hiệu quả sức khỏe, chẩn đoán, điều trị hoặc mức độ an toàn cho một quần thể lâm sàng.

## 4. Người dùng mục tiêu

### Persona chính

- Người từ 18 tuổi; nhóm nghiên cứu ban đầu ưu tiên 25–42 tuổi.
- Làm việc máy tính khoảng 7–10 giờ/ngày tại văn phòng, hybrid hoặc remote.
- Vận động không đều và thường bỏ qua reminder chung chung.
- Muốn một hướng dẫn ngắn, kín đáo, không cần dụng cụ và ít phải quyết định.
- Có thể tự thực hiện hoạt động general-wellness nhẹ đến vừa mà không cần hướng dẫn lâm sàng cá nhân.

### Job-to-be-done

> “Khi tôi cứng/mỏi hoặc trì trệ vì làm việc lâu, hãy chọn giúp một phiên ngắn phù hợp với check-in hiện tại và bối cảnh, để tôi có thể bắt đầu ngay.”

### Ngoài intended use của MVP

- Người dưới 18 tuổi.
- Người đang có triệu chứng cảnh báo, bệnh cấp tính, chấn thương mới hoặc đau mới/tăng lên.
- Người đã được chuyên gia y tế yêu cầu hạn chế vận động.
- Người cần hướng dẫn cá nhân cho rehab, hậu phẫu, thai kỳ/hậu sản hoặc bệnh mạn.
- Người cần tối ưu thành tích thể thao hoặc chương trình tập luyện đầy đủ.

App không hỏi chẩn đoán cụ thể. Onboarding chỉ hỏi eligibility tối thiểu và cung cấp safe-exit khi người dùng nằm ngoài intended use.

## 5. Nguyên tắc sản phẩm

1. **Subjective-first:** check-in hiện tại quan trọng hơn dữ liệu suy diễn.
2. **Deterministic:** rule engine thuần xác định chế độ; cùng input/version phải cho cùng output.
3. **Safety precedence:** hard stop luôn ưu tiên hơn mọi gợi ý và lịch sử.
4. **Transparent:** phần “Vì sao” chỉ diễn đạt các reason code có thật.
5. **No false precision:** không score, không phần trăm phục hồi, không confidence giả.
6. **No diagnosis:** không phát hiện stress, thiếu ngủ, bệnh, chấn thương hoặc mất cân bằng sinh lý.
7. **Closed content:** chỉ dùng routine đã có manifest, asset và sign-off hợp lệ.
8. **Local-only MVP:** không tài khoản, cloud, SDK quảng cáo hoặc telemetry từ xa.
9. **No shame:** không streak trừng phạt và không tự giảm lời nhắc một cách âm thầm.
10. **Accessible by default:** chức năng cốt lõi không phụ thuộc duy nhất vào hình ảnh, âm thanh, màu sắc hoặc cử chỉ chính xác.

## 6. Phạm vi MVP

### Trong phạm vi

- Android app `vi-VN`, hoạt động offline.
- Không tài khoản và không khai báo quyền `INTERNET`.
- Age/eligibility gate và safety onboarding.
- Check-in thủ công với enum đóng.
- Rule engine versioned với tám outcome đóng; chỉ `RECOVER`, `MAINTAIN` và `BUILD` có thể dẫn tới routine.
- Sáu routine 2–5 phút, asset được bundle trong app.
- Đúng một easier variation cho mỗi routine; mọi step tham chiếu variation đó theo content contract.
- 1–2 giờ nhắc cố định trong các ngày/giờ làm việc do người dùng chọn.
- Snooze thủ công 15/30/60 phút nếu vẫn nằm trong giờ làm việc.
- Routine player có pause, tiếp tục, bỏ qua động tác và dừng phiên.
- Feedback sau phiên: gắng sức, đau/khó chịu mới hoặc tăng lên, phù hợp bối cảnh.
- Tổng kết tuần chỉ dùng thống kê mô tả.
- Export và xóa toàn bộ dữ liệu local, miễn phí.
- Hỗ trợ TalkBack, font scaling, caption/text instruction, contrast và reduced motion.

### Ngoài phạm vi

- iOS, HealthKit, Health Connect hoặc dữ liệu wearable.
- AI/generative text, cloud sync hoặc remote analytics.
- Account, social, leaderboard, streak hoặc employer dashboard.
- Paywall, subscription, in-app purchase hoặc giới hạn export theo tier.
- Calendar access, activity recognition, location hoặc tự phát hiện đang lái xe.
- Adaptive reminder tự động, correlation/pattern mining hoặc causal insight.
- Rehab, thai kỳ/hậu sản, quản lý bệnh mạn hoặc lời khuyên dùng thuốc.
- Claim chẩn đoán, điều trị, phòng ngừa bệnh hoặc đo hiệu quả sức khỏe.

## 7. Vòng lặp cốt lõi

### 7.1. First run

1. Giới thiệu intended use và disclaimer.
2. Xác nhận từ 18 tuổi.
3. Eligibility/safety self-attestation tối thiểu; câu trả lời đủ điều kiện chỉ được staging trong RAM.
4. Chọn ngày làm việc, giờ bắt đầu/kết thúc và 1–2 giờ nhắc; lần `Lưu lịch` đầu tiên atomically tạo profile, acknowledgement, lịch và hoàn tất onboarding.
5. Chỉ sau commit trên mới hiển thị primer và, khi người dùng chủ động chọn, xin quyền notification.
6. Vào Home; không có health, calendar, motion hoặc location permission.

### 7.2. Daily flow

1. Người dùng mở check-in từ Home hoặc notification.
2. App thu các trường bắt buộc tại §8.
3. Rule engine trả về trạng thái, chế độ tối đa và reason code.
4. UI hiển thị “Nhịp hôm nay”, lý do, thời lượng và bối cảnh.
5. Người dùng bắt đầu bài đề xuất, chọn bài cùng/nhẹ hơn hoặc bỏ qua.
6. Với `COMPLETED|ABANDONED`, app atomically tạo pain gate `PENDING`; với stop chủ động, session vẫn `ACTIVE` trong lúc hỏi và chỉ câu trả lời mới atomically tạo `STOPPED + RESOLVED_NO|RESOLVED_HOLD`. Không được bắt đầu phiên khác khi pain gate/recovery chưa giải quyết; không tồn tại `STOPPED+PENDING`.
7. Gắng sức và phù hợp bối cảnh có thể trả lời sau. Nếu người dùng báo đau/khó chịu mới hoặc tăng lên, app dừng flow và tạo safety hold có lý do đến hết ngày địa phương của **thời điểm trả lời**; trả lời muộn không hồi tố hold về ngày session.

### 7.3. Mục tiêu tốc độ

- **Check-in:** median ≤20 giây và P90 ≤30 giây trong usability test.
- **Time-to-routine:** P90 ≤45 giây tính từ lúc repeat user nhấn “Check-in” đến lúc routine bắt đầu.
- Không tính onboarding, OS permission dialog hoặc thời gian tải app vào hai phép đo này.

Check-in chỉ có hiệu lực trong interval nửa mở từ lúc xác nhận đến mốc sớm hơn giữa **6 elapsed hours** và `work_end`. Durable monotonic/clock-integrity evidence là authority cho biên 6 giờ; `confirmed_at + 6h` chỉ là wall audit/UI value. Tại equality hoặc khi continuity không xác minh được, người dùng phải xác nhận lại trước khi bắt đầu routine mới.

## 8. Input và quyết định cốt lõi

### Input bắt buộc

| Field | Giá trị |
|---|---|
| `red_flag` | `true`, `false` |
| `acute_issue` | `none`, `acute_illness`, `new_or_worsening_pain_or_injury`, `medically_restricted` |
| `energy` | `low`, `okay`, `good` |
| `stiffness` | `none`, `mild`, `notable` |
| `intent` | `rest`, `gentle`, `moderate` |

### Thứ tự quyết định

Rule engine áp dụng **first match wins**:

| Ưu tiên | Điều kiện | Kết quả |
|---:|---|---|
| 0 | Safety hold còn hiệu lực | `BLOCKED_FOR_TODAY` |
| 1 | `red_flag = true` | `URGENT_STOP` |
| 2 | `red_flag` thiếu/sai | `INCOMPLETE` |
| 3 | `acute_issue` hợp lệ và khác `none` | `PAUSE_TODAY` |
| 4 | `acute_issue` thiếu/sai | `INCOMPLETE` |
| 5 | `energy`, `stiffness` hoặc `intent` thiếu/sai; day-cap bị corrupt | `INCOMPLETE` |
| 6 | `intent = rest` | `REST_ONLY` |
| 7 | `energy = low` hoặc `stiffness = notable` | `RECOVER` |
| 8 | `energy = good`, `stiffness ∈ {none, mild}` và `intent = moderate` | `BUILD` |
| 9 | Các trường hợp còn lại | `MAINTAIN` |

`URGENT_STOP`, `PAUSE_TODAY`, `BLOCKED_FOR_TODAY` và `INCOMPLETE` không được trả về routine. Chi tiết schema, reason code và test fixture nằm trong [`docs/03-safety-rule-engine.md`](docs/03-safety-rule-engine.md).

### Chế độ UI

| Engine output | Nhãn UI | RPE mục tiêu | Hành vi |
|---|---|---:|---|
| `REST_ONLY` | Hồi lại — nghỉ hôm nay | — | Không đề xuất routine |
| `RECOVER` | Hồi lại | 1–2 | Chỉ routine Recover |
| `MAINTAIN` | Giữ nhịp | 2–4 | Routine Maintain hoặc Recover |
| `BUILD` | Tăng nhịp | 4–6 | Routine Build, Maintain hoặc Recover |

Người dùng không được tự nâng cao hơn output hiện tại. App không gọi output này là đánh giá sức khỏe hay mức phục hồi.

## 9. Thư viện MVP

| ID | Tên làm việc | Thời lượng | Mode cao nhất |
|---|---|---:|---|
| `REC-01` | Thả lỏng tại ghế | 2 phút | Recover |
| `REC-02` | Đi bộ chậm | 3 phút | Recover |
| `MAI-01` | Reset bàn làm việc | 2 phút | Maintain |
| `MAI-02` | Mobility đứng | 4 phút | Maintain |
| `BUI-01` | Sức mạnh với ghế | 4 phút | Build |
| `BUI-02` | Cardio yên lặng | 5 phút | Build |

Tên, động tác, contraindication, stop rule và asset cuối cùng chỉ được phát hành khi có sign-off theo [`docs/04-content-contract.md`](docs/04-content-contract.md). Danh sách trên là contract cho phần mềm, không thay thế review chuyên môn.

## 10. Safety boundary

- App không bảo đảm một bài phù hợp cho mọi cá nhân.
- Red flag hoặc acute issue không bao giờ bị wearable, lịch sử, ý muốn vận động hay user override ghi đè.
- `red_flag=true`, từng loại acute issue và đau/khó chịu mới hoặc tăng lên sau phiên đều tạo safety hold có `kind` riêng; check-in lại trong cùng ngày không được né hold.
- Quyền **Xóa toàn bộ** vẫn xóa hold cùng mọi dữ liệu theo yêu cầu privacy và buộc onboarding lại từ đầu; sản phẩm không tuyên bố chống reset/tamper tuyệt đối.
- Mọi phiên `COMPLETED`, `STOPPED` hoặc `ABANDONED` phải có pain gate đã giải quyết trước phiên kế tiếp. `COMPLETED|ABANDONED` có thể giữ `PENDING`; stop dialog chưa trả lời vẫn là session `ACTIVE`. Process death, notification hoặc deep link không được đi vòng qua guard tương ứng.
- Khi người dùng báo đau/khó chịu mới hoặc tăng lên trong/sau phiên, app phải dừng, không gợi ý bài nhẹ hơn trong cùng phiên và khóa routine đến hết ngày địa phương của thời điểm trả lời.
- Khi tạo hold, app chụp full LocalStamp (`occurred_at_utc`, `local_date`, `zone_id`, `utc_offset_minutes`), immutable `expires_at_utc` tại đầu ngày local kế tiếp và clock evidence. Đổi timezone hoặc chỉnh clock không được làm effective deadline ngắn hơn; sau khi hold được xác minh inactive phải check-in mới.
- Urgent copy yêu cầu người dùng dừng và tìm trợ giúp y tế khẩn cấp; số điện thoại cấp cứu là localization/configuration được pháp lý/chuyên môn phê duyệt trước release.
- Non-urgent copy không chẩn đoán; chỉ yêu cầu tạm nghỉ và cân nhắc hỏi chuyên gia nếu triệu chứng kéo dài hoặc đáng lo.
- Mọi routine và thay đổi content phải được chuyên gia đủ năng lực ký duyệt, có version/date và re-review khi asset/instruction thay đổi.

## 11. Lời nhắc

- Người dùng chọn 1–7 ngày làm việc, một khoảng giờ làm việc nửa mở `[work_start, work_end)` và 1–2 fixed reminder slot nằm trong khoảng đó. Pilot chỉ tuyển người có ít nhất ba ngày làm việc/tuần.
- Ngày không được chọn không có notification, nhưng người dùng vẫn có thể check-in thủ công trong work window. Session đó lưu snapshot `is_selected_workday_at_start=false` và không tính vào qualified movement-break days.
- Tối đa hai fixed reminder slot mỗi ngày làm việc. Mỗi notification đã deliver chỉ nhận một snooze action thành công; notification con khi deliver có thể snooze tiếp thành một child mới. Overlap với fixed slot được merge theo scheduler contract và không có nhánh sửa/replace một pending snooze.
- Snooze chỉ được lên lịch nếu thời điểm mới `< work_end` trong cùng ngày; nếu không, app giải thích và bỏ reminder đó.
- `REST_ONLY` hủy/suppress mọi reminder còn lại của ngày nguồn. Người dùng vẫn có thể chủ động gửi check-in mới; nếu kết quả mới cho phép routine, app chỉ lên lại các fixed slot còn ở tương lai.
- Notification giao trễ ngoài cửa sổ cho phép phải bị drop, không bù dồn.
- Lịch đi theo timezone local hiện tại và được tính lại khi reboot, đổi timezone hoặc đổi đồng hồ.
- MVP không đọc calendar, location, motion hoặc trạng thái lái xe. Copy yêu cầu người dùng bỏ qua notification nếu đang lái xe hoặc không ở nơi phù hợp.
- Tắt notification không ảnh hưởng metric giá trị, không làm mất dữ liệu và không tạo penalty.

## 12. Dữ liệu và quyền riêng tư

- MVP không có account, backend, cloud AI, remote analytics hoặc SDK quảng cáo/crash reporting.
- App không khai báo quyền `INTERNET`, Health Connect, calendar, location hay activity recognition.
- Dữ liệu nhạy cảm được mã hóa bằng AES-GCM; khóa được bảo vệ bởi Android Keystore.
- Backup hệ điều hành cho dữ liệu app bị tắt trong MVP.
- Export dùng Android Storage Access Framework; UI cảnh báo rằng file đã export nằm ngoài vùng bảo vệ của app.
- “Xóa toàn bộ” phải xóa database, file, key, cache, local log và notification đang chờ.
- Privacy policy công khai, in-app disclaimer và Google Play Health Apps declaration vẫn là release gate dù dữ liệu chỉ nằm local.
- Pilot chỉ thu dữ liệu khi người tham gia chủ động export và chuyển qua kênh nghiên cứu đã được phê duyệt.

Chi tiết nằm trong [`docs/05-data-privacy-security.md`](docs/05-data-privacy-security.md).

## 13. Tổng kết tuần

Tổng kết tuần chỉ hiển thị số đếm và tỷ lệ hành vi đã định nghĩa:

- số ngày có phiên phù hợp (`qualified_break_days`);
- số phiên bắt đầu/hoàn thành;
- số feedback theo từng mức gắng sức;
- số feedback phù hợp bối cảnh;
- số lần snooze/bỏ qua nếu app quan sát được.

Luôn hiển thị số đếm. Chỉ hiển thị từng tỷ lệ khi mẫu số tương ứng của chính tỷ lệ đó từ năm trở lên. Không hiển thị correlation, causal claim, recovery trend hoặc insight do AI tạo.

## 14. Metric

### North-star

**Qualified movement-break days mỗi tuần**: số ngày làm việc do người dùng chọn, tính theo timezone local, có ít nhất một routine:

1. hoàn thành;
2. `context_fit = yes`;
3. `new_or_worse_pain = no`.

Ngày thiếu feedback không được tính là qualified nhưng vẫn được tính trong completion metric.

### Funnel và guardrail

- Activation: hoàn thành routine đầu trong interval nửa mở 24 elapsed hours neo tại `onboarding_completed`; completion đúng `+24h` bị loại, clock evidence không đủ được báo `unknown` chứ không mặc định 0.
- Prompt-to-open và prompt-to-start.
- Routine start/completion và completion rate.
- Week-2 active: có ít nhất một routine **bắt đầu** trong study day 8–14, là các block 24 elapsed hours neo tại original `onboarding_completed`; phân loại bằng start elapsed evidence của Session, clock evidence không đủ được báo `unknown`.
- Context-fit rate với denominator rõ ràng.
- `too_hard` và `new_or_worse_pain` rate.
- Notification permission denied/disabled, snooze và skip rate.
- Serious adverse event: bất kỳ trường hợp nào cũng tạm dừng pilot và kích hoạt review; `n=24` không thể chứng minh an toàn.

Không ghép trạng thái notification vào north-star. Event dictionary và timezone semantics nằm trong [`docs/07-analytics-and-validation.md`](docs/07-analytics-and-validation.md).

## 15. Validation

### Feasibility pilot 14 ngày

- 24 người thuộc ICP, single-arm.
- Đây là pilot khả dụng của toàn bộ experience, không phải A/B test và không chứng minh adaptive recommendation tốt hơn reminder cố định.
- Primary feasibility outcome: median `qualified_study_days_week_2` — số **study day** khác nhau trong các block 8–14 có ít nhất một session thỏa cùng predicate completed + selected-workday snapshot + context=yes + pain=no. Nhiều local date trong cùng một block chỉ tính một, nên range là 0–7; báo cáo kèm `n`, distribution/range và missingness. Chỉ số pilot này tách khỏi `qualified_break_days` theo calendar week trong UI.
- Safety event được review theo protocol, không dùng “không có event trong 24 người” làm bằng chứng an toàn.
- Dữ liệu được thu bằng export chủ động sau informed consent; production build không có remote telemetry.
- Trước recruitment, owner phải xác định pilot có phải human-subject research hay không và có ethics/IRB-equivalent approval hoặc documented exemption phù hợp. Bộ docs không tự tuyên bố pilot được miễn review.

### Go/no-go

Các ngưỡng, denominator và cách xử lý missing data được khóa trước pilot trong tài liệu validation. Không bỏ lớp check-in chỉ vì một mẫu nhỏ không cho khác biệt thống kê.

Thử nghiệm so sánh fixed reminder với contextual suggestion là bước riêng sau feasibility, cần randomization, một biến can thiệp chính, primary endpoint và cỡ mẫu phù hợp.

### Willingness-to-pay

WTP được kiểm chứng ngoài app bằng interview/landing-page có điều khoản hoàn tiền rõ ràng. MVP không triển khai paywall. Export, delete, safety và accessibility không bao giờ là quyền lợi trả phí.

## 16. Kế hoạch 6–8 tuần

Giả định tối thiểu:

- hai Android engineers;
- một product/designer;
- một QA part-time từ tuần 2;
- ít nhất hai người đủ năng lực cho content: Movement Content Author và Clinical Safety Reviewer khác identity; Technique Reviewer có thể kiêm một trong hai role nếu credential thỏa đúng contract;
- privacy/store review trước release candidate.

Nếu thiếu một trong các vai trò trên, timeline phải được estimate lại; không giảm safety, accessibility hoặc data-control gate để giữ ngày phát hành.

| Tuần | Outcome |
|---:|---|
| 1 | Architecture skeleton, schema, design tokens, content manifest validator |
| 2 | Onboarding, eligibility, check-in và rule engine với unit tests |
| 3 | Recommendation UI, routine player và hai routine đầu đã review |
| 4 | Đủ sáu routine, feedback, safety hold và notification scheduling |
| 5 | Weekly summary, settings, export/delete và privacy hardening |
| 6 | Accessibility, integration/E2E, content sign-off và closed-test build |
| 7–8 | Buffer sửa lỗi, store/policy gate và pilot readiness |

Backlog chi tiết nằm trong [`docs/09-delivery-plan.md`](docs/09-delivery-plan.md).

## 17. Release gate bắt buộc

Không phát hành pilot hoặc production nếu thiếu một trong các điều kiện:

1. Rule engine fixtures và safety invariants pass 100%.
2. Tất cả routine/movement và global eligibility/red-flag/outcome/pain-gate copy có manifest/digest hợp lệ và sign-off chuyên môn còn hiệu lực.
3. Không có đường đi từ hard stop đến routine trong cùng ngày bị khóa.
4. Export/delete, notification denied/revoked, offline, reboot và timezone tests pass.
5. TalkBack, font scaling, contrast, caption/text instruction, pause/stop flow pass.
6. Data inventory, privacy policy, store disclaimer và Google Play declaration được duyệt.
7. Build không chứa quyền/SDK/network endpoint ngoài allowlist.
8. Store listing và toàn bộ UI không chứa claim chẩn đoán, điều trị, phòng bệnh hoặc bảo đảm an toàn.
9. Pilot có documented ethics/IRB-equivalent determination phù hợp; consent, adverse-event escalation và secure transfer process được phê duyệt.

## 18. Monetization sau MVP

Giả thuyết giá giữ để research, chưa phải scope build:

- 49.000–79.000đ/tháng;
- 399.000–599.000đ/năm.

Feature có thể trả phí sau validation: content packs bổ sung, scheduling nâng cao và trend dài hạn. Safety flow, six-routine core, delete và full raw-data export vẫn miễn phí.

## 19. Roadmap sau MVP

### Phase 2 — Sau feasibility

- Thư viện lớn hơn sau content governance review.
- Thử nghiệm reminder/context có thiết kế nghiên cứu phù hợp.
- Monetization experiment và restore-purchase design.
- iOS feasibility và parity plan.

### Phase 3 — Chỉ sau data/privacy gate riêng

- Health Connect hoặc HealthKit theo từng platform, không dùng chung HRV metric.
- Baseline tối thiểu theo valid samples, source và measurement method; không dùng ngưỡng 14/28 ngày như claim lâm sàng.
- Cloud sync opt-in hoặc AI chỉ khi có data-flow, consent, retention, deletion và deterministic fallback được duyệt.
- Calendar-aware cue theo quyền tối thiểu.

## 20. Rủi ro và guardrail

| Rủi ro | Guardrail MVP |
|---|---|
| Safety rule mơ hồ | Decision table versioned, fixtures và hard-stop invariant |
| False precision | Không score/confidence/biometric trong MVP |
| Claim vượt khả năng đo | Dùng “movement break hoàn thành”, không tuyên bố đo sedentary bout |
| Notification fatigue | Người dùng tự đặt tối đa hai giờ; snooze/skip không phạt |
| Content gây khó chịu | Thư viện đóng, regression, stop rule và expert sign-off |
| Privacy leak | Không network/account/SDK; encryption, backup off, delete/export tests |
| AI tạo claim | Không AI trong MVP |
| Pilot bị diễn giải quá mức | Gọi đúng feasibility pilot; không kết luận causal/safety |
| Scope trượt | Mọi feature ngoài §6 cần decision record và estimate lại |

## 21. Nguồn tham khảo

- [WHO — Guidelines on Physical Activity and Sedentary Behaviour](https://www.who.int/publications/i/item/9789240015128)
- [CDC — Physical Activity Breaks for the Workplace](https://www.cdc.gov/workplace-health-promotion/media/pdfs/2024/06/Workplace-Physical-Activity-Break-Guide-508.pdf)
- [Guidelines for rigor and reproducibility of HRV](https://pubmed.ncbi.nlm.nih.gov/42495990/)
- [Google Play — Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511)
- [Android — Health Connect permissions and data access](https://developer.android.com/health-and-fitness/health-connect/ui/permissions)
- [Android — Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [Android — App data and files](https://developer.android.com/training/data-storage)
- [Android — Accessibility principles](https://developer.android.com/guide/topics/ui/accessibility/principles)

WHO/CDC hỗ trợ định hướng “move more, sit less”; chúng không tự xác nhận duration 2–5 phút, thuật toán recommendation hay hiệu quả sức khỏe của sản phẩm. Các chi tiết đó là giả thuyết sản phẩm và phải được validation riêng.
