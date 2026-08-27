package vn.nhip2phut.domain.wire.v1

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import vn.nhip2phut.domain.events.CompanionRolesV1
import vn.nhip2phut.domain.events.CompanionSourceTypeV1
import vn.nhip2phut.domain.events.EventCompanionClaimResolverV1
import vn.nhip2phut.domain.events.EventNameV1
import vn.nhip2phut.domain.events.NormalizedCompanionClaimV1
import vn.nhip2phut.domain.events.ProductEventWireV1
import kotlin.math.abs

/** Reconstructs the closed reverse companion graph from source records and typed event claims. */
internal object CompanionConformanceV1 {
    fun requireValid(dataset: ExportDatasetWireV1) {
        if (dataset.profile.isEmpty()) return

        validateStandaloneEventMirrors(dataset)
        val ledger = ClaimLedger(buildClaims(dataset))
        validateProfile(dataset, ledger)
        validateCheckIns(dataset, ledger)
        validateDecisions(dataset, ledger)
        validateSessions(dataset, ledger)
        validateReminders(dataset, ledger)
        validateWeeklySummaries(dataset, ledger)
        ledger.requireFullyConsumed()
    }

    private fun validateStandaloneEventMirrors(dataset: ExportDatasetWireV1) {
        val schedulesById = dataset.workSchedule.associateBy { it.scheduleVersionId.value }
        val summariesById = dataset.weeklySummaries.associateBy { it.summaryId.value }
        val decisionsById = dataset.decisions.associateBy { it.decisionId.value }
        val sessionsById = dataset.sessions.associateBy { it.sessionId.value }
        val feedbackBySessionId = dataset.feedback.associateBy { it.sessionId.value }
        val acknowledgedSafetyDigests = dataset.profile.single().body
            .requiredElement("safety_acknowledgements", "export.profile[0]")
            .asArray("export.profile[0].safety_acknowledgements")
            .mapIndexedTo(hashSetOf()) { index, acknowledgement ->
                acknowledgement.asStrictObject("export.profile[0].safety_acknowledgements[$index]")
                    .requiredString("content_digest", "export.profile[0].safety_acknowledgements[$index]")
            }
        dataset.events.forEachIndexed { index, event ->
            val path = "export.events[$index]"
            when (event.name) {
                EventNameV1.WORK_SCHEDULE_SAVED -> {
                    val scheduleId = event.envelope.scheduleVersionId?.value
                        ?: fail(path, "work_schedule_saved is missing schedule_version_id")
                    val schedule = schedulesById[scheduleId]
                        ?: fail(path, "work_schedule_saved schedule_version_id does not resolve")
                    mirror(schedule.body, event.properties.body, path, "enabled", "work_start", "work_end")
                    requireProperty(
                        event,
                        "selected_weekday_count",
                        schedule.body.requiredElement("selected_weekdays", path).asArray("$path.selected_weekdays").size.toLong(),
                        path,
                    )
                    requireProperty(
                        event,
                        "reminder_count",
                        schedule.body.requiredElement("reminder_times", path).asArray("$path.reminder_times").size.toLong(),
                        path,
                    )
                }
                EventNameV1.WEEKLY_SUMMARY_VIEWED -> {
                    val summaryId = event.properties.body.requiredString("summary_id", path)
                    val summary = summariesById[summaryId]
                        ?: fail(path, "weekly_summary_viewed summary_id does not resolve")
                    requireProperty(
                        event,
                        "week_start_local_date",
                        summary.body.requiredString("week_start_local_date", path),
                        path,
                    )
                }
                EventNameV1.SAFETY_SCREEN_SHOWN -> validateSafetyScreenShown(
                    event = event,
                    decisionsById = decisionsById,
                    sessionsById = sessionsById,
                    feedbackBySessionId = feedbackBySessionId,
                    acknowledgedSafetyDigests = acknowledgedSafetyDigests,
                    path = path,
                )
                else -> Unit
            }
        }
        validateRetainedScheduleSaveLineage(dataset, schedulesById)
    }

    private fun validateRetainedScheduleSaveLineage(
        dataset: ExportDatasetWireV1,
        schedulesById: Map<String, WorkScheduleWireV1>,
    ) {
        val saves = dataset.events.filter { it.name == EventNameV1.WORK_SCHEDULE_SAVED }
        val onboardingSaves = saves.filter {
            it.properties.body.requiredString("change_source", "work_schedule_saved.properties") == "onboarding"
        }
        if (onboardingSaves.size > 1) {
            fail("export.events", "at most one retained onboarding work_schedule_saved is permitted")
        }
        val profileOnboardingStamp = dataset.profile.singleOrNull()?.body
            ?.requiredElement("onboarding_completed_at", "export.profile[0]")
            ?.let { flatOrNestedStamp(it, "export.profile[0].onboarding_completed_at") }

        val successorByPredecessor = LinkedHashMap<String, String>()
        saves.forEach { event ->
            val currentId = event.envelope.scheduleVersionId?.value
                ?: fail("export.events", "work_schedule_saved is missing schedule_version_id")
            val path = "work_schedule_saved[$currentId]"
            val current = schedulesById[currentId] ?: fail(path, "current schedule does not resolve")
            val currentPath = "export.work_schedule[$currentId]"
            val currentEffectiveFrom = flatOrNestedStamp(
                current.body.requiredElement("effective_from", currentPath),
                "$currentPath.effective_from",
            )
            if (event.envelope.occurred != currentEffectiveFrom) {
                fail(path, "work_schedule_saved and current effective_from must share the transaction LocalStamp")
            }

            val previousId = event.properties.body.nullableString("previous_schedule_version_id", "$path.properties")
            if (previousId == currentId) {
                fail(path, "previous_schedule_version_id cannot reference the current schedule")
            }
            when (event.properties.body.requiredString("change_source", "$path.properties")) {
                "settings" -> {
                    val predecessorId = previousId ?: fail(path, "settings save requires previous_schedule_version_id")
                    val predecessor = schedulesById[predecessorId]
                        ?: fail(path, "previous_schedule_version_id does not resolve")
                    val predecessorPath = "export.work_schedule[$predecessorId]"
                    val predecessorReplacedAt = predecessor.body.requiredElement("replaced_at", predecessorPath)
                        .takeUnless { it === JsonNull }
                        ?.let { flatOrNestedStamp(it, "$predecessorPath.replaced_at") }
                        ?: fail(path, "settings predecessor must have replaced_at")
                    if (predecessorReplacedAt != event.envelope.occurred) {
                        fail(path, "predecessor replaced_at must share the settings transaction LocalStamp")
                    }
                    val existingSuccessor = successorByPredecessor.putIfAbsent(predecessorId, currentId)
                    if (existingSuccessor != null && existingSuccessor != currentId) {
                        fail(path, "one retained schedule predecessor cannot have multiple successors")
                    }
                }
                "onboarding" -> {
                    if (profileOnboardingStamp != null && event.envelope.occurred != profileOnboardingStamp) {
                        fail(path, "work_schedule_saved must share the onboarding transaction LocalStamp")
                    }
                }
                else -> fail(path, "unknown work_schedule_saved change_source")
            }
        }
        rejectRetainedScheduleSaveCycles(successorByPredecessor)
    }

    private fun rejectRetainedScheduleSaveCycles(successorByPredecessor: Map<String, String>) {
        val states = HashMap<String, Int>()
        successorByPredecessor.keys.forEach { start ->
            if (states[start] != null) return@forEach
            var current: String? = start
            val path = ArrayList<String>()
            while (current != null && states[current] == null) {
                states[current] = VISITING
                path += current
                current = successorByPredecessor[current]
            }
            if (current != null && states[current] == VISITING) {
                fail("export.events", "retained work_schedule_saved predecessor cycle detected")
            }
            path.forEach { states[it] = VISITED }
        }
    }

