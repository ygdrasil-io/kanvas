# SkParagraph — Multi-Style Paragraph Layout

`modules/skparagraph/` is Skia's paragraph layout engine. It accepts
styled text with placeholders, line breaks it under a maximum width,
shapes each segment with [SkShaper](skshaper.md) (HarfBuzz under the
hood), applies decoration / shadow / background styles, and renders
the final paragraph onto an `SkCanvas`. It is the engine behind
Flutter's text and is fully usable from CanvasKit.

The layout pipeline at a glance:

```
  raw UTF-8/UTF-16 + styles + placeholders
                │
                ▼
  ┌──────────────────────────┐
  │ ParagraphBuilder         │  push/pop styles, addText/addPlaceholder
  └──────────┬───────────────┘
             │ Build()
             ▼
  ┌──────────────────────────┐   uses SkUnicode for code-unit flags,
  │ ParagraphImpl            │   BiDi regions, words, line breaks
  │  - text                  │
  │  - blocks (TextStyle)    │
  │  - placeholders          │
  └──────────┬───────────────┘
             │ layout(width)
             │
             ▼
  ┌──────────────────────────┐
  │ OneLineShaper            │  drives SkShaper run-by-run with font
  │  + LangIterator          │  fallback (matchFamilyStyleCharacter)
  │  + Bidi/Script iterators │
  └──────────┬───────────────┘
             │ shaped Runs (LTR/RTL, font, glyphs, advances)
             ▼
  ┌──────────────────────────┐
  │ TextWrapper              │  greedy line breaking; respects ellipsis
  │  - clusters / words      │  and max lines; handles ghost spaces
  │  - line metrics          │
  └──────────┬───────────────┘
             │ TextLines
             ▼
  ┌──────────────────────────┐
  │ format/align,            │
  │ apply text-direction,    │
  │ visual reordering (BiDi) │
  └──────────┬───────────────┘
             │ paint(canvas, x, y)
             ▼
  ┌──────────────────────────┐
  │ ParagraphPainterImpl     │  emits SkTextBlob runs, decorations,
  │  → SkCanvas              │  shadows, backgrounds, placeholder rects
  └──────────────────────────┘
```

## Public surface

The public headers live in `modules/skparagraph/include/`. The two
entry classes you use directly are `ParagraphBuilder` (mutator) and
`Paragraph` (immutable result). All other types are configuration
(`ParagraphStyle`, `TextStyle`, `StrutStyle`, `PlaceholderStyle`,
`TextShadow`) or query results (`LineMetrics`, `TextBox`,
`PositionWithAffinity`, `GlyphInfo`, `FontInfo`).

Everything lives in `namespace skia::textlayout`.

---

## ParagraphBuilder — `include/ParagraphBuilder.h`

Stateful, single-paragraph builder. Maintains a stack of `TextStyle`s
plus an accumulating UTF-8 buffer and a list of placeholders. Construct
with the global `ParagraphStyle` and the typeface/fontmgr collection,
push styles for ranges, then `Build()`.

```cpp
class ParagraphBuilder {
public:
    virtual ~ParagraphBuilder() = default;

    virtual void pushStyle(const TextStyle&) = 0;
    virtual void pop() = 0;
    virtual TextStyle peekStyle() = 0;

    virtual void addText(const std::u16string&)               = 0;  // converts to UTF-8
    virtual void addText(const char* text)                    = 0;  // null-terminated UTF-8
    virtual void addText(const char* text, size_t len)        = 0;

    virtual void addPlaceholder(const PlaceholderStyle&)      = 0;  // inserts U+FFFC

    virtual std::unique_ptr<Paragraph> Build()                = 0;

    virtual SkSpan<char>             getText()           = 0;
    virtual const ParagraphStyle&    getParagraphStyle() const = 0;

    virtual void Reset()                                  = 0;

    static std::unique_ptr<ParagraphBuilder> make(
        const ParagraphStyle&,
        sk_sp<FontCollection>,
        sk_sp<SkUnicode>);
};
```

Usage:

```cpp
ParagraphStyle ps;
ps.setTextAlign(TextAlign::kLeft);
ps.setMaxLines(3);
ps.setEllipsis(SkString("…"));

auto builder = ParagraphBuilder::make(ps, fonts, unicode);

builder->pushStyle(normalStyle);
builder->addText("Hello, ");
builder->pushStyle(boldStyle);
builder->addText("world");
builder->pop();
builder->addText("!");
builder->addPlaceholder({40, 40, PlaceholderAlignment::kBaseline,
                         TextBaseline::kAlphabetic, 0});

auto paragraph = builder->Build();
paragraph->layout(320);
paragraph->paint(canvas, 0, 0);
```

`addPlaceholder` inserts a single object-replacement code point
(`U+FFFC`) into the text and records the requested width/height/
alignment so `paint` skips over the slot — the host renders into the
returned rect (`getRectsForPlaceholders`).

`Reset()` clears text, the style stack, and placeholders but keeps the
initial `ParagraphStyle`. `Build()` resets the style stack
automatically and is destructive on the accumulated text.

The "Client" overloads (under `SK_UNICODE_CLIENT_IMPLEMENTATION`) let
the host pre-supply word boundaries, grapheme breaks, and line breaks
in either UTF-8 or UTF-16 — useful when the embedder owns its own
Unicode data and does not want to ship ICU.

