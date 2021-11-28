package com.wx.methodtrace

/**
 * @author ywx
 */
object PluginLogger {
    fun debug(log: String?) {
        println(log)
    }

    @JvmStatic
    fun info(log: String?) {
        println(log)
    }

    fun error(log: String?) {
        System.err.println(log)
    }
}