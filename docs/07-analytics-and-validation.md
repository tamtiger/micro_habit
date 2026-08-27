# Nhịp 2 Phút — Analytics local và kế hoạch validation

- Phiên bản: 1.0
- Trạng thái: `Implementation baseline`; pilot chưa được phép tuyển trước `PILOT-GATE-ETHICS`
- Phạm vi: Android MVP, local-only
- Requirement nguồn: [01-product-requirements.md](./01-product-requirements.md)

## 1. Mục tiêu đo lường

Instrumentation của MVP chỉ trả lời ba nhóm câu hỏi:

1. Người dùng có hoàn tất luồng check-in → routine → feedback không?
2. Reminder, snooze và lịch làm việc có vận hành đúng như người dùng đã đặt không?
3. MVP có đủ khả thi để tiếp tục cải tiến và có safety signal nào buộc dừng/review sau pilot 14 ngày không?

Không dùng dữ liệu MVP để:

- tuyên bố routine cải thiện sức khỏe, đau, năng lượng hoặc hiệu suất làm việc;
- suy luận correlation giữa check-in và kết quả;
- so sánh causal giữa adaptive/fixed reminder;
- huấn luyện hoặc chạy AI/ML;
- xếp hạng người dùng, tạo score hoặc cá nhân hóa ngầm;
- gửi analytics, crash log hoặc health value qua mạng.

`Analytics` trong tài liệu này nghĩa là event log và phép tính local. Không có remote analytics service.

## 2. Nguyên tắc dữ liệu

### MET-001 — Local-only và opt-in export

- Event được ghi vào database mã hóa trên thiết bị.
- App không có permission Internet, endpoint, SDK analytics hoặc upload nền.
- Event log có thể được dùng tại chỗ để tạo tổng kết tuần.
- Dữ liệu chỉ rời app khi người dùng chủ động chọn `Export dữ liệu`, đọc cảnh báo plaintext và chọn vị trí lưu qua Android Storage Access Framework.
- Pilot không có nút “Gửi cho nhóm nghiên cứu”. Việc chuyển file, nếu có, diễn ra ngoài app và cần consent riêng.
- Product event giữ tối đa 90 ngày hoặc đến khi người dùng xóa sớm hơn. Xóa toàn bộ cũng xóa event log, `installation_id` và mọi random record ID cũ; onboarding lại tạo dataset không liên kết.

### MET-002 — Tối thiểu hóa

Event không chứa tên, email, số điện thoại, advertising/device ID, vị trí, text tự do hoặc nội dung triệu chứng chi tiết. Raw check-in nằm trong entity `check_ins` local; event chỉ tham chiếu ID và domain result cần thiết.

### MET-003 — Nguồn dữ liệu chuẩn

Khi entity và event khác nhau:

1. `sessions`, `feedback`, `reminders`, `decisions` là nguồn chuẩn cho số đếm/trạng thái cuối.
2. `events` dùng cho funnel, timing và audit trình tự.
3. Event trùng không được làm tăng metric; mọi phép tính dedupe theo entity/event ID.
4. Không “sửa” event lịch sử; thay đổi trạng thái tạo event mới và entity giữ trạng thái hiện hành.

Riêng step skip, ordered unique `Session.player_checkpoint.skipped_steps[]` là nguồn chuẩn; `routine_step_skipped` phải mirror từng record để audit, không được dùng event rời để dựng lại list hoặc `step_skip_count`.

### MET-004 — Retention theo đồ thị tham chiếu

Một event, active constraint hoặc immutable audit snapshot còn retention không được outlive entity mà nó tham chiếu. Khi commit, authority của toàn source graph phải được extend: `Session → Decision → CheckIn → WorkScheduleVersion` hoặc `Decision → CheckIn → WorkScheduleVersion`. Late feedback, post-session hold/cap, check-in hold/rest suppression và evaluation/runtime-cap snapshot không được sống sau FK nguồn.

Chiều ngược chỉ tồn tại cho closed `required_companion_event_ref`: profile onboarding/ack; CheckIn commit; Decision commit + created hold/rest; Session start/skip/terminal/pain/feedback transition + post-session hold/cap; Reminder create/snooze/delivery/interaction/resolution; WeeklySummary generation. Source authority kéo đúng companion event, rồi event kéo ordinary refs/source graph; ordinary entity target không bao giờ kéo ngược authority của chính nó về event. Do đó universal AppProfile ref không làm mọi event sống đến full delete. Missing/thừa companion, wrong role/cardinality/mirror hoặc late extension không kéo companion là data-quality/storage failure, không được coi event “hết retention hợp lệ”.

`ReminderOccurrence` giữ 90 ngày hoặc đến authority muộn nhất của event/companion hay `RoutineSession(source=reminder)` tham chiếu nó; occurrence cũng kéo retention của `WorkScheduleVersion` nguồn. Purge dùng exact source+required-companion deletion set tới least fixed point, xen kẽ source→all companion events và event→all peer sources; source peer tiếp tục mở rộng, nhưng ordinary refs không được traverse. Xóa referencing Session/event trước occurrence và chỉ xóa schedule khi không còn CheckIn/Decision/Session/occurrence tham chiếu. Export snapshot chỉ chứa graph FK-valid + companion-complete. Full delete do người dùng xác nhận vẫn xóa toàn bộ graph ngay lập tức và ưu tiên hơn retention định kỳ.

Authority user-data là encrypted union `RetentionAuthorityV1 = Finite(RetentionCutoffV1) | UntilFullDeleteFromAppProfile`; không sentinel max date. Finite cutoff giữ full LocalStamp origin, positive calendar days/deadline và `source_kind` đóng gồm `companion_reference`; derived prefilter non-null. Full branch exact profile singleton anchor và prefilter null. Cutoff 90 ngày được tính một lần tại start-of-day của `origin.local_date + 90 calendar days` trong `origin.zone_id`; không dùng current date/zone, `90×24h` hoặc recompute trượt. Equality mới eligible và vẫn phải qua graph/companion check. Directed queue chỉ adopt full branch hoặc finite candidate có deadline strictly muộn hơn; sớm hơn/equal giữ current. Weekly summary cố định `week_start_local_date + 13 weeks` (`91` ngày); generated/viewed event copy chính authority này, không dùng event-stamp+90, nên recompute/view ngày 80 vẫn cùng deadline và tại/equality không emit.

## 3. Time contract

### MET-010 — Event envelope

Mọi event có envelope bắt buộc:

| Field | Kiểu | Quy tắc |
|---|---|---|
| `event_id` | UUID | Ngẫu nhiên, duy nhất trong dataset local. |
| `event_schema_version` | integer | `1` trong MVP. |
| `name` | enum string | Một tên tại event dictionary. |
| `occurred_at_utc` | `InstantWireV1` string | Exact `YYYY-MM-DDTHH:mm:ss.SSSZ` UTC/millisecond theo DATA; alias/numeric bị reject. |
| `local_date` | `YYYY-MM-DD` | Tính tại lúc phát sinh, không tính lại về sau. |
| `zone_id` | IANA ZoneId | Zone thiết bị tại sự kiện. |
| `utc_offset_minutes` | integer | Offset thực tế tại sự kiện, để đọc/audit; `zone_id` vẫn là nguồn zone. |
| `installation_id` | UUID | Mirror exact encrypted `AppProfile.installation_id`; sinh ở first eligible profile commit, không phải hardware/device ID; full delete xóa và onboarding kế tiếp sinh mới. |
| `decision_id` | opaque UUID/null | Tham chiếu decision khi có. |
| `session_id` | opaque UUID/null | Tham chiếu session khi có. |
| `reminder_occurrence_id` | opaque UUID/null | Tham chiếu occurrence khi có. |
| `schedule_version_id` | opaque UUID/null | Tham chiếu WorkScheduleVersion khi event có schedule context. |
| `source` | typed enum/null | Entry point/nguồn đã whitelist cho event. |
| `properties` | object | Chỉ các key được whitelist cho event tương ứng. |

### MET-010A — Event envelope mask v1

`EventEnvelopeMaskV1` dưới đây là exhaustive cho đúng 48 event schema v1. Năm slot được kiểm soát là `decision_id`, `session_id`, `reminder_occurrence_id`, `schedule_version_id`, `source`; mỗi slot của mỗi event phải thuộc đúng một cột. “Allowed nullable (conditional)” không cho phép tùy ý: validator phải áp điều kiện ở cột cuối để quyết định non-null hay null. Slot required bị thiếu/null, slot forbidden non-null, event không xuất hiện đúng một nhóm hoặc property duplicate một envelope slot đều bị reject trước commit/import.

| Event(s) | Required non-null slots | Allowed nullable (conditional) | Forbidden/must-null slots | Exact condition/source codec |
|---|---|---|---|---|
| `app_first_opened`, `onboarding_started`, `age_gate_answered`, `scope_acknowledged`, `scope_reack_required`, `scope_reack_completed`, `notification_permission_prompted`, `notification_permission_updated`, `onboarding_completed`, `check_in_started`, `check_in_reconfirmation_required`, `rest_suppression_superseded`, `weekly_summary_generated`, `weekly_summary_viewed`, `export_started`, `export_completed`, `export_failed` | — | — | `decision_id`, `session_id`, `reminder_occurrence_id`, `schedule_version_id`, `source` | Không envelope source/ref. Property tên `source` của `notification_permission_updated` vẫn chỉ nằm trong `properties`, không được copy sang envelope. |
| `work_schedule_saved`, `schedule_reconciled`, `check_in_submitted` | `schedule_version_id` | — | `decision_id`, `session_id`, `reminder_occurrence_id`, `source` | Schedule ID là version đang được transaction dùng; không dùng previous/source version ở envelope. |
| `decision_evaluated`, `routine_start_blocked` | `decision_id`, `schedule_version_id` | — | `session_id`, `reminder_occurrence_id`, `source` | Trusted Start command luôn mang Decision + source Schedule; rejection trước trusted boundary không tạo `routine_start_blocked`. |
| `recommendation_shown`, `rest_suppression_created`, `routine_selected` | `decision_id` | — | `session_id`, `reminder_occurrence_id`, `schedule_version_id`, `source` | Decision là projection/source entity duy nhất của event. |
| `routine_paused`, `routine_resumed`, `routine_recovery_offered`, `routine_recovery_failed`, `routine_step_skipped`, `routine_stopped`, `routine_abandoned`, `routine_completed`, `pain_gate_resolved`, `feedback_updated`, `day_mode_cap_updated` | `session_id` | — | `decision_id`, `reminder_occurrence_id`, `schedule_version_id`, `source` | Session là source entity; graph Decision/CheckIn/Schedule đi qua Session, không duplicate envelope ref. |
| `reminder_posted`, `reminder_opened`, `reminder_snoozed`, `reminder_dismissed`, `reminder_merged`, `reminder_cancelled`, `reminder_blocked_permission`, `reminder_skipped` | `reminder_occurrence_id` | — | `decision_id`, `session_id`, `schedule_version_id`, `source` | Occurrence là source/loser row; schedule và related occurrence khác đi qua entity/additional-ref matrix. |
| `reminder_scheduled` | `reminder_occurrence_id`, `schedule_version_id` | — | `decision_id`, `session_id`, `source` | Nested fixed-key schedule, nếu có, phải bằng envelope schedule theo `MET-013`. |
| `safety_hold_created` | — | `session_id` | `decision_id`, `reminder_occurrence_id`, `schedule_version_id`, `source` | `source_type=check_in`: `session_id=null`, property `source_id` required và resolve CheckIn. `source_type=session`: `session_id` required, property `source_id` forbidden; logical source ID chính là envelope session. |
| `safety_screen_shown` | — | `decision_id`, `session_id` | `reminder_occurrence_id`, `schedule_version_id`, `source` | `URGENT_STOP`/`PAUSE_TODAY`: Decision required, Session null. `BLOCKED_FOR_TODAY` + `blocked_post_session_new_or_worse_pain`: Session required, Decision null. Bốn blocked route từ red/acute CheckIn hold: cả hai null; runtime rerender không tạo/đính Decision mới. |
| `routine_started` | `decision_id`, `session_id`, `schedule_version_id`, `source` | `reminder_occurrence_id` | — | `source` wire chỉ nhận lowercase `home\|reminder`. `home` bắt buộc occurrence null. `reminder` bắt buộc occurrence non-null, resolve row `DELIVERED` có `first_opened_at`, và occurrence schedule bằng envelope/active/CheckIn/Decision/Session schedule; context không đạt điều kiện phải được normalize upstream thành `home` + null trước khi tạo draft, còn importer reject event sai matrix. |

Envelope `source` không phải producer tag chung: ngoài `routine_started`, nó luôn null, kể cả event có property `source`, `source_type`, `trigger` hoặc `change_source`. Codec không lowercase/coerce alias; unknown case/value bị reject. Registry generator phải chứng minh set-union các nhóm trên bằng exact 48 `EventNameV1`, intersection đôi rỗng, rồi phát cùng mask/conditional validator cho writer và offline importer. Golden generator lấy một fixture hợp lệ của từng event, lần lượt null/missing mỗi required slot, đặt UUID/source hợp lệ vào mỗi forbidden slot, mutate từng conditional/source branch và unknown source; mọi mutant phải fail, không được normalize khi import.

Mọi raw event/export property có key kết thúc `_ms` là JSON integer int64 trong `[0, 9_223_372_036_854_775_807]`; không float, numeric string, âm, saturation hoặc wrap. Duration chỉ lấy từ elapsed-realtime monotonic anchor cùng boot và checked subtraction/addition. Wall instant/UTC chỉ để audit, không được dùng làm duration fallback. Event giữ explicit XOR giữa duration và invalid reason theo dictionary; sample invalid vẫn được giữ cho count/funnel nhưng bị loại khỏi timing aggregate kèm reason count.

### MET-011 — Ngày, tuần và lịch thay đổi

- Product week: half-open `[thứ Hai 00:00, thứ Hai kế tiếp 00:00)` theo `local_date` đã lưu ở từng event/session.
- Thay timezone không phân loại lại lịch sử.
- `RoutineSession` lưu `is_selected_workday_at_start` và `schedule_version_id`; cờ bắt buộc bằng phép membership ISO-day của `started_at.local_date` trong immutable source ScheduleVersion `selected_weekdays`. Start event mirror byte-equal; thay lịch/zone về sau không viết lại hoặc phân loại lại cờ.
- `CheckIn` và `Decision` lưu cùng non-null `schedule_version_id`; start chỉ hợp lệ khi ID này bằng active schedule và Session copy đúng ID. Schedule đổi trả `RECONFIRM_REQUIRED(reason=schedule_changed)` thay vì phân loại lại hoặc dùng work window cũ.
- Ngày không selected vẫn cho manual check-in/session trong work interval, nhưng không notification; session snapshot false và bị loại khỏi `qualified_break_days`.
- Work interval là `[work_start, work_end)`; fixed reminder/snooze target phải `< work_end`, receiver/start tại `now >= work_end` phải skip/expire.
- Sau higher guard/contract, exact precedence là: active schedule mismatch → `RECONFIRM_REQUIRED(schedule_changed)`; ngoài current active window → `EXPIRED`; trong window nhưng source date khác → `local_date_changed`; rồi freshness resolver: boot mismatch/elapsed rollback/arithmetic overflow → `clock_unknown`, generation/zone mismatch hoặc mapping drift >2.000 ms → `timezone_or_time_change`, còn continuous evidence chạm TTL equality → `ttl`. Authorization interval là half-open `[confirmed_at, min(confirmed_at + 6 giờ elapsed, work_end))`; `reconfirm_after` chỉ là audit/UI wall field.
- Các timezone/system-time/clock reason trên không phân loại lại lịch sử và không clear sớm persisted constraints.
- Completion sau nửa đêm dùng `local_date` tại `started_at` cho metric ngày; MVP không cho bắt đầu tại/sau `work_end`, nên đây chủ yếu là guard cho clock/timezone edge case.
- Safety hold/day cap/rest suppression lưu origin `local_date`, `zone_id`, audit `expires_at_utc` và clock evidence. Trong continuous boot, effective monotonic deadline là authority và hết tại equality dù wall clock bị lùi; reboot/discontinuity có thể tạo conservative extension nhưng không được giả hết hạn từ wall time mới.
- Tỷ lệ phần trăm **chỉ để hiển thị/wire summary** làm tròn số nguyên gần nhất theo half-up; mọi feasibility threshold so rational gốc bằng cross-multiplication tại `MET-054`, không so số đã làm tròn.

### MET-012 — Ngưỡng mẫu

Counts luôn được hiển thị. Một rate chỉ được tính/hiển thị khi mẫu số `n >= 5`; nếu `n < 5`, value là `null`, reason=`insufficient_sample`, UI dùng `Chưa đủ dữ liệu để tính tỷ lệ (cần ít nhất 5 lần).`

Aggregation v1 trước tiên lọc đúng sample eligible, non-null, schema/timing-valid rồi sort tăng dần `x[0..n-1]`; không impute missing/invalid. Median với `n` lẻ là middle item; với `n` chẵn là arithmetic midpoint overflow-safe của hai middle item và có thể kết thúc `.5`. P90 là nearest-rank `x[ceil(0.9×n)-1]`, không interpolation. Khi `n<5`, median/p90 đều `null`, reason=`insufficient_sample`; khi `n>=5`, báo exact raw result + `n` và so threshold trên raw result. Không làm tròn duration/median/p90; quy tắc half-up tại `MET-011` chỉ áp cho rate phần trăm. Golden bắt buộc: `[3,3,3,5,5,5]` có median `4`, p90 `5`; p90 boundary phải kiểm rank 9/10 cho `n=10` và rank 10/11 cho `n=11`.

### MET-013 — Event entity-reference matrix

Event writer/importer dùng matrix này làm canonical; không suy reference chỉ vì key kết thúc `_id`.

Mọi event luôn tạo edge `installation_id → AppProfile`; writer đồng thời chứng minh envelope installation ID byte-equal encrypted singleton profile. Mỗi canonical envelope slot non-null tạo đúng edge tương ứng: `decision_id → Decision`, `session_id → RoutineSession`, `reminder_occurrence_id → ReminderOccurrence`, `schedule_version_id → WorkScheduleVersion`. Nếu target có envelope slot, ID phải nằm ở envelope; property trùng slot bị reject. Nullable slot chỉ tạo edge khi non-null.

Physical index có exact columns `product_event_entity_ref(event_id, ref_table, ref_id)`. `ref_table` (`RefTargetType`) chỉ nhận `app_profile|safety_acknowledgement|work_schedule_version|check_in|decision|session|reminder_occurrence|weekly_summary`. `ref_id` là canonical BLOB: app-profile edge dùng signed `int64_be(1)`; mọi target khác dùng 16 raw UUID bytes. Unknown token/length bị reject. `safety_acknowledgement` resolve UUID xuất hiện đúng một lần trong decrypted immutable acknowledgement history; retention target thực tế là AppProfile singleton, vốn không auto-purge.

Additional logical refs ngoài envelope:

| Event | Logical slot → target entity |
|---|---|
| `scope_acknowledged` | `acknowledgement_id → SafetyAcknowledgement` nested record |
| `scope_reack_required` | `current_acknowledgement_id → SafetyAcknowledgement` |
| `scope_reack_completed` | `acknowledgement_id`, `supersedes_acknowledgement_id → SafetyAcknowledgement` |
| `work_schedule_saved` | nullable `previous_schedule_version_id → WorkScheduleVersion`; current schedule chỉ ở envelope |
| `check_in_submitted` | `check_in_id → CheckIn`; schedule chỉ ở envelope |
| `check_in_reconfirmation_required` | `check_in_id → CheckIn` |
| `decision_evaluated` | `check_in_id → CheckIn`; Decision/schedule chỉ ở envelope |
| `safety_hold_created` | logical `source_id → CheckIn` khi `source_type=check_in`; logical `source_id → RoutineSession` khi `source_type=session`. Wire session-source dùng canonical envelope `session_id` và không duplicate property; check-in-source dùng property `source_id` vì envelope không có CheckIn slot |
| `rest_suppression_superseded` | `source_decision_id → Decision`; `new_check_in_id → CheckIn` |
| `recommendation_shown`, `routine_selected` | Khi `runtime_day_mode_cap_snapshot` non-null: `mode_trigger_session_id`, `source_session_id → RoutineSession`; hai ID bằng nhau chỉ tạo một dedup edge |
| `routine_started` | Decision/Session/ReminderOccurrence/schedule chỉ ở envelope |
| `day_mode_cap_updated` | `expiry_source_session_id → RoutineSession`; triggering Session chỉ ở envelope; nếu hai logical refs bằng nhau chỉ một dedup edge |
| `reminder_scheduled` | nullable `parent_occurrence_id`, `supersedes_occurrence_id → ReminderOccurrence`; fixed `logical_fixed_key.schedule_version_id` phải bằng envelope schedule ID và dedupe cùng universal edge |
| `reminder_snoozed` | `snooze_occurrence_id`, nullable `supersedes_occurrence_id → ReminderOccurrence`; source occurrence chỉ ở envelope `reminder_occurrence_id`, còn envelope string `source` phải null |
| `reminder_merged` | `kept_occurrence_id → ReminderOccurrence`; loser chỉ ở envelope |
| `weekly_summary_generated`, `weekly_summary_viewed` | `summary_id → WeeklySummary` |

