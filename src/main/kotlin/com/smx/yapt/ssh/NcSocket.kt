package com.smx.yapt.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import java.io.*

class NcSocket(
    private val session: Session,
    host: String, port: Int
) : AutoCloseable {
    private val buf = ByteArrayOutputStream()
    private val cmd = session.openChannel("exec") as ChannelExec

    val inputStream get() = cmd.inputStream
    val outputStream get() = cmd.outputStream

    init {
        cmd.outputStream = buf
        cmd.setCommand("exec nc $host $port")
        cmd.connect()
    }

    override fun close() {
        // send SIGINT
        cmd.sendSignal("INT")
        cmd.disconnect()
    }
}