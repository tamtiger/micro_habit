# 04 — Routine Content Contract

- **Trạng thái:** Implementation baseline 1.0; routine clinical content pending external sign-off
- **Phạm vi:** Sáu routine MVP bundled offline/local-only, tiếng Việt (`vi-VN`)
- **Chủ sở hữu:** Content + Product
- **Phê duyệt bắt buộc:** Movement Technique Reviewer + Clinical Safety Reviewer + Content QA; Content Author phải khác Clinical Safety Reviewer
- **Tài liệu liên quan:** `03-safety-rule-engine.md`, `08-qa-and-release-gates.md`

Tài liệu này quy định cấu trúc manifest, asset, accessibility, review và phát hành nội dung. Nó không cung cấp contraindication hoặc stop rule lâm sàng cho từng bài; các nội dung đó phải do chuyên gia bên ngoài điền và ký duyệt.

## 1. Catalog MVP đã khóa

### CNT-001 — Sáu routine duy nhất

| ID | Mode | Tên hiển thị `vi-VN` | Thời lượng | RPE |
|---|---|---|---:|---:|
| `REC-01` | `RECOVER` | Thả lỏng tại ghế | 120 giây | 1–2 |
| `REC-02` | `RECOVER` | Đi bộ chậm | 180 giây | 1–2 |
| `MAI-01` | `MAINTAIN` | Reset bàn làm việc | 120 giây | 2–4 |
| `MAI-02` | `MAINTAIN` | Mobility đứng | 240 giây | 2–4 |
| `BUI-01` | `BUILD` | Sức mạnh với ghế | 240 giây | 4–6 |
| `BUI-02` | `BUILD` | Cardio yên lặng | 300 giây | 4–6 |

Tất cả routine phải:

- dài 2–5 phút, không có routine 90 giây hoặc trên 5 phút;
- không nằm sàn, không nhảy/impact cao, không cần thay đồ;
- không cần dụng cụ tập; ghế/bàn chỉ là context/support;
- phù hợp không gian làm việc, tiếng động thấp;
- có biến thể dễ hơn và nút Stop/Skip không gây phạt;
- chỉ được phát hành sau sign-off ở CNT-050.

Không thêm routine thứ bảy bằng remote config, AI hoặc content patch trong MVP. Thay catalog là thay scope và yêu cầu version/review mới.

## 2. Manifest chuẩn

### CNT-010 — Root schema

```ts
type SemVer = string;
type Locale = "vi-VN";
type RoutineId =
  | "REC-01" | "REC-02"
  | "MAI-01" | "MAI-02"
  | "BUI-01" | "BUI-02";
type Mode = "RECOVER" | "MAINTAIN" | "BUILD";
type MessageKey = string;
type Sha256 = string; // 64 lowercase hex characters
type InstantWireV1 = string; // exact YYYY-MM-DDTHH:mm:ss.SSSZ UTC, xem dưới

type ContentManifest = {
  schemaVersion: "1.0.0";
  manifestVersion: SemVer;
  locale: Locale;
  compatibleRuleVersions: [1]; // exact tuple của MVP
  generatedAt: InstantWireV1; // build metadata, không dùng để quyết định
  routines: [Routine, Routine, Routine, Routine, Routine, Routine];
  globalSafetyContent: GlobalSafetyContentContract;
  globalSafetySignOff: GlobalSafetySignOffContract | null;
  messageCatalog: MessageCatalogEntry[];
  manifestDigestSha256: Sha256;
};
```

Mọi `SemVer` phải là canonical SemVer 2.0.0 ASCII: `MAJOR.MINOR.PATCH` bắt buộc; numeric identifier không có leading zero; prerelease/build nếu có tuân grammar SemVer; cấm prefix `v`, whitespace và Unicode look-alike. Parser phải parse rồi serialize lại byte-identical mới chấp nhận. Mọi `Sha256` khớp đúng `^[0-9a-f]{64}$`.

Mọi timestamp trong manifest, sign-off và input `validationInstant` dùng cùng `InstantWireV1` của DATA: exact UTC Gregorian `YYYY-MM-DDTHH:mm:ss.SSSZ`, year `0001..9999`, valid date, second `00..59`, đúng ba fractional digit và literal uppercase `T`/`Z`. Numeric epoch, `+00:00`, lowercase/space, fraction khác ba digit, leap second, year zero/expanded, invalid date, trailing data hoặc domain instant không millisecond-aligned đều bị reject **trước** parse/compare/hash; không normalize alias rồi ký/hash. Nullable field chỉ nhận exact string hoặc JSON null.

`manifestDigestSha256` được tính theo CNT-014. Parser từ chối duplicate JSON object key, unknown root field, locale khác `vi-VN`, routine trùng/thiếu ID hoặc `compatibleRuleVersions` không chứa rule version hiện hành. `MessageCatalogEntry.key` phải unique trong toàn manifest; step ID và easier-variation ID phải unique trong routine và đúng prefix routine; asset ID phải unique qua cả ba asset array và qua toàn manifest. Mọi reference chỉ resolve đúng một target.

### CNT-011 — Routine schema

```ts
type Routine = {
  id: RoutineId;
  revision: SemVer;
  mode: Mode;
  titleKey: MessageKey;
  summaryKey: MessageKey;
  durationSeconds: 120 | 180 | 240 | 300;
  targetRpe:
    | { min: 1; max: 2 }
    | { min: 2; max: 4 }
    | { min: 4; max: 6 };
  context: RoutineContextContract;
  steps: RoutineStep[];
  easierVariation: EasierVariation;
  safetyContent: SafetyContentContract;
  assets: AssetContract;
  accessibility: AccessibilityContract;
  signOff: SignOffContract | null;
};

type RoutineContextContract = {
  reviewStatus: "PENDING_EXTERNAL_SIGN_OFF" | "APPROVED";
  stableChair: "REQUIRED" | "NOT_REQUIRED" | "PENDING_REVIEW";
  stableDeskOrWall: "REQUIRED" | "NOT_REQUIRED" | "PENDING_REVIEW";
  standingSpace: "REQUIRED" | "NOT_REQUIRED" | "PENDING_REVIEW";
  walkingPath: "REQUIRED" | "NOT_REQUIRED" | "PENDING_REVIEW";
  preflightRequirementKeys: {
    stableChair: MessageKey | null;
    stableDeskOrWall: MessageKey | null;
    standingSpace: MessageKey | null;
    walkingPath: MessageKey | null;
  };
  floorRequired: false;
  exerciseEquipmentRequired: false;
  impact: "LOW_NO_JUMP";
  noise: "QUIET";
};
```

Tên routine không đủ để developer tự suy ra yêu cầu điểm tựa/không gian. External movement/clinical review phải điền bốn field context, từng pre-flight message key tương ứng và ký cùng content digest. Release validator từ chối `reviewStatus = PENDING_EXTERNAL_SIGN_OFF`, mọi support field còn `PENDING_REVIEW`, hoặc bất kỳ giá trị ngoài enum; chỉ `reviewStatus = APPROVED` được phát hành. Với mỗi field, giá trị `REQUIRED` bắt buộc key non-null trỏ tới approved `SAFETY` entry; `NOT_REQUIRED` bắt buộc key null.

UI binding của identity copy là bắt buộc, không phải gợi ý: `Routine.titleKey` là visible routine title trên card, pre-flight và Player; `Routine.summaryKey` là mô tả card và routine overview ở pre-flight. Không surface nào được hard-code tên/mô tả trong catalog thay cho hai signed key này. `EasierVariation.titleKey` là heading visible + accessible của phần alternative khi action **Cách dễ hơn** mở ra; nó không thay `Routine.titleKey` và không được bỏ dù variation instruction/demo hợp lệ.

App render/confirm từng field `REQUIRED` theo thứ tự cố định `stableChair → stableDeskOrWall → standingSpace → walkingPath`; field `NOT_REQUIRED` không hiện prompt. Chỉ khi mọi prompt nhận `Có` mới enable Start. Bất kỳ `Không` nào chỉ mở selector cùng mode/nhẹ hơn; không có override, không tự chọn/fallback, không persist câu trả lời và không dùng nó để infer bối cảnh lần sau. Rule engine không nhận các field này.

