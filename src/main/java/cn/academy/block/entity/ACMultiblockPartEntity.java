package cn.academy.block.entity;

import cn.academy.registry.ACBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Invisible collision/link cell used by the original developer and matrix multiblocks. */
public final class ACMultiblockPartEntity extends BlockEntity {
    private BlockPos origin = BlockPos.ZERO;
    private boolean suppressOriginRemoval;

    public ACMultiblockPartEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.MULTIBLOCK_PART.get(), pos, state);
    }

    public BlockPos origin() { return origin; }
    public boolean suppressOriginRemoval() { return suppressOriginRemoval; }
    public void suppressOriginRemovalOnce() { suppressOriginRemoval = true; }

    public void setOrigin(BlockPos origin) {
        this.origin = origin.immutable();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Origin", origin.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        origin = BlockPos.of(tag.getLong("Origin"));
    }
}
