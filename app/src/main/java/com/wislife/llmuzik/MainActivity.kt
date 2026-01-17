package com.wislife.llmuzik

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private enum class PlayMode { ORDER, SHUFFLE }

    private lateinit var rvMusic: RecyclerView
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var waveform: WaveformView
    private lateinit var adapter: TrackAdapter
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvLyric: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPause: ImageButton
    private lateinit var cbOrder: CheckBox
    private lateinit var cbShuffle: CheckBox

    private var musicList = mutableListOf<MusicBean>()
    private val cloudMusicListUrl = "https://47.94.246.110:8882/api/musicList"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentIndex = -1
    private var playMode = PlayMode.ORDER

    // 定时刷新云端列表
    private val refreshIntervalMs = 60_000L
    private val musicListRefreshRunnable = object : Runnable {
        override fun run() {
            getMusicList()
            mainHandler.postDelayed(this, refreshIntervalMs)
        }
    }

    // 歌词相关
    private var lyricLines: List<String> = emptyList()
    private var currentLrcIndex = -1
    private var segmentDurationMs: Long = 0L
    private var lyricLoadToken = 0 // 用于防止歌词串台

    private val progressUpdater = object : Runnable {
        override fun run() {
            val duration = exoPlayer.duration
            if (duration > 0) {
                val pos = exoPlayer.currentPosition
                seekBar.max = duration.toInt()
                seekBar.progress = pos.toInt()
                updateLyric(pos)
            }
            mainHandler.postDelayed(this, 500)
        }
    }

    companion object {
        private const val REQ_RECORD_AUDIO = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvMusic = findViewById(R.id.rv_music)
        rvMusic.layoutManager = LinearLayoutManager(this)
        rvMusic.setHasFixedSize(true)
        rvMusic.isVerticalScrollBarEnabled = true
        adapter = TrackAdapter { position -> playTrackAt(position) }
        rvMusic.adapter = adapter

        initView()
        initPlayer()
        getMusicList()
        // 启动每分钟刷新一次列表
        mainHandler.postDelayed(musicListRefreshRunnable, refreshIntervalMs)
    }

    private fun initView() {
        waveform = findViewById(R.id.waveform)
        tvTitle = findViewById(R.id.tv_title)
        tvArtist = findViewById(R.id.tv_artist)
        tvLyric = findViewById(R.id.tv_lyric)
        seekBar = findViewById(R.id.slider_progress)
        btnPause = findViewById(R.id.btn_pause)
        cbOrder = findViewById(R.id.cb_order)
        cbShuffle = findViewById(R.id.cb_shuffle)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) exoPlayer.seekTo(progress.toLong())
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 暂停 / 播放
        btnPause.setOnClickListener {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
                btnPause.setImageResource(R.drawable.ic_play)
            } else {
                exoPlayer.play()
                btnPause.setImageResource(R.drawable.ic_pause)
            }
        }

        // 顺序/随机互斥
        cbOrder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                playMode = PlayMode.ORDER
                if (cbShuffle.isChecked) cbShuffle.isChecked = false
                showToast("顺序播放")
            } else if (!cbShuffle.isChecked) {
                // 至少保持一个选中
                cbOrder.isChecked = true
            }
        }
        cbShuffle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                playMode = PlayMode.SHUFFLE
                if (cbOrder.isChecked) cbOrder.isChecked = false
                showToast("随机播放")
            } else if (!cbOrder.isChecked) {
                cbShuffle.isChecked = true
            }
        }
        cbOrder.isChecked = true
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        exoPlayer = ExoPlayer.Builder(this)
            .setLooper(Looper.getMainLooper())
            .build()
        exoPlayer.playWhenReady = true
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val id = exoPlayer.audioSessionId
                    if (id != C.AUDIO_SESSION_ID_UNSET) {
                        if (hasAudioPermission()) {
                            tryAttachVisualizer(id)
                        } else {
                            requestAudioPermission()
                        }
                    }
                    calcSegmentDuration()
                    mainHandler.post(progressUpdater)
                }
                if (state == Player.STATE_ENDED) {
                    playNext()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                if (isPlaying) {
                    mainHandler.post(progressUpdater)
                } else {
                    mainHandler.removeCallbacks(progressUpdater)
                }
            }
        })
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestAudioPermission() =
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQ_RECORD_AUDIO
        )

    @OptIn(UnstableApi::class)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            tryAttachVisualizer(exoPlayer.audioSessionId)
        }
    }

    @OptIn(UnstableApi::class)
    private fun tryAttachVisualizer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            waveform.attachToSession(audioSessionId)
        } catch (e: RuntimeException) {
            showToast("波形初始化失败: ${e.message}")
        }
    }

    /* ---------------- 网络获取歌单 ---------------- */
    private fun getMusicList() {
        thread {
            val json = HttpManager.getMusicListFromCloud(cloudMusicListUrl)
            if (json.isNullOrEmpty()) {
                showToast("获取歌曲列表失败")
                return@thread
            }
            val type = object : TypeToken<List<MusicBean>>() {}.type
            musicList = Gson().fromJson(json, type)
            // 补全显示名：优先服务器返回的 musicName，否则用父目录名或文件名去扩展名
            musicList.forEach { bean ->
                if (bean.musicName.isNullOrBlank()) {
                    val path = bean.downloadUrl ?: ""
                    if (path.isNotBlank()) {
                        val f = File(path)
                        bean.musicName = f.parentFile?.name ?: f.nameWithoutExtension
                    }
                }
            }
            mainHandler.post {
                adapter.submitList(musicList.toList()) // 提交新列表
            }
        }
    }

    /* ---------------- 歌词处理 ---------------- */

    private fun parseLyricSegments(raw: String): List<String> {
        return raw.split(";")
            .map { it.replace(Regex("\\[[^]]+\\]"), "").trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * 带 token 的加载歌词方法，只有 token 匹配当前的请求才会更新 UI
     */
    private fun loadLyric(url: String?, token: Int) {
        if (url.isNullOrEmpty()) {
            lyricLines = emptyList()
            currentLrcIndex = -1
            segmentDurationMs = 0L
            mainHandler.post { tvLyric.text = "暂无歌词" }
            return
        }
        thread {
            val json = HttpManager.getText(url)
            if (json.isNullOrEmpty()) {
                lyricLines = emptyList()
                currentLrcIndex = -1
                segmentDurationMs = 0L
                mainHandler.post {
                    if (token == lyricLoadToken) {
                        tvLyric.text = "暂无歌词"
                    }
                }
                return@thread
            }
            val lyricStr = try {
                JSONObject(json).optString("lyric")
            } catch (e: Exception) {
                ""
            }
            val parsed = if (lyricStr.isNotEmpty()) parseLyricSegments(lyricStr) else emptyList()
            mainHandler.post {
                if (token != lyricLoadToken) return@post
                lyricLines = parsed
                currentLrcIndex = -1
                calcSegmentDuration()
                tvLyric.text = if (parsed.isEmpty()) "暂无歌词" else parsed.first()
            }
        }
    }

    private fun calcSegmentDuration() {
        val dur = exoPlayer.duration
        segmentDurationMs = if (dur > 0 && lyricLines.isNotEmpty()) dur / lyricLines.size else 0L
    }

    private fun updateLyric(positionMs: Long) {
        if (lyricLines.isEmpty() || segmentDurationMs == 0L) return
        val idx = (positionMs / segmentDurationMs).toInt().coerceIn(0, lyricLines.lastIndex)
        if (idx != currentLrcIndex) {
            currentLrcIndex = idx
            tvLyric.text = lyricLines[idx]
        }
    }

    /* ---------------- 播放/下载 ---------------- */
    private fun playTrackAt(position: Int) {
        currentIndex = position
        val music = musicList[position]

        lyricLoadToken++

        tvTitle.text = music.musicName ?: "未知歌曲"
        tvArtist.text = music.artist ?: "未知艺术家"
        tvLyric.text = "加载歌词中..."
        lyricLines = emptyList()
        currentLrcIndex = -1
        segmentDurationMs = 0L

        loadLyric(music.lyricUrl, lyricLoadToken)

        seekBar.progress = 0
        seekBar.max = 0

        if (FileManager.isMusicCached(this, music.musicName)) {
            val file = FileManager.getCachedMusicFile(this, music.musicName)
            playMusic(file.absolutePath)
            showToast("缓存播放: ${music.musicName}")
        } else {
            showToast("正在下载: ${music.musicName}")
            thread {
                val ok = HttpManager.downloadMusic(this, music.downloadUrl, music.musicName)
                mainHandler.post {
                    if (ok) {
                        val file = FileManager.getCachedMusicFile(this, music.musicName)
                        playMusic(file.absolutePath)
                        showToast("下载完成并开始播放")
                    } else {
                        showToast("下载失败")
                    }
                }
            }
        }
    }

    private fun playMusic(path: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(path))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    private fun playNext() {
        if (musicList.isEmpty()) return
        val nextIndex = when (playMode) {
            PlayMode.ORDER -> (currentIndex + 1) % musicList.size
            PlayMode.SHUFFLE -> {
                val candidates = musicList.indices.filter { it != currentIndex }
                if (candidates.isNotEmpty()) candidates.random() else currentIndex
            }
        }
        playTrackAt(nextIndex)
    }

    private fun showToast(msg: String) =
        mainHandler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(progressUpdater)
        mainHandler.removeCallbacks(musicListRefreshRunnable)
        exoPlayer.release()
    }
}
