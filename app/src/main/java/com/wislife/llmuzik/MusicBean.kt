package com.wislife.llmuzik

// 与云端SpringBoot的Music实体类一一对应，字段名必须一致
data class MusicBean(
    var musicName: String,
    val downloadUrl: String,
    val artist: String = "未知艺术家",
    val lyricUrl: String?
)