package vn.nhip2phut.domain.wire.v1

import kotlin.test.Test
import kotlin.test.assertFailsWith

class WeeklySummaryConformanceV1Test {
    @Test
    fun completionAndPainRatesMustMirrorTheirVisibleCountBuckets() {
        validate(summaryJson())

        listOf(
            summaryJson(completionRate = rate(3, 5, 60)),
            summaryJson(painRate = rate(2, 5, 40)),
        ).forEachIndexed { index, mutant ->
            assertFailsWith<WireContractException>("rate mutant $index") { validate(mutant) }
        }
    }

    @Test
    fun contextRateMayUseCompletedOnlyCohortDifferentFromVisibleCounts() {
        validate(
            summaryJson(
                contextYesCount = 4,
                contextNoCount = 1,
                contextRate = suppressedRate(3, 4),
            ),
        )
    }

    @Test
    fun painCountBucketAdditionFailsClosedOnInt64Overflow() {
        val mutant = summaryJson(
            painYesCount = Long.MAX_VALUE,
            painNoCount = 1,
            painRate = rate(0, Long.MAX_VALUE, 0),
        )

        assertFailsWith<WireContractException> { validate(mutant) }
    }

    private fun validate(source: String) {
        WeeklySummarySchemaV1.validateAndOrder(StrictJsonV1.parseObject(source), "weekly-summary")
    }

    private fun summaryJson(
        contextYesCount: Long = 3,
        contextNoCount: Long = 1,
        painYesCount: Long = 1,
        painNoCount: Long = 4,
        completionRate: String = rate(4, 5, 80),
        contextRate: String = suppressedRate(3, 4),
        painRate: String = rate(1, 5, 20),
    ): String = """
        {
          "summary_id":"00000000-0000-4000-8000-000000000050",
          "week_start_local_date":"2026-08-24",
          "week_zone_id":"UTC",
          "occurred_at_utc":"2026-08-27T10:00:00.000Z",
          "local_date":"2026-08-27",
          "zone_id":"UTC",
          "utc_offset_minutes":0,
          "qualified_break_days":1,
          "started_count":5,
          "completed_count":4,
          "effort_easy_count":1,
          "effort_moderate_count":2,
          "effort_too_hard_count":1,
          "pain_yes_count":$painYesCount,
          "pain_no_count":$painNoCount,
          "context_yes_count":$contextYesCount,
          "context_no_count":$contextNoCount,
          "reminder_opened_count":2,
          "reminder_snoozed_count":1,
          "reminder_dismissed_count":1,
          "completion_rate":$completionRate,
          "context_fit_rate":$contextRate,
          "new_or_worse_pain_rate":$painRate
        }
    """.trimIndent()

    private fun rate(numerator: Long, denominator: Long, percent: Long): String =
        """{"numerator":$numerator,"denominator":$denominator,"value_percent":$percent,"suppression_reason":null}"""

    private fun suppressedRate(numerator: Long, denominator: Long): String =
        """{"numerator":$numerator,"denominator":$denominator,"value_percent":null,"suppression_reason":"insufficient_sample"}"""
}
