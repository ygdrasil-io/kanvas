package org.skia.foundation


import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkIRect
import org.graphiks.math.SkMatrix

/**
 * R3.8 stub-coverage tests for [SkRasterHandleAllocator] — verifies
 * that [SkRasterHandleAllocator.MakeCanvas] honours a caller-supplied
 * allocator and that drawing into the returned canvas reaches pixels.
 */
class SkRasterHandleAllocatorTest {

    /**
     * Trivial allocator implementation : records every callback,
     * accepts the pre-built rec, and notes the handle/matrix/clip
     * passed to [updateHandle] (which currently isn't invoked from
     * [SkRasterHandleAllocator.MakeCanvas] — exercised directly).
     */
    private class RecordingAllocator(
        private val customHandle: Any = "my-handle",
    ) : SkRasterHandleAllocator() {
        var allocCalls = 0
        var lastAllocInfo: SkImageInfo? = null
        var updateCalls = 0
        var lastUpdateHandle: Any? = null

        override fun allocHandle(
            info: SkImageInfo,
            hndlMatrix: SkMatrix,
            rec: Rec,
        ): Boolean {
            allocCalls++
            lastAllocInfo = info
            return true
        }

        override fun updateHandle(handle: Any, ctm: SkMatrix, clip: SkIRect) {
            updateCalls++
            lastUpdateHandle = handle
        }
    }

    @Test
    fun `MakeCanvas with null rec invokes allocHandle then returns a usable canvas`() {
        val allocator = RecordingAllocator()
        val info = SkImageInfo.MakeN32Premul(8, 8)

        val canvas = SkRasterHandleAllocator.MakeCanvas(allocator, info, rec = null)

        assertNotNull(canvas, "MakeCanvas should return a canvas for a non-empty info")
        assertEquals(1, allocator.allocCalls, "allocHandle should be invoked once for the base layer")
        assertEquals(8, canvas!!.bitmap.width)
        assertEquals(8, canvas.bitmap.height)
    }

    @Test
    fun `MakeCanvas honours an explicit Rec without calling allocHandle`() {
        val allocator = RecordingAllocator()
        val info = SkImageInfo.MakeN32Premul(4, 4)
        val rec = object : SkRasterHandleAllocator.Rec {
            override val proc: ((handle: Any) -> Unit)? = null
            override val handle: Any = "caller-handle"
            override val pixels: ByteArray = ByteArray(info.minRowBytes() * info.height)
            override val rowBytes: Int = info.minRowBytes()
        }

        val canvas = SkRasterHandleAllocator.MakeCanvas(allocator, info, rec)

        assertNotNull(canvas)
        // Caller-supplied rec means allocator is not consulted.
        assertEquals(0, allocator.allocCalls)
    }

    @Test
    fun `MakeCanvas returns null for an empty image info`() {
        val allocator = RecordingAllocator()
        val info = SkImageInfo.MakeN32Premul(0, 10)
        assertNull(SkRasterHandleAllocator.MakeCanvas(allocator, info))
    }

    @Test
    fun `drawing into the canvas mutates the bitmap pixels`() {
        val allocator = RecordingAllocator()
        val info = SkImageInfo.MakeN32Premul(2, 2)
        val canvas = SkRasterHandleAllocator.MakeCanvas(allocator, info, rec = null)
        assertNotNull(canvas)

        canvas!!.drawColor(SkColorSetARGB(0xFF, 0xAA, 0xBB, 0xCC))

        val px = canvas.bitmap.getPixel(0, 0)
        assertEquals(0xAA, SkColorGetR(px))
        assertEquals(0xBB, SkColorGetG(px))
        assertEquals(0xCC, SkColorGetB(px))
        assertEquals(0xFF, SkColorGetA(px))
    }

    @Test
    fun `updateHandle is invocable directly with caller-supplied state`() {
        val allocator = RecordingAllocator()
        val token: Any = "tok"
        val ctm = SkMatrix.Identity
        val clip = SkIRect.MakeWH(10, 10)

        allocator.updateHandle(token, ctm, clip)

        assertEquals(1, allocator.updateCalls)
        assertSame(token, allocator.lastUpdateHandle)
    }

    @Test
    fun `Rec contract is satisfied by a simple anonymous implementation`() {
        val proc: (Any) -> Unit = { /* no-op */ }
        val rec = object : SkRasterHandleAllocator.Rec {
            override val proc: ((handle: Any) -> Unit)? = proc
            override val handle: Any = 42
            override val pixels: ByteArray = ByteArray(16)
            override val rowBytes: Int = 4
        }
        assertEquals(42, rec.handle)
        assertEquals(4, rec.rowBytes)
        assertEquals(16, rec.pixels.size)
        assertTrue(rec.proc === proc)
    }
}
