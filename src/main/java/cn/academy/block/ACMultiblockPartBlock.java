package cn.academy.block;

import cn.academy.block.entity.ACMultiblockPartEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Internal, itemless block that restores collision and interaction over legacy multiblock models. */
public final class ACMultiblockPartBlock extends Block implements EntityBlock {
    public ACMultiblockPartBlock(Properties properties) { super(properties); }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ACMultiblockPartEntity(pos, state);
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ACMultiblockPartEntity part
                && !part.suppressOriginRemoval() && level.getBlockState(part.origin()).getBlock() instanceof ACMachineBlock) {
            level.destroyBlock(part.origin(), true);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
