package vn.nhip2phut.domain.onboarding

import vn.nhip2phut.domain.schedule.ScheduleTime
import vn.nhip2phut.domain.wire.v1.SemVerWireV1
import vn.nhip2phut.domain.wire.v1.Sha256DigestWireV1

private const val NON_PRODUCTION_APPROVAL_MARKER_V1 =
    "NON_PRODUCTION_NOT_CLINICALLY_APPROVED"

data class SafetyContentIdentityV1 private constructor(
    val manifestVersion: SemVerWireV1,
    val globalSafetyDigestSha256: Sha256DigestWireV1,
    val approvalMarker: String,
    val isDebugOnly: Boolean,
) {
    fun forDebugBuild(): SafetyContentAvailabilityV1 =
        SafetyContentAvailabilityV1.DebugFixture(this)

    fun forReleaseBuild(): SafetyContentAvailabilityV1 =
        SafetyContentAvailabilityV1.Unavailable

    companion object {
        fun debugFixture(
            manifestVersion: String,
            globalSafetyDigestSha256: String,
        ): SafetyContentIdentityV1 = SafetyContentIdentityV1(
            manifestVersion = SemVerWireV1.parse(manifestVersion),
            globalSafetyDigestSha256 = Sha256DigestWireV1.parse(globalSafetyDigestSha256),
            approvalMarker = NON_PRODUCTION_APPROVAL_MARKER_V1,
            isDebugOnly = true,
        )
    }
}

sealed interface SafetyContentAvailabilityV1 {
    data class DebugFixture(val identity: SafetyContentIdentityV1) :
        SafetyContentAvailabilityV1

    data object Unavailable : SafetyContentAvailabilityV1
}

data class ValidatedInitialScheduleV1 private constructor(
    val selectedWeekdays: List<Int>,
    val workStart: ScheduleTime,
    val workEnd: ScheduleTime,
    val reminderTimes: List<ScheduleTime>,
) {
    companion object {
        fun create(
            selectedWeekdays: Set<Int>,
            workStart: String,
            workEnd: String,
            reminderTimes: List<String>,
        ): Result<ValidatedInitialScheduleV1> = runCatching {
            require(selectedWeekdays.isNotEmpty()) {
                "selectedWeekdays must not be empty"
            }
            require(selectedWeekdays.all { it in 1..7 }) {
                "selectedWeekdays must contain only ISO weekdays 1..7"
            }

            val parsedStart = requireNotNull(ScheduleTime.parse(workStart)) {
                "workStart must be canonical HH:mm"
            }
            val parsedEnd = requireNotNull(ScheduleTime.parse(workEnd)) {
                "workEnd must be canonical HH:mm"
            }
            require(parsedStart < parsedEnd) {
                "workStart must be before workEnd"
            }

            require(reminderTimes.size in 1..2) {
                "one or two reminder times are required"
            }
            val parsedReminders = reminderTimes.map { raw ->
                requireNotNull(ScheduleTime.parse(raw)) {
                    "reminder time must be canonical HH:mm"
                }
            }
            require(parsedReminders.zipWithNext().all { (left, right) -> left < right }) {
                "reminder times must be strictly sorted and unique"
            }
            require(parsedReminders.all { it >= parsedStart && it < parsedEnd }) {
                "reminder times must be inside the work window"
            }

            ValidatedInitialScheduleV1(
                selectedWeekdays = selectedWeekdays.sorted(),
                workStart = parsedStart,
                workEnd = parsedEnd,
                reminderTimes = parsedReminders,
            )
        }
    }
}
