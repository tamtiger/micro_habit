# Kế hoạch triển khai MVP 6–8 tuần

- **Trạng thái:** Implementation baseline
- **ID:** `DEL-*`
- **Phạm vi:** Android MVP được khóa tại [`../Product Brief.md`](../Product%20Brief.md)

## 1. Giả định nguồn lực

| Vai trò | Năng lực giả định |
|---|---:|
| Android engineer | 2 FTE |
| Product/UX | 1 FTE |
| QA | 0,5 FTE từ tuần 2; 1 FTE tuần 5–6 |
| Movement Content Author + Clinical Safety Reviewer | Ít nhất 2 identity; author khác clinical reviewer; Technique Reviewer có thể kiêm khi credential thỏa contract |
| Privacy/store reviewer | Trước RC |

Nếu chỉ có một engineer hoặc asset/content không đến đúng hạn, kế hoạch phải được estimate lại. Không bỏ safety, privacy, accessibility hoặc delete/export gate để bù lịch.

## 2. Milestone

| ID | Mốc | Exit criteria |
|---|---|---|
| `DEL-M0` | Docs baseline | Mọi requirement/test/work item normative có ID ổn định; không còn xung đột P0/P1 |
| `DEL-M1` | App skeleton | Module, navigation, database, encryption và CI build được |
| `DEL-M2` | Safety vertical slice | Onboarding → check-in → rule → một routine → terminal pain gate → hold chạy end-to-end |
| `DEL-M3` | Feature complete | Sáu routine, reminder, weekly, settings, export/delete hoàn tất |
| `DEL-M4` | Content complete | 100% manifest/asset/copy có sign-off hợp lệ |
| `DEL-M5` | Release candidate | Toàn bộ mandatory QA/release gate pass |
| `DEL-M6` | Pilot ready | Consent, recruitment, participant-support/adverse-event và data-transfer process được duyệt |

## 3. Dependency graph

```text
Docs baseline
   ├─> Domain schema ─> Rule engine ─> Recommendation UI
   │                         └────────> Safety fixtures/E2E
   ├─> Content schema ─> Manifest validator ─> Player ─> Approved assets
   ├─> Data inventory ─> Encrypted storage ─> Export/Delete
   └─> Schedule contract ─> Local scheduler ─> Notification QA

Recommendation UI + Player + Feedback + Storage ─> Weekly summary
All workstreams + external approvals ─> RC ─> Pilot
```

## 4. Workstream và backlog

### `DEL-01` — Foundation

- Tạo Android project/module theo `ARC-*`.
- Dùng exact baseline `minSdk=26`, `targetSdk=36`, `compileSdk=36` theo `ARC-011`; CI khóa ba giá trị. Release vẫn recheck Play policy trước submit, và mọi mức bắt buộc mới phải cập nhật docs/ADR/test matrix trước khi đổi build config.
- Thiết lập static analysis, unit/instrumented test và manifest allowlist check.
- Tạo design tokens, navigation shell và localization `vi-VN`.
- Cài encrypted local storage, migration harness và fake clock/timezone.
- Sinh hoặc hand-write một closed codec library duy nhất từ `ARC-013`, `ARC-024`, `ARC-111`, `ARC-127`: duplicate-safe parser, exact `ProfileWireV1`/six entity DTO/`WeeklySummaryWireV1`, typed 48-event registry, canonical enum/ID/time adapters và generated negative fixtures; exporter, importer và on-device validator không được có schema switch riêng.

**Done khi:** CI build sạch; app chạy offline; manifest không có permission ngoài allowlist; database round-trip/migration test pass.

### `DEL-02` — Eligibility, check-in và rule engine

