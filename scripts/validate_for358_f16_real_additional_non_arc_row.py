#!/usr/bin/env python3
"""Validate the FOR-358 real additional non-arc F16 comparable row."""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-358"
SCENE_ID = "f16-real-additional-non-arc-row-for358"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-358-f16-real-additional-non-arc-row.md"
SKIA_REFERENCE_JSON = ARTIFACT_DIR / "skia-reference-samples.json"
SKIA_REFERENCE_PNG = ARTIFACT_DIR / "skia-reference.png"
SKIA_REFERENCE_PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"
CURRENT_KANVAS_JSON = ARTIFACT_DIR / "current-kanvas-samples.json"

FOR345_SCENE_ID = "non-arc-rec2020-f16-reference-row-for345"
FOR345_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR345_SCENE_ID / f"{FOR345_SCENE_ID}.json"
)
FOR357_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/f16-additional-non-arc-comparable-row-for357/"
    "f16-additional-non-arc-comparable-row-for357.json"
)
FOR356_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/f16-generalized-non-scene-arc-delta-broader-evidence-for356/"
    "f16-generalized-non-scene-arc-delta-broader-evidence-for356.json"
)
FOR355_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/f16-generalized-non-scene-arc-delta-candidate-for355/"
    "f16-generalized-non-scene-arc-delta-candidate-for355.json"
)

FOR357_REQUIRED_DECISION = "F16_ADDITIONAL_NON_ARC_COMPARABLE_ROW_PARTIAL_INSUFFICIENT_REFERENCE"
FOR356_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_BROADER_EVIDENCE_PARTIAL"
FOR355_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE"

CANDIDATE_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-capture-real-additional-non-arc-f16-comparable-row-ticket"
)
SOURCE_FINDINGS = [
    "global/kanvas/findings/for-357-additional-non-arc-comparable-row-reference-gap",
]

DECISION_ACCEPTS = "F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE"
DECISION_REJECTS = "F16_REAL_ADDITIONAL_NON_ARC_ROW_REJECTS_CANDIDATE"
DECISION_PARTIAL = "F16_REAL_ADDITIONAL_NON_ARC_ROW_CAPTURE_PARTIAL"
ALLOWED_DECISIONS = [DECISION_ACCEPTS, DECISION_REJECTS, DECISION_PARTIAL]

SOURCE = PROJECT_ROOT / "tools/skia-reference/non_arc_rec2020_f16_for358_reference.cpp"
RUNNER = PROJECT_ROOT / "tools/skia-reference/build_for358_f16_real_additional_non_arc_row.py"
CURRENT_CAPTURE_TEST = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/kotlin/org/skia/tests/For358RealAdditionalNonArcF16CurrentCaptureTest.kt"
)

REFERENCE_CAPTURE_COMMAND = "rtk python3 tools/skia-reference/build_for358_f16_real_additional_non_arc_row.py"
CURRENT_CAPTURE_COMMAND = (
    f"KANVAS_FOR358_CURRENT_CAPTURE_OUTPUT={CURRENT_KANVAS_JSON.relative_to(PROJECT_ROOT)} "
    "rtk ./gradlew --no-daemon --rerun-tasks "
    ":skia-integration-tests:test --tests org.skia.tests.For358RealAdditionalNonArcF16CurrentCaptureTest"
)

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py",
    REFERENCE_CAPTURE_COMMAND,
    CURRENT_CAPTURE_COMMAND,
    "rtk python3 scripts/validate_for357_f16_additional_non_arc_comparable_row.py",
    "rtk python3 scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 -m py_compile scripts/validate_for358_f16_real_additional_non_arc_row.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]

