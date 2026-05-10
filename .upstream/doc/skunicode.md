# SkUnicode — Unicode Services

`modules/skunicode/` is Skia's Unicode-property layer. It is the
*only* part of the text stack that talks to ICU (or its Rust
counterpart ICU4X). Everything else — [SkShaper](skshaper.md),
[SkParagraph](skparagraph.md) — depends on this module for:

- per-code-unit *flags* (grapheme start, soft / hard line break,
  whitespace, control, ideographic, emoji, …);
- bidirectional algorithm: per-position embedding **levels** and
  **visual reordering** of run-level arrays;
- segmentation: **words**, **graphemes**, **lines**, **sentences**;
- a small set of code-point predicates (`isControl`, `isWhitespace`,
  `isEmoji`, …) and UTF-8 ↔ UTF-16 conversion helpers.

Multiple backends implement the same `SkUnicode` interface so the
embedder can pick the best fit for its size / dependency budget.

| Backend (`SkUnicodes::…`) | Source | Notes |
|---|---|---|
| `ICU::Make()`                 | `src/SkUnicode_icu.cpp`         | Full ICU. Ships everything. |
| `ICU4X::Make()`               | `src/SkUnicode_icu4x.cpp`       | Rust [ICU4X](https://github.com/unicode-org/icu4x) bindings, smaller binary. |
| `Libgrapheme::Make()`         | `src/SkUnicode_libgrapheme.cpp` | suckless [libgrapheme](https://libs.suckless.org/libgrapheme/) for graphemes / words. |
| `Client::Make(…)`             | `src/SkUnicode_client.cpp`     | Embedder-supplied breaks/words; no Unicode dataset of its own. |
| `Bidi::Make()`                | `src/SkUnicode_bidi.cpp`        | Bidi-only subset of ICU plus hardcoded character properties. |

## Pipeline

```
   raw UTF-8 / UTF-16
          │
          ▼
   ┌────────────────────────┐
   │ computeCodeUnitFlags   │   per code unit, an OR of CodeUnitFlags
   │  (CodeUnitFlags[])     │   { kGraphemeStart, kSoftLineBreakBefore,
   └─────────┬──────────────┘     kHardLineBreakBefore, kPartOfWhiteSpace,
             │                    kControl, kIdeographic, kEmoji, ... }
             ▼
   ┌────────────────────────┐
   │ getBidiRegions         │   logical-order (start, end, level) tuples
   │  (BidiRegion[])        │   level 0 = LTR base, 1 = RTL embedding, ...
   └─────────┬──────────────┘
             ▼
   ┌────────────────────────┐
   │ getWords / getSentences│   Position[] (UTF-16 boundaries)
   │ getUtf8Words           │   used by SkParagraph::getWordBoundary
   └────────────────────────┘

  At paint time:
   ┌────────────────────────┐
   │ reorderVisual(levels,  │   logicalFromVisual[i] = which logical run
   │  count, mapping[])     │   appears at visual position i
   └────────────────────────┘
```

The interface lives in `include/SkUnicode.h`; backend factories live
in their own headers and the implementations all extend the same
`SkUnicode` base.

---

## SkBidiIterator — `include/SkUnicode.h`

```cpp
class SKUNICODE_API SkBidiIterator {
public:
    typedef int32_t Position;
    typedef uint8_t Level;
    struct Region {
        Region(Position start, Position end, Level level)
            : start(start), end(end), level(level) {}
        Position start;
        Position end;
        Level    level;
    };
    enum Direction { kLTR, kRTL };

    virtual Position getLength()                = 0;
    virtual Level    getLevelAt(Position)       = 0;
};
```

Wraps an ICU `UBiDi` (or ICU4X `Bidi`). `getLevelAt` returns the BiDi
embedding level at a given UTF-16 / UTF-8 position; even values are
left-to-right embedding, odd are right-to-left. The shaper splits runs
at level boundaries; the paragraph painter calls `reorderVisual` on
the resulting per-run levels.

---

## SkBreakIterator

```cpp
class SKUNICODE_API SkBreakIterator {
public:
    typedef int32_t Position;
    typedef int32_t Status;

    virtual Position first()                              = 0;
    virtual Position current()                            = 0;
    virtual Position next()                               = 0;
    virtual Status   status()                             = 0;   // ICU rule status, e.g. UBRK_LINE_HARD
    virtual bool     isDone()                             = 0;
    virtual bool     setText(const char     utftext8 [], int utf8Units)  = 0;
    virtual bool     setText(const char16_t utftext16[], int utf16Units) = 0;
};
```

Forwarder around `icu::BreakIterator` (or libgrapheme / ICU4X). The
ICU implementation in `src/SkUnicode_icu.cpp` keeps a small global
LRU of break iterators keyed by `(BreakType, locale)` since
constructing one from scratch is expensive.

`status()` lets the consumer distinguish a `UBRK_LINE_HARD` break
(must break here) from a `UBRK_LINE_SOFT` one (may break here). For
graphemes, the status mirrors ICU's grapheme-cluster categories.

---

## SkUnicode — the central interface

```cpp
class SKUNICODE_API SkUnicode : public SkRefCnt {
public:
    enum CodeUnitFlags {
        kNoCodeUnitFlag        = 0x000,
        kPartOfWhiteSpaceBreak = 0x001,
        kGraphemeStart         = 0x002,
        kSoftLineBreakBefore   = 0x004,
        kHardLineBreakBefore   = 0x008,
        kPartOfIntraWordBreak  = 0x010,
        kControl               = 0x020,
        kTabulation            = 0x040,
        kGlyphClusterStart     = 0x080,
        kIdeographic           = 0x100,
        kEmoji                 = 0x200,
        kWordBreak             = 0x400,
        kSentenceBreak         = 0x800,
    };

    enum class TextDirection { kLTR, kRTL };
    enum class LineBreakType { kSoftLineBreak = 0, kHardLineBreak = 100 };
    enum class BreakType     { kWords, kGraphemes, kLines, kSentences };

    typedef size_t  Position;
    typedef uint8_t BidiLevel;
    struct BidiRegion       { Position start, end; BidiLevel level; };
    struct LineBreakBefore  { Position pos; LineBreakType breakType; };
    // ...
};
```

`CodeUnitFlags` is a bit-mask (`is_bitmask_enum<>`); a single code
unit can be (for example) both `kGraphemeStart | kSoftLineBreakBefore`.

### CodeUnitFlags semantics

| Flag                          | Meaning                                                    |
|-------------------------------|------------------------------------------------------------|
| `kPartOfWhiteSpaceBreak`      | code unit is in a run of breakable whitespace              |
| `kPartOfIntraWordBreak`       | space-like character that does **not** end a word          |
| `kGraphemeStart`              | this position begins a grapheme cluster (user character)   |
| `kSoftLineBreakBefore`        | line may break before this position                        |
| `kHardLineBreakBefore`        | line **must** break before this position                   |
| `kControl`                    | code point is a control character (Cc / Cf)                |
| `kTabulation`                 | the tab character `'\t'`                                   |
| `kGlyphClusterStart`          | populated by SkParagraph after shaping (not by SkUnicode)  |
| `kIdeographic`                | Han / Hangul / CJK class                                    |
| `kEmoji`                      | emoji code point                                            |
| `kWordBreak`                  | a word boundary precedes this position                     |
| `kSentenceBreak`              | a sentence boundary precedes this position                 |

### Predicates

The base `SkUnicode` declares pure-virtual checks that every backend
implements; ports without a Unicode dataset inherit them from
`SkUnicodeHardCodedCharProperties` (`src/SkUnicode_hardcoded.cpp`),
which uses static lookup tables:

```cpp
virtual bool isControl          (SkUnichar) = 0;
virtual bool isWhitespace       (SkUnichar) = 0;   // breakable whitespace
virtual bool isSpace            (SkUnichar) = 0;   // any space char (incl. NBSP)
virtual bool isTabulation       (SkUnichar) = 0;
virtual bool isHardBreak        (SkUnichar) = 0;
virtual bool isEmoji            (SkUnichar) = 0;
virtual bool isEmojiComponent   (SkUnichar) = 0;
virtual bool isEmojiModifier    (SkUnichar) = 0;
virtual bool isEmojiModifierBase(SkUnichar) = 0;
virtual bool isRegionalIndicator(SkUnichar) = 0;
virtual bool isIdeographic      (SkUnichar) = 0;
```

The hardcoded backend recognises a curated list of ~25 spaces,
~21 whitespaces (a strict subset of spaces — NBSP and figure space
are *not* breakable), the ASCII tab, and `'\n'` / `U+2028` for hard
breaks. It rejects all emoji checks (so embedders that do not link
ICU should not rely on them).

### Iterator factories

```cpp
virtual std::unique_ptr<SkBidiIterator>  makeBidiIterator(const uint16_t text[], int count, SkBidiIterator::Direction) = 0;
virtual std::unique_ptr<SkBidiIterator>  makeBidiIterator(const char    text[], int count, SkBidiIterator::Direction) = 0;
virtual std::unique_ptr<SkBreakIterator> makeBreakIterator(const char locale[], BreakType) = 0;
virtual std::unique_ptr<SkBreakIterator> makeBreakIterator(BreakType type)               = 0;
```

`makeBreakIterator(type)` (no locale) uses the host's default locale.

### Static helpers

```cpp
static bool hasTabulationFlag        (SkUnicode::CodeUnitFlags);
static bool hasHardLineBreakFlag     (SkUnicode::CodeUnitFlags);
static bool hasSoftLineBreakFlag     (SkUnicode::CodeUnitFlags);
static bool hasGraphemeStartFlag     (SkUnicode::CodeUnitFlags);
static bool hasControlFlag           (SkUnicode::CodeUnitFlags);
static bool hasPartOfWhiteSpaceBreakFlag(SkUnicode::CodeUnitFlags);
```

UTF conversion (always present, doesn't need ICU):

```cpp
static SkString       convertUtf16ToUtf8(const char16_t*, int);
static SkString       convertUtf16ToUtf8(const std::u16string&);
static std::u16string convertUtf8ToUtf16(const char*, int);
static std::u16string convertUtf8ToUtf16(const SkString&);
```

These use `SkUTF` (`src/base/SkUTF.h`) and validate the input.

### `extractUtfConversionMapping` / `forEachCodepoint`

A pair of templated helpers (defined inline in the header) for walking
text and synchronising UTF-8 ↔ UTF-16 indices — used by SkParagraph's
`ensureUTF16Mapping`. They take generic `Appender8` / `Appender16` /
`Callback` functors so callers can populate any container without
copying.

```cpp
template <typename A8, typename A16>
static bool extractUtfConversionMapping(SkSpan<const char> utf8,
                                        A8&& appender8, A16&& appender16);

template <typename C> void forEachCodepoint(const char*    , int32_t, C&&);
template <typename C> void forEachCodepoint(const char16_t*, int32_t, C&&);

template <typename C> void forEachBidiRegion(const uint16_t utf16[], int utf16Units,
                                             SkBidiIterator::Direction, C&&);

template <typename C> void forEachBreak(const char16_t utf16[], int utf16Units,
                                        SkUnicode::BreakType, C&&);
```

---

## `computeCodeUnitFlags` — the work-horse

The single most important method. SkParagraph calls this once per
paragraph layout to obtain a per-code-unit flag array.

```cpp
virtual bool computeCodeUnitFlags(
        char    utf8 [], int  utf8Units, bool replaceTabs,
        skia_private::TArray<SkUnicode::CodeUnitFlags, true>* results) = 0;

virtual bool computeCodeUnitFlags(
        char16_t utf16[], int utf16Units, bool replaceTabs,
        skia_private::TArray<SkUnicode::CodeUnitFlags, true>* results) = 0;
```

`results` is sized to `nUnits + 1` (sentinel slot at the end). Each
slot is the OR of all `CodeUnitFlags` derived from that code unit.

The ICU implementation:

1. Resets `results` and pushes `nUnits + 1` empty flags.
2. Constructs an `icu::BreakIterator` for line breaks; iterates and
   sets `kSoftLineBreakBefore` (when `status` is `UBRK_LINE_SOFT`) or
   `kHardLineBreakBefore` (when `status >= UBRK_LINE_HARD`).
3. Constructs a grapheme break iterator; sets `kGraphemeStart` at each
   boundary.
4. Walks every code unit, asking the per-codepoint predicates
   (`isWhitespace`, `isControl`, `isIdeographic`, `isEmoji`,
   `isTabulation`) and OR-ing the corresponding flags.
5. If `replaceTabs` is true, replaces `'\t'` with `' '` in `utf8` and
   sets `kTabulation` so the renderer can later style tab as a space.

The Bidi-only subset (`SkUnicode_bidi.cpp`) implements only space /
whitespace / control / ideographic flags from the hardcoded
properties; line / grapheme breaks return false (and SkParagraph must
be told via the Client backend).

---

## `getBidiRegions` — paragraph-level bidi resolution

```cpp
static bool extractBidi(const char utf8[], int utf8Units,
                        TextDirection,
                        std::vector<BidiRegion>* bidiRegions);

virtual bool getBidiRegions(const char utf8[], int utf8Units,
                            TextDirection dir,
                            std::vector<BidiRegion>* results) = 0;
```

The result is a list of `BidiRegion(start, end, level)` covering the
entire input in *logical* order. `dir` is the paragraph's base
direction (kLTR ⇒ paragraph level 0, kRTL ⇒ level 1).

The ICU backend opens a `UBiDi`, calls `ubidi_setPara(text, length,
paraLevel, embeddingLevels=nullptr)`, walks `ubidi_countRuns` times
calling `ubidi_getVisualRun`, and emits one `BidiRegion` per run. The
Bidi-subset backend uses a smaller bundled ICU subset
(`src/SkBidiFactory_icu_subset.cpp`) that exposes only `ubidi_*`.

The `SkBidiFactory` interface (`src/SkUnicode_icu_bidi.h`) is the
abstraction over "full ICU" vs "subset ICU" vs "no ICU at all"; it
supplies `bidi_close_callback`, `bidi_openSized`, `bidi_setPara`,
`bidi_getDirection`, `bidi_getLength`, `bidi_getLevelAt`, and
`bidi_reorderVisual`.

---

## `getWords` / `getUtf8Words` / `getSentences`

```cpp
virtual bool getWords    (const char utf8[], int utf8Units, const char* locale,
                          std::vector<Position>* results) = 0;  // UTF-16 positions
virtual bool getUtf8Words(const char utf8[], int utf8Units, const char* locale,
                          std::vector<Position>* results) = 0;  // UTF-8  positions
virtual bool getSentences(const char utf8[], int utf8Units, const char* locale,
                          std::vector<Position>* results) = 0;  // UTF-16 positions
```

Returns word boundary positions *between* characters in either UTF-16
or UTF-8 byte units. SkParagraph uses `getWords` for its
`getWordBoundary(offset)` API. `getUtf8Words` is a convenience for
embedders that operate in UTF-8.

---

## `reorderVisual` — visual reordering

```cpp
virtual void reorderVisual(const BidiLevel runLevels[],
                           int             levelsCount,
                           int32_t         logicalFromVisual[]) = 0;
```

Given a per-run array of embedding levels in *logical* order, fills
`logicalFromVisual[]` so `logicalFromVisual[v]` is the *logical* index
of the run that should appear at *visual* position `v`. This is a
direct wrapper around ICU's `ubidi_reorderVisual` and is the
implementation of Unicode UBA rules **L1-L4** at the run level (not
the full code-point implementation, which is in `getBidiRegions`).

### UBA L1-L4 summary

The Unicode Bidirectional Algorithm has multiple stages; visual
reordering (the L-rules) is what `reorderVisual` covers:

```
  Logical  l e v e l s   →   Visual order
  ─────────────────────       ──────────────────────
  L1: Reset levels of certain  whitespace characters before paragraph end
       to the paragraph level (so trailing spaces don't get dragged into RTL).
  L2: Find the highest level in the line; reverse contiguous runs whose
       level is ≥ that highest level. Then drop the level by 1 and repeat
       until you hit the paragraph level.
       (Net effect: each odd-level run is reversed, then within those
        groups every nested odd region is re-reversed, etc.)
  L3: Combining marks are kept attached to their base — they move with
       their cluster.
  L4: Mirrored characters (parens, brackets, quotes) are replaced with
       their mirrored glyphs in RTL contexts (handled by the shaper).
```

Worked example, two LTR runs around one RTL run inside an LTR paragraph:

```
  Logical run levels :  [ 0, 1, 0 ]      (paragraph level = 0)

  Step 1: highest level = 1. Reverse the contiguous span at level ≥ 1.
          → [ 0, 1, 0 ]  (single-element span, no visible change)
  Step 2: drop to level 0. Reverse spans at level ≥ 0  → entire array
          → [ 0, 1, 0 ]
  Stop.

  logicalFromVisual = [ 0, 1, 2 ]  (no swap)
```

Now nest RTL inside RTL (e.g. an Arabic phrase containing a Hebrew
inline):

```
  Logical run levels :  [ 1, 2, 1 ]      (paragraph level = 1)

  Step 1: highest level = 2. Reverse [1] → [1]
          → [ 1, 2, 1 ]
  Step 2: drop to level 1. Reverse [0,1,2] → [2,1,0]
          → after swap of indices: visual = [ logical2, logical1, logical0 ]

  logicalFromVisual = [ 2, 1, 0 ]
```

`SkShaper`'s `emit()` and `SkParagraph`'s `iterateThroughVisualRuns`
both call `reorderVisual` with the line's per-run level array and use
`logicalFromVisual` to walk runs in display order.

---

## ICU backend — `src/SkUnicode_icu.cpp`

The reference implementation. Key aspects:

- All ICU calls are routed through `SkICULib` (`SkUnicode_icupriv.h`),
  a function-pointer table that lets Skia load ICU at runtime via
  `SkLoadICU` (used on Windows so the data file can ship beside the
  binary).
- `SkBreakIterator_icu` wraps a `UBreakIterator` and a `UText`. The
  `setText` overloads create a UText from UTF-8 (`utext_openUTF8`) or
  UTF-16 (`utext_openUChars`).
- `SkIcuBreakIteratorCache` keeps an LRU of break iterators keyed by
  `(BreakType, locale)`. The cache caps at ~100 requests / 4 live
  iterators to keep memory under control.
- `SkBidiFactory_icu_full` provides the BiDi-only entrypoints used by
  the BiDi subset backend, by linking to the full ICU `ubidi_*`
  family.

ICU data file: the implementation expects a `icudtNN.dat` (or compiled-
in static data) reachable through `u_setDataDirectory` / the linked
library. Without the data file at runtime, `Make()` will succeed but
all Unicode-property lookups will fail — the embedder is expected to
ship one (`third_party/icu/`).

---

## ICU4X backend — `src/SkUnicode_icu4x.cpp`

An alternate backend backed by Rust [ICU4X](https://github.com/unicode-org/icu4x)
via its `cpp` bindings (`ICU4XBidi`, `ICU4XCaseMapper`,
`ICU4XCodePointMapData8`, `ICU4XCodePointSetData`,
`ICU4XGraphemeClusterSegmenter`, `ICU4XLineSegmenter`,
`ICU4XWordSegmenter`).

Why bother:

- Smaller binary footprint than full ICU.
- Statically-linked Rust data — no separate `.dat` file to ship.
- Same public `SkUnicode` interface.

The constructor preloads the relevant code-point sets / line-break
data once per process. `isControl`, `isWhitespace`, `isEmoji`, etc.
become a single `set.contains(unichar)` call. Hard-break detection
walks the LineBreak property table for `MandatoryBreak`,
`CarriageReturn`, `LineFeed`, `NextLine`.

---

## Libgrapheme backend — `src/SkUnicode_libgrapheme.cpp`

Very small (~12 KB) backend using suckless's
[libgrapheme](https://libs.suckless.org/libgrapheme/). Implements
graphemes / words / sentences via `grapheme_next_character_break`,
`grapheme_next_word_break`, etc. Emoji predicates fall through to the
hardcoded properties, BiDi falls through to the BiDi subset backend.

Useful for environments where licensing or size rules out ICU and
ICU4X but you still want non-trivial text segmentation.

---

## Client backend — `src/SkUnicode_client.cpp`

```cpp
namespace SkUnicodes::Client {
SKUNICODE_API sk_sp<SkUnicode> Make(
        SkSpan<char>                                    text,
        std::vector<SkUnicode::Position>                 words,
        std::vector<SkUnicode::Position>                 graphemeBreaks,
        std::vector<SkUnicode::LineBreakBefore>          lineBreaks);
}
```

Builds a `SkUnicode` whose word / grapheme / line-break data is
*supplied by the embedder*. This is how Flutter wires its own ICU
build into Skia: the engine computes word/grapheme/line break
positions in Dart land using its bundled ICU and feeds them in here.

The `ParagraphBuilder` "Client" overloads (`setWordsUtf8`,
`setGraphemeBreaksUtf8`, `setLineBreaksUtf8`, `SetUnicode`) are the
matching public entry points.

---

## Bidi-only backend — `src/SkUnicode_bidi.cpp`

A minimal backend that exists for builds where you need *only* BiDi
plus hardcoded character predicates — typical in game engines that do
their own segmentation but want Skia to handle bidirectional reordering.

```cpp
class SkUnicode_bidi : public SkUnicodeHardCodedCharProperties {
    sk_sp<SkBidiFactory> fBidiFact = sk_make_sp<SkBidiSubsetFactory>();
};
```

It implements:

- `makeBidiIterator(uint16_t)` and `getBidiRegions` via
  `SkBidiSubsetFactory` (~140 KB of subset ICU compiled in).
- `reorderVisual` via the same subset.
- `computeCodeUnitFlags(char16_t)` using only space / control /
  whitespace / ideographic flags from the hardcoded properties.
- `makeBidiIterator(char)`, `makeBreakIterator`, `getWords`,
  `getSentences`, `toUpper`, and the UTF-8 `computeCodeUnitFlags` are
  unimplemented (`SkDEBUGF` + return false).

---

## SkBidiFactory — `src/SkUnicode_icu_bidi.h`

The thin abstraction that lets a single C++ caller choose between
"full ICU" and "subset ICU" implementations of the BiDi algorithm:

```cpp
class SkBidiFactory : public SkRefCnt {
public:
    std::unique_ptr<SkBidiIterator> MakeIterator(const uint16_t utf16[],
                                                 int utf16Units,
                                                 SkBidiIterator::Direction) const;
    std::unique_ptr<SkBidiIterator> MakeIterator(const char utf8[],
                                                 int utf8Units,
                                                 SkBidiIterator::Direction) const;
    bool ExtractBidi(const char utf8[], int utf8Units,
                     SkUnicode::TextDirection,
                     std::vector<SkUnicode::BidiRegion>*) const;

    virtual const char*           errorName(UErrorCode) const = 0;
    using BidiCloseCallback = void(*)(UBiDi*);
    virtual BidiCloseCallback     bidi_close_callback() const = 0;
    virtual UBiDiDirection        bidi_getDirection(const UBiDi*) const = 0;
    virtual SkBidiIterator::Position bidi_getLength(const UBiDi*) const = 0;
    virtual SkBidiIterator::Level    bidi_getLevelAt(const UBiDi*, int pos) const = 0;
    virtual UBiDi*                bidi_openSized(int32_t maxLength, int32_t maxRunCount,
                                                 UErrorCode*) const = 0;
    virtual void                  bidi_setPara(UBiDi*, const UChar* text, int32_t length,
                                               UBiDiLevel paraLevel, UBiDiLevel* embeddings,
                                               UErrorCode*) const = 0;
    virtual void                  bidi_reorderVisual(const SkUnicode::BidiLevel[],
                                                     int levelsCount,
                                                     int32_t logicalFromVisual[]) const = 0;
};
```

Two concrete factories live next to it:

- `SkBidiFullFactory` (`src/SkBidiFactory_icu_full.cpp`) — calls into
  the full linked ICU.
- `SkBidiSubsetFactory` (`src/SkBidiFactory_icu_subset.cpp`) — calls
  into a vendored subset of ICU containing only the `ubidi_*` family
  (~140 KB).

`SkUnicode_bidi`, `ICU::Make`, and `Client::Make` all pick a different
factory.

---

## Hardcoded character properties — `src/SkUnicode_hardcoded.cpp`

`SkUnicodeHardCodedCharProperties` is the minimal predicate base used
by the Bidi-only and Client backends. It uses small `std::array`
lookup tables for spaces, whitespaces, and ideographic ranges:

```cpp
bool isControl   (SkUnichar);    // c < ' ' || (c in [0x7F..0x9F]) || ZWJ/ZWNJ/LRM/RLM/RLE etc.
bool isWhitespace(SkUnichar);    // 21 BREAKABLE spaces (NBSP and figure space excluded)
bool isSpace     (SkUnichar);    // 25 spaces (includes NBSP, figure space, NARROW-NB-SPACE)
bool isTabulation(SkUnichar);    // == '\t'
bool isHardBreak (SkUnichar);    // == '\n' || == U+2028
bool isIdeographic(SkUnichar);   // CJK_Radicals, Hangul, Phags_Pa, ... 8 ranges

// All emoji-related predicates DEBUGFAIL — embedders need a real backend for emoji.
```

The list is hand-curated to match the typical "what counts as a space
for line breaking" set — note the deliberate exclusions of NBSP
(0xA0), figure space (0x2007), and narrow no-break space (0x202F),
which are intentionally non-breakable.

---

## Cross-references

- **Shaping** — [SkShaper](skshaper.md) builds `SkBidiIterator` and
  `SkBreakIterator` instances through these factories.
- **Paragraph layout** — [SkParagraph](skparagraph.md) calls
  `computeCodeUnitFlags`, `getBidiRegions`, `getWords`, and
  `reorderVisual` once per layout pass.
- **Third-party deps** — see [Third-Party Dependencies](third-party-deps.md)
  for the ICU and ICU4X vendoring story.
- **CanvasKit** — bundles the ICU build that backs `SkUnicode::ICU`
  inside the WASM blob; see [CanvasKit](canvaskit.md).