The factory `ParagraphBuilder::make` returns `ParagraphBuilderImpl`
(`src/ParagraphBuilderImpl.h`); under client-unicode mode, it returns
a builder that also receives the supplied breaks/words.

---

## ParagraphStyle — `include/ParagraphStyle.h`

Paragraph-wide settings. Created with sensible defaults; mutate via
setters before passing to the builder.

```cpp
struct ParagraphStyle {
    ParagraphStyle();

    const StrutStyle& getStrutStyle() const;
    void              setStrutStyle(StrutStyle);

    const TextStyle&  getTextStyle()  const;          // default text style
    void              setTextStyle(const TextStyle&);

    TextDirection getTextDirection() const;            // kLtr / kRtl
    void          setTextDirection(TextDirection);

    TextAlign     getTextAlign() const;                // kLeft/kRight/kCenter/kJustify/kStart/kEnd
    void          setTextAlign(TextAlign);

    size_t        getMaxLines() const;                 // default = SIZE_MAX (unlimited)
    void          setMaxLines(size_t);

    SkString      getEllipsis()      const;            // UTF-8
    std::u16string getEllipsisUtf16() const;           // alternative form
    void          setEllipsis(const SkString&);
    void          setEllipsis(const std::u16string&);

    SkScalar      getHeight() const;                   // global line-height multiplier
    void          setHeight(SkScalar);

    TextHeightBehavior getTextHeightBehavior() const;
    void               setTextHeightBehavior(TextHeightBehavior);

    bool unlimited_lines()  const;                     // == max == SIZE_MAX
    bool ellipsized()       const;
    TextAlign effective_align() const;                 // resolves kStart/kEnd against direction
    bool hintingIsOn() const;
    void turnHintingOff();

    bool fakeMissingFontStyles() const;                // synthesize bold/italic if a font lacks it
    void setFakeMissingFontStyles(bool);

    bool getReplaceTabCharacters() const;              // map '\t' to a space-glyph at shaping time
    void setReplaceTabCharacters(bool);

    bool getApplyRoundingHack() const;                 // legacy Flutter rounding (default true)
    void setApplyRoundingHack(bool);
};
```

`TextHeightBehavior` (from `DartTypes.h`) is a bit-mask:

```cpp
enum TextHeightBehavior {
    kAll                = 0x0,
    kDisableFirstAscent = 0x1,
    kDisableLastDescent = 0x2,
    kDisableAll         = 0x3,
};
```

`kDisableFirstAscent` removes the leading half above the first line's
baseline (CSS `text-edge: cap`-ish), `kDisableLastDescent` collapses
the leading below the last baseline. Both are off by default.

### `StrutStyle` — strut metrics

A *strut* is a synthetic invisible glyph whose metrics dominate the
line height regardless of which fonts are actually shaped on a line.
This is how you guarantee a CSS-like uniform line height even when a
fallback font is much taller than the requested face.

```cpp
struct StrutStyle {
    StrutStyle();

    const std::vector<SkString>& getFontFamilies() const;
    void  setFontFamilies(std::vector<SkString>);
    SkFontStyle getFontStyle()    const;  void setFontStyle(SkFontStyle);
    SkScalar    getFontSize()     const;  void setFontSize(SkScalar);
    void        setHeight(SkScalar);      SkScalar getHeight() const;
    void        setLeading(SkScalar);     SkScalar getLeading() const;

    bool getStrutEnabled()    const; void setStrutEnabled(bool);
    bool getForceStrutHeight()const; void setForceStrutHeight(bool);
    bool getHeightOverride()  const; void setHeightOverride(bool);
    bool getHalfLeading()     const; void setHalfLeading(bool);
};
```

- `setStrutEnabled(true)` turns the strut on. When off, line height
  comes from the actual shaped runs.
- `setForceStrutHeight(true)` makes the strut metrics *replace* per-run
  metrics (matches Flutter `strutStyle.forceStrutHeight`).
- `setHeightOverride(true)` interprets `height` as a *multiplier* on
  the strut font's intrinsic height; when false `height` scales with
  the leading model.
- `setHalfLeading(true)` splits the leading evenly above and below the
  baseline, matching CSS `line-height` half-leading.

---

## TextStyle — `include/TextStyle.h`

Per-range styling. Pushed onto the builder's stack with `pushStyle`
and removed with `pop`. Equality is value-based (`equals`); two
adjacent runs with equal styles are merged into one block by the
builder.

### Construction

`TextStyle()` is a defaulted ctor with library defaults (font size 14,
edging anti-alias, slight hinting, white color). Copy/assign are also
defaulted. `cloneForPlaceholder` returns a copy with `fIsPlaceholder`
set — used by the builder for `addPlaceholder`.

### Color and paints

```cpp
SkColor getColor() const;                  void setColor(SkColor);

bool                  hasForeground() const;
SkPaint               getForeground() const;
ParagraphPainter::SkPaintOrID getForegroundPaintOrID() const;
void                  setForegroundPaint(SkPaint);
void                  setForegroundPaintID(ParagraphPainter::PaintID);
void                  clearForegroundColor();

// (mirror set for background: hasBackground, getBackground, ...)
```

