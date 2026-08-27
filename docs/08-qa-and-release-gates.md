# 08 — QA Strategy & Release Gates

- **Trạng thái:** Implementation baseline 1.0
- **Phạm vi:** Android-first MVP, `vi-VN`, local-only, sáu routine offline
- **Chủ sở hữu:** QA + Engineering + Product
- **Nguồn chuẩn:** `01-product-requirements.md` (FR), `02-ux-flows-and-copy.md` (UX), `03-safety-rule-engine.md` (SAF), `04-content-contract.md` (CNT), `05-data-privacy-security.md` (DATA/SEC), `06-technical-architecture.md` (ARC) và `07-analytics-and-validation.md` (MET)

Tài liệu này biến contract thành kiểm thử tái lập và gate phát hành. Không gate nào được bypass bằng feature flag production, remote config, AI hoặc waiver không có owner/evidence.

## 1. Chiến lược và evidence

### QA-001 — Test layers bắt buộc

1. **Pure unit/generated fixtures:** rule v1, feedback reducer, hold/cap expiry, selector, manifest validator.
2. **Property/mutation:** precedence và invariants SAF-072.
3. **Repository/integration:** encrypted persistence, transaction, process death, scheduler, export/delete.
4. **UI/E2E:** onboarding → check-in → outcome → routine → terminal state → pain gate/feedback.
5. **Accessibility/content/manual:** TalkBack, font/contrast/reduced motion, video/caption/transcript, clinical copy/sign-off.
6. **Security/release artifact:** permissions, binary/dependency scan, network capture, storage/log inspection.

Safety integration test phải dùng rule/repository thật; không mock kết quả engine ở boundary cần chứng minh. Snapshot không thay assertion semantic.

### QA-002 — Test record

Mỗi test/evidence có:

```text
test_id; requirement_ids[]; rule/content/schema_version; build_digest;
device/os; preconditions; steps; expected; actual; result;
evidence_uri; seed_if_generated; owner; executed_at
```

`requirement_ids[]` trong artifact máy đọc luôn chứa từng ID đầy đủ, không chứa shorthand. Chỉ cột `Trace` hiển thị trong tài liệu được dùng `/` theo grammar: segment đầu phải là full ID; segment sau là full ID, hoặc suffix khớp `^[0-9]{3}[A-Z]?$` và kế thừa mọi ký tự của full ID đã expand ngay trước đó tới dấu `-` cuối. Ví dụ `CNT-012/020/021` expand thành `[CNT-012,CNT-020,CNT-021]`, còn `MET-013/ARC-021` thành `[MET-013,ARC-021]`. Parser trim không ký tự nào, cấm segment rỗng/ambiguous và phải resolve mỗi expanded ID tới đúng một heading/table registry trong baseline; unknown, duplicate ngoài chủ ý hoặc unresolved ID làm REL-GATE-CONTRACT fail.

Một artifact rebuild làm đổi binary, ruleset, manifest hoặc asset digest phải chạy lại gate bị ảnh hưởng. Evidence build cũ không ký build mới.

### QA-003 — Device matrix

Mỗi RC chạy trên emulator và ít nhất một máy thật tại Android API thấp nhất được build hỗ trợ, target API và bản stable mới nhất được support. Matrix phải có:

- thiết bị RAM thấp/standard;
- font/display scale mặc định và accessibility maximum;
- locale `vi-VN`;
- timezone không DST, có DST và offset nửa giờ;
- notification permission granted/denied/revoked;
- airplane mode từ cold start.

## 2. Rule engine Given/When/Then

### QA-010 — First-match và safety-first

| ID | Trace | Given | When | Then |
|---|---|---|---|---|
| `QA-SAF-001` | SAF-020 | hold active, red flag true, field khác invalid | evaluate | `BLOCKED_FOR_TODAY`; lock precedence cao nhất; không mode/routine |
| `QA-SAF-002` | SAF-020/024 | hold inactive, red flag true, field sau null/invalid | submit | `URGENT_STOP`; ghi hold kind `RED_FLAG` tới origin-day expiry |
| `QA-SAF-003` | SAF-020/021 | hold false, red flag missing/sai kiểu | evaluate | `INCOMPLETE`; invalid field là `red_flag`; không default false |
| `QA-SAF-004` | SAF-020/024 | red flag false, acute=`acute_illness`, field sau null | evaluate | `PAUSE_TODAY`; hold `ACUTE_ILLNESS`; later missing không làm yếu safety |
| `QA-SAF-005` | SAF-020/024 | acute=`new_or_worsening_pain_or_injury` | evaluate | `PAUSE_TODAY`; hold đúng kind; không routine |
| `QA-SAF-006` | SAF-020/024 | acute=`medically_restricted` | evaluate | `PAUSE_TODAY`; hold đúng kind; không routine |
| `QA-SAF-007` | SAF-020/021 | red flag false, acute missing/invalid | evaluate | `INCOMPLETE`, không đọc ordinary fields energy/stiffness/intent để chọn mode |
| `QA-SAF-008` | SAF-020/021 | acute none, từng energy/stiffness/intent missing hoặc invalid | evaluate | `INCOMPLETE`, không Recover fallback |
| `QA-SAF-009` | SAF-020 | acute none, intent rest, energy low/stiffness notable | evaluate | `REST_ONLY`; Rest precedence trước Recover; không tạo hold |
| `QA-SAF-010` | SAF-020 | acute none, energy low | evaluate | `RECOVER` |
| `QA-SAF-011` | SAF-020 | acute none, stiffness notable | evaluate | `RECOVER` |
| `QA-SAF-012` | SAF-020 | energy good, stiffness none/mild, intent moderate | evaluate | `BUILD` |
| `QA-SAF-013` | SAF-020 | mọi tổ hợp valid còn lại | evaluate | `MAINTAIN` |
| `QA-SAF-014` | SAF-024 | urgent/pause vừa tạo hold | resubmit check-in cùng ngày | row 0 `BLOCKED_FOR_TODAY`; không gỡ hold |
| `QA-SAF-015` | SAF-024 | Rest-only | mở lại luồng trong ngày | không có safety hold do Rest; flow vẫn theo check-in lifecycle |
| `QA-SAF-016` | SAF-020/021 | không safety outcome, cap có mặt nhưng corrupt/ngoài enum | evaluate | `INCOMPLETE`, không bỏ cap lỗi để mở mode; nếu acute issue hợp lệ thì Pause vẫn thắng |
| `QA-SAF-017` | SAF-011/031 | lần lượt mọi outcome, ba acute reason, form invalid, exact cap-only invalid và ba effective mode | construct/serialize transient `RuleDecisionV1` | `presentation_route` khớp total mapping `BLOCKED_HOLD\|URGENT_STOP\|PAUSE_ACUTE_ILLNESS\|PAUSE_NEW_OR_WORSENING_PAIN_OR_INJURY\|PAUSE_MEDICALLY_RESTRICTED\|INCOMPLETE_FORM\|INCOMPLETE_CONSTRAINT_DATA\|REST_ONLY\|MODE_RECOMMENDATION`; `allowed_modes` lần lượt `[]`, `[RECOVER]`, `[MAINTAIN,RECOVER]`, `[BUILD,MAINTAIN,RECOVER]`. Missing/extra/sai-order hai required projection, legacy `message_key`, unknown route hoặc outcome/reason/route mismatch bị reject; closed persisted `DecisionWireV1` làm test ngược và reject cả hai projection như extra |

### QA-011 — Day mode cap