Các event còn lại có **không additional entity ref** ngoài universal envelope: `app_first_opened`, `onboarding_started`, `age_gate_answered`, `notification_permission_prompted`, `notification_permission_updated`, `onboarding_completed`, `schedule_reconciled`, `check_in_started`, `safety_screen_shown`, `rest_suppression_created`, `routine_start_blocked`, `routine_paused`, `routine_resumed`, `routine_recovery_offered`, `routine_recovery_failed`, `routine_step_skipped`, `routine_stopped`, `routine_abandoned`, `routine_completed`, `pain_gate_resolved`, `feedback_updated`, `reminder_posted`, `reminder_opened`, `reminder_dismissed`, `reminder_cancelled`, `reminder_blocked_permission`, `reminder_skipped`, `export_started`, `export_completed`, `export_failed`. Required/nullable/forbidden envelope refs không được suy từ danh sách prose này mà phải lấy exhaustively từ `MET-010A`.

Explicit non-entity correlation IDs là `first_open_id`, `check_in_flow_id`, NotificationPromptAttempt `attempt_id` và `export_id`. `routine_id`, `step_id`, content version/digest và asset IDs là signed content identity, không DB entity ref; presentation `route_id` là typed enum, không MessageKey/entity ID. `logical_fixed_key` không phải entity; chỉ nested schedule ID của nó là ref. Writer không tạo edge cho các giá trị này.

Trong cùng source transaction, writer resolve đúng type, insert event và một deduped `(event_id,ref_table,ref_id)` cho mỗi logical target, rồi extend authority của toàn source graph. Với closed companion role, writer còn insert exact `required_companion_event_ref(event_id,source_table,source_id)`; physical table không có plaintext role, role/selector derive từ typed event payload. `source_table` chỉ nhận `app_profile|safety_acknowledgement|check_in|decision|session|reminder_occurrence|weekly_summary`; app-profile ID là `int64_be(1)`, còn lại 16 raw UUID, acknowledgement resolve nested profile. Skip/feedback role dùng owning Session ID và selector trong event, không pseudo-ID. Missing/unresolved/wrong-type target, conditional source mismatch, property duplicate hoặc ordinary/companion edge thiếu/thừa làm rollback. Export importer recompute cả hai matrix từ payload/source graph; internal ref indexes không phải export collection.

### MET-014 — Event idempotency registry

Mỗi event row có plaintext internal `product_event.idempotency_key_version INTEGER NOT NULL=1` và physical `product_event.idempotency_key BLOB NOT NULL UNIQUE` exact 32 byte. Physical formula v1 là:

`HMAC-SHA-256(K_event_idem_v1, UTF8(JCS(preimage)))`

`K_event_idem_v1` là non-exportable per-installation Keystore key, immutable suốt lifetime của dataset; full delete xóa key, onboarding đủ điều kiện kế tiếp tạo dataset/key mới. Key, version column và physical HMAC không phải event field, không export và không được thay bằng public fingerprint. Thiếu/mất key phải fail closed với dataset hiện tại, không tự sinh key thay thế để “sửa” row cũ. `preimage` có exact object shape sau; JCS tự canonicalize object key nhưng không thay registry order của array `parts`:

```json
{"schema":"event-idem-v1","domain":"<registry-domain>","parts":[{"name":"<selector-name>","value":"<canonical-value>"}]}
```

`parts` phải đúng thứ tự registry dưới đây; không sort lại. UUID selector là lowercase canonical hyphenated string; digest/version/enum giữ exact wire string; `updated_fields` encode duy nhất thành `effort`, `context_fit` hoặc `effort,context_fit` theo canonical order. Không dùng display copy, timestamp, hashCode/ordinal hoặc nullable field ngoài selector. On-device writer/storage verifier có key phải recompute HMAC và reject unknown event/policy/selector, value không canonical, version khác `1`, HMAC mismatch, key length khác 32 byte hoặc row legacy thiếu version/dùng plain SHA-256; không silent upgrade/rekey.

Policy:

- `AT_MOST_ONCE`: natural selector xác định một logical event; mọi retry/concurrent call phải tạo cùng key.
- `REPEATABLE_BY_EVENT_ID`: mỗi **actual** UI/lifecycle observation có một `event_id` được cấp một lần và giữ trong typed command/draft; domain là chính event name, selector duy nhất `event_id`. Retry cùng observation bắt buộc reuse ID; caller không được cấp ID mới để né dedupe. Hai command đồng thời chỉ được emit khi mỗi command thắng một source-state transition/observation riêng; CAS/no-op không emit event.

Registry `AT_MOST_ONCE` exhaustive:

| Event name | `domain` | Ordered selector parts |
|---|---|---|
| `app_first_opened` | `app_first_opened` | `first_open_id` |
| `onboarding_started` | `onboarding_started` | `installation_id` |
| `age_gate_answered` | `age_gate_answered` | `installation_id` |
| `scope_acknowledged` | `scope_acknowledged` | `acknowledgement_id` |
| `scope_reack_required` | `scope_reack_required` | `current_acknowledgement_id`, `required_content_version`, `required_content_digest`, `trigger` |
| `scope_reack_completed` | `scope_reack_completed` | `acknowledgement_id` |
| `work_schedule_saved` | `work_schedule_saved` | envelope `schedule_version_id` |
| `notification_permission_prompted` | `notification_permission_prompted` | `attempt_id` |
| `notification_permission_updated` khi `source=system_prompt` | `notification_permission_system_result` | `attempt_id` |
| `onboarding_completed` | `onboarding_completed` | `installation_id` |
| `check_in_started` | `check_in_started` | `check_in_flow_id` |
| `check_in_submitted` | `check_in_submitted` | `check_in_flow_id` |
| `decision_evaluated` | `decision_evaluated` | envelope `decision_id` |
| `safety_hold_created` | `safety_hold_created` | `source_type`, logical `source_id` theo `MET-013` |
| `rest_suppression_created` | `rest_suppression_created` | envelope `decision_id` |
| `rest_suppression_superseded` | `rest_suppression_superseded` | `source_decision_id`, `new_check_in_id` |
| `routine_started` | `routine_started` | envelope `session_id` |
| `routine_recovery_failed` | `routine_recovery_failed` | envelope `session_id` |
| `routine_step_skipped` | `routine_step_skipped` | envelope `session_id`, `step_id`; `active_elapsed_ms` là payload mirror, không thuộc natural key |
| `routine_completed`, `routine_stopped`, `routine_abandoned` | shared `routine_terminal` | envelope `session_id` |
| `pain_gate_resolved` | `pain_gate_resolved` | envelope `session_id` |
| `feedback_updated` | `feedback_updated` | envelope `session_id`, canonical `updated_fields` codec |
| `day_mode_cap_updated` | `day_mode_cap_updated` | envelope triggering `session_id` |
| `reminder_scheduled` | `reminder_scheduled` | envelope `reminder_occurrence_id` |
| `reminder_posted`, `reminder_merged`, `reminder_cancelled`, `reminder_blocked_permission`, `reminder_skipped` | shared `reminder_delivery_resolution` | envelope `reminder_occurrence_id` |
| `reminder_opened` | `reminder_opened` | envelope `reminder_occurrence_id` |
| `reminder_snoozed` | `reminder_snoozed` | `snooze_occurrence_id` |
| `reminder_dismissed` | `reminder_dismissed` | envelope `reminder_occurrence_id` |
| `export_started` | `export_started` | `export_id` |
| `export_completed`, `export_failed` | shared `export_terminal` | `export_id` |

Registry `REPEATABLE_BY_EVENT_ID` exhaustive:

| Event name | Lý do repeatable |
|---|---|
| `notification_permission_updated` khi `source=settings\|resume_check` | Mỗi explicit Settings return/runtime observation là một occurrence; same-process duplicate callback reuse draft ID. |
| `schedule_reconciled` | Nhiều trigger/reconcile pass hợp lệ theo thời gian. |
| `check_in_reconfirmation_required` | Cùng CheckIn có thể bị chặn tại nhiều user entry attempt. |
| `safety_screen_shown` | Mỗi render thực tế được audit. |
| `recommendation_shown` | Recommendation có thể render lại. |
| `routine_selected` | Người dùng có thể đổi lựa chọn nhiều lần. |
| `routine_start_blocked` | Mỗi trusted typed Start command đã qua command-boundary validation rồi bị domain guard chặn là một occurrence; preflight-proof/envelope-store rejection không tạo draft/event ID. |
| `routine_paused`, `routine_resumed` | Nhiều transition player hợp lệ; chỉ state-transition winner emit. |
| `routine_recovery_offered` | Mỗi relaunch/recovery render hợp lệ là một occurrence. |
| `weekly_summary_generated` | Summary có thể recompute/refresh nhiều lần. |
| `weekly_summary_viewed` | Mỗi view thực tế là một occurrence. |

Insert event, entity side effect, ref rows và retention extension nằm trong cùng transaction. Unique physical-HMAC conflict chỉ được coi là idempotent success khi existing event có cùng name, envelope selectors, typed properties và logical ref-set byte/canonical-equivalent; khác name/payload/ref với cùng key là `IDEMPOTENCY_CONFLICT` và rollback, đặc biệt cho ba shared domain. `event_id` ngẫu nhiên một mình không thay natural key của `AT_MOST_ONCE`.

Export không chứa `idempotency_key_version`, physical HMAC hay Keystore material. Offline export validator **không thể và không được** recompute physical HMAC: nó canonicalize cùng `(domain, ordered parts)` từ payload, giữ tuple/JCS bytes chỉ trong volatile memory cho lần validate đó, rồi reject event không thuộc đúng một registry row, duplicate event ID và mọi tuple `AT_MOST_ONCE`/shared-domain xuất hiện lần hai (phân loại exact duplicate hay conflict bằng payload/ref logic). Nó phải discard set sau validation, không persist/export/log public SHA-256 fingerprint của preimage và không giả kiểm tra physical DB integrity. Physical forensic verification chỉ chạy on-device nơi key/version còn khả dụng.

## 4. Event dictionary

### 4.1. Quy ước

- Tên event dùng `snake_case`, immutable trong schema version 1.
- `decision_id`, `session_id`, `reminder_occurrence_id`, `schedule_version_id` dùng slot opaque trong envelope; ID conventional có slot không được duplicate trong properties. Các ID khác được liệt kê trong typed properties theo `MET-013`.
- Enum ngoài danh sách bị từ chối khi ghi event; không fallback sang raw string.
- Event chỉ được commit sau khi thao tác nguồn đã commit, trừ event `*_started`/`*_shown` mô tả việc bắt đầu/hiển thị thực tế.
- `notification_permission_prompted` đúng một lần/attempt và commit trước launcher; `notification_permission_updated(source=system_prompt)` tối đa một lần/cùng attempt. `check_in_started` tối đa một lần/`check_in_flow_id`; `check_in_submitted` tối đa một lần/flow và chỉ event này có actual `check_in_id`. `decision_evaluated` tối đa một lần/decision; đúng một terminal event trong `routine_completed|routine_stopped|routine_abandoned` tối đa một lần/session; `pain_gate_resolved` tối đa một lần/session; `reminder_posted` tối đa một lần/occurrence. `feedback_updated` chỉ ghi khi effort/context thực sự chuyển từ null sang giá trị hợp lệ; retry idempotent không tạo event mới.

### 4.2. Onboarding và setting

| Event | Khi ghi | Properties bắt buộc |
|---|---|---|
| `app_first_opened` | First-open timestamp của dataset đủ điều kiện, staged trong RAM rồi chỉ commit sau age/eligibility confirmation | `first_open_id` |
| `onboarding_started` | `SCR-001` hiển thị; staged trong RAM đến khi đủ age+eligibility | `timing_start_boot_marker`, `timing_start_elapsed_realtime_ms` |
| `age_gate_answered` | Eligible path được commit sau cả age+eligibility gate; không ghi event cho safe-exit | `eligible_18_plus: true` |
| `scope_acknowledged` | Initial scope + eligibility `Có` được staged rồi chỉ commit cùng first schedule/Profile/onboarding transaction; không ghi event cho safe-exit | `acknowledgement_id`, `kind: onboarding`, `eligibility_confirmed: true`, `content_version`, `content_digest` |
| `scope_reack_required` | Valid bundled global-safety artifact khác current acknowledgement và route re-ack thực sự hiển thị | `current_acknowledgement_id`, `previous_content_version`, `previous_content_digest`, `required_content_version`, `required_content_digest`, `trigger: home\|notification\|check_in\|routine_start` |
| `scope_reack_completed` | Re-ack record append + current pointer update atomically commit | `acknowledgement_id`, `supersedes_acknowledgement_id`, `content_version`, `content_digest` |
| `work_schedule_saved` | Lịch/version hợp lệ đã commit; initial `change_source=onboarding` nằm cùng Profile/onboarding transaction, edit/toggle dùng Settings transaction | required envelope `schedule_version_id`; properties `previous_schedule_version_id\|null`, `enabled: boolean`, `selected_weekday_count`, `work_start`, `work_end`, `reminder_count`, `change_source: onboarding\|settings`, `active_decision_invalidated: boolean` |
| `notification_permission_prompted` | Chỉ dialog-launchable branch: durable encrypted attempt + event đã commit trước ActivityResult launcher | `attempt_id`, `trigger: automatic_onboarding\|explicit_user_retry` |
| `notification_permission_updated` | Có dialog result, explicit Settings return hoặc runtime resume observation | `state: granted\|denied\|unavailable`, `source: system_prompt\|settings\|resume_check`; `source=system_prompt` require `attempt_id` + `prompt_result: granted\|not_granted`, source khác require cả hai null |
| `onboarding_completed` | Initial Profile + acknowledgement + active schedule + full onboarding event bundle đã commit, trước optional permission flow; envelope LocalStamp mirror 1:1 `profile.onboarding_completed_at` | exact XOR `duration_ms` hoặc `timing_invalid_reason: same_boot_unavailable\|elapsed_rollback\|overflow`; `activation_boot_marker`, `activation_elapsed_realtime_ms`, `activation_clock_generation`, `activation_wall_minus_elapsed_ms` mirror 1:1 profile |
| `schedule_reconciled` | Reschedule/cancel sau state change | required envelope `schedule_version_id`; properties `reason: schedule_edit\|boot\|timezone_change\|app_update\|permission_change\|safety_hold\|rest_only\|fresh_check_in_after_rest\|active_session\|pending_pain\|pain_resolved_no`, `scheduled_count`, `cancelled_count`, `merged_count` |

`work_start`, `work_end` và từng `reminder_times[]` dùng exact ASCII zero-padded `HH:mm`, regex `^(?:[01][0-9]|2[0-3]):[0-5][0-9]$`; domain second/nanosecond phải 0 và parse→serialize byte-identical. Reminder list có 1–2 value distinct, sort tăng theo local wall time. `work_schedule_saved.work_start/work_end` mirror exact active WorkScheduleVersion; export `work_schedule` mirror cả ba field. `9:00`, `09:00:00`, whitespace, offset/zone suffix, duplicate/unsorted list hoặc silent normalization đều là contract/data-quality error. Các giá trị này chỉ nằm trong local event/export người dùng chủ động tạo; app không gửi chúng đi. Snooze `target_at`/child `due_at` vẫn là full LocalStamp và có thể giữ giây/millisecond bất kỳ.

`NotificationPromptAttemptV1` là durable encrypted operational record chỉ của dialog-launchable branch, giữ đến full delete và không export trực tiếp; `attempt_id` là correlation ID, không entity FK. Exact fields là `trigger=automatic_onboarding|explicit_user_retry`, `state=PENDING|RESOLVED|INTERRUPTED`, opaque `origin_process_instance_id`, full LocalStamp `attempted_at`, nullable full LocalStamp `resolved_at`, nullable `prompt_result`, nullable `interruption_reason`. PENDING có cả ba nullable null; RESOLVED có resolved-at/result non-null + interruption null; INTERRUPTED có resolved-at + `interruption_reason=process_recreated_before_callback` + result null. Tối đa một PENDING. Record + `notification_permission_prompted` phải commit trước ActivityResult launcher; DB failure không launch.

Automatic onboarding launcher chỉ chạy nếu không tồn tại automatic attempt ở bất kỳ state nào. CTA launcher/retry disabled khi PENDING. New process atomically mark old PENDING thành INTERRUPTED trước UI và không tạo fabricated result event; explicit retry sau đó tạo ID mới. Late callback keyed tới interrupted/unknown attempt bị ignore/reject và không rebound. Mỗi attempt có đúng một prompted event và tối đa một `notification_permission_updated(source=system_prompt)`; retry callback không nhân event.

Android callback false không reliably phân biệt Deny/Dismiss nên cả hai map `prompt_result=not_granted`, không phát event/state `dismissed`. Với system prompt, `granted→state=granted`, `not_granted→state=denied`; `unavailable` không phải system result. Callback missing không được bịa result; khi resume chỉ có thể ghi observation `source=resume_check` với attempt/result null.

Nếu adapter trả settings-required/unavailable, explicit CTA mở Settings trực tiếp: không attempt, không prompted event, không PENDING. Same-process return từ chính navigation đó ghi đúng một `notification_permission_updated(source=settings,attempt_id=null,prompt_result=null,state=<runtime>)` kể cả state lặp; duplicate lifecycle callback bị dedupe. Process recreation không tạo INTERRUPTED vì không có attempt; generic resume observation dùng `source=resume_check`. Settings-only path không auto-open lại và bị loại khỏi initial-prompt numerator/denominator. Repeated same-state observation không phải permission transition. Allowlisted permission events được persist/export để audit/metric nhưng không là authoritative current OS state: app không lưu current-state copy trong profile/preferences, luôn đọc Android runtime khi render/reconcile và không dùng event cuối để cấp quyền hoặc schedule.

`app_first_opened`, `onboarding_started`, age answer và eligible scope acceptance chỉ được buffer trong RAM cho tới initial `Lưu lịch`. Khi age=false, eligibility=no/unsure, process loss trước save hoặc onboarding transaction fail, buffer bị bỏ và không có profile/entity/event nào được persist. First successful schedule/onboarding transaction sinh random `installation_id`, rồi atomically commit Profile + first acknowledgement/current pointer + initial ScheduleVersion/active pointer + staged events + `scope_acknowledged|work_schedule_saved|onboarding_completed` với đúng ID này. Mọi event về sau phải mirror profile ID; full delete xóa cả hai và profile mới dùng ID mới. Permission UI chỉ được mở sau full commit.

`scope_reack_completed` tối đa một lần cho mỗi `acknowledgement_id`, `supersedes_acknowledgement_id` phải bằng current pointer ngay trước transaction, và event phải mirror record `kind=reack`/pointer mới. `scope_reack_required` dedupe theo tuple `(current_acknowledgement_id, required_content_version, required_content_digest, trigger)` trong một pending re-ack epoch; retry/rerender cùng tuple không tăng event. Hai event chỉ chứa artifact identity/trigger allowlist, không raw eligibility/safety answer. Re-ack không tạo `onboarding_completed`, sửa activation anchor hoặc reset study-day clock.

### 4.3. Check-in và decision

