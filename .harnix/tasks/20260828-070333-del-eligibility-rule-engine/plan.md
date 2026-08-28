# Plan — DEL-02 Eligibility, check-in và rule engine

## Ready checkpoint

Outcome là hoàn thiện DEL-02 độc lập trên foundation hiện có. Contract đã khóa bởi FR/SAF/ARC/QA: engine first-match thuần, onboarding/check-in zero-or-all, DB schema 2 migration 1→2, TTL elapsed sáu giờ và origin-zone constraints. Permission system prompt, reminder scheduler, routine player và full feedback E2E thuộc phase sau. Safety content chỉ là typed debug fixture; release thiếu signed artifact phải fail closed. Không còn quyết định vật chất mở trước implementation.

## Checklist

- [x] `S1` — Implement pure rule engine v1 và generated safety matrix
- [x] `S2` — Implement schedule, freshness và safety-constraint domain contracts
- [ ] `S3` — Implement staged onboarding và app/domain state machine
- [ ] `S4` — Implement Room schema 2, migration, crypto/HMAC và atomic repositories
- [ ] `S5` — Implement check-in orchestration, UI routes và typed content seam
- [ ] `S6` — Khóa static/build/resource/changelog gates
- [ ] `S7` — Chạy full JVM/API-36 verification, review và persist completion evidence

### Slice `S1`

Criteria: `AC-DEL02-001`, `AC-DEL02-004`, `AC-DEL02-007`

Checks: `CHK-DEL02-DOMAIN`

Paths: `domain/src/main/kotlin/vn/nhip2phut/domain/rule/**`, `domain/src/main/kotlin/vn/nhip2phut/domain/model/**`, `domain/src/test/kotlin/vn/nhip2phut/domain/rule/**`

1. Viết RED fixtures cho từng row, lazy safety short-circuit, missing/invalid field và canonical output invariants.
2. Implement exact `DraftField`, rule enums/result, total allowed-mode/presentation-route mapping và `RuleEngineV1.evaluate`.
3. Generate toàn bộ 1.296 valid Cartesian cases, single-invalid/later-field-corrupt cases và deterministic 10.000-case property run với seed in khi fail.
4. Kiểm no-mode result luôn null/empty, cap không đổi outcome/không nâng mode, reason/invalid fields unique canonical và result không chứa routine ID.
5. Refactor chỉ khi focused suite vẫn xanh.

### Slice `S2`

Criteria: `AC-DEL02-004`, `AC-DEL02-005`

Checks: `CHK-DEL02-DOMAIN`

Paths: `domain/src/main/kotlin/vn/nhip2phut/domain/onboarding/**`, `domain/src/main/kotlin/vn/nhip2phut/domain/checkin/**`, `domain/src/main/kotlin/vn/nhip2phut/domain/safety/**`, `domain/src/main/kotlin/vn/nhip2phut/domain/time/**`, `domain/src/test/kotlin/vn/nhip2phut/domain/checkin/**`, `domain/src/test/kotlin/vn/nhip2phut/domain/safety/**`

1. Viết RED tests cho strict `HH:mm`, schedule ranges/order, canonical CheckIn union và schedule/source identity.
2. Implement freshness evidence/resolver với exact precedence, overflow-safe mapping drift, TTL equality và typed reconfirm reasons.
3. Implement origin-zone next-day expiry, five-field clock evidence, same-boot monotonic authority và conservative reboot reconcile.
4. Implement reasoned hold/suppression factories, gồm exact red/acute CHECK_IN source và post-session pain SESSION source.
5. Test DST/timezone/wall jump/reboot/equality fixtures bằng injected `FakeClock`, không sleep.

### Slice `S3`

Criteria: `AC-DEL02-002`, `AC-DEL02-006`

Checks: `CHK-DEL02-APP`

Paths: `domain/src/main/kotlin/vn/nhip2phut/domain/onboarding/**`, `app/src/main/kotlin/vn/nhip2phut/app/onboarding/**`, `app/src/test/kotlin/vn/nhip2phut/app/onboarding/**`, `ui/src/main/kotlin/vn/nhip2phut/ui/onboarding/**`, `ui/src/test/kotlin/vn/nhip2phut/ui/onboarding/**`

1. Viết RED state-machine tests chứng minh age false/no-unsure/scope incomplete không rời RAM và deep link không bỏ qua gate.
2. Implement staged eligible state, typed content identity, schedule form validation và one-shot completion command.
3. Khóa result/navigation: commit fail ở lại schedule, success duy nhất mới mở permission primer; process loss trước commit quay lại onboarding.
4. Tách state/callback khỏi Compose và giữ navigation ownership trong `:app`.
5. Không launch OS permission hoặc tạo reminder trong slice này.

### Slice `S4`

Criteria: `AC-DEL02-002`, `AC-DEL02-003`, `AC-DEL02-004`, `AC-DEL02-005`

Checks: `CHK-DEL02-DATA`, `CHK-DEL02-DEVICE`