| ID | Trace | Given | When | Then |
|---|---|---|---|---|
| `QA-CAP-001` | SAF-023 | outcome Build, không cap | render | effective Build; allowed Build/Maintain/Recover |
| `QA-CAP-002` | SAF-023 | outcome Build, cap Maintain | evaluate | outcome vẫn Build; effective Maintain; allowed Maintain/Recover |
| `QA-CAP-003` | SAF-023 | outcome Maintain, cap Recover | evaluate | outcome vẫn Maintain; effective Recover |
| `QA-CAP-004` | SAF-023 | outcome Recover, cap Maintain | evaluate | effective Recover; cap không nâng mode |
| `QA-CAP-005` | SAF-045 | terminal session có `runtime_effective_mode_at_start=BUILD`, effort too_hard, pain no | reduce feedback | cap mới Maintain tới đầu origin local date kế |
| `QA-CAP-006` | SAF-045 | cap Maintain active, effort too_hard, pain no | reduce feedback | cap mới Recover, không dựa ngược lên session mode |
| `QA-CAP-007` | SAF-045 | cap Recover active, effort too_hard, pain no | reduce feedback | cap giữ Recover |
| `QA-CAP-008` | SAF-045 | effort easy/moderate, pain no | reduce feedback | không đổi cap |
| `QA-CAP-009` | SAF-045 | context_fit no | reduce feedback | chỉ lưu feedback/tổng kết; không đổi cap/rule/reminder |
| `QA-CAP-010` | SAF-045 | effort too_hard và pain yes | reduce feedback | pain hold thắng; không tạo/cập nhật cap từ feedback đó |
| `QA-CAP-011` | SAF-046 | cap từ hôm trước đã expiry | check-in ngày mới | cap không áp; outcome/effective mode theo rule mới |
| `QA-CAP-012` | SAF-045 | lần lượt completed/stopped/abandoned, pain no, effort too_hard | resolve gate | cả ba đều tạo/hạ cap đúng một bậc từ cap hiện hành hoặc `runtime_effective_mode_at_start` |
| `QA-CAP-013` | SAF-045 | decision/runtime ceiling Build nhưng người dùng chọn routine Recover, pain no + too_hard | reduce feedback | không dùng chosen routine mode; cap mới vẫn Maintain |
| `QA-CAP-013A` | SAF-045 | Decision ceiling Build, cap-at-start Recover, selected routine Recover; tới feedback thì cap cũ không còn active nhưng session-origin constraint còn active | reduce feedback | fallback dùng `runtime_effective_mode_at_start=RECOVER`, cap vẫn Recover; không nhảy lên Maintain từ stale Decision ceiling |
| `QA-CAP-014` | SAF-045/046 | pain=no + too_hard được commit tại/qua effective terminal-origin expiry | reduce feedback | feedback answers/timestamps vẫn lưu, `day_mode_cap_update_snapshot=null`; nếu effort chuyển null→too_hard và phát `feedback_updated`, event dùng exact `cap_result=origin_day_expired`; không tạo cap state hoặc `day_mode_cap_updated`; equality là inactive |
| `QA-CAP-015` | SAF-045/047 | active cap cũ có deadline muộn hơn; feedback Session B hạ strict mode | reduce feedback | resulting cap `mode_trigger_session_id=B` nhưng expiry `source_session_id`/origin/evidence giữ existing Session A; update snapshot invocation trigger B, không rút ngắn qua timezone |
| `QA-CAP-015A` | SAF-045/047 | existing cap và candidate có effective deadline bằng nhau nhưng source/evidence khác, mode hạ strict | reduce feedback | giữ existing expiry origin/evidence/`source_session_id`, set `mode_trigger_session_id` sang current Session, ghi `deadline_source=same` và hạ mode đúng một bậc |
| `QA-CAP-015B` | SAF-045/047 | existing cap Recover có mode trigger A; Session B too-hard tạo candidate deadline strictly later | reduce feedback | `RECOVER→RECOVER` giữ `mode_trigger_session_id=A`, adopt expiry `source_session_id=B`, invocation `trigger_session_id=B`, `deadline_source=candidate_later`; importer chấp nhận ba vai trò đúng conditional |
| `QA-CAP-016` | SAF-047 | operational cap đổi/hết hạn/purge sau Decision/Session/Feedback/projection event | export | immutable evaluation/runtime/reducer/projection snapshot vẫn byte-stable, đủ mode-trigger + expiry-source/LocalStamp/expiry/evidence; ref graph của cả Session được giữ tới cutoff và không reconstruct từ state hiện hành |
| `QA-CAP-017` | SAF-023/047 | Decision Build đã persist; feedback phiên khác sau đó tạo cap Maintain, user chọn Maintain, rồi cap hạ tiếp Recover trước Start; mutate từng nested/outer before/runtime mode | render/select/start/export | `decision_evaluated` giữ snapshot Build; `recommendation_shown`/`routine_selected` ghi runtime Maintain + non-null exact `runtime_day_mode_cap_snapshot` và refs mode-trigger/expiry-source. Session nested before byte-equal outer/source Decision, nested runtime byte-equal outer và bằng min(before,cap); snapshot iff strict reduction. Null/non-null/equality/formula mutant bị reject. Start reproject Recover nên không tạo session Maintain; rerender/reselect chụp cap Recover mới, không dùng stale Decision/selection. |

### QA-012 — Pending pain gate và session guard

| ID | Trace | Given | When | Then |
|---|---|---|---|---|
| `QA-PAIN-001` | SAF-041 | session chuyển `completed` | commit terminal state | tạo pending gate cùng transaction, pain=null |
| `QA-PAIN-002` | SAF-041 | session `ACTIVE`, user yêu cầu stop | pain question chưa được trả lời/process death | session vẫn `ACTIVE`, guard chặn session mới và relaunch chỉ route recovery; không persist `STOPPED+PENDING` |
| `QA-PAIN-002A` | SAF-041 | active stop question | answer pain=no | atomically `STOPPED+RESOLVED_NO`; có thể defer effort/context |
| `QA-PAIN-002B` | SAF-041/042 | active stop question | answer pain=yes | atomically `STOPPED+RESOLVED_HOLD` cùng post-session hold; không routine khác |
| `QA-PAIN-002C` | FR-033/043/ARC-014/020 | Tap Stop lần lượt từ PLAYING/PAUSED, giữ dialog qua nhiều tick, race đúng phase/final-step boundary, Continue, answer và process death | player coordinator + persistence/event inspect | Stop reconcile tới tap rồi persist PAUSED/segment-null trước dialog; PLAYING tạo một paused event, PAUSED zero. Dialog/tick không tăng phase/counter; Continue chỉ auto-resume nếu same-process prior PLAYING. Nếu reconcile đã vào final CTA thì timer thắng, không dialog. Answer `routine_stopped.elapsed_ms` bằng frozen tap counter; process death recovery giữ ACTIVE/PAUSED, không tự resume/STOPPED |
| `QA-PAIN-003` | SAF-041 | session `abandoned` sau không thể resume | mở app | route mandatory pain gate trước mọi recommendation/session |
| `QA-PAIN-004` | SAF-041 | pending pain unresolved | deep link/notification/restore cố mở routine | start gate trả `PENDING_SAFETY_FEEDBACK`; UI route canonical `PENDING_PAIN_GATE`; không tạo session |
| `QA-PAIN-005` | SAF-041 | pending pain unresolved | process death + relaunch/reboot | gate còn nguyên; không default no |
| `QA-PAIN-006` | SAF-041 | pending gate | defer effort/context nhưng trả pain=no | status `RESOLVED_NO`; effort/context có thể null; session guard mở theo rule hiện tại; chỉ context null/khác yes ngăn qualified, effort null không thuộc predicate |
| `QA-PAIN-006A` | FR-060/061 | completed selected-workday session đã pain=no, optional feedback còn null | chọn/lưu chỉ context=yes | persist context yes, effort vẫn null; session có thể qualify, reopen chỉ hỏi effort còn thiếu |
| `QA-PAIN-006B` | SAF-045/FR-060 | terminal session đã pain=no, optional feedback còn null | chọn/lưu chỉ effort=too_hard | persist effort, context vẫn null; reducer cap chạy atomically nếu origin active, nhưng session chưa qualify |
| `QA-PAIN-007` | SAF-042 | pending gate, pain=yes | submit | hold `POST_SESSION_NEW_OR_WORSE_PAIN` được ghi trước resolve; không same-session lighter CTA |
| `QA-PAIN-008` | SAF-042 | pain=yes hold active | mở/restore/chọn bài khác cùng ngày | `BLOCKED_FOR_TODAY`; Start/Resume bị vô hiệu |
| `QA-PAIN-009` | SAF-042 | ghi hold thất bại | submit pain=yes từ pending gate hoặc active stop question | transaction rollback: pending vẫn pending hoặc stop session vẫn `ACTIVE`; UI vẫn stop và session mới bị chặn |
| `QA-PAIN-010` | SAF-042/047 | session origin day đã qua, pending/stop answer pain=yes hôm sau | submit | tạo hold theo answer-day LocalStamp/zone tới effective next-day deadline, snapshot exact hold trong feedback; không dùng session-origin expiry |
| `QA-PAIN-011` | SAF-047 | session pending rất cũ resolve pain=yes rồi maintenance chạy khi answer-day hold còn active | maintenance/start | hold vẫn resolve đúng source; Session→Decision→CheckIn→ScheduleVersion chưa purge, reason-specific block không thành dangling-reference contract error |

### QA-013 — Reason-specific blocked copy

Với từng hold kind `RED_FLAG`, `ACUTE_ILLNESS`, `NEW_OR_WORSENING_PAIN_OR_INJURY`, `MEDICALLY_RESTRICTED`, `POST_SESSION_NEW_OR_WORSE_PAIN`:

- engine luôn trả `BLOCKED_FOR_TODAY`;
- engine trả logical `presentation_route=BLOCKED_HOLD`; orchestrator resolve verified kind qua exact signed `globalSafetyContent.holdRouteBindings` tới `urgentStop`, đúng `pauseToday` reason hoặc `playerSafety.painResponse`, không có `safety.blocked.*` alias;
- `safety_screen_shown.route_id` map đúng `blocked_red_flag|blocked_acute_illness|blocked_new_or_worsening_pain_or_injury|blocked_medically_restricted|blocked_post_session_new_or_worse_pain` và đi cùng current global digest; event có `message_key` phải bị reject;
- không dùng generic pain copy cho reason khác;
- kind không làm xuất hiện routine/CTA tiếp tục;
- kind missing/corrupt fail closed với safe-state copy đã review và tạo diagnostic code không chứa health value.

### QA-014 — Routine authorization và selection

| ID | Trace | Given | When | Then |
|---|---|---|---|---|
| `QA-RTN-001` | SAF-051 | effective Recover | deep link routine Maintain/Build | từ chối tạo session |
| `QA-RTN-002` | SAF-051 | effective Maintain | chọn Maintain hoặc Recover | cho phép; Build bị từ chối |
| `QA-RTN-003` | SAF-051 | không effective mode/hold/pending gate | mọi entry point tạo session | không tạo session |
| `QA-RTN-004` | SAF-052 | một routine chưa từng complete | default select | chọn routine chưa complete |
| `QA-RTN-005` | SAF-052 | cả hai cùng completed-state | default select | chọn `last_completed_at` cũ hơn |
| `QA-RTN-006` | SAF-052 | lịch sử hòa/null | default select | chọn ID ASCII nhỏ hơn |
| `QA-RTN-007` | SAF-050 | selected routine cần điểm tựa, người dùng báo không có | pre-flight | yêu cầu chọn bài khác; không tự infer/fallback |
| `QA-RTN-008` | SAF-050 | context thay đổi | evaluate engine | outcome/effective mode không đổi vì context không là input |
| `QA-RTN-009` | FR-032/ARC-020/MET-010 | Start thiếu/forged/stale/reused/wrong-process/routine/content/ack/context attestation hoặc profile/event store không tạo được valid envelope; chạy cả khi hold đang active và khi domain state otherwise-clear | command adapter nhận Start | trả `CONTRACT_ERROR` trước trusted boundary, không Session, không `routine_start_blocked`/draft event ID và không chạy domain-gate precedence; attestation/envelope data không persist/export |
| `QA-RTN-010` | FR-032/ARC-020/MET-010 | Attestation + event boundary hợp lệ; lần lượt domain có hold/pending/recovery/re-ack/schedule/mode block hoặc DB/event commit fail | adapter claim one-shot proof rồi gọi serialized authorization transaction | trusted domain block ghi đúng một `routine_start_blocked` theo precedence/gate/reason; rollback không để event/session nửa vời. Proof đã claim không được phục hồi cho bất kỳ block/rollback nào; retry cùng proof bị từ chối trước boundary và không tạo event thứ hai |

