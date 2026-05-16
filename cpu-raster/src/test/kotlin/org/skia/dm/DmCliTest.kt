package org.skia.dm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tests.GM

/**
 * D4.4 verification suite for [DmCli] / [DmMain].
 *
 * Covers :
 *  - Flag parsing in both `--flag value` and `--flag=value` forms.
 *  - `--match` semantics : ports upstream's `~` / `^` / `$` syntax,
 *    including the "all-exclusion → run unmatched" rule.
 *  - `--config` resolution : known tags → live sinks ; unknown tags
 *    → `null` slot (caller decides policy).
 *  - `--skip` quadruples : per-(config, src, srcOptions, name) gate
 *    with `_` wildcard and `~` negation.
 *  - `--key` / `--properties` pair-up.
 *  - End-to-end : `DmMain.runFromArgs` parses, runs, and applies
 *    skip filtering — output JSON honours every flag.
 */
class DmCliTest {

    // ─── Parsing ──────────────────────────────────────────────────────

    @Test
    fun `parse handles bare flags with multiple values`() {
        val cli = DmCli.parse(arrayOf("--config", "8888", "f16", "--match", "Foo"))
        assertEquals(listOf("8888", "f16"), cli.configs)
        assertEquals(listOf("Foo"), cli.match)
    }

    @Test
    fun `parse handles --flag=value form`() {
        val cli = DmCli.parse(arrayOf("--config=8888", "--match=Bar"))
        assertEquals(listOf("8888"), cli.configs)
        assertEquals(listOf("Bar"), cli.match)
    }

    @Test
    fun `parse mixes both forms`() {
        val cli = DmCli.parse(arrayOf("--config=8888", "f16", "--match", "x", "y"))
        assertEquals(listOf("8888", "f16"), cli.configs)
        assertEquals(listOf("x", "y"), cli.match)
    }

    @Test
    fun `parse rejects positional value before any flag`() {
        assertThrows(IllegalArgumentException::class.java) {
            DmCli.parse(arrayOf("orphan", "--config", "8888"))
        }
    }

    @Test
    fun `parse rejects unknown flag`() {
        assertThrows(IllegalArgumentException::class.java) {
            DmCli.parse(arrayOf("--zorglub", "wat"))
        }
    }

    @Test
    fun `parse rejects --skip with non-multiple-of-4 values`() {
        assertThrows(IllegalArgumentException::class.java) {
            DmCli.parse(arrayOf("--skip", "8888", "gm", "_"))
        }
    }

    // ─── --match semantics ────────────────────────────────────────────

    @Test
    fun `empty match list runs everything`() {
        val cli = DmCli.parse(arrayOf("--config", "8888"))
        assertTrue(cli.shouldRun("AnyGm"))
        assertTrue(cli.shouldRun(""))
    }

    @Test
    fun `bare substring match runs only on hits`() {
        val cli = DmCli.parse(arrayOf("--config", "8888", "--match", "Rect"))
        assertTrue(cli.shouldRun("BigRectGM"))
        assertTrue(cli.shouldRun("ThinRectsGM"))
        assertFalse(cli.shouldRun("CircleSizesGM"))
    }

    @Test
    fun `caret anchors the start of the name`() {
        val cli = DmCli.parse(arrayOf("--config", "8888", "--match", "^Big"))
        assertTrue(cli.shouldRun("BigRectGM"))
        assertFalse(cli.shouldRun("ConcaveBig"), "BigRect substring at offset > 0 must not match a ^-anchored pattern")
    }

    @Test
    fun `dollar anchors the end of the name`() {
        val cli = DmCli.parse(arrayOf("--config", "8888", "--match", "GM\$"))
        assertTrue(cli.shouldRun("BigRectGM"))
        assertFalse(cli.shouldRun("BigRectGMTrailing"))
    }

    @Test
    fun `caret + dollar require an exact match`() {
        val cli = DmCli.parse(arrayOf("--config", "8888", "--match", "^BigRectGM\$"))
        assertTrue(cli.shouldRun("BigRectGM"))
        assertFalse(cli.shouldRun("BigRectGM2"))
        assertFalse(cli.shouldRun("BigRectGMx"))
        assertFalse(cli.shouldRun("xBigRectGM"))
    }

    @Test
    fun `tilde excludes a matching name and runs everything else`() {
        val cli = DmCli.parse(arrayOf("--config", "8888", "--match", "~Big"))
        assertFalse(cli.shouldRun("BigRectGM"))
        assertTrue(cli.shouldRun("ThinRectsGM"))
    }

    @Test
    fun `mix of include and exclude patterns honours first match`() {
        val cli = DmCli.parse(arrayOf("--config", "8888", "--match", "Rect", "~ThinRects"))
        // ThinRectsGM hits 'Rect' first → included. The exclude later in
        // the list never gets a chance to fire because we stop on the
        // first hit (matches upstream's behaviour).
        assertTrue(cli.shouldRun("ThinRectsGM"))
        // Reorder : ~ThinRects first → excluded.
        val cliReversed = DmCli.parse(arrayOf("--config", "8888", "--match", "~ThinRects", "Rect"))
        assertFalse(cliReversed.shouldRun("ThinRectsGM"))
        assertTrue(cliReversed.shouldRun("BigRectGM"))
    }

    // ─── --skip semantics ─────────────────────────────────────────────

