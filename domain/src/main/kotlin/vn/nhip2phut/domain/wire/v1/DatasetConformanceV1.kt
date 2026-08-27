package vn.nhip2phut.domain.wire.v1

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.TreeMap
import vn.nhip2phut.domain.events.EventContractRegistryV1
import vn.nhip2phut.domain.events.EventNameV1
import vn.nhip2phut.domain.events.ProductEventWireV1
import vn.nhip2phut.domain.events.RefTargetTypeV1

/** Cross-record checks that are shared by the exporter and offline importer. */
object DatasetConformanceV1 {
    fun requireValid(dataset: ExportDatasetWireV1) {
        validateCounts(dataset)
        validateProfileBoundary(dataset)
        val capContext = buildCapConformanceContext(dataset)
        validateEntityOrderAndReferences(dataset, capContext)
        validateWeeklySummaryContextRates(dataset)
        validateEvents(dataset, capContext)
        CompanionConformanceV1.requireValid(dataset)
    }

    private fun buildCapConformanceContext(dataset: ExportDatasetWireV1): CapConformanceContextV1 {
        val sessions = dataset.sessions.associateBy { it.sessionId.value }
        val feedbackBySession = dataset.feedback.associateBy { it.sessionId.value }
        val capUpdateEventsBySession = groupCapUpdateEventsBySession(dataset.events)
        val ownerCommitsByResult = HashMap<JsonElement, MutableList<CapResultOwnerCommitV1>>()
        feedbackBySession.values.forEach { feedback ->
            val feedbackPath = "export.feedback[${feedback.sessionId}]"
            val rawUpdate = feedback.body.requiredElement("day_mode_cap_update_snapshot", feedbackPath)
            if (rawUpdate === JsonNull) return@forEach
            val updatePath = "$feedbackPath.day_mode_cap_update_snapshot"
            val update = rawUpdate.asStrictObject(updatePath)
            val result = update.requiredElement("resulting_cap", updatePath)
            val commits = capUpdateEventsBySession[feedback.sessionId.value].orEmpty()
            if (commits.size != 1) {
                fail(updatePath, "cap update requires exactly one day_mode_cap_updated commit event")
            }
            ownerCommitsByResult.getOrPut(result) { ArrayList() }.add(
                CapResultOwnerCommitV1(
                    sessionId = feedback.sessionId.value,
                    commitAt = commits.single().envelope.occurred.occurredAtUtc,
                ),
            )
        }
        val resultOwners = ownerCommitsByResult.mapValues { (_, owners) ->
            val earliest = owners.minWithOrNull(compareBy<CapResultOwnerCommitV1> { it.commitAt }.thenBy { it.sessionId })!!
            CapResultOwnersV1(
                ownerCount = owners.size,
                earliestOwnerSessionId = earliest.sessionId,
                earliestCommitAt = earliest.commitAt,
            )
        }
        return CapConformanceContextV1(
            sessions = sessions,
            feedbackBySession = feedbackBySession,
            capUpdateEventsBySession = capUpdateEventsBySession,
            resultOwners = resultOwners,
        )
    }

    private fun validateCounts(dataset: ExportDatasetWireV1) {
        val expected = RecordCountsWireV1(
            dataset.profile.size.toLong(),
            dataset.workSchedule.size.toLong(),
            dataset.checkIns.size.toLong(),
            dataset.decisions.size.toLong(),
            dataset.sessions.size.toLong(),
            dataset.feedback.size.toLong(),
            dataset.reminders.size.toLong(),
            dataset.events.size.toLong(),
            dataset.weeklySummaries.size.toLong(),
        )
        if (dataset.metadata.recordCounts != expected) fail("export.metadata.record_counts", "counts do not match the snapshot arrays")
    }

    private fun validateProfileBoundary(dataset: ExportDatasetWireV1) {
        if (dataset.profile.size > 1) fail("export.profile", "profile permits at most one record")
        val graphSize = dataset.workSchedule.size + dataset.checkIns.size + dataset.decisions.size + dataset.sessions.size +
            dataset.feedback.size + dataset.reminders.size + dataset.events.size + dataset.weeklySummaries.size
        if (dataset.profile.isEmpty() && graphSize != 0) fail("export.profile", "empty profile is valid only for an otherwise empty dataset")
    }

    private fun validateEntityOrderAndReferences(
        dataset: ExportDatasetWireV1,
        capContext: CapConformanceContextV1,
    ) {
        requireUuidOrder("export.work_schedule", dataset.workSchedule.map { it.scheduleVersionId })
        requireUuidOrder("export.check_ins", dataset.checkIns.map { it.checkInId })
        requireUuidOrder("export.decisions", dataset.decisions.map { it.decisionId })
        requireUuidOrder("export.sessions", dataset.sessions.map { it.sessionId })
        requireUuidOrder("export.feedback", dataset.feedback.map { it.sessionId })
        requireUuidOrder("export.reminders", dataset.reminders.map { it.reminderOccurrenceId })
        dataset.weeklySummaries.zipWithNext().forEachIndexed { index, (left, right) ->
            val compareDate = left.weekStartLocalDate.compareTo(right.weekStartLocalDate)
            if (compareDate >= 0) {
                fail("export.weekly_summaries", "records are not strictly sorted at index ${index + 1}")
            }
        }
        requireUnique("work_schedule.schedule_version_id", dataset.workSchedule.map { it.scheduleVersionId.value })
        requireUnique("check_ins.check_in_id", dataset.checkIns.map { it.checkInId.value })
        requireUnique("decisions.decision_id", dataset.decisions.map { it.decisionId.value })
        requireUnique("sessions.session_id", dataset.sessions.map { it.sessionId.value })
        requireUnique("feedback.session_id", dataset.feedback.map { it.sessionId.value })
        requireUnique("reminders.reminder_occurrence_id", dataset.reminders.map { it.reminderOccurrenceId.value })
        requireUnique("weekly_summaries.summary_id", dataset.weeklySummaries.map { it.summaryId.value })

        val schedules = dataset.workSchedule.associateBy { it.scheduleVersionId.value }
        val checkIns = dataset.checkIns.associateBy { it.checkInId.value }
        val decisions = dataset.decisions.associateBy { it.decisionId.value }
        val sessions = capContext.sessions
        val feedbackBySession = capContext.feedbackBySession
        val reminders = dataset.reminders.associateBy { it.reminderOccurrenceId.value }

        if (dataset.workSchedule.isNotEmpty() && dataset.workSchedule.count { it.body.isNull("replaced_at") } != 1) {
            fail("export.work_schedule", "retained schedule graph must have exactly one unreplaced version")
        }
        dataset.checkIns.forEach { checkIn ->
            val path = "export.check_ins[${checkIn.checkInId}]"
            val schedule = checkIn.body.requiredString("schedule_version_id", path)
            if (schedule !in schedules) fail(path, "dangling schedule_version_id")
            checkIn.body.nullableString("parent_id", path)?.let { parent ->
                if (parent !in checkIns || parent == checkIn.checkInId.value) fail(path, "invalid parent_id")
            }
        }
        rejectCheckInCycles(dataset.checkIns)
        dataset.decisions.forEach { decision ->
            val path = "export.decisions[${decision.decisionId}]"
            val checkIn = checkIns[decision.body.requiredString("check_in_id", path)] ?: fail(path, "dangling check_in_id")
            val schedule = decision.body.requiredString("schedule_version_id", path)
            if (schedule !in schedules || schedule != checkIn.body.requiredString("schedule_version_id", "source CheckIn")) fail(path, "Decision/CheckIn schedule mismatch")
            if (decision.body.requiredInt64("rule_version", path) != checkIn.body.requiredInt64("rule_version", "source CheckIn")) fail(path, "Decision/CheckIn rule version mismatch")
            if (decision.body.requiredElement("created_at", path) != checkIn.body.requiredElement("confirmed_at", "source CheckIn")) fail(path, "Decision created_at must mirror CheckIn confirmed_at")
            DecisionFreshnessEvidenceSchemaV1.keys.forEach { key ->
                if (decision.body.requiredElement(key, path) != checkIn.body.requiredElement(key, "source CheckIn")) {
                    fail(path, "Decision/CheckIn freshness field '$key' mismatch")
                }
            }
            decision.body.requiredElement("evaluation_day_mode_cap_snapshot", path)
                .takeUnless { it === JsonNull }
                ?.let { raw ->
                    validateDayModeCapSnapshotProvenance(
                        raw.asStrictObject("$path.evaluation_day_mode_cap_snapshot"),
                        capContext,
                        "$path.evaluation_day_mode_cap_snapshot",
                        CapConsumerV1(
                            occurredAtUtc = localStampInstant(decision.body, "created_at", path),
                            descendantDecisionId = decision.decisionId.value,
                        ),
                    )
                }
        }
        dataset.sessions.forEach { session ->
            val path = "export.sessions[${session.sessionId}]"
            val decision = decisions[session.body.requiredString("decision_id", path)] ?: fail(path, "dangling decision_id")
            val schedule = session.body.requiredString("schedule_version_id", path)
            if (schedule !in schedules || schedule != decision.body.requiredString("schedule_version_id", "source Decision")) fail(path, "Session/Decision schedule mismatch")
            validateSessionModeLineage(
                session,
                decision,
                capContext,
                path,
            )
            session.body.nullableString("reminder_occurrence_id", path)?.let { occurrence ->
                val reminder = reminders[occurrence] ?: fail(path, "dangling reminder_occurrence_id")
                if (reminder.body.requiredString("schedule_version_id", "source Reminder") != schedule) fail(path, "Session/Reminder schedule mismatch")
                if (reminder.body.requiredString("status", "source Reminder") != "DELIVERED" || !reminder.body.hasNonNull("first_opened_at")) fail(path, "reminder-start Session requires opened DELIVERED occurrence")
            }
        }
        dataset.feedback.forEach { feedback ->
            val sourceSession = sessions[feedback.sessionId.value]
                ?: fail("export.feedback[${feedback.sessionId}]", "dangling session_id")
            validateFeedbackCapUpdate(
                feedback,
                sourceSession,
                capContext,
            )
            if (sourceSession.body.requiredString("status", "source Session") == "ACTIVE") {
                fail("export.feedback[${feedback.sessionId}]", "ACTIVE Session must have zero Feedback")
            }
        }
        validateRetainedCapUpdateLineage(capContext)
        val feedbackSessionIds = feedbackBySession.keys
        dataset.sessions.forEach { session ->
            val path = "export.sessions[${session.sessionId}]"
            val hasFeedback = session.sessionId.value in feedbackSessionIds
            when (session.body.requiredString("status", path)) {
                "ACTIVE" -> if (hasFeedback) fail(path, "ACTIVE Session must have zero Feedback")
                else -> if (!hasFeedback) fail(path, "terminal Session must have exactly one Feedback")
            }
        }
        requireAcyclicCapEntityDependencies(dataset.decisions, dataset.sessions)
        dataset.reminders.forEach { reminder ->
            val path = "export.reminders[${reminder.reminderOccurrenceId}]"
            if (reminder.body.requiredString("schedule_version_id", path) !in schedules) fail(path, "dangling schedule_version_id")
            listOf("parent_occurrence_id", "supersedes_occurrence_id", "merged_into_occurrence_id").forEach { key ->
                if (reminder.body.hasKey(key)) reminder.body.nullableString(key, path)?.let { target -> if (target !in reminders) fail(path, "dangling $key") }
            }
        }
        requireValidReminderOccurrences(dataset.reminders)
        ReminderScheduleConformanceV1.requireValid(schedules, dataset.reminders)
    }

