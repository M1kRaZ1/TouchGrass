package ch.luca.touchgrass

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Sun {

    private val vertexShaderCode =
        "uniform mat4 uVPMatrix;" +
        "attribute vec4 vPosition;" +
        "void main() {" +
        "  gl_Position = uVPMatrix * vPosition;" +
        "}"

    private val fragmentShaderCode =
        "precision mediump float;" +
        "void main() {" +
        "  gl_FragColor = vec4(1.0, 0.9, 0.5, 1.0);" + // Bright yellow-ish
        "}"

    private val vertexBuffer: FloatBuffer
    private var mProgram: Int = 0
    private var positionHandle: Int = 0
    private var vPMatrixHandle: Int = 0

    // A simple octahedron or diamond shape for the sun
    private val coords = floatArrayOf(
         0.0f,  1.0f,  0.0f,
        -1.0f,  0.0f,  1.0f,
         1.0f,  0.0f,  1.0f,
         1.0f,  0.0f, -1.0f,
        -1.0f,  0.0f, -1.0f,
         0.0f, -1.0f,  0.0f
    )

    private val drawOrder = shortArrayOf(
        0, 1, 2,  0, 2, 3,  0, 3, 4,  0, 4, 1,
        5, 2, 1,  5, 3, 2,  5, 4, 3,  5, 1, 4
    )

    private val drawListBuffer: java.nio.ShortBuffer

    init {
        val bb = ByteBuffer.allocateDirect(coords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(coords)
        vertexBuffer.position(0)

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

    fun draw(mvpMatrix: FloatArray) {
        GLES20.glUseProgram(mProgram)
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)
        vPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uVPMatrix")
        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }
}