| Event | Khi ghi | Properties bắt buộc |
|---|---|---|
| `check_in_started` | Mở red-flag gate | random `check_in_flow_id`, `kind: new\|reconfirm`, `timing_start_boot_marker`, `timing_start_elapsed_realtime_ms`; không có `check_in_id`/entity FK |
| `check_in_submitted` | Canonical discriminated CheckIn/reconfirm đã commit | required envelope `schedule_version_id`; envelope `occurred_at_utc/local_date/zone_id/utc_offset_minutes` byte-equal CheckIn `confirmed_at`; properties cùng `check_in_flow_id`, actual `check_in_id`, `kind: new\|reconfirm`, `answers_kind: red_flag_stop\|acute_stop\|full`; exact XOR `duration_ms` hoặc `timing_invalid_reason: same_boot_unavailable\|elapsed_rollback\|overflow\|background_over_10m` |
| `check_in_reconfirmation_required` | Active decision cần xác nhận lại trước start | `check_in_id`, `age_ms\|null`, `reason: ttl\|local_date_changed\|timezone_or_time_change\|clock_unknown\|schedule_changed`, `trigger: home\|notification\|routine_start` |
| `decision_evaluated` | `Decision` có source CheckIn đã commit | required envelope `decision_id` + `schedule_version_id`; properties `check_in_id` non-null, `result: URGENT_STOP\|PAUSE_TODAY\|INCOMPLETE\|REST_ONLY\|RECOVER\|MAINTAIN\|BUILD`, `base_mode: RECOVER\|MAINTAIN\|BUILD\|null`, `effective_mode: RECOVER\|MAINTAIN\|BUILD\|null`, ordered `reason_codes[]`, ordered `invalid_fields[]`, `rule_version`, `cap_applied: boolean`; persisted `INCOMPLETE` chỉ cho Full CheckIn + authenticated/decode-success inner cap enum/shape invalid và exact `[day_mode_cap]` |
| `safety_screen_shown` | Render một safety outcome từ typed signed route | conditional envelope Decision/Session theo `MET-010A`; properties `result: URGENT_STOP\|PAUSE_TODAY\|BLOCKED_FOR_TODAY`, `route_id: urgent_stop\|pause_acute_illness\|pause_new_or_worsening_pain_or_injury\|pause_medically_restricted\|blocked_red_flag\|blocked_acute_illness\|blocked_new_or_worsening_pain_or_injury\|blocked_medically_restricted\|blocked_post_session_new_or_worse_pain`, `content_digest` map exact bundled `globalSafetyContentDigestSha256` |
| `recommendation_shown` | Recommendation có mode render thành công | required envelope `decision_id`; properties `routine_id`, `base_mode: RECOVER\|MAINTAIN\|BUILD`, `decision_effective_mode: RECOVER\|MAINTAIN\|BUILD`, `runtime_effective_mode: RECOVER\|MAINTAIN\|BUILD`, `cap_applied: boolean`, nullable exact `runtime_day_mode_cap_snapshot` |
| `safety_hold_created` | Hold được commit từ red flag, acute issue hoặc post-session pain | `kind: RED_FLAG\|ACUTE_ILLNESS\|NEW_OR_WORSENING_PAIN_OR_INJURY\|MEDICALLY_RESTRICTED\|POST_SESSION_NEW_OR_WORSE_PAIN`, `source_type: check_in\|session`, conditional source wire theo `MET-010A`/`MET-013`, `origin_local_date`, `origin_timezone_id`, `expires_at_utc`, `rule_version: 1`; check-in branch require property `source_id`, session branch forbid property đó và dùng envelope session |
| `rest_suppression_created` | `REST_ONLY` persist suppression | required envelope `decision_id`; properties `origin_local_date`, `origin_timezone_id`, `expires_at_utc`, `rule_version: 1` |
| `rest_suppression_superseded` | Fresh check-in chủ động commit result thay active Rest state | `source_decision_id`, `new_check_in_id`, `new_result: mode\|rest\|safety`, `future_fixed_slots_rescheduled: integer >=0` |

Active hold trả runtime result `BLOCKED_FOR_TODAY` và chỉ ghi `safety_screen_shown`; mỗi lần rerender không tạo `Decision` mới. Persisted `INCOMPLETE`/`decision_evaluated` chỉ hợp lệ cho committed Full CheckIn khi daily-constraint bundle auth/decode thành công nhưng inner day-cap enum/shape invalid. Draft thiếu/sai không tạo CheckIn/Decision/event; restored/migrated schema sai là contract/migration error. AES-GCM tag/key/envelope/bundle auth/decode failure hoặc SafetyHold không xác minh được trả `CONTRACT_ERROR` trước engine và không có `decision_evaluated`. Red/acute early-stop CheckIn dùng discriminated canonical shape và persist decision+hold atomically.

`safety_screen_shown.route_id` là presentation-route enum, không phải MessageKey. Exact matrix: `URGENT_STOP→urgent_stop`; `PAUSE_TODAY` dùng `pause_acute_illness|pause_new_or_worsening_pain_or_injury|pause_medically_restricted` khớp Decision reason; `BLOCKED_FOR_TODAY` dùng `blocked_red_flag|blocked_acute_illness|blocked_new_or_worsening_pain_or_injury|blocked_medically_restricted|blocked_post_session_new_or_worse_pain` khớp authenticated `SafetyHold.kind`. UI resolve route qua typed `CNT-015` slots/`holdRouteBindings`, rồi event digest phải bằng exact bundled global safety digest đã dùng render toàn message set. Message key đơn lẻ, route/result/reason mismatch hoặc current-build digest thay cho rendered artifact bị reject.

`routine_start_blocked` và `check_in_reconfirmation_required` phải dùng cùng exact reason mapping: `schedule_changed|local_date_changed|ttl|timezone_or_time_change|clock_unknown`. `EXPIRED` chỉ cho outside current active work window và không giả thành reconfirm reason.

Start input chỉ trở thành trusted typed command sau khi command adapter xác minh one-shot process-scoped `PreflightAttestationV1` bind current process/pre-flight instance, routine/full content identity, explicit safety acknowledgement và ordered exact REQUIRED-context Yes set, đồng thời có thể tạo valid event envelope trên authenticated profile/store. Missing/forged/stale/reused/wrong-process/routine/content/context proof hoặc corrupt profile/event store trả `CONTRACT_ERROR`/fail closed **trước event boundary**: không Session, không `routine_start_blocked`, không draft event ID. `CONTRACT_ERROR` trong event enum chỉ dành cho trusted command sau boundary gặp authenticated data/content contract failure. Attestation/token không persist/export/event.

Domain hold source `ConstraintSourceType.CHECK_IN|SESSION` dùng codec explicit thành lowercase `source_type=check_in|session` trong storage/export/event. Writer/validator từ chối uppercase/alias trên wire và source ID sai loại.

`rest_suppression_superseded` là audit của một transaction reducer, không phải lệnh scheduler rời. `new_result=mode` iff Decision mới là `RECOVER|MAINTAIN|BUILD`: clear suppression và field `future_fixed_slots_rescheduled` bằng đúng số fixed occurrence tương lai thực sự insert, có thể bằng 0. `new_result=rest` iff Decision mới là `REST_ONLY`: atomically thay suppression cũ bằng suppression mới, emit một `rest_suppression_created` cho Decision mới và rescheduled count bắt buộc 0. `new_result=safety` iff Decision mới là `URGENT_STOP|PAUSE_TODAY`: atomically supersede suppression, tạo matching SafetyHold + `safety_hold_created`, không schedule và count bắt buộc 0. `INCOMPLETE`, contract/migration error hoặc transaction fail giữ suppression cũ, không emit superseded event và không schedule. Retry cùng committed CheckIn/Decision phải trả side-effect cũ, không tạo suppression/hold/occurrence/event trùng.

Event không chứa raw `red_flag`, `acute_issue`, `energy`, `stiffness`, `intent`; các field này chỉ nằm trong check-in entity mà người dùng sở hữu.

`check_in_flow_id` là random opaque UUID sinh khi form mở, độc lập với mọi entity ID và không phải FK. Một abandoned flow hợp lệ chỉ có started event; writer/importer không được đòi CheckIn cho flow đó. Submit dùng một transaction ClockSnapshot để commit CheckIn + submitted event atomically: CheckIn chỉ persist `confirmed_at`, còn event envelope quartet phải byte-equal stamp đó. Không có CheckIn/export `submitted_at`; correlation sang decision/session chỉ qua actual `check_in_id` của submitted event.

Nếu flow được submit, `check_in_submitted.kind` phải bằng `check_in_started.kind` của cùng flow. Với `decision_evaluated`, result `RECOVER|MAINTAIN|BUILD` bắt buộc `base_mode=result`, effective mode non-null và `effective_mode<=base_mode`; mọi result khác bắt buộc cả hai mode null. `invalid_fields[]` nonempty iff result `INCOMPLETE`, còn mọi result khác bắt buộc empty. `cap_applied=true` iff effective nhẹ hơn base.

Flow duration resolver yêu cầu cùng process-instance/`timing_start_boot_marker`, end elapsed không lùi và checked subtraction không overflow. Background bắt đầu khi `MainActivity` chuyển `ON_STOP` (`STARTED→<STARTED`) và kết thúc ở `ON_START` (`<STARTED→STARTED`); `ON_PAUSE` không tính. Accumulator cộng mọi interval từ started event đến endpoint, kể cả config recreation cùng process; duration vẫn là `end-start`, không trừ background. Encrypted tracker tiếp tục qua submit đến routine start. Mất process-instance/tracker continuity dùng `same_boot_unavailable` dù boot chưa đổi. Reason precedence exact là `same_boot_unavailable → elapsed_rollback → overflow → background_over_10m`.

Với onboarding, failure ghi đúng một `timing_invalid_reason=same_boot_unavailable|elapsed_rollback|overflow`. Với check-in/total, sau ba nhánh đó, cumulative background `>600_000 ms` ghi `background_over_10m`; equality vẫn ghi duration. Duration và reason là XOR, không cùng có/đều thiếu. `check_in_reconfirmation_required.age_ms` là nullable int64 monotonic elapsed từ CheckIn confirmation: chỉ non-null khi same-process/boot continuity và subtraction an toàn; không bao giờ dùng wall delta. Reason schedule/local-date vẫn có thể mang non-null age nếu continuity hợp lệ.

### 4.4. Routine và feedback

| Event | Khi ghi | Properties bắt buộc |
|---|---|---|
| `routine_selected` | Người dùng/app chọn card hợp lệ | required envelope `decision_id`; properties `routine_id`, `routine_mode: RECOVER\|MAINTAIN\|BUILD`, `runtime_effective_mode: RECOVER\|MAINTAIN\|BUILD`, `selection: recommended\|same_mode\|lighter_mode`, nullable exact `runtime_day_mode_cap_snapshot` |
| `routine_start_blocked` | Trusted typed Start command có valid event envelope/store đã qua command boundary, rồi domain guard từ chối trước Session | required envelope `decision_id` + `schedule_version_id`; properties `gate: SAFETY_LOCKED\|PENDING_SAFETY_FEEDBACK\|SESSION_ALREADY_ACTIVE\|SCOPE_REACK_REQUIRED\|RECONFIRM_REQUIRED\|EXPIRED\|OUTCOME_HAS_NO_ROUTINE\|MODE_NOT_ALLOWED\|CONTRACT_ERROR`; conditional `reason=schedule_changed\|ttl\|local_date_changed\|timezone_or_time_change\|clock_unknown` chỉ khi `RECONFIRM_REQUIRED`. Attestation-proof rejection không dùng row này |
| `routine_started` | Player thực sự bắt đầu, persisted lifecycle=`ACTIVE`; event mirror Session/start transaction | required envelope `session_id`, `decision_id`, `schedule_version_id`, validated `reminder_occurrence_id\|null`, `source: home\|reminder`; properties `routine_id`, source `check_in_flow_id`, `runtime_effective_mode_at_start: RECOVER\|MAINTAIN\|BUILD`, `is_selected_workday_at_start: boolean`, `start_boot_marker`, `start_elapsed_realtime_ms`, `start_clock_generation`, `start_wall_minus_elapsed_ms`; exact XOR `total_duration_ms` hoặc `total_timing_invalid_reason: same_boot_unavailable\|elapsed_rollback\|overflow\|background_over_10m` |
| `routine_paused` | Player substate `PLAYING→PAUSED`; lifecycle vẫn `ACTIVE` | required envelope `session_id`; property `elapsed_ms` mirror `accumulated_active_ms` sau reconcile |
| `routine_resumed` | Player substate `PAUSED→PLAYING` | required envelope `session_id`; property `elapsed_ms` mirror unchanged `accumulated_active_ms` |
| `routine_recovery_offered` | Relaunch có recovery evidence hợp lệ | required envelope `session_id`; properties `elapsed_ms`, `content_version` lấy từ immutable session content snapshot, không từ current metadata/build |
| `routine_recovery_failed` | Active session hợp lệ nhưng không thể resume phải atomically abandon+pending | required envelope `session_id`; property `reason: reboot_or_clock_discontinuity\|work_window_or_date_expired\|content_unavailable_or_identity_mismatch`; corrupt Session/checkpoint không emit event này |
| `routine_step_skipped` | Skip reducer thắng transaction trong `STEP_TIMER` trước equality | required envelope `session_id`; properties `step_id` từ signed content, `active_elapsed_ms` integer int64 không âm; cả hai mirror exact ordered `skipped_steps` record vừa append |
| `routine_stopped` | Người dùng trả lời direct pain question và dừng trước completion | required envelope `session_id`; properties `elapsed_ms`, `pain_gate_status: RESOLVED_NO\|RESOLVED_HOLD`; answer + terminal transition commit atomically |
| `routine_abandoned` | Phiên structurally valid nhưng không thể recovery; pain gate được tạo cùng transaction | required envelope `session_id`; properties `reason: reboot_or_clock_discontinuity\|work_window_or_date_expired\|content_unavailable_or_identity_mismatch`, `pain_gate_status: PENDING` |
| `routine_completed` | Entity chuyển từ `COMPLETION_CTA_WAIT` sang completed; pain gate và activation comparison evidence được tạo cùng transaction | required envelope `session_id`; properties `routine_id`, `duration_ms` mirror terminal `accumulated_active_ms`, `step_skip_count` mirror `skipped_steps.size`, `pain_gate_status: PENDING`, `completion_boot_marker`, `completion_elapsed_realtime_ms`, `completion_clock_generation`, `completion_wall_minus_elapsed_ms` |
| `pain_gate_resolved` | Pain answer commit; completed/abandoned resolve pending guard, còn stop commit trong cùng transaction với `routine_stopped` | required envelope `session_id`; properties `terminal_state: completed\|stopped\|abandoned`, `new_or_worse_pain: yes\|no`, `pain_gate_status: RESOLVED_NO\|RESOLVED_HOLD`, `answered_at_or_after_origin_expiry: boolean` |
| `feedback_updated` | Ít nhất một effort/context field được commit null→value | required envelope `session_id`; properties ordered nonempty `updated_fields` subset của `[effort,context_fit]`, `terminal_state: completed\|stopped\|abandoned`, post-commit `effort: easy\|moderate\|too_hard\|null`, post-commit `context_fit: yes\|no\|null`, `feedback_complete: boolean`, `cap_result: applied\|not_too_hard\|pain_not_no\|origin_day_expired\|no_effort_transition` |
| `day_mode_cap_updated` | Bất kỳ terminal feedback too_hard + pain=no hạ cap trước effective origin expiry | envelope `session_id` là triggering feedback; properties `expiry_source_session_id`, `basis_mode: BUILD\|MAINTAIN\|RECOVER`, `previous_cap: MAINTAIN\|RECOVER\|null`, `new_cap: MAINTAIN\|RECOVER`, `deadline_source: existing_later\|candidate_later\|same`, terminal-origin `origin_occurred_at_utc`, `origin_local_date`, `origin_timezone_id`, `origin_utc_offset_minutes`, `expires_at_utc`, `rule_version: 1` |

`routine_id` chỉ nhận sáu canonical ID. `routine_started.runtime_effective_mode_at_start` là authorization ceiling transaction-local sau active cap và phải mirror Session; không phải selected routine mode. `routine_mode` lấy từ signed routine/session snapshot, phải `<= runtime_effective_mode_at_start` và không bao giờ là cap basis. Feedback cap basis dùng active cap nếu có, nếu không dùng Session `runtime_effective_mode_at_start`.

`recommendation_shown` chỉ tồn tại cho mode-bearing Decision. `base_mode`/`decision_effective_mode` mirror byte-exact immutable `Decision.base_mode`/`Decision.effective_mode`; resolver đọc authenticated active cap ở từng render để tính `runtime_effective_mode=min(Decision.effective_mode, active_cap)`. `cap_applied=true` iff runtime value nhẹ hơn base, kể cả cap đã nằm trong Decision hoặc xuất hiện sau đó; routine mặc định có signed mode bằng runtime value. `routine_selected` bắt buộc cùng Decision envelope và snapshot resolver mới ở action time: `recommended` phải đúng computed recommended routine và mode bằng event runtime value; `same_mode` là routine khác có mode bằng runtime value; `lighter_mode` iff signed routine mode nhẹ hơn runtime value. Mode nặng hơn, stale Decision effective, selector label sai hoặc routine ID/mode mismatch bị reject. `routine_started.runtime_effective_mode_at_start` vẫn được re-resolve và mirror Session transaction-local ceiling; cap có thể đổi tiếp giữa select và Start nên selected event không authorize Session.

Hai projection event dùng cùng conditional: `runtime_day_mode_cap_snapshot` bắt buộc non-null iff `runtime_effective_mode < Decision.effective_mode`, và bắt buộc null nếu bằng nhau. Snapshot non-null là deep copy exact `DayModeCap` authenticated **sau** clock resolver đã checkpoint/reconcile trong cùng serialized transaction; `runtime_effective_mode=min(Decision.effective_mode,snapshot.max_mode)`. Snapshot giữ cả `mode_trigger_session_id` (nguồn lần hạ mode hiện hành) và `source_session_id` (nguồn origin/expiry); event writer tạo dedup logical ref tới cả hai Session và extend toàn source graph tới cutoff event. Active cap không làm runtime nhẹ hơn immutable Decision không được attach như metadata thừa.

Writer/importer luôn require `runtime_effective_mode <= decision_effective_mode <= base_mode`. On-device writer là boundary duy nhất authorize active state: nó resolve/checkpoint authenticated cap và commit event + snapshot + hai logical refs atomically. Offline importer không có operational constraint hay current-boot authority, nên không tái dựng cap timeline từ event rời rạc; nó validate exact conditional snapshot, `runtime_effective_mode=min(Decision.effective_mode,snapshot.max_mode)`, expiry LocalStamp/deadline/source Session, và mode lineage qua Feedback `day_mode_cap_update_snapshot` của `mode_trigger_session_id`. Missing source/snapshot/ref, stable provenance không khớp, projection nặng hơn hoặc snapshot thừa là data-quality failure. Clock evidence sau reconciliation phải structurally coherent; nếu export không thể chứng minh event-time active state trên boot khác thì sample là `unknown_clock`, không fallback UTC, không silently chấp nhận stale mode và không reject một snapshot provenance-hợp lệ chỉ vì thiếu current-boot authority.

Optional feedback update chỉ commit selected null→value field; không ghi đè answer đã có. `Để sau` không tạo `feedback_updated`. Context-only update giữ effort null, dùng `cap_result=no_effort_transition` và có thể làm completed selected-workday qualify; effort-only too_hard giữ context null và atomically commit cap snapshot/event nếu reducer hợp lệ.

`feedback_updated.updated_fields` là ordered, unique, nonempty subset theo canonical order `[effort,context_fit]`; mỗi field liệt kê phải là transition null→value thực sự và field không liệt kê không được overwrite. `terminal_state` mirror Session. Event effort/context mirror entity **sau** commit; `feedback_complete=true` iff entity có `new_or_worse_pain`, `effort` và `context_fit` đều non-null, ngược lại false.

`cap_result` là total function trên transition: `no_effort_transition` iff `effort` không thuộc updated_fields; `not_too_hard` iff effort vừa update thành easy/moderate; `pain_not_no` iff effort vừa update too_hard nhưng pain entity khác no; `origin_day_expired` iff effort vừa update too_hard + pain=no nhưng origin constraint inactive; `applied` iff cùng case đó còn active và cap state + immutable snapshot + `day_mode_cap_updated` commit atomically. Retry/overwrite, true complete khi pain pending hoặc cap-result không khớp bị reject.

Start-gate value `PENDING_SAFETY_FEEDBACK` route UI `PENDING_PAIN_GATE`; nó không phải rule outcome và không được đổi tên thành `SAFETY_CHECK_REQUIRED`.

Start/check-in guard ưu tiên active hold → pending pain → active-session recovery → global-safety acknowledgement. Authenticated artifact mismatch trả `SCOPE_REACK_REQUIRED`; corrupt acknowledgement/bundle trả `CONTRACT_ERROR`. Sau đó mới kiểm same active `schedule_version_id`, window và freshness. Generic reminder có thể post khi re-ack pending, nhưng open chỉ route Home/re-ack và không tạo session.

Pain=yes trả lời muộn tạo hold với origin date/timezone tại thời điểm answer commit, không phải session day. Với optional effort transition `too_hard` sau pain=no khi resolver xác nhận effective session-origin expiry đã tới, entity chỉ persist effort + exact `updated_at`; `day_mode_cap_update_snapshot` phải null. Event `feedback_updated` của transition đó dùng property-only `cap_result=origin_day_expired`; không có `day_mode_cap_updated` hoặc cap state ngày mới. `answered_at_or_after_origin_expiry` của `pain_gate_resolved` là kết quả resolver tại pain answer, không phải field cap-result hoặc phép so raw wall clock với audit UTC.

Cap event envelope `occurred_at_utc` là feedback commit instant; cap state/side-effect snapshot adopt nguyên terminal-origin stamp, expiry và clock evidence của `expiry_source_session_id`, không ghép event instant với origin date/zone. Resulting cap còn giữ `mode_trigger_session_id` để truy lần feedback thực sự hạ mode; nó không bị thay bằng expiry source. Post-session pain=yes hold ngược lại dùng zone/date tại answer commit.

Khi chưa có existing cap, `deadline_source=candidate_later` và resulting cap có cả mode-trigger/expiry-source bằng triggering Session. Khi có hai constraint, chỉ candidate có effective deadline strictly later mới thay expiry provenance; existing later dùng `existing_later`, equality dùng `same` và giữ existing origin/evidence/`expiry_source_session_id`. Độc lập, strict lower cập nhật `mode_trigger_session_id` sang triggering Session; existing `RECOVER→RECOVER` giữ mode trigger cũ kể cả khi expiry source đổi. `day_mode_cap_update_snapshot.trigger_session_id` luôn là invocation hiện tại và chỉ được khác resulting mode trigger ở nhánh deadline-only này.

Người dùng chủ động `Kết thúc phiên` luôn tạo `STOPPED`; `ABANDONED` chỉ dùng cho recovery fail-closed theo ba reason enum trên.

