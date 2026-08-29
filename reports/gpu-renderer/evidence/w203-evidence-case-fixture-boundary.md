# W203 — Evidence-case fixture boundary correction

W203 repairs the test contract exposed by the expanded catalogue. The
`EvidenceCase` value can now be used by prepared-runner contract tests with a
small routed scene fixture; the catalogue itself remains the source of truth
for execution-boundary pairing.

`GpuEvidenceCatalog` still requires every public render and public refusal to
use `KanvasSurfaceProgram`, and every historical standalone refusal to use a
routed product program. No renderer route or public support claim changes.

The catalogue invariant counts are updated to the current 151 render / 17
refusal split.
