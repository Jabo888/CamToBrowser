# CamToBrowser
Hardware-encoded H.264 stream from a mobile phone via WebSocket, played back on a small player page (WebCodecs) – with very low latency.

**Use your Android phone as a low-latency wireless camera for your PC – no plugin, no driver.**
Video and audio are streamed over your local network and played in a tiny web page that any
streaming software with a *Browser source* can show.

🇩🇪 [Deutsche Version weiter unten](#deutsch)

---

## English

### Features
- Hardware H.264 encoding, up to 4K, with audio (PCM)
- Very low latency (WebSocket + WebCodecs; automatic MediaSource fallback in embedded browsers)
- Full manual camera control: ISO, shutter speed, focus, exposure compensation, white balance, zoom, torch, AE/AWB lock
- Rotate and mirror the picture live in the player
- 6-digit PIN protection with rate limiting
- Runs in the background with the screen off (foreground service with a notification and a *Stop* action)
- Energy-saver mode to reduce heat
- Modern full-screen UI with side menu, 8 languages (de, en, zh, fr, es, it, pt, ja)
- No ads, no accounts, no cloud, no tracking

### How it works
```
Phone camera + mic ──► H.264 / PCM ──► WebSocket (port 8080) ──► player.html ──► Browser source
```
The app runs a small HTTP/WebSocket server. You download a self-contained `handycam-player.html`
from it once and add it as a *local file* in a Browser source of your streaming software.

### Usage
1. Install the app, grant camera, microphone and notification permissions.
2. Phone and PC must be on the same network. Open the **Connect** tab and note the address
   `http://<phone-ip>:8080/player.html`.
3. Open that address in the PC browser. If PIN protection is on, enter the PIN shown in the app.
   `handycam-player.html` is downloaded.
4. In your streaming software add a **Browser** source, tick **Local file**, choose the file and set
   width/height to the chosen resolution. Enable the option to control audio via the software.
5. Tune the camera in the app (Video / Image / Sound tabs). Rotation and mirroring are live.

Over USB: `adb forward tcp:8080 tcp:8080` and use `localhost` instead of the phone IP.

### Security notes
- The stream is **not encrypted** (plain HTTP/WS). The PIN only prevents casual access.
  Use trusted networks only, never public Wi-Fi.
- The downloaded player file contains the PIN. Don't share it. After generating a new PIN,
  download the file again.

### Build
Requirements: Android Studio Meerkat (2024.3.1) or newer, JDK 17.
- AGP 8.9.1, Gradle 8.11.1, Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00)
- `minSdk 26`, `targetSdk 36`, application ID `de.r7s.camtobrowser`

```
File ▸ Open ▸ CamToBrowser ▸ wait for Gradle sync ▸ Run
```
Release: `Build ▸ Generate Signed App Bundle / APK ▸ Android App Bundle`.

### Project structure
| File | Purpose |
|---|---|
| `MainActivity.kt` | Compose UI, tabs, permissions |
| `StreamService.kt` | Foreground service, wake/Wi-Fi lock, notification |
| `CameraStreamer.kt` | Camera2 pipeline and manual controls |
| `VideoEncoder.kt` / `AudioEncoder.kt` | MediaCodec H.264 / PCM capture |
| `StreamServer.kt` | HTTP + WebSocket server, PIN check, embedded player page |
| `PinStore.kt` | PIN generation and storage |
| `I18n.kt` | UI translations |

### Known limitations
- Latency in embedded browsers (MediaSource path) is higher than with WebCodecs in a regular browser.
- Not tested on all devices; behaviour of manual controls depends on the phone's Camera2 support.
- No virtual webcam driver for other programs; use your streaming software's virtual camera if needed.


---

## Deutsch

**Nutze dein Android-Handy als kabellose Kamera mit geringer Verzögerung für den PC – ohne Plugin und ohne Treiber.**
Bild und Ton werden im lokalen Netzwerk übertragen und in einer kleinen Webseite abgespielt, die jede
Streaming-Software mit *Browser-Quelle* anzeigen kann.

### Funktionen
- Hardware-H.264-Kodierung bis 4K mit Ton
- Sehr geringe Verzögerung (WebSocket + WebCodecs, automatischer MediaSource-Fallback)
- Volle manuelle Kamerasteuerung: ISO, Belichtungszeit, Fokus, Belichtungskorrektur, Weißabgleich, Zoom, Taschenlampe, AE/AWB-Sperre
- Bild live im Player drehen und spiegeln
- 6-stelliger PIN-Schutz mit Begrenzung der Fehlversuche
- Läuft im Hintergrund bei ausgeschaltetem Display (Vordergrunddienst mit Benachrichtigung und *Beenden*)
- Energiesparmodus gegen Überhitzung
- Modernes Vollbild-Design mit seitlichem Menü, 8 Sprachen
- Keine Werbung, keine Konten, keine Cloud, kein Tracking

### Benutzung
1. App installieren, Kamera-, Mikrofon- und Benachrichtigungsrechte erlauben.
2. Handy und PC müssen im selben Netzwerk sein. Im Reiter **Verbinden** steht die Adresse
   `http://<Handy-IP>:8080/player.html`.
3. Adresse im PC-Browser öffnen, bei aktivem PIN-Schutz die PIN aus der App eingeben –
   `handycam-player.html` wird heruntergeladen.
4. In der Streaming-Software eine **Browser**-Quelle hinzufügen, **Lokale Datei** anhaken, die Datei
   wählen, Breite/Höhe wie die Auflösung setzen und die Option zum Steuern des Audios über die Software aktivieren.
5. Kamera in der App einstellen (Reiter Video / Bild / Ton). Drehen und Spiegeln wirken sofort.

Per USB: `adb forward tcp:8080 tcp:8080` und `localhost` statt der Handy-IP verwenden.

### Sicherheit
- Die Übertragung ist **unverschlüsselt** (HTTP/WS). Die PIN verhindert nur zufälligen Zugriff.
  Nur in vertrauenswürdigen Netzwerken nutzen, nie in öffentlichem WLAN.
- Die heruntergeladene Player-Datei enthält die PIN – nicht weitergeben. Nach einer neuen PIN die Datei neu laden.


