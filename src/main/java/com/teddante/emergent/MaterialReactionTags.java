package com.teddante.emergent;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class MaterialReactionTags {
    public static final TagKey<Block> CHARS_IN_FIRE = blockTag("chars_in_fire");
    public static final TagKey<Block> SCORCHES_TO_DIRT_IN_FIRE = blockTag("scorches_to_dirt_in_fire");
    public static final TagKey<Block> BURNS_AWAY_IN_FIRE = blockTag("burns_away_in_fire");
    public static final TagKey<Block> FLASH_BURNS_IN_FIRE = blockTag("flash_burns_in_fire");
    public static final TagKey<Block> SUSTAINS_FIRE = blockTag("sustains_fire");
    public static final TagKey<Block> ERODES_IN_WATER = blockTag("erodes_in_water");
    public static final TagKey<Block> WASHES_AWAY_IN_WATER = blockTag("washes_away_in_water");
    public static final TagKey<Block> BRITTLE = blockTag("brittle");
    public static final TagKey<Block> CONDUCTIVE = blockTag("conductive");
    public static final TagKey<Block> HEAT_SOURCES = blockTag("heat_sources");
    public static final TagKey<Block> RAIN_OXIDIZES = blockTag("rain_oxidizes");
    public static final TagKey<Block> RAIN_GROWS = blockTag("rain_grows");
    public static final TagKey<Block> COMPACTS_UNDER_TRAFFIC = blockTag("compacts_under_traffic");

    private MaterialReactionTags() {
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.parse(Emergent.MOD_ID + ":" + path));
    }
}
