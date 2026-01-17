package com.wislife.llmuzik
import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object HttpManager {
    private val okHttpClient = OkHttpClient()

    // 从云端拉取歌曲列表，返回JSON字符串
    fun getMusicListFromCloud(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    // 下载歌曲到本地缓存目录，下载前自动检查缓存数量
    fun downloadMusic(context: Context, downloadUrl: String, musicName: String): Boolean {
        FileManager.checkAndDeleteOldestCache(context) // 下载前必做：检查缓存数量
        val targetFile = FileManager.getCachedMusicFile(context, musicName)
        val request = Request.Builder().url(downloadUrl).get().build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val inputStream: InputStream = response.body!!.byteStream()
                val fos = FileOutputStream(targetFile)
                val buffer = ByteArray(1024 * 8)
                var len: Int
                while (inputStream.read(buffer).also { len = it } != -1) {
                    fos.write(buffer, 0, len)
                }
                fos.flush()
                fos.close()
                inputStream.close()
                true
            } else {
                false
            }
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
    fun getText(url: String): String? {
        return try {
            OkHttpClient().newCall(Request.Builder().url(url).build())
                .execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
        } catch (e: Exception) {
            null
        }
    }
}