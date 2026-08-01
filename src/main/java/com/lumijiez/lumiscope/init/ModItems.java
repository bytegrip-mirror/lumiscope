package com.lumijiez.lumiscope.init;

import com.lumijiez.lumiscope.items.ItemBase;
import com.lumijiez.lumiscope.items.PortableJammer;
import com.lumijiez.lumiscope.items.radars.RadarDevice;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.List;

public class ModItems {
    public static final List<Item> ITEMS = new ArrayList<>();
    public static final Item radarDevice = new RadarDevice();
    public static final Item portable_jammer = new PortableJammer();
    public static final Item radarAntenna = new ItemBase("radar_antenna");
    public static final Item radarScreen = new ItemBase("radar_screen");
}
