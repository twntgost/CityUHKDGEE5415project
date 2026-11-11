package com.example.hrrepayment.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log

/**
 * 基于 [MediaPlayer] 的实现。
 *
 * 可替换的 Android 资源播放器实现。当前正式构建不注入它，而是使用
 * [NoOpAudioPlayer]；新音效资源补回后可直接重新注入本实现。
 *
 * 行为约束（项目书 §15.6）：
 *  - 新音效打断旧音；
 *  - 遵循设备媒体音量（USAGE_MEDIA / CONTENT_TYPE_SONIFICATION）并申请
 *    短暂音频焦点（TRANSCRIPT_MAY_DUCK 类：不阻挡其他应用）；
 *  - 播放失败只写日志，不抛给业务层；
 *  - 后台 ON_STOP 由宿主调用 [stop]，回到前台不自动续播。
 */
/** 全局音效音量（0..1）。作者 2026-11-11 要求整体调小，统一在此处收口。 */
private const val SFX_VOLUME = 0.55f

class AssetAudioPlayer(
    private val context: Context,
) : AudioPlayer {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var current: MediaPlayer? = null

    /** API26+ 的焦点请求；API24-25 为 null，走 legacy 监听器路径。 */
    private var focusRequest: AudioFocusRequest? = null

    /** API24-25 legacy 焦点监听器：短音效，焦点丢失/恢复无需动作（不影响业务）。 */
    private val legacyFocusListener = AudioManager.OnAudioFocusChangeListener { }

    override fun play(assetPath: String) {
        stop()
        var player: MediaPlayer? = null
        try {
            player = MediaPlayer()
            val afd = context.assets.openFd(assetPath)
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } finally {
                afd.close()
            }
            player.setOnCompletionListener { completed ->
                completed.release()
                if (current === completed) {
                    current = null
                    abandonFocus()
                }
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.w(TAG, "decode/play error $what/$extra for $assetPath")
                mp.release()
                if (current === mp) current = null
                abandonFocus()
                true
            }
            requestFocus()
            current = player
            player.prepare()
            // 全局音效音量：0.55（作者要求“所有音效都小一点点”）。
            // 只改这一处即可整体调低，不必重新生成素材。
            player.setVolume(SFX_VOLUME, SFX_VOLUME)
            player.start()
        } catch (t: Throwable) {
            Log.w(TAG, "cannot play $assetPath: ${t.message}")
            val failed = player
            if (failed != null && current === failed) current = null
            try { failed?.release() } catch (_: Throwable) { /* already released */ }
            abandonFocus()
        }
    }

    override fun stop() {
        current?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: IllegalStateException) {
                // 已结束/未初始化，无需处理
            } finally {
                player.release()
            }
        }
        current = null
        abandonFocus()
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                focusRequest = AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                )
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .build()
            }
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            // API24-25：legacy API，STREAM_MUSIC + 同一焦点类型
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                legacyFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(legacyFocusListener)
        }
    }

    private companion object {
        const val TAG = "AssetAudioPlayer"
    }
}
