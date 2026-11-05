package com.hankukko.huyfan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hankukko.huyfan.data.VocabEntity
import com.hankukko.huyfan.data.latestPerWord
import com.hankukko.huyfan.ui.components.EmptyHint
import com.hankukko.huyfan.ui.components.Pill
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra
import java.text.Normalizer

/** 1 chữ Hán trong sổ: âm Hán Việt + các từ có chữ đó. */
data class HanjaChar(val ch: String, val readings: List<String>, val words: List<VocabEntity>)

object HanjaIndex {
    private val CJK = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")

    fun chars(s: String): List<String> = CJK.findAll(s).map { it.value }.toList()

    /** Âm Hán Việt từng chữ: 學生 + "học sinh" → {學: học, 生: sinh} – chỉ khi số chữ khớp số âm tiết. */
    fun readings(hanja: String, hanViet: String): Map<String, String> {
        val cs = chars(hanja)
        val sy = hanViet.trim().split(Regex("[\\s\\-]+")).map { it.lowercase().trim(',', '.', ';', '(', ')') }.filter { it.isNotBlank() }
        if (cs.isEmpty() || cs.size != sy.size) return emptyMap()
        return cs.zip(sy).toMap()
    }

    fun build(vocab: List<VocabEntity>): List<HanjaChar> {
        val words = vocab.latestPerWord().filter { chars(it.hanja).isNotEmpty() }
        val map = LinkedHashMap<String, MutableList<VocabEntity>>()
        val reads = HashMap<String, LinkedHashSet<String>>()
        words.forEach { w ->
            val r = readings(w.hanja, w.hanViet)
            chars(w.hanja).distinct().forEach { c ->
                map.getOrPut(c) { mutableListOf() }.add(w)
                r[c]?.let { reads.getOrPut(c) { LinkedHashSet() }.add(it) }
            }
        }
        return map.map { (c, ws) -> HanjaChar(c, reads[c]?.toList().orEmpty(), ws.sortedByDescending { it.createdAt }) }
            .sortedWith(compareByDescending<HanjaChar> { it.words.size }.thenBy { it.readings.firstOrNull() ?: "~" })
    }

    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')
}

/** Tab "Chữ Hán" trong Sổ từ: lưới chữ Hán, chạm để xem các từ cùng chữ. */
@Composable
fun HanjaTab(index: List<HanjaChar>, query: String, onOpen: (String) -> Unit) {
    val x = LocalExtra.current
    val q = HanjaIndex.fold(query.trim())
    val list = remember(index, q) {
        if (q.isEmpty()) index
        else index.filter { h -> h.ch.contains(query.trim()) || h.readings.any { HanjaIndex.fold(it).contains(q) } || h.words.any { it.ko.contains(query.trim()) } }
    }
    if (index.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyHint(
                Icons.Rounded.Translate, "Chưa có chữ Hán nào",
                "Từ Hán Hàn (vd 학생 學生 – học sinh) sẽ hiện ở đây.",
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brand.Amber.copy(alpha = 0.12f))
                    .padding(14.dp)
            ) {
                Text("${index.size} chữ Hán · ${index.flatMap { it.words }.distinctBy { it.id }.size} từ Hán Hàn", fontWeight = FontWeight.Bold)
                Text(
                    "Âm Hán Việt ≈ âm Hán Hàn (學 học → 학)",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                )
            }
        }
        items(list, key = { it.ch }) { h ->
            Column(
                Modifier
                    .aspectRatio(0.82f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(x.card)
                    .border(1.dp, x.cardBorder, RoundedCornerShape(18.dp))
                    .clickable { onOpen(h.ch) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(h.ch, fontSize = 34.sp, fontWeight = FontWeight.Medium)
                Text(
                    h.readings.firstOrNull() ?: "—", style = MaterialTheme.typography.labelMedium,
                    color = Brand.Amber, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                Text("${h.words.size} từ", style = MaterialTheme.typography.labelSmall, color = x.muted)
            }
        }
    }
}

/**
 * Bảng chi tiết chữ Hán: mỗi chữ → âm Hán Việt + mọi từ trong sổ có chữ đó.
 * [hanja] có thể là 1 chữ (từ tab Chữ Hán) hoặc cả từ (vd 學生 – mở từ thẻ từ vựng).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanjaSheet(
    hanja: String,
    hanViet: String,
    index: List<HanjaChar>,
    onSpeak: (String) -> Unit,
    onAsk: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val x = LocalExtra.current
    val chars = remember(hanja) { HanjaIndex.chars(hanja).distinct() }
    val local = remember(hanja, hanViet) { HanjaIndex.readings(hanja, hanViet) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Translate, null, tint = Brand.Amber)
                Spacer(Modifier.width(8.dp))
                Text("Chữ Hán", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (chars.size > 1 && hanViet.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text("$hanja · $hanViet", style = MaterialTheme.typography.titleMedium, color = Brand.Amber)
                }
            }
            Spacer(Modifier.height(12.dp))
            chars.forEach { c ->
                val h = index.firstOrNull { it.ch == c }
                val readings = (listOfNotNull(local[c]) + h?.readings.orEmpty()).distinct()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brand.Amber.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(c, fontSize = 38.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (readings.isEmpty()) "Chưa rõ âm Hán Việt" else readings.joinToString(" / "),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Brand.Amber,
                        )
                        Text(
                            "Có trong ${h?.words?.size ?: 0} từ của sổ",
                            style = MaterialTheme.typography.bodySmall, color = x.muted,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                h?.words?.forEach { w ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(x.card)
                            .clickable { onSpeak(w.ko) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(w.ko, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    buildAnnotatedString {
                                        w.hanja.forEach { ch ->
                                            if (ch.toString() == c) withStyle(SpanStyle(color = Brand.Amber, fontWeight = FontWeight.Bold)) { append(ch) }
                                            else append(ch)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (w.hanViet.isNotBlank()) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(w.hanViet, style = MaterialTheme.typography.labelMedium, color = x.muted)
                                }
                            }
                            Text(w.vi, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                val r = readings.firstOrNull()
                Row(
                    Modifier
                        .padding(top = 6.dp, bottom = 16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable {
                            onAsk(
                                "Cho tôi 5 từ tiếng Hàn thông dụng có chữ Hán $c" + (if (r != null) " (âm Hán Việt “$r”)" else "") +
                                    ", kèm nghĩa và âm Hán Việt nhé."
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Hỏi HUY FAN thêm từ có chữ $c",
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (chars.isEmpty()) {
                Text("Không có chữ Hán.", color = x.muted)
                Spacer(Modifier.height(16.dp))
            }
            Pill("Âm Hán Việt ≈ âm Hán Hàn – đoán nghĩa nhanh hơn!", color = Brand.Amber, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}
