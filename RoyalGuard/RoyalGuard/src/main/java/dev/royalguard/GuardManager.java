package dev.royalguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

/** Owns every guard: persistence, spawning, the per-tick AI and who sees which body. */
public final class GuardManager {
    public static final Pattern SKIN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final RoyalGuard plugin;
    public final NamespacedKey key;
    private final Map<UUID, Guard> guards = new LinkedHashMap<>();
    private final Map<UUID, UUID> selected = new HashMap<>();
    private final Random random = new Random();
    private long tick = 0;

    public GuardManager(RoyalGuard plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "guard");
    }

    // ------------------------------------------------------------ lookup

    public Collection<Guard> all() {
        return guards.values();
    }

    public List<Guard> of(UUID owner) {
        List<Guard> out = new ArrayList<>();
        for (Guard g : guards.values()) if (g.owner.equals(owner)) out.add(g);
        return out;
    }

    public Guard find(UUID owner, String name) {
        for (Guard g : of(owner)) if (g.name.equalsIgnoreCase(name)) return g;
        return null;
    }

    public Guard selected(UUID owner) {
        UUID id = selected.get(owner);
        Guard g = id == null ? null : guards.get(id);
        if (g != null) return g;
        List<Guard> mine = of(owner);
        return mine.isEmpty() ? null : mine.get(0);
    }

    public void select(UUID owner, Guard g) {
        selected.put(owner, g.id);
    }

    public boolean isGuardEntity(Entity e) {
        return e.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    public Guard byEntity(Entity e) {
        String s = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return guards.get(UUID.fromString(s));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------ create / edit

    public Guard create(Player owner, String name, String skin) {
        Guard g = new Guard(UUID.randomUUID(), owner.getUniqueId());
        g.name = name;
        g.skin = skin;
        applyPreset(g, plugin.getConfig().getString("default-preset", "normal"));
        g.anchor = owner.getLocation();
        fillDefaults(g);
        guards.put(g.id, g);
        selected.put(owner.getUniqueId(), g.id);
        save();
        return g;
    }

    public void remove(Guard g) {
        returnItems(g);
        despawnBody(g);
        guards.remove(g.id);
        if (g.id.equals(selected.get(g.owner))) selected.remove(g.owner);
        save();
    }

    public boolean applyPreset(Guard g, String preset) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("presets." + preset.toLowerCase(Locale.ROOT));
        if (s == null) return false;
        g.settings.apply(s);
        return true;
    }

    public void setMode(Guard g, Guard.Mode mode, Double radius) {
        g.mode = mode;
        if (radius != null) g.radius = radius;
        if (mode != Guard.Mode.FOLLOW && g.brain != null) g.anchor = g.brain.getLocation();
        g.target = null;
        g.forced = null;
    }

    public void bring(Guard g, Player to) {
        if (g.brain != null && g.brain.isValid()) {
            g.brain.teleport(to.getLocation());
            g.target = null;
        } else {
            g.respawnAt = 0;
        }
    }

    public void rename(Guard g, String name) {
        g.name = name;
        Component c = Component.text(name);
        if (g.brain != null) g.brain.customName(c);
        if (g.visual != null) g.visual.customName(c);
    }

    public void setSkin(Guard g, String skin) {
        g.skin = skin;
        applySkin(g);
    }

    // ------------------------------------------------------------ persistence

    public void load() {
        guards.clear();
        File f = new File(plugin.getDataFolder(), "guards.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = y.getConfigurationSection("guards");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            if (s == null) continue;
            try {
                Guard g = new Guard(UUID.fromString(k), UUID.fromString(s.getString("owner", "")));
                g.name = s.getString("name", "Guard");
                g.skin = s.getString("skin", plugin.getConfig().getString("default-skin", "MHF_Steve"));
                try {
                    g.mode = Guard.Mode.valueOf(s.getString("mode", "FOLLOW"));
                } catch (IllegalArgumentException ignored) {
                    g.mode = Guard.Mode.FOLLOW;
                }
                g.radius = s.getDouble("radius", 10.0);
                World w = Bukkit.getWorld(s.getString("anchor.world", ""));
                if (w != null) {
                    g.anchor = new Location(w, s.getDouble("anchor.x"), s.getDouble("anchor.y"), s.getDouble("anchor.z"));
                }
                g.settings.apply(s.getConfigurationSection("settings"));
                if (s.getBoolean("equip-saved", false)) {
                    for (int i = 0; i < g.equip.length; i++) g.equip[i] = s.getItemStack("equip." + i);
                    for (int i = 0; i < g.pack.length; i++) g.pack[i] = s.getItemStack("pack." + i);
                } else {
                    fillDefaults(g);
                }
                guards.put(g.id, g);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping unreadable guard entry " + k);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Guard g : guards.values()) {
            syncBack(g);
            String p = "guards." + g.id + ".";
            y.set(p + "owner", g.owner.toString());
            y.set(p + "name", g.name);
            y.set(p + "skin", g.skin);
            y.set(p + "mode", g.mode.name());
            y.set(p + "radius", g.radius);
            if (g.anchor != null && g.anchor.getWorld() != null) {
                y.set(p + "anchor.world", g.anchor.getWorld().getName());
                y.set(p + "anchor.x", g.anchor.getX());
                y.set(p + "anchor.y", g.anchor.getY());
                y.set(p + "anchor.z", g.anchor.getZ());
            }
            g.settings.write(y.createSection(p + "settings"));
            y.set(p + "equip-saved", true);
            for (int i = 0; i < g.equip.length; i++) if (g.equip[i] != null) y.set(p + "equip." + i, g.equip[i]);
            for (int i = 0; i < g.pack.length; i++) if (g.pack[i] != null) y.set(p + "pack." + i, g.pack[i]);
        }
        try {
            y.save(new File(plugin.getDataFolder(), "guards.yml"));
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save guards.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ bodies

    private EntityType entityType(String name) {
        if (name == null) return null;
        try {
            return Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ItemStack gear(String slot) {
        String name = plugin.getConfig().getString("body.gear." + slot, "");
        Material m = name.isBlank() ? null : Material.matchMaterial(name);
        return new ItemStack(m == null ? Material.AIR : m);
    }

    public void spawnBody(Guard g, Location at) {
        despawnBody(g);
        World w = at.getWorld();
        if (w == null) return;
        FileConfiguration c = plugin.getConfig();

        EntityType brainType = entityType(c.getString("body.brain-type", "husk"));
        if (brainType == null) brainType = EntityType.HUSK;
        Entity raw;
        try {
            raw = w.spawnEntity(at, brainType);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Could not spawn body.brain-type '" + brainType + "': " + ex.getMessage());
            return;
        }
        if (!(raw instanceof Mob mob)) {
            raw.remove();
            plugin.getLogger().warning("body.brain-type must be a mob type (for example husk or wolf).");
            return;
        }

        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.getPersistentDataContainer().set(key, PersistentDataType.STRING, g.id.toString());
        mob.customName(Component.text(g.name));
        mob.setCustomNameVisible(true);
        if (mob instanceof Zombie z) z.setShouldBurnInDaylight(false);
        Bukkit.getMobGoals().removeAllGoals(mob);   // we drive it ourselves

        double hp = c.getDouble("body.health", 40.0);
        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(hp);
            mob.setHealth(hp);
        }

        g.brain = mob;

        boolean wantMannequin = "mannequin".equalsIgnoreCase(c.getString("appearance.java", "mannequin"))
                || "mannequin".equalsIgnoreCase(c.getString("appearance.bedrock", "mob"));
        EntityType mannequin = entityType("mannequin");
        if (wantMannequin && mannequin != null) {
            try {
                Entity v = w.spawnEntity(at, mannequin);
                v.setPersistent(false);
                v.setInvulnerable(true);
                v.setSilent(true);
                v.setGravity(false);
                v.getPersistentDataContainer().set(key, PersistentDataType.STRING, g.id.toString());
                v.customName(Component.text(g.name));
                v.setCustomNameVisible(true);
                g.visual = v;
                applySkin(g);
            } catch (Exception ex) {
                plugin.getLogger().warning("Mannequin body unavailable, using the mob body instead: " + ex.getMessage());
                if (g.visual != null) g.visual.remove();
                g.visual = null;
            }
        }
        applyGear(g);
        refreshViewers(g);
    }

    private static final String[] GEAR_KEYS = {"helmet", "chestplate", "leggings", "boots", "mainhand", "offhand"};

    private void fillDefaults(Guard g) {
        for (int i = 0; i < GEAR_KEYS.length; i++) {
            ItemStack it = gear(GEAR_KEYS[i]);
            g.equip[i] = it.getType().isAir() ? null : it;
        }
    }

    private ItemStack copy(ItemStack i) {
        return i == null ? new ItemStack(Material.AIR) : i.clone();
    }

    private ItemStack keep(ItemStack i) {
        return (i == null || i.getType().isAir()) ? null : i.clone();
    }

    /** Puts the stored gear on the live body (and the mannequin). */
    public void applyGear(Guard g) {
        if (g.brain == null) return;
        applyTo(g.brain.getEquipment(), g);
        if (g.visual instanceof LivingEntity le) {
            try {
                applyTo(le.getEquipment(), g);
            } catch (Exception ignored) {
                // cosmetic only
            }
        }
    }

    private void applyTo(EntityEquipment eq, Guard g) {
        if (eq == null) return;
        eq.setHelmet(copy(g.equip[0]));
        eq.setChestplate(copy(g.equip[1]));
        eq.setLeggings(copy(g.equip[2]));
        eq.setBoots(copy(g.equip[3]));
        eq.setItemInMainHand(copy(g.equip[4]));
        eq.setItemInOffHand(copy(g.equip[5]));
    }

    /** Pulls the live body's gear back into storage so durability is kept. */
    public void syncBack(Guard g) {
        if (g.brain == null || !g.brain.isValid()) return;
        EntityEquipment eq = g.brain.getEquipment();
        if (eq == null) return;
        g.equip[0] = keep(eq.getHelmet());
        g.equip[1] = keep(eq.getChestplate());
        g.equip[2] = keep(eq.getLeggings());
        g.equip[3] = keep(eq.getBoots());
        g.equip[4] = keep(eq.getItemInMainHand());
        g.equip[5] = keep(eq.getItemInOffHand());
    }

    private void returnItems(Guard g) {
        syncBack(g);
        Player o = Bukkit.getPlayer(g.owner);
        if (o == null) return;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : g.equip) if (it != null) items.add(it);
        for (ItemStack it : g.pack) if (it != null) items.add(it);
        for (ItemStack left : o.getInventory().addItem(items.toArray(new ItemStack[0])).values()) {
            o.getWorld().dropItemNaturally(o.getLocation(), left);
        }
    }

    private void autoEat(Guard g) {
        Mob b = g.brain;
        AttributeInstance mh = b.getAttribute(Attribute.MAX_HEALTH);
        double max = mh == null ? 20.0 : mh.getValue();
        if (b.getHealth() > max * 0.6 || tick < g.nextEat) return;
        for (int i = 0; i < g.pack.length; i++) {
            ItemStack it = g.pack[i];
            if (it == null || !it.getType().isEdible()) continue;
            double heal = it.getType() == Material.ENCHANTED_GOLDEN_APPLE ? 20.0
                    : it.getType() == Material.GOLDEN_APPLE ? 10.0 : 6.0;
            b.setHealth(Math.min(max, b.getHealth() + heal));
            b.swingOffHand();
            if (it.getAmount() <= 1) g.pack[i] = null;
            else it.setAmount(it.getAmount() - 1);
            g.nextEat = tick + 60;
            return;
        }
    }

    /** Skin is set with the vanilla data command; the name is validated first. */
    public void applySkin(Guard g) {
        if (g.visual == null || !g.visual.isValid() || !SKIN.matcher(g.skin).matches()) return;
        String cmd = "data merge entity " + g.visual.getUniqueId()
                + " {profile:\"" + g.skin + "\",hide_description:1b}";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    public void despawnBody(Guard g) {
        syncBack(g);
        if (g.visual != null) {
            g.visual.remove();
            g.visual = null;
        }
        if (g.brain != null) {
            g.brain.remove();
            g.brain = null;
        }
        g.target = null;
        g.forced = null;
    }

    public void despawnAll() {
        for (Guard g : guards.values()) despawnBody(g);
    }

    public void died(Guard g) {
        Player owner = Bukkit.getPlayer(g.owner);
        despawnBody(g);
        g.respawnAt = tick + plugin.getConfig().getInt("respawn-seconds", 20) * 20L;
        if (owner != null) {
            owner.sendMessage(Component.text("Your guard " + g.name + " fell and will return shortly.", NamedTextColor.RED));
        }
    }

    /** Java players and Bedrock players can be shown different bodies. */
    private void refreshViewers(Guard g) {
        if (g.brain == null) return;
        boolean hasVisual = g.visual != null && g.visual.isValid();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean bedrock = p.getUniqueId().getMostSignificantBits() == 0L; // Floodgate UUIDs
            String pref = plugin.getConfig().getString(bedrock ? "appearance.bedrock" : "appearance.java",
                    bedrock ? "mob" : "mannequin");
            if (hasVisual && "mannequin".equalsIgnoreCase(pref)) {
                p.hideEntity(plugin, g.brain);
                p.showEntity(plugin, g.visual);
            } else {
                p.showEntity(plugin, g.brain);
                if (hasVisual) p.hideEntity(plugin, g.visual);
            }
        }
    }

    // ------------------------------------------------------------ AI

    public void tick() {
        tick++;
        boolean think = tick % 2 == 0;
        boolean views = tick % 40 == 0;
        for (Guard g : new ArrayList<>(guards.values())) {
            Player owner = Bukkit.getPlayer(g.owner);
            if (owner == null) {
                if (g.brain != null) despawnBody(g);
                continue;
            }
            if (g.brain == null || !g.brain.isValid()) {
                if (g.brain != null) despawnBody(g);
                if (tick >= g.respawnAt) spawnBody(g, owner.getLocation());
                continue;
            }
            if (g.visual != null) {
                if (g.visual.isValid()) {
                    g.visual.teleport(g.brain.getLocation());
                } else {
                    despawnBody(g);   // respawns next tick
                    continue;
                }
            }
            if (views) refreshViewers(g);
            if (think) think(g, owner);
        }
    }

    private void think(Guard g, Player owner) {
        Mob b = g.brain;
        Settings s = g.settings;
        Location bl = b.getLocation();
        Location ol = owner.getLocation();

        if (!bl.getWorld().equals(ol.getWorld())) {
            b.teleport(ol);
            g.mode = Guard.Mode.FOLLOW;
            g.target = null;
            g.forced = null;
            return;
        }
        if (g.mode == Guard.Mode.FOLLOW && bl.distanceSquared(ol) > s.teleport * s.teleport) {
            b.teleport(ol);
            g.target = null;
            g.forced = null;
            return;
        }
        if (g.anchor == null || !bl.getWorld().equals(g.anchor.getWorld())) g.anchor = bl.clone();
        if (tick % 20 == 0) autoEat(g);

        Location center;
        double search;
        double leash;
        switch (g.mode) {
            case GUARD -> {
                center = g.anchor;
                search = g.radius;
                leash = g.radius * 1.5;
            }
            case STAY -> {
                center = g.anchor;
                search = s.reach + 1.5;
                leash = s.reach + 3.0;
            }
            default -> {
                center = ol;
                search = s.detect;
                leash = s.detect * 1.6 + 4.0;
            }
        }

        if (g.forced != null && !valid(g, g.forced, center, leash)) g.forced = null;
        if (g.target != null && !valid(g, g.target, center, leash)) g.target = null;
        if (g.forced != null) {
            if (g.target != g.forced) {
                g.target = g.forced;
                g.engageAt = tick + s.reaction;
            }
        } else if (g.target == null && s.mobs && s.damage > 0 && tick % 10 == 0) {
            LivingEntity t = nearest(g, center, search);
            if (t != null) {
                g.target = t;
                g.engageAt = tick + s.reaction;
            }
        }

        LivingEntity t = g.target;
        if (t != null) {
            if (tick < g.engageAt) return;
            double dist = bl.distance(t.getLocation());
            if (dist > s.reach) {
                if (tick % 4 == 0) b.getPathfinder().moveTo(t.getLocation(), s.speed);
                return;
            }
            b.getPathfinder().stopPathfinding();
            face(b, t);
            if (tick >= g.nextAttack) attack(g, t);
            return;
        }

        if (tick % 10 != 0) return;
        switch (g.mode) {
            case FOLLOW -> {
                double d = bl.distance(ol);
                if (d > s.follow) b.getPathfinder().moveTo(ol, s.speed * (d > 14 ? 1.5 : 1.0));
                else b.getPathfinder().stopPathfinding();
            }
            case STAY -> {
                if (bl.distance(g.anchor) > 1.5) b.getPathfinder().moveTo(g.anchor, s.speed);
                else b.getPathfinder().stopPathfinding();
            }
            case GUARD -> {
                if (bl.distance(g.anchor) > Math.max(2.0, g.radius * 0.5)) b.getPathfinder().moveTo(g.anchor, s.speed);
                else b.getPathfinder().stopPathfinding();
            }
        }
    }

    private void face(Mob b, LivingEntity t) {
        Location l = b.getLocation();
        l.setDirection(t.getLocation().toVector().subtract(l.toVector()));
        b.setRotation(l.getYaw(), l.getPitch());
    }

    private void attack(Guard g, LivingEntity t) {
        Settings s = g.settings;
        g.nextAttack = tick + s.cooldown;
        g.brain.swingMainHand();
        if (g.visual instanceof LivingEntity lv) {
            try {
                lv.swingMainHand();
            } catch (Exception ignored) {
                // cosmetic only
            }
        }
        if (random.nextDouble() <= s.accuracy) {
            t.damage(s.damage * (0.9 + random.nextDouble() * 0.2), g.brain);
        }
    }

    private LivingEntity nearest(Guard g, Location center, double r) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : center.getWorld().getNearbyEntities(center, r, r, r)) {
            if (!(e instanceof Enemy) || !(e instanceof LivingEntity le) || !valid(g, le, center, r)) continue;
            double d = le.getLocation().distanceSquared(center);
            if (d < bestDist) {
                bestDist = d;
                best = le;
            }
        }
        return best;
    }

    public boolean valid(Guard g, LivingEntity t, Location center, double r) {
        if (t == null || !t.isValid() || t.isDead()) return false;
        if (!t.getWorld().equals(center.getWorld())) return false;
        if (t.getLocation().distanceSquared(center) > r * r) return false;
        return allowed(g, t);
    }

    public boolean allowed(Guard g, LivingEntity t) {
        if (isGuardEntity(t) || t.getUniqueId().equals(g.owner)) return false;
        if (t instanceof Player p) {
            if (!g.settings.players) return false;
            GameMode gm = p.getGameMode();
            return gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE;
        }
        return true;
    }

    /** Called when the owner is attacked (any attacker) or attacks something (hostiles and players only). */
    public void threat(Guard g, LivingEntity t, boolean anyAttacker) {
        if (g.brain == null || !g.settings.assist || g.settings.damage <= 0) return;
        if (!anyAttacker && !(t instanceof Enemy) && !(t instanceof Player)) return;
        if (allowed(g, t)) g.forced = t;
    }
}
