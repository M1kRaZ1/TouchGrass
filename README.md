# TouchGrass

TouchGrass is an Android multiplayer project built around a shared 3D environment. Players can discover nearby devices and see each other in the same grass field.

The project is written in Kotlin and uses OpenGL ES for rendering, with Android's Network Service Discovery (NSD) handling nearby device discovery.

## How it works

The application is split into three main parts:

- **Game and UI:** `MainActivity.kt` brings everything together. It initializes the game, creates the OpenGL surface, updates the scene, and manages networking.
- **Networking:** `NetworkManager.kt` handles the discovery of nearby devices and the exchange of player positions.
- **Rendering:** The OpenGL renderer draws the grass field, the sun, and other players in the environment.

## Project structure

```text
app/src/main/java/ch/luca/touchgrass/
├── MainActivity.kt
├── NetworkManager.kt
├── MyGLSurfaceView.kt
├── MyGLRenderer.kt
├── Grass.kt
├── Sun.kt
└── RemotePlayerShape.kt
