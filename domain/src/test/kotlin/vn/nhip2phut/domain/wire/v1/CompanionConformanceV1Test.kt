package vn.nhip2phut.domain.wire.v1

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import vn.nhip2phut.domain.events.EventNameV1
import vn.nhip2phut.domain.events.ReminderKindV1
import vn.nhip2phut.domain.events.ReminderPostedPropertiesV1

class CompanionConformanceV1Test {
    @Test
    fun profileWithNoRequiredCompanionEventsIsRejected() {
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(profileDataset(events = "", eventCount = 0))
        }
    }

    @Test
    fun profileCompanionEventsMustMirrorTheirSourceRecords() {
        ClosedCodecV1.decodeExport(profileDataset(events = validProfileEvents, eventCount = 2))

        val wrongActivationGeneration = validProfileEvents.replace(
            "\"activation_clock_generation\":4",
            "\"activation_clock_generation\":5",
        )
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(profileDataset(events = wrongActivationGeneration, eventCount = 2))
        }

        val wrongAcknowledgementDigest = validProfileEvents.replace(
            "\"content_digest\":\"${"0".repeat(64)}\"",
            "\"content_digest\":\"${"1".repeat(64)}\"",
        )
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(profileDataset(events = wrongAcknowledgementDigest, eventCount = 2))
        }
    }

    @Test
    fun companionClaimsThatDoNotMatchTheReverseSourceGraphAreRejected() {
        val orphanReack = """
            {
              "event_id":"00000000-0000-4000-8000-000000000012",
              "event_schema_version":1,
              "name":"scope_reack_completed",
              "occurred_at_utc":"2026-08-27T10:00:00.000Z",
              "local_date":"2026-08-27",
              "zone_id":"UTC",
              "utc_offset_minutes":0,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":null,"schedule_version_id":null,"source":null,
              "properties":{
                "acknowledgement_id":"00000000-0000-4000-8000-000000000002",
                "supersedes_acknowledgement_id":"00000000-0000-4000-8000-000000000002",
                "content_version":"1.0.0",
                "content_digest":"${"0".repeat(64)}"
              }
            }
        """.trimIndent()

        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(profileDataset(events = "$validProfileEvents,$orphanReack", eventCount = 3))
        }
    }

    @Test
    fun reminderSnoozeRequiresTheSameEventToClaimBothDeliveredSourceAndSnoozeChild() {
        ClosedCodecV1.decodeExport(reminderDataset(snoozeChildClaimId = SNOOZE_CHILD_ID))

        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(reminderDataset(snoozeChildClaimId = DELIVERED_SOURCE_ID))
        }
    }

    @Test
    fun reminderCreationMustPrecedeDueAndSnoozeCreationMustShareItsActionStamp() {
        val dataset = ClosedCodecV1.decodeExport(reminderDataset(snoozeChildClaimId = SNOOZE_CHILD_ID))
        val fixed = dataset.reminders.single { it.reminderOccurrenceId.value == DELIVERED_SOURCE_ID }
        val due = LocalStampWireV1.fromObject(
            fixed.body.requiredElement("due_at", "test.fixed").asStrictObject("test.fixed.due_at"),
            "test.fixed.due_at",
        )
        val fixedCreate = dataset.events.single {
            it.name == EventNameV1.REMINDER_SCHEDULED && it.envelope.reminderOccurrenceId?.value == DELIVERED_SOURCE_ID
        }
        val createdAtDue = dataset.copy(
            events = dataset.events
                .map { if (it === fixedCreate) it.copy(envelope = it.envelope.copy(occurred = due)) else it }
                .sortedWith(compareBy<vn.nhip2phut.domain.events.ProductEventWireV1> { it.envelope.occurred.occurredAtUtc }.thenBy { it.envelope.eventId }),
        )
        val dueFailure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(createdAtDue)
        }
        assertContains(dueFailure.message.orEmpty(), "strictly before due_at")

        val snoozeCreate = dataset.events.single {
            it.name == EventNameV1.REMINDER_SCHEDULED && it.envelope.reminderOccurrenceId?.value == SNOOZE_CHILD_ID
        }
        val mismatchedStamp = LocalStampWireV1(
            InstantWireV1.parse("2026-08-27T10:30:00.001Z"),
            DateWireV1.parse("2026-08-27"),
            "UTC",
            0,
        )
        val splitSnoozeTransaction = dataset.copy(
            events = dataset.events
                .map { if (it === snoozeCreate) it.copy(envelope = it.envelope.copy(occurred = mismatchedStamp)) else it }
                .sortedWith(compareBy<vn.nhip2phut.domain.events.ProductEventWireV1> { it.envelope.occurred.occurredAtUtc }.thenBy { it.envelope.eventId }),
        )
        val transactionFailure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(splitSnoozeTransaction)
        }
        assertContains(transactionFailure.message.orEmpty(), "same transaction LocalStamp")
    }

    @Test
    fun skippedLateMustMirrorCheckedLatenessAndExceedSixtyMinutes() {
        ClosedCodecV1.decodeExport(skippedReminderDataset("2026-08-27T11:45:00.001Z", 3_600_001))

        listOf(
            skippedReminderDataset("2026-08-27T10:45:00.001Z", 1),
            skippedReminderDataset("2026-08-27T11:45:00.000Z", 3_600_000),
        ).forEachIndexed { index, mutant ->
            val failure = assertFailsWith<WireContractException>("late boundary mutant $index") {
                ClosedCodecV1.decodeExport(mutant)
            }
            assertContains(failure.message.orEmpty(), "strictly greater than 60 minutes")
        }

        val mirrorFailure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(skippedReminderDataset("2026-08-27T11:45:00.001Z", 1))
        }
        assertContains(mirrorFailure.message.orEmpty(), "does not mirror checked event-to-due lateness")
    }

    @Test
    fun skippedWorkEndMustOccurAtOrAfterResolvedWorkEnd() {
        ClosedCodecV1.decodeExport(
            skippedReminderDataset(
                skippedAtUtc = "2026-08-27T17:00:00.000Z",
                latenessMs = 22_500_000,
                status = "SKIPPED_WORK_END",
            ),
        )

        val failure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                skippedReminderDataset(
                    skippedAtUtc = "2026-08-27T16:59:59.999Z",
                    latenessMs = 22_499_999,
                    status = "SKIPPED_WORK_END",
                ),
            )
        }
        assertContains(failure.message.orEmpty(), "at or after the resolved work_end")
    }

    @Test
    fun skippedResolutionMustRespectWorkEndPrecedenceAndScheduleReplacement() {
        val workEndPrecedence = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                skippedReminderDataset(
                    skippedAtUtc = "2026-08-27T11:45:00.001Z",
                    latenessMs = 3_600_001,
                    workEnd = "11:45",
                ),
            )
        }
        assertContains(workEndPrecedence.message.orEmpty(), "strictly before the resolved work_end")

        val replacement = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                skippedReminderDataset(
                    skippedAtUtc = "2026-08-27T11:45:00.001Z",
                    latenessMs = 3_600_001,
                    scheduleReplacedAt = """{"occurred_at_utc":"2026-08-27T11:00:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}""",
                    includeActiveReplacement = true,
                ),
            )
        }
        assertContains(replacement.message.orEmpty(), "resolution occurs after schedule replacement")
    }

    @Test
    fun reminderDeliveryAndSnoozeRespectResolvedWorkWindowAndRuntimeZone() {
        val deliveredAtWorkEnd = """{"occurred_at_utc":"2026-08-27T10:01:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""
        val deliveryFailure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                reminderDataset(
                    snoozeChildClaimId = SNOOZE_CHILD_ID,
                    workEnd = "10:01",
                    deliveredStamp = deliveredAtWorkEnd,
                    deliveredAtUtc = "2026-08-27T10:01:00.000Z",
                    postedLatenessMs = 60_000,
                ),
            )
        }
        assertContains(deliveryFailure.message.orEmpty(), "resolved work_end")

        val targetAtWorkEnd = """{"occurred_at_utc":"2026-08-27T17:00:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""
        val targetFailure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                reminderDataset(
                    snoozeChildClaimId = SNOOZE_CHILD_ID,
                    targetStamp = targetAtWorkEnd,
                    snoozeActionUtc = "2026-08-27T16:45:00.000Z",
                ),
            )
        }
        assertContains(targetFailure.message.orEmpty(), "strictly before the resolved work_end")

        val bangkokTarget = """{"occurred_at_utc":"2026-08-27T10:45:00.000Z","local_date":"2026-08-27","zone_id":"Asia/Bangkok","utc_offset_minutes":420}"""
        val zoneFailure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                reminderDataset(
                    snoozeChildClaimId = SNOOZE_CHILD_ID,
                    targetStamp = bangkokTarget,
                ),
            )
        }
        assertContains(zoneFailure.message.orEmpty(), "runtime zone and local date")
    }

    @Test
    fun reminderSnoozeCannotPrecedeItsSourceDelivery() {
        val dataset = ClosedCodecV1.decodeExport(reminderDataset(snoozeChildClaimId = SNOOZE_CHILD_ID))
        val source = dataset.reminders.single { it.reminderOccurrenceId.value == DELIVERED_SOURCE_ID }
        val due = LocalStampWireV1.fromObject(
            source.body.requiredElement("due_at", "test.source").asStrictObject("test.source.due_at"),
            "test.source.due_at",
        )
        val delayedDelivery = LocalStampWireV1(
            InstantWireV1.parse("2026-08-27T11:00:00.000Z"),
            DateWireV1.parse("2026-08-27"),
            "UTC",
            0,
        )
        val delayedSourceBody = JsonObject(
            source.body.element.toMutableMap().apply {
                this["delivered_at"] = delayedDelivery.toJson()
            },
        )
        val delayedSource = ReminderWireV1(
            StrictJsonObjectV1(ReminderSchemaV1.validateAndOrder(StrictJsonObjectV1(delayedSourceBody), "test.source")),
        )
        val delayedPosted = dataset.events.single { it.name == EventNameV1.REMINDER_POSTED }.copy(
            envelope = dataset.events.single { it.name == EventNameV1.REMINDER_POSTED }.envelope.copy(occurred = delayedDelivery),
            properties = ReminderPostedPropertiesV1(ReminderKindV1.FIXED, due, delayedDelivery, 3_600_000),
        )
        val mutant = dataset.copy(
            reminders = dataset.reminders.map { if (it.reminderOccurrenceId.value == DELIVERED_SOURCE_ID) delayedSource else it },
            events = dataset.events
                .map { if (it.name == EventNameV1.REMINDER_POSTED) delayedPosted else it }
                .sortedWith(
                    compareBy<vn.nhip2phut.domain.events.ProductEventWireV1> { it.envelope.occurred.occurredAtUtc }
                        .thenBy { it.envelope.eventId.value },
                ),
        )

        val failure = assertFailsWith<WireContractException> {
            DatasetConformanceV1.requireValid(mutant)
        }
        assertContains(failure.message.orEmpty(), "precedes source delivery")
    }

    @Test
    fun reminderOccurrenceIdsMustMatchTheirCanonicalPreimages() {
        val randomSourceId = reminderDataset(snoozeChildClaimId = SNOOZE_CHILD_ID)
            .replace(DELIVERED_SOURCE_ID, RANDOM_OCCURRENCE_ID)

        val error = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(randomSourceId)
        }
        assertContains(error.message.orEmpty(), "canonical reminder occurrence ID preimage")
    }

    @Test
    fun reminderMergeStillRequiresAnExactCompanionMirror() {
        ClosedCodecV1.decodeExport(mergedReminderDataset())

        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(mergedReminderDataset(includeMergeEvent = false))
        }
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(mergedReminderDataset(distanceMs = 899_999))
        }
        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(mergedReminderDataset(keptOccurrenceId = SNOOZE_CHILD_ID))
        }
        val splitTransaction = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                mergedReminderDataset(mergeOccurredAtUtc = "2026-08-27T10:00:00.001Z"),
            )
        }
        assertContains(splitTransaction.message.orEmpty(), "same snooze bundle LocalStamp")
    }

    @Test
    fun historicalReminderTransactionsMayEqualButNotFollowScheduleReplacement() {
        val replacementAtDelivery = """{"occurred_at_utc":"2026-08-27T10:00:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""
        ClosedCodecV1.decodeExport(
            mergedReminderDataset(
                scheduleReplacedAt = replacementAtDelivery,
                includeActiveReplacement = true,
            ),
        )
        val deliveredAfterReplacement = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                mergedReminderDataset(
                    distanceMs = 960_000,
                    targetStamp = """{"occurred_at_utc":"2026-08-27T10:16:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}""",
                    snoozeActionUtc = "2026-08-27T10:01:00.000Z",
                    scheduleReplacedAt = replacementAtDelivery,
                    includeActiveReplacement = true,
                    deliveredStamp = """{"occurred_at_utc":"2026-08-27T10:01:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}""",
                    deliveredAtUtc = "2026-08-27T10:01:00.000Z",
                    postedLatenessMs = 60_000,
                ),
            )
        }
        assertContains(deliveredAfterReplacement.message.orEmpty(), "delivery occurs after schedule replacement")

        val replacementBeforeSnooze = """{"occurred_at_utc":"2026-08-27T10:05:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""
        val failure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(
                mergedReminderDataset(
                    distanceMs = 1_800_000,
                    targetStamp = """{"occurred_at_utc":"2026-08-27T10:30:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}""",
                    snoozeActionUtc = "2026-08-27T10:15:00.000Z",
                    scheduleReplacedAt = replacementBeforeSnooze,
                    includeActiveReplacement = true,
                ),
            )
        }
        assertContains(failure.message.orEmpty(), "schedule replacement")
    }

    @Test
    fun scheduleEditCancellationMustShareTheExactScheduleReplacementTransaction() {
        val replacementStamp = """{"occurred_at_utc":"2026-08-27T10:45:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""
        ClosedCodecV1.decodeExport(
            cancelledReminderDataset(scheduleReplacedAt = replacementStamp),
        )

        listOf(
            cancelledReminderDataset(
                scheduleReplacedAt = "null",
                includeActiveReplacement = false,
            ),
            cancelledReminderDataset(
                scheduleReplacedAt = replacementStamp,
                cancelledAtUtc = "2026-08-27T10:44:00.000Z",
            ),
            cancelledReminderDataset(
                scheduleReplacedAt = replacementStamp,
                cancelledZoneId = "Europe/London",
                cancelledOffsetMinutes = 60,
            ),
        ).forEachIndexed { index, mutant ->
            val failure = assertFailsWith<WireContractException>("schedule-edit cancellation mutant $index") {
                ClosedCodecV1.decodeExport(mutant)
            }
            assertContains(failure.message.orEmpty(), "schedule_edit cancellation")
        }
    }

    @Test
    fun workScheduleSavedMustMirrorItsReferencedScheduleVersion() {
        ClosedCodecV1.decodeExport(scheduleDataset())

        listOf(
            scheduleDataset(eventEnabled = false),
            scheduleDataset(eventSelectedWeekdayCount = 2),
            scheduleDataset(eventReminderCount = 2),
            scheduleDataset(eventWorkStart = "08:30"),
            scheduleDataset(eventWorkEnd = "16:30"),
        ).forEachIndexed { index, mutant ->
            assertFailsWith<WireContractException>("schedule mirror mutant $index") {
                ClosedCodecV1.decodeExport(mutant)
            }
        }
    }

    @Test
    fun retainedScheduleSaveLineageMustBeAtomicAcyclicAndNotSelfReferential() {
        ClosedCodecV1.decodeExport(settingsScheduleDataset())

        val selfFailure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(settingsScheduleDataset(previousScheduleId = REPLACEMENT_SCHEDULE_ID))
        }
        assertContains(selfFailure.message.orEmpty(), "cannot reference the current schedule")

        listOf(
            settingsScheduleDataset(predecessorReplacedAt = laterStamp),
            settingsScheduleDataset(currentEffectiveFrom = laterStamp),
            settingsScheduleDataset(eventOccurredAtUtc = "2026-08-27T10:00:00.001Z"),
        ).forEachIndexed { index, mutant ->
            val failure = assertFailsWith<WireContractException>("schedule transaction stamp mutant $index") {
                ClosedCodecV1.decodeExport(mutant)
            }
            assertContains(failure.message.orEmpty(), "transaction LocalStamp")
        }
    }

    @Test
    fun onboardingScheduleSaveMustMirrorProfileCompletionAndRemainUniqueWhenRetained() {
        val stampFailure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(scheduleDataset(scheduleEffectiveFrom = scheduleEffectiveStamp))
        }
        assertContains(stampFailure.message.orEmpty(), "transaction LocalStamp")

        val uniquenessFailure = assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(twoOnboardingSchedulesDataset())
        }
        assertContains(uniquenessFailure.message.orEmpty(), "at most one retained onboarding work_schedule_saved")
    }

    @Test
    fun weeklySummaryViewedMustMirrorTheReferencedSummaryWeek() {
        ClosedCodecV1.decodeExport(weeklyDataset("2026-08-24"))

        assertFailsWith<WireContractException> {
            ClosedCodecV1.decodeExport(weeklyDataset("2026-08-17"))
        }
    }

    private fun scheduleDataset(
        eventEnabled: Boolean = true,
        eventSelectedWeekdayCount: Long = 1,
        eventReminderCount: Long = 1,
        eventWorkStart: String = "09:00",
        eventWorkEnd: String = "17:00",
        scheduleEffectiveFrom: String = stamp,
    ): String {
        val saved = """
            {
              "event_id":"00000000-0000-4000-8000-000000000012",
              "event_schema_version":1,
              "name":"work_schedule_saved",
              "occurred_at_utc":"2026-08-27T10:00:00.000Z",
              "local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":null,"schedule_version_id":"$SCHEDULE_ID","source":null,
              "properties":{
                "previous_schedule_version_id":null,
                "enabled":$eventEnabled,
                "selected_weekday_count":$eventSelectedWeekdayCount,
                "work_start":"$eventWorkStart",
                "work_end":"$eventWorkEnd",
                "reminder_count":$eventReminderCount,
                "change_source":"onboarding",
                "active_decision_invalidated":false
              }
            }
        """.trimIndent()
        return """
            {
              "metadata":{
                "export_schema_version":1,"exported_at_utc":"2026-08-27T11:00:00.000Z",
                "app_version":"1.0.0","content_version":"1.0.0","rule_version":1,"retention_policy_version":1,
                "record_counts":{"profile":1,"work_schedule":1,"check_ins":0,"decisions":0,"sessions":0,"feedback":0,"reminders":0,"events":3,"weekly_summaries":0}
              },
              "profile":[$profile],
              "work_schedule":[{
                "schedule_version_id":"$SCHEDULE_ID","enabled":true,"selected_weekdays":[4],
                "work_start":"09:00","work_end":"17:00","reminder_times":["10:00"],
                "effective_from":$scheduleEffectiveFrom,"replaced_at":null
              }],
              "check_ins":[],"decisions":[],"sessions":[],"feedback":[],"reminders":[],
              "events":[$validProfileEvents,$saved],
              "weekly_summaries":[]
            }
        """.trimIndent()
    }

    private fun settingsScheduleDataset(
        previousScheduleId: String = SCHEDULE_ID,
        predecessorReplacedAt: String = stamp,
        currentEffectiveFrom: String = stamp,
        eventOccurredAtUtc: String = "2026-08-27T10:00:00.000Z",
    ): String {
        val saved = """
            {
              "event_id":"00000000-0000-4000-8000-000000000012",
              "event_schema_version":1,
              "name":"work_schedule_saved",
              "occurred_at_utc":"$eventOccurredAtUtc",
              "local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":null,"schedule_version_id":"$REPLACEMENT_SCHEDULE_ID","source":null,
              "properties":{
                "previous_schedule_version_id":"$previousScheduleId",
                "enabled":true,"selected_weekday_count":1,"work_start":"09:00","work_end":"17:00","reminder_count":1,
                "change_source":"settings","active_decision_invalidated":false
              }
            }
        """.trimIndent()
        return """
            {
              "metadata":{
                "export_schema_version":1,"exported_at_utc":"2026-08-27T11:00:00.000Z",
                "app_version":"1.0.0","content_version":"1.0.0","rule_version":1,"retention_policy_version":1,
                "record_counts":{"profile":1,"work_schedule":2,"check_ins":0,"decisions":0,"sessions":0,"feedback":0,"reminders":0,"events":3,"weekly_summaries":0}
              },
              "profile":[$profile],
              "work_schedule":[
                {"schedule_version_id":"$SCHEDULE_ID","enabled":true,"selected_weekdays":[4],"work_start":"09:00","work_end":"17:00","reminder_times":["10:00"],"effective_from":$scheduleEffectiveStamp,"replaced_at":$predecessorReplacedAt},
                {"schedule_version_id":"$REPLACEMENT_SCHEDULE_ID","enabled":true,"selected_weekdays":[4],"work_start":"09:00","work_end":"17:00","reminder_times":["10:00"],"effective_from":$currentEffectiveFrom,"replaced_at":null}
              ],
              "check_ins":[],"decisions":[],"sessions":[],"feedback":[],"reminders":[],
              "events":[$validProfileEvents,$saved],"weekly_summaries":[]
            }
        """.trimIndent()
    }

    private fun twoOnboardingSchedulesDataset(): String {
        fun saved(eventId: String, scheduleId: String) = """
            {
              "event_id":"$eventId","event_schema_version":1,"name":"work_schedule_saved",
              "occurred_at_utc":"2026-08-27T10:00:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":null,"schedule_version_id":"$scheduleId","source":null,
              "properties":{"previous_schedule_version_id":null,"enabled":true,"selected_weekday_count":1,"work_start":"09:00","work_end":"17:00","reminder_count":1,"change_source":"onboarding","active_decision_invalidated":false}
            }
        """.trimIndent()
        return """
            {
              "metadata":{"export_schema_version":1,"exported_at_utc":"2026-08-27T11:00:00.000Z","app_version":"1.0.0","content_version":"1.0.0","rule_version":1,"retention_policy_version":1,"record_counts":{"profile":1,"work_schedule":2,"check_ins":0,"decisions":0,"sessions":0,"feedback":0,"reminders":0,"events":4,"weekly_summaries":0}},
              "profile":[$profile],
              "work_schedule":[
                {"schedule_version_id":"$SCHEDULE_ID","enabled":true,"selected_weekdays":[4],"work_start":"09:00","work_end":"17:00","reminder_times":["10:00"],"effective_from":$stamp,"replaced_at":$stamp},
                {"schedule_version_id":"$REPLACEMENT_SCHEDULE_ID","enabled":true,"selected_weekdays":[4],"work_start":"09:00","work_end":"17:00","reminder_times":["10:00"],"effective_from":$stamp,"replaced_at":null}
              ],
              "check_ins":[],"decisions":[],"sessions":[],"feedback":[],"reminders":[],
              "events":[$validProfileEvents,${saved("00000000-0000-4000-8000-000000000012", SCHEDULE_ID)},${saved("00000000-0000-4000-8000-000000000013", REPLACEMENT_SCHEDULE_ID)}],
              "weekly_summaries":[]
            }
        """.trimIndent()
    }

    private fun weeklyDataset(viewedWeek: String): String {
        val generated = """
            {
              "event_id":"00000000-0000-4000-8000-000000000012","event_schema_version":1,
              "name":"weekly_summary_generated","occurred_at_utc":"2026-08-27T10:00:00.000Z",
              "local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":null,"schedule_version_id":null,"source":null,
              "properties":{"week_start_local_date":"2026-08-24","summary_id":"$SUMMARY_ID","qualified_break_days":0,"completed_count":0}
            }
        """.trimIndent()
        val viewed = """
            {
              "event_id":"00000000-0000-4000-8000-000000000013","event_schema_version":1,
              "name":"weekly_summary_viewed","occurred_at_utc":"2026-08-27T10:00:00.000Z",
              "local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":null,"schedule_version_id":null,"source":null,
              "properties":{"summary_id":"$SUMMARY_ID","week_start_local_date":"$viewedWeek"}
            }
        """.trimIndent()
        return """
            {
              "metadata":{
                "export_schema_version":1,"exported_at_utc":"2026-08-27T11:00:00.000Z",
                "app_version":"1.0.0","content_version":"1.0.0","rule_version":1,"retention_policy_version":1,
                "record_counts":{"profile":1,"work_schedule":0,"check_ins":0,"decisions":0,"sessions":0,"feedback":0,"reminders":0,"events":4,"weekly_summaries":1}
              },
              "profile":[$profile],
              "work_schedule":[],"check_ins":[],"decisions":[],"sessions":[],"feedback":[],"reminders":[],
              "events":[$validProfileEvents,$generated,$viewed],
              "weekly_summaries":[{
                "summary_id":"$SUMMARY_ID","week_start_local_date":"2026-08-24","week_zone_id":"UTC",
                "occurred_at_utc":"2026-08-27T10:00:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
                "qualified_break_days":0,"started_count":0,"completed_count":0,
                "effort_easy_count":0,"effort_moderate_count":0,"effort_too_hard_count":0,
                "pain_yes_count":0,"pain_no_count":0,"context_yes_count":0,"context_no_count":0,
                "reminder_opened_count":0,"reminder_snoozed_count":0,"reminder_dismissed_count":0,
                "completion_rate":$emptyRate,"context_fit_rate":$emptyRate,"new_or_worse_pain_rate":$emptyRate
              }]
            }
        """.trimIndent()
    }

    private fun mergedReminderDataset(
        includeMergeEvent: Boolean = true,
        distanceMs: Long = 900_000,
        keptOccurrenceId: String = DELIVERED_SOURCE_ID,
        targetStamp: String = fifteenMinuteTargetStamp,
        snoozeActionUtc: String = "2026-08-27T10:00:00.000Z",
        mergeOccurredAtUtc: String = snoozeActionUtc,
        scheduleReplacedAt: String = "null",
        includeActiveReplacement: Boolean = false,
        deliveredStamp: String = stamp,
        deliveredAtUtc: String = "2026-08-27T10:00:00.000Z",
        postedLatenessMs: Long = 0,
    ): String {
        val mergeEvent = if (includeMergeEvent) {
            """
                ,
                {
                  "event_id":"00000000-0000-4000-8000-000000000016",
                  "event_schema_version":1,
                  "name":"reminder_merged",
                   "occurred_at_utc":"$mergeOccurredAtUtc",
                  "local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
                  "installation_id":"00000000-0000-4000-8000-000000000001",
                  "decision_id":null,"session_id":null,"reminder_occurrence_id":"$SNOOZE_CHILD_ID","schedule_version_id":null,"source":null,
                  "properties":{"kept_occurrence_id":"$keptOccurrenceId","distance_ms":$distanceMs,"tie_break":"earlier_due"}
                }
            """.trimIndent()
        } else {
            ""
        }
        return reminderDataset(
            snoozeChildClaimId = SNOOZE_CHILD_ID,
            childStatus = "MERGED",
            childMergedInto = DELIVERED_SOURCE_ID,
            targetStamp = targetStamp,
            snoozeActionUtc = snoozeActionUtc,
            eventCount = if (includeMergeEvent) 7 else 6,
            extraReminderEvents = mergeEvent,
            scheduleReplacedAt = scheduleReplacedAt,
            includeActiveReplacement = includeActiveReplacement,
            deliveredStamp = deliveredStamp,
            deliveredAtUtc = deliveredAtUtc,
            postedLatenessMs = postedLatenessMs,
        )
    }

    private fun skippedReminderDataset(
        skippedAtUtc: String,
        latenessMs: Long,
        status: String = "SKIPPED_LATE",
        workEnd: String = "17:00",
        scheduleReplacedAt: String = "null",
        includeActiveReplacement: Boolean = false,
    ): String {
        val skipped = """
            ,
            {
              "event_id":"00000000-0000-4000-8000-000000000016",
              "event_schema_version":1,
              "name":"reminder_skipped",
              "occurred_at_utc":"$skippedAtUtc",
              "local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":"$SNOOZE_CHILD_ID","schedule_version_id":null,"source":null,
              "properties":{"status":"$status","lateness_ms":$latenessMs}
            }
        """.trimIndent()
        return reminderDataset(
            snoozeChildClaimId = SNOOZE_CHILD_ID,
            childStatus = status,
            eventCount = 7,
            extraReminderEvents = skipped,
            workEnd = workEnd,
            scheduleReplacedAt = scheduleReplacedAt,
            includeActiveReplacement = includeActiveReplacement,
        )
    }

    private fun cancelledReminderDataset(
        scheduleReplacedAt: String,
        cancelledAtUtc: String = "2026-08-27T10:45:00.000Z",
        cancelledZoneId: String = "UTC",
        cancelledOffsetMinutes: Int = 0,
        includeActiveReplacement: Boolean = true,
    ): String {
        val cancelled = """
            ,
            {
              "event_id":"00000000-0000-4000-8000-000000000016",
              "event_schema_version":1,
              "name":"reminder_cancelled",
              "occurred_at_utc":"$cancelledAtUtc",
              "local_date":"2026-08-27","zone_id":"$cancelledZoneId","utc_offset_minutes":$cancelledOffsetMinutes,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":"$SNOOZE_CHILD_ID","schedule_version_id":null,"source":null,
              "properties":{"reason":"schedule_edit","resulting_status":"CANCELLED"}
            }
        """.trimIndent()
        return reminderDataset(
            snoozeChildClaimId = SNOOZE_CHILD_ID,
            childStatus = "CANCELLED",
            eventCount = 7,
            extraReminderEvents = cancelled,
            scheduleReplacedAt = scheduleReplacedAt,
            includeActiveReplacement = includeActiveReplacement,
        )
    }

    private fun reminderDataset(
        snoozeChildClaimId: String,
        childStatus: String = "SNOOZED",
        childMergedInto: String? = null,
        targetStamp: String = snoozeTargetStamp,
        snoozeActionUtc: String = "2026-08-27T10:30:00.000Z",
        eventCount: Int = 6,
        extraReminderEvents: String = "",
        workEnd: String = "17:00",
        deliveredStamp: String = stamp,
        deliveredAtUtc: String = "2026-08-27T10:00:00.000Z",
        postedLatenessMs: Long = 0,
        scheduleReplacedAt: String = "null",
        includeActiveReplacement: Boolean = false,
    ): String = """
        {
          "metadata":{
            "export_schema_version":1,
            "exported_at_utc":"2026-08-27T11:00:00.000Z",
            "app_version":"1.0.0",
            "content_version":"1.0.0",
            "rule_version":1,
            "retention_policy_version":1,
            "record_counts":{
              "profile":1,"work_schedule":${if (includeActiveReplacement) 2 else 1},"check_ins":0,"decisions":0,"sessions":0,
              "feedback":0,"reminders":2,"events":$eventCount,"weekly_summaries":0
            }
          },
          "profile":[$profile],
          "work_schedule":[{
            "schedule_version_id":"$SCHEDULE_ID",
            "enabled":true,
            "selected_weekdays":[4],
            "work_start":"09:00",
            "work_end":"$workEnd",
            "reminder_times":["10:00"],
            "effective_from":$scheduleEffectiveStamp,
            "replaced_at":$scheduleReplacedAt
          }${if (includeActiveReplacement) ",{" +
            "\"schedule_version_id\":\"$REPLACEMENT_SCHEDULE_ID\",\"enabled\":true,\"selected_weekdays\":[4]," +
            "\"work_start\":\"09:00\",\"work_end\":\"17:00\",\"reminder_times\":[\"10:00\"]," +
            "\"effective_from\":$scheduleReplacedAt,\"replaced_at\":null}" else ""}],
          "check_ins":[],"decisions":[],"sessions":[],"feedback":[],
          "reminders":[
            {
              "reminder_occurrence_id":"$DELIVERED_SOURCE_ID",
              "schedule_version_id":"$SCHEDULE_ID",
              "kind":"fixed",
              "slot_index":0,
              "local_date":"2026-08-27",
              "generation":0,
              "creation_reason":"initial",
              "supersedes_occurrence_id":null,
              "merged_into_occurrence_id":null,
              "is_selected_workday_at_due":true,
              "due_at":$stamp,
              "delivered_at":$deliveredStamp,
              "first_opened_at":null,
              "dismissed_at":null,
              "status":"DELIVERED"
            },
            {
              "reminder_occurrence_id":"$SNOOZE_CHILD_ID",
              "schedule_version_id":"$SCHEDULE_ID",
              "kind":"snooze",
              "parent_occurrence_id":"$DELIVERED_SOURCE_ID",
              "ordinal":0,
              "supersedes_occurrence_id":null,
              "merged_into_occurrence_id":${childMergedInto.jsonNullable()},
              "is_selected_workday_at_due":true,
              "due_at":$targetStamp,
              "delivered_at":null,
              "first_opened_at":null,
              "dismissed_at":null,
              "status":"$childStatus"
            }
          ],
          "events":[
            {
              "event_id":"00000000-0000-4000-8000-000000000012",
              "event_schema_version":1,
              "name":"reminder_scheduled",
              "occurred_at_utc":"2026-08-27T09:59:00.000Z",
              "local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":"$DELIVERED_SOURCE_ID","schedule_version_id":"$SCHEDULE_ID","source":null,
              "properties":{
                "due_at":$stamp,
                "kind":"fixed",
                "supersedes_occurrence_id":null,
                "logical_fixed_key":{"schedule_version_id":"$SCHEDULE_ID","slot_index":0,"local_date":"2026-08-27","kind":"fixed"},
                "generation":0,
                "creation_reason":"initial"
              }
            },
            $validProfileEvents,
            {
              "event_id":"00000000-0000-4000-8000-000000000013",
              "event_schema_version":1,
              "name":"reminder_posted",
              "occurred_at_utc":"$deliveredAtUtc",
              "local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":"$DELIVERED_SOURCE_ID","schedule_version_id":null,"source":null,
              "properties":{"kind":"fixed","due_at":$stamp,"delivered_at":$deliveredStamp,"lateness_ms":$postedLatenessMs}
            },
            {
              "event_id":"00000000-0000-4000-8000-000000000014",
              "event_schema_version":1,
              "name":"reminder_snoozed",
              "occurred_at_utc":"$snoozeActionUtc",
              "local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":"$DELIVERED_SOURCE_ID","schedule_version_id":null,"source":null,
              "properties":{"snooze_occurrence_id":"$snoozeChildClaimId","duration_minutes":15,"target_at":$targetStamp,"ordinal":0,"supersedes_occurrence_id":null}
            },
            {
              "event_id":"00000000-0000-4000-8000-000000000015",
              "event_schema_version":1,
              "name":"reminder_scheduled",
              "occurred_at_utc":"$snoozeActionUtc",
              "local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0,
              "installation_id":"00000000-0000-4000-8000-000000000001",
              "decision_id":null,"session_id":null,"reminder_occurrence_id":"$SNOOZE_CHILD_ID","schedule_version_id":"$SCHEDULE_ID","source":null,
              "properties":{"due_at":$targetStamp,"kind":"snooze","supersedes_occurrence_id":null,"parent_occurrence_id":"$DELIVERED_SOURCE_ID","ordinal":0}
            }$extraReminderEvents
          ],
          "weekly_summaries":[]
        }
    """.trimIndent()

    private fun profileDataset(events: String, eventCount: Int): String = """
        {
          "metadata":{
            "export_schema_version":1,
            "exported_at_utc":"2026-08-27T10:02:00.000Z",
            "app_version":"1.0.0",
            "content_version":"1.0.0",
            "rule_version":1,
            "retention_policy_version":1,
            "record_counts":{
              "profile":1,"work_schedule":0,"check_ins":0,"decisions":0,"sessions":0,
              "feedback":0,"reminders":0,"events":$eventCount,"weekly_summaries":0
            }
          },
          "profile":[$profile],
          "work_schedule":[],"check_ins":[],"decisions":[],"sessions":[],"feedback":[],"reminders":[],
          "events":[$events],
          "weekly_summaries":[]
        }
    """.trimIndent()

    private val profile = """
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
            "content_digest":"${"0".repeat(64)}",
            "acknowledged_at":$stamp
          }],
          "current_safety_acknowledgement_id":"00000000-0000-4000-8000-000000000002"
        }
    """.trimIndent()

    private val validProfileEvents = """
        {
          "event_id":"00000000-0000-4000-8000-000000000010",
          "event_schema_version":1,
          "name":"scope_acknowledged",
          "occurred_at_utc":"2026-08-27T10:00:00.000Z",
          "local_date":"2026-08-27",
          "zone_id":"UTC",
          "utc_offset_minutes":0,
          "installation_id":"00000000-0000-4000-8000-000000000001",
          "decision_id":null,"session_id":null,"reminder_occurrence_id":null,"schedule_version_id":null,"source":null,
          "properties":{
            "acknowledgement_id":"00000000-0000-4000-8000-000000000002",
            "kind":"onboarding",
            "eligibility_confirmed":true,
            "content_version":"1.0.0",
            "content_digest":"${"0".repeat(64)}"
          }
        },
        {
          "event_id":"00000000-0000-4000-8000-000000000011",
          "event_schema_version":1,
          "name":"onboarding_completed",
          "occurred_at_utc":"2026-08-27T10:00:00.000Z",
          "local_date":"2026-08-27",
          "zone_id":"UTC",
          "utc_offset_minutes":0,
          "installation_id":"00000000-0000-4000-8000-000000000001",
          "decision_id":null,"session_id":null,"reminder_occurrence_id":null,"schedule_version_id":null,"source":null,
          "properties":{
            "duration_ms":1,
            "activation_boot_marker":1,
            "activation_elapsed_realtime_ms":2,
            "activation_clock_generation":4,
            "activation_wall_minus_elapsed_ms":5
          }
        }
    """.trimIndent()

    private val snoozeTargetStamp = """{"occurred_at_utc":"2026-08-27T10:45:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""
    private val fifteenMinuteTargetStamp = """{"occurred_at_utc":"2026-08-27T10:15:00.000Z","local_date":"2026-08-27","zone_id":"UTC","utc_offset_minutes":0}"""

    private fun String?.jsonNullable(): String = this?.let { "\"$it\"" } ?: "null"

    private companion object {
        const val stamp = "{\"occurred_at_utc\":\"2026-08-27T10:00:00.000Z\",\"local_date\":\"2026-08-27\",\"zone_id\":\"UTC\",\"utc_offset_minutes\":0}"
        const val scheduleEffectiveStamp = "{\"occurred_at_utc\":\"2026-08-27T09:00:00.000Z\",\"local_date\":\"2026-08-27\",\"zone_id\":\"UTC\",\"utc_offset_minutes\":0}"
        const val laterStamp = "{\"occurred_at_utc\":\"2026-08-27T10:00:00.001Z\",\"local_date\":\"2026-08-27\",\"zone_id\":\"UTC\",\"utc_offset_minutes\":0}"
        const val emptyRate = "{\"numerator\":0,\"denominator\":0,\"value_percent\":null,\"suppression_reason\":\"insufficient_sample\"}"
        const val SCHEDULE_ID = "00000000-0000-4000-8000-000000000030"
        const val REPLACEMENT_SCHEDULE_ID = "00000000-0000-4000-8000-000000000031"
        const val SUMMARY_ID = "00000000-0000-4000-8000-000000000050"
        const val DELIVERED_SOURCE_ID = "098c57a8-dd78-8744-bff6-ea89644c87d2"
        const val SNOOZE_CHILD_ID = "fb51bdb4-011d-8132-b550-c87fa30213d0"
        const val RANDOM_OCCURRENCE_ID = "00000000-0000-4000-8000-000000000099"
    }
}
