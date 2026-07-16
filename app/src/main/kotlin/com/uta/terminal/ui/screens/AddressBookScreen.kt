package com.uta.terminal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uta.terminal.data.AuthKind
import com.uta.terminal.data.HostProfile

/**
 * ホスト一覧（アプリのホーム）。保存済み接続先をカードで一覧表示する。
 * カードタップで保存済み情報を復号して接続、ゴミ箱で削除、＋/中央ボタンで新規接続フォームへ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookScreen(
    profiles: List<HostProfile>,
    onAddNew: () -> Unit,
    onConnect: (HostProfile) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onReturnToTerminal: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ホスト一覧") },
                // アクティブなセッションがあるときだけ、端末画面へ戻る矢印を出す。
                navigationIcon = {
                    if (onReturnToTerminal != null) {
                        IconButton(onClick = onReturnToTerminal) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "端末に戻る",
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "設定")
                    }
                },
            )
        },
        floatingActionButton = {
            if (profiles.isNotEmpty()) {
                FloatingActionButton(onClick = onAddNew) {
                    Icon(Icons.Filled.Add, contentDescription = "接続先を追加")
                }
            }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            EmptyHosts(
                modifier = Modifier.fillMaxSize().padding(padding),
                onAddNew = onAddNew,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(profiles, key = { it.id }) { p ->
                    HostCard(
                        profile = p,
                        onConnect = { onConnect(p) },
                        onDelete = { onDelete(p.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HostCard(
    profile: HostProfile,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    val authText = if (profile.authKind == AuthKind.KEY) "鍵" else "パスワード"
    Card(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 10.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.label,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "${profile.username}@${profile.host}:${profile.port}  ·  $authText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 保存が 0 件のときの中央導線。 */
@Composable
private fun EmptyHosts(modifier: Modifier = Modifier, onAddNew: () -> Unit) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "保存された接続先がありません",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "接続先を追加すると、ここから素早く接続できます",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAddNew) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("接続先を追加")
            }
        }
    }
}