    private fun validateWeeklySummaryContextRates(dataset: ExportDatasetWireV1) {
        val feedbackBySession = dataset.feedback.associateBy { it.sessionId.value }
        dataset.weeklySummaries.forEach { summary ->
            val path = "export.weekly_summaries[${summary.summaryId}]"
            val weekStart = summary.weekStartLocalDate.date
            val retainedInWeekSessions = dataset.sessions.filter { session ->
                val sessionPath = "export.sessions[${session.sessionId}]"
                val startedAt = session.body.requiredElement("started_at", sessionPath)
                    .asStrictObject("$sessionPath.started_at")
                val localDate = DateWireV1.parse(startedAt.requiredString("local_date", "$sessionPath.started_at")).date
                java.time.temporal.ChronoUnit.DAYS.between(weekStart, localDate) in 0L..6L
            }
            val cachedStartedCount = summary.body.requiredInt64("started_count", path)
            if (retainedInWeekSessions.size.toLong() != cachedStartedCount) return@forEach

            var expectedNumerator = 0L
            var expectedDenominator = 0L
            retainedInWeekSessions.forEach { session ->
                val sessionPath = "export.sessions[${session.sessionId}]"
                if (session.body.requiredString("status", sessionPath) != "COMPLETED") return@forEach
                val feedback = feedbackBySession[session.sessionId.value]
                    ?: fail(path, "complete weekly raw graph is missing terminal Feedback")
                when (feedback.body.nullableString("context_fit", "export.feedback[${feedback.sessionId}]")) {
                    "yes" -> {
                        expectedNumerator = Math.addExact(expectedNumerator, 1L)
                        expectedDenominator = Math.addExact(expectedDenominator, 1L)
                    }
                    "no" -> expectedDenominator = Math.addExact(expectedDenominator, 1L)
                }
            }

            val ratePath = "$path.context_fit_rate"
            val rate = summary.body.requiredElement("context_fit_rate", path).asStrictObject(ratePath)
            if (rate.requiredInt64("numerator", ratePath) != expectedNumerator ||
                rate.requiredInt64("denominator", ratePath) != expectedDenominator
            ) {
                fail(path, "context_fit_rate must match the completed Session raw cohort")
            }
        }
    }

    private fun validateSessionModeLineage(
        session: SessionWireV1,
        decision: DecisionWireV1,
        capContext: CapConformanceContextV1,
        path: String,
    ) {
        val sourceDecisionMode = decision.body.nullableString("effective_mode", "source Decision")
            ?: fail(path, "Session requires a mode-bearing source Decision")
        val decisionModeAtStart = session.body.requiredString("decision_effective_mode_at_start", path)
        if (decisionModeAtStart != sourceDecisionMode) {
            fail(path, "decision_effective_mode_at_start must mirror source Decision.effective_mode")
        }

        val runtimeMode = session.body.requiredString("runtime_effective_mode_at_start", path)
        if (modeRank(runtimeMode) > modeRank(sourceDecisionMode)) {
            fail(path, "runtime_effective_mode_at_start exceeds source Decision.effective_mode")
        }
        val rawSnapshot = session.body.requiredElement("runtime_day_mode_cap_snapshot_at_start", path)
        val strictReduction = modeRank(runtimeMode) < modeRank(sourceDecisionMode)
        if ((rawSnapshot !== JsonNull) != strictReduction) {
            fail(path, "runtime cap snapshot must be non-null iff start mode strictly reduces source Decision.effective_mode")
        }
        if (rawSnapshot === JsonNull) return

        val snapshotPath = "$path.runtime_day_mode_cap_snapshot_at_start"
        val snapshot = rawSnapshot.asStrictObject(snapshotPath)
        if (snapshot.requiredString("decision_effective_mode_before_runtime_cap", snapshotPath) != sourceDecisionMode ||
            snapshot.requiredString("runtime_effective_mode_at_start", snapshotPath) != runtimeMode
        ) {
            fail(snapshotPath, "runtime cap snapshot modes must mirror source Decision and Session")
        }
        val appliedCapPath = "$snapshotPath.applied_cap"
        val appliedCap = snapshot.requiredElement("applied_cap", snapshotPath).asStrictObject(appliedCapPath)
        val expectedRuntime = minOf(modeRank(sourceDecisionMode), modeRank(appliedCap.requiredString("max_mode", appliedCapPath)))
        if (modeRank(runtimeMode) != expectedRuntime) {
            fail(snapshotPath, "runtime mode must equal min(source Decision.effective_mode, applied cap max_mode)")
        }
        validateDayModeCapSnapshotProvenance(
            appliedCap,
            capContext,
            appliedCapPath,
            CapConsumerV1(
                occurredAtUtc = localStampInstant(session.body, "started_at", path),
                sessionId = session.sessionId.value,
            ),
        )
    }

