package com.gregtechceu.gtceu.regression;

import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.pattern.ExpandablePattern;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import com.gregtechceu.gtceu.api.multiblock.predicates.PredicateBuilder;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("gtceu_regression")
@PrefixGameTestTemplate(false)
public class PredicateApiCompatTest {

    @GameTest(template = "empty_5x5")
    public static void expandablePreviewSkipsUnmappedPositionsLikeTheWorldMatcher(GameTestHelper helper) {
        var symbols = new Char2ObjectOpenHashMap<MultiPredicate>();
        symbols.put('X', Predicates.blocks(Blocks.STONE));
        var pattern = new ExpandablePattern(ExpandablePattern.BoundsProvider.EMPTY,
                (pos, bounds) -> pos.equals(BlockPos.ZERO) ? 'X' : ' ', symbols,
                new RelativeDirection[] { RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK });
        var preview = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.expandable(IntArrayList.of(1, 1, 1, 1, 1, 1))
                .populate(preview, pattern, null, Direction.NORTH, Direction.UP, false);
        helper.assertTrue(preview.size() == 1 && preview.get(BlockPos.ZERO).getBlockState().is(Blocks.STONE),
                "Expandable preview treated unmapped corners as required blocks");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void legacyFluentDescriptorsPreserveNativeCopySemantics(GameTestHelper helper) throws Throwable {
        var source = Predicates.blocks(Blocks.STONE);
        var handle = MethodHandles.publicLookup().findVirtual(MultiPredicate.class, "setPreviewCount",
                MethodType.methodType(MultiPredicate.class, int.class));
        var configured = (MultiPredicate) handle.invokeExact(source, 3);
        helper.assertTrue(
                configured.predicates().getFirst().getPreviewCount() == 3 &&
                        source.predicates().getFirst().getPreviewCount() == -1,
                "A legacy binary setter must link while retaining the native copy semantics");
        var base = new PredicateBuilder("legacy base").predicate(context -> true).blocks(Blocks.STONE).build();
        var baseHandle = MethodHandles.publicLookup().findVirtual(BasePredicate.class, "setMinCount",
                MethodType.methodType(BasePredicate.class, int.class));
        var limited = (BasePredicate) baseHandle.invokeExact(base, 2);
        helper.assertTrue(limited.getMinCount() == 2 && base.getMinCount() == -1,
                "A legacy BasePredicate setter mutated its original predicate");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void addonCandidateSuppliersWaitForRegistrationAndCacheTheirSnapshot(GameTestHelper helper) {
        var candidates = new AtomicReference<List<BlockInfo>>();
        var calls = new AtomicInteger();
        var predicate = new PredicateBuilder("late addon registration").predicate(context -> true)
                .candidatesSupplier(() -> {
                    calls.incrementAndGet();
                    return candidates.get();
                }).build();
        helper.assertTrue(calls.get() == 0, "Building a predicate resolved an unregistered addon candidate");
        candidates.set(List.of(BlockInfo.fromBlock(Blocks.STONE)));
        helper.assertTrue(predicate.getCandidates().getFirst().getBlockState().is(Blocks.STONE),
                "The registered addon candidate was not exposed");
        candidates.set(List.of(BlockInfo.fromBlock(Blocks.DIRT)));
        helper.assertTrue(predicate.getCandidates().getFirst().getBlockState().is(Blocks.STONE) && calls.get() == 1,
                "Repeated preview queries recomputed or changed the registered candidate snapshot");
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void legacyListCandidateDescriptorLinksToTheNativeBuilder(GameTestHelper helper) throws Throwable {
        var builder = new PredicateBuilder("legacy list").predicate(context -> true);
        List<BlockInfo> candidates = List.of(BlockInfo.fromBlock(Blocks.STONE));
        var handle = MethodHandles.publicLookup().findVirtual(PredicateBuilder.class, "candidates",
                MethodType.methodType(PredicateBuilder.class, List.class));
        var configured = (PredicateBuilder) handle.invokeExact(builder, candidates);
        helper.assertTrue(configured.build().getCandidates().getFirst().getBlockState().is(Blocks.STONE),
                "An addon compiled against the List candidate signature cannot build its native predicate");
        helper.succeed();
    }
}
