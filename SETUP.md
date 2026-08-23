# Setup Guide

Instructions to get the Ambulance Driver App cloned, configured, and running.

## Prerequisites

- **Android Studio** (latest stable release)
- **JDK 17** (bundled with recent Android Studio versions)
- **Android SDK** with a recent platform installed via the SDK Manager
- A physical Android device or emulator with **Google Play services** (required for `FusedLocationProviderClient`)
- An **MQTT broker** you control — the app was built and tested against a free-tier [HiveMQ Cloud](https://www.hivemq.com/mqtt-cloud-broker/) cluster, but any broker reachable over TLS on port 8883 will work

## 1. Clone the repo

```bash
git clone https://github.com/Mk22v/Ambulance-driver-appv2.git
cd Ambulance-driver-appv2
```

## 2. Configure your MQTT broker credentials

The app reads broker credentials from a local, **gitignored** properties file so secrets never get committed.

```bash
cp mqtt-secrets.properties.example mqtt-secrets.properties
```

Open `mqtt-secrets.properties` and fill in your broker's details:

```properties
mqtt.host=YOUR_CLUSTER.s1.eu.hivemq.cloud
mqtt.port=8883
mqtt.username=YOUR_USERNAME
mqtt.password=YOUR_PASSWORD
```

If you're using HiveMQ Cloud: create a free cluster, add credentials under **Access Management**, and copy the cluster URL shown on the cluster's **Overview** tab into `mqtt.host`.

These values are baked into the app at build time — the driver never sees or enters them.

## 3. Open and build the project

- Open the cloned folder in Android Studio and let it sync Gradle, **or** build from the command line:

```bash
./gradlew assembleDebug
```

(On Windows, use `gradlew.bat assembleDebug`.)

## 4. Run the app

- Connect a physical device (with USB debugging enabled) or start an emulator with Google Play services
- Click **Run** in Android Studio, or install the built APK manually:

```bash
./gradlew installDebug
```

## 5. Grant permissions

On first launch, grant the location permission prompts (fine/background location) — the app cannot publish GPS updates without them.

## 6. Verify the MQTT connection

- Log in with a test ambulance ID/username/password (as provisioned in your backend's fleet lookup)
- Start a journey and check the tracking screen shows a **connected** status with a live-updating timestamp and coordinates
- Optionally, connect a tool like [MQTT Explorer](https://mqtt-explorer.com/) to your broker to confirm location messages are arriving on the expected topic

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Stuck on "connecting" / repeated reconnects | Wrong host/port/credentials in `mqtt-secrets.properties`, or broker not allowing your client ID/IP |
| No location updates | Location permission not granted, or GPS/location services disabled on the device |
| Build fails referencing `mqtt-secrets.properties` | You skipped step 2 — the file must exist locally (it's gitignored, so it won't be present after a fresh clone) |