    private fun validateFeedbackCapUpdate(
        feedback: FeedbackWireV1,
        sourceSession: SessionWireV1,
        capContext: CapConformanceContextV1,
    ) {
        val path = "export.feedback[${feedback.sessionId}]"
        val rawUpdate = feedback.body.requiredElement("day_mode_cap_update_snapshot", path)
        if (rawUpdate === JsonNull) return

        val updatePath = "$path.day_mode_cap_update_snapshot"
        val update = rawUpdate.asStrictObject(updatePath)
        val triggerSessionId = update.requiredString("trigger_session_id", updatePath)
        if (triggerSessionId != feedback.sessionId.value) {
            fail(updatePath, "trigger_session_id must mirror Feedback.session_id")
        }
        val resultingCapPath = "$updatePath.resulting_cap"
        val resultingCap = update.requiredElement("resulting_cap", updatePath).asStrictObject(resultingCapPath)
        val expirySourceSessionId = update.requiredString("expiry_source_session_id", updatePath)
        if (expirySourceSessionId != resultingCap.requiredString("source_session_id", resultingCapPath)) {
            fail(updatePath, "expiry_source_session_id must mirror resulting_cap.source_session_id")
        }

        val basisMode = update.requiredString("basis_mode", updatePath)
        val previousMaxMode = update.nullableString("previous_max_mode", updatePath)
        val expectedBasis = previousMaxMode
            ?: sourceSession.body.requiredString("runtime_effective_mode_at_start", "source Session")
        if (basisMode != expectedBasis) {
            fail(updatePath, "basis_mode does not match previous cap or triggering Session runtime ceiling")
        }
        val expectedMaxMode = if (basisMode == "BUILD") "MAINTAIN" else "RECOVER"
        val resultingMaxMode = resultingCap.requiredString("max_mode", resultingCapPath)
        if (resultingMaxMode != expectedMaxMode) {
            fail(updatePath, "resulting cap does not lower basis_mode by the canonical one-step rule")
        }
        val modeTriggerSessionId = resultingCap.requiredString("mode_trigger_session_id", resultingCapPath)
        if ((previousMaxMode == null || modeRank(resultingMaxMode) < modeRank(basisMode)) && modeTriggerSessionId != triggerSessionId) {
            fail(updatePath, "new or strictly lower cap must use trigger_session_id as mode trigger provenance")
        }
        val deadlineSource = update.requiredString("deadline_source", updatePath)
        when (deadlineSource) {
            "candidate_later" -> if (expirySourceSessionId != triggerSessionId) {
                fail(updatePath, "candidate_later must use trigger_session_id as expiry source provenance")
            }
            "existing_later", "same" -> if (previousMaxMode == null) {
                fail(updatePath, "existing_later/same requires a previous cap")
            }
        }
        validateDayModeCapSnapshotProvenance(
            resultingCap,
            capContext,
            resultingCapPath,
        )
    }

    private fun validateRetainedCapUpdateLineage(
        capContext: CapConformanceContextV1,
    ) {
        val updates = capContext.feedbackBySession.values.mapNotNull { feedback ->
            retainedCapUpdate(feedback, capContext)
        }
        val updatesBySession = updates.associateBy { it.sessionId }
        val unambiguousPreviousBySession = unambiguousPreviousCapUpdates(updates)

        updates.forEach { update ->
            if (update.deadlineSource == "candidate_later") {
                if (update.resultingExpiryEvidence != update.candidateExpiryEvidence) {
                    fail(update.path, "candidate_later must adopt the triggering Session origin/expiry/clock evidence")
                }
                unambiguousPreviousBySession[update.sessionId]?.let { previous ->
                    validateComparableDeadlineSource(
                        deadlineSource = update.deadlineSource,
                        existing = previous.resultingExpiryEvidence,
                        candidate = update.candidateExpiryEvidence,
                        path = update.path,
                    )
                }
            }
        }

        updates.filter { it.previousMaxMode != null }.forEach { update ->
            if (update.resultingMaxMode == update.previousMaxMode) {
                val modeEstablisher = updatesBySession[update.modeTriggerSessionId]
                    ?: fail(update.path, "non-lowering cap update has no retained mode-trigger provenance")
                if (!modeEstablisher.establishesMode(update.previousMaxMode)) {
                    fail(update.path, "non-lowering cap update must inherit mode trigger from a retained lowering commit")
                }
                requireNotAfter(
                    establisher = modeEstablisher,
                    consumer = update,
                    message = "non-lowering cap update cannot inherit mode trigger from a later cap commit",
                )
            }

            if (update.deadlineSource != "candidate_later") {
                val sourceEstablisher = updatesBySession[update.sourceSessionId]
                    ?: fail(update.path, "${update.deadlineSource} has no retained expiry-source provenance")
                if (sourceEstablisher.deadlineSource != "candidate_later" ||
                    sourceEstablisher.sourceSessionId != update.sourceSessionId
                ) {
                    fail(update.path, "${update.deadlineSource} must inherit expiry source from its retained candidate commit")
                }
                if (update.resultingExpiryEvidence != sourceEstablisher.resultingExpiryEvidence) {
                    fail(update.path, "${update.deadlineSource} must preserve retained previous expiry source evidence")
                }
                validateComparableDeadlineSource(
                    deadlineSource = update.deadlineSource,
                    existing = sourceEstablisher.resultingExpiryEvidence,
                    candidate = update.candidateExpiryEvidence,
                    path = update.path,
                )
                requireNotAfter(
                    establisher = sourceEstablisher,
                    consumer = update,
                    message = "${update.deadlineSource} cannot inherit expiry source from a later cap commit",
                )
            }
        }

        updates.groupBy { it.commitAt }.values.forEach { sameMillisecond ->
            validateSameMillisecondCapUpdates(sameMillisecond, updatesBySession)
        }
    }

    private fun unambiguousPreviousCapUpdates(
        updates: List<RetainedCapUpdateV1>,
    ): Map<String, RetainedCapUpdateV1> {
        val groupsByCommit = TreeMap<InstantWireV1, MutableList<RetainedCapUpdateV1>>()
        updates.forEach { update -> groupsByCommit.getOrPut(update.commitAt) { ArrayList() }.add(update) }
        val previousBySession = HashMap<String, RetainedCapUpdateV1>()
        var previousGroup: List<RetainedCapUpdateV1> = emptyList()
        groupsByCommit.values.forEach { currentGroup ->
            if (currentGroup.size == 1 && previousGroup.size == 1) {
                val current = currentGroup.single()
                val previous = previousGroup.single()
                if (current.previousMaxMode != null && previous.resultingMaxMode == current.previousMaxMode) {
                    previousBySession[current.sessionId] = previous
                }
            }
            previousGroup = currentGroup
        }
        return previousBySession
    }

    private fun validateComparableDeadlineSource(
        deadlineSource: String,
        existing: CapExpiryEvidenceV1,
        candidate: CapExpiryEvidenceV1,
        path: String,
    ) {
        if (existing.originBootMarker != candidate.originBootMarker) return
        val comparison = existing.monotonicDeadlineMs.compareTo(candidate.monotonicDeadlineMs)
        when (deadlineSource) {
            "candidate_later" -> if (comparison >= 0) {
                fail(path, "candidate_later requires a strictly later comparable candidate deadline")
            }
            "existing_later" -> if (comparison <= 0) {
                fail(path, "existing_later requires a strictly later comparable existing deadline")
            }
            "same" -> if (comparison != 0) {
                fail(path, "same requires equal comparable effective deadlines")
            }
        }
    }

    private fun retainedCapUpdate(
        feedback: FeedbackWireV1,
        capContext: CapConformanceContextV1,
    ): RetainedCapUpdateV1? {
        val feedbackPath = "export.feedback[${feedback.sessionId}]"
        val rawUpdate = feedback.body.requiredElement("day_mode_cap_update_snapshot", feedbackPath)
        if (rawUpdate === JsonNull) return null

        val path = "$feedbackPath.day_mode_cap_update_snapshot"
        val update = rawUpdate.asStrictObject(path)
        val commits = capContext.capUpdateEventsBySession[feedback.sessionId.value].orEmpty()
        if (commits.size != 1) {
            fail(path, "cap update requires exactly one day_mode_cap_updated commit event")
        }
        val resultingCapPath = "$path.resulting_cap"
        val resultingCap = update.requiredElement("resulting_cap", path).asStrictObject(resultingCapPath)
        val sourceSession = capContext.sessions[feedback.sessionId.value]
            ?: fail(path, "cap update has no retained triggering Session")
        return RetainedCapUpdateV1(
            sessionId = feedback.sessionId.value,
            previousMaxMode = update.nullableString("previous_max_mode", path),
            resultingMaxMode = resultingCap.requiredString("max_mode", "$path.resulting_cap"),
            modeTriggerSessionId = resultingCap.requiredString("mode_trigger_session_id", "$path.resulting_cap"),
            sourceSessionId = resultingCap.requiredString("source_session_id", "$path.resulting_cap"),
            deadlineSource = update.requiredString("deadline_source", path),
            resultingExpiryEvidence = capExpiryEvidence(resultingCap, resultingCapPath),
            candidateExpiryEvidence = sessionOriginExpiryEvidence(sourceSession),
            commitAt = commits.single().envelope.occurred.occurredAtUtc,
            path = path,
        )
    }

