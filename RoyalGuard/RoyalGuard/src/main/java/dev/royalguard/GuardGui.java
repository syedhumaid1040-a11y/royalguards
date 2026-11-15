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

import java.util.ArrayList;
import java.util.List;

/** A plain chest menu, so it works the same for Java and Bedrock (Geyser) players. */
public final class GuardGui implements InventoryHolder {
    private final GuardManager gm;
    private final Guard g;
    private final Inventory inv;

    public GuardGui(GuardManager gm, Guard g) {
        this.gm = gm;
        this.g = g;
        this.inv = Bukkit.createInventory(this, 27, Component.text("Guard - " + g.name));
        render();
    }

    public void open(Player p) {
        render();
        p.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    private ItemStack icon(Material m, String name, String... lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private String on(boolean b) {
        return b ? "ON" : "OFF";
    }

    private void render() {
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        inv.setItem(10, icon(Material.LEAD, "Follow me", "Stays close and fights for you.", g.mode == Guard.Mode.FOLLOW ? "ACTIVE" : "Click to select"));
        inv.setItem(11, icon(Material.RED_BED, "Stay here", "Holds this spot.", g.mode == Guard.Mode.STAY ? "ACTIVE" : "Click to select"));
        inv.setItem(12, icon(Material.SHIELD, "Guard this area", "Patrols a radius of " + (int) g.radius + ".", g.mode == Guard.Mode.GUARD ? "ACTIVE" : "Click to select"));
        inv.setItem(14, icon(Material.ENDER_PEARL, "Bring to me", "Teleports the guard to you."));
        inv.setItem(15, icon(Material.BARRIER, "Dismiss guard", "Removes this guard and returns its items."));
        inv.setItem(16, icon(Material.CHEST, "Gear and backpack", "Armour, weapons and food."));

        inv.setItem(19, icon(Material.WOODEN_SWORD, "Preset: casual", "Slow, forgiving PvP."));
        inv.setItem(20, icon(Material.IRON_SWORD, "Preset: normal", "Balanced."));
        inv.setItem(21, icon(Material.NETHERITE_SWORD, "Preset: sweaty", "Fast, accurate, hunts players too."));
        inv.setItem(22, icon(Material.WHITE_WOOL, "Preset: passive", "Never fights."));
        inv.setItem(24, icon(Material.ZOMBIE_HEAD, "Fight hostile mobs", "Currently " + on(g.settings.mobs)));
        inv.setItem(25, icon(Material.PLAYER_HEAD, "Fight players who attack you", "Currently " + on(g.settings.players)));
    }

    public void click(Player p, int slot) {
        switch (slot) {
            case 10 -> gm.setMode(g, Guard.Mode.FOLLOW, null);
            case 11 -> gm.setMode(g, Guard.Mode.STAY, null);
            case 12 -> gm.setMode(g, Guard.Mode.GUARD, g.radius);
            case 14 -> gm.bring(g, p);
            case 15 -> {
                gm.remove(g);
                p.closeInventory();
                p.sendMessage(Component.text(g.name + " has been dismissed.", NamedTextColor.YELLOW));
                return;
            }
            case 16 -> {
                new GearGui(gm, g).open(p);
                return;
            }
            case 19 -> gm.applyPreset(g, "casual");
            case 20 -> gm.applyPreset(g, "normal");
            case 21 -> gm.applyPreset(g, "sweaty");
            case 22 -> gm.applyPreset(g, "passive");
            case 24 -> g.settings.mobs = !g.settings.mobs;
            case 25 -> g.settings.players = !g.settings.players;
            default -> {
                return;
            }
        }
        gm.save();
        render();
    }
}
