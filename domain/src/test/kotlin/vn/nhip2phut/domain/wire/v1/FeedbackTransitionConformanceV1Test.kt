package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertFailsWith

class FeedbackTransitionConformanceV1Test {
    @Test
    fun feedbackUpdatesMustFormSequentialNullToValueTransitions() {
        val effort = update(
            id = EVENT_FEEDBACK_EFFORT_ID,
            occurredAtUtc = EFFORT_AT_UTC,
            fields = "\"effort\"",
            effort = "easy",
            contextFit = null,
            feedbackComplete = false,
            capResult = "not_too_hard",
        )
        val context = update(
            id = EVENT_FEEDBACK_CONTEXT_ID,
            occurredAtUtc = CONTEXT_AT_UTC,
            fields = "\"context_fit\"",
            effort = "easy",
            contextFit = "yes",
            feedbackComplete = true,
            capResult = "no_effort_transition",
        )
        ClosedCodecV1.decodeExport(dataset(finalEffort = "easy", finalContextFit = "yes", updates = listOf(effort, context)))

        val untouchedOverwrite = context.copy(effort = "moderate")
        val repeatedEffort = context.copy(fields = "\"effort\",\"context_fit\"")
        val wrongComplete = context.copy(feedbackComplete = false)
        listOf(untouchedOverwrite, repeatedEffort, wrongComplete).forEachIndexed { index, mutant ->
            assertFailsWith<WireContractException>("feedback sequence mutant $index") {
                ClosedCodecV1.decodeExport(dataset(finalEffort = "easy", finalContextFit = "yes", updates = listOf(effort, mutant)))
            }
        }
    }