- Implement onboarding/age/eligibility flow với staged pre-save state và một initial `Lưu lịch` transaction zero-or-all cho Profile + first acknowledgement/current pointer + ScheduleVersion/active pointer + staged/scope/schedule/completion events; permission UI chỉ được mở sau commit.
- Implement enum/schema và validation đúng `SAF-*`.
- Implement pure rule engine `first match wins`, reason code và version.
- Generate fixture tests cho mọi nhánh và property tests cho hard-stop invariants.
- Implement check-in TTL, six-hour reconfirmation và reasoned safety hold cho red flag, từng acute issue và post-session pain.
- Chụp origin `ZoneId`, tính `expires_at_utc` tại đầu ngày kế tiếp và bảo đảm timezone/clock change không rút ngắn hold.

**Done khi:** cùng input/version cho cùng output; không output bị chặn nào chứa routine; fixtures pass 100%.

### `DEL-03` — Recommendation và routine player

- Build Home, “Nhịp hôm nay”, “Vì sao” từ allowlisted reason code.
- Implement same-or-lighter routine selection; không có đường nâng mode.
- Mỗi recommendation/selection resolve cap trong serialized transaction và ghi conditional `runtime_day_mode_cap_snapshot` cùng dedup refs tới mode-trigger/expiry-source Session; Start vẫn reproject độc lập và không tin event cũ.
- Implement pre-flight từ signed content theo exact global → routine safety sequence → acknowledgement → context order. Start command adapter phải claim one-shot process-scoped attestation gắn routine + full content identity và valid event boundary ngay trước serialized authorization transaction, không tin boolean UI/deep link; proof/envelope fail trả trước trusted boundary và không event, còn trusted domain block mới ghi `routine_start_blocked`. Proof đã claim không được phục hồi sau block/rollback. Implement player phase/remaining exact, step instruction/demo và per-step `Cách dễ hơn` từ signed `EasierVariationStep`, pause/resume/skip/stop. `Replay` chỉ seek/play current signed demo, không đổi checkpoint/session/event. Variation kế thừa timer/dosage, không đổi session mode/routine hoặc infer context. Khi người dùng yêu cầu stop, giữ session `ACTIVE` trong lúc hỏi pain; chỉ câu trả lời mới atomically commit `STOPPED + RESOLVED_NO|RESOLVED_HOLD`, không tạo `STOPPED+PENDING`.
- Persist `ACTIVE` session/player substate. Same-boot recovery hợp lệ cho phép Resume/End; End quay lại stop/pain flow ở trên. Reboot, hết work window/date hoặc content unavailable/identity mismatch chỉ chuyển `ABANDONED + PENDING` khi Session/checkpoint đã authenticate và pass closed schema/invariant. Corrupt payload/checkpoint giữ active guard, zero normal event, fail export và route DATA_ERROR/full reset; không fabricate terminal state.
- Bundle asset offline và validate content manifest khi build.
- Implement TalkBack order, font scaling, reduced motion, caption/text alternative.

**Done khi:** vertical slice chạy với approved test content; recovery không auto-complete và không cho tạo session thứ hai; player không phụ thuộc duy nhất vào video/audio.

### `DEL-04` — Feedback và safety response

- Với mọi terminal state `COMPLETED|STOPPED|ABANDONED`, buộc pain gate đã resolve trước phiên kế tiếp; effort/context có thể deferred. `COMPLETED|ABANDONED` có thể persist `PENDING`; `STOPPED` chỉ được tạo cùng `RESOLVED_NO|RESOLVED_HOLD` sau câu trả lời.
- Với pain mới/tăng: dừng ngay, không CTA routine khác, tạo reasoned hold đến hết **answer day** theo LocalStamp/zone tại lúc câu trả lời được commit; late answer ngày sau không dùng session day.
- Với `too_hard` + pain=no ở bất kỳ terminal state: tạo/cập nhật `DayModeCap.max_mode` hạ đúng một bậc trong phần còn lại của **session terminal-origin day**, theo reducer/clock-evidence contract; persist riêng `mode_trigger_session_id` và expiry `source_session_id`, kể cả `RECOVER→RECOVER` deadline-only merge.
- Persist pending pain gate của `COMPLETED|ABANDONED` qua process death; stop dialog chưa trả lời vẫn là `ACTIVE` recovery. Home, notification và deep link đều phải chặn session mới cho tới khi gate/recovery được giải quyết.
- Ghi audit event local đúng exact envelope/property allowlist của `MET-*`; version/digest chỉ xuất hiện ở event mà dictionary yêu cầu, không thêm generic field. Không event nào log raw health text.

