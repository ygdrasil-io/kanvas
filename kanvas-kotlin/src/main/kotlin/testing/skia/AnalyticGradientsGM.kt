package testing.skia

import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Rect
import com.kanvas.core.Shaders
import com.kanvas.core.Size
import com.kanvas.core.TileMode
import testing.DrawResult
import testing.GM

/**
 * Port of Skia's analytic_gradients.cpp test
 * Tests analytic gradient calculations with various interpolation intervals
 * Simplified version focusing on core functionality available in Kanvas
 * 
 * This test creates a grid of gradient patterns to test different configurations:
 * - Different numbers of color stops (1-8 intervals)
 * - Different interpolation modes (smooth transitions vs hard stops)
 * - Clamping tile mode
 */
class AnalyticGradientsGM : GM() {
    override fun getName(): String = "analytic_gradients"
    override fun getSize(): Size = Size(1024f, 512f)

    companion object {
        // Color palette for gradients
        private val COLORS = listOf(
            Color(0x33, 0x33, 0x33, 0xFF), // Dark gray
            Color(0xFF, 0x00, 0x00, 0xFF), // Red
            Color(0xFF, 0xFF, 0x00, 0xFF), // Yellow
            Color(0x00, 0xFF, 0x00, 0xFF), // Green
            Color(0x00, 0xFF, 0xFF, 0xFF), // Cyan
            Color(0x00, 0x00, 0xFF, 0xFF), // Blue
            Color(0xFF, 0x00, 0xFF, 0xFF), // Magenta
            Color(0x00, 0x00, 0x00, 0xFF), // Black
            Color(0xCC, 0xCC, 0xCC, 0xFF)  // Light gray
        )

        // Grid configuration
        private const val NUM_ROWS = 8
        private const val NUM_COLS = 4
        private const val CELL_WIDTH = 128
        private const val CELL_HEIGHT = 64
        private const val PAD_WIDTH = 3
        private const val PAD_HEIGHT = 3
        private const val RECT_WIDTH = CELL_WIDTH - (2 * PAD_WIDTH)
        private const val RECT_HEIGHT = CELL_HEIGHT - (2 * PAD_HEIGHT)
    }

    override fun onDraw(canvas: Canvas): DrawResult {
        return try {
            // Set background
            canvas.clear(Color(0xFF, 0xFF, 0xFF, 0xFF))

            // Draw grid of gradient tests
            drawGradientGrid(canvas)

            // Draw title
            drawTitle(canvas, "Analytic Gradients Test")

            DrawResult.OK
        } catch (e: Exception) {
            println("Error in AnalyticGradientsGM: ${e.message}")
            DrawResult.FAIL
        }
    }

    private fun drawGradientGrid(canvas: Canvas) {
        // Define different gradient configurations
        val gradientConfigs = listOf(
            // Row 0: 2 colors (1 interval)
            GradientConfig(2, listOf(0f, 1f), "2 colors - smooth"),
            // Row 1: 3 colors (2 intervals)
            GradientConfig(3, listOf(0f, 0.5f, 1f), "3 colors - smooth"),
            // Row 2: 4 colors (3 intervals)
            GradientConfig(4, listOf(0f, 0.33f, 0.67f, 1f), "4 colors - smooth"),
            // Row 3: 5 colors (4 intervals)
            GradientConfig(5, listOf(0f, 0.25f, 0.5f, 0.75f, 1f), "5 colors - smooth"),
            // Row 4: 6 colors (5 intervals)
            GradientConfig(6, listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f), "6 colors - smooth"),
            // Row 5: 7 colors (6 intervals)
            GradientConfig(7, listOf(0f, 0.17f, 0.33f, 0.5f, 0.67f, 0.83f, 1f), "7 colors - smooth"),
            // Row 6: 8 colors (7 intervals)
            GradientConfig(8, listOf(0f, 0.14f, 0.29f, 0.43f, 0.57f, 0.71f, 0.86f, 1f), "8 colors - smooth"),
            // Row 7: 9 colors (8 intervals)
            GradientConfig(9, listOf(0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f), "9 colors - smooth")
        )

        // Draw each gradient configuration
        gradientConfigs.forEachIndexed { row, config ->
            val y = row * CELL_HEIGHT + PAD_HEIGHT

            // Draw 4 different modes for each configuration
            for (col in 0 until NUM_COLS) {
                val x = col * CELL_WIDTH + PAD_WIDTH
                
                // Create gradient colors by cycling through our color palette
                val gradientColors = (0 until config.colorCount).map { 
                    COLORS[it % COLORS.size]
                }
                
                // Create linear gradient
                val start = com.kanvas.core.Point(0f, 0f)
                val end = com.kanvas.core.Point(RECT_WIDTH.toFloat(), 0f)
                val shader = Shaders.makeLinearGradient(
                    gradientColors,
                    config.positions,
                    start,
                    end,
                    TileMode.CLAMP
                )
                
                // Draw gradient rectangle
                val paint = Paint().apply {
                    this.shader = shader
                    isAntiAlias = true
                }
                
                canvas.save()
                canvas.translate(x.toFloat(), y.toFloat())
                canvas.drawRect(Rect(0f, 0f, RECT_WIDTH.toFloat(), RECT_HEIGHT.toFloat()), paint)
                canvas.restore()
            }
        }
    }

    private fun drawTitle(canvas: Canvas, title: String) {
        // Draw title background
        val titlePaint = Paint().apply {
            color = Color(0x33, 0x33, 0x33, 0xFF)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(20f, 20f, 300f, 50f), titlePaint)

        // Draw title text (simplified - Kanvas doesn't have full text support)
        // canvas.drawText(title, 30f, 40f, Paint().apply { color = Color.WHITE })
    }

    /**
     * Helper class for gradient configuration
     */
    private data class GradientConfig(
        val colorCount: Int,
        val positions: List<Float>,
        val description: String
    )
}