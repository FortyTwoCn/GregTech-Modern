package com.gregtechceu.gtceu.regression;

import com.gregtechceu.gtceu.api.item.datacomponents.AoESymmetrical;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

// DEV-COMPAT(legacy-aoe): delete together with LegacyAoECompat, retain UpstreamMergeTest.
@GameTestHolder("gtceu_regression")
@PrefixGameTestTemplate(false)
public class LegacyAoECompatTest {

    @GameTest(template = "empty_5x5")
    public static void legacyAoeReadsAndWritesCurrentFormat(GameTestHelper helper) {
        var legacy = JsonParser.parseString("""
                {"max_column":1,"max_row":1,"max_layer":0,"column":0,"row":1,"layer":0}
                """);
        var value = AoESymmetrical.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        helper.assertTrue(value.equals(AoESymmetrical.of(0, 1, 0)), "Retain old selected AOE");
        var encoded = AoESymmetrical.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject();
        helper.assertTrue(encoded.has("additional_columns") && !encoded.has("column") && !encoded.has("max_column"),
                "Legacy values must be saved using only the current format");
        helper.succeed();
    }
}