`fColor` is the simple text color used when there is no foreground
paint. `setForegroundPaint` lets you supply a full `SkPaint` (gradient
shader, blender, etc.) — when set, `fColor` is ignored. Backgrounds
are filled rects covering each run's box prior to drawing the glyphs.

`SkPaintOrID = std::variant<SkPaint, PaintID>` lets clients that
implement a *custom* `ParagraphPainter` reference paints by ID instead
of carrying SkPaint values across the API boundary. Used by
CanvasKit's externally-supplied paints.

### Decorations

```cpp
enum TextDecoration { kNoDecoration = 0, kUnderline = 1,
                      kOverline = 2, kLineThrough = 4 };
enum TextDecorationStyle { kSolid, kDouble, kDotted, kDashed, kWavy };
enum TextDecorationMode  { kGaps, kThrough };

struct Decoration {
    TextDecoration       fType;
    TextDecorationMode   fMode;
    SkColor              fColor;
    TextDecorationStyle  fStyle;
    SkScalar             fThicknessMultiplier;
};

Decoration            getDecoration() const;
TextDecoration        getDecorationType()           const;
TextDecorationMode    getDecorationMode()           const;
SkColor               getDecorationColor()          const;
TextDecorationStyle   getDecorationStyle()          const;
SkScalar              getDecorationThicknessMultiplier() const;
void setDecoration                  (TextDecoration);
void setDecorationMode              (TextDecorationMode);
void setDecorationStyle             (TextDecorationStyle);
void setDecorationColor             (SkColor);
void setDecorationThicknessMultiplier(SkScalar);
```

`TextDecoration` is a bit mask: combine `kUnderline | kLineThrough` to
get both. `TextDecorationMode::kGaps` carves descenders out of the
underline (interrupting the line where `g`, `p`, `j` cross it);
`kThrough` draws straight through. `fThicknessMultiplier` scales the
font's `fUnderlineThickness` (or `fStrikeoutThickness` for line-through).

The drawing logic lives in `src/Decorations.cpp`. The `kWavy` style
uses an `SkPath` of a small sine sample tiled across the line; gaps
are computed from `SkTextBlob::getIntercepts` so descenders are not
struck through.

### Font selection

```cpp
SkFontStyle  getFontStyle()                 const;  // weight/width/slant
void         setFontStyle(SkFontStyle);

SkScalar     getFontSize()                  const;  // default 14
void         setFontSize(SkScalar);

const std::vector<SkString>& getFontFamilies() const;
void                         setFontFamilies(std::vector<SkString>);

SkTypeface*       getTypeface() const;       // raw pointer
sk_sp<SkTypeface> refTypeface() const;       // ref-counted
void              setTypeface(sk_sp<SkTypeface>);

SkString getLocale() const;
void     setLocale(const SkString&);

const std::optional<FontArguments>& getFontArguments() const;
void setFontArguments(const std::optional<SkFontArguments>&);
```

Font family resolution walks the families in order, falling back through
the `FontCollection` (see below) until a typeface containing each
character is found. `setLocale` provides a BCP-47 hint for
locale-aware character → glyph mapping (e.g. CJK same-codepoint
disambiguation).

`setFontArguments` lets a style request a specific variation position
or palette — the `FontArguments` struct copies the data so the original
`SkFontArguments` may be freed after the call.

### Layout knobs

```cpp
SkScalar getBaselineShift() const;       void setBaselineShift(SkScalar);

void    setHeight(SkScalar);             SkScalar getHeight() const;     // 0 if !heightOverride
void    setHeightOverride(bool);         bool     getHeightOverride() const;
void    setHalfLeading(bool);            bool     getHalfLeading()    const;

void    setLetterSpacing(SkScalar);      SkScalar getLetterSpacing() const;
void    setWordSpacing  (SkScalar);      SkScalar getWordSpacing()   const;

TextBaseline getTextBaseline() const;    void setTextBaseline(TextBaseline);
void getFontMetrics(SkFontMetrics*) const;

bool isPlaceholder() const;              void setPlaceholder();

SkFont::Edging  getFontEdging()  const;  void setFontEdging(SkFont::Edging);
bool            getSubpixel()    const;  void setSubpixel(bool);
SkFontHinting   getFontHinting() const;  void setFontHinting(SkFontHinting);
```

- `fHeight` and `fHeightOverride` mirror Flutter / CSS line-height. If
  `heightOverride` is true the line uses `fontSize × height` as the
  total line height.
- `fHalfLeading` switches between the typographic-leading (false) and
  CSS half-leading (true) models.
- `fBaselineShift` raises (negative) or lowers (positive) the run's
  baseline, in pixels — superscript / subscript.
- `fLetterSpacing` adds extra advance to every glyph in the run.
  `fWordSpacing` adds extra advance to space characters.

### Shadows

```cpp
size_t   getShadowNumber() const;
std::vector<TextShadow> getShadows() const;
void addShadow(TextShadow);
void resetShadows();
```

`TextShadow` (`include/TextShadow.h`) is `{SkColor, SkPoint offset,
double blurSigma}`. Multiple shadows are layered in declaration order
behind the glyphs; they are blurred via an `SkMaskFilter` (Gaussian
blur with sigma).

### Font features

```cpp
struct FontFeature { SkString fName; int fValue; };
size_t getFontFeatureNumber() const;
std::vector<FontFeature> getFontFeatures() const;
void addFontFeature(const SkString&, int value);
void resetFontFeatures();
```

