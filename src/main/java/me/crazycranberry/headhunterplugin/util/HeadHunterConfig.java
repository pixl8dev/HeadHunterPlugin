package me.crazycranberry.headhunterplugin.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static me.crazycranberry.headhunterplugin.HeadHunterPlugin.getPlugin;
import static me.crazycranberry.headhunterplugin.HeadHunterPlugin.logger;

public class HeadHunterConfig {
    private YamlConfiguration originalConfig;
    // General settings
    private boolean display_score_in_player_list = true;
    private boolean log_rolls = true;
    private boolean broadcast_head_drops = true;
    private boolean show_kill_count = true;
    private boolean show_head_count = true;
    private boolean show_head_collection_summary = true;
    private boolean capitalize_mob_names = true;
    private boolean fix_claim_plugins = false;
    private String broadcast_permission = "";
    
    // Looting settings
    private boolean looting_enchantment_affects_drop_rate = false;
    private double looting_enchantment_drop_rate_multiplier = 0.1;
    private String head_drop;
    private String kill_count;
    private String head_count;
    private String heads;
    private String missing_mob_name;
    private String invalid_mob_name;
    private String head_owner_statement;
    private String head_secondary_statement;

    public HeadHunterConfig(YamlConfiguration config) {
        originalConfig = new YamlConfiguration();
        try {
            InputStream defaultChanceConfigStream = getPlugin().getResource("head_hunter_config.yml");
            assert defaultChanceConfigStream != null;
            InputStreamReader defaultChanceConfigReader = new InputStreamReader(defaultChanceConfigStream);
            originalConfig.load(defaultChanceConfigReader);
        } catch (InvalidConfigurationException | IOException e) {
            logger.info("[ ERROR ] An error occured while trying to load the (default) head_hunter_config file.");
            e.printStackTrace();
        }
        loadConfig(config);
    }

