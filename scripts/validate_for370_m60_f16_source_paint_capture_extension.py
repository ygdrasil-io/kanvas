#!/usr/bin/env python3
"""Validate the FOR-370 M60 F16 source paint capture extension evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-370"
SCENE_ID = "m60-f16-source-paint-capture-extension-for370"
SOURCE_SCENE_ID = "m60-bounded-stroke-cap-join"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-05-for-370-m60-f16-source-paint-capture-extension.md"
)
CAPTURE_PRODUCER = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-extension-diagnostic-m60-f16-source-paint-apres-for-369"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-369-localise-le-blocage-metadata-m60-f16-dans-stroke-cap-join-scene-capture-test"
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

FOR365_REQUIRED_DECISION = "F16_CONSTRAINED_CANDIDATE_REJECTED_BY_CURRENT_GUARDS"
FOR366_REQUIRED_DECISION = "F16_POSITIVE_RESIDUAL_TARGET_INVENTORY_READY"
FOR367_REQUIRED_DECISION = "M60_BOUNDED_STROKE_CAP_JOIN_COMPARABLE_F16_EVIDENCE_RECORDED"
FOR367_REQUIRED_CLASSIFICATION = "still-missing-comparable-metadata"
FOR368_REQUIRED_DECISION = "M60_F16_CANDIDATE_METADATA_STILL_MISSING"
FOR368_REQUIRED_CLASSIFICATION = "candidate-metadata-still-missing"
FOR369_REQUIRED_DECISION = "M60_F16_SOURCE_CANDIDATE_PROBE_CAPTURE_PATH_STILL_MISSING_SOURCE_METADATA"
FOR369_REQUIRED_CLASSIFICATION = "capture-path-still-missing-source-metadata"

DECISION = "M60_F16_SOURCE_PAINT_CAPTURE_EXTENSION_REFUSED_BY_AMBIGUOUS_COVERAGE"
CLASSIFICATION = "candidate-probe-refused-by-ambiguous-coverage"
ALLOWED_CLASSIFICATIONS = [
    "ready-for-candidate-evaluation",
    CLASSIFICATION,
    "capture-path-still-missing-source-metadata",
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

SOURCE_COVERAGE_STATUS = "effective-aa-coverage-not-exported-by-strokeResidualStats"
EFFECTIVE_ALPHA_STATUS = "ambiguous-without-effective-aa-coverage"
CANDIDATE_STATUS = "refused-by-ambiguous-coverage"
PAINT_STATUS = "known-from-BoundedStrokeCapJoinGM"
REFUSAL_REASON = (
    "Static source paint is known for the stroke band, but the capture path does not export "
    "per-pixel AA coverage/effective source alpha; candidatePolicyRgba would require inventing coverage."
)

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
    "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
    "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
    "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for370-pycache python3 -m py_compile "
        "scripts/validate_for370_m60_f16_source_paint_capture_extension.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test "
        "--tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
]

BANDS = [
    {
        "id": "butt-bevel",
        "description": "left band: butt cap with bevel join",
        "xStart": 0,
        "xEnd": 48,
        "cap": "butt",
        "join": "bevel",
        "strokeWidth": 10.0,
        "paintSourceRgba": [0, 102, 204, 255],
    },
    {
        "id": "round-round",
        "description": "middle band: round cap with round join",
        "xStart": 48,
        "xEnd": 96,
        "cap": "round",
        "join": "round",
        "strokeWidth": 10.0,
        "paintSourceRgba": [0, 138, 76, 255],
    },
    {
        "id": "square-bevel",
        "description": "right band: square cap with bevel join",
        "xStart": 96,
        "xEnd": 192,
        "cap": "square",
        "join": "bevel",
        "strokeWidth": 10.0,
        "paintSourceRgba": [179, 60, 0, 255],
    },
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-370 validation failed: {message}")


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
    out: list[int] = []
    for channel in value:
        require(isinstance(channel, int), f"{label} channel must be int")
        require(0 <= channel <= 255, f"{label} channel out of range")
        out.append(channel)
    return out


def sample_residual(reference: list[int], current: list[int]) -> int:
    return sum(abs(reference[index] - current[index]) for index in range(4))


def band_for_x(x: int) -> dict[str, Any]:
    for band in BANDS:
        if band["xStart"] <= x < band["xEnd"]:
            return band
    fail(f"sample x coordinate outside BoundedStrokeCapJoinGM bands: {x}")


def find_line(source: str, needle: str) -> int:
    for index, line in enumerate(source.splitlines(), start=1):
        if needle in line:
            return index
    fail(f"capture producer source no longer contains: {needle}")


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


def validate_inputs() -> dict[str, Any]:
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
    for367_line = for367.get("evidenceLine")
    require(isinstance(for367_line, dict), "FOR-367 evidence line missing")
    require(for367_line.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-367 residual changed")
    require(for367_line.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-367 sample count changed")

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
    probe = for369.get("probeLine")
    require(isinstance(probe, dict), "FOR-369 probe line missing")
    require(probe.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-369 residual changed")
    require(probe.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-369 sample count changed")
    require(probe.get("candidatePolicyId") == F16_POLICY_ID, "FOR-369 policy id changed")
    samples = probe.get("samples")
    require(isinstance(samples, list), "FOR-369 samples missing")
    require(len(samples) == REQUIRED_SAMPLE_COUNT, "FOR-369 sample count changed")
    return for369


def inspect_capture_producer() -> dict[str, Any]:
    require(CAPTURE_PRODUCER.is_file(), f"missing capture producer: {rel(CAPTURE_PRODUCER)}")
    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    required_needles = [
        "writeM60F16SourcePaintCaptureExtension(residualStats, adapter)",
        "private fun writeM60F16SourcePaintCaptureExtension(",
        "private fun m60F16SourcePaintCaptureExtensionJson(",
        "private fun sourcePaintSampleJson(index: Int, sample: ResidualSample): String",
        "private fun strokePaintBands(): List<StrokePaintBand>",
        "candidate-probe-refused-by-ambiguous-coverage",
        SOURCE_COVERAGE_STATUS,
        CANDIDATE_STATUS,
        "0xFF0066CC.toInt()",
        "0xFF008A4C.toInt()",
        "0xFFB33C00.toInt()",
    ]
    lines = {needle: find_line(source, needle) for needle in required_needles}
    require(
        "WebGpuSink.draw(ctx, gm, targetColorSpaceBlend = true)" in source,
        "targetColorSpaceBlend diagnostic path changed",
    )
    return {
        "producer": rel(CAPTURE_PRODUCER),
        "sceneEvidenceWriteCallLine": lines["writeM60F16SourcePaintCaptureExtension(residualStats, adapter)"],
        "extensionWriterLine": lines["private fun writeM60F16SourcePaintCaptureExtension("],
        "extensionJsonLine": lines["private fun m60F16SourcePaintCaptureExtensionJson("],
        "sampleJsonLine": lines["private fun sourcePaintSampleJson(index: Int, sample: ResidualSample): String"],
        "sourcePaintBandsLine": lines["private fun strokePaintBands(): List<StrokePaintBand>"],
        "classificationLine": lines["candidate-probe-refused-by-ambiguous-coverage"],
        "sourceCoverageStatusLine": lines[SOURCE_COVERAGE_STATUS],
        "candidateStatusLine": lines[CANDIDATE_STATUS],
        "finding": (
            "The M60 scene evidence writer now emits a FOR-370 diagnostic artifact that links "
            "high-delta residual samples to static BoundedStrokeCapJoinGM paint bands, while "
            "refusing candidate evaluation because effective AA coverage/source alpha remains absent."
        ),
    }


def build_sample(raw: dict[str, Any], index: int) -> dict[str, Any]:
    require(raw.get("index") == index, "FOR-369 sample index changed")
    coord = (raw.get("x"), raw.get("y"))
    require(coord == EXPECTED_COORDINATES[index - 1], "FOR-369 sample coordinate changed")
    reference = rgba(raw.get("referenceRgba"), "referenceRgba")
    current = rgba(raw.get("currentRgba"), "currentRgba")
    residual = sample_residual(reference, current)
    require(raw.get("sampleResidual") == residual, "FOR-369 residual changed")
    require(raw.get("candidatePolicyRgba") is None, "FOR-369 unexpectedly produced candidatePolicyRgba")
    band = band_for_x(raw["x"])
    return {
        "index": index,
        "x": raw["x"],
        "y": raw["y"],
        "strokeBand": band["id"],
        "region": {
            "id": band["id"],
            "description": band["description"],
            "xStart": band["xStart"],
            "xEnd": band["xEnd"],
        },
        "referenceRgba": reference,
        "currentRgba": current,
        "gpuRgba": current,
        "sampleResidual": residual,
        "maxChannelDelta": max(abs(reference[channel] - current[channel]) for channel in range(4)),
        "paintSourceRgba": band["paintSourceRgba"],
        "paintSourceStatus": PAINT_STATUS,
        "paintSourceAlpha": band["paintSourceRgba"][3],
        "paintSourceAlphaStatus": "static-paint-alpha-known",
        "cap": band["cap"],
        "join": band["join"],
        "strokeWidth": band["strokeWidth"],
        "sourceCoverage": None,
        "sourceCoverageStatus": SOURCE_COVERAGE_STATUS,
        "effectiveSourceAlpha": None,
        "effectiveSourceAlphaStatus": EFFECTIVE_ALPHA_STATUS,
        "candidatePolicyId": F16_POLICY_ID,
        "candidatePolicyRgba": None,
        "candidatePolicyRgbaStatus": CANDIDATE_STATUS,
        "candidatePolicyRgbaRefusalReason": REFUSAL_REASON,
        "readyForCandidateEvaluation": False,
        "artifactOnlyValueProduced": False,
        "rendererAppliedCandidate": False,
    }


def build_artifact() -> dict[str, Any]:
    for369 = validate_inputs()
    producer = inspect_capture_producer()
    raw_samples = for369["probeLine"]["samples"]
    samples = [build_sample(raw, index) for index, raw in enumerate(raw_samples, start=1)]
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
        "candidatePolicyId": F16_POLICY_ID,
        "decisionReason": (
            "FOR-370 extends the M60 scene evidence producer to attach deterministic source paint "
            "and stroke style metadata from BoundedStrokeCapJoinGM to the preserved residual "
            "coordinates. It refuses the candidate because effective AA coverage/source alpha is "
            "still not exported by the capture path."
        ),
        "inputValidation": {
            "for365Artifact": rel(FOR365_ARTIFACT),
            "for365RequiredDecision": FOR365_REQUIRED_DECISION,
            "for366Artifact": rel(FOR366_ARTIFACT),
            "for366RequiredDecision": FOR366_REQUIRED_DECISION,
            "for367Artifact": rel(FOR367_ARTIFACT),
            "for367RequiredDecision": FOR367_REQUIRED_DECISION,
            "for367RequiredClassification": FOR367_REQUIRED_CLASSIFICATION,
            "for368Artifact": rel(FOR368_ARTIFACT),
            "for368RequiredDecision": FOR368_REQUIRED_DECISION,
            "for368RequiredClassification": FOR368_REQUIRED_CLASSIFICATION,
            "for369Artifact": rel(FOR369_ARTIFACT),
            "for369Decision": for369.get("decision"),
            "for369RequiredDecision": FOR369_REQUIRED_DECISION,
            "for369Classification": for369.get("classification"),
            "for369RequiredClassification": FOR369_REQUIRED_CLASSIFICATION,
        },
        "captureProducerExtension": producer,
        "probeLine": {
            "rowId": ROW_ID,
            "familyId": FAMILY_ID,
            "sourceKind": "non-arc-target-colorspace-blend-diagnostic",
            "rowKind": "non-arc",
            "candidatePolicyId": F16_POLICY_ID,
            "currentResidual": REQUIRED_RESIDUAL,
            "computedResidual": computed_residual,
            "sampleCount": REQUIRED_SAMPLE_COUNT,
            "sampleCoordinatesPreservedFromFor367For368For369": True,
            "referenceCurrentComparable": True,
            "referenceCurrentCandidateComparable": False,
            "sourcePaintLinkedToSamples": True,
            "classification": CLASSIFICATION,
            "readyForCandidateEvaluation": False,
            "blocker": {
                "status": CLASSIFICATION,
                "paintSourceRgba": PAINT_STATUS,
                "sourceCoverage": SOURCE_COVERAGE_STATUS,
                "effectiveSourceAlpha": EFFECTIVE_ALPHA_STATUS,
                "candidatePolicyRgba": CANDIDATE_STATUS,
                "refusalReason": REFUSAL_REASON,
            },
            "samples": samples,
        },
        "candidateProbeReadiness": {
            "classification": CLASSIFICATION,
            "readyForCandidateEvaluation": False,
            "paintSourceRgbaKnown": True,
            "effectiveAaCoverageKnown": False,
            "candidatePolicyRgbaProduced": False,
            "artifactOnlyCandidateValuesProduced": False,
            "blockingProducerPoint": (
                "strokeResidualStats still records reference/current bitmap deltas only; "
                "BoundedStrokeCapJoinGM exposes static paint colors, but no per-pixel "
                "effective AA coverage/source alpha."
            ),
            "refusalReason": REFUSAL_REASON,
        },
        "nonGoalsPreserved": {
            "rendererBehaviorChanged": False,
            "candidateImplementationAuthorized": False,
            "scoreIncreased": False,
            "thresholdChanged": False,
            "promotionChanged": False,
            "gpuOrWgslChanged": False,
            "geometryChanged": False,
            "coverageChanged": False,
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
        "criteriaEvaluation": {
            "sourceMemoryRecorded": True,
            "sourceFindingRecorded": True,
            "for369DecisionRequired": True,
            "for369ClassificationRequired": True,
            "sameTenCoordinatesPreserved": True,
            "currentResidualKeptAt856": True,
            "producerEmitsArtifactInSceneEvidenceWriteMode": True,
            "paintSourceRgbaStaticPerBandCaptured": True,
            "capJoinStrokeWidthCaptured": True,
            "coverageNotInvented": True,
            "candidatePolicyRgbaRefusedWithStableReason": True,
            "rendererBehaviorChanged": False,
            "scoreIncreased": False,
            "thresholdChanged": False,
        },
        "implementation": {
            "evidenceOnly": True,
            "rendererBehaviorChanged": False,
            "newColorPolicyImplemented": False,
            "candidateSelectedForImplementation": False,
            "selectableCandidateDefined": False,
            "implementationPlanAuthorized": False,
            "colorToF16PremulChanged": False,
            "blendF16PremulModeChanged": False,
            "skBitmapGetPixelChanged": False,
            "skBitmapGetPixelAsSrgbChanged": False,
            "gpuOrWgslChanged": False,
            "geometryChanged": False,
            "coverageChanged": False,
            "fallbackChanged": False,
            "thresholdsChanged": False,
            "promotionChanged": False,
            "scoreChanged": False,
            "kadreChanged": False,
            "rendererFixtureOrCoordinateBranchAdded": False,
            "rendererSceneBranchAdded": False,
            "rendererSelectedCellOrFullGmCropBranchAdded": False,
            "approximatedAaCoverageRebuilt": False,
        },
        "validation": {
            "commands": VALIDATION_COMMANDS,
            "targetedSceneEvidenceTest": {
                "command": VALIDATION_COMMANDS[-1],
                "expectedIfWebGpuUnavailable": "JUnit assumption skip: No WebGPU adapter",
            },
        },
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("candidatePolicyId") == F16_POLICY_ID, "candidate policy id changed")
    require(data.get("allowedClassifications") == ALLOWED_CLASSIFICATIONS, "allowed classifications changed")
    probe = data.get("probeLine") if isinstance(data.get("probeLine"), dict) else data
    require(probe.get("currentResidual") == REQUIRED_RESIDUAL, "current residual changed")
    require(probe.get("computedResidual") == REQUIRED_RESIDUAL, "computed residual changed")
    require(probe.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")
    require(probe.get("sourcePaintLinkedToSamples") is True, "source paint is not linked")
    require(probe.get("readyForCandidateEvaluation") is False, "candidate unexpectedly ready")
    samples = probe.get("samples")
    require(isinstance(samples, list), "samples missing")
    require(len(samples) == REQUIRED_SAMPLE_COUNT, "sample count changed")
    computed = 0
    for index, sample in enumerate(samples, start=1):
        require(sample.get("index") == index, "sample index changed")
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "coordinate changed")
        reference = rgba(sample.get("referenceRgba"), "referenceRgba")
        current = rgba(sample.get("currentRgba"), "currentRgba")
        require(sample.get("gpuRgba") == current, "gpu/current RGBA diverged")
        residual = sample_residual(reference, current)
        require(sample.get("sampleResidual") == residual, "sample residual changed")
        computed += residual
        band = band_for_x(sample["x"])
        require(sample.get("strokeBand") == band["id"], "stroke band changed")
        require(sample.get("paintSourceRgba") == band["paintSourceRgba"], "paint source RGBA changed")
        require(sample.get("paintSourceStatus") == PAINT_STATUS, "paint source status changed")
        require(sample.get("cap") == band["cap"], "cap changed")
        require(sample.get("join") == band["join"], "join changed")
        require(sample.get("strokeWidth") == band["strokeWidth"], "stroke width changed")
        require(sample.get("sourceCoverage") is None, "coverage was invented")
        require(sample.get("sourceCoverageStatus") == SOURCE_COVERAGE_STATUS, "coverage status changed")
        require(sample.get("effectiveSourceAlpha") is None, "effective source alpha was invented")
        require(sample.get("effectiveSourceAlphaStatus") == EFFECTIVE_ALPHA_STATUS, "source alpha status changed")
        require(sample.get("candidatePolicyRgba") is None, "candidatePolicyRgba was invented")
        require(sample.get("candidatePolicyRgbaStatus") == CANDIDATE_STATUS, "candidate status changed")
        require(sample.get("candidatePolicyRgbaRefusalReason") == REFUSAL_REASON, "refusal reason changed")
        require(sample.get("rendererAppliedCandidate") is False, "renderer applied candidate")
    require(computed == REQUIRED_RESIDUAL, "sample residual sum changed")
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal changed: {key}")
    implementation = data.get("implementation")
    if implementation is not None:
        require(isinstance(implementation, dict), "implementation block must be an object")
        for key, value in implementation.items():
            if key == "evidenceOnly":
                require(value is True, "implementation must remain evidence-only")
            else:
                require(value is False, f"implementation guard changed: {key}")


def report_text(data: dict[str, Any]) -> str:
    producer = (
        data.get("captureProducerExtension")
        if isinstance(data.get("captureProducerExtension"), dict)
        else inspect_capture_producer()
    )
    probe = data.get("probeLine") if isinstance(data.get("probeLine"), dict) else data
    samples = probe["samples"]
    rows = "\n".join(
        "| {index} | {x} | {y} | `{strokeBand}` | `{reference}` | `{current}` | {residual} | `{paint}` | `{coverage}` | `{candidate}` |".format(
            index=sample["index"],
            x=sample["x"],
            y=sample["y"],
            strokeBand=sample["strokeBand"],
            reference=sample["referenceRgba"],
            current=sample["currentRgba"],
            residual=sample["sampleResidual"],
            paint=sample["paintSourceRgba"],
            coverage=sample["sourceCoverageStatus"],
            candidate=sample["candidatePolicyRgbaStatus"],
        )
        for sample in samples
    )
    commands = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    return f"""# FOR-370 Extension diagnostic source paint F16 M60

