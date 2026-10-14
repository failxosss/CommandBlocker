package cz.commandblocker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CommandBlocker - blocks selected commands per world and hides them from tab completion.
 *
 * @author Failxos
 */
public final class CommandBlocker extends JavaPlugin implements Listener {

    private static final String BYPASS_PERMISSION = "commandblocker.bypass";

    // world name (lowercase) -> blocked command names (lowercase)
    private final Map<String, Set<String>> blocked = new HashMap<>();
    private String blockedMessage = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void loadSettings() {
        blocked.clear();
        blockedMessage = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("blocked-message", ""));

        ConfigurationSection worlds = getConfig().getConfigurationSection("worlds");
        if (worlds == null) return;

        for (String worldName : worlds.getKeys(false)) {
            Set<String> set = new HashSet<>();
            for (String cmd : worlds.getStringList(worldName + ".blocked-commands")) {
                String c = cmd.trim().toLowerCase(Locale.ROOT);
                if (c.startsWith("/")) c = c.substring(1);
                if (!c.isEmpty()) set.add(c);
            }
            blocked.put(worldName.toLowerCase(Locale.ROOT), set);
        }
    }

    /** Strips the "namespace:" prefix and lowercases. */
    private static String baseName(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        int idx = l.indexOf(':');
        return idx >= 0 ? l.substring(idx + 1) : l;
    }

    /** True if the given command label is blocked in the given world. */
    private boolean isBlocked(String worldName, String label) {
        Set<String> global = blocked.get("*");
        Set<String> local = blocked.get(worldName.toLowerCase(Locale.ROOT));
        if ((global == null || global.isEmpty()) && (local == null || local.isEmpty())) {
            return false;
        }

        String base = baseName(label);
        if (matches(global, base) || matches(local, base)) return true;

        // Resolve real command so that aliases are covered too.
        Command command = Bukkit.getCommandMap().getCommand(base);
        if (command != null) {
            String name = command.getName().toLowerCase(Locale.ROOT);
            if (matches(global, name) || matches(local, name)) {
                return true;
            }
            for (String alias : command.getAliases()) {
                String a = alias.toLowerCase(Locale.ROOT);
                if (matches(global, a) || matches(local, a)) return true;
            }
        }
        return false;
    }

    private static boolean matches(Set<String> set, String name) {
        return set != null && set.contains(name);
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
