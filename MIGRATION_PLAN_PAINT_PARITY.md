# Migration plan — `SkPaint` parity with Skia C++

**Status** : Phase 1 (audit) — open
**Started** : 2026-05-05
**Trigger** : `BatchedConvexPathsGM` 34.94 % similarity. Investigation traced the drift to alpha precision loss in `SkBitmapDevice.colorToF16Premul` — root cause is **storage**, not raster: our `SkPaint` keeps colour as a packed 32-bit `SkColor` while Skia stores `SkColor4f` (4 floats) as the source of truth. `setAlphaf(0.3f)` therefore quantises to `77/255 ≈ 0.30196` in our port and produces visible drift (max 26/255 per channel) once the F16 working space tries to round-trip back.

**Reference sources** (read-only, primary spec):
- `include/core/SkPaint.h` — public surface.
- `src/core/SkPaint.cpp` — setter / getter semantics, defaults.
- `src/core/SkPaintPriv.h` + `.cpp` — `Overwrites`, `ShouldDither`, `ComputeLuminanceColor`, `RemoveColorFilter`.
- `src/core/SkPaintDefaults.h` — `kDefault_MiterLimit = 4`.

> Same methodology as [archives/MIGRATION_PLAN_PATH_PARITY.md](archives/MIGRATION_PLAN_PATH_PARITY.md) : Phase 1 documents divergences without code changes, Phase 2 onward fixes them slice-by-slice with one PR per slice and ratchet checks at every step.

---

## Phase 1 — API parity audit (this PR)

Document only, no code changes. Catalogues every divergence between `kanvas-skia/src/main/kotlin/org/skia/foundation/SkPaint.kt` and `include/core/SkPaint.h` + `src/core/SkPaint.cpp`, classed by severity.

### A. Storage divergences — **HIGH** (root cause of `BatchedConvexPathsGM`)

| #   | Skia C++                                                                 | Kotlin port                                                          | Impact                                                                                   |
| --- | ------------------------------------------------------------------------ | -------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| A1  | `SkColor4f fColor4f` — 4 floats, source of truth                         | `var color: SkColor` — packed 32-bit ARGB int                        | `setAlphaf(0.3f)` quantised to byte (`77/255 ≈ 0.30196` vs Skia `0.3` exact)             |
| A2  | `setAlphaf(a) → fColor4f.fA = SkTPin(a, 0, 1)` — **direct float write**  | `alphaf` setter rounds to byte: `(a*255+0.5).toInt()` then stores    | Up to `0.5/255 ≈ 0.196 %` drift per channel; compounds through F16 raster pipeline       |
| A3  | `setColor(SkColor4f, SkColorSpace*) → SkColorSpaceXformSteps + pinAlpha` | `color4f` setter → lossy `toSkColor()` (no colour-space xform)       | No colour-space transform applied; setter is lossy round-trip                            |
| A4  | `getColor4f() → fColor4f` — direct                                       | `color4f` getter → recompute via `SkColor4f.FromColor(color)`        | Lossy on every read; impossible to recover the float value passed to `setAlphaf`         |
| A5  | `SkPaint() : fColor4f{0,0,0,1}` — opaque black float-init                | `var color: SkColor = SK_ColorBLACK` — equivalent end-state          | Same default, but Skia's init bypasses int → float conversion                            |

**Why this matters in practice** : `BatchedConvexPathsGM` calls `paint.setAlphaf(0.3f)`. In Skia, the F16 raster pipeline reads `fColor4f.fA = 0.3f` directly. In our port, it reads `SkColorGetA(paint.color) / 255f = 76/255 = 0.298f`. Multiplied across 50 convex paths blended with `kSrcOver`, the cumulative drift is the 34.94 % score we measure today.

### B. Missing API surface — **MEDIUM**

