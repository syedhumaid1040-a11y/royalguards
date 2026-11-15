package dev.royalguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Editable gear menu. Slots 1-4 armour, 6-7 hands, bottom row is the backpack.
 * Plain chest inventory, so it behaves the same on Java and Bedrock (Geyser).
 */
public final class GearGui implements InventoryHolder {
    // helmet, chestplate, leggings, boots, main hand, off hand
    private static final int[] EQUIP_SLOTS = {1, 2, 3, 4, 6, 7};
    private static final int PACK_START = 18;

    private final GuardManager gm;
    private final Guard g;
    private final Inventory inv;

    public GearGui(GuardManager gm, Guard g) {
        this.gm = gm;
        this.g = g;
        this.inv = Bukkit.createInventory(this, 27, Component.text("Gear - " + g.name));
        gm.syncBack(g);   // pick up durability changes from the live body
        fill();
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public static boolean editable(int slot) {
        for (int s : EQUIP_SLOTS) if (s == slot) return true;
        return slot >= PACK_START && slot < PACK_START + 9;
    }

    private ItemStack label(Material m, String name, String... lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.Arrays.stream(lore)
                .map(l -> Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .toList());
        item.setItemMeta(meta);
        return item;
    }

    private void fill() {
        ItemStack pane = label(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) if (!editable(i)) inv.setItem(i, pane);

        inv.setItem(0, label(Material.IRON_CHESTPLATE, "Armour", "Helmet, chestplate, leggings, boots", "go in the next four slots."));
        inv.setItem(5, label(Material.IRON_SWORD, "Hands", "Main hand, then off hand."));
        inv.setItem(13, label(Material.GOLDEN_APPLE, "Backpack (below)", "Food in here is eaten", "automatically when the guard is hurt."));

        for (int i = 0; i < EQUIP_SLOTS.length; i++) inv.setItem(EQUIP_SLOTS[i], g.equip[i] == null ? null : g.equip[i].clone());
        for (int i = 0; i < 9; i++) inv.setItem(PACK_START + i, g.pack[i] == null ? null : g.pack[i].clone());
    }

    private ItemStack keep(ItemStack i) {
        return (i == null || i.getType().isAir()) ? null : i.clone();
    }

    /** Called when the menu closes. Only editable slots are read. */
    public void saveFrom() {
        for (int i = 0; i < EQUIP_SLOTS.length; i++) g.equip[i] = keep(inv.getItem(EQUIP_SLOTS[i]));
        for (int i = 0; i < 9; i++) g.pack[i] = keep(inv.getItem(PACK_START + i));
        gm.applyGear(g);
        gm.save();
    }
}
