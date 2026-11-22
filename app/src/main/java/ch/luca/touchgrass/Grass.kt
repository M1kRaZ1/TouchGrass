package ch.luca.touchgrass

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Grass {

    private val vertexShaderCode =
        "uniform mat4 uVPMatrix;" +
        "uniform float uTime;" +
        "attribute vec4 vPosition;" +
        "attribute vec4 vColor;" +
        "varying vec4 fColor;" +
        "varying float vDist;" +
        "varying float vBrightness;" +
        "void main() {" +
        "  vec4 pos = vPosition;" +
        "  float bend = 0.0;" +
        "  if (pos.y > 0.0) {" +
        "    bend = sin(uTime + pos.x * 10.0) * 0.05;" +
        "    pos.x += bend;" +
        "    pos.z += cos(uTime + pos.z * 10.0) * 0.05;" +
        "  }" +
        "  gl_Position = uVPMatrix * pos;" +
        "  fColor = vColor;" +
        "  vDist = gl_Position.z;" +
        "  vBrightness = 0.8 + 0.2 * sin(uTime * 0.2) + (pos.y * 0.3);" + // Simpler lighting effect
        "}"

    private val fragmentShaderCode =
        "precision mediump float;" +
        "varying vec4 fColor;" +
        "varying float vDist;" +
        "varying float vBrightness;" +
        "void main() {" +
        "  float fogFactor = clamp((25.0 - vDist) / (25.0 - 5.0), 0.0, 1.0);" +
        "  vec4 fogColor = vec4(0.5, 0.8, 1.0, 1.0);" +
        "  vec4 litColor = fColor * vBrightness;" +
        "  gl_FragColor = mix(fogColor, litColor, fogFactor);" +
        "}"

    private var mProgram: Int
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var vPMatrixHandle: Int = 0
    private var timeHandle: Int = 0

    private val vertexBuffer: FloatBuffer
    private val colorBuffer: FloatBuffer

    private val COORDS_PER_VERTEX = 3
    private val COLORS_PER_VERTEX = 4

    private val grassCoords: FloatArray
    private val grassColors: FloatArray
    private val drawOrder: ShortArray
    private val drawListBuffer: java.nio.ShortBuffer

    init {
        val coordsList = mutableListOf<Float>()
        val colorsList = mutableListOf<Float>()
        val orderList = mutableListOf<Short>()
        var vertexCount: Short = 0

        // Realistic Colors
        val groundDark = floatArrayOf(0.15f, 0.1f, 0.05f, 1.0f)

        val grassBaseColors = arrayOf(
            floatArrayOf(0.02f, 0.2f, 0.02f, 1.0f),
            floatArrayOf(0.05f, 0.25f, 0.05f, 1.0f),
            floatArrayOf(0.01f, 0.15f, 0.02f, 1.0f)
        )
        val grassTipColors = arrayOf(
            floatArrayOf(0.3f, 0.7f, 0.1f, 1.0f),
            floatArrayOf(0.5f, 0.8f, 0.2f, 1.0f),
            floatArrayOf(0.2f, 0.6f, 0.1f, 1.0f)
        )

        // Ground plane
        coordsList.addAll(listOf(-10f, 0f, 10f, -10f, 0f, -10f, 10f, 0f, -10f, 10f, 0f, 10f))
        for (i in 0 until 4) colorsList.addAll(groundDark.toList())

        orderList.addAll(listOf(vertexCount, (vertexCount + 1).toShort(), (vertexCount + 2).toShort()))
        orderList.addAll(listOf(vertexCount, (vertexCount + 2).toShort(), (vertexCount + 3).toShort()))
        vertexCount = 4

        // Increase Density significantly
        // Changed step from 0.4f to 0.15f for much tighter packing
        for (i in -45..45) {
            for (j in -45..45) {
                // Optimization: Don't render everything, add some randomness to the density
                if (Math.random() > 0.85) continue

                val x = (i * 0.22f) + (Math.random() * 0.15 - 0.07).toFloat()
                val z = (j * 0.22f) + (Math.random() * 0.15 - 0.07).toFloat()

                // Keep performance in check by limiting total vertex count for mobile
                if (vertexCount > 30000) break

                val height = (0.3f + Math.random() * 0.5).toFloat()
                val width = 0.03f + (Math.random() * 0.04).toFloat()

                val colorIdx = (Math.random() * grassBaseColors.size).toInt()
                val baseCol = grassBaseColors[colorIdx]
                val tipCol = grassTipColors[colorIdx]

                val leanX = (Math.random() * 0.15 - 0.07).toFloat()
                val leanZ = (Math.random() * 0.15 - 0.07).toFloat()

                coordsList.addAll(listOf(x - width, 0f, z, x + width, 0f, z, x + leanX, height, z + leanZ))

                colorsList.addAll(baseCol.toList())
                colorsList.addAll(baseCol.toList())
                colorsList.addAll(tipCol.toList())

                orderList.addAll(listOf(vertexCount, (vertexCount + 1).toShort(), (vertexCount + 2).toShort()))
                vertexCount = (vertexCount + 3).toShort()
            }
            if (vertexCount > 30000) break
        }

        grassCoords = coordsList.toFloatArray()
        grassColors = colorsList.toFloatArray()
        drawOrder = orderList.toShortArray()

        val bb = ByteBuffer.allocateDirect(grassCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(grassCoords)
        vertexBuffer.position(0)

        val cb = ByteBuffer.allocateDirect(grassColors.size * 4)
        cb.order(ByteOrder.nativeOrder())
        colorBuffer = cb.asFloatBuffer()
        colorBuffer.put(grassColors)
        colorBuffer.position(0)

        val dlb = ByteBuffer.allocateDirect(drawOrder.size * 2)
        dlb.order(ByteOrder.nativeOrder())
        drawListBuffer = dlb.asShortBuffer()
        drawListBuffer.put(drawOrder)
        drawListBuffer.position(0)

        val vertexShader = MyGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = MyGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    fun draw(mvpMatrix: FloatArray, time: Float) {
        GLES20.glUseProgram(mProgram)
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, COORDS_PER_VERTEX * 4, vertexBuffer)
        colorHandle = GLES20.glGetAttribLocation(mProgram, "vColor")
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, COLORS_PER_VERTEX, GLES20.GL_FLOAT, false, COLORS_PER_VERTEX * 4, colorBuffer)
        vPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uVPMatrix")
        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix, 0)

        timeHandle = GLES20.glGetUniformLocation(mProgram, "uTime")
        GLES20.glUniform1f(timeHandle, time)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
}