Per-routine safety acknowledgement trước Start là ephemeral UI state; routine switch/process loss clear nó và không có product event. Toggle player `Cách dễ hơn` chỉ đổi signed title/instruction/demo của current step; không persist preference/inference và không có product event. `Replay` chỉ seek current signed demo media về 0/play: phase/remaining/active counter/cadence/skip/session bất biến, không persist và không có event. Writer từ chối event ngoài allowlist để suy đoán acknowledgement, regression/easier usage hoặc media replay.

Session checkpoint persist exact nullable `substate=PLAYING|PAUSED`, `phase=STEP_TIMER|STEP_TRANSITION|COMPLETION_CTA_WAIT`, `step_index`, `current_step_remaining_ms`, `transition_remaining_ms`, `accumulated_active_ms`, ordered `skipped_steps[{step_id,active_elapsed_ms}]`, nullable segment anchor, last-checkpoint elapsed/boot, `last_announced_cadence_ordinal` và content identity. `STEP_TIMER` require current remaining trong `(0,planned_step_ms]`/transition remaining 0; `STEP_TRANSITION` require current 0/transition remaining trong `(0,planned_transition_ms]`; `COMPLETION_CTA_WAIT` require last step, cả hai remaining 0 và substate/segment null. Không persist phase timer/transition với remaining 0.

`planned_step_ms` lấy signed `seconds×1.000` cho `DURATION` hoặc `estimatedSeconds×1.000` cho `REPETITIONS`; reps chỉ là display dosage và cả hai kind auto-advance. `planned_transition_ms=transitionAfterSeconds×1.000`; multiplication/subtraction dùng checked int64. Display seconds dùng overflow-safe `ceilDiv(remaining_ms,1.000)`; equality 0 atomically normalize sang transition dương, step kế hoặc CTA wait. Callback chỉ consume tối đa current-phase remaining; lateness vượt boundary không carry sang phase chưa render và phase mới neo tại current snapshot.

Chỉ milliseconds consume trong `STEP_TIMER` khi `PLAYING` cộng `accumulated_active_ms`; transition, pause, background, CTA wait và planned remainder bị skip không cộng. Mọi player `elapsed_ms` và `routine_completed.duration_ms` mirror counter này. Cadence accessibility dùng cùng counter/`last_announced_cadence_ordinal`; không có zero announcement/event. Terminal transition freeze checkpoint/counter; recovery/event retry không rewrite lịch sử, và counter/checkpoint corrupt không wall fallback hoặc auto-complete.

Stop tap reconcile dưới per-session coordinator rồi persist PAUSED/segment-null trước pain dialog. PLAYING entry tạo đúng một `routine_paused`; already-PAUSED tạo zero; Continue-from-dialog chỉ tạo `routine_resumed` khi prior same-process state là PLAYING. Dialog wait không đổi phase/counter/cadence. `routine_stopped.elapsed_ms` phải mirror frozen counter tại Stop tap, không answer timestamp; final-step equality thắng Stop nếu reconcile đã vào CTA wait.

Skip chỉ hợp lệ trong `STEP_TIMER` với remaining dương. Reducer reconcile trước, rồi atomically append đúng một ordered unique record `{step_id,active_elapsed_ms=planned_step_ms-current_step_remaining_ms}` với `0<=active_elapsed_ms<planned_step_ms`, emit event mirror và chạy cùng next-phase reducer; không cộng remainder. Record phải resolve signed step, strict catalog order, không vượt current step; current step chỉ xuất hiện sau khi rời its timer. Race tại equality thuộc timer completion và không tạo record/event. `routine_completed.step_skip_count=skipped_steps.size`; importer/metric không reconstruct list từ event.

### 4.5. Reminder

| Event | Khi ghi | Properties bắt buộc |
|---|---|---|
| `reminder_scheduled` | Một immutable occurrence generation/ordinal mới đã đăng ký | required envelope `reminder_occurrence_id` + `schedule_version_id`; common properties full LocalStamp `due_at`, `kind: fixed\|snooze`, required nullable `supersedes_occurrence_id`. Fixed branch có đúng `logical_fixed_key`, nonnegative `generation`, `creation_reason`; snooze branch có đúng `parent_occurrence_id`, integer literal `ordinal=0`. Keys của branch kia **absent**, không serialize null |
| `reminder_posted` | App thực sự post notification | envelope `reminder_occurrence_id`; properties `kind: fixed\|snooze`, full LocalStamp `due_at`, full LocalStamp `delivered_at`, `lateness_ms` |
| `reminder_opened` | Lần đầu người dùng tap notification body hoặc action `Bắt đầu`; cả hai chỉ mở Home/re-run guard | envelope `reminder_occurrence_id`; properties full LocalStamp `first_opened_at`, `open_surface: notification_body\|start_action` |
| `reminder_snoozed` | Chọn 15/30/60 hợp lệ; source DELIVERED không đổi, child SNOOZED đã commit | envelope `reminder_occurrence_id` là source; properties `snooze_occurrence_id`, integer `duration_minutes: 15\|30\|60`, full LocalStamp `target_at`, integer literal `ordinal=0`, `supersedes_occurrence_id=null`; `target_at` byte-equal child `due_at` |
| `reminder_dismissed` | Android delete intent lần đầu báo người dùng vuốt bỏ | envelope `reminder_occurrence_id`; property full LocalStamp `dismissed_at` |
| `reminder_merged` | Snooze/fixed-next pair gần nhau được gộp; loser row commit `MERGED` | envelope `reminder_occurrence_id` là loser; properties `kept_occurrence_id`, integer `distance_ms: 0..1_800_000`, `tie_break: earlier_due\|snooze_over_fixed`; loser `merged_into_occurrence_id=kept_occurrence_id` |
| `reminder_cancelled` | OS alarm/occurrence tương lai bị chủ động hủy | envelope `reminder_occurrence_id`; properties `reason: schedule_edit\|permission_revoked\|timezone_change\|safety_hold\|rest_only\|active_session\|pending_pain`, `resulting_status: CANCELLED\|BLOCKED_PERMISSION`; permission_revoked bắt buộc map `BLOCKED_PERMISSION`, reason khác map `CANCELLED`; full-delete keyless path không emit event này |
| `reminder_blocked_permission` | Receiver không thể post vì quyền notification không có | envelope `reminder_occurrence_id`; property `status: BLOCKED_PERMISSION` |
| `reminder_skipped` | Receiver không post occurrence vì guard runtime | envelope `reminder_occurrence_id`; properties `status: SKIPPED_LATE\|SKIPPED_WORK_END\|SKIPPED_SAFETY_HOLD\|SKIPPED_REST\|SKIPPED_SESSION_GUARD\|SKIPPED_NOT_SELECTED_WORKDAY`, `lateness_ms` |

Fixed/snooze chỉ hợp lệ khi target `< work_end`; receiver tại `now >= work_end` ghi `SKIPPED_WORK_END`, không post. Nếu `now-due_at > 60 phút`, ghi `SKIPPED_LATE`; đúng 60 phút chỉ post khi còn trước work end và guard khác hợp lệ.

Occurrence status chỉ nhận `SCHEDULED|DELIVERED|SNOOZED|MERGED|CANCELLED|BLOCKED_PERMISSION|SKIPPED_LATE|SKIPPED_WORK_END|SKIPPED_SAFETY_HOLD|SKIPPED_REST|SKIPPED_SESSION_GUARD|SKIPPED_NOT_SELECTED_WORKDAY`. `reminder_skipped` chỉ dùng status prefix `SKIPPED_`; `reminder_cancelled` là proactive cancellation và giữ reason, không thay thế skip ở receiver.

Nếu receiver tự phát hiện permission off trước reconcile, chỉ ghi `reminder_blocked_permission`; nếu reconcile đã chủ động cancel vì revoke, ghi `reminder_cancelled(reason=permission_revoked,resulting_status=BLOCKED_PERMISSION)` và không ghi event blocked thứ hai cho cùng transition.

ID codec exact: fixed UTF-8/ASCII preimage `fixed-v1|<lowercase UUID schedule>|<0-based slot decimal>|<YYYY-MM-DD>|fixed|<generation decimal>`; snooze preimage `snooze-v1|<lowercase UUID parent>|<ordinal decimal>`. Decimal không leading zero trừ `0`. SHA-256 → first 16 bytes → set byte-6 version nibble `8` và byte-8 RFC variant bits `10` → canonical lowercase hyphenated UUID.

`schedule_version_id` luôn required. Exact property-key union của `reminder_scheduled` là:

```text
fixed  = due_at quartet, kind, supersedes_occurrence_id,
         logical_fixed_key, generation, creation_reason
snooze = due_at quartet, kind, supersedes_occurrence_id,
         parent_occurrence_id, ordinal
```

`supersedes_occurrence_id` là key luôn present, value UUID hoặc JSON null; mọi branch-only key phải present+non-null đúng branch và **forbidden/absent** ở branch kia. Null placeholder cho forbidden key và omission của required key đều bị writer/importer reject trước JCS/idempotency. Fixed logical key exact `{schedule_version_id,slot_index,local_date,kind:fixed}`; generation 0 bắt buộc `creation_reason=initial`; generic reconcile với no prior row tạo initial, current `SCHEDULED` reuse row cũ, còn generation `max+1` chỉ dùng `slot_reeligible` khi latest fixed row là `CANCELLED|BLOCKED_PERMISSION`. Latest `MERGED|DELIVERED|SKIPPED_*` hoặc past slot không tạo row cho cùng key; `MERGED` không có restore branch. Mỗi non-initial fixed row có supersedes đúng predecessor; terminal row/event không trở lại pending. Snooze luôn ordinal 0 và supersedes null. `merged_into_occurrence_id` non-null iff status `MERGED`. Chỉ merge snooze với fixed kế tiếp chưa consume; fixed-fixed/snooze-snooze không merge.

`reminder_posted.kind` phải mirror occurrence. `reminder_snoozed.duration_minutes` chỉ nhận integer 15/30/60; event ordinal bằng child ordinal và `target_at` byte-equal child `due_at`. Với merge, compute checked integer `distance_ms=abs(loser.due_at.instant-kept.due_at.instant)` từ hai exact due instant; require `0<=distance_ms<=1_800_000` và event property byte-equal kết quả. Không chia phút, floor hoặc round: snooze target được phép giữ giây/millisecond của action time. Overflow, negative/coerce hoặc value/property ngoài range là contract error. Equality due cho `distance_ms=0` và tie-break `snooze_over_fixed`.

Entity occurrence là nguồn chuẩn cho final state/time: `due_at` luôn non-null; transition post atomically set `status=DELIVERED` + non-null `delivered_at`; callback open/delete đầu tiên set-if-null `first_opened_at`/`dismissed_at` cùng đúng một event. Occurrence chưa delivered bắt buộc cả ba nullable interaction stamp null; nếu open/dismiss cùng tồn tại thì đều không sớm hơn `delivered_at`. Callback retry/duplicate không rewrite first stamp hoặc tạo event thứ hai. Timestamp transition phụ khác chỉ ở event, không invent thêm entity field.

Snooze action giữ source notification row `DELIVERED`, tạo đúng một child row `SNOOZED` với parent ID + literal ordinal 0; event `target_at` và child `due_at` phải là cùng full LocalStamp byte-for-byte. One-shot registry/action set làm callback cùng source lần hai không reachable tới mutation và trả `SNOOZE_NOT_ELIGIBLE`; không replace pending child. Chỉ child transition sau đó. Nếu child được `DELIVERED`, notification mới của child có thể tạo grandchild ordinal 0 dưới parent mới. Fixed `MERGED` luôn consumed và không restore; bounded reconcile scan tiến tới selected date kế tiếp. Due khác giữ earlier, equality giữ snooze; same-kind pair bị validator từ chối và không có merge event. Mỗi actual transition phát đúng event; retry không tạo child/event trùng.

`REST_ONLY` hủy/skip occurrence còn lại đến effective expiry do clock-integrity resolver xác định. Fresh check-in supersede state: result có mode mới tạo lại fixed slot còn tương lai; Rest/safety result tiếp tục không nhắc. Occurrence mới có ID mới và không được tính là post bù.

`WorkScheduleVersion.enabled=false` không tạo occurrence mới và chủ động cancel occurrence tương lai với `reason=schedule_edit`; selected weekdays/window/times vẫn persist cho manual use và historical attribution. Bật lại tạo version mới và chỉ schedule fixed slot còn tương lai; OS permission off là state riêng, không tự đổi `enabled`.

Khi session bắt đầu hoặc guard chuyển sang pending pain, scheduler cancel occurrence tương lai với reason tương ứng `active_session`/`pending_pain`. Pain=no resolve guard phát `schedule_reconciled(reason=pain_resolved_no)` và chỉ tạo lại fixed slot còn ở tương lai; pain=yes dùng nhánh `safety_hold`, không reschedule trong ngày hold.

Không suy ra `dismissed` từ việc không mở; nếu platform không phát delete intent thì trạng thái là không biết, không phải dismissed.

### 4.6. Summary, export và delete

| Event | Khi ghi | Properties bắt buộc |
|---|---|---|
| `weekly_summary_generated` | Snapshot tuần được tính/refresh | `week_start_local_date`, `summary_id`, `qualified_break_days`, `completed_count` |
| `weekly_summary_viewed` | Màn hình summary hiển thị | `summary_id`, `week_start_local_date` |
| `export_started` | SAF callback đã trả destination URI hợp lệ; event commit ngay trước export work | `export_id`, `export_schema_version: 1` |
| `export_completed` | Một JSON đã write + flush + close thành công | `export_id`, `record_counts`, `byte_count` |
| `export_failed` | Export đã started nhưng không hoàn tất | `export_id`, `error_code: snapshot_read_failed\|json_encode_failed\|destination_open_failed\|destination_write_failed\|destination_flush_failed\|destination_close_failed\|provider_failed\|security_denied`; không property lỗi khác |

UI sinh random `export_id` trong RAM trước khi launch picker. Picker cancel hoặc callback không có URI thì discard ID, không `export_started`, terminal event hay denominator. Với destination hợp lệ, app commit `export_started` ngay trước work; nếu commit event thất bại thì abort trước khi mở/ghi destination và không bịa terminal row. `export_completed` chỉ thắng shared `export_terminal(export_id)` sau write, flush và close đều thành công.

`export_failed.error_code` dùng mapping v1 total, không log exception/URI/provider/path/message: local snapshot read/decrypt/validation fail → `snapshot_read_failed`; canonical JSON encoding fail → `json_encode_failed`. Với destination operation, exception security/permission luôn override thành `security_denied`; nếu không, provider-specific/remote provider failure override thành `provider_failed`; nếu không, map theo stage đang fail thành `destination_open_failed`, `destination_write_failed`, `destination_flush_failed` hoặc `destination_close_failed`. First primary failure thắng; cleanup-close failure chỉ trở thành `destination_close_failed` khi chưa có failure trước, không overwrite code trước đó. Retry cùng export ID không thể đổi completed↔failed hoặc error code nhờ shared terminal key.

Delete confirmation không phải product event. Sau confirm bước 2, UI gọi thẳng durable `DeletionMarkerV1(MARKED)` path; schema v1 không có `delete_all_started` hay `delete_all_completed`. Event DB/key có thể chính là dữ liệu corrupt cần reset nên không phải precondition, không có best-effort event được phép chặn xóa. QA xác minh bằng trạng thái app mới, marker convergence và absence của dữ liệu cũ, không bằng một event còn sót lại.

## 5. Metric dictionary

### MET-020 — North star: `qualified_break_days`

**Unit:** user-week, số nguyên 0–7.

**Formula:**

```text
count_distinct(session.local_date_at_start)
where session.status = completed
  and session.is_selected_workday_at_start = true
  and exists feedback for session
  and feedback.context_fit = yes
  and feedback.new_or_worse_pain = no
```

Một ngày chỉ cần một session thỏa điều kiện và chỉ được tính một lần. Session thiếu pain=no hoặc context=yes không qualify; `effort` không thuộc predicate và được phép null. Feedback điền muộn có thể làm recompute summary của tuần chứa `session.local_date_at_start`. Safety hold phát sinh sau một session khác không hồi tố loại session đã qualify.

Không có điều kiện “hai phiên/ngày”, “không snooze” hoặc “không tắt notification”.

Pilot dùng metric dẫn xuất có tên riêng, không đổi north star trong app:

```text
qualified_study_days_week_2 = count_distinct(study_day)
where 8 <= study_day <= 14
  and session.status = completed
  and session.is_selected_workday_at_start = true
  and feedback.context_fit = yes
  and feedback.new_or_worse_pain = no
```

`study_day` phải được phân loại từ start evidence theo `MET-027`. Nhiều local date trong cùng elapsed block chỉ tính 1; range luôn 0–7. In-app calendar-week `qualified_break_days` vẫn distinct `session.local_date_at_start`; không dùng/đổi tên field đó cho pilot. Primary report lấy median `qualified_study_days_week_2` trên participant có dataset hợp lệ, đồng thời báo distribution, range, `n` và số participant missing.

### MET-021 — Activation 24 giờ

**Definition:** participant có lần `routine_completed` đầu tiên trong half-open interval 24 elapsed hours neo tại `profile.onboarding_completed_at`; completion đúng mốc `+24h` không tính activation. Event `onboarding_completed` phải mirror 1:1 LocalStamp và activation tuple của profile; mismatch là data-quality failure, không chọn một phía tùy ý.

Classification dùng evidence, không dùng chênh UTC đơn thuần:

1. `completion_boot_marker=activation_boot_marker`, `completion_clock_generation=activation_clock_generation`, completion elapsed không nhỏ hơn anchor và `abs(completion_wall_minus_elapsed_ms - activation_wall_minus_elapsed_ms) <= 2_000`.
2. Nếu hợp lệ, `elapsed_delta_ms = completion_elapsed_realtime_ms - activation_elapsed_realtime_ms`; activated khi `0 <= elapsed_delta_ms < 86_400_000`.
3. Khác boot/generation, mapping drift `>2_000 ms`, elapsed rollback hoặc evidence thiếu/corrupt → `unknown_clock`; không tự coi activated/non-activated và không fallback sang app first-open/wall UTC. Mapping drift đúng `2_000 ms` vẫn hợp lệ.

- Numerator: số enrolled participants có evidence thỏa điều kiện.
- Denominator pilot: toàn bộ 24 participant đã consent và được cấp build; roster ngoài app chỉ xác định enrollment/unknown, không fabricate usage row.
- Báo count luôn; rate chỉ nếu denominator ≥5.

Export là tùy chọn: participant thiếu export/evidence được gắn `unknown`, không bị biến thành zero usage. Với enrollment-denominator gate, báo conservative lower bound = observed successes/24 và `unknown_count`; chỉ pass khi lower bound tự đạt ngưỡng. Nếu unknown có thể đổi kết luận thì `INSUFFICIENT_DATA`, không tự coi là fail/pass.

### MET-022 — Check-in funnel

| Metric | Numerator | Denominator |
|---|---|---|
| Check-in completion rate | distinct `check_in_flow_id` có `check_in_submitted` | distinct `check_in_flow_id` có `check_in_started` |
| Committed-result counts | count `decision_evaluated` theo result | Không dùng rate mặc định; runtime Blocked đếm qua `safety_screen_shown`; Incomplete chỉ đếm khi có committed CheckIn/Decision FK-valid |
| Reconfirmation completion rate | distinct flow `kind=reconfirm` có submitted | distinct flow `kind=reconfirm` có started |

Mỗi `check_in_flow_id` chỉ góp tối đa một lần cho từng vế. Flow started nhưng bỏ dở không có `check_in_id` vẫn ở denominator và không phải data-quality orphan. Mỗi submitted flow phải map đúng một canonical `check_in_id`; `URGENT_STOP` chặn sớm được coi là safety outcome hợp lệ, không phải form abandonment.

### MET-023 — Check-in và total time-to-routine

Ba duration tách biệt:

| Metric | Start | End | Vai trò |
|---|---|---|---|
| `check_in_duration` | `check_in_started` | `check_in_submitted` cùng `check_in_flow_id`; lấy submitted `duration_ms` | Acceptance median ≤`20_000 ms`, p90 ≤`30_000 ms` cho case routine-eligible. |
| `total_time_to_routine` | `check_in_started` | cùng flow → submitted actual `check_in_id` → Decision → `routine_started`/Session; lấy `total_duration_ms` | Acceptance chính p90 ≤`45_000 ms`. |
| `recommendation_to_start` | `check_in_submitted` | actual `check_in_id` → Decision → `routine_started`/Session | Secondary diagnostic; không thay total acceptance. |

`check_in_submitted` và `routine_started` phải map cùng flow→actual CheckIn→Decision→Session; routine event flow ID không được lấy từ một submitted CheckIn khác. Total timing dùng explicit XOR trên routine event, không trừ hai wall timestamps. Background cumulative từ flow start đúng `600_000 ms` vẫn eligible; `>600_000 ms` ghi/loại exact `background_over_10m`, kể cả phần background phát sinh sau submit. Ba invalid reason còn lại cũng bị loại và báo count riêng. Chỉ tính total/recommendation timing cho decision có mode và session bắt đầu; loại `REST_ONLY`, safety outcome, `INCOMPLETE` và start-gate block. `recommendation_to_start` dùng same-boot checked monotonic delta từ CheckIn confirmed evidence đến Session start evidence; discontinuity bị báo/loại, không wall fallback. Báo median/p90 với `n` theo aggregation v1; đây là usability metric, không phải sức khỏe.