### QA-015 — Eligibility/intended-use boundary

| ID | Trace | Given | When | Then |
|---|---|---|---|---|
| `QA-ELG-001` | FR-001/SAF-003 | age chưa xác nhận | cold start/deep link routine | age gate bắt buộc; không check-in/routine/reminder |
| `QA-ELG-002` | FR-001/DATA-001 | người dùng chọn chưa đủ 18 | submit | safe exit; chỉ xử lý câu trả lời trong RAM, không tạo dữ liệu sử dụng/health profile |
| `QA-ELG-003` | FR-002/SAF-003 | người dùng đủ tuổi | xem scope confirmation | nói rõ general wellness, dừng khi không ổn, không dùng cho individualized rehab/post-op/pregnancy-postpartum/chronic-condition/medical-restriction guidance |
| `QA-ELG-004` | FR-002/SAF-003 | scope chưa được xác nhận | Back/relaunch/deep link | không vào daily flow |
| `QA-ELG-005` | SAF-003/DATA-007 | onboarding | inspect form/storage/export | không hỏi/lưu diagnosis list, medication, pregnancy/chronic detail, medical record hoặc free text |
| `QA-ELG-006` | FR-002/CNT-015/SAF-051 | profile đã acknowledge manifest/global-safety digest cũ, binary bundle signed artifact mới | cold start/Home/deep link sau khi xử lý hold→pending pain→active recovery | `SCOPE_REACK_REQUIRED`; không check-in/start; giữ original onboarding/activation anchor và old acknowledgement audit |
| `QA-ELG-007` | FR-002/CNT-015 | scope re-ack screen của artifact mới | user chủ động xác nhận | append/current acknowledgement map exact `manifestVersion + globalSafetyContentDigestSha256`; không dùng app/root-manifest/routine digest; daily flow chỉ mở sau commit |
| `QA-ELG-008` | FR-002/003/004, ARC-020/024, MET-010/013 | eligible scope đã staged nhưng chưa save; inject fail tại profile, first acknowledgement/pointer, initial ScheduleVersion/active pointer, từng staged event, `scope_acknowledged`, `work_schedule_saved`, `onboarding_completed`, ordinary/companion ref/HMAC/retention | bấm initial `Lưu lịch`, retry/export/permission | zero hoặc full transaction: profile + first ack/current pointer + enabled initial schedule/active pointer và toàn event bundle cùng installation ID; scope/schedule/activation mirrors exact. Mọi fail ở lại lịch, zero permission attempt/launcher và không tạo entity/event nửa vời. Retry không duplicate; sau full commit primer mới render, permission outcome không đổi activation anchor |

## 3. Exhaustive, property và mutation

### QA-020 — Generated matrix

CI phải generate toàn bộ `2 × 2 × 4 × 3 × 3 × 3 × 3 = 1.296` case của tích Descartes valid:

```text
safety_lock_active: false/true
red_flag: false/true
acute_issue: 4 enums
energy: 3 enums
stiffness: 3 enums
intent: 3 enums
day_mode_cap: null/MAINTAIN/RECOVER
```

Thêm single-invalid/missing cho từng field theo đúng lazy first-match. Case acute hợp lệ khác none phải được test với mọi later field missing/invalid để chứng minh safety-first. Case lock/red flag phải được test với payload sau corrupt để chứng minh short-circuit.

### QA-021 — Property suite

Chạy toàn bộ 20 invariants SAF-072. Mỗi PR chạy ít nhất 10.000 generated cases với seed tái lập; nightly dùng seed ngẫu nhiên và lưu counterexample. Counterexample safety là P0.

### QA-022 — Mutation suite

Test phải kill các mutation:

- đảo lock/red-flag/acute/missing/rest/recover/build precedence;
- coerce missing/invalid thành false/none/Recover;
- kiểm later missing trước acute issue hợp lệ;
- cho cap đổi outcome hoặc nâng mode;
- cho pain=yes tạo cap hoặc mở routine nhẹ hơn;
- bỏ pending pain guard ở deep link/restore;
- cho resubmit xóa hold;
- map sai reason-specific blocked copy;
- cho selector history đổi mode hoặc infer context;
- tính lại hold/cap expiry theo timezone mới.

Mutation sống sót trong safety/cap/session guard là release blocker.

### QA-023 — Event correlation, timing và aggregation

| ID | Trace | Given | When | Then |
|---|---|---|---|---|
| `QA-MET-001` | MET-012/023 | người dùng mở check-in rồi thoát trước submit | export/validate event graph | `check_in_started` chỉ có immutable `check_in_flow_id` non-FK; không tạo CheckIn draft, entity-ref hoặc dangling FK |
| `QA-MET-002` | MET-023 | check-in được submit | join funnel | `check_in_started` và `check_in_submitted` có cùng `check_in_flow_id`; chỉ submitted có canonical `check_in_id`, và flow→CheckIn→Decision→Session chain resolve đúng |
| `QA-MET-003` | MET-023/024 | same boot, không overflow; flow có background ngắn hơn/đúng/trên 10 phút | tính check-in/total timing | dùng monotonic delta, không wall; background ngắn/đúng 10 phút vẫn tính, trên 10 phút dùng exact exclusion; unit millisecond và field union đúng schema |
| `QA-MET-004` | MET-023/024 | boot đổi, elapsed rollback hoặc subtraction overflow | ghi/tính timing | không tạo duration giả hoặc wall fallback; exact invalid-clock branch được ghi và sample bị loại kèm count |
| `QA-MET-005` | MET-024 | player play→pause/background→resume, skip step, timer hết rồi user chờ trước CTA Hoàn thành | ghi pause/stop/completed | mọi `elapsed_ms` và completed `duration_ms` bằng `accumulatedActiveMillis`, chỉ tăng ở `STEP_TIMER+PLAYING`; pause/background/transition/CTA wait và planned remainder của step đã skip không được cộng; terminal value freeze |
| `QA-MET-005A` | CNT-012/ARC-014 | DURATION và REPETITIONS step tại remaining `1/999/1_000/1_001 ms`; transition zero/dương; skip tại 0/partial/equality; kill/recover ở từng phase | tick/skip/recover | ceil display và phase change đúng exact equality; reps auto-advance bằng `estimatedSeconds`; callback lateness không carry vào phase chưa render; skip chỉ partial STEP, append ordered `{stepId,activeElapsedMillis}` + mirrored event một lần; persisted `STEP_TIMER\|STEP_TRANSITION\|COMPLETION_CTA_WAIT`, remaining/skip records/accumulated-active resume byte-equivalent, không reset/jump/repeat |
| `QA-MET-005B` | CNT-021/ARC-014 | Demo đang ở media position khác zero; routine lần lượt ở PLAYING, PAUSED, STEP_TRANSITION và COMPLETION_CTA_WAIT | tap `Replay` | Current signed demo seek về media position `0` rồi play; serialized `PlayerCheckpoint`, phase/substate/remaining/transition/active/cadence/skip, Session và event store byte-unchanged. Kill/recovery không persist media playhead và không restart routine step. |
| `QA-MET-005C` | ARC-022/110/111 | (a) valid Session/checkpoint nhưng reboot, expired window hoặc content unavailable/identity mismatch; (b) mutate auth/decrypt/schema/phase/counter/catalog-cross-invariant của Session/checkpoint | relaunch/recover/export | (a) exact reason, one recovery-failed + one abandoned event, `ABANDONED+PENDING`, frozen checkpoint vẫn pass closed export registry; (b) active guard giữ nguyên, zero normal terminal/product event, start bị chặn, export fail closed và UI chỉ DATA_ERROR + explicit full reset/delete. Không reset/fabricate checkpoint hoặc dùng corrupt bytes để tạo retention/event refs |
| `QA-MET-005D` | FR-006/MET-020/ARC-013/111 | source schedule weekdays lần lượt chứa/không chứa ISO day của `started_at.local_date`; đổi active schedule/zone sau start và flip entity/event boolean | start/export/import/metric | start computes true/false chỉ từ immutable source ScheduleVersion + stored start local date; `routine_started` mirror byte-equal. Current schedule/zone không reclassify; flipped entity/event/missing source bị reject trước qualified-day metric |
| `QA-MET-006` | MET-012 | valid samples có odd/even n, tie, empty, n=4/5 và `[3,3,3,5,5,5]` | aggregation v1 | median center/midpoint overflow-safe, sample trên cho `4`; p90 nearest-rank `x[ceil(0.9n)-1]`; n<5 trả null/`insufficient_sample`; threshold so raw value, không đổi bằng interpolation/rounding khác |
| `QA-MET-007` | MET-013/ARC-021 | mỗi event fixture lần lượt có universal/additional/conditional refs, mandated-equality duplicate target và correlation/content ID | writer/import/ref-index round-trip | exact `ref_table` token + BLOB codec, nested acknowledgement resolution và dedup edge-set khớp matrix; missing/extra/wrong-type edge hoặc envelope/property duplicate rollback/reject; correlation ID không tạo FK |
| `QA-MET-008` | MET-010/010A/013/ARC-021 | với đủ 48 event, mutate lần lượt missing/null required envelope slot, non-null từng forbidden slot, duplicate envelope ID trong properties, conditional hold/safety-screen/routine-started/runtime-cap-projection branch, source unknown/case; rồi mutate property enum/null/XOR/mirror/feedback/snooze/merge và cap source ref | write và import | generated mask chứng minh mỗi event thuộc đúng một row và writer/importer cùng reject mọi mutant; projection snapshot non-null iff runtime<Decision effective, có exact dedup mode-trigger/expiry-source edges. Không loose-map coercion/default/normalization; valid event round-trip byte-stable và idempotency key không tạo duplicate |
| `QA-MET-008A` | MET-010/013/014 | `reminder_scheduled` fixed/snooze fixtures; lần lượt omit/null mỗi required branch key, add null/non-null key của branch kia, đổi snooze ordinal thành âm/1, omit `supersedes_occurrence_id` hoặc đổi null↔absent/non-null | write/JCS/import | fixed exact keys chỉ logical-key/nonnegative-generation/creation; snooze chỉ parent + integer literal `ordinal=0`; common supersedes luôn present nullable nhưng snooze bắt buộc null. Mọi wrong-literal/null-placeholder/omission/extra mutant fail trước JCS/HMAC; valid branch round-trip byte-stable |
| `QA-MET-009` | MET-010/014/ARC-021 | generated fixture duyệt mọi row `AT_MOST_ONCE`, shared domain và `REPEATABLE_BY_EVENT_ID`; hai writer concurrent/retry cùng command, rồi payload/ref/name collision | insert/export/import | logical JCS preimage deterministic; on-device physical key là exact 32-byte `HMAC-SHA-256(K_event_idem_v1,preimage)` với version `1`. Đúng một event thắng, retry equivalent idempotent, same physical key khác payload/ref/name trả `IDEMPOTENCY_CONFLICT`; repeatable observation reuse draft event ID. Offline export validator dedupe canonical `(domain,parts)` trong RAM, không cần/ghi public hash hoặc physical key |
| `QA-MET-009A` | MET-014/SEC-002 | Hai clean dataset dùng cùng canonical event IDs/selectors/commands nhưng hai Keystore HMAC key khác; thêm legacy public-SHA row, missing alias và tamper version/key | forensic DB/on-device read/full delete | physical keys giữa dataset khác nhau và không bằng public SHA candidate; offline reader có plaintext UUID/ref + 48 public domain vẫn không dictionary-test taxonomy nếu không dùng Keystore. Legacy/unversioned/mismatch/missing-key-with-existing-DB fail closed; không regenerate trên rows. Full delete xóa alias trước DB, dataset mới sinh key mới |
| `QA-MET-010` | MET-054/056 | cho Gate 1 threshold 17 và Gate 2 threshold 12, generated mọi `s,u` với `0<=s+u<=24`, gồm các biên `T-1/T` | classify gate/overall decision | `s>=T→PASS`; `s<T` và `s+u<T→FAIL`; `s<T<=s+u→INSUFFICIENT_DATA`. Safety trigger luôn `STOP_FOR_SAFETY_REVIEW`; sau đó insufficient thắng fail, all-pass mới Continue, fail đã xác định mới Iterate |
| `QA-MET-010A` | MET-012/027/054/056 | generated Gate-3 `K,u` quanh `n=4/5`, odd/even midpoint và known count `0/4/8`; với dataset nhỏ enumerate từng unknown là `inactive` hoặc active count `0..12` | classify gate/overall decision | production duyệt đúng `a=0..u` với `K+0^a` và `K+8^a`, khớp exhaustive oracle: mọi low pass → `PASS`, mọi high fail → `FAIL`, còn lại → `INSUFFICIENT_DATA`; tuple audit đủ cho từng `a`. Sentinel-high=`4`, unknown-as-zero/inactive và median rounding mutants đều fail; overall precedence giữ nguyên |
| `QA-MET-010B` | MET-011/012/054/056 | Gate 4/5/6 với denominator `4/5`, equality `7/10`,`8/10`,`9/10`, rational ngay dưới/trên threshold, overflow operands và Context `16/23` | classify gate/overall decision + render | denominator `<5` insufficient; `>=5` dùng checked/BigInteger `100*numerator >= T*denominator`, equality PASS, false FAIL. `16/23` hiển thị half-up `70%` nhưng Gate 4 FAIL; float/rounded-percent/overflow mutants fail |
| `QA-MET-011` | MET-030/040/ARC-019 | Weekly summary synthetic raw graph, denominator `0/4/5`, half-percent tie, timezone change/recompute; mutate ID/key/count/rate/null-reason/event mirror | build/export/import | exact stable `WeeklySummaryWireV1` có 13 count + ba rate; n<5 iff null/`insufficient_sample`, n>=5 integer round-half-up exact; numerator<=denominator. Recompute giữ summary ID/initial zone/cutoff, event ID/week/count mirror và refs resolve; missing/extra/alias/float/numeric-string/cache-vs-raw mismatch bị reject |

