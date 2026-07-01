package org.graphiks.kanvas.svg

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import kotlin.test.Test
import java.io.File

class SvgCanvasTest {
    @Test
    fun `test draw simple SVG rect`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                <rect x="10" y="10" width="80" height="80" fill="red" />
            </svg>
        """.trimIndent()

        val parser = SvgParser()
        val root = parser.parse(svg)
        
        assert(root.rects.size == 1)
        assert(root.rects[0].x == 10f)
        assert(root.rects[0].y == 10f)
        assert(root.rects[0].width == 80f)
        assert(root.rects[0].height == 80f)
        assert(root.rects[0].fill == "red")
    }

    @Test
    fun `test draw SVG with path`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                <path d="M 10 10 L 90 10 L 90 90 L 10 90 Z" fill="blue" />
            </svg>
        """.trimIndent()

        val parser = SvgParser()
        val root = parser.parse(svg)
        
        assert(root.paths.size == 1)
        assert(root.paths[0].d == "M 10 10 L 90 10 L 90 90 L 10 90 Z")
        assert(root.paths[0].fill == "blue")
    }

    @Test
    fun `test draw SVG with circle`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                <circle cx="50" cy="50" r="40" fill="green" />
            </svg>
        """.trimIndent()

        val parser = SvgParser()
        val root = parser.parse(svg)
        
        assert(root.circles.size == 1)
        assert(root.circles[0].cx == 50f)
        assert(root.circles[0].cy == 50f)
        assert(root.circles[0].r == 40f)
        assert(root.circles[0].fill == "green")
    }

    @Test
    fun `test draw SVG with group`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                <g transform="translate(10, 10)">
                    <rect x="0" y="0" width="50" height="50" fill="yellow" />
                    <circle cx="25" cy="25" r="20" fill="orange" />
                </g>
            </svg>
        """.trimIndent()

        val parser = SvgParser()
        val root = parser.parse(svg)
        
        assert(root.groups.size == 1)
        assert(root.groups[0].transform == "translate(10, 10)")
        assert(root.groups[0].rects.size == 1)
        assert(root.groups[0].circles.size == 1)
    }

    @Test
    fun `test draw SVG with gradient`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                <defs>
                    <linearGradient id="grad1" x1="0%" y1="0%" x2="100%" y2="0%">
                        <stop offset="0%" stop-color="red" />
                        <stop offset="100%" stop-color="blue" />
                    </linearGradient>
                </defs>
                <rect x="10" y="10" width="80" height="80" fill="url(#grad1)" />
            </svg>
        """.trimIndent()

        val parser = SvgParser()
        val root = parser.parse(svg)
        
        assert(root.defs.size == 1)
        assert(root.defs[0].gradients.size == 1)
        assert(root.defs[0].gradients[0] is SvgGradient.LinearGradient)
    }

    @Test
    fun `test path parser moveTo lineTo`() {
        val parser = SvgPathParser()
        val path = parser.parse("M 10 20 L 30 40")
        
        assert(path != null)
    }

    @Test
    fun `test path parser cubicTo`() {
        val parser = SvgPathParser()
        val path = parser.parse("M 10 10 C 20 20 30 30 40 40")
        
        assert(path != null)
    }

    @Test
    fun `test path parser close`() {
        val parser = SvgPathParser()
        val path = parser.parse("M 10 10 L 20 20 L 30 10 Z")
        
        assert(path != null)
    }

    @Test
    fun `test paint parser hex color`() {
        val parser = SvgPaintParser()
        val color = parser.parseColor("#FF0000")
        assert(color == 0xFFFF0000.toInt())
    }

    @Test
    fun `test paint parser rgb color`() {
        val parser = SvgPaintParser()
        val color = parser.parseColor("rgb(255, 0, 0)")
        assert(color == 0xFFFF0000.toInt())
    }

    @Test
    fun `test paint parser rgba color`() {
        val parser = SvgPaintParser()
        val color = parser.parseColor("rgba(255, 0, 0, 0.5)")
        val alpha = (color shr 24) and 0xFF
        assert(alpha == 127 || alpha == 128)
    }

    @Test
    fun `test paint parser named color`() {
        val parser = SvgPaintParser()
        val color = parser.parseColor("red")
        assert(color == 0xFFFF0000.toInt())
    }

    @Test
    fun `test transform parser translate`() {
        val parser = SvgTransformParser()
        val transform = parser.parse("translate(10, 20)")
        assert(transform != GPUTransformFacts.identity())
    }

    @Test
    fun `test transform parser scale`() {
        val parser = SvgTransformParser()
        val transform = parser.parse("scale(2)")
        assert(transform != GPUTransformFacts.identity())
    }

    @Test
    fun `test transform parser rotate`() {
        val parser = SvgTransformParser()
        val transform = parser.parse("rotate(45)")
        assert(transform != GPUTransformFacts.identity())
    }

    @Test
    fun `test transform parser matrix`() {
        val parser = SvgTransformParser()
        val transform = parser.parse("matrix(1, 0, 0, 1, 10, 20)")
        assert(transform != GPUTransformFacts.identity())
    }

    @Test
    fun `test gradient parser linear`() {
        val parser = SvgGradientParser()
        val stops = listOf(
            SvgStop(offset = 0f, stopColor = "#FF0000"),
            SvgStop(offset = 1f, stopColor = "#0000FF")
        )
        val gradient = parser.parseLinearGradient(0f, 0f, 1f, 0f, stops)
        assert(gradient is org.graphiks.kanvas.paint.Shader.LinearGradient)
    }

    @Test
    fun `test gradient parser radial`() {
        val parser = SvgGradientParser()
        val stops = listOf(
            SvgStop(offset = 0f, stopColor = "#FF0000"),
            SvgStop(offset = 1f, stopColor = "#0000FF")
        )
        val gradient = parser.parseRadialGradient(0.5f, 0.5f, 0.5f, stops)
        assert(gradient is org.graphiks.kanvas.paint.Shader.RadialGradient)
    }
}