`FontFeature` corresponds to OpenType feature tags ("liga" = 1,
"smcp" = 1, "cv01" = 5, ...). They are passed straight through to
HarfBuzz as `SkShaper::Feature` records covering the run.

---

## PlaceholderStyle — `include/TextStyle.h`

```cpp
enum class PlaceholderAlignment {
    kBaseline,        // align baseline of placeholder with text baseline
    kAboveBaseline,   // bottom edge sits on baseline (placeholder above)
    kBelowBaseline,   // top edge sits on baseline    (placeholder below)
    kTop,             // align top of placeholder with top of font
    kBottom,          // align bottom of placeholder with top of font
    kMiddle,          // center placeholder vertically on text middle
};

struct PlaceholderStyle {
    SkScalar fWidth = 0, fHeight = 0;
    PlaceholderAlignment fAlignment = PlaceholderAlignment::kBaseline;
    TextBaseline         fBaseline  = TextBaseline::kAlphabetic;
    SkScalar             fBaselineOffset = 0;
};
```

Placeholders are inline rectangles at known sizes. The text engine
treats them as a single positioned glyph (the `U+FFFC` object
replacement character) that contributes to line breaking and
metrics. The host queries `paragraph->getRectsForPlaceholders()` and
draws into each rectangle. `fBaselineOffset` is the distance from the
top of the rect down to the baseline used for `kBaseline` alignment.

---

## DartTypes — `include/DartTypes.h`

Common enums and small value types (named "Dart" because they originated
in Flutter / Dart bindings):

```cpp
enum Affinity { kUpstream, kDownstream };

enum class RectHeightStyle {
    kTight,                            // tight bounding box per run
    kMax,                              // line-height-maximal box per run
    kIncludeLineSpacingMiddle,         // half line-gap above + half below
    kIncludeLineSpacingTop,
    kIncludeLineSpacingBottom,
    kStrut,                            // use strut metrics
};
enum class RectWidthStyle  { kTight, kMax };

enum class TextAlign     { kLeft, kRight, kCenter, kJustify, kStart, kEnd };
enum class TextDirection { kRtl, kLtr };
enum class TextBaseline  { kAlphabetic, kIdeographic };
enum class LineMetricStyle : uint8_t { Typographic, CSS };

struct PositionWithAffinity {
    int32_t  position;
    Affinity affinity;
};
struct TextBox {
    SkRect        rect;
    TextDirection direction;
};

template <typename T> struct SkRange {
    T start, end;
    T width() const;
    void Shift(SignedT delta);
    bool contains   (SkRange<size_t>) const;
    bool intersects (SkRange<size_t>) const;
    SkRange<size_t> intersection(SkRange<size_t>) const;
    bool empty() const;            // both start and end == EMPTY_INDEX
};
```

`Affinity` distinguishes whether a hit-test cursor belongs to the end
of one run or the beginning of the next (matters at line wraps and at
RTL/LTR boundaries).

---

## Paragraph — `include/Paragraph.h`

The immutable result of `Build()`. The public class is abstract; the
concrete implementation is `ParagraphImpl` (`src/ParagraphImpl.h`).

### Read-only metrics (after `layout`)

```cpp
SkScalar getMaxWidth();             // max width passed to layout()
SkScalar getHeight();
SkScalar getMinIntrinsicWidth();    // longest unbreakable run
SkScalar getMaxIntrinsicWidth();    // total advance if not wrapped
SkScalar getAlphabeticBaseline();
SkScalar getIdeographicBaseline();
SkScalar getLongestLine();
bool     didExceedMaxLines();
```

### Layout & paint

```cpp
virtual void layout(SkScalar width)                               = 0;
virtual void paint (SkCanvas* canvas, SkScalar x, SkScalar y)     = 0;
virtual void paint (ParagraphPainter*, SkScalar x, SkScalar y)    = 0;
```

Calling `layout` populates lines, runs, decoration paths, etc. The
paragraph tracks an `InternalState` (`kUnknown → kIndexed → kShaped →
kLineBroken → kFormatted → kDrawn`) so a re-`layout` only redoes the
work invalidated by changes (cf. `markDirty()`). The cache lives at
`FontCollection::getParagraphCache()` and is keyed on text + styles +
shaping inputs, so rebuilding the same paragraph is `O(1)`.

`paint(SkCanvas*, x, y)` wraps the canvas in `CanvasParagraphPainter`
and delegates to the `ParagraphPainter*` overload.

### Hit testing & ranges

```cpp
virtual std::vector<TextBox> getRectsForRange   (unsigned start, unsigned end,
                                                 RectHeightStyle, RectWidthStyle) = 0;
virtual std::vector<TextBox> getRectsForPlaceholders()                            = 0;

virtual PositionWithAffinity getGlyphPositionAtCoordinate(SkScalar dx, SkScalar dy) = 0;
virtual SkRange<size_t>      getWordBoundary(unsigned offset) = 0;
```

`getRectsForRange` returns the visible boxes for a UTF-16 range — one
per visual run on each line the range crosses. `getRectsForPlaceholders`
returns one box per `addPlaceholder` call in the order they appeared.

`getGlyphPositionAtCoordinate` is hit-testing for the cursor: returns
the UTF-16 code-unit position closest to the pointer plus an
upstream/downstream affinity. `getWordBoundary` snaps a code-unit
offset to the enclosing word range using the SkUnicode word breaks.

