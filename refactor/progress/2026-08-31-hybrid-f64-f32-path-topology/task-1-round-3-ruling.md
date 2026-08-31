# Task 1 — Round 3 blocker and architectural ruling

## Blocker reported

The round-3 implementer stopped with `NEEDS_CONTEXT` and created no commit.
`PathArrangementF64.boundary()` emits `PathContourF64` vertices containing only
the F64 point and optional original F32 point. Source locations, source-span and
section identities, and exact contact witnesses are lost before
`projectContoursF64ToPathF32`. Consequently, the legacy projector cannot prove
whether removing a projected run preserves or destroys a witness.

## Ruling

Task 1 is extended only enough to carry provenance through the temporary legacy
arrangement boundary. The extension remains internal to `:math:geometry` and may
modify `PathArrangementF64.kt` plus its direct tests/callers. It must:

- attach the contributing source span/section and exact witness incidence to
  each selected boundary half-edge or equivalent legacy boundary trace;
- preserve every flattened section and keep endpoint/internal identity spaces
  disjoint and deterministic;
- let projection return an explicit `KEEP`, `DROP`, or `REJECT` decision from
  that provenance;
- reject by `path-f32-projection-collapse` whenever no local proof authorizes a
  projected collapse;
- avoid aliases, projected coincidences, a second DCEL, or any other Task-2
  hybrid-topology construction.

This is a temporary migration bridge and must be deleted with the legacy
arrangement/projection in Task 4. The spec remains authoritative: the bridge may
transport exact provenance but may not reconstruct it from F32/F64 coordinates
after arrangement.

## Cost if the ruling is wrong

The temporary legacy surface becomes larger and Task 4 must delete more code.
The alternative is worse: either leave the compactor authoritative in violation
of Task 1, or prematurely implement the Task-2 hybrid arrangement without its
own tests and review boundary.

