# WorldGuard (No Bypass)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green.svg)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-red.svg)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper%20API-1.21.11-orange.svg)](https://papermc.io/)
[![WorldGuard](https://img.shields.io/badge/WorldGuard-7.0.16--SNAPSHOT-blue.svg)](https://github.com/EngineHub/WorldGuard)
[![License](https://img.shields.io/badge/License-GPLv3-green.svg)](LICENSE)

**WorldGuard (No Bypass)** is a performance-optimized fork of [FreshSMP's WorldGuard](https://github.com/FreshSMP/WorldGuard) that completely disables the `worldguard.region.bypass` permission system to eliminate unnecessary per-event overhead on busy servers.

## 🎯 Features

- **No Bypass Permission Overhead**: `hasBypass()` always returns `false`, eliminating all per-event permission checks, object allocations, and Guava cache lookups
- **Async Region Processing**: Based on FreshSMP's `async-move` branch with asynchronous region handling
- **1.21.11 Native Support**: Compiled against `paper-api:1.21.11-R0.1-SNAPSHOT`
- **Full Region Protection**: All WorldGuard region flags, protections, and commands work normally

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
- Bypass removal optimization suggested by MachineBreaker (UniverseSpigot)

## 📝 License

WorldGuard is licensed under the GNU Lesser General Public License v3.

## 👨‍💻 Author

Optimized and maintained for Arefy Network.

---

**Version**: 7.0.16-SNAPSHOT | **Minecraft**: 1.21.11 | **Java**: 21+
