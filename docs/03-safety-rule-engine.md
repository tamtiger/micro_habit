# 03 — Safety Rule Engine

- **Trạng thái:** Implementation baseline 1.0
- **Rule version:** `1`
- **Phạm vi:** Android MVP, local-only, manual check-in
- **Chủ sở hữu:** Product + Engineering
- **Phê duyệt trước pilot:** Clinical Safety Reviewer
- **Liên quan:** `01-product-requirements.md` (`FR-010`–`FR-026`, `FR-040`–`FR-043`), `02-ux-flows-and-copy.md`, `04-content-contract.md`, `08-qa-and-release-gates.md`

Tài liệu này là contract chuẩn (normative) cho decision table, safety hold, day mode cap và quyền mở routine. **PHẢI** là bắt buộc; **KHÔNG ĐƯỢC** là cấm.

## 1. Ranh giới và nguyên tắc

### SAF-001 — Pure, deterministic, local

Hàm quyết định canonical:

```text
evaluate_rule_v1(input: RuleInputDraftV1) -> RuleResultV1
```

Với cùng input và `rule_version=1`, output phải giống nhau. Hàm không đọc clock, database, network, random, analytics, AI, wearable hoặc lịch sử session. Adapter lấy state local rồi truyền đúng input vào hàm.

Không có weight, score, confidence, model học từ lịch sử hoặc default ẩn. AI không được chọn outcome/mode/routine, sửa reason hoặc vượt safety hold.

### SAF-002 — Chỉ đúng input canonical

Decision engine chỉ nhận:

1. `safety_lock_active`;
2. `red_flag`;
3. `acute_issue`;
4. `energy`;
5. `stiffness`;
6. `intent`;
7. `day_mode_cap` tùy chọn.

Không thêm sleep, stress, discomfort score, RPE lịch sử, wearable, context capability hoặc inferred state vào decision table v1. Context và lịch sử chỉ dùng ở selector/UX tách biệt tại SAF-050.

### SAF-003 — General wellness boundary

Engine không chẩn đoán, diễn giải nguyên nhân, phát hiện bệnh/chấn thương hoặc đưa guidance cho rehab, thai kỳ/hậu sản, bệnh mạn hay hạn chế y tế. Eligibility 18+ và nhu cầu guidance cá nhân hóa được xử lý trong onboarding, trước daily engine; app không thu danh sách chẩn đoán.

Exact wording của red flag, urgent stop, pause và feedback pain phải có clinical sign-off. Logic không được biến free text thành safety flag; MVP không thu free text.

## 2. Schema canonical

### SAF-010 — Input enum

```ts
type AcuteIssue =
  | "none"
  | "acute_illness"
  | "new_or_worsening_pain_or_injury"
  | "medically_restricted";

type Energy = "low" | "okay" | "good";
type Stiffness = "none" | "mild" | "notable";
type Intent = "rest" | "gentle" | "moderate";
type Mode = "RECOVER" | "MAINTAIN" | "BUILD";

type ParsedField<T> =
  | { state: "VALUE"; value: T }
  | { state: "MISSING" }
  | { state: "INVALID" };

type RuleInputDraftV1 = {
  safety_lock_active: boolean;
  red_flag: ParsedField<boolean>;
  acute_issue: ParsedField<AcuteIssue>;
  energy: ParsedField<Energy>;
  stiffness: ParsedField<Stiffness>;
  intent: ParsedField<Intent>;
  day_mode_cap: ParsedField<"RECOVER" | "MAINTAIN" | null>;
};

type RuleInputV1 = {
  safety_lock_active: boolean;
  red_flag: boolean;
  acute_issue: AcuteIssue;
  energy: Energy;
  stiffness: Stiffness;
  intent: Intent;
  day_mode_cap: "RECOVER" | "MAINTAIN" | null;
};
```

Boundary parser biến raw field thành đúng một `ParsedField`; không coerce string, boolean, chữ hoa/thường hoặc enum cũ. `RuleInputDraftV1` là input public duy nhất của `evaluate_rule_v1`. `RuleInputV1` là validated internal value có đúng bảy field như trên; nó chỉ được tạo sau khi lock/red/acute short-circuit không thắng và ba ordinary field cùng cap đều hợp lệ. Do đó nhánh tạo value này luôn có `safety_lock_active=false`, `red_flag=false`, `acute_issue=none`, nhưng vẫn giữ hai field system để type/codec thống nhất với architecture 06.

Hai trường hợp short-circuit mà field phía sau được phép `MISSING/INVALID` mà không làm sai outcome:

- `safety_lock_active=true`: dòng 0 thắng và không đọc field còn lại, dù chúng `MISSING/INVALID`;
- `red_flag=true`: dòng 1 thắng và bốn check-in field còn lại có thể `MISSING/INVALID` vì chưa được hỏi.

`safety_lock_active` là boolean tin cậy do repository suy ra từ entity `SafetyHold` đã xác thực. Nếu repository không đọc/xác thực được hold state, session-creation guard phải fail closed và không gọi engine/không cho mở routine; không được giả định `false`.

`day_mode_cap VALUE(null)` nghĩa là không có cap active. Missing optional cap được normalize thành `VALUE(null)` tại boundary; cap corrupt/ngoài enum là `INVALID` và dẫn tới `INCOMPLETE` nếu chưa bị safety row trước chặn.

Persisted CheckIn chỉ có ba canonical variant: `RED_FLAG_STOP` (`red_flag=true`, bốn field chưa hỏi = null), `ACUTE_STOP` (`red_flag=false`, acute non-`none`, ba ordinary field = null), hoặc `FULL` (`red_flag=false`, acute=`none`, ba ordinary field non-null). Missing/invalid token từ restored/migrated persisted CheckIn không phải canonical `INCOMPLETE`: decoder/migration trả `CONTRACT_ERROR`, không coerce và không tạo Decision. Trong MVP, persisted Decision `INCOMPLETE` chỉ hợp lệ khi source `FULL` CheckIn đã commit nhưng authenticated `day_mode_cap` present-and-invalid; UI draft incomplete không persist.

### SAF-011 — Outcome và result

