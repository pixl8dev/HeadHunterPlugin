package me.crazycranberry.headhunterplugin;

import me.crazycranberry.headhunterplugin.util.HeadHunterConfig;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static me.crazycranberry.headhunterplugin.HeadHunterPlugin.getPlugin;

public class CommandManager implements CommandExecutor, TabCompleter {
    Server server;
    YamlConfiguration chanceConfig;
    HeadHunterConfig headHunterConfig;

    public CommandManager(@NotNull Server server, @NotNull YamlConfiguration chanceConfig, @NotNull HeadHunterConfig headHunterConfig) {
        this.server = server;
        this.chanceConfig = chanceConfig;
        this.headHunterConfig = headHunterConfig;
    }

    public void reloadYmlConfigs(@NotNull YamlConfiguration chanceConfig, @NotNull HeadHunterConfig headHunterConfig) {
        this.chanceConfig = chanceConfig;
        this.headHunterConfig = headHunterConfig;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("headsreload")) {
            String refreshResponse = getPlugin().refreshYmlConfigurations();
            if (sender instanceof Player) {
                Player p = (Player) sender;
                p.sendMessage(refreshResponse);
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}
