package com.uta.terminal.core.session

import com.uta.terminal.core.model.SessionId
import com.uta.terminal.core.model.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** セッション一覧が表示する 1 行分の要約。[profileId] は由来した保存ホスト（無ければ null）。 */
data class SessionInfo(
    val id: SessionId,
    val label: String,
    val state: SessionState,
    val profileId: String? = null,
)

/**
 * 開いているセッションのレジストリ（Android 非依存）。
 *
 * MVP は 1 セッションのみだが、UI・サービスが複数前提で組めるよう
 * 最初から複数セッションの集合として状態を Flow で公開する。
 * 実際の SSH 接続の張り込みはセッション実装フェーズで各 [SessionId] に紐付ける。
 */
class SessionManager {
    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow<SessionId?>(null)
    val activeId: StateFlow<SessionId?> = _activeId.asStateFlow()

    fun upsert(info: SessionInfo) {
        _sessions.update { list ->
            val idx = list.indexOfFirst { it.id == info.id }
            if (idx >= 0) list.toMutableList().apply { this[idx] = info }
            else list + info
        }
        if (_activeId.value == null) _activeId.value = info.id
    }

    fun setActive(id: SessionId) {
        if (_sessions.value.any { it.id == id }) _activeId.value = id
    }

    fun remove(id: SessionId) {
        _sessions.update { list -> list.filterNot { it.id == id } }
        if (_activeId.value == id) _activeId.value = _sessions.value.firstOrNull()?.id
    }
}
