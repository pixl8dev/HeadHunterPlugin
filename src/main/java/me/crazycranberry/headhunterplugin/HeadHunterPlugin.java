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
            // Load with backwards-compatibility transformations where needed
            chanceConfig = loadConfig("chance.yml", BackwardsCompatibilityUtils::updateChanceConfig);
            defaultChanceConfig = getOriginalConfig("chance.yml");
            mobNameTranslationConfig = loadConfig("translations.yml", BackwardsCompatibilityUtils::updateMobNameConfig);
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
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        if (entity instanceof Player) {
            // Handle player death
            Player victim = (Player) entity;
            String playerHeadName = "PLAYER_" + victim.getUniqueId();
            String displayName = victim.getName() + "'s Head";
            double dropRate = getDropRate("player", killer);
            
            // Log the drop if logging is enabled
            double roll = Math.random();
            boolean shouldDrop = roll < dropRate;
            
            if (headHunterConfig().shouldLogRolls()) {
                logger.info(String.format("Player head roll: killer=%s victim=%s dropRate=%.4f (%.2f%%) roll=%.4f -> %s",
                        killer.getName(), victim.getName(), dropRate, dropRate * 100, roll, 
                        shouldDrop ? "DROP" : "NO DROP"));
            }

            if (shouldDrop) {
                // Drop the player head
                entity.getWorld().dropItem(entity.getLocation(), makePlayerHead(victim, killer));

                // Broadcast the head drop if enabled
                if (headHunterConfig().shouldBroadcastHeadDrops()) {
                    String broadcastMessage = headHunterConfig().head_drop_message(
                        killer.getName(), 
                        ChatColor.YELLOW + victim.getName() + "'s Head" + ChatColor.RESET
                    );
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
        } else {
            // Handle mob death (original code)
            String name = getTrueVictimName(event);
            String mobName = translateMob(name);
            double dropRate = getDropRate(name, killer);
            
            // Log the drop if logging is enabled
            double roll = Math.random();
            boolean shouldDrop = roll < dropRate;
            
            if (headHunterConfig().shouldLogRolls()) {
                logger.info(String.format("Mob head roll: player=%s mob=%s dropRate=%.4f (%.2f%%) roll=%.4f -> %s",
                        killer.getName(), mobName, dropRate, dropRate * 100, roll, 
                        shouldDrop ? "DROP" : "NO DROP"));
            }

            if (shouldDrop) {
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
                return "COW_" + ((Cow) event.getEntity()).getVariant().getKey().getKey().toUpperCase();
            case "PIG":
                return "PIG_" + ((Pig) event.getEntity()).getVariant().getKey().getKey().toUpperCase();
            case "CHICKEN":
                return "CHICKEN_" + ((Chicken) event.getEntity()).getVariant().getKey().getKey().toUpperCase();
            default:
                return name;
        }
    }

    /**
     * Creates a player head with the victim's skin and appropriate metadata
     * @param victim The player whose head will be created
     * @param killer The player who killed the victim
     * @return An ItemStack representing the player's head
     */
    public ItemStack makePlayerHead(Player victim, Player killer) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        if (meta == null) {
            return head;
        }
        
        // Set the owner of the head to the victim
        meta.setOwningPlayer(victim);
        
        // Set the display name to the victim's name + 's Head
        String displayName = ChatColor.YELLOW + victim.getName() + "'s Head";
        meta.setDisplayName(displayName);
        
        // Add lore with killer information if enabled
        if (!headHunterConfig().shouldFixClaimPlugins()) {
            List<String> lore = new ArrayList<>();
            String ownerInfo = headHunterConfig().head_owner_statement(killer.getName(), "");
            String secondaryInfo = headHunterConfig().head_secondary_statement();
            
            // Store the victim's name in the persistent data container
            meta.getPersistentDataContainer().set(NAME_KEY, PersistentDataType.STRING, displayName);
            meta.getPersistentDataContainer().set(LORE_KEY_1, PersistentDataType.STRING, ownerInfo);
            meta.getPersistentDataContainer().set(LORE_KEY_2, PersistentDataType.STRING, secondaryInfo);
            
            // Add a single line of lore that shows it's a player head
            lore.add(ChatColor.GRAY + "Player Head: " + victim.getName());
            meta.setLore(lore);
        }
        
        head.setItemMeta(meta);
        return head;
    }
    
    /**
     * Creates a mob head with the specified mob type and killer information
     * @param headName The name of the mob head to create
     * @param killer The player who killed the mob
     * @return An ItemStack representing the mob's head
     */
    public ItemStack makeSkull(String headName, Player killer) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        try {
            MobHeads mobHead = MobHeads.valueOf(headName.replace(".", "_"));
            String textureCode = mobHead.getTexture();
            // fix invalid textures
            if (textureCode == null || textureCode.isBlank()) {
                return item;
            }
            
            // Create a consistent UUID based on the mob type to ensure stackability
            UUID consistentUuid = UUID.nameUUIDFromBytes(("HeadHunter_" + mobHead.name()).getBytes(StandardCharsets.UTF_8));
            
            // Create a profile with a consistent UUID based on the mob type
            PlayerProfile profile = Bukkit.createPlayerProfile(consistentUuid, "HeadHunter");
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
            
            // Translate using the enum key (e.g., "cow_temperate") to support nested translation keys
            // Use a consistent display name without the killer's name for stackability
            String displayName = translateMob(mobHead.name().toLowerCase());
            meta.setDisplayName(displayName);
            
            if (!headHunterConfig().shouldFixClaimPlugins()) {
                // Use a simplified lore that's consistent for each mob type to ensure stackability
                List<String> lore = new ArrayList<>();
                // Store the killer's name in a separate line that won't prevent stacking
                String ownerInfo = headHunterConfig().head_owner_statement(killer.getName(), "");
                String secondaryInfo = headHunterConfig().head_secondary_statement();
                
                // Store the actual mob name in the persistent data container
                meta.getPersistentDataContainer().set(NAME_KEY, PersistentDataType.STRING, displayName);
                meta.getPersistentDataContainer().set(LORE_KEY_1, PersistentDataType.STRING, ownerInfo);
                meta.getPersistentDataContainer().set(LORE_KEY_2, PersistentDataType.STRING, secondaryInfo);
                
                // Add a single line of lore that shows it's a mob head
                lore.add(ChatColor.GRAY + "Mob Head: " + displayName);
                meta.setLore(lore);
            }
            
            // Set the item meta and amount to 1 (this ensures consistent stacking)
            item.setItemMeta(meta);
            item.setAmount(1);
        } catch (Exception e) {
            getLogger().warning("Failed to create mob head: " + e.getMessage());
        }
        
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
        // Normalize to config key format
        // Examples:
        //  - "AXOLOTL.LUCY" -> "axolotl.lucy"
        //  - "COW_TEMPERATE" -> "cow.temperate"
        //  - "ZOMBIE" -> "zombie"
        String raw = mobName.toLowerCase();
        String normalized;
        if (raw.contains(".")) {
            normalized = raw; // already variant format
        } else if (raw.contains("_")) {
            String[] parts = raw.split("_", 2);
            Set<String> nestedVariantBases = new HashSet<>(Arrays.asList(
                    "axolotl", "cat", "chicken", "cow", "fox", "frog", "horse", "llama",
                    "mushroom_cow", "panda", "parrot", "pig", "rabbit", "sheep", "trader_llama",
                    "wolf"
            ));
            if (parts.length == 2 && nestedVariantBases.contains(parts[0])) {
                normalized = parts[0] + "." + parts[1];
            } else {
                normalized = raw; // keep flat (e.g., zombie_pigman)
            }
        } else {
            normalized = raw;
        }

        String fullPath = "chance_percent." + normalized;
        String baseMob = normalized.contains(".") ? normalized.substring(0, normalized.indexOf('.')) : normalized;

        double dropRate = 0.0;
        String matchedPath = null;

        // 1) Primary: under chance_percent
        dropRate = chanceConfig().getDouble(fullPath, 0.0);
        if (dropRate > 0.0) matchedPath = fullPath;
        if (dropRate == 0.0 && normalized.contains(".")) {
            String basePath = "chance_percent." + baseMob;
            dropRate = chanceConfig().getDouble(basePath, 0.0);
            if (dropRate > 0.0) matchedPath = basePath;
        }

        // 2) Secondary: root-level keys (back-compat)
        if (dropRate == 0.0) {
            dropRate = chanceConfig().getDouble(normalized, 0.0);
            if (dropRate > 0.0) matchedPath = normalized;
        }
        if (dropRate == 0.0 && normalized.contains(".")) {
            dropRate = chanceConfig().getDouble(baseMob, 0.0);
            if (dropRate > 0.0) matchedPath = baseMob;
        }

        // 3) Default resource fallbacks
        if (dropRate == 0.0) {
            dropRate = defaultChanceConfig().getDouble(fullPath, 0.0);
            if (dropRate > 0.0) matchedPath = "(default) " + fullPath;
        }
        if (dropRate == 0.0 && normalized.contains(".")) {
            String basePath = "chance_percent." + baseMob;
            dropRate = defaultChanceConfig().getDouble(basePath, 0.0);
            if (dropRate > 0.0) matchedPath = "(default) " + basePath;
        }
        if (dropRate == 0.0) {
            dropRate = defaultChanceConfig().getDouble(normalized, 0.0);
            if (dropRate > 0.0) matchedPath = "(default) " + normalized;
        }
        if (dropRate == 0.0 && normalized.contains(".")) {
            dropRate = defaultChanceConfig().getDouble(baseMob, 0.0);
            if (dropRate > 0.0) matchedPath = "(default) " + baseMob;
        }

        // Apply Looting enchantment if enabled
        if (player != null && headHunterConfig().looting_matters()) {
            Enchantment lootingEnchant = Enchantment.getByKey(NamespacedKey.minecraft("looting"));
            int lootingLevel = 0;
            if (lootingEnchant != null) {
                ItemStack weapon = player.getInventory().getItemInMainHand();
                if (weapon != null) {
                    lootingLevel = weapon.getEnchantmentLevel(lootingEnchant);
                }
            }
            if (lootingLevel > 0) {
                double multiplier = headHunterConfig().looting_multiplier();
                dropRate = dropRate * (1.0 + (lootingLevel * multiplier));
            }
        }

        // Debug logging if enabled
        if (headHunterConfig().shouldLogRolls()) {
            logger.info(String.format("Resolved key: %s | Base: %s | Matched: %s | Drop rate for %s: %.4f (%.2f%%)",
                    fullPath, baseMob, matchedPath, mobName, dropRate, dropRate * 100));
        }

        return dropRate;
    }
}
