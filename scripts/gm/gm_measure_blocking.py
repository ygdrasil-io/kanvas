#!/usr/bin/env python3
"""Aggregate deterministic three-attempt GM render-cost measurements."""

import argparse
import json
import os
import platform
import subprocess
from pathlib import Path


SCHEMA_VERSION = 1


def _parse_attempt(attempt):
    raw = attempt["record"] if isinstance(attempt, dict) else attempt
    if not isinstance(raw, str):
        raise ValueError("attempt record must be a string")
    parts = raw.split("|", 3)
    if len(parts) < 3:
        raise ValueError("invalid attempt record: %r" % raw)
    kind, index, name = parts[:3]
    try:
        index = int(index)
    except ValueError as error:
        raise ValueError("invalid attempt index in %r" % raw) from error
    if kind == "PASS":
        if len(parts) != 4:
            raise ValueError("PASS record requires elapsedMs: %r" % raw)
        try:
            elapsed_ms = float(parts[3])
        except ValueError as error:
            raise ValueError("invalid elapsedMs in %r" % raw) from error
        return {"kind": kind, "index": index, "name": name, "elapsedMs": elapsed_ms, "raw": raw}
    if kind in {"FAIL", "TIMEOUT"}:
        detail = parts[3] if len(parts) == 4 else ""
        return {"kind": kind, "index": index, "name": name, "detail": detail, "raw": raw}
    raise ValueError("unsupported attempt record: %r" % raw)


def _median(samples):
    ordered = sorted(samples)
    middle = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[middle]
    return (ordered[middle - 1] + ordered[middle]) / 2


def _number(value):
    return int(value) if value == int(value) else value


def classify_attempts(attempts):
    """Classify exactly three raw scanner attempt records."""
    if len(attempts) != 3:
        raise ValueError("expected exactly three attempts")
    parsed = [_parse_attempt(attempt) for attempt in attempts]
    timeouts = [attempt for attempt in parsed if attempt["kind"] == "TIMEOUT"]
    failures = [attempt for attempt in parsed if attempt["kind"] == "FAIL"]
    samples = [attempt["elapsedMs"] for attempt in parsed if attempt["kind"] == "PASS"]

    result = {
        "tag": None,
        "medianMs": None,
        "timeoutCount": len(timeouts),
        "errorCount": len(failures),
        "classificationReason": None,
    }
    if failures:
        details = sorted(filter(None, (attempt["detail"] for attempt in failures)))
        result.update(
            tag="BLOCKING",
            classificationReason="incomplete-or-error: " + (", ".join(details) or "render failure"),
        )
        return result
    if len(timeouts) >= 2:
        result.update(tag="BLOCKING", classificationReason="two or more attempts timed out")
        return result
    if len(samples) < 2:
        result.update(tag="INCOMPLETE_OR_ERROR", classificationReason="fewer than two completed samples")
        return result

    median_ms = _number(_median(samples))
    if median_ms < 1000:
        tag, reason = "FAST", "median below 1000 ms"
    elif median_ms < 5000:
        tag, reason = "MEDIUM", "median below 5000 ms"
    elif median_ms < 10000:
        tag, reason = "SLOW", "median below 10000 ms"
    else:
        tag, reason = "BLOCKING", "median at or above 10000 ms"
    result.update(tag=tag, medianMs=median_ms, classificationReason=reason)
    return result


def build_report(attempts_by_name, timed_out_batches, provenance):
    """Build a sorted report retaining all records required for rebaseline review."""
    rows = []
    for name in sorted(attempts_by_name):
        attempts = attempts_by_name[name]
        row = {"name": name, "rawSamples": [
            attempt["record"] if isinstance(attempt, dict) else attempt for attempt in attempts
        ]}
        row.update(classify_attempts(attempts))
        rows.append(row)
    return {
        "provenance": {key: provenance[key] for key in sorted(provenance)},
        "rows": rows,
        "schemaVersion": SCHEMA_VERSION,
        "timedOutBatches": [sorted(batch) for batch in sorted(timed_out_batches)],
    }


def to_sorted_json(report):
    return json.dumps(report, indent=2, sort_keys=True) + "\n"


def to_markdown(report):
    lines = ["# GM render-cost measurement", "", "## Provenance", ""]
    for key, value in report["provenance"].items():
        lines.append("- %s: `%s`" % (key, value))
    lines.extend(["", "## Results", "", "| Name | Tag | Median (ms) | Timeouts | Errors | Reason |", "| --- | --- | ---: | ---: | ---: | --- |"])
    for row in report["rows"]:
        median = "" if row["medianMs"] is None else str(row["medianMs"])
        lines.append("| `%s` | %s | %s | %s | %s | %s |" % (
            row["name"], row["tag"], median, row["timeoutCount"], row["errorCount"], row["classificationReason"]
        ))
        lines.append("|  |  |  |  |  | Raw samples: %s |" % ", ".join("`%s`" % sample for sample in row["rawSamples"]))
    lines.extend(["", "## Timed-out batches", ""])
    if report["timedOutBatches"]:
        lines.extend("- %s" % ", ".join("`%s`" % name for name in batch) for batch in report["timedOutBatches"])
    else:
        lines.append("- None")
    return "\n".join(lines) + "\n"


def _git_head():
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def _default_provenance(backend):
    return {
        "backend": backend,
        "gitHead": _git_head(),
        "jdk": os.environ.get("JAVA_VERSION", "unknown"),
        "os": "%s %s" % (platform.system(), platform.release()),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="JSON mapping GM names to three attempt records")
    parser.add_argument("--json-out", type=Path, required=True, help="destination for sorted JSON")
    parser.add_argument("--markdown-out", type=Path, required=True, help="destination for Markdown report")
    parser.add_argument("--backend", default="unknown", help="renderer backend used for measurement")
    args = parser.parse_args()
    input_data = json.loads(args.input.read_text())
    report = build_report(
        input_data["attempts"],
        input_data.get("timedOutBatches", []),
        {**_default_provenance(args.backend), **input_data.get("provenance", {})},
    )
    args.json_out.write_text(to_sorted_json(report))
    args.markdown_out.write_text(to_markdown(report))


if __name__ == "__main__":
    main()
