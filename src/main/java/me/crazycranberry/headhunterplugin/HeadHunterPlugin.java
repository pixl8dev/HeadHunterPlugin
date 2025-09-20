package me.crazycranberry.headhunterplugin;

import com.google.gson.JsonParser;
import me.crazycranberry.headhunterplugin.util.HeadHunterConfig;
import me.crazycranberry.headhunterplugin.util.MobHeads;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Llama;
import org.bukkit.entity.TraderLlama;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Horse;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Item;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.logging.Logger;

import static me.crazycranberry.headhunterplugin.util.HeadHunterConfig.updateOutOfDateConfig;

public final class HeadHunterPlugin extends JavaPlugin implements Listener {
    private static HeadHunterPlugin plugin;
    public final static Logger logger = Logger.getLogger("Minecraft");
    private final NamespacedKey NAME_KEY = new NamespacedKey(this, "head_name");
    private final NamespacedKey LORE_KEY_1 = new NamespacedKey(this, "head_lore_1");
    private final NamespacedKey LORE_KEY_2 = new NamespacedKey(this, "head_lore_2");
    private static Field fieldProfileItem;
    HeadHunterConfig headHunterConfig;
    YamlConfiguration chanceConfig;
    YamlConfiguration defaultChanceConfig;
    YamlConfiguration mobNameTranslationConfig;
    YamlConfiguration defaultMobNameTranslationConfig;
    CommandManager commandManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        plugin = this;
        
        // Load configurations
        try {
            chanceConfig = loadConfig("chance.yml");
            defaultChanceConfig = getOriginalConfig("chance.yml");
            mobNameTranslationConfig = loadConfig("translations.yml");
            defaultMobNameTranslationConfig = getOriginalConfig("translations.yml");
            headHunterConfig = new HeadHunterConfig(loadConfig("config.yml"));
        } catch (Exception e) {
            logger.severe("Failed to load configuration files: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Register commands and events
        registerCommandManager();
        registerEvents();
    }

    @Override
    public void onDisable() {
        // Cleanup if needed
    }


    @EventHandler
    public void onEntityDeathEvent(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getKiller() != null) {
            Player killer = entity.getKiller();
            String name = getTrueVictimName(event);
            String mobName = translateMob(name);
            double dropRate = getDropRate(name, killer);
            
            // Log the drop if logging is enabled
            if (headHunterConfig().shouldLogRolls()) {
                logger.info(String.format("%s killed %s and got a head (%.2f%% drop rate).", 
                    killer.getName(), mobName, dropRate * 100));
            }
            
            // Drop the head
            entity.getWorld().dropItem(entity.getLocation(), makeSkull(name, killer));
            
            // Broadcast the head drop if enabled
            if (headHunterConfig().shouldBroadcastHeadDrops()) {
                String broadcastMessage = headHunterConfig().head_drop_message(killer.getName(), mobName + ChatColor.RESET);
                String permission = headHunterConfig().getBroadcastPermission();
                
                if (permission == null || permission.isEmpty()) {
                    // Broadcast to everyone if no permission is set
                    getServer().broadcastMessage(broadcastMessage);
                } else {
                    // Only broadcast to players with the required permission
                    for (Player player : getServer().getOnlinePlayers()) {
                        if (player.hasPermission(permission)) {
                            player.sendMessage(broadcastMessage);
                        }
                    }
                    // Also send to console
                    logger.info(ChatColor.stripColor(broadcastMessage));
                }
            }
        }
    }
    @EventHandler
    public void onBlockPlaceEvent(BlockPlaceEvent event) {
        ItemStack headItem = event.getItemInHand();
        ItemMeta meta = headItem.getItemMeta();
        if (headItem.getType() != Material.PLAYER_HEAD || meta == null) {
            return;
        }

        // Only process if this is a head placed by our plugin
        String name = meta.getPersistentDataContainer().get(NAME_KEY, PersistentDataType.STRING);
        if (name == null) {
            return; // Not one of our heads
        }
        
        List<String> lore = new ArrayList<>();
        Optional.ofNullable(meta.getPersistentDataContainer().get(LORE_KEY_1, PersistentDataType.STRING)).ifPresent(lore::add);
        Optional.ofNullable(meta.getPersistentDataContainer().get(LORE_KEY_2, PersistentDataType.STRING)).ifPresent(lore::add);
        
        Block block = event.getBlockPlaced();
        TileState skullState = (TileState) block.getState();
        PersistentDataContainer skullPDC = skullState.getPersistentDataContainer();
        
        // Store the head's data in the block
        skullPDC.set(NAME_KEY, PersistentDataType.STRING, name);
        if (!lore.isEmpty()) {
            skullPDC.set(LORE_KEY_1, PersistentDataType.STRING, lore.get(0));
            if (lore.size() > 1) {
                skullPDC.set(LORE_KEY_2, PersistentDataType.STRING, lore.get(1));
            }
        }
        skullState.update();
    }