    private fun validateSafetyScreenShown(
        event: ProductEventWireV1,
        decisionsById: Map<String, DecisionWireV1>,
        sessionsById: Map<String, SessionWireV1>,
        feedbackBySessionId: Map<String, FeedbackWireV1>,
        acknowledgedSafetyDigests: Set<String>,
        path: String,
    ) {
        val contentDigest = event.properties.body.requiredString("content_digest", "$path.properties")
        if (contentDigest !in acknowledgedSafetyDigests) {
            fail(path, "safety_screen_shown content_digest was never acknowledged")
        }
        val decisionId = event.envelope.decisionId?.value
        if (decisionId != null) {
            val decision = decisionsById[decisionId] ?: fail(path, "safety_screen_shown Decision does not resolve")
            val decisionPath = "export.decisions[$decisionId]"
            val outcome = decision.body.requiredString("outcome", decisionPath)
            val snapshot = decision.body.requiredElement("created_safety_hold_snapshot", decisionPath)
                .takeUnless { it === JsonNull }
                ?.asStrictObject("$decisionPath.created_safety_hold_snapshot")
                ?: fail(path, "immediate safety screen requires its Decision safety-hold snapshot")
            val kind = snapshot.requiredString("kind", "$decisionPath.created_safety_hold_snapshot")
            val expectedRoute = safetyRouteForHoldKind(kind, path)
            val expectedReason = safetyReasonForHoldKind(kind, path)
            val reasons = decision.body.requiredElement("reason_codes", decisionPath)
                .asArray("$decisionPath.reason_codes")
                .map { it.asString("$decisionPath.reason_codes") }
            requireProperty(event, "result", outcome, path)
            requireProperty(event, "route_id", expectedRoute, path)
            if (reasons != listOf(expectedReason)) {
                fail(path, "safety_screen_shown route does not match the source Decision reason")
            }
            val decisionStamp = flatOrNestedStamp(decision.body.requiredElement("created_at", decisionPath), "$decisionPath.created_at")
            if (event.envelope.occurred.occurredAtUtc < decisionStamp.occurredAtUtc) {
                fail(path, "safety_screen_shown occurs before its source Decision")
            }
            return
        }

        val sessionId = event.envelope.sessionId?.value ?: return // check-in-hold rerender has no causal source ID on wire
        sessionsById[sessionId] ?: fail(path, "post-session safety screen Session does not resolve")
        val feedback = feedbackBySessionId[sessionId]
            ?: fail(path, "post-session safety screen requires retained Feedback")
        val feedbackPath = "export.feedback[$sessionId]"
        val snapshot = feedback.body.requiredElement("created_post_session_safety_hold_snapshot", feedbackPath)
            .takeUnless { it === JsonNull }
            ?.asStrictObject("$feedbackPath.created_post_session_safety_hold_snapshot")
            ?: fail(path, "post-session safety screen requires its Feedback safety-hold snapshot")
        val kind = snapshot.requiredString("kind", "$feedbackPath.created_post_session_safety_hold_snapshot")
        requireProperty(event, "result", "BLOCKED_FOR_TODAY", path)
        requireProperty(event, "route_id", safetyRouteForHoldKind(kind, path), path)
        val answeredAt = flatOrNestedStamp(
            feedback.body.requiredElement("pain_answered_at", feedbackPath),
            "$feedbackPath.pain_answered_at",
        )
        if (event.envelope.occurred.occurredAtUtc < answeredAt.occurredAtUtc) {
            fail(path, "safety_screen_shown occurs before its source Feedback")
        }
    }

    private fun safetyRouteForHoldKind(kind: String, path: String): String = when (kind) {
        "RED_FLAG" -> "urgent_stop"
        "ACUTE_ILLNESS" -> "pause_acute_illness"
        "NEW_OR_WORSENING_PAIN_OR_INJURY" -> "pause_new_or_worsening_pain_or_injury"
        "MEDICALLY_RESTRICTED" -> "pause_medically_restricted"
        "POST_SESSION_NEW_OR_WORSE_PAIN" -> "blocked_post_session_new_or_worse_pain"
        else -> fail(path, "unknown safety-hold kind '$kind'")
    }

    private fun safetyReasonForHoldKind(kind: String, path: String): String = when (kind) {
        "RED_FLAG" -> "SAF_RED_FLAG_PRESENT"
        "ACUTE_ILLNESS" -> "SAF_ACUTE_ILLNESS"
        "NEW_OR_WORSENING_PAIN_OR_INJURY" -> "SAF_ACUTE_NEW_OR_WORSENING_PAIN"
        "MEDICALLY_RESTRICTED" -> "SAF_MEDICALLY_RESTRICTED"
        else -> fail(path, "hold kind '$kind' has no immediate Decision reason")
    }

    private fun buildClaims(dataset: ExportDatasetWireV1): List<BoundClaim> {
        val decisionsByCheckIn = dataset.decisions.groupBy {
            it.body.requiredString("check_in_id", "export.decisions[${it.decisionId}]")
        }
        return dataset.events.flatMapIndexed { eventIndex, event ->
            EventCompanionClaimResolverV1.resolve(event) { role, derivedEvent ->
                if (role.selector != "source_graph_decision") return@resolve null
                val checkInId = derivedEvent.properties.body.requiredString(
                    "source_id",
                    "export.events[$eventIndex].properties",
                )
                val matches = decisionsByCheckIn[checkInId].orEmpty().filter {
                    it.body.hasNonNull("created_safety_hold_snapshot")
                }
                if (matches.size != 1) {
                    fail(
                        "export.events[$eventIndex]",
                        "derived companion selector source_graph_decision resolves ${matches.size} Decisions",
                    )
                }
                matches.single().decisionId.value
            }.map { claim -> BoundClaim(eventIndex, event, claim) }
        }
    }

    private fun validateProfile(dataset: ExportDatasetWireV1, ledger: ClaimLedger) {
        val profile = dataset.profile.single()
        val path = "export.profile[0]"
        val onboarding = ledger.takeExact(
            CompanionRolesV1.PROFILE_ONBOARDING,
            CompanionSourceTypeV1.APP_PROFILE,
            APP_PROFILE_ID,
            EventNameV1.ONBOARDING_COMPLETED,
        )
        requireStamp(onboarding, profile.body.requiredElement("onboarding_completed_at", path), path)
        mirror(
            profile.body,
            onboarding.properties.body,
            path,
            "activation_boot_marker",
            "activation_elapsed_realtime_ms",
            "activation_clock_generation",
            "activation_wall_minus_elapsed_ms",
        )

        val acknowledgements = profile.body.requiredElement("safety_acknowledgements", path)
            .asArray("$path.safety_acknowledgements")
            .mapIndexed { index, element -> element.asStrictObject("$path.safety_acknowledgements[$index]") }
        acknowledgements.forEachIndexed { index, acknowledgement ->
            val acknowledgementPath = "$path.safety_acknowledgements[$index]"
            val role = if (index == 0) CompanionRolesV1.PROFILE_ONBOARDING else CompanionRolesV1.ACK_REACK
            val eventName = if (index == 0) EventNameV1.SCOPE_ACKNOWLEDGED else EventNameV1.SCOPE_REACK_COMPLETED
            val event = ledger.takeExact(
                role,
                CompanionSourceTypeV1.SAFETY_ACKNOWLEDGEMENT,
                acknowledgement.requiredString("acknowledgement_id", acknowledgementPath),
                eventName,
            )
            requireStamp(event, acknowledgement.requiredElement("acknowledged_at", acknowledgementPath), acknowledgementPath)
            mirror(
                acknowledgement,
                event.properties.body,
                acknowledgementPath,
                "acknowledgement_id",
                "content_version",
                "content_digest",
            )
            if (index == 0) {
                requireProperty(event, "kind", "onboarding", acknowledgementPath)
                requireProperty(event, "eligibility_confirmed", true, acknowledgementPath)
            } else {
                requireProperty(
                    event,
                    "supersedes_acknowledgement_id",
                    acknowledgements[index - 1].requiredString("acknowledgement_id", acknowledgementPath),
                    acknowledgementPath,
                )
            }
        }
    }

    private fun validateCheckIns(dataset: ExportDatasetWireV1, ledger: ClaimLedger) {
        dataset.checkIns.forEach { checkIn ->
            val path = "export.check_ins[${checkIn.checkInId}]"
            val event = ledger.takeExact(
                CompanionRolesV1.CHECK_IN_COMMIT,
                CompanionSourceTypeV1.CHECK_IN,
                checkIn.checkInId.value,
                EventNameV1.CHECK_IN_SUBMITTED,
            )
            requireStamp(event, checkIn.body.requiredElement("confirmed_at", path), path)
            requireEnvelope(event, "schedule_version_id", checkIn.body.requiredString("schedule_version_id", path), path)
            mirror(checkIn.body, event.properties.body, path, "check_in_id", "answers_kind")
            val expectedKind = if (checkIn.body.isNull("parent_id")) "new" else "reconfirm"
            requireProperty(event, "kind", expectedKind, path)
        }
    }