```ts
type Outcome =
  | "BLOCKED_FOR_TODAY"
  | "URGENT_STOP"
  | "PAUSE_TODAY"
  | "INCOMPLETE"
  | "REST_ONLY"
  | "RECOVER"
  | "BUILD"
  | "MAINTAIN";

type CanonicalField =
  | "red_flag"
  | "acute_issue"
  | "energy"
  | "stiffness"
  | "intent"
  | "day_mode_cap";

type ReasonCode =
  | "SAF_LOCK_ACTIVE"
  | "SAF_RED_FLAG_PRESENT"
  | "SAF_INPUT_MISSING"
  | "SAF_INPUT_INVALID"
  | "SAF_ACUTE_ILLNESS"
  | "SAF_ACUTE_NEW_OR_WORSENING_PAIN"
  | "SAF_MEDICALLY_RESTRICTED"
  | "SAF_INTENT_REST"
  | "SAF_ENERGY_LOW"
  | "SAF_STIFFNESS_NOTABLE"
  | "SAF_BUILD_CONDITIONS"
  | "SAF_MAINTAIN_DEFAULT"
  | "SAF_DAY_MODE_CAP_APPLIED";

type PresentationRouteV1 =
  | "BLOCKED_HOLD"
  | "URGENT_STOP"
  | "PAUSE_ACUTE_ILLNESS"
  | "PAUSE_NEW_OR_WORSENING_PAIN_OR_INJURY"
  | "PAUSE_MEDICALLY_RESTRICTED"
  | "INCOMPLETE_FORM"
  | "INCOMPLETE_CONSTRAINT_DATA"
  | "REST_ONLY"
  | "MODE_RECOMMENDATION";

type RuleResultV1 = {
  rule_version: 1;
  outcome: Outcome;                 // outcome gốc từ first-match table
  base_mode: Mode | null;           // null cho outcome không có mode
  effective_mode: Mode | null;      // sau day_mode_cap
  allowed_modes: Mode[];            // thứ tự từ effective xuống nhẹ hơn
  reason_codes: ReasonCode[];       // unique, thứ tự canonical
  invalid_fields: CanonicalField[]; // chỉ dùng cho INCOMPLETE
  presentation_route: PresentationRouteV1;
};
```

Invariants cấu trúc:

- `BLOCKED_FOR_TODAY`, `URGENT_STOP`, `PAUSE_TODAY`, `INCOMPLETE`, `REST_ONLY` → `base_mode=effective_mode=null`, `allowed_modes=[]`.
- `RECOVER` → effective mode không cao hơn Recover và allowed chỉ `[RECOVER]`.
- `MAINTAIN` → effective `MAINTAIN` hoặc `RECOVER` tùy cap.
- `BUILD` → effective `BUILD`, `MAINTAIN` hoặc `RECOVER` tùy cap.
- `outcome` không đổi khi cap áp dụng; ví dụ outcome `BUILD`, cap `MAINTAIN` vẫn giữ `outcome=BUILD` và `effective_mode=MAINTAIN`.
- Với outcome mode, `base_mode` bằng outcome và không đổi khi cap áp dụng.
- Result không có `routine_id`. Chọn routine là bước tách biệt sau authorization.
- `presentation_route` là logical renderer route, **không** phải `MessageKey`, Android string key hoặc text. Safety route chỉ resolve qua typed root contract CNT-015; non-safety route dùng fixed app resource inventory trong UX và không được nhét key giả vào `MessageCatalog`.
- `invalid_fields` chỉ non-empty khi `outcome=INCOMPLETE`; mọi outcome khác phải là `[]`.
- `allowed_modes` và `presentation_route` là required trong transient `RuleDecisionV1` để test engine/render handoff, nhưng không persist/export trong closed `DecisionWireV1`. Persistence giữ primitives rồi derive lại bằng pure total mappings SAF-011/SAF-031; importer reject hai key này như extra thay vì tin projection do file cung cấp.

### SAF-012 — Canonical field order

Thứ tự dùng cho validation, `invalid_fields` và reason ổn định:

```text
red_flag, acute_issue, energy, stiffness, intent, day_mode_cap
```

`invalid_fields` chứa mỗi field tối đa một lần. Missing dùng reason `SAF_INPUT_MISSING`; sai kiểu/enum dùng `SAF_INPUT_INVALID`; nếu có cả hai, reason theo lần xuất hiện đầu tiên trong field order.

### SAF-013 — Freshness nằm ngoài pure table

Engine không nhận timestamp. Check-in và Decision snapshot non-null `schedule_version_id` active tại lúc xác nhận. Lifecycle adapter chỉ được tái dùng decision khi ID đó vẫn bằng active schedule version, vẫn cùng local date, `now` nằm trong **current active** `[work_start, work_end)`, và chưa quá 6 giờ từ lần xác nhận chủ động. Không truyền giá trị stale vào engine để “tính lại”. Start gate phân loại theo đúng thứ tự sau khi đã xác thực contract/source graph:

1. active schedule version khác source version — kể cả chỉ sửa reminder — trả `RECONFIRM_REQUIRED(reason=schedule_changed)`;
2. `now` nằm ngoài current active `[work_start, work_end)` trả `EXPIRED`;
3. vẫn trong window nhưng current local date khác source local date trả `RECONFIRM_REQUIRED(reason=local_date_changed)`;
4. freshness resolver non-`FRESH` trả `RECONFIRM_REQUIRED` với đúng một reason: `ttl` khi same-continuity elapsed time chạm/vượt 6 giờ; `timezone_or_time_change` khi clock generation/zone/mapping cho thấy thay đổi thời gian; `clock_unknown` khi boot/elapsed continuity không thể xác minh an toàn.

Các token reason lowercase trên dùng giống nhau cho `check_in_reconfirmation_required` và conditional reason của `routine_start_blocked`. Evidence/schema/source corrupt vẫn là `CONTRACT_ERROR`, không được hạ thành `clock_unknown`; tại equality TTL là `ttl`, không phải `FRESH`.

Active `SafetyHold` vẫn thắng stale decision. Reconfirm tạo record/version mới và chạy table từ đầu; không mutate/kéo dài check-in cũ. Freshness failure không được map thành Recover/Maintain hoặc `INCOMPLETE` sức khỏe.

## 3. Decision table first-match

### SAF-020 — Bảng chuẩn duy nhất

Áp từ trên xuống; dòng đầu tiên khớp kết thúc đánh giá outcome.

| Ưu tiên | Điều kiện | Outcome | Primary reason |
|---:|---|---|---|
| 0 | `safety_lock_active = true` | `BLOCKED_FOR_TODAY` | `SAF_LOCK_ACTIVE` |
| 1 | `red_flag = true` | `URGENT_STOP` | `SAF_RED_FLAG_PRESENT` |
| 2 | `red_flag` thiếu/sai kiểu | `INCOMPLETE` | `SAF_INPUT_MISSING` / `SAF_INPUT_INVALID` |
| 3 | `acute_issue` hợp lệ và `!= none` | `PAUSE_TODAY` | reason theo exact enum |
| 4 | `acute_issue` thiếu/sai kiểu | `INCOMPLETE` | `SAF_INPUT_MISSING` / `SAF_INPUT_INVALID` |
| 5 | `energy`, `stiffness`, `intent` thiếu/sai enum, hoặc cap có mặt nhưng sai enum | `INCOMPLETE` | `SAF_INPUT_MISSING` / `SAF_INPUT_INVALID` |
| 6 | `intent = rest` | `REST_ONLY` | `SAF_INTENT_REST` |
| 7 | `energy = low OR stiffness = notable` | `RECOVER` | `SAF_ENERGY_LOW` và/hoặc `SAF_STIFFNESS_NOTABLE` |
| 8 | `energy = good AND stiffness IN (none, mild) AND intent = moderate` | `BUILD` | `SAF_BUILD_CONDITIONS` |
| 9 | Mọi tổ hợp hợp lệ còn lại | `MAINTAIN` | `SAF_MAINTAIN_DEFAULT` |

Pseudocode normative:

