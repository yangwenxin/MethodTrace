package com.wx.methodtrace

import java.io.*
import java.util.zip.ZipFile

/**
 * @author ywx
 */
object Utils {

    @JvmStatic
    fun inputStreamToBytes(inputStream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var n = 0
        while (-1 != inputStream.read(buffer).also { n = it }) {
            output.write(buffer, 0, n)
        }
        val body = output.toByteArray()
        output.close()
        inputStream.close()
        return body
    }

    @JvmStatic
    fun readFileAsString(filePath: String): String {
        val fileData = StringBuffer()
        var fileReader: Reader? = null
        var inputStream: InputStream? = null
        try {
            inputStream = FileInputStream(filePath)
            fileReader = InputStreamReader(inputStream, "UTF-8")
            val buf = CharArray(1024)
            var numRead = fileReader.read(buf)
            while (numRead != -1) {
                val readData = String(buf, 0, numRead)
                fileData.append(readData)
                numRead = fileReader.read(buf)
            }
        } catch (e: Exception) {

        } finally {
            try {
                closeQuietly(fileReader)
                closeQuietly(inputStream)
            } catch (e: Exception) {
            }

        }
        return fileData.toString()
    }

    @JvmStatic
    private fun closeQuietly(obj: Any?) {
        if (obj == null) {
            return
        }
        when (obj) {
            is Closeable -> try {
                obj.close()
            } catch (ignored: Throwable) {
                // ignore
            }
            is AutoCloseable -> try {
                obj.close()
            } catch (ignored: Throwable) {
                // ignore
            }
            is ZipFile -> try {
                obj.close()
            } catch (ignored: Throwable) {
                // ignore
            }
            else -> throw IllegalArgumentException("obj $obj is not closeable")
        }
    }

    fun isEmpty(s: CharSequence?): Boolean {
        return s?.isEmpty() ?: true
    }
}