    private fun capExpiryEvidence(
        cap: StrictJsonObjectV1,
        path: String,
    ): CapExpiryEvidenceV1 {
        val clockPath = "$path.clock_integrity"
        val clock = cap.requiredElement("clock_integrity", path).asStrictObject(clockPath)
        return CapExpiryEvidenceV1(
            occurredAtUtc = cap.requiredElement("occurred_at_utc", path),
            localDate = cap.requiredElement("local_date", path),
            zoneId = cap.requiredElement("zone_id", path),
            utcOffsetMinutes = cap.requiredElement("utc_offset_minutes", path),
            expiresAtUtc = cap.requiredElement("expires_at_utc", path),
            clockIntegrity = clock.element,
            originBootMarker = clock.requiredInt64("origin_boot_marker", clockPath),
            monotonicDeadlineMs = clock.requiredInt64("monotonic_deadline_ms", clockPath),
        )
    }

    private fun sessionOriginExpiryEvidence(session: SessionWireV1): CapExpiryEvidenceV1 {
        val path = "export.sessions[${session.sessionId}]"
        val terminalPath = "$path.terminal_at"
        val terminal = session.body.requiredElement("terminal_at", path).asStrictObject(terminalPath)
        val clockPath = "$path.session_origin_clock_integrity"
        val clock = session.body.requiredElement("session_origin_clock_integrity", path).asStrictObject(clockPath)
        return CapExpiryEvidenceV1(
            occurredAtUtc = terminal.requiredElement("occurred_at_utc", terminalPath),
            localDate = terminal.requiredElement("local_date", terminalPath),
            zoneId = terminal.requiredElement("zone_id", terminalPath),
            utcOffsetMinutes = terminal.requiredElement("utc_offset_minutes", terminalPath),
            expiresAtUtc = session.body.requiredElement("session_origin_day_expires_at_utc", path),
            clockIntegrity = clock.element,
            originBootMarker = clock.requiredInt64("origin_boot_marker", clockPath),
            monotonicDeadlineMs = clock.requiredInt64("monotonic_deadline_ms", clockPath),
        )
    }

    private fun requireNotAfter(
        establisher: RetainedCapUpdateV1,
        consumer: RetainedCapUpdateV1,
        message: String,
    ) {
        if (establisher.commitAt > consumer.commitAt) fail(consumer.path, message)
    }

    private fun validateSameMillisecondCapUpdates(
        updates: List<RetainedCapUpdateV1>,
        updatesBySession: Map<String, RetainedCapUpdateV1>,
    ) {
        if (updates.size < 2) return
        val newCaps = updates.filter { it.previousMaxMode == null }
        val strictLowers = updates.filter { it.previousMaxMode == "MAINTAIN" }
        val nonLowering = updates.filter { it.previousMaxMode == "RECOVER" }
        if (newCaps.size > 1 || strictLowers.size > 1) {
            fail("export.feedback", "same-millisecond cap updates have no semantic linearization")
        }

        val newCap = newCaps.singleOrNull()
        val strictLower = strictLowers.singleOrNull()
        if (newCap?.resultingMaxMode == "RECOVER" && strictLower != null) {
            fail("export.feedback", "same-millisecond cap updates have no semantic linearization")
        }
        if (newCap?.resultingMaxMode == "MAINTAIN" && strictLower == null && nonLowering.isNotEmpty()) {
            fail("export.feedback", "same-millisecond cap updates have no semantic linearization")
        }

        val inGroupModeEstablisher = when {
            strictLower != null -> strictLower
            newCap?.resultingMaxMode == "RECOVER" -> newCap
            else -> null
        }
        val inheritedModeTrigger = inGroupModeEstablisher?.modeTriggerSessionId
            ?: nonLowering.firstOrNull()?.modeTriggerSessionId
        if (nonLowering.any { it.modeTriggerSessionId != inheritedModeTrigger }) {
            fail("export.feedback", "same-millisecond non-lowering cap updates do not inherit one mode trigger")
        }
        if (inGroupModeEstablisher != null && inheritedModeTrigger != inGroupModeEstablisher.sessionId) {
            fail("export.feedback", "same-millisecond cap updates do not inherit the in-group mode trigger")
        }

        if (newCap != null && strictLower != null && strictLower.deadlineSource != "candidate_later" &&
            strictLower.sourceSessionId != newCap.sourceSessionId
        ) {
            fail("export.feedback", "same-millisecond cap updates cannot preserve an unestablished expiry source")
        }
        if (newCap == null && strictLower != null && strictLower.deadlineSource != "candidate_later") {
            val sourceEstablisher = updatesBySession[strictLower.sourceSessionId]
            if (sourceEstablisher?.commitAt == strictLower.commitAt) {
                fail("export.feedback", "same-millisecond cap updates cannot preserve an expiry source established later in semantic order")
            }
        }

        val phaseSource = strictLower?.sourceSessionId ?: newCap?.sourceSessionId
        val candidateSources = nonLowering.asSequence()
            .filter { it.deadlineSource == "candidate_later" }
            .mapTo(HashSet()) { it.sourceSessionId }
        val preservedSources = nonLowering.asSequence()
            .filter { it.deadlineSource != "candidate_later" }
            .mapTo(HashSet()) { it.sourceSessionId }
        val unestablishedPreservedSources = preservedSources - candidateSources - setOfNotNull(phaseSource)
        val externalSourceAllowance = if (phaseSource == null) 1 else 0
        if (unestablishedPreservedSources.size > externalSourceAllowance) {
            fail("export.feedback", "same-millisecond cap updates cannot preserve multiple unestablished expiry sources")
        }
    }

