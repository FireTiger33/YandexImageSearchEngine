package com.stacktivity.yandeximagesearchengine.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.SSLException

class ImageParser {
    companion object {
        private val imageLinkRegex: Regex = Regex("((https?:/)?/[^\\s\"]*?\\.(jpe?g|png))")

        /**
         * Search direct and relative links to images on site.
         *
         * Catches exceptions related to unsupported SSL certificates.
         *
         * @return list of direct image urls
         */
        suspend fun getUrlListToImages(parentUrl: String): List<String> =
            withContext(Dispatchers.IO) {
                val linkSet: MutableSet<String> = HashSet()
                val url = URL(parentUrl)
                try {
                    with(BufferedReader(InputStreamReader(url.openStream()))) {
                        var inputLine: String?
                        while (this.readLine().also { inputLine = it } != null) {
                            val lineDataLIst = imageLinkRegex.findAll(inputLine!!)

                            lineDataLIst.forEach { data ->
                                val dataUrl = try {
                                    URL(data.value)
                                } catch (e: MalformedURLException) {
                                    URL(url.protocol, url.host, data.value)
                                }

                                linkSet.add(decodeUnicode(dataUrl.toString()))
                            }
                        }
                    }
                } catch (e: SSLException) {
                    e.printStackTrace()
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }

                return@withContext linkSet.toList()
            }



        /**
        * Decodes Unicode characters when found.
        *
        * @return decoded string or origin str
        */
        private fun decodeUnicode(str: String): String {
            var res = str
            val unicodeRegex = Regex("\\\\u[a-fA-f0-9]{4}")
            val hexItems: MutableSet<String> = HashSet()
            unicodeRegex.findAll(res).forEach { matchResult ->
                hexItems.add(matchResult.value)
            }

            hexItems.forEach { unicodeHex ->
                val hexVal = unicodeHex.substring(2).toInt(16)
                res = res.replace(unicodeHex, "" + hexVal.toChar())
            }

            return res
        }
    }
}