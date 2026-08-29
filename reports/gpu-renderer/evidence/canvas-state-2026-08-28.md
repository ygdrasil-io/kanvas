# Canvas state evidence — 2026-08-28

## Scope

`Canvas` state queries now operate in the caller's local coordinate system for
scale/translate CTMs.  The public recording contract is covered for
`save`, `restore`, `restoreToCount`, clip snapshots, post-restore sentinels,
and empty-stack no-ops.

## Verified code contracts

- `CanvasTest.clip queries stay in local coordinates after a scale translate capture`
  checks inverse-mapped `localClipBounds` and both overlapping and disjoint
  `quickReject` calls after device-space clip capture.
- `CanvasTest.restore to count restores parent clip for the post restore sentinel`
  checks that a nested clip is removed, the saved parent clip is restored, and
  the final sentinel is recorded wide-open after the outer restore.
- `CanvasTest.negative restore count is a stable no op even with saved state`
  locks the public `restoreToCount(-1)` contract: it is invalid input and a
  deterministic no-op, leaving the saved CTM and recording state untouched.

## Render evidence and oracle

`canvas-state-restore-to-count` is a promoted public Surface bundle in
`reports/gpu-renderer/evidence/correctness/promoted/`.  It records an outer
parent clip, a nested child clip, `restoreToCount(1)`, then an orange sentinel
that deliberately extends beyond the parent clip.  The independent CPU oracle
requires the pixels outside that parent clip to remain background; the final
white sentinel is drawn only after the outer `restore`.

The native GPU capture passed the exact RGBA8 comparison: 64×64 pixels,
0 differing pixels, maximum channel difference 0, similarity 100%.  The
bundle contains the CPU and GPU PNGs, diff/statistics, manifest hashes,
route diagnostics and verdict.  Its route is `kanvas.surface.render`, with
one native submission and four recorded draws.

## Validation

```text
./gradlew :kanvas:test --tests 'org.graphiks.kanvas.canvas.CanvasTest.clip queries stay in local coordinates after a scale translate capture'
BUILD SUCCESSFUL

./gradlew :kanvas:test --tests 'org.graphiks.kanvas.canvas.CanvasTest.restore to count restores parent clip for the post restore sentinel'
BUILD SUCCESSFUL

./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
BUILD SUCCESSFUL
```

No new refusal code is introduced: baseline stack underflow remains the
documented deterministic no-op and emits no partial GPU work.
