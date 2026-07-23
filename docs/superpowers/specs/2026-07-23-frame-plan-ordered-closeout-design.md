# Frame Plan Ordered Closeout Design

## Purpose

Finish the `codex/graphite-dawn-frame-plan-design` branch through one active,
ordered backlog whose state is backed by current code and fresh validation
evidence.

The active backlog lives at
`reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`. Historical
plans, removed migration checklists, and archived ticket states remain evidence
only and are not reopened as acceptance criteria.

## Scope

The closeout covers, in order:

1. correct effective WebGPU uniform-buffer offset alignment on Windows;
2. integrate the current `origin/master`;
3. make the affected test suites portable on Windows;
4. move image operations to the prepared frame route;
5. move text operations to the prepared frame route;
6. move vertices and mesh operations to the prepared frame route;
7. move layers, filters, masks, pictures, and backdrop composites to the
   prepared frame route;
8. remove the superseded immediate renderer and CPU continuation paths;
9. expose a reusable prepared Surface session;
10. close explicitly bounded native-rendering gaps;
11. regenerate current visual and performance evidence.

Dependency-verification metadata, checksum maintenance, and reproducible-build
policy are explicitly out of scope. They must not block progress or be mixed
into commits from this closeout.

## Backlog authority

`active-todo.md` is the only branch-specific active backlog for this closeout.
Every work item has one of four states:

- `pending`: no implementation work is active;
- `in_progress`: the only item currently being implemented;
- `blocked`: progress requires an external change or user decision;
- `completed`: its stated acceptance evidence has been freshly produced.

At most one item may be `in_progress`. Items are processed in document order.
A later item may be investigated to understand an earlier failure, but its
implementation may not begin early.

Each item records:

- its bounded goal;
- the current evidence that makes it necessary;
- its acceptance commands and expected observable result;
- links to the resulting report or test evidence.

## Execution model

Each code change follows a red-green-refactor cycle:

1. add the smallest regression test for the current behavior;
2. run it and observe the expected failure;
3. implement the smallest production correction;
4. run the focused test and affected module suites;
5. inspect the diff and current backlog item;
6. commit only the files belonging to that item;
7. mark the item `completed` only after fresh evidence passes.

Failures discovered during validation are recorded under the current item when
they are caused by it. Independent failures become evidence on their already
ordered backlog item and do not silently broaden the current change.

## First work item: effective WebGPU uniform alignment

### Observed failure

On the Windows NVIDIA host, the runtime publishes
`minUniformBufferOffsetAlignment=64` from adapter limits. Prepared frame
planning then emits dynamic offsets such as `64`. The created WebGPU device
validates those offsets against a requirement of `256`, causing a native
non-unwinding panic.

The failure crosses three authorities:

1. wgpu4k exposes adapter capability data;
2. `GPUBackendRuntimeNative` converts that data into `GPULimits`;
3. uniform-slab planners treat `GPULimits.minUniformBufferOffsetAlignment` as
   the exact device alignment used for command encoding.

### Design

The runtime must publish an effective device-safe alignment rather than a
blind copy of the adapter value.

Investigation first determines whether the public wgpu4k facade exposes the
created device's actual limits or permits the requested device limits to be
set explicitly:

- when actual device limits are available, use that value;
- when device creation is fixed to WebGPU default limits and no actual-device
  query exists, normalize the effective alignment to
  `max(adapterReportedAlignment, 256)`;
- reject non-positive or non-power-of-two values before they reach frame
  planning.

The normalization belongs at the native runtime capability boundary. Slab
planners, preflight, and encoders continue consuming one exact
`GPULimits.minUniformBufferOffsetAlignment` authority and do not gain
platform-specific branches.

Over-aligning a device that could accept `64` remains correct and only consumes
additional uniform-buffer padding. Under-aligning a device that requires `256`
is invalid and must be prevented.

### Tests

The regression coverage must prove:

- adapter `64`, device/default `256` produces effective `256`;
- adapter `256` produces effective `256`;
- an alignment stricter than `256` is preserved;
- invalid alignments are refused;
- the native capability smoke observes the same effective value used by slab
  planning;
- a prepared multi-packet frame no longer submits a dynamic offset of `64` on
  the Windows host.

Focused validation runs before the broader renderer and Kanvas suites.

## Integration and portability

After the alignment item is complete, the branch integrates the four current
`origin/master` commits without dropping user changes. Conflicts are resolved
against the active renderer targets and revalidated before Windows-specific
test portability work begins.

Windows portability changes are behavior-neutral:

- Gradle subprocess tests select `gradlew.bat` on Windows and `./gradlew`
  elsewhere;
- textual golden comparisons normalize line endings without rewriting
  semantic content;
- pixel assertions distinguish exact-channel requirements from explicitly
  accepted one-LSB UNORM conversion tolerance.

## Prepared-route migration

The four remaining legacy families move separately in this fixed order:
images, text, vertices/meshes, then composites. A family is complete only when
its Surface admission gate, normalized commands, prepared resources, native
execution, diagnostics, and pixel evidence all use the common frame route.

The legacy allowlist must shrink after every family migration. The immediate
adapter is deleted only after the final family has left it. Low-level native
recording primitives may remain when they require the active prepared encoder;
high-level immediate dispatch, CPU destination snapshots, and duplicate route
classification may not.

## Reusable session and final evidence

Once legacy retirement is complete, Surface execution gains an explicitly
owned reusable prepared session. Backend, target, and invariant caches survive
across compatible frames and close deterministically on generation, size,
format, or owner changes.

Native breadth work then addresses only gaps explicitly recorded by the active
backlog. Final visual and performance evidence runs last, after integration and
route retirement, so it measures the actual candidate rather than an
intermediate architecture.

## Completion

The closeout is complete only when every active backlog item is `completed`,
the legacy allowlist and its invocation count are absent, the affected test
suites pass on the Windows host, current GM evidence is regenerated, and the
performance report states its measured and non-claimed lanes explicitly.