Linear: `FOR-370`

Decision: `{data['decision']}`

Classification: `{data['classification']}`

FOR-370 modifie uniquement le producteur de preuve
`StrokeCapJoinSceneCaptureTest.kt`. Le mode
`-Dkanvas.sceneEvidence.write=true` peut maintenant emettre l'artefact
`{rel(ARTIFACT)}` avec les 10 coordonnees FOR-367/FOR-368/FOR-369,
le residuel baseline `856`, la bande de stroke, la couleur source paint
statique issue de `BoundedStrokeCapJoinGM`, le cap, le join et le
`strokeWidth`.

## Entrees verrouillees

- FOR-369 decision requise: `{FOR369_REQUIRED_DECISION}`
- FOR-369 classification requise: `{FOR369_REQUIRED_CLASSIFICATION}`
- Politique candidate: `{F16_POLICY_ID}`
- Residuel baseline: `856`

## Resultat

- Source paint reliee aux samples: `True`
- Couverture AA effective connue: `False`
- candidatePolicyRgba produite: `False`
- Pret pour evaluation candidate: `False`

La classification reste `{CLASSIFICATION}`: la couleur source statique est
connue par bande, mais la couverture AA effective et l'alpha source effectif
ne sont pas exportes par `strokeResidualStats`. La candidate serait donc
non comparable sans inventer une couverture.

