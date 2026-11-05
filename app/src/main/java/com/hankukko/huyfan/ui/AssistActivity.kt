package com.hankukko.huyfan.ui

import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.ui.focus.focusRequester
import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import com.hankukko.huyfan.ui.components.hideKeyboardOnTap
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import com.hankukko.huyfan.ui.theme.Brand
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hankukko.huyfan.assistant.EngineEvent
import com.hankukko.huyfan.assistant.LANG_KO
import com.hankukko.huyfan.assistant.Phase
import com.hankukko.huyfan.assistant.Source
import com.hankukko.huyfan.container
import com.hankukko.huyfan.ui.components.BrandMark
import com.hankukko.huyfan.ui.components.MicButton
import com.hankukko.huyfan.ui.components.Pill
import com.hankukko.huyfan.ui.components.TypingDots
import com.hankukko.huyfan.ui.components.VocabTable
import com.hankukko.huyfan.ui.components.GrammarTable
import com.hankukko.huyfan.data.LessonItem
import com.hankukko.huyfan.data.grammarKeyOf
import com.hankukko.huyfan.data.keyOf
import com.hankukko.huyfan.ui.components.VoiceOrb
import com.hankukko.huyfan.ui.screens.LangToggle
import com.hankukko.huyfan.ui.screens.phaseLabel
import com.hankukko.huyfan.ui.theme.BrandTextStyle
import com.hankukko.huyfan.ui.theme.HuyFanTheme
import com.hankukko.huyfan.ui.theme.LocalExtra
import kotlinx.coroutines.delay

/**
 * Bảng trợ lý bật lên từ dưới màn hình (kể cả màn hình khoá):
 * mở bằng câu "HUY FAN", phím sườn, tile, widget, tai nghe, hoặc "trợ lý kỹ thuật số".
 */
class AssistActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FROM = "from"
        const val FROM_WAKE = "wake"
        const val FROM_SHORTCUT = "shortcut"
        const val FROM_BUBBLE = "bubble"
    }

    private var handedOff = false
    /** Đang mở camera / thư viện ảnh → không đóng cửa sổ khi bị che */
    private var pickingImage = false
    private val startedAt = mutableLongStateOf(0L)
    /** Mở từ bong bóng ở chế độ "Gõ phím" → bật sẵn ô nhập và bàn phím */
    private val startTyping = mutableStateOf(false)

    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) container.engine.wake(Source.SHORTCUT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        // Bảng tự đệm bàn phím bằng imePadding() → cửa sổ không tự co nữa (tránh chừa chỗ 2 lần)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onBackPressedDispatcher.addCallback(this) { close() }

        startedAt.longValue = System.currentTimeMillis() - 1500
        if (savedInstanceState == null) {
            // Vừa đổi ngôn ngữ học từ bong bóng → mở lại với cuộc trò chuyện mới, chưa bật mic
            if (com.hankukko.huyfan.Lang.afterSwitch(this, intent)) container.engine.newSession() else begin(intent)
        }

        setContent {
            val settings by container.settingsState.collectAsStateWithLifecycle()
            HuyFanTheme(settings.themeMode) {
                AssistSheet(
                    startedAt = startedAt.longValue,
                    onClose = { close() },
                    onOpenApp = { tab, today -> openApp(tab, today) },
                    onMic = { mic() },
                    startTyping = startTyping.value,
                    onPickStart = { pickingImage = true },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startedAt.longValue = System.currentTimeMillis() - 1500
        begin(intent)
    }

    private fun begin(intent: Intent?) {
        // Được gọi bằng giọng nói → dịch vụ chờ đã chào và bắt đầu nghe rồi
        if (intent?.getStringExtra(EXTRA_FROM) == FROM_WAKE) return
        startTyping.value = false
        if (intent?.getStringExtra(EXTRA_FROM) == FROM_BUBBLE && !container.settingsState.value.bubbleTapListen) {
            // Nơi ồn: không bật mic, mở bàn phím để gõ
            container.engine.cancel()
            startTyping.value = true
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            container.engine.wake(Source.SHORTCUT)
        }
    }

    private fun mic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO); return
        }
        val e = container.engine
        when (e.state.value.phase) {
            Phase.LISTENING -> e.stopListening()
            Phase.IDLE -> e.startListening(handsFree = true)
            else -> e.cancel()
        }
    }

    override fun onResume() {
        super.onResume()
        pickingImage = false
    }

    override fun onStop() {
        super.onStop()
        if (pickingImage) return
        // Bấm Home / chuyển app khi bảng đang mở (không phải do khoá màn hình) → dừng và đóng bảng
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (!isChangingConfigurations && !handedOff && !isFinishing && !locked) {
            container.engine.cancel()
            finish()
        }
    }

    private fun close() {
        // Đang chụp / chọn ảnh (dù lâu) → không đóng, kẻo camera trả ảnh về thì cửa sổ đã mất
        if (pickingImage || com.hankukko.huyfan.ForegroundTracker.pickingImage) return
        if (!handedOff) container.engine.cancel()
        finish()
    }

    private fun openApp(tab: Tab, vocabToday: Boolean?) {
        handedOff = true
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_TAB, tab.name)
        if (vocabToday != null) i.putExtra(MainActivity.EXTRA_VOCAB_TODAY, vocabToday)
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    startActivity(i); finish()
                }

                override fun onDismissCancelled() {
                    handedOff = false
                }
            })
        } else {
            startActivity(i)
            finish()
        }
    }
}

