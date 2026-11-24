package com.onlyk.justepicportals.compat.mixin;

import com.onlyk.justepicportals.PortalAnchorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Skip protected anchors before the native hammer collects loot or removes its area. */
@Pseudo
@Mixin(targets="net.byAqua3.avaritia.item.ItemInfinityPickaxe",remap=false)
public abstract class AvaritiaHammerMixin {
    @Redirect(method="mineBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState justepicportals$protectAreaAnchor(Level level,BlockPos pos,ItemStack stack,Level toolLevel,
                                                       BlockState originState,BlockPos origin,LivingEntity user) {
        if(user instanceof Player player && level.getBlockEntity(pos) instanceof PortalAnchorBlockEntity anchor
                && !anchor.ownedBy(player))return Blocks.AIR.defaultBlockState();
        return level.getBlockState(pos);
    }
}
