package org.skia.kanvas

import org.graphiks.kanvas.BlendMode
import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Image
import org.graphiks.kanvas.KanvasColorType
import org.graphiks.kanvas.KanvasFillType
import org.graphiks.kanvas.KanvasGlyphRun
import org.graphiks.kanvas.KanvasPoint
import org.graphiks.kanvas.KanvasTileMode
import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.Path
import org.graphiks.kanvas.RRect
import org.graphiks.kanvas.RRectCornerRadii
import org.graphiks.kanvas.Rect
import org.graphiks.kanvas.Shader
import org.graphiks.kanvas.TextBlob
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkRect
import org.skia.foundation.ShaderKind
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkRRect
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTileMode

fun SkRect.toKanvasRect(): Rect =
    Rect(left = left, top = top, right = right, bottom = bottom)

fun SkRRect.toKanvasRRect(): RRect {
    return RRect(
        rect = rect().toKanvasRect(),
        topLeft = RRectCornerRadii(
            radii(SkRRect.Corner.kUpperLeft_Corner).fX,
            radii(SkRRect.Corner.kUpperLeft_Corner).fY,
        ),
        topRight = RRectCornerRadii(
            radii(SkRRect.Corner.kUpperRight_Corner).fX,
            radii(SkRRect.Corner.kUpperRight_Corner).fY,
        ),
        bottomRight = RRectCornerRadii(
            radii(SkRRect.Corner.kLowerRight_Corner).fX,
            radii(SkRRect.Corner.kLowerRight_Corner).fY,
        ),
        bottomLeft = RRectCornerRadii(
            radii(SkRRect.Corner.kLowerLeft_Corner).fX,
            radii(SkRRect.Corner.kLowerLeft_Corner).fY,
        ),
    )
}

fun SkTextBlob.toKanvasTextBlob(): TextBlob {
    val glyphRuns = runs.map { run -> run.toKanvasGlyphRun() }
    return TextBlob(glyphRuns = glyphRuns)
}

internal fun SkTextBlob.Run.toKanvasGlyphRun(): KanvasGlyphRun {
    val glyphs = glyphIds.map { id -> (id and 0xFFFF).toUShort() }
    val positions = when (this) {
        is SkTextBlob.Run.HorizontalSpread -> {
            val pts = ArrayList<KanvasPoint>(glyphIds.size)
            var cx = x
            for (i in glyphIds.indices) {
                pts.add(KanvasPoint(cx, y))
                if (i + 1 < glyphIds.size) {
                    cx += font.getWidth(glyphIds[i])
                }
            }
            pts
        }
        is SkTextBlob.Run.HorizontalPositions -> {
            glyphIds.indices.map { i ->
                KanvasPoint(xs[i], constY)
            }
        }
        is SkTextBlob.Run.FullPositions -> {
            glyphIds.indices.map { i ->
                KanvasPoint(positions[2 * i], positions[2 * i + 1])
            }
        }
        is SkTextBlob.Run.RSXformPositions -> {
            glyphIds.indices.map { i ->
                KanvasPoint(xforms[i].fTx, xforms[i].fTy)
            }
        }
    }
    return KanvasGlyphRun(glyphs = glyphs, positions = positions)
}

fun SkBlendMode.toKanvasBlendMode(): BlendMode = when (this) {
    SkBlendMode.kClear -> BlendMode.CLEAR
    SkBlendMode.kSrc -> BlendMode.SRC
    SkBlendMode.kDst -> BlendMode.DST
    SkBlendMode.kSrcOver -> BlendMode.SRC_OVER
    SkBlendMode.kDstOver -> BlendMode.DST_OVER
    SkBlendMode.kSrcIn -> BlendMode.SRC_IN
    SkBlendMode.kDstIn -> BlendMode.DST_IN
    SkBlendMode.kSrcOut -> BlendMode.SRC_OUT
    SkBlendMode.kDstOut -> BlendMode.DST_OUT
    SkBlendMode.kSrcATop -> BlendMode.SRC_ATOP
    SkBlendMode.kDstATop -> BlendMode.DST_ATOP
    SkBlendMode.kXor -> BlendMode.XOR
    SkBlendMode.kPlus -> BlendMode.PLUS
    SkBlendMode.kModulate -> BlendMode.MODULATE
    SkBlendMode.kMultiply -> BlendMode.MULTIPLY
    SkBlendMode.kScreen -> BlendMode.SCREEN
    else -> BlendMode.SRC_OVER
}