@Composable
private fun AssistSheet(
    startedAt: Long,
    onClose: () -> Unit,
    onOpenApp: (Tab, Boolean?) -> Unit,
    onMic: () -> Unit,
    startTyping: Boolean = false,
    onPickStart: () -> Unit = {},
) {
    val c = androidx.compose.ui.platform.LocalContext.current.container
    val engine = c.engine
    val x = LocalExtra.current
    val state by engine.state.collectAsStateWithLifecycle()
    val messages by engine.messages.collectAsStateWithLifecycle(emptyList())
    var typing by remember(startedAt) { mutableStateOf(startTyping) }
    LaunchedEffect(startTyping, startedAt) { if (startTyping) { typing = true; engine.switchToTyping() } }
    val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(typing, startedAt) {
        if (typing && startTyping) {
            kotlinx.coroutines.delay(250)
            runCatching { inputFocus.requestFocus(); keyboard?.show() }
        }
    }
    var input by remember { mutableStateOf("") }
    val allVocab by c.vocab.all.collectAsStateWithLifecycle(emptyList())
    val allGrammar by c.grammar.all.collectAsStateWithLifecycle(emptyList())
    val vocabKeys = remember(allVocab) { allVocab.map { keyOf(it.ko) }.toHashSet() }
    val grammarKeys = remember(allGrammar) { allGrammar.map { grammarKeyOf(it.pattern) }.toHashSet() }
    // Mỗi lần chạm vào bảng → tính lại thời gian tự đóng
    var lastTouch by remember { mutableLongStateOf(0L) }
    var interpreter by remember { mutableStateOf(false) }
    var autoInterpreter by remember { mutableStateOf(false) }

    val recent = messages.filter { it.createdAt >= startedAt }
    val lastUser = recent.lastOrNull { it.isUser }
    val lastAi = recent.lastOrNull { !it.isUser && it.id > (lastUser?.id ?: 0L) }

    // Tự đóng khi rảnh tay đã kết thúc và không ai đụng tới
    LaunchedEffect(state.phase, typing, recent.size, lastTouch, interpreter, autoInterpreter) {
        if (state.phase == Phase.IDLE && !typing && !interpreter && !autoInterpreter && !com.hankukko.huyfan.ForegroundTracker.pickingImage) {
            delay(if (recent.isEmpty()) 12000 else 20000)
            onClose()
        }
    }
    LaunchedEffect(Unit) {
        engine.events.collect { e ->
            when (e) {
                is EngineEvent.OpenVocab -> onOpenApp(Tab.VOCAB, e.todayOnly)
                EngineEvent.OpenReview -> onOpenApp(Tab.REVIEW, null)
                EngineEvent.OpenHistory -> onOpenApp(Tab.HISTORY, null)
                EngineEvent.Ended -> { delay(900); onClose() }
                EngineEvent.Woke -> Unit
            }
        }
    }

    val noRipple = remember { MutableInteractionSource() }
    val sheetBrush = if (x.isDark) {
        Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF141327), androidx.compose.ui.graphics.Color(0xFF07080F)))
    } else {
        Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color(0xFFF2F4FB)))
    }

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val imeOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Box(
        Modifier
            .fillMaxSize()
            // Chạm ra ngoài: đang gõ → chỉ ẩn bàn phím; không gõ → đóng bảng
            .clickable(interactionSource = noRipple, indication = null) {
                if (imeOpen) { focusManager.clearFocus(); keyboard?.hide() } else onClose()
            }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(sheetBrush)
                .border(1.dp, x.cardBorder, RoundedCornerShape(32.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            lastTouch = System.currentTimeMillis()
                        }
                    }
                }
                .animateContentSize()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(28.dp)
                Spacer(Modifier.width(8.dp))
                Text("HUY FAN", style = MaterialTheme.typography.titleMedium.merge(BrandTextStyle))
                Spacer(Modifier.width(8.dp))
                // Trạng thái gọn: chấm màu + chữ viết tắt (SS = sẵn sàng, ĐN = đang nghe…)
                Box(Modifier.weight(1f).clipToBounds()) {
                    PhaseBadge(state.phase, state.listenLang == LANG_KO)
                }
                val st by c.settingsState.collectAsStateWithLifecycle()
                // Chi tiết: chỉ icon quyển sách (sáng = bật)
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .then(if (st.detailedAnswers) Modifier.background(Brand.gradient) else Modifier.background(Brand.Violet.copy(alpha = 0.12f)))
                        .clickable {
                            lastTouch = System.currentTimeMillis()
                            kotlinx.coroutines.MainScope().launch { c.settings.update { it.copy(detailedAnswers = !it.detailedAnswers) } }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.MenuBook,
                        if (st.detailedAnswers) "Trả lời chi tiết: bật" else "Trả lời chi tiết: tắt",
                        tint = if (st.detailedAnswers) androidx.compose.ui.graphics.Color.White else Brand.Violet,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                // Đổi ngôn ngữ học (🇰🇷 ⇄ 🇬🇧)
                com.hankukko.huyfan.ui.components.LangSwitchButton(AssistActivity::class.java, compact = true, onBeforeSwitch = { engine.cancel() })
                IconButton(onClick = { onOpenApp(Tab.CHAT, null) }) { Icon(Icons.Rounded.OpenInFull, "Mở app", modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Đóng") }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    // Bàn phím mở → thu vùng tin nhắn để ô nhập luôn nằm trong màn hình
                    .heightIn(max = if (imeOpen) 220.dp else 430.dp)
                    .verticalScroll(rememberScrollState())
                    .hideKeyboardOnTap()
            ) {
                if (lastUser == null && lastAi == null) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        VoiceOrb(state.phase, state.level, size = 140.dp)
                        Text(
                            state.partial.ifBlank {
                                when (state.phase) {
                                    Phase.GREETING -> "HUY FAN đây!"
                                    Phase.LISTENING -> if (state.listenLang == LANG_KO) (if (com.hankukko.huyfan.Lang.isEn) "Speak English…" else "말씀하세요…") else "Nói đi ông, tui nghe nè…"
                                    else -> "Chạm mic để hỏi"
                                }
                            },
                            style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                            color = if (state.partial.isBlank()) x.muted else MaterialTheme.colorScheme.onSurface,
                        )
                        state.notice?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    lastUser?.let {
                        if (it.hasImage) {
                            com.hankukko.huyfan.ui.components.ChatImageThumb(it.imagePath, maxWidth = 160.dp)
                            Spacer(Modifier.height(6.dp))
                        }
                        if (it.text.isNotBlank()) Text("“${it.text}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = x.muted)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (state.phase == Phase.THINKING) {
                        TypingDots()
                        Spacer(Modifier.height(10.dp))
                    }
                    lastAi?.let { m ->
                        val vocab = m.vocab
                        if (vocab.isNotEmpty()) {
                            VocabTable(
                                vocab, m.savedCount, onSpeakKorean = { c.speaker.speakKorean(it) },
                                onDelete = { i -> engine.removeLearned(m, LessonItem.VOCAB, i) },
                                savedFlags = vocab.map { keyOf(it.ko) in vocabKeys },
                                onAdd = { i -> engine.addLearned(m, LessonItem.VOCAB, i) },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        val grammar = m.grammar
                        if (grammar.isNotEmpty()) {
                            GrammarTable(
                                grammar, m.savedCount, onSpeakKorean = { c.speaker.speakKorean(it) },
                                onDelete = { i -> engine.removeLearned(m, LessonItem.GRAMMAR, i) },
                                savedFlags = grammar.map { grammarKeyOf(it.pattern) in grammarKeys },
                                onAdd = { i -> engine.addLearned(m, LessonItem.GRAMMAR, i) },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        Text(
                            m.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, lineHeight = 24.sp,
                            color = if (m.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    AnimatedVisibility(state.phase == Phase.LISTENING || state.phase == Phase.GREETING) {
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            VoiceOrb(state.phase, state.level, size = 44.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(state.partial.ifBlank { "Đang nghe…" }, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    state.notice?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            val pending by engine.pendingImage.collectAsStateWithLifecycle()
            pending?.let { img ->
                com.hankukko.huyfan.ui.components.AttachmentBar(
                    img, onRemove = { engine.discardImage() },
                    onQuickAsk = { q -> engine.submit(q, handsFree = false) },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (typing) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        val ph = engine.state.value.phase
                        if (ph != Phase.IDLE) engine.switchToTyping()
                        input = it
                    },
                    placeholder = { Text("Gõ câu hỏi…") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).focusRequester(inputFocus)
                        .onFocusChanged { if (it.isFocused) engine.switchToTyping() },
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank() || pending != null) { engine.submit(input, handsFree = false); input = "" }
                    }),
                    trailingIcon = {
                        IconButton(onClick = { if (input.isNotBlank() || pending != null) { engine.submit(input, handsFree = false); input = "" } }) {
                            Icon(Icons.AutoMirrored.Rounded.Send, "Gửi", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LangToggle(ko = state.listenLang == LANG_KO, onClick = { engine.toggleLanguage() })
                    Spacer(Modifier.width(8.dp))
                    com.hankukko.huyfan.ui.components.AttachImageButton(
                        onPicked = { engine.cancel(); engine.attachImage(it); typing = true },
                        onLaunch = { engine.cancel(); onPickStart() },
                    )
                }
                MicButton(
                    listening = state.phase == Phase.LISTENING || state.phase == Phase.SPEAKING,
                    onClick = onMic, size = 64.dp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                if (!com.hankukko.huyfan.Lang.isEn) {
                    com.hankukko.huyfan.ui.screens.TranslateModeButton(
                        onConversation = { engine.cancel(); interpreter = true },
                        onAuto = { engine.cancel(); autoInterpreter = true },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (typing) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { typing = !typing; if (typing) engine.switchToTyping() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Keyboard, "Gõ phím", tint = if (typing) MaterialTheme.colorScheme.primary else x.muted)
                }
                }
            }
        }
    }
    if (interpreter) com.hankukko.huyfan.ui.screens.InterpreterDialog(onDismiss = { interpreter = false; lastTouch = System.currentTimeMillis() })
    if (autoInterpreter) com.hankukko.huyfan.ui.screens.AutoInterpreterDialog(onDismiss = { autoInterpreter = false; lastTouch = System.currentTimeMillis() })
}


/** Trạng thái viết tắt + chấm màu: SS sẵn sàng · ĐN đang nghe · XL đang xử lý · TL đang trả lời. */
@Composable
private fun PhaseBadge(phase: Phase, ko: Boolean) {
    val (short, color) = when (phase) {
        Phase.IDLE -> "SS" to Brand.Green
        Phase.GREETING -> "HF" to Brand.Violet
        Phase.LISTENING -> (if (ko) "ĐN·" + com.hankukko.huyfan.Lang.current.id.uppercase() else "ĐN") to Brand.Pink
        Phase.THINKING -> "XL" to Brand.Amber
        Phase.SPEAKING -> "TL" to Brand.Cyan
    }
    Row(
        Modifier
            .semantics { contentDescription = phaseLabel(phase, ko) }
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(short, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
