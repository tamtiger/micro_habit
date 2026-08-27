package vn.nhip2phut.data.codec

import kotlin.test.Test
import kotlin.test.assertFailsWith

class DuplicateKeyJsonGuardTest {
    @Test
    fun rejectsDuplicateKeysInNestedObjects() {
        assertFailsWith<DuplicateJsonKeyException> {
            DuplicateKeyJsonGuard.requireNoDuplicateObjectKeys("""{"metadata":{"rule_version":1,"rule_version":1}}""")
        }
    }

    @Test
    fun allowsSameKeyInDifferentObjects() {
        DuplicateKeyJsonGuard.requireNoDuplicateObjectKeys("""{"a":{"id":1},"b":{"id":2}}""")
    }
}

