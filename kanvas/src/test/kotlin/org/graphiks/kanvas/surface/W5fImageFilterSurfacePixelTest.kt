package org.graphiks.kanvas.surface

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalUnsignedTypes::class)
class W5fImageFilterSurfacePixelTest {
    @ParameterizedTest(name="decoded image filter: {0}")
    @ValueSource(strings=["Matrix","Blend","Compose","Table","Lighting","SRGBToLinear","LinearToSRGB",
        "HSLAMatrix","Lerp","HighContrast","Luma","Overdraw"])
    fun allKindsImageAlphaMutationAndFinalBlend(name: String) {
        val matrix = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            1f,0f,0f,0f,.125f, 0f,.5f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,.5f,.125f)))
        val filter = when(name) {
            "Matrix" -> matrix
            "Blend" -> ColorFilter.Blend(ColorARGB.of(127,255,0,0),BlendMode.SRC_OVER)
            "Compose" -> ColorFilter.Compose(matrix,ColorFilter.LinearToSRGB)
            "Table" -> ColorFilter.Table(UByteArray(256) { (255-it).toUByte() }.apply { this[255]=64u })
            "Lighting" -> ColorFilter.Lighting(ColorARGB.of(255,128,255,0),ColorARGB.of(255,64,0,255))
            "SRGBToLinear" -> ColorFilter.SRGBToLinear
            "LinearToSRGB" -> ColorFilter.LinearToSRGB
            "HSLAMatrix" -> ColorFilter.HSLAMatrix(floatArrayOf(1f,0f,0f,0f,.25f,
                0f,.5f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,.5f,0f))
            "Lerp" -> ColorFilter.Lerp(.25f,matrix,ColorFilter.LinearToSRGB)
            "HighContrast" -> ColorFilter.HighContrast
            "Luma" -> ColorFilter.Luma
            else -> ColorFilter.Overdraw
        }
        for (alpha in listOf(0,128,255)) renderFilteredImage(filter,byteArrayOf(if(name=="Lighting") -128 else 0,-1,0,alpha.toByte()),255)
        renderFilteredImage(filter)
        exerciseCapturedKind(filter)
    }

    @ParameterizedTest(name="decoded image Blend: {0}")
    @EnumSource(BlendMode::class)
    fun everyBlendModeOnNonunitImage(mode: BlendMode) {
        // Interior operands keep dodge/burn denominators and soft-light roots
        // inside their independently bounded domains; endpoint trials are
        // recorded separately, never treated as production REDs.
        val interior = mode in setOf(BlendMode.COLOR_DODGE,BlendMode.COLOR_BURN,BlendMode.SOFT_LIGHT)
        val filter = ColorFilter.Blend(if (interior) ColorARGB.of(127,96,144,192) else ColorARGB.of(127,255,0,0),mode)
        for (alpha in listOf(0,128,255)) renderFilteredImage(filter,if (interior) byteArrayOf(-128,96,64,alpha.toByte())
            else byteArrayOf(0,-1,0,alpha.toByte()),255)
        renderFilteredImage(filter,if(interior) byteArrayOf(-128,96,64,-128) else byteArrayOf(0,-1,0,-128))
        exerciseCapturedKind(filter)
    }

    @ParameterizedTest(name="A8 actual filtered child/domain/mask order: {0}")
    @EnumSource(ColorSpaceInterpolation::class)
    fun maskChildDomainsAndFilterOrder(domain: ColorSpaceInterpolation) {
        val image = Image.fromPixels(1,1,byteArrayOf(127),ColorType.ALPHA_8,alphaType=AlphaType.UNPREMUL)
        val inner = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(.5f,0f,0f,0f,.125f,
            0f,.75f,0f,0f,0f, 0f,0f,.5f,0f,0f, 0f,0f,0f,.75f,.125f)))
        val leaf = Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),listOf(
            GradientStop(0f,ColorARGB.Black),GradientStop(1f,ColorARGB.White)),interpolation=domain)
        val paint = Paint(color=ColorARGB.of(127,255,0,0),shader=Shader.WithColorFilter(leaf,inner),
            colorFilter=translatedFilter(),blendMode=BlendMode.SRC,antiAlias=false)
        fun expected(order: W5fColorCpuOracle.ImageOrder) = W5fColorCpuOracle.expectedImagePixel(image,
            SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,finalBlend=BlendMode.SRC,order=order)
        val wanted = expected(W5fColorCpuOracle.ImageOrder.Correct)
        disjoint(wanted,expected(W5fColorCpuOracle.ImageOrder.FilterBeforeMask))
        val dropped = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),
            paint.copy(shader=null),finalBlend=BlendMode.SRC)
        disjoint(wanted,dropped)
        for (origin in listOf("Image","Nine","Lattice","Atlas")) {
            val surface = Surface(1,1)
            surface.canvas { when(origin) {
                "Image" -> drawImage(image,unitRect(),SamplingOptions.NEAREST,paint)
                "Nine" -> drawImageNine(image,unitRect(),unitRect(),paint)
                "Lattice" -> drawImageLattice(image,Lattice(emptyList(),emptyList()),unitRect(),paint,SamplingOptions.NEAREST)
                else -> drawAtlas(image,listOf(Matrix3x3F32()),listOf(unitRect()),paint=paint)
            } }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
        }
    }

    @ParameterizedTest(name="decoded padded format/alpha: {0}")
    @ValueSource(strings=["RGBA_8888:PREMUL","RGBA_8888:UNPREMUL","RGBA_8888:OPAQUE",
        "BGRA_8888:PREMUL","BGRA_8888:UNPREMUL","BGRA_8888:OPAQUE",
        "SRGBA_8888:PREMUL","SRGBA_8888:UNPREMUL","SRGBA_8888:OPAQUE"])
    fun paddedDecodedFormatAndAlphaFilter(name: String) {
        val (formatName,alphaName) = name.split(':')
        val format = ColorType.valueOf(formatName); val alpha = AlphaType.valueOf(alphaName)
        val logical = if (alpha == AlphaType.PREMUL) byteArrayOf(64,96,32,-128) else byteArrayOf(-128,-64,64,-128)
        if (format == ColorType.BGRA_8888) { val r=logical[0]; logical[0]=logical[2]; logical[2]=r }
        val other = logical.copyOf().apply { this[0]=0; this[1]=0; this[2]=0 }
        val padding = byteArrayOf(-1,0,-1,-1,0,-1,0,-1)
        val bytes = logical+padding+other+padding
        val image = Image.fromPixels(1,2,bytes,format,alphaType=alpha,rowBytesI32=12)
        val paint = Paint(color=ColorARGB.of(127,255,255,255),colorFilter=translatedFilter(),blendMode=BlendMode.SRC,antiAlias=false)
        val expected = (0..1).map { W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,
            Point2F32(.5f,it+.5f),paint,finalBlend=BlendMode.SRC) }
        expected.forEach(W5fSurfacePixelFixtures::requireBounded)
        disjoint(expected[0],expected[1])
        val surface = Surface(1,2)
        surface.canvas { drawImage(image,RectF32.ofLTRB(0f,0f,1f,2f),SamplingOptions.NEAREST,paint) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),expected) }
    }

    @ParameterizedTest(name="actual sampled decoded taps/filter: {0}")
    @ValueSource(strings=["Nearest","Linear","Cubic"])
    fun originalSamplerPrecedesExternalFilter(name: String) {
        val sampling = when(name) { "Nearest" -> SamplingOptions.NEAREST; "Linear" -> SamplingOptions.LINEAR
            else -> SamplingOptions.Cubic(0f,.5f) }
        val image = Image.fromPixels(2,2,byteArrayOf(-1,0,0,-128, 0,-1,0,-128,
            0,0,-1,-128, -1,-1,-1,-128),alphaType=AlphaType.UNPREMUL)
        val paint = Paint(color=ColorARGB.of(127,255,255,255),colorFilter=translatedFilter(),blendMode=BlendMode.SRC,antiAlias=false)
        val expected = W5fColorCpuOracle.expectedImagePixel(image,sampling,Point2F32(1f,1f),paint,finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(expected)
        if (sampling != SamplingOptions.NEAREST) disjoint(expected,W5fColorCpuOracle.expectedImagePixel(image,
            SamplingOptions.NEAREST,Point2F32(1f,1f),paint,finalBlend=BlendMode.SRC))
        val surface = Surface(1,1)
        surface.canvas { drawImage(image,unitRect(),sampling,paint) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }

    private fun translatedFilter() = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
        .75f,0f,0f,0f,.0625f, 0f,.5f,0f,0f,.125f, 0f,0f,.25f,0f,.125f, 0f,0f,0f,.5f,.25f)))

    @ParameterizedTest(name="Atlas entry then paint then external once then final blend: {0}")
    @EnumSource(value=BlendMode::class,names=["SRC_OVER","SRC_IN","SCREEN"])
    fun atlasExternalFilterKeepsEveryOrderBoundary(mode: BlendMode) {
        val image = Image.fromPixels(1,1,byteArrayOf(0,-1,0,-128),alphaType=AlphaType.UNPREMUL)
        val entry = ColorARGB.of(192,255,0,0)
        val background = ColorARGB.of(127,72,0,0)
        val finalMode = if (mode == BlendMode.SRC_IN) BlendMode.SRC_IN else BlendMode.DIFFERENCE
        val paint = Paint(color=ColorARGB.of(127,255,255,255),colorFilter=translatedFilter(),
            blendMode=finalMode,antiAlias=false)
        fun expected(order: W5fColorCpuOracle.ImageOrder=W5fColorCpuOracle.ImageOrder.Correct,
            final: BlendMode=finalMode) = W5fColorCpuOracle.expectedImagePixel(image,
            SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,entry,mode,background,final,order=order,destinationBlend=BlendMode.SRC)
        val wanted = expected()
        for (wrong in listOf(W5fColorCpuOracle.ImageOrder.FilterBeforeAtlasEntry,
            W5fColorCpuOracle.ImageOrder.FilterBeforeAtlasPaint,W5fColorCpuOracle.ImageOrder.FilterTwice))
            disjoint(wanted,expected(wrong))
        disjoint(wanted,expected(final=BlendMode.SRC))
        val surface = Surface(1,1)
        surface.canvas {
            drawRect(unitRect(),Paint(color=background,blendMode=BlendMode.SRC,antiAlias=false))
            drawAtlas(image,listOf(Matrix3x3F32()),listOf(unitRect()),colors=listOf(entry),blendMode=mode,paint=paint)
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @Test fun imageInternalOpacityExternalAndClampRemainNoncommutative() {
        val image = Image.fromPixels(1,1,byteArrayOf(0,-1,0,-128),alphaType=AlphaType.UNPREMUL)
        val inner = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(1f,0f,0f,0f,1.25f,
            0f,1f,0f,0f,-.25f, 0f,0f,1f,0f,0f, 0f,0f,0f,.5f,.25f)))
        val outer = translatedFilter()
        val leaf = Shader.Image(image)
        val paint = Paint(color=ColorARGB.of(127,255,255,255),
            shader=Shader.Opacity(Shader.WithColorFilter(leaf,inner),.5f),colorFilter=outer,
            blendMode=BlendMode.SRC,antiAlias=false)
        fun expected(p: Paint) = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,
            Point2F32(.5f,.5f),p,finalBlend=BlendMode.SRC)
        val wanted = expected(paint)
        disjoint(wanted,expected(paint.copy(shader=Shader.WithColorFilter(Shader.Opacity(leaf,.5f),inner))))
        disjoint(wanted,expected(paint.copy(shader=Shader.Opacity(Shader.WithColorFilter(leaf,outer),.5f),colorFilter=inner)))
        val surface = Surface(1,1)
        surface.canvas { drawRect(unitRect(),paint) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @Test fun hueTransparentDestinationUsesActualGuard() = renderFilteredImage(
        ColorFilter.Blend(ColorARGB.of(127,255,0,0),BlendMode.HUE),byteArrayOf(0,-1,0,0))

    @Test fun atlasTransparentDecodedDestinationUsesSharedHueGuard() {
        val image = Image.fromPixels(1,1,byteArrayOf(0,-1,0,0),alphaType=AlphaType.UNPREMUL)
        val entry = ColorARGB.of(127,255,0,0)
        val paint = Paint(color=ColorARGB.of(127,255,255,255),colorFilter=translatedFilter(),blendMode=BlendMode.SRC,antiAlias=false)
        val wanted = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),
            paint,entry,BlendMode.HUE,finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        val surface = Surface(1,1)
        surface.canvas { drawAtlas(image,listOf(Matrix3x3F32()),listOf(unitRect()),colors=listOf(entry),blendMode=BlendMode.HUE,paint=paint) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @Test fun filteredAtlasSignedSoftLightRefusesPreciselyThenRendersHealthyControl() {
        // The existing P3-red/SoftLight negative has an eager sqrt of signed
        // destination channels. An external filter selects V4 but cannot make
        // that already-executed source operation finite.
        val signed = Image.fromPixels(1,1,byteArrayOf(-1,0,0,-1),alphaType=AlphaType.PREMUL,colorSpace=ColorSpace.DISPLAY_P3)
        val control = Image.fromPixels(1,1,byteArrayOf(-64,-64,-64,-1),alphaType=AlphaType.UNPREMUL)
        val paint = Paint(color=ColorARGB.of(127,255,255,255),colorFilter=translatedFilter(),blendMode=BlendMode.SRC,antiAlias=false)
        fun expected(p: Paint) = W5fColorCpuOracle.expectedImagePixel(control,SamplingOptions.NEAREST,
            Point2F32(.5f,.5f),p,ColorARGB.White,BlendMode.SOFT_LIGHT,finalBlend=BlendMode.SRC)
        val wanted = expected(paint)
        disjoint(wanted,expected(paint.copy(colorFilter=null)))
        val failing = Surface(1,1)
        failing.canvas { drawAtlas(signed,listOf(Matrix3x3F32()),listOf(unitRect()),
            colors=listOf(ColorARGB.White),blendMode=BlendMode.SOFT_LIGHT,paint=paint) }
        val failure = assertFailsWith<IllegalStateException> { failing.render() }
        assertEquals("unsupported.material.image.numeric-domain-unbounded",failure.message.orEmpty().substringBefore(':'))
        val healthy = Surface(1,1)
        healthy.canvas { drawAtlas(control,listOf(Matrix3x3F32()),listOf(unitRect()),
            colors=listOf(ColorARGB.White),blendMode=BlendMode.SOFT_LIGHT,paint=paint) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(wanted)) }
    }

    @Test fun filteredImageFinalHueUsesTransparentDestinationGuard() {
        val image = Image.fromPixels(1,1,byteArrayOf(0,-1,0,-128),alphaType=AlphaType.UNPREMUL)
        val paint = Paint(color=ColorARGB.of(127,255,255,255),colorFilter=translatedFilter(),blendMode=BlendMode.HUE,antiAlias=false)
        val wanted = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,finalBlend=BlendMode.HUE)
        disjoint(wanted,W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),
            paint.copy(colorFilter=null),finalBlend=BlendMode.HUE))
        val surface = Surface(1,1)
        surface.canvas { drawImage(image,unitRect(),SamplingOptions.NEAREST,paint) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @Test fun atlasOrderedEntriesRetainCapturedEntryColors() {
        val image = Image.fromPixels(1,1,byteArrayOf(0,-1,0,-128),alphaType=AlphaType.UNPREMUL)
        val entries = mutableListOf(ColorARGB.of(192,255,0,0),ColorARGB.of(192,0,0,255))
        val paint = Paint(color=ColorARGB.of(127,255,255,255),colorFilter=translatedFilter(),blendMode=BlendMode.SRC,antiAlias=false)
        fun expected(entry: ColorARGB) = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,
            Point2F32(.5f,.5f),paint,entry,BlendMode.SRC_OVER,finalBlend=BlendMode.SRC)
        val wanted = expected(entries.last())
        disjoint(wanted,expected(entries.first()))
        val surface = Surface(1,1)
        surface.canvas { drawAtlas(image,List(2) { Matrix3x3F32() },List(2) { unitRect() },
            colors=entries,blendMode=BlendMode.SRC_OVER,paint=paint) }
        entries.reverse()
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @ParameterizedTest(name="A8 Atlas entry/paint/external order with child domain: {0}")
    @EnumSource(ColorSpaceInterpolation::class)
    fun maskAtlasChildEntryAndPaintOrder(domain: ColorSpaceInterpolation) {
        val image = Image.fromPixels(1,1,byteArrayOf(127),ColorType.ALPHA_8,alphaType=AlphaType.UNPREMUL)
        val child = Shader.WithColorFilter(Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
            listOf(GradientStop(0f,ColorARGB.Black),GradientStop(1f,ColorARGB.White)),interpolation=domain),translatedFilter())
        val entry = ColorARGB.of(127,255,0,0)
        val paint = Paint(color=ColorARGB.of(127,0,0,255),shader=child,colorFilter=translatedFilter(),blendMode=BlendMode.SRC,antiAlias=false)
        fun expected(order: W5fColorCpuOracle.ImageOrder=W5fColorCpuOracle.ImageOrder.Correct) =
            W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,
                entry,BlendMode.SRC_OVER,finalBlend=BlendMode.SRC,order=order)
        val wanted = expected()
        for (wrong in listOf(W5fColorCpuOracle.ImageOrder.FilterBeforeMask,W5fColorCpuOracle.ImageOrder.FilterBeforeAtlasEntry,
            W5fColorCpuOracle.ImageOrder.FilterBeforeAtlasPaint,W5fColorCpuOracle.ImageOrder.FilterTwice)) disjoint(wanted,expected(wrong))
        val surface = Surface(1,1)
        surface.canvas { drawAtlas(image,listOf(Matrix3x3F32()),listOf(unitRect()),colors=listOf(entry),blendMode=BlendMode.SRC_OVER,paint=paint) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @Test fun latticeFixedColorPaintAlphaAppliesOnceBeforeExternal() {
        val image = Image.fromPixels(1,1,byteArrayOf(0,0,-1,-128),alphaType=AlphaType.UNPREMUL)
        val paint = Paint(color=ColorARGB.of(127,0,255,0),colorFilter=translatedFilter(),blendMode=BlendMode.SRC,antiAlias=false)
        val cell = ColorARGB.of(192,255,0,0)
        val wanted = W5fColorCpuOracle.expectedShaderSource(cell,1f,paint.color.alphaNormalized,null,
            paint.colorFilter,finalBlend=BlendMode.SRC)
        disjoint(wanted,W5fColorCpuOracle.expectedShaderSource(cell,paint.color.alphaNormalized,
            paint.color.alphaNormalized,null,paint.colorFilter,finalBlend=BlendMode.SRC))
        disjoint(wanted,W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,finalBlend=BlendMode.SRC))
        val surface = Surface(1,1)
        surface.canvas { drawImageLattice(image,Lattice(emptyList(),emptyList(),colors=listOf(cell),
            flags=listOf(LatticeFlags.FIXED_COLOR)),unitRect(),paint,SamplingOptions.NEAREST) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @Test fun a8ImageShaderOriginsKeepActualSolidAndMaskOrder() {
        for (alpha in listOf(0,127,255)) {
            val image = Image.fromPixels(1,1,byteArrayOf(alpha.toByte()),ColorType.ALPHA_8,alphaType=AlphaType.UNPREMUL)
            val paint = Paint(color=ColorARGB.of(127,0,0,255),colorFilter=translatedFilter(),blendMode=BlendMode.SRC,antiAlias=false)
            val wanted = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,finalBlend=BlendMode.SRC)
            W5fSurfacePixelFixtures.requireBounded(wanted)
            if(alpha != 255) disjoint(wanted,W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,
                Point2F32(.5f,.5f),paint,finalBlend=BlendMode.SRC,order=W5fColorCpuOracle.ImageOrder.FilterBeforeMask))
            for (origin in listOf("Rect","DirectPath","StencilPath")) {
                val surface = Surface(1,1)
                surface.canvas {
                    val actual = paint.copy(shader=Shader.Image(image))
                    if(origin=="Rect") drawRect(unitRect(),actual) else drawPath(Path().apply {
                        addRect(unitRect()); if(origin=="StencilPath") addRect(RectF32.ofLTRB(.1f,.1f,.2f,.2f))
                    },actual)
                }
                repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
            }
        }
    }

    @Test fun typedTranslucentAttachmentIsDecodedBeforeFiltering() {
        val color = ColorARGB.of(128,255,0,0)
        val sourceExpected = W5fColorCpuOracle.expectedPaintSource(color,ColorFilter.Matrix(ColorMatrixF32.ofIdentity()),finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(sourceExpected)
        val source = Surface(1,1)
        source.canvas { drawRect(unitRect(),Paint(color=color,blendMode=BlendMode.SRC,antiAlias=false)) }
        val rendered = source.render()
        W5fSurfacePixelFixtures.assertNativePixels(rendered,listOf(sourceExpected))
        val image = rendered.toImage()
        val paint = Paint(color=ColorARGB.of(127,255,255,255),colorFilter=translatedFilter(),blendMode=BlendMode.SRC,antiAlias=false)
        val wanted = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,finalBlend=BlendMode.SRC)
        disjoint(wanted,W5fColorCpuOracle.expectedImagePixel(image.copy(
            premultiplication=org.graphiks.kanvas.render.ir.ImagePremultiplicationV1.SOURCE_SPACE),SamplingOptions.NEAREST,
            Point2F32(.5f,.5f),paint,finalBlend=BlendMode.SRC))
        val surface = Surface(1,1)
        surface.canvas { drawImage(image,unitRect(),SamplingOptions.NEAREST,paint) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @ParameterizedTest(name="original decoded colour space before filter: {0}")
    @ValueSource(strings=["SRGB","LINEAR_SRGB","DISPLAY_P3"])
    fun originalDecodedColorSpacesRetainSignedSource(name: String) {
        val space = when(name) { "SRGB" -> ColorSpace.SRGB; "LINEAR_SRGB" -> ColorSpace.LINEAR_SRGB; else -> ColorSpace.DISPLAY_P3 }
        val image = Image.fromPixels(1,1,byteArrayOf(0,-64,64,-128),colorSpace=space,alphaType=AlphaType.UNPREMUL)
        val paint = Paint(color=ColorARGB.of(127,255,255,255),colorFilter=translatedFilter(),blendMode=BlendMode.SRC,antiAlias=false)
        val wanted = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        if(name != "SRGB") disjoint(wanted,W5fColorCpuOracle.expectedImagePixel(image.reinterpretColorSpace(ColorSpace.SRGB),
            SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,finalBlend=BlendMode.SRC))
        val surface = Surface(1,1)
        surface.canvas { drawImage(image,unitRect(),SamplingOptions.NEAREST,paint) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @Test fun tableHalfwaySelectionAndLerpEndpointsRemainActualImageFilters() {
        val image = Image.fromPixels(1,1,byteArrayOf(0,-1,0,-1),alphaType=AlphaType.UNPREMUL)
        val centre = 128.5f/255f
        val table = ColorFilter.Table(UByteArray(256) { it.toUByte() }.apply { this[0]=64u })
        for (value in listOf(Math.nextDown(centre),centre,Math.nextUp(centre))) {
            val input = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(0f,0f,0f,0f,value,
                0f,0f,0f,0f,0f, 0f,0f,0f,0f,0f, 0f,0f,0f,0f,1f)))
            val paint = Paint(color=ColorARGB.of(128,255,255,255),shader=Shader.WithColorFilter(Shader.Image(image),input),colorFilter=table,
                blendMode=BlendMode.SRC,antiAlias=false)
            val wanted = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,finalBlend=BlendMode.SRC)
            W5fSurfacePixelFixtures.requireBounded(wanted)
            val surface = Surface(1,1)
            surface.canvas { drawRect(unitRect(),paint) }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
        }
        for (t in listOf(0f,.5f,1f)) for(alpha in listOf(0,128,255))
            renderFilteredImage(ColorFilter.Lerp(t,translatedFilter(),ColorFilter.LinearToSRGB),byteArrayOf(0,-1,0,alpha.toByte()),255)
    }

    @Test fun mixedFilteredRectPathImageMaskAtlasRepeatAndNextSurfaceRemainHealthy() {
        val image = Image.fromPixels(1,1,byteArrayOf(0,-1,0,-128),alphaType=AlphaType.UNPREMUL)
        val mask = Image.fromPixels(1,1,byteArrayOf(127),ColorType.ALPHA_8,alphaType=AlphaType.UNPREMUL)
        val filters = (1..6).map { index -> ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            .5f,0f,0f,0f,index/32f, 0f,.5f,0f,0f,index/64f, 0f,0f,.5f,0f,0f, 0f,0f,0f,.5f,.25f))) }
        fun paint(index: Int) = Paint(color=ColorARGB.of(127,255,255,255),colorFilter=filters[index],blendMode=BlendMode.SRC,antiAlias=false)
        val stops = listOf(GradientStop(0f,ColorARGB.Black),GradientStop(1f,ColorARGB.White))
        val pathGradient = Shader.LinearGradient(Point2F32(1f,0f),Point2F32(2f,0f),stops,interpolation=ColorSpaceInterpolation.OKLAB)
        val maskGradient = Shader.LinearGradient(Point2F32(3f,0f),Point2F32(4f,0f),stops,interpolation=ColorSpaceInterpolation.LINEAR)
        val maskPaint = paint(3).copy(shader=Shader.WithColorFilter(maskGradient,translatedFilter()))
        val atlasEntry = ColorARGB.of(192,255,0,0)
        val maskAtlasEntry = ColorARGB.of(127,0,0,255)
        val maskAtlasPaint = paint(5).copy(shader=Shader.WithColorFilter(Shader.LinearGradient(
            Point2F32(0f,0f),Point2F32(1f,0f),stops,interpolation=ColorSpaceInterpolation.HSL),translatedFilter()))
        val expected = listOf(
            W5fColorCpuOracle.expectedPaintSource(ColorARGB.Red,filters[0],finalBlend=BlendMode.SRC),
            W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.OKLAB,ColorARGB.Black,ColorARGB.White,.5f,
                filters[1],finalBlend=BlendMode.SRC),
            W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint(2),finalBlend=BlendMode.SRC),
            W5fColorCpuOracle.expectedImagePixel(mask,SamplingOptions.NEAREST,Point2F32(.5f,.5f),maskPaint,
                childPointF32=Point2F32(3.5f,.5f),finalBlend=BlendMode.SRC),
            W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint(4),
                atlasEntry,BlendMode.SRC_OVER,finalBlend=BlendMode.SRC),
            W5fColorCpuOracle.expectedImagePixel(mask,SamplingOptions.NEAREST,Point2F32(.5f,.5f),maskAtlasPaint,
                maskAtlasEntry,BlendMode.SRC_OVER,finalBlend=BlendMode.SRC))
        expected.forEach(W5fSurfacePixelFixtures::requireBounded)
        for (left in expected.indices) for(right in left+1 until expected.size) disjoint(expected[left],expected[right])
        val healthyExpected = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),
            paint(0),finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(healthyExpected)
        val surface = Surface(6,1)
        surface.canvas {
            drawRect(unitRect(),paint(0).copy(color=ColorARGB.Red))
            drawPath(Path().apply { addRect(RectF32.ofLTRB(1f,0f,2f,1f)) },paint(1).copy(color=ColorARGB.White,shader=pathGradient))
            drawImage(image,RectF32.ofLTRB(2f,0f,3f,1f),SamplingOptions.NEAREST,paint(2))
            drawImage(mask,RectF32.ofLTRB(3f,0f,4f,1f),SamplingOptions.NEAREST,maskPaint)
            drawAtlas(image,listOf(Matrix3x3F32.translation(4f,0f)),listOf(unitRect()),colors=listOf(atlasEntry),
                blendMode=BlendMode.SRC_OVER,paint=paint(4))
            drawAtlas(mask,listOf(Matrix3x3F32.translation(5f,0f)),listOf(unitRect()),colors=listOf(maskAtlasEntry),
                blendMode=BlendMode.SRC_OVER,paint=maskAtlasPaint)
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),expected) }
        // A separate public Surface in this same running test/runtime must remain usable.
        val healthy = Surface(1,1)
        healthy.canvas { drawImage(image,unitRect(),SamplingOptions.NEAREST,paint(0)) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(healthyExpected)) }
    }

    @ParameterizedTest(name="captured decoded image and caller filter arrays through Picture: {0}")
    @ValueSource(strings=["Matrix","Table","HSLAMatrix"])
    fun pictureCapturesCallerPixelsAndFilterValues(name: String) {
        val pixels = byteArrayOf(0,-1,0,-128)
        val image = Image.fromPixels(1,1,pixels,alphaType=AlphaType.UNPREMUL)
        val values = floatArrayOf(1f,0f,0f,0f,.125f, 0f,.5f,0f,0f,0f,
            0f,0f,1f,0f,0f, 0f,0f,0f,.5f,.25f)
        val table = UByteArray(256) { (255-it).toUByte() }
        fun filter(): ColorFilter = when(name) {
            "Matrix" -> ColorFilter.Matrix(ColorMatrixF32.of(values))
            "Table" -> ColorFilter.Table(table)
            else -> ColorFilter.HSLAMatrix(values)
        }
        val paint = Paint(color=ColorARGB.of(127,255,255,255),colorFilter=filter(),blendMode=BlendMode.SRC,antiAlias=false)
        val wanted = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,
            Point2F32(.5f,.5f),paint,finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        val changedImage = Image.fromPixels(1,1,byteArrayOf(-1,0,0,-1),alphaType=AlphaType.UNPREMUL)
        disjoint(wanted,W5fColorCpuOracle.expectedImagePixel(changedImage,SamplingOptions.NEAREST,
            Point2F32(.5f,.5f),paint,finalBlend=BlendMode.SRC))
        val changedFilter = when(name) {
            "Matrix" -> ColorFilter.Matrix(ColorMatrixF32.of(FloatArray(20)))
            "Table" -> ColorFilter.Table(UByteArray(256))
            else -> ColorFilter.HSLAMatrix(FloatArray(20))
        }
        disjoint(wanted,W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,
            Point2F32(.5f,.5f),paint.copy(colorFilter=changedFilter),finalBlend=BlendMode.SRC))
        val recorder = PictureRecorder()
        recorder.beginRecording(unitRect()).drawImage(image,unitRect(),SamplingOptions.NEAREST,paint)
        val captured = recorder.finishRecordingAsPicture()
        pixels.fill(0); values.fill(0f); table.fill(0u)
        val replay = requireNotNull(Picture.fromByteArray(captured.toByteArray()))
        val surface = Surface(1,1)
        surface.canvas { replay.playback(this) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }
    private fun unitRect() = RectF32.ofLTRB(0f,0f,1f,1f)
    private fun disjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult,b: WgslFloatEnvelopeV1Oracle.DrawResult) {
        W5fSurfacePixelFixtures.requireBounded(a); W5fSurfacePixelFixtures.requireBounded(b)
        a as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded; b as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        assertTrue(a.channels.indices.any { a.channels[it].intersect(b.channels[it]).isEmpty() },
            "Counterfactual not distinct: ${a.channels}/${b.channels}")
    }

    private fun exerciseCapturedKind(kind: ColorFilter) {
        val background = ColorARGB.of(127,72,0,0)
        for (alpha in listOf(0,128,255)) {
            val bytes = byteArrayOf(-1,0,0,alpha.toByte())
            val image = Image.fromPixels(1,1,bytes,alphaType=AlphaType.UNPREMUL)
            // The preparation requires clamping but preserves actual source alpha.
            val preparation = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(.25f,0f,0f,0f,0f,
                0f,0f,0f,0f,1.125f, 0f,0f,0f,0f,.0625f, 0f,0f,0f,1f,0f)))
            val child = ColorFilter.Compose(kind,preparation)
            val marker = ColorMatrixF32.of(floatArrayOf(.25f,0f,0f,0f,.125f, 0f,.25f,0f,0f,0f,
                0f,0f,.25f,0f,.125f, 0f,0f,0f,0f,.5f))
            val scale = ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(1f,1f,.5f,1f) })
            val filter = ColorFilter.Compose(scale,ColorFilter.Compose(ColorFilter.Matrix(marker),child))
            val reversed = ColorFilter.Compose(ColorFilter.Matrix(marker),ColorFilter.Compose(scale,child))
            val changed = ColorMatrixF32.of(marker.toFloatArray()).apply { postTranslate(.5f,0f,0f,0f) }
            val mutated = ColorFilter.Compose(scale,ColorFilter.Compose(ColorFilter.Matrix(changed),child))
            fun expected(f: ColorFilter,final: BlendMode=BlendMode.SRC_IN) = W5fColorCpuOracle.expectedImagePixel(
                image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),Paint(color=ColorARGB.White,colorFilter=f),
                destination=background,finalBlend=final,destinationBlend=BlendMode.SRC)
            val wanted = expected(filter)
            disjoint(wanted,expected(reversed))
            disjoint(wanted,expected(mutated))
            disjoint(wanted,expected(filter,BlendMode.SRC))
            val surface = Surface(1,1)
            surface.canvas {
                drawRect(unitRect(),Paint(color=background,blendMode=BlendMode.SRC,antiAlias=false))
                drawImage(image,unitRect(),SamplingOptions.NEAREST,
                    Paint(color=ColorARGB.White,colorFilter=filter,blendMode=BlendMode.SRC_IN,antiAlias=false))
            }
            marker.setRowMajor(changed.toFloatArray()); bytes.fill(0)
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
        }
    }

    private fun renderFilteredImage(filter: ColorFilter, bytes: ByteArray = byteArrayOf(0,-1,0,-1),paintAlpha: Int=127) {
        val image = Image.fromPixels(1,1,bytes,alphaType=AlphaType.UNPREMUL)
        val paint = Paint(color=ColorARGB.of(paintAlpha,255,255,255),colorFilter=filter,blendMode=BlendMode.SRC,antiAlias=false)
        val expected = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,
            finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1,1)
        surface.canvas { drawImage(image,RectF32.ofLTRB(0f,0f,1f,1f),SamplingOptions.NEAREST,paint) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }

    @Test fun unfilteredStencilOriginControl() {
        val image = Image.fromPixels(1,1,byteArrayOf(0,-1,0,-1),alphaType=AlphaType.UNPREMUL)
        val paint = Paint(color=ColorARGB.of(255,0,255,0),blendMode=BlendMode.SRC,antiAlias=false)
        val expected = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,
            finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1,1)
        surface.canvas {
            drawPath(Path().apply {
                addRect(RectF32.ofLTRB(0f,0f,1f,1f))
                addRect(RectF32.ofLTRB(.1f,.1f,.2f,.2f))
            },paint.copy(shader=Shader.Image(image)))
        }
        W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected))
    }

    @ParameterizedTest(name="external Matrix: {0}")
    @ValueSource(strings=["Image","Nine","Lattice","Atlas","Rect","DirectPath","StencilPath","A8"])
    fun externalMatrixEachImageOrigin(origin: String) {
        // A dropped external filter or early alpha-zero return loses this translated source.
        val mask = origin == "A8"
        val bytes = if(mask) byteArrayOf(127) else byteArrayOf(0,-1,0,-1)
        val image = Image.fromPixels(1,1,bytes,if(mask) ColorType.ALPHA_8 else ColorType.RGBA_8888,
            alphaType=AlphaType.UNPREMUL)
        val matrix = ColorMatrixF32.of(floatArrayOf(1f,0f,0f,0f,.25f, 0f,.5f,0f,0f,0f,
            0f,0f,1f,0f,0f, 0f,0f,0f,.5f,.25f))
        val paint = Paint(color=ColorARGB.of(255,0,255,0),colorFilter=ColorFilter.Matrix(matrix),
            blendMode=BlendMode.SRC,antiAlias=false)
        val expected = W5fColorCpuOracle.expectedImagePixel(image,SamplingOptions.NEAREST,Point2F32(.5f,.5f),paint,
            finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1,1)
        surface.canvas {
            val rect = RectF32.ofLTRB(0f,0f,1f,1f)
            when(origin) {
                "Image","A8" -> drawImage(image,rect,SamplingOptions.NEAREST,paint)
                "Nine" -> drawImageNine(image,rect,rect,paint)
                "Lattice" -> drawImageLattice(image,Lattice(emptyList(),emptyList()),rect,paint,SamplingOptions.NEAREST)
                "Atlas" -> drawAtlas(image,listOf(Matrix3x3F32()),listOf(rect),paint=paint)
                "Rect" -> drawRect(rect,paint.copy(shader=Shader.Image(image)))
                else -> drawPath(Path().apply {
                    addRect(rect)
                    if(origin=="StencilPath") addRect(RectF32.ofLTRB(.1f,.1f,.2f,.2f))
                },paint.copy(shader=Shader.Image(image)))
            }
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }
}
