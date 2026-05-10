# CPU Rendering Pipeline

The CPU pipeline is what turns geometry plus paint into pixels in
software, without a GPU. It is the renderer behind any `SkCanvas`
created from `SkSurface::MakeRasterN32Premul` (or
`SkSurface::MakeRaster`), the renderer used by the PDF/SVG backends to
rasterize fallbacks, and the renderer that gold-image tests compare
against. For an alternative rendering path that uses the GPU see
[GPU Overview](gpu-overview.md).

The pipeline has three big stages:

1. **Geometry → coverage** — `SkScan_*` walks paths and produces either
   a binary or anti-aliased coverage mask, expressed as a stream of
   `(y, x_left, run_length, alpha)` calls into a `SkBlitter`.
2. **Color → pixel program** — `SkRasterPipeline` builds a chain of
   small SIMD stages (load source, apply shader, apply colour-filter,
   blend, store) once per draw.
3. **Coverage + pixel program → frame buffer** — `SkBlitter` glues
   them together: each scan-line of coverage drives one execution of
   the pixel program, multiplying its output by the coverage and
   storing the result.

```
                    ┌──────────────────────┐
                    │     SkBitmapDevice   │
                    │  (the SkDevice for a │
                    │   raster SkSurface)  │
                    └──────────┬───────────┘
                               │ drawRect/drawPath/drawText/drawImageRect
                               ▼
                  ┌────────────────────────┐
                  │  SkDraw                │ — pulls paint, matrix, clip,
                  │  (per-call scratch)    │   builds the right SkBlitter
                  └────────┬────────────┬──┘
                           │            │
            geometry path  │            │ pixel-program path
                           ▼            ▼
                ┌──────────────────┐  ┌──────────────────────────┐
                │  SkScan / Edges  │  │ SkRasterPipelineBlitter  │
                │  (AAA / supersa- │  │ (or specialised SkBlit-  │
                │   mple analytic) │  │  ter_A8 / _ARGB32 /      │
                │                  │  │  _Sprite for hot paths)  │
                └────────┬─────────┘  └────────────┬─────────────┘
                         │  alpha runs              │ load_*/srcover/
                         │  (y, x, len, α)          │ store_* SIMD ops
                         ▼                          ▼
                 ┌─────────────────┐       ┌───────────────────┐
                 │   SkBlitter     │──────►│   SkRasterPipeline│
                 │   blitH/blitAnti│       │  (compiled program)│
                 └────────┬────────┘       └─────────┬─────────┘
                          └────────────┬─────────────┘
                                       ▼
                              ┌─────────────────┐
                              │  destination    │
                              │  pixels         │
                              └─────────────────┘
```

---

## SkBitmapDevice — `src/core/SkBitmapDevice.{h,cpp}`

`SkBitmapDevice` is the `SkDevice` subclass used when the backing
surface is a raster `SkBitmap`. Every `SkCanvas::draw…` call routes
through the canvas's current device; for raster surfaces that lands in
`SkBitmapDevice::draw…`, which forwards to a stack-allocated `SkDraw`
helper. `SkDraw` consults the paint, computes the right blitter via
`SkBlitter::Choose`, and hands it to a `SkScan` routine for paths or
to a direct memcpy/blit for sprites.

`SkBitmapDevice` also owns the `SkRasterClip` (`src/core/SkRasterClip.h`)
that tracks both the integer rectangle clip and any anti-aliased
clip held in `SkAAClip`.

---

## SkRasterPipeline — `src/core/SkRasterPipeline.{h,cpp}`

> "SkRasterPipeline provides a cheap way to chain together a pixel
> processing pipeline … {N dst formats} × {M source formats} × {K mask
> formats} × {C transfer modes}. No one wants to write specialized
> routines for all those combinations." — `SkRasterPipeline.h`

A pipeline is an array of `SkRasterPipelineStage { fn, ctx }` records
allocated in an `SkArenaAlloc`. Each stage is a tiny function
operating on four-channel SIMD registers `r,g,b,a` (and dst `dr,dg,db,da`).
Stages tail-call (`[[clang::musttail]]` where available) into the
next stage, so the entire program runs in a single straight-line
sequence with no interpreter overhead.

Two parallel implementations exist:

| Variant | Channel type | Lanes (typical) | When picked |
|---|---|---|---|
| **lowp** | uint16 | 8 (NEON / SSE2) or 16 (AVX2) | Source and dest fit in 8-bit per channel; no float-only stages used. |
| **highp** | float | 4 (NEON / SSE) or 8 (AVX2) | Anything with F16 / F32 / extended range / sRGB / SkSL shader. |

`SkRasterPipeline::buildPipeline` chooses one or the other; if a
program contains an op that has no lowp variant, the pipeline falls
through to highp. The op enumeration and the `M(name)` X-macro lists
live in `src/core/SkRasterPipelineOpList.h`:

- `SK_RASTER_PIPELINE_OPS_LOWP(M)` — ops with both lowp and highp
  variants. Includes loads/stores for `a8 / 565 / 4444 / 8888 / rg88`,
  premul/unpremul, every classic Porter-Duff and separable blend mode
  (`srcover`, `dstover`, `xor_`, `multiply`, `screen`, `darken`,
  `lighten`, `overlay`, `hardlight`, `difference`, `exclusion`),
  matrix transforms up to 2×3, gradient evaluation, decal/clamp/mirror
  tile modes, and the fast `srcover_rgba_8888` specialization.
- `SK_RASTER_PIPELINE_OPS_HIGHP(M)` — adds float-only loads/stores
  (`f16`, `f32`, `1010102`, sRGB), HSL blend modes, perspective
  matrices, transfer functions, dithering, and bicubic sampling.
- `SK_RASTER_PIPELINE_OPS_SKSL(M)` — every instruction the SkSL
  raster-pipeline backend generates: lane masks, conditional
  branches, integer arithmetic, builtin maths, function call/return.
  This is how SkSL [Runtime Effects](runtime-effects.md) and
  [SkSL](sksl-shading-language.md) work on the CPU.

The convenience appenders on `SkRasterPipeline`
(`appendMatrix`, `appendConstantColor`, `appendLoad`,
`appendTransferFunction`, `appendStackRewind`) inspect their argument
and may pick a faster specialised op (e.g. `matrix_translate` rather
than the general `matrix_2x3`) — this is the layer where the pipeline
"recognises" common cases.

### Stage implementations — `src/opts/SkRasterPipeline_opts.h`

Every named op is implemented exactly once in
`src/opts/SkRasterPipeline_opts.h`, but that header is compiled
multiple times under different `-march` flags via the small
`SkOpts_ml*.cpp` shims in `src/opts/`. At runtime
`src/core/SkOpts.cpp` (just outside `src/opts/`) detects the host CPU
and binds the function pointer table for both the lowp and highp
variants:

| Compilation unit | Target | Notes |
|---|---|---|
| `SkOpts.cpp` (baseline) | SSE2 / NEON / scalar | The fallback that always builds. |
| `SkOpts_ml3.cpp` | AVX (x86) | Enabled when `__AVX__`. |
| `SkOpts_ml4.cpp` | AVX2 + FMA / Skylake | The fast path on modern Intel/AMD. |
| `SkOpts_lasx.cpp` | LoongArch LASX | Adds a LoongArch SIMD backend. |

`SkOpts_SetTarget.h` / `SkOpts_RestoreTarget.h` use
`#pragma GCC target` to switch ISA at file scope, so the same source
is recompiled into multiple symbol tables. The per-stage function
type (`using Stage = …` near the top of `SkRasterPipeline_opts.h`)
varies with lane count, which is why the `fn` field of
`SkRasterPipelineStage` is just a bare `void(*)()` — its real
signature depends on which build won CPU detection.

`src/opts/` also holds the SIMD specialisations for
`SkBitmapProcState_opts.h` (image-sampling kernels),
`SkBlitMask_opts.h` (mask-coverage applied to a row of pixels),
`SkBlitRow_opts.h` (full-row Porter-Duff blits), `SkMemset_opts.h`,
and the `SkSwizzler_opts.inc` channel-shuffles used by the image
codecs.

---

## SkBlitter — `src/core/SkBlitter.{h,cpp}`

A `SkBlitter` writes pixels for a single draw call. The base interface
is small:

```cpp
virtual void blitH(int x, int y, int width);             // solid run
virtual void blitAntiH(int x, int y,
                       const SkAlpha runs[],
                       const int16_t aa[]);             // RLE alpha runs
virtual void blitV(int x, int y, int height, SkAlpha);   // 1-px wide run
virtual void blitRect(int x, int y, int w, int h);       // solid rect
virtual void blitMask(const SkMask&, const SkIRect& clip);
virtual const SkPixmap* justAnOpaqueColor(uint32_t* value);
```

`SkBlitter::Choose` (in `SkBlitter.cpp`) inspects the destination's
`SkColorType`, the paint, and any active mask filter to pick a
concrete subclass:

| Subclass | File | Specialisation |
|---|---|---|
| `SkA8_Blitter` family | `SkBlitter_A8.{h,cpp}` | Alpha-only destinations (mask building). |
| `SkARGB32_…` family | `SkBlitter_ARGB32.cpp` | The hot path for premultiplied 8888 with one of a handful of common blends. |
| `SkSpriteBlitter_*` | `SkBlitter_Sprite.cpp` | Fast image-as-source rectangular blits with no shader transform. |
| `SkRasterPipelineBlitter` | `SkRasterPipelineBlitter.cpp` | The fully general fallback — any colour type, any shader, any blend, any colour filter. Builds a raster pipeline once at `Choose` time and re-runs it per scan-line. |