```text
if safety_lock_active:
  BLOCKED_FOR_TODAY
else if red_flag == true:
  URGENT_STOP
else if red_flag missing/invalid:
  INCOMPLETE
else if acute_issue is valid AND acute_issue != none:
  PAUSE_TODAY
else if acute_issue missing/invalid:
  INCOMPLETE
else if energy/stiffness/intent missing/invalid OR day_mode_cap invalid:
  INCOMPLETE
else if intent == rest:
  REST_ONLY
else if energy == low OR stiffness == notable:
  RECOVER
else if energy == good AND stiffness IN [none, mild] AND intent == moderate:
  BUILD
else:
  MAINTAIN

if outcome IN [RECOVER, MAINTAIN, BUILD] AND day_mode_cap != null:
  effective_mode = min(outcome, day_mode_cap)
else if outcome IN [RECOVER, MAINTAIN, BUILD]:
  effective_mode = outcome
else:
  effective_mode = null
```

Mode order cho `min`: `RECOVER < MAINTAIN < BUILD`.

### SAF-021 — Hành vi thiếu/sai dữ liệu

- Không có `UNKNOWN` trong schema.
- Không default missing/invalid input thành Recover, Maintain hoặc `false`.
- Missing/invalid `red_flag` luôn `INCOMPLETE`, không routine.
- Khi `red_flag=false`, một `acute_issue` hợp lệ khác `none` trả `PAUSE_TODAY` trước khi đọc energy/stiffness/intent; thiếu các field sau không được làm yếu safety outcome.
- Nếu `acute_issue` thiếu/sai thì `INCOMPLETE`. Chỉ sau khi `acute_issue=none`, bất kỳ `energy`, `stiffness`, `intent` missing/invalid đều `INCOMPLETE`, kể cả field khác có vẻ đủ để chọn mode.
- `red_flag=true` vẫn `URGENT_STOP` dù field sau thiếu/sai vì hard-stop xảy ra trước.
- `safety_lock_active=true` luôn `BLOCKED_FOR_TODAY`, kể cả red flag true hoặc input corrupt, vì lock precedence cao nhất.
- Input “xung đột” không được vote/score: row safety/rest đầu tiên thắng; ví dụ acute issue hợp lệ vẫn Pause dù ordinary field thiếu, và Rest thắng energy low/stiffness notable.
- `INCOMPLETE` chỉ là lỗi form/state/migration; không diễn giải là trạng thái sức khỏe kém.

### SAF-022 — Acute issue và no-routine outcomes

Reason theo `acute_issue`:

```text
acute_illness                          -> SAF_ACUTE_ILLNESS
new_or_worsening_pain_or_injury       -> SAF_ACUTE_NEW_OR_WORSENING_PAIN
medically_restricted                  -> SAF_MEDICALLY_RESTRICTED
```

Mọi `PAUSE_TODAY` không có mode/routine. `REST_ONLY` cũng không được đổi thành Recover; nghỉ là outcome riêng, không mất streak/điểm.

### SAF-023 — Day mode cap áp sau outcome

Cap chỉ áp nếu outcome gốc có mode:

| Outcome | Cap | Effective mode | Allowed modes |
|---|---|---|---|
| Recover | bất kỳ/null | Recover | Recover |
| Maintain | null/Maintain | Maintain | Maintain, Recover |
| Maintain | Recover | Recover | Recover |
| Build | null | Build | Build, Maintain, Recover |
| Build | Maintain | Maintain | Maintain, Recover |
| Build | Recover | Recover | Recover |

Khi effective mode thấp hơn outcome, thêm `SAF_DAY_MODE_CAP_APPLIED`. Cap không thay decision-table reason và không áp cho safety/rest/incomplete outcome.

### SAF-024 — Side effect của safety outcome

Decision engine vẫn là hàm thuần. Orchestrator phải atomically tạo một reasoned `SafetyHold` sau khi nhận:

- `URGENT_STOP` → kind `RED_FLAG`;
- `PAUSE_TODAY` → kind khớp exact `acute_issue`.

Hold tồn tại đến đầu ngày địa phương kế tiếp và được ghi trước khi UI coi submit thành công. Người dùng không thể resubmit check-in để gỡ hold trong cùng ngày; lần evaluate sau trả `BLOCKED_FOR_TODAY` ở row 0. `REST_ONLY`, `INCOMPLETE` và các mode không tạo hold.

### SAF-025 — `REST_ONLY` reminder suppression

`REST_ONLY` tạo non-safety suppression cho origin day và hủy/skip mọi reminder còn lại trước effective expiry do clock-integrity resolver xác lập; `expires_at_utc` vẫn là audit/origin value bất biến. Suppression không phải `SafetyHold`, không chặn manual check-in và không đổi rule precedence.

```ts
type RestDaySuppression = LocalStamp & {
  source_decision_id: string;
  expires_at_utc: string;
  clock_integrity: ClockIntegrityEvidence;
  rule_version: 1;
};
```

Nếu người dùng chủ động submit check-in mới trong work window, transaction xử lý suppression theo **outcome mới**, không chỉ theo việc form hợp lệ: `RECOVER|MAINTAIN|BUILD` clear suppression cũ, ghi supersede `new_result=mode` và chỉ schedule lại fixed slot còn tương lai khi schedule/permission/guard khác cho phép; `REST_ONLY` atomically supersede suppression cũ bằng suppression mới từ Decision mới, ghi `new_result=rest` và giữ toàn bộ reminder còn lại im lặng; `URGENT_STOP|PAUSE_TODAY` clear/supersede Rest state bằng `new_result=safety`, tạo đúng SafetyHold và không reschedule. `INCOMPLETE` hoặc transaction/contract failure giữ suppression hiện hành và không schedule. Slot đã qua không bao giờ được phát/lập lại. Suppression không tạo streak loss; audit expiry là đầu origin local date kế tiếp, còn effective deactivation tuân cùng clock-integrity invariant SAF-046.

## 4. Reason-code registry

### SAF-030 — Code ổn định

```text
SAF_LOCK_ACTIVE
SAF_RED_FLAG_PRESENT
SAF_INPUT_MISSING
SAF_INPUT_INVALID
SAF_ACUTE_ILLNESS
SAF_ACUTE_NEW_OR_WORSENING_PAIN
SAF_MEDICALLY_RESTRICTED
SAF_INTENT_REST
SAF_ENERGY_LOW
SAF_STIFFNESS_NOTABLE
SAF_BUILD_CONDITIONS
SAF_MAINTAIN_DEFAULT
SAF_DAY_MODE_CAP_APPLIED
```

Reason theo đúng row first-match. Riêng Recover có thể có cả energy + stiffness reason, theo thứ tự trên. Cap reason luôn cuối. Không thêm reason dựa trên dữ liệu engine không nhận.

### SAF-031 — Presentation-route mapping

