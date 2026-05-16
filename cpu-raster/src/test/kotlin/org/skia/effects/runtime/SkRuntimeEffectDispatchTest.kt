package org.skia.effects.runtime

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.math.SkColor4f
import org.skia.math.SkPoint
import java.nio.ByteBuffer

/**
 * D2.1 verification suite for [SkRuntimeEffectDispatch] :
 *
 *  - FNV-1a-64 hash matches a vector test (cross-version stability).
 *  - Source normalisation strips line + block comments and collapses
 *    whitespace ; whitespace-variant SkSL hashes identically.
 *  - register / lookup round-trip works ; misses return null.
 *  - clearForTest resets the dispatch table.
 *  - Lambda factory is invoked at lookup time, not at register time
 *    (lazy instantiation contract).
 *  - Re-registering the same source replaces the prior factory
 *    (deliberate test-convenience semantic).
 */
class SkRuntimeEffectDispatchTest {

    @AfterEach
    fun cleanup() {
        // Each test starts with a fresh dispatch table so registrations
        // don't leak across tests in this class.
        SkRuntimeEffectDispatch.clearForTest()
    }

    // ─── Source normalisation ─────────────────────────────────────────

    @Test
    fun `normalisation strips line comments`() {
        val canon = SkRuntimeEffectDispatch.canonicalSource(
            """
            // a leading comment
            half4 main(vec2 p) { return vec4(0); } // trailing
            """.trimIndent()
        )
        // After comment-strip + whitespace-collapse + punctuation-
        // adjacent-strip + trim. Spaces stick only between
        // identifier tokens (e.g. `half4 main`, `vec2 p`, `return vec4`).
        assertEquals("half4 main(vec2 p){return vec4(0);}", canon)
    }

    @Test
    fun `normalisation strips block comments`() {
        val canon = SkRuntimeEffectDispatch.canonicalSource(
            "uniform half foo /* x */; vec4 main(vec2 p) { return /* y */ vec4(0); }"
        )
        // Block comments collapse to a single space which is then
        // stripped by the punctuation-adjacent-strip pass.
        assertEquals(
            "uniform half foo;vec4 main(vec2 p){return vec4(0);}",
            canon,
        )
    }

    @Test
    fun `normalisation collapses whitespace runs`() {
        val canon = SkRuntimeEffectDispatch.canonicalSource(
            "vec4    main(vec2  p)\n\n\n{\n  return\tvec4(0);\n}"
        )
        assertEquals("vec4 main(vec2 p){return vec4(0);}", canon)
    }

    @Test
    fun `normalisation trims leading and trailing whitespace`() {
        val canon = SkRuntimeEffectDispatch.canonicalSource(
            "    vec4 main(vec2 p) { return vec4(0); }    "
        )
        assertEquals("vec4 main(vec2 p){return vec4(0);}", canon)
    }

    @Test
    fun `whitespace variants hash identically`() {
        val a = SkRuntimeEffectDispatch.canonicalHash(
            "vec4 main(vec2 p) { return vec4(0); }"
        )
        val b = SkRuntimeEffectDispatch.canonicalHash(
            "  vec4   main( vec2 p ) { return vec4(0); }  "
        )
        val c = SkRuntimeEffectDispatch.canonicalHash(
            "// header\nvec4 main(vec2 p) {\n  return vec4(0);\n}"
        )
        assertEquals(a, b, "extra spaces don't change the hash")
        assertEquals(a, c, "comments + line breaks don't change the hash")
    }

    @Test
    fun `case-different SkSL hashes differently`() {
        // SkSL is case-sensitive — `Vec4` and `vec4` are distinct
        // identifiers. The normalisation must NOT lowercase tokens.
        val a = SkRuntimeEffectDispatch.canonicalHash(
            "vec4 main(vec2 p) { return vec4(0); }"
        )
        val b = SkRuntimeEffectDispatch.canonicalHash(
            "VEC4 main(vec2 p) { return vec4(0); }"
        )
        assertNotEquals(a, b, "case differences must not collapse")
    }

    // ─── FNV-1a-64 vector tests ───────────────────────────────────────