## 4. Lifecycle, time và resilience

### QA-030 — Atomicity/negative persistence

- Disk full/write denied khi tạo hold: không báo submit thành công, không resolve pending gate, không routine mới.
- Process bị kill tại từng write boundary: state trước hợp lệ hoặc transaction sau đầy đủ; không half-state.
- Ciphertext/tag/schema/key lỗi: fail closed, không tạo recommendation từ state không xác thực.
- Hold/cap trùng/corrupt: không chọn bản thuận lợi hơn; repository fail closed và không log payload.
- Pending gate và terminal session phải cùng transaction; không có terminal session “lọt” mà pain được mặc định no.
- Asset/message/checksum lỗi: routine unavailable; không stream fallback từ mạng/AI.

### QA-031 — Timezone, expiry và freshness

Chạy zone `Asia/Ho_Chi_Minh`, `America/New_York`, `Europe/London`, `Asia/Kolkata`:

| ID | Trace | Given | When | Then |
|---|---|---|---|---|
| `QA-TIME-001` | SAF-046 | hold/cap tạo 23:59 origin zone | tới đầu ngày kế | expiry đúng local midnight; không giả định 24h |
| `QA-TIME-002` | SAF-046 | state tạo trước DST spring-forward | clock qua ngày | expiry đúng start-of-next-local-date |
| `QA-TIME-003` | SAF-046 | state tạo trước DST fall-back | clock qua ngày | không sai do giờ lặp |
| `QA-TIME-004` | SAF-046 | state tạo zone A | đổi sang zone B | origin zone/local_date/absolute expiry không đổi |
| `QA-TIME-005` | SAF-046 | hold/cap active trong cùng boot | chỉnh device clock lùi/tiến | elapsed-time guard giữ constraint; không sửa expiry/rút ngắn/bypass |
| `QA-TIME-006` | SAF-046 | cùng boot/continuity hợp lệ, `elapsedRealtime == monotonic_deadline_ms` | evaluate | hold/cap inactive; check-in ngày trước vẫn không được dùng |
| `QA-TIME-006A` | SAF-046 | wall instant bị chỉnh tiến tới `expires_at_utc` nhưng monotonic còn trước deadline | evaluate | hold/cap vẫn active; wall equality đơn lẻ không clear sớm |
| `QA-TIME-007` | FR-012 | cùng boot/generation/zone, elapsed đúng TTL monotonic 6 giờ | mở recommendation | `RECONFIRM_REQUIRED(reason=ttl)`; bắt reconfirm toàn bộ field trước rule/session |
| `QA-TIME-007A` | FR-012 | cùng boot/generation/zone, elapsed còn `6h-1ms`, trước work end | mở recommendation | decision còn fresh nếu mọi guard khác hợp lệ |
| `QA-TIME-007B` | FR-012 | boot giữ nguyên nhưng generation/zone đổi hoặc wall-vs-elapsed mapping drift `>2_000 ms` | mở recommendation | `RECONFIRM_REQUIRED(reason=timezone_or_time_change)`; không dùng wall deadline để giữ decision cũ |
| `QA-TIME-007C` | FR-012 | boot marker đổi, elapsed rollback hoặc phép tính continuity overflow dù evidence đúng schema | mở recommendation | `RECONFIRM_REQUIRED(reason=clock_unknown)`; không dùng wall time để đoán; corrupt/missing evidence riêng là `CONTRACT_ERROR` |
| `QA-TIME-008` | FR-012 | check-in còn trẻ nhưng `now` nằm ngoài current active work window | mở routine | `EXPIRED`; không dùng check-in cũ, không start |
| `QA-TIME-008A` | FR-003/012/SAF-013 | check-in/decision tạo dưới schedule version A, user lưu schedule version B (kể cả chỉ sửa reminder) | mở/start routine, dù old window còn hợp lệ | `RECONFIRM_REQUIRED(reason=schedule_changed)`; không authorize bằng window A và không tạo Session mang version B từ Decision A |
| `QA-TIME-008B` | FR-012 | check-in thuộc local date trước, current date mới nhưng `now` nằm trong current active work window | mở routine | `RECONFIRM_REQUIRED(reason=local_date_changed)`; check-in mới có thể tiếp tục |
| `QA-TIME-009` | SAF-046 | reboot làm boot marker đổi và wall clock không đáng tin | reconcile | fail conservative, không clear sớm; có thể giữ constraint lâu hơn và ghi diagnostic redacted |
| `QA-TIME-010` | MET-020/027 | onboarding anchor ở giữa ngày làm seven elapsed study blocks 8–14 chạm tám `local_date`; mỗi date có qualifying session với valid start evidence | tính primary pilot | `qualified_study_days_week_2` dedupe theo study-day index, nằm trong `0..7` và bằng số block đủ điều kiện, không bằng tám local dates; weekly UI vẫn dedupe theo calendar `local_date` |
| `QA-TIME-011` | DATA-103/ARC-024 | record có retention origin ở zone A, thiết bị đổi sang zone B; instant ngay trước/đúng canonical origin-zone deadline | chạy maintenance | trước deadline không purge; tại equality chỉ eligible khi graph/FK/logical refs đều hết; device-current date và plaintext prefilter không được authorize xóa sớm hoặc bỏ sót row đã due |
| `QA-TIME-012` | DATA-103/ARC-024 | source graph nhận nhiều cutoff candidate khác zone/instant | extend retention | chỉ adopted cutoff có `deadline_at_utc` muộn hơn được thay; equality giữ existing provenance; cutoff không giảm và purge recompute/verify encrypted contract trước delete |
| `QA-TIME-013` | DATA-103/ARC-024/MET-030 | weekly summary có `week_start_local_date/week_zone_id`, được recompute nhiều lần | tới trước/đúng mốc start-of-day của week start + 13 tuần trong week zone | recompute không trượt retention; chỉ equality mới eligible và vẫn phải qua exact graph/source checks |
| `QA-TIME-013A` | DATA-103/ARC-024/MET-004 | profile/ack sống đến full delete; Session finite có `routine_started`; mọi event có universal AppProfile ref | chạy directed authority closure tới ngày 91 | chỉ onboarding/scope-ack/re-ack companion nhận `UntilFullDelete` + null prefilter; Session/routine event vẫn finite. Ordinary AppProfile ref không reverse-promote; finite/full/prefilter mismatch fail closed |
| `QA-TIME-013B` | DATA-103/ARC-024/MET-004 | Session ngày 0 có start/skip/terminal; late feedback + hold/cap ngày 89; WeeklySummary base 91 ngày; snooze chain A→B→C với multi-source edge ở mỗi hop | extend rồi export/purge tại từng `deadline-1ms`, equality và ngày 91 | Session→Decision→CheckIn→Schedule cùng mọi required lifecycle/side-effect companion được nâng atomically; weekly generated companion sống cùng row. Deletion set xen kẽ source→all event→all peer source tới least fixed point nên A/B/C và toàn companion của peer được xét, không kẹt one-hop edge. Missing/thừa edge/event/mirror không được coi là expiry hợp lệ; set không co-delete AppProfile/shared ordinary dependency |
| `QA-TIME-013C` | ARC-024/MET-030 | WeeklySummary cố định deadline `week_start+13 weeks`; recompute + view ở ngày 80 | ghi `weekly_summary_generated\|viewed`, rồi maintenance ở fixed deadline | Cả hai event copy exact summary origin/calendar/deadline, không dùng event-day+90; summary/event vẫn due tại fixed boundary, không trượt tới ngày 170. Tại/e sau deadline không emit/recompute |
| `QA-TIME-014` | SAF-040/041/047/ARC-014 | lần lượt terminalize Session thành `COMPLETED`, `STOPPED`, `ABANDONED` | persist/export/validate event | cả ba Session có cùng-shape `terminal_at`, origin-day expiry, five-field clock evidence và exact four `completion_*` terminal-anchor fields từ một snapshot; chỉ `routine_completed` mirror `completion_*`, còn stopped/abandoned event có field đó phải bị reject |