| Result/condition | `presentation_route` |
|---|---|
| `BLOCKED_FOR_TODAY` | `BLOCKED_HOLD` |
| `URGENT_STOP` | `URGENT_STOP` |
| `PAUSE_TODAY + acute_illness` | `PAUSE_ACUTE_ILLNESS` |
| `PAUSE_TODAY + new_or_worsening_pain_or_injury` | `PAUSE_NEW_OR_WORSENING_PAIN_OR_INJURY` |
| `PAUSE_TODAY + medically_restricted` | `PAUSE_MEDICALLY_RESTRICTED` |
| `INCOMPLETE` với exact `invalid_fields=[day_mode_cap]` | `INCOMPLETE_CONSTRAINT_DATA` |
| `INCOMPLETE` còn lại | `INCOMPLETE_FORM` |
| `REST_ONLY` | `REST_ONLY` |
| `RECOVER\|MAINTAIN\|BUILD` | `MODE_RECOMMENDATION` |

Exact Vietnamese copy nằm ở `02-ux-flows-and-copy.md`, nhưng token route trên không phải content identity. Với `MODE_RECOMMENDATION`, ordered `reason_codes` chọn các fixed “Vì sao” resource theo inventory UX; `SAF_DAY_MODE_CAP_APPLIED` thêm cap explanation cuối. `INCOMPLETE_FORM`, `INCOMPLETE_CONSTRAINT_DATA` và `REST_ONLY` cũng dùng fixed app resource tương ứng. Các resource code-native này không được tạo thành orphan `MessageCatalog` entry và engine không phát text/AI output.

Safety renderer bắt buộc resolve bằng typed `globalSafetyContent` đã verify: `URGENT_STOP → urgentStop`; ba pause route → `pauseToday` cùng đúng `reasonKeys` field; `BLOCKED_HOLD` đọc verified `SafetyHold.kind` rồi đi qua exact literal `holdRouteBindings` tới `urgentStop`, `pauseToday` reason hoặc `playerSafety.painResponse`. Toàn bộ message set + `emergencyDial` của route đến từ cùng `globalSafetyContentDigestSha256`; không có hard-coded `safety.*` alias.

Khi render thành công, event `safety_screen_shown` không ghi một `message_key` tùy ý. Nó ghi `route_id` theo total mapping: immediate `URGENT_STOP → urgent_stop`; ba pause route → `pause_acute_illness|pause_new_or_worsening_pain_or_injury|pause_medically_restricted`; `BLOCKED_HOLD` + kind → `blocked_red_flag|blocked_acute_illness|blocked_new_or_worsening_pain_or_injury|blocked_medically_restricted|blocked_post_session_new_or_worse_pain`, cùng exact global content digest. Route ID + digest resolve toàn typed message set, kể cả title/body/action, không chọn ngẫu nhiên một “primary key”.

Hold kind chỉ thay typed route; mọi kind đều giữ outcome `BLOCKED_FOR_TODAY` và không routine. Kind/source thiếu, sai hoặc không xác thực không được fallback sang pain copy hoặc làm yếu block; state layer trả `CONTRACT_ERROR` và dùng typed operational `corruptHoldFailClosed` route.

## 5. Safety hold và pain gate

### SAF-040 — Feedback schema

```ts
type FeedbackAnswersV1 = {
  effort: "easy" | "moderate" | "too_hard" | null;
  new_or_worse_pain: "yes" | "no" | null;
  context_fit: "yes" | "no" | null;
};

type LocalStamp = {
  occurred_at_utc: string; // exact InstantWireV1 YYYY-MM-DDTHH:mm:ss.SSSZ
  local_date: string;      // YYYY-MM-DD tại thời điểm tạo
  zone_id: string;         // IANA ZoneId
  utc_offset_minutes: number;
};

type ClockIntegrityEvidence = {
  origin_boot_marker: number; // non-negative integer/Long
  created_elapsed_realtime_ms: number;
  monotonic_deadline_ms: number;
  remaining_elapsed_ms_at_last_checkpoint: number;
  original_duration_ms: number;
};

type ClockSnapshot = LocalStamp & {
  boot_marker: number; // same integer boot-marker source as persisted evidence
  elapsed_realtime_ms: number;
};

type SessionOriginConstraint = {
  terminal_at: LocalStamp;
  session_origin_day_expires_at_utc: string;
  clock_integrity: ClockIntegrityEvidence;
  completion_boot_marker: number;
  completion_elapsed_realtime_ms: number;
  completion_clock_generation: number;
  completion_wall_minus_elapsed_ms: number;
};

type SafetyHold = LocalStamp & {
  kind:
    | "RED_FLAG"
    | "ACUTE_ILLNESS"
    | "NEW_OR_WORSENING_PAIN_OR_INJURY"
    | "MEDICALLY_RESTRICTED"
    | "POST_SESSION_NEW_OR_WORSE_PAIN";
  source_type: "CHECK_IN" | "SESSION";
  source_id: string;         // check-in ID hoặc session ID
  expires_at_utc: string;  // instant của đầu local date kế tiếp trong zone_id
  clock_integrity: ClockIntegrityEvidence;
  rule_version: 1;
};
```

`FeedbackAnswersV1` chỉ là answer subvalue cho reducer, **không** phải toàn row storage/export. Domain enum map explicit: `Effort.EASY|MODERATE|TOO_HARD → easy|moderate|too_hard`, `NewOrWorsePain.YES|NO → yes|no`, `ContextFit.YES|NO → yes|no`; null giữ null. Decoder từ chối unknown/case alias, không dùng enum ordinal. Full persisted/export row được khóa tại SAF-047; không invent `feedback_id`, `submitted_at` hoặc generic `created_at`.

`POST_SESSION_NEW_OR_WORSE_PAIN` bắt buộc có `source_type=SESSION`; bốn kind còn lại bắt buộc có `source_type=CHECK_IN`. Foreign key sai loại làm record không hợp lệ và authorization fail closed.

`SafetyHold.source_type` dùng literal domain uppercase `CHECK_IN|SESSION`. Codec storage/export map 1:1 thành key `source_type` với token lowercase `check_in|session`; event 07 cũng dùng token lowercase. Decode token khác hoặc sai mapping kind/source phải fail closed; không đổi case ngầm hay dùng alias.

Không thu vị trí đau, mức đau, diagnosis hoặc note tự do.

### SAF-041 — Pain gate và terminal transition

Khi một `RoutineSession` chuyển sang `COMPLETED` hoặc `ABANDONED`, repository phải tạo/cập nhật terminal state + pain gate `PENDING` trong cùng transaction. Stop chủ động dùng contract khác để không tạo `STOPPED + PENDING`: session giữ `ACTIVE` trong lúc hỏi pain; answer commit mới atomically chuyển thành `STOPPED + RESOLVED_NO` hoặc `STOPPED + RESOLVED_HOLD`.

```ts
type SessionPainGateBase = {
  session_id: string;
  session_origin_constraint: SessionOriginConstraint;
};

type SessionPainGate =
  | SessionPainGateBase & {
      terminal_state: "COMPLETED" | "ABANDONED";
      pain_gate_status: "PENDING";
      new_or_worse_pain: null;
    }
  | SessionPainGateBase & {
      terminal_state: "COMPLETED" | "STOPPED" | "ABANDONED";
      pain_gate_status: "RESOLVED_NO";
      new_or_worse_pain: "NO";
    }
  | SessionPainGateBase & {
      terminal_state: "COMPLETED" | "STOPPED" | "ABANDONED";
      pain_gate_status: "RESOLVED_HOLD";
      new_or_worse_pain: "YES";
    };
```

