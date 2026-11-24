package com.onlyk.justepicportals;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;

/** Administration never changes ownership, mining protection, or travel access. */
public final class PortalPermissions {
    public static boolean canAdminister(Player player) {
        return !player.level().isClientSide && player.hasPermissions(PortalConfig.minimumOperatorLevel());
    }
    public static boolean canAdminister(CommandSourceStack source) {
        return source.hasPermission(PortalConfig.minimumOperatorLevel());
    }
    public static boolean canManage(PortalAnchorBlockEntity anchor,Player player) {
        return anchor.ownedBy(player)||canAdminister(player);
    }
    private PortalPermissions() {}
}
