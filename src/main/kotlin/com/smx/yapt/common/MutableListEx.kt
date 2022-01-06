package com.smx.yapt.common

fun <E> MutableList<E>.putIfAbsent(e: E) {
    if(!this.contains(e)) this.add(e)
}