    private fun validateDayModeCapSnapshotProvenance(
        snapshot: StrictJsonObjectV1,
        capContext: CapConformanceContextV1,
        path: String,
        consumer: CapConsumerV1? = null,
    ) {
        val sessions = capContext.sessions
        val feedbackBySession = capContext.feedbackBySession
        val capUpdateEventsBySession = capContext.capUpdateEventsBySession
        val modeTriggerSessionId = snapshot.requiredString("mode_trigger_session_id", path)
        val modeTriggerSession = sessions[modeTriggerSessionId]
            ?: fail(path, "dangling mode_trigger_session_id")
        val triggerFeedback = feedbackBySession[modeTriggerSessionId]
            ?: fail(path, "mode trigger Feedback provenance is missing")
        val triggerFeedbackPath = "export.feedback[${triggerFeedback.sessionId}]"
        val triggerUpdateRaw = triggerFeedback.body.requiredElement("day_mode_cap_update_snapshot", triggerFeedbackPath)
        if (triggerUpdateRaw === JsonNull) fail(path, "mode trigger Feedback provenance is missing")
        val triggerUpdatePath = "$triggerFeedbackPath.day_mode_cap_update_snapshot"
        val triggerUpdate = triggerUpdateRaw.asStrictObject(triggerUpdatePath)
        val triggerResultPath = "$triggerUpdatePath.resulting_cap"
        val triggerResult = triggerUpdate.requiredElement("resulting_cap", triggerUpdatePath).asStrictObject(triggerResultPath)
        if (triggerUpdate.requiredString("trigger_session_id", triggerUpdatePath) != modeTriggerSessionId ||
            triggerResult.requiredString("mode_trigger_session_id", triggerResultPath) != modeTriggerSessionId ||
            triggerResult.requiredString("max_mode", triggerResultPath) != snapshot.requiredString("max_mode", path)
        ) {
            fail(path, "mode trigger Feedback provenance does not establish the snapshot max_mode")
        }

        val sourceSessionId = snapshot.requiredString("source_session_id", path)
        val sourceSession = sessions[sourceSessionId] ?: fail(path, "dangling source_session_id")
        val resultOwners = capContext.resultOwners[snapshot.element]
        if (resultOwners == null || resultOwners.ownerCount == 0) {
            fail(path, "cap snapshot must deep-match a retained Feedback resulting_cap")
        }
        val triggerCommits = capUpdateEventsBySession[modeTriggerSessionId].orEmpty()
        if (triggerCommits.size != 1) {
            fail(path, "mode trigger Feedback provenance requires exactly one day_mode_cap_updated commit event")
        }
        val triggerCommit = triggerCommits.single()
        if (triggerCommit.envelope.occurred.occurredAtUtc > resultOwners.earliestCommitAt) {
            fail(
                path,
                "mode trigger commit occurs after an inheriting cap update for Session ${resultOwners.earliestOwnerSessionId}",
            )
        }

        val triggerTerminal = requireTerminalStamp(modeTriggerSession, "mode trigger", path)
        val sourceTerminal = requireTerminalStamp(sourceSession, "expiry source", path)
        consumer?.let { context ->
            validateCapReferenceCausality(
                session = modeTriggerSession,
                terminalStamp = triggerTerminal,
                role = "mode trigger",
                consumer = context,
                path = path,
            )
            if (sourceSessionId != modeTriggerSessionId) {
                validateCapReferenceCausality(
                    session = sourceSession,
                    terminalStamp = sourceTerminal,
                    role = "expiry source",
                    consumer = context,
                    path = path,
                )
            }
            if (triggerCommit.envelope.occurred.occurredAtUtc > context.occurredAtUtc) {
                fail(path, "mode trigger day_mode_cap_updated commit occurs after cap consumer")
            }
            if (resultOwners.earliestCommitAt > context.occurredAtUtc) {
                fail(path, "matching day_mode_cap_updated commit occurs after cap consumer")
            }
        }

        val sourcePath = "export.sessions[${sourceSession.sessionId}]"
        val terminalStampPath = "$sourcePath.terminal_at"
        listOf("occurred_at_utc", "local_date", "zone_id", "utc_offset_minutes").forEach { key ->
            if (snapshot.requiredElement(key, path) != sourceTerminal.requiredElement(key, terminalStampPath)) {
                fail(path, "cap origin stamp does not mirror source Session terminal provenance")
            }
        }
        if (snapshot.requiredElement("expires_at_utc", path) != sourceSession.body.requiredElement("session_origin_day_expires_at_utc", sourcePath) ||
            snapshot.requiredElement("clock_integrity", path) != sourceSession.body.requiredElement("session_origin_clock_integrity", sourcePath)
        ) {
            fail(path, "cap expiry/clock evidence does not mirror source Session provenance")
        }
    }

    private fun requireTerminalStamp(
        session: SessionWireV1,
        role: String,
        consumerPath: String,
    ): StrictJsonObjectV1 {
        val sessionPath = "export.sessions[${session.sessionId}]"
        if (session.body.requiredString("status", sessionPath) == "ACTIVE") {
            fail(consumerPath, "cap $role Session must be terminal")
        }
        val rawTerminal = session.body.requiredElement("terminal_at", sessionPath)
        if (rawTerminal === JsonNull) {
            fail(consumerPath, "cap $role Session is missing terminal provenance")
        }
        return rawTerminal.asStrictObject("$sessionPath.terminal_at")
    }

    private fun validateCapReferenceCausality(
        session: SessionWireV1,
        terminalStamp: StrictJsonObjectV1,
        role: String,
        consumer: CapConsumerV1,
        path: String,
    ) {
        if (consumer.sessionId == session.sessionId.value) {
            fail(path, "cap provenance cannot reference the consumer Session itself")
        }
        consumer.descendantDecisionId?.let { decisionId ->
            if (session.body.requiredString("decision_id", "export.sessions[${session.sessionId}]") == decisionId) {
                fail(path, "Decision cap cannot reference its own descendant Session")
            }
        }
        val terminalInstant = LocalStampWireV1.fromObject(
            terminalStamp,
            "export.sessions[${session.sessionId}].terminal_at",
        ).occurredAtUtc
        if (terminalInstant > consumer.occurredAtUtc) {
            fail(path, "cap $role Session terminal occurs after cap consumer")
        }
    }

    private fun localStampInstant(body: StrictJsonObjectV1, key: String, path: String): InstantWireV1 =
        LocalStampWireV1.fromObject(
            body.requiredElement(key, path).asStrictObject("$path.$key"),
            "$path.$key",
        ).occurredAtUtc

    private fun groupCapUpdateEventsBySession(
        events: List<ProductEventWireV1>,
    ): Map<String, List<ProductEventWireV1>> =
        events.filter { it.name == EventNameV1.DAY_MODE_CAP_UPDATED }.groupBy { event ->
            event.envelope.sessionId?.value
                ?: fail("export.events", "day_mode_cap_updated is missing its triggering session_id")
        }

    internal fun requireValidReminderOccurrences(reminders: List<ReminderWireV1>) {
        val nodes = reminders.map(::reminderNode)
        val nodesById = nodes.associateBy { it.id }
        val fixedGroups = nodes.filter { it.fixedKey != null }.groupBy { requireNotNull(it.fixedKey) }

        nodes.forEach { node ->
            val path = "export.reminders[${node.id}]"
            when {
                node.kind == "fixed" && node.status == "SNOOZED" ->
                    fail(path, "fixed reminder cannot use SNOOZED pending status")
                node.kind == "snooze" && node.status == "SCHEDULED" ->
                    fail(path, "snooze reminder cannot use SCHEDULED pending status")
            }
        }

        fixedGroups.forEach { (key, generations) ->
            if (generations.count { it.status == "SCHEDULED" } > 1) {
                fail("export.reminders", "logical fixed key $key permits at most one pending fixed occurrence")
            }
        }

        val snoozeChildrenByParent = nodes.filter { it.parentId != null }.groupBy { requireNotNull(it.parentId) }
        snoozeChildrenByParent.forEach { (parentId, children) ->
            if (children.size > 1) fail("export.reminders", "source occurrence $parentId permits at most one snooze child")
        }
        nodes.filter { it.parentId != null }.forEach { child ->
            val path = "export.reminders[${child.id}]"
            val parent = nodesById[child.parentId] ?: fail(path, "dangling parent_occurrence_id")
            if (child.scheduleId != parent.scheduleId) fail(path, "snooze child and parent must use the same schedule")
            if (parent.status != "DELIVERED") fail(path, "snooze parent must be DELIVERED")
        }

        fixedGroups.forEach { (key, generations) ->
            generations.filter { requireNotNull(it.generation) > 0 }.forEach { current ->
                val path = "export.reminders[${current.id}]"
                val supersedesId = current.supersedesId ?: fail(path, "positive fixed generation requires supersedes_occurrence_id")
                val target = nodesById[supersedesId] ?: fail(path, "dangling supersedes_occurrence_id")
                if (target.fixedKey != key) fail(path, "fixed predecessor must use the same logical fixed key")

                val nearest = generations
                    .filter { requireNotNull(it.generation) < requireNotNull(current.generation) }
                    .maxByOrNull { requireNotNull(it.generation) }
                    ?: fail(path, "positive fixed generation has no nearest lower generation")
                if (target.id != nearest.id || requireNotNull(nearest.generation) != requireNotNull(current.generation) - 1) {
                    fail(path, "fixed occurrence must supersede the nearest lower generation")
                }
                if (nearest.status !in REELIGIBLE_TERMINAL_STATUSES) {
                    fail(path, "fixed predecessor must be an eligible terminal occurrence")
                }
            }
        }

        rejectReminderRelationshipCycles(nodes, nodesById)
        validateReminderMergeEdges(nodes, nodesById)

        nodes.forEach { node ->
            val expected = when (node.kind) {
                "fixed" -> ReminderOccurrenceIdCodecV1.fixed(
                    scheduleVersionId = UuidWireV1.parse(node.scheduleId),
                    slotIndex = requireNotNull(node.fixedKey).slotIndex,
                    localDate = DateWireV1.parse(node.fixedKey.localDate),
                    generation = requireNotNull(node.generation),
                )
                "snooze" -> ReminderOccurrenceIdCodecV1.snooze(
                    parentOccurrenceId = UuidWireV1.parse(requireNotNull(node.parentId)),
                    ordinal = requireNotNull(node.ordinal),
                )
                else -> fail("export.reminders[${node.id}]", "unknown reminder kind '${node.kind}'")
            }
            if (expected.value != node.id) {
                fail("export.reminders[${node.id}]", "reminder_occurrence_id does not match canonical reminder occurrence ID preimage")
            }
        }
    }

