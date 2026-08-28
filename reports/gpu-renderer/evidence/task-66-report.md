# W90 — simple-stroke refusal boundaries

## Objective

Prove that unsupported simple-stroke variants remain fail-closed instead of
being mistaken for native coverage.

## Evidence

The inventory tests cover two boundaries:

- a diagonal round-cap stroke is refused with
  `unsupported.core_primitive.stroke.round_cap_pixel_exact_lowering`;
- a diagonal butt/miter stroke under non-uniform scale is recorded as
  `refused.unsupported.geometry.perspective_path`, emits no render task, and
  is rejected again by native preparation with the same diagnostic.

The round-cap case checks the cap and point-count facts. The non-uniform case
checks the recording route and prepared-frame refusal directly; the inventory
semantic harness is intentionally not used as the authority for this route
refusal.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryTest.diagonal round cap refuses before native preparation' --tests '*GPUFramePathApiInventoryTest.simple stroke with nonuniform scale refuses before native preparation'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope

This wave changes no production behavior and adds no fallback approximation. It
documents stable refusal policy for unsupported cap and transform variants. No
GM baseline, threshold, or retired `gpu-renderer-scenes` asset was changed.