Paths: `data/src/main/kotlin/vn/nhip2phut/data/storage/**`, `data/src/main/kotlin/vn/nhip2phut/data/onboarding/**`, `data/src/main/kotlin/vn/nhip2phut/data/checkin/**`, `data/src/main/kotlin/vn/nhip2phut/data/events/**`, `data/src/main/kotlin/vn/nhip2phut/data/crypto/**`, `data/src/test/**`, `data/src/androidTest/**`, `data/schemas/**`

1. Viết RED migration/device tests từ checked-in schema 1 và injected transaction-failure tests cho từng entity/event/ref/HMAC boundary.
2. Thêm schema 2 tables/pointers cho profile, schedule, check-in, decision, constraints, flow timing, product events và typed refs với opaque FK/plaintext allowlist đúng contract.
3. Implement explicit migration 1→2, export schema 2 và giữ `clock_state` byte-compatible; cấm destructive fallback.
4. Implement generic encrypted row codecs bằng AAD table/column/typed PK, event HMAC Keystore alias exact, recompute/unique validation và fail-closed key lifecycle.
5. Implement `CompleteOnboarding` và `EvaluateCheckIn` repositories bằng một Room transaction, exact event mirrors/companion refs và zero-or-all rollback.
6. Deep-copy side-effect audit snapshot vào Decision trước commit; không reconstruct từ operational constraint.
7. Chạy JVM rồi API-36 migration/Keystore/Room tests trước khi đánh dấu slice.

### Slice `S5`

Criteria: `AC-DEL02-002`, `AC-DEL02-004`, `AC-DEL02-005`, `AC-DEL02-006`

Checks: `CHK-DEL02-APP`

Paths: `app/src/main/kotlin/vn/nhip2phut/app/AppContainer.kt`, `app/src/main/kotlin/vn/nhip2phut/app/navigation/**`, `app/src/main/kotlin/vn/nhip2phut/app/checkin/**`, `app/src/main/res/**`, `app/src/test/**`, `app/src/androidTest/**`, `ui/src/main/kotlin/vn/nhip2phut/ui/checkin/**`, `ui/src/main/res/**`, `ui/src/test/**`

1. Viết RED presenter/navigation tests cho route precedence: onboarding, hold, content fail-closed, work window, check-in steps và safety/rest outcome.
2. Wire repositories/use cases/clock trong application-scoped `AppContainer`; UI không import data/platform/navigation runtime.
3. Implement accessible state-hoisted screens cho welcome, age, scope, schedule, primer, Home, red gate, acute/full check-in và safety/rest/data-error routes.
4. Safety route nhận typed content contract; debug fixture mang marker non-production, release path thiếu signed artifact render fail-closed.
5. Giữ exact default/`values-b+vi+VN` resource key parity, no preselection cho new check-in và reconfirm bắt explicit submit.
6. Thêm Compose/device tests chứng minh Back/deep-link/process-recreate không bypass và blocked output không có routine CTA.

### Slice `S6`

Criteria: `AC-DEL02-006`, `AC-DEL02-007`

Checks: `CHK-DEL02-STATIC`

Paths: `scripts/verify-del02.ps1`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `data/build.gradle.kts`, `domain/build.gradle.kts`, `ui/build.gradle.kts`, `CHANGELOG.md`

1. Viết RED static gate cho required files/contracts/tests, schema 2 + migration, 1.296/10.000 markers, typed content marker và DEL-02 top changelog entry.
2. Đăng ký `verifyDel02`/`verifyDel02Device`, giữ nguyên `verifyFoundation`/`verifyFoundationDevice` và module/manifest gates.
3. Cập nhật CHANGELOG bằng hành vi thực tế; không claim external clinical sign-off hoặc DEL-M2.
4. Chạy static gate, foundation regression, lint và assemble.

### Slice `S7`

Criteria: `AC-DEL02-001`, `AC-DEL02-002`, `AC-DEL02-003`, `AC-DEL02-004`, `AC-DEL02-005`, `AC-DEL02-006`, `AC-DEL02-007`

Checks: `CHK-DEL02-FULL`, `CHK-DEL02-DEVICE`

Paths: `app/**`, `data/**`, `domain/**`, `platform/**`, `ui/**`, `scripts/**`, `build.gradle.kts`, `CHANGELOG.md`

1. Chạy compliance pass theo từng AC và đối chiếu exact spec/event/schema claims.
2. Chạy fresh domain/data/app/static/full checks với Harnix snapshot trước/sau từng required check.
3. Chạy API-36 device gate trên exact emulator, đọc toàn output và retry chỉ khi xác định được infrastructure flake.
4. Thực hiện fresh read-only review cho correctness, atomicity, crypto, migration, privacy, accessibility và unnecessary complexity.
5. Sửa finding bằng RED-GREEN, rerun affected check và sau đó rerun aggregate gates.
6. Cập nhật checklist/evidence, chuyển verifying/finishing và chỉ finish khi mọi AC met cùng inputDigest fresh.
7. Dọn đúng toolchain/AVD tạm nếu phải tạo; không commit/push tự động.