    @EventHandler
    public void onBlockDropItemEvent(BlockDropItemEvent event) {
        BlockState blockState = event.getBlockState();
        if (blockState.getType() != Material.PLAYER_HEAD && blockState.getType() != Material.PLAYER_WALL_HEAD) {
            return;
        }
        TileState skullState = (TileState) blockState;
        PersistentDataContainer skullPDC = skullState.getPersistentDataContainer();
        String name = skullPDC.get(NAME_KEY, PersistentDataType.STRING);
        List<String> lore = new ArrayList<>();
        Optional.ofNullable(skullPDC.get(LORE_KEY_1, PersistentDataType.STRING)).ifPresent(lore::add);
        Optional.ofNullable(skullPDC.get(LORE_KEY_2, PersistentDataType.STRING)).ifPresent(lore::add);
        if (name == null) {
            return;
        }
        for (Item item: event.getItems()) {
            ItemStack itemstack = item.getItemStack();
            if (itemstack.getType() == Material.PLAYER_HEAD) {
                ItemMeta meta = itemstack.getItemMeta();
                if (meta == null) {
                    continue; // This shouldn't happen
                }
                meta.setDisplayName(name);
                meta.getPersistentDataContainer().set(NAME_KEY, PersistentDataType.STRING, name);
                if (!lore.isEmpty()) {
                    meta.setLore(lore);
                    meta.getPersistentDataContainer().set(LORE_KEY_1, PersistentDataType.STRING, lore.get(0));
                    if (lore.size() > 1) {
                        meta.getPersistentDataContainer().set(LORE_KEY_2, PersistentDataType.STRING, lore.get(1));
                    }
                }
                itemstack.setItemMeta(meta);
            }
        }
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(this, this);
    }


    private void registerCommandManager() {
        commandManager = new CommandManager(getServer(), chanceConfig, headHunterConfig);
        setCommandManager("headsreload", commandManager);
    }

    private void setCommandManager(String command, @NotNull CommandManager commandManager) {
        PluginCommand pc = getCommand(command);
        if (pc == null) {
            logger.info(String.format("[ ERROR ] - Error loading the %s command", command));
        } else {
            pc.setExecutor(commandManager);
        }
    }

    private YamlConfiguration loadConfig(String configName, BiConsumer<YamlConfiguration, String> backWardsCompatibleUpdateFunction) throws InvalidConfigurationException {
        File configFile = new File(getDataFolder() + "" + File.separatorChar + configName);
        if(!configFile.exists()){
            saveResource(configName, true);
            logger.info(String.format("%s not found! copied %s to %s", configName, configName, getDataFolder()));
        }
        YamlConfiguration config = new YamlConfiguration();
        YamlConfiguration originalConfig;
        try {
            config.load(configFile);
            originalConfig = getOriginalConfig(configName);
            backWardsCompatibleUpdateFunction.accept(config, configName);
            updateOutOfDateConfig(config, originalConfig, configName);
        } catch (InvalidConfigurationException | IOException e) {
            throw new InvalidConfigurationException("[ ERROR ] An error occured while trying to load " + configName);
        }
        return config;
    }

    private YamlConfiguration loadConfig(String configName) throws InvalidConfigurationException {
        return loadConfig(configName, (v1, v2) -> {});
    }

    private YamlConfiguration getOriginalConfig(String configName) throws IOException, InvalidConfigurationException {
        if (configName.equals("chance.yml")) {
            return defaultChanceConfig();
        } else if (configName.equals("translations.yml")) {
            return defaultMobNames();
        } else if (configName.equals("config.yml")) {
            return originalHeadHunterConfig();
        } else {
            throw new InvalidConfigurationException("Unknown config file: " + configName);
        }
    }

    private YamlConfiguration chanceConfig() {
        if (chanceConfig != null) {
            return chanceConfig;
        }
        try {
            chanceConfig = loadConfig("chance.yml", BackwardsCompatibilityUtils::updateChanceConfig);
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }
        return chanceConfig;
    }

    private YamlConfiguration defaultChanceConfig() {
        if (defaultChanceConfig != null) {
            return defaultChanceConfig;
        }
        defaultChanceConfig = new YamlConfiguration();
        try {
            InputStream defaultChanceConfigStream = getResource("chance.yml");
            assert defaultChanceConfigStream != null;
            InputStreamReader defaultChanceConfigReader = new InputStreamReader(defaultChanceConfigStream);
            defaultChanceConfig.load(defaultChanceConfigReader);
        } catch (InvalidConfigurationException | IOException e) {
            logger.info("[ ERROR ] An error occurred while trying to load the (default) chance file.");
            e.printStackTrace();
        }
        return defaultChanceConfig;
    }