### Per-line metrics

```cpp
virtual void   getLineMetrics(std::vector<LineMetrics>&)               = 0;
virtual size_t lineNumber()                                            = 0;
virtual int    getLineNumberAt(TextIndex utf8)                   const = 0;
virtual int    getLineNumberAtUTF16Offset(size_t utf16)                = 0;
virtual bool   getLineMetricsAt(int line, LineMetrics* out)      const = 0;
virtual TextRange getActualTextRange(int line, bool includeSpaces) const = 0;
```

`LineMetrics` (`include/Metrics.h`) — see the dedicated section below.

`getActualTextRange` returns the visible text on the line, optionally
including trailing whitespace (which may extend past the visible right
edge as "ghost" spaces).

### Editing API

```cpp
virtual void markDirty() = 0;
virtual int32_t   unresolvedGlyphs() = 0;
virtual std::unordered_set<SkUnichar> unresolvedCodepoints() = 0;

virtual void updateTextAlign      (TextAlign);
virtual void updateFontSize       (size_t from, size_t to, SkScalar);
virtual void updateForegroundPaint(size_t from, size_t to, SkPaint);
virtual void updateBackgroundPaint(size_t from, size_t to, SkPaint);
```

`update*` exists so editor implementations (Flutter `EditableText`)
can change attributes without re-shaping. They are hot-path mutators
that bypass the normal "build a new paragraph" flow; they reset state
to before-format and trigger re-formatting only.

`unresolvedGlyphs` returns the count of glyph slots the shaper failed
to resolve (no font in the collection has a glyph) — `-1` if the
paragraph has not been laid out. `unresolvedCodepoints` returns the
actual missing code points.

### Glyph cluster queries

```cpp
struct GlyphClusterInfo {
    SkRect        fBounds;
    TextRange     fClusterTextRange;
    TextDirection fGlyphClusterPosition;
};
virtual bool getGlyphClusterAt           (TextIndex, GlyphClusterInfo*) = 0;
virtual bool getClosestGlyphClusterAt    (SkScalar dx, SkScalar dy,
                                          GlyphClusterInfo*)            = 0;

struct GlyphInfo {
    SkRect        fGraphemeLayoutBounds;
    TextRange     fGraphemeClusterTextRange;
    TextDirection fDirection;
    bool          fIsEllipsis;
};
virtual bool getGlyphInfoAtUTF16Offset   (size_t,    GlyphInfo*) = 0;
virtual bool getClosestUTF16GlyphInfoAt  (SkScalar, SkScalar,
                                          GlyphInfo*)              = 0;
```

`GlyphClusterInfo` is keyed by a single shaped glyph cluster (font
unit). `GlyphInfo` is keyed by a *grapheme* (user-perceived character)
— useful when the paragraph contains ligatures, emoji ZWJ sequences,
or combining marks where one glyph spans multiple code points.

### Font and visit APIs

```cpp
struct FontInfo {
    FontInfo(const SkFont&, const TextRange&);
    SkFont    fFont;
    TextRange fTextRange;
};
virtual SkFont               getFontAt          (TextIndex) const = 0;
virtual SkFont               getFontAtUTF16Offset(size_t)         = 0;
virtual std::vector<FontInfo> getFonts()         const = 0;
```

`getFonts` returns the map of font runs that resulted from font
fallback — useful for showing "this paragraph uses Roboto, Noto Sans
CJK SC, and Noto Color Emoji".

### Glyph visitors

```cpp
enum VisitorFlags { kWhiteSpace_VisitorFlag = 1 << 0 };

struct VisitorInfo {
    const SkFont&   font;
    SkPoint         origin;
    SkScalar        advanceX;
    int             count;
    const SkGlyphID* glyphs;     // count values
    const SkPoint*  positions;   // count values
    const uint32_t* utf8Starts;  // count+1 values
    unsigned        flags;
};
using Visitor = std::function<void(int lineNumber, const VisitorInfo*)>;
virtual void visit(const Visitor&) = 0;

struct ExtendedVisitorInfo { /* ... + per-glyph SkSize advance + SkRect bounds ... */ };
using ExtendedVisitor = std::function<void(int, const ExtendedVisitorInfo*)>;
virtual void extendedVisit(const ExtendedVisitor&) = 0;
```

`visit` calls back once per shaped run, then once per line with
`info == nullptr` to mark the line end. `extendedVisit` additionally
returns per-glyph `SkSize` advances and tight `SkRect` bounds — used
by Flutter's `Paragraph.getBoxesForRange` and the path conversion.

### Path conversion / emoji detection

```cpp
virtual int  getPath(int lineNumber, SkPath* dest) = 0;
static  SkPath GetPath(SkTextBlob*);
virtual bool containsEmoji            (SkTextBlob*) = 0;
virtual bool containsColorFontOrBitmap(SkTextBlob*) = 0;
```

`getPath` returns the count of glyphs that *could not* be converted
(bitmap or COLR glyphs), and appends the resulting `SkPath` for the
line into `dest`. `containsEmoji` and `containsColorFontOrBitmap`
inspect the runs of a blob to decide whether the destination needs a
color buffer / supports ARGB glyph atlases.

---

## LineMetrics — `include/Metrics.h`

