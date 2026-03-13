/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.bukkit.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listener for extra flags: fly, glide, give-effects, blocked-effects,
 * command-on-entry, console-command-on-entry.
 * Uses both event listeners AND periodic polling to enforce flags.
 */
public class ExtraFlagsListener implements Listener, Runnable {

    // Duration matching WorldGuardExtraFlags: 20*15+19 = 319 ticks (~16s)
    // Re-applied every poll (10 ticks), so timer never drops below ~15s on HUD
    private static final int EFFECT_DURATION = 20 * 15 + 19;

    private final WorldGuardPlugin plugin;
    private final Map<UUID, Boolean> originalFlightState = new HashMap<>();
    private final Map<UUID, Boolean> originalGlideState = new HashMap<>();
    private final Map<UUID, Set<String>> playerRegions = new HashMap<>();

    public ExtraFlagsListener(WorldGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();

        for (Player player : Bukkit.getOnlinePlayers()) {
            LocalPlayer localPlayer = plugin.wrapPlayer(player);
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));

            // --- FLY FLAG ---
            State flyState = set.queryState(localPlayer, Flags.FLY);
            if (flyState == State.DENY) {
                if (player.getAllowFlight()) {
                    if (!originalFlightState.containsKey(player.getUniqueId())) {
                        originalFlightState.put(player.getUniqueId(), true);
                    }
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
            } else if (flyState == State.ALLOW) {
                if (!player.getAllowFlight()) {
                    if (!originalFlightState.containsKey(player.getUniqueId())) {
                        originalFlightState.put(player.getUniqueId(), false);
                    }
                    player.setAllowFlight(true);
                }
            } else {
                if (originalFlightState.containsKey(player.getUniqueId())) {
                    boolean original = originalFlightState.remove(player.getUniqueId());
                    player.setAllowFlight(original);
                    if (!original) {
                        player.setFlying(false);
                    }
                }
            }

            // --- GLIDE FLAG ---
            State glideState = set.queryState(localPlayer, Flags.GLIDE);
            if (glideState == State.DENY) {
                if (player.isGliding()) {
                    if (!originalGlideState.containsKey(player.getUniqueId())) {
                        originalGlideState.put(player.getUniqueId(), true);
                    }
                    player.setGliding(false);
                }
            } else {
                if (originalGlideState.containsKey(player.getUniqueId())) {
                    originalGlideState.remove(player.getUniqueId());
                }
            }

            // --- GIVE-EFFECTS FLAG ---
            Set<String> effects = set.queryValue(localPlayer, Flags.GIVE_EFFECTS);
            if (effects != null) {
                for (String effectStr : effects) {
                    applyEffect(player, effectStr);
                }
            }

            // --- BLOCKED-EFFECTS FLAG ---
            Set<String> blockedEffects = set.queryValue(localPlayer, Flags.BLOCKED_EFFECTS);
            if (blockedEffects != null) {
                for (String blockedStr : blockedEffects) {
                    removeBlockedEffect(player, blockedStr);
                }
            }

            // --- COMMAND-ON-ENTRY & CONSOLE-COMMAND-ON-ENTRY ---
            Set<String> currentRegionIds = new HashSet<>();
            for (ProtectedRegion region : set) {
                currentRegionIds.add(region.getId());
            }

            Set<String> previousRegionIds = playerRegions.getOrDefault(player.getUniqueId(), Collections.emptySet());

            for (ProtectedRegion region : set) {
                if (!previousRegionIds.contains(region.getId())) {
                    // Player just entered this region
                    Set<String> cmds = region.getFlag(Flags.COMMAND_ON_ENTRY);
                    if (cmds != null) {
                        for (String cmd : cmds) {
                            String resolved = cmd.replace("%username%", player.getName())
                                                 .replace("%uuid%", player.getUniqueId().toString());
                            if (resolved.startsWith("/")) resolved = resolved.substring(1);
                            player.performCommand(resolved);
                        }
                    }

                    Set<String> consoleCmds = region.getFlag(Flags.CONSOLE_COMMAND_ON_ENTRY);
                    if (consoleCmds != null) {
                        for (String cmd : consoleCmds) {
                            String resolved = cmd.replace("%username%", player.getName())
                                                 .replace("%uuid%", player.getUniqueId().toString());
                            if (resolved.startsWith("/")) resolved = resolved.substring(1);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                        }
                    }
                }
            }

            playerRegions.put(player.getUniqueId(), currentRegionIds);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) return;

        Player player = event.getPlayer();
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        LocalPlayer localPlayer = plugin.wrapPlayer(player);
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));

        State flyState = set.queryState(localPlayer, Flags.FLY);
        if (flyState == State.DENY) {
            event.setCancelled(true);
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.isGliding()) return;

        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        LocalPlayer localPlayer = plugin.wrapPlayer(player);
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));

        State glideState = set.queryState(localPlayer, Flags.GLIDE);
        if (glideState == State.DENY) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        originalFlightState.remove(uuid);
        originalGlideState.remove(uuid);
        playerRegions.remove(uuid);
    }

    /**
     * Apply a potion effect from a flag string.
     * Supports formats: "EFFECT_NAME amplifier ambient" and "minecraft:effect_name amplifier ambient"
     * Re-applied unconditionally every poll to keep timer stable (matching WGEF behavior).
     */
    private void applyEffect(Player player, String effectStr) {
        String[] parts = effectStr.trim().split("\\s+");
        if (parts.length < 1) return;

        PotionEffectType type = resolveEffectType(parts[0]);
        if (type == null) return;

        int amplifier = parts.length > 1 ? parseInt(parts[1], 0) : 0;
        boolean ambient = parts.length > 2 && Boolean.parseBoolean(parts[2]);

        // Re-apply unconditionally every poll (matching WGEF behavior)
        // Duration of 319 ticks (~16s), re-applied every 10 ticks, so timer stays at ~15s
        player.addPotionEffect(new PotionEffect(type, EFFECT_DURATION, amplifier, true, ambient), true);
    }

    /**
     * Remove a blocked effect from the player.
     * Supports "minecraft:effect_name", "EFFECT_NAME", and "*" for all effects.
     */
    private void removeBlockedEffect(Player player, String effectStr) {
        String name = effectStr.trim();
        if (name.equals("*")) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            return;
        }
        PotionEffectType type = resolveEffectType(name);
        if (type != null && player.hasPotionEffect(type)) {
            player.removePotionEffect(type);
        }
    }

    /**
     * Resolve a potion effect type from a string.
     * Handles both "NIGHT_VISION" (legacy) and "minecraft:night_vision" (namespaced) formats.
     */
    private PotionEffectType resolveEffectType(String name) {
        // Try namespaced key first (minecraft:night_vision)
        PotionEffectType type = Registry.EFFECT.match(name);
        if (type != null) return type;
        // Fall back to legacy name (NIGHT_VISION)
        return PotionEffectType.getByName(name);
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
