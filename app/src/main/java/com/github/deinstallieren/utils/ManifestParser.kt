package com.github.deinstallieren.utils

import android.content.res.AssetManager
import android.content.res.XmlResourceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

object ManifestParser {

    suspend fun decodeManifest(apkPath: String): String = withContext(Dispatchers.IO) {
        val result = StringBuilder()
        var parser: XmlResourceParser? = null
        try {
            val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            val cookie = addAssetPath.invoke(assetManager, apkPath) as Int

            parser = assetManager.openXmlResourceParser(cookie, "AndroidManifest.xml")

            var indent = 0
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        result.append("  ".repeat(indent))
                        result.append("<").append(parser.name)
                        for (i in 0 until parser.attributeCount) {
                            result.append("\n")
                            result.append("  ".repeat(indent + 1))
                            result.append(parser.getAttributeName(i))
                                .append("=\"")
                                .append(parser.getAttributeValue(i))
                                .append("\"")
                        }
                        result.append(">\n")
                        indent++
                    }
                    XmlPullParser.END_TAG -> {
                        indent = (indent - 1).coerceAtLeast(0)
                        result.append("  ".repeat(indent))
                        result.append("</").append(parser.name).append(">\n")
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Throwable) {
            result.clear()
            result.append("Error al decodificar AndroidManifest.xml:\n").append(e.message)
        } finally {
            parser?.close()
        }
        result.toString()
    }
}