    @Test
    fun `FNV-1a-64 matches reference vectors`() {
        // Reference values from http://www.isthe.com/chongo/tech/comp/fnv/
        // — well-known test vectors for FNV-1a 64-bit. Computed via
        // the canonical-source pipeline, so an empty input → empty
        // bytes → FNV offset basis.
        // We use ULong literals + .toLong() so the signed-overflow
        // arithmetic is foolproof and the constants read like the
        // actual hex bit pattern.
        assertEquals(
            0xCBF29CE484222325UL.toLong(),
            SkRuntimeEffectDispatch.canonicalHash(""),
            "empty SkSL → FNV-1a-64 offset basis",
        )
        assertEquals(
            0xAF63DC4C8601EC8CUL.toLong(),
            SkRuntimeEffectDispatch.canonicalHash("a"),
            "single-char 'a' → 0xAF63DC4C8601EC8C",
        )
        assertEquals(
            0x85944171F73967E8UL.toLong(),
            SkRuntimeEffectDispatch.canonicalHash("foobar"),
            "'foobar' → 0x85944171F73967E8",
        )
    }

    // ─── register / lookup round-trip ────────────────────────────────

    private val identityShaderSksl = """
        half4 main(vec2 p) { return vec4(p.x, p.y, 0.0, 1.0); }
    """.trimIndent()

    private fun stubImpl() = object : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = 0
        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f = SkColor4f.kBlack
    }

    @Test
    fun `register then lookup returns the factory`() {
        SkRuntimeEffectDispatch.register(identityShaderSksl) { stubImpl() }
        val factory = SkRuntimeEffectDispatch.lookup(identityShaderSksl)
        assertNotNull(factory, "lookup should hit after register")
    }

    @Test
    fun `lookup of unregistered source returns null`() {
        // No register call ; lookup must miss.
        val factory = SkRuntimeEffectDispatch.lookup(
            "half4 main(vec2 p) { return vec4(0.5); }"
        )
        assertNull(factory)
    }

    @Test
    fun `lookup is whitespace insensitive`() {
        SkRuntimeEffectDispatch.register(identityShaderSksl) { stubImpl() }
        // Look up with extra whitespace + a comment.
        val factory = SkRuntimeEffectDispatch.lookup(
            """
            // some banner
            half4 main(vec2  p) {
              return  vec4(p.x, p.y, 0.0, 1.0);
            }
            """.trimIndent()
        )
        assertNotNull(factory, "whitespace + comment-variant lookup should hit")
    }

    @Test
    fun `factory is invoked lazily on each lookup hit`() {
        var invocations = 0
        SkRuntimeEffectDispatch.register(identityShaderSksl) {
            invocations++
            stubImpl()
        }
        // register alone should not have invoked the factory.
        assertEquals(0, invocations)
        // First lookup returns the factory but does NOT invoke it
        // (the caller decides when to instantiate).
        val factory = SkRuntimeEffectDispatch.lookup(identityShaderSksl)
        assertNotNull(factory)
        assertEquals(0, invocations)
        // Calling the factory increments the counter once per call.
        factory!!()
        factory()
        assertEquals(2, invocations, "each factory() call instantiates a fresh impl")
    }

    @Test
    fun `re-register replaces prior factory`() {
        SkRuntimeEffectDispatch.register(identityShaderSksl) { stubImpl() }
        var newImplUsed = false
        SkRuntimeEffectDispatch.register(identityShaderSksl) {
            newImplUsed = true
            stubImpl()
        }
        SkRuntimeEffectDispatch.lookup(identityShaderSksl)!!()
        assertEquals(true, newImplUsed, "second register should replace the first")
    }

    @Test
    fun `clearForTest empties the dispatch table`() {
        SkRuntimeEffectDispatch.register(identityShaderSksl) { stubImpl() }
        assertEquals(1, SkRuntimeEffectDispatch.size)
        SkRuntimeEffectDispatch.clearForTest()
        assertEquals(0, SkRuntimeEffectDispatch.size)
        assertNull(SkRuntimeEffectDispatch.lookup(identityShaderSksl))
    }
}
