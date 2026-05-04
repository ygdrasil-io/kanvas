package org.skia.foundation.awt

import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import java.awt.Shape
import java.awt.geom.PathIterator

/**
 * **NOTE D'IMPLÉMENTATION** — Ce fichier expose la surface API Skia
 * (`SkPath` / `SkPathBuilder`) mais l'implémentation sous-jacente
 * repose sur **`java.awt.Shape` + `java.awt.geom.PathIterator`**, pas
 * sur le moteur de fontes natif Skia (FreeType + SkScalerContext).
 *
 * Conséquences :
 *  - Les outlines de glyphes sont produits par AWT, donc fidèles à la
 *    grille de hint AWT (1-2 ulps de drift vs. FreeType).
 *  - L'iteration verb-stream est en doubles (AWT) puis castée en floats
 *    (Skia). Acceptable pour notre tolérance pixel.
 *
 * Si on remplace AWT par FreeType+JNI, **seul ce fichier (et ses pairs
 * `Awt*.kt`) doit changer** — l'API publique reste figée.
 *
 * Ce fichier est **stateless** — un seul `object` avec une fonction
 * `shapeToSkPath`. Pas de mutation, pas de cache (le caching arrivera
 * en T5).
 */
internal object AwtPathConverter {

    /**
     * Convert an AWT [Shape] (typically a `GlyphVector.getOutline(x, y)`
     * result) into an [SkPath] under the supplied [fillType]. Walks
     * the path iterator's verb stream (`SEG_MOVETO` / `SEG_LINETO` /
     * `SEG_QUADTO` / `SEG_CUBICTO` / `SEG_CLOSE`) one verb at a time.
     *
     * Default fill type is `kWinding` (non-zero) — TrueType glyph
     * outlines all use the non-zero rule, and AWT's `Font.getOutline`
     * returns them with `WIND_NON_ZERO` set. We pin the fill rule
     * explicitly rather than reading [PathIterator.getWindingRule] so
     * downstream rasterisation behaviour is independent of AWT's
     * internal convention.
     */
    fun shapeToSkPath(
        shape: Shape,
        fillType: SkPathFillType = SkPathFillType.kWinding,
    ): SkPath {
        val builder = SkPathBuilder().setFillType(fillType)
        val it: PathIterator = shape.getPathIterator(null)
        // PathIterator.currentSegment(float[]) writes up to 6 floats
        // (3 control points × 2 coordinates) for a cubic. AWT's API
        // accepts both float[] and double[]; we use float[] because
        // SkPathBuilder is float-based and avoiding the double→float
        // round-trip removes one rounding step.
        val coords = FloatArray(6)
        while (!it.isDone) {
            when (it.currentSegment(coords)) {
                PathIterator.SEG_MOVETO -> builder.moveTo(coords[0], coords[1])
                PathIterator.SEG_LINETO -> builder.lineTo(coords[0], coords[1])
                PathIterator.SEG_QUADTO -> builder.quadTo(
                    coords[0], coords[1],
                    coords[2], coords[3],
                )
                PathIterator.SEG_CUBICTO -> builder.cubicTo(
                    coords[0], coords[1],
                    coords[2], coords[3],
                    coords[4], coords[5],
                )
                PathIterator.SEG_CLOSE -> builder.close()
            }
            it.next()
        }
        return builder.detach()
    }
}
