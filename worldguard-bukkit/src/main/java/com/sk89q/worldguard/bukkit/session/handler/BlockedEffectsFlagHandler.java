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

import java.util.Set;

public class BlockedEffectsFlagHandler extends FlagValueChangeHandler<Set<String>> {

    public static final Factory FACTORY = new Factory();

    public static class Factory extends Handler.Factory<BlockedEffectsFlagHandler> {
        @Override
        public BlockedEffectsFlagHandler create(Session session) {
            return new BlockedEffectsFlagHandler(session);
        }
    }

    protected BlockedEffectsFlagHandler(Session session) {
        super(session, Flags.BLOCKED_EFFECTS);
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
        handleValue(player, player.getWorld(), set.queryValue(player, Flags.BLOCKED_EFFECTS));
    }

    private void handleValue(LocalPlayer player, World world, Set<String> value) {
        if (value == null) return;

        Player bukkitPlayer = ((BukkitPlayer) player).getPlayer();

        for (String blockedStr : value) {
            String name = blockedStr.trim();
            if (name.equals("*")) {
                for (PotionEffect effect : bukkitPlayer.getActivePotionEffects()) {
                    bukkitPlayer.removePotionEffect(effect.getType());
                }
                return;
            }

            PotionEffectType type = Registry.EFFECT.match(name);
            if (type == null) {
                type = PotionEffectType.getByName(name);
            }

            if (type != null && bukkitPlayer.hasPotionEffect(type)) {
                bukkitPlayer.removePotionEffect(type);
            }
        }
    }
}