    private fun validateProjectionLineage(
        event: ProductEventWireV1,
        decisions: Map<String, DecisionWireV1>,
        capContext: CapConformanceContextV1,
        path: String,
    ) {
        if (event.name != EventNameV1.RECOMMENDATION_SHOWN && event.name != EventNameV1.ROUTINE_SELECTED) return
        val decisionId = event.envelope.decisionId?.value ?: fail(path, "projection requires decision_id")
        val decision = decisions[decisionId] ?: fail(path, "projection has dangling decision_id")
        val sourceDecisionPath = "export.decisions[$decisionId]"
        val sourceDecisionMode = decision.body.nullableString("effective_mode", sourceDecisionPath)
            ?: fail(path, "projection requires a mode-bearing source Decision")
        val properties = event.properties.body

        if (event.name == EventNameV1.RECOMMENDATION_SHOWN) {
            val sourceBaseMode = decision.body.nullableString("base_mode", sourceDecisionPath)
                ?: fail(path, "recommendation projection requires source Decision.base_mode")
            if (properties.requiredString("base_mode", "$path.properties") != sourceBaseMode) {
                fail(path, "projection base mode must mirror source Decision.base_mode")
            }
            if (properties.requiredString("decision_effective_mode", "$path.properties") != sourceDecisionMode) {
                fail(path, "projection decision mode must mirror source Decision.effective_mode")
            }
        }

        val runtimeMode = properties.requiredString("runtime_effective_mode", "$path.properties")
        if (modeRank(runtimeMode) > modeRank(sourceDecisionMode)) {
            fail(path, "projection runtime mode exceeds source Decision.effective_mode")
        }
        val rawSnapshot = properties.requiredElement("runtime_day_mode_cap_snapshot", "$path.properties")
        val strictReduction = modeRank(runtimeMode) < modeRank(sourceDecisionMode)
        if ((rawSnapshot !== JsonNull) != strictReduction) {
            fail(path, "projection runtime cap snapshot must be non-null iff runtime strictly reduces source Decision.effective_mode")
        }
        if (rawSnapshot !== JsonNull) {
            val snapshotPath = "$path.properties.runtime_day_mode_cap_snapshot"
            val snapshot = rawSnapshot.asStrictObject(snapshotPath)
            val expectedRuntime = minOf(modeRank(sourceDecisionMode), modeRank(snapshot.requiredString("max_mode", snapshotPath)))
            if (modeRank(runtimeMode) != expectedRuntime) {
                fail(path, "projection runtime mode must equal min(source Decision.effective_mode, cap max_mode)")
            }
            validateDayModeCapSnapshotProvenance(
                snapshot,
                capContext,
                snapshotPath,
                CapConsumerV1(
                    occurredAtUtc = event.envelope.occurred.occurredAtUtc,
                    descendantDecisionId = decisionId,
                ),
            )
        }
    }

    private fun validateEvents(
        dataset: ExportDatasetWireV1,
        capContext: CapConformanceContextV1,
    ) {
        val profileId = dataset.profile.singleOrNull()?.installationId
        val schedules = dataset.workSchedule.mapTo(hashSetOf()) { it.scheduleVersionId.value }
        val decisionsById = dataset.decisions.associateBy { it.decisionId.value }
        val sessionsById = capContext.sessions
        val decisions = decisionsById.keys
        val sessions = sessionsById.keys
        val reminders = dataset.reminders.mapTo(hashSetOf()) { it.reminderOccurrenceId.value }
        val acknowledgements = dataset.profile.singleOrNull()?.body?.requiredElement("safety_acknowledgements", "profile")
            ?.asArray("profile.safety_acknowledgements")
            ?.mapTo(hashSetOf()) { acknowledgement ->
                acknowledgement.asStrictObject("profile.safety_acknowledgements[]")
                    .requiredString("acknowledgement_id", "profile.safety_acknowledgements[]")
            }.orEmpty()
        val weeklySummaries = dataset.weeklySummaries.mapTo(hashSetOf()) { it.summaryId.value }
        val refUniverse = RefUniverse(schedules, dataset.checkIns.mapTo(hashSetOf()) { it.checkInId.value }, decisions, sessions, reminders, acknowledgements, weeklySummaries)
        val eventIds = HashSet<String>()
        val logicalKeys = HashSet<String>()
        dataset.events.forEachIndexed { index, event ->
            val path = "export.events[$index]"
            if (!eventIds.add(event.envelope.eventId.value)) fail(path, "duplicate event_id")
            if (profileId == null || event.envelope.installationId != profileId) fail(path, "installation_id does not mirror profile")
            event.envelope.scheduleVersionId?.let { if (it.value !in schedules) fail(path, "dangling schedule_version_id") }
            event.envelope.decisionId?.let { if (it.value !in decisions) fail(path, "dangling decision_id") }
            event.envelope.sessionId?.let { if (it.value !in sessions) fail(path, "dangling session_id") }
            event.envelope.reminderOccurrenceId?.let { if (it.value !in reminders) fail(path, "dangling reminder_occurrence_id") }
            val spec = EventContractRegistryV1.specFor(event.name)
            spec.validateAny(event.envelope, event.properties, path)
            validateAdditionalRefs(event, spec.refPlan.additionalRefs, refUniverse, path)
            validateProjectionLineage(
                event,
                decisionsById,
                capContext,
                path,
            )
            val plan = spec.idempotencyAny(event.properties)
            val tuple = buildString {
                append(plan.domain)
                plan.orderedSelectors.forEach { selector -> append('|').append(selector).append('=').append(selectorValue(event, selector, path)) }
            }
            if (!logicalKeys.add(tuple)) fail(path, "duplicate/conflicting logical idempotency tuple")
        }
        dataset.events.zipWithNext().forEachIndexed { index, (left, right) ->
            val instantCompare = left.envelope.occurred.occurredAtUtc.compareTo(right.envelope.occurred.occurredAtUtc)
            if (instantCompare > 0 || (instantCompare == 0 && left.envelope.eventId >= right.envelope.eventId)) {
                fail("export.events", "events are not sorted by (occurred_at_utc,event_id) at index ${index + 1}")
            }
        }
        validateRoutineSelectionRecommendationLineage(dataset.events)
    }

    private fun validateRoutineSelectionRecommendationLineage(events: List<ProductEventWireV1>) {
        val recommendationsByDecision = HashMap<String, TreeMap<InstantWireV1, MutableList<ProductEventWireV1>>>()
        events.asSequence()
            .filter { it.name == EventNameV1.RECOMMENDATION_SHOWN }
            .forEach { recommendation ->
                val decisionId = requireNotNull(recommendation.envelope.decisionId).value
                recommendationsByDecision
                    .getOrPut(decisionId, ::TreeMap)
                    .getOrPut(recommendation.envelope.occurred.occurredAtUtc, ::mutableListOf)
                    .add(recommendation)
            }

        events.forEachIndexed { index, selection ->
            if (selection.name != EventNameV1.ROUTINE_SELECTED) return@forEachIndexed
            val properties = selection.properties.body
            val selectionLabel = properties.requiredString("selection", "export.events[$index].properties")
            if (selectionLabel != "recommended" && selectionLabel != "same_mode") return@forEachIndexed

            val decisionId = requireNotNull(selection.envelope.decisionId).value
            val timeline = recommendationsByDecision[decisionId] ?: return@forEachIndexed
            val selectionInstant = selection.envelope.occurred.occurredAtUtc
            val latestStrictlyEarlier = timeline.lowerEntry(selectionInstant)?.value
                ?: return@forEachIndexed // Same-instant recommendations can all be causally later than a selection.
            val possibleLatest = latestStrictlyEarlier + timeline[selectionInstant].orEmpty()
            val latestCandidates = possibleLatest.map { recommendation ->
                RetainedRecommendationV1(
                    projection = runtimeProjectionKey(recommendation),
                    routineId = recommendation.properties.body.requiredString(
                        "routine_id",
                        "recommendation_shown.properties",
                    ),
                )
            }.distinct()
            val selectionProjection = runtimeProjectionKey(selection)
            if (latestCandidates.any { it.projection != selectionProjection }) {
                return@forEachIndexed // A differently projected latest candidate is not comparable to this selection.
            }

            val selectedRoutineId = properties.requiredString("routine_id", "export.events[$index].properties")
            val isExplained = latestCandidates.any { candidate ->
                val expectedLabel = if (selectedRoutineId == candidate.routineId) "recommended" else "same_mode"
                selectionLabel == expectedLabel
            }
            if (!isExplained) {
                fail(
                    "export.events[$index]",
                    "selection '$selectionLabel' contradicts every possible latest retained recommendation",
                )
            }
        }
    }