Clock test dùng injected clock/ZoneId; không sleep thời gian thật. Cùng boot, monotonic evidence phải phát hiện wall-clock jump. Sau reboot/clock discontinuity không xác minh được, session guard giữ fail-closed cho tới state reconciliation theo architecture; conservative extension được phép, clear sớm thì không. Không mặc định hold/cap expired chỉ từ timezone/clock mới.

### QA-032 — Process death/reboot

- Hold active → force-stop/reboot → vẫn Blocked.
- Cap active → force-stop/reboot → cap còn đến effective deadline do clock-integrity resolver xác lập; audit `expires_at_utc` bất biến và conservative extension có thể kéo dài qua instant đó.
- Pending pain gate → force-stop/reboot → route pain gate trước session.
- Active session sau same-boot process death, vẫn cùng local date/trước work_end/content-state valid/clock continuous → chỉ recovery Resume/End session đó; guard chặn session khác; không auto-complete.
- Active session schema-valid sau reboot/clock discontinuity, qua local date/work_end hoặc content unavailable/identity mismatch → atomically `abandoned` + pending pain trước khi xét session mới. Auth/decrypt/schema/checkpoint invariant corrupt giữ active guard, zero normal event và route DATA_ERROR/full reset; không tạo terminal checkpoint giả.
- `paused` là substate của active session, không được persist/đếm như terminal status riêng.
- Kill ngay sau pain=yes submit ở mọi transaction boundary → không có state cho phép session mới.
- Lock/cap hết khi app tắt → mở ngày mới bắt check-in mới, không reuse decision/routine cũ.

### QA-033 — Offline

| ID | Given | When | Then |
|---|---|---|---|
| `QA-OFF-001` | airplane mode từ cold start | onboarding/check-in/rule/select/start/finish/feedback | toàn bộ core flow hoạt động local |
| `QA-OFF-002` | mất mạng giữa player | pause/resume/stop/pain gate | timer/media/safety control không mất |
| `QA-OFF-003` | offline | mở cả sáu routine | text/transcript/media/timer từ bundle, checksum pass |
| `QA-OFF-004` | offline | export/delete | hoàn tất không login/paywall/network |

Production binary không khai báo `INTERNET`; test network capture vẫn chạy để phát hiện SDK/socket ngoài ý muốn.

## 5. Permission và notification

### QA-040 — Permission allowlist

Binary/merged-manifest scan phải chứng minh:

- merged manifest có đúng permission allowlist `{POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED}`; chỉ `POST_NOTIFICATIONS` có runtime request và chỉ khi Android version yêu cầu, còn `RECEIVE_BOOT_COMPLETED` chỉ phục vụ local schedule reconcile sau boot;
- không có Health Connect/HealthKit tương đương, calendar, location, activity recognition, contacts, microphone, camera, storage broad permission hoặc `INTERNET`;
- dependency không merge permission ngoài allowlist.

### QA-041 — Notification Given/When/Then

