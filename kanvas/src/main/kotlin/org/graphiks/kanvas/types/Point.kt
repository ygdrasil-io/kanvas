package org.graphiks.kanvas.types

data class Point(var x: Float, var y: Float) {
    companion object {
        val ZERO = Point(0f, 0f)
    }
}
