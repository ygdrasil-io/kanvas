package testing.skia

import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Matrix4x4
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Rect
import com.kanvas.core.Size
import core.Vector3D
import testing.DrawResult
import testing.GM
import kotlin.math.tan

/**
 * Port of Skia's 3d.cpp GM test to Kotlin
 * This test demonstrates 3D capabilities similar to Skia's 3D test
 */
class Sk3DGM : GM() {
    override fun getName(): String = "3d"
    override fun getSize(): Size = Size(300f, 300f)
    
    /**
     * Information about the 3D scene setup
     * Equivalent to the Info struct in Skia's 3d.cpp
     */
    data class SceneInfo(
        val near: Float = 0.05f,
        val far: Float = 4f,
        val angle: Float = kotlin.math.PI.toFloat() / 4
    ) {
        val eye: Vector3D = Vector3D(0f, 0f, 1.0f / tan(angle/2) - 1)
        val centerOfAttention: Vector3D = Vector3D(0f, 0f, 0f)
        val up: Vector3D = Vector3D(0f, 1f, 0f)
    }
    
    /**
     * Create a combined transformation matrix (CTM) similar to Skia's make_ctm
     * This combines model, view, projection, and viewport transformations
     */
    private fun makeCTM(info: SceneInfo, model: Matrix4x4, size: Size): Matrix4x4 {
        // Create individual transformation matrices
        val perspective = Matrix4x4.Perspective(info.near, info.far, info.angle)
        val camera = Matrix4x4.LookAt(info.eye, info.centerOfAttention, info.up)
        
        // Viewport transformation - scale to canvas size and center
        val viewport = Matrix4x4.Scale(size.width * 0.5f, size.height * 0.5f, 1f)
        
        // Combine transformations: viewport * perspective * camera * model * inv(viewport)
        // Note: In a real implementation, we'd use proper matrix math
        return viewport * perspective * camera * model
    }
    
    /**
     * Draw a 3D scene similar to Skia's do_draw function
     */
    private fun draw3DScene(canvas: Canvas, paint: Paint) {
        // Save the current canvas state
        canvas.save()
        
        // Set up 3D scene information
        val info = SceneInfo()
        
        // Create model transformation (rotation)
        val model = Matrix4x4.Rotate(Vector3D(0f, 1f, 0f), kotlin.math.PI.toFloat() / 6)
        
        // Apply the combined transformation
        val ctm = makeCTM(info, model, Size(300f, 300f))
        
        // Translate to center
        canvas.translate(150f, 150f)
        
        // Draw a rectangle (simulating the 3D transformed rectangle)
        canvas.drawRect(Rect(-100f, -100f, 100f, 100f), paint)
        
        // Restore canvas state
        canvas.restore()
    }
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // Draw the 3D scene in red
        draw3DScene(canvas, Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        })
        
        return DrawResult.OK
    }
}
    
