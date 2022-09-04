@file:OptIn(ExperimentalSerializationApi::class)

package io.github.gaming32.mckt

import io.github.gaming32.mckt.objects.Identifier
import io.github.gaming32.mckt.packet.PacketState
import io.github.gaming32.mckt.packet.login.c2s.LoginStartPacket
import io.github.gaming32.mckt.packet.login.s2c.LoginDisconnectPacket
import io.github.gaming32.mckt.packet.login.s2c.LoginSuccessPacket
import io.github.gaming32.mckt.packet.play.PlayPingPacket
import io.github.gaming32.mckt.packet.play.c2s.*
import io.github.gaming32.mckt.packet.play.s2c.*
import io.github.gaming32.mckt.packet.sendPacket
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.kyori.adventure.text.Component
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import kotlin.time.Duration.Companion.nanoseconds

class PlayClient(
    server: MinecraftServer,
    socket: Socket,
    receiveChannel: ByteReadChannel,
    sendChannel: ByteWriteChannel
) : Client(server, socket, receiveChannel, sendChannel) {
    companion object {
        private val LOGGER = getLogger()
    }

    data class ClientOptions(
        val locale: String = "en_us",
        val viewDistance: Int = 10,
        val chatMode: Int = 0,
        val chatColors: Boolean = true,
        val displayedSkinParts: Int = 127,
        val mainHand: Int = 0,
        val textFiltering: Boolean = false,
        val allowServerListings: Boolean = true
    ) {
        constructor(client: PlayClient) : this(
            viewDistance = client.server.config.viewDistance
        )
    }

    override val primaryState = PacketState.PLAY

    lateinit var username: String
        private set
    lateinit var uuid: UUID
        private set
    lateinit var handlePacketsJob: Job
    private var nextTeleportId = 0

    internal var nextPingId = 0
    internal var pingId = -1
    internal var pingStart = 0L

    lateinit var dataFile: File
        private set
    lateinit var data: PlayerData
        private set

    var options = ClientOptions(this)

    suspend fun handshake() {
        val loginStart = PacketState.LOGIN.readPacket<LoginStartPacket>(receiveChannel)
        if (loginStart == null) {
            sendChannel.sendPacket(LoginDisconnectPacket(Component.text("Unexpected packet")))
            socket.dispose()
            return
        }
        username = loginStart.username
        if (!(username matches USERNAME_REGEX)) {
            sendChannel.sendPacket(
                LoginDisconnectPacket(Component.text("Username doesn't match regex $USERNAME_REGEX"))
            )
            socket.dispose()
            return
        }
        uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".encodeToByteArray())
        sendChannel.sendPacket(LoginSuccessPacket(uuid, username))
    }

    suspend fun postHandshake() {
        sendChannel.sendPacket(PlayLoginPacket(
            entityId = 0,
            hardcore = false,
            gamemode = Gamemode.CREATIVE,
            previousGamemode = null,
            dimensions = listOf(Identifier("overworld")),
            registryCodec = DEFAULT_REGISTRY_CODEC,
            dimensionType = Identifier("overworld"),
            dimensionName = Identifier("overworld"),
            hashedSeed = 0L,
            maxPlayers = server.config.maxPlayers,
            viewDistance = server.config.viewDistance,
            simulationDistance = server.config.simulationDistance,
            reducedDebugInfo = false,
            enableRespawnScreen = false,
            isDebug = false,
            isFlat = true,
            deathLocation = null
        ))

        dataFile = File(server.world.playersDir, "$username.json")
        data = try {
            dataFile.inputStream().use { PRETTY_JSON.decodeFromStream(it) }
        } catch (e: Exception) {
            if (e !is FileNotFoundException) {
                LOGGER.warn("Couldn't read player data, creating anew", e)
            }
            PlayerData()
        }
        sendChannel.sendPacket(
            ClientboundPlayerAbilitiesPacket(
                invulnerable = true,
                flying = data.flying,
                allowFlying = true,
                creativeMode = true
            )
        )
        sendChannel.sendPacket(PlayerPositionSyncPacket(nextTeleportId++, data.x, data.y, data.z, data.yaw, data.pitch))
        server.broadcast(PlayerListUpdatePacket(
            PlayerListUpdatePacket.AddPlayer(
                uuid = uuid,
                name = username,
                properties = mapOf(),
                gamemode = Gamemode.CREATIVE,
                ping = -1,
                displayName = null,
                signatureData = null
            )
        ))
    }

    suspend fun handlePackets() {
        while (server.running) {
            val packet = try {
                readPacket()
            } catch (e: Exception) {
                if (e !is ClosedReceiveChannelException) {
                    sendChannel.sendPacket(PlayDisconnectPacket(
                        Component.text(e.toString())
                    ))
                    socket.dispose()
                    LOGGER.warn("Client connection had error", e)
                }
                break
            }
            when (packet) {
                is ConfirmTeleportationPacket -> if (packet.teleportId >= nextTeleportId) {
                    LOGGER.warn("Client sent unknown teleportId {}", packet.teleportId)
                }
                is CommandPacket -> sendChannel.sendPacket(SystemChatPacket(
                    Component.text("Commands not implemented yet")
                ))
                is ServerboundChatPacket -> server.broadcastIf(SystemChatPacket(
                    Component.translatable(
                        "chat.type.text",
                        Component.text(username),
                        Component.text(packet.message)
                    )
                )) { it.options.chatMode == 0 }
                is ClientOptionsPacket -> options = packet.options
                is ServerboundPlayPluginPacket -> LOGGER.info("Plugin packet {}", packet.channel)
                is MovementPacket -> {
                    packet.x?.let { data.x = it }
                    packet.y?.let { data.y = it }
                    packet.z?.let { data.z = it }
                    packet.yaw?.let { data.yaw = it }
                    packet.pitch?.let { data.pitch = it }
                    data.onGround = packet.onGround
                }
                is ServerboundPlayerAbilitiesPacket -> data.flying = packet.flying
                is PlayPingPacket -> if (packet.id == pingId) {
                    val pingTime = System.nanoTime() - pingStart
                    pingId = -1
                    server.broadcast(PlayerListUpdatePacket(
                        PlayerListUpdatePacket.UpdatePing(uuid, pingTime.nanoseconds.inWholeMilliseconds.toInt())
                    ))
                }
                else -> LOGGER.warn("Unhandled packet {}", packet)
            }
        }
    }

    fun save() {
        dataFile.outputStream().use { PRETTY_JSON.encodeToStream(data, it) }
    }

    fun close() = save()
}
