package xyz.gojihub.vpn.globe

import android.content.Context
import android.opengl.GLSurfaceView

/**
 * GLSurfaceView в дефолтном z-order (не setZOrderOnTop) рисуется НИЖЕ обычного контента —
 * ровно то, что нужно: глобус фоном, а Compose-оверлеи (градиент, подписи), добавленные
 * следом в том же Box, ложатся сверху без обходных манёвров с прозрачностью поверхности.
 */
class GojiGlobeView(context: Context) : GLSurfaceView(context) {
    val goji = GojiGlobeRenderer(context)

    init {
        setEGLContextClientVersion(2)
        setRenderer(goji)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
