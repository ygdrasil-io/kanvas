package device

import com.kanvas.core.Arc
import com.kanvas.core.Bitmap
import com.kanvas.core.Color
import com.kanvas.core.ColorInfo
import com.kanvas.core.Device
import com.kanvas.core.Matrix
import com.kanvas.core.Paint
import com.kanvas.core.Path
import com.kanvas.core.RRect
import com.kanvas.core.Rect
import com.kanvas.core.SamplingOptions
import com.kanvas.core.Shader
import com.kanvas.core.SurfaceProps
import core.GlyphRunList

/**
 * GPU Device implementation - currently a no-op placeholder
 *
 * This class serves as a temporary implementation for GPU rendering.
 * It will be fully rewritten once the rasterization pipeline is working correctly.
 *
 * Current implementation:
 * - Does not perform any actual GPU rendering
 * - Falls back to CPU rendering (BitmapDevice)
 * - Serves as a structural placeholder for future GPU implementation
 *
 * TODO: Replace this with actual GPU rendering using Vulkan/Metal/OpenGL
 * once the rasterization pipeline is stable and tested.
 */
class GPUDevice(
    override val width: Int,
    override val height: Int,
    override val colorInfo: ColorInfo,
    override val surfaceProps: SurfaceProps
) : Device {

    // Fallback to CPU rendering for now
    private val fallbackDevice: Device = BitmapDevice(width, height, colorInfo, surfaceProps)

    override val bitmap: Bitmap
        get() = fallbackDevice.bitmap

    // No-op implementations for GPU-specific methods
    // These will be properly implemented when GPU rendering is added

    override fun drawRect(rect: Rect, paint: Paint) {
        // TODO: Implement GPU-accelerated rectangle drawing
        // For now, fall back to CPU rendering
        fallbackDevice.drawRect(rect, paint)
    }

    override fun drawPath(path: Path, paint: Paint) {
        // TODO: Implement GPU-accelerated path drawing
        // For now, fall back to CPU rendering
        fallbackDevice.drawPath(path, paint)
    }

    override fun setShader(shader: Shader?) {
        // TODO: Implement GPU shader handling
        fallbackDevice.setShader(shader)
    }

    override fun getShader(): Shader? {
        // TODO: Implement GPU shader retrieval
        return fallbackDevice.getShader()
    }

    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        // TODO: Implement GPU-accelerated text drawing
        fallbackDevice.drawText(text, x, y, paint)
    }

    override fun onDrawGlyphRunList(glyphRunList: GlyphRunList, paint: Paint) {
        // TODO: Implement GPU-accelerated glyph run drawing
        fallbackDevice.onDrawGlyphRunList(glyphRunList, paint)
    }

    override fun drawImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint) {
        // TODO: Implement GPU-accelerated image drawing
        fallbackDevice.drawImage(image, src, dst, paint)
    }

    override fun drawImage(image: Bitmap, src: Rect, dst: Rect, paint: Paint, sampling: SamplingOptions) {
        // TODO: Implement GPU-accelerated image drawing with sampling
        fallbackDevice.drawImage(image, src, dst, paint, sampling)
    }

    override fun clear(color: Color) {
        // TODO: Implement GPU-accelerated clear
        fallbackDevice.clear(color)
    }

    override fun flush() {
        // TODO: Implement GPU command flushing
        fallbackDevice.flush()
    }

    override fun getTotalMatrix(): Matrix {
        // TODO: Implement GPU matrix handling
        return fallbackDevice.getTotalMatrix()
    }

    override fun getClipBounds(): Rect {
        // TODO: Implement GPU clip bounds
        return fallbackDevice.getClipBounds()
    }

    override fun saveClipStack(): Int {
        // TODO: Implement GPU clip stack saving
        return fallbackDevice.saveClipStack()
    }

    override fun restoreClipStack(): Int {
        // TODO: Implement GPU clip stack restoring
        return fallbackDevice.restoreClipStack()
    }

    override fun getClipStackDepth(): Int {
        // TODO: Implement GPU clip stack depth
        return fallbackDevice.getClipStackDepth()
    }

    override fun clipRect(rect: Rect, clipOp: Device.ClipOp, doAntiAlias: Boolean) {
        // TODO: Implement GPU rectangle clipping
        fallbackDevice.clipRect(rect, clipOp, doAntiAlias)
    }

    override fun clipPath(path: Path, clipOp: Device.ClipOp, doAntiAlias: Boolean) {
        // TODO: Implement GPU path clipping
        fallbackDevice.clipPath(path, clipOp, doAntiAlias)
    }

    override fun writePixels(src: Bitmap, x: Int, y: Int): Boolean {
        // TODO: Implement GPU pixel writing
        return fallbackDevice.writePixels(src, x, y)
    }

    override fun readPixels(dst: Bitmap, x: Int, y: Int): Boolean {
        // TODO: Implement GPU pixel reading
        return fallbackDevice.readPixels(dst, x, y)
    }

    override fun accessPixels(): Bitmap {
        // TODO: Implement GPU pixel access
        return fallbackDevice.accessPixels()
    }

    override fun peekPixels(): Bitmap {
        // TODO: Implement GPU pixel peeking
        return fallbackDevice.peekPixels()
    }

    override fun replaceClip(rect: Rect) {
        // TODO: Implement GPU clip replacement
        fallbackDevice.replaceClip(rect)
    }

    override fun drawOval(oval: Rect, paint: Paint) {
        // TODO: Implement GPU-accelerated oval drawing
        fallbackDevice.drawOval(oval, paint)
    }

    override fun drawArc(arc: Arc, paint: Paint) {
        // TODO: Implement GPU-accelerated arc drawing
        fallbackDevice.drawArc(arc, paint)
    }

    override fun drawRRect(rrect: RRect, paint: Paint) {
        // TODO: Implement GPU-accelerated rounded rectangle drawing
        fallbackDevice.drawRRect(rrect, paint)
    }

    override fun drawPaint(paint: Paint) {
        // TODO: Implement GPU-accelerated paint drawing
        fallbackDevice.drawPaint(paint)
    }
}