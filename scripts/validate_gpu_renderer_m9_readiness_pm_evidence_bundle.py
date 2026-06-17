#!/usr/bin/env python3
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "gpu-renderer/build/reports/gpu-renderer-m9-readiness-pm-evidence"
RELEASE_DIR = "release/gpu-renderer-m9-readiness-pm-evidence"
DASHBOARD_LINES_ARTIFACT = "gpu-renderer-readiness-dashboard-lines.txt"
SUMMARY_ARTIFACT = "gpu-renderer-readiness-dashboard-summary.json"
OUTPUT_MANIFEST_ENTRY = "pm-bundle-manifest-entry.json"
EXPECTED_ROW_ORDER = ["correctness", "activation", "performance", "cache", "release"]
EXPECTED_NON_CLAIMS = [
    "No readiness delta.",
    "No release-blocking gate.",
    "No product activation.",
    "No correctness support inferred from performance evidence.",
    "No derived cache telemetry counted as observed.",
    "No dashboard row promotes readiness.",
]
EXPECTED_ENTRY_FIELDS = {
    "key",
    "claimLevel",
    "status",
    "classification",
    "dashboardId",
    "evidenceRow",
    "artifactDirectory",
    "summaryArtifact",
    "dashboardLinesArtifact",
    "manifestEntryJson",
    "generationCommand",
    "pmPackageCommand",
    "productRouteActivated",
    "releaseBlocking",
    "readinessDelta",
    "webGpuAdapterRequired",
    "nativeKadreCiRequired",
    "dashboardRows",
    "nonClaims",
    "notice",
}
READINESS_TOKEN_RE = re.compile(r"(?<![A-Za-z0-9_])readinessDelta=([^\s]+)")
BOOLEAN_TOKEN_RE = re.compile(r"(?<![A-Za-z0-9_])(?P<key>releaseBlocking|productRouteActivated)=(?P<value>[^\s]+)")


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"GPU renderer M9 readiness PM evidence validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def reject_duplicate_json_object_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    payload: dict[str, Any] = {}
    for key, value in pairs:
        require(key not in payload, f"duplicate JSON key: {key}")
        payload[key] = value
    return payload


def load_json_object(path: Path, owner: str) -> dict[str, Any]:
    require(path.is_file(), f"missing {owner}: {path}")
    try:
        payload = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=reject_duplicate_json_object_pairs,
        )
    except json.JSONDecodeError as exc:
        fail(f"{owner} is not valid JSON: {exc}")
    require(isinstance(payload, dict), f"{owner} must be a JSON object")
    return payload


def sha256_file(path: Path) -> str:
    return "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()


def require_exact_fields(payload: dict[str, Any], expected_fields: set[str], owner: str) -> None:
    actual_fields = set(payload.keys())
    unexpected = sorted(actual_fields - expected_fields)
    missing = sorted(expected_fields - actual_fields)
    require(not unexpected, f"{owner} has unexpected fields: {', '.join(unexpected)}")
    require(not missing, f"{owner} missing fields: {', '.join(missing)}")


def require_zero_movement(payload: dict[str, Any], owner: str) -> None:
    require(payload.get("readinessDelta") == 0.0, f"{owner}.readinessDelta must remain 0.0")
    require(payload.get("releaseBlocking") is False, f"{owner}.releaseBlocking must remain false")
    require(payload.get("productRouteActivated") is False, f"{owner}.productRouteActivated must remain false")


