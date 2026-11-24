package com.onlyk.justepicportals.compat.mixin;

import com.onlyk.justepicportals.PortalAnchorBlock;
import com.onlyk.justepicportals.PortalMaterial;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets the pickaxe's own bedrock shortcut supply the anchor item and remove the block. */
@Pseudo
@Mixin(targets="net.byAqua3.avaritia.event.AvaritiaEvent",remap=false)
public abstract class AvaritiaMiningMixin {
    @Redirect(method="onPlayerLeftClick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;"))
    private List<ItemStack> justepicportals$bedrockShortcut(BlockState state,ServerLevel level,BlockPos pos,BlockEntity entity) {
        if(state.getBlock() instanceof PortalAnchorBlock anchor && anchor.material()==PortalMaterial.BEDROCK)return List.of();
        return Block.getDrops(state,level,pos,entity);
    }
}
