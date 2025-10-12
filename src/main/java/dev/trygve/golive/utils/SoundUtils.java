package dev.trygve.golive.utils;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for playing sounds with modern API
 */
public class SoundUtils {
    
    /**
     * Play a sound to a player
     * 
     * @param player The player to play the sound to
     * @param sound The sound to play
     * @param volume The volume (0.0 to 1.0)
     * @param pitch The pitch (0.5 to 2.0)
     */
    public static void playSound(@NotNull Player player, @NotNull Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
    
    /**
     * Play a sound to a player with default volume and pitch
     * 
     * @param player The player to play the sound to
     * @param sound The sound to play
     */
    public static void playSound(@NotNull Player player, @NotNull Sound sound) {
        playSound(player, sound, 1.0f, 1.0f);
    }
    
    /**
     * Play a sound to a player by name
     * 
     * @param player The player to play the sound to
     * @param soundName The name of the sound
     * @param volume The volume (0.0 to 1.0)
     * @param pitch The pitch (0.5 to 2.0)
     */
    public static void playSound(@NotNull Player player, @NotNull String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            playSound(player, sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            // Try to play as a custom sound (for resource packs)
            player.playSound(player.getLocation(), soundName, volume, pitch);
        }
    }
    
    /**
     * Play a sound to a player by name with default volume and pitch
     * 
     * @param player The player to play the sound to
     * @param soundName The name of the sound
     */
    public static void playSound(@NotNull Player player, @NotNull String soundName) {
        playSound(player, soundName, 1.0f, 1.0f);
    }
    
    /**
     * Play a sound to all online players
     * 
     * @param sound The sound to play
     * @param volume The volume (0.0 to 1.0)
     * @param pitch The pitch (0.5 to 2.0)
     */
    public static void playSoundToAll(@NotNull Sound sound, float volume, float pitch) {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            playSound(player, sound, volume, pitch);
        }
    }
    
    /**
     * Play a sound to all online players with default volume and pitch
     * 
     * @param sound The sound to play
     */
    public static void playSoundToAll(@NotNull Sound sound) {
        playSoundToAll(sound, 1.0f, 1.0f);
    }
    
    /**
     * Play a sound to all online players by name
     * 
     * @param soundName The name of the sound
     * @param volume The volume (0.0 to 1.0)
     * @param pitch The pitch (0.5 to 2.0)
     */
    public static void playSoundToAll(@NotNull String soundName, float volume, float pitch) {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            playSound(player, soundName, volume, pitch);
        }
    }
    
    /**
     * Play a sound to all online players by name with default volume and pitch
     * 
     * @param soundName The name of the sound
     */
    public static void playSoundToAll(@NotNull String soundName) {
        playSoundToAll(soundName, 1.0f, 1.0f);
    }
    
    /**
     * Stop all sounds for a player
     * 
     * @param player The player to stop sounds for
     */
    public static void stopAllSounds(@NotNull Player player) {
        player.stopAllSounds();
    }
    
    /**
     * Stop a specific sound for a player
     * 
     * @param player The player to stop the sound for
     * @param sound The sound to stop
     */
    public static void stopSound(@NotNull Player player, @NotNull Sound sound) {
        player.stopSound(sound);
    }
    
    /**
     * Stop a specific sound for a player by name
     * 
     * @param player The player to stop the sound for
     * @param soundName The name of the sound to stop
     */
    public static void stopSound(@NotNull Player player, @NotNull String soundName) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            stopSound(player, sound);
        } catch (IllegalArgumentException e) {
            // Try to stop as a custom sound
            player.stopSound(soundName);
        }
    }
    
    /**
     * Check if a sound name is valid
     * 
     * @param soundName The sound name to check
     * @return True if the sound is valid
     */
    public static boolean isValidSound(@NotNull String soundName) {
        try {
            Sound.valueOf(soundName.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Get a sound by name, returning null if not found
     * 
     * @param soundName The sound name
     * @return The sound or null if not found
     */
    @Nullable
    public static Sound getSound(@NotNull String soundName) {
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Get all available sound names
     * 
     * @return Array of all sound names
     */
    @NotNull
    public static String[] getAllSoundNames() {
        Sound[] sounds = Sound.values();
        String[] names = new String[sounds.length];
        for (int i = 0; i < sounds.length; i++) {
            names[i] = sounds[i].name();
        }
        return names;
    }
}