SAMPLE_METADATA = [
    {
        "name": "for358_background_top_left",
        "zone": "background",
        "x": 0,
        "y": 0,
        "covered": False,
        "rawReferenceAlpha": 255,
    },
    {
        "name": "for358_rect_center",
        "zone": "fill-center",
        "x": 14,
        "y": 14,
        "covered": True,
        "rawReferenceAlpha": 255,
    },
    {
        "name": "for358_rect_left_inside",
        "zone": "fill-left-inside",
        "x": 5,
        "y": 14,
        "covered": True,
        "rawReferenceAlpha": 255,
    },
    {
        "name": "for358_rect_bottom_inside",
        "zone": "fill-bottom-inside",
        "x": 14,
        "y": 20,
        "covered": True,
        "rawReferenceAlpha": 255,
    },
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-358 validation failed: {message}")


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
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def rgba(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{label} must be RGBA")
    result = []
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{label} channel out of range")
        result.append(channel)
    return result


def abs_delta(a: list[int], b: list[int]) -> list[int]:
    return [abs(a[i] - b[i]) for i in range(4)]


def signed_delta(a: list[int], b: list[int]) -> list[int]:
    return [a[i] - b[i] for i in range(4)]


def validate_prerequisite(
    data: dict[str, Any],
    linear: str,
    required_decision: str,
) -> None:
    require(data.get("linear") == linear, f"{linear} identity changed")
    require(data.get("decision") == required_decision, f"{linear} required decision missing")
    candidate = data.get("candidate")
    require(isinstance(candidate, dict), f"{linear} candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, f"{linear} candidate policy changed")
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), f"{linear} implementation guard missing")
    require(implementation.get("rendererBehaviorChanged") is False, f"{linear} changed renderer behavior")


def validate_for345_guard(for345: dict[str, Any]) -> None:
    require(for345.get("linear") == "FOR-345", "FOR-345 identity changed")
    require(for345.get("sceneId") == FOR345_SCENE_ID, "FOR-345 scene id changed")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    require(row.get("referenceCurrentCandidateComparable") is True, "FOR-345 comparable guard changed")
    require(row.get("sceneId") != SCENE_ID, "FOR-358 must not reuse FOR-345 scene id")


def validate_source_guardrails() -> dict[str, Any]:
    required = {
        SOURCE: [
            "SkColorSpace::MakeRGB(SkNamedTransferFn::kRec2020, SkNamedGamut::kRec2020)",
            "kRGBA_F16Norm_SkColorType",
            "canvas->clear(SK_ColorWHITE)",
            "green.setBlendMode(SkBlendMode::kSrcOver)",
            "SkColorSetARGB(160, 0, 192, 64)",
            "canvas->drawRect(SkRect::MakeXYWH(5, 6, 18, 15), green)",
            "\"sceneId\\\": \\\"f16-real-additional-non-arc-row-for358\\\"",
            "\"nonArc\\\": true",
            "\"excludedScene\\\": \\\"circular_arcs_stroke_butt\\\"",
        ],
        RUNNER: [
            "non_arc_rec2020_f16_for358_reference.cpp",
            "libskia.a",
            "F16_REAL_ADDITIONAL_NON_ARC_ROW_SKIA_REFERENCE_CAPTURED",
            "selectedCellSubstitutionAccepted",
        ],
        CURRENT_CAPTURE_TEST: [
            "kanvas.for358.currentCapture.output",
            "For358RealAdditionalNonArcF16CurrentCaptureTest",
            "SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)",
            "SkBitmap(40, 28, rec2020, SkColorType.kRGBA_F16Norm)",
            "bitmap.eraseColor(SK_ColorWHITE)",
            "SkCanvas(bitmap).drawRect(SkRect.MakeXYWH(5f, 6f, 18f, 15f), paint)",
            "bitmap.getPixelAsSrgb(sample.x, sample.y)",
        ],
    }
    for path, snippets in required.items():
        require(path.is_file(), f"missing source file: {rel(path)}")
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            require(snippet in text, f"{rel(path)} missing required snippet: {snippet}")
    source_text = SOURCE.read_text(encoding="utf-8")
    for forbidden in ["drawArc", "CircularArcsStrokeButt", "fullGmCrop\": true"]:
        require(forbidden not in source_text, f"source uses forbidden term `{forbidden}`")
    return {
        "source": {"path": rel(SOURCE), "sha256": sha256(SOURCE), "present": True},
        "runner": {"path": rel(RUNNER), "present": True, "headless": True},
        "currentCaptureTest": {"path": rel(CURRENT_CAPTURE_TEST), "present": True, "headless": True},
    }


def keyed_samples(data: dict[str, Any], key: str) -> dict[str, dict[str, Any]]:
    raw_samples = data.get("samples")
    require(isinstance(raw_samples, list), f"{key} samples missing")
    result: dict[str, dict[str, Any]] = {}
    for sample in raw_samples:
        require(isinstance(sample, dict), f"{key} sample must be an object")
        name = sample.get("name")
        require(isinstance(name, str) and name, f"{key} sample name missing")
        require(name not in result, f"duplicate sample {name}")
        rgba(sample.get(key), f"{name} {key}")
        result[name] = sample
    return result


def validate_reference(reference: dict[str, Any], provenance: dict[str, Any]) -> None:
    require(reference.get("sceneId") == SCENE_ID, "Skia reference scene id changed")
    require(
        reference.get("sourceType") == "isolated-skia-non-arc-rec2020-f16-for358-src-over-rect",
        "Skia reference source type changed",
    )
    require(reference.get("dimensions") == {"width": 40, "height": 28}, "Skia reference dimensions changed")
    require(reference.get("colorType") == "kRGBA_F16Norm", "Skia reference color type changed")
    require(reference.get("colorSpace") == "Rec.2020", "Skia reference color space changed")
    require(reference.get("blendMode") == "kSrcOver", "Skia reference blend mode changed")
    require(reference.get("nonArc") is True, "Skia reference must be non-arc")
    require(reference.get("excludedScene") == "circular_arcs_stroke_butt", "Skia reference exclusion changed")
    require(provenance.get("linear") == LINEAR_ID, "Skia provenance linear id changed")
    require(provenance.get("sceneId") == SCENE_ID, "Skia provenance scene id changed")
    require(provenance.get("referenceJsonSha256") == sha256(SKIA_REFERENCE_JSON), "Skia reference JSON sha changed")
    require(provenance.get("referencePngSha256") == sha256(SKIA_REFERENCE_PNG), "Skia reference PNG sha changed")
    proof = provenance.get("sourceTypeProof")
    require(isinstance(proof, dict), "Skia provenance proof missing")
    require(proof.get("compiledRepoOwnedSource") is True, "Skia source compile proof missing")
    require(proof.get("executedBinary") is True, "Skia execution proof missing")
    require(proof.get("isolatedNonArcOutput") is True, "Skia isolated non-arc proof missing")
    require(proof.get("distinctFromFor345") is True, "FOR-358 distinctness proof missing")
    require(provenance.get("fullGmCrop") is False, "Skia provenance uses full GM crop")
    require(provenance.get("selectedCellSubstitutionAccepted") is False, "Skia provenance accepts selected-cell substitution")


def validate_current_capture(current: dict[str, Any]) -> None:
    require(current.get("linear") == LINEAR_ID, "current capture linear id changed")
    require(current.get("sceneId") == SCENE_ID, "current capture scene id changed")
    require(
        current.get("sourceType") == "current-kanvas-non-arc-rec2020-f16-for358-src-over-rect",
        "current source type changed",
    )
    require(current.get("dimensions") == {"width": 40, "height": 28}, "current dimensions changed")
    require(current.get("colorType") == "kRGBA_F16Norm", "current color type changed")
    require(current.get("colorSpace") == "Rec.2020", "current color space changed")
    require(current.get("blendMode") == "kSrcOver", "current blend mode changed")
    require(current.get("nonArc") is True, "current capture must be non-arc")
    require(current.get("excludedScene") == "circular_arcs_stroke_butt", "current exclusion changed")


def for355_rule_applies(metadata: dict[str, Any]) -> bool:
    return metadata["zone"] == "stroke-center" and 0 < int(metadata["rawReferenceAlpha"]) < 255


def candidate_rgba(metadata: dict[str, Any], current_rgba: list[int]) -> list[int]:
    if for355_rule_applies(metadata):
        fail("FOR-358 fixture unexpectedly entered the FOR-355 stroke-center rule")
    return current_rgba.copy()


def build_samples(reference: dict[str, Any], current_capture: dict[str, Any]) -> list[dict[str, Any]]:
    reference_samples = keyed_samples(reference, "referenceSrgbRgba")
    current_samples = keyed_samples(current_capture, "currentKanvasSrgbRgba")
    samples: list[dict[str, Any]] = []
    for metadata in SAMPLE_METADATA:
        name = metadata["name"]
        require(name in reference_samples, f"Skia reference missing sample {name}")
        require(name in current_samples, f"Kanvas current capture missing sample {name}")
        ref_sample = reference_samples[name]
        cur_sample = current_samples[name]
        require(ref_sample.get("x") == metadata["x"] and ref_sample.get("y") == metadata["y"], f"Skia coords changed: {name}")
        require(cur_sample.get("x") == metadata["x"] and cur_sample.get("y") == metadata["y"], f"Kanvas coords changed: {name}")
        reference_rgba = rgba(ref_sample["referenceSrgbRgba"], f"{name} reference")
        current_rgba = rgba(cur_sample["currentKanvasSrgbRgba"], f"{name} current")
        candidate = candidate_rgba(metadata, current_rgba)
        current_abs = abs_delta(current_rgba, reference_rgba)
        candidate_abs = abs_delta(candidate, reference_rgba)
        samples.append(
            {
                "name": name,
                "zone": metadata["zone"],
                "x": metadata["x"],
                "y": metadata["y"],
                "covered": metadata["covered"],
                "rawReferenceAlpha": metadata["rawReferenceAlpha"],
                "paintSourceRgba": [0, 192, 64, 160] if metadata["covered"] else None,
                "referenceSrgbRgba": reference_rgba,
                "currentKanvasSrgbRgba": current_rgba,
                "candidatePolicyId": CANDIDATE_POLICY_ID,
                "candidateRgba": candidate,
                "candidatePolicyRgba": candidate,
                "for355RuleApplies": for355_rule_applies(metadata),
                "currentVsReferenceSignedDelta": signed_delta(current_rgba, reference_rgba),
                "currentVsReferenceAbsDelta": current_abs,
                "currentResidual": sum(current_abs),
                "candidateVsReferenceSignedDelta": signed_delta(candidate, reference_rgba),
                "candidateVsReferenceAbsDelta": candidate_abs,
                "candidateResidual": sum(candidate_abs),
                "worsened": sum(candidate_abs) > sum(current_abs),
                "candidateWorsensCurrent": sum(candidate_abs) > sum(current_abs),
            }
        )
    return samples


def summarize(samples: list[dict[str, Any]]) -> dict[str, Any]:
    current = sum(sample["currentResidual"] for sample in samples)
    candidate = sum(sample["candidateResidual"] for sample in samples)
    worsened = sum(1 for sample in samples if sample["worsened"])
    covered = sum(1 for sample in samples if sample["covered"])
    return {
        "sampleCount": len(samples),
        "coveredSampleCount": covered,
        "currentResidual": current,
        "candidateResidual": candidate,
        "residualDelta": candidate - current,
        "candidateMinusCurrentResidual": candidate - current,
        "worsenedSampleCount": worsened,
        "for355RuleAppliedSampleCount": sum(1 for sample in samples if sample["for355RuleApplies"]),
    }


def build_artifact(
    for345: dict[str, Any],
    for357: dict[str, Any],
    for356: dict[str, Any],
    for355: dict[str, Any],
    reference: dict[str, Any],
    provenance: dict[str, Any],
    current_capture: dict[str, Any],
) -> dict[str, Any]:
    source_runner = validate_source_guardrails()
    validate_for345_guard(for345)
    validate_reference(reference, provenance)
    validate_current_capture(current_capture)
    samples = build_samples(reference, current_capture)
    residuals = summarize(samples)
    decision = DECISION_REJECTS if residuals["worsenedSampleCount"] > 0 else DECISION_ACCEPTS
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "decision": decision,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-358 captures a real additional non-arc Rec.2020 kRGBA_F16Norm SrcOver row "
            "distinct from FOR-345. The FOR-355 candidate is reused exactly and remains an "
            "identity-preserving evidence probe on this non-stroke-center non-arc row."
        ),
        "inputValidation": {
            "for345Artifact": rel(FOR345_ARTIFACT),
            "for345SceneId": for345.get("sceneId"),
            "for357Artifact": rel(FOR357_ARTIFACT),
            "for357Decision": for357.get("decision"),
            "for357RequiredDecision": FOR357_REQUIRED_DECISION,
            "for356Artifact": rel(FOR356_ARTIFACT),
            "for356Decision": for356.get("decision"),
            "for356RequiredDecision": FOR356_REQUIRED_DECISION,
            "for355Artifact": rel(FOR355_ARTIFACT),
            "for355Decision": for355.get("decision"),
            "for355RequiredDecision": FOR355_REQUIRED_DECISION,
        },
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "reusedFromFor355": True,
            "newCandidateDefined": False,
            "selectedForImplementation": False,
            "rendererBehaviorChanged": False,
            "scoreIncreaseAuthorized": False,
            "formula": for355.get("candidate", {}).get("formula"),
        },
        "row": {
            "rowId": "for358-non-arc-rec2020-f16-src-over-green-rect",
            "family": "non-arc-solid-rect",
            "sceneId": SCENE_ID,
            "sourceKind": "isolated-skia-current-kanvas-candidate",
            "nonArc": True,
            "distinctFromFor345": True,
            "excludedScene": "circular_arcs_stroke_butt",
            "referenceCurrentCandidateComparable": True,
            "reference": {
                "status": "isolated-skia-non-arc-rec2020-f16-src-over-reference-available",
                "captureCommand": REFERENCE_CAPTURE_COMMAND,
                "samplesPath": rel(SKIA_REFERENCE_JSON),
                "provenancePath": rel(SKIA_REFERENCE_PROVENANCE),
                "pngPath": rel(SKIA_REFERENCE_PNG),
                "referenceJsonSha256": sha256(SKIA_REFERENCE_JSON),
                "referencePngSha256": sha256(SKIA_REFERENCE_PNG),
            },
            "current": {
                "status": "current-kanvas-kotlin-cpu-rec2020-f16-src-over-samples-captured",
                "captureCommand": CURRENT_CAPTURE_COMMAND,
                "samplesPath": rel(CURRENT_KANVAS_JSON),
                "captureMethod": current_capture.get("captureMethod"),
            },
            "candidateEvaluation": {
                "policyId": CANDIDATE_POLICY_ID,
                "status": "computed-policy-samples-available-not-applied",
                "identityGuardApplied": True,
                "for355RuleAppliedSampleCount": residuals["for355RuleAppliedSampleCount"],
            },
            "samples": samples,
            "residuals": residuals,
        },
        "scene": {
            "dimensions": {"width": 40, "height": 28},
            "colorType": "kRGBA_F16Norm",
            "colorSpace": "Rec.2020",
            "backgroundRgba": [255, 255, 255, 255],
            "draw": {
                "op": "drawRect",
                "rectXYWH": [5, 6, 18, 15],
                "antiAlias": False,
                "blendMode": "kSrcOver",
                "paintArgb": [160, 0, 192, 64],
            },
            "nonArc": True,
            "fullGmCrop": False,
            "selectedCellSubstitutionUsed": False,
            "fixtureOrCoordinateRendererBranchUsed": False,
        },
        "referenceSource": source_runner,
        "boundary": {
            "rendererBehaviorChanged": False,
            "globalF16RendererChangeAllowedNow": False,
            "candidatePolicyId": CANDIDATE_POLICY_ID,
        },
        "implementation": {
            "rendererBehaviorChanged": False,
            "evidenceOnly": True,
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
        },
        "nonGoalsPreserved": {
            "rendererBehaviorChange": True,
            "candidateSelectionOrImplementation": True,
            "scoreIncrease": True,
            "selectedCellSubstitutionRefused": True,
            "fixtureBranchRefused": True,
            "coordinateBranchRefused": True,
            "fullGmCropRefused": True,
            "thresholdRelaxationRefused": True,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "artifact source memory changed")
    require(data.get("sourceFindings") == SOURCE_FINDINGS, "artifact source findings changed")
    require(data.get("decision") in (DECISION_ACCEPTS, DECISION_REJECTS), "unexpected FOR-358 decision")
    row = data.get("row")
    require(isinstance(row, dict), "row missing")
    require(row.get("sceneId") != FOR345_SCENE_ID, "FOR-358 reused FOR-345 scene id")
    require(row.get("distinctFromFor345") is True, "distinctness guard missing")
    require(row.get("nonArc") is True, "row must be non-arc")
    require(row.get("referenceCurrentCandidateComparable") is True, "row must be comparable")
    samples = row.get("samples")
    require(isinstance(samples, list) and len(samples) >= 2, "expected at least two samples")
    require(any(sample.get("covered") is True for sample in samples), "expected at least one covered sample")
    for sample in samples:
        for key in ("referenceSrgbRgba", "currentKanvasSrgbRgba", "candidateRgba", "candidatePolicyRgba"):
            rgba(sample.get(key), f"{sample.get('name')} {key}")
        require(sample.get("candidatePolicyId") == CANDIDATE_POLICY_ID, f"{sample.get('name')} candidate policy changed")
        require(isinstance(sample.get("currentResidual"), int), f"{sample.get('name')} current residual missing")
        require(isinstance(sample.get("candidateResidual"), int), f"{sample.get('name')} candidate residual missing")
        require(isinstance(sample.get("worsened"), bool), f"{sample.get('name')} worsened flag missing")
        require(sample.get("for355RuleApplies") is False, "FOR-358 non-arc row should not enter stroke-center rule")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "residuals missing")
    require(residuals.get("sampleCount") == 4, "sample count changed")
    require(residuals.get("coveredSampleCount") == 3, "covered sample count changed")
    require(residuals.get("candidateResidual") == residuals.get("currentResidual"), "candidate should preserve current row")
    require(residuals.get("residualDelta") == 0, "candidate residual delta changed")
    require(residuals.get("worsenedSampleCount") == 0, "candidate worsened FOR-358 row")
    require(data.get("boundary", {}).get("rendererBehaviorChanged") is False, "renderer behavior changed")
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation missing")
    for key in (
        "colorToF16PremulChanged",
        "blendF16PremulModeChanged",
        "skBitmapGetPixelChanged",
        "skBitmapGetPixelAsSrgbChanged",
        "gpuOrWgslChanged",
        "geometryChanged",
        "coverageChanged",
        "fallbackChanged",
        "thresholdsChanged",
        "promotionChanged",
        "scoreChanged",
        "kadreChanged",
    ):
        require(implementation.get(key) is False, f"implementation guard changed: {key}")