    private fun validateDecisions(dataset: ExportDatasetWireV1, ledger: ClaimLedger) {
        dataset.decisions.forEach { decision ->
            val path = "export.decisions[${decision.decisionId}]"
            val commit = ledger.takeExact(
                CompanionRolesV1.DECISION_COMMIT,
                CompanionSourceTypeV1.DECISION,
                decision.decisionId.value,
                EventNameV1.DECISION_EVALUATED,
            )
            requireStamp(commit, decision.body.requiredElement("created_at", path), path)
            requireEnvelope(commit, "decision_id", decision.decisionId.value, path)
            requireEnvelope(commit, "schedule_version_id", decision.body.requiredString("schedule_version_id", path), path)
            mirrorMapped(
                decision.body,
                commit.properties.body,
                path,
                "check_in_id" to "check_in_id",
                "outcome" to "result",
                "base_mode" to "base_mode",
                "effective_mode" to "effective_mode",
                "reason_codes" to "reason_codes",
                "invalid_fields" to "invalid_fields",
                "rule_version" to "rule_version",
            )
            val capApplied = decision.body.hasNonNull("evaluation_day_mode_cap_snapshot")
            requireProperty(commit, "cap_applied", capApplied, path)

            decision.body.requiredElement("created_safety_hold_snapshot", path).takeUnless { it === JsonNull }?.let { raw ->
                val event = ledger.takeExact(
                    CompanionRolesV1.DECISION_SIDE_EFFECT,
                    CompanionSourceTypeV1.DECISION,
                    decision.decisionId.value,
                    EventNameV1.SAFETY_HOLD_CREATED,
                )
                val snapshot = raw.asStrictObject("$path.created_safety_hold_snapshot")
                mirrorSafetyHold(snapshot, event, path)
            }
            decision.body.requiredElement("created_rest_suppression_snapshot", path).takeUnless { it === JsonNull }?.let { raw ->
                val event = ledger.takeExact(
                    CompanionRolesV1.DECISION_SIDE_EFFECT,
                    CompanionSourceTypeV1.DECISION,
                    decision.decisionId.value,
                    EventNameV1.REST_SUPPRESSION_CREATED,
                )
                val snapshot = raw.asStrictObject("$path.created_rest_suppression_snapshot")
                requireEnvelope(event, "decision_id", decision.decisionId.value, path)
                requireFlatStamp(event, snapshot, path)
                mirrorMapped(
                    snapshot,
                    event.properties.body,
                    path,
                    "local_date" to "origin_local_date",
                    "zone_id" to "origin_timezone_id",
                    "expires_at_utc" to "expires_at_utc",
                    "rule_version" to "rule_version",
                )
            }
        }
    }

    private fun validateSessions(dataset: ExportDatasetWireV1, ledger: ClaimLedger) {
        val feedbackBySession = dataset.feedback.associateBy { it.sessionId.value }
        val decisionsById = dataset.decisions.associateBy { it.decisionId.value }
        val checkInCommitsById = dataset.events
            .filter { it.name == EventNameV1.CHECK_IN_SUBMITTED }
            .groupBy { event ->
                event.properties.body.requiredString("check_in_id", "check_in_submitted.properties")
            }
        dataset.sessions.forEach { session ->
            val path = "export.sessions[${session.sessionId}]"
            val start = ledger.takeExact(
                CompanionRolesV1.SESSION_START,
                CompanionSourceTypeV1.SESSION,
                session.sessionId.value,
                EventNameV1.ROUTINE_STARTED,
            )
            requireStamp(start, session.body.requiredElement("started_at", path), path)
            requireEnvelope(start, "session_id", session.sessionId.value, path)
            requireEnvelope(start, "decision_id", session.body.requiredString("decision_id", path), path)
            requireEnvelope(start, "schedule_version_id", session.body.requiredString("schedule_version_id", path), path)
            requireEnvelope(start, "source", session.body.requiredString("source", path), path)
            requireEnvelope(start, "reminder_occurrence_id", session.body.nullableString("reminder_occurrence_id", path), path)
            mirrorMapped(
                session.body,
                start.properties.body,
                path,
                "routine_id" to "routine_id",
                "runtime_effective_mode_at_start" to "runtime_effective_mode_at_start",
                "is_selected_workday_at_start" to "is_selected_workday_at_start",
                "start_boot_marker" to "start_boot_marker",
                "start_elapsed_realtime_ms" to "start_elapsed_realtime_ms",
                "start_clock_generation" to "start_clock_generation",
                "start_wall_minus_elapsed_ms" to "start_wall_minus_elapsed_ms",
            )
            val decisionId = session.body.requiredString("decision_id", path)
            val decision = decisionsById[decisionId] ?: fail(path, "Session Decision does not resolve")
            val decisionPath = "export.decisions[$decisionId]"
            val checkInId = decision.body.requiredString("check_in_id", decisionPath)
            val checkInCommit = checkInCommitsById[checkInId].orEmpty().singleOrNull()
                ?: fail(path, "routine_started requires exactly one retained source check_in_submitted event")
            requireProperty(
                start,
                "check_in_flow_id",
                checkInCommit.properties.body.requiredString("check_in_flow_id", "check_in_submitted.properties"),
                path,
            )
            if (start.envelope.occurred.occurredAtUtc < checkInCommit.envelope.occurred.occurredAtUtc) {
                fail(path, "routine_started occurs before source check_in_submitted")
            }

            validateSkippedSteps(session, ledger, path)
            validateTerminal(session, feedbackBySession[session.sessionId.value], ledger, path)
            feedbackBySession[session.sessionId.value]?.let { validateFeedback(session, it, ledger, path) }
        }
    }

    private fun validateSkippedSteps(session: SessionWireV1, ledger: ClaimLedger, path: String) {
        val checkpoint = session.body.requiredElement("player_checkpoint", path).asStrictObject("$path.player_checkpoint")
        val skipped = checkpoint.requiredElement("skipped_steps", "$path.player_checkpoint")
            .asArray("$path.player_checkpoint.skipped_steps")
            .mapIndexed { index, element -> element.asStrictObject("$path.player_checkpoint.skipped_steps[$index]") }
        val events = ledger.takeAll(
            CompanionRolesV1.SESSION_STEP_SKIP,
            CompanionSourceTypeV1.SESSION,
            session.sessionId.value,
            EventNameV1.ROUTINE_STEP_SKIPPED,
        )
        if (events.size != skipped.size) fail(path, "expected ${skipped.size} routine_step_skipped companions, found ${events.size}")
        skipped.zip(events).forEachIndexed { index, (record, event) ->
            mirror(record, event.properties.body, "$path.player_checkpoint.skipped_steps[$index]", "step_id", "active_elapsed_ms")
        }
    }

    private fun validateTerminal(
        session: SessionWireV1,
        feedback: FeedbackWireV1?,
        ledger: ClaimLedger,
        path: String,
    ) {
        val status = session.body.requiredString("status", path)
        if (status == "ACTIVE") return
        val expectedName = when (status) {
            "COMPLETED" -> EventNameV1.ROUTINE_COMPLETED
            "STOPPED" -> EventNameV1.ROUTINE_STOPPED
            "ABANDONED" -> EventNameV1.ROUTINE_ABANDONED
            else -> fail(path, "unknown terminal status '$status'")
        }
        val event = ledger.takeExact(
            CompanionRolesV1.SESSION_TERMINAL,
            CompanionSourceTypeV1.SESSION,
            session.sessionId.value,
            expectedName,
        )
        requireStamp(event, session.body.requiredElement("terminal_at", path), path)
        val checkpoint = session.body.requiredElement("player_checkpoint", path).asStrictObject("$path.player_checkpoint")
        when (status) {
            "COMPLETED" -> {
                mirror(session.body, event.properties.body, path, "routine_id", "completion_boot_marker", "completion_elapsed_realtime_ms", "completion_clock_generation", "completion_wall_minus_elapsed_ms")
                requireProperty(event, "duration_ms", checkpoint.requiredInt64("accumulated_active_ms", path), path)
                requireProperty(event, "step_skip_count", checkpoint.requiredElement("skipped_steps", path).asArray(path).size.toLong(), path)
                requireProperty(event, "pain_gate_status", "PENDING", path)
            }
            "STOPPED" -> {
                requireProperty(event, "elapsed_ms", checkpoint.requiredInt64("accumulated_active_ms", path), path)
                val painStatus = when (feedback?.body?.requiredString("pain_gate_status", path)) {
                    "resolved_no" -> "RESOLVED_NO"
                    "resolved_hold" -> "RESOLVED_HOLD"
                    else -> fail(path, "STOPPED Session requires resolved Feedback")
                }
                requireProperty(event, "pain_gate_status", painStatus, path)
            }
            "ABANDONED" -> requireProperty(event, "pain_gate_status", "PENDING", path)
        }
    }

