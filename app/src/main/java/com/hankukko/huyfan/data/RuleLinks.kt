package com.hankukko.huyfan.data

/** Một quy tắc trong cẩm nang (assets/rules_vi.json) liên quan tới 1 từ / ngữ pháp. */
data class RuleLink(val ruleId: String, val label: String, val kind: Kind) {
    enum class Kind { IRREGULAR, SOUND, GRAMMAR }
}

/**
 * Tự phân tích từ tiếng Hàn để gợi ý quy tắc liên quan:
 *  - Động/tính từ bất quy tắc (ㅂ, ㄷ, ㅅ, ㅎ, 르, ㄹ, 으 – có tính ngoại lệ)
 *  - Biến âm khi đọc (nối âm, mũi hoá, căng âm, bật hơi, vòm hoá…) bằng cách tách âm tiết thành phụ âm/nguyên âm
 *  - Ngữ pháp → đúng mục giải thích trong cẩm nang
 */
object RuleLinks {

    // ---------------------------------------------------------------- Tách âm tiết Hangul
    private const val BASE = 0xAC00
    private const val LAST = 0xD7A3

    private data class Syl(val initial: Int, val medial: Int, val final: Int)

    private fun syl(c: Char): Syl? {
        val code = c.code
        if (code !in BASE..LAST) return null
        val i = code - BASE
        return Syl(i / 588, (i % 588) / 28, i % 28)
    }

    // Phụ âm đầu
    private const val I_G = 0; private const val I_N = 2; private const val I_D = 3; private const val I_R = 5
    private const val I_M = 6; private const val I_B = 7; private const val I_S = 9; private const val I_O = 11
    private const val I_J = 12; private const val I_H = 18
    // Nguyên âm
    private const val V_EU = 18; private const val V_I = 20
    // Patchim
    private const val F_NONE = 0; private const val F_N = 4; private const val F_D = 7; private const val F_L = 8
    private const val F_M = 16; private const val F_B = 17; private const val F_S = 19; private const val F_NG = 21
    private const val F_T = 25; private const val F_H = 27; private const val F_NH = 6; private const val F_LH = 15

    private val K_CLASS = setOf(1, 2, 3, 9, 24)            // ㄱ ㄲ ㄳ ㄺ ㅋ → [ㄱ]
    private val T_CLASS = setOf(7, 19, 20, 22, 23, 25)      // ㄷ ㅅ ㅆ ㅈ ㅊ ㅌ → [ㄷ]
    private val P_CLASS = setOf(17, 18, 26, 14)             // ㅂ ㅄ ㅍ ㄿ → [ㅂ]
    private val OBSTRUENT = K_CLASS + T_CLASS + P_CLASS
    private val DOUBLE = setOf(3, 5, 6, 9, 10, 11, 12, 13, 14, 15, 18)
    private val TENSABLE_INITIAL = setOf(I_G, I_D, I_B, I_S, I_J)

