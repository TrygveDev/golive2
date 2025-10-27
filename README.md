# GoLive

A modern livestream announcement plugin for Minecraft 1.21+ servers using PaperMC API. Allows players to announce when they're streaming and automatically updates their status, permissions, and groups.

## Features

-   **Stream Announcements**: Players can easily announce when they go live with `/live` command
-   **Interactive GUI**: User-friendly GUI for managing live status and stream links
-   **Database Support**: Choose between SQLite (default) or MySQL for multi-server networks
-   **Universal Permission Support**: Works with all Vault-compatible permission plugins (LuckPerms, PermissionsEx, GroupManager, PowerfulPerms, UltraPermissions, bPermissions, and more)
-   **Flexible Integration Modes**: Choose between prefix mode (preserves permissions) or group mode (changes groups)
-   **PlaceholderAPI Support**: Display live status in chat, scoreboards, and more
-   **Stream Link Validation**: Validate stream links with configurable domain whitelist
-   **Cooldown System**: Prevent spam with configurable cooldowns (bypassable)
-   **Auto-Removal**: Automatically remove live status after a set time
-   **Modern UI**: Rich text formatting with gradients and colors using MiniMessage
-   **Update Checker**: Automatic update notifications for admins
-   **Metrics**: bStats integration for usage statistics

## Commands

### `/golive` (Alias: `/gl`)

Main admin command for managing the plugin.

-   `/golive reload` - Reload configuration files
-   `/golive info` - Display plugin information
-   `/golive update` - Check for updates
-   `/golive help` - Show help menu

**Permission**: `golive.admin`

### `/live` (Aliases: `/stream`, `/broadcast`)

Announce that you're streaming.

-   `/live` - Open the GUI or go live directly
-   `/live setlink <url>` - Set your stream link
-   `/live link` - View your current stream link
-   `/live help` - Show help menu

**Permission**: `golive.live`

### `/offline` (Aliases: `/stop`, `/end`)

Stop streaming and remove live status.

-   `/offline` - Open the GUI or go offline directly
-   `/offline help` - Show help menu

**Permission**: `golive.offline`

## Permissions

| Permission                 | Description                   | Default |
| -------------------------- | ----------------------------- | ------- |
| `golive.admin`             | Access to admin commands      | op      |
| `golive.live`              | Permission to go live         | true    |
| `golive.offline`           | Permission to go offline      | true    |
| `golive.bypass.cooldown`   | Bypass cooldown restrictions  | op      |
| `golive.bypass.validation` | Bypass stream link validation | op      |

## Configuration

### Database Settings

The plugin supports both SQLite and MySQL databases:

```yaml
database:
    type: SQLITE # or MYSQL
    mysql:
        host: localhost
        port: 3306
        database: golive
        username: root
        password: password
```

### Vault Integration

The plugin supports **all Vault-compatible permission plugins** including:

-   **LuckPerms** (default)
-   **PermissionsEx (PEX)**
-   **GroupManager**
-   **PowerfulPerms**
-   **UltraPermissions**
-   **bPermissions**

Configure prefix mode (recommended - preserves player permissions):

```yaml
vault:
    enabled: true
    mode: prefix # or "group"
    prefix:
        # NOTE: Permission plugins don't support MiniMessage in prefix
        # Use Minecraft color codes: &c = red, &6 = gold, &a = green, etc.
        live: "&c&l🔴 LIVE &r"
        priority: 100
    commands:
        live: "lp user %player% meta setprefix %priority% %prefix%"
        offline: "lp user %player% meta removeprefix %priority%"
```

**Important:** Permission plugins (LuckPerms, PEX, etc.) don't support MiniMessage formatting in prefix/suffix. Use simple Minecraft color codes instead (`&c` for red, `&6` for gold, etc.).

