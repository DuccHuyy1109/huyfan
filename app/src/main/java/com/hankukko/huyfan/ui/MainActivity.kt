package com.hankukko.huyfan.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hankukko.huyfan.assistant.EngineEvent
import com.hankukko.huyfan.container
import com.hankukko.huyfan.service.StandbyService
import com.hankukko.huyfan.ui.screens.ChatScreen
import com.hankukko.huyfan.ui.screens.HistoryScreen
import com.hankukko.huyfan.ui.screens.PromptEditorDialog
import com.hankukko.huyfan.ui.screens.LessonEditorDialog
import com.hankukko.huyfan.ui.screens.LessonEditorRequest
import com.hankukko.huyfan.ui.screens.ReviewScreen
import com.hankukko.huyfan.ui.screens.RulesScreen
import com.hankukko.huyfan.ui.screens.StudyDialog
import com.hankukko.huyfan.ui.screens.StudySession
import com.hankukko.huyfan.ui.screens.SettingsScreen
import com.hankukko.huyfan.ui.screens.SheetsGuideDialog
import com.hankukko.huyfan.ui.screens.VocabFilter
import com.hankukko.huyfan.ui.screens.VocabScreen
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.HuyFanTheme
import com.hankukko.huyfan.ui.theme.LocalExtra
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector, val inBar: Boolean = true) {
    CHAT("Chat", Icons.Rounded.Forum),
    TRANSLATE("Phiên dịch", Icons.Rounded.Translate),
    VOCAB("Sổ từ", Icons.AutoMirrored.Rounded.MenuBook),
    REVIEW("Ôn tập", Icons.Rounded.Style),
    RULES("Nguyên tắc", Icons.Rounded.AutoStories),
    SETTINGS("Cài đặt", Icons.Rounded.Settings),
    // Mở từ nút đồng hồ ở màn Chat
    HISTORY("Lịch sử", Icons.Rounded.History, inBar = false),
}

data class NavRequest(
    val tab: Tab,
    val vocabToday: Boolean? = null,
    /** Mở luôn phiên ôn thẻ đến hạn (bấm thông báo nhắc học) */
    val startDue: Boolean = false,
    val nonce: Long = System.nanoTime(),
)

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_VOCAB_TODAY = "vocab_today"
        const val EXTRA_REQUEST_PERMS = "request_perms"
    }

    private val vm: MainViewModel by viewModels()
    private val navRequests = MutableStateFlow<NavRequest?>(null)

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun requestPermissions() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permLauncher.launch(perms.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        if (savedInstanceState == null) {
            // Vừa đổi ngôn ngữ học → cuộc trò chuyện mới, bật lại chế độ chờ nếu trước đó đang bật
            if (com.hankukko.huyfan.Lang.afterSwitch(this, intent)) vm.newChat()
            if (!hasMic()) requestPermissions()
            lifecycleScope.launch {
                val s = container.settings.get()
                if (s.standbyOnLaunch && hasMic() && !StandbyService.running.value) StandbyService.start(this@MainActivity)
            }
        }

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = com.hankukko.huyfan.ui.theme.isDarkTheme(settings.themeMode)
            LaunchedEffect(dark) { applySystemBars(dark) }
            HuyFanTheme(settings.themeMode) {
                MainScaffold(
                    vm = vm,
                    navRequest = navRequests.collectAsStateWithLifecycle().value,
                    onNavConsumed = { navRequests.value = null },
                    onRequestPermissions = { requestPermissions() },
                    onMic = { if (hasMic()) vm.micTap() else requestPermissions() },
                    onToggleStandby = {
                        when {
                            StandbyService.running.value -> StandbyService.stop(this)
                            hasMic() -> StandbyService.start(this)
                            else -> requestPermissions()
                        }
                    },
                )
            }
        }
    }

    /** Biểu tượng thanh trạng thái/điều hướng sáng hay tối theo giao diện của app (không theo hệ thống). */
    private fun applySystemBars(dark: Boolean) {
        val t = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
            navigationBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    override fun onStop() {
        super.onStop()
        // Ẩn app (về màn hình chính, tắt màn hình…) → dừng trò chuyện, giữ nguyên đoạn chat.
        // Trừ khi bảng trợ lý vừa bật lên đè (nó đang dùng chính cuộc trò chuyện này).
        if (!isChangingConfigurations && !com.hankukko.huyfan.ForegroundTracker.assistVisible) {
            container.engine.cancel()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_REQUEST_PERMS, false)) requestPermissions()
        val req = when (intent.action) {
            "com.hankukko.huyfan.OPEN_VOCAB" -> NavRequest(Tab.VOCAB, vocabToday = true)
            "com.hankukko.huyfan.OPEN_REVIEW" -> NavRequest(Tab.REVIEW)
            com.hankukko.huyfan.service.Notifications.ACTION_OPEN_DUE -> NavRequest(Tab.REVIEW, startDue = true)
            else -> intent.getStringExtra(EXTRA_TAB)?.let { t ->
                runCatching { Tab.valueOf(t) }.getOrNull()?.let { tab ->
                    NavRequest(tab, if (intent.hasExtra(EXTRA_VOCAB_TODAY)) intent.getBooleanExtra(EXTRA_VOCAB_TODAY, false) else null)
                }
            }
        }
        if (req != null) navRequests.value = req
    }
}

