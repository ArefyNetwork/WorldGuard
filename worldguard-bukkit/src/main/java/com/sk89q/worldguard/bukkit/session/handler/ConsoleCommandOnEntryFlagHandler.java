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

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.Handler;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class ConsoleCommandOnEntryFlagHandler extends Handler {

    public static final Factory FACTORY = new Factory();

    public static class Factory extends Handler.Factory<ConsoleCommandOnEntryFlagHandler> {
        @Override
        public ConsoleCommandOnEntryFlagHandler create(Session session) {
            return new ConsoleCommandOnEntryFlagHandler(session);
        }
    }

    private Collection<Set<String>> lastCommands;

    protected ConsoleCommandOnEntryFlagHandler(Session session) {
        super(session);
        this.lastCommands = new ArrayList<>();
    }

    @Override
    public boolean onCrossBoundary(LocalPlayer player, Location from, Location to, ApplicableRegionSet toSet,
                                   Set<ProtectedRegion> entered, Set<ProtectedRegion> exited, MoveType moveType) {
        Collection<Set<String>> commands = toSet.queryAllValues(player, Flags.CONSOLE_COMMAND_ON_ENTRY);

        for (Set<String> commandSet : commands) {
            if (!lastCommands.contains(commandSet) && !commandSet.isEmpty()) {
                WorldGuardPlugin plugin = WorldGuardPlugin.inst();
                for (String command : commandSet) {
                    String resolved = command.replace("%username%", player.getName())
                            .replace("%uuid%", player.getUniqueId().toString());
                    if (resolved.startsWith("/")) resolved = resolved.substring(1);
                    final String finalCommand = resolved;
                    if (plugin.isFolia()) {
                        Bukkit.getGlobalRegionScheduler().run(plugin,
                                task -> Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), finalCommand));
                    } else {
                        Bukkit.getScheduler().runTask(plugin,
                                () -> Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), finalCommand));
                    }
                }
                break;
            }
        }

        lastCommands = new ArrayList<>(commands);

        if (!lastCommands.isEmpty()) {
            for (ProtectedRegion region : toSet) {
                Set<String> regionCmds = region.getFlag(Flags.CONSOLE_COMMAND_ON_ENTRY);
                if (regionCmds != null) {
                    lastCommands.add(regionCmds);
                }
            }
        }

        return true;
    }
}
