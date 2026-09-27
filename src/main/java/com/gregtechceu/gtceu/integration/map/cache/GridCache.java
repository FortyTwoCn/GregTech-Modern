package com.gregtechceu.gtceu.integration.map.cache;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GridCache {

    @Getter
    private final List<GeneratedVeinMetadata> veins = new ArrayList<>();

    public boolean addVein(GeneratedVeinMetadata vein) {
        if (veins.contains(vein)) return false;
        veins.add(vein);
        return true;
    }

    public ListTag toNBT(HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        var lookup = registries.lookup(GTRegistries.Keys.ORE_VEIN);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        for (GeneratedVeinMetadata pos : veins) {
            // Cached holders may originate from the integrated server or a previous
            // registry instance. Resolve the stable key in the registry being saved.
            var definition = pos.definition().unwrapKey().flatMap(key -> lookup.flatMap(registry -> registry.get(key)));
            if (definition.isEmpty()) {
                GTCEu.LOGGER.warn("Skipping cached ore vein with missing definition: {}", pos.definition().unwrapKey());
                continue;
            }
            var rebound = new GeneratedVeinMetadata(pos.originChunk(), pos.center(), definition.get(), pos.depleted());
            GeneratedVeinMetadata.CODEC.encodeStart(ops, rebound)
                    .resultOrPartial(error -> GTCEu.LOGGER.warn("Could not save cached ore vein: {}", error))
                    .ifPresent(result::add);
        }
        return result;
    }

    public void fromNBT(ListTag tag, HolderLookup.Provider provider) {
        for (Tag veinTag : tag) {
            GeneratedVeinMetadata.CODEC
                    .parse(provider.createSerializationContext(NbtOps.INSTANCE), veinTag)
                    .resultOrPartial(error -> GTCEu.LOGGER.warn("Skipping invalid cached ore vein: {}", error))
                    .ifPresent(this::addVein);
        }
    }

    public List<GeneratedVeinMetadata> getVeinsMatching(Predicate<GeneratedVeinMetadata> predicate) {
        return veins.stream().filter(predicate).collect(Collectors.toList());
    }

    public void removeVeinsMatching(Predicate<GeneratedVeinMetadata> predicate) {
        for (int i = 0; i < veins.size(); i++) {
            if (predicate.test(veins.get(i))) {
                veins.remove(i);
                i--;
            }
        }
    }
}
