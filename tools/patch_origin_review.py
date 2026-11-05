import io, sys, os

root = sys.argv[1]  # .../app/src/main
J = 'java/com/hankukko/huyfan/'


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# Mặt sau thẻ ôn: khối "Gốc từ" có nhãn – Hán Hàn (chữ Hán + Hán Việt) hoặc từ mượn
patch(J + 'ui/screens/ReviewScreen.kt', [
    ('''                    if (v.hanja.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("${v.hanja}  ·  ${v.hanViet}", color = Brand.Amber, fontWeight = FontWeight.SemiBold)
                    }''',
     '''                    if (v.hanja.isNotBlank() || v.loan.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        OriginBadge(v.hanja, v.hanViet, v.loan)
                    }'''),
    ('''@Composable
fun StudyConfigDialog(''',
     '''/** Gốc từ trên thẻ ôn: "HÁN HÀN 學生 · học sinh" hoặc "TỪ MƯỢN ice cream". */
@Composable
private fun OriginBadge(hanja: String, hanViet: String, loan: String) {
    val isHan = hanja.isNotBlank()
    val color = if (isHan) Brand.Amber else Brand.Cyan
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isHan) "HÁN HÀN" else "TỪ MƯỢN", color = color,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(10.dp))
        if (isHan) {
            Text(hanja, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            if (hanViet.isNotBlank()) {
                Text("  ·  $hanViet", color = color, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text(loan, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun StudyConfigDialog('''),
])

# Widget "Từ của ngày"
patch(J + 'service/WordWidget.kt', [
    ('''if (reveal) w.vi + (if (w.hanja.isNotBlank()) "  ·  ${w.hanja} ${w.hanViet}" else "") else''',
     '''if (reveal) w.vi + (if (w.hanja.isNotBlank()) "  ·  ${w.hanja} ${w.hanViet}".trimEnd() else if (w.loan.isNotBlank()) "  ·  ${w.loan}" else "") else'''),
])

# Xuất CSV: thêm cột Từ mượn
patch(J + 'ui/MainViewModel.kt', [
    ('''Phiên âm,Hán Hàn,Hán Việt,Từ loại,''', '''Phiên âm,Hán Hàn,Hán Việt,Từ mượn,Từ loại,'''),
    ('''it.romaja, it.hanja, it.hanViet, it.pos,''', '''it.romaja, it.hanja, it.hanViet, it.loan, it.pos,'''),
])
print('ok')
