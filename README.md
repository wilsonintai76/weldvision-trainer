<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# WeldVision Trainer 2.0

WeldVision Trainer 2.0 is a **Hybrid Edge-Cloud Welding Simulation Platform**. It fuses on-device computer vision and sensor telemetry to deliver real-time, zero-latency feedback ($< 16.6\text{ ms}$) to welding trainees, while seamlessly backing up high-frequency motion data for comprehensive 3D instructor replays in the browser.

## Features

- **On-Device AR Tracking:** Android application utilizing CameraX, AprilTags, and Gyroscope sensor fusion to compute 6-DOF (Degrees of Freedom) torch position and orientation.
- **Rigid Fixture Architecture:** AprilTags are permanently mounted on a fixed, rigid fixture frame, eliminating consumable tag waste and simplifying setup. The system automatically computes positional offsets for the inserted steel coupons.
- **Cloudflare Edge Ingestion:** Telemetry is asynchronously streamed to Cloudflare D1 (SQL Summary) and R2 (Raw time-series gzip).
- **Web Replay Dashboard:** A self-contained Three.js (WebGL) portal that parses the raw telemetry streams directly from Cloudflare R2 and reconstructs the student's 3D weld trajectory for granular grading and NDT overlay inspection.

## Repository Structure

```
weldvision-trainer/
├── app/                          # Android Trainer App (Trainee)
│   └── src/main/java/com/example/
│       ├── MainActivity.kt       # Jetpack Compose UI Entry
│       └── tracking/             # 6-DOF AprilTag + Gyro Sensor Fusion Engine
├── web-dashboard/                # Instructor Web Portal
│   ├── telemetry-stream.ts       # R2 gzip stream parser & WebGL helper
│   └── replay-viewer.html        # Interactive Three.js 3D replay demo
├── ARCHITECTURE.md               # Detailed system topology and mathematics
└── ...                           # Gradle build configuration files
```

## Running the Android App

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)

1. Open Android Studio.
2. Select **Open** and choose the `weldvision-trainer` directory.
3. Allow Android Studio to sync the Gradle build.
4. Create a `.env` file in the root project directory and set your `CLOUDFLARE_API_TOKEN`:
   ```env
   CLOUDFLARE_API_TOKEN=your_api_token_here
   ```
   *(See `.env.example` for reference)*
5. Connect an Android device (or emulator) and click **Run**.

## Running the Web Dashboard

The web dashboard is a browser-native vanilla JS application requiring zero build steps.

1. Simply open `web-dashboard/replay-viewer.html` in any modern web browser.
2. By default, it will fall back to a generated mock telemetry stream for offline development if the R2 bucket cannot be reached.

---
*For a deep dive into the coordinate systems, spatial boundary math, and Cloudflare database schemas, please refer to [ARCHITECTURE.md](./ARCHITECTURE.md).*
