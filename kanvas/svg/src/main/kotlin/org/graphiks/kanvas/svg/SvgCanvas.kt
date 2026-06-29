package org.graphiks.kanvas.svg

import org.graphiks.kanvas.Canvas
import java.io.File
import java.io.InputStream

class SvgCanvas(private val canvas: Canvas) {
    private val parser = SvgParser()
    private val renderer = SvgRenderer(canvas)

    fun drawSvg(svg: String) {
        val root = parser.parse(svg)
        renderer.render(root)
    }

    fun drawSvg(file: File) {
        drawSvg(file.readText())
    }

    fun drawSvg(input: InputStream) {
        drawSvg(input.reader().readText())
    }
}

fun Canvas.drawSvg(svg: String) {
    SvgCanvas(this).drawSvg(svg)
}

fun Canvas.drawSvg(file: File) {
    SvgCanvas(this).drawSvg(file)
}

fun Canvas.drawSvg(input: InputStream) {
    SvgCanvas(this).drawSvg(input)
}
