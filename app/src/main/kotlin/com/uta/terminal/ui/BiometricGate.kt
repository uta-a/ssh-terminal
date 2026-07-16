package com.uta.terminal.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 生体認証（指紋等）でアプリ全体をロックするゲート。
 *
 * - 起動時、およびバックグラウンドから復帰時に認証を要求する（画面回転では再ロックしない）。
 * - 生体認証が使えない端末（未登録/非対応）ではロックせず素通しする（締め出し回避）。
 * - 認証成功まで [content]（アプリ本体）は描画しない。
 */
@Composable
fun BiometricGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val canAuth = remember {
        activity != null &&
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    // 生体認証が使えないなら最初から解除済み扱い。
    var authed by rememberSaveable { mutableStateOf(!canAuth) }
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
                    prompting = false // ロック画面のボタンから再試行できる
                }

                override fun onAuthenticationFailed() { /* 一致せず。プロンプトは継続 */ }
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

    // 起動時（observer 追加時に現在状態まで昇格して ON_START が届く）と復帰時に認証。
    // 画面回転（isChangingConfigurations）では再ロックしない。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, canAuth) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (canAuth && !authed) prompt()
                Lifecycle.Event.ON_STOP ->
                    if (canAuth && activity?.isChangingConfigurations == false) authed = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (authed) {
        content()
    } else {
        LockScreen(onUnlock = { prompt() })
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
