package com.smx.yapt.ssh

import com.jcraft.jsch.*
import java.io.*
import java.util.*

class SshSessionFactory(){
    var hostname:String = "192.168.1.1"
        get set
    var username:String = "root"
        get set
    var port:Int = 22
        get set

    var password:String = System.getProperty("user.home") + "/.ssh/id_rsa"
        get set

    fun connect(): YapsSession {
        val isKeyAuth = File(password).exists()
        JSch.setLogger(JSCHLogger())
        val jsch = JSch()
        if(isKeyAuth){
            jsch.addIdentity(password)
        }
        val sess = jsch.getSession(username, hostname, port)
        if(!isKeyAuth){
            sess.setPassword(password)
        }

        val opts = Properties().apply {
            put("StrictHostKeyChecking", "no")
        }
        sess.setConfig(opts)

        // bind CM port - not supported by dropbear
        // sess.setPortForwardingL(9034, "127.0.0.1", 9034)

        sess.connect()
        return YapsSession(sess)
    }
}

interface IYapsSession : AutoCloseable {
    val cm:CmClientSession get
    fun <T> download(filePath: String, cb: (InputStream) -> T): T
    fun openSocket(host: String, port: Int) : NcSocket
    fun exec(cmd:String): String
}

class NullYapsSession : IYapsSession {
    override val cm: CmClientSession
        get() = throw IllegalStateException()

    override fun <T> download(filePath: String, cb: (InputStream) -> T): T = throw IllegalStateException()
    override fun openSocket(host: String, port: Int): NcSocket = throw IllegalStateException()
    override fun exec(cmd: String): String = throw IllegalStateException()
    override fun close() {}
}

class YapsSession(private val session: Session) : IYapsSession, AutoCloseable {
    override val cm:CmClientSession by lazy {
        CmClientSession(this)
    }

    override fun <T> download(filePath: String, cb: (InputStream) -> T): T {
        val sftp = session.openChannel("sftp") as ChannelSftp
        sftp.connect()

        val stream = sftp.get(filePath)
        val result = cb(stream)
        sftp.disconnect()
        return result
    }

    override fun openSocket(host: String, port: Int) : NcSocket {
        return NcSocket(session, host, port)
    }

    override fun exec(cmd:String): String {
        val sh = session.openChannel("shell") as ChannelShell
        sh.connect()

        val uuid = UUID.randomUUID().toString()

        val cmdStream = sh.outputStream.let { PrintStream(it) }
        cmdStream.apply {
            println("echo \"$uuid\"; ${cmd}; echo \"$uuid\"")
            flush()
        }

        val output = StringBuffer()

        val lines = sh.inputStream.bufferedReader().lineSequence()
        lines
            .dropWhile { !it.startsWith(uuid) }
            .drop(1)
            .takeWhile { !it.startsWith(uuid) }
            .forEach {
                //println("> $it")
                output.appendLine(it)
            }

        cmdStream.apply {
            println("exit")
            flush()
        }

        sh.disconnect()
        return output.toString().trimEnd()
    }

    override fun close() {
        session.disconnect()
    }

}