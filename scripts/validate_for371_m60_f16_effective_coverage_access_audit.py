#!/usr/bin/env python3
"""Validate the FOR-371 M60 F16 effective coverage access audit."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-371"
SCENE_ID = "m60-f16-effective-coverage-access-audit-for371"
SOURCE_SCENE_ID = "m60-bounded-stroke-cap-join"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-05-for-371-m60-f16-effective-coverage-access-audit.md"
)
CAPTURE_PRODUCER = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

ROUTE_CPU = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-cpu.json"
)
ROUTE_GPU = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-gpu.json"
)
AA_RESIDUAL = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/aa-residual-diagnostic.json"
)

FOR365_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/f16-constrained-candidate-evaluation-for365/"
    "f16-constrained-candidate-evaluation-for365.json"
)
FOR366_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/f16-positive-residual-target-inventory-for366/"
    "f16-positive-residual-target-inventory-for366.json"
)
FOR367_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join-comparable-f16-evidence-for367/"
    "m60-bounded-stroke-cap-join-comparable-f16-evidence-for367.json"
)
FOR368_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-candidate-metadata-capture-for368/"
    "m60-f16-candidate-metadata-capture-for368.json"
)
FOR369_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-candidate-coordinate-probe-for369/"
    "m60-f16-source-candidate-coordinate-probe-for369.json"
)
FOR370_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-paint-capture-extension-for370/"
    "m60-f16-source-paint-capture-extension-for370.json"
)

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-couverture-aa-effective-apres-for-370"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-370-relie-source-paint-m60-f16-mais-refuse-la-candidate-faute-de-couverture-aa-effective"
)

FOR365_REQUIRED_DECISION = "F16_CONSTRAINED_CANDIDATE_REJECTED_BY_CURRENT_GUARDS"
FOR366_REQUIRED_DECISION = "F16_POSITIVE_RESIDUAL_TARGET_INVENTORY_READY"
FOR367_REQUIRED_DECISION = "M60_BOUNDED_STROKE_CAP_JOIN_COMPARABLE_F16_EVIDENCE_RECORDED"
FOR367_REQUIRED_CLASSIFICATION = "still-missing-comparable-metadata"
FOR368_REQUIRED_DECISION = "M60_F16_CANDIDATE_METADATA_STILL_MISSING"
FOR368_REQUIRED_CLASSIFICATION = "candidate-metadata-still-missing"
FOR369_REQUIRED_DECISION = "M60_F16_SOURCE_CANDIDATE_PROBE_CAPTURE_PATH_STILL_MISSING_SOURCE_METADATA"
FOR369_REQUIRED_CLASSIFICATION = "capture-path-still-missing-source-metadata"
FOR370_REQUIRED_DECISION = "M60_F16_SOURCE_PAINT_CAPTURE_EXTENSION_REFUSED_BY_AMBIGUOUS_COVERAGE"
FOR370_REQUIRED_CLASSIFICATION = "candidate-probe-refused-by-ambiguous-coverage"

DECISION = "M60_F16_EFFECTIVE_COVERAGE_ACCESS_REQUIRES_NEW_EXPORT_POINT"
CLASSIFICATION = "coverage-access-requires-new-export-point"
ALLOWED_CLASSIFICATIONS = [
    "coverage-access-ready-for-candidate-probe",
    "coverage-access-missing-from-current-artifacts",
    CLASSIFICATION,
]
F16_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
FAMILY_ID = "m60-bounded-stroke-cap-join-positive-residual-evidence-target"
REQUIRED_RESIDUAL = 856
REQUIRED_SAMPLE_COUNT = 10
EXPECTED_COORDINATES = [
    (92, 75),
    (91, 76),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
    (21, 81),
    (93, 74),
    (17, 77),
    (69, 81),
]

CPU_OWNER_ROUTE = "cpu.coverage.stroke-cap-join-oracle"
CPU_COVERAGE_PLAN = (
    "PathStrokeCoverage(openPolyline,aa=true,strokeWidth=10,"
    "capJoinMatrix=butt-bevel+round-round+square-bevel)"
)
SOURCE_COVERAGE_STATUS = "requires-new-export-point-from-cpu-coverage-oracle"
EFFECTIVE_ALPHA_STATUS = "requires-new-export-point-from-effective-aa-coverage"
ABSENCE_REASON = (
    "The CPU route names a PathStrokeCoverage owner, but the committed producer and artifacts "
    "only expose route facts, bitmap RGBA deltas, source paint, cap, join, and strokeWidth. "
    "They do not export per-pixel AA coverage or effective source alpha for the sample."
)

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
    "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
    "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
    "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
    "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for371-pycache python3 -m py_compile "
        "scripts/validate_for371_m60_f16_effective_coverage_access_audit.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-371 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def rgba(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{label} must be RGBA")
    result: list[int] = []
    for channel in value:
        require(isinstance(channel, int), f"{label} channel must be int")
        require(0 <= channel <= 255, f"{label} channel out of range")
        result.append(channel)
    return result


def sample_residual(reference: list[int], current: list[int]) -> int:
    return sum(abs(reference[index] - current[index]) for index in range(4))


def find_line(path: Path, needle: str) -> int:
    source = path.read_text(encoding="utf-8")
    for index, line in enumerate(source.splitlines(), start=1):
        if needle in line:
            return index
    fail(f"{rel(path)} no longer contains: {needle}")


def find_line_after(path: Path, anchor: str, needle: str) -> int:
    source = path.read_text(encoding="utf-8")
    lines = source.splitlines()
    for anchor_index, line in enumerate(lines, start=1):
        if anchor in line:
            for index, candidate in enumerate(lines[anchor_index:], start=anchor_index + 1):
                if needle in candidate:
                    return index
            fail(f"{rel(path)} contains {anchor!r} but no later {needle!r}")
    fail(f"{rel(path)} no longer contains anchor: {anchor}")


def validate_implementation_false(data: dict[str, Any], linear: str) -> None:
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), f"{linear} implementation block missing")
    for key in (
        "rendererBehaviorChanged",
        "gpuOrWgslChanged",
        "geometryChanged",
        "coverageChanged",
        "fallbackChanged",
        "thresholdsChanged",
        "promotionChanged",
        "scoreChanged",
        "kadreChanged",
    ):
        require(implementation.get(key) is False, f"{linear} implementation guard changed: {key}")


def validate_inputs() -> dict[str, dict[str, Any]]:
    for365 = load_json(FOR365_ARTIFACT)
    require(for365.get("linear") == "FOR-365", "FOR-365 identity changed")
    require(for365.get("decision") == FOR365_REQUIRED_DECISION, "FOR-365 decision changed")
    validate_implementation_false(for365, "FOR-365")

    for366 = load_json(FOR366_ARTIFACT)
    require(for366.get("linear") == "FOR-366", "FOR-366 identity changed")
    require(for366.get("decision") == FOR366_REQUIRED_DECISION, "FOR-366 decision changed")
    validate_implementation_false(for366, "FOR-366")
    target = for366.get("proposedNextEvidenceTarget")
    require(isinstance(target, dict), "FOR-366 selected target missing")
    require(target.get("selectedRowId") == ROW_ID, "FOR-366 selected row changed")
    require(target.get("familyId") == FAMILY_ID, "FOR-366 selected family changed")
    require(target.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-366 residual changed")

    for367 = load_json(FOR367_ARTIFACT)
    require(for367.get("linear") == "FOR-367", "FOR-367 identity changed")
    require(for367.get("decision") == FOR367_REQUIRED_DECISION, "FOR-367 decision changed")
    require(for367.get("classification") == FOR367_REQUIRED_CLASSIFICATION, "FOR-367 classification changed")
    validate_implementation_false(for367, "FOR-367")
    evidence_line = for367.get("evidenceLine")
    require(isinstance(evidence_line, dict), "FOR-367 evidence line missing")
    require(evidence_line.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-367 residual changed")
    require(evidence_line.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-367 sample count changed")

    for368 = load_json(FOR368_ARTIFACT)
    require(for368.get("linear") == "FOR-368", "FOR-368 identity changed")
    require(for368.get("decision") == FOR368_REQUIRED_DECISION, "FOR-368 decision changed")
    require(for368.get("classification") == FOR368_REQUIRED_CLASSIFICATION, "FOR-368 classification changed")
    validate_implementation_false(for368, "FOR-368")

    for369 = load_json(FOR369_ARTIFACT)
    require(for369.get("linear") == "FOR-369", "FOR-369 identity changed")
    require(for369.get("decision") == FOR369_REQUIRED_DECISION, "FOR-369 decision changed")
    require(for369.get("classification") == FOR369_REQUIRED_CLASSIFICATION, "FOR-369 classification changed")
    validate_implementation_false(for369, "FOR-369")

    for370 = load_json(FOR370_ARTIFACT)
    require(for370.get("linear") == "FOR-370", "FOR-370 identity changed")
    require(for370.get("decision") == FOR370_REQUIRED_DECISION, "FOR-370 decision changed")
    require(for370.get("classification") == FOR370_REQUIRED_CLASSIFICATION, "FOR-370 classification changed")
    require(for370.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-370 residual changed")
    require(for370.get("computedResidual") == REQUIRED_RESIDUAL, "FOR-370 computed residual changed")
    require(for370.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-370 sample count changed")
    require(for370.get("sourcePaintLinkedToSamples") is True, "FOR-370 source paint link missing")
    require(for370.get("readyForCandidateEvaluation") is False, "FOR-370 unexpectedly ready")
    samples = for370.get("samples")
    require(isinstance(samples, list), "FOR-370 samples missing")
    require(len(samples) == REQUIRED_SAMPLE_COUNT, "FOR-370 sample count changed")

    route_cpu = load_json(ROUTE_CPU)
    require(route_cpu.get("selectedRoute") == CPU_OWNER_ROUTE, "CPU owner route changed")
    require(route_cpu.get("coveragePlan") == CPU_COVERAGE_PLAN, "CPU coverage plan changed")
    require(route_cpu.get("status") == "pass", "CPU route status changed")

    route_gpu = load_json(ROUTE_GPU)
    require(route_gpu.get("selectedRoute") == "webgpu.coverage.refuse", "GPU route changed")
    require(route_gpu.get("status") == "expected-unsupported", "GPU status changed")
    require(
        route_gpu.get("remainingRootCause") == "coverage.stroke-cap-join-aa-residual",
        "GPU residual root cause changed",
    )

    aa_residual = load_json(AA_RESIDUAL)
    require(aa_residual.get("sceneId") == SOURCE_SCENE_ID, "AA residual scene changed")
    require(aa_residual.get("remainingRootCause") == "coverage.stroke-cap-join-aa-residual", "root cause changed")
    require(aa_residual.get("greaterThanEightPixels") == REQUIRED_SAMPLE_COUNT, "AA residual sample count changed")
    aa_samples = aa_residual.get("highDeltaSamples")
    require(isinstance(aa_samples, list), "AA residual samples missing")
    require(len(aa_samples) == REQUIRED_SAMPLE_COUNT, "AA residual sample count changed")

    return {
        "for365": for365,
        "for366": for366,
        "for367": for367,
        "for368": for368,
        "for369": for369,
        "for370": for370,
        "routeCpu": route_cpu,
        "routeGpu": route_gpu,
        "aaResidual": aa_residual,
    }


def inspect_existing_access_points() -> dict[str, Any]:
    require(CAPTURE_PRODUCER.is_file(), f"missing capture producer: {rel(CAPTURE_PRODUCER)}")
    residual_anchor = "private fun strokeResidualStats("
    return {
        "captureProducer": {
            "path": rel(CAPTURE_PRODUCER),
            "cpuRouteJsonLine": find_line(CAPTURE_PRODUCER, "private fun cpuRouteJson(): String"),
            "cpuSelectedRouteLine": find_line(CAPTURE_PRODUCER, '"selectedRoute": "cpu.coverage.stroke-cap-join-oracle"'),
            "cpuCoveragePlanLine": find_line(CAPTURE_PRODUCER, '"coveragePlan": "PathStrokeCoverage'),
            "gpuRouteJsonLine": find_line(CAPTURE_PRODUCER, "private fun gpuRouteJson(adapter: String): String"),
            "strokeResidualStatsLine": find_line(CAPTURE_PRODUCER, residual_anchor),
            "residualGpuPixelReadLine": find_line_after(CAPTURE_PRODUCER, residual_anchor, "val gpuPixel = gpu.getPixel(x, y)"),
            "residualReferencePixelReadLine": find_line_after(
                CAPTURE_PRODUCER,
                residual_anchor,
                "val refPixel = reference.getPixel(x, y)",
            ),
            "residualSampleConstructorLine": find_line_after(
                CAPTURE_PRODUCER,
                residual_anchor,
                "highDeltaSamples += ResidualSample(",
            ),
            "residualSampleJsonLine": find_line_after(
                CAPTURE_PRODUCER,
                "private data class ResidualSample(",
                "fun toJson(): String =",
            ),
            "sourcePaintExtensionLine": find_line(CAPTURE_PRODUCER, "private fun sourcePaintSampleJson("),
            "for370SourceCoverageNullLine": find_line(CAPTURE_PRODUCER, '"sourceCoverage": null'),
            "for370EffectiveSourceAlphaNullLine": find_line(CAPTURE_PRODUCER, '"effectiveSourceAlpha": null'),
            "strokePaintBandsLine": find_line(CAPTURE_PRODUCER, "private fun strokePaintBands(): List<StrokePaintBand>"),
        },
        "routeCpuArtifact": {
            "path": rel(ROUTE_CPU),
            "selectedRouteLine": find_line(ROUTE_CPU, '"selectedRoute": "cpu.coverage.stroke-cap-join-oracle"'),
            "coveragePlanLine": find_line(ROUTE_CPU, '"coveragePlan": "PathStrokeCoverage'),
            "statusLine": find_line(ROUTE_CPU, '"status": "pass"'),
        },
        "routeGpuArtifact": {
            "path": rel(ROUTE_GPU),
            "selectedRouteLine": find_line(ROUTE_GPU, '"selectedRoute": "webgpu.coverage.refuse"'),
            "remainingRootCauseLine": find_line(ROUTE_GPU, '"remainingRootCause": "coverage.stroke-cap-join-aa-residual"'),
            "statusLine": find_line(ROUTE_GPU, '"status": "expected-unsupported"'),
        },
        "aaResidualArtifact": {
            "path": rel(AA_RESIDUAL),
            "highDeltaSamplesLine": find_line(AA_RESIDUAL, '"highDeltaSamples": ['),
            "remainingRootCauseLine": find_line(AA_RESIDUAL, '"remainingRootCause": "coverage.stroke-cap-join-aa-residual"'),
            "greaterThanEightPixelsLine": find_line(AA_RESIDUAL, '"greaterThanEightPixels": 10'),
        },
        "for370Artifact": {
            "path": rel(FOR370_ARTIFACT),
            "decisionLine": find_line(FOR370_ARTIFACT, FOR370_REQUIRED_DECISION),
            "classificationLine": find_line(FOR370_ARTIFACT, FOR370_REQUIRED_CLASSIFICATION),
            "firstSourceCoverageNullLine": find_line(FOR370_ARTIFACT, '"sourceCoverage": null'),
            "firstEffectiveSourceAlphaNullLine": find_line(FOR370_ARTIFACT, '"effectiveSourceAlpha": null'),
            "candidateRefusalLine": find_line(FOR370_ARTIFACT, '"candidatePolicyRgbaStatus": "refused-by-ambiguous-coverage"'),
        },
    }


def require_sample_absence(sample: dict[str, Any], index: int) -> None:
    require(sample.get("index") == index, "FOR-370 sample index changed")
    require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "FOR-370 coordinate changed")
    reference = rgba(sample.get("referenceRgba"), "referenceRgba")
    current = rgba(sample.get("currentRgba"), "currentRgba")
    require(sample.get("gpuRgba") == current, "FOR-370 gpu/current RGBA diverged")
    require(sample.get("sampleResidual") == sample_residual(reference, current), "FOR-370 residual changed")
    require(sample.get("sourceCoverage") is None, "FOR-370 unexpectedly exports sourceCoverage")
    require(
        sample.get("sourceCoverageStatus") == "effective-aa-coverage-not-exported-by-strokeResidualStats",
        "FOR-370 sourceCoverageStatus changed",
    )
    require(sample.get("effectiveSourceAlpha") is None, "FOR-370 unexpectedly exports effectiveSourceAlpha")
    require(
        sample.get("effectiveSourceAlphaStatus") == "ambiguous-without-effective-aa-coverage",
        "FOR-370 effectiveSourceAlphaStatus changed",
    )
    require(sample.get("candidatePolicyRgba") is None, "FOR-370 unexpectedly produced candidatePolicyRgba")
    require(
        sample.get("candidatePolicyRgbaStatus") == "refused-by-ambiguous-coverage",
        "FOR-370 candidate status changed",
    )


def build_sample(sample: dict[str, Any], index: int) -> dict[str, Any]:
    require_sample_absence(sample, index)
    reference = rgba(sample["referenceRgba"], "referenceRgba")
    current = rgba(sample["currentRgba"], "currentRgba")
    return {
        "index": index,
        "x": sample["x"],
        "y": sample["y"],
        "strokeBand": sample["strokeBand"],
        "paintSourceRgba": sample["paintSourceRgba"],
        "paintSourceStatus": sample["paintSourceStatus"],
        "cap": sample["cap"],
        "join": sample["join"],
        "strokeWidth": sample["strokeWidth"],
        "referenceRgba": reference,
        "currentRgba": current,
        "gpuRgba": current,
        "sampleResidual": sample_residual(reference, current),
        "maxChannelDelta": sample["maxChannelDelta"],
        "sourceCoverage": None,
        "sourceCoverageStatus": SOURCE_COVERAGE_STATUS,
        "effectiveSourceAlpha": None,
        "effectiveSourceAlphaStatus": EFFECTIVE_ALPHA_STATUS,
        "reliableCoverageOwnerPath": CPU_OWNER_ROUTE,
        "reliableCoverageOwnerArtifact": rel(ROUTE_CPU),
        "reliableCoverageOwnerStatus": "owner-route-known-but-sample-export-missing",
        "missingReason": ABSENCE_REASON,
        "coverageReconstructedFromRgbaDeltas": False,
        "candidatePolicyId": F16_POLICY_ID,
        "candidatePolicyRgba": None,
        "candidatePolicyRgbaStatus": "blocked-until-effective-coverage-export",
        "accessClassification": CLASSIFICATION,
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("allowedClassifications") == ALLOWED_CLASSIFICATIONS, "allowed classifications changed")

    coverage_access = data.get("coverageAccess")
    require(isinstance(coverage_access, dict), "coverageAccess missing")
    require(coverage_access.get("classification") == CLASSIFICATION, "coverageAccess classification changed")
    require(coverage_access.get("readyForCandidateProbe") is False, "candidate probe unexpectedly ready")
    require(coverage_access.get("coverageOwnerExists") is True, "coverage owner should be known")
    require(coverage_access.get("availableInCurrentArtifacts") is False, "coverage unexpectedly available")
    require(coverage_access.get("requiresNewExportPoint") is True, "new export point should be required")
    require(coverage_access.get("ownerRoute") == CPU_OWNER_ROUTE, "owner route changed")
    require(coverage_access.get("ownerCoveragePlan") == CPU_COVERAGE_PLAN, "owner coverage plan changed")
    require(coverage_access.get("coverageReconstructedFromRgbaDeltas") is False, "coverage was reconstructed")

    samples = data.get("samples")
    require(isinstance(samples, list), "samples missing")
    require(len(samples) == REQUIRED_SAMPLE_COUNT, "sample count changed")
    computed_residual = 0
    for index, sample in enumerate(samples, start=1):
        require(sample.get("index") == index, "sample index changed")
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "coordinate changed")
        reference = rgba(sample.get("referenceRgba"), "referenceRgba")
        current = rgba(sample.get("currentRgba"), "currentRgba")
        require(sample.get("gpuRgba") == current, "gpu/current RGBA diverged")
        residual = sample_residual(reference, current)
        require(sample.get("sampleResidual") == residual, "sample residual changed")
        computed_residual += residual
        require(sample.get("sourceCoverage") is None, "sample sourceCoverage was invented")
        require(sample.get("sourceCoverageStatus") == SOURCE_COVERAGE_STATUS, "sourceCoverageStatus changed")
        require(sample.get("effectiveSourceAlpha") is None, "sample effectiveSourceAlpha was invented")
        require(sample.get("effectiveSourceAlphaStatus") == EFFECTIVE_ALPHA_STATUS, "effectiveSourceAlphaStatus changed")
        require(sample.get("reliableCoverageOwnerPath") == CPU_OWNER_ROUTE, "owner path changed")
        require(sample.get("coverageReconstructedFromRgbaDeltas") is False, "sample reconstructs coverage")
        require(sample.get("candidatePolicyRgba") is None, "candidatePolicyRgba was invented")
        require(sample.get("candidatePolicyRgbaStatus") == "blocked-until-effective-coverage-export", "candidate status changed")
        require(sample.get("accessClassification") == CLASSIFICATION, "sample classification changed")
    require(computed_residual == REQUIRED_RESIDUAL, "computed residual changed")
    require(data.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count field changed")
    require(data.get("currentResidual") == REQUIRED_RESIDUAL, "current residual field changed")
    require(data.get("computedResidual") == REQUIRED_RESIDUAL, "computed residual field changed")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal changed: {key}")


def build_artifact() -> dict[str, Any]:
    inputs = validate_inputs()
    access_points = inspect_existing_access_points()
    for370_samples = inputs["for370"]["samples"]
    samples = [build_sample(sample, index) for index, sample in enumerate(for370_samples, start=1)]
    computed_residual = sum(sample["sampleResidual"] for sample in samples)
    require(computed_residual == REQUIRED_RESIDUAL, "computed residual changed")

    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceSceneId": SOURCE_SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "decision": DECISION,
        "classification": CLASSIFICATION,
        "allowedClassifications": ALLOWED_CLASSIFICATIONS,
        "decisionReason": (
            "A reliable coverage owner is visible through the CPU route "
            f"{CPU_OWNER_ROUTE}, but the current committed producer and artifacts do not expose "
            "per-pixel effective AA coverage or effective source alpha for the 10 M60 samples. "
            "The next step must add an explicit proof export point instead of reconstructing "
            "coverage from RGBA deltas."
        ),
        "requiredFor370": {
            "artifact": rel(FOR370_ARTIFACT),
            "decision": FOR370_REQUIRED_DECISION,
            "classification": FOR370_REQUIRED_CLASSIFICATION,
            "sampleCount": REQUIRED_SAMPLE_COUNT,
            "currentResidual": REQUIRED_RESIDUAL,
        },
        "inspectedInputs": {
            "for365Artifact": rel(FOR365_ARTIFACT),
            "for366Artifact": rel(FOR366_ARTIFACT),
            "for367Artifact": rel(FOR367_ARTIFACT),
            "for368Artifact": rel(FOR368_ARTIFACT),
            "for369Artifact": rel(FOR369_ARTIFACT),
            "for370Artifact": rel(FOR370_ARTIFACT),
            "captureProducer": rel(CAPTURE_PRODUCER),
            "routeCpu": rel(ROUTE_CPU),
            "routeGpu": rel(ROUTE_GPU),
            "aaResidualDiagnostic": rel(AA_RESIDUAL),
        },
        "evidenceLines": access_points,
        "coverageAccess": {
            "classification": CLASSIFICATION,
            "readyForCandidateProbe": False,
            "coverageOwnerExists": True,
            "ownerRoute": CPU_OWNER_ROUTE,
            "ownerCoveragePlan": CPU_COVERAGE_PLAN,
            "ownerArtifact": rel(ROUTE_CPU),
            "availableInCurrentArtifacts": False,
            "requiresNewExportPoint": True,
            "requiredExportPoint": (
                "Add a diagnostic-only per-sample export from the CPU stroke coverage oracle or "
                "its PathStrokeCoverage evaluation path, carrying effective AA coverage/source "
                "alpha for the preserved M60 coordinates."
            ),
            "coverageReconstructedFromRgbaDeltas": False,
            "missingReason": ABSENCE_REASON,
        },
        "sampleCount": REQUIRED_SAMPLE_COUNT,
        "currentResidual": REQUIRED_RESIDUAL,
        "computedResidual": computed_residual,
        "candidatePolicyId": F16_POLICY_ID,
        "samples": samples,
        "nonGoalsPreserved": {
            "rendererBehaviorChanged": False,
            "candidateImplementationAuthorized": False,
            "candidatePolicyRgbaProduced": False,
            "scoreIncreased": False,
            "thresholdChanged": False,
            "promotionChanged": False,
            "gpuOrWgslChanged": False,
            "geometryChanged": False,
            "coverageProductionChanged": False,
            "fallbackChanged": False,
            "kadreChanged": False,
            "f16PremulBlendRuntimeChanged": False,
            "skBitmapGetPixelChanged": False,
            "rendererSceneBranchAdded": False,
            "rendererCoordinateBranchAdded": False,
            "rendererSelectedCellBranchAdded": False,
            "fixtureOnlyPathAdded": False,
            "fullGmCropPathAdded": False,
            "approximatedAaCoverageRebuilt": False,
        },
        "validation": {
            "commands": VALIDATION_COMMANDS,
            "notes": "This validator generates and validates the FOR-371 audit artifact and report.",
        },
    }


def report_text(data: dict[str, Any]) -> str:
    lines = data["evidenceLines"]
    coverage = data["coverageAccess"]
    sample_rows = "\n".join(
        "| {index} | {x} | {y} | `{band}` | `{reference}` | `{current}` | `{paint}` | `{coverage}` | `{alpha}` |".format(
            index=sample["index"],
            x=sample["x"],
            y=sample["y"],
            band=sample["strokeBand"],
            reference=sample["referenceRgba"],
            current=sample["currentRgba"],
            paint=sample["paintSourceRgba"],
            coverage=sample["sourceCoverageStatus"],
            alpha=sample["effectiveSourceAlphaStatus"],
        )
        for sample in data["samples"]
    )
    commands = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    producer = lines["captureProducer"]
    route_cpu = lines["routeCpuArtifact"]
    route_gpu = lines["routeGpuArtifact"]
    aa_residual = lines["aaResidualArtifact"]
    for370 = lines["for370Artifact"]
    return f"""# FOR-371 Audit acces couverture effective M60 F16

