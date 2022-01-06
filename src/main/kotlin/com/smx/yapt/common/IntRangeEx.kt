package com.smx.yapt.common

fun IntRange.Companion.exclusive(start: Int, endExclusive: Int): IntRange {
    return IntRange(start, endExclusive - 1)
}