package com.plexon.gpflags.text;

import com.plexon.gpflags.flag.ClaimFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class Messages {
    private final JavaPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private YamlConfiguration yaml;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.isFile()) plugin.saveResource("messages.yml", false);
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public Component component(String key, Map<String, String> values) {
        String prefix = yaml.getString("prefix", "<green>CLAIMS</green> <dark_gray>»</dark_gray> <gray>");
        String raw = yaml.getString(key, key).replace("%prefix%", prefix);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            raw = raw.replace("%" + entry.getKey() + "%", escape(entry.getValue()));
        }
        return mini.deserialize(raw);
    }

    public Component raw(String miniMessage) { return mini.deserialize(miniMessage == null ? "" : miniMessage); }

    public void send(Player player, String key) { send(player, key, Map.of()); }
    public void send(Player player, String key, Map<String, String> values) { player.sendMessage(component(key, values)); }

    public String flagName(ClaimFlag flag) { return yaml.getString("flag-names." + flag.key(), flag.key()); }

    public void saveDefaultsIfMissing() throws IOException {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
    }

    /** User-derived values are converted to plain MiniMessage-safe text. */
    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("<", "\\<").replace(">", "\\>");
    }
}
