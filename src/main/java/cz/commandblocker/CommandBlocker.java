package cz.commandblocker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * CommandBlocker - blocks selected commands per world and hides them from tab completion.
 * Supports "*" wildcards in command names (e.g. "tpa*") and per-world allow lists
 * (e.g. block "*" and allow only a few commands).
 *
 * @author Failxos
 */
public final class CommandBlocker extends JavaPlugin implements Listener {

    private static final String BYPASS_PERMISSION = "commandblocker.bypass";

    // world name (lowercase) -> blocked command patterns
    private final Map<String, List<Pattern>> blocked = new HashMap<>();
    // world name (lowercase) -> allowed command patterns (exceptions to blocked)
    private final Map<String, List<Pattern>> allowed = new HashMap<>();
    private String blockedMessage = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void loadSettings() {
        blocked.clear();
        allowed.clear();
        blockedMessage = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("blocked-message", ""));

        ConfigurationSection worlds = getConfig().getConfigurationSection("worlds");
        if (worlds == null) return;

        for (String worldName : worlds.getKeys(false)) {
            String key = worldName.toLowerCase(Locale.ROOT);
            blocked.put(key, readPatterns(worlds.getStringList(worldName + ".blocked-commands")));
            allowed.put(key, readPatterns(worlds.getStringList(worldName + ".allowed-commands")));
        }
    }

    private static List<Pattern> readPatterns(List<String> entries) {
        List<Pattern> patterns = new ArrayList<>();
        for (String entry : entries) {
            String c = entry.trim().toLowerCase(Locale.ROOT);
            if (c.startsWith("/")) c = c.substring(1);
            if (!c.isEmpty()) patterns.add(toPattern(c));
        }
        return patterns;
    }

    /** Converts a command entry with optional "*" wildcards into a regex pattern. */
    private static Pattern toPattern(String entry) {
        String[] parts = entry.split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) regex.append(".*");
            if (!parts[i].isEmpty()) regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex.toString());
    }

    /** Strips the "namespace:" prefix and lowercases. */
    private static String baseName(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        int idx = l.indexOf(':');
        return idx >= 0 ? l.substring(idx + 1) : l;
    }

    private static boolean isEmpty(List<Pattern> list) {
        return list == null || list.isEmpty();
    }

    private static boolean matchesAny(List<Pattern> a, List<Pattern> b, List<String> names) {
        return matches(a, names) || matches(b, names);
    }

    private static boolean matches(List<Pattern> patterns, List<String> names) {
        if (patterns == null) return false;
        for (Pattern p : patterns) {
            for (String name : names) {
                if (p.matcher(name).matches()) return true;
            }
        }
        return false;
    }

    /** True if the given command label is blocked in the given world. */
    private boolean isBlocked(String worldName, String label) {
        String world = worldName.toLowerCase(Locale.ROOT);
        List<Pattern> blockedGlobal = blocked.get("*");
        List<Pattern> blockedLocal = blocked.get(world);
        if (isEmpty(blockedGlobal) && isEmpty(blockedLocal)) {
            return false;
        }

        String base = baseName(label);
        Command command = Bukkit.getCommandMap().getCommand(base);

        // Never lock out this plugin's own command (/cb reload).
        if (command instanceof PluginCommand pluginCommand && pluginCommand.getPlugin() == this) {
            return false;
        }

        // The typed name plus the real command name and all its aliases.
        List<String> names = new ArrayList<>();
        names.add(base);
        if (command != null) {
            names.add(command.getName().toLowerCase(Locale.ROOT));
            for (String alias : command.getAliases()) {
                names.add(alias.toLowerCase(Locale.ROOT));
            }
        }

        if (!matchesAny(blockedGlobal, blockedLocal, names)) {
            return false;
        }
        // Allowed commands are exceptions and always win over blocked ones.
        return !matchesAny(allowed.get("*"), allowed.get(world), names);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_PERMISSION)) return;

        String message = event.getMessage();
        if (message.length() < 2) return;
        String label = message.substring(1).split("\\s+", 2)[0];

        if (isBlocked(player.getWorld().getName(), label)) {
            event.setCancelled(true);
            if (!blockedMessage.isEmpty()) {
                player.sendMessage(blockedMessage);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_PERMISSION)) return;

        String world = player.getWorld().getName();
        Iterator<String> it = event.getCommands().iterator();
        while (it.hasNext()) {
            if (isBlocked(world, it.next())) {
                it.remove();
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Re-send the command list so tab completion matches the new world.
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, player::updateCommands);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadSettings();
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.updateCommands();
            }
            sender.sendMessage(ChatColor.GREEN + "CommandBlocker config reloaded.");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " reload");
        return true;
    }
}