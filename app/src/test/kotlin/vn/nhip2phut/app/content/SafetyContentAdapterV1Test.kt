package vn.nhip2phut.app.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import vn.nhip2phut.app.navigation.AppEntryContractStateV1
import vn.nhip2phut.domain.onboarding.SafetyContentAvailabilityV1
import vn.nhip2phut.domain.onboarding.SafetyContentIdentityV1

class SafetyContentAdapterV1Test {
    private val debugIdentity = SafetyContentIdentityV1.debugFixture(
        manifestVersion = "1.0.0",
        globalSafetyDigestSha256 = "d".repeat(64),
    )

    @Test
    fun `typed debug fixture is ready only with the exact non-production marker`() {
        val resolved = SafetyContentAdapterV1.resolve(
            availability = debugIdentity.forDebugBuild(),
            contractStatus = SafetyContentContractStatusV1.VERIFIED,
        )

        val ready = assertIs<SafetyContentResolutionV1.Ready>(resolved)
        assertEquals(debugIdentity, ready.identity)
        assertEquals(
            "NON_PRODUCTION_NOT_CLINICALLY_APPROVED",
            ready.identity.approvalMarker,
        )
        assertTrue(ready.identity.isDebugOnly)
        assertEquals(AppEntryContractStateV1.READY, ready.contractState)
    }

    @Test
    fun `release without a signed artifact fails closed before Home and check-in`() {
        val resolved = SafetyContentAdapterV1.resolve(
            availability = SafetyContentAvailabilityV1.Unavailable,
            contractStatus = SafetyContentContractStatusV1.VERIFIED,
        )

        val blocked = assertIs<SafetyContentResolutionV1.Blocked>(resolved)
        assertEquals(AppEntryContractStateV1.CONTENT_UNAVAILABLE, blocked.contractState)
        assertEquals(SafetyContentBlockReasonV1.MISSING_SIGNED_ARTIFACT, blocked.reason)
        assertFalse(blocked.canEnterHome)
        assertFalse(blocked.canStartCheckIn)
    }

    @Test
    fun `invalid typed slot binding or digest fails closed without a raw fallback`() {
        listOf(
            SafetyContentContractStatusV1.INVALID_REQUIRED_SLOT to
                SafetyContentBlockReasonV1.INVALID_REQUIRED_SLOT,
            SafetyContentContractStatusV1.INVALID_ROUTE_BINDING to
                SafetyContentBlockReasonV1.INVALID_ROUTE_BINDING,
            SafetyContentContractStatusV1.DIGEST_MISMATCH to
                SafetyContentBlockReasonV1.DIGEST_MISMATCH,
        ).forEach { (status, expectedReason) ->
            val resolved = SafetyContentAdapterV1.resolve(
                availability = debugIdentity.forDebugBuild(),
                contractStatus = status,
            )

            val blocked = assertIs<SafetyContentResolutionV1.Blocked>(resolved)
            assertEquals(AppEntryContractStateV1.DATA_ERROR, blocked.contractState, "status=$status")
            assertEquals(expectedReason, blocked.reason, "status=$status")
            assertFalse(blocked.canEnterHome, "status=$status")
            assertFalse(blocked.canStartCheckIn, "status=$status")
        }
    }
}