**Done khi:** E2E chứng minh hard-stop precedence, reason-specific hold, pending pain không bypass được, và expiry theo instant/zone nguồn.

### `DEL-05` — Reminder

- Settings cho 1–7 ngày, work start/end và 1–2 giờ nhắc hợp lệ.
- Xin notification permission chỉ sau khi initial onboarding/schedule transaction đã commit; denied/unavailable/process loss không rollback hoặc đổi activation anchor.
- Lên lịch local không exact; reschedule khi reboot/timezone/time change/app update.
- Drop notification trễ/outside work interval; dedupe exact duplicate và chỉ merge cặp snooze-vs-next-fixed cách nhau `<=30m` theo tie-break chuẩn. Hai fixed slot hợp lệ không bị merge chỉ vì ở gần nhau.
- Snooze 15/30/60 chỉ trong interval; không calendar/driving/activity detection.
- `REST_ONLY` suppress reminder còn lại trong ngày nguồn; fresh manual check-in chỉ khôi phục fixed slot còn ở tương lai khi outcome mới cho phép.
- Mọi PendingIntent identity đi qua exact keyless registry: durable-add trước create, `UPDATE_CURRENT|IMMUTABLE` chỉ cho create/update; cancel/recovery dùng `NO_CREATE|IMMUTABLE`, platform-cancel trước durable-remove. Full-delete không đi qua occurrence event transition.

**Done khi:** deterministic scheduler tests pass qua timezone/DST/reboot/denied/revoked/late/merge, one-shot snooze và child-delivered snooze chain; row+event+companion atomic allocation, bounded next-selected-date scan, shared delivery-lease races, DB→platform kill recovery và exact PendingIntent/notification live-set lifecycle; full post-pair scan phục hồi cả fixed lẫn snooze, tối đa hai fixed slot và tối đa một child cho mỗi delivered occurrence.

### `DEL-06` — Weekly summary và local metrics

- Implement event ledger theo `MET-*`.
- Tính local week/day bằng stored `ZoneId`/`local_date` semantics.
- Persist/export exact `WeeklySummaryWireV1`: stable ID/initial week zone, 13 count và ba typed rate; checked integer round-half-up, exact suppression conditional và generated/viewed event refs/mirrors.
- Hiển thị count; chỉ hiển thị rate khi denominator ≥5.
- Implement generated feasibility classifier cho Gate 1–2 bằng exact success/unknown bound; Gate 3 bằng `a=0..u` low-zero/high-eight median bounds, audit tuples và exhaustive small-dataset oracle; Gate 4–6 bằng checked raw-rational cross-multiplication, không compare percent đã round. Overall precedence safety stop → insufficient → all-pass → confirmed-fail; không coerce unknown thành zero/inactive.
- Không correlation, AI insight, health outcome hoặc sedentary claim.

**Done khi:** golden dataset cho cùng expected metrics qua timezone change và missing feedback.

### `DEL-07` — Data control và privacy

- Data inventory implementation map.
- Export versioned JSON qua Storage Access Framework với warning.
- Implement `RetentionAuthorityV1` + nullable derived prefilter, ordinary refs và directed required-companion closure đúng `ARC-024`: active Schedule là finite-authority sink cho tới replace; replace seed base rồi replay incoming candidates; purge dùng exact directed companion deletion set, không dùng undirected connected component hoặc max-date sentinel.
- Xóa database/file/key/cache/log/pending notification; idempotent và crash-safe.
- Disable backup; redact notification preview, release log và app-private diagnostic record. MVP không có diagnostic-export/Support route riêng.
- Đặt `FLAG_SECURE` trước frame đầu và giữ suốt `MainActivity`; device-test screenshot, recording/share/cast, non-secure display, recent-task thumbnail và TalkBack trade-off.
- Bundle toàn văn/version privacy policy đã duyệt để đọc offline trong Settings; CTA tùy chọn mở URL HTTPS công khai bằng browser ngoài app, không WebView/`INTERNET`.

