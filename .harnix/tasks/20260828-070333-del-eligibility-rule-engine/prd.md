# PRD — DEL-02 Eligibility, check-in và rule engine

## Mục tiêu

Hoàn thiện DEL-02 thành một vertical slice offline có thể kiểm chứng độc lập: onboarding eligible chỉ tạo dữ liệu tại initial schedule commit, daily check-in tạo Decision bằng pure rule engine v1 và mọi safety/freshness constraint đều deterministic, reasoned và fail closed.

## Giá trị người dùng

Người dùng đủ điều kiện có thể hoàn tất onboarding, lưu lịch và check-in an toàn mà không cần mạng; red flag, acute issue, nghỉ chủ động, dữ liệu thiếu và clock/time change không bao giờ mở routine sai hoặc để trạng thái nửa vời.

## Phạm vi

- Age/eligibility/scope staged trong RAM; safe-exit không persist.
- Schedule canonical 1–7 weekday, work window cùng ngày và 1–2 reminder ASCII `HH:mm`.
- Initial `Lưu lịch` zero-or-all với Profile, acknowledgement/current pointer, ScheduleVersion/active pointer và exact typed event bundle.
- Pure `RuleEngineV1`, rule version 1, generated matrix/property suite.
- Canonical CheckIn union, Decision, SafetyHold, RestDaySuppression, freshness/clock evidence và atomic repository transactions.
- Room schema 2 + migration 1→2, encrypted payload/AAD, product-event HMAC/idempotency và fail-closed reads.
- Debug UI/navigation cho onboarding, check-in, safety/rest và permission primer gating; release content seam fail closed.
- Static/unit/instrumented/API-36 regression gates và CHANGELOG.

## Ngoài phạm vi

- Recommendation/routine selection, pre-flight, player, session recovery và full terminal feedback UI.
- Android notification prompt, alarm scheduling, reminder delivery/snooze.
- Weekly summary, export/delete pipeline, retention maintenance hoàn chỉnh, approved content production và pilot/release approval.
- Account, cloud/network, AI, wearable/Health Connect, location/calendar/driving detection, billing.
- Commit/push/PR hoặc cài toolchain system-wide tự động.

## Quyết định đã khóa

- `phase 2` trong request là `DEL-02 / tuần 2`, không phải roadmap sau feasibility và chưa phải toàn bộ `DEL-M2`.
- Database hiện đã có schema 1 chỉ với `clock_state`; DEL-02 dùng schema 2 cùng explicit migration `1→2`, không rewrite schema 1 và không destructive migration.
- Permission seam chỉ render primer sau successful onboarding commit; OS prompt/scheduler thuộc DEL-05.
- Post-session pain chỉ khóa model/factory/repository seam và property test; Session/player UX thuộc DEL-03/04.
- REST suppression được persist/audit; platform alarm cancellation/reschedule thuộc DEL-05.
- Safety copy chưa external sign-off: debug fixture phải typed và mang marker `NON_PRODUCTION_NOT_CLINICALLY_APPROVED`; release adapter không có signed artifact phải fail closed.
- Module ownership giữ `:domain` thuần Kotlin/JDK, `:data` sở hữu Room/crypto, `:app` sở hữu orchestration/navigation và `:ui` chỉ render state/callback.
- SDK giữ `minSdk=26`, `targetSdk=36`, `compileSdk=36`; app không có `INTERNET`.

## Acceptance criteria

### AC `AC-DEL02-001`

Rule engine v1 là hàm thuần, deterministic, first-match đúng SAF-020 trên `RuleInputDraftV1`; output đóng giữ reason/invalid-field/route/allowed-mode canonical, không có `routine_id` và toàn bộ no-mode outcome không chứa mode/routine.

### AC `AC-DEL02-002`

Onboarding giữ age/eligibility/scope/safety-content identity trong RAM tới initial `Lưu lịch`; input ineligible không tạo dữ liệu, còn eligible schedule hợp lệ commit zero-or-all Profile, first acknowledgement/current pointer, ScheduleVersion/active pointer và exact staged/scope/schedule/completion events trước khi permission primer được phép render.

### AC `AC-DEL02-003`

Room nâng schema 1 lên 2 bằng migration không destructive và persist payload DEL-02 bằng AES-GCM/AAD canonical; product event dùng exact HMAC-SHA-256 dataset key, unique physical key và closed typed event codec, mọi crypto/schema/source/mirror mismatch fail closed.

### AC `AC-DEL02-004`

`EvaluateCheckIn` commit atomically canonical CheckIn union, Decision, freshness evidence, check-in/decision events và đúng side effect: red flag hoặc từng acute issue tạo reasoned SafetyHold, `REST_ONLY` tạo RestDaySuppression, post-session pain factory tạo SESSION-source hold; draft incomplete và transaction lỗi không để half-state hoặc routine.

### AC `AC-DEL02-005`

Lifecycle resolver áp precedence schedule-version, active window, local date và clock evidence; TTL sáu giờ dùng half-open elapsed interval, equality stale, timezone/time/generation/mapping change reconfirm đúng reason, còn hold/suppression giữ origin ZoneId/expiry bất biến và không bị clear sớm qua clock change hoặc reboot.

### AC `AC-DEL02-006`

App debug có state-hoisted onboarding/check-in/safety/rest UI và app-owned navigation không bypass gate; safety copy đi qua typed non-production fixture có marker rõ, release path fail closed khi chưa có signed content, locale `vi-VN`/default giữ parity và semantics accessibility cơ bản.

### AC `AC-DEL02-007`

DEL-02 giữ offline posture, SDK `26/36/36`, module boundaries và DEL-01 regression gates; generated 1.296-case matrix, single-invalid/lazy-short-circuit tests, ít nhất 10.000 deterministic property cases, migration/atomicity/device tests và CHANGELOG DEL-02 đều xanh.

## Rủi ro và rollback

- Migration/schema sai có thể làm DB không mở được: instrumentation tạo schema 1, migrate 1→2, reopen và so schema; không có destructive fallback.
- Safety content chưa sign-off: release path fail closed và không được biến debug fixture thành approved artifact.
- Atomic transaction/event mirror/HMAC phức tạp: repository dùng một Room transaction, injected failure tests tại từng write boundary và không báo success nếu thiếu companion.
- Clock discontinuity không có trusted time: resolver giữ conservative state, chấp nhận kéo dài nhưng cấm clear sớm.
- Mỗi slice giữ tests focused xanh trước refactor; rollback bằng revert slice chưa persist evidence, không rewrite schema/task history.