- Given app launch/onboarding chưa chọn giờ, when mở app, then không system permission prompt.
- Given người dùng đã chọn lịch, xem explanation, OS adapter xác nhận runtime dialog launchable và bấm CTA hộp thoại, then encrypted attempt + đúng một `notification_permission_prompted` phải commit **trước** system launcher; write fail thì không mở dialog.
- Given Allow hoặc callback false sau Deny/Dismiss, when callback commit, then cùng `attempt_id` được resolve đúng một lần và `notification_permission_updated(source=system_prompt)` ghi `prompt_result=granted|not_granted`; app không suy tách Deny với swipe-Dismiss từ trạng thái OS. Duplicate/late callback không tạo result/event thứ hai.
- Given process chết trước callback, when relaunch bằng process-instance mới, then app atomically chuyển attempt cũ `PENDING→INTERRUPTED(reason=process_recreated_before_callback)` mà không fabricate prompt result/system-prompt update; late callback không được rebound. Automatic onboarding prompt vẫn at-most-once vì attempt cũ còn audit; sau transition này explicit user retry mới có thể tạo ID `PENDING` mới, và luôn chỉ có tối đa một pending attempt.
- Given OS adapter yêu cầu Settings thay vì runtime dialog, when bấm `Mở Settings`, then không tạo PromptAttempt/`notification_permission_prompted`; same-process `ON_START` đầu tiên consume navigation token đúng một lần và ghi `notification_permission_updated(source=settings, attempt_id=null, prompt_result=null)` với current state, kể cả grant hoặc back/no-change. Process death làm mất token; cold start chỉ ghi observation `source=resume_check` với attempt/result null. Mọi nhánh không để `PENDING` mắc kẹt và explicit runtime-dialog retry sau đó vẫn dùng ID mới.
- Given unavailable/deny/“don’t ask again”, when dùng app, then check-in/routine/export/delete vẫn đầy đủ; current permission render từ OS, không từ event/cache và không prompt loop.
- Given permission revoked trong Settings, when app resume, then ghi observation `source=resume_check`, reconcile state, không crash và không đổi rule outcome; event history không được dùng làm permission authority.
- Given hold active/pending pain gate, when occurrence tới hạn, then notification không deep-link bypass safety; nội dung lock-screen vẫn trung tính.
- Given snooze chạm biên work window hoặc timezone change, then không phát ngoài `[work_start, work_end)`, không phát bù occurrence lỡ và không tự tăng tần suất.
- Given receiver chạy tại `due+60m` và vẫn trước `work_end`, then occurrence còn được phép post; tại `due+60m+1ms`, then `SKIPPED_LATE` và không phát bù.
- Given fixed/snooze occurrence cách nhau `<=30m`, then chỉ occurrence có due time sớm hơn post (nếu cùng due thì snooze thắng), occurrence kia `MERGED` và không vào denominator delivered.
- Given snooze lúc `10:03:17.250` tạo target `10:18:17.250` và fixed kế tiếp `10:30:00.000`, then pair vẫn merge theo exact instant; event ghi `distance_ms=702750`, không đòi chia hết cho phút hoặc floor/round.
- Given hai fixed slot hợp lệ cách nhau `<=30m`, then cả hai vẫn là occurrence riêng và có thể post; quy tắc merge 30 phút chỉ áp cho cặp snooze-vs-next-fixed, không fixed-fixed.
- Given schedule wire lần lượt `09:05`, `9:05`, `09:05:00`, có second/nano khác zero hoặc reminder array chưa sort/trùng, then chỉ exact zero-padded `HH:mm` với domain second/nano zero và distinct sorted array được nhận; parse→serialize phải byte-identical. Snooze target vẫn được giữ full millisecond.
- Given fixed generation 0 còn tương lai đã terminal `CANCELLED|BLOCKED_PERMISSION`, when guard/permission clear, then reconcile tạo generation 1/ID mới với `supersedes_occurrence_id`; row/event cũ bất biến và tối đa một generation pending cho logical slot.
- Given fixed logical key lần lượt chưa có row, có latest eligible terminal và đã có pending `SCHEDULED`, when allocator chạy/retry hoặc fail/kill ở row/event/HMAC/ref/retention boundary, then hai nhánh insert tạo `initial|slot_reeligible` row + đúng một `reminder_scheduled` atomically; reuse trả cùng row và zero event; failure để zero row/event/alarm. Platform chỉ schedule từ full post-pair pending query.
- Given source notification đã tạo một snooze child pending, when người dùng/OS gửi callback duration khác hoặc duplicate callback của cùng source, then callback sau trả `SNOOZE_NOT_ELIGIBLE`, zero row/event/alarm; pending child và fixed pair hiện tại không đổi.
- Given snooze `10:18` thắng và fixed `10:30` ngày D đã `MERGED`, when cold-start/resume generic reconcile chạy trước hoặc sau khi snooze winner deliver nhưng fixed due cũ vẫn ở tương lai, then logical key D vẫn consumed: không generation/`slot_reeligible` mới và không post lần hai. Reconcile bounded scan bỏ D, tạo/reuse fixed candidate selected-date đầu tiên sau D cho cùng slot và schedule nó.
- Given snooze child B đã `DELIVERED`, when user snooze notification mới của B, then tạo child C literal ordinal 0 dưới parent B; source A/B vẫn DELIVERED, không replace child cũ, không restore fixed MERGED và chain không cycle.
- Given occurrence `DELIVERED` được người dùng snooze, when commit action, then source row vẫn `DELIVERED`; app insert child row `SNOOZED` có parent/ordinal/ID riêng. Chỉ child chuyển trạng thái sau đó; posted invariant/denominator của parent không bị viết lại.
- Given initial one-shot snooze, child-delivered snooze chain, pair/no-pair và fixed date đã MERGED, when inject kill/write/HMAC/ref/companion-retention failure ở từng boundary hoặc chạy hai caller concurrent, then mỗi transaction chỉ zero hoặc full bundle: child mới có đúng một `reminder_scheduled`, accepted action có đúng một `reminder_snoozed`, loser mới MERGED có đúng một `reminder_merged`; không replaced-child/fixed-restore event, orphan hoặc extra event. Sau commit schedule exact **full** post-pair pending set; no-overlap có thể chứa cả fixed và snooze, không ép một singular winner.
- Given DB bundle đã commit nhưng kill/reboot trước/sau từng registry-add/AlarmManager set/cancel, với unpaired snooze, snooze thắng pair, fixed thắng pair và no-overlap hai pending, when cold-start/resume reconcile, then query exact union active-schedule `SCHEDULED|SNOOZED`, schedule idempotent mọi pending row, cleanup terminal loser và không child mắc vĩnh viễn/duplicate/resurrect.
- Given DELIVERED source action đua lần lượt schedule edit/disable, permission revoke, hold, Rest, active session hoặc pending pain, when chạy cả hai serialization order, then blocker-first trả `SNOOZE_NOT_ELIGIBLE` với zero child/event/alarm; snooze-first commit full bundle rồi blocker transaction terminalize child bằng exact reason/event. Pain-no chỉ reschedule future fixed, không resurrect snooze.
- Given cùng exact duration PendingIntent bị callback hai lần/queued sau cleanup, hoặc command duration không khớp action/data URI/tag active/registry kind, when receiver lấy delivery lease, then chỉ callback đầu đủ điều kiện có thể commit child ordinal 0; callback sau/mismatch trả `SNOOZE_NOT_ELIGIBLE` và zero row/event/alarm. Snooze tiếp chỉ dùng notification identity của child mới đã DELIVERED, không reuse token source cũ.
- Given receiver đua với từng blocker/cancel ở trên, hoặc kill ngay sau `notify()` trước DELIVERED CAS, when chạy shared cross-component delivery lease/recovery, then chỉ một linearization order: blocker-first không post; receiver-first commit đầy đủ rồi blocker cleanup visible tag; crash gap để pending row + registered identities, next holder cancel uncertain tag trước recheck và repost cùng stable tag chỉ khi still eligible. Start action luôn rerun guard; không tuyên bố exactly-once DB↔Android service.
- Given registry chứa từng ALARM và notification-kind universe qua pending→claim→delivered→open/delete/snooze/OEM disappearance/terminal/purge, when reconcile/kill ở mỗi add/cancel/remove boundary, then exact live-set giữ ALARM chỉ cho pending scheduled; DELIVERED active giữ base CONTENT/START/DELETE + đúng subset duration có `delivered_at+d<work_end`; state khác zero. Missing expected entry/action mismatch fail closed, extra cleanup no-create trước durable-remove, open/delete/snooze cleanup đúng và không fabricate dismiss. Capacity cleanup chạy trước boundary 4096; inactive entries được thu hồi, truly-full live set không post/drop tracking.
- Given notification occurrence thuộc schedule A đã `DELIVERED`/tap thành công, rồi user sửa lịch và reconfirm dưới schedule B, when bắt đầu routine B từ navigation context cũ, then start transaction normalize `source=home, reminder_occurrence_id=null`; chỉ giữ reminder attribution khi occurrence resolve, vẫn `DELIVERED`, có `first_opened_at`, navigation context đã validate và occurrence/CheckIn/Decision/Session cùng schedule ID. Normalization không tự block routine; cửa sổ attribution 60 phút chỉ được áp khi tính metric.
- Given outcome `REST_ONLY`, when còn fixed slots trong origin day, then hủy/skip toàn bộ slot còn lại, không phát bù và không tạo safety hold.
- Given Rest suppression active, when manual check-in mới cho `RECOVER|MAINTAIN|BUILD`, then atomically supersede/clear suppression với `new_result=mode` và chỉ schedule fixed slot còn tương lai nếu schedule/permission/guard khác cho phép; slot đã qua không quay lại.
- Given Rest suppression active, when manual check-in mới lại cho `REST_ONLY`, then atomically thay bằng suppression của Decision mới, ghi `new_result=rest`, không schedule/post reminder.
- Given Rest suppression active, when manual check-in mới cho `URGENT_STOP|PAUSE_TODAY`, then atomically supersede Rest bằng `new_result=safety`, tạo đúng hold và tiếp tục không nhắc; `INCOMPLETE`/contract failure thì giữ suppression cũ, không schedule.
- Given Rest suppression active, when app/deep link mở manual check-in, then không chặn; suppression chỉ inactive khi clock adapter xác minh đã tới effective deadline của origin day theo `SAF-046`, không chỉ vì wall date/timezone hiện tại đổi.

## 6. Export, delete, privacy và security

### QA-050 — Export

Export luôn miễn phí, chạy offline qua Android Storage Access Framework và có explicit user action. Test:

- JSON UTF-8 có schema version, export timestamp và đầy đủ record còn retention. Profile fixture phải có exact `installation_id`, `adult_confirmed=true`, `eligibility_scope_confirmed=true`, `locale=vi-VN`, activation stamp/evidence, nonempty ordered acknowledgement history và append-last current pointer; false/missing/alias/extra/wrong-kind-order/pointer mutant bị reject, còn `profile=[]` chỉ pass khi user-data/event graph rỗng. Schedule, check-in/decision, session/feedback, hold/cap/projection snapshot, reminder và exact weekly-summary 13-count/3-rate DTO theo contract DATA;
- generated closed-schema suite duyệt từng key của `WorkScheduleWireV1|CheckInWireV1|DecisionWireV1|SessionWireV1|FeedbackWireV1|ReminderWireV1`: remove/add/alias/duplicate, wrong scalar type, null flip, enum case, fixed-vs-snooze opposite branch, canonical row-ID rename và reordered/duplicate semantic array đều bị reject trước graph validation. Valid export canonical re-encode giữ deterministic key/collection order; v1 không ignore unknown field, unsupported version không bind bằng v1;
- `LocalStamp` giữ origin UTC/local_date/ZoneId/UTC offset; enum/version không bị dịch thành UI text;
- export empty/large/special Unicode hợp lệ, không OOM/corrupt;
- mọi instant ở metadata/entity/event/nested snapshot round-trip exact `YYYY-MM-DDTHH:mm:ss.SSSZ`; generated mutant numeric epoch, `+00:00`, lowercase/space, fraction khác ba digit, invalid leap/date/year-zero/leap-second hoặc non-millisecond domain value đều bị reject trước normalization;
- không có encryption key, OS token, reviewer credential, diagnostic nội bộ, temp plaintext hoặc secret;
- hủy picker/không có URI phải discard `export_id` chỉ ở RAM, không tạo file và không ghi `export_started|export_completed|export_failed`; destination hợp lệ mới ghi `export_started` ngay trước export work và mới vào denominator;
- inject lần lượt snapshot-read, JSON-encode, open, write, flush, close, provider và security failure sau khi có destination: chỉ ghi một `export_failed` với exact code tương ứng `snapshot_read_failed|json_encode_failed|destination_open_failed|destination_write_failed|destination_flush_failed|destination_close_failed|provider_failed|security_denied`; first primary failure thắng cleanup-close, còn trong cùng primary failure security thắng provider và provider thắng operation-stage. Unknown/case/alias/extra raw exception, provider name, URI hoặc path bị writer/importer reject;
- mọi failure sau khi có URI phải best-effort đóng stream, không ghi `export_completed`, cảnh báo provider có thể giữ file chưa hoàn chỉnh để người dùng tự xóa; riêng close failure sau write+flush thành công vẫn là failed; chỉ write+flush+close đều thành công mới ghi `export_completed`; app không tạo plaintext temp/cache ngoài đúng URI đã chọn;
- app giải thích sau export app không còn kiểm soát bản copy ngoài sandbox.

### QA-051 — Delete all

Delete all không paywall và phải:

1. confirm scope rõ;
2. chặn write/schedule mới và hủy notification;
3. xóa Room/WAL/SHM, preferences nhạy cảm, pending gate, hold/cap, session, temp export, diagnostics và app encryption keys;
4. reset onboarding và eligibility/safety acknowledgement; chỉ giữ immutable bundled content;
5. không resurrect sau relaunch/reboot/reinstall/OS restore;
6. black-box search theo canary values không tìm thấy trong app-controlled storage/log/cache.

Crash-cancellation matrix bắt buộc dùng exact `PendingIntentIdentityRegistryV1`/`DeletionMarkerV1`: inject kill sau marker `MARKED`, trước/sau từng platform cancel, trước/sau durable registry remove, mỗi marker phase, alias delete và file delete. Relaunch/receiver không mở main DB hoặc post khi marker tồn tại; registry keyless vẫn reconstruct exact bảy identity kind, cancel idempotent rồi hội tụ qua `INTENTS_CANCELLED→KEYS_ERASED→FILES_PURGED`. Registry add phải durable trước mọi create/schedule/post và cancel phải xảy ra trước remove; kill không được tạo live unregistered identity. Corrupt/digest/count/order/kind/UUID/trailing-byte/over-cap registry trước `INTENTS_CANCELLED` giữ marker/fail closed, không giả empty. Min/API 26/33/36 verify `FLAG_NO_CREATE` không tìm thấy identity sau completion, AlarmManager không fire và `NotificationManager` trống.

