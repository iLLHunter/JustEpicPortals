package com.onlyk.justepicportals;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

public final class PortalAnchorCoreItem extends Item {
    public PortalAnchorCoreItem(Properties properties) { super(properties); }

    public static Optional<AnchorKey> destination(ItemStack stack) {
        return AnchorKey.load(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("justepicportals_destination"));
    }
    public static void remember(ItemStack stack, AnchorKey key) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.put("justepicportals_destination", key.save());
            tag.remove("justepicportals_authorization");
        });
    }
    public static java.util.UUID authorization(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return tag.hasUUID("justepicportals_authorization") ? tag.getUUID("justepicportals_authorization") : null;
    }
    public static void authorize(ItemStack stack, java.util.UUID token) {
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag -> tag.putUUID("justepicportals_authorization",token));
    }
    public static void remember(ItemStack stack, AnchorKey key, net.minecraft.core.Direction.Axis axis) {
        remember(stack,key);
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putString("justepicportals_destination_axis",axis.getName()));
    }
    public static net.minecraft.core.Direction.Axis destinationAxis(ItemStack stack) {
        var name=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("justepicportals_destination_axis");
        for(var axis:net.minecraft.core.Direction.Axis.values())if(axis.getName().equals(name))return axis;
        return null;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof PortalAnchorBlockEntity anchor)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!player.isShiftKeyDown() && anchor.containsCore()) {
            PortalService.openMenu(anchor,player); return InteractionResult.CONSUME;
        }
        if (anchor.material().disabled()) {
            player.displayClientMessage(Component.translatable("message.justepicportals.disabled"), true);
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            if (!PortalPermissions.canManage(anchor,player)) { PortalService.fail(player,"owner_only"); return InteractionResult.FAIL; }
            remember(context.getItemInHand(), anchor.key(), context.getClickedFace().getAxis());
            authorize(context.getItemInHand(),PortalLinks.get(((net.minecraft.server.level.ServerLevel)level).getServer()).authorize(anchor.key()));
            player.displayClientMessage(Component.translatable("message.justepicportals.recorded",
                    anchor.getBlockPos().getX(), anchor.getBlockPos().getY(), anchor.getBlockPos().getZ(), level.dimension().location().toString()), true);
            return InteractionResult.CONSUME;
        }
        var saved = destination(context.getItemInHand());
        if (saved.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.justepicportals.no_destination"), true);
            return InteractionResult.FAIL;
        }
        return PortalService.link(anchor, saved.get(), player, context.getItemInHand(),context.getClickedFace().getAxis(),destinationAxis(context.getItemInHand())) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.justepicportals.pac").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.justepicportals.pac_insert").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.justepicportals.pac_remove").withStyle(ChatFormatting.GRAY));
        destination(stack).ifPresent(key -> tooltip.add(Component.translatable("tooltip.justepicportals.destination",
                key.position().getX(), key.position().getY(), key.position().getZ(), key.dimension().location().toString()).withStyle(ChatFormatting.GREEN)));
    }
}
