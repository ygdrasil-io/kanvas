#!/usr/bin/env python3
"""Validate FOR-449 M60 F16 stencil-write/subpass trace evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-stencil-write-subpass-trace-for449"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-449-m60-f16-stencil-write-subpass-trace.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16StencilWriteSubpassTraceFor449.enabled"
FOR448_FLAG = "kanvas.webgpu.m60F16ZeroMaskNeutralPathTraceFor448.enabled"
EXPECTED_POINTS = [(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)]
ALLOWED_CLASSIFICATIONS = {
    "stencil-write-excludes-zero-mask-targets",
    "fragment-subpass-not-invoked-for-zero-mask-targets",
    "fragment-invoked-but-fixed-blend-neutral",
    "later-write-overwrites-subpass-effect",
    "direct-stencil-subpass-trace-inconclusive",
}
ARTIFACT_FILES = {
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/reference-cpu.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/current-webgpu.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/current-webgpu-diff.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/inside-webgpu-for447.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/outside-webgpu-for448.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/both-webgpu-for448.png",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for448_m60_f16_zero_mask_neutral_path_trace.py",
    "scripts/validate_for449_m60_f16_stencil_write_subpass_trace.py",
    "scripts/validate_for450_m60_f16_stencil_boundary_audit.py",
    "reports/wgsl-pipeline/2026-06-06-for-450-m60-f16-stencil-boundary-audit.md",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-boundary-audit-for450",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-boundary-audit-for450/m60-f16-stencil-boundary-audit-for450.json",
    "reports/wgsl-pipeline/2026-06-06-for-449-m60-f16-stencil-write-subpass-trace.md",
    *ARTIFACT_FILES,
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-449 validation failed: {message}")


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
    status_result = subprocess.run(
        ["git", "status", "--short"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    for line in status_result.stdout.splitlines():
        path = line[3:].strip()
        if path:
            changed.add(path.rstrip("/"))
    return changed


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    shader = PRODUCTION_SHADER.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16StencilWriteSubpassTraceFor449(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build,
        "for448Dependency": (
            "stencilWriteSubpassTraceFor449Enabled" in capture
            and "zeroMaskNeutralPathTraceFor448Enabled" in capture
            and FOR448_FLAG in capture
        ),
        "existingDiagnosticsUsed": all(
            token in capture
            for token in (
                "aaStencilCoverPredrawDstReadbackSnapshot",
                "aaStencilCoverContributionIsolationSnapshot",
                "aaStencilCoverShaderReturnDiagnosticSnapshot",
                "aaStencilCoverContributionIsolationPostPassSnapshot",
            )
        ),
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "memorySources": (
            "brouillon-ticket-m60-f16-tracer-directement-stencil-write-et-ecritures-fragmentaires-apres-for-448"
            in capture
            and "for-448-zero-mask-neutral-path-trace-reste-inconclusive" in capture
        ),
        "for442Excluded": (
            '"for442DecisionSourceUsed": false' in capture
            and '"for442UsedAsDecisionSource": false' in capture
        ),
        "stencilRefusal": (
            "stencil-buffer-direct-readback-unavailable" in capture
            and "safe WebGPU stencil-buffer readback boundary" in capture
        ),
        "deviceUnchangedByFor449": FLAG not in device and "FOR449" not in device and "For449" not in device,
        "productionShaderDoesNotContainFor449": "for449" not in shader.lower() and "stencil_write_subpass_trace" not in shader,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-449: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES))
    require(not forbidden, f"forbidden spec/external/production-shader diffs: {forbidden}")

    diff_text = subprocess.run(
        [
            "git",
            "diff",
            "--unified=0",
            "origin/master",
            "--",
            rel(CAPTURE_TEST),
            rel(BUILD),
        ],
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
        )
    ]
    require(not dangerous_lines, f"threshold/scoring/fallback/PipelineKey lines changed: {dangerous_lines}")


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-449 flag")
    require("FOR-448" in text and "FOR-447" in text, "report must cite source tickets")
    require("FOR-442" in text and "exclu" in text.lower(), "report must state FOR-442 is excluded")
    require("refuse" in text.lower() or "refus" in text.lower(), "report must refuse to name a correction")
    require(
        "global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-tracer-directement-stencil-write-et-ecritures-fragmentaires-apres-for-448"
        in text,
        "report must cite draft memory",
    )


def require_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-449", "linear must be FOR-449")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("optInFlag") == FLAG, "opt-in flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")

    for field in (
        "supportClaim",
        "promoted",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
        "productionWgslChanged",
        "wgsl4kModified",
        "renderingFixAppliedByDefault",
        "for442UsedAsDecisionSource",
    ):
        require(data.get(field) is False, f"{field} must be false")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary must be object")
    require(summary.get("zeroMaskPixelCount") == 6, "zeroMaskPixelCount must be 6")
    require(summary.get("for442DecisionSourceUsedCount") == 0, "FOR-442 must not be used")
    require(summary.get("stencilWriteDirectReadbackAvailable") is False, "direct stencil readback must be false")
    for mode in ("inside", "outside", "both"):
        require(summary.get(f"{mode}ChangedPixels") == 0, f"{mode} render must remain neutral")
        require(summary.get(f"{mode}ChangedTargetPixels") == 0, f"{mode} target pixels must be unchanged")
        require(summary.get(f"{mode}TotalResidual") == summary.get("currentTotalResidual"), f"{mode} residual must match")
    require(summary.get("predrawDstObservedTargetCount") == 6, "predraw destination must be observed for six targets")
    require(summary.get("postPassObservedTargetCount") == 6, "post-pass destination must be observed for six targets")

    stencil = data.get("stencilWriteTrace")
    require(isinstance(stencil, dict), "stencilWriteTrace must be object")
    require(stencil.get("available") is False, "stencil write trace must be unavailable")
    require(stencil.get("readAttempted") is False, "stencil read must not be attempted")
    require(
        stencil.get("fallbackClassification") == "direct-stencil-subpass-trace-inconclusive",
        "stencil refusal must force inconclusive fallback",
    )

    completeness = data.get("traceCompleteness")
    require(isinstance(completeness, dict), "traceCompleteness must be object")
    require(completeness.get("stencilWriteTraceComplete") is False, "stencil trace must be incomplete")
    require(completeness.get("destinationBeforeAfterTraceComplete") is True, "destination trace must be complete")
    require(completeness.get("finalImageTraceComplete") is True, "final image trace must be complete")
    require("refuse" in completeness.get("incompleteTracePolicy", ""), "incomplete policy must refuse correction")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six pixels")
    seen = {(pixel.get("x"), pixel.get("y")) for pixel in pixels}
    require(seen == set(EXPECTED_POINTS), f"partialPixels mismatch: {seen}")
    for pixel in pixels:
        require(pixel.get("classification") == "direct-stencil-subpass-trace-inconclusive", "pixel classification mismatch")
        pixel_stencil = pixel.get("stencilWriteTrace")
        require(isinstance(pixel_stencil, dict), "pixel stencilWriteTrace must be object")
        require(pixel_stencil.get("available") is False, "pixel stencil trace must be unavailable")
        shader_trace = pixel.get("shaderReturnSubpassTrace")
        contribution_trace = pixel.get("contributionSubpassTrace")
        require(isinstance(shader_trace, list) and len(shader_trace) == 2, "shaderReturnSubpassTrace must have inside/outside")
        require(isinstance(contribution_trace, list) and len(contribution_trace) == 2, "contributionSubpassTrace must have inside/outside")
        roles = {entry.get("role") for entry in shader_trace}
        require(roles == {"inside", "outside"}, f"shader roles mismatch: {roles}")
        require(pixel.get("destinationBeforeTrace", {}).get("observed") is True, "destination before must be observed")
        require(pixel.get("destinationAfterTrace", {}).get("observed") is True, "destination after must be observed")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved must be object")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must be false")
    require("Refuse" in data.get("nextCorrectionCandidate", ""), "nextCorrectionCandidate must refuse")
    return data


def require_files() -> None:
    for rel_path in ARTIFACT_FILES:
        path = ROOT / rel_path
        require(path.exists(), f"missing artifact file: {rel_path}")


def main() -> None:
    source_audit()
    require_files()
    data = require_artifact()
    require_report(data)
    print(
        "FOR-449 validation passed: "
        f"classification={data['classification']} "
        f"shaderObserved={data['summary']['shaderReturnObservedTargetCount']} "
        f"stencilReadback={data['summary']['stencilWriteDirectReadbackAvailable']}"
    )


if __name__ == "__main__":
    main()
