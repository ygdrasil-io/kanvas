package com.kanvas.core

/**
 * Represents a color in RGBA format
 */
data class Color(val red: Int, val green: Int, val blue: Int, val alpha: Int = 255) {
    companion object {
        val TRANSPARENT = Color(0, 0, 0, 0)
        val BLACK = Color(0, 0, 0)
        val WHITE = Color(255, 255, 255)
        val RED = Color(255, 0, 0)
        val GREEN = Color(0, 255, 0)
        val BLUE = Color(0, 0, 255)
    }
}