    public static void updateOutOfDateConfig(YamlConfiguration config, YamlConfiguration originalConfig, String configName) {
        boolean madeAChange = false;
        for (String key : originalConfig.getKeys(true)) {
            if (!config.isString(key) && !config.isConfigurationSection(key) && !config.isBoolean(key) && !config.isDouble(key) && !config.isInt(key)) {
                logger.info("[HeadHunterPlugin] The " + key + " is missing from " + configName + ", adding it now.");
                config.set(key, originalConfig.get(key));
                madeAChange = true;
            }
        }

        if (madeAChange) {
            try {
                config.save(getPlugin().getDataFolder() + "" + File.separatorChar + configName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void loadConfig(YamlConfiguration config) {
        // General settings
        display_score_in_player_list = config.getBoolean("general.display_score_in_player_list", 
            config.getBoolean("display_score_in_player_list", originalConfig.getBoolean("general.display_score_in_player_list", true)));
            
        log_rolls = config.getBoolean("general.log_rolls", 
            config.getBoolean("log_rolls", originalConfig.getBoolean("general.log_rolls", true)));
            
        broadcast_head_drops = config.getBoolean("general.broadcast_head_drops", 
            originalConfig.getBoolean("general.broadcast_head_drops", true));
            
        show_kill_count = config.getBoolean("general.show_kill_count", 
            originalConfig.getBoolean("general.show_kill_count", true));
            
        show_head_count = config.getBoolean("general.show_head_count", 
            originalConfig.getBoolean("general.show_head_count", true));
            
        show_head_collection_summary = config.getBoolean("general.show_head_collection_summary", 
            originalConfig.getBoolean("general.show_head_collection_summary", true));
            
        capitalize_mob_names = config.getBoolean("general.capitalize_mob_names",
            originalConfig.getBoolean("general.capitalize_mob_names", true));
            
        fix_claim_plugins = config.getBoolean("general.fix_claim_plugins",
            originalConfig.getBoolean("general.fix_claim_plugins", false));
            
        broadcast_permission = config.getString("general.broadcast_permission", 
            originalConfig.getString("general.broadcast_permission", ""));
        
        // Looting settings
        looting_enchantment_affects_drop_rate = config.getBoolean("looting_enchantment.affects_drop_rate", 
            config.getBoolean("looting_enchantment_affects_drop_rate", 
            originalConfig.getBoolean("looting_enchantment.affects_drop_rate", false)));
            
        looting_enchantment_drop_rate_multiplier = config.getDouble("looting_enchantment.drop_rate_multiplier", 
            config.getDouble("looting_enchantment.drop_rate_multiplier", 
            originalConfig.getDouble("looting_enchantment.drop_rate_multiplier", 0.1)));
        String head_drop_maybe = config.getString("messages.head_drop", originalConfig.getString("messages.head_drop"));
        if (head_drop_maybe.contains("{PLAYER_NAME}") && head_drop_maybe.contains("{MOB_NAME}")) {
            head_drop = injectColor(head_drop_maybe);
        } else {
            head_drop = injectColor(originalConfig.getString("messages.head_drop"));
        }
        String kill_count_maybe = config.getString("messages.kill_count", originalConfig.getString("messages.kill_count"));
        if (kill_count_maybe.contains("{PLAYER_NAME}") && kill_count_maybe.contains("{NUMBER}") && kill_count_maybe.contains("{MOB_NAME}")) {
            kill_count = injectColor(kill_count_maybe);
        } else {
            kill_count = injectColor(originalConfig.getString("messages.kill_count"));
        }
        String head_count_maybe = config.getString("messages.head_count", originalConfig.getString("messages.head_count"));
        if (head_count_maybe.contains("{PLAYER_NAME}") && head_count_maybe.contains("{NUMBER}") && head_count_maybe.contains("{MOB_NAME}")) {
            head_count = injectColor(head_count_maybe);
        } else {
            head_count = injectColor(originalConfig.getString("messages.head_count"));
        }
        String heads_maybe = config.getString("messages.heads", originalConfig.getString("messages.heads"));
        if (heads_maybe.contains("{PLAYER_NAME}") && heads_maybe.contains("{NUMBER}") && heads_maybe.contains("{TOTAL}") && heads_maybe.contains("{HEAD_LIST}")) {
            heads = injectColor(heads_maybe);
        } else {
            heads = injectColor(originalConfig.getString("messages.heads"));
        }
        missing_mob_name = injectColor(config.getString("messages.command_errors.missing_mob_name", originalConfig.getString("messages.command_errors.missing_mob_name")));
        invalid_mob_name = injectColor(config.getString("messages.command_errors.invalid_mob_name", originalConfig.getString("messages.command_errors.invalid_mob_name")));
        head_owner_statement = injectColor(config.getString("messages.head_lore.owner_statement", originalConfig.getString("messages.head_lore.owner_statement")));
        head_secondary_statement = injectColor(config.getString("messages.head_lore.secondary_statement", originalConfig.getString("messages.head_lore.secondary_statement")));
    }

    // General settings getters
    public boolean display_score() {
        return display_score_in_player_list;
    }
    
    public boolean shouldLogRolls() {
        return log_rolls;
    }
    
    public boolean shouldBroadcastHeadDrops() {
        return broadcast_head_drops;
    }
    
    public boolean shouldShowKillCount() {
        return show_kill_count;
    }
    
    public boolean shouldShowHeadCount() {
        return show_head_count;
    }
    
    public boolean shouldCapitalizeMobNames() {
        return capitalize_mob_names;
    }
    
    public boolean shouldShowHeadCollectionSummary() {
        return show_head_collection_summary;
    }
    
    public String getBroadcastPermission() {
        return broadcast_permission;
    }
    
    public boolean shouldFixClaimPlugins() {
        return fix_claim_plugins;
    }
    
    // Looting settings getters

    public boolean looting_matters() {
        return looting_enchantment_affects_drop_rate;
    }

    public double looting_multiplier() {
        return looting_enchantment_drop_rate_multiplier;
    }

    public boolean log_rolls() {
        return log_rolls;
    }

    public String head_drop_message(String playerName, String mobName) {
        return head_drop
                .replace("{PLAYER_NAME}", playerName)
                .replace("{MOB_NAME}", mobName);
    }

    public String kill_count_message(String playerName, String number, String mobName) {
        return kill_count
                .replace("{PLAYER_NAME}", playerName)
                .replace("{NUMBER}", number)
                .replace("{MOB_NAME}", mobName);
    }

    public String head_count_message(String playerName, String number, String mobName) {
        return head_count
                .replace("{PLAYER_NAME}", playerName)
                .replace("{NUMBER}", number)
                .replace("{MOB_NAME}", mobName);
    }

    public String heads_message(String playerName, String number, String total, String headList) {
        return heads
                .replace("{PLAYER_NAME}", playerName)
                .replace("{NUMBER}", number)
                .replace("{TOTAL}", total)
                .replace("{HEAD_LIST}", headList);
    }

    public String missing_mob_name_message() {
        return missing_mob_name;
    }

    public String invalid_mob_name_message() {
        return invalid_mob_name;
    }

    public String head_owner_statement(String ownerName, String mobName) {
        return head_owner_statement
                .replace("{PLAYER_NAME}", ownerName)
                .replace("{MOB_NAME}", mobName);
    }

    public String head_secondary_statement() {
        return head_secondary_statement;
    }

    public static String injectColor(String strToHaveColor) {
        return strToHaveColor
                .replace("{COLOR:AQUA}", ChatColor.AQUA.toString())
                .replace("{COLOR:BLACK}",  ChatColor.BLACK.toString())
                .replace("{COLOR:BLUE}",  ChatColor.BLUE.toString())
                .replace("{COLOR:BOLD}",  ChatColor.BOLD.toString())
                .replace("{COLOR:DARK_AQUA}",  ChatColor.DARK_AQUA.toString())
                .replace("{COLOR:DARK_BLUE}",  ChatColor.DARK_BLUE.toString())
                .replace("{COLOR:DARK_GRAY}",  ChatColor.DARK_GRAY.toString())
                .replace("{COLOR:DARK_GREEN}",  ChatColor.DARK_GREEN.toString())
                .replace("{COLOR:DARK_PURPLE}",  ChatColor.DARK_PURPLE.toString())
                .replace("{COLOR:DARK_RED}",  ChatColor.DARK_RED.toString())
                .replace("{COLOR:GOLD}",  ChatColor.GOLD.toString())
                .replace("{COLOR:GRAY}",  ChatColor.GRAY.toString())
                .replace("{COLOR:GREEN}",  ChatColor.GREEN.toString())
                .replace("{COLOR:ITALIC}",  ChatColor.ITALIC.toString())
                .replace("{COLOR:LIGHT_PURPLE}",  ChatColor.LIGHT_PURPLE.toString())
                .replace("{COLOR:MAGIC}",  ChatColor.MAGIC.toString())
                .replace("{COLOR:RED}",  ChatColor.RESET.toString())
                .replace("{COLOR:STRIKETHROUGH}",  ChatColor.STRIKETHROUGH.toString())
                .replace("{COLOR:UNDERLINE}",  ChatColor.UNDERLINE.toString())
                .replace("{COLOR:WHITE}",  ChatColor.WHITE.toString())
                .replace("{COLOR:YELLOW}",  ChatColor.YELLOW.toString());
    }
}