def build_report(data: dict[str, Any]) -> str:
    row = data["row"]
    sample_rows = "\n".join(
        "| {name} | {xy} | {zone} | {ref} | {current} | {candidate} | {rule} | {current_residual} | {candidate_residual} | {worsened} |".format(
            name=sample["name"],
            xy=f"{sample['x']},{sample['y']}",
            zone=sample["zone"],
            ref=json.dumps(sample["referenceSrgbRgba"]),
            current=json.dumps(sample["currentKanvasSrgbRgba"]),
            candidate=json.dumps(sample["candidateRgba"]),
            rule="yes" if sample["for355RuleApplies"] else "no",
            current_residual=sample["currentResidual"],
            candidate_residual=sample["candidateResidual"],
            worsened="yes" if sample["worsened"] else "no",
        )
        for sample in row["samples"]
    )
    residuals = row["residuals"]
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-358 F16 Real Additional Non-Arc Row

Linear: `FOR-358`

Decision: `{data["decision"]}`

Candidate: `{CANDIDATE_POLICY_ID}`

FOR-358 captures a real additional non-arc Rec.2020 `kRGBA_F16Norm`
`SrcOver` row that is distinct from FOR-345. It reuses the FOR-355 candidate
exactly and keeps the work evidence-only.

## Result

