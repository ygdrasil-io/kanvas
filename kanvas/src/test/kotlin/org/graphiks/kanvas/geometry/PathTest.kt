package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class PathTest {
    @Test
    fun `Path moveTo lineTo close`() {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(50f, 100f)
            close()
        }
        assertEquals(FillType.WINDING, path.fillType)
    }

    @Test
    fun `Path DSL construction`() {
        val path = Path {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(100f, 100f)
            close()
        }
        assertEquals(FillType.WINDING, path.fillType)
    }

    @Test
    fun `Path addRect`() {
        val path = Path().addRect(Rect.fromLTRB(0f, 0f, 100f, 80f))
        assertTrue(path.verbs().size >= 5) // 4 lines + close
    }

    @Test
    fun `Path addOval`() {
        val path = Path().addOval(Rect.fromLTRB(0f, 0f, 100f, 80f))
        assertTrue(path.verbs().size >= 5) // 4 cubics + close
    }

    @Test
    fun `Path addCircle`() {
        val path = Path().addCircle(50f, 50f, 30f)
        assertTrue(path.verbs().size >= 5) // 4 cubics + close
    }

    @Test
    fun `Path addRRect`() {
        val path = Path().addRRect(RRect(Rect.fromLTRB(0f, 0f, 100f, 80f), 10f))
        assertTrue(path.verbs().size >= 5) // multiple lines + arcs + close
    }

    @Test
    fun `Path transform`() {
        val path = Path().addRect(Rect.fromLTRB(0f, 0f, 10f, 10f))
        val moved = path.transform(5f, 5f, 1f, 1f)
        assertTrue(moved is Path)
    }

    @Test
    fun `Path internal verbs access`() {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(10f, 10f)
        }
        assertEquals(2, path.verbs().size)
    }
}