Lifecycle domain/storage/export giữ literal uppercase `ACTIVE|COMPLETED|STOPPED|ABANDONED`. `PENDING|RESOLVED_NO|RESOLVED_HOLD` và `YES|NO` là literal domain; codec storage/export chỉ map pain fields explicit thành `pending|resolved_no|resolved_hold` và `yes|no`. Event 07 dùng lowercase `terminal_state`/pain answer theo event codec riêng. Không dùng ordinal, đổi case ngầm hoặc alias.

Nếu `new_or_worse_pain=null` trên `COMPLETED|ABANDONED`, domain session-creation guard chặn mọi session mới và route tới câu hỏi pain bắt buộc. Không mặc định `NO`; back, deep link, process death, reboot hoặc restore không được bypass. Trong stop flow, tap Stop đã reconcile rồi persist PAUSED/frozen checkpoint dưới cùng player coordinator; dialog wait không tăng counter. Process chết trước answer để session ở `ACTIVE`/PAUSED và lần mở sau đi qua active-session recovery, không tự ghi `STOPPED` hoặc tự resume. Với mọi terminal state, người dùng có thể để sau `effort` và `context_fit`, nhưng pain phải được resolve trước phiên kế tiếp.

Chỉ một pending gate unresolved hoặc một active stop question được phép tồn tại vì active/pending guard chặn session mới. Trả lời pain được persist atomically cùng resolved status; pain=`yes` còn phải tạo hold trong cùng transaction.

Start gate gặp `pain_gate_status=PENDING` trả domain status `PENDING_SAFETY_FEEDBACK` và UI route canonical `PENDING_PAIN_GATE`. Đây không phải outcome mới của `evaluate_rule_v1`.

Pain `NO` chuyển status `RESOLVED_NO`; pain `YES` chỉ chuyển `RESOLVED_HOLD` trong cùng transaction đã ghi hold. Effort/context còn null sau `RESOLVED_NO` không chặn session mới. Với north-star, chỉ `context_fit=null` hoặc khác `YES` làm session chưa qualify; `effort=null` không thuộc predicate và không tự loại một session đã `COMPLETED + pain NO + context YES` trên selected workday.

### SAF-042 — Pain `yes` luôn thắng

Khi pending gate hoặc active stop flow nhận `new_or_worse_pain=yes`, trong cùng transaction logic:

1. dừng/không resume session;
2. tạo `SafetyHold` đến đầu ngày địa phương kế tiếp;
3. không tạo/cập nhật day mode cap từ cùng feedback;
4. trả UI pain response;
5. vô hiệu hóa Start, Resume, Choose another và mọi deep link routine;
6. không hiển thị routine nhẹ hơn trong cùng session hoặc phần còn lại của ngày.

Persist hold thành công trước khi resolve gate/báo “đã lưu phản hồi”. Nếu ghi thất bại, gate vẫn pending, UI vẫn giữ trạng thái stop và session-creation guard fail closed. Relaunch/reboot trong ngày phải suy ra `safety_lock_active=true`; engine trả `BLOCKED_FOR_TODAY`.

Hold chỉ không còn active tại effective deadline do clock-integrity resolver xác lập: monotonic deadline ở nhánh same-boot hoặc conservative reconciled deadline sau discontinuity. `expires_at_utc` là origin/audit instant bất biến, không phải wall-only clear condition và không được tính lại theo timezone hiện tại. Check-in cũ không có hiệu lực sang ngày mới, vì vậy người dùng phải check-in mới trước recommendation; hold hết hạn không tự khôi phục routine.

Nếu người dùng trả lời pain=`yes` muộn sau khi origin day của session đã qua, hold lấy ngày/zone của **thời điểm trả lời** và vẫn chặn đến đầu ngày kế tiếp tương ứng. Ngược lại, `too_hard + pain=no` trả lời muộn không tạo cap cho ngày mới theo SAF-045.

### SAF-043 — Acute/urgent outcomes

`URGENT_STOP` là hard-stop tức thời và không có CTA routine. `PAUSE_TODAY` không có routine. Cả hai tạo reasoned hold theo SAF-024 nên không thể bị resubmit bypass trong ngày. Engine không tự diễn giải nguyên nhân hoặc tự thêm hướng dẫn y khoa; UI chỉ render copy đã duyệt.

Không dùng per-routine contraindication chưa sign-off để tạo rule mới. Các constraint đó thuộc manifest và pre-flight trong `04-content-contract.md`.

## 6. Day mode cap từ `too_hard`

### SAF-044 — Cap state

```ts
type DayModeCap = LocalStamp & {
  max_mode: "RECOVER" | "MAINTAIN";
  mode_trigger_session_id: string; // feedback session gần nhất thực sự hạ max_mode
  source_session_id: string; // expiry-source session cung cấp origin/expiry đang được giữ
  expires_at_utc: string; // đầu local date kế tiếp trong zone_id
  clock_integrity: ClockIntegrityEvidence;
  rule_version: 1;
};
```

`BUILD` không được lưu làm cap vì không hạn chế gì. `mode_trigger_session_id` và `source_session_id` đều non-null, resolve đúng `RoutineSession`, nhưng có thể khác nhau: field đầu truy mode, field sau truy expiry. Các field `LocalStamp` của cap là terminal stamp của expiry `source_session_id`, không phải answer stamp; thời điểm feedback/update nằm trong feedback/event audit.

### SAF-045 — Feedback reducer

Feedback reducer là hàm thuần tách biệt khỏi decision engine:

```text
reduce_feedback_v1(
  feedback,
  terminal_state,
  runtime_effective_mode_at_start,
  session_origin_constraint,
  origin_constraint_status, // ACTIVE gồm cả conservative extension; hoặc INACTIVE_VERIFIED
  active_day_mode_cap,
  answer_clock_snapshot
) -> { safety_hold_mutation, day_mode_cap_mutation }
```

`SessionOriginConstraint` được chụp tại terminal transition, không lấy lại ngày/zone từ thời điểm trả lời. Adapter clock-integrity resolve evidence thành `ACTIVE` hoặc `INACTIVE_VERIFIED`; trạng thái không chắc chắn phải được giữ bảo thủ như `ACTIVE`. Reducer không tự so wall clock. Pain=`yes` dùng `answer_clock_snapshot` để tạo hold theo ngày/zone lúc trả lời; cap dùng terminal stamp, persisted expiry và checkpointed evidence của `session_origin_constraint`.

Áp đúng thứ tự:

