package ch.luca.touchgrass

import android.content.Context
import android.opengl.GLSurfaceView

class MyGLSurfaceView(context: Context) : GLSurfaceView(context) {

    val renderer: MyGLRenderer

    init {
        setEGLContextClientVersion(2)
        renderer = MyGLRenderer()
        setRenderer(renderer)

        // Optimize: Only render when something actually changes.
        // This significantly reduces GPU usage and saves battery.
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}
