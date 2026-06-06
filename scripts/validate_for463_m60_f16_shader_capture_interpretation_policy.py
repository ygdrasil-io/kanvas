#!/usr/bin/env python3
"""Validate FOR-463 shader-capture interpretation policy evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-shader-capture-interpretation-policy-for463"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-463-m60-f16-shader-capture-interpretation-policy.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"
FOR455_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-zero-stencil-cover-emission-audit-for455"
    / "m60-f16-zero-stencil-cover-emission-audit-for455.json"
)
FOR458_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-production-cover-state-vs-shader-emission-for458"
    / "m60-f16-production-cover-state-vs-shader-emission-for458.json"
)
FOR459_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-production-cover-color-attachment-acceptance-for459"
    / "m60-f16-production-cover-color-attachment-acceptance-for459.json"
)

FLAG = "kanvas.webgpu.m60F16ShaderCaptureInterpretationPolicyFor463.enabled"
EXPECTED_CLASSIFICATION = "shader-capture-before-reject-confirmed-by-color-attachment"
ALLOWED_CLASSIFICATIONS = {
    EXPECTED_CLASSIFICATION,
    "shader-capture-color-attachment-contradiction",
    "shader-capture-interpretation-policy-inconclusive",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for459_m60_f16_production_cover_color_attachment_acceptance.py",
    "scripts/validate_for463_m60_f16_shader_capture_interpretation_policy.py",
    "reports/wgsl-pipeline/2026-06-06-for-463-m60-f16-shader-capture-interpretation-policy.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
}
FORBIDDEN_DIFF_PREFIXES = (
    ".upstream/",
    "external/",
    "buildSrc/",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/",
    "gpu-raster/src/main/resources/shaders/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-463 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def git_changed_paths() -> set[str]:
    diff_result = subprocess.run(
        ["git", "diff", "--name-only", "origin/master"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    return changed


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    shader = PRODUCTION_SHADER.read_text(encoding="utf-8")
    checks = {
        "flagRelayed": FLAG in build and FLAG in capture,
        "writerCalled": "writeM60F16ShaderCaptureInterpretationPolicyFor463(" in capture,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "sourceArtifactsRead": all(
            token in capture
            for token in (
                "m60-f16-zero-stencil-cover-emission-audit-for455",
                "m60-f16-production-cover-state-vs-shader-emission-for458",
                "m60-f16-production-cover-color-attachment-acceptance-for459",
            )
        ),
        "for442Excluded": '"for442UsedAsDecisionSource": false' in capture,
        "for447NotPromoted": '"for447Promoted": false' in capture,
        "shaderEmissionPolicy": '"shaderEmissionAloneMeansColorAcceptance": false' in capture,
        "productionShaderDoesNotContainFor463": "for463" not in shader.lower()
        and "shader_capture_interpretation" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-463: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES))
    require(not forbidden, f"forbidden production/spec diffs: {forbidden}")

    diff_text = subprocess.run(
        ["git", "diff", "--unified=0", "origin/master", "--", rel(CAPTURE_TEST), rel(BUILD)],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    ).stdout
    dangerous_lines = [
        line
        for line in diff_text.splitlines()
        if (line.startswith("+") or line.startswith("-"))
        and not line.startswith(("+++", "---"))
        and (
            "GPU_SUPPORT_THRESHOLD" in line
            or "similarity <" in line
            or "similarity >" in line
            or "coverage.stroke-cap-join-visual-parity-below-threshold" in line
            or "PipelineKey" in line
            or ("fallbackPolicy" in line and '"fallbackPolicyChanged": false' not in line)
            or "m60F16ZeroMaskCorrectionFor447(true)" in line
        )
    ]
    require(not dangerous_lines, f"threshold/scoring/fallback/PipelineKey/FOR-447 lines changed: {dangerous_lines}")


def require_source_artifacts() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    for455 = load_json(FOR455_ARTIFACT)
    for458 = load_json(FOR458_ARTIFACT)
    for459 = load_json(FOR459_ARTIFACT)
    require(for455.get("linear") == "FOR-455", "FOR-455 source mismatch")
    require(for458.get("linear") == "FOR-458", "FOR-458 source mismatch")
    require(for459.get("linear") == "FOR-459", "FOR-459 source mismatch")
    require(
        for458.get("classification") == "production-cover-shader-capture-before-fixed-function-stencil-reject",
        "FOR-458 source classification mismatch",
    )
    require(
        for459.get("classification") == "production-cover-color-attachment-rejects-zero-stencil-targets",
        "FOR-459 source classification mismatch",
    )
    return for455, for458, for459


def require_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-463", "linear must be FOR-463")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected FOR-463 classification")

    expected_top_level = {
        "supportClaim": False,
        "defaultRenderingChanged": False,
        "thresholdChanged": False,
        "scoringChanged": False,
        "fallbackPolicyChanged": False,
        "pipelineKeyChanged": False,
        "productionWgslChanged": False,
        "wgsl4kModified": False,
        "for447Promoted": False,
        "insideShaderEmissionOnDiagnosticZeroStencilCount": 6,
        "colorAttachmentChangedTargetCount": 0,
        "colorAttachmentUnchangedTargetCount": 6,
        "for442DecisionSourceUsedCount": 0,
    }
    for key, expected in expected_top_level.items():
        require(data.get(key) == expected, f"{key} mismatch")

    sources = data.get("sourceArtifacts")
    require(isinstance(sources, dict), "sourceArtifacts must be object")
    require("for455" in sources and "for458" in sources and "for459" in sources, "sourceArtifacts incomplete")

    source_classifications = data.get("sourceClassifications")
    require(isinstance(source_classifications, dict), "sourceClassifications must be object")
    require(
        source_classifications.get("for458")
        == "production-cover-shader-capture-before-fixed-function-stencil-reject",
        "FOR-458 classification not carried into FOR-463",
    )
    require(
        source_classifications.get("for459") == "production-cover-color-attachment-rejects-zero-stencil-targets",
        "FOR-459 classification not carried into FOR-463",
    )

    summary = data.get("evidenceSummary")
    require(isinstance(summary, dict), "evidenceSummary must be object")
    expected_summary = {
        "for455InsideShaderEmissionOnZeroStencilCount": 6,
        "insideShaderEmissionOnDiagnosticZeroStencilCount": 6,
        "colorAttachmentChangedTargetCount": 0,
        "colorAttachmentUnchangedTargetCount": 6,
        "for442DecisionSourceUsedCount": 0,
        "supportClaim": False,
        "defaultRenderingChanged": False,
        "thresholdChanged": False,
        "scoringChanged": False,
        "fallbackPolicyChanged": False,
        "pipelineKeyChanged": False,
        "productionWgslChanged": False,
        "wgsl4kModified": False,
        "for447Promoted": False,
    }
    for key, expected in expected_summary.items():
        require(summary.get(key) == expected, f"evidenceSummary.{key} mismatch")

    policy = data.get("policyFlags")
    require(isinstance(policy, dict), "policyFlags must be object")
    require(policy.get("shaderEmissionAloneMeansColorAcceptance") is False, "shader emission alone must not accept")
    require(
        policy.get("requiresColorAttachmentEvidenceForAcceptanceClaim") is True,
        "color evidence requirement must be true",
    )
    for field in (
        "for442DecisionSourceUsed",
        "for447Promoted",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
        "productionWgslChanged",
        "wgsl4kModified",
    ):
        require(policy.get(field) is False, f"policyFlags.{field} must be false")

    reason = data.get("classificationReason", "")
    require("not color acceptance" in reason, "classificationReason must reject color acceptance")
    require("FOR-442" not in reason, "classificationReason must not depend on FOR-442")
    require("FOR-447" not in reason, "classificationReason must not promote FOR-447")
    next_action = data.get("nextAction", "")
    require("color-attachment evidence" in next_action, "nextAction must require color evidence")
    require("shader emission" in next_action, "nextAction must mention shader emission")
    require("correction" in next_action, "nextAction must constrain future correction tickets")
    return data


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report file: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    lower = text.lower()
    require(data["classification"] in text, "report must include classification")
    require(FLAG in text, "report must include FOR-463 flag")
    require("for-455" in lower and "for-458" in lower and "for-459" in lower, "report must cite sources")
    require("insideshaderemissionondiagnosticzerostencilcount=6" in lower, "report must include shader count")
    require("colorattachmentchangedtargetcount=0" in lower, "report must include changed count")
    require("colorattachmentunchangedtargetcount=6" in lower, "report must include unchanged count")
    require(
        ("emission shader" in lower or "émission shader" in lower) and "acceptation couleur" in lower,
        "report must explain policy",
    )
    require("ne corrige pas" in lower, "report must reject fix claim")
    require("for-442 reste exclu" in lower, "report must exclude FOR-442")
    require("for-447 n'est pas promu" in lower, "report must not promote FOR-447")
    require("suite unique" in lower, "report must include one next-action section")


def main() -> None:
    source_audit()
    require_source_artifacts()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-463 validation passed: "
        f"classification={data['classification']} "
        f"insideEmission={data['insideShaderEmissionOnDiagnosticZeroStencilCount']} "
        f"colorChanged={data['colorAttachmentChangedTargetCount']}"
    )


if __name__ == "__main__":
    main()