    private YamlConfiguration defaultMobNames() {
        if (defaultMobNameTranslationConfig != null) {
            return defaultMobNameTranslationConfig;
        }
        defaultMobNameTranslationConfig = new YamlConfiguration();
        try {
            InputStream defaultChanceConfigStream = getResource("translations.yml");
            assert defaultChanceConfigStream != null;
            InputStreamReader defaultChanceConfigReader = new InputStreamReader(defaultChanceConfigStream);
            defaultMobNameTranslationConfig.load(defaultChanceConfigReader);
        } catch (InvalidConfigurationException | IOException e) {
            logger.info("[ ERROR ] An error occurred while trying to load the (default) mob name file.");
            e.printStackTrace();
        }
        return defaultMobNameTranslationConfig;
    }
    
    private YamlConfiguration originalHeadHunterConfig() {
        YamlConfiguration config = new YamlConfiguration();
        try {
            InputStream defaultConfigStream = getResource("config.yml");
            assert defaultConfigStream != null;
            InputStreamReader defaultConfigReader = new InputStreamReader(defaultConfigStream);
            config.load(defaultConfigReader);
        } catch (InvalidConfigurationException | IOException e) {
            logger.info("[ ERROR ] An error occurred while trying to load the (default) config file.");
            e.printStackTrace();
        }
        return config;
    }

    private YamlConfiguration mobNameTranslationConfig() {
        if (mobNameTranslationConfig != null) {
            return mobNameTranslationConfig;
        }
        try {
            mobNameTranslationConfig = loadConfig("translations.yml", BackwardsCompatibilityUtils::updateMobNameConfig);
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }
        return mobNameTranslationConfig;
    }

    private HeadHunterConfig headHunterConfig() {
        if (headHunterConfig != null) {
            return headHunterConfig;
        }
        YamlConfiguration headHunterYamlConfig = null;
        try {
            headHunterYamlConfig = loadConfig("config.yml");
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }
        headHunterConfig = new HeadHunterConfig(headHunterYamlConfig);
        return headHunterConfig;
    }

    private String getTrueVictimName(EntityDeathEvent event) {
        NamespacedKey variant;
        String name = event.getEntityType().name().replaceAll(" ", "_");
        switch(name) {
            case "AXOLOTL":
                return "AXOLOTL." + ((Axolotl) event.getEntity()).getVariant();
            case "CAT":
                return "CAT." + ((Cat) event.getEntity()).getCatType();
            case "FOX":
                return "FOX." + ((Fox) event.getEntity()).getFoxType();
            case "SHEEP":
                return "SHEEP." + ((Sheep) event.getEntity()).getColor();
            case "TRADER_LLAMA":
                return "TRADER_LLAMA." + ((TraderLlama) event.getEntity()).getColor();
            case "HORSE":
                return "HORSE." + ((Horse) event.getEntity()).getColor();
            case "LLAMA":
                return "LLAMA." + ((Llama) event.getEntity()).getColor();
            case "MUSHROOM_COW":
                return "MUSHROOM_COW." + ((MushroomCow) event.getEntity()).getVariant();
            case "PANDA":
                return "PANDA." + ((Panda) event.getEntity()).getMainGene();
            case "PARROT":
                return "PARROT." + ((Parrot) event.getEntity()).getVariant();
            case "RABBIT":
                return "RABBIT." + ((Rabbit) event.getEntity()).getRabbitType();
            case "FROG":
                return "FROG." + ((Frog) event.getEntity()).getVariant();
            case "WOLF":
                return "WOLF";
            case "COW":
                return "COW_TEMPERATE";
            case "PIG":
                return "PIG_TEMPERATE";
            case "CHICKEN":
                return "CHICKEN_TEMPERATE";
            default:
                return name;
        }
    }

    public ItemStack makeSkull(String headName, Player killer) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        MobHeads mobHead = MobHeads.valueOf(headName.replace(".", "_"));
        String textureCode = mobHead.getTexture();
        if (textureCode == null) {
            return item;
        }
        try {
            // Create a profile with a valid UUID and name
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "HeadHunter");
            PlayerTextures textures = profile.getTextures();
            String jsonTexture = new String(Base64.getDecoder().decode(textureCode), StandardCharsets.UTF_8);
            String url = JsonParser.parseString(jsonTexture).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            
            // Set the skin texture
            textures.setSkin(new URL(url));
            profile.setTextures(textures);
            
