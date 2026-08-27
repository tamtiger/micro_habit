package vn.nhip2phut.domain.model

enum class AcuteIssue(val wire: String) {
    NONE("none"),
    ACUTE_ILLNESS("acute_illness"),
    NEW_OR_WORSENING_PAIN_OR_INJURY("new_or_worsening_pain_or_injury"),
    MEDICALLY_RESTRICTED("medically_restricted");

    companion object {
        fun fromWire(wire: String): AcuteIssue? = values().firstOrNull { it.wire == wire }
    }
}

enum class Energy(val wire: String) {
    LOW("low"),
    OKAY("okay"),
    GOOD("good");

    companion object {
        fun fromWire(wire: String): Energy? = values().firstOrNull { it.wire == wire }
    }
}

enum class Stiffness(val wire: String) {
    NONE("none"),
    MILD("mild"),
    NOTABLE("notable");

    companion object {
        fun fromWire(wire: String): Stiffness? = values().firstOrNull { it.wire == wire }
    }
}

enum class Intent(val wire: String) {
    REST("rest"),
    GENTLE("gentle"),
    MODERATE("moderate");

    companion object {
        fun fromWire(wire: String): Intent? = values().firstOrNull { it.wire == wire }
    }
}

enum class Mode {
    RECOVER,
    MAINTAIN,
    BUILD,
}

enum class DayModeCapMode {
    RECOVER,
    MAINTAIN,
}

fun DayModeCapMode.asMode(): Mode = when (this) {
    DayModeCapMode.RECOVER -> Mode.RECOVER
    DayModeCapMode.MAINTAIN -> Mode.MAINTAIN
}

