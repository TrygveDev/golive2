package dev.trygve.golive.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling colors and text formatting
 * Supports both legacy color codes and modern MiniMessage
 */
public class ColorUtils {
    
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    
    // Pattern for hex colors in legacy format
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    
    /**
     * Convert a string with legacy color codes to a Component
     * Supports both legacy codes (&a, &l, etc.) and hex colors (&#RRGGBB)
     * 
     * @param text The text to convert
     * @return The converted Component
     */
    @NotNull
    public static Component legacyToComponent(@NotNull String text) {
        // Convert hex colors to MiniMessage format
        text = convertHexToMiniMessage(text);
        
        // Convert legacy color codes to MiniMessage format
        text = convertLegacyToMiniMessage(text);
        
        return MINI_MESSAGE.deserialize(text);
    }
    
    /**
     * Convert a string with MiniMessage format to a Component
     * 
     * @param text The text to convert
     * @return The converted Component
     */
    @NotNull
    public static Component miniMessageToComponent(@NotNull String text) {
        return MINI_MESSAGE.deserialize(text);
    }
    
    /**
     * Convert a Component to legacy color code string
     * 
     * @param component The component to convert
     * @return The legacy color code string
     */
    @NotNull
    public static String componentToLegacy(@NotNull Component component) {
        return LEGACY_SERIALIZER.serialize(component);
    }
    
    /**
     * Convert legacy color codes to MiniMessage format
     * 
     * @param text The text to convert
     * @return The converted text
     */
    @NotNull
    public static String convertLegacyToMiniMessage(@NotNull String text) {
        // Convert legacy color codes to MiniMessage equivalents
        text = text.replace("&0", "<black>");
        text = text.replace("&1", "<dark_blue>");
        text = text.replace("&2", "<dark_green>");
        text = text.replace("&3", "<dark_aqua>");
        text = text.replace("&4", "<dark_red>");
        text = text.replace("&5", "<dark_purple>");
        text = text.replace("&6", "<gold>");
        text = text.replace("&7", "<gray>");
        text = text.replace("&8", "<dark_gray>");
        text = text.replace("&9", "<blue>");
        text = text.replace("&a", "<green>");
        text = text.replace("&b", "<aqua>");
        text = text.replace("&c", "<red>");
        text = text.replace("&d", "<light_purple>");
        text = text.replace("&e", "<yellow>");
        text = text.replace("&f", "<white>");
        
        // Convert formatting codes
        text = text.replace("&k", "<obfuscated>");
        text = text.replace("&l", "<bold>");
        text = text.replace("&m", "<strikethrough>");
        text = text.replace("&n", "<underlined>");
        text = text.replace("&o", "<italic>");
        text = text.replace("&r", "<reset>");
        
        return text;
    }
    
    /**
     * Convert hex colors to MiniMessage format
     * 
     * @param text The text to convert
     * @return The converted text
     */
    @NotNull
    public static String convertHexToMiniMessage(@NotNull String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(result, "<#" + hex + ">");
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Strip all color codes from a string
     * 
     * @param text The text to strip
     * @return The stripped text
     */
    @NotNull
    public static String stripColors(@NotNull String text) {
        // Strip legacy color codes using regex instead of deprecated method
        text = text.replaceAll("§[0-9a-fk-or]", "");
        
        // Strip hex colors
        text = text.replaceAll("&#[A-Fa-f0-9]{6}", "");
        
        // Strip MiniMessage tags (basic)
        text = text.replaceAll("<[^>]*>", "");
        
        return text;
    }
    
    /**
     * Check if a string contains color codes
     * 
     * @param text The text to check
     * @return True if the text contains color codes
     */
    public static boolean hasColors(@NotNull String text) {
        return text.contains("&") || text.contains("<") || text.contains("#");
    }
    
    /**
     * Convert a string to a Component, automatically detecting the format
     * 
     * @param text The text to convert
     * @return The converted Component
     */
    @NotNull
    public static Component toComponent(@NotNull String text) {
        if (text.contains("<") && text.contains(">")) {
            // Likely MiniMessage format
            return miniMessageToComponent(text);
        } else if (text.contains("&")) {
            // Likely legacy format
            return legacyToComponent(text);
        } else {
            // Plain text
            return Component.text(text);
        }
    }
    
    /**
     * Replace placeholders in a text string
     * 
     * @param text The text to process
     * @param placeholders The placeholders to replace
     * @return The processed text
     */
    @NotNull
    public static String replacePlaceholders(@NotNull String text, @NotNull String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be in pairs (key, value)");
        }
        
        for (int i = 0; i < placeholders.length; i += 2) {
            String placeholder = placeholders[i];
            String value = placeholders[i + 1];
            text = text.replace(placeholder, value);
        }
        
        return text;
    }
    
    /**
     * Create a gradient text component
     * 
     * @param text The text to apply gradient to
     * @param startColor The start color (hex)
     * @param endColor The end color (hex)
     * @return The gradient component
     */
    @NotNull
    public static Component createGradient(@NotNull String text, @NotNull String startColor, @NotNull String endColor) {
        String gradientText = "<gradient:" + startColor + ":" + endColor + ">" + text + "</gradient>";
        return miniMessageToComponent(gradientText);
    }
    
    /**
     * Create a rainbow text component
     * 
     * @param text The text to apply rainbow to
     * @return The rainbow component
     */
    @NotNull
    public static Component createRainbow(@NotNull String text) {
        String rainbowText = "<rainbow>" + text + "</rainbow>";
        return miniMessageToComponent(rainbowText);
    }
}
