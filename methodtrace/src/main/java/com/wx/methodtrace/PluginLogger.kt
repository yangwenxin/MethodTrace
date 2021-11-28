package com.wx.methodtrace

/**
 * @author ywx
 * @date 2021-11-21
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