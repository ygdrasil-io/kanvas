package org.graphiks.kanvas.text

import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.kanvas.text.paragraph.HitTestResult
import org.graphiks.kanvas.text.paragraph.LineBreaker
import org.graphiks.kanvas.text.paragraph.LineLayout
import org.graphiks.kanvas.text.paragraph.LineMetrics
import org.graphiks.kanvas.text.paragraph.Paragraph
import org.graphiks.kanvas.text.paragraph.ParagraphBuilder
import org.graphiks.kanvas.text.paragraph.ParagraphLayoutEngine
import org.graphiks.kanvas.text.paragraph.ParagraphLayoutResult
import org.graphiks.kanvas.text.paragraph.ParagraphStyle
import org.graphiks.kanvas.text.paragraph.PlaceholderStyle
import org.graphiks.kanvas.text.paragraph.SelectionRange
import org.graphiks.kanvas.text.paragraph.TextBox
import org.graphiks.kanvas.text.paragraph.TextPosition
import org.graphiks.kanvas.text.paragraph.TextStyle
import org.graphiks.kanvas.text.shaping.BidiResolver
import org.graphiks.kanvas.text.shaping.BidiRun
import org.graphiks.kanvas.text.shaping.EmojiSequenceShaper
import org.graphiks.kanvas.text.shaping.FeatureSet
import org.graphiks.kanvas.text.shaping.GDEFData
import org.graphiks.kanvas.text.shaping.GPOSEngine
import org.graphiks.kanvas.text.shaping.GSUBEngine
import org.graphiks.kanvas.text.shaping.GlyphCluster
import org.graphiks.kanvas.text.shaping.OpenTypeShapingEngine
import org.graphiks.kanvas.text.shaping.ScriptItemizer
import org.graphiks.kanvas.text.shaping.ScriptRun
import org.graphiks.kanvas.text.shaping.ShapedGlyphRun
import org.graphiks.kanvas.text.shaping.ShapingDiagnostic
import org.graphiks.kanvas.text.shaping.ShapingRequest
import org.graphiks.kanvas.text.shaping.ShapingResult
import org.graphiks.kanvas.text.shaping.TextSegmenter
import org.graphiks.kanvas.text.shaping.UnicodeData

class TextStackSurfaceTest {
    @Test
    fun exposesExpectedPureKotlinTextStackTypes() {
        val shapingTypes = listOf(
            ShapingRequest::class.simpleName,
            ShapingResult::class.simpleName,
            ShapedGlyphRun::class.simpleName,
            GlyphCluster::class.simpleName,
            FeatureSet::class.simpleName,
            ScriptRun::class.simpleName,
            BidiRun::class.simpleName,
            UnicodeData::class.simpleName,
            TextSegmenter::class.simpleName,
            BidiResolver::class.simpleName,
            ScriptItemizer::class.simpleName,
            OpenTypeShapingEngine::class.simpleName,
            GSUBEngine::class.simpleName,
            GPOSEngine::class.simpleName,
            GDEFData::class.simpleName,
            EmojiSequenceShaper::class.simpleName,
            ShapingDiagnostic::class.simpleName,
        )

        val paragraphTypes = listOf(
            ParagraphBuilder::class.simpleName,
            Paragraph::class.simpleName,
            ParagraphStyle::class.simpleName,
            TextStyle::class.simpleName,
            PlaceholderStyle::class.simpleName,
            ParagraphLayoutEngine::class.simpleName,
            ParagraphLayoutResult::class.simpleName,
            LineBreaker::class.simpleName,
            LineLayout::class.simpleName,
            LineMetrics::class.simpleName,
            TextBox::class.simpleName,
            HitTestResult::class.simpleName,
            SelectionRange::class.simpleName,
            TextPosition::class.simpleName,
        )

        assertEquals(17, shapingTypes.size)
        assertEquals(14, paragraphTypes.size)
    }
}
