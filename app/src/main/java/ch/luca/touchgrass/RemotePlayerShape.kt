package ch.luca.touchgrass

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class RemotePlayerShape {

    private val vertexShaderCode =
        "uniform mat4 uVPMatrix;" +
        "attribute vec4 vPosition;" +
        "void main() {" +
        "  gl_Position = uVPMatrix * vPosition;" +
        "}"

    private val fragmentShaderCode =
        "precision mediump float;" +
        "uniform vec4 vColor;" +
        "void main() {" +
        "  gl_FragColor = vColor;" +
        "}"

    private val vertexBuffer: FloatBuffer
    private val mProgram: Int
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var vPMatrixHandle: Int = 0

    // A simple character shape: Body + Head
    private val coords = floatArrayOf(
        // Body (Cube)
        -0.2f,  0.8f,  0.2f, // 0
        -0.2f,  0.0f,  0.2f, // 1
         0.2f,  0.0f,  0.2f, // 2
         0.2f,  0.8f,  0.2f, // 3
        -0.2f,  0.8f, -0.2f, // 4
        -0.2f,  0.0f, -0.2f, // 5
         0.2f,  0.0f, -0.2f, // 6
         0.2f,  0.8f, -0.2f, // 7

        // Head (Smaller Cube on top)
        -0.12f, 1.1f,  0.12f, // 8
        -0.12f, 0.8f,  0.12f, // 9
         0.12f, 0.8f,  0.12f, // 10
         0.12f, 1.1f,  0.12f, // 11
        -0.12f, 1.1f, -0.12f, // 12
        -0.12f, 0.8f, -0.12f, // 13
         0.12f, 0.8f, -0.12f, // 14
         0.12f, 1.1f, -0.12f  // 15
    )

    private val drawOrder = shortArrayOf(
        // Body
        0, 1, 2, 0, 2, 3,
        4, 5, 6, 4, 6, 7,
        0, 1, 5, 0, 5, 4,
        3, 2, 6, 3, 6, 7,
        0, 3, 7, 0, 7, 4,
        1, 2, 6, 1, 6, 5,

        // Head
        8, 9, 10, 8, 10, 11,
        12, 13, 14, 12, 14, 15,
        8, 9, 13, 8, 13, 12,
        11, 10, 14, 11, 14, 15,
        8, 11, 15, 8, 15, 12,
        9, 10, 14, 9, 14, 13
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

    fun draw(mvpMatrix: FloatArray, color: FloatArray) {
        GLES20.glUseProgram(mProgram)

        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        colorHandle = GLES20.glGetUniformLocation(mProgram, "vColor")
        GLES20.glUniform4fv(colorHandle, 1, color, 0)

        vPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uVPMatrix")
        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)

        GLES20.glDisableVertexAttribArray(positionHandle)
    }
}
