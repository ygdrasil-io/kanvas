#!/usr/bin/env python3
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence"
MANIFEST_ARTIFACT = "diagnostic-webgpu-first-route-pm-evidence-00-manifest.txt"

EXPECTED_REPORT_NAME = "diagnostic-webgpu-first-route-pm-evidence"
EXPECTED_ARTIFACT_NAMES = [
    "diagnostic-webgpu-first-route-pm-evidence-01-command.txt",
    "diagnostic-webgpu-first-route-pm-evidence-02-analysis.txt",
    "diagnostic-webgpu-first-route-pm-evidence-03-route.txt",
    "diagnostic-webgpu-first-route-pm-evidence-04-material.txt",
    "diagnostic-webgpu-first-route-pm-evidence-05-wgsl.txt",
    "diagnostic-webgpu-first-route-pm-evidence-06-payload.txt",
    "diagnostic-webgpu-first-route-pm-evidence-07-pipeline-key.txt",
    "diagnostic-webgpu-first-route-pm-evidence-08-resource-decision.txt",
    "diagnostic-webgpu-first-route-pm-evidence-09-submission.txt",
    "diagnostic-webgpu-first-route-pm-evidence-10-readback.txt",
    "diagnostic-webgpu-first-route-pm-evidence-11-telemetry.txt",
    "diagnostic-webgpu-first-route-pm-evidence-12-pipeline-cache.txt",
    "diagnostic-webgpu-first-route-pm-evidence-13-negative-cpu-fallback.txt",
    "diagnostic-webgpu-first-route-pm-evidence-14-unsupported-route-refusals.txt",
    "diagnostic-webgpu-first-route-pm-evidence-15-recording-analysis.txt",
    "diagnostic-webgpu-first-route-pm-evidence-16-recording-task-list.txt",
    "diagnostic-webgpu-first-route-pm-evidence-17-recording-compatibility.txt",
    "diagnostic-webgpu-first-route-pm-evidence-18-recording-replay.txt",
]
EXPECTED_SUMMARY_FIELDS = {
    "key",
    "claimLevel",
    "status",
    "validationReportName",
    "promotionGate",
    "promotionGatePassed",
    "missingEvidence",
    "scope",
    "artifactDirectory",
    "manifestArtifact",
    "artifactCount",
    "dumpArtifactCount",
    "generationCommand",
    "validationCommand",
    "productRouteActivated",
    "rootPipelinePmBundleDependency",
    "nativeKadreCiRequired",
    "webGpuAdapterRequired",
    "releaseBlocking",
    "readinessDelta",
    "nonClaims",
    "notice",
    "manifestSha256",
    "artifactHashes",
}

FORBIDDEN_RAW_OR_PRODUCT_CLAIM_SNIPPETS = {
    "productRouteActivated=true",
    "releaseBlocking=true",
    "nativeKadreCiRequired=true",
    "supportClaim",
    "supported=true",
    "readbackBytes",
    "rawPixels",
    "pixel",
    "fn vs_main",
    "fn fs_main",
    "var<uniform>",
}

