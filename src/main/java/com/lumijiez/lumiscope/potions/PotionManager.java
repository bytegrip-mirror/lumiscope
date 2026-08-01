package com.lumijiez.lumiscope.potions;

import net.minecraft.potion.Potion;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class PotionManager {
    public static final Potion JAMMED_POTION_EFFECT = new CustomPotion("jammed", 0, 0, false, 0x006400);

    public static void registerPotions() {
        registerPotion(JAMMED_POTION_EFFECT);
    }

    private static void registerPotion(Potion effect) {
        ForgeRegistries.POTIONS.register(effect);
    }
}