    private fun validateFeedback(
        session: SessionWireV1,
        feedback: FeedbackWireV1,
        ledger: ClaimLedger,
        path: String,
    ) {
        val feedbackPath = "export.feedback[${feedback.sessionId}]"
        val painStatus = feedback.body.requiredString("pain_gate_status", feedbackPath)
        val painResolutionEvent = if (painStatus != "pending") {
            val event = ledger.takeExact(
                CompanionRolesV1.SESSION_PAIN_RESOLUTION,
                CompanionSourceTypeV1.SESSION,
                session.sessionId.value,
                EventNameV1.PAIN_GATE_RESOLVED,
            )
            requireStamp(event, feedback.body.requiredElement("pain_answered_at", feedbackPath), feedbackPath)
            requireProperty(event, "terminal_state", session.body.requiredString("status", path).lowercase(), feedbackPath)
            requireProperty(event, "new_or_worse_pain", feedback.body.requiredString("new_or_worse_pain", feedbackPath), feedbackPath)
            requireProperty(event, "pain_gate_status", if (painStatus == "resolved_no") "RESOLVED_NO" else "RESOLVED_HOLD", feedbackPath)
            event
        } else {
            null
        }

        feedback.body.requiredElement("created_post_session_safety_hold_snapshot", feedbackPath).takeUnless { it === JsonNull }?.let { raw ->
            val event = ledger.takeExact(
                CompanionRolesV1.SESSION_FEEDBACK_SIDE_EFFECT,
                CompanionSourceTypeV1.SESSION,
                session.sessionId.value,
                EventNameV1.SAFETY_HOLD_CREATED,
            )
            mirrorSafetyHold(raw.asStrictObject("$feedbackPath.created_post_session_safety_hold_snapshot"), event, feedbackPath)
            if (event.envelope.occurred != painResolutionEvent?.envelope?.occurred) {
                fail(feedbackPath, "post-session safety hold and pain_gate_resolved must share the commit LocalStamp")
            }
        }
        val capUpdateEvent = feedback.body.requiredElement("day_mode_cap_update_snapshot", feedbackPath).takeUnless { it === JsonNull }?.let { raw ->
            val event = ledger.takeExact(
                CompanionRolesV1.SESSION_FEEDBACK_SIDE_EFFECT,
                CompanionSourceTypeV1.SESSION,
                session.sessionId.value,
                EventNameV1.DAY_MODE_CAP_UPDATED,
            )
            mirrorDayModeCapUpdate(raw.asStrictObject("$feedbackPath.day_mode_cap_update_snapshot"), event, feedbackPath)
            event
        }

        val transitionEvents = validateFeedbackTransitions(
            session = session,
            feedback = feedback,
            ledger = ledger,
            painResolutionEvent = painResolutionEvent,
            capUpdateEvent = capUpdateEvent,
            path = feedbackPath,
        )
        validateCapUpdateCause(feedback, painResolutionEvent, capUpdateEvent, transitionEvents, feedbackPath)
        validateFeedbackUpdatedAt(session, feedback, painResolutionEvent, transitionEvents, feedbackPath)
    }

    private fun validateFeedbackTransitions(
        session: SessionWireV1,
        feedback: FeedbackWireV1,
        ledger: ClaimLedger,
        painResolutionEvent: ProductEventWireV1?,
        capUpdateEvent: ProductEventWireV1?,
        path: String,
    ): List<ProductEventWireV1> {
        val events = ledger.takeAll(
            CompanionRolesV1.SESSION_FEEDBACK_TRANSITION,
            CompanionSourceTypeV1.SESSION,
            session.sessionId.value,
            EventNameV1.FEEDBACK_UPDATED,
        )
        val fields = listOf("effort", "context_fit")
        val state = linkedMapOf<String, JsonElement>(
            "effort" to JsonNull,
            "context_fit" to JsonNull,
        )
        val finalPain = feedback.body.nullableString("new_or_worse_pain", path)
        val terminalStamp = localStamp(session.body.requiredElement("terminal_at", path), "$path.terminal_at")
        if (events.size > fields.size) {
            fail(path, "feedback_updated sequence has more write-once transitions than feedback fields")
        }
        events.forEach { event ->
            requireNotBeforeTerminal(event.envelope.occurred, terminalStamp, path, "feedback_updated")
            requireProperty(event, "terminal_state", session.body.requiredString("status", path).lowercase(), path)
        }

        val semanticEvents = ArrayList<ProductEventWireV1>(events.size)
        events.groupBy { it.envelope.occurred.occurredAtUtc }.values.forEach { sameInstantEvents ->
            val stamps = sameInstantEvents.map { it.envelope.occurred }.distinct()
            if (stamps.size != 1) {
                fail(path, "feedback events share an instant but not one transaction LocalStamp")
            }
            val candidateOrders = when (sameInstantEvents.size) {
                1 -> listOf(sameInstantEvents)
                2 -> listOf(sameInstantEvents, sameInstantEvents.asReversed())
                else -> fail(path, "feedback LocalStamp group exceeds the bounded write-once field count")
            }
            var acceptedState: LinkedHashMap<String, JsonElement>? = null
            var acceptedOrder: List<ProductEventWireV1>? = null
            candidateOrders.forEach { candidateOrder ->
                if (acceptedOrder != null) return@forEach
                val candidateState = LinkedHashMap(state)
                try {
                    candidateOrder.forEach { event ->
                        validateFeedbackTransitionEvent(
                            event = event,
                            state = candidateState,
                            fields = fields,
                            finalPain = finalPain,
                            painResolutionEvent = painResolutionEvent,
                            capUpdateEvent = capUpdateEvent,
                            path = path,
                        )
                    }
                    acceptedState = candidateState
                    acceptedOrder = candidateOrder
                } catch (_: WireContractException) {
                    // Try the only other possible order. There are two write-once fields, so a
                    // valid group can contain at most two events and this search stays constant.
                }
            }
            val resolvedState = acceptedState
                ?: fail(path, "feedback LocalStamp group has no valid semantic transition order")
            state.clear()
            state.putAll(resolvedState)
            semanticEvents.addAll(requireNotNull(acceptedOrder))
        }
        fields.forEach { field ->
            if (state.getValue(field) != feedback.body.requiredElement(field, path)) {
                fail(path, "feedback_updated sequence does not reach final '$field'")
            }
        }
        return semanticEvents
    }

    private fun validateFeedbackTransitionEvent(
        event: ProductEventWireV1,
        state: LinkedHashMap<String, JsonElement>,
        fields: List<String>,
        finalPain: String?,
        painResolutionEvent: ProductEventWireV1?,
        capUpdateEvent: ProductEventWireV1?,
        path: String,
    ) {
        val updated = updatedFields(event, path)
        updated.forEach { field ->
            if (state.getValue(field) !== JsonNull) fail(path, "feedback field '$field' is updated more than once")
            state[field] = event.properties.body.requiredElement(field, path)
        }
        fields.forEach { field ->
            if (event.properties.body.requiredElement(field, path) != state.getValue(field)) {
                fail(path, "feedback_updated does not mirror post-state field '$field'")
            }
        }

        val painAtTransaction = painAtTransaction(finalPain, painResolutionEvent, event.envelope.occurred, path)
        val expectedComplete = painAtTransaction != null && fields.all { state.getValue(it) !== JsonNull }
        requireProperty(event, "feedback_complete", expectedComplete, path)
        val originKnownInactive = painAtTransaction != null &&
            painResolutionEvent?.properties?.body?.requiredBoolean("answered_at_or_after_origin_expiry", path) == true
        val expectedCapResult = when {
            "effort" !in updated -> "no_effort_transition"
            state.getValue("effort").asString("$path.effort") in listOf("easy", "moderate") -> "not_too_hard"
            painAtTransaction != "no" -> "pain_not_no"
            originKnownInactive -> "origin_day_expired"
            capUpdateEvent?.envelope?.occurred == event.envelope.occurred -> "applied"
            else -> "origin_day_expired"
        }
        requireProperty(event, "cap_result", expectedCapResult, path)
    }

