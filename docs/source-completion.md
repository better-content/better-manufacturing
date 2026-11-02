# Historical source-completion contract

**Scope notice:** this documents the superseded chemistry/recovery program and its existing broad gates.
The active implementation is the part-based machine conversion described in `../README.md`.
Do not expand this evidence protocol or make it a prerequisite to machine conversions; do not weaken its
existing gates or interpret old provenance/retooling receipts as acceptance of the new baked-settings behavior.


The authorized target is source implementation and owner-local verification, not combined-pack acceptance.

## Separate gates

- `verifyCoverage` checks the selected roster structure.
- `verifyImplementationComplete` runs fast/native verification plus the source/local ledger check.
- `verifyCoverageComplete` and `verifyFull` retain their existing full-acceptance meaning.

The new pack-owned `kubejs/data/better_manufacturing/implementation.json` has independent implementation,
owner-local and pack-acceptance states. Its initial pending entries deliberately grant no credit, including
to the partially implemented Drill. It also tracks profiles, transfers, process transactions, both polymer
chains, shaping, recovery, retirement, bootstrap, propylene identity and all six Ponder scenes. Finishing
machine records alone cannot satisfy this gate.

A completed source record requires an exact mechanism contract (roles, quantities, reference, affected
properties, accounting hook and applicable lifecycle paths), hashed source audits for registration,
behavior, acquisition analysis, policy and reference behavior, implementation/teaching files, and native
receipts covering the required cases. Recipe-only records have different obligations, not invented effects.

Only the five explicitly authorized identity deferrals are automatic exclusions. Other exclusions require
an approved exclusion family and hashed registration/policy evidence. The CLI also rechecks frozen upstream
artifact hashes against current Packwiz pins and local reference archives; it performs no downloads.

All file bindings are workspace-relative `{path, sha256}` objects. An owner-local receipt uses
`better_manufacturing.owner_local.v1`, names one subject and its upstream SHA-256, and binds an execution
file plus current source fingerprints under `mod_source`. The execution binds compiled files, dependencies
and logs, declares `forge_userdev` or `owner_local_runtime_jar`, and lists `subject_cases`. Every declared
case must appear as a JSON log event after `MANUFACTURING_CASE_PASS `. A native execution must complete
all required GameTests and shut down normally; a client execution must emit `MANUFACTURING_CLIENT_COMPLETE`
and stop normally. Teaching additionally requires hashed images and a bounded subject-specific review.
Feature receipts bind the SHA-256 of the sorted JSON coverage `sources` map in place of a single upstream.
None of these records may promote pack acceptance.

Existing core/client fixture receipts remain useful evidence at their stated scope. They do not already
satisfy this expanded subject/case protocol. No adapter or feature has been promoted through the new gate.

## Build schema 2 and access identities

`MachineBuild` now includes at most 16 recipe-issued qualification IDs. They are identities, not numerical
bonuses. Required qualifications block a specialized function; explicit per-axis access ceilings prevent a
stronger material from bypassing a progression input. Multiple ceilings compose by their minimum. Unknown
qualifications block evaluation. Stock builds cannot carry qualifications and retain native baseline values.
Retooling preserves qualifications.

NBT schema 2 requires the qualification list. Valid schema-1 Create Drill records with the original cutting
function are read losslessly into the current in-memory representation with no qualifications. Reading does
not rewrite the holder. Other legacy chassis, extra legacy fields, duplicate qualifications, malformed types
and unsupported future versions reject without destroying data. Schematic matching and EMI use decoded build
identity, so equivalent valid legacy/current records match without inventing a head or repairing metadata.
This is not a general world-migration facility.

## Explicit transfers and crafting

`TransferPlan` names each source slot/role and destination role. Each source component is accounted for once;
unmapped components, lost qualifications, duplicate sources and overwritten destinations reject. Clean
material credit divides exactly across a bounded batch; stateful parts cannot split. Inherited qualifications
cannot fan out across multiple outputs. Only explicit recipe grants may issue new qualifications.

The shaped serializer now uses this plan and serializes its bounded `qualifications` list. The new
`better_manufacturing:material_shapeless` serializer binds `transfers[].ingredient` to the declared ingredient
index, not to a guessed Tinkers part found in the grid. A bounded bipartite-matching solver checks valid
assignments. If different assignments yield different provenance for a bound role, crafting rejects; use a
shaped recipe for that distinction. Equivalent permutations are accepted. Preview, actual assembly and
network reconstruction use the same result calculation. No production recipes have yet been moved to the
new shapeless serializer. Shaped subassemblies can declare `source_role` and `source_function` on each
transfer, plus explicit `qualification_transfers` (`slot`, `qualification`). Multiple roles can come from
one declared source slot, but every source role/qualification must be accounted for exactly once. The
source chassis is checked against the actual registry item, and only the build tag/empty capabilities are
accepted on subassemblies. Unknown holder metadata is preserved by rejecting the craft. Native fixtures
exercise mirrored intermediate crafting and networking. Native processing, sequenced assembly and other
lifecycle adapters still need their own integration; the new serializer does not complete those routes.

## Reloadable material policies and initial Sifter

Pack files under `engineering/materials` use strict schema 1: `material`, `supported_parts`, `native_stats`
(`base_material` or `none`), `traits` and `compatible_processes`. Limits are 1,024 policies and 262,144 UTF-8
bytes per synchronized snapshot. Duplicate fields/materials, invalid numerical values, excessive nesting,
invalid UTF-8 and syntax errors fail the reload rather than silently removing a restriction. Prepared data
is published at successful server/global datapack synchronization, not player joins. Client state resets on
disconnect. Immutable build results use bounded epoch caches; live conditions are not cached.

Wood/iron policies author `screen_rate` and narrow `better_manufacturing:sifting/water` service. Frame rate
uses the live authored iron reference, not head mining speed or an invented material quality score. Water
eligibility is the intersection of all four frame sides. Native waterlogging is checked at operation time.
The catalog does not yet provide the entire planned machine-profile catalog or specialist material roster.

The initial Sifter adapter uses the existing installed mesh inventory. It adds no second inventory, item
identity or maintenance system. Source/native/client evidence now covers the boundaries listed in the
README, but full family lifecycle/variant evidence and the expanded subject/case receipt protocol remain
pending. The advanced brass mesh's pre-existing closed acquisition route remains closed; its finding is
recorded in `sifter-source-audit.md`, without silently changing the selected ledger or awarding completion.

## Vacuum conditions

`ProcessEnvelope` can require a named native condition source plus a separate `VacuumRequirement`.
The owner must report the actual connected/operating pump, recovery-trap connection and negative native
gauge-pressure reading. A negative machine reading alone, a positive pressure minimum, an idle/disconnected
pump, a missing required trap or a nonfinite reading cannot satisfy that requirement. This does not add a
pressure simulation to Create or implement a PneumaticCraft vacuum adapter by itself. Persistent batch
transactions and actual pump/trap process integration remain work to do.
