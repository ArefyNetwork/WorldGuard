# WorldGuard (Arefy Network Fork)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green.svg)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-red.svg)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper%20API-1.21.11-orange.svg)](https://papermc.io/)
[![WorldGuard](https://img.shields.io/badge/WorldGuard-7.0.16--SNAPSHOT-blue.svg)](https://github.com/EngineHub/WorldGuard)
[![License](https://img.shields.io/badge/License-GPLv3-green.svg)](LICENSE)

A performance-optimized fork of [FreshSMP's WorldGuard](https://github.com/FreshSMP/WorldGuard) with the bypass permission system disabled, built-in extra flags (replacing WorldGuardExtraFlags), and dragon egg protection.

## 🎯 Features

- **No Bypass Permission Overhead**: `hasBypass()` always returns `false`, eliminating all per-event permission checks, object allocations, and Guava cache lookups
- **Async Region Processing**: Based on FreshSMP's `async-move` branch with asynchronous region handling
- **1.21.11 Native Support**: Compiled against `paper-api:1.21.11-R0.1-SNAPSHOT`
- **Full Region Protection**: All WorldGuard region flags, protections, and commands work normally
- **Built-in Extra Flags**: `fly`, `glide`, and `give-effects` flags built directly into WorldGuard — no need for WorldGuardExtraFlags plugin
- **Dragon Egg Protection**: Dragon eggs cannot be teleported inside any WorldGuard region

## 🪶 Built-in Extra Flags

These flags replace the [WorldGuardExtraFlags](https://github.com/aromaa/WorldGuardExtraFlags) plugin, which is abandoned and has a known bug where `fly: deny` only works on region entry, not when `/fly` is used inside the region.

This fork fixes that bug by using **periodic polling (every 500ms)** combined with **event listeners**, ensuring flags are always enforced.

### `fly`

Controls whether players can fly inside a region.

| Value | Behavior |
|---|---|
| `allow` | Enables flight for all players in the region |
| `deny` | Disables flight and forces players to the ground |
| *(not set)* | Restores the player's original flight state |

```
/rg flag <region> fly deny
/rg flag <region> fly allow
```

### `glide`

Controls whether players can glide with elytra inside a region.

| Value | Behavior |
|---|---|
| `deny` | Prevents elytra gliding inside the region |
| *(not set)* | Normal gliding behavior |

```
/rg flag <region> glide deny
```

### `give-effects`

Applies potion effects to players inside the region. Format: `EFFECT_NAME AMPLIFIER AMBIENT`

```
/rg flag <region> give-effects NIGHT_VISION 1 false
/rg flag <region> give-effects SPEED 2 true
```

- **EFFECT_NAME**: Bukkit potion effect name (e.g., `NIGHT_VISION`, `SPEED`, `JUMP_BOOST`)
- **AMPLIFIER**: Effect level (0 = level I, 1 = level II, etc.)
- **AMBIENT**: `true` for subtle particles, `false` for normal particles

Effects are re-applied automatically every 500ms while the player is in the region.

## 🥚 Dragon Egg Protection

Dragon eggs inside any WorldGuard region are protected from teleporting when clicked. The interaction is blocked at the block level (`setUseInteractedBlock(DENY)`) so other plugins (like MyCommand) can still detect the click event.

## 🚀 Performance Optimization: Bypass Removal

The standard WorldGuard checks `worldguard.region.bypass.<world>` on **every single player event**:

| Event | Frequency |
|---|---|
| Block place/break | Every block |
| Block interact (chests, doors, buttons) | Every click |
| Entity damage (PvP, mobs) | Every hit |
| Entity spawn/destroy/use/mount | Every action |
| Player movement | ~20 checks/sec per player |
| Commands, teleports, portals | Every occurrence |

Each check allocates a `BukkitPlayer` wrapper, a `WorldPlayerTuple` cache key, performs Guava cache lookups, and every 2 seconds triggers synchronous permission resolution through the permissions plugin (LuckPerms, etc.) for every online player.

**This fork eliminates all of that.** `hasBypass()` returns `false` immediately with zero allocations, zero cache lookups, and zero permission checks.

### What changes for admins

- `worldguard.region.bypass.<world>` permission has no effect
- `/rg bypass` command has no effect
- To build in protected regions, use `/rg addowner <region> <name>` instead

## 📦 Dependencies

### Required
- **Java** 21+ — Runtime requirement
- **Paper** 1.21.11 — Server platform (or Paper-based forks: Pufferfish, Purpur, etc.)
- **WorldEdit** 7.3.18+ — Required dependency

## 🛠️ Installation

1. Build the project:
```bash
./gradlew build
```

2. Copy the output JAR to your server's `plugins/` folder:
```
worldguard-bukkit/build/libs/worldguard-bukkit-7.0.16-SNAPSHOT-dist.jar
```

3. Restart the server.

## 🤝 Credits

- [EngineHub/WorldGuard](https://github.com/EngineHub/WorldGuard) — Original WorldGuard
- [FreshSMP/WorldGuard](https://github.com/FreshSMP/WorldGuard) — Async-move fork with 1.21.11 support
- [WorldGuardExtraFlags](https://github.com/aromaa/WorldGuardExtraFlags) — Inspiration for the built-in fly, glide, and give-effects flags
- Bypass removal optimization suggested by MachineBreaker (UniverseSpigot)

## 📝 License

WorldGuard is licensed under the GNU Lesser General Public License v3.

## 👨‍💻 Author

Optimized and maintained for Arefy Network.

---

**Version**: 7.0.16-SNAPSHOT | **Minecraft**: 1.21.11 | **Java**: 21+
