package com.wx.methodtrace

import java.io.File
import java.io.FileNotFoundException
import java.util.HashSet

/**
 * @author ywx
 * @date 2021-11-21
 */
class Config {

    //一些默认无需插桩的类
    val UNNEED_TRACE_CLASS = arrayOf("R.class", "R$", "Manifest", "BuildConfig")

    //插桩配置文件
    var traceConfigFile: String? = null

    //需插桩的包
    private val needTracePackageMap: HashSet<String> by lazy {
        HashSet<String>()
    }

    //插桩代码所在类
    var beatClass: String? = null

    fun isNeedTraceClass(fileName: String): Boolean {
        var isNeed = true
        if (fileName.endsWith(".class")) {
            for (unTraceCls in UNNEED_TRACE_CLASS) {
                if (fileName.contains(unTraceCls)) {
                    isNeed = false
                    break
                }
            }
        } else {
            isNeed = false
        }
        return isNeed
    }

    //判断是否是traceConfig.txt中配置范围的类
    fun isConfigTraceClass(className: String): Boolean {
        var isIn = false
        if (!needTracePackageMap.isNullOrEmpty()) {
            needTracePackageMap.forEach {
                if (className.contains(it)) {
                    isIn = true
                    return@forEach
                }
            }
        }
        return isIn
    }


    /**
     * 解析插桩配置文件
     */
    fun parseTraceConfigFile() {
        PluginLogger.info("parseTraceConfigFile start!!!!!!!!!!!!")
        val traceConfigFile = File(traceConfigFile)
        if (!traceConfigFile.exists()) {
            throw FileNotFoundException(
                """
                    找不到 ${this.traceConfigFile} 配置文件
                """.trimIndent()
            )
        }

        val configStr = Utils.readFileAsString(traceConfigFile.absolutePath)

        val configArray =
            configStr.split(System.lineSeparator().toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()

        if (configArray != null) {
            for (element in configArray) {
                var config = element
                if (config.isNullOrBlank()) {
                    continue
                }
                if (config.startsWith("#")) {
                    continue
                }
                if (config.startsWith("[")) {
                    continue
                }

                when {
                    config.startsWith("-tracepackage ") -> {
                        config = config.replace("-tracepackage ", "")
                        needTracePackageMap.add(config)
                        PluginLogger.info("tracepackage:$config")
                    }
                    config.startsWith("-beatclass ") -> {
                        config = config.replace("-beatclass ", "")
                        beatClass = config
                        PluginLogger.info("beatclass:$config")
                    }
                    else -> {
                    }
                }
            }

        }


    }

}