Linear: `FOR-371`

Decision: `{data['decision']}`

Classification: `{data['classification']}`

Cet audit ne change pas le rendu. Il relit FOR-370 et les artefacts M60
existants pour verifier si les 10 coordonnees disposent deja d'une source
fiable de couverture AA effective ou d'alpha source effectif.

## Conclusion

La route CPU expose un proprietaire probable de la donnee:
`{coverage['ownerRoute']}` avec `{coverage['ownerCoveragePlan']}`.
Cette information suffit a cibler le prochain point d'export, mais les
artefacts actuels ne portent pas la valeur par pixel pour les 10 samples.

Resultat: `{data['classification']}`. La prochaine etape doit ajouter un
export de preuve, limite au diagnostic, depuis le proprietaire de couverture
CPU ou son chemin `PathStrokeCoverage`.

## Lignes de preuve

- Producteur: `{producer['path']}`
- Route CPU nommee: `{producer['path']}:{producer['cpuSelectedRouteLine']}` et `{route_cpu['path']}:{route_cpu['selectedRouteLine']}`
- Plan CPU nomme: `{producer['path']}:{producer['cpuCoveragePlanLine']}` et `{route_cpu['path']}:{route_cpu['coveragePlanLine']}`
- Route GPU encore refusee: `{route_gpu['path']}:{route_gpu['selectedRouteLine']}` et racine `{route_gpu['path']}:{route_gpu['remainingRootCauseLine']}`
- Samples AA residuels: `{aa_residual['path']}:{aa_residual['highDeltaSamplesLine']}`
- Lecture du producteur limitee aux pixels bitmap: `{producer['path']}:{producer['residualGpuPixelReadLine']}` et `{producer['path']}:{producer['residualReferencePixelReadLine']}`
- Serialization residuelle limitee a `referenceRgba` / `gpuRgba`: `{producer['path']}:{producer['residualSampleJsonLine']}`
- FOR-370 ajoute `source paint`, mais garde `sourceCoverage` et `effectiveSourceAlpha` nuls: `{for370['path']}:{for370['firstSourceCoverageNullLine']}` et `{for370['path']}:{for370['firstEffectiveSourceAlphaNullLine']}`

## Samples audites

| # | x | y | bande | reference RGBA | current/gpu RGBA | source paint RGBA | sourceCoverage | effectiveSourceAlpha |
|---|---:|---:|---|---|---|---|---|---|
{sample_rows}

Residuel conserve: `{data['currentResidual']}`.

## Non-objectifs preserves

- Pas de `candidatePolicyRgba` produit.
- Pas de reconstruction de couverture depuis les deltas RGBA.
- Pas de changement renderer/runtime, GPU/WGSL, geometrie, couverture de production, fallback, Kadre, seuil, score ou promotion.
- Pas de modification de `SkBitmap.getPixel`.

## Validations

{commands}
"""


def main() -> int:
    data = build_artifact()
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=True) + "\n")
    write_if_changed(REPORT, report_text(data))
    written = load_json(ARTIFACT)
    validate_artifact(written)
    print(f"FOR-371 audit validated: {rel(ARTIFACT)}")
    print(f"FOR-371 report validated: {rel(REPORT)}")
    print(f"decision={DECISION}")
    print(f"classification={CLASSIFICATION}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