fun org.skia.foundation.SkPaint.toKanvasPaint(): Paint {
    val c4f: SkColor4f = color4f
    return Paint(
        r = c4f.fR,
        g = c4f.fG,
        b = c4f.fB,
        a = c4f.fA,
        shader = shader?.toKanvasShader(),
        blendMode = blendMode.toKanvasBlendMode(),
        strokeWidth = strokeWidth,
        antiAlias = isAntiAlias,
    ).also { p ->
        p.strokeCap = when (strokeCap) {
            org.skia.foundation.SkPaint.Cap.kButt_Cap -> org.graphiks.kanvas.StrokeCap.BUTT
            org.skia.foundation.SkPaint.Cap.kRound_Cap -> org.graphiks.kanvas.StrokeCap.ROUND
            org.skia.foundation.SkPaint.Cap.kSquare_Cap -> org.graphiks.kanvas.StrokeCap.SQUARE
        }
        p.strokeJoin = when (strokeJoin) {
            org.skia.foundation.SkPaint.Join.kMiter_Join -> org.graphiks.kanvas.StrokeJoin.MITER
            org.skia.foundation.SkPaint.Join.kRound_Join -> org.graphiks.kanvas.StrokeJoin.ROUND
            org.skia.foundation.SkPaint.Join.kBevel_Join -> org.graphiks.kanvas.StrokeJoin.BEVEL
        }
        // Carry the geometry style so stroke-style (and stroke-and-fill) draws
        // REFUSE with `unsupported_stroke` downstream instead of being silently
        // filled. Real stroke rendering is dependency-gated (KGPU-M3-003).
        p.style = when (style) {
            org.skia.foundation.SkPaint.Style.kFill_Style -> org.graphiks.kanvas.PaintStyle.FILL
            org.skia.foundation.SkPaint.Style.kStroke_Style,
            org.skia.foundation.SkPaint.Style.kStrokeAndFill_Style,
            -> org.graphiks.kanvas.PaintStyle.STROKE
        }
    }
}

fun org.skia.foundation.SkShader.toKanvasShader(): Shader? = when (val kind = shaderKind) {
    is ShaderKind.Linear -> {
        val k = kind
        Shader.LinearGradient(
            start = KanvasPoint(k.p0.fX, k.p0.fY),
            end = KanvasPoint(k.p1.fX, k.p1.fY),
            stops = k.colors.map { c ->
                Triple(
                    SkColorGetR(c) / 255f,
                    SkColorGetG(c) / 255f,
                    SkColorGetB(c) / 255f,
                )
            },
            positions = if (k.positions.size == k.colors.size) k.positions.toList() else null,
            tileMode = k.tileMode.toKanvasTileMode(),
        )
    }
    is ShaderKind.Radial -> {
        val k = kind
        Shader.RadialGradient(
            center = KanvasPoint(k.center.fX, k.center.fY),
            radius = k.radius,
            stops = k.colors.map { c ->
                Triple(
                    SkColorGetR(c) / 255f,
                    SkColorGetG(c) / 255f,
                    SkColorGetB(c) / 255f,
                )
            },
            positions = if (k.positions.size == k.colors.size) k.positions.toList() else null,
            tileMode = k.tileMode.toKanvasTileMode(),
        )
    }
    is ShaderKind.Sweep -> {
        val k = kind
        Shader.SweepGradient(
            center = KanvasPoint(k.center.fX, k.center.fY),
            startAngle = k.startAngle,
            endAngle = k.endAngle,
            stops = k.colors.map { c ->
                Triple(
                    SkColorGetR(c) / 255f,
                    SkColorGetG(c) / 255f,
                    SkColorGetB(c) / 255f,
                )
            },
            positions = if (k.positions.size == k.colors.size) k.positions.toList() else null,
            tileMode = k.tileMode.toKanvasTileMode(),
        )
    }
    is ShaderKind.Bitmap -> {
        val k = kind
        Shader.Bitmap(
            image = k.image.toKanvasImage(),
            tileModeX = k.tileX.toKanvasTileMode(),
            tileModeY = k.tileY.toKanvasTileMode(),
        )
    }
    is ShaderKind.Unknown -> null
}

fun SkTileMode.toKanvasTileMode(): KanvasTileMode = when (this) {
    SkTileMode.kClamp -> KanvasTileMode.CLAMP
    SkTileMode.kRepeat -> KanvasTileMode.REPEAT
    SkTileMode.kMirror -> KanvasTileMode.MIRROR
    SkTileMode.kDecal -> KanvasTileMode.DECAL
}

fun SkPathFillType.toKanvasFillType(): KanvasFillType = when (this) {
    SkPathFillType.kWinding -> KanvasFillType.WINDING
    SkPathFillType.kEvenOdd -> KanvasFillType.EVEN_ODD
    SkPathFillType.kInverseWinding -> KanvasFillType.INVERSE_WINDING
    SkPathFillType.kInverseEvenOdd -> KanvasFillType.INVERSE_EVEN_ODD
}