The row is a green non-arc solid rect with different dimensions, geometry,
paint color, sample names, and source files from FOR-345. The FOR-355
`stroke-center` rule does not apply to these non-arc fill/background samples,
so the candidate preserves the current Kanvas RGBA values for this guard row.

## Samples

| sample | x,y | zone | reference | current | candidate | FOR-355 rule applies | current residual | candidate residual | worsened |
|---|---|---|---|---|---|---:|---:|---:|---|
{sample_rows}

## Aggregate Residuals

| metric | value |
|---|---:|
| samples | {residuals["sampleCount"]} |
| covered samples | {residuals["coveredSampleCount"]} |
| current residual | {residuals["currentResidual"]} |
| candidate residual | {residuals["candidateResidual"]} |
| residual delta | {residuals["residualDelta"]} |
| worsened samples | {residuals["worsenedSampleCount"]} |
| FOR-355 rule-applied samples | {residuals["for355RuleAppliedSampleCount"]} |

## Prerequisites

- FOR-357 decision: `{data["inputValidation"]["for357Decision"]}`
- FOR-356 decision: `{data["inputValidation"]["for356Decision"]}`
- FOR-355 decision: `{data["inputValidation"]["for355Decision"]}`
- Source memory: `{SOURCE_MEMORY}`

## Non-goals Preserved

