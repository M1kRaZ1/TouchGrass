package ch.luca.touchgrass

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class NetworkManager(private val context: Context) {
    private val TAG = "NetworkManager"
    private val SERVICE_TYPE = "_touchgrass._tcp."
    private val deviceName = android.os.Build.MODEL.replace(" ", "_")
    private val myId = "${deviceName}_${UUID.randomUUID().toString().substring(0, 4)}"
    private var nsdManager: NsdManager? = null
    private var serverSocket: ServerSocket? = null
    private var localPort: Int = 0

    val remotePlayers = ConcurrentHashMap<String, RemotePlayerData>()
    var isHost = false
    private var hostConnection: Connection? = null

    data class RemotePlayerData(var x: Float = 0f, var z: Float = 0f, var angle: Float = 0f, var lastUpdate: Long = 0)

    private val clientConnections = ConcurrentHashMap<String, Connection>()

    private var isStarted = false

    fun start() {
        if (isStarted) return
        isStarted = true
        nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager)

        // Start as potential host by default
        startServer()
        discoverServices()
    }

    private fun startServer() {
        thread {
            try {
                serverSocket = ServerSocket(0)
                localPort = serverSocket!!.localPort
                registerService(localPort)
                isHost = true // We are hosting our own service

                while (isStarted) {
                    val socket = serverSocket?.accept() ?: break
                    handleIncomingConnection(socket)
                }
            } catch (e: Exception) {
                if (isStarted) Log.e(TAG, "Server error", e)
            }
        }
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        nsdManager?.apply {
            try { unregisterService(registrationListener) } catch (e: Exception) {}
            try { stopServiceDiscovery(discoveryListener) } catch (e: Exception) {}
        }
        try { serverSocket?.close() } catch (e: Exception) {}
        clientConnections.values.forEach { it.close() }
        clientConnections.clear()
        hostConnection?.close()
        hostConnection = null
        isHost = false
    }

    fun broadcastPosition(x: Float, z: Float, angle: Float) {
        val message = "POS:$myId:$x:$z:$angle"
        if (isHost) {
            // Host sends to all connected clients
            clientConnections.values.forEach { it.send(message) }
        } else {
            // Client sends only to Host
            hostConnection?.send(message)
        }
        cleanupInactivePlayers()
    }

    private fun cleanupInactivePlayers() {
        val now = System.currentTimeMillis()
        val iterator = remotePlayers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastUpdate > 5000) {
                iterator.remove()
            }
        }
    }

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "TouchGrass_$myId"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun discoverServices() {
        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun handleIncomingConnection(socket: Socket) {
        val conn = Connection(socket)
        conn.start()
    }

    private fun connectToHost(service: NsdServiceInfo) {
        if (hostConnection != null) return // Already connected to a host

        val host = service.host
        val port = service.port
        val name = service.serviceName
        val otherId = name.substringAfter("TouchGrass_", "")

        if (otherId == myId) return

        // If we found a host and our ID is "higher", we gracefully stop being a host
        // This is a simple election: the one who was there first (or has lower ID) wins
        if (myId > otherId) {
            thread {
                try {
                    val socket = Socket(host, port)
                    val conn = Connection(socket)
                    hostConnection = conn
                    conn.start()

                    // Since we found a host, we stop advertising ourselves as host
                    isHost = false
                    try { nsdManager?.unregisterService(registrationListener) } catch (e: Exception) {}
                    Log.d(TAG, "Successfully connected to host $otherId. I am now a client.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to host $name", e)
                }
            }
        }
    }

    inner class Connection(private val socket: Socket) {
        private var out: PrintWriter? = null
        private var remoteId: String? = null

        fun start() {
            thread {
                try {
                    out = PrintWriter(socket.getOutputStream(), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    // Send identification
                    out?.println("ID:$myId")

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        handleMessage(line!!)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Connection lost", e)
                } finally {
                    remoteId?.let {
                        clientConnections.remove(it)
                        remotePlayers.remove(it)
                    }
                    if (this == hostConnection) hostConnection = null
                    close()
                }
            }
        }

        fun send(message: String) {
            thread {
                try {
                    out?.println(message)
                } catch (e: Exception) {
                    // Log.e(TAG, "Send error", e)
                }
            }
        }

        private fun handleMessage(message: String) {
            val parts = message.split(":")
            when (parts[0]) {
                "ID" -> {
                    remoteId = parts[1]
                    if (isHost) {
                        clientConnections[remoteId!!] = this
                    }
                    Log.d(TAG, "Connected to $remoteId")
                }
                "POS" -> {
                    val id = parts[1]
                    val x = parts[2].toFloatOrNull() ?: 0f
                    val z = parts[3].toFloatOrNull() ?: 0f
                    val angle = parts[4].toFloatOrNull() ?: 0f
                    remotePlayers[id] = RemotePlayerData(x, z, angle, System.currentTimeMillis())

                    if (isHost) {
                        // Host relays this position to all other clients
                        val relayMsg = "POS:$id:$x:$z:$angle"
                        clientConnections.forEach { (cid, conn) ->
                            if (cid != id) conn.send(relayMsg)
                        }
                    }
                }
            }
        }

        fun close() {
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service registered: ${NsdServiceInfo.serviceName}")
        }
        override fun onRegistrationFailed(arg0: NsdServiceInfo, arg1: Int) {}
        override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
        override fun onUnregistrationFailed(arg0: NsdServiceInfo, arg1: Int) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Discovery started")
        }
        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "Service found: ${service.serviceName}")
            if (service.serviceType == SERVICE_TYPE && service.serviceName.contains("TouchGrass_")) {
                nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed: $errorCode")
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        connectToHost(serviceInfo)
                    }
                })
            }
        }
        override fun onServiceLost(service: NsdServiceInfo) {
            Log.d(TAG, "Service lost: ${service.serviceName}")
        }
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }
}
