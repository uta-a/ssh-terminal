package com.uta.tunnel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.uta.tunnel.data.AuthKind
import com.uta.tunnel.data.HostProfile
import com.uta.tunnel.data.SshKeyItem

/**
 * フォームが返す認証指定。秘密鍵は鍵ストア参照（[ExistingKey]）か新規貼り付け（[NewKey]）で、
 * PEM の実体解決・鍵ストア登録は呼び出し側（MainActivity）が行う。
 */
sealed interface AuthSpec {
    data class Password(val password: String) : AuthSpec
    data class ExistingKey(val keyId: String) : AuthSpec
    data class NewKey(val name: String, val pem: String, val passphrase: String?) : AuthSpec
}

/** 鍵ドロップダウンの「新しい鍵を貼り付け…」を表す番兵値。 */
private const val KEY_NEW = "__NEW_KEY__"

/** rememberSaveable 用の List<String> セーバ（改行区切りで 1 文字列へ）。 */
private val tagListSaver: Saver<MutableState<List<String>>, String> = Saver(
    save = { it.value.joinToString("\n") },
    restore = { s -> mutableStateOf(if (s.isEmpty()) emptyList() else s.split("\n")) },
)

/**
 * 接続フォーム。新規（[initial] == null）と編集（!= null）を兼ねる。
 * - 新規：入力した接続情報で即座に SSH セッションを張る（保存は任意）。
 * - 編集：非秘密情報をプリフィルする。パスワードは「変更する」を押したときだけ再入力、
 *   鍵は鍵ストアのドロップダウンで付け替え（未変更なら [onSaveEdit] の auth に null を渡す）。
 * - 秘密鍵タブは鍵ストア（[keys]）から選択するか、新しい鍵を貼り付けて登録する。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    keys: List<SshKeyItem>,
    allTags: List<String> = emptyList(),
    onBack: () -> Unit,
    onConnect: (label: String, host: String, port: Int, username: String, auth: AuthSpec, save: Boolean, tags: List<String>) -> Unit,
    initial: HostProfile? = null,
    onSaveEdit: (label: String, host: String, port: Int, username: String, auth: AuthSpec?, tags: List<String>) -> Unit =
        { _, _, _, _, _, _ -> },
) {
    val editing = initial != null
    val initialTab = if (initial?.authKind == AuthKind.KEY) 1 else 0
    // 旧形式（鍵ストア未昇格・行内保存）の鍵プロファイルか。
    val legacyInlineKey = editing && initial?.authKind == AuthKind.KEY && initial.keyId == null

    // 画面回転でフォーム入力が消えないよう rememberSaveable で保持する。
    var label by rememberSaveable { mutableStateOf(initial?.label ?: "") }
    var host by rememberSaveable { mutableStateOf(initial?.host ?: "") }
    var port by rememberSaveable { mutableStateOf(initial?.port?.toString() ?: "22") }
    var username by rememberSaveable { mutableStateOf(initial?.username ?: "") }
    var authTab by rememberSaveable { mutableStateOf(initialTab) } // 0=パスワード, 1=秘密鍵
    var password by rememberSaveable { mutableStateOf("") }
    var save by rememberSaveable { mutableStateOf(true) }
    // 編集時に「パスワード/鍵を変更する」を押したか（パスワード・旧形式鍵のみ使用）。
    var changeSecret by rememberSaveable { mutableStateOf(false) }
    // 付与タグ。カンマ/改行区切りではなくチップで管理する。
    val tags = rememberSaveable(saver = tagListSaver) {
        mutableStateOf(initial?.tags ?: emptyList())
    }
    var tagInput by rememberSaveable { mutableStateOf("") }

    // 鍵選択。null = 未操作（初期値から解決）、KEY_NEW = 新しい鍵を貼り付け。
    var keyChoice by rememberSaveable { mutableStateOf<String?>(null) }
    var newKeyName by rememberSaveable { mutableStateOf("") }
    var newKeyPem by rememberSaveable { mutableStateOf("") }
    var newKeyPass by rememberSaveable { mutableStateOf("") }

    // 実際に選ばれている鍵：明示選択 > 編集元の鍵 > 先頭の保存鍵 > 新規貼り付け。
    val resolvedKeyChoice = keyChoice
        ?: initial?.keyId?.takeIf { id -> keys.any { it.id == id } }
        ?: keys.firstOrNull()?.id
        ?: KEY_NEW

    // パスワードの再入力が必要か：新規は常に必要。編集は「変更」を押したか、認証方式を変えたとき。
    val passwordRequired = !editing || changeSecret || initialTab != 0
    // 旧形式鍵をそのまま使い続けるか（編集で鍵タブのまま「変更」を押していない）。
    val keepLegacyKey = legacyInlineKey && authTab == 1 && !changeSecret

    val portNum = port.toIntOrNull()
    val authValid = if (authTab == 0) {
        !passwordRequired || password.isNotEmpty()
    } else {
        keepLegacyKey || (if (resolvedKeyChoice == KEY_NEW) newKeyPem.isNotBlank() else true)
    }
    val canSubmit = host.isNotBlank() && username.isNotBlank() &&
        portNum != null && portNum in 1..65535 && authValid

    fun buildSpec(displayLabel: String): AuthSpec? {
        // 戻り値 null は「認証情報を変更しない」（編集時のみ許される）。
        return if (authTab == 0) {
            if (editing && !passwordRequired) null else AuthSpec.Password(password)
        } else {
            when {
                keepLegacyKey -> null
                resolvedKeyChoice == KEY_NEW -> AuthSpec.NewKey(
                    name = newKeyName.trim().ifEmpty { "$displayLabel の鍵" },
                    pem = newKeyPem,
                    passphrase = newKeyPass.ifEmpty { null },
                )
                editing && resolvedKeyChoice == initial?.keyId -> null
                else -> AuthSpec.ExistingKey(resolvedKeyChoice)
            }
        }
    }

    fun submit() {
        val p = portNum ?: return
        val displayLabel = label.trim().ifEmpty { "${username.trim()}@${host.trim()}" }
        val spec = buildSpec(displayLabel)
        val tagList = tags.value
        if (editing) {
            onSaveEdit(displayLabel, host.trim(), p, username.trim(), spec, tagList)
        } else {
            onConnect(displayLabel, host.trim(), p, username.trim(), spec ?: return, save, tagList)
        }
    }

    fun addTag(raw: String) {
        val t = raw.trim()
        if (t.isNotEmpty() && t !in tags.value) tags.value = tags.value + t
        tagInput = ""
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

            if (authTab == 0) {
                if (!passwordRequired) {
                    // 編集中・パスワード未変更：既存を維持する旨と、変更への導線だけ出す。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "保存済みのパスワードを使用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { changeSecret = true }) { Text("パスワードを変更") }
                    }
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("パスワード") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (keepLegacyKey) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "保存済みの鍵を使用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { changeSecret = true }) { Text("鍵を変更") }
                }
            } else {
                KeyPicker(
                    keys = keys,
                    selected = resolvedKeyChoice,
                    onSelect = { keyChoice = it },
                )
                if (resolvedKeyChoice == KEY_NEW) {
                    OutlinedTextField(
                        value = newKeyName,
                        onValueChange = { newKeyName = it },
                        label = { Text("鍵の名前（任意・未入力なら自動）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newKeyPem,
                        onValueChange = { newKeyPem = it },
                        label = { Text("秘密鍵（PEM/OpenSSH 形式を貼り付け）") },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    )
                    OutlinedTextField(
                        value = newKeyPass,
                        onValueChange = { newKeyPass = it },
                        label = { Text("パスフレーズ（任意）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "貼り付けた鍵は鍵ストアに登録され、他の接続先でも使い回せます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // タグ（任意）。付与済みチップ＋入力欄。既存タグは候補として下に出す。
            Text("タグ（任意）", style = MaterialTheme.typography.labelLarge)
            if (tags.value.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.value.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { tags.value = tags.value - tag },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "削除",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                label = { Text("タグを追加してエンター") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTag(tagInput) }),
                trailingIcon = {
                    if (tagInput.isNotBlank()) {
                        IconButton(onClick = { addTag(tagInput) }) {
                            Icon(Icons.Filled.Add, contentDescription = "タグを追加")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            // 既存タグの候補（未付与のもの）。
            val suggestions = allTags.filter { it !in tags.value }
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { tag ->
                        SuggestionChip(
                            onClick = { addTag(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }

            if (!editing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

/** 鍵ストアの鍵を選ぶドロップダウン。「新しい鍵を貼り付け…」を末尾に持つ。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyPicker(
    keys: List<SshKeyItem>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = if (selected == KEY_NEW) {
        "新しい鍵を貼り付け…"
    } else {
        keys.firstOrNull { it.id == selected }?.name ?: "鍵を選択"
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("使用する鍵") },
            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            keys.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key.name) },
                    leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSelect(key.id)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("新しい鍵を貼り付け…") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onSelect(KEY_NEW)
                },
            )
        }
    }
}