**Done khi:** exhaustive closed-registry export round-trip/schema mutants và directed-retention migration/late-reference/active-schedule/weekly-fixed-deadline tests pass; forensic QA không tìm thấy user data trong app sandbox sau delete; reinstall không phục hồi dữ liệu cũ.

### `DEL-08` — Content production

- Chốt movement sequence/regression và full four-field `RoutineContextContract` (`stableChair`, `stableDeskOrWall`, `standingSpace`, `walkingPath`) với signed per-field preflight key; không rút gọn thành một context tag/boolean. Chốt comfortable-range/setup/contraindication/stop/escalation copy và kiểm exact runtime placement/acknowledgement, không chỉ sự hiện diện trong manifest.
- Quay/compress video; tạo caption, transcript/text instruction, poster và alt description. Media pipeline phải phát non-fragmented H.264 MP4/WebVTT đúng exact metadata/duration/cue contract `CNT-021`; PNG/WebP phải đồng thuận declared MIME + byte signature + production full decoder/intrinsic dimension, không suy extension/tolerance/OS metadata.
- Author điền đủ typed global-safety slot theo đúng semantic role/cardinality và total key-role→message-category matrix `CNT-040A`; không dùng mảng key chung hoặc hard-code clinical copy trong UI.
- Reviewer ký exact routine/global clinical digest, routine revision, credential/time validity và Content QA manifest digest theo `CNT-014`, `CNT-015`, `CNT-051`; cả sáu Content-QA time phải sau max của 20 professional sign-off timestamp toàn manifest. Global content author phải khác global clinical reviewer và version label đơn lẻ không phải sign-off.
- Build validator từ chối content thiếu/expired/mismatched checksum, poster/video metadata/format/decode/duration/caption sai, message category mismatch hoặc typed global route missing/extra/swapped.
- Release CI đối chiếu append-only previous-approved catalog index theo `CNT-061`; digest đổi không được reuse/lùi SemVer và mapping current chỉ append sau approval.
- Release validator emit exact `ContentReleaseValidationEvidenceV1` + generated evidence SHA constant theo `CNT-060A`; runtime bind evidence→manifest và replay chronology bằng baked validation instant, không dùng device clock để expire offline content. Mọi build/update mới vẫn phải revalidate trước `validThrough`.

**Done khi:** sáu routine đạt `CNT-*`; không dùng placeholder trong RC.

### `DEL-09` — Pilot readiness

- Usability test check-in/time-to-routine.
- Khóa protocol/metric threshold trước khi tuyển người.
- Có documented determination pilot có phải human-subject research hay không; nhận ethics/IRB-equivalent approval hoặc exemption phù hợp trước recruitment.
- Chuẩn bị consent, study code, support, adverse-event escalation và secure transfer.
- Rehearse export/import và data-quality checks với synthetic participants.

**Done khi:** dry run hoàn chỉnh; reviewer ký `DEL-M6`.

## 5. Lịch đề xuất

| Tuần | Engineer A | Engineer B | Product/UX/QA/External |
|---:|---|---|---|
| 1 | Project/data foundation | Navigation/content validator | Flow/copy, content brief, test plan |
| 2 | Rule engine/check-in | Onboarding/storage | Safety fixtures, first content review |
| 3 | Recommendation/player | Reminder scheduler | Usability round 1, asset production |
| 4 | Feedback/safety hold | Weekly/settings | Sáu routine review, E2E expansion |
| 5 | Export/delete/security | Accessibility/hardening | Full regression, privacy/store review |
| 6 | Bug fix/performance | Bug fix/RC automation | Content sign-off, release-gate audit |
| 7 | RC fixes | Pilot tooling | Closed-test feedback, dry run |
| 8 | Contingency | Contingency | Pilot approval/start |