fun SkPath.toKanvasPath(): Path {
    val path = Path(fillType = fillType.toKanvasFillType())
    val iter = SkPath.Iter(this, false)
    val pts = FloatArray(8)
    var verb = iter.next(pts)
    while (verb != SkPath.Verb.kDone) {
        when (verb) {
            SkPath.Verb.kMove -> path.moveTo(pts[0], pts[1])
            SkPath.Verb.kLine -> path.lineTo(pts[2], pts[3])
            SkPath.Verb.kQuad -> path.quadTo(pts[2], pts[3], pts[4], pts[5])
            SkPath.Verb.kConic -> {
                val cx = pts[2]; val cy = pts[3]
                val ex = pts[4]; val ey = pts[5]
                val stepCount = 8
                var px = pts[0]; var py = pts[1]
                val w = iter.conicWeight()
                for (k in 1..stepCount) {
                    val t = k.toFloat() / stepCount
                    val u = 1f - t
                    val numW = u * u + 2f * u * t * w + t * t
                    val xc = (u * u * px + 2f * u * t * w * cx + t * t * ex) / numW
                    val yc = (u * u * py + 2f * u * t * w * cy + t * t * ey) / numW
                    if (k == 1) path.quadTo(cx, cy, xc, yc) else path.lineTo(xc, yc)
                    px = xc; py = yc
                }
            }
            SkPath.Verb.kCubic -> path.cubicTo(pts[2], pts[3], pts[4], pts[5], pts[6], pts[7])
            SkPath.Verb.kClose -> path.close()
            SkPath.Verb.kDone -> error("kDone is iterator-only, never stored")
        }
        verb = iter.next(pts)
    }
    return path
}

fun org.skia.foundation.SkImage.toKanvasImage(): Image = Image(
    width = width,
    height = height,
    colorType = when (colorType) {
        org.skia.foundation.SkColorType.kRGBA_8888 -> KanvasColorType.RGBA_8888
        org.skia.foundation.SkColorType.kBGRA_8888 -> KanvasColorType.BGRA_8888
        org.skia.foundation.SkColorType.kAlpha_8 -> KanvasColorType.ALPHA_8
        org.skia.foundation.SkColorType.kGray_8 -> KanvasColorType.GRAY_8
        else -> KanvasColorType.RGBA_8888
    },
    sourceId = "skia-image-${System.identityHashCode(this)}",
)

private const val BRIDGE_DIAGNOSTIC_PREFIX = "[kanvas-skia-bridge]"

internal fun emitBridgeDiagnostic(code: String, message: String) {
    System.err.println("$BRIDGE_DIAGNOSTIC_PREFIX $code: $message")
}

internal fun emitUnsupportedBridgeDiagnostic(feature: String) {
    emitBridgeDiagnostic(
        code = "unsupported-skia-bridge-feature",
        message = "Unsupported SkCanvas bridge feature: $feature. No silent fallback.",
    )
}

class KanvasSkiaBridge(private val kanvasCanvas: Canvas) {
    fun drawRect(rect: SkRect, paint: org.skia.foundation.SkPaint) {
        kanvasCanvas.drawRect(rect.toKanvasRect(), paint.toKanvasPaint())
    }

    fun drawRRect(rrect: SkRRect, paint: org.skia.foundation.SkPaint) {
        kanvasCanvas.drawRRect(rrect.toKanvasRRect(), paint.toKanvasPaint())
    }

    fun drawPath(path: SkPath, paint: org.skia.foundation.SkPaint) {
        kanvasCanvas.drawPath(path.toKanvasPath(), paint.toKanvasPaint())
    }

    fun drawImage(
        image: org.skia.foundation.SkImage,
        rect: SkRect,
        paint: org.skia.foundation.SkPaint?,
    ) {
        kanvasCanvas.drawImage(
            image = image.toKanvasImage(),
            rect = rect.toKanvasRect(),
            paint = paint?.toKanvasPaint(),
        )
    }

    fun drawTextBlob(blob: SkTextBlob, x: Float, y: Float, paint: org.skia.foundation.SkPaint) {
        kanvasCanvas.drawTextBlob(
            blob = blob.toKanvasTextBlob(),
            x = x,
            y = y,
            paint = paint.toKanvasPaint(),
        )
    }

    fun unsupported(feature: String) {
        emitUnsupportedBridgeDiagnostic(feature)
    }

    val canvas: Canvas get() = kanvasCanvas
}
