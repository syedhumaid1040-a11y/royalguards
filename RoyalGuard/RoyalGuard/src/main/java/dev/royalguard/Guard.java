package dev.royalguard;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** One bodyguard: saved data plus its live runtime state. */
public final class Guard {
    public enum Mode { FOLLOW, STAY, GUARD }

    public final UUID id;
    public final UUID owner;
    public String name = "Guard";
    public String skin = "MHF_Steve";
    public Mode mode = Mode.FOLLOW;
    public double radius = 10.0;
    public Location anchor;
    public final Settings settings = new Settings();
    /** helmet, chestplate, leggings, boots, main hand, off hand (null = empty) */
    public final ItemStack[] equip = new ItemStack[6];
    /** 9 backpack slots; edible items heal the guard when it is hurt */
    public final ItemStack[] pack = new ItemStack[9];

    // runtime only
    public Mob brain;          // real mob: pathfinding, health, hit box
    public Entity visual;      // optional Mannequin that wears the player skin
    public LivingEntity target;
    public LivingEntity forced;
    public long engageAt;
    public long nextAttack;
    public long respawnAt;
    public long nextEat;

    public Guard(UUID id, UUID owner) {
        this.id = id;
        this.owner = owner;
    }
}
