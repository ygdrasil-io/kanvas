package testing

import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Rect
import com.kanvas.core.Size

/**
 * DrawResult represents the outcome of a GM (Graphics Module) test execution.
 */
enum class DrawResult {
    OK,        // Test drew successfully
    FAIL,      // Test failed to draw
    SKIP       // Test is not applicable in this context
}

/**
 * GM (Graphics Module) is the base class for all rendering tests in Kanvas.
 * It provides the basic structure and lifecycle for graphics tests.
 */
abstract class GM {
    private var backgroundColor: Color = Color.WHITE
    private var haveCalledOnceBeforeDraw: Boolean = false
    
    /**
     * Constructor with optional background color
     */
    constructor() : this(Color.WHITE)
    
    constructor(backgroundColor: Color) {
        this.backgroundColor = backgroundColor
    }
    
    /**
     * Get the name of this GM test
     */
    abstract fun getName(): String
    
    /**
     * Get the preferred size for this GM test
     */
    abstract fun getSize(): Size
    
    /**
     * Called once before any drawing operations
     */
    open fun onOnceBeforeDraw() {
        // Override for setup code
    }
    
    /**
     * Main drawing method - implement this to perform the test
     */
    abstract fun onDraw(canvas: Canvas): DrawResult
    
    /**
     * Draw the background color
     */
    protected fun drawBackground(canvas: Canvas) {
        val size = getSize()
        val bgPaint = Paint().apply {
            color = backgroundColor
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(0f, 0f, size.width, size.height), bgPaint)
    }
    
    /**
     * Main entry point for executing the GM test
     */
    fun draw(canvas: Canvas): DrawResult {
        // Call onceBeforeDraw exactly once
        if (!haveCalledOnceBeforeDraw) {
            haveCalledOnceBeforeDraw = true
            onOnceBeforeDraw()
        }
        
        // Draw background
        drawBackground(canvas)
        
        // Perform the actual drawing
        return onDraw(canvas)
    }
    
    /**
     * Get the background color
     */
    fun getBackgroundColor(): Color = backgroundColor
    
    /**
     * Set the background color
     */
    fun setBackgroundColor(color: Color) {
        this.backgroundColor = color
    }
}

/**
 * SimpleGM is a convenience class for creating simple GM tests that don't need
 * complex state or lifecycle management.
 */
class SimpleGM(
    private val name: String,
    private val size: Size,
    private val backgroundColor: Color = Color.WHITE,
    private val drawProc: (Canvas) -> DrawResult
) : GM(backgroundColor) {
    
    override fun getName(): String = name
    override fun getSize(): Size = size
    override fun onDraw(canvas: Canvas): DrawResult = drawProc(canvas)
}

/**
 * Helper function to create simple GM tests with a more concise syntax
 */
fun simpleGM(
    name: String,
    width: Int,
    height: Int,
    backgroundColor: Color = Color.WHITE,
    drawProc: (Canvas) -> Unit
): SimpleGM {
    return SimpleGM(name, Size(width.toFloat(), height.toFloat()), backgroundColor) { canvas ->
        drawProc(canvas)
        DrawResult.OK
    }
}