# Historical Sifter source audit — superseded implementation

**Current behavior is described in `../README.md`.** Meshes now store crafting-time settings, not parts.
The capacity reservation, separate water qualification and exact-part retooling described below were removed
from the active path. The old receipts are historical, not evidence for those behaviors in the current code.


## Pinned native authority

- Pack pin: `better-content-modpack/mods/create-sifting.pw.toml`.
- Archive: `createsifter-1.20.1-1.8.6-6.0.6.jar`, Modrinth version `GlQRvUIe`.
- SHA-512: `5f1c795a1fd6f95dbeda490ddffc7cc8385908f19387dfab2d6919c582c9d18c6862d52fc893e6390cde3b5d7fba279ff223b4022df4a498783a7a2bedf74a53`.
- `scripts/sifter_fixture_identity.py` checks the public archive against the pin before compilation and
  records hashes of five inspected hook classes. Native/client receipts also hash the actual mapped runtime
  dependency. This is Forge userdev evidence, not execution of the staged production JAR.

Inspected native classes are `SifterBlockEntity`, its mesh handler `$1`, `SifterBlock`, `SiftingRecipe`, and
`brasss_sifter.BrassSifterBlockEntity` under `com.oierbravo.createsifter.content.contraptions.components`.
Native `meshInv` is an actual ItemStackHandler: insertion copies one stack, save/read serialize it, removal
returns it through the player inventory, and block removal drops native inventory contents. The handler's
`onContentsChanged` supplies the automation dirty event. Manufacturing does not replace these inventories.

## Implemented contract

- Meshes: string, andesite, zinc and brass have new conditional pack recipes. Four whole native
  `tconstruct:tool_handle` parts form `north/east/south/west`; each is 90 quanta. The mesh center retains its
  original string/andesite/zinc/brass ingredient and native functional identity. Original recipes remain the
  conditional fallback when Manufacturing is absent.
- Stock native machines/meshes keep native behavior and gain no refundable parts.
- Authored gameplay `screen_rate`, relative to the live iron policy, controls the weakest side's bounded
  0.75–1.5× throughput. Native rotational stress receives the same multiplier. Head/handle mining stats do
  not supply this property. Every side needs an explicit water-service qualification for waterlogged work.
- Native tick gates, speed requirements, mesh matching, wet/dry recipes, per-cycle counts and RNG rolls are
  retained. Brass output filtering intentionally retains native filtered-output void semantics.
- Native `tryToInsertOutputItem` discards an uninserted remainder. For attributed meshes, the adapter first
  simulates the full possible output vector against a copied native output inventory. Insufficient capacity
  pauses work before rolling/debiting input. It never rerolls a blocked assay. Brass filtering is accounted
  for before the capacity test. This conservative reservation may wait for more space than a lucky roll
  would need; it does not invent extra capacity or yield.
- Native mesh mutations and catalog/native-material epochs invalidate evaluation. Fractional work is saved
  independently from immutable engineering results. Saved transient recipes are rebound before resumed
  work; changed native recipe objects invalidate pending timing without consuming input.
- Grid retooling uses the extracted mesh at the center and four replacement handles around it. All four
  original whole parts return via vanilla crafting remainders; reviewed wear/affixes remain exact.
  Unsupported holder inventories/capabilities reject unchanged. No free bare frame or monomer recovery.
- Tooltips/goggles and EMI use the same evaluated state. Assembly and remainder examples run the actual
  serializers; material-bearing outputs are searchable. Synthetic IDs are explicitly synthetic.

## Source-policy findings — not silent scope changes

1. `kubejs/server_scripts/processing/ore_sifting_and_spouting.js` already removes every recipe outputting
   `createsifter:advanced_brass_mesh`, `custom_mesh` and `advanced_custom_mesh`. It also removes bundled
   Sifter processing recipes before installing curated geological routes. No new advanced-mesh recipe or
   EMI acquisition page was added. The coverage manifest still selects advanced brass mesh; its acquisition
   discrepancy needs the evidence-backed ledger reconciliation required by the plan. It has not been
   substituted, silently excluded or credited as complete.
2. `kubejs/data/tconstruct/tinkering/materials/stats/wood.json` does not author native head stats. The isolated
   Drill fixture's upstream wood head must not be advertised as full-pack survival acquisition. Frame handles
   have native handle stats; authoring a wood engineering policy does not create missing native head stats.
3. The four new mesh recipes do not alter the pack's geological assay scripts or reopen bundled processing.
   The deterministic cactus recipes live only in `src/createFixture/resources`, not the production JAR or
   pack acquisition data.

## Actual evidence and remaining work

The current native population is 19 tests: 11 core, four Drill, three Sifter and one reload test. It includes
wood/iron ordinary-Sifter work times and matching stress, deterministic dry coproducts versus wet output,
brass filtering/batch debit, insufficient-output pause, BE serialization, native extraction, brass breaking,
actual ResultSlot recovery with full-inventory overflow, future records/stock safety, and actual changed
engineering/native data plus failed/recovered reloads. The isolated client checks authoritative catalog and
installed mesh packets, recipe/index lookup, and renders wood/iron/retool pages.

Still needed: every selected functional variant's two-build operation cases, full physical conversion,
contraption and chunk lifecycle, interruption/resume and capacity edge cases across all native variants,
connected-client changed-data reload, representative population bounds, complete registration/acquisition/
reference audit bindings, Ponder and the subject/case receipt protocol. These fixtures do not prove full-pack
acquisition or gameplay. The source ledger remains pending, and neither completion gate has been waived.
