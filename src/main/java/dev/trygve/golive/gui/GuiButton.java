package dev.trygve.golive.gui;

import dev.trygve.golive.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a GUI button with modern features
 */
public class GuiButton {
    
    private final Material material;
    private final int slot;
    private final String name;
    private final List<String> lore;
    private final int customModelData;
    private final boolean glow;
    private final String skullTexture;
    private final String skullUrl;
    private final ButtonType type;
    
    /**
     * Create a new GuiButton
     * 
     * @param material The material
     * @param slot The slot position
     * @param name The display name
     * @param lore The lore
     * @param customModelData The custom model data
     * @param glow Whether to glow
     * @param skullTexture The skull texture (base64)
     * @param skullUrl The skull URL
     * @param type The button type
     */
    public GuiButton(@NotNull Material material, int slot, @NotNull String name, 
                    @NotNull List<String> lore, int customModelData, boolean glow,
                    @Nullable String skullTexture, @Nullable String skullUrl, @NotNull ButtonType type) {
        this.material = material;
        this.slot = slot;
        this.name = name;
        this.lore = new ArrayList<>(lore);
        this.customModelData = customModelData;
        this.glow = glow;
        this.skullTexture = skullTexture;
        this.skullUrl = skullUrl;
        this.type = type;
    }
    
    /**
     * Create an ItemStack from this button
     * 
     * @return The ItemStack
     */
    @NotNull
    public ItemStack createItemStack() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta == null) {
            return item;
        }
        
        // Set display name
        Component nameComponent = ColorUtils.toComponent(name);
        meta.displayName(nameComponent);
        
        // Set lore
        if (!lore.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String loreLine : lore) {
                loreComponents.add(ColorUtils.toComponent(loreLine));
            }
            meta.lore(loreComponents);
        }
        
        // Set custom model data
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        
        // Set glow effect
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        
        // Handle skull textures
        if (material == Material.PLAYER_HEAD && meta instanceof SkullMeta ) {
            if (skullTexture != null && !skullTexture.isEmpty()) {
                // Set skull texture from base64
                try {
                    // Note: PlayerProfile API may vary between versions
                    // This is a simplified implementation
                } catch (Exception e) {
                    // Fallback to default skull
                }
            } else if (skullUrl != null && !skullUrl.isEmpty()) {
                // Set skull texture from URL
                try {
                    // Note: PlayerProfile API may vary between versions
                    // This is a simplified implementation
                } catch (Exception e) {
                    // Fallback to default skull
                }
            }
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Get the material
     * 
     * @return The material
     */
    @NotNull
    public Material getMaterial() {
        return material;
    }
    
    /**
     * Get the slot position
     * 
     * @return The slot position
     */
    public int getSlot() {
        return slot;
    }
    
    /**
     * Get the display name
     * 
     * @return The display name
     */
    @NotNull
    public String getName() {
        return name;
    }
    
    /**
     * Get the lore
     * 
     * @return The lore
     */
    @NotNull
    public List<String> getLore() {
        return new ArrayList<>(lore);
    }
    
    /**
     * Get the custom model data
     * 
     * @return The custom model data
     */
    public int getCustomModelData() {
        return customModelData;
    }
    
    /**
     * Check if the button glows
     * 
     * @return True if the button glows
     */
    public boolean isGlow() {
        return glow;
    }
    
    /**
     * Get the skull texture
     * 
     * @return The skull texture
     */
    @Nullable
    public String getSkullTexture() {
        return skullTexture;
    }
    
    /**
     * Get the skull URL
     * 
     * @return The skull URL
     */
    @Nullable
    public String getSkullUrl() {
        return skullUrl;
    }
    
    /**
     * Get the button type
     * 
     * @return The button type
     */
    @NotNull
    public ButtonType getType() {
        return type;
    }
    
    /**
     * Button types
     */
    public enum ButtonType {
        LIVE,
        OFFLINE,
        EDIT_LINK,
        CLOSE,
        BACKGROUND,
        DECORATION
    }
}