    @Test
    fun `skip quadruple drops only the matching (config, gm) pair`() {
        val cli = DmCli.parse(arrayOf(
            "--config", "8888",
            "--skip", "8888", "gm", "_", "BigRectGM",
        ))
        assertTrue(cli.shouldSkipPair("BigRectGM", "8888"))
        // Same GM, different sink : not skipped.
        assertFalse(cli.shouldSkipPair("BigRectGM", "f16"))
        // Different GM, same sink : not skipped.
        assertFalse(cli.shouldSkipPair("ThinRectsGM", "8888"))
    }

    @Test
    fun `skip with underscore wildcard skips across all configs`() {
        val cli = DmCli.parse(arrayOf(
            "--config", "8888",
            "--skip", "_", "gm", "_", "BigRectGM",
        ))
        assertTrue(cli.shouldSkipPair("BigRectGM", "8888"))
        assertTrue(cli.shouldSkipPair("BigRectGM", "f16"))
        assertTrue(cli.shouldSkipPair("BigRectGM", "pic-8888"))
    }

    // ─── --config resolution ──────────────────────────────────────────

    @Test
    fun `resolveSinks maps known tags to live sinks`() {
        val cli = DmCli.parse(arrayOf("--config", "8888", "f16", "pic-8888", "pic-f16"))
        val sinks = cli.resolveSinks()
        assertEquals(4, sinks.size)
        for (s in sinks) assertNotNull(s)
        assertEquals("8888", sinks[0]!!.tag)
        assertEquals("f16", sinks[1]!!.tag)
        assertEquals("pic-8888", sinks[2]!!.tag)
        assertEquals("pic-f16", sinks[3]!!.tag)
    }

    @Test
    fun `resolveSinks returns null for unknown tags`() {
        val cli = DmCli.parse(arrayOf("--config", "wat"))
        assertNull(cli.resolveSinks().single())
    }

    // ─── End-to-end ───────────────────────────────────────────────────

    @Test
    fun `DmMain runFromArgs runs the resolved matrix and applies skip filter`() {
        val gms = listOf(RedSquareGM(), TinyGM())
        // Run both GMs in 8888 and f16, skip TinyGM in f16 only.
        val report = DmMain.runFromArgs(
            args = arrayOf(
                "--config", "8888", "f16",
                "--skip", "f16", "gm", "_", "TinyGM",
            ),
            allGms = gms,
        )
        // 2 GMs × 2 sinks = 4 (GM, sink) pairs minus 1 skip = 3 records.
        assertEquals(3, report.all.size, "expected 3 kept records, got ${report.all.map { it.gmName + ":" + it.sinkTag }}")
        // The skipped pair must be gone.
        assertFalse(report.all.any { it.gmName == "TinyGM" && it.sinkTag == "f16" })
        // The sibling pair (TinyGM × 8888) must remain.
        assertTrue(report.all.any { it.gmName == "TinyGM" && it.sinkTag == "8888" })
    }

    @Test
    fun `DmMain runFromArgs honours --match`() {
        val gms = listOf(RedSquareGM(), TinyGM())
        val report = DmMain.runFromArgs(
            args = arrayOf("--config", "8888", "--match", "RedSquare"),
            allGms = gms,
        )
        assertEquals(1, report.all.size)
        assertEquals("RedSquareGM", report.all.single().gmName)
    }

    @Test
    fun `DmMain rejects empty --config`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            DmMain.runFromArgs(arrayOf<String>(), listOf(RedSquareGM()))
        }
        assertTrue(ex.message!!.contains("no --config given"))
    }

    @Test
    fun `DmMain rejects unknown --config tag`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            DmMain.runFromArgs(arrayOf("--config", "8888", "wat"), listOf(RedSquareGM()))
        }
        assertTrue(ex.message!!.contains("unknown --config tag"))
        assertTrue(ex.message!!.contains("wat"))
    }

    @Test
    fun `DmMain wires --key and --properties into the Report`() {
        val report = DmMain.runFromArgs(
            args = arrayOf(
                "--config", "8888",
                "--key", "os", "Mac",
                "--properties", "build", "release",
            ),
            allGms = listOf(RedSquareGM()),
        )
        val json = report.toJson()
        assertTrue(json.contains("\"os\": \"Mac\""))
        assertTrue(json.contains("\"build\": \"release\""))
    }

    @Test
    fun `DmMain rejects odd-length --key`() {
        assertThrows(IllegalArgumentException::class.java) {
            DmMain.runFromArgs(
                args = arrayOf("--config", "8888", "--key", "lonely"),
                allGms = listOf(RedSquareGM()),
            )
        }
    }

    // ─── Test fixtures ────────────────────────────────────────────────

    private class RedSquareGM : GM() {
        override fun getName(): String = "RedSquareGM"
        override fun getISize(): SkISize = SkISize.Make(4, 4)
        override fun onDraw(canvas: SkCanvas?) {
            canvas?.drawRect(SkRect.MakeWH(4f, 4f), SkPaint(SK_ColorRED))
        }
    }

    private class TinyGM : GM() {
        override fun getName(): String = "TinyGM"
        override fun getISize(): SkISize = SkISize.Make(2, 2)
        override fun onDraw(canvas: SkCanvas?) {
            canvas?.drawRect(SkRect.MakeWH(2f, 2f), SkPaint(SK_ColorRED))
        }
    }
}