    /** Hiện tượng biến âm khi đọc 1 từ/cụm từ (tối đa vài mục, không trùng). */
    fun soundRules(text: String): List<RuleLink> {
        if (com.hankukko.huyfan.Lang.isEn) return emptyList() // liên kết quy tắc chỉ có cho tiếng Hàn
        val out = LinkedHashMap<String, String>()
        text.split(' ', '.', ',', '?', '!').filter { it.isNotBlank() }.forEach { word ->
            val s = word.map { syl(it) }
            for (k in s.indices) {
                val a = s[k] ?: continue
                if (a.final in DOUBLE) out.putIfAbsent("patchim-kep", "Patchim kép")
                val b = s.getOrNull(k + 1) ?: continue
                val f = a.final
                if (f == F_NONE) continue
                when {
                    // 같이 → [가치], 굳이 → [구지]
                    (f == F_D || f == F_T) && b.initial == I_O && b.medial == V_I -> out.putIfAbsent("vom-hoa", "Vòm hoá")
                    (f == F_H || f == F_NH || f == F_LH) && b.initial == I_O -> out.putIfAbsent("bo-h", "Lược âm ㅎ")
                    b.initial == I_O && f != F_NG -> out.putIfAbsent("noi-am", "Nối âm")
                    (f == F_H || f == F_NH || f == F_LH) && b.initial in setOf(I_G, I_D, I_J) -> out.putIfAbsent("bat-hoi", "Bật hơi hoá")
                    f in OBSTRUENT && b.initial == I_H -> out.putIfAbsent("bat-hoi", "Bật hơi hoá")
                    f in OBSTRUENT && (b.initial == I_N || b.initial == I_M) -> out.putIfAbsent("mui-hoa", "Mũi hoá")
                    (f == F_N && b.initial == I_R) || (f == F_L && b.initial == I_N) -> out.putIfAbsent("luu-am-hoa", "Lưu âm hoá")
                    (f == F_M || f == F_NG || f in K_CLASS || f in P_CLASS) && b.initial == I_R -> out.putIfAbsent("l-thanh-n", "ㄹ đọc [ㄴ]")
                    f in OBSTRUENT && b.initial in TENSABLE_INITIAL -> out.putIfAbsent("cang-am", "Căng âm hoá")
                }
            }
        }
        return out.entries.take(3).map { RuleLink(it.key, it.value, RuleLink.Kind.SOUND) }
    }

    // ---------------------------------------------------------------- Bất quy tắc
    private val EXC_B = setOf("입", "잡", "좁", "씹", "업", "뽑", "집", "접")
    private val EXC_D = setOf("받", "닫", "믿", "얻", "쏟", "뜯", "곧")
    private val EXC_S = setOf("씻", "웃", "벗", "앗", "솟", "빗")
    private val EXC_H = setOf("좋", "놓", "넣", "낳", "닿", "쌓", "찧")

    /** Động/tính từ (kết thúc bằng 다) thuộc nhóm bất quy tắc nào. */
    fun irregular(ko: String): RuleLink? {
        val w = ko.trim().substringAfterLast(' ')
        if (w.length < 2 || !w.endsWith("다")) return null
        val stemChar = w[w.length - 2]
        val stem = stemChar.toString()
        val s = syl(stemChar) ?: return null
        if (stem == "하" || stem == "이") return null
        return when {
            stem == "르" -> {
                val prev = w.getOrNull(w.length - 3)?.toString()
                if (prev == "따" || prev == "치") RuleLink("bqt-eu", "Bất quy tắc 으", RuleLink.Kind.IRREGULAR)
                else RuleLink("bqt-reu", "Bất quy tắc 르", RuleLink.Kind.IRREGULAR)
            }
            s.final == F_B && stem !in EXC_B -> RuleLink("bqt-b", "Bất quy tắc ㅂ", RuleLink.Kind.IRREGULAR)
            s.final == F_D && stem !in EXC_D -> RuleLink("bqt-d", "Bất quy tắc ㄷ", RuleLink.Kind.IRREGULAR)
            s.final == F_S && stem !in EXC_S -> RuleLink("bqt-s", "Bất quy tắc ㅅ", RuleLink.Kind.IRREGULAR)
            s.final == F_H && stem !in EXC_H -> RuleLink("bqt-h", "Bất quy tắc ㅎ", RuleLink.Kind.IRREGULAR)
            s.final == F_L -> RuleLink("bqt-l", "Lược ㄹ khi chia", RuleLink.Kind.IRREGULAR)
            s.final == F_NONE && s.medial == V_EU -> RuleLink("bqt-eu", "Lược 으 khi chia", RuleLink.Kind.IRREGULAR)
            else -> null
        }
    }

    fun forVocab(ko: String): List<RuleLink> = if (com.hankukko.huyfan.Lang.isEn) emptyList() else listOfNotNull(irregular(ko)) + soundRules(ko)

    // ---------------------------------------------------------------- Ngữ pháp
    private fun gkey(p: String) = p.replace(Regex("[\\s\\-~()/·.]"), "")

