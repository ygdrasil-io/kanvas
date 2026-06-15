# KFONT-M0-003 Boundary Diagnostic Evidence

Date: 2026-06-15
Status: review-unblock implemented
Classification: tracked-gap
Claim promotion allowed: false

## Scope

This evidence freezes stable architecture diagnostics for the M0 module and
package-boundary validator. The validator still reports boundary violations
only; it does not claim font parsing, shaping, glyph artifact, renderer, native
font, or GPU text support.

## Stable Diagnostic Codes

- `font.architecture.skia-api-leak`: emitted when a pure Kotlin font/text/glyph
  or GPU renderer text boundary imports Skia facade, Skia binding, or `skia`
  package APIs.
- `font.architecture.gpu-backedge`: emitted when a pure Kotlin font/text/glyph
  source root imports `org.graphiks.kanvas.gpu.renderer.*`.
- `font.architecture.gpu-font-dependency`: emitted when GPU renderer text code
  imports font parser, scaler, shaping, or paragraph owner packages.
- `font.architecture.native-font-dependency`: emitted for forbidden native,
  platform, HarfBuzz, FreeType, Fontations, CoreText, DirectWrite, fontconfig,
  AWT, Swing, JNI, JNA, or interop imports.
- `font.architecture.forbidden-import`: fallback stable architecture code for
  any remaining forbidden boundary import class.

## Synthetic Snapshot

The unit test
`test_boundary_diagnostic_snapshot_uses_stable_architecture_codes` creates two
temporary Kotlin sources and asserts this stable snapshot:

```text
skia_api_leak:
pure Kotlin text boundary contract validation failed: font.architecture.skia-api-leak: pure Kotlin text boundary import violation in font/core/src/main/kotlin/org/graphiks/kanvas/font/SkLeak.kt:3: import org.skia.foundation.SkFont (Skia facade)

gpu_backedge:
pure Kotlin text boundary contract validation failed: font.architecture.gpu-backedge: pure Kotlin text boundary import violation in font/core/src/main/kotlin/org/graphiks/kanvas/font/GpuBackedge.kt:3: import org.graphiks.kanvas.gpu.renderer.text.GPUTextRouteDiagnostics (gpu renderer)
```

## Validation

RED:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_boundary_contracts.py
```

Result: failed as expected. The synthetic snapshot observed free-form boundary
messages without `font.architecture.skia-api-leak` or
`font.architecture.gpu-backedge`.

GREEN:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_boundary_contracts.py
rtk python3 scripts/validate_pure_kotlin_text_boundary_contracts.py
```

Result: the focused boundary unit suite passed, and the boundary validator
passed against the current workspace.

## Remaining Gates

This is architecture diagnostic evidence only. It does not add a Gradle
dependency-graph validator, because this slice's local contract is the boundary
manifest/import validator and the M0-003 write set does not include Gradle model
files. It does not implement or promote parser, scaler, shaping, paragraph,
glyph artifact, renderer route, native font engine, CPU oracle, or GPU text
support.
