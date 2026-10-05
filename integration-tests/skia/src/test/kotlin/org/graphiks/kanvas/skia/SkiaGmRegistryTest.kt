package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkiaGmRegistryTest {
    @Test
    fun `animated backdrop blur keeps the canonical unsupported-port contract`() {
        val matches = SkiaGmRegistry.all().filter { it.name == "animated-backdrop-blur" }

        assertEquals(1, matches.size)
        val gm = matches.single()
        assertEquals(RenderFamily.BLUR, gm.renderFamily)
        assertEquals(RenderCost.BLOCKING, gm.renderCost)
        assertEquals(512, gm.width)
        assertEquals(1024, gm.height)
    }

    @Test
    fun `convex line-only path ports keep logical names and explicit reference aliases`() {
        val gmsByName = SkiaGmRegistry.all().associateBy { it.name }

        val convexLineOnly = requireNotNull(gmsByName["convex_lineonly_paths"])
        assertEquals("convex_lineonly_paths", convexLineOnly.name)
        assertEquals("convex-lineonly-paths", convexLineOnly.referenceName)

        val strokeAndFill = requireNotNull(
            gmsByName["convex_lineonly_paths_stroke_and_fill"],
        )
        assertEquals("convex_lineonly_paths_stroke_and_fill", strokeAndFill.name)
        assertEquals(
            "convex-lineonly-paths-stroke-and-fill",
            strokeAndFill.referenceName,
        )
    }

    @Test
    fun `faithful portable GM ports remain discoverable through the registry`() {
        val names = SkiaGmRegistry.all().map { it.name }.toSet()

        assertTrue(
            setOf(
                "lattice2",
                "not_native32_bitmap_config",
                "stroketext_native",
                "typefacerendering",
                "user_typeface",
            ).all(names::contains),
        ) { "Missing faithful GM ports: ${names.sorted()}" }
    }

    @Test
    fun `production GM registry contains only loadable unique entries`() {
        val entries = SkiaGmRegistry.entries()

        assertTrue(entries.isNotEmpty())
        assertEquals(emptyList<String>(), entries.filter { it.gm == null }.map { it.provider })
        val names = entries.map { requireNotNull(it.gm).name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `registered GMs have unique logical names`() {
        val gms = SkiaGmRegistry.all()
        val names = gms.map { it.name }

        assertEquals(names.size, names.toSet().size) {
            "Duplicate registered GM names: ${names.groupingBy { it }.eachCount().filterValues { it > 1 }}"
        }
    }
}