0. Nếu pain chưa trả lời: completed/abandoned giữ gate pending; stop request giữ session active; không tạo cap và không cho session mới.
1. Nếu pain=`yes`: upsert safety hold; resolve gate trong cùng transaction; không tạo/nới/hạ cap từ feedback đó.
2. Nếu pain=`no` và effort null/khác `too_hard`: resolve gate; không đổi hold/cap.
3. Nếu pain=`no`, effort=`too_hard` và `origin_constraint_status=INACTIVE_VERIFIED`: lưu feedback và resolve gate nhưng không tạo/cập nhật cap; feedback của ngày trước không tác động ngày mới.
4. Nếu pain=`no`, effort=`too_hard`, `origin_constraint_status=ACTIVE` và terminal state là bất kỳ `completed`, `stopped` hoặc `abandoned`:
   - basis = cap hiện hành nếu có, nếu không là snapshot `runtime_effective_mode_at_start` của session — authorization ceiling sau cap tại transaction start;
   - `BUILD → MAINTAIN`, `MAINTAIN → RECOVER`, `RECOVER → RECOVER`;
   - upsert cap với terminal stamp, expiry và clock evidence đã checkpoint của session-origin constraint; cap mới đặt `mode_trigger_session_id=current session`; không dùng answer zone.

Khi đã có cap active từ origin/zone khác, reducer không được thay nó bằng deadline sớm hơn. Mode mới vẫn hạ từ cap hiện hành; effective deadline là deadline bảo thủ muộn hơn giữa cap cũ và candidate mới. Nếu cap cũ có deadline muộn hơn **hoặc bằng** candidate, giữ nguyên origin/expiry/clock evidence và expiry `source_session_id` của existing constraint; equality ghi `deadline_source=same`. Chỉ khi candidate có effective deadline muộn hơn mới dùng origin/expiry/evidence/source của candidate. Độc lập với expiry merge, nếu `max_mode` hạ strict thì set `mode_trigger_session_id=current feedback session`; existing `RECOVER→RECOVER` giữ mode trigger cũ dù deadline source có thể đổi. Feedback cap-update snapshot luôn giữ invocation `trigger_session_id`, vì vậy field đó được phép khác resulting cap `mode_trigger_session_id` chỉ ở nhánh không hạ mode này. Như vậy timezone change không rút ngắn cap đang active và cả hai provenance deterministic.

Cap được tạo từ feedback của bất kỳ terminal session khi `too_hard + pain=no`; nó chỉ ảnh hưởng luồng mới sau feedback và không sửa outcome/session đã kết thúc. Session phải lưu riêng immutable `decision_effective_mode_at_start`, transaction-local `runtime_effective_mode_at_start` và selected `routine_mode`; reducer dùng runtime field khi không còn active cap, tuyệt đối không dùng stale Decision ceiling hoặc mode routine nhẹ hơn mà người dùng tự chọn. `context_fit=no` chỉ phục vụ tổng kết; không đổi rule, reminder hoặc routine.

Cap không bao giờ làm yếu safety. Nếu safety hold active (`safety_lock_active=true`), row 0 trả `BLOCKED_FOR_TODAY` trước khi cap được xét. Cap hết hạn không có ảnh hưởng ngày sau và không tự tạo yêu cầu y tế; daily check-in mới vẫn bắt buộc theo vòng đời check-in.

### SAF-046 — Timezone và atomicity

- `local_date`/`zone_id` đóng dấu lúc tạo; dữ liệu lịch sử không được suy lại theo timezone mới.
- `expires_at_utc` tính một lần bằng đầu ngày kế trong `zone_id`; không giả định một ngày luôn 24 giờ.
- Mỗi constraint persist đủ năm field `ClockIntegrityEvidence`: boot marker, elapsed-realtime lúc tạo, monotonic deadline, remaining tại checkpoint gần nhất và original duration tới expiry; các số phải không âm, nhất quán và được ghi cùng transaction.
- Đổi timezone không được tính lại expiry. Trong cùng boot với monotonic continuity hợp lệ, monotonic deadline là authority: constraint inactive tại equality ngay cả khi wall clock bị lùi. Wall-clock jump/TIME_SET tiến không được clear sớm; `expires_at_utc` vẫn immutable để audit/origin nhưng không thay monotonic authority của nhánh same-boot.
- Sau reboot/clock discontinuity khi không chứng minh được clock integrity, repository được phép conservatively giữ constraint lâu hơn cho tới khi reconciliation xác lập state; tuyệt đối không clear sớm vì clock/timezone mới.
- Boundary là half-open: ở nhánh same-boot, hold/cap inactive tại đúng monotonic deadline; sau discontinuity, inactive tại đúng effective deadline mà reconciliation bảo thủ đã persist. Không yêu cầu wall clock đồng thời bằng `expires_at_utc`.
- Upsert hold/cap, pain answer và resolve pending gate dùng transaction; nếu pain=yes, hold phải tồn tại trước mọi navigation cho phép rời safety screen.
- Nhiều `too_hard` cùng ngày giảm từ cap hiện hành: Build→Maintain→Recover→Recover; không nới cap.

Residual limitation: vì production binary không có network/trusted-time service, sau reboot app không thể chứng minh tuyệt đối rằng người dùng không chỉnh wall clock. Hành vi bắt buộc là fail conservative/reconciliation và có thể kéo dài constraint; không tuyên bố chống clock tampering tuyệt đối.

### SAF-047 — Immutable audit snapshot của side effect

Operational `DailyConstraintsBundle` có thể bị replace/clear/purge, nên export/history không được tái dựng side effect từ state hiện hành. Source record phải persist immutable snapshot ngay trong cùng transaction tạo/áp reducer:

```ts
type DecisionConstraintAuditV1 = {
  created_safety_hold_snapshot: SafetyHold | null;
  created_rest_suppression_snapshot: RestDaySuppression | null;
  evaluation_day_mode_cap_snapshot: DayModeCap | null;
};

type SessionConstraintAuditV1 = {
  runtime_day_mode_cap_snapshot_at_start: {
    applied_cap: DayModeCap;
    decision_effective_mode_before_runtime_cap:
      | "RECOVER" | "MAINTAIN" | "BUILD";
    runtime_effective_mode_at_start:
      | "RECOVER" | "MAINTAIN" | "BUILD";
  } | null;
};

type DayModeCapUpdateSnapshotV1 = {
  trigger_session_id: string;
  expiry_source_session_id: string;
  basis_mode: "RECOVER" | "MAINTAIN" | "BUILD";
  previous_max_mode: "RECOVER" | "MAINTAIN" | null;
  resulting_cap: DayModeCap;
  deadline_source: "existing_later" | "candidate_later" | "same";
};

type FeedbackConstraintAuditV1 = {
  created_post_session_safety_hold_snapshot: SafetyHold | null;
  day_mode_cap_update_snapshot: DayModeCapUpdateSnapshotV1 | null;
};

type PersistedFeedbackWireV1 = FeedbackAnswersV1 &
  FeedbackConstraintAuditV1 & {
    session_id: string; // identity/FK duy nhất; không có feedback_id
    pain_gate_status: "pending" | "resolved_no" | "resolved_hold";
    pain_answered_at: LocalStamp | null;
    updated_at: LocalStamp;
  };
```

