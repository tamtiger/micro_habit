package vn.nhip2phut.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class AppDestinationTest {
    @Test
    fun `foundation is the only DEL-01 destination and the start destination`() {
        assertEquals(listOf("foundation"), AppDestination.Entries.map(AppDestination::route))
        assertEquals(AppDestination.Foundation, AppDestination.Start)
    }
}
