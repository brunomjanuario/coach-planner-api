package com.coachplanner.api.common

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** tasks.md T7 / AD-105: newId() must be a real, correctly-tagged UUIDv7. */
class IdsTest {

    @Test
    fun `generated ids report version 7 and the IETF variant`() {
        val id = newId()

        assertEquals(7, id.version(), "expected UUID version 7, got version ${id.version()}: $id")
        assertEquals(2, id.variant(), "expected the IETF variant (2), got ${id.variant()}: $id")
    }

    @Test
    fun `1000 generated ids are all distinct`() {
        val ids = (1..1000).map { newId() }

        assertEquals(1000, ids.toSet().size, "expected no collisions across 1000 generated ids")
    }

    /** Extracts the 48-bit unix_ts_ms prefix from the UUID's most-significant bits (top 48 of 64). */
    private fun timestampPrefix(id: java.util.UUID): Long = id.mostSignificantBits ushr 16

    @Test
    fun `the 48-bit timestamp prefix is time-ordered — never decreasing across a sequence`() {
        // Only the millisecond-timestamp prefix is guaranteed ordered — two ids minted within
        // the same millisecond differ only in their random suffix, which is not itself ordered.
        val ids = (1..200).map { newId() }

        for (i in 1 until ids.size) {
            assertTrue(
                timestampPrefix(ids[i - 1]) <= timestampPrefix(ids[i]),
                "expected a non-decreasing timestamp prefix, but ${ids[i - 1]} came before ${ids[i]}",
            )
        }
    }
}