    private fun runtimeProjectionKey(event: ProductEventWireV1): RuntimeProjectionKeyV1 {
        val path = "${event.name.wire}.properties"
        return RuntimeProjectionKeyV1(
            runtimeEffectiveMode = event.properties.body.requiredString("runtime_effective_mode", path),
            runtimeDayModeCapSnapshot = event.properties.body.requiredElement("runtime_day_mode_cap_snapshot", path),
        )
    }

    private data class RetainedRecommendationV1(
        val projection: RuntimeProjectionKeyV1,
        val routineId: String,
    )

    private data class RuntimeProjectionKeyV1(
        val runtimeEffectiveMode: String,
        val runtimeDayModeCapSnapshot: JsonElement,
    )

    private data class RefUniverse(
        val schedules: Set<String>,
        val checkIns: Set<String>,
        val decisions: Set<String>,
        val sessions: Set<String>,
        val reminders: Set<String>,
        val acknowledgements: Set<String>,
        val weeklySummaries: Set<String>,
    )

    private fun validateAdditionalRefs(
        event: ProductEventWireV1,
        refs: List<vn.nhip2phut.domain.events.EventAdditionalRefV1>,
        universe: RefUniverse,
        path: String,
    ) {
        refs.forEach { ref ->
            val value = nestedPropertyString(event, ref.logicalSlot, path)
            if (value == null) {
                if (!ref.conditional) fail(path, "required additional ref '${ref.logicalSlot}' is missing/null")
                return@forEach
            }
            val exists = when (ref.target) {
                RefTargetTypeV1.APP_PROFILE -> value == "1"
                RefTargetTypeV1.SAFETY_ACKNOWLEDGEMENT -> value in universe.acknowledgements
                RefTargetTypeV1.WORK_SCHEDULE_VERSION -> value in universe.schedules
                RefTargetTypeV1.CHECK_IN -> value in universe.checkIns
                RefTargetTypeV1.DECISION -> value in universe.decisions
                RefTargetTypeV1.SESSION -> value in universe.sessions
                RefTargetTypeV1.REMINDER_OCCURRENCE -> value in universe.reminders
                RefTargetTypeV1.WEEKLY_SUMMARY -> value in universe.weeklySummaries
            }
            if (!exists) fail(path, "additional ref '${ref.logicalSlot}' does not resolve as ${ref.target.wire}")
        }
    }

