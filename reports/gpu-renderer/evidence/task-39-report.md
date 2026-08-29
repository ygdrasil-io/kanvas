# W53 — Runtime boundaries evidence

The descriptor route gate now refuses dynamic WGSL, dynamic compilation, and
VM execution requests before route materialization. Unknown or incompatible
placements (ColorFilter, Blender, render-filter, and compute-filter) remain
explicit `kind_mismatch` refusals unless a descriptor advertises that exact
placement. Invalid WGSL continues to fail through the existing reflection gate.

`RuntimeEffect.compile` remains a parser-only compatibility API for existing
callers; the resulting compatibility ID is not a registered descriptor and
cannot enter the GPU route. No dynamic compiler or VM is used to activate an
arbitrary source.

Verification: `:gpu-renderer:test --tests '*RuntimeEffectRuntimeBoundaryW53Test'`
— 3 tests passed. This slice claims no new native rendering capability; all
routes remain registered-descriptor-only and headless.
