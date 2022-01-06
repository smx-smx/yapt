package com.smx.yapt

import com.smx.yapt.common.resettableManager
import com.smx.yapt.ssh.CmClientSession
import com.smx.yapt.ssh.CmModelProperties
import com.smx.yapt.ssh.CmTypeId

data class CmNode (
    private val cm: CmClientSession,
    val path: String,
    val props: CmModelProperties
) {
    val fullName: String get() = path.trimEnd('.')
    val baseName: String get(){
        val tmp = fullName
        val lastDot = tmp.lastIndexOf('.')
        return if(lastDot == -1)
            tmp
        else
            tmp.substring(lastDot + 1)
    }

    val isObject: Boolean get() = path.endsWith('.')
    val isProperty: Boolean get() = !path.endsWith('.')

    val lazymgr = resettableManager()

    private val valueParser by lazy {
        val isObject = props.type == null
        if(isObject) null

        val parser = when(props.type?.typeName){
            CmTypeId.STRING.str -> object: CmValueFactory<String> {
                override fun parse(data: String): String {
                    return data
                }
            }
            CmTypeId.INT.str -> object: CmValueFactory<Int> {
                override fun parse(data: String): Int {
                    return data.toInt()
                }
            }
            CmTypeId.LONG.str -> object: CmValueFactory<Long> {
                override fun parse(data: String): Long {
                    return data.toLong()
                }
            }
            CmTypeId.UINT.str -> object: CmValueFactory<UInt> {
                override fun parse(data: String): UInt {
                    return data.toUInt()
                }
            }
            CmTypeId.BOOLEAN.str -> object: CmValueFactory<Boolean> {
                override fun parse(data: String): Boolean {
                    return data == "true"
                }
            }
            else -> null
        }

        parser
    }

    fun getValue() : Any {
        val parser = valueParser ?: throw NotImplementedError("no valueparser for ${props.type?.typeName}")

        val isList = props.isList
        val values = cm.getValue(path)

        return if(isList){
            values
                .map { parser.parse(it) }
                .toList()
                .toTypedArray()
        } else {
            values.first().let { parser.parse(it) }
        }
    }
}