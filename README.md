# Minecraft Plugin Template

A modern Minecraft plugin template using PaperMC API and Maven for Java development.

## Features

-   **PaperMC API**: Uses the latest PaperMC API for better performance and features
-   **Maven Build System**: Standard Maven project structure with proper dependencies
-   **Java 21**: Uses modern Java features and syntax
-   **Sample Code**: Includes example command and event listener implementations
-   **Configuration**: Sample config.yml with common plugin settings
-   **Proper Structure**: Organized package structure for commands, listeners, and utilities

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── example/
│   │           └── plugin/
│   │               ├── MinecraftPlugin.java          # Main plugin class
│   │               ├── commands/
│   │               │   └── HelloCommand.java         # Sample command
│   │               └── listeners/
│   │                   └── PlayerJoinListener.java   # Sample event listener
│   └── resources/
│       ├── plugin.yml                                # Plugin metadata
│       └── config.yml                                # Configuration file
└── test/
    └── java/                                         # Test classes
```

## Getting Started

### Prerequisites

-   Java 21 or higher
-   Maven 3.6 or higher
-   A Minecraft server running PaperMC

### Setup

1. **Clone or download this template**
2. **Update the package name**: Change `com.example.plugin` to your desired package name
3. **Update plugin metadata**: Edit `src/main/resources/plugin.yml` with your plugin details
4. **Update pom.xml**: Change the `groupId`, `artifactId`, and other project details
5. **Build the plugin**: Run `mvn clean package` to build the JAR file

### Building

```bash
# Clean and build the project
mvn clean package

# The built JAR will be in target/minecraft-plugin-1.0.0.jar
```

### Installation

1. Copy the built JAR file to your server's `plugins/` folder
2. Restart your Minecraft server
3. The plugin will be loaded automatically

## Sample Features

### Hello Command

The template includes a sample `/hello` command that:

-   Greets the player who runs it
-   Can greet other online players
-   Includes tab completion for player names
-   Has permission checks

Usage:

-   `/hello` - Greet yourself
-   `/hello <player>` - Greet another player

### Player Join Listener

The template includes a sample event listener that:

-   Welcomes players when they join
-   Logs player join/quit events
-   Can be extended to give welcome items or effects

## Configuration

The plugin includes a sample `config.yml` file with common configuration options. You can access configuration values in your code using:

```java
// Get a configuration value
boolean debug = getConfig().getBoolean("plugin.debug", false);
String welcomeMessage = getConfig().getString("plugin.welcome.message", "Welcome!");
```

## Development Tips

### Adding New Commands

1. Create a new class in the `commands` package
2. Implement `CommandExecutor` and optionally `TabCompleter`
3. Register the command in `MinecraftPlugin.java`
4. Add the command to `plugin.yml`

### Adding New Event Listeners

1. Create a new class in the `listeners` package
2. Implement `Listener` interface
3. Add `@EventHandler` methods for the events you want to handle
4. Register the listener in `MinecraftPlugin.java`

### Best Practices

-   Use proper Java documentation with Javadoc comments
-   Follow Java naming conventions
-   Use the `@NotNull` and `@Nullable` annotations for better code quality
-   Handle exceptions properly
-   Use the plugin's logger for debugging: `getLogger().info("Message")`
-   Test your plugin thoroughly before releasing

## PaperMC vs Spigot vs Bukkit

This template uses **PaperMC**, which is recommended because:

-   Better performance than Spigot/Bukkit
-   More features and APIs
-   Active development and community
-   Backward compatible with Spigot/Bukkit plugins

## Resources

-   [PaperMC Documentation](https://docs.papermc.io/)
-   [Bukkit API Documentation](https://hub.spigotmc.org/javadocs/bukkit/)
-   [Maven Documentation](https://maven.apache.org/guides/)
-   [Java Documentation](https://docs.oracle.com/en/java/)

## License

This template is provided as-is for educational purposes. Feel free to modify and use it for your own projects.

## Contributing

Feel free to submit issues or pull requests to improve this template!
