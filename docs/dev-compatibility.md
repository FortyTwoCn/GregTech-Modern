# Development compatibility policy

This fork is in development. Compatibility with older development builds is
temporary; the current implementation must work independently of legacy bridges.

- Put old-format readers and API adapters in dedicated compatibility classes or
  isolated deprecated declarations, with one clearly marked call site.
- Mark each temporary bridge with `DEV-COMPAT(<id>)`, its supported old format/API,
  and the condition for deletion. Add an entry to the table below.
- Read older data only when needed; write the current format. Keep compatibility
  tests separate from tests for current behavior.
- Do not add scattered version checks or broad exception fallbacks. Do not make
  normal runtime logic depend on a deprecated field.

## Removal inventory for this fork's changes

| ID | Temporary compatibility | Removal condition and steps |
| --- | --- | --- |
| `legacy-aoe` | `api/item/datacomponents/compat/LegacyAoECompat.java` reads the `column/row/layer` object from builds before upstream `70db06c48`. | When those development saves are retired or migrated, replace the `LegacyAoECompat.withLegacyRead(...)` hook in `AoESymmetrical.CODEC` with its argument, remove the import, delete the compatibility class and `LegacyAoECompatTest`. Retain both current codecs and `UpstreamMergeTest`. |
| `boiler-fuel-cache` | Deprecated, unused `SteamSolidBoilerMachine.FUEL_CACHE` preserves an old public field. | When dependent development addons have been rebuilt, delete the field and its Item/fastutil imports. The stack-aware fuel check and boiler tests remain unchanged. |

## Dependency patch retirement

The JEI 19.57 ModularUI patch targets the supported dependency API; it does not
provide a fallback to older JEI. It is already isolated under `patches/modularui`
and `scripts/build_modularui.py`. When an upstream ModularUI release includes all
these fixes, update `gradle/forge.versions.toml`, remove the script invocation from
`.github/actions/build_setup/action.yml`, then remove the patch and build script.
Verify JEI loading, multiblock viewport position, scaling and mouse interaction.

Cache synchronization, registry ownership checks, recipe registration, correct
fuel handling and disconnected-input guards are correctness fixes. Their
removal is not part of retiring old-version compatibility.
