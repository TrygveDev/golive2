package dev.trygve.golive.gui;

import dev.trygve.golive.GoLive;
import dev.trygve.golive.managers.LiveStatusManager;
import dev.trygve.golive.managers.MessageManager;
import dev.trygve.golive.utils.ColorUtils;
import dev.trygve.golive.utils.SoundUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the GoLive GUI with modern features
 */
public class GuiManager {
    
    private final GoLive plugin;
    private final LiveStatusManager liveStatusManager;
    private final MessageManager messageManager;
    
    private final Map<UUID, Long> clickCooldowns;
    private final Map<UUID, BukkitTask> animationTasks;
    
    /**
     * Create a new GuiManager
     * 
     * @param plugin The plugin instance
     * @param liveStatusManager The live status manager
     * @param messageManager The message manager
     */
    public GuiManager(@NotNull GoLive plugin, @NotNull LiveStatusManager liveStatusManager, 
                     @NotNull MessageManager messageManager) {
        this.plugin = plugin;
        this.liveStatusManager = liveStatusManager;
        this.messageManager = messageManager;
        this.clickCooldowns = new HashMap<>();
        this.animationTasks = new HashMap<>();
    }
    
    /**
     * Initialize the GuiManager
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    @NotNull
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                plugin.getLogger().info("GuiManager initialized successfully!");
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize GuiManager: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    /**
     * Open the GoLive GUI for a player
     * 
     * @param player The player
     */
    public void openGui(@NotNull Player player) {
        if (!plugin.getConfig().getBoolean("gui.enabled", true)) {
            messageManager.sendMessage(player, "general.invalid-arguments");
            return;
        }
        
        Inventory gui = createGui(player);
        player.openInventory(gui);
        
        // Play open sound
        if (plugin.getConfig().getBoolean("gui.settings.open-sound.enabled", true)) {
            String soundType = plugin.getConfig().getString("gui.settings.open-sound.type", "ui.button.click");
            float volume = (float) plugin.getConfig().getDouble("gui.settings.open-sound.volume", 0.5);
            float pitch = (float) plugin.getConfig().getDouble("gui.settings.open-sound.pitch", 1.0);
            SoundUtils.playSound(player, soundType, volume, pitch);
        }
        
        // Start animations if enabled
        if (plugin.getConfig().getBoolean("gui.animations.enabled", true)) {
            startAnimations(player);
        }
    }
    
    /**
     * Create the GoLive GUI
     * 
     * @param player The player
     * @return The GUI inventory
     */
    @NotNull
    private Inventory createGui(@NotNull Player player) {
        int size = plugin.getConfig().getInt("gui.size", 27);
        String title = plugin.getConfig().getString("gui.title", "<gradient:#00ff00:#ff0000>GoLive</gradient> <gray>Menu</gray>");
        
        Component titleComponent = ColorUtils.toComponent(title);
        Inventory gui = Bukkit.createInventory(null, size, titleComponent);
        
        // Add background items
        if (plugin.getConfig().getBoolean("gui.background.enabled", true)) {
            addBackgroundItems(gui);
        }
        
        // Add decorative items
        if (plugin.getConfig().getBoolean("gui.decorations.enabled", true)) {
            addDecorativeItems(gui);
        }
        
        // Add buttons
        addButtons(gui, player);
        
        return gui;
    }
    
