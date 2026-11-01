# Better Manufacturing — implementation in progress

Forge 1.20.1 / Java 17. Source owner for the approved full-roster chemistry → material →
part → machine → recovery implementation. **This is not a completed gameplay feature.**
No runtime JAR has been deployed. No pack recipes or generic-plastic acquisition routes
have been replaced. Adding this provider to the active-source inventory is not acceptance
or permission to include an incomplete feature in a release.

## Implemented and locally exercised

- Immutable material, machine, process-envelope and adapter contracts; role-specific,
  reference-normalized evaluation with caps, explicit properties and no generic quality
  fallback. Stock builds have no refundable provenance.
- Versioned `better_manufacturing:build` NBT with bounded roles/metadata, strict field
  types, preservation of malformed/future input, chassis validation and no trusted
  performance numbers. Live pressure/heat is not cached with material profiles.
- Native Tinkers head/base and handle/additive-modifier stat reads. No invented
  pressure, insulation, chemistry or mining-tier-to-quality conversion.
- `better_manufacturing:material_shaped`, retaining the vanilla crafting recipe type.
  Transfers explicitly name normalized pattern slots and distinct component roles.
  Matching includes vanilla shifts/mirroring. Preview and assembly call the same pure
  computation; networking preserves those declarations. Each current role consumes one
  native part. Batch costs are divided exactly, and stateful parts cannot be split.
- Explicit native part costs, checked against pinned Tinkers Part Builder recipes.
  Reviewed original part metadata (including Jackhammer wear and affix provenance) is
  retained, not interpreted as an extra machine bonus. Unknown metadata/capabilities
  reject crafting without altering the source. Nested NBT is bounded before copying.
- Seven component item identities; no blocks. **No polymer fluids, Tinkers polymer
  definitions, acquisition recipes, models or production chains yet.**
- A pack-owned candidate manifest with 467 identities and archive hashes/base recipe
  IDs. Asset evidence is not a runtime registry/acquisition audit. All entries are
  pending; five lack asset evidence and several recipe-only families remain unexpanded.

## Verification

From this repository (serialize heavyweight Gradle work using the workspace lock):

```sh
flock /home/dev/.tmp/better-content-gradle.lock ./gradlew --offline --max-workers=2 verifyFast verifyNative stageRuntimeJar
./gradlew verifyCoverageComplete
```

`verifyFast`: 22 JUnit contract tests, 9 Python build/acceptance-gate tests, and structural
manifest validation. `verifyNative`: 9 Forge/Tinkers GameTests in a fresh UUID fixture.
They exercise NBT round trips and malformed records, stock/chassis behavior, the item
budget, shifted/mirrored assembly, batch attribution, retained wear, rejection of
unrelated/deep metadata, serializer networking, native stat roles and pinned part costs.

Each successful native run writes `build/gametest/<uuid>/execution.json`, binding logs,
compiled classes, resources, production inputs and runtime dependencies. This records a
**userdev core fixture**, not a staged-JAR machine-adapter or visual acceptance claim.
`verifyFull` deliberately also requires complete manifest acceptance and currently
**fails**. Do not replace it with `verifyFast` in release qualification.

`stageRuntimeJar` writes the reobfuscated artifact to
`build/libs/better-manufacturing-0.1.0.jar`. It does not deploy anything, refresh Packwiz,
package a release or authorize Smoke/Soak. Source identity metadata covers dirty inputs;
without a committed HEAD it explicitly reports a source snapshot.

## Remaining required work

The approved plan remains in force; later milestones are not optional:

1. Finish native registry/acquisition/reference audits, explicit transfer/invariant
   manifests, exclusions and recipe-only family expansion. Resolve stale Backpacks
   hopper and Clockwork candidate identities without silently substituting features.
2. Complete intermediate/shapeless/processing/sequenced transfers, safe role-specific
   retooling/recovery, machine lifecycle persistence, evaluator data/reload/sync and EMI.
   Demonstrate the full loop on Drill, Sifter, contained Basin and Beetle brakes; wire
   existing owners and their provider dependencies, preserving Jackhammer ownership.
3. Add both polymer chains, fluids, actual process/catalyst/vacuum/byproduct support,
   material definitions/shaping, gas identity, containment, generic-plastic retirement,
   conservation/bootstrap tests and teaching.
4. Complete workshop, pressure, heat and electricity adapters and recipe-only neighbors.
5. Complete biology, logistics, quarry, Little Logistics and Beetle coverage.
6. Complete Trackwork, Clockwork, Eureka, remaining recipes and every adapter's native
   lifecycle/accounting/performance evidence. Perform separately authorized real-client
   gameplay/UI review; headless tests are not visual proof.

No selected machine has been marked implemented or verified by these core fixtures.
Manufacturing must not depend back on custom-mod API consumers. Rail Beetle, Industrial
Heat, Airtight Machines, ChemLib Hazards, Compatibility Fixes and contextual learning
owners retain their existing domains. The pack owns progression and acquisition policy.