### CNT-012 — Step và easier variation

```ts
type RoutineStep = {
  id: string;                  // `${routineId}-STEP-${NN}`
  order: number;               // liên tục từ 1, không gap/trùng
  instructionKey: MessageKey;
  screenReaderInstructionKey: MessageKey;
  dosage:
    | { kind: "DURATION"; seconds: number; reps: null }
    | {
        kind: "REPETITIONS";
        seconds: null;
        reps: number;
        estimatedSeconds: number;
      };
  transitionAfterSeconds: number;
  demoAssetIds: string[];
  requiredDistinctDemoAngles: 1 | 2;
  easierVariationRef: string;  // trỏ tới EasierVariation.id
};

type EasierVariationStep = {
  sourceStepId: string;        // trỏ đúng RoutineStep.id cùng index
  instructionKey: MessageKey;
  demoAssetIds: string[];
  requiredDistinctDemoAngles: 1 | 2;
};

type EasierVariation = {
  id: string;                  // `${routineId}-EASY-01`
  titleKey: MessageKey;
  steps: EasierVariationStep[];
};
```

Validation:

- `steps.length >= 1`; order phải liên tục và ổn định.
- Tổng `(seconds hoặc estimatedSeconds) + transitionAfterSeconds` của mọi step phải bằng `durationSeconds`; transition của step cuối phải bằng `0`.
- Timer UI dùng `durationSeconds` từ manifest, không suy ra từ video.
- `demoAssetIds` chỉ trỏ tới `VideoAsset`; external technique reviewer quyết định `requiredDistinctDemoAngles` cho từng step và ký cùng digest.
- MVP có đúng một easier variation cho mỗi routine: mọi `RoutineStep.easierVariationRef` phải bằng chính xác `Routine.easierVariation.id`. Không có variation-level mapping thứ hai hoặc “bản khó hơn”.
- `easierVariation.steps.length == routine.steps.length` và là bijection giữ nguyên thứ tự: item `i.sourceStepId == routine.steps[i].id`; không thiếu, lặp hoặc map chéo step. Mỗi item có `demoAssetIds` non-empty/unique, chỉ trỏ video của cùng routine và số `angle` distinct phải đạt `requiredDistinctDemoAngles` của chính variation step.
- Easier step kế thừa đúng `dosage` và `transitionAfterSeconds` của source step; schema v1 không có dosage/timing override. Variation chỉ thay technique/range/support qua approved instruction/demo. Không nhét reps/seconds override vào text; nếu reviewer cần đổi timing thì phải sửa schema/version và tính lại duration/digest.
- `Routine.context` là conservative union của support/space requirements cho cả primary steps và easier-variation steps. Easier variation không được thêm floor/equipment/impact/noise ngoài root contract; technique reviewer phải xác nhận mọi support nó dùng đã được đánh dấu `REQUIRED` và có signed preflight key. Player không được suy context mới từ text/video.
- Mọi ID/key trong các array reference phải unique trong chính array đó.
- Instruction không dùng claim điều trị/chẩn đoán, cam kết kết quả hoặc ngôn ngữ gây xấu hổ.

`seconds`, `estimatedSeconds`, `reps` phải là integer dương; `transitionAfterSeconds` là integer không âm. Không nhét timing vào text tự do.

Player timer v1 dùng persisted discriminated phase tại ARC-014. Với step `DURATION`, `plannedStepMs = seconds * 1_000`; với `REPETITIONS`, `plannedStepMs = estimatedSeconds * 1_000`, còn `reps` là dosage guidance hiển thị — MVP không có nút “đã đủ reps” ẩn và tự advance khi timer chạm biên. `plannedTransitionMs = transitionAfterSeconds * 1_000`. Mọi phép nhân/trừ dùng checked int64.

Trong `STEP_TIMER`, authority là `currentStepRemainingMs` đã persist; `remainingSeconds = ceilDiv(currentStepRemainingMs, 1_000)` với `ceilDiv(0,1_000)=0`, tương đương `(ms / 1_000) + (ms % 1_000 == 0 ? 0 : 1)` để tránh overflow. Vì vậy `1/999/1_000 ms` đều hiển thị `1`, còn equality `0` kết thúc phase. Reducer atomically chuyển sang `STEP_TRANSITION` nếu transition dương, sang `STEP_TIMER` của step kế nếu transition bằng 0, hoặc `COMPLETION_CTA_WAIT` sau step cuối; không giữ state STEP với zero và không render `1` sau equality.

Monotonic callback chỉ consume tối đa remaining budget của **phase hiện tại**. Phần callback lateness vượt boundary không được carry sang transition/step chưa render; phase mới bắt đầu anchor tại current snapshot. Chỉ milliseconds consume trong `STEP_TIMER` cộng `accumulatedActiveMillis`; `STEP_TRANSITION`, pause, background và `COMPLETION_CTA_WAIT` không cộng. Transition tự advance tại exact zero theo cùng ceil/equality rule. Skip chỉ hợp lệ trong `STEP_TIMER` khi remaining dương: ghi step ID đúng một lần, không cộng planned remainder, rồi đi qua cùng next-phase reducer; race tại equality thuộc timer completion và không ghi skip. Process recovery phải resume exact phase/remaining/skip set, không reset hoặc suy từ session-total counter.

### CNT-013 — Safety content schema

```ts
type SafetyContentContract = {
  status:
    | "PENDING_EXTERNAL_SIGN_OFF"
    | "APPROVED"
    | "REJECTED"
    | "EXPIRED";
  comfortableRangeInstructionKey: MessageKey | null;
  setupSafetyKeys: MessageKey[];
  contraindicationDisposition:
    | "LISTED"
    | "NONE_BEYOND_GLOBAL"
    | null;
  contraindicationKeys: MessageKey[];
  stopRuleKeys: MessageKey[];
  escalationMessageKey: MessageKey | null;
  clinicalContentDigestSha256: Sha256 | null;
};
```

Authoring validation cho phép `PENDING_EXTERNAL_SIGN_OFF` với array rỗng/null để developer dựng UI. **Release validation** chỉ pass khi:

- `status = APPROVED`;
- `comfortableRangeInstructionKey` và `escalationMessageKey` khác null;
- `setupSafetyKeys` và `stopRuleKeys` non-empty;
- `contraindicationDisposition = LISTED` thì `contraindicationKeys` non-empty; `NONE_BEYOND_GLOBAL` thì array phải rỗng. Chỉ clinical reviewer được chọn disposition; developer không suy luận từ array rỗng;
- mọi key tồn tại, locale `vi-VN`, có `category=SAFETY` theo CNT-040A và nằm trong digest đã được reviewer ký;
- không có placeholder `TBD`, `TODO`, `PLACEHOLDER` hoặc nội dung do AI/runtime tạo.

Runtime phải render approved per-routine safety content trên pre-flight theo đúng sequence sau, sau global `preflightSafety` và trước các câu context: `comfortableRangeInstructionKey` → từng `setupSafetyKeys[]` theo array order → từng `contraindicationKeys[]` theo array order chỉ khi disposition là `LISTED` → từng `stopRuleKeys[]` theo array order → `escalationMessageKey`. Với `NONE_BEYOND_GLOBAL`, contraindication segment có cardinality zero; không chèn placeholder. Không sort, dedupe, collapse mặc định, thay key bằng global copy hoặc bỏ một segment.

Ngay sau block đó là checkbox/switch code-native `preflight_routine_safety_acknowledgement` với exact `vi-VN` copy **“Tôi đã đọc hướng dẫn an toàn của bài này.”**; control nằm sau toàn block trong semantic/focus order. Start chỉ enable khi acknowledgement của **current content identity** là true và mọi context prompt `REQUIRED` đã trả lời Có. Acknowledgement chỉ là in-memory pre-start state, không analytics/export; đổi routine, content identity, process recreation hoặc rời pre-flight phải clear nó cùng context confirmations. Session start snapshot đã giữ content identity, nên không invent một persistent acknowledgement field. Missing/unapproved key, sai sequence/cardinality hoặc code path bypass acknowledgement là content-contract error và không tạo Session.

