package org.skia.core

/**
 * Functional factory for [SkBBoxHierarchy] instances. Mirrors Skia's
 * [`SkBBHFactory`](https://github.com/google/skia/blob/main/include/core/SkBBHFactory.h)
 * — `SkPictureRecorder.beginRecording` consumes a factory, calls it
 * once per recording, and the produced hierarchy ends up owned by
 * the resulting [SkPicture].
 *
 * Defined as a SAM interface so callers can pass a lambda when a
 * full subclass is overkill :
 *
 * ```kotlin
 * val recorder = SkPictureRecorder()
 * val canvas = recorder.beginRecording(800f, 600f, SkRTreeFactory)
 * gm.draw(canvas)
 * val picture = recorder.finishRecordingAsPicture()  // BBH baked in
 * ```
 *
 * Built-in factories :
 *  - [SkRTreeFactory] — produces an [SkRTree], the upstream default.
 */
public fun interface SkBBHFactory {
    public fun create(): SkBBoxHierarchy
}

/**
 * Default [SkBBHFactory] — every invocation returns a fresh
 * [SkRTree]. Mirrors Skia's `SkRTreeFactory::operator()`.
 *
 * Exposed both as an `object` (callers using SAM-interface idioms
 * pass `SkRTreeFactory`) and as `SkRTreeFactory()` syntactic sugar
 * via the [invoke] companion fallback.
 */
public object SkRTreeFactory : SkBBHFactory {
    override fun create(): SkBBoxHierarchy = SkRTree()
}