@Composable
private fun MainScaffold(
    vm: MainViewModel,
    navRequest: NavRequest?,
    onNavConsumed: () -> Unit,
    onRequestPermissions: () -> Unit,
    onMic: () -> Unit,
    onToggleStandby: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.CHAT) }
    var vocabFilter by rememberSaveable { mutableStateOf(VocabFilter.ALL) }
    var study by remember { mutableStateOf<StudySession?>(null) }
    // Chuyển tab do HUY FAN tự mở (vd "xem danh sách từ") → không cắt ngang câu đang đọc
    var engineNav by remember { mutableStateOf(false) }
    var lessonEditor by remember { mutableStateOf<LessonEditorRequest?>(null) }
    var autoStartDue by remember { mutableStateOf(false) }
    var practiceOpen by remember { mutableStateOf(false) }
    var interpreterOpen by remember { mutableStateOf(false) }
    var autoOpen by remember { mutableStateOf(false) }
    var rolePlayOpen by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }
    var showSheets by remember { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val x = LocalExtra.current

    LaunchedEffect(navRequest) {
        val r = navRequest ?: return@LaunchedEffect
        tab = r.tab
        if (r.startDue) autoStartDue = true
        if (r.vocabToday != null) vocabFilter = if (r.vocabToday) VocabFilter.TODAY else VocabFilter.ALL
        onNavConsumed()
    }
    LaunchedEffect(Unit) {
        vm.engine.events.collect { e ->
            when (e) {
                is EngineEvent.OpenVocab -> {
                    engineNav = true
                    tab = Tab.VOCAB
                    vocabFilter = if (e.todayOnly) VocabFilter.TODAY else VocabFilter.ALL
                }
                EngineEvent.OpenReview -> { engineNav = true; tab = Tab.REVIEW }
                EngineEvent.OpenHistory -> { engineNav = true; tab = Tab.HISTORY }
                EngineEvent.Ended -> Unit
                EngineEvent.Woke -> tab = Tab.CHAT
            }
        }
    }

    // Rời tab Chat → dừng cuộc trò chuyện (vẫn giữ nội dung) để không nghe/trả lời lung tung
    LaunchedEffect(tab) {
        com.hankukko.huyfan.ForegroundTracker.setMainOnChat(tab == Tab.CHAT || tab == Tab.TRANSLATE) // tab đã có sẵn mic + phiên dịch
        if (tab != Tab.CHAT) {
            if (engineNav) vm.engine.endHandsFree() else vm.engine.cancel()
        }
        engineNav = false
    }

    // Bàn phím đang mở → ẩn thanh tab để ô nhập nằm sát bàn phím
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val imeOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    Box(
        Modifier
            .fillMaxSize()
            .neonBackdrop()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { if (!imeOpen) NeonNavBar(tab) { tab = it } },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
            ) {
                AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                    when (t) {
                        Tab.CHAT -> ChatScreen(
                            vm, onMic = onMic, onToggleStandby = onToggleStandby,
                            onOpenSettings = { tab = Tab.SETTINGS },
                            onOpenHistory = { tab = Tab.HISTORY },
                            onOpenInterpreter = { tab = Tab.TRANSLATE },
                        )
                        Tab.VOCAB -> VocabScreen(
                            vm, vocabFilter, onFilter = { vocabFilter = it },
                            onOpenReview = { tab = Tab.REVIEW },
                            onCreateLesson = { v, g -> lessonEditor = LessonEditorRequest(vocabIds = v, grammarIds = g) },
                            onAsk = { q -> tab = Tab.CHAT; vm.send(q) },
                        )
                        Tab.REVIEW -> ReviewScreen(
                            vm,
                            onStart = { study = it },
                            onEditLesson = { lessonEditor = it },
                            onVoiceQuiz = {
                                tab = Tab.CHAT
                                vm.engine.submit("Kiểm tra tôi các từ đã học hôm nay, hỏi từng từ một nhé", handsFree = true)
                            },
                            autoStartDue = autoStartDue,
                            onAutoStartConsumed = { autoStartDue = false },
                            onPractice = { vm.engine.cancel(); practiceOpen = true },
                            onRolePlay = { vm.engine.cancel(); rolePlayOpen = true },
                        )
                        Tab.HISTORY -> HistoryScreen(vm, onOpen = { id -> vm.openSession(id); tab = Tab.CHAT }, onBack = { tab = Tab.CHAT })
                        Tab.TRANSLATE -> com.hankukko.huyfan.ui.screens.TranslateScreen(
                            onConversation = { vm.engine.cancel(); interpreterOpen = true },
                            onAuto = { vm.engine.cancel(); autoOpen = true },
                        )
                        Tab.RULES -> RulesScreen(vm, onAsk = { q -> tab = Tab.CHAT; vm.send(q) })
                        Tab.SETTINGS -> SettingsScreen(
                            vm,
                            onRequestPermissions = onRequestPermissions,
                            onEditPrompt = { showPrompt = true },
                            onSheetsGuide = { showSheets = true },
                        )
                    }
                }
            }
        }
    }

    if (showPrompt) {
        PromptEditorDialog(
            current = settings.customPrompt,
            onSave = { p -> vm.updateSettings { it.copy(customPrompt = p) } },
            onDismiss = { showPrompt = false },
        )
    }
    if (showSheets) SheetsGuideDialog(onDismiss = { showSheets = false })
    study?.let { StudyDialog(vm, it, onDismiss = { study = null; vm.syncAfterStudy() }) }
    lessonEditor?.let { LessonEditorDialog(vm, it, onDismiss = { lessonEditor = null }) }
    if (practiceOpen) com.hankukko.huyfan.ui.screens.PracticeDialog(vm, onDismiss = { practiceOpen = false })
    if (interpreterOpen) com.hankukko.huyfan.ui.screens.InterpreterDialog(onDismiss = { interpreterOpen = false })
    if (autoOpen) com.hankukko.huyfan.ui.screens.AutoInterpreterDialog(onDismiss = { autoOpen = false })
    if (rolePlayOpen) com.hankukko.huyfan.ui.screens.RolePlayDialog(vm, onDismiss = { rolePlayOpen = false })
}

