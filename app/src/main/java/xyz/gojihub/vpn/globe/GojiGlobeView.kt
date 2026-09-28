package xyz.gojihub.vpn.globe

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.GLSurfaceView
import android.view.TextureView

/**
 * Прозрачный GL-холст на TextureView — как <canvas> с `alpha: true` в эталоне
 * (handoff-1.0.77/reference/goji-globe.js): вокруг сферы виден общий стеклянный фон, а
 * Compose-элементы можно класть и под, и поверх глобуса. GLSurfaceView так не умеет:
 * он либо непрозрачный прямоугольник, либо поверх всего окна.
 */
class GojiGlobeView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    val goji = GojiGlobeRenderer(context)
    private var renderThread: GlobeRenderThread? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread = GlobeRenderThread(surface, goji, width, height).also { it.start() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.onSizeChanged(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderThread?.requestExitAndWait()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}

private class GlobeRenderThread(
    private val surfaceTexture: SurfaceTexture,
    private val renderer: GLSurfaceView.Renderer,
    @Volatile private var width: Int,
    @Volatile private var height: Int
) : Thread("GojiGlobeGL") {
    @Volatile private var running = true
    @Volatile private var sizeChanged = true

    fun onSizeChanged(w: Int, h: Int) {
        width = w; height = h; sizeChanged = true
    }

    fun requestExitAndWait() {
        running = false
        try { join(2000) } catch (_: InterruptedException) {}
    }

    override fun run() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return
        // antialias: true в эталоне — MSAA 4x, если GPU умеет, иначе без него.
        val config = chooseConfig(display, 4) ?: chooseConfig(display, 0) ?: return
        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        val surface = EGL14.eglCreateWindowSurface(display, config, surfaceTexture, intArrayOf(EGL14.EGL_NONE), 0)
        if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, context)) {
            EGL14.eglDestroyContext(display, context)
            return
        }

        renderer.onSurfaceCreated(null, null)
        while (running) {
            val frameStart = System.nanoTime()
            if (sizeChanged) {
                sizeChanged = false
                renderer.onSurfaceChanged(null, width, height)
            }
            renderer.onDrawFrame(null)
            EGL14.eglSwapBuffers(display, surface)
            val sleepMs = 16 - (System.nanoTime() - frameStart) / 1_000_000
            if (sleepMs > 0) try { sleep(sleepMs) } catch (_: InterruptedException) {}
        }

        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
    }

    private fun chooseConfig(display: EGLDisplay, samples: Int): EGLConfig? {
        val attribs = mutableListOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_STENCIL_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT
        )
        if (samples > 0) attribs += listOf(EGL14.EGL_SAMPLE_BUFFERS, 1, EGL14.EGL_SAMPLES, samples)
        attribs += EGL14.EGL_NONE
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs.toIntArray(), 0, configs, 0, 1, num, 0) || num[0] == 0) return null
        return configs[0]
    }
}
