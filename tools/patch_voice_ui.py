import sys

p = sys.argv[1]
s = open(p, encoding='utf-8').read()


def rep(old, new):
    global s
    if old not in s:
        raise SystemExit('NOT FOUND: ' + old[:90])
    s = s.replace(old, new, 1)


rep('''                val engines = remember(tick, ttsReady) { vm.ttsEngines() }
                DropdownField(
                    "Bộ đọc (TTS)", s.ttsEngine,
                    listOf("" to "Tự động (ưu tiên Google)") + engines,
                    onSelect = { e -> vm.updateSettings { it.copy(ttsEngine = e) } },
                )
                SliderRow("Tốc độ đọc", "%.1fx".format(s.speechRate), s.speechRate, 0.6f..1.6f, steps = 9) { v ->
                    vm.updateSettings { it.copy(speechRate = v) }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    FilledTonalButton(onClick = { vm.testVoice() }) { Text("Nghe thử giọng") }
                }''', '''                Text("Kiểu giọng đọc", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                Segmented(TtsMode.entries, s.ttsMode, { it.label }, { m -> vm.updateSettings { it.copy(ttsMode = m) } })
                if (s.ttsMode == TtsMode.AI) {
                    Text(
                        "Giọng AI Gemini tự nhiên như người thật, 1 giọng đọc liền cả tiếng Việt lẫn tiếng Hàn. " +
                            "Dùng chung Gemini key; mạng chậm hoặc lỗi thì tự đọc bằng giọng máy.",
                        style = MaterialTheme.typography.bodySmall, color = x.muted,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    listOf(true to "GIỌNG NỮ", false to "GIỌNG NAM").forEach { (female, title) ->
                        Text(
                            title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = x.muted,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
                        )
                        AI_VOICES.filter { it.female == female }.forEach { v ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.updateSettings { it.copy(aiVoice = v.id) } }
                                    .padding(start = 8.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = s.aiVoice == v.id, onClick = { vm.updateSettings { it.copy(aiVoice = v.id) } })
                                Column(Modifier.weight(1f)) {
                                    Text(v.id, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(v.desc, style = MaterialTheme.typography.bodySmall, color = x.muted)
                                }
                                IconButton(onClick = {
                                    vm.previewAiVoice(v.id) { err -> if (err != null) context.toast(err) }
                                }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe thử ${v.id}", tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
                RowDivider()
                Text(
                    "Giọng máy (offline)", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                )
                Text(
                    "Đọc tức thì, dùng khi bấm loa ở từ vựng và làm dự phòng. Mỗi ngôn ngữ chọn bộ đọc + giọng riêng " +
                        "(vd tiếng Hàn dùng Samsung, tiếng Việt dùng Google) – bấm loa để nghe thử.",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                val engines = remember(tick, ttsReady) { vm.ttsEngines() }
                val viVoices = remember(tick, ttsReady, s.viTtsEngine) { vm.voiceOptions(false) }
                val koVoices = remember(tick, ttsReady, s.koTtsEngine) { vm.voiceOptions(true) }
                DropdownField(
                    "Bộ đọc tiếng Việt", s.viTtsEngine,
                    listOf("" to "Tự động (ưu tiên Google)") + engines,
                    onSelect = { e -> vm.updateSettings { it.copy(viTtsEngine = e, viVoice = "") } },
                )
                DropdownField(
                    "Giọng tiếng Việt", s.viVoice,
                    listOf("" to "Mặc định của bộ đọc") + viVoices.map { it.name to it.label },
                    onSelect = { v -> vm.updateSettings { it.copy(viVoice = v) } },
                    trailingAction = {
                        IconButton(onClick = { vm.previewDeviceVoice(false, s.viVoice) }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe thử", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
                DropdownField(
                    "Bộ đọc tiếng Hàn", s.koTtsEngine,
                    listOf("" to "Tự động (ưu tiên Google)") + engines,
                    onSelect = { e -> vm.updateSettings { it.copy(koTtsEngine = e, koVoice = "") } },
                )
                DropdownField(
                    "Giọng tiếng Hàn", s.koVoice,
                    listOf("" to "Mặc định của bộ đọc") + koVoices.map { it.name to it.label },
                    onSelect = { v -> vm.updateSettings { it.copy(koVoice = v) } },
                    trailingAction = {
                        IconButton(onClick = { vm.previewDeviceVoice(true, s.koVoice) }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe thử", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
                SliderRow("Tốc độ đọc", "%.1fx".format(s.speechRate), s.speechRate, 0.6f..1.6f, steps = 9) { v ->
                    vm.updateSettings { it.copy(speechRate = v) }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    FilledTonalButton(onClick = { vm.testVoice() }) { Text("Nghe thử câu song ngữ") }
                }''')

rep('import com.hankukko.huyfan.data.ThemeMode\n', '''import com.hankukko.huyfan.data.ThemeMode
import com.hankukko.huyfan.data.TtsMode
import com.hankukko.huyfan.voice.AI_VOICES
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.RadioButton
''')

open(p, 'w', encoding='utf-8').write(s)
print('ok')
