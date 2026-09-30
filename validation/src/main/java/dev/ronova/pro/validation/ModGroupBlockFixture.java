package dev.ronova.pro.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** A real mod-owned, non-ticking holder for the exact group stop/protect effect. */
final class ModGroupBlockFixture {
    static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"pro_fixture");
    static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"pro_fixture");
    static final RegistryObject<Block> BLOCK=BLOCKS.register("group_holder",HolderBlock::new);
    static final RegistryObject<BlockEntityType<Holder>> TYPE=TYPES.register("group_holder",
            ()->BlockEntityType.Builder.of(Holder::new,BLOCK.get()).build(null));
    private ModGroupBlockFixture() { }
    static final class HolderBlock extends BaseEntityBlock {
        HolderBlock() { super(BlockBehaviour.Properties.of().strength(1)); }
        @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new Holder(pos,state); }
    }
    static final class Holder extends BlockEntity {
        Holder(BlockPos pos,BlockState state) { super(TYPE.get(),pos,state); }
    }
}
