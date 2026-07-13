package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LatticeDecompositionTest {
    @Test
    fun `fixed color lattice cells override the image while transparent cells are skipped`() {
        val operation = DisplayOp.DrawImageLattice(
            image = Image(80, 80, ColorType.RGBA_8888, "lattice", ByteArray(80 * 80 * 4)),
            lattice = Lattice(
                xDivs = listOf(4, 5),
                yDivs = listOf(1, 2),
                flags = listOf(
                    LatticeFlags.DEFAULT, LatticeFlags.DEFAULT, LatticeFlags.DEFAULT,
                    LatticeFlags.FIXED_COLOR, LatticeFlags.FIXED_COLOR, LatticeFlags.FIXED_COLOR,
                    LatticeFlags.TRANSPARENT, LatticeFlags.TRANSPARENT, LatticeFlags.TRANSPARENT,
                ),
                colors = listOf(
                    Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT,
                    Color.RED, Color(0x880000FFu), Color(0xFF00FF00u),
                    Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT,
                ),
            ),
            dst = Rect.fromXYWH(100f, 100f, 200f, 200f),
            paint = Paint(),
            transform = Matrix33.identity(),
            clip = ClipStack.WideOpen,
        )

        val cells = operation.decompose()

        assertEquals(6, cells.size)
        // The first and final bands remain at their source size. Only the
        // middle band stretches to absorb the remaining destination space.
        assertEquals(Rect.fromLTRB(100f, 100f, 104f, 101f), cells[0].dst)
        assertEquals(Rect.fromLTRB(104f, 100f, 225f, 101f), cells[1].dst)
        assertEquals(Rect.fromLTRB(225f, 100f, 300f, 101f), cells[2].dst)
        assertEquals(Rect.fromLTRB(100f, 101f, 104f, 222f), cells[3].dst)
        assertEquals(Rect.fromLTRB(104f, 101f, 225f, 222f), cells[4].dst)
        assertEquals(Rect.fromLTRB(225f, 101f, 300f, 222f), cells[5].dst)
        assertNull(cells[0].color)
        assertEquals(Color.RED, cells[3].color)
        assertEquals(Color(0x880000FFu), cells[4].color)
        assertEquals(Color(0xFF00FF00u), cells[5].color)
    }
}
