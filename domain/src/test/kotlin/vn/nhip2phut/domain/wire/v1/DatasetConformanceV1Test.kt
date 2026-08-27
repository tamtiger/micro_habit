package vn.nhip2phut.domain.wire.v1

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class DatasetConformanceV1Test {
    @Test
    fun sessionRoutineIdMustMatchItsCanonicalSignedMode() {
        val failure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                datasetJson(
                    sessionStatus = "STOPPED",
                    includeFeedback = true,
                    sessionRoutineId = "BUI-01",
                    sessionRoutineMode = "RECOVER",
                ),
            )
        }

        assertContains(failure.message.orEmpty(), "routine_id does not belong to routine_mode")
    }

    @Test
    fun decisionMustMirrorAllSixFreshnessFieldsFromItsCheckIn() {
        val validDataset = ClosedCodecV1.decodeExport(datasetJson())
        val sourceCheckIn = validDataset.checkIns.single()

        listOf(
            "confirmed_boot_marker" to JsonPrimitive(9),
            "confirmed_elapsed_realtime_ms" to JsonPrimitive(1),
            "ttl_monotonic_deadline_ms" to JsonPrimitive(4),
            "confirmed_clock_generation" to JsonPrimitive(9),
            "confirmed_zone_id" to JsonPrimitive("Asia/Bangkok"),
            "confirmed_wall_minus_elapsed_ms" to JsonPrimitive(9),
        ).forEach { (key, mutantValue) ->
            val mutantBody = StrictJsonObjectV1(JsonObject(sourceCheckIn.body.element + (key to mutantValue)))
            val mutantDataset = validDataset.copy(checkIns = listOf(CheckInWireV1(mutantBody)))

            assertFailsWith<WireContractException>(key) {
                DatasetConformanceV1.requireValid(mutantDataset)
            }
        }
    }

    @Test
    fun activeSessionHasNoFeedback() {
        ClosedCodecV1.decodeExport(datasetJson(sessionStatus = "ACTIVE"))

        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(datasetJson(sessionStatus = "ACTIVE", includeFeedback = true))
        }
    }

    @Test
    fun everyTerminalSessionHasExactlyOneFeedback() {
        listOf("COMPLETED", "STOPPED", "ABANDONED").forEach { status ->
            ClosedCodecV1.decodeExport(datasetJson(sessionStatus = status, includeFeedback = true))

            assertFailsWith<WireContractException>(status) {
                ClosedCodecV1.decodeExport(datasetJson(sessionStatus = status, includeFeedback = false))
            }
        }
    }

    @Test
    fun sessionDecisionModeMustMirrorItsSourceDecision() {
        val failure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                datasetJson(
                    sessionStatus = "ACTIVE",
                    sessionRoutineId = "MAI-01",
                    sessionRoutineMode = "MAINTAIN",
                    sessionDecisionMode = "MAINTAIN",
                    sessionRuntimeMode = "MAINTAIN",
                ),
            )
        }
        assertContains(failure.message.orEmpty(), "decision_effective_mode_at_start must mirror source Decision.effective_mode")
    }

    @Test
    fun routineStartedFlowMustMirrorItsRetainedSourceCheckInCommit() {
        val valid = ClosedCodecV1.decodeExport(datasetJson(sessionStatus = "ACTIVE"))
        val mismatchedStart = ClosedCodecV1.decodeEvent(
            eventJson(
                id = EVENT_SESSION_START_ID,
                name = "routine_started",
                occurredAtUtc = STARTED_AT_UTC,
                decisionId = DECISION_ID,
                sessionId = SESSION_ID,
                scheduleVersionId = SCHEDULE_ID,
                source = "home",
                properties = """
                    {
                      "routine_id":"BUI-01",
                      "check_in_flow_id":"$SECOND_CHECK_IN_FLOW_ID",
                      "runtime_effective_mode_at_start":"BUILD",
                      "is_selected_workday_at_start":true,
                      "start_boot_marker":1,
                      "start_elapsed_realtime_ms":20,
                      "start_clock_generation":4,
                      "start_wall_minus_elapsed_ms":5,
                      "total_duration_ms":1
                    }
                """.trimIndent(),
            ),
        )
        val mutant = valid.copy(
            events = sortEvents(
                valid.events.map { event ->
                    if (event.envelope.eventId.value == EVENT_SESSION_START_ID) mismatchedStart else event
                },
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "check_in_flow_id")
    }

    @Test
    fun immediateSafetyScreenRejectsAModeBearingSourceDecision() {
        val mutant = withProjectionEvents(
            eventJson(
                id = EVENT_MODE_DECISION_SAFETY_SCREEN_ID,
                name = "safety_screen_shown",
                occurredAtUtc = STARTED_AT_UTC,
                decisionId = DECISION_ID,
                properties = safetyScreenProperties(
                    result = "PAUSE_TODAY",
                    routeId = "pause_acute_illness",
                ),
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "Decision safety-hold snapshot")
    }

    @Test
    fun immediatePauseSafetyScreenMustMirrorTheExactDecisionReasonRoute() {
        DatasetConformanceV1.requireValid(immediatePauseDataset("pause_acute_illness"))

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(immediatePauseDataset("pause_medically_restricted"))
        }
        assertContains(failure.message.orEmpty(), "route_id")
    }

    @Test
    fun safetyScreenMustUseADigestFromTheRetainedAcknowledgementHistory() {
        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(
                immediatePauseDataset(
                    routeId = "pause_acute_illness",
                    contentDigest = "1".repeat(64),
                ),
            )
        }
        assertContains(failure.message.orEmpty(), "content_digest was never acknowledged")
    }

    @Test
    fun postSessionSafetyScreenMustMirrorTheFeedbackHoldRoute() {
        DatasetConformanceV1.requireValid(postSessionSafetyDataset("blocked_post_session_new_or_worse_pain"))

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(postSessionSafetyDataset("blocked_red_flag"))
        }
        assertContains(failure.message.orEmpty(), "forbids Decision/Session envelope")
    }

    @Test
    fun projectionCannotHideStrictReductionBehindAStaleDecisionMode() {
        val failure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                datasetJson(
                    recommendation = RecommendationFixture(
                        decisionEffectiveMode = "MAINTAIN",
                        runtimeEffectiveMode = "MAINTAIN",
                        capApplied = true,
                        runtimeCapSnapshot = "null",
                    ),
                ),
            )
        }
        assertContains(failure.message.orEmpty(), "projection decision mode must mirror source Decision.effective_mode")
    }

    @Test
    fun projectionWithoutRuntimeCapHasZeroConditionalSessionReferences() {
        ClosedCodecV1.decodeExport(
            datasetJson(
                recommendation = RecommendationFixture(
                    decisionEffectiveMode = "BUILD",
                    runtimeEffectiveMode = "BUILD",
                    capApplied = false,
                    runtimeCapSnapshot = "null",
                ),
            ),
        )
    }

    @Test
    fun recommendedSelectionMustMatchLatestStrictlyEarlierRetainedRecommendation() {
        val mutant = withProjectionEvents(
            recommendationEvent(
                projection = RecommendationFixture(
                    decisionEffectiveMode = "BUILD",
                    runtimeEffectiveMode = "BUILD",
                    capApplied = false,
                    runtimeCapSnapshot = "null",
                ),
                occurredAtUtc = PROJECTION_AT_UTC,
            ),
            routineSelectedEvent(
                routineId = "BUI-02",
                selection = "recommended",
                occurredAtUtc = LATE_CAP_AT_UTC,
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "latest retained recommendation")
    }

    @Test
    fun recommendedAndSameModeSelectionsAcceptTheExactRetainedRecommendationRelationship() {
        val recommendation = recommendationEvent(
            projection = buildProjection,
            occurredAtUtc = PROJECTION_AT_UTC,
        )

        DatasetConformanceV1.requireValid(
            withProjectionEvents(
                recommendation,
                routineSelectedEvent(
                    routineId = "BUI-01",
                    selection = "recommended",
                    occurredAtUtc = LATE_CAP_AT_UTC,
                ),
            ),
        )
        DatasetConformanceV1.requireValid(
            withProjectionEvents(
                recommendation,
                routineSelectedEvent(
                    routineId = "BUI-02",
                    selection = "same_mode",
                    occurredAtUtc = LATE_CAP_AT_UTC,
                ),
            ),
        )
    }

    @Test
    fun sameModeSelectionCannotReuseTheRetainedRecommendedRoutineId() {
        val mutant = withProjectionEvents(
            recommendationEvent(buildProjection, occurredAtUtc = PROJECTION_AT_UTC),
            routineSelectedEvent(
                routineId = "BUI-01",
                selection = "same_mode",
                occurredAtUtc = LATE_CAP_AT_UTC,
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "latest retained recommendation")
    }

    @Test
    fun selectionWithoutARetainedRecommendationDoesNotFalseReject() {
        DatasetConformanceV1.requireValid(
            withProjectionEvents(
                routineSelectedEvent(
                    routineId = "BUI-02",
                    selection = "recommended",
                    occurredAtUtc = LATE_CAP_AT_UTC,
                ),
            ),
        )
    }

    @Test
    fun sameInstantRecommendationDoesNotUseRandomEventIdAsCausalOrder() {
        listOf(EVENT_SELECTION_BEFORE_RECOMMENDATION_ID, EVENT_SELECTION_AFTER_RECOMMENDATION_ID).forEach { selectionId ->
            DatasetConformanceV1.requireValid(
                withProjectionEvents(
                    recommendationEvent(
                        projection = buildProjection,
                        id = EVENT_SAME_INSTANT_RECOMMENDATION_ID,
                        occurredAtUtc = PROJECTION_AT_UTC,
                    ),
                    routineSelectedEvent(
                        routineId = "BUI-02",
                        selection = "recommended",
                        id = selectionId,
                        occurredAtUtc = PROJECTION_AT_UTC,
                    ),
                ),
            )
        }
    }

    @Test
    fun sameInstantSelectionIsRejectedWhenNoPossibleLatestRecommendationExplainsIt() {
        val mutant = withProjectionEvents(
            recommendationEvent(
                projection = buildProjection,
                routineId = "BUI-01",
                occurredAtUtc = INHERITED_COMMIT_AT_UTC,
            ),
            recommendationEvent(
                projection = buildProjection,
                routineId = "BUI-01",
                id = EVENT_SAME_INSTANT_RECOMMENDATION_ID,
                occurredAtUtc = PROJECTION_AT_UTC,
            ),
            routineSelectedEvent(
                routineId = "BUI-02",
                selection = "recommended",
                id = EVENT_SELECTION_AFTER_RECOMMENDATION_ID,
                occurredAtUtc = PROJECTION_AT_UTC,
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "latest retained recommendation")
    }

    @Test
    fun sameInstantCandidatesRemainAcceptedWhenOneSemanticOrderingExplainsTheSelection() {
        DatasetConformanceV1.requireValid(
            withProjectionEvents(
                recommendationEvent(
                    projection = buildProjection,
                    routineId = "BUI-01",
                    occurredAtUtc = INHERITED_COMMIT_AT_UTC,
                ),
                recommendationEvent(
                    projection = buildProjection,
                    routineId = "BUI-02",
                    id = EVENT_SAME_INSTANT_RECOMMENDATION_ID,
                    occurredAtUtc = PROJECTION_AT_UTC,
                ),
                routineSelectedEvent(
                    routineId = "BUI-01",
                    selection = "recommended",
                    id = EVENT_SELECTION_AFTER_RECOMMENDATION_ID,
                    occurredAtUtc = PROJECTION_AT_UTC,
                ),
            ),
        )
    }

    @Test
    fun conflictingRecommendationsAtTheLatestInstantRemainAmbiguous() {
        DatasetConformanceV1.requireValid(
            withProjectionEvents(
                recommendationEvent(
                    projection = buildProjection,
                    routineId = "BUI-01",
                    occurredAtUtc = PROJECTION_AT_UTC,
                ),
                recommendationEvent(
                    projection = buildProjection,
                    routineId = "BUI-02",
                    id = EVENT_LATEST_RECOMMENDATION_ID,
                    occurredAtUtc = PROJECTION_AT_UTC,
                ),
                routineSelectedEvent(
                    routineId = "BUI-01",
                    selection = "recommended",
                    occurredAtUtc = LATE_CAP_AT_UTC,
                ),
            ),
        )
    }

    @Test
    fun latestRecommendationWinsWhenSeveralAreRetained() {
        val latestRecommendation = recommendationEvent(
            projection = buildProjection,
            routineId = "BUI-02",
            id = EVENT_LATEST_RECOMMENDATION_ID,
            occurredAtUtc = INHERITED_CONSUMER_AT_UTC,
        )
        DatasetConformanceV1.requireValid(
            withProjectionEvents(
                recommendationEvent(buildProjection, occurredAtUtc = INHERITED_COMMIT_AT_UTC),
                latestRecommendation,
                routineSelectedEvent(
                    routineId = "BUI-02",
                    selection = "recommended",
                    occurredAtUtc = PROJECTION_AT_UTC,
                ),
            ),
        )

        val staleLabel = withProjectionEvents(
            recommendationEvent(buildProjection, occurredAtUtc = INHERITED_COMMIT_AT_UTC),
            latestRecommendation,
            routineSelectedEvent(
                routineId = "BUI-01",
                selection = "recommended",
                occurredAtUtc = PROJECTION_AT_UTC,
            ),
        )
        assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(staleLabel)
        }
    }

    @Test
    fun selectionDoesNotReachPastTheLatestDifferentRuntimeProjection() {
        val cap = capSnapshot(SESSION_ID, SESSION_ID)
        val capSource = ClosedCodecV1.decodeExport(
            datasetJson(
                sessionStatus = "STOPPED",
                includeFeedback = true,
                capUpdateResultingSourceSessionId = SESSION_ID,
            ),
        )
        val base = withSecondDecision(capSource)
        val retainedHistory = withProjectionEvents(
            base,
            recommendationEvent(
                projection = buildProjection,
                occurredAtUtc = INHERITED_COMMIT_AT_UTC,
                decisionId = SECOND_DECISION_ID,
            ),
            recommendationEvent(
                projection = RecommendationFixture(
                    decisionEffectiveMode = "BUILD",
                    runtimeEffectiveMode = "MAINTAIN",
                    capApplied = true,
                    runtimeCapSnapshot = cap,
                ),
                id = EVENT_LATEST_RECOMMENDATION_ID,
                occurredAtUtc = PROJECTION_AT_UTC,
                decisionId = SECOND_DECISION_ID,
            ),
            routineSelectedEvent(
                routineId = "BUI-02",
                selection = "recommended",
                occurredAtUtc = LATE_CAP_AT_UTC,
                decisionId = SECOND_DECISION_ID,
            ),
        )

        DatasetConformanceV1.requireValid(retainedHistory)
    }

    @Test
    fun projectionCapRequiresTerminalFeedbackModeTriggerProvenance() {
        val failure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                datasetJson(
                    sessionStatus = "ACTIVE",
                    recommendation = RecommendationFixture(
                        decisionEffectiveMode = "BUILD",
                        runtimeEffectiveMode = "MAINTAIN",
                        capApplied = true,
                        runtimeCapSnapshot = capSnapshot(
                            modeTriggerSessionId = SESSION_ID,
                            sourceSessionId = SESSION_ID,
                        ),
                    ),
                ),
            )
        }
        assertContains(failure.message.orEmpty(), "mode trigger Feedback provenance")
    }

    @Test
    fun capUpdateTopLevelExpirySourceMustMirrorAResolvingResultingCapSource() {
        val failure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                datasetJson(
                    sessionStatus = "STOPPED",
                    includeFeedback = true,
                    capUpdateResultingSourceSessionId = DANGLING_SESSION_ID,
                ),
            )
        }
        assertContains(failure.message.orEmpty(), "expiry_source_session_id must mirror resulting_cap.source_session_id")
    }

    @Test
    fun decisionCannotConsumeCapProducedByItsOwnFutureSession() {
        val futureCap = capSnapshot(
            modeTriggerSessionId = SESSION_ID,
            sourceSessionId = SESSION_ID,
            maxMode = "RECOVER",
        )

        val failure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                datasetJson(
                    decisionEffectiveMode = "RECOVER",
                    decisionReasonCodes = listOf("SAF_BUILD_CONDITIONS", "SAF_DAY_MODE_CAP_APPLIED"),
                    decisionCapSnapshot = futureCap,
                    sessionStatus = "STOPPED",
                    includeFeedback = true,
                    sessionRoutineId = "REC-01",
                    sessionRoutineMode = "RECOVER",
                    sessionDecisionMode = "RECOVER",
                    sessionRuntimeMode = "RECOVER",
                    capUpdateResultingSourceSessionId = SESSION_ID,
                    capUpdateBasisMode = "RECOVER",
                    capUpdateResultingMaxMode = "RECOVER",
                ),
            )
        }
        assertContains(failure.message.orEmpty(), "own descendant Session")
    }

    @Test
    fun sessionCannotConsumeCapProducedByItsOwnFutureFeedback() {
        val futureCap = capSnapshot(
            modeTriggerSessionId = SESSION_ID,
            sourceSessionId = SESSION_ID,
            maxMode = "RECOVER",
        )

        val failure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                datasetJson(
                    sessionStatus = "STOPPED",
                    includeFeedback = true,
                    sessionRoutineId = "REC-01",
                    sessionRoutineMode = "RECOVER",
                    sessionRuntimeMode = "RECOVER",
                    sessionRuntimeCapSnapshot = sessionRuntimeCapSnapshot(
                        appliedCap = futureCap,
                        decisionMode = "BUILD",
                        runtimeMode = "RECOVER",
                    ),
                    capUpdateResultingSourceSessionId = SESSION_ID,
                    capUpdateBasisMode = "RECOVER",
                    capUpdateResultingMaxMode = "RECOVER",
                ),
            )
        }
        assertContains(failure.message.orEmpty(), "consumer Session itself")
    }

    @Test
    fun projectionCannotConsumeCapFromItsOwnDecisionDescendant() {
        val futureCap = capSnapshot(
            modeTriggerSessionId = SESSION_ID,
            sourceSessionId = SESSION_ID,
        )

        val failure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                datasetJson(
                    sessionStatus = "STOPPED",
                    includeFeedback = true,
                    recommendation = RecommendationFixture(
                        decisionEffectiveMode = "BUILD",
                        runtimeEffectiveMode = "MAINTAIN",
                        capApplied = true,
                        runtimeCapSnapshot = futureCap,
                    ),
                    capUpdateResultingSourceSessionId = SESSION_ID,
                ),
            )
        }
        assertContains(failure.message.orEmpty(), "own descendant Session")
    }

    @Test
    fun projectionCapMustDeepMatchOneRetainedFeedbackResultingCap() {
        val firstCapDataset = ClosedCodecV1.decodeExport(
            datasetJson(
                sessionStatus = "STOPPED",
                includeFeedback = true,
                capUpdateResultingSourceSessionId = SESSION_ID,
            ),
        )
        val twoSessionDataset = withSecondTerminalSession(firstCapDataset)
        val splicedProjection = ClosedCodecV1.decodeEvent(
            recommendationEvent(
                projection = RecommendationFixture(
                    decisionEffectiveMode = "BUILD",
                    runtimeEffectiveMode = "MAINTAIN",
                    capApplied = true,
                    runtimeCapSnapshot = capSnapshot(
                        modeTriggerSessionId = SESSION_ID,
                        sourceSessionId = SECOND_SESSION_ID,
                    ),
                ),
                id = EVENT_SPLICED_PROJECTION_ID,
                occurredAtUtc = SUMMARY_AT_UTC,
            ),
        )
        val mutant = twoSessionDataset.copy(
            metadata = twoSessionDataset.metadata.copy(
                recordCounts = twoSessionDataset.metadata.recordCounts.copy(
                    events = twoSessionDataset.metadata.recordCounts.events + 1,
                ),
            ),
            events = sortEvents(twoSessionDataset.events + splicedProjection),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "deep-match a retained Feedback resulting_cap")
    }

    @Test
    fun projectionCannotConsumeCapWhoseMatchingUpdateCommitsLater() {
        val lateCapDataset = ClosedCodecV1.decodeExport(
            datasetJson(
                sessionStatus = "STOPPED",
                includeFeedback = true,
                capUpdateResultingSourceSessionId = SESSION_ID,
                capUpdateOccurredAtUtc = LATE_CAP_AT_UTC,
            ),
        )
        val twoDecisionDataset = withSecondDecision(lateCapDataset)
        val projection = ClosedCodecV1.decodeEvent(
            recommendationEvent(
                projection = RecommendationFixture(
                    decisionEffectiveMode = "BUILD",
                    runtimeEffectiveMode = "MAINTAIN",
                    capApplied = true,
                    runtimeCapSnapshot = capSnapshot(SESSION_ID, SESSION_ID),
                ),
                id = EVENT_FUTURE_CAP_PROJECTION_ID,
                occurredAtUtc = PROJECTION_AT_UTC,
                decisionId = SECOND_DECISION_ID,
            ),
        )
        val mutant = twoDecisionDataset.copy(
            metadata = twoDecisionDataset.metadata.copy(
                recordCounts = twoDecisionDataset.metadata.recordCounts.copy(
                    events = twoDecisionDataset.metadata.recordCounts.events + 1,
                ),
            ),
            events = sortEvents(twoDecisionDataset.events + projection),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "commit occurs after cap consumer")
    }

    @Test
    fun projectionCanConsumeExactCapCommittedByAnEarlierUnrelatedDecisionSession() {
        val priorCapDataset = ClosedCodecV1.decodeExport(
            datasetJson(
                sessionStatus = "STOPPED",
                includeFeedback = true,
                capUpdateResultingSourceSessionId = SESSION_ID,
            ),
        )
        val twoDecisionDataset = withSecondDecision(priorCapDataset)
        val projection = ClosedCodecV1.decodeEvent(
            recommendationEvent(
                projection = RecommendationFixture(
                    decisionEffectiveMode = "BUILD",
                    runtimeEffectiveMode = "MAINTAIN",
                    capApplied = true,
                    runtimeCapSnapshot = capSnapshot(SESSION_ID, SESSION_ID),
                ),
                id = EVENT_VALID_PRIOR_CAP_PROJECTION_ID,
                occurredAtUtc = SUMMARY_AT_UTC,
                decisionId = SECOND_DECISION_ID,
            ),
        )
        val valid = twoDecisionDataset.copy(
            metadata = twoDecisionDataset.metadata.copy(
                recordCounts = twoDecisionDataset.metadata.recordCounts.copy(
                    events = twoDecisionDataset.metadata.recordCounts.events + 1,
                ),
            ),
            events = sortEvents(twoDecisionDataset.events + projection),
        )

        DatasetConformanceV1.requireValid(valid)
    }

    @Test
    fun inheritedCapCannotUseAModeTriggerCommittedAfterItsOwnerUpdate() {
        val base = ClosedCodecV1.decodeExport(
            datasetJson(
                sessionStatus = "STOPPED",
                includeFeedback = true,
                capUpdateResultingSourceSessionId = SESSION_ID,
                capUpdateOccurredAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
            ),
        )
        val triggerEstablishedLate = withStrictRecoverCap(base, MODE_TRIGGER_COMMIT_AT_UTC)
        val inheritedEarlier = withInheritedSecondCap(triggerEstablishedLate, INHERITED_COMMIT_AT_UTC)
        val unrelatedDecision = withSecondDecision(inheritedEarlier)
        val inheritedSnapshot = capSnapshot(
            modeTriggerSessionId = SESSION_ID,
            sourceSessionId = SECOND_SESSION_ID,
            maxMode = "RECOVER",
        )
        val projection = ClosedCodecV1.decodeEvent(
            recommendationEvent(
                projection = RecommendationFixture(
                    decisionEffectiveMode = "BUILD",
                    runtimeEffectiveMode = "RECOVER",
                    capApplied = true,
                    runtimeCapSnapshot = inheritedSnapshot,
                ),
                id = EVENT_INHERITED_CAP_PROJECTION_ID,
                occurredAtUtc = INHERITED_CONSUMER_AT_UTC,
                decisionId = SECOND_DECISION_ID,
            ),
        )
        val mutant = unrelatedDecision.copy(
            metadata = unrelatedDecision.metadata.copy(
                recordCounts = unrelatedDecision.metadata.recordCounts.copy(
                    events = unrelatedDecision.metadata.recordCounts.events + 1,
                ),
            ),
            events = sortEvents(unrelatedDecision.events + projection),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "mode trigger commit occurs after an inheriting cap update")
    }

    @Test
    fun strictLowerCandidateLaterAcceptsMissingPriorOwnerAfterRetention() {
        val retainedSubset = withGraphCapUpdate(
            capGraphDataset(listOf(null), listOf(null)),
            index = 0,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(0),
                expirySourceSessionId = graphSessionId(0),
                deadlineSource = "candidate_later",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
            ),
        )

        DatasetConformanceV1.requireValid(retainedSubset)
    }

    @Test
    fun sameMillisecondCapChainUsesSemanticOrderInsteadOfEventIdOrder() {
        var valid = capGraphDataset(listOf(null, null, null), listOf(null, null, null))
        valid = withGraphCapUpdate(
            valid,
            index = 2,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(2),
                expirySourceSessionId = graphSessionId(2),
                deadlineSource = "candidate_later",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
            ),
        )
        valid = withGraphCapUpdate(
            valid,
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "RECOVER",
                previousMaxMode = "RECOVER",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(2),
                expirySourceSessionId = graphSessionId(1),
                deadlineSource = "candidate_later",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
            ),
        )

        DatasetConformanceV1.requireValid(valid)
    }

    @Test
    fun sameMillisecondCapForkCannotConsumeTheSamePriorStateTwice() {
        var mutant = capGraphDataset(listOf(null, null, null), listOf(null, null, null))
        listOf(1, 2).forEach { index ->
            mutant = withGraphCapUpdate(
                mutant,
                index = index,
                fixture = CapMergeFixture(
                    basisMode = "MAINTAIN",
                    previousMaxMode = "MAINTAIN",
                    resultingMaxMode = "RECOVER",
                    modeTriggerSessionId = graphSessionId(index),
                    expirySourceSessionId = graphSessionId(index),
                    deadlineSource = "candidate_later",
                    commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
                ),
            )
        }

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "same-millisecond cap updates")
    }

    @Test
    fun sameMillisecondNewMaintainCapCanPrecedeOneStrictLower() {
        val valid = withGraphCapUpdate(
            capGraphDataset(listOf(null, null), listOf(null, null)),
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(1),
                expirySourceSessionId = graphSessionId(1),
                deadlineSource = "candidate_later",
                commitAtUtc = TERMINAL_AT_UTC,
            ),
        )

        DatasetConformanceV1.requireValid(valid)
    }

    @Test
    fun sameMillisecondNewCapForkHasNoSemanticLinearization() {
        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(
                capGraphDataset(listOf(null, null), listOf(null, null)),
            )
        }
        assertContains(failure.message.orEmpty(), "same-millisecond cap updates")
    }

    @Test
    fun capUpdateCannotInheritAnExpirySourceEstablishedByAFutureCommit() {
        var mutant = capGraphDataset(
            decisionTargets = listOf(null, null, null),
            sessionTargets = listOf(null, null, null),
            capClockEvidenceBySession = listOf(
                capClockEvidence(monotonicDeadlineMs = 1_000),
                capClockEvidence(monotonicDeadlineMs = 2_000),
                capClockEvidence(monotonicDeadlineMs = 1_500),
            ),
        )
        mutant = withGraphCapUpdate(
            mutant,
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "BUILD",
                previousMaxMode = null,
                resultingMaxMode = "MAINTAIN",
                modeTriggerSessionId = graphSessionId(1),
                expirySourceSessionId = graphSessionId(1),
                deadlineSource = "candidate_later",
                commitAtUtc = LATE_CAP_AT_UTC,
            ),
        )
        mutant = withGraphCapUpdate(
            mutant,
            index = 2,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(2),
                expirySourceSessionId = graphSessionId(1),
                deadlineSource = "existing_later",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "inherit expiry source")
    }

    @Test
    fun nonLoweringCapUpdateMustMatchItsRetainedModeTriggerResult() {
        val mutant = withGraphCapUpdate(
            capGraphDataset(listOf(null, null), listOf(null, null)),
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "RECOVER",
                previousMaxMode = "RECOVER",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(0),
                expirySourceSessionId = graphSessionId(1),
                deadlineSource = "candidate_later",
                commitAtUtc = LATE_CAP_AT_UTC,
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "mode trigger Feedback provenance does not establish the snapshot max_mode")
    }

    @Test
    fun nonLoweringCapUpdateCannotSelfEstablishModeTrigger() {
        var mutant = capGraphDataset(
            decisionTargets = listOf(null, null, null),
            sessionTargets = listOf(null, null, null),
            capClockEvidenceBySession = listOf(
                capClockEvidence(monotonicDeadlineMs = 2_000),
                capClockEvidence(monotonicDeadlineMs = 1_500),
                capClockEvidence(monotonicDeadlineMs = 2_500),
            ),
        )
        mutant = withGraphCapUpdate(
            mutant,
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(1),
                expirySourceSessionId = graphSessionId(0),
                deadlineSource = "existing_later",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
            ),
        )
        mutant = withGraphCapUpdate(
            mutant,
            index = 2,
            fixture = CapMergeFixture(
                basisMode = "RECOVER",
                previousMaxMode = "RECOVER",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(2),
                expirySourceSessionId = graphSessionId(2),
                deadlineSource = "candidate_later",
                commitAtUtc = LATE_CAP_AT_UTC,
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "inherit mode trigger")
    }

    @Test
    fun existingOrSameDeadlineCannotReplaceThePriorExpirySource() {
        listOf("existing_later", "same").forEach { deadlineSource ->
            val mutant = withGraphCapUpdate(
                capGraphDataset(listOf(null, null), listOf(null, null)),
                index = 1,
                fixture = CapMergeFixture(
                    basisMode = "MAINTAIN",
                    previousMaxMode = "MAINTAIN",
                    resultingMaxMode = "RECOVER",
                    modeTriggerSessionId = graphSessionId(1),
                    expirySourceSessionId = graphSessionId(1),
                    deadlineSource = deadlineSource,
                    commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
                ),
            )

            val failure = assertFailsWith<WireContractException>(deadlineSource) {
                DatasetConformanceV1.requireValid(mutant)
            }
            assertContains(failure.message.orEmpty(), "inherit expiry source")
        }
    }

    @Test
    fun candidateLaterMustAdoptTheTriggeringSessionClockEvidence() {
        val triggerClock = capClockEvidence(monotonicDeadlineMs = 2_000)
        val mutant = withGraphCapUpdate(
            capGraphDataset(
                decisionTargets = listOf(null, null),
                sessionTargets = listOf(null, null),
                capClockEvidenceBySession = listOf(clockIntegrity, triggerClock),
            ),
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(1),
                expirySourceSessionId = graphSessionId(1),
                deadlineSource = "candidate_later",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
                resultingClockEvidenceOverride = clockIntegrity,
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "expiry/clock evidence")
    }

    @Test
    fun existingDeadlineMustPreserveTheRetainedPreviousSourceClockEvidence() {
        val existingClock = capClockEvidence(monotonicDeadlineMs = 2_000)
        val candidateClock = capClockEvidence(monotonicDeadlineMs = 1_500)
        val mutant = withGraphCapUpdate(
            capGraphDataset(
                decisionTargets = listOf(null, null),
                sessionTargets = listOf(null, null),
                capClockEvidenceBySession = listOf(existingClock, candidateClock),
            ),
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(1),
                expirySourceSessionId = graphSessionId(0),
                deadlineSource = "existing_later",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
                resultingClockEvidenceOverride = candidateClock,
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "expiry/clock evidence")
    }

    @Test
    fun candidateLaterRequiresAStrictlyLaterComparableCandidateDeadline() {
        val mutant = withGraphCapUpdate(
            capGraphDataset(
                decisionTargets = listOf(null, null),
                sessionTargets = listOf(null, null),
                capClockEvidenceBySession = listOf(
                    capClockEvidence(monotonicDeadlineMs = 2_000),
                    capClockEvidence(monotonicDeadlineMs = 1_500),
                ),
            ),
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(1),
                expirySourceSessionId = graphSessionId(1),
                deadlineSource = "candidate_later",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "candidate_later")
    }

    @Test
    fun existingLaterRequiresAStrictlyLaterComparableExistingDeadline() {
        listOf(1_500L, 2_000L, 2_500L).forEach { candidateDeadline ->
            val mutant = withGraphCapUpdate(
                capGraphDataset(
                    decisionTargets = listOf(null, null),
                    sessionTargets = listOf(null, null),
                    capClockEvidenceBySession = listOf(
                        capClockEvidence(monotonicDeadlineMs = 2_000),
                        capClockEvidence(monotonicDeadlineMs = candidateDeadline),
                    ),
                ),
                index = 1,
                fixture = CapMergeFixture(
                    basisMode = "MAINTAIN",
                    previousMaxMode = "MAINTAIN",
                    resultingMaxMode = "RECOVER",
                    modeTriggerSessionId = graphSessionId(1),
                    expirySourceSessionId = graphSessionId(0),
                    deadlineSource = "existing_later",
                    commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
                ),
            )

            if (candidateDeadline < 2_000) {
                DatasetConformanceV1.requireValid(mutant)
            } else {
                val failure = assertFailsWith<WireContractException> {
                    DatasetConformanceV1.requireValid(mutant)
                }
                assertContains(failure.message.orEmpty(), "existing_later")
            }
        }
    }

    @Test
    fun sameRequiresEqualComparableDeadlines() {
        val valid = withGraphCapUpdate(
            capGraphDataset(
                decisionTargets = listOf(null, null),
                sessionTargets = listOf(null, null),
                capClockEvidenceBySession = listOf(
                    capClockEvidence(monotonicDeadlineMs = 2_000),
                    capClockEvidence(monotonicDeadlineMs = 2_000),
                ),
            ),
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(1),
                expirySourceSessionId = graphSessionId(0),
                deadlineSource = "same",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
            ),
        )
        DatasetConformanceV1.requireValid(valid)

        val mutant = withGraphCapUpdate(
            capGraphDataset(
                decisionTargets = listOf(null, null),
                sessionTargets = listOf(null, null),
                capClockEvidenceBySession = listOf(
                    capClockEvidence(monotonicDeadlineMs = 2_000),
                    capClockEvidence(monotonicDeadlineMs = 1_500),
                ),
            ),
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(1),
                expirySourceSessionId = graphSessionId(0),
                deadlineSource = "same",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
            ),
        )
        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "same")
    }

    @Test
    fun differentBootDeadlinesAreNotComparedByRawMonotonicNumbers() {
        val valid = withGraphCapUpdate(
            capGraphDataset(
                decisionTargets = listOf(null, null),
                sessionTargets = listOf(null, null),
                capClockEvidenceBySession = listOf(
                    capClockEvidence(originBootMarker = 1, monotonicDeadlineMs = 1_500),
                    capClockEvidence(originBootMarker = 2, monotonicDeadlineMs = 2_000),
                ),
            ),
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(1),
                expirySourceSessionId = graphSessionId(0),
                deadlineSource = "existing_later",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
            ),
        )

        DatasetConformanceV1.requireValid(valid)
    }

    @Test
    fun canonicalThreeStepCapMergeLineagePasses() {
        var valid = capGraphDataset(
            decisionTargets = listOf(null, null, null),
            sessionTargets = listOf(null, null, null),
            capClockEvidenceBySession = listOf(
                capClockEvidence(monotonicDeadlineMs = 2_000),
                capClockEvidence(monotonicDeadlineMs = 1_500),
                capClockEvidence(monotonicDeadlineMs = 2_500),
            ),
        )
        valid = withGraphCapUpdate(
            valid,
            index = 1,
            fixture = CapMergeFixture(
                basisMode = "MAINTAIN",
                previousMaxMode = "MAINTAIN",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(1),
                expirySourceSessionId = graphSessionId(0),
                deadlineSource = "existing_later",
                commitAtUtc = MODE_TRIGGER_COMMIT_AT_UTC,
            ),
        )
        valid = withGraphCapUpdate(
            valid,
            index = 2,
            fixture = CapMergeFixture(
                basisMode = "RECOVER",
                previousMaxMode = "RECOVER",
                resultingMaxMode = "RECOVER",
                modeTriggerSessionId = graphSessionId(1),
                expirySourceSessionId = graphSessionId(2),
                deadlineSource = "candidate_later",
                commitAtUtc = LATE_CAP_AT_UTC,
            ),
        )

        DatasetConformanceV1.requireValid(valid)
    }

    @Test
    fun capDependencyGraphRejectsTwoNodeMixedDecisionSessionCycle() {
        val mutant = capGraphDataset(
            decisionTargets = listOf(1, null),
            sessionTargets = listOf(null, 0),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireAcyclicCapEntityDependencies(mutant.decisions, mutant.sessions)
        }
        assertContains(failure.message.orEmpty(), "day-mode cap dependency cycle")
    }

    @Test
    fun capDependencyGraphRejectsMutualSiblingSessionCycleUnderOneDecision() {
        val mutant = withGraphSessionDecision(
            capGraphDataset(
                decisionTargets = listOf(null, null),
                sessionTargets = listOf(1, 0),
            ),
            sessionIndex = 1,
            decisionIndex = 0,
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireAcyclicCapEntityDependencies(mutant.decisions, mutant.sessions)
        }
        assertContains(failure.message.orEmpty(), "day-mode cap dependency cycle")
    }

    @Test
    fun capDependencyGraphAcceptsOneWayPriorSiblingUnderOneDecision() {
        val valid = withGraphSessionDecision(
            capGraphDataset(
                decisionTargets = listOf(null, null),
                sessionTargets = listOf(1, null),
            ),
            sessionIndex = 1,
            decisionIndex = 0,
        )

        DatasetConformanceV1.requireAcyclicCapEntityDependencies(valid.decisions, valid.sessions)
    }

    @Test
    fun capDependencyGraphRejectsThreeNodeDecisionCycle() {
        val mutant = capGraphDataset(
            decisionTargets = listOf(1, 2, 0),
            sessionTargets = listOf(null, null, null),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireAcyclicCapEntityDependencies(mutant.decisions, mutant.sessions)
        }
        assertContains(failure.message.orEmpty(), "day-mode cap dependency cycle")
    }

    @Test
    fun capDependencyTraversalAcceptsADeepAcyclicChainWithoutUsingTheCallStack() {
        val nodeCount = 20_000
        val edges = LinkedHashMap<String, Set<String>>(nodeCount)
        repeat(nodeCount) { index ->
            edges["decision-$index"] = if (index + 1 < nodeCount) {
                setOf("decision-${index + 1}")
            } else {
                emptySet()
            }
        }

        DatasetConformanceV1.requireAcyclicCapDecisionDependencies(edges)
    }

    @Test
    fun capDependencyTraversalRejectsADeepBackEdgeWithoutOverflowing() {
        val nodeCount = 20_000
        val edges = LinkedHashMap<String, Set<String>>(nodeCount)
        repeat(nodeCount) { index ->
            edges["decision-$index"] = if (index + 1 < nodeCount) {
                setOf("decision-${index + 1}")
            } else {
                setOf("decision-${nodeCount / 2}")
            }
        }

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireAcyclicCapDecisionDependencies(edges)
        }
        assertContains(failure.message.orEmpty(), "day-mode cap dependency cycle")
    }

    @Test
    fun checkInParentTraversalAcceptsADeepAcyclicChainInLinearSpace() {
        val nodeCount = 20_000
        val parents = LinkedHashMap<String, String?>(nodeCount)
        repeat(nodeCount) { index ->
            parents["check-in-$index"] = if (index + 1 < nodeCount) "check-in-${index + 1}" else null
        }

        DatasetConformanceV1.requireAcyclicCheckInParents(parents)
    }

    @Test
    fun checkInParentTraversalRejectsADeepBackEdgeWithoutOverflowing() {
        val nodeCount = 20_000
        val parents = LinkedHashMap<String, String?>(nodeCount)
        repeat(nodeCount) { index ->
            parents["check-in-$index"] = if (index + 1 < nodeCount) {
                "check-in-${index + 1}"
            } else {
                "check-in-${nodeCount / 2}"
            }
        }

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireAcyclicCheckInParents(parents)
        }
        assertContains(failure.message.orEmpty(), "parent_id cycle")
    }

    @Test
    fun capDependencyGraphAcceptsAcyclicSameMillisecondHistory() {
        val valid = capGraphDataset(
            decisionTargets = listOf(1, null),
            sessionTargets = listOf(null, null),
        )

        DatasetConformanceV1.requireAcyclicCapEntityDependencies(valid.decisions, valid.sessions)
    }

    @Test
    fun completeWeeklyRawGraphRejectsWrongCompletedOnlyContextRate() {
        val failure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                datasetJson(
                    sessionStatus = "STOPPED",
                    includeFeedback = true,
                    feedbackContextFit = "yes",
                    weeklySummary = WeeklySummaryFixture(
                        startedCount = 1,
                        contextRate = suppressedRate(1, 1),
                    ),
                ),
            )
        }
        assertContains(failure.message.orEmpty(), "context_fit_rate must match the completed Session raw cohort")
    }

    @Test
    fun incompleteRetainedWeeklyRawGraphDoesNotOverrideCachedContextRate() {
        ClosedCodecV1.decodeExport(
            datasetJson(
                sessionStatus = "STOPPED",
                includeFeedback = true,
                feedbackContextFit = "yes",
                weeklySummary = WeeklySummaryFixture(
                    startedCount = 2,
                    contextRate = suppressedRate(1, 1),
                ),
            ),
        )
    }

    private fun datasetJson(
        decisionFreshness: Freshness = Freshness(),
        decisionEffectiveMode: String = "BUILD",
        decisionReasonCodes: List<String> = listOf("SAF_BUILD_CONDITIONS"),
        decisionCapSnapshot: String = "null",
        sessionStatus: String? = null,
        includeFeedback: Boolean = false,
        sessionRoutineId: String = "BUI-01",
        sessionRoutineMode: String = "BUILD",
        sessionDecisionMode: String = "BUILD",
        sessionRuntimeMode: String = "BUILD",
        sessionRuntimeCapSnapshot: String = "null",
        recommendation: RecommendationFixture? = null,
        capUpdateResultingSourceSessionId: String? = null,
        capUpdateBasisMode: String = "BUILD",
        capUpdateResultingMaxMode: String = "MAINTAIN",
        capUpdateOccurredAtUtc: String = TERMINAL_AT_UTC,
        feedbackContextFit: String? = null,
        weeklySummary: WeeklySummaryFixture? = null,
    ): String {
        val sessions = sessionStatus?.let {
            sessionJson(
                status = it,
                routineId = sessionRoutineId,
                routineMode = sessionRoutineMode,
                decisionMode = sessionDecisionMode,
                runtimeMode = sessionRuntimeMode,
                runtimeCapSnapshot = sessionRuntimeCapSnapshot,
            )
        }.orEmpty()
        val feedback = if (includeFeedback) {
            feedbackJson(
                requireNotNull(sessionStatus),
                capUpdateResultingSourceSessionId,
                capUpdateBasisMode,
                capUpdateResultingMaxMode,
                capUpdateOccurredAtUtc,
                feedbackContextFit,
            )
        } else {
            ""
        }
        val events = companionEvents(
            sessionStatus = sessionStatus,
            sessionRoutineId = sessionRoutineId,
            sessionRuntimeMode = sessionRuntimeMode,
            recommendation = recommendation,
            capUpdateResultingSourceSessionId = capUpdateResultingSourceSessionId,
            capUpdateBasisMode = capUpdateBasisMode,
            capUpdateResultingMaxMode = capUpdateResultingMaxMode,
            capUpdateOccurredAtUtc = capUpdateOccurredAtUtc,
            feedbackContextFit = feedbackContextFit,
            includeWeeklySummary = weeklySummary != null,
            decisionEffectiveMode = decisionEffectiveMode,
            decisionReasonCodes = decisionReasonCodes,
        )
        val weeklySummaryJson = weeklySummary?.let(::weeklySummaryJson).orEmpty()
        return """
            {
              "metadata":{
                "export_schema_version":1,
                "exported_at_utc":"2026-08-27T10:02:00.000Z",
                "app_version":"1.0.0",
                "content_version":"1.0.0",
                "rule_version":1,
                "retention_policy_version":1,
                "record_counts":{
                  "profile":1,
                  "work_schedule":1,
                  "check_ins":1,
                  "decisions":1,
                  "sessions":${if (sessionStatus == null) 0 else 1},
                  "feedback":${if (includeFeedback) 1 else 0},
                  "reminders":0,
                  "events":${events.size},
                  "weekly_summaries":${if (weeklySummary == null) 0 else 1}
                }
              },
              "profile":[$profileJson],
              "work_schedule":[$scheduleJson],
              "check_ins":[$checkInJson],
              "decisions":[${decisionJson(decisionFreshness, decisionEffectiveMode, decisionReasonCodes, decisionCapSnapshot)}],
              "sessions":[$sessions],
              "feedback":[$feedback],
              "reminders":[],
              "events":[${events.joinToString(",")}],
              "weekly_summaries":[$weeklySummaryJson]
            }
        """.trimIndent()
    }

    private fun companionEvents(
        sessionStatus: String?,
        sessionRoutineId: String,
        sessionRuntimeMode: String,
        recommendation: RecommendationFixture?,
        capUpdateResultingSourceSessionId: String?,
        capUpdateBasisMode: String,
        capUpdateResultingMaxMode: String,
        capUpdateOccurredAtUtc: String,
        feedbackContextFit: String?,
        includeWeeklySummary: Boolean,
        decisionEffectiveMode: String,
        decisionReasonCodes: List<String>,
    ): List<String> = buildList {
        add(eventJson(
            id = EVENT_ACK_ID,
            name = "scope_acknowledged",
            occurredAtUtc = STARTED_AT_UTC,
            properties = """
                {
                  "acknowledgement_id":"00000000-0000-4000-8000-000000000002",
                  "kind":"onboarding",
                  "eligibility_confirmed":true,
                  "content_version":"1.0.0",
                  "content_digest":"0000000000000000000000000000000000000000000000000000000000000000"
                }
            """.trimIndent(),
        ))
        add(eventJson(
            id = EVENT_ONBOARDING_ID,
            name = "onboarding_completed",
            occurredAtUtc = STARTED_AT_UTC,
            properties = """
                {
                  "duration_ms":1,
                  "activation_boot_marker":1,
                  "activation_elapsed_realtime_ms":2,
                  "activation_clock_generation":4,
                  "activation_wall_minus_elapsed_ms":5
                }
            """.trimIndent(),
        ))
        add(eventJson(
            id = EVENT_CHECK_IN_ID,
            name = "check_in_submitted",
            occurredAtUtc = STARTED_AT_UTC,
            scheduleVersionId = SCHEDULE_ID,
            properties = """
                {
                  "check_in_flow_id":"$CHECK_IN_FLOW_ID",
                  "check_in_id":"$CHECK_IN_ID",
                  "kind":"new",
                  "answers_kind":"full",
                  "duration_ms":1
                }
            """.trimIndent(),
        ))
        add(eventJson(
            id = EVENT_DECISION_ID,
            name = "decision_evaluated",
            occurredAtUtc = STARTED_AT_UTC,
            decisionId = DECISION_ID,
            scheduleVersionId = SCHEDULE_ID,
            properties = """
                {
                  "check_in_id":"$CHECK_IN_ID",
                  "result":"BUILD",
                  "base_mode":"BUILD",
                  "effective_mode":"$decisionEffectiveMode",
                  "reason_codes":${decisionReasonCodes.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\"")},
                  "invalid_fields":[],
                  "rule_version":1,
                  "cap_applied":${decisionEffectiveMode != "BUILD"}
                }
            """.trimIndent(),
        ))
        if (sessionStatus == null) {
            recommendation?.let { add(recommendationEvent(it)) }
            return@buildList
        }
        add(eventJson(
            id = EVENT_SESSION_START_ID,
            name = "routine_started",
            occurredAtUtc = STARTED_AT_UTC,
            decisionId = DECISION_ID,
            sessionId = SESSION_ID,
            scheduleVersionId = SCHEDULE_ID,
            source = "home",
            properties = """
                {
                  "routine_id":"$sessionRoutineId",
                  "check_in_flow_id":"$CHECK_IN_FLOW_ID",
                  "runtime_effective_mode_at_start":"$sessionRuntimeMode",
                  "is_selected_workday_at_start":true,
                  "start_boot_marker":1,
                  "start_elapsed_realtime_ms":20,
                  "start_clock_generation":4,
                  "start_wall_minus_elapsed_ms":5,
                  "total_duration_ms":1
                }
            """.trimIndent(),
        ))
        recommendation?.let { add(recommendationEvent(it)) }
        if (sessionStatus == "ACTIVE") {
            return@buildList
        }
        val terminal = when (sessionStatus) {
            "COMPLETED" -> "routine_completed" to """
                {
                  "routine_id":"BUI-01",
                  "duration_ms":1000,
                  "step_skip_count":0,
                  "pain_gate_status":"PENDING",
                  "completion_boot_marker":1,
                  "completion_elapsed_realtime_ms":1000,
                  "completion_clock_generation":4,
                  "completion_wall_minus_elapsed_ms":5
                }
            """.trimIndent()
            "STOPPED" -> "routine_stopped" to """{"elapsed_ms":1000,"pain_gate_status":"RESOLVED_NO"}"""
            "ABANDONED" -> "routine_abandoned" to """{"reason":"reboot_or_clock_discontinuity","pain_gate_status":"PENDING"}"""
            else -> error("Unsupported fixture status $sessionStatus")
        }
        add(eventJson(
            id = EVENT_SESSION_TERMINAL_ID,
            name = terminal.first,
            occurredAtUtc = TERMINAL_AT_UTC,
            sessionId = SESSION_ID,
            properties = terminal.second,
        ))
        if (sessionStatus == "STOPPED") {
            add(eventJson(
                id = EVENT_PAIN_RESOLVED_ID,
                name = "pain_gate_resolved",
                occurredAtUtc = TERMINAL_AT_UTC,
                sessionId = SESSION_ID,
                properties = """
                    {
                      "terminal_state":"stopped",
                      "new_or_worse_pain":"no",
                      "pain_gate_status":"RESOLVED_NO",
                      "answered_at_or_after_origin_expiry":false
                    }
                """.trimIndent(),
            ))
        }
        capUpdateResultingSourceSessionId?.let { resultingSource ->
            add(eventJson(
                id = EVENT_FEEDBACK_UPDATED_ID,
                name = "feedback_updated",
                occurredAtUtc = capUpdateOccurredAtUtc,
                sessionId = SESSION_ID,
                properties = """
                    {
                      "updated_fields":["effort"],
                      "terminal_state":"stopped",
                      "effort":"too_hard",
                      "context_fit":null,
                      "feedback_complete":false,
                      "cap_result":"applied"
                    }
                """.trimIndent(),
            ))
            add(eventJson(
                id = EVENT_CAP_UPDATED_ID,
                name = "day_mode_cap_updated",
                occurredAtUtc = capUpdateOccurredAtUtc,
                sessionId = SESSION_ID,
                properties = """
                    {
                      "expiry_source_session_id":"$SESSION_ID",
                      "basis_mode":"$capUpdateBasisMode",
                      "previous_cap":null,
                      "new_cap":"$capUpdateResultingMaxMode",
                      "deadline_source":"candidate_later",
                      "origin_occurred_at_utc":"$TERMINAL_AT_UTC",
                      "origin_local_date":"2026-08-27",
                      "origin_timezone_id":"UTC",
                      "origin_utc_offset_minutes":0,
                      "expires_at_utc":"2026-08-28T00:00:00.000Z",
                      "rule_version":1
                    }
                """.trimIndent(),
            ))
        }
        if (feedbackContextFit != null && capUpdateResultingSourceSessionId == null) {
            add(eventJson(
                id = EVENT_FEEDBACK_UPDATED_ID,
                name = "feedback_updated",
                occurredAtUtc = TERMINAL_AT_UTC,
                sessionId = SESSION_ID,
                properties = """
                    {
                      "updated_fields":["context_fit"],
                      "terminal_state":"${requireNotNull(sessionStatus).lowercase()}",
                      "effort":null,
                      "context_fit":"$feedbackContextFit",
                      "feedback_complete":false,
                      "cap_result":"no_effort_transition"
                    }
                """.trimIndent(),
            ))
        }
        if (includeWeeklySummary) {
            add(eventJson(
                id = EVENT_WEEKLY_SUMMARY_ID,
                name = "weekly_summary_generated",
                occurredAtUtc = SUMMARY_AT_UTC,
                properties = """
                    {
                      "week_start_local_date":"2026-08-24",
                      "summary_id":"$SUMMARY_ID",
                      "qualified_break_days":0,
                      "completed_count":0
                    }
                """.trimIndent(),
            ))
        }
    }

    private fun recommendationEvent(
        projection: RecommendationFixture,
        routineId: String = when (projection.runtimeEffectiveMode) {
            "BUILD" -> "BUI-01"
            "MAINTAIN" -> "MAI-01"
            else -> "REC-01"
        },
        id: String = EVENT_PROJECTION_ID,
        occurredAtUtc: String = STARTED_AT_UTC,
        decisionId: String = DECISION_ID,
    ): String = eventJson(
        id = id,
        name = "recommendation_shown",
        occurredAtUtc = occurredAtUtc,
        decisionId = decisionId,
        properties = """
            {
              "routine_id":"$routineId",
              "base_mode":"BUILD",
              "decision_effective_mode":"${projection.decisionEffectiveMode}",
              "runtime_effective_mode":"${projection.runtimeEffectiveMode}",
              "cap_applied":${projection.capApplied},
              "runtime_day_mode_cap_snapshot":${projection.runtimeCapSnapshot}
            }
        """.trimIndent(),
    )

    private fun immediatePauseDataset(
        routeId: String,
        contentDigest: String = "0".repeat(64),
    ): ExportDatasetWireV1 {
        val base = ClosedCodecV1.decodeExport(datasetJson())
        val safetySnapshot = """
            {
              "occurred_at_utc":"$STARTED_AT_UTC",
              "local_date":"2026-08-27",
              "zone_id":"UTC",
              "utc_offset_minutes":0,
              "kind":"ACUTE_ILLNESS",
              "source_type":"check_in",
              "source_id":"$CHECK_IN_ID",
              "expires_at_utc":"2026-08-28T00:00:00.000Z",
              "clock_integrity":$decisionSafetyClockIntegrity,
              "rule_version":1
            }
        """.trimIndent()
        val pauseDecision = DecisionWireV1(
            StrictJsonV1.parseObject(
                """
                    {
                      "decision_id":"$DECISION_ID",
                      "check_in_id":"$CHECK_IN_ID",
                      "schedule_version_id":"$SCHEDULE_ID",
                      "rule_version":1,
                      "outcome":"PAUSE_TODAY",
                      "base_mode":null,
                      "effective_mode":null,
                      "reason_codes":["SAF_ACUTE_ILLNESS"],
                      "invalid_fields":[],
                      "created_safety_hold_snapshot":$safetySnapshot,
                      "created_rest_suppression_snapshot":null,
                      "evaluation_day_mode_cap_snapshot":null,
                      "created_at":$stamp,
                      "reconfirm_after":"2026-08-27T16:00:00.000Z",
                      "valid_until_work_end":"2026-08-27T17:00:00.000Z",
                      "confirmed_boot_marker":1,
                      "confirmed_elapsed_realtime_ms":2,
                      "ttl_monotonic_deadline_ms":3,
                      "confirmed_clock_generation":4,
                      "confirmed_zone_id":"UTC",
                      "confirmed_wall_minus_elapsed_ms":5
                    }
                """.trimIndent(),
            ),
        )
        val pauseCommit = ClosedCodecV1.decodeEvent(
            eventJson(
                id = EVENT_DECISION_ID,
                name = "decision_evaluated",
                occurredAtUtc = STARTED_AT_UTC,
                decisionId = DECISION_ID,
                scheduleVersionId = SCHEDULE_ID,
                properties = """
                    {
                      "check_in_id":"$CHECK_IN_ID",
                      "result":"PAUSE_TODAY",
                      "base_mode":null,
                      "effective_mode":null,
                      "reason_codes":["SAF_ACUTE_ILLNESS"],
                      "invalid_fields":[],
                      "rule_version":1,
                      "cap_applied":false
                    }
                """.trimIndent(),
            ),
        )
        val holdCreated = ClosedCodecV1.decodeEvent(
            eventJson(
                id = EVENT_IMMEDIATE_SAFETY_HOLD_ID,
                name = "safety_hold_created",
                occurredAtUtc = STARTED_AT_UTC,
                properties = """
                    {
                      "kind":"ACUTE_ILLNESS",
                      "source_type":"check_in",
                      "source_id":"$CHECK_IN_ID",
                      "origin_local_date":"2026-08-27",
                      "origin_timezone_id":"UTC",
                      "expires_at_utc":"2026-08-28T00:00:00.000Z",
                      "rule_version":1
                    }
                """.trimIndent(),
            ),
        )
        val screenShown = ClosedCodecV1.decodeEvent(
            eventJson(
                id = EVENT_IMMEDIATE_SAFETY_SCREEN_ID,
                name = "safety_screen_shown",
                occurredAtUtc = STARTED_AT_UTC,
                decisionId = DECISION_ID,
                properties = safetyScreenProperties("PAUSE_TODAY", routeId, contentDigest),
            ),
        )
        val retainedEvents = base.events.filterNot { it.envelope.eventId.value == EVENT_DECISION_ID }
        return base.copy(
            metadata = base.metadata.copy(
                recordCounts = base.metadata.recordCounts.copy(events = base.metadata.recordCounts.events + 2),
            ),
            decisions = listOf(pauseDecision),
            events = sortEvents(retainedEvents + pauseCommit + holdCreated + screenShown),
        )
    }

    private fun postSessionSafetyDataset(routeId: String): ExportDatasetWireV1 {
        val base = ClosedCodecV1.decodeExport(
            datasetJson(
                sessionStatus = "STOPPED",
                includeFeedback = true,
            ),
        )
        val holdSnapshot = StrictJsonV1.parseObject(
            """
                {
                  "occurred_at_utc":"$TERMINAL_AT_UTC",
                  "local_date":"2026-08-27",
                  "zone_id":"UTC",
                  "utc_offset_minutes":0,
                  "kind":"POST_SESSION_NEW_OR_WORSE_PAIN",
                  "source_type":"session",
                  "source_id":"$SESSION_ID",
                  "expires_at_utc":"2026-08-28T00:00:00.000Z",
                  "clock_integrity":$clockIntegrity,
                  "rule_version":1
                }
            """.trimIndent(),
        ).element
        val feedback = base.feedback.single()
        val heldFeedback = FeedbackWireV1(
            StrictJsonObjectV1(
                JsonObject(
                    feedback.body.element + mapOf(
                        "pain_gate_status" to JsonPrimitive("resolved_hold"),
                        "new_or_worse_pain" to JsonPrimitive("yes"),
                        "created_post_session_safety_hold_snapshot" to holdSnapshot,
                    ),
                ),
            ),
        )
        val stopped = ClosedCodecV1.decodeEvent(
            eventJson(
                id = EVENT_SESSION_TERMINAL_ID,
                name = "routine_stopped",
                occurredAtUtc = TERMINAL_AT_UTC,
                sessionId = SESSION_ID,
                properties = """{"elapsed_ms":1000,"pain_gate_status":"RESOLVED_HOLD"}""",
            ),
        )
        val painResolved = ClosedCodecV1.decodeEvent(
            eventJson(
                id = EVENT_PAIN_RESOLVED_ID,
                name = "pain_gate_resolved",
                occurredAtUtc = TERMINAL_AT_UTC,
                sessionId = SESSION_ID,
                properties = """
                    {
                      "terminal_state":"stopped",
                      "new_or_worse_pain":"yes",
                      "pain_gate_status":"RESOLVED_HOLD",
                      "answered_at_or_after_origin_expiry":false
                    }
                """.trimIndent(),
            ),
        )
        val holdCreated = ClosedCodecV1.decodeEvent(
            eventJson(
                id = EVENT_POST_SESSION_SAFETY_HOLD_ID,
                name = "safety_hold_created",
                occurredAtUtc = TERMINAL_AT_UTC,
                sessionId = SESSION_ID,
                properties = """
                    {
                      "kind":"POST_SESSION_NEW_OR_WORSE_PAIN",
                      "source_type":"session",
                      "origin_local_date":"2026-08-27",
                      "origin_timezone_id":"UTC",
                      "expires_at_utc":"2026-08-28T00:00:00.000Z",
                      "rule_version":1
                    }
                """.trimIndent(),
            ),
        )
        val screenShown = ClosedCodecV1.decodeEvent(
            eventJson(
                id = EVENT_POST_SESSION_SAFETY_SCREEN_ID,
                name = "safety_screen_shown",
                occurredAtUtc = TERMINAL_AT_UTC,
                sessionId = SESSION_ID,
                properties = safetyScreenProperties("BLOCKED_FOR_TODAY", routeId),
            ),
        )
        val replacementEvents = mapOf(
            EVENT_SESSION_TERMINAL_ID to stopped,
            EVENT_PAIN_RESOLVED_ID to painResolved,
        )
        return base.copy(
            metadata = base.metadata.copy(
                recordCounts = base.metadata.recordCounts.copy(events = base.metadata.recordCounts.events + 2),
            ),
            feedback = listOf(heldFeedback),
            events = sortEvents(
                base.events.map { replacementEvents[it.envelope.eventId.value] ?: it } + holdCreated + screenShown,
            ),
        )
    }

    private fun safetyScreenProperties(
        result: String,
        routeId: String,
        contentDigest: String = "0".repeat(64),
    ): String = """
        {
          "result":"$result",
          "route_id":"$routeId",
          "content_digest":"$contentDigest"
        }
    """.trimIndent()

    private fun routineSelectedEvent(
        routineId: String,
        selection: String,
        id: String = EVENT_SELECTION_ID,
        occurredAtUtc: String,
        runtimeEffectiveMode: String = "BUILD",
        runtimeCapSnapshot: String = "null",
        decisionId: String = DECISION_ID,
    ): String = eventJson(
        id = id,
        name = "routine_selected",
        occurredAtUtc = occurredAtUtc,
        decisionId = decisionId,
        properties = """
            {
              "routine_id":"$routineId",
              "routine_mode":"${RoutineModeCatalogV1.modeFor(routineId, "test.routine_selected")}",
              "runtime_effective_mode":"$runtimeEffectiveMode",
              "selection":"$selection",
              "runtime_day_mode_cap_snapshot":$runtimeCapSnapshot
            }
        """.trimIndent(),
    )

    private fun withProjectionEvents(vararg encodedEvents: String): ExportDatasetWireV1 {
        return withProjectionEvents(ClosedCodecV1.decodeExport(datasetJson()), *encodedEvents)
    }

    private fun withProjectionEvents(
        dataset: ExportDatasetWireV1,
        vararg encodedEvents: String,
    ): ExportDatasetWireV1 {
        val addedEvents = encodedEvents.map(ClosedCodecV1::decodeEvent)
        return dataset.copy(
            metadata = dataset.metadata.copy(
                recordCounts = dataset.metadata.recordCounts.copy(
                    events = dataset.metadata.recordCounts.events + addedEvents.size,
                ),
            ),
            events = sortEvents(dataset.events + addedEvents),
        )
    }

    private fun eventJson(
        id: String,
        name: String,
        occurredAtUtc: String,
        properties: String,
        decisionId: String? = null,
        sessionId: String? = null,
        scheduleVersionId: String? = null,
        source: String? = null,
    ): String = """
        {
          "event_id":"$id",
          "event_schema_version":1,
          "name":"$name",
          "occurred_at_utc":"$occurredAtUtc",
          "local_date":"2026-08-27",
          "zone_id":"UTC",
          "utc_offset_minutes":0,
          "installation_id":"00000000-0000-4000-8000-000000000001",
          "decision_id":${decisionId.jsonStringOrNull()},
          "session_id":${sessionId.jsonStringOrNull()},
          "reminder_occurrence_id":null,
          "schedule_version_id":${scheduleVersionId.jsonStringOrNull()},
          "source":${source.jsonStringOrNull()},
          "properties":$properties
        }
    """.trimIndent()

    private fun String?.jsonStringOrNull(): String = this?.let { "\"$it\"" } ?: "null"

    private fun withSecondTerminalSession(dataset: ExportDatasetWireV1): ExportDatasetWireV1 {
        val sourceSession = dataset.sessions.single()
        val secondSession = SessionWireV1(
            StrictJsonObjectV1(
                JsonObject(sourceSession.body.element + ("session_id" to JsonPrimitive(SECOND_SESSION_ID))),
            ),
        )
        val sourceFeedback = dataset.feedback.single()
        val secondFeedback = FeedbackWireV1(
            StrictJsonObjectV1(
                JsonObject(
                    sourceFeedback.body.element + mapOf(
                        "session_id" to JsonPrimitive(SECOND_SESSION_ID),
                        "effort" to JsonNull,
                        "day_mode_cap_update_snapshot" to JsonNull,
                    ),
                ),
            ),
        )
        val secondEvents = listOf(
            ClosedCodecV1.decodeEvent(
                eventJson(
                    id = EVENT_SECOND_SESSION_START_ID,
                    name = "routine_started",
                    occurredAtUtc = STARTED_AT_UTC,
                    decisionId = DECISION_ID,
                    sessionId = SECOND_SESSION_ID,
                    scheduleVersionId = SCHEDULE_ID,
                    source = "home",
                    properties = """
                        {
                          "routine_id":"BUI-01",
                          "check_in_flow_id":"$CHECK_IN_FLOW_ID",
                          "runtime_effective_mode_at_start":"BUILD",
                          "is_selected_workday_at_start":true,
                          "start_boot_marker":1,
                          "start_elapsed_realtime_ms":20,
                          "start_clock_generation":4,
                          "start_wall_minus_elapsed_ms":5,
                          "total_duration_ms":1
                        }
                    """.trimIndent(),
                ),
            ),
            ClosedCodecV1.decodeEvent(
                eventJson(
                    id = EVENT_SECOND_SESSION_TERMINAL_ID,
                    name = "routine_stopped",
                    occurredAtUtc = TERMINAL_AT_UTC,
                    sessionId = SECOND_SESSION_ID,
                    properties = """{"elapsed_ms":1000,"pain_gate_status":"RESOLVED_NO"}""",
                ),
            ),
            ClosedCodecV1.decodeEvent(
                eventJson(
                    id = EVENT_SECOND_PAIN_RESOLVED_ID,
                    name = "pain_gate_resolved",
                    occurredAtUtc = TERMINAL_AT_UTC,
                    sessionId = SECOND_SESSION_ID,
                    properties = """
                        {
                          "terminal_state":"stopped",
                          "new_or_worse_pain":"no",
                          "pain_gate_status":"RESOLVED_NO",
                          "answered_at_or_after_origin_expiry":false
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val counts = dataset.metadata.recordCounts
        return dataset.copy(
            metadata = dataset.metadata.copy(
                recordCounts = counts.copy(
                    sessions = counts.sessions + 1,
                    feedback = counts.feedback + 1,
                    events = counts.events + secondEvents.size,
                ),
            ),
            sessions = dataset.sessions + secondSession,
            feedback = dataset.feedback + secondFeedback,
            events = sortEvents(dataset.events + secondEvents),
        )
    }

    private fun withStrictRecoverCap(
        dataset: ExportDatasetWireV1,
        commitAtUtc: String,
    ): ExportDatasetWireV1 {
        val feedback = dataset.feedback.single()
        val update = StrictJsonV1.parseObject(
            """
                {
                  "trigger_session_id":"$SESSION_ID",
                  "expiry_source_session_id":"$SESSION_ID",
                  "basis_mode":"MAINTAIN",
                  "previous_max_mode":"MAINTAIN",
                  "resulting_cap":${capSnapshot(SESSION_ID, SESSION_ID, "RECOVER")},
                  "deadline_source":"candidate_later"
                }
            """.trimIndent(),
        ).element
        val mutatedFeedback = FeedbackWireV1(
            StrictJsonObjectV1(
                JsonObject(feedback.body.element + ("day_mode_cap_update_snapshot" to update)),
            ),
        )
        val replacementEvents = listOf(
            feedbackUpdatedEvent(
                id = EVENT_FEEDBACK_UPDATED_ID,
                sessionId = SESSION_ID,
                occurredAtUtc = commitAtUtc,
            ),
            capUpdatedEvent(
                id = EVENT_CAP_UPDATED_ID,
                sessionId = SESSION_ID,
                occurredAtUtc = commitAtUtc,
                expirySourceSessionId = SESSION_ID,
                basisMode = "MAINTAIN",
                previousCap = "\"MAINTAIN\"",
                newCap = "RECOVER",
            ),
        )
        val retainedEvents = dataset.events.filterNot { event ->
            event.envelope.sessionId?.value == SESSION_ID &&
                event.name in setOf(
                    vn.nhip2phut.domain.events.EventNameV1.FEEDBACK_UPDATED,
                    vn.nhip2phut.domain.events.EventNameV1.DAY_MODE_CAP_UPDATED,
                )
        }
        return dataset.copy(
            feedback = listOf(mutatedFeedback),
            events = sortEvents(retainedEvents + replacementEvents),
        )
    }

    private fun withInheritedSecondCap(
        dataset: ExportDatasetWireV1,
        commitAtUtc: String,
    ): ExportDatasetWireV1 {
        val sourceSession = dataset.sessions.single()
        val secondSession = SessionWireV1(
            StrictJsonObjectV1(
                JsonObject(sourceSession.body.element + ("session_id" to JsonPrimitive(SECOND_SESSION_ID))),
            ),
        )
        val sourceFeedback = dataset.feedback.single()
        val inheritedUpdate = StrictJsonV1.parseObject(
            """
                {
                  "trigger_session_id":"$SECOND_SESSION_ID",
                  "expiry_source_session_id":"$SECOND_SESSION_ID",
                  "basis_mode":"RECOVER",
                  "previous_max_mode":"RECOVER",
                  "resulting_cap":${capSnapshot(SESSION_ID, SECOND_SESSION_ID, "RECOVER")},
                  "deadline_source":"candidate_later"
                }
            """.trimIndent(),
        ).element
        val secondFeedback = FeedbackWireV1(
            StrictJsonObjectV1(
                JsonObject(
                    sourceFeedback.body.element + mapOf(
                        "session_id" to JsonPrimitive(SECOND_SESSION_ID),
                        "day_mode_cap_update_snapshot" to inheritedUpdate,
                        "updated_at" to StrictJsonV1.parseObject(localStampJson(commitAtUtc)).element,
                    ),
                ),
            ),
        )
        val secondEvents = listOf(
            ClosedCodecV1.decodeEvent(
                eventJson(
                    id = EVENT_SECOND_SESSION_START_ID,
                    name = "routine_started",
                    occurredAtUtc = STARTED_AT_UTC,
                    decisionId = DECISION_ID,
                    sessionId = SECOND_SESSION_ID,
                    scheduleVersionId = SCHEDULE_ID,
                    source = "home",
                    properties = """
                        {
                          "routine_id":"BUI-01",
                          "check_in_flow_id":"$CHECK_IN_FLOW_ID",
                          "runtime_effective_mode_at_start":"BUILD",
                          "is_selected_workday_at_start":true,
                          "start_boot_marker":1,
                          "start_elapsed_realtime_ms":20,
                          "start_clock_generation":4,
                          "start_wall_minus_elapsed_ms":5,
                          "total_duration_ms":1
                        }
                    """.trimIndent(),
                ),
            ),
            ClosedCodecV1.decodeEvent(
                eventJson(
                    id = EVENT_SECOND_SESSION_TERMINAL_ID,
                    name = "routine_stopped",
                    occurredAtUtc = TERMINAL_AT_UTC,
                    sessionId = SECOND_SESSION_ID,
                    properties = """{"elapsed_ms":1000,"pain_gate_status":"RESOLVED_NO"}""",
                ),
            ),
            ClosedCodecV1.decodeEvent(
                eventJson(
                    id = EVENT_SECOND_PAIN_RESOLVED_ID,
                    name = "pain_gate_resolved",
                    occurredAtUtc = TERMINAL_AT_UTC,
                    sessionId = SECOND_SESSION_ID,
                    properties = """
                        {
                          "terminal_state":"stopped",
                          "new_or_worse_pain":"no",
                          "pain_gate_status":"RESOLVED_NO",
                          "answered_at_or_after_origin_expiry":false
                        }
                    """.trimIndent(),
                ),
            ),
            feedbackUpdatedEvent(
                id = EVENT_SECOND_FEEDBACK_UPDATED_ID,
                sessionId = SECOND_SESSION_ID,
                occurredAtUtc = commitAtUtc,
            ),
            capUpdatedEvent(
                id = EVENT_SECOND_CAP_UPDATED_ID,
                sessionId = SECOND_SESSION_ID,
                occurredAtUtc = commitAtUtc,
                expirySourceSessionId = SECOND_SESSION_ID,
                basisMode = "RECOVER",
                previousCap = "\"RECOVER\"",
                newCap = "RECOVER",
            ),
        )
        val counts = dataset.metadata.recordCounts
        return dataset.copy(
            metadata = dataset.metadata.copy(
                recordCounts = counts.copy(
                    sessions = counts.sessions + 1,
                    feedback = counts.feedback + 1,
                    events = counts.events + secondEvents.size,
                ),
            ),
            sessions = dataset.sessions + secondSession,
            feedback = dataset.feedback + secondFeedback,
            events = sortEvents(dataset.events + secondEvents),
        )
    }

    private fun feedbackUpdatedEvent(
        id: String,
        sessionId: String,
        occurredAtUtc: String,
    ) = ClosedCodecV1.decodeEvent(
        eventJson(
            id = id,
            name = "feedback_updated",
            occurredAtUtc = occurredAtUtc,
            sessionId = sessionId,
            properties = """
                {
                  "updated_fields":["effort"],
                  "terminal_state":"stopped",
                  "effort":"too_hard",
                  "context_fit":null,
                  "feedback_complete":false,
                  "cap_result":"applied"
                }
            """.trimIndent(),
        ),
    )

    private fun capUpdatedEvent(
        id: String,
        sessionId: String,
        occurredAtUtc: String,
        expirySourceSessionId: String,
        basisMode: String,
        previousCap: String,
        newCap: String,
        deadlineSource: String = "candidate_later",
    ) = ClosedCodecV1.decodeEvent(
        eventJson(
            id = id,
            name = "day_mode_cap_updated",
            occurredAtUtc = occurredAtUtc,
            sessionId = sessionId,
            properties = """
                {
                  "expiry_source_session_id":"$expirySourceSessionId",
                  "basis_mode":"$basisMode",
                  "previous_cap":$previousCap,
                  "new_cap":"$newCap",
                  "deadline_source":"$deadlineSource",
                  "origin_occurred_at_utc":"$TERMINAL_AT_UTC",
                  "origin_local_date":"2026-08-27",
                  "origin_timezone_id":"UTC",
                  "origin_utc_offset_minutes":0,
                  "expires_at_utc":"2026-08-28T00:00:00.000Z",
                  "rule_version":1
                }
            """.trimIndent(),
        ),
    )

    private fun withGraphCapUpdate(
        dataset: ExportDatasetWireV1,
        index: Int,
        fixture: CapMergeFixture,
    ): ExportDatasetWireV1 {
        val ownerSessionId = graphSessionId(index)
        val sourceSession = dataset.sessions.single { it.sessionId.value == fixture.expirySourceSessionId }
        val sourceSessionPath = "export.sessions[${fixture.expirySourceSessionId}]"
        val sourceClockEvidence = sourceSession.body.requiredElement(
            "session_origin_clock_integrity",
            sourceSessionPath,
        ).toString()
        val update = StrictJsonV1.parseObject(
            """
                {
                  "trigger_session_id":"$ownerSessionId",
                  "expiry_source_session_id":"${fixture.expirySourceSessionId}",
                  "basis_mode":"${fixture.basisMode}",
                  "previous_max_mode":${fixture.previousMaxMode.jsonStringOrNull()},
                  "resulting_cap":${capSnapshot(
                      fixture.modeTriggerSessionId,
                      fixture.expirySourceSessionId,
                      fixture.resultingMaxMode,
                      fixture.resultingClockEvidenceOverride ?: sourceClockEvidence,
                  )},
                  "deadline_source":"${fixture.deadlineSource}"
                }
            """.trimIndent(),
        ).element
        val feedback = dataset.feedback.map { retained ->
            if (retained.sessionId.value != ownerSessionId) {
                retained
            } else {
                FeedbackWireV1(
                    StrictJsonObjectV1(
                        JsonObject(
                            retained.body.element + mapOf(
                                "day_mode_cap_update_snapshot" to update,
                                "updated_at" to StrictJsonV1.parseObject(localStampJson(fixture.commitAtUtc)).element,
                            ),
                        ),
                    ),
                )
            }
        }
        val retainedEvents = dataset.events.filterNot { event ->
            event.envelope.sessionId?.value == ownerSessionId &&
                event.name in setOf(
                    vn.nhip2phut.domain.events.EventNameV1.FEEDBACK_UPDATED,
                    vn.nhip2phut.domain.events.EventNameV1.DAY_MODE_CAP_UPDATED,
                )
        }
        val replacementEvents = listOf(
            feedbackUpdatedEvent(
                id = graphEventId(index, 5),
                sessionId = ownerSessionId,
                occurredAtUtc = fixture.commitAtUtc,
            ),
            capUpdatedEvent(
                id = graphEventId(index, 6),
                sessionId = ownerSessionId,
                occurredAtUtc = fixture.commitAtUtc,
                expirySourceSessionId = fixture.expirySourceSessionId,
                basisMode = fixture.basisMode,
                previousCap = fixture.previousMaxMode.jsonStringOrNull(),
                newCap = fixture.resultingMaxMode,
                deadlineSource = fixture.deadlineSource,
            ),
        )
        return dataset.copy(
            feedback = feedback,
            events = sortEvents(retainedEvents + replacementEvents),
        )
    }

    private fun withGraphSessionDecision(
        dataset: ExportDatasetWireV1,
        sessionIndex: Int,
        decisionIndex: Int,
    ): ExportDatasetWireV1 {
        val sessionId = graphSessionId(sessionIndex)
        val decisionId = graphDecisionId(decisionIndex)
        val sessions = dataset.sessions.map { session ->
            if (session.sessionId.value != sessionId) {
                session
            } else {
                SessionWireV1(
                    StrictJsonObjectV1(
                        JsonObject(session.body.element + ("decision_id" to JsonPrimitive(decisionId))),
                    ),
                )
            }
        }
        val events = dataset.events.map { event ->
            if (event.name != vn.nhip2phut.domain.events.EventNameV1.ROUTINE_STARTED ||
                event.envelope.sessionId?.value != sessionId
            ) {
                event
            } else {
                event.copy(
                    envelope = event.envelope.copy(decisionId = UuidWireV1.parse(decisionId)),
                )
            }
        }
        return dataset.copy(sessions = sessions, events = events)
    }

    private fun capGraphDataset(
        decisionTargets: List<Int?>,
        sessionTargets: List<Int?>,
        capClockEvidenceBySession: List<String> = List(decisionTargets.size) { clockIntegrity },
    ): ExportDatasetWireV1 {
        require(decisionTargets.size == sessionTargets.size)
        require(decisionTargets.size == capClockEvidenceBySession.size)
        val nodeCount = decisionTargets.size
        val template = ClosedCodecV1.decodeExport(
            datasetJson(
                sessionStatus = "STOPPED",
                includeFeedback = true,
                capUpdateResultingSourceSessionId = SESSION_ID,
            ),
        )
        var decisionModes = List(nodeCount) { "BUILD" }
        var runtimeModes = decisionModes
        var feedbackResults = runtimeModes.map(::lowerCapMode)
        repeat(nodeCount + 3) {
            val nextDecisions = decisionTargets.map { target ->
                target?.let(feedbackResults::get) ?: "BUILD"
            }
            val nextRuntime = sessionTargets.mapIndexed { index, target ->
                target?.let { minMode(nextDecisions[index], feedbackResults[it]) } ?: nextDecisions[index]
            }
            val nextResults = nextRuntime.map(::lowerCapMode)
            if (nextDecisions == decisionModes && nextRuntime == runtimeModes && nextResults == feedbackResults) {
                return@repeat
            }
            decisionModes = nextDecisions
            runtimeModes = nextRuntime
            feedbackResults = nextResults
        }

        val terminalElement = StrictJsonV1.parseObject(terminalStamp).element
        val checkInTemplate = template.checkIns.single()
        val decisionTemplate = template.decisions.single()
        val sessionTemplate = template.sessions.single()
        val feedbackTemplate = template.feedback.single()
        val checkIns = List(nodeCount) { index ->
            CheckInWireV1(
                StrictJsonObjectV1(
                    JsonObject(
                        checkInTemplate.body.element + mapOf(
                            "check_in_id" to JsonPrimitive(graphCheckInId(index)),
                            "confirmed_at" to terminalElement,
                        ),
                    ),
                ),
            )
        }
        val decisions = List(nodeCount) { index ->
            val target = decisionTargets[index]
            val snapshot = target?.let {
                StrictJsonV1.parseObject(
                    capSnapshot(
                        graphSessionId(it),
                        graphSessionId(it),
                        feedbackResults[it],
                        capClockEvidenceBySession[it],
                    ),
                ).element
            } ?: JsonNull
            DecisionWireV1(
                StrictJsonObjectV1(
                    JsonObject(
                        decisionTemplate.body.element + mapOf(
                            "decision_id" to JsonPrimitive(graphDecisionId(index)),
                            "check_in_id" to JsonPrimitive(graphCheckInId(index)),
                            "effective_mode" to JsonPrimitive(decisionModes[index]),
                            "reason_codes" to JsonArray(
                                if (target == null) {
                                    listOf(JsonPrimitive("SAF_BUILD_CONDITIONS"))
                                } else {
                                    listOf(
                                        JsonPrimitive("SAF_BUILD_CONDITIONS"),
                                        JsonPrimitive("SAF_DAY_MODE_CAP_APPLIED"),
                                    )
                                },
                            ),
                            "evaluation_day_mode_cap_snapshot" to snapshot,
                            "created_at" to terminalElement,
                        ),
                    ),
                ),
            )
        }
        val sessions = List(nodeCount) { index ->
            val target = sessionTargets[index]
            val runtimeSnapshot = target?.let {
                StrictJsonV1.parseObject(
                    sessionRuntimeCapSnapshot(
                        appliedCap = capSnapshot(
                            graphSessionId(it),
                            graphSessionId(it),
                            feedbackResults[it],
                            capClockEvidenceBySession[it],
                        ),
                        decisionMode = decisionModes[index],
                        runtimeMode = runtimeModes[index],
                    ),
                ).element
            } ?: JsonNull
            SessionWireV1(
                StrictJsonObjectV1(
                    JsonObject(
                        sessionTemplate.body.element + mapOf(
                            "session_id" to JsonPrimitive(graphSessionId(index)),
                            "decision_id" to JsonPrimitive(graphDecisionId(index)),
                            "routine_id" to JsonPrimitive(routineIdForMode(runtimeModes[index])),
                            "routine_mode" to JsonPrimitive(runtimeModes[index]),
                            "decision_effective_mode_at_start" to JsonPrimitive(decisionModes[index]),
                            "runtime_effective_mode_at_start" to JsonPrimitive(runtimeModes[index]),
                            "runtime_day_mode_cap_snapshot_at_start" to runtimeSnapshot,
                            "started_at" to terminalElement,
                            "session_origin_clock_integrity" to StrictJsonV1.parseObject(
                                capClockEvidenceBySession[index],
                            ).element,
                        ),
                    ),
                ),
            )
        }
        val feedback = List(nodeCount) { index ->
            val update = StrictJsonV1.parseObject(
                """
                    {
                      "trigger_session_id":"${graphSessionId(index)}",
                      "expiry_source_session_id":"${graphSessionId(index)}",
                      "basis_mode":"${runtimeModes[index]}",
                      "previous_max_mode":null,
                      "resulting_cap":${capSnapshot(
                          graphSessionId(index),
                          graphSessionId(index),
                          feedbackResults[index],
                          capClockEvidenceBySession[index],
                      )},
                      "deadline_source":"candidate_later"
                    }
                """.trimIndent(),
            ).element
            FeedbackWireV1(
                StrictJsonObjectV1(
                    JsonObject(
                        feedbackTemplate.body.element + mapOf(
                            "session_id" to JsonPrimitive(graphSessionId(index)),
                            "day_mode_cap_update_snapshot" to update,
                        ),
                    ),
                ),
            )
        }
        val graphEvents = buildList {
            addAll(
                template.events.filter {
                    it.name in setOf(
                        vn.nhip2phut.domain.events.EventNameV1.SCOPE_ACKNOWLEDGED,
                        vn.nhip2phut.domain.events.EventNameV1.ONBOARDING_COMPLETED,
                    )
                },
            )
            repeat(nodeCount) { index ->
                addAll(
                    capGraphNodeEvents(
                        index = index,
                        decisionMode = decisionModes[index],
                        runtimeMode = runtimeModes[index],
                        resultingCap = feedbackResults[index],
                        capAppliedToDecision = decisionTargets[index] != null,
                    ),
                )
            }
        }
        val counts = template.metadata.recordCounts
        return template.copy(
            metadata = template.metadata.copy(
                recordCounts = counts.copy(
                    checkIns = nodeCount.toLong(),
                    decisions = nodeCount.toLong(),
                    sessions = nodeCount.toLong(),
                    feedback = nodeCount.toLong(),
                    events = graphEvents.size.toLong(),
                ),
            ),
            checkIns = checkIns,
            decisions = decisions,
            sessions = sessions,
            feedback = feedback,
            events = sortEvents(graphEvents),
        )
    }

    private fun capGraphNodeEvents(
        index: Int,
        decisionMode: String,
        runtimeMode: String,
        resultingCap: String,
        capAppliedToDecision: Boolean,
    ) = listOf(
        ClosedCodecV1.decodeEvent(
            eventJson(
                id = graphEventId(index, 0),
                name = "check_in_submitted",
                occurredAtUtc = TERMINAL_AT_UTC,
                scheduleVersionId = SCHEDULE_ID,
                properties = """
                    {
                      "check_in_flow_id":"${graphFlowId(index)}",
                      "check_in_id":"${graphCheckInId(index)}",
                      "kind":"new",
                      "answers_kind":"full",
                      "duration_ms":1
                    }
                """.trimIndent(),
            ),
        ),
        ClosedCodecV1.decodeEvent(
            eventJson(
                id = graphEventId(index, 1),
                name = "decision_evaluated",
                occurredAtUtc = TERMINAL_AT_UTC,
                decisionId = graphDecisionId(index),
                scheduleVersionId = SCHEDULE_ID,
                properties = """
                    {
                      "check_in_id":"${graphCheckInId(index)}",
                      "result":"BUILD",
                      "base_mode":"BUILD",
                      "effective_mode":"$decisionMode",
                      "reason_codes":${if (capAppliedToDecision) "[\"SAF_BUILD_CONDITIONS\",\"SAF_DAY_MODE_CAP_APPLIED\"]" else "[\"SAF_BUILD_CONDITIONS\"]"},
                      "invalid_fields":[],
                      "rule_version":1,
                      "cap_applied":$capAppliedToDecision
                    }
                """.trimIndent(),
            ),
        ),
        ClosedCodecV1.decodeEvent(
            eventJson(
                id = graphEventId(index, 2),
                name = "routine_started",
                occurredAtUtc = TERMINAL_AT_UTC,
                decisionId = graphDecisionId(index),
                sessionId = graphSessionId(index),
                scheduleVersionId = SCHEDULE_ID,
                source = "home",
                properties = """
                    {
                      "routine_id":"${routineIdForMode(runtimeMode)}",
                      "check_in_flow_id":"${graphFlowId(index)}",
                      "runtime_effective_mode_at_start":"$runtimeMode",
                      "is_selected_workday_at_start":true,
                      "start_boot_marker":1,
                      "start_elapsed_realtime_ms":20,
                      "start_clock_generation":4,
                      "start_wall_minus_elapsed_ms":5,
                      "total_duration_ms":1
                    }
                """.trimIndent(),
            ),
        ),
        ClosedCodecV1.decodeEvent(
            eventJson(
                id = graphEventId(index, 3),
                name = "routine_stopped",
                occurredAtUtc = TERMINAL_AT_UTC,
                sessionId = graphSessionId(index),
                properties = """{"elapsed_ms":1000,"pain_gate_status":"RESOLVED_NO"}""",
            ),
        ),
        ClosedCodecV1.decodeEvent(
            eventJson(
                id = graphEventId(index, 4),
                name = "pain_gate_resolved",
                occurredAtUtc = TERMINAL_AT_UTC,
                sessionId = graphSessionId(index),
                properties = """
                    {
                      "terminal_state":"stopped",
                      "new_or_worse_pain":"no",
                      "pain_gate_status":"RESOLVED_NO",
                      "answered_at_or_after_origin_expiry":false
                    }
                """.trimIndent(),
            ),
        ),
        feedbackUpdatedEvent(
            id = graphEventId(index, 5),
            sessionId = graphSessionId(index),
            occurredAtUtc = TERMINAL_AT_UTC,
        ),
        capUpdatedEvent(
            id = graphEventId(index, 6),
            sessionId = graphSessionId(index),
            occurredAtUtc = TERMINAL_AT_UTC,
            expirySourceSessionId = graphSessionId(index),
            basisMode = runtimeMode,
            previousCap = "null",
            newCap = resultingCap,
        ),
    )

    private fun lowerCapMode(mode: String): String = if (mode == "BUILD") "MAINTAIN" else "RECOVER"

    private fun minMode(left: String, right: String): String {
        val rank = listOf("RECOVER", "MAINTAIN", "BUILD")
        return if (rank.indexOf(left) <= rank.indexOf(right)) left else right
    }

    private fun routineIdForMode(mode: String): String = when (mode) {
        "BUILD" -> "BUI-01"
        "MAINTAIN" -> "MAI-01"
        else -> "REC-01"
    }

    private fun graphUuid(suffix: Int): String =
        "00000000-0000-4000-8000-${suffix.toString().padStart(12, '0')}"

    private fun graphCheckInId(index: Int) = graphUuid(2_000 + index)
    private fun graphDecisionId(index: Int) = graphUuid(3_000 + index)
    private fun graphSessionId(index: Int) = graphUuid(4_000 + index)
    private fun graphFlowId(index: Int) = graphUuid(5_000 + index)
    private fun graphEventId(index: Int, slot: Int) = graphUuid(6_000 + index * 10 + slot)

    private fun withSecondDecision(dataset: ExportDatasetWireV1): ExportDatasetWireV1 {
        val sourceCheckIn = dataset.checkIns.single()
        val secondCheckIn = CheckInWireV1(
            StrictJsonObjectV1(
                JsonObject(sourceCheckIn.body.element + ("check_in_id" to JsonPrimitive(SECOND_CHECK_IN_ID))),
            ),
        )
        val sourceDecision = dataset.decisions.single()
        val secondDecision = DecisionWireV1(
            StrictJsonObjectV1(
                JsonObject(
                    sourceDecision.body.element + mapOf(
                        "decision_id" to JsonPrimitive(SECOND_DECISION_ID),
                        "check_in_id" to JsonPrimitive(SECOND_CHECK_IN_ID),
                    ),
                ),
            ),
        )
        val secondDecisionEvents = listOf(
            ClosedCodecV1.decodeEvent(
                eventJson(
                    id = EVENT_SECOND_CHECK_IN_ID,
                    name = "check_in_submitted",
                    occurredAtUtc = STARTED_AT_UTC,
                    scheduleVersionId = SCHEDULE_ID,
                    properties = """
                        {
                          "check_in_flow_id":"$SECOND_CHECK_IN_FLOW_ID",
                          "check_in_id":"$SECOND_CHECK_IN_ID",
                          "kind":"new",
                          "answers_kind":"full",
                          "duration_ms":1
                        }
                    """.trimIndent(),
                ),
            ),
            ClosedCodecV1.decodeEvent(
                eventJson(
                    id = EVENT_SECOND_DECISION_ID,
                    name = "decision_evaluated",
                    occurredAtUtc = STARTED_AT_UTC,
                    decisionId = SECOND_DECISION_ID,
                    scheduleVersionId = SCHEDULE_ID,
                    properties = """
                        {
                          "check_in_id":"$SECOND_CHECK_IN_ID",
                          "result":"BUILD",
                          "base_mode":"BUILD",
                          "effective_mode":"BUILD",
                          "reason_codes":["SAF_BUILD_CONDITIONS"],
                          "invalid_fields":[],
                          "rule_version":1,
                          "cap_applied":false
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val counts = dataset.metadata.recordCounts
        return dataset.copy(
            metadata = dataset.metadata.copy(
                recordCounts = counts.copy(
                    checkIns = counts.checkIns + 1,
                    decisions = counts.decisions + 1,
                    events = counts.events + secondDecisionEvents.size,
                ),
            ),
            checkIns = dataset.checkIns + secondCheckIn,
            decisions = dataset.decisions + secondDecision,
            events = sortEvents(dataset.events + secondDecisionEvents),
        )
    }

    private fun sortEvents(events: List<vn.nhip2phut.domain.events.ProductEventWireV1>) =
        events.sortedWith { left, right ->
            val instantOrder = left.envelope.occurred.occurredAtUtc.compareTo(right.envelope.occurred.occurredAtUtc)
            if (instantOrder != 0) instantOrder else left.envelope.eventId.compareTo(right.envelope.eventId)
        }

    private fun decisionJson(
        freshness: Freshness,
        effectiveMode: String,
        reasonCodes: List<String>,
        capSnapshot: String,
    ): String = """
        {
          "decision_id":"$DECISION_ID",
          "check_in_id":"$CHECK_IN_ID",
          "schedule_version_id":"$SCHEDULE_ID",
          "rule_version":1,
          "outcome":"BUILD",
          "base_mode":"BUILD",
          "effective_mode":"$effectiveMode",
          "reason_codes":${reasonCodes.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\"")},
          "invalid_fields":[],
          "created_safety_hold_snapshot":null,
          "created_rest_suppression_snapshot":null,
          "evaluation_day_mode_cap_snapshot":$capSnapshot,
          "created_at":$stamp,
          "reconfirm_after":"2026-08-27T16:00:00.000Z",
          "valid_until_work_end":"2026-08-27T17:00:00.000Z",
          "confirmed_boot_marker":${freshness.bootMarker},
          "confirmed_elapsed_realtime_ms":${freshness.elapsedRealtimeMs},
          "ttl_monotonic_deadline_ms":${freshness.ttlDeadlineMs},
          "confirmed_clock_generation":${freshness.clockGeneration},
          "confirmed_zone_id":"${freshness.zoneId}",
          "confirmed_wall_minus_elapsed_ms":${freshness.wallMinusElapsedMs}
        }
    """.trimIndent()

    private fun sessionJson(
        status: String,
        routineId: String,
        routineMode: String,
        decisionMode: String,
        runtimeMode: String,
        runtimeCapSnapshot: String,
    ): String {
        val isActive = status == "ACTIVE"
        val checkpoint = when (status) {
            "ACTIVE" -> activeCheckpoint
            "COMPLETED" -> completedCheckpoint
            else -> stoppedOrAbandonedCheckpoint
        }
        return """
            {
              "session_id":"$SESSION_ID",
              "decision_id":"$DECISION_ID",
              "schedule_version_id":"$SCHEDULE_ID",
              "routine_id":"$routineId",
              "content_identity":$contentIdentity,
              "routine_mode":"$routineMode",
              "decision_effective_mode_at_start":"$decisionMode",
              "runtime_effective_mode_at_start":"$runtimeMode",
              "runtime_day_mode_cap_snapshot_at_start":$runtimeCapSnapshot,
              "source":"home",
              "reminder_occurrence_id":null,
              "is_selected_workday_at_start":true,
              "started_at":$stamp,
              "start_boot_marker":1,
              "start_elapsed_realtime_ms":20,
              "start_clock_generation":4,
              "start_wall_minus_elapsed_ms":5,
              "status":"$status",
              "player_checkpoint":$checkpoint,
              "terminal_at":${if (isActive) "null" else terminalStamp},
              "session_origin_day_expires_at_utc":${if (isActive) "null" else "\"2026-08-28T00:00:00.000Z\""},
              "session_origin_clock_integrity":${if (isActive) "null" else clockIntegrity},
              "completion_boot_marker":${if (isActive) "null" else "1"},
              "completion_elapsed_realtime_ms":${if (isActive) "null" else "1000"},
              "completion_clock_generation":${if (isActive) "null" else "4"},
              "completion_wall_minus_elapsed_ms":${if (isActive) "null" else "5"}
            }
        """.trimIndent()
    }

    private data class Freshness(
        val bootMarker: Long = 1,
        val elapsedRealtimeMs: Long = 2,
        val ttlDeadlineMs: Long = 3,
        val clockGeneration: Long = 4,
        val zoneId: String = "UTC",
        val wallMinusElapsedMs: Long = 5,
    )

    private data class CapMergeFixture(
        val basisMode: String,
        val previousMaxMode: String?,
        val resultingMaxMode: String,
        val modeTriggerSessionId: String,
        val expirySourceSessionId: String,
        val deadlineSource: String,
        val commitAtUtc: String,
        val resultingClockEvidenceOverride: String? = null,
    )

    private val stamp = """{"occurred_at_utc":"2026-08-27T10:00:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""
    private val terminalStamp = """{"occurred_at_utc":"2026-08-27T10:01:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""
    private val contentIdentity = """{"schema_version":"1.0.0","content_version":"1.0.0","routine_revision":"1.0.0","manifest_digest_sha256":"0000000000000000000000000000000000000000000000000000000000000000"}"""
    private val activeCheckpoint = """{"substate":"PLAYING","phase":"STEP_TIMER","step_index":0,"current_step_remaining_ms":1000,"transition_remaining_ms":0,"accumulated_active_ms":0,"skipped_steps":[],"segment_started_elapsed_realtime_ms":20,"last_checkpoint_elapsed_realtime_ms":20,"boot_marker":1,"last_announced_cadence_ordinal":0,"content_identity":$contentIdentity}"""
    private val completedCheckpoint = """{"substate":null,"phase":"COMPLETION_CTA_WAIT","step_index":0,"current_step_remaining_ms":0,"transition_remaining_ms":0,"accumulated_active_ms":1000,"skipped_steps":[],"segment_started_elapsed_realtime_ms":null,"last_checkpoint_elapsed_realtime_ms":1000,"boot_marker":1,"last_announced_cadence_ordinal":1,"content_identity":$contentIdentity}"""
    private val stoppedOrAbandonedCheckpoint = """{"substate":"PAUSED","phase":"STEP_TIMER","step_index":0,"current_step_remaining_ms":1000,"transition_remaining_ms":0,"accumulated_active_ms":1000,"skipped_steps":[],"segment_started_elapsed_realtime_ms":null,"last_checkpoint_elapsed_realtime_ms":1000,"boot_marker":1,"last_announced_cadence_ordinal":1,"content_identity":$contentIdentity}"""
    private val clockIntegrity = """{"origin_boot_marker":1,"created_elapsed_realtime_ms":20,"monotonic_deadline_ms":1000,"remaining_elapsed_ms_at_last_checkpoint":980,"original_duration_ms":980}"""
    private val decisionSafetyClockIntegrity = """{"origin_boot_marker":1,"created_elapsed_realtime_ms":2,"monotonic_deadline_ms":3,"remaining_elapsed_ms_at_last_checkpoint":1,"original_duration_ms":1}"""
    private val profileJson = """
        {
          "installation_id":"00000000-0000-4000-8000-000000000001",
          "adult_confirmed":true,
          "eligibility_scope_confirmed":true,
          "locale":"vi-VN",
          "onboarding_completed_at":$stamp,
          "activation_boot_marker":1,
          "activation_elapsed_realtime_ms":2,
          "activation_clock_generation":4,
          "activation_wall_minus_elapsed_ms":5,
          "safety_acknowledgements":[{
            "acknowledgement_id":"00000000-0000-4000-8000-000000000002",
            "kind":"onboarding",
            "content_version":"1.0.0",
            "content_digest":"0000000000000000000000000000000000000000000000000000000000000000",
            "acknowledged_at":$stamp
          }],
          "current_safety_acknowledgement_id":"00000000-0000-4000-8000-000000000002"
        }
    """.trimIndent()
    private val scheduleJson = """
        {
          "schedule_version_id":"$SCHEDULE_ID",
          "enabled":true,
          "selected_weekdays":[4],
          "work_start":"09:00",
          "work_end":"17:00",
          "reminder_times":["10:30"],
          "effective_from":$stamp,
          "replaced_at":null
        }
    """.trimIndent()
    private val checkInJson = """
        {
          "check_in_id":"$CHECK_IN_ID",
          "parent_id":null,
          "schedule_version_id":"$SCHEDULE_ID",
          "rule_version":1,
          "answers_kind":"full",
          "red_flag":false,
          "acute_issue":"none",
          "energy":"good",
          "stiffness":"none",
          "intent":"moderate",
          "confirmed_at":$stamp,
          "confirmed_boot_marker":1,
          "confirmed_elapsed_realtime_ms":2,
          "ttl_monotonic_deadline_ms":3,
          "confirmed_clock_generation":4,
          "confirmed_zone_id":"UTC",
          "confirmed_wall_minus_elapsed_ms":5
        }
    """.trimIndent()
    private fun feedbackJson(
        sessionStatus: String,
        capUpdateResultingSourceSessionId: String?,
        capUpdateBasisMode: String,
        capUpdateResultingMaxMode: String,
        capUpdateOccurredAtUtc: String,
        feedbackContextFit: String?,
    ): String {
        val isStopped = sessionStatus == "STOPPED"
        val capUpdate = capUpdateResultingSourceSessionId?.let { resultingSource ->
            """
                {
                  "trigger_session_id":"$SESSION_ID",
                  "expiry_source_session_id":"$SESSION_ID",
                  "basis_mode":"$capUpdateBasisMode",
                  "previous_max_mode":null,
                  "resulting_cap":${capSnapshot(SESSION_ID, resultingSource, capUpdateResultingMaxMode)},
                  "deadline_source":"candidate_later"
                }
            """.trimIndent()
        } ?: "null"
        return """
            {
              "session_id":"$SESSION_ID",
              "pain_gate_status":"${if (isStopped) "resolved_no" else "pending"}",
              "new_or_worse_pain":${if (isStopped) "\"no\"" else "null"},
              "pain_answered_at":${if (isStopped) terminalStamp else "null"},
              "effort":${if (capUpdateResultingSourceSessionId == null) "null" else "\"too_hard\""},
              "context_fit":${feedbackContextFit?.let { "\"$it\"" } ?: "null"},
              "created_post_session_safety_hold_snapshot":null,
              "day_mode_cap_update_snapshot":$capUpdate,
              "updated_at":${if (capUpdateResultingSourceSessionId == null) terminalStamp else localStampJson(capUpdateOccurredAtUtc)}
            }
        """.trimIndent()
    }

    private data class RecommendationFixture(
        val decisionEffectiveMode: String,
        val runtimeEffectiveMode: String,
        val capApplied: Boolean,
        val runtimeCapSnapshot: String,
    )

    private val buildProjection = RecommendationFixture(
        decisionEffectiveMode = "BUILD",
        runtimeEffectiveMode = "BUILD",
        capApplied = false,
        runtimeCapSnapshot = "null",
    )

    private data class WeeklySummaryFixture(
        val startedCount: Long,
        val contextRate: String,
    )

    private fun weeklySummaryJson(fixture: WeeklySummaryFixture): String = """
        {
          "summary_id":"$SUMMARY_ID",
          "week_start_local_date":"2026-08-24",
          "week_zone_id":"UTC",
          "occurred_at_utc":"$SUMMARY_AT_UTC",
          "local_date":"2026-08-27",
          "zone_id":"UTC",
          "utc_offset_minutes":0,
          "qualified_break_days":0,
          "started_count":${fixture.startedCount},
          "completed_count":0,
          "effort_easy_count":0,
          "effort_moderate_count":0,
          "effort_too_hard_count":0,
          "pain_yes_count":0,
          "pain_no_count":1,
          "context_yes_count":1,
          "context_no_count":0,
          "reminder_opened_count":0,
          "reminder_snoozed_count":0,
          "reminder_dismissed_count":0,
          "completion_rate":${suppressedRate(0, fixture.startedCount)},
          "context_fit_rate":${fixture.contextRate},
          "new_or_worse_pain_rate":${suppressedRate(0, 1)}
        }
    """.trimIndent()

    private fun suppressedRate(numerator: Long, denominator: Long): String =
        """{"numerator":$numerator,"denominator":$denominator,"value_percent":null,"suppression_reason":"insufficient_sample"}"""

    private fun capSnapshot(
        modeTriggerSessionId: String,
        sourceSessionId: String,
        maxMode: String = "MAINTAIN",
        clockEvidence: String = clockIntegrity,
    ): String =
        """{"occurred_at_utc":"$TERMINAL_AT_UTC","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,"max_mode":"$maxMode","mode_trigger_session_id":"$modeTriggerSessionId","source_session_id":"$sourceSessionId","expires_at_utc":"2026-08-28T00:00:00.000Z","clock_integrity":$clockEvidence,"rule_version":1}"""

    private fun capClockEvidence(
        originBootMarker: Long = 1,
        createdElapsedRealtimeMs: Long = 20,
        monotonicDeadlineMs: Long,
    ): String {
        require(monotonicDeadlineMs >= createdElapsedRealtimeMs)
        val duration = monotonicDeadlineMs - createdElapsedRealtimeMs
        return """{"origin_boot_marker":$originBootMarker,"created_elapsed_realtime_ms":$createdElapsedRealtimeMs,"monotonic_deadline_ms":$monotonicDeadlineMs,"remaining_elapsed_ms_at_last_checkpoint":$duration,"original_duration_ms":$duration}"""
    }

    private fun sessionRuntimeCapSnapshot(
        appliedCap: String,
        decisionMode: String,
        runtimeMode: String,
    ): String =
        """{"applied_cap":$appliedCap,"decision_effective_mode_before_runtime_cap":"$decisionMode","runtime_effective_mode_at_start":"$runtimeMode"}"""

    private fun localStampJson(occurredAtUtc: String): String =
        """{"occurred_at_utc":"$occurredAtUtc","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""

    companion object {
        private const val STARTED_AT_UTC = "2026-08-27T10:00:00.000Z"
        private const val TERMINAL_AT_UTC = "2026-08-27T10:01:00.000Z"
        private const val PROJECTION_AT_UTC = "2026-08-27T10:01:30.000Z"
        private const val INHERITED_COMMIT_AT_UTC = "2026-08-27T10:01:10.000Z"
        private const val INHERITED_CONSUMER_AT_UTC = "2026-08-27T10:01:20.000Z"
        private const val MODE_TRIGGER_COMMIT_AT_UTC = "2026-08-27T10:01:30.000Z"
        private const val LATE_CAP_AT_UTC = "2026-08-27T10:02:00.000Z"
        private const val SUMMARY_AT_UTC = "2026-08-27T10:02:00.000Z"
        private const val SCHEDULE_ID = "00000000-0000-4000-8000-000000000010"
        private const val CHECK_IN_ID = "00000000-0000-4000-8000-000000000020"
        private const val SECOND_CHECK_IN_ID = "00000000-0000-4000-8000-000000000021"
        private const val DECISION_ID = "00000000-0000-4000-8000-000000000030"
        private const val SECOND_DECISION_ID = "00000000-0000-4000-8000-000000000031"
        private const val SESSION_ID = "00000000-0000-4000-8000-000000000040"
        private const val SECOND_SESSION_ID = "00000000-0000-4000-8000-000000000041"
        private const val CHECK_IN_FLOW_ID = "00000000-0000-4000-8000-000000000050"
        private const val SECOND_CHECK_IN_FLOW_ID = "00000000-0000-4000-8000-000000000051"
        private const val EVENT_ACK_ID = "00000000-0000-4000-8000-000000000100"
        private const val EVENT_ONBOARDING_ID = "00000000-0000-4000-8000-000000000101"
        private const val EVENT_CHECK_IN_ID = "00000000-0000-4000-8000-000000000102"
        private const val EVENT_DECISION_ID = "00000000-0000-4000-8000-000000000103"
        private const val EVENT_SESSION_START_ID = "00000000-0000-4000-8000-000000000104"
        private const val EVENT_SESSION_TERMINAL_ID = "00000000-0000-4000-8000-000000000105"
        private const val EVENT_PAIN_RESOLVED_ID = "00000000-0000-4000-8000-000000000106"
        private const val EVENT_PROJECTION_ID = "00000000-0000-4000-8000-000000000107"
        private const val EVENT_FEEDBACK_UPDATED_ID = "00000000-0000-4000-8000-000000000108"
        private const val EVENT_CAP_UPDATED_ID = "00000000-0000-4000-8000-000000000109"
        private const val DANGLING_SESSION_ID = "00000000-0000-4000-8000-000000000099"
        private const val SUMMARY_ID = "00000000-0000-4000-8000-000000000060"
        private const val EVENT_WEEKLY_SUMMARY_ID = "00000000-0000-4000-8000-000000000110"
        private const val EVENT_SECOND_SESSION_START_ID = "00000000-0000-4000-8000-000000000120"
        private const val EVENT_SECOND_SESSION_TERMINAL_ID = "00000000-0000-4000-8000-000000000121"
        private const val EVENT_SECOND_PAIN_RESOLVED_ID = "00000000-0000-4000-8000-000000000122"
        private const val EVENT_SPLICED_PROJECTION_ID = "00000000-0000-4000-8000-000000000123"
        private const val EVENT_SECOND_CHECK_IN_ID = "00000000-0000-4000-8000-000000000124"
        private const val EVENT_SECOND_DECISION_ID = "00000000-0000-4000-8000-000000000125"
        private const val EVENT_FUTURE_CAP_PROJECTION_ID = "00000000-0000-4000-8000-000000000126"
        private const val EVENT_VALID_PRIOR_CAP_PROJECTION_ID = "00000000-0000-4000-8000-000000000127"
        private const val EVENT_SECOND_FEEDBACK_UPDATED_ID = "00000000-0000-4000-8000-000000000128"
        private const val EVENT_SECOND_CAP_UPDATED_ID = "00000000-0000-4000-8000-000000000129"
        private const val EVENT_INHERITED_CAP_PROJECTION_ID = "00000000-0000-4000-8000-000000000130"
        private const val EVENT_SELECTION_ID = "00000000-0000-4000-8000-000000000131"
        private const val EVENT_SELECTION_BEFORE_RECOMMENDATION_ID = "00000000-0000-4000-8000-000000000139"
        private const val EVENT_SAME_INSTANT_RECOMMENDATION_ID = "00000000-0000-4000-8000-000000000140"
        private const val EVENT_SELECTION_AFTER_RECOMMENDATION_ID = "00000000-0000-4000-8000-000000000141"
        private const val EVENT_LATEST_RECOMMENDATION_ID = "00000000-0000-4000-8000-000000000142"
        private const val EVENT_IMMEDIATE_SAFETY_HOLD_ID = "00000000-0000-4000-8000-000000000143"
        private const val EVENT_IMMEDIATE_SAFETY_SCREEN_ID = "00000000-0000-4000-8000-000000000144"
        private const val EVENT_MODE_DECISION_SAFETY_SCREEN_ID = "00000000-0000-4000-8000-000000000145"
        private const val EVENT_POST_SESSION_SAFETY_HOLD_ID = "00000000-0000-4000-8000-000000000146"
        private const val EVENT_POST_SESSION_SAFETY_SCREEN_ID = "00000000-0000-4000-8000-000000000147"
    }
}
