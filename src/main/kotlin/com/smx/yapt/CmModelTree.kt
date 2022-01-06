package com.smx.yapt

import com.smx.yapt.ssh.CmModelProperties

class CmModelTree(val items:Map<String, CmNode>) {
    operator fun get(key: String): CmModelProperties? = items[key]?.props

    val isLeaf get() = items.none { it.key.endsWith(".") }
    val objects get() = items.filter { it.key.endsWith(".") }
    val properties get() = items.filter { !it.key.endsWith(".") }

    private fun filterByDepth(
        items: Map<String, CmNode>,
        root: String,
        depth: Int = 0
    ): List<Map.Entry<String, CmNode>> {
        val wantedNumDots = depth + 1
        return items
            .filter { it.key.indexOf(root) == 0}
            .map {
                // remove the object marker at the end
                val name = it.key.let {
                    when(it.lastOrNull()) {
                        '.' -> it.dropLast(1)
                        else -> it
                    }
                }

                // Device.Hosts[.Host.5.X_ADB_LastUp]
                val subPath = name.substring(root.length)
                val numDots = subPath.count { it == '.' }
                Pair(numDots, it)
            }
            // skip nested properties (if any)
            .filter { (numDots, _) -> numDots == wantedNumDots }
            .map { (_, e) -> e }
    }

    fun getObjects(root: String, depth: Int = 0) : List<Map.Entry<String, CmNode>> {
        return filterByDepth(objects, root, depth)
    }

    fun getProperties(root:String, depth:Int = 0): List<Map.Entry<String, CmNode>> {
        return filterByDepth(properties, root, depth)
    }
}