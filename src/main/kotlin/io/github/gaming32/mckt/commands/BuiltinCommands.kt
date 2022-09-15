package io.github.gaming32.mckt.commands

import com.google.gson.JsonSyntaxException
import io.github.gaming32.mckt.Gamemode
import io.github.gaming32.mckt.PlayClient
import io.github.gaming32.mckt.capitalize
import io.github.gaming32.mckt.enumValueOfOrNull
import io.github.gaming32.mckt.packet.play.s2c.PlayDisconnectPacket
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import kotlin.collections.asSequence
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.getOrNull
import kotlin.collections.map
import kotlin.math.min

object BuiltinCommands {
    val HELP = registerCommand("help", Component.text("Show this help")) { sender, args ->
        val text = if (args.isEmpty()) {
            Component.text { text ->
                text.append(Component.text("Here's a list of the commands you can use:\n"))
                COMMANDS.forEach { (name, command) ->
                    if (sender.operator >= command.minPermission) {
                        text.append(Component.text("  + /$name"))
                        if (command.description != null) {
                            text.append(Component.text(" -- ")).append(command.description)
                        }
                        text.append(Component.newline())
                    }
                }
                sender.server.helpTexts.forEach { (command, description) ->
                    if (command.canUse(sender)) {
                        text.append(Component.text("  + /${command.usageText} -- "))
                        if (description != null) {
                            text.append(description)
                        }
                        text.append(Component.newline())
                    }
                }
            }
        } else {
            val dispatcher = sender.server.commandDispatcher
            if (args == "all") {
                Component.join(
                    JoinConfiguration.newlines(),
                    dispatcher.root.children
                        .asSequence()
                        .filter { it.canUse(sender) }
                        .flatMap { command ->
                            dispatcher.getAllUsage(command, sender, true)
                                .map { "/${command.usageText} $it" }
                        }
                        .map(Component::text)
                        .toList()
                )
            } else {
                val command = dispatcher.root.getChild(args)
                if (command == null || !command.canUse(sender)) {
                    Component.text("Unknown command: $args")
                } else {
                    Component.text { builder ->
                        builder.append(Component.join(
                            JoinConfiguration.newlines(),
                            dispatcher.getAllUsage(command, sender, true)
                                .map { Component.text("/${command.usageText} $it") }
                        ))
                        sender.server.helpTexts[command]?.let { description ->
                            builder.append(Component.newline()).append(description)
                        }
                    }
                }
            }
        }
        sender.reply(text)
    }

    val TELEPORT = registerCommand("tp", Component.text("Teleport a player"), 1) { sender, args ->
        val argv = args.split(" ")
        if (argv.size < 2) {
            return@registerCommand sender.reply(
                Component.text("Usage:\n  /tp <who> <to>\n  /tp <who> <x> <y> <z>", NamedTextColor.RED)
            )
        }
        val who = sender.evaluateClient(argv[0]) ?: return@registerCommand sender.reply(
            Component.text("Player ${argv[0]} not found", NamedTextColor.RED)
        )
        if (argv.size < 4) {
            val to = sender.evaluateClient(argv[1]) ?: return@registerCommand sender.reply(
                Component.text("Player ${argv[1]} not found", NamedTextColor.RED)
            )
            who.teleport(to)
            sender.replyBroadcast(Component.text("Teleported ${who.username} to ${to.username}"))
        } else {
            val x = argv[1].toDoubleOrNull() ?: return@registerCommand sender.reply(
                Component.text("Invalid number: ${argv[1]}", NamedTextColor.RED)
            )
            val y = argv[2].toDoubleOrNull() ?: return@registerCommand sender.reply(
                Component.text("Invalid number: ${argv[2]}", NamedTextColor.RED)
            )
            val z = argv[3].toDoubleOrNull() ?: return@registerCommand sender.reply(
                Component.text("Invalid number: ${argv[3]}", NamedTextColor.RED)
            )
            who.teleport(x, y, z)
            sender.replyBroadcast(Component.text("Teleported ${who.username} to $x $y $z"))
        }
    }

