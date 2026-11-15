package dev.royalguard;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/** Per-guard combat and behaviour tuning. */
public final class Settings {
    public static final List<String> KEYS = List.of("damage", "reach", "cooldown", "reaction", "accuracy",
            "detect", "speed", "follow", "teleport", "mobs", "players", "assist");

    public double damage = 6.0;
    public double reach = 3.0;
    public int cooldown = 12;
    public int reaction = 8;
    public double accuracy = 0.8;
    public double detect = 14.0;
    public double speed = 1.2;
    public double follow = 3.5;
    public double teleport = 28.0;
    public boolean mobs = true;
    public boolean players = false;
    public boolean assist = true;

    public void apply(ConfigurationSection s) {
        if (s == null) return;
        damage = s.getDouble("damage", damage);
        reach = s.getDouble("reach", reach);
        cooldown = s.getInt("cooldown", cooldown);
        reaction = s.getInt("reaction", reaction);
        accuracy = s.getDouble("accuracy", accuracy);
        detect = s.getDouble("detect", detect);
        speed = s.getDouble("speed", speed);
        follow = s.getDouble("follow", follow);
        teleport = s.getDouble("teleport", teleport);
        mobs = s.getBoolean("mobs", mobs);
        players = s.getBoolean("players", players);
        assist = s.getBoolean("assist", assist);
    }

    public void write(ConfigurationSection s) {
        s.set("damage", damage);
        s.set("reach", reach);
        s.set("cooldown", cooldown);
        s.set("reaction", reaction);
        s.set("accuracy", accuracy);
        s.set("detect", detect);
        s.set("speed", speed);
        s.set("follow", follow);
        s.set("teleport", teleport);
        s.set("mobs", mobs);
        s.set("players", players);
        s.set("assist", assist);
    }

    /** Returns null on success, or an error message. */
    public String set(String key, String value) {
        try {
            switch (key.toLowerCase(Locale.ROOT)) {
                case "damage" -> damage = clamp(Double.parseDouble(value), 0, 40);
                case "reach" -> reach = clamp(Double.parseDouble(value), 1, 6);
                case "cooldown" -> cooldown = (int) clamp(Double.parseDouble(value), 1, 100);
                case "reaction" -> reaction = (int) clamp(Double.parseDouble(value), 0, 100);
                case "accuracy" -> accuracy = clamp(Double.parseDouble(value), 0, 1);
                case "detect" -> detect = clamp(Double.parseDouble(value), 0, 48);
                case "speed" -> speed = clamp(Double.parseDouble(value), 0.3, 2.5);
                case "follow" -> follow = clamp(Double.parseDouble(value), 1.5, 16);
                case "teleport" -> teleport = clamp(Double.parseDouble(value), 8, 128);
                case "mobs" -> mobs = Boolean.parseBoolean(value);
                case "players" -> players = Boolean.parseBoolean(value);
                case "assist" -> assist = Boolean.parseBoolean(value);
                default -> {
                    return "Unknown setting '" + key + "'. Options: " + String.join(", ", KEYS);
                }
            }
        } catch (NumberFormatException e) {
            return "'" + value + "' is not a number.";
        }
        return null;
    }

    public String describe() {
        return String.format(Locale.ROOT,
                "damage=%.1f reach=%.1f cooldown=%d reaction=%d accuracy=%.2f detect=%.0f speed=%.1f follow=%.1f teleport=%.0f mobs=%s players=%s assist=%s",
                damage, reach, cooldown, reaction, accuracy, detect, speed, follow, teleport, mobs, players, assist);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