| #   | Skia C++                                                                  | Kotlin port                                          | Impact                                                                                  |
| --- | ------------------------------------------------------------------------- | ---------------------------------------------------- | --------------------------------------------------------------------------------------- |
| B1  | `explicit SkPaint(const SkColor4f&, SkColorSpace*)` constructor           | only `constructor(SkColor)`                          | No way to construct from float colour                                                   |
| B2  | `setColor4f(const SkColor4f&, SkColorSpace*)` — explicit setter           | only computed `var color4f` property                 | Naming mismatch with C++ call-sites; setter is lossy (see A3)                           |
| B3  | `setStrokeWidth(SkScalar)` silently rejects `< 0`                         | `var strokeWidth: SkScalar = 0f` — accepts anything  | Negative width can corrupt strokes (untested today)                                     |
| B4  | `setStrokeMiter(SkScalar)` silently rejects `< 0`                         | `var strokeMiter: SkScalar = 4f` — accepts anything  | Negative miter can corrupt joins (untested today)                                       |
| B5  | `nothingToDraw()` includes `kDst` short-circuit                           | omits `kDst` (returns `false`)                       | Misses some no-op cases; minor overdraw cost only                                       |
| B6  | `nothingToDraw()` checks `affects_alpha(colorFilter)` / `imageFilter`     | guarded only by `shader != null`                     | Conservative correct, but not iso (filters not yet ported — see C1/C4)                  |
| B7  | `canComputeFastBounds()` / `computeFastBounds()` / `doComputeFastBounds()`| absent                                               | No `quickReject` support; no `SkStrokeRec::GetInflationRadius` plumbed                  |
| B8  | `SkPaintPriv::Overwrites` / `ShouldDither` / `ComputeLuminanceColor`      | absent                                               | Used by Skia for opaque-fill optimisations and gamma-correct glyph caching              |
| B9  | `SkPaintPriv::RemoveColorFilter`                                          | absent                                               | Folds `SkColorFilter` into shader/colour pre-render — depends on C1 first               |
| B10 | `SkPaintPriv::Flatten` / `Unflatten`                                      | absent                                               | Skia's serialization path — out of scope (no SkPicture / SkSerialize port)              |

### C. Out-of-scope filters — **LOW** (already documented in `SkPaint.kt` doc-comment)

| #   | Skia C++                                                              | Kotlin port              | Comment                                                          |
| --- | --------------------------------------------------------------------- | ------------------------ | ---------------------------------------------------------------- |
| C1  | `sk_sp<SkColorFilter> fColorFilter`                                   | absent                   | Massive subsystem; defer to a dedicated plan                     |
| C2  | `sk_sp<SkPathEffect> fPathEffect`                                     | absent                   | Idem (dashing, corner-path, discretize…)                         |
| C3  | `sk_sp<SkMaskFilter> fMaskFilter`                                     | absent                   | Blur, emboss, table — defer                                      |
| C4  | `sk_sp<SkImageFilter> fImageFilter`                                   | absent                   | Multi-pass image-filter graph — defer                            |
| C5  | `sk_sp<SkBlender> fBlender` + `setBlendMode` ⇒ `SkBlender::Mode(...)` | `SkBlendMode` enum field | Pragmatic subset; `asBlendMode()`/`getBlendMode_or` not relevant |

C1–C4 stay out of scope for this plan. C5 is documented but not changed: our enum-based `blendMode` covers every mode our raster pipeline supports today; the polymorphic `SkBlender` indirection adds nothing without runtime effects.

### D. Internal consumers requiring migration in Phase 2

`grep -E 'paint\.(color|alpha)' kanvas-skia/src/main/kotlin/org/skia` (excluding `tests/` and `SkPaint.kt`) returns **29 sites**, all in `SkBitmapDevice.kt`. Highlights:

