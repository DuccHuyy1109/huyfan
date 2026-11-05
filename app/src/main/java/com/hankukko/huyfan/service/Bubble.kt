package com.hankukko.huyfan.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.hankukko.huyfan.R
import kotlin.math.hypot

/** Trạng thái hiển thị trên bong bóng (chấm màu + vòng sóng). */
enum class BubbleState { WAITING, PAUSED, LISTENING, THINKING, SPEAKING }

/**
 * Bong bóng chat nổi trên mọi màn hình (cần quyền "Hiển thị trên ứng dụng khác").
 * - Chạm: mở cửa sổ hỏi HUY FAN.
 * - Kéo: di chuyển, thả ra tự hít vào mép gần nhất; kéo vào vòng ✕ ở đáy để ẩn.
 * - Để yên vài giây tự mờ bớt cho đỡ vướng.
 */
class BubbleController(
    private val ctx: Context,
    private val onTap: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("bubble", Context.MODE_PRIVATE)
    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop

    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var ring: View? = null
    private var dot: View? = null
    private var face: FrameLayout? = null
    private var ringAnim: Animator? = null

    private var trash: FrameLayout? = null
    private var trashParams: WindowManager.LayoutParams? = null
    private var trashCircle: TextView? = null

    private var sizeDp = 58
    private var state = BubbleState.WAITING
    private var suppressed = false

    val isShowing: Boolean get() = root != null

    private fun dp(v: Number): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    private val pad get() = dp(12)
    private val windowSize get() = dp(sizeDp) + pad * 2
    private val screenW get() = ctx.resources.displayMetrics.widthPixels
    private val screenH get() = ctx.resources.displayMetrics.heightPixels

    /** Dịch vụ Trợ năng → lớp nổi Trợ năng (hiện cả trên màn hình khoá); còn lại → lớp nổi thường. */
    val onLockScreen: Boolean = ctx is android.accessibilityservice.AccessibilityService

    private val overlayType =
        if (onLockScreen) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ------------------------------------------------------------------ Hiện / ẩn

    fun show(size: Int) {
        if (!onLockScreen && !Settings.canDrawOverlays(ctx)) return
        if (root != null && size == sizeDp) return
        if (root != null) hide(animate = false)
        sizeDp = size
        val s = windowSize
        val p = WindowManager.LayoutParams(
            s, s, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Vị trí lần trước (mặc định: mép phải, 1/3 màn hình)
            val right = prefs.getBoolean("right", true)
            x = if (right) screenW - s + pad / 2 else -pad / 2
            y = prefs.getInt("y", screenH / 3).coerceIn(dp(40), screenH - s - dp(80))
        }
        createTrash()
        val v = buildView()
        runCatching { wm.addView(v, p) }.onFailure { destroyTrash(); return }
        root = v
        params = p
        applyState()
        applySuppressed()
        // Hiện lên mượt: phóng từ 0
        face?.apply { scaleX = 0f; scaleY = 0f; animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator()).start() }
        scheduleIdleFade()
    }

    fun hide(animate: Boolean = true) {
        val v = root ?: return
        ringAnim?.cancel()
        main.removeCallbacksAndMessages(null)
        destroyTrash()
        root = null
        params = null
        if (animate) {
            face?.animate()?.scaleX(0f)?.scaleY(0f)?.setDuration(180)?.withEndAction { runCatching { wm.removeView(v) } }?.start()
        } else {
            runCatching { wm.removeView(v) }
        }
    }

    /** Tạm giấu (vd khi cửa sổ hỏi đang mở) mà không gỡ hẳn. */
    fun setSuppressed(on: Boolean) {
        if (suppressed == on) return
        suppressed = on
        applySuppressed()
    }

    private fun applySuppressed() {
        val v = root ?: return
        v.animate().cancel()
        if (suppressed) {
            v.animate().alpha(0f).setDuration(150).withEndAction { v.visibility = View.INVISIBLE }.start()
        } else {
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(200).start()
            scheduleIdleFade()
        }
    }

    fun setState(s: BubbleState) {
        if (state == s) return
        state = s
        applyState()
    }

    // ------------------------------------------------------------------ Giao diện

    @SuppressLint("ClickableViewAccessibility")
    private fun buildView(): FrameLayout {
        val size = dp(sizeDp)
        val container = FrameLayout(ctx).apply { clipChildren = false; clipToPadding = false }

        // Vòng sóng khi đang nghe
        val ringView = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), Color.parseColor("#FF4D6D"))
            }
            alpha = 0f
        }
        container.addView(ringView, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        ring = ringView

        // Mặt bong bóng: nền gradient thương hiệu + logo ㅎ
        val faceView = FrameLayout(ctx).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#FF3D9A"), Color.parseColor("#9B5CFF"), Color.parseColor("#4F5BFF")),
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), Color.argb(230, 255, 255, 255))
            }
            elevation = dp(8).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setOval(0, 0, view.width, view.height)
            }
            clipToOutline = true
            contentDescription = "Hỏi HUY FAN"
        }
        val logo = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        // Vector icon có lề an toàn → phóng to cho lấp khung (giống logo trong app)
        val logoSize = (size * 1.55f).toInt()
        faceView.addView(logo, FrameLayout.LayoutParams(logoSize, logoSize, Gravity.CENTER))
        container.addView(faceView, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        face = faceView

        // Chấm trạng thái
        val dotSize = dp(sizeDp / 4 + 2)
        val dotView = View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(2), Color.WHITE) }
            elevation = dp(9).toFloat()
        }
        container.addView(
            dotView,
            FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER).apply {
                leftMargin = (size * 0.36f).toInt(); topMargin = (size * 0.36f).toInt()
            },
        )
        dot = dotView

        container.setOnTouchListener(touch)
        return container
    }

    private fun applyState() {
        val d = dot ?: return
        val color = when (state) {
            BubbleState.WAITING -> "#22C55E"   // đang chờ gọi "HUY FAN"
            BubbleState.PAUSED -> "#9AA3B8"    // không nghe từ khoá – chạm để hỏi
            BubbleState.LISTENING -> "#FF4D6D"
            BubbleState.THINKING -> "#FFB547"
            BubbleState.SPEAKING -> "#2DD4BF"
        }
        (d.background as? GradientDrawable)?.setColor(Color.parseColor(color))
        ringAnim?.cancel()
        val r = ring ?: return
        if (state == BubbleState.LISTENING || state == BubbleState.SPEAKING) {
            (r.background as? GradientDrawable)?.setStroke(dp(3), Color.parseColor(color))
            r.alpha = 0.9f
            val sx = ObjectAnimator.ofFloat(r, View.SCALE_X, 1f, 1.32f)
            val sy = ObjectAnimator.ofFloat(r, View.SCALE_Y, 1f, 1.32f)
            val a = ObjectAnimator.ofFloat(r, View.ALPHA, 0.9f, 0f)
            listOf(sx, sy, a).forEach { it.repeatCount = ValueAnimator.INFINITE; it.duration = 1100 }
            val set = android.animation.AnimatorSet().apply { playTogether(sx, sy, a); start() }
            ringAnim = set
            root?.alpha = 1f
        } else {
            r.alpha = 0f
            scheduleIdleFade()
        }
    }

    /** Để yên 3 giây → mờ bớt cho đỡ che nội dung. */
    private fun scheduleIdleFade() {
        main.removeCallbacksAndMessages(null)
        val v = root ?: return
        if (!suppressed) v.alpha = 1f
        main.postDelayed({
            if (root == v && !suppressed && state != BubbleState.LISTENING && state != BubbleState.SPEAKING) {
                v.animate().alpha(0.72f).setDuration(400).start()
            }
        }, 3000)
    }

    // ------------------------------------------------------------------ Kéo thả

    private val touch = object : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var overTrash = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = params ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                    dragging = false; overTrash = false
                    main.removeCallbacksAndMessages(null)
                    v.animate().cancel(); v.alpha = 1f
                    face?.animate()?.scaleX(0.9f)?.scaleY(0.9f)?.setDuration(90)?.start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        face?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(90)?.start()
                        showTrash()
                    }
                    if (dragging) {
                        val (tx, ty) = trashCenter()
                        val cx = startX + dx + windowSize / 2f
                        val cy = startY + dy + windowSize / 2f
                        val near = hypot(cx - tx, cy - ty) < dp(90)
                        if (near) {
                            // Hút vào vòng ✕
                            p.x = (tx - windowSize / 2f).toInt()
                            p.y = (ty - windowSize / 2f).toInt()
                        } else {
                            p.x = (startX + dx).toInt()
                            p.y = (startY + dy).toInt()
                        }
                        if (near != overTrash) {
                            overTrash = near
                            highlightTrash(near)
                            if (near) buzz(v)
                        }
                        runCatching { wm.updateViewLayout(root, p) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    face?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
                    if (dragging) {
                        removeTrash()
                        if (overTrash) {
                            hide()
                            onDismiss()
                        } else {
                            snapToEdge()
                        }
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onTap()
                        scheduleIdleFade()
                    }
                }
            }
            return true
        }
    }

    private fun snapToEdge() {
        val p = params ?: return
        val v = root ?: return
        val s = windowSize
        val toRight = p.x + s / 2 > screenW / 2
        val targetX = if (toRight) screenW - s + pad / 2 else -pad / 2
        p.y = p.y.coerceIn(dp(40), screenH - s - dp(80))
        ValueAnimator.ofInt(p.x, targetX).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                if (root !== v) return@addUpdateListener
                p.x = it.animatedValue as Int
                runCatching { wm.updateViewLayout(v, p) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    prefs.edit().putBoolean("right", toRight).putInt("y", p.y).apply()
                    scheduleIdleFade()
                }
            })
            start()
        }
    }

    // ------------------------------------------------------------------ Vòng ✕ để ẩn

    private fun trashCenter(): Pair<Float, Float> = screenW / 2f to (screenH - dp(120)).toFloat()

    /** Vòng ✕ được tạo sẵn (ẩn) TRƯỚC bong bóng để bong bóng luôn nằm trên – không cần gỡ/thêm lại khi đang kéo. */
    private fun createTrash() {
        if (trash != null) return
        val box = dp(120)
        val circleSize = dp(64)
        val t = FrameLayout(ctx)
        val circle = TextView(ctx).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(200, 20, 24, 41))
                setStroke(dp(2), Color.argb(120, 255, 255, 255))
            }
            elevation = dp(6).toFloat()
        }
        t.addView(circle, FrameLayout.LayoutParams(circleSize, circleSize, Gravity.CENTER))
        val (tx, ty) = trashCenter()
        val p = WindowManager.LayoutParams(
            box, box, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (tx - box / 2).toInt()
            y = (ty - box / 2).toInt()
        }
        circle.alpha = 0f
        runCatching { wm.addView(t, p) }.onFailure { return }
        trash = t
        trashParams = p
        trashCircle = circle
    }

    private fun showTrash() {
        val circle = trashCircle ?: return
        circle.animate().cancel()
        circle.translationY = dp(40).toFloat()
        circle.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun highlightTrash(on: Boolean) {
        val c = trashCircle ?: return
        (c.background as? GradientDrawable)?.setColor(if (on) Color.parseColor("#E8FF4D6D") else Color.argb(200, 20, 24, 41))
        c.animate().scaleX(if (on) 1.25f else 1f).scaleY(if (on) 1.25f else 1f).setDuration(120).start()
    }

    /** Thả tay → giấu vòng ✕ (vẫn giữ cửa sổ cho lần kéo sau). */
    private fun removeTrash() {
        val c = trashCircle ?: return
        c.animate().alpha(0f).scaleX(1f).scaleY(1f).setDuration(120).start()
        highlightTrash(false)
    }

    private fun destroyTrash() {
        val t = trash ?: return
        trash = null
        trashCircle = null
        trashParams = null
        runCatching { wm.removeView(t) }
    }

    private fun buzz(v: View) {
        if (!v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)) {
            runCatching {
                val vib = ctx.getSystemService(Vibrator::class.java)
                vib.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}