            // Set the owner profile if meta is not null
            if (meta != null) {
                meta.setOwnerProfile(profile);
            } else {
                getLogger().warning("Failed to set owner profile: ItemMeta is null");
                return item;
            }
        } catch (Exception e) {
            getLogger().warning("Failed to create player profile for head: " + e.getMessage());
            return item;
        }
        
        // Translate using the enum key (e.g., "cow_temperate") to support nested translation keys
        String translatedName = translateMob(mobHead.name().toLowerCase());
        meta.setDisplayName(translatedName);
        
        if (!headHunterConfig().shouldFixClaimPlugins()) {
            // Only add custom lore and NBT data if not in claim plugin compatibility mode
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + headHunterConfig().head_owner_statement(killer.getName(), translatedName) + ChatColor.RESET);
            lore.add(ChatColor.WHITE + headHunterConfig().head_secondary_statement() + ChatColor.RESET);
            meta.setLore(lore);
            
            // Store additional data in persistent data container
            meta.getPersistentDataContainer().set(NAME_KEY, PersistentDataType.STRING, translatedName);
            meta.getPersistentDataContainer().set(LORE_KEY_1, PersistentDataType.STRING, lore.get(0));
            meta.getPersistentDataContainer().set(LORE_KEY_2, PersistentDataType.STRING, lore.get(1));
        }
        
        item.setItemMeta(meta);
        return item;
    }

    public String refreshYmlConfigurations() {
        try {
            chanceConfig = loadConfig("chance.yml");
            mobNameTranslationConfig = loadConfig("translations.yml");
            headHunterConfig = new HeadHunterConfig(loadConfig("config.yml"));
            commandManager.reloadYmlConfigs(chanceConfig, headHunterConfig);
            return "Successfully loaded configs.";
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    public static HeadHunterPlugin getPlugin() {
        return plugin;
    }

    public String translateMob(String mobNameEnglish) {
        // Normalize the input into a consistent key form: lower-case with underscores
        String key = mobNameEnglish.toLowerCase().replace(' ', '_');

        String translatedName = null;

        // 1) Try direct string key (e.g., "cow_temperate")
        if (mobNameTranslationConfig().isString(key)) {
            translatedName = mobNameTranslationConfig().getString(key);
        }

        // 2) Try nested path (e.g., cow.temperate) if not found directly
        if (translatedName == null && key.contains("_")) {
            String[] parts = key.split("_", 2);
            String nestedPath = parts[0] + "." + parts[1];
            if (mobNameTranslationConfig().isString(nestedPath)) {
                translatedName = mobNameTranslationConfig().getString(nestedPath);
            }
        }

        // 3) Fallback to a humanized version of the key
        if (translatedName == null) {
            translatedName = key.replace('_', ' ');
        } else {
            // Replace underscores and dots in translation values with spaces
            translatedName = translatedName.replace('_', ' ').replace('.', ' ');
        }

        // Apply capitalization if enabled in config (capitalize first letter only to preserve styles)
        if (headHunterConfig != null && headHunterConfig.shouldCapitalizeMobNames()) {
            if (!translatedName.isEmpty()) {
                translatedName = translatedName.substring(0, 1).toUpperCase() +
                        (translatedName.length() > 1 ? translatedName.substring(1) : "");
            }
        }

        return translatedName;
    }

    public String translateMobToEnglish(String renamedMob) {
        ConfigurationSection translations = mobNameTranslationConfig().getConfigurationSection("mobs");
        if (translations == null) {
            return renamedMob;
        }
        for (String key : translations.getKeys(false)) {
            if (translations.getString(key, "").equalsIgnoreCase(renamedMob)) {
                return key.toUpperCase();
            }
        }
        return renamedMob;
    }
    
    private double getDropRate(String mobName, Player player) {
        // Get the base drop rate from the configuration
        double dropRate = chanceConfig().getDouble(mobName, 0.0);
        
        // If the mob isn't in the config, check if it's a variant (e.g., ZOMBIE.VILLAGER)
        if (dropRate == 0.0 && mobName.contains(".")) {
            String baseMob = mobName.split("\\.")[0];
            dropRate = chanceConfig().getDouble(baseMob, 0.0);
        }
        
        // If we still don't have a drop rate, use the default from the default config
        if (dropRate == 0.0) {
            dropRate = defaultChanceConfig().getDouble(mobName, 0.0);
            
            // If it's a variant, try the base mob in the default config
            if (dropRate == 0.0 && mobName.contains(".")) {
                String baseMob = mobName.split("\\.")[0];
                dropRate = defaultChanceConfig().getDouble(baseMob, 0.0);
            }
        }
        
        // Apply looting bonus if the player has a looting sword
        if (player.getInventory().getItemInMainHand() != null) {
            int lootingLevel = player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
            if (lootingLevel > 0) {
                // Each level of looting increases the drop rate by 0.5% (0.005)
                dropRate += (0.005 * lootingLevel);
                // Cap at 100% drop rate
                dropRate = Math.min(dropRate, 1.0);
            }
        }
        
        return dropRate;
    }
}
