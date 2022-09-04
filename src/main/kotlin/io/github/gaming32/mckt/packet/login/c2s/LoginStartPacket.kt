package io.github.gaming32.mckt.packet.login.c2s

import io.github.gaming32.mckt.packet.MinecraftInputStream
import io.github.gaming32.mckt.packet.MinecraftOutputStream
import io.github.gaming32.mckt.packet.Packet
import java.util.UUID

class LoginStartPacket(
    val username: String,
    val signatureInfo: SignatureInfo? = null,
    val uuid: UUID? = null
) : Packet(TYPE) {
    companion object {
        const val TYPE = 0x00
    }

    class SignatureInfo(val timestamp: Long, val publicKey: ByteArray, val signature: ByteArray)

    constructor(inp: MinecraftInputStream) : this(
        inp.readString(16),
        if (!inp.readBoolean()) null else inp.run { SignatureInfo(
            readLong(),
            ByteArray(readVarInt()).also { readFully(it) },
            ByteArray(readVarInt()).also { readFully(it) }
        ) },
        if (!inp.readBoolean()) null else inp.readUuid()
    )

    override fun write(out: MinecraftOutputStream) {
        out.writeString(username, 16)
        out.writeBoolean(signatureInfo != null)
        signatureInfo?.let {
            out.writeLong(it.timestamp)
            out.writeVarInt(it.publicKey.size)
            out.write(it.publicKey)
            out.writeVarInt(it.signature.size)
            out.write(it.signature)
        }
        out.writeBoolean(uuid != null)
        uuid?.let { out.writeUuid(it) }
    }
}
