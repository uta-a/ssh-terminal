package com.uta.terminal.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uta.terminal.core.model.SessionId
import com.uta.terminal.core.model.SessionState
import com.uta.terminal.core.session.SessionInfo

/**
 * セッションタブ。開いている SSH セッションをカードで一覧する（ホスト一覧と同トーン）。
 * - カード tap：そのセッションのシェル（端末画面）を開く。
 * - ⋮ メニュー：リネーム / 切断（削除）。切断は確認ダイアログを挟む。
 * - 空なら中央に「セッションなし」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    sessions: List<SessionInfo>,
    onOpen: (SessionId) -> Unit,
    onRename: (SessionId, String) -> Unit,
    onDisconnect: (SessionId) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var disconnectTarget by remember { mutableStateOf<SessionInfo?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("セッション") }) },
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "セッションなし",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sessions, key = { it.id.value }) { s ->
                    SessionRow(
                        session = s,
                        onOpen = { onOpen(s.id) },
                        onRename = { renameTarget = s },
                        onDisconnect = { disconnectTarget = s },
                    )
                }
            }
        }
    }

    val toRename = renameTarget
    if (toRename != null) {
        var newName by remember(toRename) { mutableStateOf(toRename.label) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("セッション名を変更") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("名前") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(toRename.id, newName)
                    renameTarget = null
                }) { Text("変更") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("キャンセル") }
            },
        )
    }

    val toDisconnect = disconnectTarget
    if (toDisconnect != null) {
        AlertDialog(
            onDismissRequest = { disconnectTarget = null },
            title = { Text("セッションを切断") },
            text = { Text("「${toDisconnect.label}」を切断して一覧から削除します。よろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    onDisconnect(toDisconnect.id)
                    disconnectTarget = null
                }) { Text("切断") }
            },
            dismissButton = {
                TextButton(onClick = { disconnectTarget = null }) { Text("キャンセル") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(
    session: SessionInfo,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 生存インジケータ：接続中=緑、確立中=tertiary、失敗=error、切断=灰。
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(sessionDotColor(session.state), CircleShape),
            )
            Spacer(Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.label,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    stateText(session.state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "メニュー",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("リネーム") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("切断") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDisconnect()
                        },
                    )
                }
            }
        }
    }
}

/** セッション状態の副次テキスト。 */
private fun stateText(state: SessionState): String = when (state) {
    is SessionState.Connected -> "接続済み"
    is SessionState.Connecting -> "接続中…"
    is SessionState.Reconnecting -> "再接続中…"
    is SessionState.Failed -> "接続失敗"
    is SessionState.Disconnected -> "切断"
}

/** セッション生存インジケータの色。 */
@Composable
private fun sessionDotColor(state: SessionState): Color = when (state) {
    is SessionState.Connected -> Color(0xFF4CAF50)
    is SessionState.Connecting, is SessionState.Reconnecting -> MaterialTheme.colorScheme.tertiary
    is SessionState.Failed -> MaterialTheme.colorScheme.error
    is SessionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
}
