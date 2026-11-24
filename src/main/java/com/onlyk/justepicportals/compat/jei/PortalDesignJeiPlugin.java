package com.onlyk.justepicportals.compat.jei;

import com.onlyk.justepicportals.*;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** JEI discovers this class only when installed; no common startup code references its API. */
@JeiPlugin
public final class PortalDesignJeiPlugin implements IModPlugin {
    private IJeiRuntime runtime;
    private List<PortalDesignRecipe> displayed=List.of();

    public PortalDesignJeiPlugin(){NeoForge.EVENT_BUS.addListener(this::tagsUpdated);}
    @Override public ResourceLocation getPluginUid(){return JustEpicPortals.id("portal_design");}
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new PortalDesignCategory(registration.getJeiHelpers().getGuiHelper()));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        displayed=PortalDesignRecipe.create(registration.getIngredientManager());
        registration.addRecipes(PortalDesignCategory.TYPE,displayed);
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for(var material:PortalMaterial.values())if(!PortalConfig.creativeRules().disabled(material))
            registration.addRecipeCatalyst(JustEpicPortals.anchorBlock(material),PortalDesignCategory.TYPE);
    }
    @Override public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addTypedRecipeManagerPlugin(PortalDesignCategory.TYPE,new ISimpleRecipeManagerPlugin<PortalDesignRecipe>() {
            private List<PortalDesignRecipe> matching(ITypedIngredient<?> ingredient) {
                return ingredient.getItemStack().map(stack->displayed.stream().filter(recipe->recipe.accepts(stack)).toList()).orElse(List.of());
            }
            @Override public boolean isHandledInput(ITypedIngredient<?> ingredient){return !matching(ingredient).isEmpty();}
            @Override public boolean isHandledOutput(ITypedIngredient<?> ingredient){return false;}
            @Override public List<PortalDesignRecipe> getRecipesForInput(ITypedIngredient<?> ingredient){return matching(ingredient);}
            @Override public List<PortalDesignRecipe> getRecipesForOutput(ITypedIngredient<?> ingredient){return List.of();}
            @Override public List<PortalDesignRecipe> getAllRecipes(){return List.of();}
        });
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime){this.runtime=runtime;}
    @Override public void onRuntimeUnavailable(){runtime=null;displayed=List.of();}
    private void tagsUpdated(TagsUpdatedEvent event) {
        if(event.getUpdateCause()!=TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED)return;
        Minecraft.getInstance().execute(()->{
            if(runtime==null)return;
            var manager=runtime.getRecipeManager();
            manager.hideRecipes(PortalDesignCategory.TYPE,displayed);
            displayed=PortalDesignRecipe.create(runtime.getIngredientManager());
            manager.addRecipes(PortalDesignCategory.TYPE,displayed);
            manager.unhideRecipes(PortalDesignCategory.TYPE,displayed);
        });
    }
}
