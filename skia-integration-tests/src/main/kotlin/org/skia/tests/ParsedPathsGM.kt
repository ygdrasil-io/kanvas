package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom
import org.skia.utils.SkParsePath

/**
 * Port of Skia's gm/arcto.cpp (DEF_SIMPLE_GM(parsedpaths, ..., 500, 500)).
 *
 * Exercises SkParsePath::FromSVGString by generating a large number of
 * random-but-syntactically-valid SVG path strings (seeded with the default
 * SkRandom) and drawing each into a 100x100 clipped tile on a 5x5 grid.
 *
 * Each 100x100 tile receives 3 random paths drawn with random colours.
 * Reference image: parsedpaths.png, 500 x 500, white background.
 */
public class ParsedPathsGM : GM() {

    override fun getName(): String = "parsedpaths"
    override fun getISize(): SkISize = SkISize.Make(DIMENSION, DIMENSION)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rand = SkRandom()
        val paint = SkPaint().apply { isAntiAlias = true }

        for (xStart in 0 until DIMENSION step 100) {
            c.save()
            for (yStart in 0 until DIMENSION step 100) {
                var count = 3
                do {
                    val y = rand.nextRangeU(30, 70)
                    val x = rand.nextRangeU(30, 70)
                    val spec = StringBuilder()
                    spec.append("M ").append(x).append(',').append(y).append('\n')
                    var i = rand.nextRangeU(0, 10)
                    while (i-- > 0) {
                        spec.append(makeRandomSvgPath(rand))
                    }
                    val path = SkParsePath.FromSVGString(spec.toString())
                    checkNotNull(path) { "SkParsePath.FromSVGString failed" }
                    paint.color = rand.nextU()
                    c.save()
                    c.clipRect(SkRect.MakeIWH(100, 100))
                    c.drawPath(path, paint)
                    c.restore()
                } while (--count > 0)
                c.translate(0f, 100f)
            }
            c.restore()
            c.translate(100f, 0f)
        }
    }

    private companion object {
        const val DIMENSION = 500

        /**
         * Table of legal SVG path verbs and the number of scalar coordinates
         * each consumes per repetition. Matches C++ gLegal[] exactly.
         *
         * For 'A' (fScalars=4): indices 0..1 are the two radii, indices 2..3
         * are the endpoint (x,y). The rotation/flags are inserted after index 1
         * to match the C++ special-case.
         */
        private data class Legal(val symbol: Char, val scalars: Int)

        private val LEGAL = arrayOf(
            Legal('M', 2),
            Legal('H', 1),
            Legal('V', 1),
            Legal('L', 2),
            Legal('Q', 4),
            Legal('T', 2),
            Legal('C', 6),
            Legal('S', 4),
            Legal('A', 4),
            Legal('Z', 0),
        )

        /**
         * Mirrors C++ gWhiteSpace[]:
         * { 0, 0, 0, 0, 0, 0, 0, 0, ' ', ' ', ' ', ' ', 0x09, 0x0D, 0x0A }
         * 15 entries: indices 0-7 are NUL (emitting nothing),
         * indices 8-11 are SPACE, index 12 is HT, 13 is CR, 14 is LF.
         *
         * Using Char(0) for the NUL entries; addWhite() guards with ch.code != 0.
         */
        @Suppress("MagicNumber")
        private val WHITE_SPACE: CharArray = charArrayOf(
            0.toChar(), 0.toChar(), 0.toChar(), 0.toChar(),
            0.toChar(), 0.toChar(), 0.toChar(), 0.toChar(),
            ' ', ' ', ' ', ' ',
            '\t', '\r', '\n',
        )

        /**
         * Mirrors add_white (gEasy = false branch).
         * Emits 0-2 characters from WHITE_SPACE; NUL entries are skipped.
         */
        private fun addWhite(rand: SkRandom, atom: StringBuilder) {
            val reps = rand.nextRangeU(0, 2)
            repeat(reps) {
                val idx = rand.nextRangeU(0, WHITE_SPACE.size - 1)
                val ch = WHITE_SPACE[idx]
                if (ch.code != 0) atom.append(ch)
            }
        }

        /**
         * Mirrors add_comma (gEasy = false branch).
         * Appends optional whitespace, an optional comma, then at least one
         * more whitespace character (do-while matches C++).
         */
        private fun addComma(rand: SkRandom, atom: StringBuilder) {
            val countBefore = atom.length
            addWhite(rand, atom)
            if (rand.nextBool()) atom.append(',')
            do {
                addWhite(rand, atom)
            } while (countBefore == atom.length)
        }

        /**
         * Mirrors add_some_white.
         * Loops add_white until at least one character was appended.
         */
        private fun addSomeWhite(rand: SkRandom, atom: StringBuilder) {
            val before = atom.length
            do {
                addWhite(rand, atom)
            } while (before == atom.length)
        }

        /**
         * Mirrors C++ SkStrAppendScalar (%.8g format, up to 8 significant digits,
         * no trailing zeros, no unnecessary decimal point).
         * Using Locale.US to match C++'s decimal point (always '.').
         */
        private fun appendScalar(atom: StringBuilder, v: Float) {
            atom.append("%.8g".format(java.util.Locale.US, v))
        }

        /**
         * Mirrors make_random_svg_path (gEasy = false).
         *
         * Picks a random Legal entry, randomises its case (upper=absolute /
         * lower=relative), emits 1-3 repetitions of its coordinate block with
         * randomly-placed whitespace/commas. For 'A' verbs appends the
         * rotation/flag/flag tokens after the second scalar (index 1).
         */
        private fun makeRandomSvgPath(rand: SkRandom): String {
            val atom = StringBuilder()
            val legalIndex = rand.nextRangeU(0, LEGAL.size - 1)
            val legal = LEGAL[legalIndex]
            addWhite(rand, atom)
            // Randomise case: OR with 0x20 -> lower-case (relative).
            val symbol = if (rand.nextBool()) legal.symbol
                         else (legal.symbol.code or 0x20).toChar()
            atom.append(symbol)
            val reps = rand.nextRangeU(1, 3)
            for (rep in 0 until reps) {
                for (index in 0 until legal.scalars) {
                    val coord = rand.nextRangeF(0f, 100f)
                    addWhite(rand, atom)
                    appendScalar(atom, coord)
                    if (rep < reps - 1 && index < legal.scalars - 1) {
                        addComma(rand, atom)
                    } else {
                        addSomeWhite(rand, atom)
                    }
                    // After the second scalar of an 'A' verb, emit rotation + flags.
                    if (legal.symbol == 'A' && index == 1) {
                        appendScalar(atom, rand.nextRangeF(-720f, 720f))
                        addComma(rand, atom)
                        atom.append(rand.nextRangeU(0, 1))
                        addComma(rand, atom)
                        atom.append(rand.nextRangeU(0, 1))
                        addComma(rand, atom)
                    }
                }
            }
            return atom.toString()
        }
    }
}
