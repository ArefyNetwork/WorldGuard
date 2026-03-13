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

package com.sk89q.worldguard.bukkit.session.handler;

import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.FlagValueChangeHandler;
import com.sk89q.worldguard.session.handler.Handler;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class GiveEffectsFlagHandler extends FlagValueChangeHandler<Set<String>> {

    // Matching WGEF: 20*15+19 = 319 ticks (~16s), timer never drops below ~15s
    private static final int POTION_EFFECT_DURATION = 20 * 15 + 19;

    public static final Factory FACTORY = new Factory();

    public static class Factory extends Handler.Factory<GiveEffectsFlagHandler> {
        @Override
        public GiveEffectsFlagHandler create(Session session) {
            return new GiveEffectsFlagHandler(session);
        }
    }

    private final Set<PotionEffectType> givenEffects = new HashSet<>();

    protected GiveEffectsFlagHandler(Session session) {
        super(session, Flags.GIVE_EFFECTS);
    }

    @Override
    protected void onInitialValue(LocalPlayer player, ApplicableRegionSet set, Set<String> value) {
        handleValue(player, player.getWorld(), value);
    }

    @Override
    protected boolean onSetValue(LocalPlayer player, Location from, Location to, ApplicableRegionSet toSet,
                                 Set<String> currentValue, Set<String> lastValue, MoveType moveType) {
        handleValue(player, (World) to.getExtent(), currentValue);
        return true;
    }

    @Override
    protected boolean onAbsentValue(LocalPlayer player, Location from, Location to, ApplicableRegionSet toSet,
                                    Set<String> lastValue, MoveType moveType) {
        handleValue(player, (World) to.getExtent(), null);
        return true;
    }

    @Override
    public void tick(LocalPlayer player, ApplicableRegionSet set) {
        handleValue(player, player.getWorld(), set.queryValue(player, Flags.GIVE_EFFECTS));
    }

    private void handleValue(LocalPlayer player, World world, Set<String> value) {
        Player bukkitPlayer = ((BukkitPlayer) player).getPlayer();

        if (value != null) {
            for (String effectStr : value) {
                String[] parts = effectStr.trim().split("\\s+");
                if (parts.length < 1) continue;

                PotionEffectType type = resolveEffectType(parts[0]);
                if (type == null) continue;

                int amplifier = parts.length > 1 ? parseInt(parts[1], 0) : 0;
                boolean showParticles = parts.length > 2 && Boolean.parseBoolean(parts[2]);

                givenEffects.add(type);
                bukkitPlayer.addPotionEffect(
                    new PotionEffect(type, POTION_EFFECT_DURATION, amplifier, true, showParticles), true);
            }
        }

        // Remove effects that are no longer in the flag value
        Iterator<PotionEffectType> it = givenEffects.iterator();
        while (it.hasNext()) {
            PotionEffectType type = it.next();

            if (value != null) {
                boolean stillActive = false;
                for (String effectStr : value) {
                    String[] parts = effectStr.trim().split("\\s+");
                    if (parts.length >= 1) {
                        PotionEffectType parsed = resolveEffectType(parts[0]);
                        if (type.equals(parsed)) {
                            stillActive = true;
                            break;
                        }
                    }
                }
                if (stillActive) continue;
            }

            bukkitPlayer.removePotionEffect(type);
            it.remove();
        }
    }

    private static PotionEffectType resolveEffectType(String name) {
        PotionEffectType type = Registry.EFFECT.match(name);
        if (type != null) return type;
        return PotionEffectType.getByName(name);
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