### MET-024 — Routine funnel

| Metric | Numerator | Denominator |
|---|---|---|
| Routine starts | count distinct session được tạo `ACTIVE` | Count, không rate |
| Completion rate | session terminal `COMPLETED` | session từng `ACTIVE` |
| Stop count | session terminal `STOPPED` | Count |
| Abandon count | session terminal `ABANDONED` | Count |
| Step-skip count | tổng ordered unique `Session.player_checkpoint.skipped_steps` | Count; event phải mirror, không là source để dựng count |

Persisted lifecycle chỉ `ACTIVE|COMPLETED|STOPPED|ABANDONED`; nullable substate chỉ `PLAYING|PAUSED`, còn phase là `STEP_TIMER|STEP_TRANSITION|COMPLETION_CTA_WAIT`. Một session có tối đa một terminal state. Resume/recovery không tạo session/start mới và không tự completion.

Routine elapsed/duration là `accumulated_active_ms` monotonic chỉ consume `STEP_TIMER+PLAYING`, không phải wall/session age. Transition, pause/background, post-timer CTA wait và skipped planned dosage không góp thời lượng; terminal value bất biến. Repetitions dùng signed estimated time để auto-advance, không dùng số lần user tự báo.

### MET-025 — Feedback

| Metric | Numerator | Denominator |
|---|---|---|
| Pain-gate resolution rate | terminal session có pain yes/no | terminal sessions `completed\|stopped\|abandoned` |
| Completed-session feedback completion rate | completed session có đủ pain/effort/context | completed sessions |
| All-terminal feedback completion rate | terminal session có đủ pain/effort/context | terminal sessions |
| Context-fit rate chính | completed session có `context_fit=yes` | completed session có context yes/no |
| New/worse-pain rate | terminal feedback `new_or_worse_pain=yes` | terminal feedback có pain yes/no |
| Effort distribution | count/rate từng `easy\|moderate\|too_hard`, tách terminal state | terminal feedback có effort |
| Day-mode-cap count | count `day_mode_cap_updated` | Count, không rate mặc định |

Pending pain không được giả là `no` và luôn được báo `pending_pain_count`. Effort/context null là deferred optional state, báo `missing_optional_feedback_count`; không giả là `context_fit=yes`. Day cap được tính cho too_hard+pain=no ở mọi terminal state.

### MET-026 — Reminder engagement

| Metric | Numerator | Denominator |
|---|---|---|
| Prompt-open rate | distinct `reminder_opened` | distinct `reminder_posted` |
| Prompt-to-start rate | posted reminder có routine start được attribute | distinct `reminder_posted` |
| Snooze rate | posted source reminder có `reminder_snoozed` tham chiếu child | distinct source `reminder_posted`; source vẫn `DELIVERED` |
| Dismiss rate | posted reminder có delete-intent dismiss | posted reminder có trạng thái open/snooze/dismiss xác định |
| Initial prompt non-grant (Deny/Dismiss combined) count/rate | distinct `installation_id` có first `notification_permission_prompted(trigger=automatic_onboarding)` được pair cùng attempt với `prompt_result=not_granted` | distinct installation có first automatic attempt được pair cùng `prompt_result=granted\|not_granted` |
| Disabled-after-grant count/rate | distinct `installation_id` có chronologically valid `granted → denied` và event denied được observe qua `source=settings\|resume_check` | distinct `installation_id` từng có observed `state=granted` trước/equal report cutoff |
| Runtime reminder-skip count/rate | distinct occurrence có final status thuộc `SKIPPED_*` | distinct occurrence có final status `DELIVERED` hoặc `SKIPPED_*` |

Session/event chỉ persist `source=reminder` khi navigation context xuất phát từ successful tap, occurrence resolve với final status `DELIVERED`, `first_opened_at` non-null, và occurrence/active/CheckIn/Decision/Session có cùng `schedule_version_id`. Nếu không, transaction normalize `source=home`, `reminder_occurrence_id=null` mà không block authorization. Reminder A đã mở nhưng schedule/check-in đổi sang B vì vậy không được gắn vào Session B.

Attribution prompt-to-start chỉ hợp lệ khi persisted `routine_started.reminder_occurrence_id` khớp notification đã mở và start nằm trong 60 phút sau `first_opened_at`; cửa sổ này chỉ là metric, không đổi persisted source. Một session chỉ attribute cho một reminder. Reminder `MERGED`, `CANCELLED`, `SKIPPED_*`, chưa post hoặc permission bị chặn không vào denominator.

Hai permission metric dùng `installation_id` trong validated profile/event graph làm participant-dataset identity, giới hạn ở cohort/period và event có `occurred_at_utc <= report_cutoff`. Initial metric chỉ dùng first automatic **dialog** attempt và result event có cùng `attempt_id`; explicit retries không đổi initial classification. Settings-only path không có attempt/prompted, bị loại khỏi numerator/denominator và báo riêng `settings_only_path_count`. Automatic attempt PENDING/INTERRUPTED hoặc prompted event thiếu paired result, cùng `unavailable`, bị loại khỏi denominator và báo `prompt_unresolved`; báo riêng non-rate `prompt_not_granted`. Không suy Deny hay Dismiss riêng từ `not_granted`, không lấy resume/settings observation làm system result. Same-state Settings observation không tạo granted→denied transition. Full delete tạo installation ID mới nên không tự nối hai dataset; research roster chỉ được consolidate nếu protocol prespecify. `disabled-after-grant` yêu cầu thứ tự event hợp lệ, không suy từ state cuối hoặc event thiếu. Dataset thiếu history được báo unknown/missing, không giả denied, enabled hay denominator. Mọi count báo luôn; rate vẫn theo `MET-012` và chỉ tính khi denominator ≥5.

Runtime skip dùng final-state `reminders`, không đếm event retry. Numerator nhận đúng mọi `SKIPPED_LATE|SKIPPED_WORK_END|SKIPPED_SAFETY_HOLD|SKIPPED_REST|SKIPPED_SESSION_GUARD|SKIPPED_NOT_SELECTED_WORKDAY`; denominator chỉ union `DELIVERED` + các status đó. `MERGED|CANCELLED|BLOCKED_PERMISSION|SCHEDULED|SNOOZED` bị loại, không coi missing/permission block là skip.

### MET-027 — Study day và week-2 active

`study_day=1` neo tại activation elapsed anchor của `profile/onboarding_completed`, không phải app first-open hay raw UTC. Session và `routine_started` phải mirror exact `start_boot_marker`, `start_elapsed_realtime_ms`, `start_clock_generation`, `start_wall_minus_elapsed_ms`. Chỉ phân loại khi start/anchor cùng boot marker + clock generation, start elapsed không lùi và absolute mapping drift `<=2_000 ms`.

Khi hợp lệ, `elapsed_delta_ms=start_elapsed_realtime_ms-activation_elapsed_realtime_ms`; day `k` là half-open `[86_400_000×(k-1), 86_400_000×k)`. Day 1 bắt đầu đúng lúc onboarding commit, delayed onboarding không tiêu hao exposure; đúng mốc `14×24h` nằm ngoài day 14. Khác boot/generation, mapping drift `>2_000 ms`, rollback hoặc Session↔event mirror mismatch → `unknown_clock`, không fallback `occurred_at_utc`, timezone/local-date hoặc impute block.

Phân loại unknown dùng quy tắc bảo thủ theo participant, không được chỉ bỏ record lỗi. Activation anchor thiếu/sai làm **cả** study-week 1, study-week 2 và primary `qualified_study_days_week_2` của participant thành unknown. Nếu tồn tại bất kỳ in-scope `routine_started`/Session nào có start evidence thiếu, mismatch hoặc không thể xếp block, cả hai participant-week sample và primary week-2 value của participant cũng thành unknown vì record đó có thể thuộc một trong hai block và đổi kết quả. Ngược lại, record có evidence hợp lệ xếp rõ `elapsed_delta_ms <0` hoặc `>=14×86_400_000` nằm ngoài exposure và không làm nhiễm hai sample.

Participant là week-2 active nếu có ít nhất một `routine_started` hợp lệ trong study day 8–14. Một completed session dùng start evidence của chính session để xếp `qualified_study_days_week_2`, không dùng completion instant; nhiều session/local date trong cùng study-day block vẫn chỉ góp tối đa một đơn vị.

- Numerator: participant thỏa định nghĩa.
- Denominator: 24 enrolled participants.
- Báo count và rate nếu đủ ngưỡng mẫu.

Một Gate-3 sample là đúng một `(installation_id, study_week)` với `study_week=1` cho elapsed study day 1–7 hoặc `study_week=2` cho day 8–14. Sample chỉ active khi có ít nhất một `routine_started` với validated Session start evidence trong block đó. Completed count của sample là số distinct Session `status=COMPLETED` có **start evidence** hợp lệ nằm trong cùng block; terminal/completion instant không được reassign session sang tuần khác. Chỉ khi evidence hợp lệ xác định được block mới được loại/giữ riêng đúng participant-week; anchor invalid hoặc một in-scope start không thể phân block áp dụng conservative contamination rule phía trên cho **cả hai** tuần và primary week 2. Mọi unknown bị exclude khỏi aggregate, báo `unknown_clock`/data-quality count và không fallback UTC, completion date hay calendar week. Median Gate 3 dùng aggregation v1 và `n` là số valid active participant-weeks.

### MET-028 — Export success

- Numerator: distinct `export_id` có `export_completed`.
- Denominator: distinct `export_id` có `export_started` sau khi SAF trả về một destination hợp lệ.
- `export_id` sinh trong RAM trước picker nhưng hủy/no-URI phải discard, không failure/event/denominator. Started không terminal tại cutoff báo unresolved riêng; không tự coerce thành một error code.

### MET-029 — Safety monitoring counts

Báo counts riêng:

- decision `URGENT_STOP`;
- decision `PAUSE_TODAY`;
- màn `safety_screen_shown` có runtime result `BLOCKED_FOR_TODAY` (render count, báo riêng số hold distinct từ `safety_hold_created`);
- terminal feedback `new_or_worse_pain=yes`;
- pending pain gate chưa resolve;
- safety hold được tạo, tách theo exact kind và source;
- incident người dùng chủ động báo cho nhóm pilot ngoài app.

Không gộp các count này thành “injury rate” hoặc suy luận nguyên nhân. `new_or_worse_pain=yes` là safety signal tự báo, không tự động là adverse event y khoa.

### MET-030 — Nội dung tuần trong app

Summary local chỉ được dùng:

- counts hiển thị của `qualified_break_days`, session started/completed, từng lựa chọn feedback đã trả lời (`easy|moderate|too_hard`, pain yes/no, context yes/no) và reminder opened/snoozed/dismissed;
- completion/context-fit/pain rates khi mẫu số tương ứng ≥5.

`WeeklySummaryWireV1` có exact identity/week/stamp key `summary_id`, `week_start_local_date`, `week_zone_id`, `occurred_at_utc`, `local_date`, `zone_id`, `utc_offset_minutes`; exact 13 nonnegative-int64 count key `qualified_break_days`, `started_count`, `completed_count`, `effort_easy_count`, `effort_moderate_count`, `effort_too_hard_count`, `pain_yes_count`, `pain_no_count`, `context_yes_count`, `context_no_count`, `reminder_opened_count`, `reminder_snoozed_count`, `reminder_dismissed_count`; và exact ba object `completion_rate`, `context_fit_rate`, `new_or_worse_pain_rate`. Cấm key khác/alias.

Mỗi rate object có đúng `numerator`, `denominator`, `value_percent`, `suppression_reason`; hai count là nonnegative int64 và numerator không vượt denominator. Denominator `<5` iff value null + reason `insufficient_sample`; denominator `>=5` iff reason null + integer percent `floor((200*numerator+denominator)/(2*denominator))` bằng checked/BigInteger arithmetic. Completion dùng `completed_count/started_count`; hai rate còn lại dùng exact cohort tại ARC §11. `summary_id` random/stable, week start phải Monday; `week_zone_id` immutable từ lần tạo row và unique row theo week-start. `weekly_summary_generated|viewed` phải resolve ID/mirror week; generated mirror thêm `qualified_break_days`/`completed_count`. Importer recompute toàn count/rate từ retained raw graph khi source đủ; mismatch là data-quality failure, không trust summary cache.

Không hiển thị stopped/abandoned, pending pain hoặc missing optional feedback trên weekly UI; các state này chỉ nằm trong safety monitoring, funnel/data-quality và export local.

Không tính/hiển thị correlation giữa energy/stiffness/intent, routine, reminder time và feedback. Không có “thời điểm tốt nhất”, “bài cải thiện X”, AI summary hoặc recommendation từ lịch sử.

## 6. Export schema cho analytics/pilot

### MET-040 — Một JSON UTF-8

Export tạo đúng một file JSON UTF-8 qua SAF, không ZIP/CSV, có cấu trúc root:

```json
{
  "metadata": {
    "export_schema_version": 1,
    "exported_at_utc": "2026-08-27T08:00:00.000Z",
    "app_version": "1.0.0",
    "content_version": "1.0.0",
    "rule_version": 1,
    "retention_policy_version": 1,
    "record_counts": {
      "profile": 0,
      "work_schedule": 0,
      "check_ins": 0,
      "decisions": 0,
      "sessions": 0,
      "feedback": 0,
      "reminders": 0,
      "events": 0,
      "weekly_summaries": 0
    }
  },
  "profile": [],
  "work_schedule": [],
  "check_ins": [],
  "decisions": [],
  "sessions": [],
  "feedback": [],
  "reminders": [],
  "events": [],
  "weekly_summaries": []
}
```

Root có đúng `metadata` object và chín collection array; `profile` có tối đa một record. Export dùng snapshot transaction nhất quán; `metadata.record_counts` phải có count cho đủ chín array và khớp số phần tử. Mọi instant-valued leaf dùng exact `InstantWireV1` UTC-millisecond `YYYY-MM-DDTHH:mm:ss.SSSZ`; enum/ID giữ nguyên contract, không dịch sang display copy. Exact six-entity key/type/nullability/branch registry và top-level/semantic array order lấy duy nhất từ ARC §9.2; phần này không được nới bằng prose.

`metadata.content_version` là catalog `manifestVersion` SemVer của build tạo export; không dùng integer, display label hoặc app version thay thế. Đây không phải routine revision và không được dùng để ghi đè content identity lịch sử của session.

Ví dụ phần content identity bắt buộc trong mỗi record `sessions`:

```json
{
  "routine_id": "REC-01",
  "content_identity": {
    "schema_version": "1.0.0",
    "content_version": "1.0.0",
    "routine_revision": "1.0.0",
    "manifest_digest_sha256": "0000000000000000000000000000000000000000000000000000000000000000"
  }
}
```

Digest trên chỉ là placeholder đúng shape; validator không được coi giá trị ví dụ là digest phát hành.

Không thêm collection thứ mười cho state vận hành. Mapping bắt buộc:

- `profile` chứa exact `ProfileWireV1`: encrypted random-local `installation_id`, literal `adult_confirmed=true`, `eligibility_scope_confirmed=true`, `locale=vi-VN`, `onboarding_completed_at` full LocalStamp cùng four-field activation evidence, nonempty immutable `safety_acknowledgements[]` và current pointer bằng append-last ID. Mọi acknowledgement có exact ID, `kind=onboarding|reack`, version/digest/full LocalStamp; first là onboarding, phần còn lại reack. Mọi exported event phải có cùng installation ID; false/alias/extra/default/sai pointer-order bị reject; re-ack không sửa activation anchor; `profile=[]` chỉ khi user-data/event graph rỗng;
- `work_schedule` giữ exact ASCII `work_start`, `work_end`, sorted-distinct `reminder_times[]` theo codec `HH:mm` ở §4.2; mọi `work_schedule_saved` mirror byte-exact start/end của version nguồn, không coerce time string;
- `check_ins` giữ required integer `rule_version=1`, `answers_kind=red_flag_stop|acute_stop|full`, non-null `schedule_version_id`, đúng một named LocalStamp `confirmed_at` và exact freshness evidence `confirmed_boot_marker`, `confirmed_elapsed_realtime_ms`, `ttl_monotonic_deadline_ms`, `confirmed_clock_generation`, `confirmed_zone_id`, `confirmed_wall_minus_elapsed_ms`; không có `submitted_at`. `check_in_submitted` envelope quartet phải byte-equal `confirmed_at`; `decisions` giữ byte-equal evidence + cùng schedule ID và audit-only `reconfirm_after`/`valid_until_work_end`. Red/acute branch chỉ null field chưa hỏi, Full branch đủ năm input; Session schedule ID phải bằng Decision/CheckIn nguồn;
- `decisions` có exact nullable `created_safety_hold_snapshot`, `created_rest_suppression_snapshot`, `evaluation_day_mode_cap_snapshot`. Đây là deep copy tại transaction evaluate: urgent/pause có đúng created hold, rest có đúng suppression, và reason `SAF_DAY_MODE_CAP_APPLIED` có evaluation cap; Decision không được mutate sau commit;
- `sessions` chứa signed `routine_mode`, `decision_effective_mode_at_start`, `runtime_effective_mode_at_start`, nullable exact `runtime_day_mode_cap_snapshot_at_start`, terminal/pain/content/time evidence. Runtime value là authorization ceiling/cap basis; selected routine may be lighter. Player checkpoint giữ exact nullable `substate`, discriminated `phase`/remaining matrix, step index, accumulated active counter, ordered unique `skipped_steps[{step_id,active_elapsed_ms}]`, segment/checkpoint clock fields, cadence ordinal và content identity; terminal export giữ frozen values. `routine_started` mirrors runtime value + start evidence, `routine_step_skipped` mirrors appended skip record, `routine_completed` mirrors completion evidence/counter/skip-list size;
- `feedback` được derive từ session payload và keyed bằng `session_id`; ngoài pain/effort/context nullable, nó giữ nullable full-LocalStamp `pain_answered_at`, non-null full-LocalStamp `updated_at`, nullable `created_post_session_safety_hold_snapshot` và `day_mode_cap_update_snapshot`. Pending record lấy `updated_at` tại terminal commit; mỗi pain/optional-feedback commit cập nhật field này; không có `submitted_at`. Cap-update snapshot gồm invocation `trigger_session_id`, `expiry_source_session_id`, `basis_mode`, nullable `previous_max_mode`, full `resulting_cap` có `mode_trigger_session_id`, `deadline_source=existing_later|candidate_later|same`; nguồn stamp/expiry/evidence là adopted expiry source, còn mode trigger theo strict-lower/deadline-only rule ở §4.4;
- `reminders` giữ từng immutable occurrence generation/ordinal, logical/parent/supersedes/merged-into refs, non-null full-LocalStamp `due_at`, nullable full-LocalStamp `delivered_at`, `first_opened_at`, `dismissed_at` và terminal history; export không collapse theo logical slot hoặc đổi source DELIVERED thành SNOOZED;
- mỗi `weekly_summaries` record là exact `WeeklySummaryWireV1` tại `MET-030`, gồm stable `summary_id`, 13 count, ba typed rate, immutable week identity và full flat last-computed LocalStamp; không dùng week start/zone để suy timestamp tính gần nhất. `weekly_summary_generated` vẫn là event riêng và mirror/ref row;
- `weekly_summaries[].qualified_break_days` chỉ là calendar-week metric trong app. `qualified_study_days_week_2` không phải alias/export field của summary này; importer pilot phải derive từ `sessions` + `feedback` + validated start evidence để tránh trộn hai time unit;
- `session_guard`, encrypted `NotificationPromptAttemptV1` và daily-constraint table không export trực tiếp. Prompt-attempt audit vẫn có qua allowlisted events; purge/supersede enforcement row không được null/rewrite snapshot audit hoặc buộc exporter tái dựng từ state hiện hành.

Mỗi nested `SafetyHold`, `DayModeCap`, `RestDaySuppression` snapshot phải giữ `rule_version=1`, full LocalStamp `occurred_at_utc`, `local_date`, `zone_id`, `utc_offset_minutes`, immutable `expires_at_utc` và đúng five-field clock evidence `origin_boot_marker`, `created_elapsed_realtime_ms`, `monotonic_deadline_ms`, `remaining_elapsed_ms_at_last_checkpoint`, `original_duration_ms`. Hold giữ `kind+source_type+source_id`; cap giữ `max_mode+mode_trigger_session_id+source_session_id`, trong đó `source_session_id` chỉ là expiry-source; rest giữ `source_decision_id`. Importer từ chối field thiếu/sai thay vì suy từ timezone/current constraint.

Trước SAF, UI cảnh báo file là plaintext ngoài vùng app và có thể chứa dữ liệu tự đánh giá nhạy cảm. App không tự share/upload. Export hỏng/gián đoạn không để lại file được coi là hoàn tất và không thay đổi dữ liệu gốc.

### MET-041 — Validation file

Importer/research script phải:

1. parse duplicate-safe root/metadata discriminator rồi từ chối version không hỗ trợ; với version `1`, reject mọi missing/extra/alias key theo closed registry trước binding. Chỉ explicit registered upcaster cho version mới được dùng; không ignore unknown/default enum;
2. validate required array/enum và content identity: ba version phải là SemVer, `manifest_digest_sha256` là 64 lowercase hex; dùng approved catalog artifact local keyed bởi `(schema_version, content_version, manifest_digest_sha256)` để verify `routine_id`/`routine_revision`, không suy từ metadata/current catalog và không tải artifact qua mạng;
3. dedupe theo ID;
4. recompute exact `MET-013` logical ref-set cho từng exported event: mọi installation/envelope/additional target resolve đúng type trong chín arrays, conventional ID không duplicate property, conditional source đúng, mandated-equality target dedupe và non-entity ID không sinh edge. Reconstruct closed required-companion role/cardinality từ source graph + event payload: onboarding/ack, check-in/decision+side-effect, session start/skip/terminal/pain/feedback+side-effect, reminder full lifecycle và weekly generated; missing/thừa/mismatch bị reject. Internal ref tables/retention authority không export; app-side DB validator riêng phải đối chiếu physical ordinary/companion rows, directed propagation và nullable-prefilter mirror. Sau đó xác minh schedule graph qua CheckIn→Decision→Session; Session `source=reminder` phải resolve occurrence `DELIVERED`, có `first_opened_at` và cùng schedule ID, Home phải null;
5. validate CheckIn có exact integer `rule_version=1`, chỉ có `confirmed_at`, không `submitted_at`, và `check_in_submitted` envelope quartet byte-equal stamp này; validate CheckIn↔Decision freshness evidence byte-equal và audit-deadline shape; validate profile activation anchor ↔ `onboarding_completed`, Session start ↔ `routine_started`, và completion ↔ `routine_completed` mirror; áp same boot/generation/non-rollback/mapping-drift rule cho activation/study-day, mismatch hoặc discontinuity là data-quality error/`unknown_clock`, không fallback wall time;
6. validate invariant nullable của ba Decision snapshot, runtime-cap-at-start và hai feedback reducer snapshot; xác minh source FK, full LocalStamp/expiry/five-field clock evidence, adopted expiry source và Decision bất biến; không reconstruct từ event/daily constraint;
7. validate acknowledgement history/current pointer, mapping version/digest và invariant re-ack không đổi activation anchor; validate discriminated CheckIn shape và từ chối persisted `INCOMPLETE` ngoài Full + authenticated/decode-success inner cap enum/shape invalid với `[day_mode_cap]`; auth/envelope failure phải không có Decision event;
8. validate schedule-time codec byte-exact (`HH:mm`, zero second/nano, sorted-distinct reminder list, event/export mirror), rồi reminder ID derivation byte-exact theo fixed/snooze UUIDv8 codec (lowercase UUID, slot 0-based, decimal canonical), strict fixed-vs-snooze absent/null matrix, monotonic fixed generation, literal snooze ordinal 0 + max-one-child/source, source-child/supersedes/merged-into refs, exact timestamp transition, max-one-pending invariant và terminal immutability;
9. validate exact player checkpoint phase/remaining/substate matrix, signed duration/repetitions/transition arithmetic, step index/content identity và ordered unique skip records. Mỗi skip record phải đúng catalog order/range và có đúng một event mirror cùng `step_id+active_elapsed_ms`; event mồ côi/mismatch bị reject, còn `routine_completed.step_skip_count` phải bằng persisted list size;
10. yêu cầu mỗi weekly summary khớp exact `WeeklySummaryWireV1`: ID/event ref, Monday/immutable zone, 13 count, ba rate/null-reason/rounding invariant và full last-computed LocalStamp tách khỏi week identity; recompute được thì cache phải match raw graph;
11. tạo data-quality report trước metric report;
12. không gọi mạng hoặc enrich bằng nguồn ngoài.

## 7. Pilot feasibility 14 ngày

### MET-050 — Thiết kế

- Thiết kế: single-arm feasibility pilot.
- Cỡ mẫu mục tiêu: `n=24` enrolled participants.
- Thời lượng sử dụng: 14 ngày/participant.
- Tất cả participant nhận cùng Android MVP, cùng sáu routine, decision table, reminder và copy đã duyệt.
- Không chia A/B, không có control arm, không so “adaptive” với “fixed”.
- Không chứng minh efficacy, phòng bệnh, giảm đau, giảm ngồi hoặc causal effect.

### MET-050A — Pre-enrollment research/ethics gate

Trước recruitment, Research Lead phải phối hợp owner privacy/legal/ethics có thẩm quyền để lập văn bản xác định pilot có phải health-related human-subject research theo policy/jurisdiction áp dụng hay không. Nhóm sản phẩm không được tự tuyên bố “exempt”.

Evidence bắt buộc:

- determination memo gắn protocol version;
- ethics/IRB-equivalent approval, documented exemption, hoặc documented non-research determination do bên có thẩm quyền đưa ra, tùy kết quả phân loại;
- informed-consent form;
- quy trình withdrawal và yêu cầu xóa dữ liệu đã chia sẻ;
- adverse-event intake/escalation/stop protocol;
- data-management plan, owner và retention period.

Thiếu evidence, hết hạn, sai protocol/build/content digest hoặc còn điều kiện chưa đóng → `PILOT-GATE-ETHICS` fail; không được tuyển, consent hay thu pilot data. Release owner lưu ID/ngày/owner của evidence trong pilot readiness record. Gate này không chứng nhận app/pilot “an toàn” và việc không có incident cũng không chứng minh safety.

### MET-051 — Eligibility pilot

Inclusion:

- từ đủ 18 tuổi;
- có Android tương thích và đọc được `vi-VN`;
- làm việc máy tính và chọn ít nhất 3 ngày làm việc/tuần trong app;
- đồng ý dùng build local-only 14 ngày và hiểu export là tùy chọn;
- tự có khả năng dừng routine khi không thoải mái.

Exclusion:

- đang cần rehab/hậu phẫu, thai kỳ/hậu sản cần cá nhân hóa, phục hồi tim phổi hoặc quản lý bệnh mạn bằng chương trình chuyên môn;
- đang được chuyên gia y tế giới hạn vận động theo cách không phù hợp với app;
- có bệnh cấp tính, đau/chấn thương mới/tăng hoặc red flag tại enrollment;
- dưới 18 tuổi hoặc không thể consent.

Eligibility là ranh giới nghiên cứu sản phẩm, không phải sàng lọc/chẩn đoán y khoa. Trường hợp không đủ điều kiện được safe-exit, không thu raw health detail ngoài câu trả lời cần thiết.

### MET-052 — Lịch pilot

| Mốc | Hoạt động |
|---|---|
| Trước ngày 1 | Consent, cấp build, giải thích general wellness và emergency guidance, gán mã participant ngoài app. |
| Ngày 1 | Moderated onboarding/usability; người dùng tự đặt workdays, work window và 1–2 giờ nhắc. |
| Ngày 1–14 | Dùng app bình thường; không có can thiệp/đổi arm. |
| Ngày 7 ±1 | Phỏng vấn ngắn về friction/copy; export giữa kỳ chỉ khi participant chủ động đồng ý. |
| Ngày 15 ±2 | Phỏng vấn kết thúc và tùy chọn export file JSON cuối kỳ. |

Nhóm pilot không xem dữ liệu live. Không nhận file không có consent. Người dùng có thể tiếp tục dùng app, dừng pilot, từ chối export hoặc xóa local bất cứ lúc nào mà không bị phạt.

### MET-053 — Primary và secondary outcomes

Primary feasibility outcome:

- median `qualified_study_days_week_2` (distinct valid study day 8–14 thỏa predicate tại `MET-020`, max 7), kèm distribution, range và `n` participant có dữ liệu hợp lệ.

Secondary:

- activation 24 giờ và week-2 active;
- check-in completion và check-in-to-start;
- routine completion;
- feedback completion/context fit/effort/pain signal;
- prompt-open/prompt-to-start/snooze/dismiss, initial prompt non-grant (Deny/Dismiss combined), disabled-after-grant và runtime reminder skip;
- counts theo domain result và day mode cap;
- export completion;
- task success và qualitative friction từ interview.

Không thực hiện hypothesis test về sức khỏe hoặc causal difference. Với n=24, report tập trung counts, denominators, median/range và missingness.

### MET-054 — Feasibility gates

Pilot đủ bằng chứng để tiếp tục iterate khi không chạm safety stop và đạt tất cả:

1. Activation: ít nhất 17/24 participant hoàn thành routine đầu trong 24 elapsed hours sau `onboarding_completed`.
2. Week 2 active: ít nhất 12/24 participant có ≥1 `routine_started` với valid start evidence trong study day 8–14.
3. Usage: trên exact active `(installation_id, study_week)` samples tại `MET-027`, median distinct completed Session theo validated **start-evidence block** ≥4/tuần; terminal/completion time không reassign, aggregation v1 và `n` valid active participant-weeks phải ≥5.
4. Context fit: theo `MET-025`, numerator là completed sessions có `context_fit=yes`; denominator là completed sessions có context `yes|no`, loại stopped/abandoned và null; rate ≥70%, denominator ≥5 và phải báo count.
5. Feedback capture: ≥80% completed sessions có đủ feedback, với denominator ≥5.
6. Export: ≥90% export attempts có destination hợp lệ hoàn tất, với denominator ≥5 trong các participant đồng ý export.

Gate 4–6 không dùng `value_percent` đã round. Với nonnegative integer `numerator<=denominator`, gate có denominator `<5` là `INSUFFICIENT_DATA`; khi denominator `>=5`, so exact bằng checked/BigInteger cross-multiplication, equality PASS:

```text
Gate 4 PASS iff 100 * context_yes       >= 70 * context_answered
Gate 5 PASS iff 100 * feedback_complete >= 80 * completed_sessions
Gate 6 PASS iff 100 * export_completed  >= 90 * valid_destination_attempts
```

Nếu inequality false thì gate là `FAIL`. Không division binary floating-point, không half-up/floor/ceil trước so sánh; display vẫn dùng integer half-up tại `MET-011`. Ví dụ `16/23` hiển thị `70%` nhưng Gate 4 FAIL vì `1600<1610`; `7/10`, `8/10`, `9/10` lần lượt chạm equality và PASS cho Gate 4/5/6.

Median `qualified_study_days_week_2` là primary descriptive feasibility outcome. Pilot đầu không đặt efficacy/health threshold cho metric này; distribution/range/`n` được dùng để chọn target prespecified cho pilot kế tiếp mà không post-hoc tuyên bố thành công.

Gate 1–2 dùng denominator enrollment exact `24`; missing export/evidence là `unknown`, không phải zero. Với `s=observed_successes`, `u=unknown_count`, `0<=s+u<=24` và threshold `T` (`17` cho Activation, `12` cho Week-2 active), classifier total là:

| Điều kiện | Kết quả gate |
|---|---|
| `s >= T` | `PASS` — lower bound tự đạt, bất kể unknown |
| `s < T` và `s + u < T` | `FAIL` — ngay cả upper bound cũng không đạt |
| `s < T` và `s + u >= T` | `INSUFFICIENT_DATA` — unknown có thể đổi kết luận |

Ba nhánh mutually exclusive/exhaustive; equality theo đúng công thức, không coerce unknown thành fail/pass.

Gate 3 dùng classifier total sau. Gọi `K` là multiset completed-count của mọi participant-week **valid active** tại `MET-027`; valid inactive đã được xác định chắc chắn thì không thuộc `K`. Gọi `u` là số participant-week unknown sau conservative contamination. Predicate trên một multiset `S` là `usage_pass(S) := (|S| >= 5) && (aggregation_v1_median(S) >= 4)`. Mỗi unknown có thể thật sự inactive, hoặc active với completed-count là một integer bất kỳ `>=0`; không được tự chọn một giả định duy nhất.

Classifier production duyệt `a=0..u`, trong đó `a` là số unknown giả định active, và tạo đúng hai bound:

```text
low(a)  = K multiset-union a copies of 0
high(a) = K multiset-union a copies of 8

PASS              iff usage_pass(low(a))  với mọi a trong 0..u
FAIL              iff usage_pass(high(a)) là false với mọi a trong 0..u
INSUFFICIENT_DATA  otherwise
```

`8` là upper sentinel bắt buộc, không phải `4`: median-v1 của mẫu chẵn dùng midpoint nên cặp middle `0,8` vừa chạm threshold `4`. Với completed-count không âm, median monotonic theo từng phần tử; mọi giá trị unknown `>8` có thể clamp về `8` mà không đổi truth của `median>=4`, còn với một tập unknown-active cố định thì all-zero/all-eight là hai extrema. Duyệt `a` đồng thời bao phủ mọi lựa chọn inactive; vì identity của unknown không ảnh hưởng multiset, thuật toán chỉ có `u+1` cặp bound, không enumerate `3^u`. Hai nhánh PASS/FAIL loại trừ nhau; còn lại chính xác là trường hợp có hai admissible assignment cho kết luận khác nhau. Mỗi result phải xuất `known_active_n=|K|`, `unknown_week_n=u` và cho từng `a` các tuple `{a,low_n,low_median_or_null,low_pass,high_n,high_median_or_null,high_pass}` để audit; median null khi `n<5` theo `MET-012`.

Primary week-2 median loại participant có primary value unknown và không có ít nhất 5 participant hợp lệ thì là `INSUFFICIENT_DATA`.

### MET-055 — Safety stop và review

Pilot phải tạm dừng enrollment/content liên quan khi có bất kỳ incident nghiêm trọng nào được participant báo và có khả năng liên quan routine, gồm nhu cầu trợ giúp y tế khẩn cấp, nhập viện hoặc suy giảm chức năng đáng kể. Đây là trigger review, không phải kết luận nguyên nhân.

Không có incident được báo trong n=24/14 ngày không chứng minh sản phẩm hoặc routine an toàn; chỉ được ghi `không ghi nhận incident trong phạm vi quan sát/nguồn dữ liệu này` cùng missingness.

Ngoài app, consent sheet phải có contact nghiên cứu và emergency guidance. App không theo dõi incident live. `new_or_worse_pain=yes` kích hoạt safety hold trên thiết bị; research team chỉ biết nếu participant tự báo hoặc tự nguyện export/phỏng vấn.

Sau trigger, product owner, safety/content reviewer và research lead phải:

1. dừng routine/content liên quan trong pilot;
2. bảo toàn evidence đã được participant đồng ý chia sẻ;
3. đánh giá scope/copy/content trước khi resume;
4. không ghi incident là “do app” hoặc “không do app” khi chưa có review phù hợp.

### MET-056 — Quy tắc quyết định

Decision reducer dùng exact first-match precedence:

1. Có trigger `MET-055` → `STOP_FOR_SAFETY_REVIEW`, bất kể metric khác.
2. Không safety stop nhưng bất kỳ Gate 1–3 classifier là `INSUFFICIENT_DATA`, rate Gate 4–6 có denominator `<5`, hoặc primary không có ít nhất 5 participant hợp lệ → `INSUFFICIENT_DATA`.
3. Không rơi hai nhánh trên và cả sáu gate `PASS` → `CONTINUE`.
4. Còn lại ít nhất một gate `FAIL` → `ITERATE_AND_RETEST`.

`ITERATE_AND_RETEST` chỉ áp cho failure đã xác định, không dùng thay unknown. `CONTINUE` chỉ cho phép tiếp tục usability/content iteration; `STOP_FOR_SAFETY_REVIEW` cấm release/pilot nội dung liên quan trước sign-off. Mọi kết quả phải báo `s/u/known-failure` hoặc numerator/denominator cần thiết, không chỉ báo nhãn.

Không có nhánh quyết định “adaptive tốt hơn fixed” vì pilot không có đối chứng.

## 8. Privacy và quản trị pilot

### MET-060 — Consent

Consent phải tách:

1. đồng ý tham gia usability/feasibility pilot;
2. đồng ý export/chia sẻ file local tại từng lần;
3. đồng ý trích dẫn phản hồi chỉ sau bước de-identification có evidence, nếu cần.

Từ chối mục 2 hoặc 3 không làm mất quyền dùng app/pilot. App không lưu research-consent signature; eligibility/safety scope acknowledgement là record riêng và không phải research consent. Research team quản lý consent ngoài app.

### MET-061 — Pseudonymization và khả năng nhận diện

App có random-local `installation_id` và UUID record; không dùng hardware/advertising identifier. Mã participant và thông tin liên hệ nằm trong roster riêng, không nhập vào app. Tuy nhiên raw export chứa `installation_id`, timestamp chính xác, lịch làm việc và self-report nên là dữ liệu nhạy cảm pseudonymous, có thể có khả năng nhận diện; không được gọi là anonymous/de-identified chỉ vì thiếu tên/email. Consent và data-management plan phải mô tả đúng rủi ro này.

Researcher chỉ gắn mã participant sau khi nhận file theo consent; roster mapping được lưu tách biệt, access-controlled. Dataset/report dùng ngoài phân tích nội bộ phải qua bước de-identification/aggregation được lập hồ sơ, gồm loại hoặc generalize timestamp/schedule/ID không cần thiết và đánh giá nguy cơ tái nhận diện.

### MET-062 — Lưu giữ ngoài app

Trước recruitment, protocol/consent phải nêu nơi lưu, người có quyền truy cập và thời hạn xóa file export. Baseline cho pilot này: raw pseudonymous export được access-control theo DMP; chỉ file đã qua bước de-identification có evidence theo `MET-061` mới được gọi là de-identified và giữ tối đa 90 ngày sau khi báo cáo pilot chốt. Roster mapping xóa sớm nhất có thể và lưu tách biệt. Yêu cầu pháp lý/nội bộ nghiêm hơn được ưu tiên.

Xóa dữ liệu trong app không thể xóa bản export đã rời app; UX và consent phải nói rõ. Participant có thể yêu cầu research team xóa bản họ đã chia sẻ theo contact trong consent, trong giới hạn nghĩa vụ đã công bố.

### MET-063 — Reporting

- Báo mọi count cùng period/cohort.
- Báo mọi rate cùng numerator/denominator và suppress nếu denominator <5.
- Báo missing/invalid data riêng, không impute.
- Không publish raw row hoặc quote có thể nhận diện.
- Không dùng “cải thiện”, “giảm”, “gây ra”, “phát hiện” cho outcome sức khỏe.
- Kết luận chỉ ở mức feasibility/usability của build và protocol này.

## 9. Data quality và test plan

### MET-070 — Invariants

