package dev.trygve.golive.listeners;

import dev.trygve.golive.GoLive;
import dev.trygve.golive.gui.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.jetbrains.annotations.NotNull;

/**
 * Handles GUI-related events
 */
public class GuiListener implements Listener {
    
    private final GoLive plugin;
    private final GuiManager guiManager;
    
    /**
     * Create a new GuiListener
     * 
     * @param plugin The plugin instance
     * @param guiManager The GUI manager
     */
    public GuiListener(@NotNull GoLive plugin, @NotNull GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }
    
    /**
     * Handle inventory click event
     * 
     * @param event The inventory click event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        // Check if it's our GUI
        if (!isGoLiveGui(event.getView().title())) {
            return;
        }
        
        // Cancel the event to prevent item movement
        if (plugin.getConfig().getBoolean("gui.advanced.inventory-protection", true)) {
            event.setCancelled(true);
        }
        
        // Handle button clicks
        if (event.getClickedInventory() != null && 
            event.getClickedInventory().getType() == InventoryType.CHEST) {
            
            int slot = event.getSlot();
            boolean handled = guiManager.handleButtonClick(player, slot);
            
            if (handled) {
                event.setCancelled(true);
            }
        }
    }
    
    /**
     * Handle inventory drag event
     * 
     * @param event The inventory drag event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        // Check if it's our GUI
        if (!isGoLiveGui(event.getView().title())) {
            return;
        }
        
        // Cancel the event to prevent item dragging
        if (plugin.getConfig().getBoolean("gui.advanced.drag-protection", true)) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Handle inventory close event
     * 
     * @param event The inventory close event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        
        // Check if it's our GUI
        if (!isGoLiveGui(event.getView().title())) {
            return;
        }
        
        // Stop animations for the player
        guiManager.stopAnimations(player);
    }
    
    /**
     * Check if the inventory title matches our GUI
     * 
     * @param title The inventory title
     * @return True if it's our GUI
     */
    private boolean isGoLiveGui(@NotNull Component title) {
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);
        String guiTitleConfig = plugin.getConfig().getString("gui.title", "<gradient:#00ff00:#ff0000>GoLive</gradient> <gray>Menu</gray>");
        // Since the GUI uses ColorUtils to parse the title, we need to serialize the expected title too
        Component expectedTitle = dev.trygve.golive.utils.ColorUtils.toComponent(guiTitleConfig);
        String expectedTitleText = PlainTextComponentSerializer.plainText().serialize(expectedTitle);
        return titleText.equals(expectedTitleText);
    }
}
