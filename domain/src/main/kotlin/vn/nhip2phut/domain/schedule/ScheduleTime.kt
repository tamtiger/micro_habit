package vn.nhip2phut.domain.schedule

@JvmInline
value class ScheduleTime private constructor(val wire: String) : Comparable<ScheduleTime> {
    override fun compareTo(other: ScheduleTime): Int = wire.compareTo(other.wire)
    override fun toString(): String = wire

    companion object {
        private val Pattern = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")

        fun parse(raw: String): ScheduleTime? {
            if (!Pattern.matches(raw)) return null
            return ScheduleTime(raw)
        }
    }
}