    /** (khoá con trong mẫu ngữ pháp, khớp nguyên văn?) → quy tắc */
    private val GRAMMAR: List<Triple<List<String>, Boolean, Pair<String, String>>> = listOf(
        Triple(listOf("아서어서", "아서", "어서", "해서", "니까"), false, "aseo-vs-nikka" to "Phân biệt 아서 / 니까"),
        Triple(listOf("고있", "고계"), false, "tiep-dien" to "Đang làm: -고 있다"),
        Triple(listOf("고싶", "수있", "수없", "야하", "야되", "야해", "줄알", "줄모르"), false, "muon-co-the-phai" to "Muốn / có thể / phải"),
        Triple(listOf("았", "었", "였", "했", "았었", "았었였", "었었"), false, "qua-khu" to "Thì quá khứ"),
        Triple(listOf("ㄹ거", "을거", "겠"), false, "tuong-lai" to "Thì tương lai"),
        Triple(listOf("지않", "지못", "안", "못"), false, "phu-dinh" to "Phủ định 안 / 못"),
        Triple(listOf("세요", "십시오", "주세요", "지마", "ㅂ시다", "읍시다", "을까요", "ㄹ까요"), false, "menh-lenh" to "Đề nghị, yêu cầu"),
        Triple(listOf("시", "으시", "께서"), false, "eusi" to "Kính ngữ -(으)시-"),
        Triple(listOf("습니다", "ㅂ니다", "습니까", "ㅂ니까"), false, "b-seumnida" to "Đuôi -ㅂ니다/습니다"),
        Triple(listOf("아요", "어요", "해요"), false, "a-eo-yo" to "Chia -아요/어요"),
        Triple(listOf("이에요", "예요", "입니다", "아니"), false, "la-co" to "Câu “là” 이에요/예요"),
        Triple(listOf("은는", "은", "는"), true, "eun-neun" to "Tiểu từ 은/는"),
        Triple(listOf("이가", "이", "가"), true, "i-ga" to "Tiểu từ 이/가"),
        Triple(listOf("을를", "을", "를"), true, "eul-reul" to "Tiểu từ 을/를"),
        Triple(listOf("에서", "에"), true, "e-eseo" to "에 và 에서"),
        Triple(listOf("에게", "한테", "께", "에게서", "한테서"), true, "ege-hante" to "에게 / 한테 / 께"),
        Triple(listOf("와과", "와", "과", "하고", "랑", "이랑"), true, "wa-gwa" to "와/과, 하고, (이)랑"),
        Triple(listOf("으로", "로", "으로로"), true, "euro" to "Tiểu từ (으)로"),
        Triple(listOf("도", "만", "의", "부터", "까지", "보다", "처럼", "같이"), true, "do-man" to "도, 만, 의, 부터/까지…"),
        Triple(listOf("는데", "ㄴ데", "은데", "지만", "면", "으면", "려고", "으려고", "러", "으러", "면서", "으면서", "다가", "아도", "어도", "ㄹ때", "을때", "기전", "ㄴ후", "은후", "고", "기때문", "때문"), false, "lien-ket-co-ban" to "Vĩ tố nối câu"),
    )

    fun forGrammar(pattern: String, form: String = ""): List<RuleLink> {
        if (com.hankukko.huyfan.Lang.isEn) return emptyList() // liên kết quy tắc chỉ có cho tiếng Hàn
        val k = gkey(pattern)
        if (k.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, String>()
        for ((keys, exact, rule) in GRAMMAR) {
            val hit = if (exact) keys.any { it == k } else keys.any { key -> key.length >= 2 && k.contains(key) || key.length == 1 && k == key }
            if (hit) out.putIfAbsent(rule.first, rule.second)
            if (out.size >= 2) break
        }
        if (out.isEmpty() && form.contains("định ngữ", ignoreCase = true)) out["dinh-ngu"] = "Định ngữ"
        return out.map { RuleLink(it.key, it.value, RuleLink.Kind.GRAMMAR) }
    }
}
