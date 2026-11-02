# Better Manufacturing — part-based machine conversion in progress

Forge 1.20.1 / Java 17. The active scope is **part-based machine variants**, not the earlier
chemistry/recovery program. Parts are consumed normally. Their relevant properties are calculated
once during crafting and stored in `better_manufacturing:settings` (version 1). No original parts,
wear, refunds, material accounting or qualification history are retained by these recipes.

**The selected roster is not complete. No Manufacturing JAR has been deployed.**

## Implemented source and pack changes

| Machine/component | Construction and native behavior |
|---|---|
| Create Mechanical Drill | Pick head; baked native mining-speed ratio and harvest tier; matching stress; stationary and moving cutting hooks. |
| Create Mechanical Saw | Small blade; baked native mining-speed ratio/tier; native processing and stationary/moving cutting hooks; matching stress. |
| Create Mechanical Press | Large plate; authored pressing rating controls native press progress and stress. |
| Create Millstone | Large plate; authored milling rating controls native processing progress and stress. |
| Create Whisk / Mechanical Mixer | Handle defines whisk settings; mixer consumes those baked settings and applies them to mixing progress and stress. |
| Create Propeller / Encased Fan | Four small blades define airflow; fan consumes the propeller settings. Native airflow RPM/distance calculation and force use the rating, with matching stress. Native range caps, obstructions and processing recipes remain native. |
| Create Mechanical Pump | A handle defines the impeller rating. The native pipe graph receives bounded scaled pump pressure; placement, save, drop, and matching kinetic stress use the baked settings. Existing pipe endpoints, reversal, and fluid recipes remain Create-owned. |
| Create Sifter / Brass Sifter meshes | String, andesite, zinc and brass meshes consume four handles. Baked screen rate changes native progress and stress; native wet/dry recipes, rolls, overflow and brass filtering are unchanged. |

Untiered multipliers remain within 0.75–1.5× reference. Cutters use native Tinkers head stats;
other roles have separate explicit ratings, not a universal quality score. Cheap builds include
native rock plates/blades and wooden handles. Native texture variants inherit their base rating
unless specifically overridden. Iron is the reference at **crafting**, never a live runtime dependency.

The pack's `20_powered_works.js` overrides upstream press, millstone, mixer and pump recipes; those actual
progression roots now use the same serializer while retaining Machine Blocks and other gates. The mixer
consumes its configured native whisk (not an added handle), inheriting the whisk's final rate/labels.
Brass Hand, Deployer and Mechanical Crafter receive construction-only handle ingredients, preserving
native functionality and progression. No performance bonuses were invented for these neighbors.
Existing advanced/custom mesh acquisition closures remain closed.

## Persistence and player display

- Versioned settings contain only output identity, bounded properties, material labels and applicable native tier.
- Missing/invalid settings use native baseline. Unrelated native data is not deleted.
- Placement, native save/packets, loot/wrench paths, schematic matching and relevant moving-actor paths carry settings.
- Material reload affects future crafts only; existing machines do not reevaluate materials or refresh their networks.
- Tooltips/goggles show the applicable multiplier. EMI uses actual configured crafting examples, including configured
  whisk/propeller ingredients and native material variants. Exact-part retooling recipes and recovery pages are inactive.

## Native evidence (scoped)

The archived wave-one core Create/Sifter fixture passed 21/21 native GameTests
(`build/gametest/2514cfd1-19ef-41ee-945e-4153cd2b3555/execution.json`). The wave-two
**independent Create/actual-20** Forge userdev profile subsequently passed **31/31 required native
GameTests** with Create, Sifter, TCon, KubeJS and its selected production scripts/data loaded
(`build/gametest/856c112d-b73d-41ba-8897-654be127fbe8/execution.json`,
`/home/dev/.tmp/manufacturing-wave2/runtime-create-native7.log`). This checks the scoped
configured crafting, native work/stress, consumption and fallback cases, including the
nonidentity crushing-wheel and paid ejector stress paths. The receipt explicitly records
`full_roster_accepted=false`: it does **not** qualify the separately required
Bumblezone/`85_dimension_font_ecology.js`/harvester run, full native boiler/controller timing,
optional PNCR/fluids/vehicles or polymer chains, nor every selected ID. Source registration and
recipe JSON alone are not acceptance. Only the runtime owner runs heavyweight checks under the
shared Gradle lock, offline with at most two workers. No real-client/full-pack run or release
artifact has been authorized.

## Wave-two work and remaining scope

Candidate source, native hooks and conditional recipes now exist across Create bulk/actors/fluids,
Industrial Heat, PNCR/Compressed, PowerGrid/Diesel, precision/bees, storage/logistics, and
Clockwork/vehicle families. These additions are **not** accepted family conversions until their
meaningful native work/resource behavior, effective alternate acquisition and loaded-mod lifecycles
pass focused tests. Construction-only outputs consume parts without fake performance settings;
unavailable outputs retain their unavailability and the five Backpacks/Clockwork exclusions remain
excluded. The original 462 selected IDs are not a count of completed conversions.

Nine physical components are centrally registered (12 maximum): eight polymer feed/stock/catalyst/
culture items and one compressed-iron valve blank. The blank is a consumed prepared body for the
native one-slot PNCR assembly-drill route, not a retained qualification token. Its recipe/hook are
still subject to loaded PNCR tests. Polymer work has **20 authored process recipes** and scoped
PLA/PP casting/material assets; the combined Manufacturing/Hazards propylene case, connected powered
vacuum, first-feed bootstrap, casting/remelt and consumer replacement remain unaccepted. The generic
PNCR plastic routes must remain until both chains and replacements actually pass. Do not infer a
measured 30–40 °C fermentation window from the native temperature-unspecified condition.

Older provenance/retool/recovery and process abstractions remain dormant compatibility/history,
not a wave-two promise of wear, refunds, original-part retention or new chemistry gates. No migration
system is needed for undeployed experimental records.

The old full-roster/shared-feature gates and ledgers retain their original meaning and are not completion claims
for this phase. See `docs/source-completion.md` for that historical contract. Do not weaken those gates or count
infrastructure as converted machines. No full-pack launch, deployment, Packwiz refresh or release packaging is authorized.
