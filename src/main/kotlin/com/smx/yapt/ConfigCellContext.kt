package com.smx.yapt

import javafx.beans.value.ChangeListener
import javafx.scene.control.*
import javafx.util.converter.NumberStringConverter

data class ConfigCellContext(
    val displayName: String?,
    val displayValue: String?,
    val node: CmNode?
){
    val isHidden
        get() = displayName == null

    var cell: ConfigTreeCell? = null

    var childrenModel: CmModelTree? = null
    var expandListener: ChangeListener<Boolean>? = null

    val controls = makeControls(node)

    private fun makeControls(node: CmNode?): Collection<Control> {
        if(node == null){
            return emptyList()
        }
        return when (val value = node.getValue()) {
            is Collection<*> -> {
                value
                    .filterNotNull()
                    .map { makeControls(it) }
            }
            else -> {
                listOf(makeControls(value))
            }
        }
    }

    private fun makeControls(value:Any) : Control {
        return when(value){
            is Boolean -> {
                CheckBox("").also { it.isSelected = value }
            }
            is Int, is UInt, is Long, is ULong -> {
                TextField("").also {
                    it.textFormatter = TextFormatter(NumberStringConverter())
                    it.text = value.toString()
                }
            }
            is String -> {
                TextField("").also {
                    it.text = value as String?
                }
            }
            else -> throw NotImplementedError(value.javaClass.toString())
        }
    }
}