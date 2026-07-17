package com.uta.tunnel.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.uta.tunnel.core.model.HostKeyEntry

/**
 * 既知ホスト（TOFU）画面（設定 > 既知ホスト）。初回接続で信頼したホスト鍵の一覧・削除。
 * `~/.ssh/known_hosts` に相当する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownHostsScreen(
    hosts: List<HostKeyEntry>,
    onBack: () -> Unit,
    onDelete: (HostKeyEntry) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<HostKeyEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("既知ホスト") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        if (hosts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("既知のホストがありません", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "初回接続したホストの鍵がここに記録され、次回以降の接続で照合されます",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(hosts, key = { "${it.address.host}:${it.address.port}" }) { entry ->
                    KnownHostRow(entry = entry, onDelete = { deleteTarget = entry })
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("既知ホストを削除") },
            text = {
                Text(
                    "「${target.address}」の記録を削除します。次回の接続では鍵を照合できず、" +
                        "提示された鍵をそのまま信頼します（初回接続と同じ扱い）。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    deleteTarget = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun KnownHostRow(entry: HostKeyEntry, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    ListItem(
        leadingContent = {
            Icon(
                Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(entry.address.toString()) },
        supportingContent = {
            Text(
                "${entry.keyType} · ${entry.fingerprint}",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("フィンガープリントをコピー") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        onClick = {
                            clipboard.setText(AnnotatedString(entry.fingerprint))
                            Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("削除") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}