/**
 * Nền "Neon Seoul": đen ánh xanh + quầng neon hồng / cyan / tím + lưới chấm LED rất mờ (giao diện tối).
 * Dùng chung cho màn chính và các màn toàn màn hình.
 */
@Composable
fun Modifier.neonBackdrop(): Modifier {
    val x = LocalExtra.current
    val bg = MaterialTheme.colorScheme.background
    return this
        .background(bg)
        .drawBehind {
            val w = size.width
            val h = size.height
            if (x.isDark) {
                val step = 22.dp.toPx()
                val pts = ArrayList<Offset>()
                var yy = step / 2
                while (yy < h) {
                    var xx = step / 2
                    while (xx < w) { pts.add(Offset(xx, yy)); xx += step }
                    yy += step
                }
                drawPoints(pts, PointMode.Points, Color.White.copy(alpha = 0.045f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
            }
            fun glow(c: Color, a: Float, cx: Float, cy: Float, r: Float) {
                val center = Offset(cx, cy)
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center, r), radius = r, center = center)
            }
            val k = if (x.isDark) 1f else 0.45f
            glow(Brand.Pink, 0.20f * k, w * 0.05f, -h * 0.02f, w * 0.85f)
            glow(Brand.Cyan, 0.13f * k, w * 1.0f, h * 0.10f, w * 0.75f)
            glow(Brand.Violet, 0.14f * k, w * 0.5f, h * 1.02f, w * 0.95f)
        }
}

/** Thanh tab nổi: khối kính tối, viền neon, mục đang chọn có viên gradient hồng → tím phát sáng. */
@Composable
private fun NeonNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 8.dp)
            .shadow(if (x.isDark) 22.dp else 12.dp, shape, ambientColor = Brand.Violet, spotColor = if (x.isDark) Brand.Pink else Brand.Violet)
            .clip(shape)
            .background(x.navBg)
            .border(1.dp, x.neonBorder, shape)
            .padding(horizontal = 2.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tiếng Anh: Phiên dịch & Nguyên tắc làm ở đợt sau → tạm ẩn
        Tab.entries.filter { it.inBar && !(com.hankukko.huyfan.Lang.isEn && (it == Tab.TRANSLATE || it == Tab.RULES)) }.forEach { t ->
            NeonNavItem(t, current == t || (t == Tab.CHAT && current == Tab.HISTORY), Modifier.weight(1f)) { onSelect(t) }
        }
    }
}

@Composable
private fun NeonNavItem(t: Tab, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val x = LocalExtra.current
    val tint by animateColorAsState(if (on) Color.White else x.muted, tween(220), label = "navTint")
    val label by animateColorAsState(if (on) MaterialTheme.colorScheme.onBackground else x.muted, tween(220), label = "navLabel")
    val pill by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "navPill")
    Column(
        modifier
            .selectable(
                selected = on, onClick = onClick, role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 50.dp, height = 32.dp), contentAlignment = Alignment.Center) {
            if (pill > 0.01f) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val s = 0.55f + 0.45f * pill
                            scaleX = s; scaleY = s; alpha = pill.coerceIn(0f, 1f)
                        }
                        .shadow(12.dp, CircleShape, ambientColor = Brand.Pink, spotColor = Brand.Pink)
                        .clip(CircleShape)
                        .background(Brand.gradient)
                )
            }
            Icon(t.icon, null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            t.label, maxLines = 1, softWrap = false, color = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp, letterSpacing = 0.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
            ),
            // Nhãn dài ("Nguyên tắc") được tràn nhẹ sang 2 bên thay vì bị cắt chữ
            modifier = Modifier.wrapContentWidth(unbounded = true),
        )
    }
}