```cpp
class LineMetrics {
public:
    LineMetrics();
    LineMetrics(size_t start, size_t end,
                size_t end_excluding_whitespace,
                size_t end_including_newline,
                bool   hard_break);

    size_t fStartIndex                = 0;
    size_t fEndIndex                  = 0;
    size_t fEndExcludingWhitespaces   = 0;
    size_t fEndIncludingNewline       = 0;
    bool   fHardBreak                 = false;

    double fAscent           = SK_ScalarMax;   // positive
    double fDescent          = SK_ScalarMin;   // positive
    double fUnscaledAscent   = SK_ScalarMax;   // before height multipliers
    double fHeight           = 0.0;            // line height (rounded)
    double fWidth            = 0.0;            // visible text width
    double fLeft             = 0.0;            // x of leftmost run
    double fBaseline         = 0.0;            // y of baseline from paragraph top
    size_t fLineNumber       = 0;

    std::map<size_t, StyleMetrics> fLineMetrics;  // per-style font metrics
};

class StyleMetrics {
public:
    explicit StyleMetrics(const TextStyle*);
    StyleMetrics(const TextStyle*, SkFontMetrics&);
    const TextStyle* text_style;
    SkFontMetrics    font_metrics;
};
```

`fStartIndex`, `fEndIndex`, etc. are UTF-8 byte indices into the
paragraph's text. `fHardBreak` is true when the line ended on a hard
break (`\n`, `U+2028`, …); soft breaks (wrap) leave it false.

Top edge of a line: `fBaseline − fAscent`. Bottom edge:
`fBaseline + fDescent`. `fLineMetrics` keys per-block metrics by the
style block's UTF-8 start index.

---

## ParagraphPainter — `include/ParagraphPainter.h`

The drawing abstraction. The default implementation drives an
`SkCanvas`; embedders can implement it to retarget the output (e.g.
record into a non-Skia surface, or substitute paints by ID).

```cpp
class ParagraphPainter {
public:
    typedef int                          PaintID;
    typedef std::variant<SkPaint, PaintID> SkPaintOrID;

    struct DashPathEffect {
        DashPathEffect(SkScalar onLength, SkScalar offLength);
        SkScalar fOnLength, fOffLength;
    };

    class DecorationStyle {
    public:
        DecorationStyle();
        DecorationStyle(SkColor, SkScalar strokeWidth,
                        std::optional<DashPathEffect>);
        SkColor                         getColor()         const;
        SkScalar                        getStrokeWidth()   const;
        std::optional<DashPathEffect>   getDashPathEffect()const;
        const SkPaint&                  skPaint()          const;
    };

    virtual ~ParagraphPainter() = default;

    virtual void drawTextBlob   (const sk_sp<SkTextBlob>&, SkScalar x, SkScalar y, const SkPaintOrID&) = 0;
    virtual void drawTextShadow (const sk_sp<SkTextBlob>&, SkScalar x, SkScalar y, SkColor, SkScalar blurSigma) = 0;
    virtual void drawRect       (const SkRect&, const SkPaintOrID&) = 0;
    virtual void drawFilledRect (const SkRect&, const DecorationStyle&) = 0;
    virtual void drawPath       (const SkPath&, const DecorationStyle&) = 0;
    virtual void drawLine       (SkScalar x0, SkScalar y0,
                                 SkScalar x1, SkScalar y1,
                                 const DecorationStyle&) = 0;

    virtual void clipRect(const SkRect&) = 0;
    virtual void translate(SkScalar dx, SkScalar dy) = 0;
    virtual void save();    virtual void restore();
};
```

The default `CanvasParagraphPainter` (`src/ParagraphPainterImpl.h`)
forwards each call onto the wrapped `SkCanvas`. CanvasKit ships an
embedder painter that intercepts paints by `PaintID` so JavaScript
callers can pre-allocate paint objects and reference them by integer.

---

## FontCollection — `include/FontCollection.h`

`FontCollection` aggregates one or more `SkFontMgr`s and is the
typeface-resolution authority for a `Paragraph`. It also owns the
shared `ParagraphCache`.

```cpp
class FontCollection : public SkRefCnt {
public:
    FontCollection();

    size_t           getFontManagersCount() const;

    void setAssetFontManager   (sk_sp<SkFontMgr>);
    void setDynamicFontManager (sk_sp<SkFontMgr>);
    void setTestFontManager    (sk_sp<SkFontMgr>);
    void setDefaultFontManager (sk_sp<SkFontMgr>);
    void setDefaultFontManager (sk_sp<SkFontMgr>, const char defaultFamilyName[]);
    void setDefaultFontManager (sk_sp<SkFontMgr>, const std::vector<SkString>& defaults);

    sk_sp<SkFontMgr> getFallbackManager() const;

    std::vector<sk_sp<SkTypeface>> findTypefaces(const std::vector<SkString>& families,
                                                 SkFontStyle);
    std::vector<sk_sp<SkTypeface>> findTypefaces(const std::vector<SkString>&, SkFontStyle,
                                                 const std::optional<FontArguments>&);
    sk_sp<SkTypeface> defaultFallback(SkUnichar, const std::vector<SkString>&,
                                      SkFontStyle, const SkString& locale,
                                      const std::optional<FontArguments>&);
    sk_sp<SkTypeface> defaultEmojiFallback(SkUnichar, SkFontStyle, const SkString& locale);
    sk_sp<SkTypeface> defaultFallback();

    void disableFontFallback();   void enableFontFallback();
    bool fontFallbackEnabled();

    ParagraphCache* getParagraphCache();
    void clearCaches();
};
```

