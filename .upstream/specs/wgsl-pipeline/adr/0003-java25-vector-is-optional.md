# ADR 0003: Java 25 Vector API Is Optional

Status: Accepted

## Context

Java 25 provides `jdk.incubator.vector`, which can accelerate common CPU
pipeline kernels. The module can be unavailable, disabled, or slower on a
specific machine and workload.

## Decision

Treat Vector API kernels as optional acceleration. Scalar kernels remain the
correctness baseline and are always available.

Vector code must live behind an isolated JVM-specific boundary. Selection must
report whether vector code was used or why scalar fallback was selected.

## Consequences

- Tests can run without Vector API support.
- Performance claims require measured scalar and vector results.
- Hot loops can use vector lanes without leaking incubator classes across the
  project.