- Mỗi `session_id` có đúng một `routine_started`, `decision_id` non-null/FK-valid, lifecycle bắt đầu `ACTIVE` và tối đa một terminal `COMPLETED|STOPPED|ABANDONED`; pause/resume không đổi lifecycle.
- Mỗi CheckIn/Decision có cùng non-null `schedule_version_id`; Session copy đúng ID này và ID phải là active schedule tại transaction start. Mismatch chỉ tạo reconfirm/start-block event `schedule_changed`, không Session.
- Mỗi `check_in_flow_id` có tối đa một started và một submitted event; submitted bắt buộc có đúng một canonical CheckIn FK-valid, envelope stamp bằng `confirmed_at`, còn started-only abandoned flow không có `check_in_id` và không bị báo orphan. CheckIn/export có `submitted_at` hoặc stamp mismatch phải fail validation. Timing chỉ join flow→submitted CheckIn→Decision→Session, không đoán từ timestamp hoặc reuse entity ID làm flow ID.
- Mỗi valid completed flow có exact duration/reason XOR; `routine_started.check_in_flow_id` phải resolve submitted CheckIn nguồn của its Decision và có exact total-duration/reason XOR. Mọi raw `*_ms` là non-negative int64; `age_ms` chỉ monotonic same-boot hoặc null. Không wall fallback.
- Session `source=reminder` bắt buộc có validated navigation context, resolvable occurrence `DELIVERED`, non-null `first_opened_at` và cùng `schedule_version_id`; `source=home` bắt buộc ID null. Điều kiện attribution sai phải normalize Home trước commit, không block start; không infer attribution từ thời gian gần nhau nếu ID thiếu.
- Mỗi immutable ReminderOccurrence generation/child mới có đúng một `reminder_scheduled`; initial/slot-reeligible insert là row+event/ref/companion/retention atomic, reuse pending row emit zero. Mỗi accepted one-shot snooze bundle có đúng một child ordinal 0 + `reminder_snoozed`; callback cùng source lần hai tạo zero mutation. Nếu pair có loser thì có đúng companion merge event. Full post-pair pending-set—not singular winner—là platform scheduling authority. Shared delivery lease serializes receiver với edit/permission/hold/rest/session/pain/snooze; pending/delivered/terminal registry live-set theo §10.1 tài liệu 06. Terminal row không nhận cancel lặp, fixed MERGED không restore và generic reconcile không backfill event thiếu.
- `routine_completed` phải tham chiếu routine ID canonical và decision có effective mode cho phép routine đó.
- Mỗi feedback tham chiếu một terminal session; exact mapping `PENDING↔pain null`, `RESOLVED_NO↔pain no`, `RESOLVED_HOLD↔pain yes` và hold snapshot; effort/context nullable.
- Optional effort/context chỉ transition null→value độc lập; context-only có thể qualify, effort-only không qualify khi context null; too_hard cap side effect atomically đi cùng effort commit.
- `feedback_updated` mirror terminal state + post-commit effort/context; `feedback_complete` đúng iff pain/effort/context entity đều non-null. Recommendation/selection/mode enums và null/order matrix phải đúng signed routine/Decision/Session.
- Mọi terminal session phải có canonical pain-gate state: completed/abandoned tạo `PENDING`; explicit stopped dùng answer đã thu và atomically kết thúc ở `RESOLVED_NO|RESOLVED_HOLD`. Không có terminal state thiếu pain gate; pending guard chặn session mới và sống qua crash/reboot.
- Session `ACTIVE` hoặc pain gate `PENDING` giữ Decision+CheckIn+ScheduleVersion nguồn khỏi retention; purge sau resolve phải giữ đúng dependency order, không tạo orphan.
- Mọi retained event/constraint/audit snapshot giữ source graph ít nhất đến authority; late feedback/cap/post-session hold kéo dài Session+Decision+CheckIn+ScheduleVersion, check-in hold/rest kéo dài Decision+CheckIn+ScheduleVersion. Source đồng thời giữ closed required companions, gồm exact side-effect event; directed queue không reverse ordinary AppProfile ref. ReminderOccurrence giữ ít nhất 90 ngày/đến authority cuối của event, companion hoặc Session source=reminder và giữ ScheduleVersion nguồn. Day90/91, weekly 91 ngày và late-feedback extension không được để source thiếu mirror.
- `safety_hold_created` tồn tại cho red flag, từng acute issue và terminal pain=yes, với đúng kind/source/origin timezone/absolute expiry.
- `day_mode_cap_updated` có thể xuất phát từ mọi terminal feedback too_hard+pain=no; cap không tăng, basis đúng active cap hoặc Session runtime-effective-at-start, không selected routine mode.
- Decision side-effect snapshot không đổi sau commit; runtime cap mới chỉ nằm trong Session snapshot, còn post-session hold/cap update nằm trong feedback reducer snapshot và vẫn export được sau khi daily constraint bị purge.
- Late pain=yes tạo hold theo answer-day date/zone. Optional effort `too_hard` commit sau pain=no tại/sau session-origin expiry chỉ persist answer/timestamp, giữ `day_mode_cap_update_snapshot=null` và ghi event-only `feedback_updated.cap_result=origin_day_expired`; không có cap state/event ngày mới.
- `BLOCKED_FOR_TODAY` ưu tiên mọi check-in trong ngày có hold.
- Persisted `INCOMPLETE` chỉ có Full CheckIn hợp lệ + authenticated/decode-success inner cap enum/shape invalid + `[day_mode_cap]`; invalid/missing draft không persist, restored/migrated shape hoặc daily-constraint auth/envelope sai là contract error trước engine. Mọi `INCOMPLETE` không có recommendation/session.
- `REST_ONLY` tạo suppression/cancel reminder; fresh mode check-in clear và reschedule đúng số fixed slot tương lai đã insert, fresh Rest thay suppression, fresh urgent/pause supersede bằng hold; lỗi/Incomplete giữ suppression cũ. Supersede + created hold/suppression + occurrence/events áp dụng phải atomic/idempotent.
- `reminder_posted` không tồn tại cho occurrence ở bất kỳ status nào ngoài `DELIVERED`; mọi `MERGED|CANCELLED|BLOCKED_PERMISSION|SKIPPED_*` phải không có posted event.
- Reminder final stamps đúng transition: `due_at` non-null; `DELIVERED` có `delivered_at`; first open/delete callback chỉ set-if-null `first_opened_at`/`dismissed_at` và có tối đa một event tương ứng; duplicate không rewrite.
- Permission-rate participant key chỉ là validated `installation_id` trong cutoff; denial/disabled transition không được suy từ event thiếu. Runtime skip dedupe theo occurrence final state và denominator chỉ `DELIVERED|SKIPPED_*`.
- Notification attempt giữ exact state union, tối đa một PENDING, một prompted event pre-launch và tối đa một paired system result. New process chuyển old PENDING→INTERRUPTED(reason exact) không result; any automatic row chặn auto-prompt; late callback không rebound. Callback false chỉ `not_granted`; settings/resume có attempt/result null; current permission chỉ đọc OS.
- Delivered source không đổi status khi snooze; mỗi `reminder_snoozed` tham chiếu đúng source DELIVERED + unique child SNOOZED ordinal 0 và event `target_at` byte-equal child `due_at`. Snooze tiếp chỉ dùng child mới DELIVERED làm parent mới. Fixed generation tăng đơn điệu; terminal occurrence/event immutable, MERGED loser trỏ winner. Generic `slot_reeligible` chỉ kế `CANCELLED|BLOCKED_PERMISSION`; `MERGED` không có generation kế cho cùng logical key và slot scan phải tiến tới selected date kế.
- Player checkpoint luôn thuộc đúng discriminated phase/substate/remaining matrix; repetitions/duration dùng signed planned time, callback không carry qua boundary. Ordered skip records unique/catalog-ordered và mỗi record có đúng một `routine_step_skipped` mirror cùng active elapsed; `routine_completed.duration_ms`/`step_skip_count` lần lượt bằng frozen active counter/list size.
- Pre-flight safety acknowledgement, `Cách dễ hơn` toggle và media `Replay` không có entity/event field; Replay không được làm đổi checkpoint/counter/cadence/skips. Missing/forged/stale/reused process-scoped attestation hoặc profile/event-store không đủ tạo trusted envelope bị reject trước event boundary, nên không có Session hay `routine_start_blocked`. Event ngoài allowlist nhằm suy các interaction/proof này là contract violation.
- Valid recovery giữ session ACTIVE và chỉ Resume/End; invalid recovery atomically `ABANDONED + PENDING`.
- `safety_acknowledgements[]` immutable, current pointer resolve đúng một record; re-ack không đổi onboarding/activation anchor. `scope_reack_completed` 1:1 record `kind=reack`; safe-exit không lưu raw answer.
- Mọi event mirror exact profile `installation_id`; safe-exit không có profile/event, full delete không giữ ID/event cũ, eligible onboarding sau delete sinh ID khác.
- Mỗi event có exact entity-ref set theo `MET-013`: universal envelope + allowlisted additional refs, dedup cùng target; conventional ID không duplicate envelope, non-entity correlation/content ID không có edge. Missing/extra/wrong-type/conditional mismatch fail validation và event không được commit/import.
- Weekly summary recompute cho kết quả giống nhau từ cùng snapshot dữ liệu và mỗi row giữ full last-computed LocalStamp tách với week start/zone.
- `qualified_study_days_week_2` chỉ nhận valid study day 8–14, distinct theo elapsed block và nằm trong 0–7; không đọc `weekly_summaries[].qualified_break_days` hay distinct local date để thay thế.

### MET-071 — Fixture bắt buộc

Test suite phải có:

- exhaustive đúng 1.296 tổ hợp Cartesian hợp lệ `2 safety-lock × 2 red × 4 acute × 3 energy × 3 stiffness × 3 intent × 3 cap-state(none|MAINTAIN|RECOVER)`, kể cả field bị short-circuit; thêm fixture single-invalid riêng cho từng input/cap và auth failure riêng. Invalid/missing draft không commit, discriminated red/acute/full shape đúng; duy nhất authenticated inner-cap enum/shape-invalid Full CheckIn persist `INCOMPLETE`, còn tag/key/envelope/bundle-auth failure trả contract error trước engine;
- cap mọi terminal state `BUILD→MAINTAIN→RECOVER`, persisted expiry và safety hold precedence;
- completed/abandoned pain pending qua process death/reboot/deep link; yes/no resolve; stop dialog giữ session `ACTIVE` đến khi atomically commit `STOPPED+RESOLVED_NO|RESOLVED_HOLD`; effort/context deferred;
- late answer: pain=yes ngày sau tạo answer-day hold; pain=no/too_hard tại/trễ origin expiry chỉ lưu feedback, không cap;
- north-star: nhiều session cùng ngày, missing feedback, context=no, pain=yes, non-selected workday, late feedback;
- tuần qua timezone change và tuần bắt đầu thứ Hai;
- fixed/snooze target ngay trước, đúng và sau `work_end`; receiver late đúng 60 phút/60 phút+1ms; durable elapsed TTL ngay trước/đúng `+6h`, wall `reconfirm_after` nhảy tiến/lùi không thay authority;
- merge window đúng 30 phút chỉ snooze-vs-next-fixed chưa consume; delivered source bất biến, event `target_at` byte-equal child `due_at`, child ordinal 0; tie-break chỉ earlier→snooze-over-fixed; same-kind pair bị reject; callback cùng source lần hai bị reject và fixed MERGED không restore;
- MERGED tombstone qua cold start/resume/boot/timezone/package reconcile khi due fixed vẫn future và khi snooze winner đã delivered: không generation/event mới; generic slot reeligible chỉ tạo generation kế `CANCELLED|BLOCKED_PERMISSION`, sai predecessor/reason bị importer reject;
- schedule-time codec: `00:00`, `09:05`, `23:59` round-trip byte-identical; reject `9:05`, `24:00`, `09:05:00`, whitespace/suffix, domain second/nano khác 0 và duplicate/unsorted `reminder_times`; snooze full LocalStamp có nonzero second/millisecond vẫn hợp lệ;
- stale-cap projection: Decision Build được commit, cap Maintain xuất hiện trước render, rồi cap Recover xuất hiện sau recommendation hoặc sau selection; mỗi recommendation/selection snapshot đúng runtime projection ở chính action đó, immutable Decision fields vẫn Build/evaluation-effective cũ, và Start re-resolve Recover transaction-locally;
- delivered/open/dismiss transition atomically ghi exact LocalStamp; duplicate receiver/open/delete callback không rewrite `delivered_at`/`first_opened_at`/`dismissed_at` hoặc tạo event trùng;
- process recreation recovery hợp lệ không tạo start/completion trùng; reboot/clock unknown, expired window/date, hoặc content unavailable/identity mismatch với schema-valid checkpoint tạo `ABANDONED + PENDING` và freeze exact checkpoint;
- player state-machine goldens: DURATION seconds và REPETITIONS estimatedSeconds/reps-display-only; transition 0/dương; `ceilDiv` ở 1/999/1.000/0 ms; exact boundary sang next phase/CTA; callback late ít/nhiều hơn remaining nhưng không carry; pause/background/transition không cộng active counter; recovery exact phase/index/two remaining/counter/skips/cadence. Auth/decrypt/schema/phase/counter/catalog-cross-invariant corrupt giữ active guard, zero recovery/abandoned product event, fail export và route DATA_ERROR/full reset; không fabricate/reset checkpoint;
- skip goldens: before-boundary append `{step_id,active_elapsed_ms}` đúng một lần theo catalog order/range rồi next-phase; equality timer wins không record/event; duplicate/out-of-order/future-step/wrong active elapsed bị reject; completed `step_skip_count` bằng persisted list size và event mirror không được dùng để reconstruct;
- no-event goldens: toggle/cancel per-routine safety acknowledgement, expand/collapse easier variation và Replay current base/easier demo qua mọi player phase; missing/forged/stale/reused/wrong-process/routine/content/context preflight attestation, corrupt profile hoặc unavailable event store. Các case proof/envelope bị reject trước trusted command và không có `routine_start_blocked` draft; checkpoint/event log chỉ đổi khi allowlisted trusted domain transition khác thực sự xảy ra;
- reminder scheduler goldens: fixed initial/slot-reeligible/reuse, MERGED-date→next-selected-date bounded scan, snooze one-shot/child-delivered-chain/pair/no-pair; inject kill/write/HMAC/ref/companion/retention fail sau từng DB step và kill trước/sau registry/alarm/notify/CAS/cancel/remove. Assert zero/full ledger, exact companion events, `SNOOZE_NOT_ELIGIBLE` zero mutation, full post-pair fixed+snooze scheduling, cleanup-first uncertain-post recovery và exact seven-kind live-set/capacity. Exhaustively race receiver/snooze với edit/disable/permission/hold/rest/active/pending theo cả lease order; blocker-first không post/create child, action-first bị exact later cancellation; không fabricate dismiss hoặc resurrect loser;
- export failure goldens: picker cancel/no-URI discard in-RAM ID không event; valid URI commit started trước work; inject snapshot/encode/open/write/flush/close, provider và security failure để nhận exact v1 code/precedence; failure đầu không bị cleanup close overwrite; success chỉ terminal sau close; payload không có exception/URI/provider/path/text;
- red/acute/post-pain holds đúng kind/source/origin timezone/expiry; timezone/clock không rút ngắn/bypass;
- `REST_ONLY` cancel/skip; fresh mode/Rest/safety lần lượt clear+reschedule tương lai, replace suppression, hoặc supersede bằng hold, với exact superseded-event count/companion event; Incomplete/error giữ state cũ;
- schedule enabled→disabled→enabled versioning: cancel future, không post bù, manual selected-workday qualification không đổi và permission state độc lập;
- schedule A→B sau check-in: reconfirm reason `schedule_changed`, CheckIn/Decision/Session ID equality, không mixed-version start;
- global safety initial ack/re-ack history/pointer: valid mismatch route sau hold/pain/recovery, corrupt state fail contract, idempotent events, activation anchor byte-identical, generic notification tap route re-ack;
- partial optional feedback: context-only, effort-only too_hard, reopen chỉ missing field, `Để sau` không event/value, retry idempotent;
- event-property negative goldens: check-in kind mismatch; Decision non-mode có mode/mode-result mismatch/invalid-fields iff violation; safety route_id/result/Decision reason/SafetyHold kind/digest mismatch hoặc legacy `message_key`; recommendation/selector label-mode-default mismatch; routine start runtime mode mismatch; step-skip missing/wrong `active_elapsed_ms` hoặc không mirror checkpoint; completed skip-count mismatch; posted kind, snooze duration/ordinal/target/creation-reason mismatch; merge `distance_ms` overflow/negative/out-of-range hoặc không bằng exact due-instant delta; feedback updated_fields order/duplicate/overwrite, terminal/complete/cap-result mismatch; export unknown/uppercase error code, code sai precedence/stage, extra exception/path/provider/URI/message hoặc completed trước close;
- rate denominator 4/5 và suppressed state;
- permission fixtures: dialog branch first automatic attempt commit-before-launch, DB failure, exact PENDING/RESOLVED/INTERRUPTED null matrix và max-one pending; process recreation reason/no result/no-auto-loop, late callback ignored, explicit retry new ID, callback `granted|not_granted` không tách Deny/Dismiss. Settings-required branch mở trực tiếp, zero attempt/prompted/PENDING, same-process one `source=settings` observation cho changed/no-change, duplicate resume dedupe, process recreation không interrupt; settings/resume event có null attempt/result, settings-only bị loại initial metric. Thêm ordered/out-of-order granted→denied, full-delete/new installation ID; runtime skip từng `SKIPPED_*` cùng excluded delivered/merged/cancelled/blocked/pending;
- export snapshot parse được, đủ chín arrays, record count/foreign key hợp lệ, không direct-identifier key; vẫn phân loại pseudonymous sensitive.
- event-ref matrix fixture cho từng row `MET-013`: nullable edge, duplicate target qua mandated equality, wrong conditional hold source, dangling/wrong type, envelope-property duplicate và non-entity `_id`; exact `ref_table` token, app-profile `int64_be(1)`, UUID 16-byte length, nested acknowledgement uniqueness; writer rollback/directed authority extension + importer logical ref-set phải deterministic;
- companion-retention goldens: exact role/cardinality cho profile/ack, check-in/decision side effect, session lifecycle/feedback side effect, reminder lifecycle và weekly generation. Day 90/91, weekly 91-day row, late feedback ngày 89 và snooze chain A→B→C phải giữ đủ create/snooze/delivery/interaction/resolution mirror. Deletion-set builder xen kẽ source/event tới fixed point, không dừng ở peer một hop; ordinary AppProfile target không reverse-promote finite Session/event. Missing/thừa edge/event, wrong selector/mirror, finite/full-prefilter mismatch, bound overflow hoặc undirected ordinary-ref co-delete đều fail closed.
- generated envelope-mask parity cho đủ 48 event tại `MET-010A`: mỗi name thuộc đúng một group; writer/importer cùng generated spec. Từ fixture hợp lệ, null/missing từng required slot, điền từng forbidden slot, đảo từng conditional Decision/Session/reminder/source branch và thử unknown-case source; mọi mutant bị reject. Bắt buộc có safety-screen ba linkage branch, safety-hold hai source branch, Start `home/reminder`, export zero-ref và routine lifecycle không nhận Decision thừa.
- event-idempotency fixture cho đủ 48 event tại `MET-014`: canonical exact JCS logical preimage, natural selector/shared domain, repeatable draft event-ID reuse, unknown/missing policy. On-device dùng per-dataset Keystore HMAC-SHA-256 full 32 byte + plaintext version 1; chạy hai thread/process callback đồng thời để cùng logical command chỉ một source transition/event, same HMAC khác payload/name/ref rollback `IDEMPOTENCY_CONFLICT`, terminal/reminder-resolution/export-terminal cross-name race chỉ một winner. Forensic negatives: row thiếu/sai version, plain SHA-256(preimage) giả làm physical key, key length sai, HMAC từ dataset khác hoặc byte bị sửa đều bị on-device verifier reject, không rekey. Offline importer không có key vẫn reject duplicate logical tuple/event ID bằng volatile registry set, rồi discard set; export không có HMAC/version/key/public fingerprint.
- export sau purge daily constraint vẫn giữ byte-equivalent Decision/Session/Feedback side-effect snapshots; fixture thiếu kind/mode/source, expiry, full LocalStamp hoặc một trong năm clock-evidence field phải fail validation.
- activation anchor/event mirror và completion evidence: same boot/generation, mapping drift 2,000/2,001 ms, elapsed rollback, `24h-1ms`/đúng `24h`, reboot/discontinuity → `unknown_clock`.
- session/routine_started start evidence mirror và study-day boundary: day 1, ngay trước/đúng day 8, ngay trước/cuối day 14; mismatch/reboot/drift 2,001ms → `unknown_clock`, không UTC fallback.
- golden pilot `qualified_study_days_week_2` với onboarding giữa calendar day/timezone edge: chỉ distinct study day 8–14, nhiều local date cùng block tính 1, range 0–7; in-app calendar week vẫn distinct local date.
- weekly summary last-computed LocalStamp đủ bốn field và độc lập với `week_start_local_date/week_zone_id`; thiếu tuple phải fail import.
- check-in flow correlation: completed flow dùng cùng random `check_in_flow_id` trên started/submitted và actual CheckIn chỉ xuất hiện ở submitted; CheckIn `rule_version=1`, event envelope byte-equal `confirmed_at`, entity/export không có `submitted_at`; abandoned flow chỉ có started vẫn validate, duplicate/mismatched flow, missing/wrong rule version, stamp alias/mismatch hoặc dangling submitted CheckIn bị reject.
- timing fixtures: onboarding/check-in/total cùng process+boot, process/tracker loss cùng boot, boot mismatch, elapsed rollback, checked overflow; MainActivity ON_STOP→ON_START accumulation, ON_PAUSE excluded, config recreation counted, background `600_000/600_001 ms`, reason precedence; field XOR/nullability; `age_ms` same-process/boot hoặc null; player PLAYING→PAUSED/background→PLAYING, transition, skip, CTA wait và terminal freeze chỉ mirror `accumulated_active_ms`.
- Gate-3 sample fixtures cho `(installation_id,study_week 1|2)`: start ở biên day 1/8/15, completion qua boundary nhưng giữ start block, duplicate session và inactive week. Golden riêng: invalid activation anchor hoặc một in-scope start không thể phân block làm cả hai week samples + primary week-2 participant value unknown; valid start rõ ràng ngoài day 1–14 không contaminate. `n` chỉ gồm valid active participant-weeks, unknown không thành zero/calendar fallback. Generated classifier duyệt mọi `a=0..u`, `low=K+0^a`, `high=K+8^a`, gồm `n=4/5`, odd/even midpoint, known values quanh `0/4/8`, và chứng minh result tương đương exhaustive oracle trên mọi assignment `inactive|active(count=0..12)` cho dataset nhỏ.
- aggregation-v1 golden cho odd/even, tie, empty, `n=4/5`, `[3,3,3,5,5,5]→median 4,p90 5`, p90 nearest-rank boundary `n=10/11`; threshold dùng raw result, không interpolation/rounding.

### MET-072 — Data-quality report

Mỗi pilot import tạo trước metric report:

- số record theo collection/event;
- duplicate/orphan/invalid enum;
- pending pain và missing optional feedback tách riêng;
- invalid clock sample;
- app/content/rule/export schema versions;
- participant không có export hoặc export thiếu ngày;
- tỷ lệ bị suppress do denominator nhỏ.

Không tính feasibility gate trên dataset chưa qua validation.

## 10. Acceptance Given/When/Then