    private fun nestedPropertyString(event: ProductEventWireV1, logicalSlot: String, path: String): String? {
        var current: kotlinx.serialization.json.JsonElement = event.properties.body.element
        logicalSlot.split('.').forEach { component ->
            if (current === JsonNull) return null
            val objectValue = current as? kotlinx.serialization.json.JsonObject
                ?: fail(path, "ref path '$logicalSlot' crosses a non-object")
            current = objectValue[component] ?: return null
        }
        if (current === JsonNull) return null
        return (current as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: fail(path, "ref path '$logicalSlot' is not a canonical string ID")
    }

    private fun selectorValue(event: ProductEventWireV1, selector: String, path: String): String {
        val envelopeValue = when (selector) {
            "event_id" -> event.envelope.eventId.value
            "installation_id" -> event.envelope.installationId.value
            "decision_id" -> event.envelope.decisionId?.value
            "session_id" -> event.envelope.sessionId?.value
            "reminder_occurrence_id" -> event.envelope.reminderOccurrenceId?.value
            "schedule_version_id" -> event.envelope.scheduleVersionId?.value
            else -> null
        }
        if (envelopeValue != null) return envelopeValue
        val value = event.properties.body[selector]
        if (value == null && selector == "source_id") return event.envelope.sessionId?.value ?: fail(path, "idempotency source_id is unavailable")
        if (value == null || value === JsonNull) fail(path, "idempotency selector '$selector' is missing/null")
        return when (value) {
            is JsonPrimitive -> value.content
            is JsonArray -> value.joinToString(",") { element -> element.asString("$path.properties.$selector") }
            else -> fail(path, "idempotency selector '$selector' is not scalar/canonical array")
        }
    }

    private fun rejectCheckInCycles(checkIns: List<CheckInWireV1>) {
        val parents = checkIns.associate { checkIn -> checkIn.checkInId.value to checkIn.body.nullableString("parent_id", "CheckIn") }
        requireAcyclicCheckInParents(parents)
    }

    internal fun requireAcyclicCheckInParents(parents: Map<String, String?>) {
        val states = HashMap<String, Int>(parents.size)
        parents.keys.forEach { start ->
            if (states[start] == VISITED) return@forEach
            var current: String? = start
            val path = ArrayList<String>()
            while (current != null) {
                when (states[current]) {
                    VISITING -> fail("export.check_ins", "parent_id cycle detected")
                    VISITED -> break
                    else -> {
                        states[current] = VISITING
                        path += current
                        current = parents[current]
                    }
                }
            }
            path.forEach { states[it] = VISITED }
        }
    }

    internal fun requireAcyclicCapEntityDependencies(
        decisions: List<DecisionWireV1>,
        sessionRows: List<SessionWireV1>,
    ) {
        val sessionsById = sessionRows.associateBy { it.sessionId.value }
        val decisionIds = decisions.mapTo(HashSet()) { it.decisionId.value }
        val edgesByNode = LinkedHashMap<CapDependencyNodeV1, LinkedHashSet<CapDependencyNodeV1>>()
        decisions.forEach { decision ->
            edgesByNode[CapDependencyNodeV1.Decision(decision.decisionId.value)] = LinkedHashSet()
        }
        sessionRows.forEach { session ->
            val sessionPath = "export.sessions[${session.sessionId}]"
            val ownerDecisionId = session.body.requiredString("decision_id", sessionPath)
            if (ownerDecisionId !in decisionIds) fail(sessionPath, "dangling decision_id")
            val sessionNode = CapDependencyNodeV1.Session(session.sessionId.value)
            edgesByNode[sessionNode] = linkedSetOf(CapDependencyNodeV1.Decision(ownerDecisionId))
        }

        fun addCapEdges(consumerNode: CapDependencyNodeV1, cap: StrictJsonObjectV1, path: String) {
            listOf("mode_trigger_session_id", "source_session_id").forEach { key ->
                val referencedSessionId = cap.requiredString(key, path)
                val referencedSession = sessionsById[referencedSessionId]
                    ?: fail(path, "dangling $key")
                val referencedDecisionId = referencedSession.body.requiredString(
                    "decision_id",
                    "export.sessions[${referencedSession.sessionId}]",
                )
                if (referencedDecisionId !in decisionIds) {
                    fail(path, "$key Session has dangling decision_id")
                }
                edgesByNode.getValue(consumerNode).add(CapDependencyNodeV1.Session(referencedSessionId))
            }
        }

        decisions.forEach { decision ->
            val path = "export.decisions[${decision.decisionId}]"
            decision.body.requiredElement("evaluation_day_mode_cap_snapshot", path)
                .takeUnless { it === JsonNull }
                ?.let { raw ->
                    val capPath = "$path.evaluation_day_mode_cap_snapshot"
                    addCapEdges(
                        CapDependencyNodeV1.Decision(decision.decisionId.value),
                        raw.asStrictObject(capPath),
                        capPath,
                    )
                }
        }
        sessionRows.forEach { session ->
            val path = "export.sessions[${session.sessionId}]"
            val rawRuntimeCap = session.body.requiredElement("runtime_day_mode_cap_snapshot_at_start", path)
            if (rawRuntimeCap !== JsonNull) {
                val runtimeCapPath = "$path.runtime_day_mode_cap_snapshot_at_start"
                val runtimeCap = rawRuntimeCap.asStrictObject(runtimeCapPath)
                val appliedCapPath = "$runtimeCapPath.applied_cap"
                val appliedCap = runtimeCap.requiredElement("applied_cap", runtimeCapPath).asStrictObject(appliedCapPath)
                addCapEdges(CapDependencyNodeV1.Session(session.sessionId.value), appliedCap, appliedCapPath)
            }
        }

        requireAcyclicCapDependencies(edgesByNode)
    }

    internal fun requireAcyclicCapDecisionDependencies(edgesByDecision: Map<String, Set<String>>) {
        requireAcyclicCapDependencies(edgesByDecision)
    }

    private fun <T> requireAcyclicCapDependencies(edgesByNode: Map<T, Set<T>>) {
        val states = HashMap<T, Int>()
        edgesByNode.keys.forEach { startId ->
            if (states[startId] != null) return@forEach
            states[startId] = VISITING
            val stack = java.util.ArrayDeque<CapDependencyFrameV1<T>>()
            stack.addLast(CapDependencyFrameV1(startId, edgesByNode[startId].orEmpty().iterator()))
            while (stack.isNotEmpty()) {
                val frame = stack.peekLast()
                if (!frame.dependencies.hasNext()) {
                    states[frame.id] = VISITED
                    stack.removeLast()
                    continue
                }
                val dependencyId = frame.dependencies.next()
                when (states[dependencyId]) {
                    VISITING -> fail("export.decisions", "day-mode cap dependency cycle detected")
                    VISITED -> Unit
                    else -> {
                        states[dependencyId] = VISITING
                        stack.addLast(
                            CapDependencyFrameV1(
                                dependencyId,
                                edgesByNode[dependencyId].orEmpty().iterator(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun reminderNode(reminder: ReminderWireV1): ReminderOccurrenceNodeV1 {
        val path = "export.reminders[${reminder.reminderOccurrenceId}]"
        val kind = reminder.kind
        return ReminderOccurrenceNodeV1(
            id = reminder.reminderOccurrenceId.value,
            scheduleId = reminder.body.requiredString("schedule_version_id", path),
            kind = kind,
            status = reminder.body.requiredString("status", path),
            fixedKey = if (kind == "fixed") {
                LogicalFixedKeyV1(
                    scheduleId = reminder.body.requiredString("schedule_version_id", path),
                    slotIndex = reminder.body.requiredInt64("slot_index", path),
                    localDate = reminder.body.requiredString("local_date", path),
                )
            } else {
                null
            },
            generation = if (kind == "fixed") reminder.body.requiredInt64("generation", path) else null,
            parentId = if (kind == "snooze") reminder.body.requiredString("parent_occurrence_id", path) else null,
            ordinal = if (kind == "snooze") reminder.body.requiredInt64("ordinal", path) else null,
            supersedesId = reminder.body.nullableString("supersedes_occurrence_id", path),
            mergedIntoId = reminder.body.nullableString("merged_into_occurrence_id", path),
            dueAt = LocalStampWireV1.fromObject(
                reminder.body.requiredElement("due_at", path).asStrictObject("$path.due_at"),
                "$path.due_at",
            ).occurredAtUtc,
        )
    }

    private fun validateReminderMergeEdges(
        nodes: List<ReminderOccurrenceNodeV1>,
        nodesById: Map<String, ReminderOccurrenceNodeV1>,
    ) {
        nodes.filter { it.status == "MERGED" }.forEach { source ->
            val path = "export.reminders[${source.id}]"
            val targetId = source.mergedIntoId ?: fail(path, "MERGED reminder requires merged_into_occurrence_id")
            val target = nodesById[targetId] ?: fail(path, "dangling merged_into_occurrence_id")
            if (source.kind == target.kind) fail(path, "merge pair must use opposite reminder kinds")
            if (source.scheduleId != target.scheduleId) fail(path, "merged source and target must use the same schedule")

            val dueOrder = target.dueAt.compareTo(source.dueAt)
            if (dueOrder > 0) fail(path, "positive-distance merged target must have strictly earlier due time")
            if (dueOrder == 0 && (source.kind != "fixed" || target.kind != "snooze")) {
                fail(path, "equal-due merge must keep snooze over fixed")
            }
        }
    }

    private fun rejectReminderRelationshipCycles(
        nodes: List<ReminderOccurrenceNodeV1>,
        nodesById: Map<String, ReminderOccurrenceNodeV1>,
    ) {
        val edgesById = nodes.associate { node ->
            node.id to listOfNotNull(node.parentId, node.supersedesId, node.mergedIntoId)
        }
        val states = HashMap<String, Int>()
        nodes.forEach { start ->
            if (states[start.id] != null) return@forEach
            states[start.id] = VISITING
            val stack = java.util.ArrayDeque<RelationshipFrameV1>()
            stack.addLast(RelationshipFrameV1(start.id))
            while (stack.isNotEmpty()) {
                val frame = stack.peekLast()
                val edges = edgesById.getValue(frame.id)
                if (frame.nextEdgeIndex == edges.size) {
                    states[frame.id] = VISITED
                    stack.removeLast()
                    continue
                }
                val target = edges[frame.nextEdgeIndex++]
                if (target !in nodesById) fail("export.reminders[${frame.id}]", "dangling reminder relationship")
                when (states[target]) {
                    VISITING -> fail("export.reminders", "reminder relationship cycle detected")
                    VISITED -> Unit
                    else -> {
                        states[target] = VISITING
                        stack.addLast(RelationshipFrameV1(target))
                    }
                }
            }
        }
    }

    private fun requireUuidOrder(path: String, ids: List<UuidWireV1>) {
        ids.zipWithNext().forEachIndexed { index, (left, right) ->
            if (left >= right) fail(path, "UUID array must be strictly sorted/unique at index ${index + 1}")
        }
    }

    private fun requireUnique(label: String, values: List<String>) {
        if (values.distinct().size != values.size) fail("export.$label", "duplicate canonical row ID")
    }

    private data class LogicalFixedKeyV1(
        val scheduleId: String,
        val slotIndex: Long,
        val localDate: String,
    )

    private data class ReminderOccurrenceNodeV1(
        val id: String,
        val scheduleId: String,
        val kind: String,
        val status: String,
        val fixedKey: LogicalFixedKeyV1?,
        val generation: Long?,
        val parentId: String?,
        val ordinal: Long?,
        val supersedesId: String?,
        val mergedIntoId: String?,
        val dueAt: InstantWireV1,
    )

    private data class CapConsumerV1(
        val occurredAtUtc: InstantWireV1,
        val descendantDecisionId: String? = null,
        val sessionId: String? = null,
    )

    private data class CapConformanceContextV1(
        val sessions: Map<String, SessionWireV1>,
        val feedbackBySession: Map<String, FeedbackWireV1>,
        val capUpdateEventsBySession: Map<String, List<ProductEventWireV1>>,
        val resultOwners: Map<JsonElement, CapResultOwnersV1>,
    )

    private data class CapResultOwnerCommitV1(
        val sessionId: String,
        val commitAt: InstantWireV1,
    )

    private data class CapResultOwnersV1(
        val ownerCount: Int,
        val earliestOwnerSessionId: String,
        val earliestCommitAt: InstantWireV1,
    )

    private data class CapExpiryEvidenceV1(
        val occurredAtUtc: JsonElement,
        val localDate: JsonElement,
        val zoneId: JsonElement,
        val utcOffsetMinutes: JsonElement,
        val expiresAtUtc: JsonElement,
        val clockIntegrity: JsonElement,
        val originBootMarker: Long,
        val monotonicDeadlineMs: Long,
    )

    private data class RetainedCapUpdateV1(
        val sessionId: String,
        val previousMaxMode: String?,
        val resultingMaxMode: String,
        val modeTriggerSessionId: String,
        val sourceSessionId: String,
        val deadlineSource: String,
        val resultingExpiryEvidence: CapExpiryEvidenceV1,
        val candidateExpiryEvidence: CapExpiryEvidenceV1,
        val commitAt: InstantWireV1,
        val path: String,
    ) {
        fun establishesMode(mode: String): Boolean =
            resultingMaxMode == mode &&
                modeTriggerSessionId == sessionId &&
                (previousMaxMode == null || modeRank(resultingMaxMode) < modeRank(previousMaxMode))
    }

    private sealed interface CapDependencyNodeV1 {
        data class Decision(val id: String) : CapDependencyNodeV1
        data class Session(val id: String) : CapDependencyNodeV1
    }

    private data class CapDependencyFrameV1<T>(val id: T, val dependencies: Iterator<T>)
    private data class RelationshipFrameV1(val id: String, var nextEdgeIndex: Int = 0)

    private val REELIGIBLE_TERMINAL_STATUSES = setOf("CANCELLED", "BLOCKED_PERMISSION")
    private const val VISITING = 1
    private const val VISITED = 2
}
