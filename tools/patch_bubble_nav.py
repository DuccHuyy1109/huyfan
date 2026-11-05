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


# Bong bóng tự ẩn khi đang mở màn phiên dịch trong app
patch(J + 'HuyFanApp.kt', [
    ('''    /** Bong bóng nên tạm giấu: cửa sổ hỏi đang mở, đang ở tab Chat, hoặc đang chọn/chụp ảnh. */
    val hideBubble: Boolean get() = assistVisible || pickingImage || (mainVisible && mainOnChat)''',
     '''    /** Đang mở màn phiên dịch (Dịch hội thoại / Dịch tự động) – bong bóng không được che chữ dịch. */
    @Volatile
    var interpreting: Boolean = false
        private set

    fun setInterpreting(v: Boolean) {
        if (interpreting == v) return
        interpreting = v
        onChange?.invoke()
    }

    /** Bong bóng nên tạm giấu: cửa sổ hỏi đang mở, đang ở tab Chat / màn phiên dịch, hoặc đang chọn/chụp ảnh. */
    val hideBubble: Boolean get() = assistVisible || pickingImage || (mainVisible && (mainOnChat || interpreting))'''),
])

patch(J + 'ui/screens/AutoInterpreterScreen.kt', [
    ('''    DisposableEffect(Unit) {
        c.engine.cancel()
        c.engine.setMicHold(true)''', '''    DisposableEffect(Unit) {
        c.engine.cancel()
        c.engine.setMicHold(true)
        com.hankukko.huyfan.ForegroundTracker.setInterpreting(true)'''),
    ('''            recorder.stop()
            c.speaker.stop()
            c.engine.setMicHold(false)''', '''            recorder.stop()
            c.speaker.stop()
            c.engine.setMicHold(false)
            com.hankukko.huyfan.ForegroundTracker.setInterpreting(false)'''),
])

patch(J + 'ui/screens/InterpreterScreen.kt', [
    ('''    DisposableEffect(Unit) {
        engine.setMicHold(true)
        onDispose {
            engine.setMicHold(false)''', '''    DisposableEffect(Unit) {
        engine.setMicHold(true)
        com.hankukko.huyfan.ForegroundTracker.setInterpreting(true)
        onDispose {
            com.hankukko.huyfan.ForegroundTracker.setInterpreting(false)
            engine.setMicHold(false)'''),
])

# Thanh điều hướng 6 mục: chữ nhỏ lại chút, bỏ giãn chữ để "Phiên dịch", "Nguyên tắc" không bị cắt
patch(J + 'ui/MainActivity.kt', [
    ('''                                    fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelSmall,''', '''                                    fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.sp),'''),
    ('''import androidx.compose.ui.unit.dp''', '''import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp'''),
])
print('ok')