    /**
     * Add background items to the GUI
     * 
     * @param gui The GUI inventory
     */
    private void addBackgroundItems(@NotNull Inventory gui) {
        Material material = Material.valueOf(plugin.getConfig().getString("gui.background.item.material", "GRAY_STAINED_GLASS_PANE"));
        int customModelData = plugin.getConfig().getInt("gui.background.item.custom-model-data", 1000);
        String name = plugin.getConfig().getString("gui.background.item.name", " ");
        
        GuiButton backgroundButton = new GuiButton(
            material, 0, name, new ArrayList<>(), customModelData, false, null, null, GuiButton.ButtonType.BACKGROUND
        );
        
        ItemStack backgroundItem = backgroundButton.createItemStack();
        
        // Fill empty slots with background items
        List<Integer> backgroundSlots = plugin.getConfig().getIntegerList("gui.background.slots");
        if (backgroundSlots.isEmpty()) {
            // Fill all empty slots
            for (int i = 0; i < gui.getSize(); i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, backgroundItem);
                }
            }
        } else {
            // Fill specified slots
            for (int slot : backgroundSlots) {
                if (slot >= 0 && slot < gui.getSize()) {
                    gui.setItem(slot, backgroundItem);
                }
            }
        }
    }
    
    /**
     * Add decorative items to the GUI
     * 
     * @param gui The GUI inventory
     */
    private void addDecorativeItems(@NotNull Inventory gui) {
        var decorationsSection = plugin.getConfig().getConfigurationSection("gui.decorations.items");
        if (decorationsSection == null) {
            return;
        }
        
        for (String key : decorationsSection.getKeys(false)) {
            var itemSection = decorationsSection.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }
            
            int slot = itemSection.getInt("slot", 0);
            Material material = Material.valueOf(itemSection.getString("material", "LIME_STAINED_GLASS_PANE"));
            int customModelData = itemSection.getInt("custom-model-data", 1005);
            String name = itemSection.getString("name", " ");
            boolean glow = itemSection.getBoolean("glow", true);
            
            GuiButton decorationButton = new GuiButton(
                material, slot, name, new ArrayList<>(), customModelData, glow, null, null, GuiButton.ButtonType.DECORATION
            );
            
            gui.setItem(slot, decorationButton.createItemStack());
        }
    }
    
    /**
     * Add buttons to the GUI
     * 
     * @param gui The GUI inventory
     * @param player The player
     */
    private void addButtons(@NotNull Inventory gui, @NotNull Player player) {
        // Live button
        addButton(gui, "live", GuiButton.ButtonType.LIVE, player);
        
        // Offline button
        addButton(gui, "offline", GuiButton.ButtonType.OFFLINE, player);
        
        // Edit link button
        addButton(gui, "edit-link", GuiButton.ButtonType.EDIT_LINK, player);
        
        // Close button
        addButton(gui, "close", GuiButton.ButtonType.CLOSE, player);
    }
    
    /**
     * Add a button to the GUI
     * 
     * @param gui The GUI inventory
     * @param buttonKey The button configuration key
     * @param type The button type
     * @param player The player
     */
    private void addButton(@NotNull Inventory gui, @NotNull String buttonKey, @NotNull GuiButton.ButtonType type, @NotNull Player player) {
        var buttonsSection = plugin.getConfig().getConfigurationSection("gui.buttons." + buttonKey);
        if (buttonsSection == null) {
            return;
        }
        
        int slot = buttonsSection.getInt("slot", 0);
        Material material = Material.valueOf(buttonsSection.getString("material", "STONE"));
        int customModelData = buttonsSection.getInt("custom-model-data", 0);
        boolean glow = buttonsSection.getBoolean("glow", false);
        String name = buttonsSection.getString("name", "Button");
        List<String> lore = buttonsSection.getStringList("lore");
        
        // Get skull texture if using player head
        String skullTexture = null;
        String skullUrl = null;
        if (material == Material.PLAYER_HEAD) {
            skullTexture = buttonsSection.getString("skull-texture");
            skullUrl = buttonsSection.getString("skull-url");
        }
        
        GuiButton button = new GuiButton(
            material, slot, name, lore, customModelData, glow, skullTexture, skullUrl, type
        );
        
        gui.setItem(slot, button.createItemStack());
    }
    
    /**
     * Handle button click
     * 
     * @param player The player
     * @param slot The clicked slot
     * @return True if the click was handled
     */
    public boolean handleButtonClick(@NotNull Player player, int slot) {
        // Check click cooldown
        if (isOnClickCooldown(player)) {
            return true;
        }
        
        // Get button configuration for this slot
        GuiButton.ButtonType buttonType = getButtonTypeForSlot(slot);
        if (buttonType == null) {
            return false;
        }
        
        // Set click cooldown
        setClickCooldown(player);
        
        // Play click sound
        playButtonClickSound(player, buttonType);
        
        // Handle button action
        switch (buttonType) {
            case LIVE -> handleLiveButton(player);
            case OFFLINE -> handleOfflineButton(player);
            case EDIT_LINK -> handleEditLinkButton(player);
            case CLOSE -> handleCloseButton(player);
            default -> {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get button type for a slot
     * 
     * @param slot The slot
     * @return The button type, or null if not a button
     */
    @Nullable
    private GuiButton.ButtonType getButtonTypeForSlot(int slot) {
        var buttonsSection = plugin.getConfig().getConfigurationSection("gui.buttons");
        if (buttonsSection == null) {
            return null;
        }
        
        for (String buttonKey : buttonsSection.getKeys(false)) {
            var buttonSection = buttonsSection.getConfigurationSection(buttonKey);
            if (buttonSection != null && buttonSection.getInt("slot", -1) == slot) {
                return switch (buttonKey) {
                    case "live" -> GuiButton.ButtonType.LIVE;
                    case "offline" -> GuiButton.ButtonType.OFFLINE;
                    case "edit-link" -> GuiButton.ButtonType.EDIT_LINK;
                    case "close" -> GuiButton.ButtonType.CLOSE;
                    default -> null;
                };
            }
        }
        
        return null;
    }
    
    /**
     * Handle live button click
     * 
     * @param player The player
     */
    private void handleLiveButton(@NotNull Player player) {
        // Check if player has permission
        if (!player.hasPermission("golive.live")) {
            messageManager.sendMessage(player, "general.no-permission");
            player.closeInventory();
            return;
        }
        
        // Check if player has a stream link
        String streamLink = liveStatusManager.getPlayerStreamLink(player.getUniqueId());
        if (streamLink == null) {
            messageManager.sendMessage(player, "stream-link.not-set");
            player.closeInventory();
            return;
        }
        
        // Set player live
        liveStatusManager.setPlayerLiveStatus(player.getUniqueId(), true, streamLink);
        player.closeInventory();
    }
    
    /**
     * Handle offline button click
     * 
     * @param player The player
     */
    private void handleOfflineButton(@NotNull Player player) {
        // Check if player has permission
        if (!player.hasPermission("golive.offline")) {
            messageManager.sendMessage(player, "general.no-permission");
            player.closeInventory();
            return;
        }
        
        // Check if player is actually live
        if (!liveStatusManager.isPlayerLive(player.getUniqueId())) {
            messageManager.sendMessage(player, "offline.not-live");
            player.closeInventory();
            return;
        }
        
        // Set player offline
        liveStatusManager.setPlayerLiveStatus(player.getUniqueId(), false, null);
        player.closeInventory();
    }
    
    /**
     * Handle edit link button click
     * 
     * @param player The player
     */
    private void handleEditLinkButton(@NotNull Player player) {
        // Check if player has permission
        if (!player.hasPermission("golive.live")) {
            messageManager.sendMessage(player, "general.no-permission");
            player.closeInventory();
            return;
        }
        
        // Close GUI and send instructions
        player.closeInventory();
        messageManager.sendMessage(player, "stream-link.current", "%link%", 
            liveStatusManager.getPlayerStreamLink(player.getUniqueId()) != null ? 
            liveStatusManager.getPlayerStreamLink(player.getUniqueId()) : "Not set");
        messageManager.sendMessage(player, "stream-link.set-instructions");
    }
    
    /**
     * Handle close button click
     * 
     * @param player The player
     */
    private void handleCloseButton(@NotNull Player player) {
        player.closeInventory();
    }
    
    /**
     * Play button click sound
     * 
     * @param player The player
     * @param buttonType The button type
     */
    private void playButtonClickSound(@NotNull Player player, @NotNull GuiButton.ButtonType buttonType) {
        String soundKey = "gui.buttons." + buttonType.name().toLowerCase().replace("_", "-") + ".click-sound";
        
        if (plugin.getConfig().getBoolean(soundKey + ".enabled", true)) {
            String soundType = plugin.getConfig().getString(soundKey + ".type", "ui.button.click");
            float volume = (float) plugin.getConfig().getDouble(soundKey + ".volume", 0.5);
            float pitch = (float) plugin.getConfig().getDouble(soundKey + ".pitch", 1.0);
            SoundUtils.playSound(player, soundType, volume, pitch);
        }
    }
    
    /**
     * Check if player is on click cooldown
     * 
     * @param player The player
     * @return True if on cooldown
     */
    private boolean isOnClickCooldown(@NotNull Player player) {
        if (!plugin.getConfig().getBoolean("gui.advanced.click-cooldown.enabled", true)) {
            return false;
        }
        
        Long cooldownEnd = clickCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) {
            return false;
        }
        
        if (System.currentTimeMillis() > cooldownEnd) {
            clickCooldowns.remove(player.getUniqueId());
            return false;
        }
        
        return true;
    }
    
    /**
     * Set click cooldown for player
     * 
     * @param player The player
     */
    private void setClickCooldown(@NotNull Player player) {
        if (!plugin.getConfig().getBoolean("gui.advanced.click-cooldown.enabled", true)) {
            return;
        }
        
        int duration = plugin.getConfig().getInt("gui.advanced.click-cooldown.duration", 5);
        long cooldownEnd = System.currentTimeMillis() + (duration * 1000L);
        clickCooldowns.put(player.getUniqueId(), cooldownEnd);
    }
    
    /**
     * Start animations for a player
     * 
     * @param player The player
     */
    private void startAnimations(@NotNull Player player) {
        if (!plugin.getConfig().getBoolean("gui.animations.enabled", true)) {
            return;
        }
        
        int speed = plugin.getConfig().getInt("gui.animations.speed", 20);
        
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() == null) {
                stopAnimations(player);
                return;
            }
            
            // Implement animation logic here
            // This would involve updating the GUI with animated effects
            
        }, 0L, speed);
        
        animationTasks.put(player.getUniqueId(), task);
    }
    
    /**
     * Stop animations for a player
     * 
     * @param player The player
     */
    public void stopAnimations(@NotNull Player player) {
        BukkitTask task = animationTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * Shutdown the GuiManager
     */
    public void shutdown() {
        // Stop all animations
        for (BukkitTask task : animationTasks.values()) {
            task.cancel();
        }
        animationTasks.clear();
        
        // Clear cooldowns
        clickCooldowns.clear();
    }
}
