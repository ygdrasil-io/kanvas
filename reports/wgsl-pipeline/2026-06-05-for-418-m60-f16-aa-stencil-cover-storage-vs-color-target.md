# FOR-418 - M60 F16 storage vs color-target

Classification: `storage-zero-while-same-pass-color-target-nonzero`.

FOR-418 adds an opt-in diagnostic pass guarded by `kanvas.webgpu.m60F16AaStencilCoverStorageColorTargetComparison.enabled` and disabled by default. The pass replays the bounded M60 F16 AA stencil-cover draw into an `RGBA16Float` scratch target with blending disabled while the shader-return diagnostic writes to storage in the same render pass.

Result:

- Covered mutation records: 16.
- Storage-zero records: 16.
- Non-zero color-target records: 16.
- Mutating draws covered: 1 and 3.

Interpretation: the direct comparison no longer points at fixed-function blend, load/store, or the no-blend scratch output. The remaining suspect is the shader-return/storage side-channel path: it observes the fragments but records zero while the same pass produces the non-zero color output.

No default route, score, threshold, fallback, promotion, or rendering output is changed by this ticket.
