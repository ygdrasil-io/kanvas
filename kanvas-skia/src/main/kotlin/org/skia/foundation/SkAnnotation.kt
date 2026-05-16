package org.skia.foundation

import org.skia.core.SkCanvas
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Iso-aligned port of Skia's free-function annotation API in
 * [`include/core/SkAnnotation.h`](https://github.com/google/skia/blob/main/include/core/SkAnnotation.h).
 *
 * Annotations are sink-specific metadata that structured backends
 * (PDF, XPS) embed into the produced document — URLs, named
 * destinations, link targets. The raster pipeline cannot store
 * them in pixels and silently drops them.
 *
 * In this port the three free C++ functions are exposed as
 * top-level Kotlin functions in the `org.skia.foundation` package
 * with the same names (`SkAnnotateRectWithURL`,
 * `SkAnnotateNamedDestination`, `SkAnnotateLinkToDestination`).
 * For raster canvases they are **no-ops** — they delegate to
 * [SkCanvas.drawAnnotation] which the raster sink already ignores
 * (see `SkCanvas.drawAnnotation` KDoc). Future PDF / structured
 * backends override `drawAnnotation` and these helpers will start
 * emitting metadata automatically.
 *
 * Keys used by upstream (kept verbatim for forward compatibility
 * with PDF backends):
 *  * `"url"`        — link rectangle to URL
 *  * `"define-named-dest"` — named destination anchor
 *  * `"link-to-named-dest"` — link rectangle to named destination
 */

/** Key embedded by [SkAnnotateRectWithURL] — see PDF spec. */
public const val SK_ANNOTATION_URL_KEY: String = "url"

/** Key embedded by [SkAnnotateNamedDestination] — see PDF spec. */
public const val SK_ANNOTATION_DEFINE_NAMED_DEST_KEY: String = "define-named-dest"

/** Key embedded by [SkAnnotateLinkToDestination] — see PDF spec. */
public const val SK_ANNOTATION_LINK_TO_NAMED_DEST_KEY: String = "link-to-named-dest"

/**
 * Annotate the canvas by associating the specified URL with the
 * specified rectangle (in local coordinates, like drawRect).
 *
 * Raster backends ignore the annotation.
 */
public fun SkAnnotateRectWithURL(canvas: SkCanvas, rect: SkRect, urlData: SkData?) {
    canvas.drawAnnotation(rect, SK_ANNOTATION_URL_KEY, urlData?.toByteArray())
}

/**
 * Annotate the canvas by associating a name with the specified point.
 *
 * Raster backends ignore the annotation.
 */
public fun SkAnnotateNamedDestination(canvas: SkCanvas, point: SkPoint, nameData: SkData?) {
    val r = SkRect.MakeLTRB(point.fX, point.fY, point.fX, point.fY)
    canvas.drawAnnotation(r, SK_ANNOTATION_DEFINE_NAMED_DEST_KEY, nameData?.toByteArray())
}

/**
 * Annotate the canvas by making the specified rectangle link to a
 * named destination.
 *
 * Raster backends ignore the annotation.
 */
public fun SkAnnotateLinkToDestination(canvas: SkCanvas, rect: SkRect, nameData: SkData?) {
    canvas.drawAnnotation(rect, SK_ANNOTATION_LINK_TO_NAMED_DEST_KEY, nameData?.toByteArray())
}
