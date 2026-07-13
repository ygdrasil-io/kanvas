package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader

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
    fun `all service entries load Skia GM classes`() {
        val resourceName = "META-INF/services/${SkiaGm::class.qualifiedName}"
        val classLoader = SkiaGm::class.java.classLoader
        val stream = requireNotNull(classLoader.getResourceAsStream(resourceName)) {
            "No $resourceName found"
        }
        val failures = mutableListOf<String>()

        BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { className ->
                    try {
                        val clazz = classLoader.loadClass(className)
                        if (!SkiaGm::class.java.isAssignableFrom(clazz)) {
                            failures.add("$className does not implement ${SkiaGm::class.qualifiedName}")
                        }
                    } catch (e: LinkageError) {
                        failures.add("$className failed to link: ${e.message}")
                    } catch (e: ClassNotFoundException) {
                        failures.add("$className was not found")
                    }
                }
        }

        assertTrue(failures.isEmpty()) {
            failures.joinToString(separator = "\n")
        }
    }
}