    private fun validateCapUpdateCause(
        feedback: FeedbackWireV1,
        painResolutionEvent: ProductEventWireV1?,
        capUpdateEvent: ProductEventWireV1?,
        transitionEvents: List<ProductEventWireV1>,
        path: String,
    ) {
        val finalPain = feedback.body.nullableString("new_or_worse_pain", path)
        val effortAtPainResolution = painResolutionEvent?.let {
            fieldAtTransaction(transitionEvents, "effort", it.envelope.occurred, path)
        }
        val activePainResolutionRequiresCap = painResolutionEvent != null &&
            finalPain == "no" &&
            !painResolutionEvent.properties.body.requiredBoolean("answered_at_or_after_origin_expiry", path) &&
            effortAtPainResolution?.asString("$path.effort") == "too_hard"
        if (capUpdateEvent == null) {
            if (activePainResolutionRequiresCap) {
                fail(path, "pain=no resolution with existing too_hard effort and active origin constraint requires a cap update")
            }
            return
        }

        val capStamp = capUpdateEvent.envelope.occurred
        val appliedByEffortTransition = transitionEvents.any { event ->
            event.envelope.occurred == capStamp &&
                "effort" in updatedFields(event, path) &&
                event.properties.body.requiredString("effort", path) == "too_hard" &&
                painAtTransaction(finalPain, painResolutionEvent, event.envelope.occurred, path) == "no" &&
                !originKnownInactiveAtTransaction(finalPain, painResolutionEvent, event.envelope.occurred, path)
        }
        val effortAtCap = fieldAtTransaction(transitionEvents, "effort", capStamp, path)
        val appliedByPainResolution = painResolutionEvent?.envelope?.occurred == capStamp &&
            finalPain == "no" &&
            !painResolutionEvent.properties.body.requiredBoolean("answered_at_or_after_origin_expiry", path) &&
            effortAtCap?.asString("$path.effort") == "too_hard"
        if (!appliedByEffortTransition && !appliedByPainResolution) {
            fail(path, "day mode cap update is not caused by a too_hard effort transition or pain=no resolution at the same commit stamp")
        }
    }

    private fun validateFeedbackUpdatedAt(
        session: SessionWireV1,
        feedback: FeedbackWireV1,
        painResolutionEvent: ProductEventWireV1?,
        transitionEvents: List<ProductEventWireV1>,
        path: String,
    ) {
        val terminalStamp = localStamp(session.body.requiredElement("terminal_at", path), "$path.terminal_at")
        painResolutionEvent?.let { requireNotBeforeTerminal(it.envelope.occurred, terminalStamp, path, "pain_gate_resolved") }
        val relevantStamps = buildList {
            add(terminalStamp)
            painResolutionEvent?.let { add(it.envelope.occurred) }
            addAll(transitionEvents.map { it.envelope.occurred })
        }
        val latestInstant = relevantStamps.maxOf { it.occurredAtUtc }
        val latestStamps = relevantStamps.filter { it.occurredAtUtc == latestInstant }.distinct()
        if (latestStamps.size != 1) {
            fail(path, "latest feedback transactions share an instant but not one LocalStamp")
        }
        val updatedAt = localStamp(feedback.body.requiredElement("updated_at", path), "$path.updated_at")
        if (updatedAt != latestStamps.single()) {
            fail(path, "updated_at does not mirror the latest terminal, pain, or optional-feedback transaction")
        }
    }

    private fun painAtTransaction(
        finalPain: String?,
        painResolutionEvent: ProductEventWireV1?,
        transactionStamp: LocalStampWireV1,
        path: String,
    ): String? {
        if (painResolutionEvent == null) return null
        val painStamp = painResolutionEvent.envelope.occurred
        val instantOrder = transactionStamp.occurredAtUtc.compareTo(painStamp.occurredAtUtc)
        return when {
            instantOrder < 0 -> null
            instantOrder > 0 -> finalPain
            transactionStamp == painStamp -> finalPain
            else -> fail(path, "pain and feedback events share an instant but not one transaction LocalStamp")
        }
    }

    private fun originKnownInactiveAtTransaction(
        finalPain: String?,
        painResolutionEvent: ProductEventWireV1?,
        transactionStamp: LocalStampWireV1,
        path: String,
    ): Boolean = painResolutionEvent != null &&
        painResolutionEvent.properties.body.requiredBoolean("answered_at_or_after_origin_expiry", path) &&
        painAtTransaction(finalPain, painResolutionEvent, transactionStamp, path) != null

    private fun fieldAtTransaction(
        events: List<ProductEventWireV1>,
        field: String,
        transactionStamp: LocalStampWireV1,
        path: String,
    ): JsonElement? {
        var value: JsonElement? = null
        events.forEach { event ->
            val instantOrder = event.envelope.occurred.occurredAtUtc.compareTo(transactionStamp.occurredAtUtc)
            val included = when {
                instantOrder < 0 -> true
                instantOrder > 0 -> false
                event.envelope.occurred == transactionStamp -> true
                else -> fail(path, "feedback events share an instant but not one transaction LocalStamp")
            }
            if (included && field in updatedFields(event, path)) {
                value = event.properties.body.requiredElement(field, path)
            }
        }
        return value
    }

    private fun updatedFields(event: ProductEventWireV1, path: String): Set<String> =
        event.properties.body.requiredElement("updated_fields", path)
            .asArray("$path.updated_fields")
            .mapTo(linkedSetOf()) { it.asString("$path.updated_fields") }

    private fun localStamp(raw: JsonElement, path: String): LocalStampWireV1 =
        LocalStampWireV1.fromObject(raw.asStrictObject(path), path)

    private fun requireNotBeforeTerminal(
        stamp: LocalStampWireV1,
        terminalStamp: LocalStampWireV1,
        path: String,
        eventName: String,
    ) {
        if (stamp.occurredAtUtc < terminalStamp.occurredAtUtc) {
            fail(path, "$eventName occurs before the terminal Feedback record exists")
        }
    }

    private fun validateReminders(dataset: ExportDatasetWireV1, ledger: ClaimLedger) {
        val remindersById = dataset.reminders.associateBy { it.reminderOccurrenceId.value }
        val schedulesById = dataset.workSchedule.associateBy { it.scheduleVersionId.value }
        val lifecycleEventsByReminderId = dataset.events.mapNotNull { event ->
            event.envelope.reminderOccurrenceId?.value?.let { it to event }
        }.groupBy({ it.first }, { it.second })
        val snoozeChildrenByParent = dataset.reminders.filter { it.kind == "snooze" }.groupBy {
            it.body.requiredString("parent_occurrence_id", "export.reminders[${it.reminderOccurrenceId}]")
        }
        snoozeChildrenByParent.forEach { (parent, children) ->
            if (children.size != 1) fail("export.reminders", "source occurrence $parent has ${children.size} snooze children")
        }

        dataset.reminders.forEach { reminder ->
            val path = "export.reminders[${reminder.reminderOccurrenceId}]"
            val scheduleId = reminder.body.requiredString("schedule_version_id", path)
            val schedule = schedulesById[scheduleId] ?: fail(path, "reminder schedule_version_id does not resolve")
            val created = ledger.takeExact(
                CompanionRolesV1.REMINDER_CREATE,
                CompanionSourceTypeV1.REMINDER_OCCURRENCE,
                reminder.reminderOccurrenceId.value,
                EventNameV1.REMINDER_SCHEDULED,
            )
            requireEnvelope(created, "reminder_occurrence_id", reminder.reminderOccurrenceId.value, path)
            requireEnvelope(created, "schedule_version_id", scheduleId, path)
            mirror(reminder.body, created.properties.body, path, "kind", "due_at", "supersedes_occurrence_id")
            validateReminderCreation(
                reminder = reminder,
                schedule = schedule,
                created = created,
                remindersById = remindersById,
                lifecycleEventsByReminderId = lifecycleEventsByReminderId,
                path = path,
            )
            if (reminder.kind == "fixed") {
                mirror(reminder.body, created.properties.body, path, "generation", "creation_reason")
                val logical = created.properties.body.requiredElement("logical_fixed_key", path).asStrictObject("$path.logical_fixed_key")
                mirrorMapped(
                    reminder.body,
                    logical,
                    path,
                    "schedule_version_id" to "schedule_version_id",
                    "slot_index" to "slot_index",
                    "local_date" to "local_date",
                )
                if (logical.requiredString("kind", path) != "fixed") fail(path, "logical_fixed_key.kind mismatch")
            } else {
                mirror(reminder.body, created.properties.body, path, "parent_occurrence_id", "ordinal")
                validateSnoozeEdge(reminder, schedule, remindersById, ledger, created, path)
            }

            validateReminderDelivery(reminder, schedule, ledger, path)
            validateReminderInteractions(reminder, ledger, path)
            validateReminderResolution(
                reminder,
                schedule,
                remindersById,
                lifecycleEventsByReminderId,
                ledger,
                created,
                path,
            )
        }
    }

