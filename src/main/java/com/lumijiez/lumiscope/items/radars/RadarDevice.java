package com.lumijiez.lumiscope.items.radars;

import com.lumijiez.lumiscope.Lumiscope;
import com.lumijiez.lumiscope.items.ItemBase;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

public class RadarDevice extends ItemBase {

    public RadarDevice() {
        super("radar_device");
        setMaxStackSize(1);
        setMaxDamage(200);
    }

    @Override
    @ParametersAreNonnullByDefault
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        if (worldIn.isRemote) {
            playerIn.openGui(Lumiscope.instance, 0, worldIn, (int) playerIn.posX, (int) playerIn.posY, (int) playerIn.posZ);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(new TextComponentString("A sophisticated player-detection device.")
                .setStyle(new Style().setColor(TextFormatting.GOLD)).getFormattedText());
        tooltip.add(new TextComponentString("Consumes 1 Ender Pearl per scan.")
                .setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText());
        tooltip.add(new TextComponentString("Directional only — not precise. Hunt wisely.")
                .setStyle(new Style().setColor(TextFormatting.DARK_GREEN)).getFormattedText());
        tooltip.add(new TextComponentString("")
                .setStyle(new Style().setColor(TextFormatting.WHITE)).getFormattedText());
        tooltip.add(new TextComponentString("Durability: " + (stack.getMaxDamage() - stack.getItemDamage()) + " / " + stack.getMaxDamage())
                .setStyle(new Style().setColor(TextFormatting.DARK_GRAY)).getFormattedText());
        super.addInformation(stack, worldIn, tooltip, flagIn);
    }
}
