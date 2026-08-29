# W195 — Round-cap stroke under winding path-clip refusal

W195 records a public `Kanvas Surface` refusal for a non-AA round-cap path
stroke `(6,16) → (26,16)`, width `4`, under a winding triangular path clip.
The scene is intentionally kept at the public API boundary and is refused
before native submission with the stable diagnostic
`unsupported.recording.core_primitive_path_stencil_clip`.

This is a refusal proof, not a rendering claim: no CPU pixel oracle is
expected and no GPU readback is submitted. It prevents the unsupported
clip/stroke composition from being mistaken for a supported route while the
native stencil-cover composition remains unimplemented.
