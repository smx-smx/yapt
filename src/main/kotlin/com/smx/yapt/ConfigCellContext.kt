package com.smx.yapt

import javafx.beans.value.ChangeListener

data class ConfigCellContext(
    val displayName: String?,
    val displayValue: String?,
    val node: CmNode?
){
    var childrenModel: CmModelTree? = null
    var expandListener: ChangeListener<Boolean>? = null
}