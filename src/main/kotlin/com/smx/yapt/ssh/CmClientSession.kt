package com.smx.yapt.ssh

import com.smx.yapt.CmModelTree
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

enum class CmAccess(val str:String) {
    READ_ONLY("ro"),
    READ_WRITE("rw");

    companion object {
        fun fromString(value: String): CmAccess? {
            return when(value){
                READ_ONLY.str -> READ_ONLY
                READ_WRITE.str -> READ_WRITE
                else -> null
            }
        }
    }
}

enum class CmType(val str:String){
    BOOLEAN("boolean"),
    INT("int"),
    UINT("unsignedInt"),
    LONG("long"),
    STRING("string"),
    ALIAS("Alias"),
    OPAQUE("?"),
    DATETIME("dateTime");

    companion object {
        fun fromString(value: String): CmType? {
            return when (value) {
                BOOLEAN.str -> BOOLEAN
                INT.str -> INT
                UINT.str -> UINT
                LONG.str -> LONG
                STRING.str -> STRING
                DATETIME.str -> DATETIME
                else -> null
            }
        }
    }
}

class CmModel(val items:HashMap<String, String?> = HashMap()){
    operator fun get(key: String) = items[key]
}

class CmClientSession(private val session: YapsSession) : AutoCloseable {
    private var cmSocket:NcSocket = session.openSocket("127.0.0.1", 9034)

    private val cmLock = Object()

    private val modelParser = Pattern.compile("""
        ^([^\t].*)\n((?:\t.*\n)*)
    """.trimIndent(), Pattern.MULTILINE)

    private fun sendMessage(msg: String): String {
        val pkt = ByteArray(msg.length + 2)
        msg.forEachIndexed { index, c -> pkt[index] = c.code.toByte() }
        pkt[msg.length+0] = 0x0a
        pkt[msg.length+1] = 0x00

        val sb = StringBuilder()
        var count = 0

        synchronized(cmLock) {
            val cmOut = cmSocket.inputStream
            val cmIn = cmSocket.outputStream

            cmIn.write(pkt)
            cmIn.flush()

            cmOut.use {
                while (true) {
                    val b = it.read()
                    if (b <= 0) break // \0 is EOF
                    ++count
                    sb.append(b.toChar())
                }
            }
        }

        if(count > 0) {
            // each line ends with "\n". delete the trailing newline
            sb.deleteAt(sb.lastIndex)
        }

        return sb.toString()
    }

    fun setValue(key: String, value: String): String {
        return sendMessage("SET $key $value")
    }

    private fun extractModelDef(output: String): CmModelTree {
        val r = CmModelTree()

        val m = modelParser.matcher(output)
        while(m.find()){
            val key = m.group(1)
            val model = CmModel()
            m.group(2)
                .replace("\t", "").trimEnd()
                .lines()
                .map { it.split(':') }
                .filter { it.isNotEmpty() }
                .associateTo(model.items) {
                    when(it.size){
                        // foo
                        1 -> Pair(it[0], null)
                        // FIXME: this doesn't handle multiple attributes correctly
                        // e.g.
                        // type:string:size:0..256
                        // pathref:targetType:6
                        else -> Pair(it[0], it[1])
                    }
                }

            r[key] = model
        }

        return r
    }

    private fun extractMap(output: String): HashMap<String, String> {
        val list = HashMap<String, String>()
        output.lines().forEach {
            val parts = it.split(';', ignoreCase = true, limit = 2)
            if(parts.size < 2) return@forEach

            val (key, value) = parts
            list[key] = value
        }
        return list
    }

    fun get(name: String) : Map<String, String> {
        return extractMap(sendMessage("GET $name"))
    }

    fun getForcedInform(query:String): Map<String, String> {
        return extractMap(sendMessage("GETFI $query"))
    }

    fun getValue(query:String): Array<String> {
        return sendMessage("GETV $query").lines().toTypedArray()
    }

    fun getObject(query: String, index: Int? = null) : Array<String> {
        var q = "GETO $query"
        if(index != null) q += " $index"
        return sendMessage(q).lines().toTypedArray()
    }

    fun getModel(query: String) {
        sendMessage("GETMD $query")
    }

    fun getModelForObjects(query: String) {
        sendMessage("GETMDO $query")
    }

    fun getModelForParams(query: String, maxDepth: Int) : CmModelTree {
        return extractModelDef(sendMessage("GETMDP $query $maxDepth"))
    }

    fun startCm(): String {
        return sendMessage("START")
    }

    fun stopCm(): String {
        return sendMessage("STOP")
    }

    fun restartCm() : String {
        return sendMessage("RESTARTCM")
    }

    fun loadDom(remotePath: String){
        sendMessage("DOM $remotePath")
    }

    fun loadConf(remotePath: String){
        sendMessage("CONF $remotePath")
    }

    fun dumpDeviceModel() : String {
        val tmpPath = "/tmp/.deviceinfo.xml"
        sendMessage("DUMPDM $tmpPath")
        val deviceInfo = session.download(tmpPath){
            it.readAllBytes().toString(StandardCharsets.UTF_8)
        }
        session.exec("rm $tmpPath")
        return deviceInfo
    }

    fun saveConfig(): String {
        return sendMessage("SAVE")
    }

    override fun close() {
        cmSocket.close()
    }
}