| ID | Given | When | Then |
|---|---|---|---|
| `MET-AC-01` | App ở airplane mode từ cài mới | Dùng core flow 7 ngày | Event/entities/summary vẫn ghi local; không request mạng. |
| `MET-AC-02` | Hai `routine_completed` đủ điều kiện cùng selected workday | Tính tuần | `qualified_break_days` tăng đúng 1. |
| `MET-AC-03` | Session complete, context=yes, pain=no nhưng ngày không selected tại start | Tính tuần | Ngày không qualify. |
| `MET-AC-04` | 4 feedback | Tính context-fit rate | Rate là null/insufficient; count vẫn đúng. |
| `MET-AC-05` | 5 feedback | Tính context-fit rate | Rate hiển thị theo numerator/5, làm tròn chuẩn. |
| `MET-AC-06` | Reminder bị merged | Tính prompt rate | Merged occurrence không vào denominator. |
| `MET-AC-07` | Notification tap A, routine start mang source A trong 60 phút | Attribution | A vào numerator prompt-to-start đúng một lần. |
| `MET-AC-08` | Session start từ Home có `reminder_occurrence_id=null` | Attribution | Không reminder nào nhận numerator. |
| `MET-AC-09` | Bất kỳ terminal session có too_hard, pain=no | Ghi state/event | Cap hạ một bậc từ active cap hoặc `runtime_effective_mode_at_start`; không dùng selected routine mode; snapshot giữ riêng invocation trigger, mode trigger và expiry provenance. |
| `MET-AC-10` | Terminal pain=yes và một check-in mới trước expiry | Evaluate | Hold kind post-session tạo `BLOCKED_FOR_TODAY`; không recommendation/session. |
| `MET-AC-11` | Acute non-none hợp lệ; Full draft thiếu field; inner cap invalid; hoặc constraint auth/envelope lỗi | Evaluate | Acute tạo early-stop `PAUSE_TODAY`; missing draft không commit; chỉ valid Full + authenticated/decode-success inner-cap enum/shape invalid persist `INCOMPLETE/[day_mode_cap]`; auth/envelope lỗi là `CONTRACT_ERROR` trước engine. |
| `MET-AC-12` | Người dùng chưa chọn export | Chạy app/pilot | Không byte dữ liệu nào rời app. |
| `MET-AC-13` | Người dùng xác nhận export và SAF thành công | Parse file | Một JSON UTF-8 có `metadata` canonical, đủ chín arrays, counts khớp và không direct-identifier key; file vẫn được phân loại pseudonymous sensitive. |
| `MET-AC-14` | Người dùng xóa toàn bộ | Mở lại app | Event log/installation ID/random record ID cũ không còn; không giữ delete-completed event. |
| `MET-AC-15` | Pilot n=24 single arm | Lập report | Primary là median `qualified_study_days_week_2` + distribution/range/n; không A/B, correlation hay causal claim. |
| `MET-AC-16` | Completed/abandoned pain pending | Start/deep link/relaunch | Ghi gate block; không session mới; yes/no resolve atomically, optional effort/context được defer. |
| `MET-AC-17` | Active `REST_ONLY` rồi fresh check-in cho mode/Rest/safety/Incomplete | Commit reducer + reconcile reminder | Mode clear và event count bằng exact fixed rows tương lai insert; Rest thay suppression + created event; safety tạo hold + hold event; hai nhánh sau count 0; Incomplete giữ state và không superseded event. Retry không tạo side effect trùng. |
| `MET-AC-18` | Durable elapsed TTL đúng +6h hoặc target snooze đúng `work_end` | Evaluate | Freshness resolver non-`FRESH` nên reconfirm, hoặc reject snooze theo half-open boundary; wall `reconfirm_after` không thay TTL result. |
| `MET-AC-19` | Active session relaunch, kể cả process đóng ở stop dialog; (a) authenticated/decrypted/schema/cross-invariant-valid Session+checkpoint với reboot/discontinuity, expired window/date hoặc content unavailable/identity mismatch; (b) auth/decrypt/schema/phase/counter/catalog-cross-invariant corrupt | Validate recovery | Valid same-window continuity chỉ Resume/End và không tạo `STOPPED+PENDING`. Nhánh (a) atomically `ABANDONED+PENDING` + exact recovery-failed/abandoned events, không duplicate start/completion. Nhánh (b) giữ ACTIVE guard, zero normal product/terminal event, export fail closed và route typed DATA_ERROR + explicit full reset/delete; không fabricate checkpoint/terminal. |
| `MET-AC-20` | Chưa có documented research determination/approval-or-exemption evidence | Chuẩn bị recruitment | `PILOT-GATE-ETHICS` fail; không tuyển/thu dữ liệu và không tuyên bố pilot chứng minh safety. |
| `MET-AC-21` | Pending pain được trả lời yes ngày sau | Commit answer | Hold event dùng answer-day origin timezone/expiry và block phần còn lại của answer day. |
| `MET-AC-22` | Sau pain=no, optional effort null→too_hard commit khi resolver xác nhận effective session-origin expiry đã tới | Reduce feedback | Persist effort + `updated_at`; `day_mode_cap_update_snapshot=null`; chỉ `feedback_updated.cap_result=origin_day_expired`, không `day_mode_cap_updated`/cap state ngày mới. |
| `MET-AC-23` | Receiver chạy đúng 60 phút trễ / 60 phút+1ms | Apply late guard | Equality có thể post nếu guard hợp lệ; +1ms ghi `SKIPPED_LATE`, không catch-up. |
| `MET-AC-24` | Fixed/snooze merge cùng due time; hoặc input pair cùng kind | Dedupe | Snooze thắng fixed và chỉ một post; same-kind pair bị validator reject, không merge/event. |
| `MET-AC-25` | Một routine-eligible check-in flow | Tính usability timing | Total dùng start-of-check-in→routine-start; submit→start chỉ là secondary, không thể làm xanh P90≤45s thay total. |
| `MET-AC-26` | Receiver gặp từng workday/work-end/late/hold/rest/session guard hoặc permission state | Xử lý occurrence | Ghi đúng exact `SKIPPED_*` hoặc `BLOCKED_PERMISSION`, không post; proactive cancellation dùng `CANCELLED` + reason. |
| `MET-AC-27` | Anchor/completion evidence hợp lệ, routine đầu hoàn thành trước/đúng/sau mốc 24h | Tính activation | Chỉ elapsed delta `< 86_400_000 ms` vào numerator; equality không tính, không dùng app-first-open/wall delta làm mốc. |
| `MET-AC-28` | Raw export có installation ID/timestamp/schedule | Chuẩn bị analysis/report | Phân loại pseudonymous sensitive; chỉ gọi de-identified sau bước có evidence, roster mapping lưu riêng. |
| `MET-AC-29` | Event/late feedback/active constraint hoặc Session source=reminder còn retention nhưng source gần cutoff | Chạy directed closure/purge/export | Source graph và closed required companions cùng được extend; reminder giữ ≥90 ngày/qua event-or-session authority và giữ ScheduleVersion. Exact companion-only deletion set lặp source→event→peer source tới fixed point, purge event trước source; ordinary AppProfile ref không kéo ngược full-delete; export FK-valid + mirror-complete. |
| `MET-AC-30` | Schedule enabled có slot tương lai và Decision hiện hành | Tắt rồi bật reminder trong app | Version/event giữ exact `enabled`; future occurrence bị cancel khi tắt, chỉ future fixed slot được tạo khi bật; Decision cũ bị invalidated và không mixed-version start. |
| `MET-AC-31` | Notification body hoặc action Start được tap | Mở Home | Ghi `reminder_opened` đúng `open_surface`, re-run guard và chưa tạo session; prompt-to-start metric chỉ join cùng occurrence trong 60 phút, còn persisted Session source dùng validated same-schedule context và không phụ thuộc ngưỡng này. |
| `MET-AC-32` | Onboarding event/profile mirror sai, hoặc completion khác boot/generation/mapping drift >2,000 ms | Tính activation | Gắn data-quality failure/`unknown_clock`; không phân loại activated/non-activated và không fallback UTC. |
| `MET-AC-33` | Daily constraint đã supersede/purge sau khi Decision/Session/Feedback commit side effect | Export/import | Snapshot bất biến vẫn đủ rule/local stamp/expiry/clock/kind-mode-source; không reconstruct hoặc mutate Decision. |
| `MET-AC-34` | Approved global safety version/digest khác current acknowledgement | Home/check-in/start/notification tap | Ghi deduped `scope_reack_required`, block CheckIn/Session; commit re-ack append history/pointer + one completed event, giữ nguyên activation anchor. |
| `MET-AC-35` | CheckIn/Decision schedule A nhưng active schedule B | Start | Ghi `RECONFIRM_REQUIRED(reason=schedule_changed)`; không Session; check-in mới B mới có thể start Session B. |
| `MET-AC-36` | Weekly summary có week start/zone nhưng thiếu last-computed LocalStamp | Import | Data-quality failure; không suy timestamp từ week start/event. |
| `MET-AC-37` | Session/start event có valid evidence ngay trước/đúng study-day boundary | Xếp block | Dùng elapsed half-open block; same boot/generation + drift≤2,000ms mới hợp lệ, discontinuity/mirror mismatch thành `unknown_clock`, không fallback UTC. |
| `MET-AC-38` | Source A đã tạo child B; callback A lặp và sau đó B thực sự DELIVERED | Snooze/export | Callback A lặp tạo zero mutation; A có đúng một child ordinal 0. Notification identity mới của B có thể tạo child C ordinal 0 dưới parent B; mọi parent/child/event unique, không replace hoặc restore fixed tombstone. |
| `MET-AC-39` | Export có profile và events trước/sau full delete | Validate | Mọi event trong dataset bằng profile installation ID; delete bỏ ID cũ, eligible onboarding mới sinh ID khác; mismatch fail import. |
| `MET-AC-40` | Completed selected-workday có pain=no, effort/context null | Commit chỉ context=yes | Chỉ context null→yes, effort vẫn null; ngày có thể qualify, không effort cap/event và reopen chỉ hỏi effort. |
| `MET-AC-41` | Onboarding giữa ngày làm cửa sổ study day 8–14 chạm tám local date, có nhiều local date trong một elapsed block | Tính `qualified_study_days_week_2` | Đếm distinct valid `study_day` 8–14 nên range 0–7 và cùng block chỉ tính 1; không đọc calendar-week summary/distinct local date thay pilot primary. |
| `MET-AC-42` | Start ở ngoài current window; hoặc trong window với schedule/date/TTL/clock-change/unknown mismatch | Ghi block/reconfirm event | Ngoài window gate `EXPIRED`; trong window dùng exact `schedule_changed\|local_date_changed\|ttl\|timezone_or_time_change\|clock_unknown` nhất quán giữa hai event, không Session. |
| `MET-AC-43` | Dialog attempts/results, settings-only changed/no-change observations và reminder final states trộn paired/unresolved/unavailable/terminal statuses | Tính permission + runtime-skip metrics tại cutoff | Initial non-grant chỉ từ first automatic dialog prompted/result cùng attempt; settings-only zero attempt bị loại+báo, repeated state không thành transition; unresolved excluded+reported; disable chỉ từ ordered granted→denied; skip denominator chỉ `DELIVERED\|SKIPPED_*`; rate suppress khi n<5. |
| `MET-AC-44` | Reminder A delivered/opened nhưng active schedule + new CheckIn/Decision là B; hoặc context forged/stale | Start hợp lệ | Normalize Session/event thành `source=home`, occurrence ID null, không block. Chỉ opened DELIVERED occurrence cùng schedule giữ source; 60 phút chỉ ảnh hưởng metric. |
| `MET-AC-45` | Một flow hoàn thành và một flow bị bỏ giữa form | Import/tính funnel và timing | Flow hoàn thành join bằng cùng `check_in_flow_id`, rồi actual submitted `check_in_id` nối Decision/Session; flow bỏ dở không có `check_in_id`, vẫn ở denominator và không bị coi dangling FK. |
| `MET-AC-46` | Same-process/boot flow có MainActivity ON_STOP→ON_START background đúng 10 phút / 10 phút+1ms; hoặc tracker mất | Ghi/tính duration | ON_PAUSE không tính, config recreation interval có tính; equality vẫn duration, +1ms `background_over_10m`, tracker loss `same_boot_unavailable`; duration không trừ background và không wall fallback. |
| `MET-AC-47` | Valid samples odd/even, n=4/5 và p90 rank boundary | Aggregate v1 | Sort asc; median middle/midpoint overflow-safe, p90 nearest-rank; n<5 null/insufficient; so threshold raw và golden `[3,3,3,5,5,5]` cho median 4/p90 5. |
| `MET-AC-48` | Session start ở cuối study week 1 nhưng complete trong week 2; một participant-week khác có anchor unknown | Tính Gate 3 | Completion vẫn thuộc week 1 theo validated start; sample key là installation+study_week, unknown week bị exclude/report chứ không zero/calendar fallback; median/n dùng aggregation v1 trên valid active weeks. |
| `MET-AC-49` | Event có target lặp, nullable ref, duplicate envelope, wrong `ref_table/ref_id` codec hoặc `_id` correlation/content | Commit/import | Lặp target tạo một dedup edge; null không edge; unknown token/length, duplicate/wrong-type/missing-extra edge bị reject; profile dùng int64 singleton, target khác raw UUID16; correlation/content IDs không tạo edge. |
| `MET-AC-50` | Event có mode/null, selector, reminder distance hoặc feedback transition property lệch entity/rule | Commit/import | Reject toàn event/transaction theo exact property registry; `distance_ms` phải bằng checked absolute due-instant delta trong 0..1_800_000, không coerce/floor/round; không sửa label, suy feedback complete hoặc chạy cap reducer ngoài matching `cap_result`. |
| `MET-AC-51` | Activation anchor invalid hoặc một in-scope Session/start không thể xếp study block; participant khác có valid start sau day 14 | Tính Gate 2/3 và primary | Participant lỗi có cả week 1, week 2 và primary week-2 value unknown, không drop record rồi cải thiện median; valid out-of-exposure start không contaminate; báo unknown count và không fallback wall/calendar. |
| `MET-AC-52` | Canonical CheckIn và `check_in_submitted` cùng submit transaction | Persist/export/import | CheckIn có integer `rule_version=1` và duy nhất `confirmed_at`; event envelope quartet byte-equal stamp đó; missing/wrong version, `submitted_at`, alias stamp hoặc mismatch bị reject. |
| `MET-AC-53` | Hai caller đồng thời retry cùng terminal/reminder/export action hoặc một repeatable UI observation | Commit event | Natural/shared logical tuple chỉ cho một winner qua on-device HMAC key; exact retry trả existing, conflicting payload rollback; repeatable action reuse stable draft event ID, còn actual action mới có ID mới. Offline import chỉ dedupe canonical tuple trong RAM, không recompute physical HMAC. |
| `MET-AC-54` | Urgent, từng acute pause hoặc từng active hold kind render safety screen | Validate E2E typed content/event | `route_id` khớp exact result/reason/hold matrix và digest khớp signed global artifact; event không có `message_key`. Wrong route/digest hoặc legacy field bị reject. |
| `MET-AC-55` | Step duration/repetitions, transition, late callback, skip race, completion và recovery | Persist/event/import | Checkpoint phase/remaining/substate hợp lệ; auto-advance theo signed planned time và không carry lateness; skip chỉ trước equality, event mirror exact `{step_id,active_elapsed_ms}` theo natural key session+step; completion count bằng ordered list size và recovery không reset/fabricate state. |
| `MET-AC-56` | Người dùng acknowledge pre-flight, mở `Cách dễ hơn` hoặc Replay signed demo | Persist/export event log | Không interaction nào tự tạo product event; Replay chỉ seek/play media và toàn player/session state bất biến. Event ngoài allowlist bị reject. |
| `MET-AC-57` | Decision Build còn immutable nhưng active cap Maintain xuất hiện trước recommendation/select, rồi có thể đổi tiếp trước Start | Render/select/start + validate event | Recommendation/selector snapshot runtime Maintain, non-null full cap + dedup mode-trigger/expiry-source refs và lọc không quá Maintain; snapshot null/extra sai conditional bị reject. Start re-resolve transaction-local cap, không dùng stale Decision hay selected event làm authorization. |
| `MET-AC-58` | Work schedule có canonical/malformed time string, unordered duplicate reminder hoặc snooze due có sub-minute precision | Save/export/import | Chỉ sorted-distinct exact `HH:mm` schedule round-trip byte-identical; alias/second/nano schedule bị reject, còn snooze full LocalStamp sub-minute vẫn hợp lệ. |
| `MET-AC-59` | Latest fixed generation ngày D là `MERGED`, due cũ vẫn future hoặc snooze winner đã delivered | Cold start/resume/generic reconcile rồi import | Không tạo generation/event mới cho key D; `slot_reeligible` sau MERGED bị reject. Bounded scan bỏ D và insert/reuse đúng candidate selected-date đầu tiên sau D; hết 370 ngày fail closed. |
| `MET-AC-60` | Start input thiếu/forged/stale/reused preflight attestation hoặc không tạo được authenticated event envelope | Đi qua command adapter | Trả fail-closed `CONTRACT_ERROR` trước trusted boundary; không Session, event draft hay `routine_start_blocked`. Chỉ trusted typed command bị data/content guard chặn mới dùng blocked event/REPEATABLE registry. |
| `MET-AC-61` | Picker cancel/no URI hoặc export fail ở từng snapshot/encode/destination stage với security/provider overlap | Ghi/validate funnel | Cancel không event/denominator; valid destination mới ghi started. Terminal dùng đúng một completed hoặc failed với exact v1 code, security > provider > stage, first failure wins và success chỉ sau close. |
| `MET-AC-62` | Generated registry duyệt 48 EventName và mutate năm envelope slot | Build/commit/import parity | Mỗi name khớp đúng một `MET-010A` group; missing required, non-null forbidden, conditional/source mismatch và property duplicate slot đều bị writer/importer reject. `routine_started` chỉ `home+null` hoặc validated `reminder+ID`; export event luôn zero ref/source. |
| `MET-AC-63` | Physical idempotency row version 1 hợp lệ; hoặc bị thay bằng missing/legacy/plain-SHA/wrong-dataset HMAC | On-device verify và offline export validate | Chỉ exact 32-byte `HMAC-SHA-256(K_event_idem_v1, UTF8(JCS(preimage)))` pass on-device; mọi forensic mutant fail closed, không upgrade/rekey. Export không lộ key/version/HMAC/fingerprint; offline duplicate/conflict check dùng canonical logical tuple trong volatile memory rồi discard. |
| `MET-AC-64` | Existing cap A hết muộn, feedback B strict-lower; sau đó cap Recover A-mode-trigger nhận candidate C hết muộn hơn | Reduce/export/purge/import | B strict-lower thành mode trigger trong khi expiry source vẫn A; `RECOVER→RECOVER` sau đó giữ B là mode trigger nhưng đổi expiry source sang C. Mọi retained cap/projection snapshot giữ cả graph và importer validate lineage không cần operational cap. |
| `MET-AC-65` | Gate 1/2 có mọi cặp `s,u` hợp lệ quanh threshold, có/không safety incident | Classify feasibility | Exact truth table `PASS`/`FAIL`/`INSUFFICIENT_DATA`; unknown có thể đổi kết luận không thành Iterate. Overall precedence safety stop → insufficient → all-pass Continue → confirmed-fail Iterate. |
| `MET-AC-66` | Gate 3 có known active multiset `K`, `u` unknown, gồm `n=4/5`, odd/even midpoint và unknown thật sự inactive hoặc active count `0..12` | Classify feasibility | Production `a=0..u` low-zero/high-eight bounds byte-equal exhaustive small-dataset oracle; all-low pass → `PASS`, all-high fail → `FAIL`, còn lại → `INSUFFICIENT_DATA`. Sentinel `4` mutant bị golden `[0,8]`/even-midpoint bắt lỗi; không coerce unknown thành inactive/zero. |
| `MET-AC-67` | Gate 4/5/6 có denominator `4/5`, exact equality và rational ngay dưới/trên threshold, gồm Context `16/23` | Classify feasibility | `n<5→INSUFFICIENT_DATA`; còn lại checked cross-multiply raw rational, equality PASS. `16/23` Gate 4 FAIL dù display half-up là 70%; floating-point và compare-rounded mutants bị reject. |

## 11. Definition of Done cho analytics/pilot

- Event names/properties được sinh từ enum/schema dùng chung, không string tự do.
- Metric implementation có unit test theo `MET-070`/`MET-071`.
- Weekly summary và offline recomputation cho kết quả deterministic.
- Release APK không có Internet permission, analytics endpoint hoặc telemetry SDK.
- Export JSON schema 1 có validator và round-trip test.
- Dashboard/report template luôn kèm period, numerator, denominator, missingness và low-n suppression.
- Raw export được access-control như dữ liệu pseudonymous sensitive; evidence de-identification/aggregation và roster separation hoàn tất trước output nghiên cứu.
- `PILOT-GATE-ETHICS` có documented determination và approval/exemption/non-research evidence phù hợp; protocol, consent, withdrawal/deletion, adverse-event plan và safety message có owner/sign-off trước recruitment.
- Research analysis chỉ dùng metric đã prespecify; thay đổi sau khi xem dữ liệu phải được ghi là exploratory.
