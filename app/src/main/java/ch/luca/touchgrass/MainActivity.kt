package ch.luca.touchgrass

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

class MainActivity : AppCompatActivity() {
    private lateinit var gLView: MyGLSurfaceView
    private lateinit var networkManager: NetworkManager
    
    // UI Elements
    private lateinit var gameHud: View
    private lateinit var mainMenu: View
    private lateinit var settingsMenu: View
    private lateinit var handView: TextView
    private lateinit var statusText: TextView
    private lateinit var seekSensitivity: SeekBar

    // Joystick state
    private var moveX = 0f
    private var moveY = 0f
    private var lookX = 0f
    private var sensitivity = 3.5f

    private val MAP_LIMIT = 9f
    private val handler = Handler(Looper.getMainLooper())
    private var lastVibrationTime = 0L
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            val moved = updateGame()
            
            // Sync with network
            val remotePlayers = networkManager.remotePlayers.toMap()
            gLView.renderer.remotePlayers = remotePlayers
            networkManager.broadcastPosition(gLView.renderer.posX, gLView.renderer.posZ, gLView.renderer.angle)
            
            val status = if (networkManager.isHost) "HOST" else "CLIENT"
            statusText.text = "Online: ${remotePlayers.size + 1} ($status)"

            if (moved) {
                // Footstep haptics
                val now = System.currentTimeMillis()
                if (now - lastVibrationTime > 400) {
                    vibrateSubtle()
                    lastVibrationTime = now
                }
            }

            if (moved || remotePlayers.isNotEmpty()) {
                gLView.requestRender()
            }
            handler.postDelayed(this, 20)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        networkManager = NetworkManager(this)

        gLView = MyGLSurfaceView(this)
        findViewById<FrameLayout>(R.id.gLViewContainer).addView(gLView)

        initUI()
        setupJoysticks()
    }

    private fun initUI() {
        gameHud = findViewById(R.id.gameHud)
        mainMenu = findViewById(R.id.mainMenu)
        settingsMenu = findViewById(R.id.settingsMenu)
        handView = findViewById(R.id.handView)
        statusText = findViewById(R.id.statusText)
        seekSensitivity = findViewById(R.id.seekSensitivity)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            mainMenu.visibility = View.GONE
            gameHud.visibility = View.VISIBLE
            networkManager.start()
            startLoop()
            // Test vibration on start
            vibrateStrong()
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            mainMenu.visibility = View.GONE
            settingsMenu.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            settingsMenu.visibility = View.GONE
            mainMenu.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.btnMenu).setOnClickListener {
            gameHud.visibility = View.GONE
            mainMenu.visibility = View.VISIBLE
            networkManager.stop()
            stopLoop()
        }

        findViewById<Button>(R.id.btnTouchGrass).setOnClickListener {
            performTouchGrassAnimation()
        }

        seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sensitivity = 1.0f + (progress / 100f) * 6.0f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startLoop() {
        stopLoop()
        handler.post(updateRunnable)
    }

    private fun stopLoop() {
        handler.removeCallbacks(updateRunnable)
    }

    private fun performTouchGrassAnimation() {
        handView.animate().cancel()
        handView.visibility = View.VISIBLE
        handView.translationY = 400f 
        
        vibrateStrong()
        
        handView.animate()
            .translationY(-100f)
            .setDuration(300)
            .withEndAction {
                handView.animate()
                    .translationY(400f)
                    .setDuration(300)
                    .setStartDelay(100)
                    .withEndAction {
                        handView.visibility = View.INVISIBLE
                    }
            }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupJoysticks() {
        val moveBase = findViewById<View>(R.id.moveJoystickBase)
        val moveThumb = findViewById<View>(R.id.moveJoystickThumb)
        val camBase = findViewById<View>(R.id.cameraJoystickBase)
        val camThumb = findViewById<View>(R.id.cameraJoystickThumb)

        moveBase.setOnTouchListener { v, event ->
            handleJoystick(event, moveBase, moveThumb) { x, y ->
                moveX = x
                moveY = y
            }
            true
        }

        camBase.setOnTouchListener { v, event ->
            handleJoystick(event, camBase, camThumb) { x, _ ->
                lookX = x
            }
            true
        }
    }

    private fun handleJoystick(event: MotionEvent, base: View, thumb: View, update: (Float, Float) -> Unit) {
        val radius = base.width / 2f
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - radius
                val dy = event.y - radius
                val distance = sqrt(dx * dx + dy * dy)
                val limit = min(distance, radius)
                val angle = atan2(dy, dx)

                val tx = cos(angle) * limit
                val ty = sin(angle) * limit
                
                thumb.translationX = tx
                thumb.translationY = ty
                update(tx / radius, ty / radius)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                thumb.translationX = 0f
                thumb.translationY = 0f
                update(0f, 0f)
            }
        }
    }

    private fun updateGame(): Boolean {
        if (gameHud.visibility != View.VISIBLE) return false
        var changed = false

        if (abs(lookX) > 0.05f) {
            gLView.renderer.angle += lookX * sensitivity
            changed = true
        }

        if (abs(moveX) > 0.05f || abs(moveY) > 0.05f) {
            val speed = 0.12f
            val angleRad = Math.toRadians(gLView.renderer.angle.toDouble())
            
            val forward = -moveY 
            val strafe = moveX

            val dx = (forward * sin(angleRad) + strafe * cos(angleRad)) * speed
            val dz = (-forward * cos(angleRad) + strafe * sin(angleRad)) * speed

            val newX = gLView.renderer.posX + dx.toFloat()
            val newZ = gLView.renderer.posZ + dz.toFloat()

            if (abs(newX) < MAP_LIMIT) {
                gLView.renderer.posX = newX
                changed = true
            }
            if (abs(newZ) < MAP_LIMIT) {
                gLView.renderer.posZ = newZ
                changed = true
            }
        }
        return changed
    }

    private fun vibrateStrong() {
        vibrate(100, 255)
    }

    private fun vibrateSubtle() {
        vibrate(20, 100)
    }

    private fun vibrate(duration: Long, amplitude: Int) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        } catch (e: Exception) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    override fun onPause() {
        super.onPause()
        gLView.onPause()
        networkManager.stop()
        stopLoop()
    }

    override fun onResume() {
        super.onResume()
        gLView.onResume()
        if (gameHud.visibility == View.VISIBLE) {
            networkManager.start()
            startLoop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkManager.stop()
        stopLoop()
    }
}
