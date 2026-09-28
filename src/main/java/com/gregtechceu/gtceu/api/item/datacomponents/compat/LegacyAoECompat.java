package com.gregtechceu.gtceu.api.item.datacomponents.compat;

import com.gregtechceu.gtceu.api.item.datacomponents.AoESymmetrical;

import net.minecraft.util.ExtraCodecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.ApiStatus;

/**
 * DEV-COMPAT(legacy-aoe): read pre-70db06c48 development saves, always write the current format.
 * Delete this class, its codec hook and LegacyAoECompatTest when that save format is retired.
 * See docs/dev-compatibility.md.
 */
@ApiStatus.Internal
public final class LegacyAoECompat {

    private LegacyAoECompat() {}

    private static final Codec<AoESymmetrical> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("column").forGetter(AoESymmetrical::column),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("row").forGetter(AoESymmetrical::row),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("layer").forGetter(AoESymmetrical::layer))
            .apply(instance, AoESymmetrical::new));

    public static Codec<AoESymmetrical> withLegacyRead(Codec<AoESymmetrical> current) {
        return Codec.withAlternative(current, LEGACY_CODEC);
    }
}
