package com.uta.terminal.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.uta.terminal.BuildConfig

/**
 * 生体認証（指紋等）でアプリ全体をロックするゲート。
 *
 * - [enabled]（設定）が true かつ端末が生体認証可能なときのみロックする。
 * - **デバッグビルドではロックしない**（開発中の検証のため）。
 * - 起動時・バックグラウンド復帰時に認証を要求する（画面回転では再ロックしない）。
 * - **ロック画面は [content] の上に重ねて表示**する（content を再マウントしないため、解除後は元の画面へ戻る）。
 */
@Composable
fun BiometricGate(enabled: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val canAuth = remember {
        activity != null &&
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    val gate = enabled && canAuth && !BuildConfig.DEBUG

    // gate 無効なら常に解除済み。
    var authed by rememberSaveable(gate) { mutableStateOf(!gate) }
    var prompting by remember { mutableStateOf(false) }

    fun prompt() {
        if (authed || activity == null || prompting) return
        prompting = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    authed = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }

                override fun onAuthenticationFailed() { /* 継続 */ }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ロックを解除")
            .setSubtitle("指紋認証でアプリのロックを解除します")
            .setNegativeButtonText("キャンセル")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, gate) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (gate && !authed) prompt()
                Lifecycle.Event.ON_STOP ->
                    if (gate && activity?.isChangingConfigurations == false) authed = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ロックに入ったら下層（端末入力欄など）の残留フォーカスを明示的に外す。
    // LockScreen 側の requestFocus が万一失敗しても、物理/BT キーが端末へ届かないようにする多重防御。
    val focusManager = LocalFocusManager.current
    LaunchedEffect(authed) {
        if (!authed) focusManager.clearFocus(force = true)
    }

    // content は常に compose する（再マウントで端末やナビが失われないように）。
    content()
    // ロック中は不透明なロック画面を上に重ねる。
    if (!authed) {
        LockScreen(onUnlock = { prompt() })
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    // 下層の content（端末を含む）は compose されたままなので、ロック中はここで
    // フォーカス・キー入力・タッチを吸収し、ソフトキーボードも閉じる。
    // これをしないと、物理/Bluetooth キーボードや IME 再表示時にロック中でも
    // 下の入力欄へ文字が届き、リモートへ送信され得る（セキュリティ）。
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        keyboard?.hide()
        runCatching { focusRequester.requestFocus() }
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            // 全てのキーイベントを奪って消費する（下層の入力欄へ渡さない）。
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { true }
            // タッチも消費し、下層のタップ（キーボード表示）を発火させない。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    "ロックされています",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Button(onClick = onUnlock, modifier = Modifier.padding(top = 20.dp)) {
                    Text("ロックを解除")
                }
            }
        }
    }
}
