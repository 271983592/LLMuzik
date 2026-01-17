package com.wislife.llmuzik
import android.content.Context
import java.io.File

object FileManager {
    const val MAX_CACHE_COUNT = 5 // 硬性限制：最多缓存5首歌曲
    private const val MUSIC_DIR_NAME = "MusicCache" // 本地缓存文件夹名称

    // 获取本地音乐缓存根目录，Android10+沙盒存储，无需动态权限
    fun getMusicCacheDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), MUSIC_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // 判断指定歌曲是否已缓存到本地
    fun isMusicCached(context: Context, musicName: String): Boolean {
        val file = File(getMusicCacheDir(context), musicName)
        return file.exists() && file.length() > 0
    }

    // 获取本地缓存的歌曲文件对象
    fun getCachedMusicFile(context: Context, musicName: String): File {
        return File(getMusicCacheDir(context), musicName)
    }

    // 核心核心：超出5首自动删除【最后修改时间最早】的缓存歌曲，严格控制缓存数量
    fun checkAndDeleteOldestCache(context: Context) {
        val cacheDir = getMusicCacheDir(context)
        val musicFiles = cacheDir.listFiles()?.filter { it.name.endsWith(".mp3") } ?: return

        if (musicFiles.size >= MAX_CACHE_COUNT) {
            val sortedFiles = musicFiles.sortedBy { it.lastModified() } // 按修改时间排序：旧→新
            // 删除最旧的，直到缓存数≤4
            for (i in 0 until sortedFiles.size - MAX_CACHE_COUNT + 1) {
                sortedFiles[i].delete()
            }
        }
    }

    // 获取本地所有缓存的歌曲名称列表
    fun getLocalCacheList(context: Context): List<String> {
        val cacheDir = getMusicCacheDir(context)
        return cacheDir.listFiles()?.filter { it.name.endsWith(".mp3") }?.map { it.name } ?: emptyList()
    }
}