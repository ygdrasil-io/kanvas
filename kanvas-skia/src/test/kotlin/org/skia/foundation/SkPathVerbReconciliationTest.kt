package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * R-suivi.25 — verifies that the two upstream verb enums (storage 6-value
 * [SkPath.Verb] and iter 7-value [SkPath.IterVerb]) are exposed with the
 * expected entry counts and that the file-scope typealiases ([SkPathVerb],
 * [SkPathStorageVerb], [SkPathIterVerb]) resolve to the right targets.
 */
class SkPathVerbReconciliationTest {

    @Test
    fun `storage Verb has exactly six entries matching upstream SkPathVerb`() {
        val entries = SkPath.Verb.entries
        assertEquals(6, entries.size, "storage Verb must mirror upstream SkPathVerb (6 entries)")
        // Order matters — upstream's SkPathVerb enum-class values are in
        // this exact order so a future numeric cast (e.g. for `verbs()`)
        // matches the C++ memory representation.
        assertEquals("kMove", entries[0].name)
        assertEquals("kLine", entries[1].name)
        assertEquals("kQuad", entries[2].name)
        assertEquals("kConic", entries[3].name)
        assertEquals("kCubic", entries[4].name)
        assertEquals("kClose", entries[5].name)
    }

    @Test
    fun `iter Verb has exactly seven entries matching upstream SkPath colon colon Verb`() {
        val entries = SkPath.IterVerb.entries
        assertEquals(7, entries.size, "iter Verb must mirror upstream SkPath::Verb (7 entries)")
        // kDoneVerb is the upstream sentinel returned once the verb
        // stream is exhausted ; absent from the storage enum.
        assertTrue(entries.any { it.name == "kDoneVerb" })
        assertTrue(entries.any { it.name == "kMoveVerb" })
    }

    @Test
    fun `point counts on storage Verb match upstream pts_advance_after_verb`() {
        // Upstream `SkPath::pts_advance_after_verb` returns the new
        // points added per verb. Our enum exposes [pointCount] as the
        // equivalent — the storage-side count, not the iter
        // "pts[4]" stride.
        assertEquals(1, SkPath.Verb.kMove.pointCount)
        assertEquals(1, SkPath.Verb.kLine.pointCount)
        assertEquals(2, SkPath.Verb.kQuad.pointCount)
        assertEquals(2, SkPath.Verb.kConic.pointCount)
        assertEquals(3, SkPath.Verb.kCubic.pointCount)
        assertEquals(0, SkPath.Verb.kClose.pointCount)
    }

    @Test
    fun `SkPathVerb typealias resolves to the storage enum`() {
        val storageClass: KClass<SkPath.Verb> = SkPath.Verb::class
        val aliasClass: KClass<SkPathVerb> = SkPathVerb::class
        // Both names must point at the same JVM class — typealiases
        // are erased at compile time, so a class-literal comparison is
        // the strongest signal we can make in Kotlin.
        assertSame(storageClass.java, aliasClass.java)
    }

    @Test
    fun `SkPathStorageVerb typealias resolves to the storage enum`() {
        val storageClass: KClass<SkPath.Verb> = SkPath.Verb::class
        val aliasClass: KClass<SkPathStorageVerb> = SkPathStorageVerb::class
        assertSame(storageClass.java, aliasClass.java)
    }

    @Test
    fun `SkPathIterVerb typealias resolves to the iter enum`() {
        val iterClass: KClass<SkPath.IterVerb> = SkPath.IterVerb::class
        val aliasClass: KClass<SkPathIterVerb> = SkPathIterVerb::class
        assertSame(iterClass.java, aliasClass.java)
        // Sanity : the two enums are *distinct* classes.
        assertNotEquals(SkPath.Verb::class.java, SkPath.IterVerb::class.java)
    }
}
