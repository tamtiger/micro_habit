package vn.nhip2phut.domain.wire.v1

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Cross-record schedule invariants that apply only to fixed reminder occurrences. */
internal object ReminderScheduleConformanceV1 {
    fun requireValid(
        schedulesById: Map<String, WorkScheduleWireV1>,
        reminders: List<ReminderWireV1>,
    ) {
        reminders.forEach { reminder ->
            val path = "export.reminders[${reminder.reminderOccurrenceId}]"
            val scheduleId = reminder.body.requiredString("schedule_version_id", path)
            val pending = reminder.body.requiredString("status", path) in setOf("SCHEDULED", "SNOOZED")
            val schedule = schedulesById[scheduleId]
                ?: fail(path, "reminder requires a referenced enabled schedule")
            val schedulePath = "export.work_schedule[$scheduleId]"
            if (!schedule.body.requiredBoolean("enabled", schedulePath)) {
                fail(path, "reminder requires a referenced enabled schedule")
            }
            if (pending && schedule.body.requiredElement("replaced_at", schedulePath) !== kotlinx.serialization.json.JsonNull) {
                fail(path, "pending reminder requires a referenced enabled schedule that is unreplaced")
            }
            if (reminder.kind != "fixed") return@forEach

            val fixedSchedule = schedule

            val reminderTimes = fixedSchedule.body.requiredElement("reminder_times", schedulePath)
                .asArray("$schedulePath.reminder_times")
            val slotIndex = reminder.body.requiredInt64("slot_index", path)
            if (slotIndex < 0 || slotIndex >= reminderTimes.size.toLong()) {
                fail(path, "fixed reminder slot_index must be less than reminder_times.size")
            }

            val localDate = DateWireV1.parse(reminder.body.requiredString("local_date", path))
            val selectedWeekdays = fixedSchedule.body.requiredElement("selected_weekdays", schedulePath)
                .asArray("$schedulePath.selected_weekdays")
                .mapTo(hashSetOf()) { it.asInt64("$schedulePath.selected_weekdays") }
            if (localDate.date.dayOfWeek.value.toLong() !in selectedWeekdays) {
                fail(path, "fixed reminder local_date must be a selected weekday")
            }

            val effectiveFrom = LocalStampWireV1.fromObject(
                fixedSchedule.body.requiredElement("effective_from", schedulePath)
                    .asStrictObject("$schedulePath.effective_from"),
                "$schedulePath.effective_from",
            )
            if (localDate < effectiveFrom.localDate) {
                fail(path, "fixed reminder local_date must not precede schedule effective_from")
            }

            val duePath = "$path.due_at"
            val due = LocalStampWireV1.fromObject(
                reminder.body.requiredElement("due_at", path).asStrictObject(duePath),
                duePath,
            )
            if (due.occurredAtUtc < effectiveFrom.occurredAtUtc) {
                fail(path, "fixed reminder due_at must not precede schedule effective_from")
            }

            val indexedTime = TimeMinuteWireV1.parse(
                reminderTimes[slotIndex.toInt()].asString("$schedulePath.reminder_times[$slotIndex]"),
            )
            val zone = ZoneId.of(due.zoneId)
            val localDateTime = LocalDateTime.of(localDate.date, indexedTime.time)
            val offsets = zone.rules.getValidOffsets(localDateTime)
            val resolved = resolveScheduleWallTime(localDate.date, indexedTime.time, due.zoneId)
            val workStart = TimeMinuteWireV1.parse(fixedSchedule.body.requiredString("work_start", schedulePath)).time
            val workEnd = TimeMinuteWireV1.parse(fixedSchedule.body.requiredString("work_end", schedulePath)).time
            val resolvedTime = resolved.toLocalTime()
            if (resolvedTime < workStart || resolvedTime >= workEnd) {
                fail(path, "fixed reminder resolved time must remain inside the half-open work window")
            }
            val matchesResolvedStamp = due.occurredAtUtc.instant == resolved.toInstant() &&
                due.localDate.date == resolved.toLocalDate() &&
                due.utcOffsetMinutes == resolved.offset.totalSeconds / 60L
            if (!matchesResolvedStamp) {
                val detail = when {
                    due.localDate.date != resolved.toLocalDate() ->
                        "fixed reminder due_at.local_date must equal the resolved reminder local date"
                    offsets.size > 1 -> "fixed reminder due_at must use the earlier overlap offset"
                    else -> "fixed reminder due_at must equal the resolved indexed reminder time"
                }
                fail(path, detail)
            }
        }
    }

    internal fun resolveScheduleWallTime(
        localDate: java.time.LocalDate,
        localTime: java.time.LocalTime,
        zoneId: String,
    ): ZonedDateTime {
        val zone = ZoneId.of(zoneId)
        val localDateTime = LocalDateTime.of(localDate, localTime)
        val offsets = zone.rules.getValidOffsets(localDateTime)
        return when (offsets.size) {
            0 -> zone.rules.getTransition(localDateTime).dateTimeAfter.atZone(zone)
            1 -> ZonedDateTime.ofLocal(localDateTime, zone, offsets.single())
            else -> ZonedDateTime.ofLocal(localDateTime, zone, offsets.first())
        }
    }
}
