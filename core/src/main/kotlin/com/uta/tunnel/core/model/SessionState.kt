package com.uta.tunnel.core.model

/** 1 セッションを一意に識別する ID。 */
@JvmInline
value class SessionId(val value: String)

/**
 * セッションの生存状態。フォアグラウンドサービスと UI（ドロワーのセッション一覧）が購読する。
 */
sealed interface SessionState {
    data object Connecting : SessionState
    data object Connected : SessionState
    data object Reconnecting : SessionState
    data object Disconnected : SessionState
    data class Failed(val reason: String) : SessionState
}