def validate_summary(output_dir: Path) -> dict[str, Any]:
    summary = load_json_object(output_dir / SUMMARY_ARTIFACT, "readiness dashboard summary")
    require(summary.get("dashboardId") == "m9-gpu-renderer-readiness", "unexpected dashboardId")
    require(summary.get("evidenceRow") == "gpu-renderer.readiness", "unexpected evidenceRow")
    require(summary.get("classification") == "PolicyGated", "summary classification must be PolicyGated")
    require_zero_movement(summary, "summary")
    rows = summary.get("rows")
    require(isinstance(rows, list), "summary.rows must be a list")
    require(len(rows) == len(EXPECTED_ROW_ORDER), "summary.rows must include five readiness areas")
    row_areas = []
    for row in rows:
        require(isinstance(row, dict), "summary row must be a JSON object")
        row_areas.append(row.get("area"))
        require(row.get("state"), "summary row state must not be blank")
        require(row.get("source"), "summary row source must not be blank")
    require(row_areas == EXPECTED_ROW_ORDER, f"summary row order must be {EXPECTED_ROW_ORDER}")
    require(summary.get("nonClaims") == EXPECTED_NON_CLAIMS, "summary nonClaims changed")
    artifacts = summary.get("artifacts")
    require(isinstance(artifacts, dict), "summary.artifacts must be a JSON object")
    lines_path = output_dir / DASHBOARD_LINES_ARTIFACT
    require(lines_path.is_file(), f"missing dashboard lines artifact: {lines_path}")
    require(
        artifacts.get(DASHBOARD_LINES_ARTIFACT) == sha256_file(lines_path),
        "dashboard lines artifact hash mismatch",
    )
    lines = lines_path.read_text(encoding="utf-8").splitlines()
    require(lines, "dashboard lines must not be empty")
    require(
        any(line.startswith("pm:gpu-renderer.readiness classification=PolicyGated") for line in lines),
        "dashboard lines missing PM readiness row",
    )
    validate_dashboard_line_claims(lines)
    return summary


def validate_dashboard_line_claims(lines: list[str]) -> None:
    for line in lines:
        for match in READINESS_TOKEN_RE.finditer(line):
            require(
                match.group(1) == "0.0",
                f"dashboard line readinessDelta must remain 0.0: {line}",
            )
        for match in BOOLEAN_TOKEN_RE.finditer(line):
            require(
                match.group("value") == "false",
                f"dashboard line {match.group('key')} must remain false: {line}",
            )
        require(
            "no-dashboard-promotion=false" not in line,
            f"dashboard lines contain forbidden claim: no-dashboard-promotion=false",
        )


def build_manifest_entry(summary: dict[str, Any]) -> dict[str, Any]:
    return {
        "key": "gpuRendererM9ReadinessPmEvidence",
        "claimLevel": "gpu-renderer-m9-readiness-dashboard",
        "status": "PolicyGated",
        "classification": "PolicyGated",
        "dashboardId": summary["dashboardId"],
        "evidenceRow": summary["evidenceRow"],
        "artifactDirectory": RELEASE_DIR,
        "summaryArtifact": f"{RELEASE_DIR}/{SUMMARY_ARTIFACT}",
        "dashboardLinesArtifact": f"{RELEASE_DIR}/{DASHBOARD_LINES_ARTIFACT}",
        "manifestEntryJson": f"{RELEASE_DIR}/{OUTPUT_MANIFEST_ENTRY}",
        "generationCommand": "rtk ./gradlew --no-daemon :gpu-renderer:gpuRendererM9ReadinessPmEvidenceBundle",
        "pmPackageCommand": "rtk ./gradlew --no-daemon pipelinePmBundle",
        "productRouteActivated": False,
        "releaseBlocking": False,
        "readinessDelta": 0.0,
        "webGpuAdapterRequired": False,
        "nativeKadreCiRequired": False,
        "dashboardRows": EXPECTED_ROW_ORDER,
        "nonClaims": EXPECTED_NON_CLAIMS,
        "notice": (
            "GPU renderer M9 readiness dashboard integration separates correctness, activation, "
            "performance, cache, and release visibility without moving readiness."
        ),
    }


