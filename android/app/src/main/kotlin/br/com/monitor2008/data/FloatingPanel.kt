package br.com.monitor2008.data

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import br.com.monitor2008.MainActivity
import br.com.monitor2008.domain.FloatItem
import br.com.monitor2008.domain.FloatRules
import br.com.monitor2008.domain.FloatValues
import br.com.monitor2008.ui.MonitorEngine
import br.com.monitor2008.ui.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Janela flutuante própria do Monitor, por cima do mapa ou da música (pedido do dono: a janela do
 * Android era pequena e não dava para aumentar). Tamanho em três passos (− e +), os números
 * escolhidos em Ajustes, arrastável (guarda a posição) e um toque volta para o app.
 *
 * Desenhada com Views simples sobre o WindowManager ("sobrepor a outros apps"): não precisa de
 * serviço, e um app com janela sobreposta visível segue vivo. Usar na thread principal.
 */
object FloatingPanel {
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cells = mutableMapOf<FloatItem, Pair<TextView, TextView>>()

    val showing: Boolean get() = root != null

    /** Pode usar a janela própria: APK do multimídia, escolhida em Ajustes e com a permissão liberada. */
    fun available(ctx: Context): Boolean =
        Features.ownFloatingWindow && Store(ctx).floatMode == "MONITOR" && Settings.canDrawOverlays(ctx)

    fun show(ctx: Context) {
        val app = ctx.applicationContext as Application
        if (!available(app)) return
        if (root != null) return
        val wm = app.getSystemService(WindowManager::class.java) ?: return
        val store = Store(app)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = store.floatX
            y = store.floatY
        }
        val view = build(app)
        try {
            wm.addView(view, p)
        } catch (e: Exception) {
            return
        }
        root = view
        params = p
        val engine = MonitorEngine.get(app)
        job = scope.launch { engine.ui.collect { update(it) } }
    }

    /** Depois de mudar tamanho ou números em Ajustes: refaz a janela, se estiver aberta. */
    fun refresh(ctx: Context) {
        if (root != null) rebuild(ctx.applicationContext as Application)
    }

    fun hide(ctx: Context) {
        val v = root ?: return
        job?.cancel()
        job = null
        try { ctx.applicationContext.getSystemService(WindowManager::class.java)?.removeView(v) } catch (_: Exception) { }
        root = null
        params = null
        cells.clear()
    }

    /** Troca de tamanho ou de números: refaz o conteúdo no mesmo lugar. */
    private fun rebuild(app: Application) {
        val wm = app.getSystemService(WindowManager::class.java) ?: return
        val old = root ?: return
        val p = params ?: return
        val view = build(app)
        try {
            wm.removeView(old)
            wm.addView(view, p)
            root = view
            update(MonitorEngine.get(app).ui.value)
        } catch (_: Exception) { }
    }

    private fun dp(ctx: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun build(app: Application): LinearLayout {
        val store = Store(app)
        val size = FloatRules.size(store.floatSize)
        val items = FloatRules.parse(store.floatItems)
        cells.clear()

        val box = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 11, 15, 20))
                cornerRadius = dp(app, 18f).toFloat()
                setStroke(dp(app, 2f), Color.rgb(56, 189, 248))
            }
            setPadding(dp(app, 12f), dp(app, 6f), dp(app, 12f), dp(app, 12f))
            layoutParams = ViewGroup.LayoutParams(dp(app, size.widthDp.toFloat()), ViewGroup.LayoutParams.WRAP_CONTENT)
            minimumWidth = dp(app, size.widthDp.toFloat())
        }

        // Barra de cima: nome, − e + (tamanho) e ✕ (fechar). Botões de 56 dp (regra do app).
        val bar = LinearLayout(app).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(TextView(app).apply {
            text = "Monitor"
            setTextColor(Color.rgb(148, 163, 184))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        fun button(label: String, onClick: () -> Unit) = TextView(app).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(56, 189, 248))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dp(app, 56f), dp(app, 56f))
            setOnClickListener { onClick() }
        }
        bar.addView(button("−") { store.floatSize = FloatRules.stepSize(store.floatSize, false); rebuild(app) })
        bar.addView(button("+") { store.floatSize = FloatRules.stepSize(store.floatSize, true); rebuild(app) })
        bar.addView(button("✕") { hide(app) })
        box.addView(bar)

        // Números em duas colunas.
        items.chunked(2).forEach { pair ->
            val row = LinearLayout(app).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { item ->
                val cell = LinearLayout(app).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(0, dp(app, 4f), 0, dp(app, 4f))
                }
                val value = TextView(app).apply {
                    text = "—"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, size.valueSp)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                }
                val label = TextView(app).apply {
                    text = "${item.label} · ${item.unit}"
                    setTextColor(Color.rgb(148, 163, 184))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp)
                }
                cell.addView(value)
                cell.addView(label)
                row.addView(cell)
                cells[item] = value to label
            }
            if (pair.size == 1) row.addView(View(app).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
            box.addView(row)
        }

        // Arrastar move a janela (e guarda a posição); um toque sem arrastar volta para o app.
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        box.setOnTouchListener { _, e ->
            val p = params ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > dp(app, 8f) || abs(dy) > dp(app, 8f)) moved = true
                    if (moved) {
                        p.x = (startX + dx).toInt()
                        p.y = (startY + dy).toInt()
                        try { app.getSystemService(WindowManager::class.java)?.updateViewLayout(root, p) } catch (_: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        store.floatX = p.x
                        store.floatY = p.y
                    } else {
                        openApp(app)
                    }
                    true
                }
                else -> false
            }
        }
        return box
    }

    private fun openApp(app: Application) {
        try {
            app.startActivity(
                Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } catch (_: Exception) { }
    }

    private fun update(ui: UiState) {
        val s = ui.sample
        val v = FloatValues(
            speedKmh = s?.speedKmh, kmPerL = ui.kmPerL, avgKmPerL = ui.tripAvgKmPerL,
            rpm = s?.rpm, coolantC = s?.coolantC, batteryV = s?.batteryV,
        )
        cells.forEach { (item, views) ->
            views.first.text = FloatRules.text(item, v)
            views.first.setTextColor(if (FloatRules.warn(item, v)) Color.rgb(251, 191, 36) else Color.WHITE)
        }
    }
}
