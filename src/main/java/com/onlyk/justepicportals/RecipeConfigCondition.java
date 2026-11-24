package com.onlyk.justepicportals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Reads the active configuration; only the PAC recipe option may change during play. */
public record RecipeConfigCondition(String option, boolean value) implements ICondition {
    public static final MapCodec<RecipeConfigCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("option").forGetter(RecipeConfigCondition::option),
            Codec.BOOL.fieldOf("value").forGetter(RecipeConfigCondition::value)
    ).apply(instance, RecipeConfigCondition::new));

    @Override public boolean test(IContext context) {
        var rules = PortalConfig.rules();
        Boolean actual = switch (option) {
            case "PacRecipeCheap" -> rules.cheapRecipe();
            case "disableStonePortal" -> rules.disableStone();
            case "disableNetherrackPortal" -> rules.disableNetherrack();
            case "disableEndStonePortal" -> rules.disableEndStone();
            case "disableDimensionalTeleportation" -> rules.disableDimensional();
            default -> java.util.Arrays.stream(PortalMaterial.values()).filter(material -> material.disableOption().equals(option))
                    .findFirst().map(rules::disabled).orElse(null);
        };
        return actual != null && actual == value;
    }

    @Override public MapCodec<? extends ICondition> codec() { return CODEC; }
}