- No renderer behavior change.
- No candidate selection or implementation.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for358_f16_real_additional_non_arc_row.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-358-f16-real-additional-non-arc-row.md`
- Skia reference source: `tools/skia-reference/non_arc_rec2020_f16_for358_reference.cpp`
- Skia reference builder: `tools/skia-reference/build_for358_f16_real_additional_non_arc_row.py`
- Current capture test: `skia-integration-tests/src/test/kotlin/org/skia/tests/For358RealAdditionalNonArcF16CurrentCaptureTest.kt`

## Validation

{validation}
"""


def main() -> None:
    for345 = load_json(FOR345_ARTIFACT)
    for357 = load_json(FOR357_ARTIFACT)
    for356 = load_json(FOR356_ARTIFACT)
    for355 = load_json(FOR355_ARTIFACT)
    validate_prerequisite(for357, "FOR-357", FOR357_REQUIRED_DECISION)
    validate_prerequisite(for356, "FOR-356", FOR356_REQUIRED_DECISION)
    validate_prerequisite(for355, "FOR-355", FOR355_REQUIRED_DECISION)
    reference = load_json(SKIA_REFERENCE_JSON)
    provenance = load_json(SKIA_REFERENCE_PROVENANCE)
    current_capture = load_json(CURRENT_KANVAS_JSON)
    data = build_artifact(for345, for357, for356, for355, reference, provenance, current_capture)
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-358 validation passed")


if __name__ == "__main__":
    main()
