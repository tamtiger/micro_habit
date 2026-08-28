package vn.nhip2phut.domain.schedule

import java.util.UUID
import vn.nhip2phut.domain.model.LocalStamp

data class WorkScheduleVersion(
    val id: UUID,
    val enabled: Boolean,
    val selectedWeekdays: Set<Int>,
    val workStart: ScheduleTime,
    val workEnd: ScheduleTime,
    val reminderTimes: List<ScheduleTime>,
    val effectiveFrom: LocalStamp,
    val replacedAt: LocalStamp?,
) {
    init {
        require(selectedWeekdays.isNotEmpty()) { "At least one weekday is required." }
        require(selectedWeekdays.all { it in ISO_WEEKDAY_RANGE }) {
            "Weekdays must use ISO-8601 values 1 through 7."
        }
        require(workStart < workEnd) { "Work start must be before work end." }
        require(reminderTimes.size in REMINDER_COUNT_RANGE) { "One or two reminder times are required." }
        require(reminderTimes.zipWithNext().all { (previous, next) -> previous < next }) {
            "Reminder times must be distinct and sorted in ascending order."
        }
        require(reminderTimes.all { it >= workStart && it < workEnd }) {
            "Reminder times must be inside the half-open work window."
        }
        require(replacedAt == null || replacedAt.instant >= effectiveFrom.instant) {
            "Replacement time cannot precede the effective time."
        }
    }

    companion object {
        private val ISO_WEEKDAY_RANGE = 1..7
        private val REMINDER_COUNT_RANGE = 1..2
    }
}