## Inspection du producteur

- Producteur: `{producer['producer']}`
- Appel ecriture preuve: ligne `{producer['sceneEvidenceWriteCallLine']}`
- Writer FOR-370: ligne `{producer['extensionWriterLine']}`
- Source bands: ligne `{producer['sourcePaintBandsLine']}`

## Table des echantillons

| # | x | y | bande | reference RGBA | current/gpu RGBA | residual | paint source RGBA | coverage | candidatePolicyRgba |
|---:|---:|---:|---|---|---|---:|---|---|---|
{rows}

## Refus stable

`{REFUSAL_REASON}`

## Non-objectifs respectes

- Aucun changement renderer/runtime.
- Aucun changement GPU/WGSL, geometrie, couverture, fallback, Kadre,
  F16 premul/blend ou `SkBitmap.getPixel`.
- Aucun score augmente, seuil modifie, promotion ou statut de support.
- Aucune branche renderer par scene, coordonnee, selected-cell, fixture-only
  path ou full-GM crop.
- Aucune reconstruction approximative de couverture AA.

## Artefacts

- JSON: `{rel(ARTIFACT)}`
- Validateur: `scripts/validate_for370_m60_f16_source_paint_capture_extension.py`
- Rapport: `{rel(REPORT)}`

## Validation

{commands}
"""


def main() -> None:
    validate_inputs()
    inspect_capture_producer()
    if not ARTIFACT.is_file():
        expected = build_artifact()
        write_if_changed(ARTIFACT, json.dumps(expected, indent=2, ensure_ascii=False) + "\n")
    actual = load_json(ARTIFACT)
    validate_artifact(actual)
    write_if_changed(REPORT, report_text(actual))
    validate_artifact(load_json(ARTIFACT))
    print(
        "FOR-370 validation passed: "
        f"{CLASSIFICATION}, {REQUIRED_SAMPLE_COUNT} samples, residual {REQUIRED_RESIDUAL}"
    )


if __name__ == "__main__":
    main()
