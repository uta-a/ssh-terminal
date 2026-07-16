package com.uta.terminal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.uta.terminal.data.AuthKind
import com.uta.terminal.data.HostProfile

/**
 * アドレス帳（保存ホスト一覧）。ドロワーの「新規セッション」からの遷移先。
 * 行タップで保存済み情報を復号して接続、ゴミ箱で削除、＋ で新規接続フォームへ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookScreen(
    profiles: List<HostProfile>,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onConnect: (HostProfile) -> Unit,
    onDelete: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("保存ホスト") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Filled.Add, contentDescription = "接続先を追加")
            }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "保存ホストはまだありません。＋ から追加してください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(profiles, key = { it.id }) { p ->
                    val authText = if (p.authKind == AuthKind.KEY) "鍵" else "パスワード"
                    ListItem(
                        headlineContent = { Text(p.label) },
                        supportingContent = {
                            Text("${p.username}@${p.host}:${p.port}  ·  $authText")
                        },
                        trailingContent = {
                            IconButton(onClick = { onDelete(p.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "削除")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConnect(p) },
                    )
                }
            }
        }
    }
}
