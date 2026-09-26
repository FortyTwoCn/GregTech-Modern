# ModularUI compatibility for JEI 19.57

This fork embeds `brachy.modularui:modularui-mc1.21.1:3.3.1-jei19.57-preview1`.
It is built from [ModularUI-Modern](https://github.com/brachy84/ModularUI-Modern)
commit `8ecb104d0c38b1eb9baab5c837624034db7ca873`, with
`jei-19.57-preview.diff` applied. The upstream project is licensed under LGPL-3.0;
the patch preserves that license. No prebuilt dependency binary is committed here.

## Build from a fresh checkout

Install Git, Python 3 and JDK 21, and set `JAVA_HOME` to JDK 21. From the GregTech
repository root:

```sh
python scripts/build_modularui.py
./gradlew test jar
```

On Windows, use `python scripts/build_modularui.py` followed by
`.\gradlew.bat test jar`. The script fetches the exact upstream revision, applies
the patch, runs ModularUI's unit tests and publishes the patched dependency to
Maven local. GregTech already resolves Maven local and embeds this dependency
through Jar-in-Jar. Generated checkouts are kept under `build/patched-dependencies`.
Run the preparation script again after changing the patch or clearing Maven local.
The shared GitHub Actions build setup runs the same preparation automatically.

## Included changes

- Replace removed JEI `RecipeSlot` ingredient fields with `RecipeSlotIngredients`.
- Resolve the JEI 19.57 clickable-ingredient handler default-method conflict.
- Position schematic rendering using the actual GUI drawing matrix, including
  the host recipe panel's translation and scale.
- Account for drawable padding when tracing the mouse into the schematic.
- Add three viewport regression tests for embedding, scaling/padding and a
  standalone GUI.

The companion GregTech changes also fix recipe registry ordering, lazy JEI icon
creation, mutable energy-transfer recipe results, null predicate candidates and
key synchronization while disconnected. The schematic preview was confirmed in
the modpack; the key synchronization change has compiled successfully and still
needs an in-game disconnect/reconnect regression check. Gradle's `test` task does
not replace GregTech's separate in-game GameTests.
