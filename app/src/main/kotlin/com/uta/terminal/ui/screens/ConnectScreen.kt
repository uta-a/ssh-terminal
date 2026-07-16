package com.uta.terminal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.uta.terminal.core.ssh.SshAuth
import com.uta.terminal.core.ssh.SshConnectionRequest
import com.uta.terminal.data.AuthKind
import com.uta.terminal.data.HostProfile

/**
 * 接続フォーム。新規（[initial] == null）と編集（!= null）を兼ねる。
 * - 新規：入力した接続情報で即座に SSH セッションを張る（保存は任意）。
 * - 編集：非秘密情報をプリフィルし、秘密（パスワード/鍵）は「変更する」を押したときだけ再入力
 *   （押さなければ既存の秘密を維持したまま [onSaveEdit] の auth に null を渡す）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    onBack: () -> Unit,
    onConnect: (SshConnectionRequest, String, Boolean) -> Unit,
    initial: HostProfile? = null,
    onSaveEdit: (label: String, host: String, port: Int, username: String, auth: SshAuth?) -> Unit =
        { _, _, _, _, _ -> },
) {
    val editing = initial != null
    val initialTab = if (initial?.authKind == AuthKind.KEY) 1 else 0

    // 画面回転でフォーム入力が消えないよう rememberSaveable で保持する。
    var label by rememberSaveable { mutableStateOf(initial?.label ?: "") }
    var host by rememberSaveable { mutableStateOf(initial?.host ?: "") }
    var port by rememberSaveable { mutableStateOf(initial?.port?.toString() ?: "22") }
    var username by rememberSaveable { mutableStateOf(initial?.username ?: "") }
    var authTab by rememberSaveable { mutableStateOf(initialTab) } // 0=パスワード, 1=秘密鍵
    var password by rememberSaveable { mutableStateOf("") }
    var privateKey by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var save by rememberSaveable { mutableStateOf(true) }
    // 編集時に「パスワード/鍵を変更する」を押したか。押すまで秘密入力欄は出さない。
    var changeSecret by rememberSaveable { mutableStateOf(false) }

    // 秘密の入力が必要か：新規は常に必要。編集は「変更する」を押したか、認証方式を変えたとき。
    val secretRequired = !editing || changeSecret || authTab != initialTab

    val portNum = port.toIntOrNull()
    val canSubmit = host.isNotBlank() && username.isNotBlank() &&
        portNum != null && portNum in 1..65535 &&
        (!secretRequired || (if (authTab == 0) password.isNotEmpty() else privateKey.isNotBlank()))

    fun buildAuth(): SshAuth = if (authTab == 0) {
        SshAuth.Password(password)
    } else {
        SshAuth.PrivateKey(privateKey, passphrase.ifEmpty { null })
    }

    fun submit() {
        val p = portNum ?: return
        val displayLabel = label.trim().ifEmpty { "${username.trim()}@${host.trim()}" }
        if (editing) {
            onSaveEdit(
                displayLabel,
                host.trim(),
                p,
                username.trim(),
                if (secretRequired) buildAuth() else null,
            )
        } else {
            // cols/rows は仮値。実サイズは端末カード計測後に resize で送る。
            val req = SshConnectionRequest(
                host.trim(), p, username.trim(), buildAuth(), cols = 80, rows = 24,
            )
            onConnect(req, displayLabel, save)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "接続先を編集" else "新規接続") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("名前（任意・未入力なら user@host）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("ホスト") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("ポート") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("ユーザー名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = authTab == 0,
                    onClick = { authTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("パスワード") }
                SegmentedButton(
                    selected = authTab == 1,
                    onClick = { authTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("秘密鍵") }
            }

            if (!secretRequired) {
                // 編集中・秘密未変更：既存の秘密を維持する旨と、変更への導線だけ出す。
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        if (authTab == 0) "保存済みのパスワードを使用" else "保存済みの鍵を使用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { changeSecret = true }) {
                        Text(if (authTab == 0) "パスワードを変更" else "鍵を変更")
                    }
                }
            } else if (authTab == 0) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("パスワード") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = privateKey,
                    onValueChange = { privateKey = it },
                    label = { Text("秘密鍵（PEM/OpenSSH 形式を貼り付け）") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("パスフレーズ（任意）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!editing) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = save, onCheckedChange = { save = it })
                    Text("この接続先を保存する（パスワード/鍵は暗号化して保存）")
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { submit() },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        editing -> "保存"
                        save -> "保存して接続"
                        else -> "接続"
                    },
                )
            }
            if (!editing) {
                Text(
                    "ホスト鍵は初回接続時に信頼保存されます（TOFU）。現在は起動ごとにリセットされます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