## 6. Cut line

### Không được cắt

- Eligibility/safety flow, hold và deterministic engine.
- Six-routine coverage sau chuyên gia duyệt.
- Notification permission/error handling.
- Offline, encrypted storage, delete/export.
- Accessibility core flow.
- QA/security/store/pilot release gates.

### Có thể cắt nếu trễ

- Biểu đồ trang trí; thay bằng số đếm/text.
- Haptic tùy chọn nếu text/timer state vẫn đầy đủ.
- Animation không thiết yếu.

### Không đưa ngược vào MVP

- AI, wearable/Health Connect, account/cloud, remote telemetry.
- Paywall/subscription.
- Calendar/driving detection hoặc adaptive reminder.
- Correlation/causal insight, streak, social/B2B.
- Diagnostic-export/Support route riêng; MVP chỉ giữ diagnostic app-private đã redact.

## 7. Definition of Done chung

Story chỉ `Done` khi:

1. acceptance criteria và negative paths pass;
2. unit/integration/UI test phù hợp được thêm;
3. TalkBack/font scaling/contrast được kiểm tra nếu có UI;
4. data inventory và delete/export map được cập nhật nếu có dữ liệu mới;
5. safety/content re-review hoàn tất nếu output/copy/asset thay đổi;
6. không thêm permission, SDK hoặc network endpoint ngoài allowlist;
7. requirement ID được ghi trong PR và test;
8. tài liệu/ADR/migration note được cập nhật.

## 8. RACI tối thiểu

| Quyết định | Responsible | Accountable | Consulted |
|---|---|---|---|
| Scope/metric | Product | Product owner | Engineering, QA |
| Rule implementation | Engineering | Tech lead | Product, QA, safety reviewer |
| Safety/content copy | Content producer | Qualified reviewer | Product, legal/privacy |
| Data/security | Engineering | Tech lead | Privacy reviewer, QA |
| Release gate | QA | Product owner | Engineering, safety/privacy reviewers |
| Ethics/research determination | Research lead | Product owner | Ethics/IRB-equivalent authority, privacy, safety |
| Pilot protocol | Research/Product | Product owner | Safety/privacy reviewers |

Tên cá nhân phải được điền trước `DEL-M4`; role trống là release blocker.

## 9. Rủi ro lịch

| Rủi ro | Dấu hiệu sớm | Ứng phó |
|---|---|---|
| Content approval chậm | Tuần 2 chưa có hai routine approved | Dùng test fixture để build; không ship placeholder; dùng buffer tuần 7–8 |
| Scheduler khác hành vi theo OEM | Instrumented test lỗi trên device matrix | Dùng inexact window, reschedule triggers, mở rộng physical-device test |
| Encryption/migration lỗi | Golden migration không round-trip | Khóa schema sớm, test upgrade/uninstall/delete từ tuần 1 |
| Check-in vượt 20 giây | Usability P90 >30 giây | Rút copy, giữ enum; không bỏ safety fields |
| APK quá lớn | Asset budget vượt ở tuần 3 | Nén/chia clip đã duyệt; không chuyển sang cloud trong MVP |
| External gate chưa duyệt | Privacy/content/store owner chưa ký tuần 5 | Không tạo RC; escalation theo owner, dùng contingency |

## 10. Quyết định sau pilot

Sau feasibility, tạo decision record riêng cho từng nhánh:

- giữ/rút gọn check-in;
- cần thử fixed-vs-contextual reminder hay không;
- mở rộng content nào;
- monetization experiment;
- iOS/Health Connect/AI feasibility.

Không gom các nhánh này thành một scope change lớn và không suy diễn “không khác biệt” từ pilot `n=24` thành bằng chứng nên bỏ sản phẩm.