Backup phải disabled/excluded theo DATA/SEC; restore dữ liệu đã xóa là P0.

### QA-052 — Security/privacy

- Verify AES-GCM envelope, unique nonce, AAD binding, Keystore non-exportable key và tamper/tag failure fail closed.
- Network capture theo app UID ở mọi critical flow: zero app traffic; binary không `INTERNET`, ad/analytics/cloud SDK. Traffic của browser/document provider do người dùng chủ động mở nằm ngoài app UID và phải được phân biệt trong evidence.
- `MainActivity` đặt `FLAG_SECURE` trước `setContent`/frame đầu và không clear ở bất kỳ route hoặc lifecycle transition nào. Trên cold start, warm resume và chuyển app background/foreground ở API 26/33/36 cùng OEM matrix: screenshot, screen recording/share/cast và non-secure display phải bị OS chặn hoặc cho frame trống; recent-task thumbnail không chứa app content. TalkBack/keyboard semantics vẫn hoạt động; mục `Về sản phẩm` nói rõ screenshot/chia sẻ màn hình bị tắt vì riêng tư.
- Logcat/crash/diagnostic/notification/clipboard không lộ red flag, acute issue, energy, stiffness, intent, outcome, feedback, routine/history identifier hoặc document URI; app-private diagnostic store không có đường export trong MVP.
- Không có remote analytics SDK/upload. Local event log chỉ chứa allowlisted schema phục vụ summary/pilot, ở encrypted app storage; không advertising identifier/device ID/account và không tự gửi.
- Manifest/asset hash được verify; path traversal, duplicate JSON key, malformed manifest bị từ chối.
- Export URI permission scope/lifetime tối thiểu; plaintext không copy vào shared cache.
- Airplane-mode Settings hiển thị đầy đủ privacy-policy text/version đã duyệt; content digest khớp release evidence. CTA URL dùng external browser, app không có WebView/`INTERNET` và không claim browser sẽ tải được offline.
- Dependency, secret và license scan sạch; critical/high vulnerability phải fix hoặc có security owner disposition nhưng không được waive lỗi ảnh hưởng safety/data.
- Diagnostic codec generated test duyệt đúng 12 event-code, 10 component-code và fixed pair mapping; unknown/sai case/mismatched pair/free text/extra field/noncanonical UTC-ms/SemVer/API/sequence bị reject. Main DB open fail vẫn ghi dedicated no-backup diagnostic DB; user lựa chọn không tạo diagnostic; full delete xóa DB/sidecar.
- Forensic DB test xác nhận physical event unique key có version `1`, là keyed HMAC khác nhau giữa hai dataset dù logical event giống nhau và không match public SHA candidate. Keystore alias missing/invalid khi DB còn event fail closed; full delete xóa alias và không export/public-hash fingerprint.

## 7. Content và accessibility

### QA-060 — Content artifact

Chạy release validator của CNT-060 trên **đúng manifest/asset digest đóng trong binary**:

| ID | Trace | Fixture/biến thể | Expected |
|---|---|---|---|
| `QA-CNT-001` | CNT-001 | Release manifest | Đúng sáu ID/tên/mode/duration/RPE, không routine thứ bảy. |
| `QA-CNT-002` | CNT-013 | Từng routine release | Có approved setup/stop/escalation và explicit contraindication disposition; không placeholder. |
| `QA-CNT-002A` | CNT-011/013 | Mở pre-flight từng routine, đổi routine/content identity, rời route và recreate process | Render global pre-flight trước, rồi exact routine sequence comfortable-range → setup array order → contraindication array order iff LISTED → stop-rule array order → escalation, sau đó mới acknowledgement và context prompts. Không segment nào collapsed/bị bỏ/thay bằng global copy; Start chỉ enable khi current-identity acknowledgement=true và mọi required context=yes. Mọi change/exit/recreate clear acknowledgement + context, bypass không tạo Session. |
| `QA-CNT-003` | CNT-012/020/021 | Release asset bundle trên device matrix | Asset local tồn tại, checksum đúng; poster PNG/WebP có exact byte signature, declared MIME và production decoder format đồng thuận, full-decode đúng intrinsic dimension; MP4 non-fragmented/H.264 metadata, intrinsic dimension, audio-presence bit và first/middle/last-frame decode đúng; exact `ceil(mvhd.duration*1000/mvhd.timescale)` bằng positive-safe-integer `durationMs`; caption canonical WebVTT nằm trong `[0,durationMs]`; primary/easier variation map demo/angle theo từng source step, không dùng flat demo pool. |
| `QA-CNT-003A` | CNT-020/021 | Mutate PNG/WebP swapped MIME, extension đánh lừa, fake/truncated/correct signature + corrupt body, decoder null/generic/bounds-only/format mismatch, intrinsic dimension mismatch; hoặc MP4 zero/unsafe duration, unknown/all-ones duration, timescale zero, float-rounding edge, `moof`/edit-list/external-ref/rotation, metadata dimension/codec/audio mismatch, cue overlap/end `durationMs+1` | Validator fail deterministic với exact asset-format/metadata code; không suy MIME từ extension hoặc chỉ bounds decode. Cue end đúng `durationMs` pass; JVM/reference parser và production device matrix cho cùng integer duration/classification, không tolerance/OS-metadata fallback. |
| `QA-CNT-004` | CNT-030 | Release accessibility content | Transcript/screen-reader/reduced-motion contract đầy đủ. |
| `QA-CNT-004A` | CNT-011/012/030 | Card → pre-flight → Player → mở/đóng Cách dễ hơn; chạy từng player control/state với TalkBack | `titleKey` bind card/pre-flight/Player, `summaryKey` bind card + visible pre-flight overview, variation `titleKey` bind alternative heading. Sáu AccessibilityContract key bind đúng exact element table CNT-030; Pause key không được reuse cho Resume, step-start dùng per-step screen-reader key; missing/swapped/orphan/hard-coded substitute fail integration. |
| `QA-CNT-005` | CNT-041 | Release message catalog | Không có diagnosis/treatment/causal/recovery-score claim; manual review pass. |
| `QA-CNT-005A` | CNT-040A | Generated every key-role/category fixture; mutate category, reuse key cho role xung đột, unknown role và hard-coded substitute; validate catalog/digest | Mọi safety-bearing comfortable/setup/contraindication/stop/escalation/context/global key chỉ `SAFETY`; title/summary, instruction và accessibility role map đúng total matrix. Mismatch fail `CNT_MESSAGE_CATEGORY_MISMATCH`; covered change làm digest đổi và sign-off cũ vô hiệu |
| `QA-CNT-006` | CNT-015/050/051 | Routine/global sign-off graph và credential registry; latest professional sign-off lần lượt nằm ở routine khác hoặc global | Reviewer/jurisdiction ref hợp canonical regex và resolve credential; từng routine author khác routine clinical reviewer, global-safety author khác global clinical reviewer; technique=clinical chỉ pass khi cùng credential thỏa cả hai role nhưng vẫn có hai chữ ký. Tính exact max của 20 timestamp chuyên môn (6×3 routine + 2 global); **cả sáu** `contentQa.checkedAt` phải `>=` max này và `<=validationInstant`. QA-A trước signer muộn ở routine-B/global phải fail dù local chronology A pass; credential/signature còn hạn và approved revision/digest trùng build. Empty/unknown ref, self-approval, future/out-of-order timestamp đều fail. |
| `QA-CNT-007` | CNT-052 | Mọi approval ở validation instant | Không re-review trigger hoặc expired sign-off đang mở. |
| `QA-CNT-008` | CNT-011/SAF-050 | Lần lượt từng field `stableChair\|stableDeskOrWall\|standingSpace\|walkingPath=REQUIRED`; `NOT_REQUIRED`; user chọn No | Field required render đúng signed prompt theo thứ tự; field not-required không hỏi; bất kỳ No chỉ mở selector, không override/persist/infer/auto-fallback. |
| `QA-CNT-009` | CNT-015 | Global safety artifact và mọi route | Mọi typed global slot cho age/scope/re-ack/red-flag/acute-issue/outcome/hold/next-day-recheck/pre-flight/stop/pain-gate resolve đúng role, tuple order/cardinality và literal hold-route binding. `acuteIssueGate` render question từ signed key và đúng ordered enum→label bindings `none`, `acute_illness`, `new_or_worsening_pain_or_injury`, `medically_restricted`; missing/extra/swapped slot, đổi value-label binding, bucket coercion hoặc hard-coded safety copy fail. Home trong post-session pain hold phải render nguyên `playerSafety.painResponse` title + ordered body + shared emergency path, không rút gọn/paraphrase/collapse; chỉ routine CTA bị cấm. Entry/global/sign-off digest khớp, independent global author + clinical reviewer còn hạn; urgent-stop và pain-response cùng render signed emergency target ngay trước CTA, dùng `ACTION_DIAL tel:<same target>`, không auto-call/phone permission, unavailable path dùng signed copy. |
| `QA-CNT-010` | CNT-010/012/021/060 | Mutate manifest graph/path | Duplicate message/step/variation/asset ID, ambiguous reference, orphan asset/message và mọi absolute/traversal/backslash/percent/URI/symlink asset path đều bị release validator từ chối. |
| `QA-CNT-011` | CNT-014 | Đổi text của routine instruction, video transcript hoặc poster alt entry nhưng giữ asset bytes | Routine clinical digest đổi và clinical sign-off cũ fail; referenced message/asset union sort deterministic, mỗi ID/key đúng một lần. |
| `QA-CNT-012` | CNT-014/015/051 | Manifest có đủ sign-off, chạy validator tại fixed `validationInstant` | Golden manifest digest chỉ normalize root digest và từng Content-QA approved-manifest digest thành JSON null; field khác/array order giữ nguyên; `validationInstant == validThrough`, timestamp null/future hoặc validity trên 365 ngày đều fail closed. |
| `QA-CNT-012A` | CNT-010/015/051/DATA-102 | Mutate lần lượt `generatedAt`, mọi authored/verified/signed/valid-through/checked timestamp và `validationInstant` thành numeric epoch, `+00:00`, lowercase/space, fraction 0/1/2/4+, invalid leap/date, year zero/expanded, leap second, trailing byte hoặc non-millisecond domain instant | Chỉ exact InstantWireV1 `YYYY-MM-DDTHH:mm:ss.SSSZ` pass. Mọi mutant fail lexical validation trước compare/JCS/digest/sign-off; validator không normalize alias thành bytes hợp lệ. |
| `QA-CNT-012B` | CNT-052/060A/ARC-015 | Build/runtime loader với valid release evidence ở `validThrough-1ms`; mutate evidence key/type/instant/manifest/validator version/JCS digest/BuildConfig constant, rồi chạy installed APK với device wall trước/đúng/sau validThrough và rollback lớn | Fresh validation equality fail release; valid `-1ms` emits exact evidence bound to manifest và loader replay cùng result. Mọi evidence mismatch fail before Home. Với unchanged signed APK/evidence, device clock trước/đúng/sau/rollback cho cùng result và app vẫn offline-usable; wall clock không gia hạn/expire. Build/update mới sau boundary chỉ pass bằng re-review + fresh evidence |
| `QA-CNT-013` | CNT-012/014/052 | Easier variation thiếu/lặp/reorder source step, thiếu góc demo, cố override dosage/timing hoặc dùng support ngoài signed routine-context union; hay bất kỳ typo trong covered message | Release validator/review fail; valid variation phải bijection đúng order, inherit timing và chỉ dùng approved context, còn covered typo làm digest đổi nên chữ ký cũ không được giữ. |
| `QA-CNT-014` | CNT-014 | RFC 8785 golden vectors gồm property order khác nhau, non-ASCII key, literal so với escaped Unicode, control escape, array reorder và integer biên; thêm duplicate key, decomposed non-NFC, lone surrogate, `-0`, fraction, số ngoài safe-integer range và exponent lexical `1e0`, `1E3`, `10e-1` | Kotlin validator và một reference implementation độc lập phải phát byte/digest giống hệt cho input hợp lệ: sort key theo UTF-16, giữ array order và không tự normalize string. Raw number chỉ nhận canonical integer token; mọi exponent kể cả integer-valued cùng từng input không hợp lệ phải fail trước hash thay vì silently coerce. |
| `QA-CNT-015` | CNT-061 | Prior approved index có manifest/routine versions; lần lượt đổi routine payload, global/message/asset/sign-off metadata, chỉ build metadata của SemVer, reuse/lùi version và rebuild byte-identical | Release validation buộc mọi root-digest change tăng manifest SemVer precedence; routine clinical-digest change còn đòi revision tăng; build-only bump/reuse version với digest khác/thiếu non-first index fail `CNT_VERSION_LINEAGE_INVALID`; byte-identical rebuild giữ mapping cũ pass. |