    private fun validateReminderCreation(
        reminder: ReminderWireV1,
        schedule: WorkScheduleWireV1,
        created: ProductEventWireV1,
        remindersById: Map<String, ReminderWireV1>,
        lifecycleEventsByReminderId: Map<String, List<ProductEventWireV1>>,
        path: String,
    ) {
        val dueAt = flatOrNestedStamp(reminder.body.requiredElement("due_at", path), "$path.due_at")
        if (created.envelope.occurred.occurredAtUtc >= dueAt.occurredAtUtc) {
            fail(path, "reminder_scheduled must occur strictly before due_at")
        }

        val schedulePath = "export.work_schedule[${schedule.scheduleVersionId}]"
        val effectiveFrom = flatOrNestedStamp(
            schedule.body.requiredElement("effective_from", schedulePath),
            "$schedulePath.effective_from",
        )
        if (created.envelope.occurred.occurredAtUtc < effectiveFrom.occurredAtUtc) {
            fail(path, "reminder_scheduled occurs before schedule effective_from")
        }
        scheduleReplacementStamp(schedule, schedulePath)?.let { replacedAt ->
            if (created.envelope.occurred.occurredAtUtc > replacedAt.occurredAtUtc) {
                fail(path, "reminder_scheduled occurs after schedule replacement")
            }
        }

        if (reminder.kind != "fixed" || reminder.body.requiredInt64("generation", path) == 0L) return
        val predecessorId = reminder.body.requiredString("supersedes_occurrence_id", path)
        val predecessor = remindersById[predecessorId] ?: fail(path, "fixed predecessor is missing")
        val predecessorPath = "export.reminders[${predecessor.reminderOccurrenceId}]"
        val expectedResolutionNames = when (predecessor.body.requiredString("status", predecessorPath)) {
            "CANCELLED" -> setOf(EventNameV1.REMINDER_CANCELLED)
            "BLOCKED_PERMISSION" -> setOf(EventNameV1.REMINDER_CANCELLED, EventNameV1.REMINDER_BLOCKED_PERMISSION)
            else -> fail(path, "positive fixed generation predecessor is not reeligible")
        }
        val predecessorResolution = lifecycleEventsByReminderId[predecessorId].orEmpty()
            .filter { it.name in expectedResolutionNames }
            .singleOrNull()
            ?: fail(path, "positive fixed generation requires exactly one retained predecessor resolution")
        if (created.envelope.occurred.occurredAtUtc < predecessorResolution.envelope.occurred.occurredAtUtc) {
            fail(path, "positive fixed generation was created before predecessor resolution")
        }
    }

    private fun validateSnoozeEdge(
        child: ReminderWireV1,
        schedule: WorkScheduleWireV1,
        remindersById: Map<String, ReminderWireV1>,
        ledger: ClaimLedger,
        created: ProductEventWireV1,
        path: String,
    ) {
        val parentId = child.body.requiredString("parent_occurrence_id", path)
        val parent = remindersById[parentId] ?: fail(path, "snooze parent is missing")
        if (parent.body.requiredString("status", path) != "DELIVERED") fail(path, "snooze parent must remain DELIVERED")
        val childClaim = ledger.takeExactClaim(
            CompanionRolesV1.REMINDER_SNOOZE_EDGE,
            CompanionSourceTypeV1.REMINDER_OCCURRENCE,
            child.reminderOccurrenceId.value,
            EventNameV1.REMINDER_SNOOZED,
        )
        val parentClaim = ledger.takeExactClaim(
            CompanionRolesV1.REMINDER_SNOOZE_EDGE,
            CompanionSourceTypeV1.REMINDER_OCCURRENCE,
            parentId,
            EventNameV1.REMINDER_SNOOZED,
        )
        if (childClaim.eventIndex != parentClaim.eventIndex) fail(path, "snooze source and child companion edges must belong to the same event")
        val event = childClaim.event
        if (created.envelope.occurred != event.envelope.occurred) {
            fail(path, "reminder_scheduled and reminder_snoozed must share the same transaction LocalStamp")
        }
        val sourceDelivered = LocalStampWireV1.fromObject(
            parent.body.requiredElement("delivered_at", path).asStrictObject("$path.source_delivered_at"),
            "$path.source_delivered_at",
        )
        if (event.envelope.occurred.occurredAtUtc < sourceDelivered.occurredAtUtc) {
            fail(path, "reminder_snoozed precedes source delivery")
        }
        requireEnvelope(event, "reminder_occurrence_id", parentId, path)
        requireProperty(event, "snooze_occurrence_id", child.reminderOccurrenceId.value, path)
        mirrorMapped(
            child.body,
            event.properties.body,
            path,
            "due_at" to "target_at",
            "ordinal" to "ordinal",
            "supersedes_occurrence_id" to "supersedes_occurrence_id",
        )
        val target = LocalStampWireV1.fromObject(
            child.body.requiredElement("due_at", path).asStrictObject("$path.due_at"),
            "$path.due_at",
        )
        val duration = event.properties.body.requiredInt64("duration_minutes", path)
        val sourceDue = LocalStampWireV1.fromObject(
            parent.body.requiredElement("due_at", path).asStrictObject("$path.source_due_at"),
            "$path.source_due_at",
        )
        if (target.zoneId != event.envelope.occurred.zoneId || target.zoneId != sourceDue.zoneId ||
            target.localDate != event.envelope.occurred.localDate || target.localDate != sourceDue.localDate
        ) {
            fail(path, "snooze target, event, and source due must share the runtime zone and local date")
        }
        val expectedTarget = try {
            Math.addExact(event.envelope.occurred.occurredAtUtc.instant.toEpochMilli(), Math.multiplyExact(duration, 60_000L))
        } catch (_: ArithmeticException) {
            fail(path, "snooze target calculation overflows")
        }
        if (target.occurredAtUtc.instant.toEpochMilli() != expectedTarget) fail(path, "snooze target does not mirror duration_minutes")
        val schedulePath = "export.work_schedule[${schedule.scheduleVersionId}]"
        val workEnd = TimeMinuteWireV1.parse(schedule.body.requiredString("work_end", schedulePath)).time
        val resolvedWorkEnd = ReminderScheduleConformanceV1.resolveScheduleWallTime(
            target.localDate.date,
            workEnd,
            target.zoneId,
        )
        if (!target.occurredAtUtc.instant.isBefore(resolvedWorkEnd.toInstant())) {
            fail(path, "snooze target must be strictly before the resolved work_end")
        }
        scheduleReplacementStamp(schedule, schedulePath)?.let { replacedAt ->
            if (event.envelope.occurred.occurredAtUtc > replacedAt.occurredAtUtc) {
                fail(path, "reminder_snoozed occurs after schedule replacement")
            }
        }
    }

    private fun validateReminderDelivery(
        reminder: ReminderWireV1,
        schedule: WorkScheduleWireV1,
        ledger: ClaimLedger,
        path: String,
    ) {
        if (reminder.body.requiredString("status", path) != "DELIVERED") return
        val event = ledger.takeExact(
            CompanionRolesV1.REMINDER_DELIVERY,
            CompanionSourceTypeV1.REMINDER_OCCURRENCE,
            reminder.reminderOccurrenceId.value,
            EventNameV1.REMINDER_POSTED,
        )
        requireStamp(event, reminder.body.requiredElement("delivered_at", path), path)
        mirror(reminder.body, event.properties.body, path, "kind", "due_at", "delivered_at")
        val deliveredAt = event.envelope.occurred
        val dueAt = LocalStampWireV1.fromObject(
            reminder.body.requiredElement("due_at", path).asStrictObject("$path.due_at"),
            "$path.due_at",
        )
        val schedulePath = "export.work_schedule[${schedule.scheduleVersionId}]"
        val workEnd = TimeMinuteWireV1.parse(schedule.body.requiredString("work_end", schedulePath)).time
        val resolvedWorkEnd = ReminderScheduleConformanceV1.resolveScheduleWallTime(
            dueAt.localDate.date,
            workEnd,
            dueAt.zoneId,
        )
        if (!deliveredAt.occurredAtUtc.instant.isBefore(resolvedWorkEnd.toInstant())) {
            fail(path, "reminder delivery must be strictly before the resolved work_end")
        }
        scheduleReplacementStamp(schedule, schedulePath)?.let { replacedAt ->
            if (deliveredAt.occurredAtUtc > replacedAt.occurredAtUtc) {
                fail(path, "reminder delivery occurs after schedule replacement")
            }
        }
    }

