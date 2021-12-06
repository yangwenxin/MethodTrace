package com.wx.methodtrace.gradle

/**
 * 自定义的配置项extension
 */
class MethodTraceConfig {
    boolean open
    String traceConfigFile

    MethodTraceConfig() {
        open = true
    }
}