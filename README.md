# Ambulance Driver App

A native Android app used by ambulance drivers as part of an IoT-based traffic signal preemption system. The app starts a journey, streams the ambulance's live location over MQTT, and lets junction-side devices along the route grant the ambulance a green signal before it arrives.

This app is the **driver-facing mobile component** of a larger system (IoT junction device + backend + broker) that computes ETA, distance, and bearing to nearby junctions and preempts signals accordingly. This repo contains only the Android app.

## Features

- **Simple login** — driver signs in with an ambulance ID, username, and password (no broker details ever entered on-device)
- **Journey flow** — select the patient's medical severity, start the journey, select the destination hospital
- **Live tracking screen** — shows current latitude/longitude, a live-updating timestamp, and real-time MQTT connection status
- **Background location publishing** — GPS coordinates are pushed to the backend over MQTT every few seconds so junctions can calculate ETA
- **Resilient MQTT session handling** — clean reconnects with fresh client IDs to avoid broker-side session conflicts

## Tech stack

- **Language:** Kotlin
- **Build system:** Gradle (Kotlin DSL — `build.gradle.kts`)
- **Location:** Android `FusedLocationProviderClient`
- **Messaging:** MQTT (tested against [HiveMQ Cloud](https://www.hivemq.com/mqtt-cloud-broker/))

## Project structure

```
app/                                  Android app module (source, resources, manifest)
gradle/wrapper/                       Gradle wrapper
build.gradle.kts                      Top-level build config
settings.gradle.kts                   Module settings
mqtt-secrets.properties.example       Template for local MQTT broker credentials (see SETUP.md)
```

## Getting started

See [SETUP.md](SETUP.md) for full instructions on cloning, configuring your MQTT broker credentials, and running the app.

## Security note

Broker host, port, username, and password are supplied at build time via a local, gitignored `mqtt-secrets.properties` file and baked into the app — they are never entered or displayed on any screen by the driver.

## Status

This app is part of an ongoing college capstone project (also being written up as an IEEE conference paper). Interfaces and the MQTT message schema may change as the wider system evolves.

## License

Licensed under the [MIT License](LICENSE).