`PersistedFeedbackWireV1` là exact record trong array export `feedback`; `SessionOriginConstraint`/`terminal_at` nằm ở Session nguồn và reducer join bằng non-null `session_id`, không duplicate sang feedback row. Mọi terminal Session (`COMPLETED|STOPPED|ABANDONED`) persist/export đủ bốn `completion_*` field trong chính object này từ cùng coherent terminal clock snapshot; tên `completion_*` là canonical wire prefix lịch sử cho terminal anchor, không có nghĩa chỉ status `COMPLETED`. Chỉ event `routine_completed` mirror bốn field; `routine_stopped|routine_abandoned` từ chối chúng theo event allowlist. Khi terminal `COMPLETED|ABANDONED` vừa tạo pending record, `updated_at` bằng Session `terminal_at` và `pain_answered_at=null`; mỗi successful pain/optional-field commit thay `updated_at` bằng commit LocalStamp, còn `pain_answered_at` chỉ được set đúng một lần khi pain transition null→answer. Invariant codec: `pending ↔ pain=null`, `resolved_no ↔ pain=no`, `resolved_hold ↔ pain=yes + created hold snapshot`; `STOPPED` không có pending. Nested `SafetyHold` trong wire dùng lowercase `source_type=check_in|session` theo SAF-040 dù domain type phía trên viết uppercase.

Invariants:

- `URGENT_STOP|PAUSE_TODAY` decision có đúng `created_safety_hold_snapshot`; `REST_ONLY` có đúng `created_rest_suppression_snapshot`; outcome khác có cả hai null.
- Decision mode chỉ có `evaluation_day_mode_cap_snapshot` khi cap làm `effective_mode < base_mode` và reason chứa `SAF_DAY_MODE_CAP_APPLIED`. Snapshot này là cap đã đọc trong evaluation transaction; Decision không mutate nếu cap đổi sau đó.
- Mỗi session chụp `runtime_day_mode_cap_snapshot_at_start` chỉ khi cap active sau immutable Decision làm runtime mode nhẹ hơn `Decision.effective_mode`; snapshot giữ full `applied_cap` cùng before/runtime mode trong transaction start. Nếu cap đã được phản ánh nguyên vẹn khi evaluate và không nhẹ thêm tại start, field null; Decision snapshot là provenance.
- Pain=`YES` feedback giữ exact answer-day hold vừa commit trong `created_post_session_safety_hold_snapshot`; cap-update field null. Pain=`NO + TOO_HARD` chỉ ghi `day_mode_cap_update_snapshot` khi cap update thực sự commit trước origin expiry. Snapshot chụp **resulting cap sau merge**, gồm invocation trigger session, resulting mode-trigger session, expiry-source session, basis/previous mode và deadline source; không ghi candidate giả định. `trigger_session_id=resulting_cap.mode_trigger_session_id` khi tạo cap hoặc hạ strict; existing `RECOVER→RECOVER` deadline-only merge giữ mode trigger cũ và được phép khác invocation. Pain/effort đã non-null không được đổi giá trị; retry idempotent trả snapshot cũ và không hạ lần hai.
- No-op (`pain` chưa NO, effort null/khác TOO_HARD, origin inactive) không tạo side-effect snapshot; lý do nằm trong typed feedback/event audit. Export không phát minh placeholder snapshot cho no-op.
- Mọi constraint snapshot giữ `rule_version=1`, full `LocalStamp`, immutable audit expiry, đủ năm field `ClockIntegrityEvidence`, kind/mode và source reference. Cap luôn có cả `mode_trigger_session_id` và expiry `source_session_id`. Wire codec giống entity tương ứng; không rút gọn evidence và không recompute khi export.
- Snapshot sống cùng source Decision/Session/Feedback/event kể cả operational constraint đã inactive/purge. Retention của mọi retained cap snapshot phải kéo dài graph của cả mode-trigger và expiry-source Session, dedupe nếu cùng ID; các snapshot khác giữ mọi referenced CheckIn/Decision/Session/ScheduleVersion cần để FK/provenance còn resolvable. Riêng operational constraint còn active/retained cũng giữ graph nguồn tương ứng (`SafetyHold/DayModeCap` từ session giữ Session→Decision→CheckIn→ScheduleVersion; hold/suppression từ decision giữ Decision→CheckIn→ScheduleVersion) đến khi constraint inactive/purge và không còn event/snapshot retained tham chiếu. Full delete vẫn xóa toàn bộ ngay.

Authoring/DAO invariant fail hoặc snapshot mismatch với mutation trong cùng transaction là authenticated contract error và fail closed. Exporter chỉ serialize snapshot đã lưu; không join state constraint hiện hành để “đoán lại” lịch sử.

## 7. Routine authorization và selector

### SAF-050 — Không infer context, không auto-fallback

Decision engine không nhận `chairAvailable`, `canStand`, `canWalk` hoặc tương tự. App không tự suy đoán bối cảnh rồi âm thầm đổi mode/routine.

Manifest đã duyệt hiển thị yêu cầu điểm tựa/context. Người dùng chọn routine cùng effective mode hoặc mode nhẹ hơn. Nếu pre-flight không đạt (ví dụ thiếu điểm tựa chắc chắn), CTA duy nhất là chọn bài khác trong tập mode được phép; không có “vẫn tiếp tục”.

### SAF-051 — Authorization trước session

Domain layer phải chạy lại authorization ngay trước khi tạo/restore `RoutineSession`, kể cả deep link:

```text
allowed(RECOVER)  = [RECOVER]
allowed(MAINTAIN) = [MAINTAIN, RECOVER]
allowed(BUILD)    = [BUILD, MAINTAIN, RECOVER]
```

Mọi entry point kiểm theo thứ tự: `SafetyHold` active → pending pain gate → active-session recovery → current signed global-safety acknowledgement → contract/freshness/schedule-version/outcome/mode. Hold active trả `BLOCKED_FOR_TODAY`; pending gate unresolved route bắt buộc về pain question và không tạo/restore session mới; active session chỉ route recovery hợp lệ. Thiếu/mismatch acknowledgement của bundled `manifestVersion + globalSafetyContentDigestSha256` trả `SCOPE_REACK_REQUIRED`, giữ original onboarding/activation anchor và không cho check-in/start cho tới khi re-ack commit. Sau các guard đó mới authorize decision/mode. Không có effective mode → không routine. Requested routine mode không thuộc `allowed(effective_mode)` → từ chối tạo session. UI state/deep link/restore không được bypass hold, pending gate, active-session guard, scope re-ack, rule hoặc cap.

Active session không cho tạo session thứ hai. Sau same-boot process death, app chỉ cho recovery `Resume`/`End` nếu vẫn cùng local date, trước `work_end`, content/checksum/state hợp lệ và clock continuity còn xác minh được. Reboot/clock discontinuity hoặc date/window hết hạn được atomically chuyển session thành `abandoned` + pending pain. Content unavailable/checksum/identity mismatch cũng chỉ vào nhánh đó khi Session đã authenticate/decrypt, schema và checkpoint/cross-invariant vẫn valid để freeze/export. Session/checkpoint auth/decrypt/schema/phase/counter/catalog-cross-invariant corrupt phải giữ active guard, emit zero normal terminal/product event và route typed `DATA_ERROR`/explicit full reset; không fabricate checkpoint để tạo `ABANDONED`. `paused` chỉ là substate của active session, không phải terminal status được persist độc lập.

