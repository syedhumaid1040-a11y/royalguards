package dev.royalguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GuardCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("spawn", "remove", "list", "select", "follow", "stay", "guard",
            "bring", "skin", "rename", "preset", "set", "info", "gui", "inv", "reload");
    private static final List<String> PRESETS = List.of("casual", "normal", "sweaty", "passive");

    private final RoyalGuard plugin;
    private final GuardManager gm;

    public GuardCommand(RoyalGuard plugin, GuardManager gm) {
        this.plugin = plugin;
        this.gm = gm;
    }

    private void say(CommandSender to, String msg, NamedTextColor color) {
        to.sendMessage(Component.text("[Guard] ", NamedTextColor.GOLD).append(Component.text(msg, color)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("royalguard.admin")) {
                say(sender, "You do not have permission.", NamedTextColor.RED);
            } else {
                plugin.reloadConfig();
                say(sender, "Config reloaded. New appearance settings apply to guards when they respawn.", NamedTextColor.GREEN);
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            say(sender, "Players only.", NamedTextColor.RED);
            return true;
        }
        if (a.length == 0) {
            say(p, "Commands: " + String.join(", ", SUBS), NamedTextColor.YELLOW);
            say(p, "Tip: tap or right-click your guard to open its menu.", NamedTextColor.GRAY);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("spawn")) {
            spawn(p, a);
            return true;
        }
        if (sub.equals("list")) {
            List<Guard> mine = gm.of(p.getUniqueId());
            if (mine.isEmpty()) say(p, "You have no guards. Use /guard spawn.", NamedTextColor.YELLOW);
            for (Guard g : mine) say(p, g.name + " - " + g.mode + ", skin " + g.skin, NamedTextColor.YELLOW);
            return true;
        }
        if (sub.equals("select")) {
            Guard g = a.length > 1 ? gm.find(p.getUniqueId(), a[1]) : null;
            if (g == null) {
                say(p, "Usage: /guard select <name>", NamedTextColor.RED);
            } else {
                gm.select(p.getUniqueId(), g);
                say(p, "Selected " + g.name + ".", NamedTextColor.GREEN);
            }
            return true;
        }
        Guard g = gm.selected(p.getUniqueId());
        if (g == null) {
            say(p, "You have no guard yet. Use /guard spawn [name] [skin].", NamedTextColor.RED);
            return true;
        }
        other(p, g, sub, a);
        return true;
    }

    private void spawn(Player p, String[] a) {
        int max = plugin.getConfig().getInt("max-guards", 2);
        if (!p.hasPermission("royalguard.unlimited") && gm.of(p.getUniqueId()).size() >= max) {
            say(p, "You can only have " + max + " guard(s).", NamedTextColor.RED);
            return;
        }
        String name = a.length > 1 ? a[1] : "Guard";
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            say(p, "Names use letters, numbers and _ (max 16).", NamedTextColor.RED);
            return;
        }
        if (gm.find(p.getUniqueId(), name) != null) {
            say(p, "You already have a guard called " + name + ".", NamedTextColor.RED);
            return;
        }
        String skin = a.length > 2 ? a[2]
                : (GuardManager.SKIN.matcher(p.getName()).matches() ? p.getName()
                : plugin.getConfig().getString("default-skin", "MHF_Steve"));
        if (!GuardManager.SKIN.matcher(skin).matches()) {
            say(p, "Skin must be a Java Edition username (3-16 letters, numbers or _).", NamedTextColor.RED);
            return;
        }
        gm.create(p, name, skin);
        say(p, name + " is on duty. Tap the guard to open its menu.", NamedTextColor.GREEN);
    }

    private void other(Player p, Guard g, String sub, String[] a) {
        switch (sub) {
            case "remove" -> {
                gm.remove(g);
                say(p, g.name + " has been dismissed.", NamedTextColor.YELLOW);
            }
            case "follow" -> {
                gm.setMode(g, Guard.Mode.FOLLOW, null);
                gm.save();
                say(p, g.name + " will follow you.", NamedTextColor.GREEN);
            }
            case "stay" -> {
                gm.setMode(g, Guard.Mode.STAY, null);
                gm.save();
                say(p, g.name + " will hold this position.", NamedTextColor.GREEN);
            }
            case "guard" -> {
                double r = 10;
                if (a.length > 1) {
                    try {
                        r = Math.max(3, Math.min(48, Double.parseDouble(a[1])));
                    } catch (NumberFormatException ex) {
                        say(p, "Radius must be a number.", NamedTextColor.RED);
                        return;
                    }
                }
                gm.setMode(g, Guard.Mode.GUARD, r);
                gm.save();
                say(p, g.name + " will guard a radius of " + (int) r + ".", NamedTextColor.GREEN);
            }
            case "bring" -> {
                gm.bring(g, p);
                say(p, g.name + " is on the way.", NamedTextColor.GREEN);
            }
            case "skin" -> {
                if (a.length < 2 || !GuardManager.SKIN.matcher(a[1]).matches()) {
                    say(p, "Usage: /guard skin <java username>", NamedTextColor.RED);
                    return;
                }
                gm.setSkin(g, a[1]);
                gm.save();
                say(p, "Skin set to " + a[1] + ".", NamedTextColor.GREEN);
            }
            case "rename" -> {
                if (a.length < 2 || !a[1].matches("[A-Za-z0-9_]{1,16}")) {
                    say(p, "Usage: /guard rename <name>", NamedTextColor.RED);
                    return;
                }
                gm.rename(g, a[1]);
                gm.save();
                say(p, "Renamed to " + a[1] + ".", NamedTextColor.GREEN);
            }
            case "preset" -> {
                if (a.length < 2 || !gm.applyPreset(g, a[1])) {
                    say(p, "Usage: /guard preset <" + String.join("|", PRESETS) + ">", NamedTextColor.RED);
                    return;
                }
                gm.save();
                say(p, "Preset " + a[1].toLowerCase(Locale.ROOT) + " applied.", NamedTextColor.GREEN);
            }
            case "set" -> {
                if (a.length < 3) {
                    say(p, "Usage: /guard set <" + String.join("|", Settings.KEYS) + "> <value>", NamedTextColor.RED);
                    return;
                }
                String err = g.settings.set(a[1], a[2]);
                if (err != null) {
                    say(p, err, NamedTextColor.RED);
                    return;
                }
                gm.save();
                say(p, a[1].toLowerCase(Locale.ROOT) + " set to " + a[2] + ".", NamedTextColor.GREEN);
            }
            case "info" -> {
                say(p, g.name + " (" + g.mode + ", skin " + g.skin + ")", NamedTextColor.YELLOW);
                say(p, g.settings.describe(), NamedTextColor.GRAY);
            }
            case "gui" -> new GuardGui(gm, g).open(p);
            case "inv", "gear" -> new GearGui(gm, g).open(p);
            default -> say(p, "Unknown subcommand. Try /guard for the list.", NamedTextColor.RED);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String s : SUBS) if (s.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("preset")) {
            out.addAll(PRESETS);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("set")) {
            for (String s : Settings.KEYS) if (s.startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("set")
                && List.of("mobs", "players", "assist").contains(a[1].toLowerCase(Locale.ROOT))) {
            out.addAll(List.of("true", "false"));
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("select")) && sender instanceof Player p) {
            for (Guard g : gm.of(p.getUniqueId())) out.add(g.name);
        }
        return out;
    }
}
