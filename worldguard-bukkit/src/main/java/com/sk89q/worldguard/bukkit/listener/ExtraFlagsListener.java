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
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listener for extra flags: fly, glide, give-effects.
 * Uses both event listeners AND periodic polling to enforce flags,
 * fixing the issue where /fly given while inside a region was not caught.
 */
public class ExtraFlagsListener implements Listener, Runnable {

    private final WorldGuardPlugin plugin;
    private final Map<UUID, Boolean> originalFlightState = new HashMap<>();
    private final Map<UUID, Boolean> originalGlideState = new HashMap<>();

    public ExtraFlagsListener(WorldGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called periodically (every 10 ticks / 500ms) to enforce fly, glide, and give-effects flags.
     */
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
                // No flag set - restore original state if we modified it
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
        }
    }

    /**
     * Cancels flight toggle if fly: deny is active in the player's region.
     */
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

    /**
     * Cancels glide if glide: deny is active in the player's region.
     */
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

    /**
     * Cleanup on quit.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        originalFlightState.remove(uuid);
        originalGlideState.remove(uuid);
    }

    /**
     * Parse and apply a potion effect string in the format "EFFECT_NAME AMPLIFIER AMBIENT"
     * e.g. "NIGHT_VISION 1 false"
     */
    private void applyEffect(Player player, String effectStr) {
        String[] parts = effectStr.trim().split("\\s+");
        if (parts.length < 1) return;

        PotionEffectType type = PotionEffectType.getByName(parts[0]);
        if (type == null) return;

        int amplifier = parts.length > 1 ? parseInt(parts[1], 0) : 0;
        boolean ambient = parts.length > 2 && Boolean.parseBoolean(parts[2]);

        // Only re-apply if the player doesn't already have this effect or it's about to expire
        PotionEffect existing = player.getPotionEffect(type);
        if (existing == null || existing.getDuration() < 40) {
            player.addPotionEffect(new PotionEffect(type, 80, amplifier, ambient, false));
        }
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