    private fun scheduleReplacementStamp(schedule: WorkScheduleWireV1, path: String): LocalStampWireV1? =
        schedule.body.requiredElement("replaced_at", path).takeUnless { it === JsonNull }?.let { raw ->
            LocalStampWireV1.fromObject(raw.asStrictObject("$path.replaced_at"), "$path.replaced_at")
        }

    private fun validateReminderInteractions(reminder: ReminderWireV1, ledger: ClaimLedger, path: String) {
        listOf(
            Triple("first_opened_at", EventNameV1.REMINDER_OPENED, "first_opened_at"),
            Triple("dismissed_at", EventNameV1.REMINDER_DISMISSED, "dismissed_at"),
        ).forEach { (stampKey, name, propertyKey) ->
            reminder.body.requiredElement(stampKey, path).takeUnless { it === JsonNull }?.let { stamp ->
                val event = ledger.takeExact(
                    CompanionRolesV1.REMINDER_INTERACTION,
                    CompanionSourceTypeV1.REMINDER_OCCURRENCE,
                    reminder.reminderOccurrenceId.value,
                    name,
                )
                requireStamp(event, stamp, path)
                if (event.properties.body.requiredElement(propertyKey, path) != stamp) fail(path, "$name does not mirror $stampKey")
            }
        }
    }

    private fun validateReminderResolution(
        reminder: ReminderWireV1,
        schedule: WorkScheduleWireV1,
        remindersById: Map<String, ReminderWireV1>,
        lifecycleEventsByReminderId: Map<String, List<ProductEventWireV1>>,
        ledger: ClaimLedger,
        created: ProductEventWireV1,
        path: String,
    ) {
        val status = reminder.body.requiredString("status", path)
        val allowedNames = when {
            status == "MERGED" -> setOf(EventNameV1.REMINDER_MERGED)
            status == "CANCELLED" -> setOf(EventNameV1.REMINDER_CANCELLED)
            status == "BLOCKED_PERMISSION" -> setOf(EventNameV1.REMINDER_BLOCKED_PERMISSION, EventNameV1.REMINDER_CANCELLED)
            status.startsWith("SKIPPED_") -> setOf(EventNameV1.REMINDER_SKIPPED)
            else -> return
        }
        val event = ledger.takeExact(
            CompanionRolesV1.REMINDER_RESOLUTION,
            CompanionSourceTypeV1.REMINDER_OCCURRENCE,
            reminder.reminderOccurrenceId.value,
            allowedNames,
        )
        if (event.envelope.occurred.occurredAtUtc < created.envelope.occurred.occurredAtUtc) {
            fail(path, "reminder resolution occurs before reminder_scheduled")
        }
        val schedulePath = "export.work_schedule[${schedule.scheduleVersionId}]"
        val scheduleReplacement = scheduleReplacementStamp(schedule, schedulePath)
        scheduleReplacement?.let { replacedAt ->
            if (event.envelope.occurred.occurredAtUtc > replacedAt.occurredAtUtc) {
                fail(path, "reminder resolution occurs after schedule replacement")
            }
        }
        when (event.name) {
            EventNameV1.REMINDER_MERGED -> {
                val keptId = reminder.body.requiredString("merged_into_occurrence_id", path)
                requireProperty(event, "kept_occurrence_id", keptId, path)
                val kept = remindersById[keptId] ?: fail(path, "merged target is missing")
                val snoozeMember = if (reminder.kind == "snooze") reminder else kept
                val snoozeCreate = lifecycleEventsByReminderId[snoozeMember.reminderOccurrenceId.value].orEmpty()
                    .filter { it.name == EventNameV1.REMINDER_SCHEDULED }
                    .singleOrNull()
                    ?: fail(path, "merged pair has no exact snooze creation companion")
                if (event.envelope.occurred != snoozeCreate.envelope.occurred) {
                    fail(path, "reminder_merged must share the same snooze bundle LocalStamp")
                }
                val sourceDue = flatOrNestedStamp(reminder.body.requiredElement("due_at", path), path)
                val keptDue = flatOrNestedStamp(kept.body.requiredElement("due_at", path), path)
                val distance = abs(sourceDue.occurredAtUtc.instant.toEpochMilli() - keptDue.occurredAtUtc.instant.toEpochMilli())
                requireProperty(event, "distance_ms", distance, path)
                requireProperty(event, "tie_break", if (distance == 0L) "snooze_over_fixed" else "earlier_due", path)
            }
            EventNameV1.REMINDER_CANCELLED -> {
                requireProperty(event, "resulting_status", status, path)
                if (event.properties.body.requiredString("reason", "$path.properties") == "schedule_edit") {
                    val replacedAt = scheduleReplacement
                        ?: fail(path, "schedule_edit cancellation requires schedule.replaced_at")
                    if (event.envelope.occurred != replacedAt) {
                        fail(path, "schedule_edit cancellation must share the schedule replacement LocalStamp")
                    }
                }
            }
            EventNameV1.REMINDER_BLOCKED_PERMISSION -> requireProperty(event, "status", status, path)
            EventNameV1.REMINDER_SKIPPED -> {
                requireProperty(event, "status", status, path)
                val dueAt = flatOrNestedStamp(reminder.body.requiredElement("due_at", path), "$path.due_at")
                val lateness = checkedEventToDueLateness(event, dueAt, path)
                requireProperty(event, "lateness_ms", lateness, path)
                if (status == "SKIPPED_LATE" && lateness <= 3_600_000L) {
                    fail(path, "SKIPPED_LATE requires lateness strictly greater than 60 minutes")
                }
                if (status == "SKIPPED_WORK_END" || status == "SKIPPED_LATE") {
                    val workEnd = TimeMinuteWireV1.parse(schedule.body.requiredString("work_end", schedulePath)).time
                    val resolvedWorkEnd = ReminderScheduleConformanceV1.resolveScheduleWallTime(
                        dueAt.localDate.date,
                        workEnd,
                        dueAt.zoneId,
                    )
                    val beforeWorkEnd = event.envelope.occurred.occurredAtUtc.instant.isBefore(resolvedWorkEnd.toInstant())
                    if (status == "SKIPPED_WORK_END" && beforeWorkEnd) {
                        fail(path, "SKIPPED_WORK_END must occur at or after the resolved work_end")
                    }
                    if (status == "SKIPPED_LATE" && !beforeWorkEnd) {
                        fail(path, "SKIPPED_LATE must occur strictly before the resolved work_end")
                    }
                }
            }
            else -> fail(path, "unexpected reminder resolution event ${event.name.wire}")
        }
    }

    private fun checkedEventToDueLateness(
        event: ProductEventWireV1,
        dueAt: LocalStampWireV1,
        path: String,
    ): Long {
        val lateness = try {
            Math.subtractExact(
                event.envelope.occurred.occurredAtUtc.instant.toEpochMilli(),
                dueAt.occurredAtUtc.instant.toEpochMilli(),
            )
        } catch (_: ArithmeticException) {
            fail(path, "event-to-due lateness overflows")
        }
        if (lateness < 0L) fail(path, "reminder resolution occurs before due_at")
        val encoded = event.properties.body.requiredInt64("lateness_ms", "$path companion")
        if (encoded != lateness) {
            fail(path, "reminder_skipped lateness_ms does not mirror checked event-to-due lateness")
        }
        return lateness
    }

    private fun validateWeeklySummaries(dataset: ExportDatasetWireV1, ledger: ClaimLedger) {
        dataset.weeklySummaries.forEach { summary ->
            val path = "export.weekly_summaries[${summary.summaryId}]"
            val events = ledger.takeAtLeastOne(
                CompanionRolesV1.WEEKLY_GENERATION,
                CompanionSourceTypeV1.WEEKLY_SUMMARY,
                summary.summaryId.value,
                EventNameV1.WEEKLY_SUMMARY_GENERATED,
            )
            events.forEach { event ->
                requireProperty(event, "week_start_local_date", summary.body.requiredString("week_start_local_date", path), path)
            }
            val newest = events.maxWith(compareBy<ProductEventWireV1> { it.envelope.occurred.occurredAtUtc }.thenBy { it.envelope.eventId })
            requireFlatStamp(newest, summary.body, path)
            mirror(summary.body, newest.properties.body, path, "summary_id", "week_start_local_date", "qualified_break_days", "completed_count")
        }
    }

