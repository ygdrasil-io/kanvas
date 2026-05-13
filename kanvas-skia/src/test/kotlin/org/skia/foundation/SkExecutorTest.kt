package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R1 tests for [SkExecutor] — verify the [SkExecutor.Synchronous]
 * default runs work inline and [SetDefault] swaps the global.
 */
class SkExecutorTest {

    @Test
    fun `default executor runs work synchronously`() {
        var ran = false
        SkExecutor.GetDefault().add { ran = true }
        assertTrue(ran)
    }

    @Test
    fun `default returns the Synchronous singleton initially`() {
        // Reset in case a previous test changed the default.
        SkExecutor.SetDefault(null)
        assertSame(SkExecutor.Synchronous, SkExecutor.GetDefault())
    }

    @Test
    fun `SetDefault swaps the global and null resets to Synchronous`() {
        val custom = object : SkExecutor() {
            var count = 0
            override fun add(work: () -> Unit) {
                count++
                work()
            }
        }
        SkExecutor.SetDefault(custom)
        assertSame(custom, SkExecutor.GetDefault())
        SkExecutor.GetDefault().add { /* no-op */ }
        assertEquals(1, custom.count)

        SkExecutor.SetDefault(null)
        assertSame(SkExecutor.Synchronous, SkExecutor.GetDefault())
    }

    @Test
    fun `borrow and discardAllPendingWork are safe defaults`() {
        val sync = SkExecutor.Synchronous
        sync.borrow()
        assertEquals(0, sync.discardAllPendingWork())
    }
}