Automated claim lint không thay Product/Clinical review. False positive cần disposition theo item; không allowlist toàn cục để né review.

### QA-061 — Accessibility

Chạy TalkBack + automated scanner + manual trên onboarding, red-flag stop, reasoned hold, Incomplete/Rest, recommendation/why/cap explanation, pre-flight/player, pending pain gate/feedback, settings/export/delete:

- accessible name/role/state, heading, error association, focus order;
- safety/pain copy không truncate/timeout; focus vào heading khi route hard stop/gate;
- Stop/Pause/Skip/Pain answer truy cập được; pending pain không bị focus trap ngoài ý muốn;
- exact six-field mapping của CNT-030: pane title, routine overview, posture/setup intro và accessible name của Stop/Pause/Skip đều consume đúng signed key; Pause key không được reuse cho Resume, và hard-coded/đổi chỗ/orphan key làm test fail;
- font/display scale accessibility lớn nhất không che CTA/safety text;
- caption khớp, transcript đủ khi mute/không xem video; không audio/color-only;
- reduced motion dùng static steps + timer; không autoplay motion/audio;
- contrast WCAG 2.2 AA và touch target theo Android guideline;
- orientation, screen reader, switch/keyboard nơi hỗ trợ không làm mất chức năng;
- từng step-start announce đúng `RoutineStep.screenReaderInstructionKey` cùng current canonical timer state; missing/wrong-step/extra legacy `progressAnnouncementKeys` fail content validation;
- fake monotonic clock qua `30_000/60_000 ms`, pause, background, transition, recovery và step-change trùng cadence: mỗi due ordinal phát tối đa một `polite` announcement, transition/pause không tiến cadence, recovery không replay, simultaneous step+cadence không đọc timer hai lần, step zero không phát announcement riêng và focus không nhảy.

Không đọc được safety guidance, không trả lời pain hoặc không dừng được routine là P0.

## 8. Release gates

### QA-070 — Severity

- **P0:** bypass hold/pending pain/urgent/pause; tạo routine cao hơn effective mode; pain=yes vẫn có routine; mất/lộ dữ liệu; clinical content chưa duyệt; delete resurrect; safety UI không truy cập được.
- **P1:** core offline, notification, export/delete, content asset hoặc accessibility flow hỏng nhưng chưa gây trực tiếp P0; context auto-fallback trái contract; clock/reboot state sai.
- **P2:** lỗi không ảnh hưởng safety/data/core completion, có workaround rõ.

Không release với P0/P1 mở. P2 cần owner, deadline, Product sign-off; không hạ severity safety/privacy để dùng waiver.

### QA-071 — Mandatory gates

| Gate | Điều kiện pass | Evidence | Owner |
|---|---|---|---|
| `REL-GATE-CONTRACT` | FR/UX/SAF/CNT/DATA/SEC/ARC/MET version khóa, traceability không gap | requirement-test matrix + docs digest | Product/QA |
| `REL-GATE-SAFETY` | generated/property/mutation/integration pass; hold/cap/pain gate không bypass | CI report, seeds, E2E video/log redacted | Engineering/QA |
| `REL-GATE-CLINICAL` | red flag/copy + cả sáu routine có external sign-off còn hạn | credential verification ref + signed content digest | Clinical/Product |
| `REL-GATE-CONTENT` | release validator pass trên bundled manifest/assets | validator output + manifest/asset digest | Content QA |
| `REL-GATE-A11Y` | critical flows pass automated/manual; không P0/P1 | device matrix + TalkBack evidence | Accessibility QA |
| `REL-GATE-PRIVACY` | permissions/network/storage/log/export/delete pass; final privacy policy URL/content approved | manifest/binary/traffic/storage reports + policy approval | Security/Privacy |
| `REL-GATE-RESILIENCE` | offline/timezone/DST/clock/reboot/process-death/atomicity pass | integration report | QA |
| `REL-GATE-METRICS` | event allowlist/ref graph + export/import round-trip hợp schema; toàn bộ `MET-071` golden, data-quality exclusion và Gate-1/2/3 calculation tái lập đúng từ fixed dataset | writer/importer conformance report + golden-dataset hash, expected/actual output và exclusion counts | Data/QA/Research |
| `REL-GATE-PLATFORM` | signed Android build, install/upgrade/smoke; final store copy + Google Play Health Apps declaration/disclaimer review pass | build hash + store/declaration checklist | Release/Product |
| `REL-GATE-PILOT-ETHICS` | informed consent, recruitment, adverse-event escalation, secure pilot transfer và ethics/IRB-equivalent determination/approval hoàn tất | signed protocol + approval/determination reference | Research/Product |

### QA-072 — Clinical dependency mặc định đóng

Tại implementation baseline, per-routine steps/regression/support condition/contraindication/stop rule/escalation copy/media và reviewer signature là external dependencies. Privacy policy URL, store listing cuối, Google Play Health Apps declaration/disclaimer review, informed consent, adverse-event escalation, secure pilot-transfer procedure và ethics/IRB-equivalent determination/approval cũng chưa thể do developer tự ký. `REL-GATE-CLINICAL`, phần external của `REL-GATE-PRIVACY`/`REL-GATE-PLATFORM` và `REL-GATE-PILOT-ETHICS` mặc định **CLOSED**.

Engineering được dùng test fixture ghi rõ `NON_PRODUCTION_NOT_CLINICALLY_APPROVED` để dựng schema/UI/validator. Production/RC build phải fail nếu có placeholder, pending status, expired/mismatched digest hoặc bypass flag.

## 9. RC exit criteria

Release candidate chỉ được ký khi:

1. mọi gate QA-071 pass trên cùng artifact digest;
2. không P0/P1 mở;
3. exact first-match table, all valid combinations, single-invalid và 20 invariants pass;
4. red flag/acute/post-session pain tạo đúng reasoned hold, resubmit/deep link/reboot không bypass;
5. `COMPLETED|ABANDONED + PENDING` luôn route mandatory pain gate; stop request chưa trả lời vẫn là `ACTIVE` recovery, và chỉ answer mới atomically tạo `STOPPED + RESOLVED_NO|RESOLVED_HOLD`; không có `STOPPED+PENDING`; effort/context được defer nhưng pain không;
6. pain=yes không có same-session lighter recommendation; next local date cần check-in mới;
7. too_hard pain=no hạ cap từng bậc trong origin day, không đổi outcome và không ảnh hưởng ngày sau;
8. routine selector đúng history rule, user chỉ chọn same/lighter, không inferred-context auto-fallback;
9. sáu routine/asset/copy đúng digest và external sign-off;
10. offline/no-INTERNET/permission allowlist pass;
11. export/delete miễn phí, offline và privacy/security pass;
12. TalkBack/large text/reduced motion/caption/transcript critical flow pass;
13. UI/store không có diagnosis/treatment/false precision claim.
14. privacy policy/store declaration và pilot ethics/consent/adverse-event/secure-transfer blockers có external approval phù hợp.

QA lưu evidence theo release version + binary/ruleset/content digest; không tái dùng evidence build cũ.