    val GETBLOCK = registerCommand("getblock", Component.text("Gets a block and a position"), 1) { sender, args ->
        val argv = args.split(" ")
        if (argv.size < 3) {
            return@registerCommand sender.reply(
                Component.text("Usage:\n  /getblock <x> <y> <z>", NamedTextColor.RED)
            )
        }
        val x = argv[0].toIntOrNull() ?: return@registerCommand sender.reply(
            Component.text("Invalid integer: ${argv[0]}", NamedTextColor.RED)
        )
        val y = argv[1].toIntOrNull() ?: return@registerCommand sender.reply(
            Component.text("Invalid integer: ${argv[1]}", NamedTextColor.RED)
        )
        val z = argv[2].toIntOrNull() ?: return@registerCommand sender.reply(
            Component.text("Invalid integer: ${argv[2]}", NamedTextColor.RED)
        )
        val block = sender.server.world.getBlock(x, y, z)
        sender.replyBroadcast(
            Component.text("The block at $x $y $z is ")
                .append(Component.text(block.toString(), NamedTextColor.GREEN))
        )
    }

    val GAMEMODE = registerCommand("gamemode", Component.text("Set player gamemode"), 1) { sender, args ->
        if (args.isEmpty()) {
            return@registerCommand sender.reply(
                Component.text("Usage: /gamemode <gamemode> [player]", NamedTextColor.RED)
            )
        }
        val gamemodeString: String
        val who: PlayClient?
        if (' ' in args) {
            gamemodeString = args.substringBefore(' ')
            who = sender.evaluateClient(args.substringAfter(' '))
        } else {
            gamemodeString = args
            who = (sender as? ClientCommandSender)?.client ?: return@registerCommand sender.reply(
                Component.text("Usage: /op <gamemode> <player>", NamedTextColor.RED)
            )
        }
        if (who == null) {
            return@registerCommand sender.reply(
                Component.text("Player not found online", NamedTextColor.RED)
            )
        }
        val gamemode = enumValueOfOrNull(gamemodeString.uppercase()) ?:
            Gamemode.values().getOrNull(gamemodeString.toIntOrNull() ?: return@registerCommand sender.reply(
                Component.text("Invalid integer: $gamemodeString", NamedTextColor.RED)
            )) ?: return@registerCommand sender.reply(
                Component.text("Unknown gamemode: $gamemodeString", NamedTextColor.RED)
            )
        who.setGamemode(gamemode)
        sender.reply(Component.text("Set ${who.username}'s gamemode to ${gamemode.name.capitalize()}"))
    }

    val KICK = registerCommand("kick", Component.text("Forcefully disconnect a player"), 2) { sender, args ->
        val spaceIndex = args.indexOf(' ')
        val (username, reason) = if (spaceIndex != -1) {
            Pair(
                args.substring(0, spaceIndex),
                try {
                    GsonComponentSerializer.gson().deserialize(args.substring(spaceIndex + 1))
                } catch (e: JsonSyntaxException) {
                    Component.text(args.substring(spaceIndex + 1))
                }
            )
        } else {
            Pair(args, Component.text("Kicked by operator"))
        }
        val client = sender.evaluateClient(username)
        if (client == null || client.receiveChannel.isClosedForRead) {
            return@registerCommand sender.reply(Component.text("Player $username is not online.", NamedTextColor.RED))
        }
        client.sendPacket(PlayDisconnectPacket(reason))
        client.socket.dispose()
        sender.replyBroadcast(Component.text("Kicked $username for ").append(reason))
    }

    val OP = registerCommand("op", Component.text("Sets a player's operator level"), 3) { sender, args ->
        if (args.isEmpty()) {
            return@registerCommand sender.reply(
                Component.text("Usage: /op <player> [level]", NamedTextColor.RED)
            )
        }
        val who: PlayClient?
        val level: Int
        if (' ' in args) {
            who = sender.evaluateClient(args.substringBefore(' '))
            level = args.substringAfter(' ').toIntOrNull() ?: return@registerCommand sender.reply(
                Component.text("Invalid int ${args.substringAfter(' ')}", NamedTextColor.RED)
            )
        } else {
            who = sender.evaluateClient(args)
            level = min(sender.operator, 2)
        }
        if (who == null) {
            return@registerCommand sender.reply(
                Component.text("Player not found online", NamedTextColor.RED)
            )
        }
        if (level > sender.operator) {
            return@registerCommand sender.reply(
                Component.text("Can't set a player's operator level to be greater than your own.", NamedTextColor.RED)
            )
        }
        who.data.operatorLevel = level
        who.syncOpLevel()
        sender.replyBroadcast(Component.text("Set ${who.username}'s operator level to $level"))
    }

    val DEOP = registerCommand("deop", Component.text("Sets a player's operator level to 0"), 3) { sender, args ->
        if (args.isEmpty()) {
            return@registerCommand sender.reply(
                Component.text("Usage: /op <player> [level]", NamedTextColor.RED)
            )
        }
        OP.call(sender, "${args.substringBefore(' ')} 0")
    }

    internal fun register() = Unit
}