Resolution order is `Asset → Dynamic → Test → Default`. The asset
manager typically holds bundled fonts; dynamic is used for runtime-loaded
fonts; test is used by `dm`/`gm`; default falls back to the platform.

`findTypefaces` returns *all* typefaces matching the family list
(after walking the four managers). `defaultFallback(SkUnichar, …)` is
the per-character fallback used by the shaper — it queries each
manager's `matchFamilyStyleCharacter` until one succeeds.

`defaultEmojiFallback` is dedicated to color-emoji selection: it
prefers a typeface that explicitly claims emoji support so combined
emoji sequences (skin-tone modifiers, ZWJ joins) shape consistently.

The collection caches resolved typefaces in a `THashMap` keyed by
family list + style + variation arguments to avoid repeated
font-mgr round trips during shaping.

`TypefaceFontProvider` (`include/TypefaceFontProvider.h`) is a small
in-memory `SkFontMgr` subclass that lets you register typefaces by
family name + alias — useful as an asset manager for tests and apps
that ship their own fonts:

```cpp
size_t TypefaceFontProvider::registerTypeface(sk_sp<SkTypeface>);
size_t TypefaceFontProvider::registerTypeface(sk_sp<SkTypeface>, const SkString& alias);
```

---

## FontArguments wrapper — `include/FontArguments.h`

A copy-friendly companion to `SkFontArguments` (which holds raw
pointers). Used inside `TextStyle` and `FontCollection` so that
variation positions and palette overrides survive across shapings.

```cpp
class FontArguments {
public:
    FontArguments(const SkFontArguments&);
    FontArguments(const FontArguments&) = default;
    FontArguments(FontArguments&&)      = default;
    sk_sp<SkTypeface> CloneTypeface(const sk_sp<SkTypeface>&) const;

    friend bool operator==(const FontArguments&, const FontArguments&);
    friend bool operator!=(const FontArguments&, const FontArguments&);
    friend struct std::hash<FontArguments>;
};
```

`CloneTypeface` invokes `SkTypeface::makeClone` with the stored
arguments. The hash and equality let `FontArguments` participate in
the `FamilyKey` of `FontCollection::fTypefaces` and the cache key of
`ParagraphCache`.

---

## Implementation overview — `src/`

The implementation is large (`ParagraphImpl.cpp` ~1.5 kLOC,
`TextLine.cpp` ~1.6 kLOC, `OneLineShaper.cpp` ~900 LOC,
`TextWrapper.cpp` ~600 LOC). Highlights:

### `ParagraphBuilderImpl`

Maintains `std::stack<TextStyle>`, an accumulating `SkString fUtf8`,
`std::vector<Block>`, and `std::vector<Placeholder>`. On each
`addText`, the top style becomes the next block (merging adjacent
blocks with equal styles). `Build()` returns a `ParagraphImpl` and
resets internal state.

When using the "Client" Unicode path, the builder additionally keeps
caller-supplied breaks/words so `ParagraphImpl::computeCodeUnitProperties`
can substitute the supplied data for ICU output.

### `ParagraphImpl`

Top-level layout coordinator. Holds:

- `SkString fText` — the canonical UTF-8 text.
- `TArray<Block>` `fTextStyles` — style runs.
- `TArray<Placeholder>` `fPlaceholders`.
- `TArray<Run>` `fRuns` — one shaped run per (font, bidi level,
  script, language) segment, populated by `OneLineShaper`.
- `TArray<Cluster>` `fClusters` — one cluster per *shaping cluster*,
  with width and grapheme/whitespace flags, and back-pointers to its
  `Run`.
- `TArray<TextLine>` `fLines` — one entry per visual line.
- `TArray<CodeUnitFlags>` `fCodeUnitProperties` — `SkUnicode`-derived
  per-code-unit flags (grapheme start, soft/hard break, …).
- `InternalLineMetrics fStrutMetrics` — strut height/leading.
- `InternalState fState` — coarse state machine.

`layout(width)`:

1. `computeCodeUnitProperties()` — fill `fCodeUnitProperties` via
   `SkUnicode::computeCodeUnitFlags`.
2. `OneLineShaper::shape()` — produce `fRuns` and `fClusters`.
3. `TextWrapper::breakTextIntoLines()` — fill `fLines`.
4. `formatLines()` — apply alignment / justification / ellipsis.
5. `resolveStrut()` — adjust line metrics if strut is enabled.
6. `fState = kFormatted`.

The cache is consulted at the start of the `layout` pipeline: if the
hash matches a previously shaped paragraph, the cached `fRuns` and
`fClusters` are reused.

### `OneLineShaper`

Implements `SkShaper::RunHandler` and is the bridge from style runs to
`SkShaper`. Walks the text in *shaping regions* (ranges that the
language iterator says have a single locale and direction). For each
region, iterates the constituent style blocks and collects an
`SkShaper::Feature[]` from each `TextStyle::getFontFeatures()`. Then:

1. Tries the user-specified typeface. Glyph IDs that come back as 0
   are recorded as "unresolved".