Or use group mode (changes player's group):

```yaml
vault:
    mode: group
    groups:
        live: content-creator-live
        standard: content-creator
```

**Note:** The plugin automatically uses Vault Chat API when available (works with all permission plugins). Commands are only used as fallback. See `config.yml` for templates for different permission plugins.

### GUI Customization

Fully customizable GUI with custom model data support:

```yaml
gui:
    enabled: true
    size: 27
    title: "<gradient:#8b5cf6:#4c1d95>GoLive</gradient> <gray>Menu</gray>"
    buttons:
        live:
            slot: 11
            material: PURPLE_WOOL
            custom-model-data: 1001
            glow: true
```

### Stream Validation

Control which streaming platforms are allowed:

```yaml
stream-validation:
    require-https: true
    allowed-domains:
        - twitch.tv
        - youtube.com
        - kick.com
        - tiktok.com
```

### Announcements

Configure title, sound, and message settings:

```yaml
announcements:
    title:
        enabled: true
        fade-in: 10
        stay: 50
        fade-out: 10
    sound:
        enabled: true
        type: entity.player.levelup
    auto-removal:
        enabled: true
        delay: 43200 # 12 hours
```

## PlaceholderAPI Placeholders

The plugin provides PlaceholderAPI placeholders for displaying live status:

-   `%golive_status%` - Player's live status (live/offline)
-   `%golive_link%` - Player's stream link
-   `%golive_count%` - Number of players currently live
-   And more...

## Installation

1. **Download the plugin**: Download the latest `golive-2.0.0.jar` from releases
2. **Install dependencies** (optional but recommended):
    - [Vault](https://www.spigotmc.org/resources/vault.34315/) - For permission group management
    - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) - For placeholders
3. **Copy to plugins folder**: Place the JAR file in your server's `plugins/` folder
4. **Restart your server**: The plugin will generate default configuration files
5. **Configure the plugin**: Edit `config.yml` and `messages.yml` to your liking
6. **Reload the plugin**: Run `/golive reload` to apply changes

## Building from Source

### Prerequisites

-   Java 21 or higher
-   Maven 3.6 or higher

### Build Instructions

```bash
# Clone the repository
git clone https://github.com/trygve/golive.git
cd golive

# Build the plugin
mvn clean package

# The built JAR will be in target/golive-2.0.0.jar
```

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── dev/
│   │       └── trygve/
│   │           └── golive/
│   │               ├── GoLive.java                    # Main plugin class
│   │               ├── commands/                      # Command implementations
│   │               │   ├── GoLiveCommand.java
│   │               │   ├── LiveCommand.java
│   │               │   └── OfflineCommand.java
│   │               ├── database/                      # Database management
│   │               │   ├── DatabaseManager.java
│   │               │   ├── MySQLDatabase.java
│   │               │   └── SQLiteDatabase.java
│   │               ├── gui/                           # GUI system
│   │               │   ├── GuiManager.java
│   │               │   └── GuiButton.java
│   │               ├── hooks/                         # Plugin integrations
│   │               │   ├── PlaceholderHook.java
│   │               │   └── VaultHook.java
│   │               ├── listeners/                     # Event listeners
│   │               │   ├── PlayerJoinListener.java
│   │               │   └── GuiListener.java
│   │               ├── managers/                      # Core managers
│   │               │   ├── LiveStatusManager.java
│   │               │   └── MessageManager.java
│   │               ├── placeholders/                  # PlaceholderAPI expansion
│   │               │   └── GoLivePlaceholders.java
│   │               └── utils/                         # Utility classes
│   │                   ├── ColorUtils.java
│   │                   ├── SoundUtils.java
│   │                   ├── UpdateChecker.java
│   │                   └── MetricsManager.java
│   └── resources/
│       ├── plugin.yml                                # Plugin metadata
│       ├── config.yml                                # Main configuration
│       └── messages.yml                              # Customizable messages
└── test/
    └── java/                                         # Test classes
```

## Dependencies

### Required (Included in JAR)

-   **PaperMC API 1.21.1** - Core server API
-   **HikariCP 5.1.0** - Database connection pooling
-   **SQLite JDBC 3.46.0.0** - SQLite database driver
-   **bStats 3.0.2** - Plugin metrics

### Optional (Server Plugins)

-   **Vault** - Permission group management
-   **PlaceholderAPI** - Placeholder support
-   **MySQL Connector** - MySQL database support

## Support

-   **Issues**: Report bugs on [GitHub Issues](https://github.com/trygve/golive/issues)
-   **SpigotMC**: [Plugin Page](https://www.spigotmc.org/resources/88288/)
-   **bStats**: [Statistics](https://bstats.org/plugin/bukkit/GoLive/11803)

## Development

### Technologies Used

-   **Java 21** - Modern Java features and syntax
-   **Maven** - Build system and dependency management
-   **PaperMC API** - Better performance than Spigot/Bukkit
-   **Adventure API** - Modern text components and MiniMessage
-   **HikariCP** - Fast and reliable connection pooling
-   **Async Programming** - CompletableFuture for non-blocking operations

### Code Quality

-   Comprehensive JavaDoc documentation
-   `@NotNull` and `@Nullable` annotations for null safety
-   Proper exception handling and logging
-   Modern async/await patterns with CompletableFuture
-   Clean architecture with separation of concerns

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Author

Developed by **Trygve** - [GitHub Profile](https://github.com/trygve)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

---

**Note**: This plugin requires PaperMC 1.21+ and Java 21+. It will not work on older Spigot or Bukkit servers.
