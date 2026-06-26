# M32-013 / M32-015 (stroke sub-case): stroke-style draws refuse instead of silently filling

- Date: 2026-06-26
- Phase: Legacy gpu-raster Decommission — Phase 2.B(i) (Pattern A refusal)
- Tickets: KGPU-M32-013 (rect/rrect stroke), KGPU-M32-015 (path-stroke sub-case)
- Status of tickets: kept `proposed` (independent review still owed)
- Future real stroke port: KGPU-M3-003 (rect/rrect) / KGPU-M11-007 (path) —
  dependency-gated; **no short-lived substitute added** (per AGENTS.md).

## The bug (silent stroke fill)

`KanvasSkiaBridge.toKanvasPaint()` copied `strokeWidth/strokeCap/strokeJoin`
from `SkPaint` but **did not read `SkPaint.style`** (Fill vs Stroke vs
StrokeAndFill). `drawRect/drawRRect/drawPath` then called the kanvas
`Canvas.drawRect/RRect/Path(...)`, which always built **FILL** commands
(`NormalizedDrawCommand.FillRect/FillRRect/FillPath`). `Surface.dispatchFill*`
always filled. Net effect: an `SkPaint` with `style = kStroke_Style` (or
`kStrokeAndFill_Style`) was **silently FILLED** — a "no silent fallback" policy
violation and the integrity issue this decommission must close.

## The fix (clean refusal, not a stroke renderer)

Chosen approach: **command-model flag** (plan option 1), consistent with the
existing `Canvas.drawImage` → `ImageDraw`-material refuse pattern that already
makes `dispatchFillRect` refuse instead of silently filling.

1. `kanvas/Paint.kt`: added `enum class PaintStyle { FILL, STROKE }` and
   `Paint.style: PaintStyle = PaintStyle.FILL` (default FILL → all existing fill
   behavior unchanged).
2. `gpu-renderer/.../commands/NormalizedDrawCommand.kt`: added
   `val stroke: Boolean = false` to `FillRect`, `FillRRect`, `FillPath`, and a
   `stroke: Boolean = false` parameter to their three builders. Default `false`
   keeps every existing caller on the fill path.
3. `kanvas/Canvas.kt`: `drawRect`, both `drawRRect` overloads, and `drawPath`
   pass `stroke = paint.style == PaintStyle.STROKE` to the builder.
4. `kanvas-skia-bridge/.../KanvasSkiaBridge.kt`: `toKanvasPaint()` maps
   `SkPaint.style` → `PaintStyle` (`kFill_Style` → FILL; `kStroke_Style` and
   `kStrokeAndFill_Style` → STROKE).
5. `kanvas/Surface.kt`: added the pure helper
   `NormalizedDrawCommand.strokeRefusalReasonOrNull()` (returns
   `"unsupported_stroke"` for stroke commands, `null` for fill).
   `dispatchFillRect/RRect/Path` call it FIRST and `refuse(it); return` before
   any fill, so the diagnostic is added to `SurfaceRenderResult.diagnostics`,
   which `SkiaKanvasSurface.emitRefusedDiagnostics` then emits.

No stroke geometry/expansion/SDF was implemented. The deliverable is a clean
refusal.

## Diagnostic codes (stable)

Emitted through the existing dispatch refuse pattern
`refuse:${cmd.diagnosticName}:$reason`, with the stable reason token
**`unsupported_stroke`** (mirroring the existing `unsupported_material` /
`unsupported_blend` reasons). Concrete emitted strings for the Kanvas API
adapter look like:

- `refuse:kanvas-api:draw#<id>:unsupported_stroke` (rect / rrect / path)

The stable, asserted token is `unsupported_stroke`, surfaced via
`SkiaKanvasSurface.emitRefusedDiagnostics`. (The two ticket bodies were drafted
pre-implementation with the literal strings `unsupported_stroke_command`
(M32-013) and `path:unsupported_stroke` (M32-015); the implemented reason follows
the user task instruction's `unsupported_stroke` suffix and the existing
`Surface.kt` refuse-pattern prefix. See Concerns.)

## Tests (TDD: failing first, then green)

Headless (no GPU) — `kanvas/src/test/kotlin/org/graphiks/kanvas/StrokeRefusalTest.kt`:

- `drawRect with stroke paint records a stroke-marked fill command` — PASSED
- `drawRect with default fill paint records a non-stroke command` — PASSED
- `drawRRect with stroke paint records a stroke-marked fill command` — PASSED
- `drawRRect with default fill paint records a non-stroke command` — PASSED
- `drawPath with stroke paint records a stroke-marked fill command` — PASSED
- `drawPath with default fill paint records a non-stroke command` — PASSED
- `stroke-marked rect command refuses with unsupported_stroke` — PASSED
- `stroke-marked rrect command refuses with unsupported_stroke` — PASSED
- `stroke-marked path command refuses with unsupported_stroke` — PASSED
- `fill-marked command does not refuse for stroke` — PASSED

Headless paint mapping — `kanvas-skia-bridge/.../KanvasSkiaBridgeTest.kt`:

- `toKanvasPaint maps fill style to FILL` — PASSED
- `toKanvasPaint defaults to FILL style` — PASSED
- `toKanvasPaint maps stroke style to STROKE` — PASSED
- `toKanvasPaint maps stroke-and-fill style to STROKE` — PASSED

End-to-end through the standard plumbing (GPU-gated via
`assumeTrue(WebGPU available)`, like the existing `drawImage`/`blend` refuse
tests; WebGPU **was** available in this run, so they executed):

- `stroked drawRect via bridge emits refuse unsupported_stroke` — PASSED
- `stroked drawRRect via bridge emits refuse unsupported_stroke` — PASSED
- `stroked drawPath via bridge emits refuse unsupported_stroke` — PASSED

These assert `output.contains("refuse:") && output.contains("unsupported_stroke")`
against captured `System.err` after `flush()`, i.e. the refusal is surfaced via
`SurfaceRenderResult.diagnostics` → `emitRefusedDiagnostics`, and nothing is
dispatched as a fill.

### Pre-RED evidence

`:kanvas:compileTestKotlin` failed first with unresolved references
(`PaintStyle`, `Paint.style`, command `stroke`, `strokeRefusalReasonOrNull`,
builder `stroke` parameter) — feature-missing RED — before the production change.

### Green / no-regression runs

```
rtk ./gradlew --no-daemon :kanvas:test :kanvas-skia-bridge:test          # BUILD SUCCESSFUL
rtk ./gradlew --no-daemon :gpu-renderer:test :kanvas-skia-bridge:compileKotlin  # BUILD SUCCESSFUL
```

- `:kanvas:test` and `:kanvas-skia-bridge:test`: all PASSED (only
  `activation check emits kanvas-activation-failed when WebGPU unavailable` was
  SKIPPED — its assumeTrue requires WebGPU to be *absent*; WebGPU was present).
- `:gpu-renderer:test`: all PASSED (the new defaulted `stroke=false` field did
  not break command-equality / builder tests).
- `:kanvas-skia-bridge:compileKotlin`: SUCCESS (GPU parity harness still
  compiles).

## Fill behavior unchanged

`PaintStyle` defaults to `FILL`; the command `stroke` flag defaults to `false`;
all existing builder/command callers omit the new parameter. Existing fill
dispatch tests (`SkiaBridgeParityTest` rrect/path fill task equivalence,
`flush does not emit diagnostic for supported solid rect`, etc.) remained green,
and the headless `*default fill paint records a non-stroke command` tests pin the
default to FILL.

## Coverage

- KGPU-M32-013 (rect-rrect-stroke): stroked rect/rrect refuse — covered.
- KGPU-M32-015 (path-fill-stroke): the **path-stroke sub-case** refuses —
  covered. Path *fill* parity is unaffected and remains as previously evidenced.

Real stroke rendering remains **out of scope and dependency-gated**:
KGPU-M3-003 (rect/rrect) and KGPU-M11-007 (path).

## Concerns

- The implemented stable reason token is `unsupported_stroke` (per the task
  instruction and consistent with `Surface.kt`'s `unsupported_material` /
  `unsupported_blend` pattern), which differs from the literal strings drafted in
  the ticket bodies (`unsupported_stroke_command`, `path:unsupported_stroke`).
  An independent reviewer should decide whether to align the ticket prose to the
  implemented `unsupported_stroke` token.