Contraindication, stop rule, setup warning và escalation text cụ thể **chưa được định nghĩa trong tài liệu này**. Product/Engineering không được tự điền. Đây là dependency bên ngoài và release blocker `CNT_CLINICAL_CONTENT_PENDING` cho cả sáu routine.

### CNT-014 — Canonical digest, không vòng lặp

Mọi digest trong contract này dùng đúng [RFC 8785 JSON Canonicalization Scheme (JCS)](https://www.rfc-editor.org/rfc/rfc8785.html), không dùng biến thể theo platform. Trước canonicalization, decoder phải từ chối duplicate object key **trước khi** materialize map; mọi property name, dynamic map key và string value phải là chuỗi Unicode scalar hợp lệ ở dạng NFC, không có lone surrogate. Content schema này đặt precondition chặt hơn tập JSON number chung: raw number token phải khớp đúng `0|-?[1-9][0-9]*`, có giá trị trong `[-(2^53-1), 2^53-1]`; riêng `-0` và mọi token chứa `e|E`, fraction, `NaN` hoặc infinity đều bị từ chối **trước hash**, kể cả `1e0` có giá trị toán học là integer. Canonicalizer không tự normalize/sửa input: nó áp đúng primitive serialization và thứ tự property theo UTF-16 code unit của RFC 8785, giữ nguyên thứ tự array, rồi phát UTF-8 không BOM/whitespace. SHA-256 được tính trên chính byte sequence đó và encode lowercase hex. Locale collation, iteration order của map hoặc serializer mặc định của Kotlin/JavaScript không phải implementation hợp lệ nếu chưa chứng minh byte-identical bằng golden vectors liên nền tảng.

Payload routine digest có shape duy nhất sau:

```ts
type DigestMessageEntryV1 = Omit<
  MessageCatalogEntry,
  "reviewedDigestSha256"
> & { reviewedDigestSha256: null };

type RoutineClinicalDigestCoreV1 = Omit<
  Routine,
  "assets" | "signOff" | "safetyContent"
> & {
  safetyContent: Omit<
    SafetyContentContract,
    "clinicalContentDigestSha256"
  > & { clinicalContentDigestSha256: null };
};

type RoutineClinicalDigestPayloadV1 = {
  digestSchema: "ROUTINE_CLINICAL_V1";
  routine: RoutineClinicalDigestCoreV1;
  referencedMessages: DigestMessageEntryV1[];
  referencedAssets: (VideoAsset | CaptionAsset | ImageAsset)[];
};
```

`referencedAssets` là transitive union của asset được step/easier variation tham chiếu, cộng caption/poster mà video đó tham chiếu; đưa mỗi asset đúng một lần và sort tăng dần theo globally-unique `id`. `referencedMessages` là transitive union của mọi `MessageKey` trong routine core **và trong toàn bộ referenced asset object**, gồm ít nhất `VideoAsset.transcriptKey` và `ImageAsset.altTextKey`; đưa mỗi entry đúng một lần và sort tăng dần theo `key`. Trước khi serialize, validator đọc bytes của từng regular file đã resolve theo CNT-021, tính SHA-256 và yêu cầu bằng chính field `asset.sha256`; payload dùng object asset nguyên vẹn, không thêm field hash alias. Các array có ý nghĩa trình bày/thực thi trong `routine` và asset object giữ nguyên thứ tự manifest; chỉ hai union array nói trên được sort. Đổi transcript/alt text làm routine clinical digest thay đổi dù asset bytes không đổi.

`clinicalContentDigestSha256` là SHA-256 của canonical JSON bytes của đúng `RoutineClinicalDigestPayloadV1`. Payload không gồm `signOff`, asset không được tham chiếu, root `generatedAt`, manifest version/digest hoặc global safety object.

Technique reviewer và clinical reviewer cùng ký **exact computed content digest** vào `approvedContentDigestSha256`; field `safetyContent.clinicalContentDigestSha256` phải bằng digest đó.

Global payload có shape duy nhất:

```ts
type GlobalSafetyDigestPayloadV1 = {
  digestSchema: "GLOBAL_SAFETY_V1";
  globalSafetyContent: Omit<
    GlobalSafetyContentContract,
    "globalSafetyContentDigestSha256"
  > & { globalSafetyContentDigestSha256: null };
  referencedMessages: DigestMessageEntryV1[];
};
```

`referencedMessages` là union mọi `MessageKey` trong các typed slot của global contract, mỗi entry đúng một lần và sort tăng dần theo `key`. `globalSafetyContentDigestSha256` là SHA-256 canonical JSON bytes của đúng payload trên. Không gồm `globalSafetySignOff`, routine, `generatedAt` hoặc manifest digest. Clinical reviewer ký exact digest này vào `globalSafetySignOff.clinicalReviewer.approvedGlobalSafetyContentDigestSha256`.

Với mỗi message entry, `reviewedDigestSha256 = SHA-256(canonical JSON {key, locale, text, category})`; digest này kiểm tra identity của riêng entry và không thay thế clinical sign-off. Routine/global content digest tiếp tục cover toàn entry được tham chiếu, vì vậy sửa text/key/category luôn làm sign-off mismatch.

`manifestDigestSha256` hash một deep copy của toàn `ContentManifest` sau đúng hai phép thay thế digest-only: root `manifestDigestSha256 = null`; và, với mỗi routine có `signOff != null`, `signOff.contentQa.approvedManifestDigestSha256 = null`. JSON `null` là giá trị thật trong payload hash, không phải chuỗi rỗng, 64 số `0` hoặc field bị bỏ đi. Không normalize field nào khác: mọi sign-off identity/status/time/digest còn lại, gồm global safety sign-off, vẫn nằm trong input hash; thứ tự sáu routine và mọi array cũng giữ nguyên. Content QA phải điền **cả sáu** reviewer/timestamp sau khi toàn bộ routine/global professional sign-off đã cố định và thỏa exact manifest-wide cutoff tại CNT-051, rồi mới tính digest và ghi cùng digest vào từng routine. Recompute theo cùng phép thay thế phải byte-identical; thay bất kỳ covered field/asset làm verify fail.

### CNT-015 — Global safety copy và sign-off

```ts
type GlobalSafetyContentContract = {
  status:
    | "PENDING_EXTERNAL_SIGN_OFF"
    | "APPROVED"
    | "REJECTED"
    | "EXPIRED";
  ageGate: {
    questionKey: MessageKey;
    descriptionKey: MessageKey;
    adultOptionLabelKey: MessageKey;
    minorOptionLabelKey: MessageKey;
    safeExit: {
      titleKey: MessageKey;
      bodyKey: MessageKey;
      closeActionLabelKey: MessageKey;
    };
  } | null;
  scopeEligibility: {
    titleKey: MessageKey;
    bodyKeys: readonly [
      generalWellnessLimitKey: MessageKey,
      excludedUseCasesKey: MessageKey,
      stopWarningKey: MessageKey
    ];
    acknowledgementLabelKey: MessageKey;
    questionKey: MessageKey;
    yesOptionLabelKey: MessageKey;
    yesOptionDescriptionKey: MessageKey;
    noOrUnsureOptionLabelKey: MessageKey;
    continueActionLabelKey: MessageKey;
    reackTitleKey: MessageKey;
    reackContinueActionLabelKey: MessageKey;
    safeExit: {
      titleKey: MessageKey;
      bodyKey: MessageKey;
      closeActionLabelKey: MessageKey;
    };
  } | null;
  redFlagGate: {
    questionKey: MessageKey;
    symptomKeys: readonly [
      chestPainOrPressureKey: MessageKey,
      severeDizzinessOrFaintingKey: MessageKey,
      abnormalBreathlessnessKey: MessageKey,
      abnormalRapidOrIrregularHeartbeatKey: MessageKey,
      acuteOrRapidlyWorseningSymptomKey: MessageKey
    ];
    anyPresentOptionLabelKey: MessageKey;
    nonePresentOptionLabelKey: MessageKey;
  } | null;
  acuteIssueGate: {
    questionKey: MessageKey;
    optionBindings: readonly [
      none: {
        value: "none";
        labelKey: MessageKey;
      },
      acuteIllness: {
        value: "acute_illness";
        labelKey: MessageKey;
      },
      newOrWorseningPainOrInjury: {
        value: "new_or_worsening_pain_or_injury";
        labelKey: MessageKey;
      },
      medicallyRestricted: {
        value: "medically_restricted";
        labelKey: MessageKey;
      }
    ];
  } | null;
  urgentStop: {
    titleKey: MessageKey;
    limitationBodyKey: MessageKey;
    closeActionLabelKey: MessageKey;
  } | null;
  pauseToday: {
    titleKey: MessageKey;
    reasonKeys: {
      acuteIllnessKey: MessageKey;
      newOrWorseningPainOrInjuryKey: MessageKey;
      medicallyRestrictedKey: MessageKey;
    };
    bodyKey: MessageKey;
    homeActionLabelKey: MessageKey;
  } | null;
  holdRouteBindings: {
    redFlag: "urgentStop";
    acuteIllness: "pauseToday.acuteIllness";
    newOrWorseningPainOrInjury: "pauseToday.newOrWorseningPainOrInjury";
    medicallyRestricted: "pauseToday.medicallyRestricted";
    postSessionNewOrWorsePain: "playerSafety.painResponse";
    corruptHoldFailClosed: "corruptHoldFailClosed";
  } | null;
  corruptHoldFailClosed: {
    titleKey: MessageKey;
    bodyKey: MessageKey;
    retryActionLabelKey: MessageKey;
    manageDataActionLabelKey: MessageKey;
  } | null;
  nextDayRecheck: {
    titleKey: MessageKey;
    bodyKey: MessageKey;
    checkInActionLabelKey: MessageKey;
  } | null;
  preflightSafety: {
    titleKey: MessageKey;
    checklistKeys: readonly [
      clearSpaceKey: MessageKey,
      stableSupportKey: MessageKey,
      comfortableRangeKey: MessageKey,
      stopWarningKey: MessageKey
    ];
  } | null;
  playerSafety: {
    painAnswerLabels: {
      yesKey: MessageKey;
      noKey: MessageKey;
    };
    stopDialog: {
      titleKey: MessageKey;
      questionKey: MessageKey;
      continueRoutineActionLabelKey: MessageKey;
    };
    terminalPainGate: {
      titleKey: MessageKey;
      questionKey: MessageKey;
    };
    pendingPainGate: {
      titleKey: MessageKey;
      bodyKey: MessageKey;
      questionKey: MessageKey;
      entryActionLabelKey: MessageKey;
    };
    painResponse: {
      titleKey: MessageKey;
      bodyKeys: readonly [
        reportedPainKey: MessageKey,
        urgentSymptomsKey: MessageKey,
        limitationKey: MessageKey
      ];
      homeActionLabelKey: MessageKey;
    };
  } | null;
  emergencyDial: EmergencyDialContract | null;
  globalSafetyContentDigestSha256: Sha256 | null;
};

type EmergencyDialContract = {
  locale: "vi-VN";
  dialTargetDigits: string; // `^[0-9]{2,15}$`, ví dụ cụ thể cần external approval
  instructionTemplateKey: MessageKey;
  actionLabelKey: MessageKey;
  unavailableMessageKey: MessageKey;
  intentAction: "ACTION_DIAL";
};

type GlobalSafetySignOffContract = {
  contentAuthor: {
    reviewerRef: string;
    credentialStatus: "VERIFIED_CURRENT" | "UNVERIFIED" | "EXPIRED";
    authoredAt: InstantWireV1;
  };
  clinicalReviewer: {
    reviewerRef: string;
    profession:
      | "PHYSIOTHERAPIST_OR_PHYSICAL_THERAPIST"
      | "PHYSICIAN";
    jurisdiction: string;
    credentialStatus: "VERIFIED_CURRENT" | "UNVERIFIED" | "EXPIRED";
    credentialVerifiedAt: InstantWireV1 | null;
    signedAt: InstantWireV1 | null;
    validThrough: InstantWireV1 | null;
    approvedGlobalSafetyContentDigestSha256: Sha256 | null;
  };
};
```

Mỗi object/field là một semantic slot duy nhất; các tuple có cardinality, thứ tự và vai trò cố định đúng như label trong type. Runtime render theo field/tuple position đó, không sort, suy role từ text, gộp bucket hoặc dùng một key ở slot khác. `acuteIssueGate.optionBindings` phải giữ đúng bốn phần tử theo thứ tự schema; mỗi `value` phải byte-equal literal đi kèm và label của lựa chọn phải resolve từ chính `labelKey` cùng phần tử. `holdRouteBindings` phải byte-equal sáu literal trong schema, nên từng `SafetyHold.kind` chỉ resolve đúng route đã ký. Mọi key phải resolve đúng một entry `locale=vi-VN`, `category=SAFETY`, `reviewStatus=APPROVED`; một entry có thể được nhiều slot tham chiếu, nhưng mọi tuple cấm key lặp. Khi tính digest, union referenced message chỉ giữ mỗi key một lần rồi sort theo `key`. Union phải bằng chính xác tập global-safety key mà binary render; hard-code thêm clinical copy ngoài manifest, thiếu key, extra key, sai slot, sai tuple order/cardinality, đổi enum-label binding hoặc route literal khác đều là contract error.

Trong release profile, `emergencyDial` bắt buộc non-null và được render ở cả `urgentStop` lẫn `playerSafety.painResponse`, ngay trước các CTA của route tương ứng. Text của `instructionTemplateKey` phải chứa đúng một placeholder `{emergency_number}` và không hard-code phone-number literal khác; UI thay placeholder bằng chính `dialTargetDigits`. CTA luôn tạo `Intent(ACTION_DIAL, Uri.fromParts("tel", dialTargetDigits, null))`, không `ACTION_CALL`, không tự gọi và không xin phone permission. `actionLabelKey` là nhãn CTA; `unavailableMessageKey` dùng khi không resolve được dialer. Validator yêu cầu target đúng regex, locale khớp root, không URI/control/whitespace; target, template và action đều nằm trong global digest/sign-off nên binary không thể hiển thị một số nhưng dial số khác.

Authoring profile cho phép `status=PENDING_EXTERNAL_SIGN_OFF`, các typed route object/emergency config và digest/sign-off là null. Release validator nhận một exact `InstantWireV1 validationInstant` cố định và chỉ pass khi `status=APPROVED`, **mọi** typed route object (bao gồm `acuteIssueGate`) cùng `emergencyDial` non-null và hợp lệ; content author/clinical reviewer đúng vai trò CNT-050, credential current, identity/jurisdiction hợp lệ theo CNT-051, và `contentAuthor.reviewerRef != clinicalReviewer.reviewerRef` theo byte sau validation. `contentAuthor.authoredAt`, `clinicalReviewer.credentialVerifiedAt`, `clinicalReviewer.signedAt`, `clinicalReviewer.validThrough` đều là InstantWireV1 non-null; `authoredAt <= signedAt <= validationInstant`, `credentialVerifiedAt <= signedAt <= validationInstant < validThrough`, `validThrough - signedAt <= 365 ngày`; và ba digest `computed global safety = globalSafetyContentDigestSha256 = clinicalReviewer.approvedGlobalSafetyContentDigestSha256` byte-identical. Equality tại `validThrough` là expired. `content_version` được lưu cùng onboarding acknowledgement/event phải map đúng `ContentManifest.manifestVersion`; `content_digest` phải map đúng `globalSafetyContent.globalSafetyContentDigestSha256`, không dùng app version, root manifest digest hoặc routine digest. Thay route binding, acute enum-label binding, key, text safety, emergency target/action hoặc catalog manifest version làm acknowledgement artifact mới và sign-off/acceptance cũ không được giả là hiện hành.

## 3. Asset contract

### CNT-020 — Asset schema

```ts
type DemoAngle =
  | "FRONT" | "SIDE" | "THREE_QUARTER" | "DETAIL";

type AssetLocalPath = string; // canonical relative POSIX path theo CNT-021

type VideoAsset = {
  id: string;
  kind: "VIDEO_DEMO";
  localPath: AssetLocalPath;
  mimeType: "video/mp4";
  codec: "H264";
  widthPx: number;
  heightPx: number;
  durationMs: number; // positive safe integer, derived exactly by CNT-021
  angle: DemoAngle;
  hasAudioInstruction: boolean;
  captionTrackId: string;
  transcriptKey: MessageKey;
  posterAssetId: string;
  sha256: Sha256;
};

type CaptionAsset = {
  id: string;
  kind: "CAPTION";
  localPath: AssetLocalPath;
  mimeType: "text/vtt";
  locale: "vi-VN";
  sha256: Sha256;
};

type ImageAsset = {
  id: string;
  kind: "POSTER";
  localPath: AssetLocalPath;
  mimeType: "image/webp" | "image/png";
  widthPx: number;
  heightPx: number;
  altTextKey: MessageKey;
  sha256: Sha256;
};

type AssetContract = {
  videos: VideoAsset[];
  captions: CaptionAsset[];
  posters: ImageAsset[];
};
```

### CNT-021 — Asset validation

- `localPath` phải là UTF-8 NFC relative POSIX path khớp `^(?:[A-Za-z0-9][A-Za-z0-9._-]*/)*[A-Za-z0-9][A-Za-z0-9._-]*$`. Dấu phân cách duy nhất là `/`; cấm path rỗng, absolute path, drive prefix, URI scheme, `.`/`..` segment, backslash, colon, `%`, NUL/control character và symlink. Validator resolve từ một bundled-asset root cố định, canonicalize, rồi yêu cầu target là regular file nằm bên trong root; không fallback sang URL hoặc filesystem path khác.
- Asset ID phải unique trong toàn manifest; step/easier-variation chỉ được tham chiếu asset thuộc `Routine.assets` của chính routine đó. `captionTrackId` và `posterAssetId` cũng phải resolve đúng một caption/poster cùng routine; reference array không được chứa ID lặp.
- Release artifact không được chứa asset mồ côi: mọi video phải được ít nhất một step/easier variation của chính routine tham chiếu; mọi caption/poster phải nằm trong transitive closure của một video được tham chiếu. Authoring profile có thể giữ orphan tạm thời nhưng phải báo warning có ID; release profile fail.
- Mỗi step có ít nhất một video demo; mọi asset tồn tại, hash đúng và mở được offline.
- Mọi dimension và `durationMs` là JSON safe integer; `widthPx >= 720`, `heightPx >= 720`, `durationMs > 0`. Image decoder phải trả intrinsic width/height đúng field; video không méo và orientation được UI hỗ trợ.
- Poster format classifier v1 chỉ đọc bytes của regular file đã resolve, không đọc/suy từ phần mở rộng `localPath`, tên file, manifest MIME hint hoặc ContentResolver. `PNG` iff file có ít nhất 8 byte và bytes `0..7` bằng exact hex `89 50 4E 47 0D 0A 1A 0A`. `WEBP` iff file có ít nhất 12 byte, bytes `0..3` là ASCII `RIFF` (`52 49 46 46`) và bytes `8..11` là ASCII `WEBP` (`57 45 42 50`). Hai branch là mutually exclusive; thiếu/truncated/không khớp signature bị từ chối, không fallback theo suffix.
- Với mỗi `ImageAsset`, ba kết quả phải đồng thuận tuyệt đối: classifier bytes ở trên, `mimeType` khai báo (`PNG ↔ image/png`, `WEBP ↔ image/webp`) và encoded format/MIME do production image decoder nhận diện. Release validator phải parse được container hợp lệ, decode **toàn bộ poster** thành bitmap offline và đối chiếu intrinsic `widthPx`/`heightPx`; signature đúng nhưng payload hỏng, decoder trả null/generic/format khác, declared MIME hoán đổi hoặc decoder chỉ đọc bounds mà không decode pixels đều fail `CNT_ASSET_FORMAT_INVALID`. Release device matrix phải full-decode mọi poster bằng chính production image path trên từng device/API bắt buộc; không được chỉ dựa vào authoring/reference decoder hoặc extension.
- Video là non-fragmented ISO BMFF MP4 có đúng một `moov`, không `moof`, đúng một enabled `vide` track với sample entry `avc1|avc3`, không external data reference, edit list hoặc non-identity rotation matrix. Sample width/height phải bằng `widthPx`/`heightPx`; release device matrix phải decode được frame đầu, frame giữa và frame cuối bằng production player. Field `codec="H264"` không được tin thay metadata thực.
- Duration authority duy nhất là `mvhd.timescale` và `mvhd.duration` của movie header v0/v1. Cả hai phải dương; duration all-ones/unknown bị cấm. Validator dùng arbitrary-precision integer arithmetic để tính `containerDurationMs = ceil(durationTicks * 1000 / timescale)`, yêu cầu kết quả là safe integer và **bằng chính xác** `VideoAsset.durationMs`; không dùng float, OS metadata, frame-count/fps, decoder rounding hoặc tolerance. File có duration/header mâu thuẫn bị từ chối.
- `hasAudioInstruction=true` iff MP4 có ít nhất một enabled audio track; `false` thì không được có enabled audio track. Audio instruction phải được transcript/caption cover; semantic match vẫn cần manual Content QA.
- Mỗi step phải có số `angle` khác nhau trong `demoAssetIds` lớn hơn hoặc bằng `requiredDistinctDemoAngles`; reviewer quyết định 1 hay 2, không phải developer.
- Caption là UTF-8 không BOM, LF-only, bắt đầu bằng canonical header `WEBVTT\n`; cue timestamp dùng đúng `HH:MM:SS.mmm`. File phải có ít nhất một cue, cue theo thứ tự không giảm và không overlap, với integer millisecond `0 <= start < end <= linked VideoAsset.durationMs`; target đúng equality được phép, vượt `1 ms` phải fail. Một caption chỉ được link tới các video có cùng duration; validator không đoán rescale. Caption khớp lời nói/hướng dẫn hiển thị và hoạt động khi mute.
- Transcript chứa đầy đủ thông tin cần làm bài; audio/video không là kênh duy nhất.
- Poster không chứa chỉ dẫn an toàn chỉ thể hiện bằng hình.
- Không có flash quá 3 lần/giây, âm thanh đột ngột hoặc autoplay có tiếng.
- Player phải có Play/Pause/Replay/Stop, hỗ trợ reduced motion và không bắt buộc xem animation để hiểu step. `Replay` chỉ điều khiển **demo media hiện tại**: từ thao tác người dùng, seek signed demo về media position `0` rồi play; nó không restart routine step, không đổi `PlayerCheckpoint`, phase/substate/remaining/transition, accumulated-active/cadence, skip record, Session hay product event. Media position/playback state không persist; recovery vẫn lấy canonical routine state và khởi tạo demo ở position `0` theo presentation adapter.
- Không tải asset từ CDN trong MVP; core session phải hoạt động ở airplane mode.

## 4. Accessibility contract

### CNT-030 — Accessibility schema

```ts
type AccessibilityContract = {
  screenReaderTitleKey: MessageKey;
  routineOverviewKey: MessageKey;
  postureAndSetupKey: MessageKey;
  stopButtonLabelKey: MessageKey;
  pauseButtonLabelKey: MessageKey;
  skipButtonLabelKey: MessageKey;
  reducedMotionAlternative: "STATIC_STEPS_WITH_TIMER";
  informationNotColorOnly: true;
  informationNotAudioOnly: true;
};
```

Release validation/UI integration PHẢI bảo đảm:

| Field | Surface/element binding duy nhất |
|---|---|
| `screenReaderTitleKey` | Semantic pane title/level-1 heading khi vào pre-flight và Player; visual title vẫn là `Routine.titleKey`. |
| `routineOverviewKey` | Đoạn overview accessible ngay sau title ở pre-flight, trước safety content; không thay `Routine.summaryKey` visible. |
| `postureAndSetupKey` | Semantic heading/intro ngay trước exact per-routine safety sequence của CNT-013. |
| `stopButtonLabelKey` | Accessible name của action Stop/Dừng bài trong `STEP_TIMER` và `STEP_TRANSITION`; action mở stop dialog, không dùng key này cho pain answer. Control absent ở `COMPLETION_CTA_WAIT`. |
| `pauseButtonLabelKey` | Accessible name của action Pause trong `STEP_TIMER`/`STEP_TRANSITION` khi `substate=PLAYING`; khi đã pause, action Resume dùng fixed app resource `player_resume_action`, không giả rằng signed key có nghĩa “resume”. Control absent ở `COMPLETION_CTA_WAIT`. |
| `skipButtonLabelKey` | Accessible name của action Skip current step chỉ trong `STEP_TIMER` khi remaining dương; control hidden/disabled ở phase khác và không phát click. |

Sáu field trên phải được consume đúng surface; duplicate-consume ở vai trò khác không bù cho field bị bỏ. Fixed non-clinical app resources như Resume không thuộc MessageCatalog và không được dùng để thay signed safety/accessibility field.

- mọi message key trong accessibility contract resolve đúng một approved entry; mỗi step dùng chính `RoutineStep.screenReaderInstructionKey`, nên mapping step→announcement là bijection theo `steps[]`, không có progress-key pool hoặc extra key bị bỏ qua;
- screen reader đọc title, tổng thời lượng, setup, từng step, timer và CTA Stop/Pause/Skip. Khi step bắt đầu, announce đúng `screenReaderInstructionKey` của step đó rồi current timer state; không dùng `instructionKey` thay thế hoặc ghép free text;
- Timer announcement không lấy từ `MessageCatalog` và không có interpolation token trong manifest. Formatter code-native v1 duy nhất là: remaining `0` → `Còn 0 giây`; `minutes=0` → `Còn {seconds} giây`; `seconds=0` → `Còn {minutes} phút`; còn lại → `Còn {minutes} phút {seconds} giây`, với decimal integer không leading zero. `remainingSeconds` phải là đúng nonnegative integer của **current-step** canonical player timer đang render tại cùng checkpoint; accessibility layer không tự tính một timer thứ hai từ wall time, media duration hoặc event timestamp.
- Cadence dùng `accumulatedActiveMillis`, counter chỉ tăng khi `phase=STEP_TIMER && substate=PLAYING`: ordinal `k>=1` đến hạn tại `k*30_000 ms`, mỗi ordinal announce tối đa một lần và checkpoint `lastAnnouncedCadenceOrdinal`. Pause/background/transition/CTA wait không tiến cadence; resume/recovery không replay ordinal. Nếu cadence và step-start cùng một checkpoint, emit một combined step+timer announcement, không emit timer lần hai. Step timer zero chuyển phase atomically theo CNT-012 và **không** có zero announcement riêng; next-step instruction hoặc completion state thay thế nó. TalkBack live region là `polite`, không giành focus và không announce mỗi tick;
- Android font/display scaling không cắt instruction hoặc che CTA ở mức accessibility lớn nhất build hỗ trợ;
- thứ tự focus theo logic, focus không nhảy khi timer tick;
- caption bật được độc lập với âm thanh;
- text/controls đạt WCAG 2.2 AA cho contrast; target chạm tối thiểu theo guideline native của từng nền tảng;
- portrait/landscape và zoom không làm mất safety copy;
- reduced-motion path hiển thị static steps + timer, không auto-play chuyển động;
- trạng thái Recover/Maintain/Build và safety không chỉ phân biệt bằng màu/icon.

## 5. Message catalog và language guardrail

### CNT-040 — Message entry

```ts
type MessageCatalogEntry = {
  key: MessageKey;
  locale: "vi-VN";
  text: string;
  category:
    | "TITLE"
    | "INSTRUCTION"
    | "ACCESSIBILITY"
    | "SAFETY"
    | "EXPLANATION";
  reviewStatus: "DRAFT" | "APPROVED" | "REJECTED" | "EXPIRED";
  reviewedDigestSha256: Sha256 | null;
};
```

### CNT-040A — Total key-role → category matrix

Category là constraint theo **reference role**, không được suy từ key prefix, text, UI surface hoặc reviewer phỏng đoán. Matrix dưới đây exhaustive cho mọi `MessageKey` field của schema `1.0.0`; mỗi non-null reference phải resolve entry có exact category tương ứng.

| Reference role/path | Required `category` |
|---|---|
| `Routine.titleKey`; `Routine.easierVariation.titleKey` | `TITLE` |
| `Routine.summaryKey` | `EXPLANATION` |
| `Routine.steps[].instructionKey`; `Routine.easierVariation.steps[].instructionKey` | `INSTRUCTION` |
| `Routine.steps[].screenReaderInstructionKey`; `Routine.assets.videos[].transcriptKey`; `Routine.assets.posters[].altTextKey`; `Routine.accessibility.screenReaderTitleKey`; `Routine.accessibility.routineOverviewKey`; `Routine.accessibility.postureAndSetupKey`; `Routine.accessibility.stopButtonLabelKey`; `Routine.accessibility.pauseButtonLabelKey`; `Routine.accessibility.skipButtonLabelKey` | `ACCESSIBILITY` |
| Mọi non-null `Routine.context.preflightRequirementKeys.{stableChair,stableDeskOrWall,standingSpace,walkingPath}`; `Routine.safetyContent.comfortableRangeInstructionKey`; mọi item của `setupSafetyKeys[]`, `contraindicationKeys[]`, `stopRuleKeys[]`; `escalationMessageKey` | `SAFETY` |
| Mọi `MessageKey` reachable trong `ContentManifest.globalSafetyContent`, gồm toàn typed slot/tuple và ba key của nested `EmergencyDialContract` | `SAFETY` |

Hai accessibility field có tên `postureAndSetupKey`/`stopButtonLabelKey` chỉ là semantic heading/action label và vẫn là `ACCESSIBILITY`; chúng không được chứa hoặc thay thế unique setup/stop guidance của `SafetyContentContract`, vốn bắt buộc `SAFETY`. Tương tự, title/instruction/transcript/alt/summary không được dùng làm nơi duy nhất chứa safety warning để né clinical category/sign-off.

Nếu cùng một key được reuse ở nhiều role, entry phải thỏa **tất cả** required category; hai role đòi category khác nhau là `CNT_MESSAGE_CATEGORY_MISMATCH`, không clone/coerce category theo call site. Authoring và release validator đều reject referenced key sai category; release còn yêu cầu status/digest/sign-off tương ứng. Entry authoring chưa được tham chiếu có thể dùng bất kỳ enum category hợp lệ, nhưng release đã cấm dormant entry. Thêm một `MessageKey` field mới mà chưa có row matrix là schema/contract error, không mặc định `EXPLANATION`. Vì category nằm trong entry digest và routine/global/root digest, đổi category luôn invalidates mọi digest/sign-off cũ có cover entry đó.

`key` phải khớp `^[a-z0-9]+(?:[._-][a-z0-9]+)*$`, unique trong toàn catalog và resolve đúng một entry. `text` phải là Unicode NFC hợp lệ, có ít nhất một code point không phải whitespace, không có whitespace đầu/cuối, NUL hoặc C0/C1 control (chỉ LF được phép để xuống dòng). Release từ chối authoring marker `TBD`, `TODO`, `PLACEHOLDER` dưới dạng token độc lập, không phân biệt hoa/thường. Runtime interpolation token `{...}` bị cấm, ngoại lệ duy nhất là đúng một `{emergency_number}` trong entry được `EmergencyDialContract.instructionTemplateKey` tham chiếu; entry khác hoặc token khác đều fail. `reviewedDigestSha256` null khi draft; khi `APPROVED`, nó phải bằng entry digest định nghĩa tại CNT-014. Entry `REJECTED|EXPIRED` không được tham chiếu bởi release artifact.

### CNT-041 — Copy rules

- Chỉ mô tả hành động và cảm nhận chủ quan; dùng ngôn ngữ “gợi ý”, “trong biên độ thoải mái”, “có thể bỏ qua”.
- Không dùng “chữa”, “điều trị”, “phòng ngừa”, “phát hiện”, “sửa tư thế”, “cân bằng hệ thần kinh”, phần trăm recovery hoặc quan hệ nhân quả sức khỏe.
- Không nói người dùng “thất bại”, “lười” hoặc mất streak khi nghỉ/skip.
- Safety copy không được paraphrase bằng AI; app render đúng key đã duyệt.
- Bản dịch safety/instruction mới cần review như nội dung mới, không kế thừa sign-off tiếng Việt.

## 6. Chuyên gia, sign-off và re-review

### CNT-050 — Vai trò và điều kiện tối thiểu

1. **Movement Content Author:** chuyên gia vận động có credential còn hiệu lực và kinh nghiệm hướng dẫn người lớn; có thể soạn sequence/kỹ thuật và candidate global-safety copy nhưng không tự phê duyệt safety cuối cùng.
2. **Movement Technique Reviewer:** exercise professional có credential được tổ chức cấp chứng nhận công nhận, hoặc physiotherapist/physical therapist có giấy phép hiện hành; review sequence, cue, regression, dosage, RPE, support/context và video technique.
3. **Clinical Safety Reviewer:** physiotherapist/physical therapist hoặc physician có giấy phép hành nghề còn hiệu lực trong jurisdiction ghi nhận, có kinh nghiệm liên quan tới vận động người lớn/general wellness; review contraindication/stop/escalation/global safety copy.
4. **Content QA:** kiểm tra manifest, asset, timing, localization và accessibility; không thay movement/clinical reviewer.

Hồ sơ credential chi tiết được giữ trong hệ thống nội bộ hạn chế truy cập; manifest chỉ lưu reference và trạng thái verify, không công khai số giấy phép đầy đủ.

### CNT-051 — Sign-off schema

```ts
type SignOffContract = {
  contentAuthor: {
    reviewerRef: string;
    credentialStatus: "VERIFIED_CURRENT" | "UNVERIFIED" | "EXPIRED";
    authoredAt: InstantWireV1;
  };
  techniqueReviewer: {
    reviewerRef: string;
    profession:
      | "ACCREDITED_EXERCISE_PROFESSIONAL"
      | "PHYSIOTHERAPIST_OR_PHYSICAL_THERAPIST";
    jurisdiction: string;
    credentialStatus: "VERIFIED_CURRENT" | "UNVERIFIED" | "EXPIRED";
    credentialVerifiedAt: InstantWireV1 | null;
    signedAt: InstantWireV1 | null;
    validThrough: InstantWireV1 | null;
    approvedRoutineRevision: SemVer | null;
    approvedContentDigestSha256: Sha256 | null;
  };
  clinicalReviewer: {
    reviewerRef: string;
    profession: "PHYSIOTHERAPIST_OR_PHYSICAL_THERAPIST" | "PHYSICIAN";
    jurisdiction: string;
    credentialStatus: "VERIFIED_CURRENT" | "UNVERIFIED" | "EXPIRED";
    credentialVerifiedAt: InstantWireV1 | null;
    signedAt: InstantWireV1 | null;
    validThrough: InstantWireV1 | null;
    approvedRoutineRevision: SemVer | null;
    approvedContentDigestSha256: Sha256 | null;
  };
  contentQa: {
    reviewerRef: string;
    checkedAt: InstantWireV1 | null;
    approvedManifestDigestSha256: Sha256 | null;
  };
};
```

Mọi `reviewerRef` trong routine/global sign-off phải là opaque ASCII reference khớp `^[A-Za-z0-9][A-Za-z0-9._:-]{2,127}$`; không dùng tên hoặc số giấy phép thô. Mọi `jurisdiction` phải là canonical ISO 3166-1 alpha-2 hoặc ISO 3166-2 code khớp `^[A-Z]{2}(?:-[A-Z0-9]{1,3})?$`. Empty/trimmed khác input, control, Unicode look-alike và unknown credential reference đều fail closed. Trong từng routine, `contentAuthor.reviewerRef` bắt buộc khác `clinicalReviewer.reviewerRef`; tại root, `globalSafetySignOff.contentAuthor.reviewerRef` cũng bắt buộc khác `globalSafetySignOff.clinicalReviewer.reviewerRef`, đều so byte sau validation. Đây là ràng buộc “không tự phê duyệt safety”. MVP cho phép technique và clinical role dùng cùng reference **chỉ khi** credential record độc lập chứng minh người đó thỏa profession/jurisdiction của cả hai role; mỗi role vẫn phải có timestamp và chữ ký digest riêng. Content QA không thay thế bất kỳ chữ ký chuyên môn nào.

Authoring cho phép `Routine.signOff=null` và root `globalSafetySignOff=null` khi reviewer chưa được gán; sau khi non-null, object phải đầy đủ schema dù các status/timestamp nullable có thể còn pending như type cho phép. Release validator dùng cùng exact `InstantWireV1 validationInstant` của CNT-015 và chỉ pass khi `signOff` của cả sáu routine cùng root `globalSafetySignOff` đều non-null; mọi content author, technique reviewer và clinical reviewer credential current. Mỗi routine `contentAuthor.authoredAt` và root `globalSafetySignOff.contentAuthor.authoredAt` là InstantWireV1 non-null; routine author time phải `<=` cả hai routine signature, còn global author time phải `<= globalSafetySignOff.clinicalReviewer.signedAt`; mọi author time phải `<= validationInstant`. Với từng technique/clinical reviewer, `credentialVerifiedAt`, `signedAt`, `validThrough` là InstantWireV1 non-null và thỏa `credentialVerifiedAt <= signedAt <= validationInstant < validThrough`, `validThrough - signedAt <= 365 ngày`. Revision/digest phải khớp chính xác artifact build; Global Safety Sign-off tại CNT-015 phải hợp lệ; equality tại `validThrough` là expired. Reviewer không ký một digest rồi build digest khác.

Sau lexical validation InstantWireV1, release validator parse mọi value thành exact epoch-millisecond absolute Instant rồi tính đúng một manifest-wide cutoff, không so chuỗi và không tính riêng từng routine:

```text
manifestProfessionalSignoffMax = max(
  { routine.signOff.contentAuthor.authoredAt,
    routine.signOff.techniqueReviewer.signedAt,
    routine.signOff.clinicalReviewer.signedAt
    for routine in exact six ContentManifest.routines },
  globalSafetySignOff.contentAuthor.authoredAt,
  globalSafetySignOff.clinicalReviewer.signedAt
)
```

Với **từng một trong sáu** routine, `signOff.contentQa.checkedAt` phải là instant non-null và thỏa `manifestProfessionalSignoffMax <= contentQa.checkedAt <= validationInstant`; tất cả sáu `approvedManifestDigestSha256` phải bằng root manifest digest được CNT-014 tính sau khi mọi reviewer/timestamp đã cố định. Vì digest là whole-manifest, Content QA của routine A vẫn phải sau author/technique/clinical signature muộn nhất của routine B–F và global safety; max chỉ dựa trên exact 20 professional timestamps nêu trên (6×3 + 2), không gồm `credentialVerifiedAt`, `validThrough`, `generatedAt` hay các `contentQa.checkedAt` khác. Bất kỳ timestamp nguồn nào null, parse lỗi hoặc thiếu một trong exact six routine làm release fail trước phép `max`; equality tại cutoff được phép.

### CNT-052 — Chu kỳ và trigger re-review

Trong authoring/release governance, Routine phải được coi `EXPIRED` và review lại trước **lần validation/release mới** khi xảy ra bất kỳ điều kiện nào:

- đến `validThrough`; thời hạn tối đa một năm kể từ `signedAt`;
- đổi step, thứ tự, dosage, duration, RPE, context, easier variation;
- đổi video theo cách ảnh hưởng kỹ thuật, góc quay hoặc instruction;
- đổi safety/contraindication/stop/escalation copy;
- đổi rule engine làm routine được dùng trong mode/context khác;
- có adverse-event report hoặc pattern `new_or_worse_pain=yes` cần điều tra;
- credential reviewer hết hiệu lực/bị thu hồi;
- có yêu cầu pháp lý/store policy mới ảnh hưởng claim hoặc safety.

Mọi thay đổi field/message/asset nằm trong `RoutineClinicalDigestPayloadV1` hoặc `GlobalSafetyDigestPayloadV1` — kể cả typo — đều làm digest đổi và bắt buộc đúng reviewer clinical/technique tương ứng re-sign. MVP không có amendment/digest-lineage ngoại lệ. Thay đổi chỉ nằm ngoài hai clinical payload nhưng vẫn được root manifest cover (ví dụ build metadata) ít nhất phải tính lại manifest digest và Content QA ký lại.

`globalSafetyContent` cũng bị coi `EXPIRED` cho validation/release mới và bắt buộc clinical re-sign khi đổi bất kỳ route binding/key/text nào, emergency dial target/action, eligibility/red-flag/pain question hoặc option, outcome/hold-kind mapping, pre-flight/stop/escalation/recheck copy, rule behavior ảnh hưởng route, reviewer credential/policy, hoặc khi validation instant tới `validThrough`. Thời hạn tối đa vẫn là một năm từ `signedAt`; routine sign-off không thay thế global sign-off và ngược lại.

Expiry là release-governance boundary, không phải runtime device-clock gate. Một APK đã ký không mutate bundled status hoặc brick/offline-disable content chỉ vì wall clock thiết bị tới/lùi qua `validThrough`; device clock không có authority để gia hạn hoặc hết hạn chữ ký. Mọi build/RC/update mới vẫn bắt buộc chạy release validator với fresh fixed instant và fail tại equality. Recall/adverse-event/policy trigger ngoài build là quy trình phát hành/thu hồi riêng, không được giả bằng wall clock local thiếu tin cậy.

## 7. Authoring và release validation

### CNT-060 — Hai profile validator

`validate-content --profile authoring`:

- kiểm schema, uniqueness/reference, duration/RPE/context, canonical asset path/hash nếu có;
- cho phép clinical fields pending;
- xuất danh sách blocker có ID.

`validate-content --profile release`:

- bao gồm toàn bộ authoring checks;
- yêu cầu chính xác sáu routine CNT-001, không thừa/thiếu;
- yêu cầu mọi safety content và message `APPROVED`;
- yêu cầu global safety route/key coverage, entry/global digest và external sign-off hợp lệ theo CNT-015;
- yêu cầu asset/accessibility/sign-off đầy đủ, hash/digest khớp;
- từ chối asset mồ côi và message-catalog entry không được bất kỳ routine/global-safety contract nào tham chiếu; dormant content không được đi vào binary release;
- yêu cầu `compatibleRuleVersions` chứa đúng `[1]` trong MVP;
- fail non-zero nếu có warning chưa disposition.

### CNT-060A — Bằng chứng validation đóng gói

Release validator thành công phải emit một generated app resource `ContentReleaseValidationEvidenceV1` có đúng bốn key, không missing/extra/alias/default:

```ts
type ContentReleaseValidationEvidenceV1 = {
  schema_version: 1;
  validation_instant: InstantWireV1;
  manifest_digest_sha256: Sha256;
  validator_version: SemVer;
};
```

`validation_instant` byte-equal exact CLI input đã dùng cho **toàn bộ** chronology/`validThrough`/Content-QA comparisons; `manifest_digest_sha256` byte-equal root manifest đã pass; validator version là canonical SemVer của executable/schema rules. Pipeline JCS-encode object, tính SHA-256 và generate `BuildConfig.CONTENT_RELEASE_EVIDENCE_SHA256` bằng 64 lowercase hex. Generated evidence + constant nằm trong cùng signed APK; evidence không thuộc manifest clinical/root digest để tránh vòng băm và không thay thế reviewer sign-off.

Runtime loader duplicate/unknown-safe parse evidence, verify JCS digest byte-equal generated constant và manifest digest byte-equal loaded manifest, rồi dùng **duy nhất** `validation_instant` đã bind đó để replay deterministic sign-off chronology. Thiếu/mismatch/noncanonical evidence fail trước Home. Runtime tuyệt đối không dùng `Instant.now()`, device wall/zone hoặc install time cho `validThrough`; app đã cài vẫn chạy offline sau boundary, còn build/update mới phải có evidence mới từ fresh release validation. Evidence không phải user data, không persist/export.

Mã lỗi tối thiểu:

```text
CNT_SCHEMA_INVALID
CNT_CATALOG_MISMATCH
CNT_DURATION_OR_RPE_MISMATCH
CNT_CONTEXT_MISMATCH
CNT_MESSAGE_MISSING
CNT_REFERENCE_AMBIGUOUS
CNT_ASSET_PATH_INVALID
CNT_ASSET_MISSING_OR_HASH_MISMATCH
CNT_ASSET_FORMAT_INVALID
CNT_MESSAGE_CATEGORY_MISMATCH
CNT_ACCESSIBILITY_INCOMPLETE
CNT_CLINICAL_CONTENT_PENDING
CNT_GLOBAL_SAFETY_SIGNOFF_INVALID
CNT_SIGNOFF_INVALID_OR_EXPIRED
CNT_RULE_VERSION_INCOMPATIBLE
CNT_VERSION_LINEAGE_INVALID
CNT_PLACEHOLDER_PRESENT
```

### CNT-061 — Version lineage không được tái sử dụng

Release validator nhận một append-only `previousApprovedCatalogIndex` đã được release owner xác thực; chỉ release đầu tiên được dùng index rỗng. Index giữ ít nhất mapping `manifestVersion → manifestDigestSha256` và `(routineId, revision) → clinicalContentDigestSha256`, cùng latest approved SemVer của manifest và từng routine ID. Đây là CI/release input, không bundle credential registry đầy đủ vào app và runtime không gọi mạng.

- Cùng `manifestVersion` chỉ được resolve đúng một root manifest digest. Nếu current `manifestDigestSha256` khác latest approved digest, `manifestVersion` phải có SemVer precedence **lớn hơn** latest approved version; build-metadata-only change không tính là lớn hơn. Điều này áp cho mọi field được root digest cover, kể cả `generatedAt`, sign-off metadata, global safety, message, asset và routine.
- Cùng `(routineId, revision)` chỉ được resolve đúng một clinical content digest. Khi routine clinical digest khác latest approved digest của ID đó, `revision` phải có SemVer precedence lớn hơn latest approved revision; đổi chỉ build metadata không đủ. Global-safety-only change không buộc routine revision nhưng vẫn buộc manifest version theo rule trên.
- Artifact byte-identical được rebuild thì giữ version/digest cũ. Version tăng dù digest nội dung tương ứng không đổi được phép nhưng index vẫn append exact mapping; version giảm, collision version→digest, thiếu previous index ở non-first release hoặc routine ID bị tái dùng đều trả `CNT_VERSION_LINEAGE_INVALID`.
- `schemaVersion` vẫn đúng `1.0.0` trong MVP. Thay schema cần baseline/reader migration mới; không được dùng routine/manifest bump để che incompatible schema.

Sau approval, pipeline append mapping current vào index trong release evidence. Validator chạy hoàn toàn offline trên current artifact + exact prior index; không suy “previous” từ file order, Git tag hoặc timestamp.

## 8. Trạng thái hiện tại và blocker

Metadata của sáu routine ở CNT-001 đã được khóa để engineering dựng manifest/selector. Sequence động tác, easier variation, setup safety, per-routine contraindication, stop rule, escalation copy, global eligibility/red-flag/outcome/pain-gate copy và video kỹ thuật **chưa được tài liệu này tự tạo**.

Mỗi routine bắt đầu ở:

```json
{
  "safetyContent": {
    "status": "PENDING_EXTERNAL_SIGN_OFF",
    "comfortableRangeInstructionKey": null,
    "setupSafetyKeys": [],
    "contraindicationDisposition": null,
    "contraindicationKeys": [],
    "stopRuleKeys": [],
    "escalationMessageKey": null,
    "clinicalContentDigestSha256": null
  }
}
```

`globalSafetyContent.status` cũng bắt đầu ở `PENDING_EXTERNAL_SIGN_OFF`, các array có thể rỗng và digest/sign-off null trong fixture authoring. Đây là trạng thái authoring hợp lệ nhưng **không release được**. Gate `REL-GATE-CLINICAL` chỉ mở khi cả sáu routine và global safety content đều có external clinical sign-off hợp lệ.
