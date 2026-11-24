package com.onlyk.justepicportals.compat.jei;

import com.onlyk.justepicportals.JustEpicPortals;
import com.onlyk.justepicportals.PortalEffect;
import com.onlyk.justepicportals.PortalSurfaceBlock;
import java.util.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

/** Documentation entries use the same item checks as applying a dye or effect in the world. */
public record PortalDesignRecipe(ResourceLocation id,DyeColor color,PortalEffect effect,List<ItemStack> ingredients) {
    public PortalDesignRecipe { ingredients=List.copyOf(ingredients); }

    public boolean accepts(ItemStack stack) {
        var trigger=PortalEffect.fromItem(stack);
        return id.getPath().startsWith("portal_color/")
                ? trigger==null&&PortalSurfaceBlock.dye(stack)==color : trigger==effect;
    }

    public static List<PortalDesignRecipe> create(IIngredientManager manager) {
        var accepted=ItemStackLinkedSet.createTypeAndComponentsSet();
        // Default stacks supply JEI's wildcard lookup as well as component-specific examples.
        // In particular, any enchanted book must work, including combinations absent from JEI.
        for(var item:BuiltInRegistries.ITEM)if(item!=Items.AIR) {
            var stack=item.getDefaultInstance();
            if(PortalSurfaceBlock.dye(stack)!=null||PortalEffect.fromItem(stack)!=null)accepted.add(stack);
        }
        for(var stack:manager.getAllIngredients(VanillaTypes.ITEM_STACK))
            if(PortalSurfaceBlock.dye(stack)!=null||PortalEffect.fromItem(stack)!=null)accepted.add(stack.copyWithCount(1));
        var stacks=List.copyOf(accepted);
        var recipes=new ArrayList<PortalDesignRecipe>();
        for(var color:DyeColor.values())recipes.add(new PortalDesignRecipe(JustEpicPortals.id("portal_color/"+color.getName()),color,PortalEffect.ASTRAL,
                stacks.stream().filter(stack->PortalEffect.fromItem(stack)==null&&PortalSurfaceBlock.dye(stack)==color).map(stack->stack.copyWithCount(1)).toList()));
        for(var effect:PortalEffect.values())recipes.add(new PortalDesignRecipe(JustEpicPortals.id("portal_effect/"+effect.id()),DyeColor.LIME,effect,
                stacks.stream().filter(stack->PortalEffect.fromItem(stack)==effect).map(stack->stack.copyWithCount(1)).toList()));
        return List.copyOf(recipes);
    }
}
