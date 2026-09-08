package xyz.gojihub.vpn.globe

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class GlobeNode(val id: String, val lat: Double, val lon: Double, val country: String)

/** Палитра "light" из goji-globe.js, конвертированная в нормализованный RGB — исходная
 *  бежево-бумажная концепция глобуса, без фото-текстур/реалистичных цветов/освещения. */
data class GlobeTheme(
    val ocean: FloatArray, val land: FloatArray, val grid: FloatArray,
    val home: FloatArray, val hi: FloatArray, val arc: FloatArray, val dot: FloatArray
) {
    companion object {
        val Light = GlobeTheme(
            ocean = hex(0xe7e0cf), land = hex(0x12312c), grid = hex(0x12312c),
            home = hex(0xd9714b), hi = hex(0x00897e), arc = hex(0xd9714b), dot = hex(0xa9a08a)
        )
        // Та же композиция ролей (тёплый океан → тёмный, чернильные берега → светлые,
        // акценты чуть ярче для контраста на тёмном фоне), а не случайные цвета — карточка с
        // глобусом раньше оставалась светло-бежевой даже при включённой тёмной теме приложения.
        val Dark = GlobeTheme(
            ocean = hex(0x1b211e), land = hex(0xc7d0cb), grid = hex(0xc7d0cb),
            home = hex(0xe98863), hi = hex(0x2bc8b8), arc = hex(0xe98863), dot = hex(0x6b746e)
        )
        private fun hex(v: Int) = floatArrayOf(
            ((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f
        )
    }
}

private val HOME_LAT = 55.75
private val HOME_LON = 37.62

private const val VS = """
    uniform mat4 uMVP;
    uniform vec2 uOffset;
    attribute vec4 aPosition;
    attribute float aT;
    varying float vT;
    void main() {
        vec4 pos = uMVP * aPosition;
        pos.xy += uOffset * pos.w;
        gl_Position = pos;
        gl_PointSize = 5.0;
        vT = aT;
    }
"""
// uShimmer=1 — бегущий по линии связи блик (vT — параметр 0..1 вдоль дуги, aT): сама линия
// остаётся сплошной обычного цвета, а поверх неё едет один плавный световой пик от точки
// А к точке Б — простая анимация без освещения/текстур.
// "Кометный" блик вместо симметричной синусоиды: яркая голова ровно в uShimmerTime и
// экспоненциально затухающий хвост позади неё (mod заворачивает дистанцию по кругу вдоль
// дуги) — читается как бегущий пакет данных с шлейфом, а не просто пятно света туда-сюда.
private const val FS = """
    precision mediump float;
    uniform vec4 uColor;
    uniform float uShimmer;
    uniform float uShimmerTime;
    uniform vec3 uHighlight;
    varying float vT;
    void main() {
        if (uShimmer > 0.5) {
            float behind = mod(uShimmerTime - vT, 1.0);
            float glow = exp(-behind * 9.0);
            gl_FragColor = vec4(mix(uColor.rgb, uHighlight, glow), uColor.a * (0.55 + 0.45 * glow));
        } else {
            gl_FragColor = uColor;
        }
    }
"""

class GojiGlobeRenderer(private val context: Context, initialTheme: GlobeTheme = GlobeTheme.Light) : GLSurfaceView.Renderer {

    // var, не val — палитру переключает GojiGlobe.kt при смене тёмной/светлой темы в
    // Настройках, уже после того как GL-поверхность создана и рендерер живёт (пересоздавать
    // саму GLSurfaceView ради смены пары цветов не нужно).
    @Volatile var theme: GlobeTheme = initialTheme

    @Volatile var status: String = "off"
    @Volatile var currentNode: GlobeNode? = null
    @Volatile var nodes: List<GlobeNode> = emptyList()

    /** Экранные координаты (0..1 от размера вьюпорта) и видимость плавающей подписи узла. */
    var onLabelUpdate: ((visible: Boolean, x: Float, y: Float, title: String) -> Unit)? = null

    private var program = 0
    private var aPosition = 0
    private var aT = 0
    private var uMVP = 0
    private var uColor = 0
    private var uOffset = 0
    private var uShimmer = 0
    private var uShimmerTime = 0
    private var uHighlight = 0

    private var geo: GeoData? = null
    private var coastBuf: FloatBuffer? = null
    private var borderBuf: FloatBuffer? = null
    private var highlightBuf: FloatBuffer? = null
    private var highlightVerts = 0
    private var highlightedCountry: String? = null

    private lateinit var sphereBuf: FloatBuffer
    private var sphereLatSeg = 32
    private var sphereBandVerts = 0
    private lateinit var pinBuf: FloatBuffer // единичная сфера (r=1), масштабируется матрицей модели
    private var pinLatSeg = 6
    private var pinBandVerts = 0
    private lateinit var ringBuf: FloatBuffer
    private var ringVerts = 0
    private lateinit var graticuleBuf: FloatBuffer // сетка параллелей/меридианов — доп. детализация
    private var graticuleVerts = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    private var width = 1
    private var height = 1
    private var rotY = -0.2f
    private var rotX = -0.16f
    private var targetY = -0.2f
    private var targetX = -0.16f
    private var locked = false
    private var lastStatus = "off"
    private var lastNodeId: String? = null
    private var t = 0f

    // Дистанция камеры до глобуса — при подключении "подъезжаем" ближе, чтобы крупнее и
    // нагляднее показать маршрут дом → узел; в покое ближе, чем раньше, чтобы планета лучше
    // заполняла собой всю карточку, а не оставляла пустые поля по бокам.
    private var camDist = 4.3f
    private var targetCamDist = 4.3f

    private val homePos = GlobeMath.toVec(HOME_LAT, HOME_LON, GlobeMath.RADIUS * 1.012f)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // GLSurfaceView непрозрачен по умолчанию — если чистить в чёрный, область за пределами
        // диска сферы будет чёрной (это и было видно на скриншоте). Чистим в цвет "океана" —
        // ровно то же заполнение, что и у сферы, стык невидим.
        GLES20.glClearColor(theme.ocean[0], theme.ocean[1], theme.ocean[2], 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        program = buildProgram(VS, FS)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aT = GLES20.glGetAttribLocation(program, "aT")
        uMVP = GLES20.glGetUniformLocation(program, "uMVP")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uOffset = GLES20.glGetUniformLocation(program, "uOffset")
        uShimmer = GLES20.glGetUniformLocation(program, "uShimmer")
        uShimmerTime = GLES20.glGetUniformLocation(program, "uShimmerTime")
        uHighlight = GLES20.glGetUniformLocation(program, "uHighlight")

        // Было 48×32 — на крупном плане (после недавнего приближения камеры) грани сферы и
        // ступеньки широтного затенения были заметны на глаз ("угловатость"). Вдвое плотнее
        // сетка сглаживает и силуэт, и переходы между полосами освещения — по треугольникам
        // для мобильного GPU всё ещё дёшево (около 12 тыс. вместо 3 тыс.).
        val lonSeg = 96
        sphereLatSeg = 64
        sphereBandVerts = (lonSeg + 1) * 2
        sphereBuf = buildSphere(GlobeMath.RADIUS, lonSeg, sphereLatSeg)

        val pinLonSeg = 16
        pinLatSeg = 12
        pinBandVerts = (pinLonSeg + 1) * 2
        pinBuf = buildSphere(1f, pinLonSeg, pinLatSeg)

        ringBuf = buildRingLine(0.065f, 40).also { ringVerts = it.capacity() / 3 }
        graticuleBuf = buildGraticule()

        // Гео-данные грузим асинхронно и заливаем в GL на следующем кадре (см. onDrawFrame).
        pendingGeoLoad = true
        CoroutineScope(Dispatchers.IO).launch {
            val loaded = GeoData.load(context)
            geo = loaded
        }
    }

    private var pendingGeoLoad = false

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
        GLES20.glViewport(0, 0, w, h)
        Matrix.perspectiveM(projection, 0, 38f, w.toFloat() / h.toFloat(), 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Цвет очистки — каждый кадр, а не только в onSurfaceCreated: тема может переключиться
        // (тёмная/светлая), пока GL-поверхность уже живёт, а onSurfaceCreated второй раз не
        // вызывается — иначе фон за пределами диска сферы остался бы в старом цвете.
        GLES20.glClearColor(theme.ocean[0], theme.ocean[1], theme.ocean[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glUniform2f(uOffset, 0f, 0f)

        if (pendingGeoLoad && geo != null) {
            val g = geo!!
            coastBuf = toBuffer(g.coastLines)
            borderBuf = toBuffer(g.borderLines)
            pendingGeoLoad = false
        }

        t += 0.016f
        val node = currentNode
        val nodePos = if (node != null) GlobeMath.toVec(node.lat, node.lon, GlobeMath.RADIUS * 1.014f)
                      else GlobeMath.toVec(60.17, 24.94, GlobeMath.RADIUS * 1.014f)

        // ── доворот и "замирание", как в JS loop() ──
        val on = status == "on"
        val connecting = status == "connecting"
        if (node != null) {
            // Камера целится точно в сам узел подключения (framing = nodePos, без смешивания
            // с домом и без постоянного смещения по тангажу) — раньше и смешивание с домом,
            // и фиксированный сдвиг -0.18f в сумме уводили узел заметно выше центра карточки.
            val framing = nodePos
            targetY = -atan2(framing[0], framing[2])
            val y0 = framing[1]
            val z0 = hypot(framing[0].toDouble(), framing[2].toDouble()).toFloat()
            targetX = atan2(y0.toDouble(), z0.toDouble()).toFloat()
        }
        // "Замирание" (locked) раньше сбрасывалось только при смене статуса — если выбранный
        // узел меняется, пока соединение и так остаётся "on" (например автопереключение обратно
        // на обычный узел при появлении Wi-Fi, без разрыва самого туннеля), камера оставалась
        // "примёрзшей" к экрану ПРЕЖНЕГО узла и никогда не доворачивалась к новому — снаружи
        // это выглядело как "не центрируется", хотя формула наведения сама по себе верна.
        val nodeChanged = lastNodeId != node?.id
        if (status != "on" || nodeChanged) locked = false
        if (lastStatus != status || nodeChanged) {
            highlightedCountry = if (status == "off") null else node?.country
            highlightBuf = buildHighlight(highlightedCountry)
            lastStatus = status; lastNodeId = node?.id
        }

        if (on) {
            val dy = targetY - rotY; val dx = targetX - rotX
            if (abs(dy) < 0.002f && abs(dx) < 0.002f) locked = true
            if (!locked) { rotY += dy * 0.06f; rotX += dx * 0.06f }
        } else if (connecting) {
            rotY += (targetY - rotY) * 0.05f + 0.001f
            rotX += (targetX - rotX) * 0.05f
        } else {
            rotY += 0.0013f
            // лёгкое "дыхание" наклона в покое — глобус не выглядит статичным даже когда
            // автовращение по Y почти незаметно на глаз
            rotX += (-0.16f + 0.02f * sin(t * 0.35f) - rotX) * 0.02f
        }

        // приближение камеры при подключении/на связи — "заезжаем" ближе к маршруту, при
        // отключении плавно возвращаемся на исходный общий план (тот теперь тоже крупнее,
        // чтобы планета лучше заполняла карточку целиком)
        targetCamDist = when {
            on -> 2.3f
            connecting -> 2.75f
            else -> 4.3f
        }
        camDist += (targetCamDist - camDist) * 0.045f
        Matrix.setLookAtM(view, 0, 0f, 0f, camDist, 0f, 0f, 0f, 0f, 1f, 0f)

        Matrix.multiplyMM(vpMatrix, 0, projection, 0, view, 0)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, Math.toDegrees(rotX.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(model, 0, Math.toDegrees(rotY.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, vpMatrix, 0, model, 0)

        // океан — по одной полосе широты за отрисовку (иначе triangle strip склеит несмежные полосы).
        // bandShade — простое "освещение сверху" без изменения шейдера, сфера читается объёмной,
        // а не плоской заливкой одного тона. Яркость считаем по РЕАЛЬНОЙ повёрнутой позиции
        // полосы (bandBrightness ниже), а не по номеру полосы в исходной сетке — той же полосе,
        // что была "северным полюсом" при построении сферы, вовсе не обязательно оказываться
        // наверху экрана после доворота/наклона камеры к узлу; со статичной по номеру привязкой
        // градиент "плыл" по сфере и на скриншотах выглядел как смещённое пятно, а не освещение.
        drawSphereBands(sphereBuf, sphereLatSeg, sphereBandVerts, mvp, theme.ocean, 1f) { band ->
            bandBrightness(sphereBuf, band, sphereBandVerts)
        }
        // сетка параллелей/меридианов — тонкая фоновая деталь поверх океана, под берегами
        draw(graticuleBuf, graticuleVerts, GLES20.GL_LINES, theme.grid, 0.1f)
        // берега/границы: glLineWidth>1 не работает на большинстве мобильных GPU (реальный
        // диапазон часто [1,1]), поэтому толщину имитируем 5-проходной отрисовкой со сдвигом
        // на ~1px в NDC (см. drawThickLine) — иначе линии остаются машным волоском на плотных
        // экранах даже при альфе, близкой к 1.0.
        coastBuf?.let { drawThickLine(it, it.capacity() / 3, GLES20.GL_LINES, theme.land, 1f) }
        borderBuf?.let { drawThickLine(it, it.capacity() / 3, GLES20.GL_LINES, theme.land, 0.8f) }
        // подсветка страны назначения
        highlightBuf?.let { if (highlightVerts > 0) drawThickLine(it, highlightVerts, GLES20.GL_LINES, theme.hi, if (on) 1f else 0.45f + 0.25f * sin(t * 4), pixelRadius = 1.6f) }

        // пины серверов + дом — "кольцо-мишень" вместо плоского кружка (тонкое кольцо теплого
        // акцента + маленькое ядро) читается как современная метка на карте, а не голая точка;
        // лёгкое мерцание у каждого узла (разная фаза от id) не даёт списку выглядеть статичным.
        nodes.forEach { n ->
            val active = node != null && n.id == node.id
            if (!(active && status != "off")) {
                val pos = GlobeMath.toVec(n.lat, n.lon, GlobeMath.RADIUS * 1.012f)
                val phase = (n.id.hashCode() and 0xFFFF) / 65535f * (2f * PI.toFloat())
                val breathe = 0.7f + 0.3f * ((sin(t * 1.1f + phase) + 1f) / 2f)
                drawPinAt(pos, theme.dot, 0.012f, breathe)
                drawRingAt(pos, theme.hi, 0.042f, breathe * 0.32f)
            }
        }
        // точка А (дом) — мягкое свечение того же тона вокруг компактного ядра, по мотивам
        // референсного видео (простая светящаяся точка, а не сплошной плоский кружок).
        drawPinAt(homePos, theme.home, 0.032f, 0.22f)
        drawPinAt(homePos, theme.home, 0.015f)

        // точка Б (узел подключения) — тот же приём: мягкий ореол + маленькое яркое ядро +
        // один тонкий пульсирующий обод (вместо прежних двух разноцветных колец — по видео
        // это одна чистая светящаяся точка, а не "радар" из нескольких окружностей).
        if (status != "off") {
            val glowOp = (if (on) 0.32f else 0.2f) + 0.07f * sin(t * 2.4f)
            drawPinAt(nodePos, theme.hi, 0.044f, glowOp)
            drawPinAt(nodePos, floatArrayOf(1f, 1f, 1f), 0.016f)
            drawPinAt(nodePos, theme.hi, 0.022f, 0.85f)
            val p = (t * 0.5f) % 1f
            drawRingAt(nodePos, theme.hi, 0.9f + p * 1.6f, (if (on) 0.5f else 0.35f) * (1f - p))
        }

        // дуга дом → узел — мягкое свечение под пунктирной линией (эффект луча/кабеля передачи
        // данных вместо плоской сплошной нити — как на референсном глобусе), поверх которой
        // едет "кометный" блик с хвостом от точки А к точке Б.
        if (node != null) {
            val control = GlobeMath.midControlPoint(homePos, nodePos, GlobeMath.RADIUS * 1.5f)
            val arcVerts = buildArc(homePos, control, nodePos, 48)
            val dashVerts = buildDashedArc(homePos, control, nodePos, 40)
            val arcOp = if (on) 0.9f else if (connecting) 0.3f + 0.22f * sin(t * 5) else 0.05f
            if (on || connecting) drawThickLine(toBuffer(arcVerts), arcVerts.size / 3, GLES20.GL_LINE_STRIP, theme.arc, arcOp * 0.35f, pixelRadius = 4.5f)
            draw(toBuffer(dashVerts), dashVerts.size / 3, GLES20.GL_LINES, theme.arc, arcOp)
            if (on || connecting) {
                val speed = if (on) 0.35f else 0.2f
                val arcVertsWithT = buildArcWithT(homePos, control, nodePos, 48)
                drawShimmerArc(toBuffer(arcVertsWithT), 49, theme.arc, arcOp, floatArrayOf(1f, 0.82f, 0.55f), t * speed)
            }
        }

        // проекция маркера в экранные координаты — для плавающей подписи в Compose
        if (status != "off" && node != null) {
            val screen = project(nodePos)
            val front = screen != null
            onLabelUpdate?.invoke(front, screen?.get(0) ?: 0f, screen?.get(1) ?: 0f, node.country)
        } else {
            onLabelUpdate?.invoke(false, 0f, 0f, "")
        }

        GLES20.glDisableVertexAttribArray(aPosition)
    }

    /** Яркость широтной полосы для псевдо-освещения — по Y одной опорной вершины полосы
     *  ПОСЛЕ поворота текущей моделью (см. model), а не по номеру полосы в исходной сетке.
     *  Camera смотрит вдоль Z с up=(0,1,0) без собственного поворота, поэтому Y после модели —
     *  прямой аналог "выше/ниже на экране": свет условно всегда сверху экрана независимо от
     *  того, как сейчас повёрнут/наклонён сам глобус. */
    private fun bandBrightness(buf: FloatBuffer, band: Int, bandVerts: Int): Float {
        val idx = band * bandVerts * 3
        val world = floatArrayOf(buf.get(idx), buf.get(idx + 1), buf.get(idx + 2), 1f)
        val rotated = FloatArray(4)
        Matrix.multiplyMV(rotated, 0, model, 0, world, 0)
        val ny = (rotated[1] / GlobeMath.RADIUS).coerceIn(-1f, 1f)
        return 0.96f + 0.26f * ny
    }

    private fun project(v: FloatArray): FloatArray? {
        val world = floatArrayOf(v[0], v[1], v[2], 1f)
        val rotated = FloatArray(4)
        Matrix.multiplyMV(rotated, 0, model, 0, world, 0)
        val clip = FloatArray(4)
        Matrix.multiplyMV(clip, 0, vpMatrix, 0, rotated, 0)
        if (clip[3] <= 0f) return null
        val ndcX = clip[0] / clip[3]; val ndcY = clip[1] / clip[3]; val ndcZ = clip[2] / clip[3]
        if (ndcZ > 1f) return null
        // видимость: сторона сферы, обращённая к камере (та же эвристика, что и в JS)
        val camDir = GlobeMath.normalize(floatArrayOf(0f, 0f, camDist))
        val worldNorm = GlobeMath.normalize(floatArrayOf(rotated[0], rotated[1], rotated[2]))
        val dot = worldNorm[0] * camDir[0] + worldNorm[1] * camDir[1] + worldNorm[2] * camDir[2]
        if (dot < 0.16f) return null
        return floatArrayOf(ndcX * 0.5f + 0.5f, -ndcY * 0.5f + 0.5f)
    }

    private fun draw(buf: FloatBuffer?, count: Int, mode: Int, color: FloatArray, alpha: Float) {
        if (buf == null || count <= 0) return
        buf.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniform4f(uColor, color[0], color[1], color[2], alpha)
        GLES20.glUniform2f(uOffset, 0f, 0f)
        GLES20.glDrawArrays(mode, 0, count)
    }

    /** glLineWidth выше 1px не работает на многих мобильных GPU (Adreno и т.п. — реально
     *  поддерживают только [1,1]), из-за чего берега/границы оставались еле видной "паутинкой"
     *  на плотных экранах даже с альфой под 1.0. Вместо этого рисуем ту же линию несколько раз
     *  со смещением на ~1px в экранных координатах (NDC-офсет через uOffset в шейдере,
     *  умноженный на w до перспективного деления — не "плывёт" по глубине) — крест из 5
     *  проходов даёт устойчивую толщину линии в 2-3px независимо от поддержки GPU. */
    private fun drawThickLine(buf: FloatBuffer?, count: Int, mode: Int, color: FloatArray, alpha: Float, pixelRadius: Float = 1.1f) {
        if (buf == null || count <= 0) return
        val dx = pixelRadius * 2f / width
        val dy = pixelRadius * 2f / height
        buf.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniform4f(uColor, color[0], color[1], color[2], alpha)
        val offsets = arrayOf(0f to 0f, dx to 0f, -dx to 0f, 0f to dy, 0f to -dy)
        for ((ox, oy) in offsets) {
            GLES20.glUniform2f(uOffset, ox, oy)
            GLES20.glDrawArrays(mode, 0, count)
        }
        GLES20.glUniform2f(uOffset, 0f, 0f)
    }

    /** Бегущий по дуге световой блик: та же геометрия, что и обычная линия, но с параметром
     *  vT (0 в точке А, 1 в точке Б) — один плавный пик яркости едет по линии со временем.
     *  Толщину имитируем тем же NDC-офсетом, что и в drawThickLine — иначе на плотных экранах
     *  блик теряется в однопиксельной линии. */
    private fun drawShimmerArc(buf: FloatBuffer, vertCount: Int, color: FloatArray, alpha: Float, highlight: FloatArray, timeVal: Float) {
        buf.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 16, buf)
        buf.position(3)
        GLES20.glEnableVertexAttribArray(aT)
        GLES20.glVertexAttribPointer(aT, 1, GLES20.GL_FLOAT, false, 16, buf)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniform4f(uColor, color[0], color[1], color[2], alpha)
        GLES20.glUniform3f(uHighlight, highlight[0], highlight[1], highlight[2])
        GLES20.glUniform1f(uShimmerTime, timeVal)
        GLES20.glUniform1f(uShimmer, 1f)
        val dx = 1.4f * 2f / width
        val dy = 1.4f * 2f / height
        val offsets = arrayOf(0f to 0f, dx to 0f, -dx to 0f, 0f to dy, 0f to -dy)
        for ((ox, oy) in offsets) {
            GLES20.glUniform2f(uOffset, ox, oy)
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, vertCount)
        }
        GLES20.glUniform2f(uOffset, 0f, 0f)
        GLES20.glUniform1f(uShimmer, 0f)
        GLES20.glDisableVertexAttribArray(aT)
    }

    private fun drawPinAt(pos: FloatArray, color: FloatArray, radius: Float, alpha: Float = 1f) {
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, pos[0], pos[1], pos[2])
        Matrix.scaleM(m, 0, radius, radius, radius)
        val localMvp = FloatArray(16)
        Matrix.multiplyMM(localMvp, 0, mvp, 0, m, 0)
        drawSphereBands(pinBuf, pinLatSeg, pinBandVerts, localMvp, color, alpha)
    }

    /** Рисует UV-сферу как набор triangle strip'ов, по одной полосе широты — полосы не смежны
     *  по вершинам, поэтому одним общим strip'ом их рисовать нельзя (получились бы паразитные грани).
     *  [bandShade] — необязательный множитель яркости цвета на полосу (0-based индекс от
     *  северного полюса к южному), без него ведёт себя как раньше — сплошная заливка. */
    private fun drawSphereBands(
        buf: FloatBuffer, latSeg: Int, bandVerts: Int, mvpMatrix: FloatArray, color: FloatArray, alpha: Float,
        bandShade: ((Int) -> Float)? = null
    ) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
        if (bandShade == null) GLES20.glUniform4f(uColor, color[0], color[1], color[2], alpha)
        for (band in 0 until latSeg) {
            if (bandShade != null) {
                val shade = bandShade(band)
                GLES20.glUniform4f(uColor, color[0] * shade, color[1] * shade, color[2] * shade, alpha)
            }
            buf.position(band * bandVerts * 3)
            GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, buf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, bandVerts)
        }
    }

    private fun drawRingAt(pos: FloatArray, color: FloatArray, scale: Float, alpha: Float) {
        val normal = GlobeMath.normalize(pos)
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, pos[0], pos[1], pos[2])
        // ориентация кольца по нормали поверхности (приблизительно — поворот к "up")
        val angle = Math.toDegrees(atan2(normal[0].toDouble(), normal[2].toDouble())).toFloat()
        Matrix.rotateM(m, 0, angle, 0f, 1f, 0f)
        val tilt = Math.toDegrees(atan2(hypot(normal[0].toDouble(), normal[2].toDouble()), normal[1].toDouble())).toFloat()
        Matrix.rotateM(m, 0, tilt, 1f, 0f, 0f)
        Matrix.scaleM(m, 0, scale, scale, scale)
        val localMvp = FloatArray(16)
        Matrix.multiplyMM(localMvp, 0, mvp, 0, m, 0)
        ringBuf.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, ringBuf)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, localMvp, 0)
        GLES20.glUniform4f(uColor, color[0], color[1], color[2], alpha)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, ringVerts)
    }

    private fun buildHighlight(country: String?): FloatBuffer? {
        val g = geo ?: return null
        if (country == null) { highlightVerts = 0; return null }
        val rings = g.countryRings[country] ?: run { highlightVerts = 0; return null }
        val floats = ArrayList<Float>()
        rings.forEach { ring ->
            var i = 0
            while (i + 5 < ring.size) {
                floats.add(ring[i]); floats.add(ring[i + 1]); floats.add(ring[i + 2])
                floats.add(ring[i + 3]); floats.add(ring[i + 4]); floats.add(ring[i + 5])
                i += 3
            }
        }
        highlightVerts = floats.size / 3
        return toBuffer(floats.toFloatArray())
    }

    private fun buildArc(a: FloatArray, control: FloatArray, b: FloatArray, segments: Int): FloatArray {
        val out = FloatArray((segments + 1) * 3)
        val p = FloatArray(3)
        for (i in 0..segments) {
            GlobeMath.quadBezier(a, control, b, i.toFloat() / segments, p)
            out[i * 3] = p[0]; out[i * 3 + 1] = p[1]; out[i * 3 + 2] = p[2]
        }
        return out
    }

    /** Та же дуга, но не сплошной GL_LINE_STRIP, а набор коротких отрезков с зазорами для
     *  GL_LINES — как пунктирная линия связи на референсном глобусе, а не сплошная нить. */
    private fun buildDashedArc(a: FloatArray, control: FloatArray, b: FloatArray, segments: Int, dashRatio: Float = 0.55f): FloatArray {
        val out = ArrayList<Float>((segments * 6))
        val p = FloatArray(3)
        for (i in 0 until segments) {
            val t0 = i.toFloat() / segments
            val t1 = (i + dashRatio) / segments
            if (t1 > 1f) continue
            GlobeMath.quadBezier(a, control, b, t0, p)
            out.add(p[0]); out.add(p[1]); out.add(p[2])
            GlobeMath.quadBezier(a, control, b, t1, p)
            out.add(p[0]); out.add(p[1]); out.add(p[2])
        }
        return out.toFloatArray()
    }

    /** То же самое, но с 4-м компонентом на вершину — параметром 0..1 вдоль дуги (для
     *  бегущего блика в drawShimmerArc). */
    private fun buildArcWithT(a: FloatArray, control: FloatArray, b: FloatArray, segments: Int): FloatArray {
        val out = FloatArray((segments + 1) * 4)
        val p = FloatArray(3)
        for (i in 0..segments) {
            val tt = i.toFloat() / segments
            GlobeMath.quadBezier(a, control, b, tt, p)
            out[i * 4] = p[0]; out[i * 4 + 1] = p[1]; out[i * 4 + 2] = p[2]; out[i * 4 + 3] = tt
        }
        return out
    }

    private fun buildSphere(r: Float, lonSeg: Int, latSeg: Int): FloatBuffer {
        val verts = ArrayList<Float>()
        for (i in 0 until latSeg) {
            val lat1 = 90.0 - i * 180.0 / latSeg
            val lat2 = 90.0 - (i + 1) * 180.0 / latSeg
            for (j in 0..lonSeg) {
                val lon = -180.0 + j * 360.0 / lonSeg
                val v1 = GlobeMath.toVec(lat1, lon, r)
                val v2 = GlobeMath.toVec(lat2, lon, r)
                verts.add(v1[0]); verts.add(v1[1]); verts.add(v1[2])
                verts.add(v2[0]); verts.add(v2[1]); verts.add(v2[2])
            }
        }
        return toBuffer(verts.toFloatArray())
    }

    /** Тонкая сетка параллелей (широта, шаг 30°) и меридианов (долгота, шаг 30°) чуть поверх
     *  поверхности океана — фоновая деталь, отрисовывается с низкой альфой поверх сферы. */
    private fun buildGraticule(): FloatBuffer {
        val r = GlobeMath.RADIUS * 1.002f
        val segs = 64
        val verts = ArrayList<Float>()
        for (lat in intArrayOf(-60, -30, 0, 30, 60)) {
            var prev: FloatArray? = null
            for (i in 0..segs) {
                val lon = -180.0 + i * 360.0 / segs
                val v = GlobeMath.toVec(lat.toDouble(), lon, r)
                prev?.let { verts.add(it[0]); verts.add(it[1]); verts.add(it[2]); verts.add(v[0]); verts.add(v[1]); verts.add(v[2]) }
                prev = v
            }
        }
        var lonDeg = 0
        while (lonDeg < 360) {
            var prev: FloatArray? = null
            for (i in 0..segs) {
                val lat = -90.0 + i * 180.0 / segs
                val v = GlobeMath.toVec(lat, lonDeg.toDouble(), r)
                prev?.let { verts.add(it[0]); verts.add(it[1]); verts.add(it[2]); verts.add(v[0]); verts.add(v[1]); verts.add(v[2]) }
                prev = v
            }
            lonDeg += 30
        }
        graticuleVerts = verts.size / 3
        return toBuffer(verts.toFloatArray())
    }

    private fun buildRingLine(r: Float, segments: Int): FloatBuffer {
        val verts = FloatArray(segments * 3)
        for (i in 0 until segments) {
            val a = 2.0 * PI * i / segments
            verts[i * 3] = (r * cos(a)).toFloat()
            verts[i * 3 + 1] = (r * sin(a)).toFloat()
            verts[i * 3 + 2] = 0f
        }
        return toBuffer(verts)
    }

    private fun toBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(arr); position(0)
        }

    private fun buildProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compile(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }
}
