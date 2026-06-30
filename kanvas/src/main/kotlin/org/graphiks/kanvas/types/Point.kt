package org.graphiks.kanvas.types

data class Point(val x: Float, val y: Float) {
    companion object {
        val ZERO = Point(0f, 0f)
    }
}
