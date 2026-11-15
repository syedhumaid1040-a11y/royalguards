package dev.royalguard;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class GuardListener implements Listener {
    private final GuardManager gm;

    public GuardListener(GuardManager gm) {
        this.gm = gm;
    }

    private Entity source(Entity damager) {
        if (damager instanceof Projectile p && p.getShooter() instanceof Entity shooter) return shooter;
        return damager;
    }

    /** Owners cannot hurt their own guard. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuardHit(EntityDamageByEntityEvent e) {
        Guard g = gm.byEntity(e.getEntity());
        if (g == null) return;
        if (source(e.getDamager()).getUniqueId().equals(g.owner)) e.setCancelled(true);
    }

    /** Defend the owner, and join fights the owner starts. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        Entity attacker = source(e.getDamager());
        if (e.getEntity() instanceof Player victim && attacker instanceof LivingEntity la
                && !gm.isGuardEntity(la) && !la.getUniqueId().equals(victim.getUniqueId())) {
            for (Guard g : gm.of(victim.getUniqueId())) gm.threat(g, la, true);
        }
        if (attacker instanceof Player p && e.getEntity() instanceof LivingEntity target && !gm.isGuardEntity(target)) {
            for (Guard g : gm.of(p.getUniqueId())) gm.threat(g, target, false);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Guard g = gm.byEntity(e.getEntity());
        if (g == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        if (e.getEntity() == g.brain) gm.died(g);
    }

    /** Tap or right-click your guard to open its menu. */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Guard g = gm.byEntity(e.getRightClicked());
        if (g == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!g.owner.equals(e.getPlayer().getUniqueId())) return;
        gm.select(g.owner, g);
        new GuardGui(gm, g).open(e.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuardGui gui)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        if (e.getWhoClicked() instanceof Player p) gui.click(p, e.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GuardGui) e.setCancelled(true);
    }

    // ---- gear editor: only the armour, hand and backpack slots can be touched
    @EventHandler
    public void onGearClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GearGui)) return;
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }
        if (e.getClickedInventory() == e.getInventory() && !GearGui.editable(e.getSlot())) e.setCancelled(true);
    }

    @EventHandler
    public void onGearDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof GearGui)) return;
        for (int raw : e.getRawSlots()) {
            if (raw < 27 && !GearGui.editable(raw)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onGearClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof GearGui gui) gui.saveFrom();
    }

    /** Clean up leftovers from a crash. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (!gm.isGuardEntity(en)) continue;
            Guard g = gm.byEntity(en);
            if (g == null || (g.brain != en && g.visual != en)) en.remove();
        }
    }

    /** A husk drowning would turn into a zombie and lose its guard tag. */
    @EventHandler
    public void onTransform(EntityTransformEvent e) {
        if (gm.isGuardEntity(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onPortal(EntityPortalEvent e) {
        if (gm.isGuardEntity(e.getEntity())) e.setCancelled(true);
    }
}