- `colorToF16Premul(SkColor, FloatArray)` (line ~480) — **the hot path**: reads byte alpha, divides by 255 → causes drift.
- Shader modulation paths (lines ~134, ~155, ~395, ~1051, ~1085) — read `SkColorGetA(paint.color)` to fold paint alpha into shader output.
- Solid-colour rect helpers (lines ~496–500, ~572–576) — pass `paint.color` directly to `fillRect` / `strokeRect` (currently byte-precision; Phase 2 keeps them on `SkColor` since the rect helpers don't run through F16).
- `transformPaintColor(paint.color)` (lines ~173, ~398) — colour-space xform; currently round-trips via byte and is therefore the second precision-loss site.

`SkBitmapShader.kt` and `SkShader.kt` do **not** consume `paint.color` directly (verified by grep). Gradient stops own their own colour data.

---

## Phase 2 — Float-precision storage refactor

Status : planned. Goal : preserve `setAlphaf` / `setColor4f` precision end-to-end through the F16 raster pipeline.

### Slice 2.1 — Refactor `SkPaint` storage (no behavioural change)

- Replace public `var color: SkColor` field with private `var fColor4f: SkColor4f = SkColor4f(0f, 0f, 0f, 1f)`.
- `var color: SkColor` becomes computed: `get() = fColor4f.toSkColor()`, `set(c) { fColor4f = SkColor4f.FromColor(c) }`.
- `var alphaf: Float` becomes `get() = fColor4f.fA`, `set(v) { fColor4f.fA = v.coerceIn(0f, 1f) }` — **direct float write, no byte round-trip**.
- `var color4f: SkColor4f` becomes `get() = fColor4f.copy()`, `set(v) { fColor4f = v.copy() }`.
- Add `fun setColor4f(c: SkColor4f, cs: SkColorSpace? = null)` overload (colour-space xform stub for now; full `SkColorSpaceXformSteps.apply` follows in slice 2.5 once we audit upstream's xform path for paint colours).
- Add `constructor(color: SkColor4f, colorSpace: SkColorSpace? = null)` secondary constructor.
- `equals` / `hashCode` / `copy` switch to `fColor4f`.

**Expected GM impact** : zero. All existing call-sites set colour via `paint.color = X` (byte path) — they go through the same `FromColor` / `toSkColor` round-trip and produce identical bytes downstream.

### Slice 2.2 — Plumb float colour into the F16 raster pipeline ✅

- Add `colorToF16Premul(SkColor4f, FloatArray)` overload to `SkBitmapDevice` that reads the float value directly: `out[3] = c.fA; out[0] = c.fR * c.fA; …`.
- Re-route the `useF16SolidPath` callers (`fillRectAA`, `strokeRectAA`, `scanFillPath`) to the new overload, passing `paint.color4f` instead of `paint.color`.
- `transformPaintColor` gains an `SkColor4f → SkColor4f` overload symmetrically; `inDeviceColorSpace` switches to it (no more byte round-trip in the colour-space xform).
- `fillPath` / `scanFillPath` signatures take `SkColor4f`; the legacy 8-bit fall-through computes the byte form lazily.
- AAA / hairline / non-F16 paths stay on the byte path (they already truncate regardless; rewriting them buys nothing).

**Measured GM impact** (post-implementation):
- `BatchedConvexPathsGM` : 34.94 % → 34.94 % (+0.01 % only). **The audit hypothesis was wrong** — alpha-precision drift is NOT the dominant error source. The systematic ~25/255 max diff against the reference is consistent with a different blend-math discrepancy (possibly working-space vs linear-space compositing for translucent stacking, see Phase 5h post-mortem at `MIGRATION_PLAN.md` line ~1104, which already showed that linear-premul F16 storage delivered 0 GM improvements). Slice 2.2 is therefore a **precision iso-with-Skia improvement**, not a `BatchedConvexPaths` unblocker.
- Iso side-effect : restored `paint.setAlphaf(0.3f)` in the `BatchedConvexPathsGM` Kotlin port to match upstream `gm/batchedconvexpaths.cpp` (the previous port hard-coded byte 77 as a workaround for the precision lossage that this slice eliminates).
- ±0.01 to ±0.12 % drift on ~6 GMs (e.g. `Bug5099GM` -0.12 %, `BeziersGM` -0.02 %, `B119394958GM` -0.01 %; `AddArcGM` +0.01 %, `ArcToGM` +0.00 %). All within the 1 % ratchet tolerance — no test fails. Drift comes from the F16 path now keeping float precision longer (instead of byte-rounding before blend), which produces slightly different — but not uniformly better/worse — final pixel values vs. the upstream-rendered references.

**Outcome** : the storage half of the SkPaint parity track is iso. The compositing-math half (working-space vs linear-space SrcOver in the F16 buffer) is a separate track and does not block on `SkPaint` shape — see [`Phase 5h`](MIGRATION_PLAN.md) for prior exploration.

### Slice 2.3 — Plumb float alpha through shader modulation

- Replace the five `SkColorGetA(paint.color)` modulation sites in `SkBitmapDevice.kt` (~134, ~155, ~395, ~1051, ~1085) with `paint.alphaf`.
- Update the modulation arithmetic to stay in float (today some sites compute `paintAlpha / 255f` inline — switch to `paint.alphaf` directly).

**Expected GM impact** : neutral on existing GMs (no GM today exercises `setAlphaf` on a shader paint with non-trivial precision). Future shader GMs benefit.

### Slice 2.4 — Honour `setStrokeWidth(<0)` / `setStrokeMiter(<0)` rejection

- Switch `strokeWidth` / `strokeMiter` to a `private var` + `setStrokeWidth` / `setStrokeMiter` functions matching Skia's silent-reject (`if (v >= 0) field = v`). Keep Kotlin property access available via custom getters.
- Or simpler : keep `var` but install custom setters that pin to `>= 0`.

**Expected GM impact** : zero (no GM passes a negative stroke).

### Slice 2.5 — `setColor(SkColor4f, SkColorSpace?)` colour-space xform

- Once Phase 2.1 lands the new setter signature, wire `SkColorSpaceXformSteps(srcCS, kUnpremul, sk_srgb_singleton(), kUnpremul)` and call `steps.apply(fColor4f.vec())` on the float array.
- Requires `SkColorSpaceXformSteps` to expose `apply(FloatArray)` — verify in `kanvas-skia/src/main/kotlin/org/skia/core/SkColorSpaceXformSteps.kt`.

**Expected GM impact** : neutral until a GM constructs a paint with a non-sRGB colour space (none today).

### Slice 2.6 — `nothingToDraw` parity

- Add `kDst` to the short-circuit list (returns `true` unconditionally).
- Document that `affects_alpha(colorFilter)` / `affects_alpha(imageFilter)` checks are deferred to C1/C4 ports.

**Expected GM impact** : zero (kDst draws aren't exercised by current GMs); marginal CPU win on any future GM that issues kDst draws.

---

## Phase 3 — Audit-driven follow-ups (deferred)

Open these only if Phase 2 closes leave residual divergences material to GM scores.

- **`canComputeFastBounds` / `computeFastBounds`** (B7) — needed for `SkCanvas::quickReject` plumbing once we port that. Depends on `SkStrokeRec::GetInflationRadius`.
- **`SkPaintPriv::Overwrites`** (B8) — opaque-fill fast-path; modest perf win on solid rects.
- **`SkColorFilter` scaffolding** (C1) — huge scope; defer to a dedicated plan if any GM unblocked by it surfaces.
- **`SkBlender` polymorphism** (C5) — only useful if we port `SkRuntimeEffect`.

---

## Risk register

| Risk                                                                    | Mitigation                                                                                          |
| ----------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Phase 2.1 storage refactor breaks `equals` / `copy` semantics elsewhere | Run full GM suite after slice 2.1 (no behavioural change expected) — ratchet catches regressions    |
| Phase 2.2 F16 plumbing shifts other GMs by ±0.1 %                       | Per-GM ratchet (1 % drop tolerance); slice is reversible                                            |
| `transformPaintColor` float overload diverges from byte version         | Keep byte overload as fallback; F16 path opts in explicitly                                         |
| `SkColorSpaceXformSteps.apply(FloatArray)` signature missing            | Slice 2.5 is independent of 2.2; defer if blocked, BatchedConvexPathsGM fix lives in 2.2 alone      |

---

## Closeout target (when Phase 2 completes)

- `SkPaint` storage iso with Skia (`fColor4f` source of truth) — **delivered by Slice 2.1**.
- F16 raster pipeline reads colour without a byte round-trip (precision iso) — **delivered by Slice 2.2**.
- Honour `setStrokeWidth(<0)` / `setStrokeMiter(<0)` rejection — Slice 2.3.
- `setColor(SkColor4f, SkColorSpace?)` colour-space xform via `SkColorSpaceXformSteps` — Slice 2.5.
- `nothingToDraw` parity with `kDst` — Slice 2.6.
- All ratchets pass (1 % drop tolerance); no test fails.
- New unit tests on `SkPaint`: `setAlphaf` round-trip preserves float precision; `setColor4f` round-trip preserves all 4 channels at full float precision — **delivered by Slice 2.1**.
- Cross-link this plan from `MIGRATION_PLAN.md` recap section — **delivered by Phase 1**.
- Archive to `archives/MIGRATION_PLAN_PAINT_PARITY.md` once closed.

**`BatchedConvexPathsGM` ≥ 85 %** is **not** a Phase 2 target — Slice 2.2 demonstrated that alpha precision is not the dominant error source. The remaining drift requires a separate compositing-math investigation (working-space vs linear-space SrcOver), out of scope for SkPaint parity.
