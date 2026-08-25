package org.graphiks.kanvas.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PromotionRebaselineArgumentsTest {
    @Test
    fun `omits a suffix when all rebaseline properties are absent`() {
        assertEquals(emptyList(), promotionRebaselineArguments(null, null, null))
    }

    @Test
    fun `emits the exact rebaseline suffix with both comparisons`() {
        assertEquals(
            listOf("--rebaseline", "--prior-comparison", "previous audit", "--new-comparison", "current audit"),
            promotionRebaselineArguments("true", "previous audit", "current audit"),
        )
    }

    @Test
    fun `rejects comparison properties unless rebaseline is true`() {
        assertFailsWith<IllegalArgumentException> {
            promotionRebaselineArguments("false", "previous audit", "current audit")
        }
    }

    @Test
    fun `rejects missing or blank comparisons for a rebaseline`() {
        listOf(
            Triple(null, "previous audit", "current audit"),
            Triple("true", null, "current audit"),
            Triple("true", "previous audit", null),
            Triple("true", " ", "current audit"),
            Triple("true", "previous audit", " "),
        ).forEach { (rebaseline, prior, next) ->
            assertFailsWith<IllegalArgumentException> {
                promotionRebaselineArguments(rebaseline, prior, next)
            }
        }
    }

    @Test
    fun `rejects rebaseline values other than lowercase true or false`() {
        assertFailsWith<IllegalArgumentException> {
            promotionRebaselineArguments("TRUE", null, null)
        }
    }
}
