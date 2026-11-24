package com.onlyk.justepicportals.compat.jei;

import com.onlyk.justepicportals.JustEpicPortals;
import com.onlyk.justepicportals.client.PortalRenderer;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

public final class PortalDesignCategory implements IRecipeCategory<PortalDesignRecipe> {
    public static final RecipeType<PortalDesignRecipe> TYPE=new RecipeType<>(JustEpicPortals.id("portal_design"),PortalDesignRecipe.class);
    private final IDrawable icon,arrow;

    public PortalDesignCategory(IGuiHelper helper) {
        icon=helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,JustEpicPortals.PORTAL_ANCHOR_CORE.get().getDefaultInstance());
        arrow=helper.getRecipeArrow();
    }
    @Override public RecipeType<PortalDesignRecipe> getRecipeType(){return TYPE;}
    @Override public Component getTitle(){return Component.translatable("jei.justepicportals.portal_design");}
    @Override public int getWidth(){return 174;}
    @Override public int getHeight(){return 104;}
    @Override public IDrawable getIcon(){return icon;}
    @Override public ResourceLocation getRegistryName(PortalDesignRecipe recipe){return recipe.id();}
    @Override public void setRecipe(IRecipeLayoutBuilder builder,PortalDesignRecipe recipe,IFocusGroup focuses) {
        builder.addInputSlot(22,51).setStandardSlotBackground().setSlotName("ingredient").addItemStacks(recipe.ingredients());
    }
    @Override public void draw(PortalDesignRecipe recipe,IRecipeSlotsView slots,GuiGraphics graphics,double mouseX,double mouseY) {
        var font=Minecraft.getInstance().font;
        var label=Component.translatable("color.minecraft."+recipe.color().getName()).append(" · ")
                .append(Component.translatable(recipe.effect().translationKey()));
        graphics.drawWordWrap(font,label,2,1,170,0xFF404040);
        arrow.draw(graphics,60,51);
        graphics.fill(106,21,162,95,0xFF34343D);
        graphics.fill(107,22,161,94,0xFF10121B);
        graphics.flush();
        // Scissor coordinates are screen-relative in 1.21.1; JEI supplies a translated pose.
        var matrix=graphics.pose().last().pose();
        var first=matrix.transformPosition(new Vector3f(107,22,0));
        var last=matrix.transformPosition(new Vector3f(161,94,0));
        graphics.enableScissor((int)Math.floor(first.x()),(int)Math.floor(first.y()),(int)Math.ceil(last.x()),(int)Math.ceil(last.y()));
        graphics.pose().pushPose();
        graphics.pose().translate(107,94,100);
        graphics.pose().scale(18,-18,1);
        PortalRenderer.renderPreview(graphics.pose(),graphics.bufferSource(),recipe.color().getTextureDiffuseColor(),recipe.effect(),Util.getMillis()/50d);
        graphics.flush();
        graphics.pose().popPose();
        graphics.disableScissor();
    }
}
