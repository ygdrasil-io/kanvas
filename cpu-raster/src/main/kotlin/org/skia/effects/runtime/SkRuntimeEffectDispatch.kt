package org.skia.effects.runtime

/**
 * Process-wide registry of hand-ported [SkRuntimeImpl] entries
 * indexed by canonical SkSL hash. Used by
 * [SkRuntimeEffect.MakeForShader] / `MakeForColorFilter` /
 * `MakeForBlender` to resolve a caller-supplied SkSL string to the
 * Kotlin impl that reproduces it.
 *
 * **Why a hash-keyed registry** : a ported GM calls
 * `SkRuntimeEffect.MakeForShader(skslLiteral)` verbatim with the
 * same SkSL source as upstream. We don't compile / interpret
 * the SkSL — instead we look up the hand-ported [SkRuntimeImpl]
 * registered against that exact source. See
 * [§ Architecture](../../../../../../../../MIGRATION_PLAN_D2_RUNTIME_EFFECT.md#architecture--runtime-effects--nouveaux-types-de-shader)
 * for the design rationale.
 *
 * **Source normalization** runs before hashing so trivially-
 * different SkSL sources (extra whitespace, comments, indentation)
 * collapse to the same hash. The normalized canonical form is :
 *
 *  1. **Strip line comments** : every `// ...` to end-of-line is
 *     dropped (block comment delimiters never appear inside a
 *     string literal in our usage — SkSL has no string literals at
 *     the top level).
 *  2. **Strip block comments** : the slash-star ... star-slash pair
 *     is dropped (most-greedy reader, including newlines inside).
 *  3. **Collapse whitespace runs** to one space ; preserve a single
 *     space at every transition between tokens.
 *  4. **Trim leading and trailing whitespace**.
 *
 * After normalization, the FNV-1a-64 hash is computed over the
 * UTF-8 byte sequence (Kotlin's `String.toByteArray()` — bit-iso
 * with upstream's `SkString::data()`).
 *
 * **Thread-safety** : registrations are typically done via
 * explicit builtin registration calls before lookup. Lookups can
 * come from any thread (the dispatch table is a [HashMap] but
 * writes after class-load only happen during test setup ; if a
 * future use case needs concurrent registration, swap to
 * [java.util.concurrent.ConcurrentHashMap] — no other code change
 * required).
 *
 * **Misses** : `lookup(unregisteredSkSL)` returns `null`. The
 * caller (typically [SkRuntimeEffect.Companion]) wraps that into
 * `Result(effect = null, errorText = "SkSL not registered: <hash>.
 * Add an entry to SkRuntimeEffectDispatch.")` so the failure path
 * is visible to the GM driver and the DM pipeline (which logs the
 * missing hash and skips the GM gracefully — D2.6 wires that).
 */
public object SkRuntimeEffectDispatch {

    /** Canonical-source-hash → impl factory. */
    private val table: MutableMap<Long, () -> SkRuntimeImpl> = HashMap()
    private val builtinMetadataByHash: MutableMap<Long, SkRuntimeEffectDispatchMetadata> = HashMap()

    /**
     * Register a hand-ported impl under the canonical form of the supplied
     * SkSL source. Duplicate production registration is rejected; tests that
     * intentionally replace an entry must use [registerForTestOverride].
     *
     * @param canonicalSource the SkSL string. May contain comments
     *   and arbitrary whitespace — normalisation runs before
     *   hashing.
     * @param factory zero-arg constructor of the impl ; called
     *   once per `MakeForShader` resolution that hits this entry.
     */
    public fun register(canonicalSource: String, factory: () -> SkRuntimeImpl) {
        val hash = hashCanonical(canonicalSource)
        check(hash !in table) {
            "Duplicate runtime effect dispatch registration: canonicalHash=$hash"
        }
        table[hash] = factory
    }

    internal fun registerBuiltinIfAbsent(canonicalSource: String, factory: () -> SkRuntimeImpl) {
        val hash = hashCanonical(canonicalSource)
        table.putIfAbsent(hash, factory)
    }

