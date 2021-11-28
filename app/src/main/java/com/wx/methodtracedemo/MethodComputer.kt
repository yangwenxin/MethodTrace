package com.wx.methodtracedemo

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.lang.StringBuilder
import java.util.*

object MethodComputer {
    private const val TAG = "MethodComputer"
    private var sDepth = -1
    private val START = SystemClock.elapsedRealtime()
    private val times = Stack<Long>()

    @JvmStatic
    fun onMethodEnter(clazz: String?, methodName: String?) {
        if (!isMainThread()) {
            return
        }
        sDepth++
        val stringBuilder = StringBuilder()
        for (i in 0 until sDepth) {
            stringBuilder.append("\t")
        }
        val prev = SystemClock.elapsedRealtime()
        Log.i(
            TAG,
            stringBuilder.toString() + (prev - START) + " Enter Method " + clazz + "." + methodName
        )
        times.push(prev)
    }

    @JvmStatic
    private fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    @JvmStatic
    fun onMethodExit(clazz: String?, methodName: String?) {
        if (!isMainThread()) {
            return
        }
        val stringBuilder = StringBuilder()
        for (i in 0 until sDepth) {
            stringBuilder.append("\t")
        }
        val prv = times.pop()
        val time = SystemClock.elapsedRealtime() - prv
        if (time > 10) {
            Log.i(
                TAG, stringBuilder.toString() + (SystemClock.elapsedRealtime() - START)
                        + " Exit Method " + clazz + "." + methodName + " Cost " + (SystemClock.elapsedRealtime() - prv)
            )
        }
        sDepth--
    }
}