The hack `gSkForceRasterPipelineBlitter` (`SkBlitter.cpp` line 45)
forces every draw through the general pipeline — used in tests to make
sure no specialisation has diverged behaviour.

Mask filters (blurs, emboss) wrap the chosen blitter via
`SkMaskFilterBase::filterMask` to interpose another mask-generation
step before pixels reach the destination.

---

## Scan conversion — `src/core/SkScan*.{h,cpp}`

`SkScan` is a namespace of free functions that walks geometry and
calls into a blitter. The four main entry points:

| Function | Source file | Purpose |
|---|---|---|
| `SkScan::FillPath` | `SkScan_Path.cpp` | Aliased path fill via integer edges. |
| `SkScan::AntiFillPath` | `SkScan_AntiPath.cpp` | Supersampled (4×4) anti-aliased fill — the historical default. |
| `SkScan::AAAFillPath` | `SkScan_AAAPath.cpp` | "Analytic Anti-Aliasing" — exact coverage via the trapezoid algorithm; usually faster and more accurate than supersampling. The default since 2018, controlled by `SkScanPriv.h`. |
| `SkScan::HairLine*` / `AntiHairLine*` | `SkScan_Hairline.cpp`, `SkScan_Antihair.cpp` | One-pixel-wide stroke rasterizers. |

Edges feeding the path scanners come from `SkEdgeBuilder`
(`SkEdgeBuilder.{h,cpp}`), which tessellates a path's quads, conics,
and cubics into either integer (`SkEdge`) or analytic
(`SkAnalyticEdge`) edges, with `SkEdgeClipper` enforcing the device
bounds. The supersampled path uses an 8.8 fixed-point grid (see the
`SK_SUPERSAMPLE_SHIFT` references in `SkScan_AntiPath.cpp`); the
analytic path uses double-precision X-intersections per scan-line
edge segment.

Output for AA fills is a stream of `(left, right, alpha)` triples per
scan-line, which `SkBlitter::blitAntiH` turns into either pixel
writes or further mask accumulation when building a clip.

---

## SkAAClip — `src/core/SkAAClip.{h,cpp}`

`SkAAClip` is the canonical run-length-encoded representation of an
anti-aliased clip. A path is rasterized into an `SkAAClip` once,
producing a compact RLE mask stored as runs of `(width, alpha)` per
scan-line. The clip is then evaluated against the current draw by
intersecting its RLE rows with each call's coverage runs — much
cheaper than re-rasterising the clip path per draw.

`SkAAClip` is owned by `SkRasterClip` together with the integer-rect
clip; the combination drives `SkRasterClipBlitter`, a wrapper blitter
that masks every output run by the active AA clip before it reaches
the underlying blitter.

---

## Putting it together: a single `drawPath`

For `canvas->drawPath(p, paint)` on a raster surface:

1. `SkBitmapDevice::drawPath` builds a stack `SkDraw` with the device
   pixmap, the current matrix, and the `SkRasterClip`.
2. `SkBlitter::Choose` looks at the paint and destination. If the
   shader is solid 8888 with `SrcOver`, you get
   `SkARGB32_Blitter`; for anything more complex,
   `SkRasterPipelineBlitter` is constructed — it appends colour ops
   (gradient/texture sample, colour filter, blend mode, store_8888)
   into a fresh `SkRasterPipeline`.
3. `SkScan::AAAFillPath` walks the path's edges, producing AA runs.
4. Each run goes through the AA-clip mask, then to `blitAntiH`, which
   loads dst pixels, runs the cached pipeline with the coverage as a
   `scale_native` factor, and stores the result.

For a fully opaque rect blit, the pipeline collapses to
`uniform_color → srcover_rgba_8888 → store_8888`, three SIMD stages
that the lowp variant runs at one cache-line per cycle on modern
hardware.

---

## Cross-references

- [GPU Overview](gpu-overview.md) — the same `SkCanvas` API but
  rasterized with tessellation and fragment shaders instead of
  scan-line edges and SIMD stages.
- [Color Management](color-management.md) — how `appendTransferFunction`
  and the sRGB / wide-gamut store ops inter-operate with `skcms` to
  perform colour-space conversion inside the pipeline.
- [Paint, Color & Blending](paint-color-and-blending.md) — the
  `SkBlendMode` enum and how Porter-Duff modes map to lowp ops.
- [Path Operations](path-operations.md) — `SkPath` boolean operations
  whose output feeds the scan converter.
- [Geometry & Math](geometry-and-math.md) — the verb stream and curve
  types consumed by `SkEdgeBuilder`.
- [Runtime Effects](runtime-effects.md) and
  [SkSL](sksl-shading-language.md) — SkSL programs are translated to
  the `SK_RASTER_PIPELINE_OPS_SKSL` opcode set and run inside this
  same pipeline on the CPU.
