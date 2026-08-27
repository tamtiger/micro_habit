package vn.nhip2phut.domain.wire.v1

/** Canonical signed-content routine-to-mode mapping from CNT-001. */
internal object RoutineModeCatalogV1 {
    private val modeByRoutineId = mapOf(
        "REC-01" to "RECOVER",
        "REC-02" to "RECOVER",
        "MAI-01" to "MAINTAIN",
        "MAI-02" to "MAINTAIN",
        "BUI-01" to "BUILD",
        "BUI-02" to "BUILD",
    )

    fun modeFor(routineId: String, path: String): String =
        modeByRoutineId[routineId] ?: fail("$path.routine_id", "unknown routine_id")

    fun requireMode(routineId: String, mode: String, path: String, modeName: String) {
        if (modeFor(routineId, path) != mode) {
            fail(path, "routine_id does not belong to $modeName")
        }
    }
}
