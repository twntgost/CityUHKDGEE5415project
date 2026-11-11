package com.example.hrrepayment

import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.di.AppContainer
import com.example.hrrepayment.presentation.GameViewModel
import com.example.hrrepayment.ui.compositionlocals.LocalAvatarCache
import com.example.hrrepayment.ui.compositionlocals.LocalLang
import com.example.hrrepayment.ui.compositionlocals.LocalStrings
import com.example.hrrepayment.ui.home.HomeScreen
import com.example.hrrepayment.ui.theme.HRPayDebtTheme

/**
 * Single-activity shell. Everything else is Compose panels routed by the saved
 * [com.example.hrrepayment.core.model.Phase] — screen state is restored from the
 * save file after process death, never from memory.
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy {
        (application as HRPayDebtApp).container
    }

    private val viewModel: GameViewModel by viewModels {
        GameViewModel.factory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the system chrome in the same khaki palette as the app.
        // WindowInsetsControllerCompat is safe on minSdk 24: it uses the legacy flags
        // on API 24-25 and the native controller on newer APIs.
        //
        // 关于 statusBarColor / navigationBarColor 的弃用：这两个 setter 从 API 35 起
        // 已废弃并在 targetSdk 35+ 上被系统忽略（强制 edge-to-edge）。保留是为了让
        // Android 7–14 的旧机器状态栏仍与应用同色——所以这里抑制告警而不是删代码。
        val paper = AndroidColor.rgb(0xF3, 0xEC, 0xDD)
        @Suppress("DEPRECATION")
        window.statusBarColor = paper
        @Suppress("DEPRECATION")
        window.navigationBarColor = paper
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContent {
            HRPayDebtTheme {

                // 后台 ON_STOP：停播，回到前台不自动续播（项目书 §15.4）
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) container.audioPlayer.stop()
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                val lang by viewModel.lang.collectAsStateWithLifecycle()
                val strings = remember(lang) { Strings(lang) }

                CompositionLocalProvider(
                    LocalStrings provides strings,
                    LocalLang provides lang,
                    LocalAvatarCache provides container.avatarCache,
                ) {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }
}