def validate_entry(entry: dict[str, Any], summary: dict[str, Any]) -> None:
    require_exact_fields(entry, EXPECTED_ENTRY_FIELDS, "manifest entry")
    require(entry.get("key") == "gpuRendererM9ReadinessPmEvidence", "manifest entry key changed")
    require(entry.get("status") == "PolicyGated", "manifest entry status must be PolicyGated")
    require(entry.get("classification") == "PolicyGated", "manifest entry classification must be PolicyGated")
    require(entry.get("dashboardId") == summary["dashboardId"], "manifest entry dashboardId mismatch")
    require(entry.get("evidenceRow") == summary["evidenceRow"], "manifest entry evidenceRow mismatch")
    require_zero_movement(entry, "manifest entry")
    require(entry.get("webGpuAdapterRequired") is False, "manifest entry.webGpuAdapterRequired must remain false")
    require(entry.get("nativeKadreCiRequired") is False, "manifest entry.nativeKadreCiRequired must remain false")
    require(entry.get("dashboardRows") == EXPECTED_ROW_ORDER, "manifest entry dashboardRows changed")
    require(entry.get("nonClaims") == EXPECTED_NON_CLAIMS, "manifest entry nonClaims changed")


def validate_manifest_entry_sidecar(output_dir: Path, entry: dict[str, Any], summary: dict[str, Any]) -> None:
    sidecar_path = output_dir / OUTPUT_MANIFEST_ENTRY
    if not sidecar_path.is_file():
        return
    sidecar = load_json_object(sidecar_path, "manifest entry sidecar")
    validate_entry(sidecar, summary)
    require(
        json.dumps(sidecar, sort_keys=True, separators=(",", ":")) ==
        json.dumps(entry, sort_keys=True, separators=(",", ":")),
        "manifest entry sidecar disagrees with rebuilt entry",
    )


def validate_output(output_dir: Path) -> tuple[dict[str, Any], dict[str, Any]]:
    summary = validate_summary(output_dir)
    entry = build_manifest_entry(summary)
    validate_entry(entry, summary)
    validate_manifest_entry_sidecar(output_dir, entry, summary)
    expected_files = {DASHBOARD_LINES_ARTIFACT, SUMMARY_ARTIFACT, OUTPUT_MANIFEST_ENTRY}
    actual_files = {
        str(path.relative_to(output_dir)).replace("\\", "/")
        for path in output_dir.rglob("*")
        if path.is_file()
    }
    require(actual_files.issubset(expected_files), f"unexpected files: {sorted(actual_files - expected_files)}")
    return summary, entry


def inject_pm_bundle(output_dir: Path, bundle_dir: Path, entry: dict[str, Any]) -> None:
    manifest_path = bundle_dir / "manifest.json"
    require(manifest_path.is_file(), f"missing PM bundle manifest: {manifest_path}")
    manifest = load_json_object(manifest_path, "PM bundle manifest")
    require(manifest.get("generatedBy") == "pipelinePmBundle", "target manifest must be generated by pipelinePmBundle")

    target_dir = bundle_dir / RELEASE_DIR
    if target_dir.exists():
        shutil.rmtree(target_dir)
    shutil.copytree(output_dir, target_dir)
    (target_dir / OUTPUT_MANIFEST_ENTRY).write_text(
        json.dumps(entry, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    manifest.pop(entry["key"], None)
    manifest[entry["key"]] = entry
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    readme_path = bundle_dir / "README.md"
    if readme_path.is_file():
        readme = readme_path.read_text(encoding="utf-8")
        marker = "GPU renderer M9 readiness PM evidence"
        if marker not in readme:
            readme += (
                "\n"
                "- GPU renderer M9 readiness PM evidence lives in `manifest.json` under "
                "`gpuRendererM9ReadinessPmEvidence` and in "
                "`release/gpu-renderer-m9-readiness-pm-evidence/`; it is `PolicyGated`, "
                "reporting-only, and does not move readiness.\n"
            )
            readme_path.write_text(readme, encoding="utf-8")


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 and argv[2] != "--inject-pm-bundle" else root / DEFAULT_OUTPUT_DIR
    _, entry = validate_output(output_dir)
    if "--inject-pm-bundle" in argv:
        bundle_arg_index = argv.index("--inject-pm-bundle") + 1
        require(bundle_arg_index < len(argv), "--inject-pm-bundle requires bundle directory")
        inject_pm_bundle(output_dir, Path(argv[bundle_arg_index]).resolve(), entry)
    print(
        "GPU renderer M9 readiness PM evidence validation passed: "
        f"status={entry['status']}, readinessDelta={entry['readinessDelta']}, releaseBlocking={entry['releaseBlocking']}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
