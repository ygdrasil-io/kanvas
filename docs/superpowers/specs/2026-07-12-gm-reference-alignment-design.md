# GM Reference Alignment Design

Date: 2026-07-12
Status: Approved design

## Goal

Align Kanvas Skia GM reference lookup with the names actually registered by
the upstream Skia C++ GMs, while preserving the logical Kotlin GM names used
for registry selection, generated-render paths, and similarity tracking.

The change is limited to source-aware reference identity and diagnostics. It
does not claim new rendering support and does not regenerate or replace PNG
reference assets.

## Context and root cause

`SkiaGmRunner` currently loads `/reference/${gm.name}.png`. This assumes that
the Kotlin port name is identical to the upstream C++ registration name. That
assumption is false for several cases:

- runtime-effect ports omit separators, while Skia uses names such as
  `color_cube_rt`, `color_cube_cf_rt`, and `linear_gradient_rt`;
- some upstream GMs register a family of concrete variants from one C++
  factory, such as `clipped-bitmap-shaders-*` and
  `anisotropic_image_scale_*`;
- some Kotlin ports are explicitly `No-op`, `STUB`, or `Best-effort` and must
  not be assigned an arbitrary variant reference;
- the existing Python checker compares names heuristically but does not use
  CPP registrations as a source-aware classification.

The current reference directory contains 998 PNGs. The inspected external
Skia output directory contains 992 PNGs, all with names already present in the
repository, so importing or duplicating those PNGs is out of scope.

## Design

### 1. Separate logical GM identity from reference identity

Add this property to `SkiaGm`:

```kotlin
val referenceName: String get() = name
```

The default keeps existing behavior. Only a one-to-one, CPP-verified alias
overrides `referenceName`.

`name` remains the logical Kanvas identity and continues to drive:

- `-Dkanvas.gm.name` selection;
- generated-render and diagnostic output directories;
- similarity score keys;
- registry uniqueness checks.

`referenceName` is used only when loading the reference PNG and when reporting
the reference asset selected by the runner or dashboard.

The initial verified aliases are:

| Kotlin GM | CPP registration | Reference PNG |
|---|---|---|
| `ColorCubeRTGm` | `color_cube_rt` | `color_cube_rt.png` |
| `ColorCubeColorFilterRTGm` | `color_cube_cf_rt` | `color_cube_cf_rt.png` |
| `LinearGradientRTGm` | `linear_gradient_rt` | `linear_gradient_rt.png` |

No alias is assigned when the C++ source registers multiple variants and the
Kotlin port does not identify one concrete variant. This applies to the
anisotropic, clipped-bitmap-shader, circular-arc, encode-sRGB, perspective-
shader, preserve-fill-rule, shallow-gradient, and varied-text families.

### 2. Make the checker source-aware

Update `scripts/check_missing_gms.py` to classify each Kotlin GM/reference
relationship instead of emitting one undifferentiated missing-name list.

The checker will support an optional `--cpp-gm-dir PATH` argument. When a CPP
directory is provided, it will use the existing C++ name extraction logic to
recognize direct registrations and generated variant families. The checker
will remain usable without the external Skia checkout and will report that
source evidence is unavailable in that mode.

Classification order:

1. direct reference basename match;
2. explicit `referenceName`/verified alias match;
3. normalized separator/case alias match, reported as a naming mismatch;
4. CPP-generated variant family with one or more matching reference PNGs;
5. CPP registration with no matching reference PNG;
6. Kotlin-only `No-op`, `STUB`, or `Best-effort` entry requiring explicit
   status rather than an inferred reference.

The checker output will retain the actionable missing list while also showing
the evidence and reason for every excluded entry. It will not treat a similar
filename as a valid reference unless the source registration or an explicit
alias supports that relationship.

### 3. Keep reference quality claims explicit

The change will not mark an approximate Kotlin drawing as pixel-equivalent to
an upstream reference. Existing `ReferenceStatusEntry` values remain the
authority for untrustable references. No new reference PNGs, copied aliases,
or score baselines will be added by this PR.

## Files and responsibilities

- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGm.kt`
  - define the default `referenceName` contract;
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRunner.kt`
  - load and report references through `referenceName` while preserving
    logical GM output names;
- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaDashboardGenerator.kt`
  - resolve dashboard reference assets through the same contract;
- the three runtime-effect GM Kotlin files listed in the alias table
  - declare only CPP-verified reference aliases;
- `scripts/check_missing_gms.py`
  - provide source-aware classification and stable CLI output;
- `scripts/test_check_missing_gms.py`
  - cover direct matches, aliases, variants, missing CPP references, and
    source-unavailable behavior.

## Testing and validation

Test-first implementation will cover:

1. the default `referenceName == name` behavior;
2. each of the three verified aliases;
3. runner reference loading with an aliased reference while retaining the
   logical output directory name;
4. dashboard lookup using the same alias;
5. checker classifications using temporary Kotlin/CPP/reference fixtures;
6. rejection of ambiguous variant families as direct aliases.

Validation will include the focused Python/Kotlin tests, the Skia GM registry
and runner tests, the source-aware checker against the checked-in references,
and the relevant headless Skia GM validation. Native Kadre execution and the
unpublished `external/poc-koreos` submodule remain out of scope.

## Non-goals

- porting or reimplementing the 76 previously reported GM gaps;
- changing renderer behavior or similarity thresholds;
- importing PNGs from the external Skia checkout;
- renaming every Kotlin `name` to a source name;
- assigning a single reference to a multi-variant upstream factory;
- claiming Skia parity for `No-op`, `STUB`, or `Best-effort` ports.

## Acceptance criteria

- The three CPP-verified aliases load their existing reference PNGs without
  changing logical GM selection or output naming.
- The source-aware checker no longer reports those aliases as missing and
  explains variant-family and unsupported cases.
- No reference PNG files are added, removed, or duplicated.
- Focused tests and the relevant Skia GM validation pass.
- The PR description documents the root cause, the source evidence, the
  non-goals, and the validation commands.
