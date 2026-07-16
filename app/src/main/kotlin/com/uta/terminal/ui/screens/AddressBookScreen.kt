package com.uta.terminal.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.uta.terminal.data.AuthKind
import com.uta.terminal.data.HostProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ホスト一覧（アプリのホーム）。保存済み接続先をカードで一覧表示する。
 * - カードタップ：保存済み情報を復号して接続。
 * - カード右端の ⋮ メニュー：編集 / 複製 / 削除（全操作の見える入口）。
 * - カード長押し＋上下ドラッグ：並び替え（[onReorder] で永続化）。
 * - カード横スワイプ：赤い削除ボタンを表出（削除のショートカット）。
 * - 削除はどの経路でも確認ダイアログを挟む（秘密ごと消え、復元できないため）。
 * - ＋/中央ボタン：新規接続フォームへ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookScreen(
    profiles: List<HostProfile>,
    onAddNew: () -> Unit,
    onConnect: (HostProfile) -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDuplicate: (HostProfile) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onReturnToTerminal: (() -> Unit)? = null,
) {
    // 削除確認ダイアログの対象（null＝非表示）。スワイプX・⋮メニューの両経路からここに集約する。
    var confirmDelete by remember { mutableStateOf<HostProfile?>(null) }
    // 並び替え中のライブ順を保持するローカル State。**識別子を安定させる**ため remember はキー無しにし、
    // profiles の再発行は LaunchedEffect で中身だけ同期する（DragDropState のクロージャが
    // 初期 emptyList の State を掴んだままにならないようにする＝クラッシュ/未反映の防止）。
    val itemsState = remember { mutableStateOf(profiles) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 端の自動スクロール用ジョブ（ドラッグ終了/端から離れたら止める）。
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    val dragState = remember(listState) {
        DragDropState(listState) { from, to ->
            val current = itemsState.value
            // index と items のずれで範囲外にならないようガードする。
            if (from in current.indices && to in current.indices && from != to) {
                itemsState.value = current.toMutableList().apply { add(to, removeAt(from)) }
            }
        }
    }
    // profiles が変わったら（保存/削除/並び替え永続化後の再発行）同期。ドラッグ中は上書きしない。
    LaunchedEffect(profiles) {
        if (dragState.draggingItemIndex == null) itemsState.value = profiles
    }
    val items = itemsState.value

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
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // 長押し＋ドラッグで並び替え。ドロップで新しい順を永続化する。
                    .pointerInput(dragState) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset -> dragState.onDragStart(offset.y) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragState.onDrag(dragAmount.y)
                                // 指が端に近ければ自動スクロールを回す（1本のジョブで継続）。
                                if (autoScrollJob?.isActive != true && dragState.autoScrollDelta() != 0f) {
                                    autoScrollJob = scope.launch {
                                        while (true) {
                                            val d = dragState.autoScrollDelta()
                                            if (d == 0f) break
                                            listState.scrollBy(d * 0.5f)
                                            dragState.onDrag(0f) // スクロール後に入れ替え判定を更新
                                            delay(16)
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                autoScrollJob?.cancel()
                                dragState.onDragEnd()
                                onReorder(itemsState.value.map { it.id })
                            },
                            onDragCancel = {
                                autoScrollJob?.cancel()
                                dragState.onDragEnd()
                            },
                        )
                    },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, p ->
                    val isDragging = index == dragState.draggingItemIndex
                    HostRow(
                        profile = p,
                        dragging = isDragging,
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragState.draggingItemOffset else 0f
                            },
                        onConnect = { onConnect(p) },
                        onEdit = { onEdit(p) },
                        onDuplicate = { onDuplicate(p) },
                        onDeleteRequest = { confirmDelete = p },
                    )
                }
            }
        }
    }

    // 削除確認。秘密（パスワード/鍵）ごと消えて復元できないため、必ず確認を挟む。
    val toDelete = confirmDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("接続先を削除") },
            text = { Text("「${toDelete.label}」を削除します。保存されたパスワード/鍵も削除され、元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(toDelete.id)
                    confirmDelete = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("キャンセル") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostRow(
    profile: HostProfile,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val authText = if (profile.authKind == AuthKind.KEY) "鍵" else "パスワード"
    val shape = RoundedCornerShape(12.dp)
    val revealPx = with(LocalDensity.current) { 76.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    // 並び替えドラッグ開始時はスワイプ表出を閉じておく。
    LaunchedEffect(dragging) { if (dragging) offsetX.animateTo(0f) }

    Box(modifier = modifier.fillMaxWidth()) {
        // 背景：右端に赤い削除ボタン。前面カードを左へスワイプすると露出する。
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.CenterEnd,
        ) {
            IconButton(
                onClick = {
                    onDeleteRequest()
                    scope.launch { offsetX.animateTo(0f) }
                },
                modifier = Modifier.padding(end = 14.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        }
        // 前面：接続カード。左スワイプで赤ボタンを表出、タップで接続（表出中はまず閉じる）。
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-revealPx, 0f))
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                offsetX.animateTo(if (offsetX.value < -revealPx / 2f) -revealPx else 0f)
                            }
                        },
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (offsetX.value != 0f) scope.launch { offsetX.animateTo(0f) } else onConnect()
                    },
                ),
            shape = shape,
            colors = CardDefaults.elevatedCardColors(),
            elevation = CardDefaults.elevatedCardElevation(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
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
                // 全操作の「見える入口」。ジェスチャー（スワイプ削除・長押し並び替え）は
                // ショートカット扱いで、ここから編集/複製/削除に必ず到達できる。
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
                            text = { Text("編集") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("複製") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
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
                                onDeleteRequest()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * LazyColumn の長押しドラッグ並び替え状態。可視アイテムのレイアウト情報から、
 * ドラッグ中アイテムの視覚オフセットを算出し、指の位置に追従させる。
 */
private class DragDropState(
    val listState: LazyListState,
    private val onMove: (Int, Int) -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private val delta = mutableFloatStateOf(0f)
    private val initialOffset = mutableIntStateOf(0)
    // 指のビューポート座標（px）。入れ替え判定と端の自動スクロールに使う。
    private var pointerY = 0f

    private val draggingLayoutInfo: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    /** ドラッグ中アイテムの、現在スロットからの視覚オフセット（px）。translationY に使う。 */
    val draggingItemOffset: Float
        get() = draggingLayoutInfo?.let {
            initialOffset.intValue + delta.floatValue - it.offset
        } ?: 0f

    fun onDragStart(offsetY: Float) {
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { offsetY.toInt() in it.offset..(it.offset + it.size) }
            ?.also {
                draggingItemIndex = it.index
                initialOffset.intValue = it.offset
                delta.floatValue = 0f
                pointerY = offsetY
            }
    }

    fun onDrag(deltaY: Float) {
        delta.floatValue += deltaY
        pointerY += deltaY
        val dragging = draggingLayoutInfo ?: return
        // 指の位置（ビューポート座標）に重なる別アイテムと入れ替える。自動スクロール中に
        // リストが動いても、指位置基準なら新しく指の下に来たアイテムを正しく捉えられる。
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != dragging.index && pointerY.toInt() in it.offset..(it.offset + it.size)
        }
        if (target != null) {
            val from = draggingItemIndex ?: return
            onMove(from, target.index)
            draggingItemIndex = target.index
        }
    }

    /**
     * 指がリスト端に近いときの 1 フレームあたりのスクロール量（px）。0 なら不要。
     * ドラッグ対象が可視範囲外にあるときも、端へ持っていけばリストが送られ並び替えできる。
     */
    fun autoScrollDelta(): Float {
        if (draggingItemIndex == null) return 0f
        val vpStart = listState.layoutInfo.viewportStartOffset.toFloat()
        val vpEnd = listState.layoutInfo.viewportEndOffset.toFloat()
        val edge = 120f
        return when {
            pointerY > vpEnd - edge -> (pointerY - (vpEnd - edge)).coerceIn(0f, edge)
            pointerY < vpStart + edge -> (pointerY - (vpStart + edge)).coerceIn(-edge, 0f)
            else -> 0f
        }
    }

    fun onDragEnd() {
        draggingItemIndex = null
        delta.floatValue = 0f
        initialOffset.intValue = 0
        pointerY = 0f
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