2. For each unresolved range, walks the `FontCollection`'s
   resolved-font cache (`fFallbackFonts`) looking for a previously-
   matched fallback for that `(unicode, style, locale, fontArgs)` key.
3. On miss, calls `FontCollection::defaultFallback` to obtain a new
   typeface and re-shapes the unresolved range.
4. Repeats until all glyphs are resolved or no further fallback
   exists.

`commitRunBuffer` materializes one `Run` per resolved sub-range and
appends it to `fParagraph->fRuns`.

There is a special `getEmojiSequenceStart` static that recognizes
Unicode TR-51 emoji sequences (ZWJ joins, regional indicators,
modifiers) so a single emoji glyph isn't broken across fallback
runs.

### `TextWrapper`

Greedy line breaker. Walks `fClusters` left-to-right, keeping a
`TextStretch` for the current line. Each cluster is added; when the
stretch's width exceeds the layout width:

- If the cluster is whitespace or a soft-break opportunity, the line
  is committed and the next stretch starts after it.
- Otherwise, the wrapper backs up to the last `kSoftLineBreakBefore`
  cluster and commits there. Hard breaks (`kHardLineBreakBefore`) are
  always honoured.

Trailing whitespace on a line becomes "ghost" — it still counts in
`widthWithGhostSpaces` (used for justification) but not in `width`.

When the line count would exceed `ParagraphStyle::getMaxLines()`, the
wrapper engages the ellipsis path: it computes the width of the
ellipsis text, pops clusters off the last line until the remaining
width plus the ellipsis fits, and replaces the popped clusters with a
synthesized `Run` for the ellipsis glyphs.

### `TextLine`

One per visual line. Holds:

- `SkVector fOffset`, `fAdvance` — origin and total advance.
- `ClusterRange fClusters` — clusters owned by this line (after the
  ghost-space clip).
- `InternalLineMetrics fSizes` — combined ascent/descent/leading.
- decoration paints (`std::vector<DecorationStyle>` keyed by run).

`paint()` walks the line's runs in *visual* order, calling
`ParagraphPainter::drawTextBlob` for the glyph runs, `drawFilledRect`
for backgrounds, and either `drawPath` (wavy) or `drawLine`/`drawRect`
for decorations.

### `Decorations` (`src/Decorations.cpp`)

Computes the underline/overline/strikethrough geometry per
`TextStyle`:

- Position is taken from the typeface's `SkFontMetrics`
  (`fUnderlinePosition`, `fStrikeoutPosition`, ascent for overline).
- Thickness is `font.thickness × fThicknessMultiplier`.
- Wavy lines build an `SkPath` of half-period sine bumps tiled across
  the line, then transformed by the line's local matrix.
- For `kGaps` mode, intercepts are queried from the line's blobs
  (`SkTextBlob::getIntercepts`) and the line is broken at those gaps.

---

## BiDi handling and RTL flow

SkParagraph delegates all BiDi work to [SkUnicode](skunicode.md):

1. `ParagraphImpl::computeCodeUnitProperties` calls
   `SkUnicode::getBidiRegions(utf8, utf8Units, paragraphDir, &regions)`.
   This returns `(start, end, level)` triples in *logical* order.
2. The `OneLineShaper` builds an `SkShaper::BiDiRunIterator` that
   exposes the level at each UTF-8 offset so the shaper can split runs
   at level boundaries.
3. The shaper produces glyphs in *logical* order (with each run's
   `fLevel` recorded).
4. `TextWrapper` line-breaks in logical order.
5. At paint time, `TextLine::iterateThroughVisualRuns` uses
   `SkUnicode::reorderVisual(levels, count, logicalFromVisual[])` to
   compute the visual ordering — see the L1-L4 summary in
   [SkUnicode](skunicode.md).
6. RTL runs render with their glyphs in left-to-right *visual* order
   (HarfBuzz already handed them to the shaper in logical order; the
   GPU/PDF code wants visual order, so the shaper output is reversed).

A simplified picture:

```
  Logical text:  "abc אבג def"
                 [---LTR----][--RTL-][--LTR-]
  BiDi levels:    0 0 0 0    1 1 1   0 0 0 0
  Visual order:  "abc גבא def"
                 [---LTR----][--RTL-][--LTR-]
                                ▲
                       glyphs are reversed here
                       so that the line still
                       reads left-to-right on screen
```

`getRectsForRange` and `getGlyphPositionAtCoordinate` operate in the
*visual* domain — the boxes for an RTL run start at the run's right
edge and grow leftward, but their `SkRect`s are still axis-aligned.

---

## Cross-references

- **Shaping** — every Run goes through [SkShaper](skshaper.md)
  (HarfBuzz). `TextStyle::FontFeature` becomes `SkShaper::Feature`.
- **Unicode** — line / word / grapheme breaks, BiDi levels, and
  visual reordering all come from [SkUnicode](skunicode.md).
- **Fonts** — `FontCollection`, `findTypefaces`, and `defaultFallback`
  build on [SkFontMgr / SkTypeface](text-and-fonts.md).
- **CanvasKit** — Paragraph is a first-class CanvasKit API; see
  [CanvasKit](canvaskit.md).
- **Skottie** — the Lottie text layer feeds shape-text into Skottie's
  layout (a small subset of paragraph features); see [Skottie](skottie.md).