FORBIDDEN_RAW_OR_PRODUCT_CLAIM_PATTERNS = [
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?product[\s_-]*route[\s_-]*activated[\"']?\s*(?:=|:)\s*true\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?release[\s_-]*blocking[\"']?\s*(?:=|:)\s*true\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?native[\s_-]*kadre[\s_-]*ci[\s_-]*required[\"']?\s*(?:=|:)\s*true\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?readiness[\s_-]*delta[\"']?\s*(?:=|:)\s*[+-]?(?:[1-9]\d*(?:\.\d+)?|0?\.\d*[1-9]\d*)\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?support[\s_-]*claim[\"']?\s*(?:=|:)\s*true\b",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?supported[\"']?\s*(?:=|:)\s*true\b",
        re.IGNORECASE,
    ),
]

FORBIDDEN_ADAPTER_REQUIREMENT_CLAIM_PATTERNS = [
    re.compile(
        r"(?<![A-Za-z0-9_])[\"']?web[\s_-]*gpu[\s_-]*adapter[\s_-]*required[\"']?\s*(?:=|:)\s*false\b",
        re.IGNORECASE,
    ),
]

FORBIDDEN_SKIP_MARKER_SNIPPETS = {
    "No WebGPU adapter",
    "adapter-skipped",
    "skipped=true",
    "skipReason",
}

FORBIDDEN_SKIP_MARKER_PATTERNS = [
    re.compile(r"\bskipped\s*=\s*true\b", re.IGNORECASE),
    re.compile(r"\bskipreason\b", re.IGNORECASE),
    re.compile(r"\bno[-\s]+webgpu[-\s]+adapter\b", re.IGNORECASE),
    re.compile(r"\bwebgpu[-\s]+adapter[-\s]+unavailable\b", re.IGNORECASE),
    re.compile(r"\badapter[-\s]+skipped\b", re.IGNORECASE),
]

FORBIDDEN_SHADOW_RESULT_DUMP_SNIPPETS = {
    "gpuRendererShadow v=1",
    "kanvas.gpu.renderer.shadow.fillRect",
}

FORBIDDEN_POSITIVE_CONFLICT_SNIPPETS = {
    "GPURouteDecision.Prepared",
    "GPURouteDecision.Refused",
    "GPURouteDecision.ReferenceOnly",
    "GPUResourceMaterializationDecision.Deferred",
    "GPUResourceMaterializationDecision.Failed",
    "GPUResourceMaterializationDecision.Refused",
    "GPUCommandSubmission.Failed",
    "GPUCommandSubmission.Refused",
    "GPUReadbackResult.Failed",
    "GPUReadbackResult.Refused",
    "GPUReadbackResult.Skipped",
    "resource.materialization:deferred",
    "resource.materialization:failed",
    "resource.materialization:refused",
    "execution.submission:failed",
    "execution.submission:refused",
    "execution.readback:failed",
    "execution.readback:skipped",
}

REQUIRED_ARTIFACT_SNIPPETS = {
    "diagnostic-webgpu-first-route-pm-evidence-01-command.txt": [
        "commands:NormalizedDrawCommand.FillRect:",
        "recording.webgpu-submit",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-02-analysis.txt": [
        "analysis:GPUDrawAnalysisRecord:",
        "analysis.fill_rect.42",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-03-route.txt": [
        "routing:GPURouteDecision.Native:",
        "route:native.fill_rect.solid",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-04-material.txt": [
        "materials:GPUPaintPipelinePlan:",
        "materialKeyHashes=",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-05-wgsl.txt": [
        "wgsl:WGSLReflectionResult:",
        "parserState=parser-backed",
        "tool=wgsl4k",
        "source=wgsl4k-parser-ast:generated-solid-rect",
        "bindings=group=0/binding=0/role=uniforms/kind=uniform-buffer",
        "uniforms=layout:",
        "storageCount=0",
        "diagnostics=none",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-06-payload.txt": [
        "payloads:GPUPayloadGatherPlan:",
        "renderTasks=task.render.42",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-07-pipeline-key.txt": [
        "pipelines:GPUPipelineKeyPreimage.Render:",
        "pipelineKeyHashes=",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-08-resource-decision.txt": [
        "resources:GPUResourceMaterializationDecision.Materialized:",
        "resource.materialization:materialized",
        "resourcePlans=webgpu.headless-target.surface",
        "resourceCount=1",
        "diagnostics=none",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-09-submission.txt": [
        "execution:GPUCommandSubmission.Submitted:",
        "execution.submission:submitted",
        "tasks=task.render.42",
        "passes=pass.root.42",
        "readbacks=readback-1",
        "diagnostics=none",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-10-readback.txt": [
        "execution:GPUReadbackResult.Completed:",
        "execution.readback:completed",
        "request=readback-1",
        "source=first-route-webgpu-submit",
        "format=rgba8unorm",
        "failureReason=none",
        "bytes=1024",
        "payloadHash=sha256:",
        "diagnostics=none",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-11-telemetry.txt": [
        "telemetry:GPUTelemetryLedger:counter:first_route.route.count:kind=GPUNative:1:count",
        "telemetry:GPUTelemetryLedger:counter:first_route.wgsl_module_validation.count:outcome=Success:1:count",
        "telemetry:GPUTelemetryLedger:counter:first_route.resource_materialization.count:outcome=Materialized:1:count",
        "telemetry:GPUTelemetryLedger:counter:first_route.command_submission.count:outcome=Submitted:1:count",
        "telemetry:GPUTelemetryLedger:counter:first_route.negative_cpu_fallback.refusal.count:policy=forbidden:1:count",
        "telemetry:GPUCacheTelemetry.pipeline:event:pipeline:Miss:key=render-pipeline:first-fill-rect:subject=webgpu:first-route-submit",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-12-pipeline-cache.txt": [
        "telemetry:GPUCacheTelemetry.pipeline:cache:pipeline:hits=0:misses=1",
        "telemetry:GPUCacheTelemetry.pipeline:event:pipeline:Miss:key=render-pipeline:first-fill-rect:subject=webgpu:first-route-submit",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-13-negative-cpu-fallback.txt": [
        "routing:NegativeCPUFallbackRefusal:",
        "has no CPU-rendered texture fallback tasks",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-14-unsupported-route-refusals.txt": [
        "routing:UnsupportedRouteFamilyRefusal:",
        "unsupportedFamilies=perspective-transform,singular-transform,unsupported-target-format,unsupported-blend,non-simple-clip,layer-filter-destination-read,missing-capability,wgsl-validation-or-abi-mismatch",
        "diagnostics=none",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-15-recording-analysis.txt": [
        "recording:GPUAnalysisDecisionDump:",
        "decision:candidate:analysis.fill_rect.42:native.fill_rect.solid",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-16-recording-task-list.txt": [
        "recording:GPUTaskList:",
        "task:render:task.render.42:pass.root.42:analysis.fill_rect.42:pre_materialization",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-17-recording-compatibility.txt": [
        "recording:GPURecordingCompatibilityKey:",
        "targetFormatClass=rgba8unorm",
        "resourceTopologyClass=pre_materialization.no_concrete_resources",
        "replayPolicy=one-shot",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-18-recording-replay.txt": [
        "recording:GPURecordingReplayResult.Refused:",
        "replay.one_shot_recording",
    ],
}

FIRST_ROUTE_POSITIVE_CONCEPT_SNIPPETS = {
    "diagnostic-webgpu-first-route-pm-evidence-03-route.txt": "routing:GPURouteDecision.Native:",
    "diagnostic-webgpu-first-route-pm-evidence-08-resource-decision.txt": (
        "resources:GPUResourceMaterializationDecision.Materialized:"
    ),
    "diagnostic-webgpu-first-route-pm-evidence-09-submission.txt": "execution:GPUCommandSubmission.Submitted:",
    "diagnostic-webgpu-first-route-pm-evidence-10-readback.txt": "execution:GPUReadbackResult.Completed:",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"GPU renderer R6 executed PM evidence validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def require_exact_fields(payload: dict[str, Any], expected_fields: set[str], owner: str) -> None:
    actual_fields = set(payload.keys())
    unexpected = sorted(actual_fields - expected_fields)
    missing = sorted(expected_fields - actual_fields)
    require(not unexpected, f"{owner} has unexpected fields: {', '.join(unexpected)}")
    require(not missing, f"{owner} missing fields: {', '.join(missing)}")


def require_float(payload: dict[str, Any], field: str, expected: float, owner: str) -> None:
    value = payload.get(field)
    require(isinstance(value, int | float) and not isinstance(value, bool), f"{owner}.{field} must be numeric")
    require(float(value) == expected, f"{owner}.{field} must be {expected}")


def canonical_json(payload: dict[str, Any]) -> str:
    return json.dumps(payload, ensure_ascii=True, separators=(",", ":"), sort_keys=True)


def reject_duplicate_json_object_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    payload: dict[str, Any] = {}
    for key, value in pairs:
        require(key not in payload, f"duplicate JSON key: {key}")
        payload[key] = value
    return payload


def load_manifest_fields(manifest_path: Path) -> dict[str, str]:
    require(manifest_path.is_file(), f"missing validation manifest: {manifest_path}")
    fields: dict[str, str] = {}
    for line in manifest_path.read_text(encoding="utf-8").splitlines():
        require("=" in line, f"manifest line must be key=value: {line}")
        key, value = line.split("=", 1)
        require(key not in fields, f"duplicate manifest key: {key}")
        fields[key] = value
    return fields


def expected_manifest_fields() -> set[str]:
    fields = {
        "validation.report.name",
        "validation.report.status",
        "validation.gate.name",
        "validation.gate.passed",
        "validation.gate.missingEvidence",
        "validation.bundle.scope",
        "validation.report.artifacts",
        "validation.report.diagnostics",
        "validation.gate.diagnostics",
    }
    for index in range(1, len(EXPECTED_ARTIFACT_NAMES) + 1):
        ordinal = f"{index:02d}"
        fields.update(
            {
                f"artifact.{ordinal}.name",
                f"artifact.{ordinal}.lines",
                f"artifact.{ordinal}.sha256",
            }
        )
    return fields


def validate_manifest_field_inventory(fields: dict[str, str]) -> None:
    expected = expected_manifest_fields()
    actual = set(fields.keys())
    unexpected = sorted(actual - expected)
    missing = sorted(expected - actual)
    require(not unexpected, f"unexpected executed manifest fields: {', '.join(unexpected)}")
    require(not missing, f"missing executed manifest fields: {', '.join(missing)}")


def line_count_for_exported_text(text: str) -> int:
    if text == "\n":
        return 0
    require(text.endswith("\n"), "artifact text must end with a final newline")
    return len(text[:-1].split("\n"))


def artifact_rows(fields: dict[str, str]) -> list[dict[str, Any]]:
    artifact_count = int(fields.get("validation.report.artifacts", "-1"))
    rows: list[dict[str, Any]] = []
    for index in range(1, artifact_count + 1):
        ordinal = f"{index:02d}"
        name = fields.get(f"artifact.{ordinal}.name")
        lines = fields.get(f"artifact.{ordinal}.lines")
        sha256 = fields.get(f"artifact.{ordinal}.sha256")
        require(name is not None, f"artifact.{ordinal}.name missing")
        require(lines is not None, f"artifact.{ordinal}.lines missing")
        require(sha256 is not None, f"artifact.{ordinal}.sha256 missing")
        rows.append(
            {
                "ordinal": ordinal,
                "name": name,
                "lines": int(lines),
                "sha256": sha256,
            }
        )
    actual_names = [str(row["name"]) for row in rows]
    missing_names = [name for name in EXPECTED_ARTIFACT_NAMES if name not in actual_names]
    require(
        "diagnostic-webgpu-first-route-pm-evidence-14-unsupported-route-refusals.txt" not in missing_names,
        "unsupported-route-refusals evidence artifact is missing",
    )
    require(artifact_count == len(EXPECTED_ARTIFACT_NAMES), "executed manifest artifact count changed")
    require(actual_names == EXPECTED_ARTIFACT_NAMES, f"executed artifact order changed: {actual_names}")
    return rows


def validate_artifact_hashes(output_dir: Path, rows: list[dict[str, Any]]) -> None:
    for row in rows:
        relative_path = str(row["name"])
        require(not relative_path.startswith("/") and ".." not in relative_path.split("/"), f"non-portable artifact path: {relative_path}")
        artifact_path = output_dir / relative_path
        require(artifact_path.is_file(), f"missing artifact: {relative_path}")
        bytes_value = artifact_path.read_bytes()
        actual_hash = "sha256:" + hashlib.sha256(bytes_value).hexdigest()
        require(actual_hash == row["sha256"], f"hash mismatch for {relative_path}: {actual_hash} != {row['sha256']}")
        actual_lines = line_count_for_exported_text(bytes_value.decode("utf-8"))
        require(actual_lines == row["lines"], f"line-count mismatch for {relative_path}: {actual_lines} != {row['lines']}")


def validate_no_unexpected_files(output_dir: Path, rows: list[dict[str, Any]]) -> None:
    allowed_files = {MANIFEST_ARTIFACT, "diagnostic-webgpu-first-route-pm-evidence-summary.json"}
    allowed_files.update(str(row["name"]) for row in rows)
    actual_files = sorted(
        str(path.relative_to(output_dir)).replace("\\", "/")
        for path in output_dir.rglob("*")
        if path.is_file()
    )
    unexpected = [path for path in actual_files if path not in allowed_files]
    require(not unexpected, f"executed PM bundle contains unexpected files: {', '.join(unexpected)}")
    require("pm-bundle-manifest-entry.json" not in actual_files, "executed PM bundle must not contain root PM manifest entry")


def validate_no_raw_or_product_claims(output_dir: Path, fields: dict[str, str], rows: list[dict[str, Any]]) -> None:
    joined_manifest = "\n".join(f"{key}={value}" for key, value in sorted(fields.items()))
    for snippet in FORBIDDEN_RAW_OR_PRODUCT_CLAIM_SNIPPETS:
        require(snippet not in joined_manifest, f"manifest contains forbidden raw or product claim: {snippet}")
    for pattern in FORBIDDEN_RAW_OR_PRODUCT_CLAIM_PATTERNS:
        require(pattern.search(joined_manifest) is None, f"manifest contains forbidden raw or product claim: {pattern.pattern}")
    for pattern in FORBIDDEN_ADAPTER_REQUIREMENT_CLAIM_PATTERNS:
        require(pattern.search(joined_manifest) is None, f"manifest contains forbidden adapter requirement claim: {pattern.pattern}")
    for snippet in FORBIDDEN_SKIP_MARKER_SNIPPETS:
        require(snippet not in joined_manifest, f"manifest contains forbidden skip marker: {snippet}")
    for pattern in FORBIDDEN_SKIP_MARKER_PATTERNS:
        require(pattern.search(joined_manifest) is None, f"manifest contains forbidden skip marker: {pattern.pattern}")
    for snippet in FORBIDDEN_SHADOW_RESULT_DUMP_SNIPPETS:
        require(snippet not in joined_manifest, f"manifest contains forbidden shadow result dump marker: {snippet}")
    for row in rows:
        artifact_path = output_dir / str(row["name"])
        text = artifact_path.read_text(encoding="utf-8")
        require(
            re.search(r"(@(?:vertex|fragment|compute)\b|\bfn\s+[A-Za-z_][A-Za-z0-9_]*\s*\(|\bvar<)", text) is None,
            f"{row['name']} contains raw WGSL source",
        )
        for snippet in FORBIDDEN_RAW_OR_PRODUCT_CLAIM_SNIPPETS:
            require(snippet not in text, f"{row['name']} contains forbidden raw or product claim: {snippet}")
        for pattern in FORBIDDEN_RAW_OR_PRODUCT_CLAIM_PATTERNS:
            require(pattern.search(text) is None, f"{row['name']} contains forbidden raw or product claim: {pattern.pattern}")
        for pattern in FORBIDDEN_ADAPTER_REQUIREMENT_CLAIM_PATTERNS:
            require(pattern.search(text) is None, f"{row['name']} contains forbidden adapter requirement claim: {pattern.pattern}")
        for snippet in FORBIDDEN_SKIP_MARKER_SNIPPETS:
            require(snippet not in text, f"{row['name']} contains forbidden skip marker: {snippet}")
        for pattern in FORBIDDEN_SKIP_MARKER_PATTERNS:
            require(pattern.search(text) is None, f"{row['name']} contains forbidden skip marker: {pattern.pattern}")
        for snippet in FORBIDDEN_SHADOW_RESULT_DUMP_SNIPPETS:
            require(snippet not in text, f"{row['name']} contains forbidden shadow result dump marker: {snippet}")
        for snippet in FORBIDDEN_POSITIVE_CONFLICT_SNIPPETS:
            require(snippet not in text, f"{row['name']} contains conflicting positive evidence: {snippet}")


def validate_required_artifact_content(output_dir: Path, rows: list[dict[str, Any]]) -> None:
    names = {str(row["name"]) for row in rows}
    for artifact_name, snippets in REQUIRED_ARTIFACT_SNIPPETS.items():
        require(artifact_name in names, f"missing required executed artifact: {artifact_name}")
        text = (output_dir / artifact_name).read_text(encoding="utf-8")
        for snippet in snippets:
            require(snippet in text, f"{artifact_name} missing required snippet: {snippet}")
    readback_text = (output_dir / "diagnostic-webgpu-first-route-pm-evidence-10-readback.txt").read_text(encoding="utf-8")
    require(
        re.search(r"payloadHash=sha256:[0-9a-f]{64}\b", readback_text) is not None,
        "readback evidence must contain a SHA-256 payload hash",
    )
    for artifact_name, snippet in FIRST_ROUTE_POSITIVE_CONCEPT_SNIPPETS.items():
        text = (output_dir / artifact_name).read_text(encoding="utf-8")
        occurrence_count = text.count(snippet)
        require(
            occurrence_count == 1,
            f"{artifact_name} must contain exactly one first-route positive evidence line: {snippet}",
        )


def build_summary(fields: dict[str, str], rows: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "key": "gpuRendererR6ExecutedFirstRoutePmEvidence",
        "claimLevel": "gpu-renderer-r6-executed-diagnostic-pm-evidence-only",
        "status": fields["validation.report.status"],
        "validationReportName": fields["validation.report.name"],
        "promotionGate": fields["validation.gate.name"],
        "promotionGatePassed": True,
        "missingEvidence": [],
        "scope": fields["validation.bundle.scope"],
        "artifactDirectory": DEFAULT_OUTPUT_DIR,
        "manifestArtifact": f"{DEFAULT_OUTPUT_DIR}/{MANIFEST_ARTIFACT}",
        "artifactCount": len(rows) + 1,
        "dumpArtifactCount": len(rows),
        "generationCommand": "rtk ./gradlew --no-daemon :gpu-raster:gpuRendererR6ExecutedFirstRoutePmEvidenceBundle",
        "validationCommand": "rtk python3 scripts/validate_gpu_renderer_r6_executed_pm_evidence_bundle.py . gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence",
        "productRouteActivated": False,
        "rootPipelinePmBundleDependency": False,
        "nativeKadreCiRequired": False,
        "webGpuAdapterRequired": True,
        "releaseBlocking": False,
        "readinessDelta": 0.0,
        "nonClaims": [
            "No product route activation.",
            "No root pipelinePmBundle dependency on adapter-backed evidence.",
            "No release readiness movement from this diagnostic lane.",
            "No raw readback payloads, backend handles, or WGSL source are exported.",
        ],
        "notice": "The executed GPU renderer R6 bundle is adapter-backed diagnostic PM evidence. It proves one opt-in WebGPU first-route submit/readback path for review, but it is not root PM packaging and not product support activation.",
    }


def validate_summary_artifact_hashes(summary: dict[str, Any], rows: list[dict[str, Any]] | None = None) -> None:
    artifact_hashes = summary.get("artifactHashes")
    require(isinstance(artifact_hashes, dict), "summary sidecar artifactHashes must be a JSON object")

    non_string_keys = [key for key in artifact_hashes.keys() if not isinstance(key, str)]
    require(
        not non_string_keys,
        f"summary sidecar artifactHashes has non-string artifact names: {', '.join(str(key) for key in non_string_keys)}",
    )
    actual_names = set(artifact_hashes.keys())
    expected_names = set(EXPECTED_ARTIFACT_NAMES)
    unexpected = sorted(actual_names - expected_names)
    missing = [name for name in EXPECTED_ARTIFACT_NAMES if name not in artifact_hashes]
    require(not unexpected, f"summary sidecar artifactHashes has unexpected artifacts: {', '.join(unexpected)}")
    require(not missing, f"summary sidecar artifactHashes missing artifacts: {', '.join(missing)}")

    for name in EXPECTED_ARTIFACT_NAMES:
        value = artifact_hashes.get(name)
        require(isinstance(value, str), f"summary sidecar artifactHashes.{name} must be a string")

    if rows is None:
        return

    rebuilt_hashes = {
        str(row["name"]): str(row["sha256"])
        for row in rows
    }
    require(
        set(rebuilt_hashes.keys()) == expected_names,
        "rebuilt executed PM artifact hash inventory changed",
    )
    for name in EXPECTED_ARTIFACT_NAMES:
        require(
            artifact_hashes[name] == rebuilt_hashes[name],
            f"summary sidecar artifactHashes.{name} changed",
        )


def validate_summary_payload(summary: dict[str, Any], rows: list[dict[str, Any]] | None = None) -> None:
    require_exact_fields(summary, EXPECTED_SUMMARY_FIELDS, "summary sidecar")
    validate_summary_artifact_hashes(summary, rows)
    require(summary.get("key") == "gpuRendererR6ExecutedFirstRoutePmEvidence", "summary sidecar key changed")
    require(
        summary.get("claimLevel") == "gpu-renderer-r6-executed-diagnostic-pm-evidence-only",
        "summary sidecar claimLevel changed",
    )
    require(summary.get("status") == "Passed", "summary sidecar status changed")
    require(summary.get("validationReportName") == EXPECTED_REPORT_NAME, "summary sidecar validationReportName changed")
    require(summary.get("promotionGate") == "first-route-promotion", "summary sidecar promotionGate changed")
    require(summary.get("promotionGatePassed") is True, "summary sidecar promotionGatePassed must be true")
    require(summary.get("missingEvidence") == [], "summary sidecar missingEvidence changed")
    require(summary.get("scope") == "pm-evidence-only", "summary sidecar scope changed")
    require(summary.get("artifactDirectory") == DEFAULT_OUTPUT_DIR, "summary sidecar artifactDirectory changed")
    require(
        summary.get("manifestArtifact") == f"{DEFAULT_OUTPUT_DIR}/{MANIFEST_ARTIFACT}",
        "summary sidecar manifestArtifact changed",
    )
    require(summary.get("artifactCount") == len(EXPECTED_ARTIFACT_NAMES) + 1, "summary sidecar artifactCount changed")
    require(summary.get("dumpArtifactCount") == len(EXPECTED_ARTIFACT_NAMES), "summary sidecar dumpArtifactCount changed")
    require(summary.get("productRouteActivated") is False, "summary sidecar productRouteActivated must be false")
    require(
        summary.get("rootPipelinePmBundleDependency") is False,
        "summary sidecar rootPipelinePmBundleDependency must be false",
    )
    require(summary.get("nativeKadreCiRequired") is False, "summary sidecar nativeKadreCiRequired must be false")
    require(summary.get("webGpuAdapterRequired") is True, "summary sidecar webGpuAdapterRequired must be true")
    require(summary.get("releaseBlocking") is False, "summary sidecar releaseBlocking must be false")
    require_float(summary, "readinessDelta", 0.0, "summary sidecar")


def validate_summary_sidecar(output_dir: Path, summary: dict[str, Any], rows: list[dict[str, Any]]) -> None:
    sidecar_path = output_dir / "diagnostic-webgpu-first-route-pm-evidence-summary.json"
    if not sidecar_path.is_file():
        return
    try:
        sidecar = json.loads(
            sidecar_path.read_text(encoding="utf-8"),
            object_pairs_hook=reject_duplicate_json_object_pairs,
        )
    except json.JSONDecodeError as exc:
        fail(f"summary sidecar is not valid JSON: {exc}")
    require(isinstance(sidecar, dict), "summary sidecar must be a JSON object")
    validate_summary_payload(sidecar, rows)
    require(canonical_json(sidecar) == canonical_json(summary), "summary sidecar disagrees with rebuilt executed PM summary")


def validate_output(output_dir: Path) -> tuple[dict[str, str], list[dict[str, Any]], dict[str, Any]]:
    manifest_path = output_dir / MANIFEST_ARTIFACT
    fields = load_manifest_fields(manifest_path)
    require(fields.get("validation.report.name") == EXPECTED_REPORT_NAME, "unexpected executed validation report name")
    require(fields.get("validation.report.status") == "Passed", "executed validation report must be Passed")
    require(fields.get("validation.gate.name") == "first-route-promotion", "unexpected promotion gate name")
    require(fields.get("validation.gate.passed") == "true", "executed promotion gate must be true")
    require(fields.get("validation.gate.missingEvidence") == "none", "executed promotion gate must have no missing evidence")
    require(fields.get("validation.bundle.scope") == "pm-evidence-only", "executed bundle must remain evidence-only")
    require(fields.get("validation.report.diagnostics") == "0", "executed report diagnostics must be zero")
    require(fields.get("validation.gate.diagnostics") == "0", "executed gate diagnostics must be zero")
    rows = artifact_rows(fields)
    validate_artifact_hashes(output_dir, rows)
    validate_no_unexpected_files(output_dir, rows)
    validate_no_raw_or_product_claims(output_dir, fields, rows)
    validate_manifest_field_inventory(fields)
    validate_required_artifact_content(output_dir, rows)
    summary = build_summary(fields, rows)
    summary["manifestSha256"] = "sha256:" + hashlib.sha256(manifest_path.read_bytes()).hexdigest()
    summary["artifactHashes"] = {
        str(row["name"]): str(row["sha256"])
        for row in rows
    }
    validate_summary_payload(summary, rows)
    validate_summary_sidecar(output_dir, summary, rows)
    return fields, rows, summary


def validate_root_pipeline_isolation(root: Path) -> None:
    build_gradle_path = root / "build.gradle.kts"
    require(build_gradle_path.is_file(), f"missing root build file: {build_gradle_path}")
    build_gradle = build_gradle_path.read_text(encoding="utf-8")
    task_name_aliases = gradle_task_name_aliases(build_gradle)
    require(gradle_has_pipeline_task_registration(build_gradle, task_name_aliases), "root pipelinePmBundle task is missing")
    pipeline_blocks = root_pipeline_pm_bundle_blocks(build_gradle, task_name_aliases)
    forbidden = [
        "gpuRendererR6ExecutedFirstRoutePmEvidenceBundle",
        "validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle",
        "validate_gpu_renderer_r6_executed_pm_evidence_bundle.py",
    ]
    forbidden_aliases = gradle_value_aliases(build_gradle, forbidden)
    for block in pipeline_blocks:
        searchable_block = gradle_without_comments(block)
        for snippet in forbidden:
            require(
                snippet not in searchable_block,
                f"pipelinePmBundle depends on adapter-backed executed evidence: {snippet}",
            )
        for alias in forbidden_aliases:
            require(
                re.search(rf"\b{re.escape(alias)}\b", searchable_block) is None,
                f"pipelinePmBundle depends on adapter-backed executed evidence through alias: {alias}",
            )


def root_pipeline_pm_bundle_blocks(build_gradle: str, task_name_aliases: set[str]) -> list[str]:
    blocks: list[str] = []
    pipeline_aliases = gradle_pipeline_task_aliases(build_gradle, task_name_aliases)
    direct_pattern = re.compile(r"(?:project\.|this\.)?tasks\.(?:register|named|getByName)(?:<[^>]+>)?\s*\(")
    for match in direct_pattern.finditer(build_gradle):
        start = match.start()
        if not gradle_position_is_code(build_gradle, start):
            continue
        if gradle_receiver_starts_statement(build_gradle, start):
            statement = gradle_statement_or_block(build_gradle, start)
            if gradle_task_call_targets_pipeline_task(
                build_gradle,
                start,
                {"register", "named", "getByName"},
                task_name_aliases,
            ):
                blocks.append(statement)
    configure_pattern = re.compile(r"(?:project\.|this\.)?configure(?:<[^>]+>)?\s*\(")
    for match in configure_pattern.finditer(build_gradle):
        start = match.start()
        if not gradle_position_is_code(build_gradle, start):
            continue
        if gradle_receiver_starts_statement(build_gradle, start) and gradle_configure_call_targets_pipeline_task(
            build_gradle,
            start,
            pipeline_aliases,
            task_name_aliases,
        ):
            blocks.append(gradle_statement_or_block(build_gradle, start))
    for alias in pipeline_aliases:
        alias_pattern = re.compile(rf"\b{re.escape(alias)}\b")
        for match in alias_pattern.finditer(build_gradle):
            start = match.start()
            if not gradle_position_is_code(build_gradle, start):
                continue
            if gradle_receiver_starts_statement(build_gradle, start):
                blocks.append(gradle_statement_or_block(build_gradle, start))
    require(blocks, "root pipelinePmBundle task is missing")
    return blocks


def gradle_pipeline_task_aliases(build_gradle: str, task_name_aliases: set[str]) -> set[str]:
    alias_statements: list[tuple[str, str]] = []
    pattern = re.compile(
        r"\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*[^=\n]+)?="
    )
    for match in pattern.finditer(build_gradle):
        if not gradle_position_is_code(build_gradle, match.start()):
            continue
        statement = gradle_statement_or_block(build_gradle, match.start())
        alias_statements.append((match.group(1), gradle_without_comments(statement)))
    aliases: set[str] = set()
    changed = True
    while changed:
        changed = False
        for name, statement in alias_statements:
            if name in aliases:
                continue
            direct_pipeline_task = gradle_statement_targets_pipeline_task(
                statement,
                {"named", "getByName"},
                task_name_aliases,
            )
            transitive_pipeline_task = any(
                re.search(rf"\b{re.escape(alias)}\b", statement) is not None
                for alias in aliases
            )
            if direct_pipeline_task or transitive_pipeline_task:
                aliases.add(name)
                changed = True
    return aliases


def gradle_task_name_aliases(build_gradle: str) -> set[str]:
    alias_statements: list[tuple[str, str]] = []
    pattern = re.compile(
        r"\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*[^=\n]+)?="
    )
    for match in pattern.finditer(build_gradle):
        if not gradle_position_is_code(build_gradle, match.start()):
            continue
        statement = gradle_without_comments(gradle_statement_or_block(build_gradle, match.start()))
        alias_statements.append((match.group(1), statement))
    aliases: set[str] = set()
    changed = True
    while changed:
        changed = False
        for name, statement in alias_statements:
            if name in aliases:
                continue
            direct_pipeline_name = (
                '"pipelinePmBundle"' in statement
                or '"""pipelinePmBundle"""' in statement
            )
            transitive_pipeline_name = any(
                re.search(rf"\b{re.escape(alias)}\b", statement) is not None
                for alias in aliases
            )
            if direct_pipeline_name or transitive_pipeline_name:
                aliases.add(name)
                changed = True
    return aliases


def gradle_has_pipeline_task_registration(build_gradle: str, task_name_aliases: set[str]) -> bool:
    register_pattern = re.compile(r"(?:project\.|this\.)?tasks\.register(?:<[^>]+>)?\s*\(")
    for match in register_pattern.finditer(build_gradle):
        start = match.start()
        if not gradle_position_is_code(build_gradle, start):
            continue
        statement = gradle_statement_or_block(build_gradle, start)
        if gradle_task_call_targets_pipeline_task(build_gradle, start, {"register"}, task_name_aliases):
            return True
    return False


def gradle_statement_targets_pipeline_task(statement: str, methods: set[str], task_name_aliases: set[str]) -> bool:
    method_pattern = "|".join(re.escape(method) for method in sorted(methods))
    task_call_pattern = re.compile(rf"(?:project\.|this\.)?tasks\.({method_pattern})(?:<[^>]+>)?\s*\(")
    for match in task_call_pattern.finditer(statement):
        if not gradle_position_is_code(statement, match.start()):
            continue
        if gradle_task_call_targets_pipeline_task(statement, match.start(), methods, task_name_aliases):
            return True
    return False


def gradle_task_call_targets_pipeline_task(
    build_gradle: str,
    start: int,
    methods: set[str],
    task_name_aliases: set[str],
) -> bool:
    call_pattern = re.compile(r"(?:project\.|this\.)?tasks\.([A-Za-z_][A-Za-z0-9_]*)(?:<[^>]+>)?\s*\(")
    match = call_pattern.match(build_gradle, start)
    if match is None or match.group(1) not in methods:
        return False
    searchable_call = gradle_without_comments(gradle_call_expression(build_gradle, start))
    if '"pipelinePmBundle"' in searchable_call:
        return True
    return any(
        re.search(rf"\b{re.escape(alias)}\b", searchable_call) is not None
        for alias in task_name_aliases
    )


def gradle_configure_call_targets_pipeline_task(
    build_gradle: str,
    start: int,
    pipeline_aliases: set[str],
    task_name_aliases: set[str],
) -> bool:
    call_pattern = re.compile(r"(?:project\.|this\.)?configure(?:<[^>]+>)?\s*\(")
    if call_pattern.match(build_gradle, start) is None:
        return False
    searchable_call = gradle_without_comments(gradle_call_expression(build_gradle, start))
    if '"pipelinePmBundle"' in searchable_call:
        return True
    return any(
        re.search(rf"\b{re.escape(alias)}\b", searchable_call) is not None
        for alias in pipeline_aliases | task_name_aliases
    )


def gradle_call_expression(build_gradle: str, start: int) -> str:
    open_index = build_gradle.find("(", start)
    if open_index == -1:
        return build_gradle[start:]
    paren_depth = 0
    in_string = False
    in_raw_string = False
    in_line_comment = False
    in_block_comment = False
    escaped = False
    index = open_index
    while index < len(build_gradle):
        char = build_gradle[index]
        next_char = build_gradle[index + 1] if index + 1 < len(build_gradle) else ""
        if in_line_comment:
            if char == "\n":
                in_line_comment = False
            index += 1
            continue
        if in_block_comment:
            if char == "*" and next_char == "/":
                in_block_comment = False
                index += 2
                continue
            index += 1
            continue
        if in_raw_string:
            if build_gradle.startswith('"""', index):
                in_raw_string = False
                index += 3
                continue
            index += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue
        if char == "/" and next_char == "/":
            in_line_comment = True
            index += 2
            continue
        if char == "/" and next_char == "*":
            in_block_comment = True
            index += 2
            continue
        if build_gradle.startswith('"""', index):
            in_raw_string = True
            index += 3
            continue
        if char == '"':
            in_string = True
        elif char == "(":
            paren_depth += 1
        elif char == ")":
            paren_depth = max(0, paren_depth - 1)
            if paren_depth == 0:
                return build_gradle[start:index + 1]
        index += 1
    return build_gradle[start:]


def gradle_value_aliases(build_gradle: str, forbidden: list[str]) -> set[str]:
    alias_statements: list[tuple[str, str]] = []
    pattern = re.compile(r"\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*[^=\n]+)?=")
    for match in pattern.finditer(build_gradle):
        if not gradle_position_is_code(build_gradle, match.start()):
            continue
        statement = gradle_statement_or_block(build_gradle, match.start())
        alias_statements.append((match.group(1), gradle_without_comments(statement)))
    aliases: set[str] = set()
    changed = True
    while changed:
        changed = False
        for name, statement in alias_statements:
            if name in aliases:
                continue
            direct_forbidden = any(snippet in statement for snippet in forbidden)
            transitive_forbidden = any(
                re.search(rf"\b{re.escape(alias)}\b", statement) is not None
                for alias in aliases
            )
            if direct_forbidden or transitive_forbidden:
                aliases.add(name)
                changed = True
    return aliases


def gradle_receiver_starts_statement(build_gradle: str, start: int) -> bool:
    line_start = build_gradle.rfind("\n", 0, start) + 1
    paren_depth, bracket_depth = gradle_expression_nesting_at(build_gradle, start)
    return build_gradle[line_start:start].strip() == "" and paren_depth == 0 and bracket_depth == 0


def gradle_expression_nesting_at(build_gradle: str, target: int) -> tuple[int, int]:
    paren_depth = 0
    bracket_depth = 0
    in_string = False
    in_raw_string = False
    in_line_comment = False
    in_block_comment = False
    escaped = False
    index = 0
    while index < target:
        char = build_gradle[index]
        next_char = build_gradle[index + 1] if index + 1 < len(build_gradle) else ""
        if in_line_comment:
            if char == "\n":
                in_line_comment = False
            index += 1
            continue
        if in_block_comment:
            if char == "*" and next_char == "/":
                in_block_comment = False
                index += 2
                continue
            index += 1
            continue
        if in_raw_string:
            if build_gradle.startswith('"""', index):
                in_raw_string = False
                index += 3
                continue
            index += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue
        if char == "/" and next_char == "/":
            in_line_comment = True
            index += 2
            continue
        if char == "/" and next_char == "*":
            in_block_comment = True
            index += 2
            continue
        if build_gradle.startswith('"""', index):
            in_raw_string = True
            index += 3
            continue
        if char == '"':
            in_string = True
        elif char == "(":
            paren_depth += 1
        elif char == ")":
            paren_depth = max(0, paren_depth - 1)
        elif char == "[":
            bracket_depth += 1
        elif char == "]":
            bracket_depth = max(0, bracket_depth - 1)
        index += 1
    return paren_depth, bracket_depth


def gradle_position_is_code(build_gradle: str, target: int) -> bool:
    in_string = False
    in_raw_string = False
    in_line_comment = False
    in_block_comment = False
    escaped = False
    index = 0
    while index < target:
        char = build_gradle[index]
        next_char = build_gradle[index + 1] if index + 1 < len(build_gradle) else ""
        if in_line_comment:
            if char == "\n":
                in_line_comment = False
            index += 1
            continue
        if in_block_comment:
            if char == "*" and next_char == "/":
                in_block_comment = False
                index += 2
                continue
            index += 1
            continue
        if in_raw_string:
            if build_gradle.startswith('"""', index):
                in_raw_string = False
                index += 3
                continue
            index += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue
        if char == "/" and next_char == "/":
            in_line_comment = True
            index += 2
            continue
        if char == "/" and next_char == "*":
            in_block_comment = True
            index += 2
            continue
        if build_gradle.startswith('"""', index):
            in_raw_string = True
            index += 3
            continue
        if char == '"':
            in_string = True
        index += 1
    return not (in_string or in_raw_string or in_line_comment or in_block_comment)


def gradle_without_comments(build_gradle: str) -> str:
    output: list[str] = []
    in_string = False
    in_raw_string = False
    in_line_comment = False
    in_block_comment = False
    escaped = False
    index = 0
    while index < len(build_gradle):
        char = build_gradle[index]
        next_char = build_gradle[index + 1] if index + 1 < len(build_gradle) else ""
        if in_line_comment:
            if char == "\n":
                in_line_comment = False
                output.append(char)
            index += 1
            continue
        if in_block_comment:
            if char == "*" and next_char == "/":
                in_block_comment = False
                index += 2
                continue
            if char == "\n":
                output.append(char)
            index += 1
            continue
        if in_raw_string:
            output.append(char)
            if build_gradle.startswith('"""', index):
                output.extend(['"', '"'])
                in_raw_string = False
                index += 3
                continue
            index += 1
            continue
        if in_string:
            output.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue
        if char == "/" and next_char == "/":
            in_line_comment = True
            index += 2
            continue
        if char == "/" and next_char == "*":
            in_block_comment = True
            index += 2
            continue
        output.append(char)
        if build_gradle.startswith('"""', index):
            output.extend(['"', '"'])
            in_raw_string = True
            index += 3
            continue
        if char == '"':
            in_string = True
        index += 1
    return "".join(output)


def gradle_statement_or_block(build_gradle: str, start: int) -> str:
    line_start = build_gradle.rfind("\n", 0, start) + 1
    paren_depth = 0
    brace_depth = 0
    bracket_depth = 0
    in_string = False
    in_raw_string = False
    in_line_comment = False
    in_block_comment = False
    escaped = False
    index = line_start
    while index < len(build_gradle):
        char = build_gradle[index]
        next_char = build_gradle[index + 1] if index + 1 < len(build_gradle) else ""
        if in_line_comment:
            if char == "\n":
                in_line_comment = False
                if index > start and paren_depth == 0 and brace_depth == 0 and bracket_depth == 0:
                    if gradle_should_continue_after_newline(build_gradle, line_start, index):
                        index += 1
                        continue
                    return build_gradle[line_start:index]
            index += 1
            continue
        if in_block_comment:
            if char == "*" and next_char == "/":
                in_block_comment = False
                index += 2
                continue
            index += 1
            continue
        if in_raw_string:
            if build_gradle.startswith('"""', index):
                in_raw_string = False
                index += 3
                continue
            index += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue
        if char == "/" and next_char == "/":
            in_line_comment = True
            index += 2
            continue
        if char == "/" and next_char == "*":
            in_block_comment = True
            index += 2
            continue
        if build_gradle.startswith('"""', index):
            in_raw_string = True
            index += 3
            continue
        if char == '"':
            in_string = True
        elif char == "(":
            paren_depth += 1
        elif char == ")":
            paren_depth = max(0, paren_depth - 1)
        elif char == "{":
            brace_depth += 1
        elif char == "}":
            brace_depth = max(0, brace_depth - 1)
        elif char == "[":
            bracket_depth += 1
        elif char == "]":
            bracket_depth = max(0, bracket_depth - 1)
        elif char == "\n" and index > start and paren_depth == 0 and brace_depth == 0 and bracket_depth == 0:
            if gradle_should_continue_after_newline(build_gradle, line_start, index):
                index += 1
                continue
            return build_gradle[line_start:index]
        index += 1
    return build_gradle[line_start:]


def gradle_should_continue_after_newline(build_gradle: str, line_start: int, newline: int) -> bool:
    continuation = gradle_next_expression_token_start(build_gradle, newline + 1)
    if continuation < len(build_gradle) and build_gradle[continuation] == ".":
        return True
    return gradle_line_code_before_comment(build_gradle, line_start, newline).rstrip().endswith("=")


def gradle_next_expression_token_start(build_gradle: str, start: int) -> int:
    index = start
    while index < len(build_gradle):
        while index < len(build_gradle) and build_gradle[index] in " \t\r\n":
            index += 1
        if build_gradle.startswith("//", index):
            newline = build_gradle.find("\n", index + 2)
            if newline == -1:
                return len(build_gradle)
            index = newline + 1
            continue
        if build_gradle.startswith("/*", index):
            end = build_gradle.find("*/", index + 2)
            if end == -1:
                return len(build_gradle)
            index = end + 2
            continue
        break
    return index


def gradle_line_code_before_comment(build_gradle: str, start: int, end: int) -> str:
    in_string = False
    in_raw_string = False
    escaped = False
    index = start
    while index < end:
        char = build_gradle[index]
        next_char = build_gradle[index + 1] if index + 1 < end else ""
        if in_raw_string:
            if build_gradle.startswith('"""', index):
                in_raw_string = False
                index += 3
                continue
            index += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue
        if build_gradle.startswith('"""', index):
            in_raw_string = True
            index += 3
            continue
        if char == '"':
            in_string = True
            index += 1
            continue
        if char == "/" and next_char in {"/", "*"}:
            return build_gradle[start:index]
        index += 1
    return build_gradle[start:end]


def write_summary(summary: dict[str, Any], output_dir: Path) -> Path:
    path = output_dir / "diagnostic-webgpu-first-route-pm-evidence-summary.json"
    path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 and argv[2] != "--write-summary" else root / DEFAULT_OUTPUT_DIR
    _, rows, summary = validate_output(output_dir)
    validate_root_pipeline_isolation(root)
    if "--write-summary" in argv:
        write_summary(summary, output_dir)
    print(
        "GPU renderer R6 executed PM evidence validation passed: "
        f"{len(rows)} dump artifacts, gatePassed={summary['promotionGatePassed']}, "
        f"status={summary['status']}, rootPipelinePmBundleDependency={summary['rootPipelinePmBundleDependency']}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