    internal fun registerBuiltinIfAbsent(
        canonicalSource: String,
        metadata: SkRuntimeEffectDispatchMetadata,
        factory: () -> SkRuntimeImpl,
    ) {
        val hash = hashCanonical(canonicalSource)
        table.putIfAbsent(hash, factory)
        val existing = builtinMetadataByHash.putIfAbsent(hash, metadata)
        check(existing == null || existing == metadata) {
            "Duplicate runtime effect dispatch metadata: canonicalHash=$hash stableId=${metadata.stableId}"
        }
    }

    internal fun registerForTestOverride(canonicalSource: String, factory: () -> SkRuntimeImpl) {
        val hash = hashCanonical(canonicalSource)
        table[hash] = factory
        builtinMetadataByHash.remove(hash)
    }

    /**
     * Resolve [sksl] to a registered impl factory. Returns `null`
     * if no impl is registered for the canonical form of [sksl].
     *
     * The returned factory has not yet been invoked — the caller
     * decides whether to instantiate (typically once per
     * `SkRuntimeEffect` resolution).
     */
    public fun lookup(sksl: String): (() -> SkRuntimeImpl)? {
        val hash = hashCanonical(sksl)
        return table[hash]
    }

    /**
     * Convenience for the test surface : return the canonical hash
     * of [sksl] without resolving an impl. Lets unit tests pin
     * cross-version hash stability against known reference values.
     */
    public fun canonicalHash(sksl: String): Long = hashCanonical(sksl)

    /**
     * Convenience for the test surface : return the canonical
     * (normalised) form of [sksl]. Lets unit tests pin the
     * normalisation pipeline against known reference inputs.
     */
    public fun canonicalSource(sksl: String): String = normalise(sksl)

    /** Number of registered entries. Test-surface only. */
    internal val size: Int get() = table.size

    internal fun builtinMetadataEntries(): List<Pair<Long, SkRuntimeEffectDispatchMetadata>> =
        builtinMetadataByHash.entries
            .map { it.key to it.value }
            .sortedWith(compareBy<Pair<Long, SkRuntimeEffectDispatchMetadata>> { it.second.stableId }.thenBy { it.first })

    /** Drop every registration. Test-surface only — call between
     *  isolated test cases that want a fresh dispatch table. */
    internal fun clearForTest() {
        table.clear()
        builtinMetadataByHash.clear()
        SkRuntimeEffectDescriptorRegistry.clearForTest()
    }

    // ─── Normalisation pipeline ────────────────────────────────────────

    /**
     * Apply the four normalisation steps documented at the top of
     * this file, then hash the resulting bytes via FNV-1a-64.
     */
    private fun hashCanonical(source: String): Long {
        return fnv1a64(normalise(source).encodeToByteArray())
    }

    /**
     * Strip comments + collapse whitespace runs + remove whitespace
     * adjacent to punctuation + trim. The resulting string is the
     * canonical form used as the dispatch key (after FNV-1a-64
     * hashing).
     *
     * SkSL is whitespace-insensitive between tokens (just like C-
     * family languages), so `main(p)` and `main ( p )` are the same
     * program — the canonical form must collapse them. We achieve
     * that in three passes :
     *
     *  1. Strip comments.
     *  2. Collapse runs of whitespace to a single space.
     *  3. Strip whitespace adjacent to punctuation
     *     (`( ) [ ] { } , ;` and the math / assign / compare
     *     operators).
     *  4. Trim leading / trailing whitespace.
     */
    private fun normalise(source: String): String {
        val stripped = stripComments(source)
        val collapsed = collapseWhitespace(stripped)
        val tokenised = stripWhitespaceAroundPunctuation(collapsed)
        return tokenised.trim()
    }

