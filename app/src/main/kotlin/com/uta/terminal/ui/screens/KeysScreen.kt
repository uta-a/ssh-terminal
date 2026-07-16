package com.uta.terminal.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.uta.terminal.data.SshKeyItem

/**
 * 鍵管理画面（設定 > 鍵管理）。鍵ストアの一覧・追加（貼り付け）・公開鍵コピー・名前変更・削除。
 * 使用中（接続先が参照している）の鍵は削除できない。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(
    keys: List<SshKeyItem>,
    onBack: () -> Unit,
    onAdd: (name: String, pem: String, passphrase: String?) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SshKeyItem?>(null) }
    var deleteTarget by remember { mutableStateOf<SshKeyItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("鍵管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        floatingActionButton = {
            if (keys.isNotEmpty()) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "鍵を追加")
                }
            }
        },
    ) { padding ->
        if (keys.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("保存された鍵がありません", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "秘密鍵を登録すると、複数の接続先で使い回せます",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("鍵を追加")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(keys, key = { it.id }) { key ->
                    KeyRow(
                        key = key,
                        onRename = { renameTarget = key },
                        onDelete = { deleteTarget = key },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddKeyDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, pem, pass ->
                showAdd = false
                onAdd(name, pem, pass)
            },
        )
    }

    val toRename = renameTarget
    if (toRename != null) {
        RenameKeyDialog(
            current = toRename.name,
            onDismiss = { renameTarget = null },
            onRename = { name ->
                onRename(toRename.id, name)
                renameTarget = null
            },
        )
    }

    val toDelete = deleteTarget
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("鍵を削除") },
            text = { Text("「${toDelete.name}」を削除します。秘密鍵は復元できません。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(toDelete.id)
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
private fun KeyRow(
    key: SshKeyItem,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    val inUse = key.usageCount > 0

    val typeText = key.keyType ?: "種別不明"
    val fpText = key.fingerprint?.removePrefix("SHA256:")?.take(16)?.let { "SHA256:$it…" }
    val usageText = if (inUse) "使用中: ${key.usageCount} 件" else "未使用"

    ListItem(
        leadingContent = {
            Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(key.name) },
        supportingContent = {
            Text(listOfNotNull(typeText, fpText, usageText).joinToString("  ·  "))
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("公開鍵をコピー") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        enabled = key.publicKey != null,
                        onClick = {
                            menuOpen = false
                            val pub = key.publicKey
                            if (pub != null) {
                                clipboard.setText(AnnotatedString(pub))
                                Toast.makeText(context, "公開鍵をコピーしました", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("名前を変更") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (inUse) "削除（使用中のため不可）" else "削除")
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = if (inUse) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        },
                        enabled = !inUse,
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

@Composable
private fun AddKeyDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, pem: String, passphrase: String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var pem by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("鍵を追加") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名前（例: メインの鍵）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pem,
                    onValueChange = { pem = it },
                    label = { Text("秘密鍵（PEM/OpenSSH 形式を貼り付け）") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("パスフレーズ（任意）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && pem.isNotBlank(),
                onClick = { onAdd(name.trim(), pem, passphrase.ifEmpty { null }) },
            ) { Text("追加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

@Composable
private fun RenameKeyDialog(
    current: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("名前を変更") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名前") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onRename(name.trim()) },
            ) { Text("変更") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
