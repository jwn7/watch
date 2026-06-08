# Watch

Wear OS watch app for collecting IMU data, creating walking sessions, uploading sensor batches to WatchSave, and receiving safety grade updates from the backend.

## Overview

- Creates a walking session with the WatchSave backend
- Collects accelerometer, gyroscope, and optional GPS data on the watch
- Uploads IMU batches to the backend over REST
- Subscribes to safety grade updates over STOMP WebSocket
- Shows foreground notification updates and haptic feedback for safety grade changes

## Backend

- REST base URL: [https://port-0-watchsave-mof8rht583b71611.sel3.cloudtype.app](https://port-0-watchsave-mof8rht583b71611.sel3.cloudtype.app)
- STOMP WebSocket URL: `wss://port-0-watchsave-mof8rht583b71611.sel3.cloudtype.app/ws`

## Current App Flow

1. The watch app creates a session with `POST /api/v1/sessions`
2. The app starts the local sensor collection service
3. IMU batches are uploaded with `POST /api/v1/sessions/{sessionId}/imu`
4. The app connects to STOMP and subscribes to `/topic/session/{sessionId}/grade`
5. The app receives safety grade updates from the backend
6. The app stops the session locally when the user presses stop

## Important Note

The current watch app uses:

- REST for IMU batch upload
- STOMP WebSocket for safety grade subscription

Even though the backend supports STOMP publish to `/app/session/{sessionId}/imu`, this app currently uploads batches with REST.

## API Summary

### 1. Create Session

`POST /api/v1/sessions`

Request:

```json
{
  "device_id": "watch-001"
}
```

Response example:

```json
{
  "device_id": "watch-001",
  "session_id": "d8118916-6320-4c87-b0dd-86c3f4b7c25c",
  "status": "ACTIVE",
  "started_at": "2026-04-27T09:12:45.639196659+09:00"
}
```

### 2. End Session

`POST /api/v1/sessions/{id}/end`

Response example:

```json
{
  "ended_at": "2026-04-27T09:13:26.339759744+09:00",
  "status": "COMPLETED",
  "session_id": "d8118916-6320-4c87-b0dd-86c3f4b7c25c"
}
```

### 3. Pause Session

`POST /api/v1/sessions/{id}/pause`

Response example:

```json
{
  "status": "PAUSED",
  "session_id": "0d7ae7ce-019c-4795-97ca-66e8368850e9"
}
```

### 4. Resume Session

`POST /api/v1/sessions/{id}/resume`

Response example:

```json
{
  "status": "ACTIVE",
  "session_id": "0d7ae7ce-019c-4795-97ca-66e8368850e9"
}
```

### 5. Upload IMU Batch

`POST /api/v1/sessions/{id}/imu`

Request example:

```json
{
  "samples": [
    {
      "ax": 0.11,
      "ay": 0.22,
      "az": 9.81,
      "gx": 0.01,
      "gy": 0.02,
      "gz": 0.03,
      "timestamp_ms": 1710000000000
    }
  ],
  "lat": 37.5665,
  "lng": 126.9780,
  "gps_accuracy_meters": 4.2
}
```

Response example:

```json
{
  "received": 1,
  "session_id": "d8118916-6320-4c87-b0dd-86c3f4b7c25c"
}
```

### 6. List Sessions

`GET /api/v1/sessions`

Response example:

```json
[
  {
    "id": "f1e8dfc0-8057-4bac-a8fd-d7d63a1cc0b9",
    "device_id": "unknown",
    "status": "ACTIVE",
    "started_at": "2026-04-26T13:13:35.029215Z",
    "ended_at": null,
    "total_points": 11,
    "summary_grade": null
  }
]
```

### 7. Get Session Detail

`GET /api/v1/sessions/{sessionId}`

Response example:

```json
{
  "session_id": "d8118916-6320-4c87-b0dd-86c3f4b7c25c",
  "device_id": "watch-001",
  "status": "ACTIVE",
  "started_at": "2026-04-27T00:12:45.639197Z",
  "ended_at": null,
  "total_points": 0,
  "summary_grade": null,
  "waypoints": []
}
```

### 8. Export One Session CSV

`GET /api/v1/sessions/{sessionId}/export`

Response:

- Content type: `text/csv;charset=UTF-8`

CSV header example:

```text
sequence_num,timestamp_ms,lat,lng,gps_accuracy_meters,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z
```

### 9. Export All Records CSV

`GET /api/v1/sessions/export`

### 10. Get Latest Safety Status

`GET /api/v1/safety/status?sessionId={sessionId}`

Response example:

```json
{
  "session_id": "d8118916-6320-4c87-b0dd-86c3f4b7c25c",
  "grade": "SAFE",
  "color_code": "#2ECC71",
  "confidence": 1.0,
  "ai_latency_ms": null,
  "sequence_num": 1,
  "timestamp_ms": 1777248803029
}
```

### 11. Get Full Safety Path

`GET /api/v1/safety/path/{sessionId}`

### 12. Health Check

`GET /actuator/health`

Response example:

```json
{
  "status": "UP",
  "groups": [
    "liveness",
    "readiness"
  ]
}
```

## STOMP WebSocket

Connect:

```text
wss://port-0-watchsave-mof8rht583b71611.sel3.cloudtype.app/ws
```

Subscribe destination:

```text
/topic/session/{sessionId}/grade
```

Publish destination supported by backend:

```text
/app/session/{sessionId}/imu
```

Safety grade message example:

```json
{
  "session_id": "d8118916-6320-4c87-b0dd-86c3f4b7c25c",
  "grade": "SAFE",
  "confidence": 1.0,
  "color_code": "#2ECC71",
  "ai_latency_ms": null,
  "sequence_num": 1,
  "timestamp_ms": 1777248803029
}
```

## Local Build

Build debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Generated APK:

- [app-debug.apk](C:/Users/ksyzi/IdeaProjects/issr-watch/app/build/outputs/apk/debug/app-debug.apk)

## Install On a Real Watch

### 1. Enable Developer Options on the Watch

- Open `Settings`
- Go to `About watch` or `About`
- Tap `Build number` several times
- Open `Developer options`
- Enable `ADB debugging`
- If needed, also enable `Wi-Fi debugging`

### 2. Check ADB From the Computer

```powershell
C:\Users\ksyzi\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
```

### 3. If You Install Over Wi-Fi

Use the pairing info shown on the watch:

```powershell
C:\Users\ksyzi\AppData\Local\Android\Sdk\platform-tools\adb.exe pair IP:PAIR_PORT
C:\Users\ksyzi\AppData\Local\Android\Sdk\platform-tools\adb.exe connect IP:ADB_PORT
C:\Users\ksyzi\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
```

### 4. Install the APK on the Watch

```powershell
C:\Users\ksyzi\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r C:\Users\ksyzi\IdeaProjects\issr-watch\app\build\outputs\apk\debug\app-debug.apk
```

### 5. Launch the App

```powershell
C:\Users\ksyzi\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am start -n com.issr.watch/.presentation.MainActivity
```

### 6. Check Logs if Something Fails

```powershell
C:\Users\ksyzi\AppData\Local\Android\Sdk\platform-tools\adb.exe logcat -s MainActivity ImuService StompClient AndroidRuntime
```

## Notes

- This repository is currently configured to talk to the deployed WatchSave backend by default
- Real watch testing is recommended after emulator verification because sensors, permissions, and connectivity can differ
- `pause/resume` APIs exist on the backend, but the current watch UI only exposes start and stop