    /**
     * Single-pass scanner that drops line comments (slash-slash to
     * end-of-line) and block comments (slash-star to star-slash).
     * State machine :
     *
     *  - `kCode` : default ; `/` followed by `/` enters `kLine` ;
     *    `/` followed by `*` enters `kBlock` ; everything else is
     *    appended verbatim.
     *  - `kLine` : drop until newline (newline is preserved as a
     *    space so the surrounding tokens don't merge).
     *  - `kBlock` : drop until the closing star-slash (the closing
     *    pair itself is also dropped ; a single space is emitted in
     *    its place).
     *
     * Edge cases :
     *  - A trailing `/` at end-of-input emits the slash verbatim.
     *  - An unterminated block comment swallows everything to
     *    end-of-input (matches GCC / clang's behaviour ; mirrors
     *    upstream SkSL's lexer).
     */
    private fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        val n = source.length
        while (i < n) {
            val c = source[i]
            if (c == '/' && i + 1 < n) {
                val next = source[i + 1]
                if (next == '/') {
                    // Line comment — skip to newline (or EOF).
                    i += 2
                    while (i < n && source[i] != '\n') i++
                    if (i < n) {
                        out.append(' ')
                        i++
                    }
                    continue
                } else if (next == '*') {
                    // Block comment — skip to "*/".
                    i += 2
                    while (i + 1 < n && !(source[i] == '*' && source[i + 1] == '/')) i++
                    if (i + 1 < n) {
                        out.append(' ')
                        i += 2
                    } else {
                        // Unterminated — swallow rest of input.
                        i = n
                    }
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /**
     * Drop the single space (output of [collapseWhitespace]) when it
     * sits next to a punctuation character. Punctuation characters
     * are : `( ) [ ] { } , ; + - * / = < > ! & | ^ % ~ ?` plus the
     * `:` and `.` (SkSL field-access dot, ternary colon).
     *
     * The pass is single-character ; runs of whitespace have already
     * been collapsed to a single space by [collapseWhitespace], so
     * we only ever see at most one space at any boundary.
     */
    private fun stripWhitespaceAroundPunctuation(source: String): String {
        val out = StringBuilder(source.length)
        for (i in source.indices) {
            val c = source[i]
            if (c == ' ') {
                val prev = if (i > 0) source[i - 1] else ' '
                val next = if (i + 1 < source.length) source[i + 1] else ' '
                if (isPunctuation(prev) || isPunctuation(next)) continue
            }
            out.append(c)
        }
        return out.toString()
    }

    private fun isPunctuation(c: Char): Boolean = when (c) {
        '(', ')', '[', ']', '{', '}', ',', ';',
        '+', '-', '*', '/', '=', '<', '>', '!',
        '&', '|', '^', '%', '~', '?', ':', '.',
        -> true
        else -> false
    }

    /** Collapse every run of whitespace (`\s+`) to a single space. */
    private fun collapseWhitespace(source: String): String {
        val out = StringBuilder(source.length)
        var prevWs = false
        for (c in source) {
            val isWs = c.isWhitespace()
            if (isWs) {
                if (!prevWs) out.append(' ')
                prevWs = true
            } else {
                out.append(c)
                prevWs = false
            }
        }
        return out.toString()
    }

    // ─── FNV-1a-64 ─────────────────────────────────────────────────────

    /**
     * FNV-1a 64-bit hash. Reference :
     * [Fowler-Noll-Vo](http://www.isthe.com/chongo/tech/comp/fnv/).
     *
     * Vector test (also pinned in
     * [`SkRuntimeEffectDispatchTest`](../../../../../../../test/kotlin/org/skia/effects/runtime/SkRuntimeEffectDispatchTest.kt)) :
     *  - `fnv1a64("")` == `0xCBF29CE484222325L`
     *  - `fnv1a64("a")` == `0xAF63DC4C8601EC8CL`
     *  - `fnv1a64("foobar")` == `0x85944171F73967E8L`
     */
    private fun fnv1a64(bytes: ByteArray): Long {
        val FNV_OFFSET = -0x340D631B7BDDDCDBL // == 0xCBF29CE484222325 (signed 64-bit)
        val FNV_PRIME = 0x100000001B3L
        var hash = FNV_OFFSET
        for (b in bytes) {
            hash = hash xor (b.toLong() and 0xFFL)
            hash *= FNV_PRIME
        }
        return hash
    }
}

internal data class SkRuntimeEffectDispatchMetadata(
    val stableId: String,
    val kind: SkRuntimeEffect.Kind,
    val uniforms: List<SkRuntimeEffect.Uniform>,
    val children: List<SkRuntimeEffect.Child>,
    val flags: Int,
    val cpuImplementationId: String,
)
