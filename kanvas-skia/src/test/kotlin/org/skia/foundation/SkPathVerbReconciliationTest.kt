package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * R-suivi.25 — verifies that the unified upstream verb enum
 * [SkPath.Verb] mirrors `SkPath::Verb` (7 entries: the 6 storage values
 * plus the `kDone` iterator sentinel) and that the file-scope deprecated
 * typealiases ([SkPathVerb], [SkPathStorageVerb], [SkPathIterVerb]) all
 * resolve to it.
 *
 * The R-suivi.25 follow-up unified what was previously a split between
 * `SkPath.StorageVerb` (6) and `SkPath.Verb` (7) — upstream Skia uses a
 * single `SkPath::Verb` (7 values) where storage code uses the 6 "real"
 * values and only iterators ever produce `kDone`.
 */
class SkPathVerbReconciliationTest {

    @Test
    fun `unified Verb has exactly seven entries matching upstream SkPath colon colon Verb`() {
        val entries = SkPath.Verb.entries
        assertEquals(7, entries.size, "Verb must mirror upstream SkPath::Verb (7 entries)")
        // Order matters — upstream's SkPath::Verb enum-class values are
        // in this exact order so a future numeric cast (e.g. for verb
        // streams) matches the C++ memory representation.
        assertEquals("kMove", entries[0].name)
        assertEquals("kLine", entries[1].name)
        assertEquals("kQuad", entries[2].name)
        assertEquals("kConic", entries[3].name)
        assertEquals("kCubic", entries[4].name)
        assertEquals("kClose", entries[5].name)
        // kDone is the upstream sentinel returned once the verb stream
        // is exhausted ; never stored in the verbs[] array.
        assertEquals("kDone", entries[6].name)
        assertTrue(entries.any { it.name == "kDone" })
        assertTrue(entries.any { it.name == "kMove" })
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
        // kDone never appears in storage — its pointCount is 0 by
        // convention so the storage-walk pattern stays total.
        assertEquals(0, SkPath.Verb.kDone.pointCount)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `SkPathVerb typealias resolves to the unified Verb enum`() {
        val verbClass: KClass<SkPath.Verb> = SkPath.Verb::class
        val aliasClass: KClass<SkPathVerb> = SkPathVerb::class
        // Both names must point at the same JVM class — typealiases
        // are erased at compile time, so a class-literal comparison is
        // the strongest signal we can make in Kotlin.
        assertSame(verbClass.java, aliasClass.java)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `SkPathStorageVerb typealias resolves to the unified Verb enum`() {
        val verbClass: KClass<SkPath.Verb> = SkPath.Verb::class
        val aliasClass: KClass<SkPathStorageVerb> = SkPathStorageVerb::class
        assertSame(verbClass.java, aliasClass.java)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `SkPathIterVerb typealias resolves to the unified Verb enum`() {
        val verbClass: KClass<SkPath.Verb> = SkPath.Verb::class
        val aliasClass: KClass<SkPathIterVerb> = SkPathIterVerb::class
        assertSame(verbClass.java, aliasClass.java)
    }
}
