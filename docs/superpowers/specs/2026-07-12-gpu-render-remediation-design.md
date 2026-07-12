# GPU render remediation design

**Date:** 2026-07-12  
**Status:** Approved for specification review  
**Scope:** Correct the legitimate GPU clip, blend, blur, resource-lifetime, and proof defects found by the independent review. CI policy is explicitly out of scope.

## Goal

Make the active WebGPU paths correct and bounded, then prove their pixel behaviour. A GPU destination-read operation must remain entirely on the GPU; a clip or blur must either render correctly within its budget or return a stable refusal. The committed GM scores must represent the last measured run, not a historical high-water mark.

## Decisions

### Destination-read blends

- Replace the legacy `readRgba()` then texture-upload snapshot with a GPU-to-GPU texture copy.
- Keep the top-level destination-read formula path GPU-only and assert zero CPU destination snapshots.
- For advanced blends nested inside `DrawPicture`, return a stable, explicit refusal before source routing until every nested draw can independently use the snapshot-plus-formula path. Never coerce such a draw to `SrcOver`.

### Clip and blur correctness

- A mask-blur plan uses the precise captured device-rect clip when available. Complex clips retain their conservative full-target policy unless a separate semantic bound is proved.
- Blur sampling outside its source mask is transparent (decal behaviour), not edge-clamped.
- The clip-mask budget includes every allocation that the native runtime makes: color render attachment, resolve attachment when MSAA is enabled, and depth/stencil attachment at the actual sample count.
- Blur intermediate textures are transient. They are released after their last GPU submission completes; a frame-level accounting guard prevents unbounded accumulation across many blur draws.

### Evidence and scores

- Pixel tests compare all RGBA channels with an explicit tolerance and include negative samples, so white cannot satisfy a red or blue assertion.
- Native mask evidence samples real rendered output for x1 and x4 masks, including interior, exterior, and AA edge pixels.
- Tests cover even-odd and inverse clip behaviour, clipped blur bounds, edge halo transparency, nested-picture advanced-blend refusal, and destination-read telemetry.
- `SimilarityTracker` writes the last successfully measured similarity value, including a decrease. It does not update a missing-reference GM. The dashboard continues to classify missing references separately.

## Architecture and ownership

`GPUBackendOffscreenTarget` owns the underlying WebGPU textures. It gains an explicit transient-texture release operation whose native implementation defers destruction until all submissions retaining that texture are complete. Persistent scene targets and cached clip masks keep their existing ownership model; only blur intermediates use the new transient path.

The GPU clip planner remains the single source of budget truth. Its byte estimate mirrors the native coverage-mask allocation description rather than inferring memory from the sampled color texture alone.

`DrawPicture` preflight checks nested operations before creating source intermediates. A nested blend requiring destination reads either gets a complete GPU snapshot/formula route in a future change or is refused with a stable reason code in this change.

## Error handling and diagnostics

- Unsupported nested destination-read picture blend: stable `unsupported.picture.nested_destination_read_blend:<mode>` refusal before allocation.
- A mask that exceeds the corrected aggregate frame budget: existing stable budget refusal.
- A transient release failure is surfaced as a runtime resource error; it must not silently retain the resource.
- Diagnostics record GPU destination copies and CPU destination readbacks separately; all new destination-read tests require the latter to remain zero.

## Non-goals

- No CI lane, required-task, GPU-availability, or `SKIPPED` policy changes.
- No support claim for advanced blends nested in pictures; the safe immediate policy is refusal.
- No redesign of the persistent clip-mask cache or a general texture-atlas system.
- No new reference PNGs for missing Skia baselines.

## Validation

1. Unit tests for corrected allocation arithmetic, clip bounds forwarding, score replacement, and nested-picture preflight.
2. Adapter-backed surface tests for exact RGBA clip/mask/blur pixels, destination-copy telemetry, and edge behaviour.
3. Native resource-lifetime tests showing blur intermediates are released after their final submission and do not accumulate across a draw sequence.
4. Focused GPU renderer and Kanvas suites, then dashboard and Skia score regeneration. The Skia result is reported honestly: missing references and unrelated pre-existing failures are not silently rebased.

## Acceptance criteria

- No destination-read route covered by this change invokes `readRgba()`.
- Nested picture destination-read blends are refused rather than rendered as `SrcOver`.
- Corrected budgets account for the native depth/stencil allocation.
- A blur touching an edge fades against transparent coverage, and a small device clip affects blur planning.
- Tests would fail if the mask is all-zero, the mask is all-white, or an expected red/blue pixel becomes white.
- The score file reflects the most recent comparable measurement.
