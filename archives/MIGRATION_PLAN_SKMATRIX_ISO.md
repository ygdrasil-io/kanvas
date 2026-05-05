# SkMatrix iso-alignment with Skia C++

> Five-phase plan to bring [`kanvas-skia/.../SkMatrix.kt`](kanvas-skia/src/main/kotlin/org/skia/math/SkMatrix.kt) to bit-equivalent (within 1 ulp) parity with upstream
> [`include/core/SkMatrix.h`](/Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkMatrix.h) +
> [`src/core/SkMatrix.cpp`](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkMatrix.cpp), restricted to the affine sub-group.
> Each phase is independently mergeable — stop at any milestone.

## Scope

**In:** Identity / Translate / Scale / Rotate / Skew and any composition. Numeric semantics, snap-to-zero, near-singular handling, naming, factory signatures.

**Out (deliberate, documented):** perspective (`kMPersp0/1/2`), `TypeMask` system, `Rect2Rect`, `setPolyToPoly`, `setRSXform`, `decomposeScale`, `getMinMaxScales`, `mapPointsToHomogeneous`. Add on demand when a GM requires.

## Conformance audit (baseline)

Cross-checked against [SkMatrix.h:153](/Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkMatrix.h#L153),
[SkMatrix.cpp:267-682](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkMatrix.cpp#L267):

**Already iso:**
- Element ordering `(sx, kx, tx, ky, sy, ty)` matches `kMScaleX..kMTransY = 0..5`.
- `MakeAll` parameter order matches Skia's first 6 args.
- `MakeRotate` sign convention `sx=cos, kx=-sin, ky=sin, sy=cos`.
- `concat(a, b)` algebra matches `setConcat` non-perspective branch.
- `preTranslate` closed form matches `sdot` form.
- `preScale` closed form matches Skia's direct multiply.
- `invert` algebra matches `ComputeInv` non-perspective branch.

**Gaps to close (this plan):**

| # | Gap | Severity |
|---|---|---|
| A | `MakeRotate` doesn't snap near-zero sin/cos → `isAxisAligned` lies for cardinal angles | Medium |
| B | `invert` near-singular check is exact-zero only → garbage NaN-like values for `det ≈ 1e-12` | Medium |
| C | `concat` and `invert` use float multiplies → up to 1 ulp error on long chains | Low |
| D | `preScale(kx_, ky_)` parameter naming misleading (should be `sx, sy`) | Cosmetic |
| E | Missing pivoted overloads `preScale(sx, sy, px, py)`, `MakeScale(sx, sy, px, py)`, etc. | Low |

---

## Phase 1 — API & naming alignment

**Goal:** signatures match Skia 1:1; zero behaviour change.

**Edits:**

| File | What |
|---|---|
| [`SkMatrix.kt:82-84`](kanvas-skia/src/main/kotlin/org/skia/math/SkMatrix.kt#L82) | rename `preScale(kx_, ky_)` → `preScale(sx, sy)` (matches Skia [`SkMatrix.h:646`](/Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkMatrix.h#L646)) |
| [`SkCanvas.kt:113`](kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt#L113) | callsite untouched (positional args) — but update internal doc references |
| [`SkMatrix.kt:154-211`](kanvas-skia/src/main/kotlin/org/skia/math/SkMatrix.kt#L154) | add pivoted factory overloads: `MakeScale(sx, sy, px, py)`, `MakeSkew(kx, ky, px, py)`, mirroring [`SkMatrix.cpp:300-317`](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkMatrix.cpp#L300) and [`SkMatrix.cpp:492-498`](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkMatrix.cpp#L492). Closed form: `setScale(sx, sy, px, py) = SkMatrix(sx, 0, px - sx*px, 0, sy, py - sy*py)` |
| [`SkMatrix.kt:96-98`](kanvas-skia/src/main/kotlin/org/skia/math/SkMatrix.kt#L96) | add `preSkew(kx, ky, px, py)` overload |

**Risk:** zero (rename + additive).
**Tests:** existing 21 tests pass unchanged; add 2 tests for pivoted scale/skew factories.

---

## Phase 2 — Numeric precision parity

**Goal:** every `a*b ± c*d` promoted to double before final round, matching Skia's `muladdmul` / `dcross_dscale`.

**Helpers (private companion):**

```kotlin
// Skia src/core/SkMatrix.cpp:603
private fun muladdmul(a: Float, b: Float, c: Float, d: Float): Float =
    (a.toDouble() * b + c.toDouble() * d).toFloat()

// Skia src/core/SkMatrix.cpp ComputeInv (dcross_dscale)
private fun dcrossDscale(a: Float, b: Float, c: Float, d: Float, scale: Double): Float =
    ((a.toDouble() * b - c.toDouble() * d) * scale).toFloat()
```

**Apply:**
- [`SkMatrix.kt:193-200`](kanvas-skia/src/main/kotlin/org/skia/math/SkMatrix.kt#L193) `concat()` — rewrite the 6 non-translate/translate cells with `muladdmul`, matching [`SkMatrix.cpp:644-672`](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkMatrix.cpp#L644). The `+ a.tx`/`+ a.ty` finals stay in float (matches Skia).
- [`SkMatrix.kt:148-150`](kanvas-skia/src/main/kotlin/org/skia/math/SkMatrix.kt#L148) `invert()` — `itx = dcrossDscale(kx, ty, sy, tx, invDet)`, `ity = dcrossDscale(ky, tx, sx, ty, invDet)`.

**Tests added:**
- Long-chain CTM (10 transforms) compared against pure-double reference: ≤ 1 ulp.

---

## Phase 3 — Sin/cos snap-to-zero in `MakeRotate`

**Goal:** `MakeRotate(90 / 180 / 270 / -90)` produces exactly axis-aligned matrices.

**Constants:** Skia [`SkScalar.h:113`](/Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkScalar.h#L113):
```
SK_ScalarSinCosNearlyZero = 1f / (1 << 16)  ≈ 1.526e-5
```

**Edit [`SkMatrix.kt:173-178`](kanvas-skia/src/main/kotlin/org/skia/math/SkMatrix.kt#L173):**

```kotlin
private const val SK_ScalarSinCosNearlyZero = 1.0f / (1 shl 16)

private fun snapToZero(v: Float): Float =
    if (kotlin.math.abs(v) <= SK_ScalarSinCosNearlyZero) 0f else v

public fun MakeRotate(deg: SkScalar): SkMatrix {
    val rad = deg.toDouble() * PI / 180.0
    val s = snapToZero(sin(rad).toFloat())
    val c = snapToZero(cos(rad).toFloat())
    return SkMatrix(sx = c, kx = -s, ky = s, sy = c)
}
```

**Consequences:**
- `MakeRotate(90).isAxisAligned == true` (currently `false`).
- Tests at [`SkMatrixTest.kt:55-58`](kanvas-skia/src/test/kotlin/org/skia/math/SkMatrixTest.kt#L55) tighten from `assertNear` → `assertEquals` for cardinal angles.

---

## Phase 4 — Near-singular threshold in `invert`

**Goal:** matrices with `|det| < SK_ScalarNearlyZero³` return `null` instead of producing finite garbage.

**Constants:** Skia [`SkScalar.h:99`](/Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkScalar.h#L99) + [`SkMatrix.cpp:745`](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkMatrix.cpp#L745):
```
SK_ScalarNearlyZero = 1f / (1 << 12)        ≈ 2.44e-4
nearlyZero³         ≈ 1.4551915e-11         (det threshold)
```

**Edit [`SkMatrix.kt:140-152`](kanvas-skia/src/main/kotlin/org/skia/math/SkMatrix.kt#L140):**

```kotlin
private const val SK_DetNearlyZero = 1.4551915e-11f

public fun invert(): SkMatrix? {
    val det = sx.toDouble() * sy - kx.toDouble() * ky
    if (kotlin.math.abs(det.toFloat()) <= SK_DetNearlyZero) return null
    // ... rest unchanged (Phase 2 may have already updated invert)
}
```

**Tests added:**
- `(sx=1e-6, sy=1e-6)` → `det = 1e-12` → `invert() == null`.

---

## Phase 5 — Behavioural-parity tests

Add to [`SkMatrixTest.kt`](kanvas-skia/src/test/kotlin/org/skia/math/SkMatrixTest.kt):

| Test | Assertion |
|---|---|
| `MakeRotate cardinal angles are exactly axis-aligned` | `sx, sy, kx, ky` ∈ `{-1, 0, 1}` bit-exact for 0/90/180/270/-90/-180 |
| `MakeRotate cardinal angles isAxisAligned == true` | (was `false` pre-Phase 3) |
| `invert near-singular returns null` | det ≈ 1e-12 → null |
| `invert exactly-singular returns null` | unchanged behaviour |
| `concat long chain stable to 1 ulp` | 10 chained transforms vs double reference |
| `MakeAll round-trips` | constructed matrix's 6 fields == args |
| `pivoted MakeScale` | `MakeScale(2, 3, 1, 1)` keeps `(1, 1)` fixed |
| `pivoted MakeSkew` | `MakeSkew(0.5, 0, 0, 4)` keeps `(0, 4)` fixed |

---

## Out of scope — class doc update

[`SkMatrix.kt:8-33`](kanvas-skia/src/main/kotlin/org/skia/math/SkMatrix.kt#L8): expand the existing scope note to enumerate what's deliberately not ported (perspective, TypeMask system, `Rect2Rect`, `setPolyToPoly`, `setRSXform`, `decomposeScale`, `getMinMaxScales`, `mapPointsToHomogeneous`) and note that they're added on-demand when a GM requires.

---

## Cadence

One PR per phase, mergeable independently:

| PR | Phase | Lines (est.) |
|---|---|---|
| 1 | API/naming | ~30 prod, ~10 test |
| 2 | Numeric precision | ~20 prod, ~25 test |
| 3 | sin/cos snap | ~10 prod, ~15 test |
| 4 | det threshold | ~5 prod, ~10 test |
| 5 | Parity test pack | 0 prod, ~40 test |

Total: ~65 lines prod + ~100 lines test, 5 commits.

## Status

- [x] Phase 1 — API/naming alignment
- [x] Phase 2 — Numeric precision parity
- [x] Phase 3 — sin/cos snap-to-zero
- [x] Phase 4 — Near-singular det threshold
- [x] Phase 5 — Behavioural parity tests

## Follow-up phases (post-baseline)

- [x] **Phase 1b — TypeMask system** (kanvas#77): `fTypeMask` cache, `getType()`, `isIdentity` / `isTranslate` / `isScaleTranslate` / `rectStaysRect` / `hasPerspective` / `isSimilarity(tol)` / `preservesRightAngles(tol)` / `cheapEqualTo`.
- [x] **Phase 2b — Mapping API expansion** (kanvas#77): `mapXY(SkPoint)`, `mapVector`, `mapPoints` (bulk + in-place + 4 fast paths), `mapVectors`, `mapRectScaleTranslate`, `mapRadius`.
- [x] **Phase 3b — Function-style accessors + det + array exchange + RectToRect + decomposition** (this PR): `getScaleX/Y` / `getSkewX/Y` / `getTranslateX/Y` / `getPerspX/Y`, `det()` / `det2x2()`, `get9` / `MakeFrom9` / `asAffine` / `MakeFromAffine` (with `kM*` / `kA*` index constants), `MakeRectToRect(src, dst, ScaleToFit)` + `ScaleToFit` enum, `getMaxScale` / `getMinScale` / `getMinMaxScales` (full eigenvalue solver), `decomposeScale` (Pair-returning).

## Phase 5 — post* family + perspective-correct pre* + cosmetic aliases

- `postTranslate` / `postScale` (+ pivoted) / `postRotate` (+ pivoted) / `postSkew` (+ pivoted) — convenience overloads matching Skia's hand-port-friendly API. `postTranslate` and `postScale` use closed-form fast paths; the rest delegate to `postConcat(MakeX(...))`.
- `preTranslate` and `preScale` updated to be **perspective-correct**: `preTranslate` dispatches to `preConcat` on perspective (Skia's pattern); `preScale` extends its closed form to update `persp0` / `persp1` (no-op on affine, correct on perspective).
- `I()` static — function-form alias of `Identity` matching `SkMatrix::I()`.
- `MakeTrans(SkVector)` overload.
- `operator times` (`a * b`) — Kotlin idiom for `concat(a, b)`.

## Phase 4 — Perspective, RSXform, PolyToPoly

`SkMatrix` is now a full 3×3 transform — no longer restricted to the affine sub-group.

- **Storage**: 9-tuple `(sx, kx, tx, ky, sy, ty, persp0, persp1, persp2)`. Default-constructed identity has `persp = (0, 0, 1)` so existing affine traffic is bit-equivalent.
- **`computeTypeMask`**: detects perspective and short-circuits to `kORableMasks` (identical to Skia).
- **`concat`**: dispatches to a full 3×3 `rowcol3` multiply when either input has perspective; affine fast path unchanged.
- **`mapXY` / `mapPoints`**: homogeneous divide (`x/w, y/w`) when `hasPerspective()`. Affine fast path unchanged.
- **`invert`**: cofactor matrix in double precision (`invertPerspective`). Affine fast path unchanged.
- **`det()` / `det2x2()`**: 3×3 cofactor expansion vs upper 2×2.
- **`MakePerspective(p0, p1)`**: factory for pure perspective.
- **`MakeAll(9-arg)`**: full row-major construction.
- **`MakeFrom9` / `get9`**: handle arbitrary perspective row.
- **`asAffine`**: returns `false` (and leaves buffer untouched) when perspective is present.
- **`mapHomogeneousPoints(SkPoint3[], SkPoint[], count)`**: bulk map without perspective divide. New [`SkPoint3.kt`](kanvas-skia/src/main/kotlin/org/skia/math/SkPoint3.kt) primitive added.
- **`MakeRSXform(scos, ssin, tx, ty)`** + 6-arg pivoted overload: rotate-scale-translate composite. Mirrors `SkMatrix::setRSXform`.
- **`MakePolyToPoly(src[], dst[])`**: 0/1/2/3/4-point fit (perspective for 4 points). Internal `Poly2Proc` / `Poly3Proc` / `Poly4Proc` mirror Skia line-by-line.

## Still hors-scope (porter à la demande)

- Setters on instance (`setScaleX/Y`, `setRotate`, `setTranslate`, `setSinCos`, etc.) — our port is immutable; use factories or `copy()`.
- Serialization (`writeToMemory` / `readFromMemory`).
- `dump` / `dumpHex` / `dumpToString`.