### SAF-052 — Default selector deterministic

Chỉ sau outcome có mode, selector mặc định xét đúng hai routine của **effective mode**:

1. ưu tiên routine chưa từng hoàn thành;
2. nếu cả hai cùng trạng thái, chọn `last_completed_at` cũ hơn;
3. nếu vẫn hòa (kể cả cả hai chưa từng hoàn thành), chọn ID nhỏ hơn theo ASCII.

Lịch sử chỉ luân phiên nội dung; không nâng/hạ mode. Selector không filter theo inferred context. Màn hình “Đổi bài” hiển thị toàn bộ routine của effective mode và mode nhẹ hơn; người dùng tự chọn dựa trên yêu cầu đã hiển thị.

## 8. Versioning

### SAF-060 — Rule version

- MVP dùng integer `rule_version=1` đúng bảng SAF-020.
- Mỗi decision, safety hold, day cap và rest suppression lưu `rule_version`; session giữ `decision_id` non-null tới versioned Decision và không duplicate field này trong session payload/export.
- Bất kỳ thay đổi input enum, precedence, điều kiện row, outcome, cap/lock semantics hoặc allowed-mode mapping phải tăng rule version và có migration/fixture review.
- Sửa copy không đổi logic vẫn tăng content/copy version và sign-off digest, không tự đổi rule version.
- App không được silently evaluate record có rule version không hỗ trợ; fail closed và yêu cầu check-in mới hoặc reset state có chủ đích.

## 9. Fixtures và property invariants

### SAF-070 — Fixture schema

```ts
type RuleFixtureV1 = {
  id: string; // SAF-FIX-NNN
  input: RuleInputDraftV1;
  expected: RuleResultV1;
};
```

Fixture output thay đổi chỉ khi rule version thay đổi hoặc lỗi spec được owner duyệt; không update snapshot để “làm xanh” test.

### SAF-071 — Coverage tối thiểu

Phải có fixture cho:

- hold active cùng red flag true và mọi input invalid → Blocked;
- red flag true cùng mỗi tổ hợp field null/invalid → Urgent;
- red flag missing/invalid; acute issue missing/invalid; từng energy/stiffness/intent missing và từng enum invalid;
- từng `acute_issue` khác none cùng later fields null/invalid để chứng minh safety-first;
- intent rest với energy low/stiffness notable để chứng minh Rest precedence;
- Recover do energy, stiffness, và cả hai;
- mọi tổ hợp Build hợp lệ (`stiffness none/mild`);
- mọi tổ hợp valid còn lại → Maintain;
- mỗi outcome mode × cap null/Maintain/Recover;
- routine authorization same/lighter/heavier và deep link;
- selector never-completed/oldest/tie;
- pain yes với mọi effort; too_hard pain=no qua Build→Maintain→Recover;
- completed/abandoned với pain null; stop request vẫn active tới answer; process death/reboot/deep link; pain no/yes resolve path;
- red flag và từng acute issue tạo đúng reasoned hold; resubmit cùng ngày vẫn Blocked; Rest không tạo hold;
- hold/cap trước, đúng tại, sau expiry; DST/timezone/reboot.

Generated suite phải duyệt toàn bộ tích Descartes của enum hợp lệ và single-invalid cases. CI in fixture ID/counterexample/seed khi fail.

### SAF-072 — Property invariants

Với mọi input:

1. **Determinism:** cùng input → cùng result.
2. **Hold dominance:** hold active → `BLOCKED_FOR_TODAY`.
3. **Red-flag dominance sau lock:** lock false + red flag true → `URGENT_STOP`, bất kể field sau.
4. **No missing fallback:** field bắt buộc thiếu/sai → `INCOMPLETE`, không mode/routine.
5. **First match:** không row thấp hơn thay kết quả row đã khớp.
6. **No routine outcome:** năm outcome không-mode luôn `effective_mode=null` và allowed empty.
7. **Cap monotonic:** cap chỉ giữ/hạ effective mode, không nâng và không đổi outcome.
8. **Pain dominance:** pain=yes tạo hold, không tạo cap từ cùng feedback và không có same-session routine.
9. **Repeated too-hard monotonic:** cap chỉ Build→Maintain→Recover→Recover.
10. **Next-day separation:** hold/cap hết tại effective deadline do clock-integrity resolver xác lập; persisted `expires_at_utc` là audit value bất biến và conservative reconciliation có thể kéo dài state; sau khi inactive, check-in ngày cũ không được tái dùng.
11. **Authorization:** routine mode không bao giờ cao hơn effective mode.
12. **Selector isolation:** history chỉ đổi routine ID trong effective mode, không đổi outcome/mode.
13. **Context non-inference:** context không là input engine và không làm auto-fallback.
14. **AI independence:** AI/copy layer không đổi result.
15. **Pain-gate lifecycle:** completed/abandoned với pain unresolved chặn session mới qua process death/reboot/deep link; stop request giữ `ACTIVE` tới answer rồi commit `STOPPED + resolved`; effort/context có thể defer nhưng pain không thể.
16. **Reasoned hold:** Red flag, từng acute issue và post-session pain=yes tạo đúng hold tới effective deadline của origin-day constraint; Rest không tạo hold; resubmit không gỡ hold.
17. **Reason-specific block copy:** verified hold kind chọn đúng copy; kind không bao giờ đổi/bỏ `BLOCKED_FOR_TODAY`; kind corrupt fail closed, không giả làm post-session pain.
18. **Rest suppression isolation:** Rest suppress reminder phần còn lại origin day nhưng không tạo hold/chặn manual check-in; check-in mới chỉ reschedule slot tương lai.
19. **Cap source:** completed/stopped/abandoned + pain=no + effort=too_hard đều tạo cap; terminal state không được làm mất adaptation.
20. **Reason stability:** reason unique, đúng row và canonical order.

## 10. External clinical gate

Tài liệu này khóa logic nhưng không tự chứng nhận nội dung lâm sàng. Trước pilot/release, reviewer đủ điều kiện theo `04-content-contract.md` phải ký đúng version/digest của:

- câu hỏi và năm nhóm red flag;
- copy `URGENT_STOP`, `PAUSE_TODAY`, `BLOCKED_FOR_TODAY` và pain response;
- stop guidance/CTA theo locale Việt Nam;
- global và per-routine stop rule;
- xác nhận không có đường vòng qua deep link, restore hoặc “bài nhẹ hơn”.

Các key của red-flag gate, global outcome/hold/pain route và global stop/CTA phải thuộc exact typed coverage + global sign-off của `CNT-015`. Per-routine setup/contraindication/comfortable-range/stop/escalation keys thuộc `SafetyContentContract` tại `CNT-013`, được cover bởi routine clinical digest/sign-off theo `CNT-014/051`; không được copy chúng sang global artifact để “đủ coverage”. Deep-link/restore bypass là behavior gate được test độc lập. Thiếu một trong hai sign-off domain, thiếu key hoặc digest mismatch → `REL-GATE-CLINICAL` đóng. Developer không tự điền contraindication/clinical guidance.