    @Test
    fun sameStampFeedbackUpdatesUseSemanticOrderInsteadOfEventIdOrder() {
        val effort = update(
            id = EVENT_FEEDBACK_CONTEXT_ID,
            occurredAtUtc = EFFORT_AT_UTC,
            fields = "\"effort\"",
            effort = "easy",
            contextFit = null,
            feedbackComplete = false,
            capResult = "not_too_hard",
        )
        val contextWithLowerId = update(
            id = EVENT_FEEDBACK_SAME_STAMP_CONTEXT_ID,
            occurredAtUtc = EFFORT_AT_UTC,
            fields = "\"context_fit\"",
            effort = "easy",
            contextFit = "yes",
            feedbackComplete = true,
            capResult = "no_effort_transition",
        )
        ClosedCodecV1.decodeExport(
            dataset(
                finalEffort = "easy",
                finalContextFit = "yes",
                updates = listOf(effort, contextWithLowerId),
            ),
        )

        val noValidOrder = contextWithLowerId.copy(effort = null)
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                dataset(
                    finalEffort = "easy",
                    finalContextFit = "yes",
                    updates = listOf(effort, noValidOrder),
                ),
            )
        }
    }

    @Test
    fun feedbackCapResultIsTheExactTotalTransitionFunction() {
        val contextOnly = update(
            id = EVENT_FEEDBACK_EFFORT_ID,
            occurredAtUtc = EFFORT_AT_UTC,
            fields = "\"context_fit\"",
            effort = null,
            contextFit = "yes",
            feedbackComplete = false,
            capResult = "no_effort_transition",
        )
        ClosedCodecV1.decodeExport(dataset(finalEffort = null, finalContextFit = "yes", updates = listOf(contextOnly)))

        val originExpired = update(
            id = EVENT_FEEDBACK_EFFORT_ID,
            occurredAtUtc = EFFORT_AT_UTC,
            fields = "\"effort\"",
            effort = "too_hard",
            contextFit = null,
            feedbackComplete = false,
            capResult = "origin_day_expired",
        )
        ClosedCodecV1.decodeExport(dataset(finalEffort = "too_hard", finalContextFit = null, updates = listOf(originExpired)))

        val applied = originExpired.copy(capResult = "applied")
        ClosedCodecV1.decodeExport(
            dataset(
                finalEffort = "too_hard",
                finalContextFit = null,
                updates = listOf(applied),
                includeCapSnapshot = true,
                includeCapEvent = true,
            ),
        )

        val easyWithWrongResult = originExpired.copy(effort = "easy")
        listOf(
            dataset(finalEffort = "easy", finalContextFit = null, updates = listOf(easyWithWrongResult)),
            dataset(finalEffort = "too_hard", finalContextFit = null, updates = listOf(applied)),
            dataset(
                finalEffort = "too_hard",
                finalContextFit = null,
                updates = listOf(originExpired),
                includeCapSnapshot = true,
                includeCapEvent = true,
            ),
        ).forEachIndexed { index, mutant ->
            assertFailsWith<WireContractException>("cap result mutant $index") {
                ClosedCodecV1.decodeExport(mutant)
            }
        }
    }

    @Test
    fun feedbackReplayUsesPainStateAtEachTransactionAndAllowsCapOnPainResolution() {
        val effortBeforePain = update(
            id = EVENT_FEEDBACK_EFFORT_ID,
            occurredAtUtc = EFFORT_AT_UTC,
            fields = "\"effort\"",
            effort = "too_hard",
            contextFit = null,
            feedbackComplete = false,
            capResult = "pain_not_no",
            terminalState = "completed",
        )
        ClosedCodecV1.decodeExport(
            dataset(
                finalEffort = "too_hard",
                finalContextFit = null,
                updates = listOf(effortBeforePain),
                sessionStatus = "COMPLETED",
                painOccurredAtUtc = CONTEXT_AT_UTC,
                includeCapSnapshot = true,
                includeCapEvent = true,
                capEventOccurredAtUtc = CONTEXT_AT_UTC,
            ),
        )

        val completeFieldsBeforePain = effortBeforePain.copy(
            fields = "\"effort\",\"context_fit\"",
            contextFit = "yes",
        )
        ClosedCodecV1.decodeExport(
            dataset(
                finalEffort = "too_hard",
                finalContextFit = "yes",
                updates = listOf(completeFieldsBeforePain),
                sessionStatus = "COMPLETED",
                painOccurredAtUtc = CONTEXT_AT_UTC,
                includeCapSnapshot = true,
                includeCapEvent = true,
                capEventOccurredAtUtc = CONTEXT_AT_UTC,
            ),
        )
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                dataset(
                    finalEffort = "too_hard",
                    finalContextFit = "yes",
                    updates = listOf(completeFieldsBeforePain.copy(feedbackComplete = true)),
                    sessionStatus = "COMPLETED",
                    painOccurredAtUtc = CONTEXT_AT_UTC,
                    includeCapSnapshot = true,
                    includeCapEvent = true,
                    capEventOccurredAtUtc = CONTEXT_AT_UTC,
                ),
            )
        }

        val effortAndPainInOneTransaction = effortBeforePain.copy(
            id = EVENT_FEEDBACK_SAME_STAMP_BEFORE_PAIN_ID,
            occurredAtUtc = CONTEXT_AT_UTC,
            capResult = "applied",
        )
        ClosedCodecV1.decodeExport(
            dataset(
                finalEffort = "too_hard",
                finalContextFit = null,
                updates = listOf(effortAndPainInOneTransaction),
                sessionStatus = "COMPLETED",
                painOccurredAtUtc = CONTEXT_AT_UTC,
                includeCapSnapshot = true,
                includeCapEvent = true,
                capEventOccurredAtUtc = CONTEXT_AT_UTC,
            ),
        )

        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                dataset(
                    finalEffort = "too_hard",
                    finalContextFit = null,
                    updates = listOf(effortAndPainInOneTransaction),
                    sessionStatus = "COMPLETED",
                    painOccurredAtUtc = CONTEXT_AT_UTC,
                    painAtOrAfterOriginExpiry = true,
                    includeCapSnapshot = true,
                    includeCapEvent = true,
                    capEventOccurredAtUtc = CONTEXT_AT_UTC,
                ),
            )
        }

        val effortAfterExpiredPain = effortBeforePain.copy(
            occurredAtUtc = CONTEXT_AT_UTC,
            capResult = "applied",
        )
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                dataset(
                    finalEffort = "too_hard",
                    finalContextFit = null,
                    updates = listOf(effortAfterExpiredPain),
                    sessionStatus = "COMPLETED",
                    painOccurredAtUtc = EFFORT_AT_UTC,
                    painAtOrAfterOriginExpiry = true,
                    includeCapSnapshot = true,
                    includeCapEvent = true,
                    capEventOccurredAtUtc = CONTEXT_AT_UTC,
                ),
            )
        }

        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                dataset(
                    finalEffort = "too_hard",
                    finalContextFit = null,
                    updates = listOf(effortBeforePain.copy(capResult = "applied")),
                    sessionStatus = "COMPLETED",
                    painOccurredAtUtc = CONTEXT_AT_UTC,
                    includeCapSnapshot = true,
                    includeCapEvent = true,
                    capEventOccurredAtUtc = CONTEXT_AT_UTC,
                ),
            )
        }
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                dataset(
                    finalEffort = "too_hard",
                    finalContextFit = null,
                    updates = listOf(effortBeforePain),
                    sessionStatus = "COMPLETED",
                    painOccurredAtUtc = CONTEXT_AT_UTC,
                ),
            )
        }
        ClosedCodecV1.decodeExport(
            dataset(
                finalEffort = "too_hard",
                finalContextFit = null,
                updates = listOf(effortBeforePain),
                sessionStatus = "COMPLETED",
                painOccurredAtUtc = CONTEXT_AT_UTC,
                painAtOrAfterOriginExpiry = true,
            ),
        )
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                dataset(
                    finalEffort = "too_hard",
                    finalContextFit = null,
                    updates = listOf(effortBeforePain),
                    sessionStatus = "COMPLETED",
                    painOccurredAtUtc = CONTEXT_AT_UTC,
                    painAtOrAfterOriginExpiry = true,
                    includeCapSnapshot = true,
                    includeCapEvent = true,
                    capEventOccurredAtUtc = CONTEXT_AT_UTC,
                ),
            )
        }
    }

    @Test
    fun feedbackUpdatedAtMirrorsTheLatestFeedbackTransaction() {
        ClosedCodecV1.decodeExport(
            dataset(
                finalEffort = null,
                finalContextFit = null,
                updates = emptyList(),
                sessionStatus = "COMPLETED",
                painStatus = "pending",
            ),
        )
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                dataset(
                    finalEffort = null,
                    finalContextFit = null,
                    updates = emptyList(),
                    sessionStatus = "COMPLETED",
                    painStatus = "pending",
                    updatedAtUtc = EFFORT_AT_UTC,
                ),
            )
        }

        val contextBeforePain = update(
            id = EVENT_FEEDBACK_CONTEXT_ID,
            occurredAtUtc = EFFORT_AT_UTC,
            fields = "\"context_fit\"",
            effort = null,
            contextFit = "yes",
            feedbackComplete = false,
            capResult = "no_effort_transition",
            terminalState = "completed",
        )
        ClosedCodecV1.decodeExport(
            dataset(
                finalEffort = null,
                finalContextFit = "yes",
                updates = listOf(contextBeforePain),
                sessionStatus = "COMPLETED",
                painOccurredAtUtc = CONTEXT_AT_UTC,
            ),
        )
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                dataset(
                    finalEffort = null,
                    finalContextFit = "yes",
                    updates = listOf(contextBeforePain),
                    sessionStatus = "COMPLETED",
                    painOccurredAtUtc = CONTEXT_AT_UTC,
                    updatedAtUtc = EFFORT_AT_UTC,
                ),
            )
        }
        val effortAfterPain = update(
            id = EVENT_FEEDBACK_EFFORT_ID,
            occurredAtUtc = CONTEXT_AT_UTC,
            fields = "\"effort\"",
            effort = "easy",
            contextFit = null,
            feedbackComplete = false,
            capResult = "not_too_hard",
            terminalState = "completed",
        )
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                dataset(
                    finalEffort = "easy",
                    finalContextFit = null,
                    updates = listOf(effortAfterPain),
                    sessionStatus = "COMPLETED",
                    painOccurredAtUtc = EFFORT_AT_UTC,
                    updatedAtUtc = EFFORT_AT_UTC,
                ),
            )
        }
    }

    @Test
    fun postSessionHoldMustShareThePainResolutionTransactionStamp() {
        ClosedCodecV1.decodeExport(
            dataset(
                finalEffort = null,
                finalContextFit = null,
                updates = emptyList(),
                sessionStatus = "COMPLETED",
                painStatus = "resolved_hold",
                painOccurredAtUtc = EFFORT_AT_UTC,
            ),
        )

        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                dataset(
                    finalEffort = null,
                    finalContextFit = null,
                    updates = emptyList(),
                    sessionStatus = "COMPLETED",
                    painStatus = "resolved_hold",
                    painOccurredAtUtc = EFFORT_AT_UTC,
                    holdEventOccurredAtUtc = CONTEXT_AT_UTC,
                    updatedAtUtc = EFFORT_AT_UTC,
                ),
            )
        }
    }

    private fun dataset(
        finalEffort: String?,
        finalContextFit: String?,
        updates: List<FeedbackUpdateFixture>,
        includeCapSnapshot: Boolean = false,
        includeCapEvent: Boolean = false,
        sessionStatus: String = "STOPPED",
        painStatus: String = "resolved_no",
        painOccurredAtUtc: String = TERMINAL_AT_UTC,
        painAtOrAfterOriginExpiry: Boolean = false,
        capEventOccurredAtUtc: String? = null,
        holdEventOccurredAtUtc: String? = null,
        updatedAtUtc: String? = null,
    ): String {
        val holdOccurredAtUtc = holdEventOccurredAtUtc ?: painOccurredAtUtc
        val events = buildList {
            add(eventJson(EVENT_ACK_ID, "scope_acknowledged", STARTED_AT_UTC, properties = acknowledgementProperties))
            add(eventJson(EVENT_ONBOARDING_ID, "onboarding_completed", STARTED_AT_UTC, properties = onboardingProperties))
            add(eventJson(EVENT_CHECK_IN_ID, "check_in_submitted", STARTED_AT_UTC, scheduleVersionId = SCHEDULE_ID, properties = checkInProperties))
            add(eventJson(EVENT_DECISION_ID, "decision_evaluated", STARTED_AT_UTC, decisionId = DECISION_ID, scheduleVersionId = SCHEDULE_ID, properties = decisionProperties))
            add(eventJson(EVENT_SESSION_START_ID, "routine_started", STARTED_AT_UTC, decisionId = DECISION_ID, sessionId = SESSION_ID, scheduleVersionId = SCHEDULE_ID, source = "home", properties = sessionStartProperties))
            if (sessionStatus == "STOPPED") {
                add(eventJson(EVENT_SESSION_TERMINAL_ID, "routine_stopped", TERMINAL_AT_UTC, sessionId = SESSION_ID, properties = """{"elapsed_ms":1000,"pain_gate_status":"RESOLVED_NO"}"""))
            } else {
                add(eventJson(EVENT_SESSION_TERMINAL_ID, "routine_completed", TERMINAL_AT_UTC, sessionId = SESSION_ID, properties = completedProperties))
            }
            if (painStatus != "pending") {
                add(
                    eventJson(
                        EVENT_PAIN_RESOLVED_ID,
                        "pain_gate_resolved",
                        painOccurredAtUtc,
                        sessionId = SESSION_ID,
                        properties = painResolvedProperties(sessionStatus.lowercase(), painStatus, painAtOrAfterOriginExpiry),
                    ),
                )
            }
            updates.forEach { fixture -> add(feedbackUpdateEvent(fixture)) }
            if (includeCapEvent) add(capUpdatedEvent(capEventOccurredAtUtc ?: updates.single().occurredAtUtc))
            if (painStatus == "resolved_hold") add(safetyHoldCreatedEvent(holdOccurredAtUtc))
        }.sortedWith(compareBy<String> { eventOccurredAt(it) }.thenBy { eventId(it) })
        val updatedAt = updatedAtUtc ?: buildList {
            add(TERMINAL_AT_UTC)
            if (painStatus != "pending") add(painOccurredAtUtc)
            addAll(updates.map { it.occurredAtUtc })
        }.max()
        val capSnapshot = if (includeCapSnapshot) dayModeCapUpdateSnapshot else "null"
        val painAnswer = when (painStatus) {
            "pending" -> "null"
            "resolved_hold" -> "\"yes\""
            else -> "\"no\""
        }
        val painAnsweredAt = if (painStatus == "pending") "null" else stamp(painOccurredAtUtc)
        val holdSnapshot = if (painStatus == "resolved_hold") safetyHoldSnapshot(holdOccurredAtUtc) else "null"
        return """
            {
              "metadata":{
                "export_schema_version":1,"exported_at_utc":"2026-08-27T11:00:00.000Z",
                "app_version":"1.0.0","content_version":"1.0.0","rule_version":1,"retention_policy_version":1,
                "record_counts":{"profile":1,"work_schedule":1,"check_ins":1,"decisions":1,"sessions":1,"feedback":1,"reminders":0,"events":${events.size},"weekly_summaries":0}
              },
              "profile":[$profileJson],"work_schedule":[$scheduleJson],"check_ins":[$checkInJson],"decisions":[$decisionJson],
              "sessions":[${sessionJson(sessionStatus)}],
              "feedback":[{
                "session_id":"$SESSION_ID","pain_gate_status":"$painStatus","new_or_worse_pain":$painAnswer,"pain_answered_at":$painAnsweredAt,
                "effort":${finalEffort.jsonNullable()},"context_fit":${finalContextFit.jsonNullable()},
                "created_post_session_safety_hold_snapshot":$holdSnapshot,"day_mode_cap_update_snapshot":$capSnapshot,
                "updated_at":${stamp(updatedAt)}
              }],
              "reminders":[],"events":[${events.joinToString(",")}],"weekly_summaries":[]
            }
        """.trimIndent()
    }

    private fun feedbackUpdateEvent(fixture: FeedbackUpdateFixture): String = eventJson(
        id = fixture.id,
        name = "feedback_updated",
        occurredAtUtc = fixture.occurredAtUtc,
        sessionId = SESSION_ID,
        properties = """
            {
              "updated_fields":[${fixture.fields}],"terminal_state":"${fixture.terminalState}",
              "effort":${fixture.effort.jsonNullable()},"context_fit":${fixture.contextFit.jsonNullable()},
              "feedback_complete":${fixture.feedbackComplete},"cap_result":"${fixture.capResult}"
            }
        """.trimIndent(),
    )

    private fun capUpdatedEvent(occurredAtUtc: String): String = eventJson(
        id = EVENT_CAP_UPDATED_ID,
        name = "day_mode_cap_updated",
        occurredAtUtc = occurredAtUtc,
        sessionId = SESSION_ID,
        properties = """
            {
              "expiry_source_session_id":"$SESSION_ID","basis_mode":"BUILD","previous_cap":null,"new_cap":"MAINTAIN",
              "deadline_source":"candidate_later","origin_occurred_at_utc":"$TERMINAL_AT_UTC","origin_local_date":"2026-08-27",
              "origin_timezone_id":"UTC","origin_utc_offset_minutes":0,"expires_at_utc":"2026-08-28T00:00:00.000Z","rule_version":1
            }
        """.trimIndent(),
    )

    private fun safetyHoldCreatedEvent(occurredAtUtc: String): String = eventJson(
        id = EVENT_SAFETY_HOLD_ID,
        name = "safety_hold_created",
        occurredAtUtc = occurredAtUtc,
        sessionId = SESSION_ID,
        properties = """
            {
              "kind":"POST_SESSION_NEW_OR_WORSE_PAIN","source_type":"session",
              "origin_local_date":"2026-08-27","origin_timezone_id":"UTC",
              "expires_at_utc":"2026-08-28T00:00:00.000Z","rule_version":1
            }
        """.trimIndent(),
    )

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
          "event_id":"$id","event_schema_version":1,"name":"$name","occurred_at_utc":"$occurredAtUtc",
          "local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
          "installation_id":"$INSTALLATION_ID","decision_id":${decisionId.jsonNullable()},"session_id":${sessionId.jsonNullable()},
          "reminder_occurrence_id":null,"schedule_version_id":${scheduleVersionId.jsonNullable()},"source":${source.jsonNullable()},
          "properties":$properties
        }
    """.trimIndent()

    private fun update(
        id: String,
        occurredAtUtc: String,
        fields: String,
        effort: String?,
        contextFit: String?,
        feedbackComplete: Boolean,
        capResult: String,
        terminalState: String = "stopped",
    ) = FeedbackUpdateFixture(id, occurredAtUtc, fields, effort, contextFit, feedbackComplete, capResult, terminalState)

    private fun eventOccurredAt(event: String): String = Regex("\"occurred_at_utc\":\"([^\"]+)\"").find(event)!!.groupValues[1]
    private fun eventId(event: String): String = Regex("\"event_id\":\"([^\"]+)\"").find(event)!!.groupValues[1]
    private fun String?.jsonNullable(): String = this?.let { "\"$it\"" } ?: "null"
    private fun stamp(instant: String): String = """{"occurred_at_utc":"$instant","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""

    private data class FeedbackUpdateFixture(
        val id: String,
        val occurredAtUtc: String,
        val fields: String,
        val effort: String?,
        val contextFit: String?,
        val feedbackComplete: Boolean,
        val capResult: String,
        val terminalState: String,
    )

    private val acknowledgementProperties = """{"acknowledgement_id":"$ACK_ID","kind":"onboarding","eligibility_confirmed":true,"content_version":"1.0.0","content_digest":"${"0".repeat(64)}"}"""
    private val onboardingProperties = """{"duration_ms":1,"activation_boot_marker":1,"activation_elapsed_realtime_ms":2,"activation_clock_generation":4,"activation_wall_minus_elapsed_ms":5}"""
    private val checkInProperties = """{"check_in_flow_id":"$CHECK_IN_FLOW_ID","check_in_id":"$CHECK_IN_ID","kind":"new","answers_kind":"full","duration_ms":1}"""
    private val decisionProperties = """{"check_in_id":"$CHECK_IN_ID","result":"BUILD","base_mode":"BUILD","effective_mode":"BUILD","reason_codes":["SAF_BUILD_CONDITIONS"],"invalid_fields":[],"rule_version":1,"cap_applied":false}"""
    private val sessionStartProperties = """{"routine_id":"BUI-01","check_in_flow_id":"$CHECK_IN_FLOW_ID","runtime_effective_mode_at_start":"BUILD","is_selected_workday_at_start":true,"start_boot_marker":1,"start_elapsed_realtime_ms":20,"start_clock_generation":4,"start_wall_minus_elapsed_ms":5,"total_duration_ms":1}"""
    private fun painResolvedProperties(terminalState: String, painStatus: String, atOrAfterOriginExpiry: Boolean): String {
        val answer = if (painStatus == "resolved_hold") "yes" else "no"
        val eventStatus = if (painStatus == "resolved_hold") "RESOLVED_HOLD" else "RESOLVED_NO"
        return """{"terminal_state":"$terminalState","new_or_worse_pain":"$answer","pain_gate_status":"$eventStatus","answered_at_or_after_origin_expiry":$atOrAfterOriginExpiry}"""
    }
    private val completedProperties = """{"routine_id":"BUI-01","duration_ms":1000,"step_skip_count":0,"pain_gate_status":"PENDING","completion_boot_marker":1,"completion_elapsed_realtime_ms":1000,"completion_clock_generation":4,"completion_wall_minus_elapsed_ms":5}"""
    private val contentIdentity = """{"schema_version":"1.0.0","content_version":"1.0.0","routine_revision":"1.0.0","manifest_digest_sha256":"${"0".repeat(64)}"}"""
    private val clockIntegrity = """{"origin_boot_marker":1,"created_elapsed_realtime_ms":20,"monotonic_deadline_ms":1000,"remaining_elapsed_ms_at_last_checkpoint":980,"original_duration_ms":980}"""
    private val terminalStamp = stamp(TERMINAL_AT_UTC)
    private val profileJson = """
        {"installation_id":"$INSTALLATION_ID","adult_confirmed":true,"eligibility_scope_confirmed":true,"locale":"vi-VN",
         "onboarding_completed_at":${stamp(STARTED_AT_UTC)},"activation_boot_marker":1,"activation_elapsed_realtime_ms":2,
         "activation_clock_generation":4,"activation_wall_minus_elapsed_ms":5,
         "safety_acknowledgements":[{"acknowledgement_id":"$ACK_ID","kind":"onboarding","content_version":"1.0.0","content_digest":"${"0".repeat(64)}","acknowledged_at":${stamp(STARTED_AT_UTC)}}],
         "current_safety_acknowledgement_id":"$ACK_ID"}
    """.trimIndent()
    private val scheduleJson = """{"schedule_version_id":"$SCHEDULE_ID","enabled":true,"selected_weekdays":[4],"work_start":"09:00","work_end":"17:00","reminder_times":["10:30"],"effective_from":${stamp(STARTED_AT_UTC)},"replaced_at":null}"""
    private val checkInJson = """
        {"check_in_id":"$CHECK_IN_ID","parent_id":null,"schedule_version_id":"$SCHEDULE_ID","rule_version":1,"answers_kind":"full",
         "red_flag":false,"acute_issue":"none","energy":"good","stiffness":"none","intent":"moderate","confirmed_at":${stamp(STARTED_AT_UTC)},
         "confirmed_boot_marker":1,"confirmed_elapsed_realtime_ms":2,"ttl_monotonic_deadline_ms":3,"confirmed_clock_generation":4,
         "confirmed_zone_id":"UTC","confirmed_wall_minus_elapsed_ms":5}
    """.trimIndent()
    private val decisionJson = """
        {"decision_id":"$DECISION_ID","check_in_id":"$CHECK_IN_ID","schedule_version_id":"$SCHEDULE_ID","rule_version":1,"outcome":"BUILD",
         "base_mode":"BUILD","effective_mode":"BUILD","reason_codes":["SAF_BUILD_CONDITIONS"],"invalid_fields":[],
         "created_safety_hold_snapshot":null,"created_rest_suppression_snapshot":null,"evaluation_day_mode_cap_snapshot":null,
         "created_at":${stamp(STARTED_AT_UTC)},"reconfirm_after":"2026-08-27T16:00:00.000Z","valid_until_work_end":"2026-08-27T17:00:00.000Z",
         "confirmed_boot_marker":1,"confirmed_elapsed_realtime_ms":2,"ttl_monotonic_deadline_ms":3,"confirmed_clock_generation":4,
         "confirmed_zone_id":"UTC","confirmed_wall_minus_elapsed_ms":5}
    """.trimIndent()
    private val checkpoint = """{"substate":"PAUSED","phase":"STEP_TIMER","step_index":0,"current_step_remaining_ms":1000,"transition_remaining_ms":0,"accumulated_active_ms":1000,"skipped_steps":[],"segment_started_elapsed_realtime_ms":null,"last_checkpoint_elapsed_realtime_ms":1000,"boot_marker":1,"last_announced_cadence_ordinal":1,"content_identity":$contentIdentity}"""
    private fun sessionJson(status: String) = """
        {"session_id":"$SESSION_ID","decision_id":"$DECISION_ID","schedule_version_id":"$SCHEDULE_ID","routine_id":"BUI-01","content_identity":$contentIdentity,
         "routine_mode":"BUILD","decision_effective_mode_at_start":"BUILD","runtime_effective_mode_at_start":"BUILD","runtime_day_mode_cap_snapshot_at_start":null,
         "source":"home","reminder_occurrence_id":null,"is_selected_workday_at_start":true,"started_at":${stamp(STARTED_AT_UTC)},
         "start_boot_marker":1,"start_elapsed_realtime_ms":20,"start_clock_generation":4,"start_wall_minus_elapsed_ms":5,"status":"$status",
         "player_checkpoint":$checkpoint,"terminal_at":$terminalStamp,"session_origin_day_expires_at_utc":"2026-08-28T00:00:00.000Z",
         "session_origin_clock_integrity":$clockIntegrity,"completion_boot_marker":1,"completion_elapsed_realtime_ms":1000,
         "completion_clock_generation":4,"completion_wall_minus_elapsed_ms":5}
    """.trimIndent()
    private val resultingCap = """{"occurred_at_utc":"$TERMINAL_AT_UTC","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,"max_mode":"MAINTAIN","mode_trigger_session_id":"$SESSION_ID","source_session_id":"$SESSION_ID","expires_at_utc":"2026-08-28T00:00:00.000Z","clock_integrity":$clockIntegrity,"rule_version":1}"""
    private val dayModeCapUpdateSnapshot = """{"trigger_session_id":"$SESSION_ID","expiry_source_session_id":"$SESSION_ID","basis_mode":"BUILD","previous_max_mode":null,"resulting_cap":$resultingCap,"deadline_source":"candidate_later"}"""
    private fun safetyHoldSnapshot(occurredAtUtc: String) = """{"occurred_at_utc":"$occurredAtUtc","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,"kind":"POST_SESSION_NEW_OR_WORSE_PAIN","source_type":"session","source_id":"$SESSION_ID","expires_at_utc":"2026-08-28T00:00:00.000Z","clock_integrity":$clockIntegrity,"rule_version":1}"""

    private companion object {
        const val INSTALLATION_ID = "00000000-0000-4000-8000-000000000001"
        const val ACK_ID = "00000000-0000-4000-8000-000000000002"
        const val SCHEDULE_ID = "00000000-0000-4000-8000-000000000010"
        const val CHECK_IN_ID = "00000000-0000-4000-8000-000000000020"
        const val DECISION_ID = "00000000-0000-4000-8000-000000000030"
        const val SESSION_ID = "00000000-0000-4000-8000-000000000040"
        const val CHECK_IN_FLOW_ID = "00000000-0000-4000-8000-000000000050"
        const val STARTED_AT_UTC = "2026-08-27T10:00:00.000Z"
        const val TERMINAL_AT_UTC = "2026-08-27T10:01:00.000Z"
        const val EFFORT_AT_UTC = "2026-08-27T10:02:00.000Z"
        const val CONTEXT_AT_UTC = "2026-08-27T10:03:00.000Z"
        const val EVENT_ACK_ID = "00000000-0000-4000-8000-000000000100"
        const val EVENT_ONBOARDING_ID = "00000000-0000-4000-8000-000000000101"
        const val EVENT_CHECK_IN_ID = "00000000-0000-4000-8000-000000000102"
        const val EVENT_DECISION_ID = "00000000-0000-4000-8000-000000000103"
        const val EVENT_SESSION_START_ID = "00000000-0000-4000-8000-000000000104"
        const val EVENT_SESSION_TERMINAL_ID = "00000000-0000-4000-8000-000000000105"
        const val EVENT_PAIN_RESOLVED_ID = "00000000-0000-4000-8000-000000000106"
        const val EVENT_FEEDBACK_SAME_STAMP_BEFORE_PAIN_ID = "00000000-0000-4000-8000-000000000099"
        const val EVENT_FEEDBACK_SAME_STAMP_CONTEXT_ID = "00000000-0000-4000-8000-000000000098"
        const val EVENT_FEEDBACK_EFFORT_ID = "00000000-0000-4000-8000-000000000107"
        const val EVENT_FEEDBACK_CONTEXT_ID = "00000000-0000-4000-8000-000000000108"
        const val EVENT_CAP_UPDATED_ID = "00000000-0000-4000-8000-000000000109"
        const val EVENT_SAFETY_HOLD_ID = "00000000-0000-4000-8000-000000000110"
    }
}