    private fun mirrorSafetyHold(snapshot: StrictJsonObjectV1, event: ProductEventWireV1, path: String) {
        requireFlatStamp(event, snapshot, path)
        mirrorMapped(
            snapshot,
            event.properties.body,
            path,
            "kind" to "kind",
            "source_type" to "source_type",
            "local_date" to "origin_local_date",
            "zone_id" to "origin_timezone_id",
            "expires_at_utc" to "expires_at_utc",
            "rule_version" to "rule_version",
        )
        if (snapshot.requiredString("source_type", path) == "check_in") {
            requireProperty(event, "source_id", snapshot.requiredString("source_id", path), path)
        } else {
            requireEnvelope(event, "session_id", snapshot.requiredString("source_id", path), path)
        }
    }

    private fun mirrorDayModeCapUpdate(snapshot: StrictJsonObjectV1, event: ProductEventWireV1, path: String) {
        val resultingCap = snapshot.requiredElement("resulting_cap", path).asStrictObject("$path.resulting_cap")
        requireEnvelope(event, "session_id", snapshot.requiredString("trigger_session_id", path), path)
        mirrorMapped(
            snapshot,
            event.properties.body,
            path,
            "expiry_source_session_id" to "expiry_source_session_id",
            "basis_mode" to "basis_mode",
            "previous_max_mode" to "previous_cap",
            "deadline_source" to "deadline_source",
        )
        mirrorMapped(
            resultingCap,
            event.properties.body,
            path,
            "max_mode" to "new_cap",
            "occurred_at_utc" to "origin_occurred_at_utc",
            "local_date" to "origin_local_date",
            "zone_id" to "origin_timezone_id",
            "utc_offset_minutes" to "origin_utc_offset_minutes",
            "expires_at_utc" to "expires_at_utc",
            "rule_version" to "rule_version",
        )
    }

    private fun requireStamp(event: ProductEventWireV1, rawStamp: JsonElement, path: String) {
        if (rawStamp === JsonNull || event.envelope.occurred.toJson() != rawStamp) fail(path, "${event.name.wire} envelope stamp does not mirror source")
    }

    private fun requireFlatStamp(event: ProductEventWireV1, source: StrictJsonObjectV1, path: String) {
        val sourceStamp = LocalStampWireV1(
            InstantWireV1.parse(source.requiredString("occurred_at_utc", path)),
            DateWireV1.parse(source.requiredString("local_date", path)),
            source.requiredString("zone_id", path),
            source.requiredInt64("utc_offset_minutes", path),
        )
        if (event.envelope.occurred != sourceStamp) fail(path, "${event.name.wire} envelope stamp does not mirror source")
    }

    private fun flatOrNestedStamp(rawStamp: JsonElement, path: String): LocalStampWireV1 =
        LocalStampWireV1.fromObject(rawStamp.asStrictObject(path), path)

    private fun mirror(source: StrictJsonObjectV1, target: StrictJsonObjectV1, path: String, vararg keys: String) {
        keys.forEach { key ->
            if (source.requiredElement(key, path) != target.requiredElement(key, "$path companion")) {
                fail(path, "companion property '$key' does not mirror source")
            }
        }
    }

    private fun mirrorMapped(
        source: StrictJsonObjectV1,
        target: StrictJsonObjectV1,
        path: String,
        vararg keys: Pair<String, String>,
    ) {
        keys.forEach { (sourceKey, targetKey) ->
            if (source.requiredElement(sourceKey, path) != target.requiredElement(targetKey, "$path companion")) {
                fail(path, "companion property '$targetKey' does not mirror source '$sourceKey'")
            }
        }
    }

    private fun requireProperty(event: ProductEventWireV1, key: String, expected: Any?, path: String) {
        val actual = event.properties.body.requiredElement(key, "$path companion")
        val matches = when (expected) {
            null -> actual === JsonNull
            is String -> actual.asString("$path companion.$key") == expected
            is Long -> actual.asInt64("$path companion.$key") == expected
            is Int -> actual.asInt64("$path companion.$key") == expected.toLong()
            is Boolean -> actual.asBoolean("$path companion.$key") == expected
            is JsonElement -> actual == expected
            else -> false
        }
        if (!matches) fail(path, "${event.name.wire} property '$key' does not mirror source")
    }

    private fun requireEnvelope(event: ProductEventWireV1, key: String, expected: String?, path: String) {
        val actual = when (key) {
            "decision_id" -> event.envelope.decisionId?.value
            "session_id" -> event.envelope.sessionId?.value
            "reminder_occurrence_id" -> event.envelope.reminderOccurrenceId?.value
            "schedule_version_id" -> event.envelope.scheduleVersionId?.value
            "source" -> event.envelope.source?.wire
            else -> fail(path, "unknown envelope mirror '$key'")
        }
        if (actual != expected) fail(path, "${event.name.wire} envelope '$key' does not mirror source")
    }

    private data class BoundClaim(
        val eventIndex: Int,
        val event: ProductEventWireV1,
        val claim: NormalizedCompanionClaimV1,
        var consumed: Boolean = false,
    )

    private data class ClaimKey(
        val role: String,
        val sourceType: CompanionSourceTypeV1,
        val sourceId: String,
    )

    private class ClaimLedger(private val claims: List<BoundClaim>) {
        private val claimsByKey: Map<ClaimKey, List<BoundClaim>> = claims.groupBy { claim ->
            ClaimKey(claim.claim.role, claim.claim.sourceType, claim.claim.sourceId)
        }

        fun takeExact(
            role: String,
            sourceType: CompanionSourceTypeV1,
            sourceId: String,
            expectedName: EventNameV1,
        ): ProductEventWireV1 = takeExact(role, sourceType, sourceId, setOf(expectedName))

        fun takeExact(
            role: String,
            sourceType: CompanionSourceTypeV1,
            sourceId: String,
            expectedNames: Set<EventNameV1>,
        ): ProductEventWireV1 = takeExactClaim(role, sourceType, sourceId, expectedNames).event

        fun takeExactClaim(
            role: String,
            sourceType: CompanionSourceTypeV1,
            sourceId: String,
            expectedName: EventNameV1,
        ): BoundClaim = takeExactClaim(role, sourceType, sourceId, setOf(expectedName))

        private fun takeExactClaim(
            role: String,
            sourceType: CompanionSourceTypeV1,
            sourceId: String,
            expectedNames: Set<EventNameV1>,
        ): BoundClaim {
            val matches = matching(role, sourceType, sourceId)
            if (matches.size != 1) fail(
                "export.companions",
                "expected exactly one $role companion for ${sourceType.wire}:$sourceId, found ${matches.size}",
            )
            val match = matches.single()
            if (match.event.name !in expectedNames) fail(
                "export.events[${match.eventIndex}]",
                "$role companion has wrong event ${match.event.name.wire}",
            )
            match.consumed = true
            return match
        }

        fun takeAll(
            role: String,
            sourceType: CompanionSourceTypeV1,
            sourceId: String,
            expectedName: EventNameV1,
        ): List<ProductEventWireV1> {
            val matches = matching(role, sourceType, sourceId)
            matches.forEach { match ->
                if (match.event.name != expectedName) fail(
                    "export.events[${match.eventIndex}]",
                    "$role companion has wrong event ${match.event.name.wire}",
                )
                match.consumed = true
            }
            return matches.map { it.event }
        }

        fun takeAtLeastOne(
            role: String,
            sourceType: CompanionSourceTypeV1,
            sourceId: String,
            expectedName: EventNameV1,
        ): List<ProductEventWireV1> = takeAll(role, sourceType, sourceId, expectedName).also {
            if (it.isEmpty()) fail(
                "export.companions",
                "expected at least one $role companion for ${sourceType.wire}:$sourceId",
            )
        }

        fun requireFullyConsumed() {
            claims.firstOrNull { !it.consumed }?.let { orphan ->
                fail(
                    "export.events[${orphan.eventIndex}]",
                    "unexpected/orphan companion '${orphan.claim.role}' for ${orphan.claim.sourceType.wire}:${orphan.claim.sourceId}",
                )
            }
        }

        private fun matching(role: String, sourceType: CompanionSourceTypeV1, sourceId: String): List<BoundClaim> =
            claimsByKey[ClaimKey(role, sourceType, sourceId)].orEmpty().filterNot { it.consumed }
    }

    private const val APP_PROFILE_ID = "1"
    private const val VISITING = 1
    private const val VISITED = 2
}
