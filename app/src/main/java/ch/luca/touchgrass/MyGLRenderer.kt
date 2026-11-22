package ch.luca.touchgrass

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class MyGLRenderer : GLSurfaceView.Renderer {

    private lateinit var grass: Grass
    private lateinit var remotePlayerShape: RemotePlayerShape
    private lateinit var sun: Sun

    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    // Player position and orientation
    var posX = 0f
    var posZ = 5f
    var angle = 0f // Yaw angle in degrees

    var remotePlayers: Map<String, NetworkManager.RemotePlayerData> = emptyMap()
    private val interpolatedPlayers = mutableMapOf<String, NetworkManager.RemotePlayerData>()
    private var startTime = System.currentTimeMillis()

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // Sky Blue background
        GLES20.glClearColor(0.5f, 0.8f, 1.0f, 1.0f)

        // Enable depth testing to prevent seeing through objects
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        grass = Grass()
        remotePlayerShape = RemotePlayerShape()
        sun = Sun()
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Calculate look-at direction based on angle
        val lookX = posX + sin(Math.toRadians(angle.toDouble())).toFloat()
        val lookZ = posZ - cos(Math.toRadians(angle.toDouble())).toFloat()

        // Set the camera position (View matrix)
        Matrix.setLookAtM(viewMatrix, 0, posX, 1.2f, posZ, lookX, 1.0f, lookZ, 0f, 1.0f, 0f)

        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        val time = (System.currentTimeMillis() - startTime) / 1000f
        grass.draw(vPMatrix, time)

        // Draw Sun
        val sunModelMatrix = FloatArray(16)
        Matrix.setIdentityM(sunModelMatrix, 0)
        Matrix.translateM(sunModelMatrix, 0, 0f, 20f, -40f)
        Matrix.rotateM(sunModelMatrix, 0, time * 2f, 0f, 1f, 0f)
        val sunMvpMatrix = FloatArray(16)
        Matrix.multiplyMM(sunMvpMatrix, 0, vPMatrix, 0, sunModelMatrix, 0)
        sun.draw(sunMvpMatrix)

        // Draw remote players with interpolation
        val playerColor = floatArrayOf(0.8f, 0.2f, 0.2f, 1.0f)
        remotePlayers.forEach { (id, target) ->
            val current = interpolatedPlayers[id] ?: target.copy()

            // Very simple lerp for smooth movement
            val lerpFactor = 0.15f
            val ix = current.x + (target.x - current.x) * lerpFactor
            val iz = current.z + (target.z - current.z) * lerpFactor

            // Angle lerp (handle wrap-around)
            var targetAngle = target.angle
            var currentAngle = current.angle
            while (targetAngle - currentAngle > 180) currentAngle += 360f
            while (targetAngle - currentAngle < -180) currentAngle -= 360f
            val iAngle = currentAngle + (targetAngle - currentAngle) * lerpFactor

            val interpolated = NetworkManager.RemotePlayerData(ix, iz, iAngle, target.lastUpdate)
            interpolatedPlayers[id] = interpolated

            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, ix, 0f, iz)
            Matrix.rotateM(modelMatrix, 0, -iAngle, 0f, 1f, 0f)

            val mvpMatrix = FloatArray(16)
            Matrix.multiplyMM(mvpMatrix, 0, vPMatrix, 0, modelMatrix, 0)
            remotePlayerShape.draw(mvpMatrix, playerColor)
        }

        // Cleanup removed players
        if (interpolatedPlayers.size > remotePlayers.size) {
            val keysToRemove = interpolatedPlayers.keys.filter { !remotePlayers.containsKey(it) }
            keysToRemove.forEach { interpolatedPlayers.remove(it) }
        }
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()

        // We adjust the near plane and frustum to ensure the ground covers the bottom of the screen.
        // Lowering the bottom value of the frustum relative to the camera height
        // ensures the "void" below the ground is not visible.
        Matrix.frustumM(projectionMatrix, 0, -ratio * 0.5f, ratio * 0.5f, -1.0f, 0.5f, 1.0f, 100f)
    }

    companion object {
        fun loadShader(type: Int, shaderCode: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, shaderCode)
                GLES20.glCompileShader(shader)
            }
        }
    }
}
