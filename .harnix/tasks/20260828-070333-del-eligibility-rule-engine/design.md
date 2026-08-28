# Design — DEL-02 Eligibility, check-in và rule engine

## 1. Boundary

`:domain` chứa toàn bộ canonical model, pure rule/freshness/constraint logic và không đọc Android/Room/clock trực tiếp. `:data` ánh xạ domain vào encrypted Room rows, transaction và event HMAC. `:app` sở hữu application use-case wiring, lifecycle/navigation và debug/release content selection. `:ui` chỉ render immutable state cùng callbacks.

## 2. Rule pipeline

Raw UI/parser value → `DraftField` → `RuleInputDraftV1` → `RuleEngineV1.evaluate` → `RuleDecisionDraft`. Engine chỉ first-match và không clock/DB/event/routine. Orchestrator đã authenticate constraint bundle trước khi tạo draft; crypto/bundle/source failure dừng `CONTRACT_ERROR` ngoài engine. Authenticated inner cap invalid mới được truyền `DraftField.Invalid`.

## 3. Onboarding transaction

Age/eligibility/scope selection và staged funnel event drafts chỉ sống trong RAM. `CompleteOnboardingCommand` mang validated schedule, typed content identity, staged LocalStamp/timing và không mang raw diagnosis/reason. Repository sinh installation/profile/schedule IDs trong transaction, encrypt payload, ghi pointers, exact events + HMAC + refs/companion, rồi mới trả `Committed`. Mọi failure rollback và presentation ở lại schedule; permission primer không quan sát partial state.

## 4. Check-in transaction

`EvaluateCheckInCommand` giữ flow ID, canonical answers, optional parent và expected active schedule. Repository lock active pointer/version, lấy một coherent ClockSnapshot, validate window, resolve authenticated constraint bundle, gọi pure engine rồi atomically ghi CheckIn, Decision, freshness evidence, event bundle và hold/suppression + immutable audit snapshot. Active hold rerender không tạo Decision mới. Draft incomplete không persist; persisted cap-only INCOMPLETE chỉ khi authenticated inner cap invalid.

## 5. Time và constraints

TTL authority là elapsed monotonic deadline; work-window/date/schedule guards chạy trước freshness. Constraint `expires_at_utc` được tính một lần ở start-of-next-local-date trong origin ZoneId; same boot dùng monotonic equality, discontinuity reconcile bằng max(checkpoint remaining, clamped wall remaining) và không clear sớm. Operational bundle có thể đổi, còn Decision audit snapshots bất biến.

## 6. Storage migration

Schema 1 là immutable baseline `clock_state`. Schema 2 thêm DEL-02 tables bằng SQL migration explicit, với only allowlisted plaintext IDs/FKs/local/delete/rule/crypto metadata. Mọi user/event payload là independent AES-GCM envelope dùng canonical AAD. Product event physical key là 32-byte HMAC-SHA-256 alias `n2p_event_idem_hmac_v1`; encrypted payload mirror version 1 và read recompute trước trust.

## 7. Content seam

Clinical/safety copy chưa signed. Domain/presenter dùng typed slot model, không arbitrary string-key lookup. Debug build inject fixture có marker `NON_PRODUCTION_NOT_CLINICALLY_APPROVED` để test flow. Release build không tự coi fixture là approved; thiếu valid signed artifact trả typed unavailable/data-error route và không mở check-in/routine.

## 8. Deferred seams

REST suppression chỉ persist/audit; alarm cancellation/reschedule là DEL-05. Post-session pain factory có exact SESSION source contract và tests; Session/player/feedback transaction sẽ bind nó ở DEL-03/04. Không tạo routine selector/player placeholder hoặc claim DEL-M2.