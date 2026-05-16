package com.kanvas.core

/**
 * Extension functions for Canvas that provide missing APIs
 */

/**
 * Draw a circle on the canvas
 * Note: This is a placeholder implementation since the real drawCircle is not yet implemented
 */
fun Canvas.drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
    // Placeholder implementation: draw a square that approximates the circle
    // In a real implementation, this would draw an actual circle
    val diameter = radius * 2
    drawRect(Rect(cx - radius, cy - radius, cx + radius, cy + radius), paint)
}

/**
 * Draw text on the canvas
 * Note: This is a placeholder implementation since text rendering is complex
 */
fun Canvas.drawText(text: String, x: Float, y: Float, paint: Paint) {
    // Placeholder: text rendering would go here
    // For now, we'll just draw a small rectangle to represent text position
    drawRect(Rect(x, y - 10f, x + text.length * 6f, y), paint)
}

/**
 * Draw an oval on the canvas
 * Note: This is a placeholder implementation
 */
fun Canvas.drawOval(oval: Rect, paint: Paint) {
    // Placeholder: draw the bounding rectangle
    drawRect(oval, paint)
}

/**
 * Draw a rounded rectangle on the canvas
 * Note: This is a placeholder implementation
 */
fun Canvas.drawRoundRect(rect: Rect, rx: Float, ry: Float, paint: Paint) {
    // Placeholder: draw the regular rectangle
    drawRect(rect, paint)
}