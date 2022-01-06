package com.smx.yapt.common

class IntRangeEx {
    companion object {
        @JvmStatic
        fun IntRange.Companion.exclusive(start: Int, endExclusive: Int): IntRange {
            return IntRange(start, endExclusive - 1)
        }
    }
}