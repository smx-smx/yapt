package com.smx.yapt.ssh

import com.jcraft.jsch.Logger

class JSCHLogger : Logger {
    override fun isEnabled(level: Int): Boolean {
        return true
    }

    override fun log(level: Int, msg: String?) {
        val tag = when (level) {
            Logger.DEBUG -> "DEBUG"
            Logger.ERROR -> "ERROR"
            Logger.FATAL -> "FATAL"
            Logger.INFO -> "INFO"
            Logger.WARN -> "WARNING"
            else -> "LOG"
        }
        println("[$tag]: $msg")
    }

}