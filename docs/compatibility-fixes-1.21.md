# 1.21 compatibility fixes

Upstream `GregTechCEu/GregTech-Modern:1.21` was merged through
`3a1493f10` (eleven commits after `17e17006f`). The prior JEI 19.57,
schematic preview, registry ordering and disconnected key-sync fixes are retained.
See `patches/modularui/README.md` for the required dependency preparation step.
Temporary backward compatibility and removal instructions are tracked separately
in `docs/dev-compatibility.md`.

The 2026-09-28 merge adds `e8a86525d` (host-rock-aware ore recipe ingredients for
Almost Unified) and `70db06c48` (AOE components and configuration UI). Regression
checks cover ore recipes accepting only the correct host rock, current AOE
serialization/network transport, tool range limits, reconfiguring zero AOE, and
reading old AOE data while writing only the current format. Full Almost Unified
modpack rewriting and client GUI interactions still require in-game validation.

## 2026-10-01 upstream merge

Merged `e769595ce` (long-distance pipe casing/connected textures) and `3a1493f10`
(magnet and filter fixes). FilterMergeTest covers filter component writeback,
first/ninth slot persistence, magnet filter copies without shared item stacks,
cold tag-expression matching, and composite filters containing empty slots.
The existing eleven regression tests remain part of the same test run. Magnet
GUI interaction and connected-texture appearance still need client validation.

## Solid-fuel boilers

`GTRecipeType` was not copying custom recipe logic from its builder properties.
This disabled both server-side dynamic fuel recipes and representative recipes
for recipe viewers. The constructor now retains those logic runners.

The small solid boiler uses the same stack-aware burn-time check for insertion
and recipe generation, rather than caching validity by item type. Cached recipe
keys are copied so consuming the inventory stack cannot mutate a cache key.
The GUI flame shows remaining burn progress and is empty while idle.

## Steam machine wrench rotation

The 2026-09-29 multiplayer crash reports an invalid vertical upwards direction
in SimpleSteamMachine.updateModelVentDirection when a wrench rotates a machine
to face up or down. Non-extended machines report UP as their upwards facing, but
the block model uses NORTH as its top when the front is vertical.

Vent model updates now invert the actual front/top frame instead of passing the
invalid frame to RelativeDirection.findRelativeOf. Extended-facing machines use
their configured top; an intermediate parallel front/top pair is deferred until
the front rotation finishes. Loading a machine also refreshes the vent model
after restoring its physical output direction. No legacy data adapter is needed.

SteamWrenchRotationTest exercises the wrench path for bronze and steel variants
of all seven simple steam machine types. It checks permitted front rotations,
all output sides, front/output collision rejection, and vent placement against
the block model Euler transform. Both pressure variants reproduced the reported
exception with the original implementation. These shared-code server tests do
not replace a multiplayer client visual check.

## Prospection cache

Saving cached ore metadata with a holder owned by another registry could throw
`Element Reference ... is not valid in current registry set` during disconnect.
Saving now resolves each ore key against the supplied registry without modifying
the shared metadata. Position and depletion state are preserved. Removed
definitions or malformed entries are logged and skipped individually on load;
missing definitions are also skipped on save.

## Upstream Jade display correction

The merged zero-amount fluid display path must only apply to empty locked quantum
tanks. Nonempty quantum tanks continue to report their actual long-valued stored
amount rather than the zero-value placeholder.

## Regression checks

### Concurrent material-entry lookups

The 2026-09-28 client crash occurred while drawing a block outline: concurrent
material lookups corrupted the fastutil negative-result set and failed in rehash.
Material-entry lookups, lazy supplier resolution, registration, dynamic tag
generation and reload now share the ItemMaterialData.class monitor. Reload also
discards collected item entries, negative results and resolved tag entries, so
removed mappings cannot survive into the next reload. Addons accessing these
public cache fields directly must use the same monitor.

MaterialEntryCacheTest uses eight workers over all registered items for six
rounds, forcing negative-cache growth and overlapping lookups with reloads. It
also checks explicit registration after a negative lookup and removal on reload.
Restoring only the old getMaterialEntry(ItemLike) method made this test fail with
Index -1 out of bounds for length 2049; the synchronized implementation passed
alongside the five existing regression tests on 2026-09-28.

With JDK 21 and the patched ModularUI installed in Maven local:

```sh
./gradlew runGameTestServer -PgameTestNamespaces=gtceu_regression
./gradlew spotlessCheck test jar
```

The five dedicated GameTests cover bronze and steel boilers accepting coal,
rejecting stone/fluid containers, consuming fuel, heating and producing steam;
coal representative recipes; saving foreign registry holders; and loading valid
cache entries alongside missing/corrupt entries. All five passed on 2026-09-27.
These server tests do not exercise client GUI drawing or all modpack combinations.
