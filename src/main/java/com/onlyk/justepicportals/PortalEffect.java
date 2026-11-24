package com.onlyk.justepicportals;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A portal's visual pattern is independent of its frame material and dye color. */
public enum PortalEffect {
    ASTRAL("astral"), ENDER("the_ender"), ARCANE("arcane"), CLIMAX("climax"),
    SUPERNOVA("supernova"), STELLAR_CONVERGENCE("stellar_convergence"), COSMIC_CRESCENDO("cosmic_crescendo"),
    SIGIL_RAIN("sigil_rain"), CRYSTAL_DRIFT("crystal_drift"), DRAGONWAKE("dragonwake"), SPELLMIST("spellmist");

    private final String id;
    PortalEffect(String id) { this.id=id; }
    public String id() { return id; }
    public String translationKey() { return "effect.justepicportals."+id; }
    public static PortalEffect load(String id) {
        for(var effect:values())if(effect.id.equals(id))return effect;
        return ASTRAL;
    }
    public static PortalEffect fromItem(ItemStack stack) {
        if(stack.is(Items.ENDER_PEARL))return ASTRAL;
        if(stack.is(Items.CHORUS_FRUIT)||stack.is(Items.POPPED_CHORUS_FRUIT))return ENDER;
        if(stack.is(Items.ENCHANTED_BOOK))return ARCANE;
        if(stack.is(Items.FIREWORK_STAR))return CLIMAX;
        if(stack.is(Items.NETHER_STAR))return SUPERNOVA;
        if(stack.is(Items.ECHO_SHARD))return STELLAR_CONVERGENCE;
        if(stack.is(Items.DRAGON_BREATH))return COSMIC_CRESCENDO;
        if(stack.is(Items.EXPERIENCE_BOTTLE))return SIGIL_RAIN;
        if(stack.is(Items.AMETHYST_SHARD))return CRYSTAL_DRIFT;
        if(stack.is(Items.DRAGON_EGG))return DRAGONWAKE;
        if(stack.is(Items.GOLDEN_APPLE))return SPELLMIST;
        return null;
    }
}
