# RC-MEP Docs, Package, CI, And Wording Slice

Date: 2026-06-02

Linear issues: FOR-183, FOR-184, FOR-185, FOR-186

## Summary

This slice tightens the RC/MEP handoff language without changing rendering
support claims. The implementation target is WGSL on WebGPU. SkSL is now
described only as Skia API compatibility context for runtime effects, and the
M88 package distinguishes PM-visible support, expected unsupported rows,
dependency-gated work, implementation gaps, and reporting-only evidence.

## FOR-183 WGSL vs SkSL Wording

Updated README, agent docs, target docs, and RC2 artifacts to state:

- WGSL is the shader implementation target;
- SkSL appears only as Skia compatibility wording;
- Kanvas does not dynamically compile SkSL and does not build a SkSL compiler,
  IR, or VM;
- supported runtime effects require registered Kanvas descriptors, Kotlin CPU
  behavior, and parser-validated WGSL GPU modules.

Stable fallback keys such as `runtime-effect.arbitrary-sksl-unsupported` were
not renamed. They remain machine-stable diagnostics while the PM summary now
explains that the refused surface is Skia/SkSL compatibility input, not a
Kanvas shader target.

## FOR-184 Expected Unsupported PM Framing

`reports/wgsl-pipeline/m88-realtime-rc2/support-refusal-matrix.json` now
includes PM meaning and next action fields for:

- `supported`;
- `expected-unsupported`;
- `dependency-gated`;
- `implementation-gap`;
- `reporting-only`.

The same matrix adds high-impact PM triage for image-filter DAG, text/glyphs,
runtime effects, and native timing/cache. No threshold was weakened and no row
was promoted without new CPU/GPU/reference evidence.

## FOR-185 PM Package Reproducibility

The M88 RC2 markdown, release notes, and demo script now use the headless
package command:

```bash
rtk ./gradlew --no-daemon pipelinePmBundle
```

Native Kadre demos remain opt-in local evidence. The PM script documents the
submodule setup command when a native demo is needed:

```bash
git submodule update --init --recursive external/poc-koreos
```

## FOR-186 CI And Kadre Dependency Boundary

The M88 package and validator now assert that headless CI validation does not
require Kadre to be published or the `external/poc-koreos` submodule to be
initialized. `:kadre-runtime:pipelineM88ReleaseCandidate2` is classified as
`source-generation-local` in the M88 gate freeze, while `pipelinePmBundle`
remains the blocking portable package gate.

## Validation

Expected validation commands for this slice:

```bash
python3 scripts/validate_m88_rc2.py .
python3 -m json.tool reports/wgsl-pipeline/m88-realtime-rc2/rc2-evidence.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m88-realtime-rc2/support-refusal-matrix.json >/dev/null
rg -n "SkSL|WGSL|expected-unsupported|dependency-gated|implementation-gap|reporting-only|external/poc-koreos" README.md AGENTS.md CLAUDE.md .upstream/target .upstream/specs/skia-like-realtime reports/wgsl-pipeline/m88-realtime-rc2 scripts/validate_m88_rc2.py
```

`pipelinePmBundle` should be run by the integration owner before merge when
the shared worktree is ready. This slice intentionally avoids `kadre-runtime/**`
because runtime source generation is owned by another agent.
