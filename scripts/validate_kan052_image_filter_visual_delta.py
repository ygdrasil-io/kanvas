#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/image-filter-visual-delta"
OUTPUT_JSON = "kan-052-image-filter-visual-delta.json"
OUTPUT_MARKDOWN = "kan-052-image-filter-visual-delta.md"

SELECTED_ROW_ID = "crop-image-filter-nonnull-prepass"
SELECTED_ROUTE = "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite"
ROOT_CAUSE = "rgba16float-intermediate-store-to-present-byte-quantization-policy"
BLOCKER_REASON = "not-bounded-to-image-filter-crop-prepass"

CURRENT_ARTIFACTS = {
    "reference": "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/skia.png",
    "cpu": "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/cpu.png",
    "gpu": "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/gpu.png",
    "cpuDiff": "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/cpu-diff.png",
    "gpuDiff": "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/gpu-diff.png",
    "routeCpu": "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-cpu.json",
    "routeGpu": "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-gpu.json",
    "routePrepass": "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json",
    "stats": "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/stats.json",
    "highDeltaScan": "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/high-delta-scan-for250.json",
    "colorPremulAudit": "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/color-premul-audit-for251.json",
}

DIAGNOSTIC_ARTIFACTS = {
    "FOR-252": "reports/wgsl-pipeline/scenes/artifacts/color-reference-bias-audit-for252/color-reference-bias-audit-for252.json",
    "FOR-259": "reports/wgsl-pipeline/scenes/artifacts/intermediate-store-present-audit-for259/intermediate-store-present-audit-for259.json",
    "FOR-260": "reports/wgsl-pipeline/scenes/artifacts/intermediate-quantization-candidate-audit-for260/intermediate-quantization-candidate-audit-for260.json",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-052 image-filter visual delta validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(path: Path) -> Any:
    require(path.is_file(), f"missing JSON file: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {path}: {exc}")


def read_text(path: Path) -> str:
    require(path.is_file(), f"missing text file: {path}")
    return path.read_text(encoding="utf-8")


def artifact_audit(root: Path, artifacts: dict[str, str]) -> list[dict[str, Any]]:
    audit = []
    for key, path in artifacts.items():
        audit.append({"key": key, "path": path, "exists": (root / path).is_file()})
    return audit


def audit_artifacts(root: Path, current: dict[str, Any]) -> list[dict[str, Any]]:
    artifacts = current.get("artifacts")
    require(isinstance(artifacts, dict), "current artifacts missing")
    return artifact_audit(root, artifacts)


def selected_row_from_kan041(root: Path) -> dict[str, Any]:
    payload = load_json(
        root / "reports/wgsl-pipeline/image-filter-dag-bounded-v3/kan-041-image-filter-dag-bounded-v3.json"
    )
    rows = payload.get("supportScenes")
    require(isinstance(rows, list), "KAN-041 supportScenes missing")
    row = next((item for item in rows if item.get("sceneId") == SELECTED_ROW_ID), None)
    require(isinstance(row, dict), f"KAN-041 selected row missing: {SELECTED_ROW_ID}")
    return {
        "rowId": SELECTED_ROW_ID,
        "sourceTicket": "KAN-041",
        "referenceKind": row.get("referenceKind"),
        "status": row.get("status"),
        "fallbackReason": row.get("fallbackReason"),
        "gpuRoute": SELECTED_ROUTE,
        "nodeCount": row.get("nodeCount"),
        "intermediateTextureCount": row.get("intermediateTextureCount"),
        "baseArtifactScene": row.get("baseArtifactScene"),
    }


def kan042_matrix_summary(root: Path) -> dict[str, Any]:
    payload = load_json(
        root / "reports/wgsl-pipeline/image-filter-residual-refusal-matrix/kan-042-image-filter-residual-refusal-matrix.json"
    )
    rows = payload.get("matrixRows")
    require(isinstance(rows, list), "KAN-042 matrixRows missing")
    return {
        "path": "reports/wgsl-pipeline/image-filter-residual-refusal-matrix/kan-042-image-filter-residual-refusal-matrix.json",
        "supportableBoundedRows": [
            slim_matrix_row(row) for row in rows if row.get("pmCategory") == "supportable-bounded"
        ],
        "implementationGapRows": [
            slim_matrix_row(row) for row in rows if row.get("pmCategory") == "implementation-gap"
        ],
        "dependencyGatedRows": [
            slim_matrix_row(row) for row in rows if row.get("pmCategory") == "dependency-gated"
        ],
    }


def slim_matrix_row(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "rowId": row.get("rowId"),
        "pmCategory": row.get("pmCategory"),
        "status": row.get("status"),
        "reasonCode": row.get("reasonCode"),
        "gpuRoute": row.get("gpuRoute"),
        "proofComplete": row.get("proofComplete"),
    }


def current_bundle(root: Path) -> dict[str, Any]:
    stats = load_json(root / CURRENT_ARTIFACTS["stats"])
    route_cpu = load_json(root / CURRENT_ARTIFACTS["routeCpu"])
    route_gpu = load_json(root / CURRENT_ARTIFACTS["routeGpu"])
    route_prepass = load_json(root / CURRENT_ARTIFACTS["routePrepass"])
    high_delta = load_json(root / CURRENT_ARTIFACTS["highDeltaScan"])
    color_audit = load_json(root / CURRENT_ARTIFACTS["colorPremulAudit"])
    return {
        "phase": "current",
        "artifacts": dict(CURRENT_ARTIFACTS),
        "stats": {
            "sceneId": stats.get("sceneId"),
            "dimensions": stats.get("dimensions"),
            "pixels": stats.get("pixels"),
            "cpuSimilarity": stats.get("cpuSimilarity"),
            "gpuSimilarity": stats.get("gpuSimilarity"),
            "cpuMatchingPixels": stats.get("cpuMatchingPixels"),
            "gpuMatchingPixels": stats.get("gpuMatchingPixels"),
            "threshold": stats.get("threshold"),
            "maxChannelDelta": stats.get("maxChannelDelta"),
            "unsupportedImageFilterBefore": stats.get("unsupportedImageFilterBefore"),
            "unsupportedImageFilterAfter": stats.get("unsupportedImageFilterAfter"),
        },
        "routeCpu": route_cpu,
        "routeGpu": route_gpu,
        "routePrepass": route_prepass,
        "residual": {
            "highDeltaTotalPixels": high_delta.get("totalPixelsAboveThreshold"),
            "highDeltaMaxChannelDelta": high_delta.get("maxChannelDelta"),
            "strictHighDeltaPixels": high_delta.get("strictHighDeltaThreshold", {}).get(
                "totalPixelsAboveThreshold"
            ),
            "rgbOnlyResidualPixels": color_audit.get("rgbOnlyResidualPixels"),
            "alphaDeltaNonZeroPixels": color_audit.get("alphaDeltaNonZeroPixels"),
            "colorAuditMaxChannelDelta": color_audit.get("maxChannelDelta"),
        },
    }


def diagnostic_summary(root: Path) -> dict[str, Any]:
    for252 = load_json(root / DIAGNOSTIC_ARTIFACTS["FOR-252"])
    for259 = load_json(root / DIAGNOSTIC_ARTIFACTS["FOR-259"])
    for260 = load_json(root / DIAGNOSTIC_ARTIFACTS["FOR-260"])
    non_image_filter_samples = [
        sample for sample in for252.get("samples", [])
        if isinstance(sample, dict) and sample.get("imageFilterInPath") is False
    ]
    return {
        "FOR-252": {
            "path": DIAGNOSTIC_ARTIFACTS["FOR-252"],
            "supportDecision": for252.get("supportDecision"),
            "correctionApplied": for252.get("correctionApplied"),
            "maxChannelDelta": for252.get("maxChannelDelta"),
            "nonImageFilterResidualSamples": [
                {
                    "id": sample.get("id"),
                    "route": sample.get("route"),
                    "residualPixels": sample.get("residualPixels"),
                    "maxChannelDelta": sample.get("maxChannelDelta"),
                    "imageFilterInPath": sample.get("imageFilterInPath"),
                }
                for sample in non_image_filter_samples
            ],
        },
        "FOR-259": {
            "path": DIAGNOSTIC_ARTIFACTS["FOR-259"],
            "supportDecision": for259.get("supportDecision"),
            "correctionApplied": for259.get("correctionApplied"),
            "cropPolicyChanged": for259.get("cropPolicyChanged"),
            "normalRenderingChanged": for259.get("normalRenderingChanged"),
            "remainingBoundary": for259.get("remainingBoundary"),
            "formatStoreFinding": for259.get("formatStoreFinding"),
        },
        "FOR-260": {
            "path": DIAGNOSTIC_ARTIFACTS["FOR-260"],
            "supportDecision": for260.get("supportDecision"),
            "correctionApplied": for260.get("correctionApplied"),
            "cropPolicyChanged": for260.get("cropPolicyChanged"),
            "normalRenderingChanged": for260.get("normalRenderingChanged"),
            "remainingBoundary": for260.get("remainingBoundary"),
            "missingCondition": for260.get("missingCondition"),
            "caseCount": for260.get("caseCount"),
            "residualRepresentativeRoutes": [
                case.get("route")
                for case in for260.get("cases", [])
                if isinstance(case, dict) and case.get("kind") == "for259-residual-representative-sample"
            ],
        },
    }


def build_evidence(root: Path) -> dict[str, Any]:
    selected = selected_row_from_kan041(root)
    current = current_bundle(root)
    diagnostics = diagnostic_summary(root)
    kan042 = kan042_matrix_summary(root)
    visual_delta = {
        "mode": "blocked-root-cause-no-renderer-after",
        "before": {
            "phase": "selected-row-existing-evidence",
            "artifacts": dict(current["artifacts"]),
            "stats": current["stats"],
            "routeGpu": current["routeGpu"],
        },
        "after": {
            "phase": "not-materialized",
            "rendererChanged": False,
            "blocked": True,
            "blockedBy": ROOT_CAUSE,
            "reasonCode": BLOCKER_REASON,
            "requiredForAfter": (
                "A policy-level renderer change with whole-scene candidate evidence must materialize "
                "new PNG/stat/route artifacts before this can become a renderer visual-delta pass."
            ),
        },
    }
    evidence = {
        "schemaVersion": 1,
        "ticket": "KAN-052",
        "packId": "kan-052-image-filter-visual-delta",
        "status": "blocked",
        "closureDecision": "blocked-root-cause",
        "rendererChanged": False,
        "blocked": True,
        "thresholdsWeakened": False,
        "broadDagClaim": False,
        "implicitCpuReadbackFallback": False,
        "selectedRow": selected,
        "current": current,
        "visualDeltaEvidence": visual_delta,
        "blocker": {
            "rootCause": ROOT_CAUSE,
            "reasonCode": BLOCKER_REASON,
            "summary": (
                "The selected row has complete reference/CPU/GPU/diff/stat/route evidence, but the "
                "remaining byte residual is not local to the Crop(input=Offset(null)) image-filter "
                "route. Existing diagnostics reproduce the residual on non-image-filter draw and "
                "bitmap routes, then narrow it to the RGBA16Float intermediate store-to-present "
                "byte quantization policy. A crop-only renderer change would be a hidden workaround."
            ),
            "supportingDiagnostics": ["FOR-252", "FOR-259", "FOR-260"],
            "missingCondition": diagnostics["FOR-260"]["missingCondition"],
            "admissibleNextStep": (
                "Add whole-scene intermediate-format candidate evidence or a policy-level renderer "
                "change that covers exact and precision-sensitive routes; do not claim an image-filter "
                "crop fix from representative-sample diagnostics only."
            ),
        },
        "diagnostics": diagnostics,
        "kan042Matrix": kan042,
        "artifactAudit": artifact_audit(root, CURRENT_ARTIFACTS),
        "targetDocs": [
            ".upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md",
            ".upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md",
            ".upstream/target/skia-like-realtime-renderer-target.md",
            ".upstream/target/high-performance-wgsl-pipeline-target.md",
        ],
        "validations": [
            "rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/kan-052-image-filter-visual-delta/.gradle-codex ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest",
            "rtk python3 scripts/test_validate_kan052_image_filter_visual_delta.py",
            "rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/kan-052-image-filter-visual-delta/.gradle-codex ./gradlew --no-daemon validateKan052ImageFilterVisualDelta",
        ],
    }
    validate_evidence(evidence, root)
    return evidence


def validate_evidence(evidence: dict[str, Any], root: Path) -> None:
    require(evidence.get("ticket") == "KAN-052", "ticket mismatch")
    require(evidence.get("packId") == "kan-052-image-filter-visual-delta", "packId mismatch")
    if not evidence.get("rendererChanged"):
        require(evidence.get("blocked") is True, "rendererChanged=false requires blocked=true")
    require(evidence.get("closureDecision") == "blocked-root-cause", "closure decision mismatch")
    require(evidence.get("thresholdsWeakened") is False, "thresholds weakened")
    require(evidence.get("broadDagClaim") is False, "broad DAG claim")
    require(evidence.get("implicitCpuReadbackFallback") is False, "implicit CPU readback fallback")

    selected = evidence.get("selectedRow")
    require(isinstance(selected, dict), "selected row missing")
    require(selected.get("rowId") == SELECTED_ROW_ID, "selected row mismatch")
    require(selected.get("referenceKind") == "skia-upstream", "selected row must keep Skia reference")
    require(selected.get("fallbackReason") == "none", "selected row fallback must be none")
    require(selected.get("gpuRoute") == SELECTED_ROUTE, "selected row GPU route mismatch")

    current = evidence.get("current")
    require(isinstance(current, dict), "current bundle missing")
    stats = current.get("stats")
    require(isinstance(stats, dict), "current stats missing")
    require(stats.get("sceneId") == SELECTED_ROW_ID, "current stats scene mismatch")
    require(float(stats.get("threshold")) == 50.0, "threshold changed")
    route_gpu = current.get("routeGpu")
    require(isinstance(route_gpu, dict), "current GPU route missing")
    require(route_gpu.get("fallbackReason") == "none", "current GPU route fallback changed")
    require(route_gpu.get("selectedRoute") == SELECTED_ROUTE, "current GPU route changed")
    route_prepass = current.get("routePrepass")
    require(isinstance(route_prepass, dict), "current prepass route missing")
    require(route_prepass.get("fallbackReason") == "none", "current prepass fallback changed")

    missing = [item for item in evidence.get("artifactAudit", []) if not item.get("exists")]
    require(not missing, f"current artifacts missing: {missing}")

    visual_delta = evidence.get("visualDeltaEvidence")
    require(isinstance(visual_delta, dict), "visual delta evidence missing")
    before = visual_delta.get("before")
    after = visual_delta.get("after")
    require(isinstance(before, dict), "visual delta before evidence missing")
    require(isinstance(after, dict), "visual delta after evidence missing")
    require(before.get("phase") == "selected-row-existing-evidence", "visual delta before phase mismatch")
    before_artifacts = before.get("artifacts")
    require(isinstance(before_artifacts, dict), "visual delta before artifacts missing")
    before_missing = [item for item in artifact_audit(root, before_artifacts) if not item.get("exists")]
    require(not before_missing, f"visual delta before artifacts missing: {before_missing}")
    if not evidence.get("rendererChanged"):
        require(after.get("phase") == "not-materialized", "blocked renderer after artifacts must not be materialized")
        require(after.get("blocked") is True, "blocked renderer after evidence must stay blocked")
        require(after.get("blockedBy") == ROOT_CAUSE, "blocked renderer after root cause mismatch")

    blocker = evidence.get("blocker")
    require(isinstance(blocker, dict), "blocker missing")
    require(bool(blocker.get("rootCause")), "missing root cause")
    require(blocker.get("rootCause") == ROOT_CAUSE, "root cause mismatch")
    require(
        blocker.get("reasonCode") == BLOCKER_REASON,
        "blocker reason must preserve non-image-filter scope",
    )
    diagnostics = evidence.get("diagnostics")
    require(isinstance(diagnostics, dict), "diagnostics missing")
    required_diagnostics = {"FOR-252", "FOR-259", "FOR-260"}
    present = set(blocker.get("supportingDiagnostics", []))
    missing_diagnostics = sorted(required_diagnostics - present)
    require(not missing_diagnostics, f"missing blocker diagnostic: {missing_diagnostics}")

    for252 = diagnostics.get("FOR-252")
    require(isinstance(for252, dict), "FOR-252 diagnostic missing")
    require(for252.get("supportDecision") == "KEEP_DIAGNOSTIC", "FOR-252 support decision changed")
    require(for252.get("correctionApplied") is False, "FOR-252 correction changed")
    non_if = for252.get("nonImageFilterResidualSamples")
    require(isinstance(non_if, list) and non_if, "FOR-252 non-image-filter samples missing")
    require(
        any(sample.get("imageFilterInPath") is False and sample.get("residualPixels", 0) > 0 for sample in non_if),
        "FOR-252 must prove residual outside image-filter routing",
    )

    for259 = diagnostics.get("FOR-259")
    require(isinstance(for259, dict), "FOR-259 diagnostic missing")
    require(for259.get("remainingBoundary") == ROOT_CAUSE, "FOR-259 boundary mismatch")
    require(for259.get("correctionApplied") is False, "FOR-259 correction changed")
    require(for259.get("cropPolicyChanged") is False, "FOR-259 crop policy changed")
    require(for259.get("normalRenderingChanged") is False, "FOR-259 normal rendering changed")

    for260 = diagnostics.get("FOR-260")
    require(isinstance(for260, dict), "FOR-260 diagnostic missing")
    require(for260.get("remainingBoundary") == ROOT_CAUSE, "FOR-260 boundary mismatch")
    require(for260.get("correctionApplied") is False, "FOR-260 correction changed")
    require(for260.get("cropPolicyChanged") is False, "FOR-260 crop policy changed")
    require(for260.get("normalRenderingChanged") is False, "FOR-260 normal rendering changed")
    require(for260.get("caseCount") == 5, "FOR-260 case count changed")
    require(
        for260.get("missingCondition")
        == "missing_whole_scene_intermediate_rgba8_candidate_evidence_for_exact_and_precision_sensitive_routes",
        "FOR-260 missing condition changed",
    )
    require(
        "webgpu.canvas.draw-rect.src-over" in for260.get("residualRepresentativeRoutes", []),
        "FOR-260 must keep non-image-filter residual route visible",
    )

    kan042 = evidence.get("kan042Matrix")
    require(isinstance(kan042, dict), "KAN-042 matrix missing")
    gaps = kan042.get("implementationGapRows")
    require(isinstance(gaps, list) and gaps, "KAN-042 implementation gaps missing")
    converted = [row for row in gaps if row.get("status") == "pass"]
    require(not converted, f"implementation-gap converted to support: {converted}")


def markdown_report(evidence: dict[str, Any]) -> str:
    stats = evidence["current"]["stats"]
    residual = evidence["current"]["residual"]
    blocker = evidence["blocker"]
    diagnostics = evidence["diagnostics"]
    return f"""# KAN-052 Image Filter Visual Delta

KAN-052 relit `crop-image-filter-nonnull-prepass` after KAN-041/KAN-042 and closes this slice as `blocked=true`, not as a renderer visual fix.

## Decision

| Field | Value |
|---|---|
| rendererChanged | `{evidence["rendererChanged"]}` |
| blocked | `{evidence["blocked"]}` |
| closureDecision | `{evidence["closureDecision"]}` |
| rootCause | `{blocker["rootCause"]}` |
| reasonCode | `{blocker["reasonCode"]}` |

## Selected Row

| Field | Value |
|---|---|
| row | `{evidence["selectedRow"]["rowId"]}` |
| referenceKind | `{evidence["selectedRow"]["referenceKind"]}` |
| GPU route | `{evidence["selectedRow"]["gpuRoute"]}` |
| fallbackReason | `{evidence["selectedRow"]["fallbackReason"]}` |

## Current Evidence

| Metric | Value |
|---|---:|
| GPU similarity | `{stats["gpuSimilarity"]}` |
| GPU matching pixels | `{stats["gpuMatchingPixels"]}` |
| threshold | `{stats["threshold"]}` |
| RGB-only residual pixels | `{residual["rgbOnlyResidualPixels"]}` |
| alpha delta non-zero pixels | `{residual["alphaDeltaNonZeroPixels"]}` |
| color audit max channel delta | `{residual["colorAuditMaxChannelDelta"]}` |

Current artifacts remain under `reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/`.

## Before/After Status

- before: existing selected-row PNG/stat/route artifacts are reused as the investigation input.
- after: renderer artifacts are not materialized because `rendererChanged=false` and the slice is blocked by `{blocker["rootCause"]}`.

## Blocker Evidence

- `FOR-252`: residual reproduces on non image-filter route `{diagnostics["FOR-252"]["nonImageFilterResidualSamples"][0]["route"]}`.
- `FOR-259`: remaining boundary is `{diagnostics["FOR-259"]["remainingBoundary"]}`.
- `FOR-260`: candidate remains diagnostic because `{diagnostics["FOR-260"]["missingCondition"]}`.

No threshold, picture-prepass, CPU readback, broad DAG, or implementation-gap to support conversion is claimed.
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(
        json.dumps(evidence, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (output_dir / OUTPUT_MARKDOWN).write_text(markdown_report(evidence), encoding="utf-8")
    return evidence


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path(__file__).resolve().parents[1]
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    try:
        write_outputs(root, output_dir)
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(f"KAN-052 image-filter visual delta evidence written to {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
