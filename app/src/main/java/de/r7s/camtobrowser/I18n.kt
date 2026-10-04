package de.r7s.camtobrowser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Einfache Übersetzungstabelle: de, en, zh, fr, es, it, pt, ja. */
object I18n {
    val codes = listOf("de", "en", "zh", "fr", "es", "it", "pt", "ja")
    val names = listOf("Deutsch", "English", "中文", "Français", "Español", "Italiano", "Português", "日本語")

    var lang by mutableStateOf("en")
    private var ready = false

    /** Gespeicherte Sprache laden; sonst Gerätesprache (falls unterstützt), sonst Englisch. */
    fun init(ctx: Context) {
        if (ready) return
        ready = true
        val saved = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("lang", null)
        val dev = Locale.getDefault().language
        lang = when {
            saved != null && saved in codes -> saved
            dev in codes -> dev
            else -> "en"
        }
    }

    fun set(ctx: Context, code: String) {
        lang = code
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putString("lang", code).apply()
    }

    fun index() = codes.indexOf(lang).coerceAtLeast(0)

    fun t(key: String): String = TABLE[key]?.getOrNull(index()) ?: key

    private val TABLE: Map<String, Array<String>> = mapOf(
        "notif_group" to arrayOf("Benachrichtigung", "Notification", "通知", "Notification", "Notificación", "Notifica", "Notificação", "通知"),
        "notif_on" to arrayOf("Aktiv – der laufende Stream ist in der Statusleiste sichtbar", "On – the running stream is shown in the status bar", "已开启：运行中的推流会显示在状态栏", "Activée : le flux en cours s'affiche dans la barre d'état", "Activada: la transmisión en curso aparece en la barra de estado", "Attiva: lo stream in corso è visibile nella barra di stato", "Ativada: a transmissão em andamento aparece na barra de status", "オン：配信中はステータスバーに表示されます"),
        "notif_off" to arrayOf("Ausgeschaltet", "Turned off", "已关闭", "Désactivée", "Desactivada", "Disattivata", "Desativada", "オフ"),
        "notif_off_hint" to arrayOf("Ohne Benachrichtigung siehst du nicht, dass der Stream im Hintergrund läuft, und kannst ihn nicht darüber beenden.", "Without the notification you can't see that the stream is running in the background, and you can't stop it from there.", "没有通知，你就看不到推流正在后台运行，也无法通过通知停止它。", "Sans notification, vous ne voyez pas que le flux tourne en arrière-plan et ne pouvez pas l'arrêter depuis celle-ci.", "Sin la notificación no verás que la transmisión sigue en segundo plano ni podrás detenerla desde ahí.", "Senza la notifica non vedi che lo stream è attivo in background e non puoi terminarlo da lì.", "Sem a notificação você não vê que a transmissão está em segundo plano e não pode encerrá-la por ela.", "通知がないと、バックグラウンドで配信中かどうか分からず、通知から停止することもできません。"),
        "notif_open" to arrayOf("Benachrichtigungs-Einstellungen öffnen", "Open notification settings", "打开通知设置", "Ouvrir les réglages de notification", "Abrir ajustes de notificaciones", "Apri impostazioni notifiche", "Abrir configurações de notificação", "通知設定を開く"),
        "pin_group" to arrayOf("Zugriffsschutz", "Access protection", "访问保护", "Protection d'accès", "Protección de acceso", "Protezione dell'accesso", "Proteção de acesso", "アクセス保護"),
        "pin_switch" to arrayOf("PIN-Schutz", "PIN protection", "PIN 保护", "Protection par PIN", "Protección con PIN", "Protezione con PIN", "Proteção por PIN", "PIN 保護"),
        "pin_switch_sub" to arrayOf("Nur wer die PIN kennt, kann den Stream sehen", "Only people who know the PIN can watch the stream", "只有知道 PIN 的人才能观看", "Seules les personnes connaissant le PIN peuvent voir le flux", "Solo quien conozca el PIN puede ver la transmisión", "Solo chi conosce il PIN può vedere lo stream", "Só quem souber o PIN pode ver a transmissão", "PIN を知っている人だけが視聴できます"),
        "pin_new" to arrayOf("Neue PIN erzeugen", "Generate new PIN", "生成新 PIN", "Générer un nouveau PIN", "Generar PIN nuevo", "Genera nuovo PIN", "Gerar novo PIN", "新しい PIN を生成"),
        "pin_hint" to arrayOf("Beim Öffnen der Adresse im PC-Browser fragt eine Seite nach dieser PIN. Danach ist sie in der heruntergeladenen Datei enthalten – in der Streaming-Software musst du nichts eingeben. Nach einer neuen PIN die Datei erneut laden.", "When you open the address in the PC browser, a page asks for this PIN. It is then built into the downloaded file – nothing to type in the streaming software. After a new PIN, download the file again.", "在电脑浏览器中打开地址时，页面会要求输入此 PIN，之后它会包含在下载的文件中，无需在直播软件里输入。生成新 PIN 后请重新下载文件。", "À l'ouverture de l'adresse dans le navigateur du PC, une page demande ce PIN. Il est ensuite intégré au fichier téléchargé : rien à saisir dans le logiciel de streaming. Après un nouveau PIN, retéléchargez le fichier.", "Al abrir la dirección en el navegador del PC, una página pide este PIN. Luego queda incluido en el archivo descargado: no hay que escribir nada en el software de streaming. Tras un PIN nuevo, vuelve a descargar el archivo.", "Aprendo l'indirizzo nel browser del PC, una pagina chiede questo PIN. Poi è incluso nel file scaricato: non serve digitare nulla nel software di streaming. Dopo un nuovo PIN, scarica di nuovo il file.", "Ao abrir o endereço no navegador do PC, uma página pede este PIN. Depois ele fica incluído no arquivo baixado: não é preciso digitar nada no software de streaming. Após um novo PIN, baixe o arquivo novamente.", "PC のブラウザでアドレスを開くと、この PIN を尋ねるページが表示されます。PIN はダウンロードしたファイルに含まれるので、配信ソフトで入力する必要はありません。新しい PIN を生成したら、ファイルを再ダウンロードしてください。"),
        "pin_off_hint" to arrayOf("Ohne PIN kann jeder im selben Netzwerk, der die Adresse kennt, das Kamerabild sehen.", "Without a PIN, anyone on the same network who knows the address can see the camera image.", "没有 PIN 时，同一网络中知道地址的任何人都能看到摄像头画面。", "Sans PIN, toute personne du même réseau connaissant l'adresse peut voir l'image de la caméra.", "Sin PIN, cualquiera en la misma red que conozca la dirección puede ver la imagen de la cámara.", "Senza PIN, chiunque nella stessa rete conosca l'indirizzo può vedere l'immagine della fotocamera.", "Sem PIN, qualquer pessoa na mesma rede que conheça o endereço pode ver a imagem da câmera.", "PIN がないと、同じネットワーク内でアドレスを知っている人は誰でもカメラ映像を見られます。"),
        "pin_note" to arrayOf("Die PIN schützt vor zufälligem Zugriff im WLAN. Die Übertragung selbst ist nicht verschlüsselt – nutze die App nur in Netzwerken, denen du vertraust.", "The PIN protects against casual access on Wi-Fi. The transmission itself is not encrypted – use the app only on networks you trust.", "PIN 可防止他人在 Wi-Fi 中随意访问。传输本身未加密，请仅在可信网络中使用。", "Le PIN protège contre un accès fortuit sur le Wi-Fi. La transmission n'est pas chiffrée : n'utilisez l'app que sur des réseaux de confiance.", "El PIN protege contra accesos casuales en la Wi-Fi. La transmisión no está cifrada: usa la app solo en redes de confianza.", "Il PIN protegge da accessi occasionali sul Wi-Fi. La trasmissione non è cifrata: usa l'app solo su reti attendibili.", "O PIN protege contra acessos casuais no Wi-Fi. A transmissão não é criptografada: use o app apenas em redes confiáveis.", "PIN は Wi-Fi 上での不用意なアクセスを防ぎます。通信自体は暗号化されていないため、信頼できるネットワークでのみ使用してください。"),
        "iso" to arrayOf("ISO", "ISO", "ISO", "ISO", "ISO", "ISO", "ISO", "ISO"),
        "status_live" to arrayOf("Verbunden", "Connected", "已连接", "Connecté", "Conectado", "Connesso", "Conectado", "接続済み"),
        "status_wait" to arrayOf("Wartet auf Player", "Waiting for player", "等待播放器", "En attente du lecteur", "Esperando al reproductor", "In attesa del player", "Aguardando o player", "プレーヤーを待機中"),
        "tab_connect" to arrayOf("Verbinden", "Connect", "连接", "Connexion", "Conexión", "Collega", "Conexão", "接続"),
        "tab_video" to arrayOf("Video", "Video", "视频", "Vidéo", "Vídeo", "Video", "Vídeo", "ビデオ"),
        "tab_image" to arrayOf("Bild", "Image", "画面", "Image", "Imagen", "Immagine", "Imagem", "画質"),
        "tab_sound" to arrayOf("Ton", "Audio", "声音", "Son", "Sonido", "Audio", "Áudio", "音声"),
        "tab_more" to arrayOf("Mehr", "More", "更多", "Plus", "Más", "Altro", "Mais", "その他"),
        "cam_need" to arrayOf("Kamera-Zugriff benötigt", "Camera access required", "需要相机权限", "Accès à la caméra requis", "Se necesita acceso a la cámara", "Serve l'accesso alla fotocamera", "Acesso à câmera necessário", "カメラへのアクセスが必要です"),
        "cam_need_sub" to arrayOf("Ohne Berechtigung kann kein Bild gestreamt werden.", "Without permission no image can be streamed.", "没有权限就无法传输画面。", "Sans autorisation, aucune image ne peut être diffusée.", "Sin permiso no se puede transmitir imagen.", "Senza autorizzazione non è possibile trasmettere l'immagine.", "Sem permissão não é possível transmitir imagem.", "権限がないと映像を配信できません。"),
        "grant" to arrayOf("Berechtigung erteilen", "Grant permission", "授予权限", "Accorder l'autorisation", "Conceder permiso", "Concedi autorizzazione", "Conceder permissão", "権限を許可"),
        "starting" to arrayOf("Starte …", "Starting …", "正在启动 …", "Démarrage …", "Iniciando …", "Avvio …", "Iniciando …", "起動中 …"),
        "saver" to arrayOf("Energiesparmodus", "Power saving mode", "省电模式", "Mode économie d'énergie", "Modo de ahorro de energía", "Modalità risparmio energia", "Modo de economia de energia", "省電力モード"),
        "saver_sub" to arrayOf("Display dunkel, Stream läuft weiter", "Screen dark, stream keeps running", "屏幕变暗，推流继续", "Écran sombre, le flux continue", "Pantalla oscura, la transmisión continúa", "Schermo scuro, lo stream continua", "Tela escura, a transmissão continua", "画面は暗くなり、配信は続きます"),
        "saver_overlay" to arrayOf("Der Stream läuft weiter · Antippen zum Aufwecken", "The stream keeps running · Tap to wake", "推流仍在继续 · 点按唤醒", "Le flux continue · Touchez pour réactiver", "La transmisión continúa · Toca para despertar", "Lo stream continua · Tocca per riattivare", "A transmissão continua · Toque para ativar", "配信は続いています · タップして復帰"),
        "conn_ok" to arrayOf("Player verbunden", "Player connected", "播放器已连接", "Lecteur connecté", "Reproductor conectado", "Player connesso", "Player conectado", "プレーヤーに接続済み"),
        "conn_wait" to arrayOf("Wartet auf Player …", "Waiting for player …", "正在等待播放器 …", "En attente du lecteur …", "Esperando al reproductor …", "In attesa del player …", "Aguardando o player …", "プレーヤーを待機中 …"),
        "step1" to arrayOf("1  Player-Datei laden", "1  Download player file", "1  下载播放器文件", "1  Télécharger le lecteur", "1  Descargar el reproductor", "1  Scarica il player", "1  Baixar o player", "1  プレーヤーをダウンロード"),
        "step1_hint" to arrayOf("Diese Adresse im Browser am PC öffnen. Die Datei wird heruntergeladen.", "Open this address in a browser on your PC. The file will be downloaded.", "在电脑浏览器中打开此地址，文件将自动下载。", "Ouvrez cette adresse dans le navigateur de votre PC. Le fichier sera téléchargé.", "Abre esta dirección en el navegador del PC. Se descargará el archivo.", "Apri questo indirizzo nel browser del PC. Il file verrà scaricato.", "Abra este endereço no navegador do PC. O arquivo será baixado.", "PC のブラウザでこのアドレスを開くと、ファイルがダウンロードされます。"),
        "copy" to arrayOf("Adresse kopieren", "Copy address", "复制地址", "Copier l'adresse", "Copiar dirección", "Copia indirizzo", "Copiar endereço", "アドレスをコピー"),
        "copied" to arrayOf("Kopiert ✓", "Copied ✓", "已复制 ✓", "Copié ✓", "Copiado ✓", "Copiato ✓", "Copiado ✓", "コピーしました ✓"),
        "step2" to arrayOf("2  In Streaming-Software einbinden", "2  Add to your streaming software", "2  添加到直播软件", "2  Ajouter dans le logiciel de streaming", "2  Añadir en el software de streaming", "2  Aggiungi nel software di streaming", "2  Adicionar no software de streaming", "2  配信ソフトに追加"),
        "step2_h1" to arrayOf("Quelle „Browser“ hinzufügen → „Lokale Datei“ anhaken → heruntergeladene Datei wählen.", "Add a “Browser” source → tick “Local file” → choose the downloaded file.", "添加“浏览器”来源 → 勾选“本地文件” → 选择已下载的文件。", "Ajoutez une source « Navigateur » → cochez « Fichier local » → choisissez le fichier téléchargé.", "Añade una fuente «Navegador» → marca «Archivo local» → elige el archivo descargado.", "Aggiungi una sorgente «Browser» → spunta «File locale» → scegli il file scaricato.", "Adicione uma fonte «Navegador» → marque «Arquivo local» → escolha o arquivo baixado.", "「ブラウザ」ソースを追加 →「ローカルファイル」にチェック → ダウンロードしたファイルを選択。"),
        "step2_h2" to arrayOf("Breite/Höhe wie die Auflösung setzen und die Option zum Steuern des Audios über die Software aktivieren.", "Set width/height to the resolution and enable the option to control audio via the software.", "宽度/高度设为与分辨率一致，并启用通过软件控制音频的选项。", "Réglez largeur/hauteur sur la résolution et activez l'option de contrôle de l'audio via le logiciel.", "Pon el ancho/alto igual a la resolución y activa la opción de controlar el audio mediante el software.", "Imposta larghezza/altezza uguali alla risoluzione e attiva l'opzione per controllare l'audio tramite il software.", "Defina largura/altura como a resolução e ative a opção de controlar o áudio pelo software.", "幅/高さを解像度に合わせ、ソフトウェア経由で音声を制御するオプションを有効にします。"),
        "rot_title" to arrayOf("Bild im Player drehen", "Rotate image in player", "旋转播放器画面", "Pivoter l'image dans le lecteur", "Girar la imagen en el reproductor", "Ruota l'immagine nel player", "Girar a imagem no player", "プレーヤーの画像を回転"),
        "mirror" to arrayOf("Spiegeln", "Mirror", "镜像", "Miroir", "Espejo", "Specchia", "Espelhar", "反転"),
        "mirror_sub" to arrayOf("Hilfreich bei der Frontkamera", "Useful for the front camera", "适用于前置摄像头", "Utile pour la caméra avant", "Útil para la cámara frontal", "Utile per la fotocamera anteriore", "Útil para a câmera frontal", "フロントカメラに便利"),
        "rot_hint" to arrayOf("Wirkt sofort im laufenden Player – die Datei muss nicht neu geladen werden.", "Applies instantly to the running player – no need to reload the file.", "立即对正在运行的播放器生效，无需重新加载文件。", "S'applique immédiatement au lecteur en cours – inutile de recharger le fichier.", "Se aplica al instante en el reproductor activo; no hace falta recargar el archivo.", "Si applica subito al player in esecuzione: non serve ricaricare il file.", "Aplica-se imediatamente ao player em execução; não é preciso recarregar o arquivo.", "実行中のプレーヤーにすぐ反映されます。ファイルの再読み込みは不要です。"),
        "usb_hint" to arrayOf("Per USB: „adb forward tcp:8080 tcp:8080“, dann in der Adresse localhost statt der IP.", "Via USB: “adb forward tcp:8080 tcp:8080”, then use localhost instead of the IP in the address.", "通过 USB：执行“adb forward tcp:8080 tcp:8080”，然后在地址中用 localhost 代替 IP。", "Par USB : « adb forward tcp:8080 tcp:8080 », puis utilisez localhost au lieu de l'IP.", "Por USB: «adb forward tcp:8080 tcp:8080» y usa localhost en lugar de la IP.", "Via USB: «adb forward tcp:8080 tcp:8080», poi usa localhost al posto dell'IP.", "Via USB: «adb forward tcp:8080 tcp:8080» e use localhost no lugar do IP.", "USB の場合：「adb forward tcp:8080 tcp:8080」を実行し、アドレスの IP を localhost に置き換えます。"),
        "stop" to arrayOf("Stream beenden", "Stop stream", "停止推流", "Arrêter le flux", "Detener transmisión", "Termina stream", "Encerrar transmissão", "配信を停止"),
        "camera" to arrayOf("Kamera", "Camera", "摄像头", "Caméra", "Cámara", "Fotocamera", "Câmera", "カメラ"),
        "rear" to arrayOf("Hinten", "Rear", "后置", "Arrière", "Trasera", "Posteriore", "Traseira", "背面"),
        "front" to arrayOf("Vorne", "Front", "前置", "Avant", "Frontal", "Anteriore", "Frontal", "前面"),
        "resolution" to arrayOf("Auflösung", "Resolution", "分辨率", "Résolution", "Resolución", "Risoluzione", "Resolução", "解像度"),
        "framerate" to arrayOf("Bildrate", "Frame rate", "帧率", "Fréquence d'images", "Velocidad de fotogramas", "Frequenza fotogrammi", "Taxa de quadros", "フレームレート"),
        "bitrate" to arrayOf("Bitrate", "Bitrate", "码率", "Débit", "Tasa de bits", "Bitrate", "Taxa de bits", "ビットレート"),
        "bitrate_hint" to arrayOf("Weniger = kühler und flüssiger im WLAN, mehr = schärfer.", "Lower = cooler and smoother over Wi-Fi, higher = sharper.", "越低越凉爽、Wi-Fi 下越流畅；越高越清晰。", "Moins = plus frais et plus fluide en Wi-Fi, plus = plus net.", "Menos = más fresco y fluido por Wi-Fi, más = más nítido.", "Meno = più fresco e fluido via Wi-Fi, più = più nitido.", "Menos = mais frio e fluido no Wi-Fi, mais = mais nítido.", "低いほど発熱が少なく Wi-Fi でも滑らか、高いほど高精細です。"),
        "zoom" to arrayOf("Zoom", "Zoom", "变焦", "Zoom", "Zoom", "Zoom", "Zoom", "ズーム"),
        "magnification" to arrayOf("Vergrößerung", "Magnification", "放大倍数", "Grossissement", "Aumento", "Ingrandimento", "Ampliação", "倍率"),
        "exposure" to arrayOf("Belichtung", "Exposure", "曝光", "Exposition", "Exposición", "Esposizione", "Exposição", "露出"),
        "auto" to arrayOf("Auto", "Auto", "自动", "Auto", "Auto", "Auto", "Auto", "自動"),
        "manual" to arrayOf("Manuell", "Manual", "手动", "Manuel", "Manual", "Manuale", "Manual", "手動"),
        "shutter" to arrayOf("Belichtungszeit", "Shutter speed", "快门速度", "Vitesse d'obturation", "Velocidad de obturación", "Tempo di posa", "Velocidade do obturador", "シャッタースピード"),
        "ev" to arrayOf("Helligkeit (EV)", "Brightness (EV)", "亮度 (EV)", "Luminosité (EV)", "Brillo (EV)", "Luminosità (EV)", "Brilho (EV)", "明るさ (EV)"),
        "ae_lock" to arrayOf("Belichtung sperren", "Lock exposure", "锁定曝光", "Verrouiller l'exposition", "Bloquear exposición", "Blocca esposizione", "Bloquear exposição", "露出をロック"),
        "ae_lock_sub" to arrayOf("Hält die aktuelle Helligkeit fest", "Holds the current brightness", "保持当前亮度", "Maintient la luminosité actuelle", "Mantiene el brillo actual", "Mantiene la luminosità attuale", "Mantém o brilho atual", "現在の明るさを固定"),
        "focus" to arrayOf("Fokus", "Focus", "对焦", "Mise au point", "Enfoque", "Messa a fuoco", "Foco", "フォーカス"),
        "distance" to arrayOf("Distanz", "Distance", "距离", "Distance", "Distancia", "Distanza", "Distância", "距離"),
        "wb" to arrayOf("Weißabgleich", "White balance", "白平衡", "Balance des blancs", "Balance de blancos", "Bilanciamento del bianco", "Balanço de branco", "ホワイトバランス"),
        "wb_auto" to arrayOf("Auto", "Auto", "自动", "Auto", "Auto", "Auto", "Auto", "自動"),
        "wb_daylight" to arrayOf("Tageslicht", "Daylight", "日光", "Lumière du jour", "Luz del día", "Luce diurna", "Luz do dia", "昼光"),
        "wb_cloudy" to arrayOf("Bewölkt", "Cloudy", "阴天", "Nuageux", "Nublado", "Nuvoloso", "Nublado", "曇り"),
        "wb_incandescent" to arrayOf("Glühlampe", "Incandescent", "白炽灯", "Incandescent", "Incandescente", "Incandescenza", "Incandescente", "白熱灯"),
        "wb_fluorescent" to arrayOf("Leuchtstoff", "Fluorescent", "荧光灯", "Fluorescent", "Fluorescente", "Fluorescente", "Fluorescente", "蛍光灯"),
        "wb_shade" to arrayOf("Schatten", "Shade", "阴影", "Ombre", "Sombra", "Ombra", "Sombra", "日陰"),
        "wb_lock" to arrayOf("Weißabgleich sperren", "Lock white balance", "锁定白平衡", "Verrouiller la balance des blancs", "Bloquear balance de blancos", "Blocca bilanciamento", "Bloquear balanço de branco", "ホワイトバランスをロック"),
        "wb_lock_sub" to arrayOf("Hält die aktuellen Farben fest", "Holds the current colors", "保持当前色彩", "Maintient les couleurs actuelles", "Mantiene los colores actuales", "Mantiene i colori attuali", "Mantém as cores atuais", "現在の色味を固定"),
        "locked" to arrayOf("Gesperrt – zum Ändern die Sperre aufheben.", "Locked – unlock to change.", "已锁定，解锁后才能更改。", "Verrouillé – déverrouillez pour modifier.", "Bloqueado: desbloquea para cambiar.", "Bloccato: sblocca per modificare.", "Bloqueado: desbloqueie para alterar.", "ロック中：変更するにはロックを解除してください。"),
        "torch" to arrayOf("Lampe", "Torch", "手电筒", "Lampe torche", "Linterna", "Torcia", "Lanterna", "ライト"),
        "torch_sub" to arrayOf("Taschenlampe der Rückkamera", "Flashlight of the rear camera", "后置摄像头的闪光灯", "Lampe de poche de la caméra arrière", "Linterna de la cámara trasera", "Torcia della fotocamera posteriore", "Lanterna da câmera traseira", "背面カメラのライト"),
        "mic" to arrayOf("Mikrofon", "Microphone", "麦克风", "Microphone", "Micrófono", "Microfono", "Microfone", "マイク"),
        "audio_send" to arrayOf("Ton übertragen", "Send audio", "传输声音", "Transmettre le son", "Transmitir sonido", "Trasmetti audio", "Transmitir áudio", "音声を送信"),
        "audio_sub" to arrayOf("Wird im Player abgespielt", "Played in the player", "在播放器中播放", "Lu dans le lecteur", "Se reproduce en el reproductor", "Riprodotto nel player", "Reproduzido no player", "プレーヤーで再生されます"),
        "audio_perm" to arrayOf("Mikrofon-Berechtigung erteilen und den Schalter danach erneut aktivieren.", "Grant the microphone permission, then switch this on again.", "请授予麦克风权限，然后重新打开此开关。", "Accordez l'autorisation du microphone, puis réactivez l'option.", "Concede el permiso del micrófono y vuelve a activar el interruptor.", "Concedi l'autorizzazione al microfono, poi riattiva l'interruttore.", "Conceda a permissão do microfone e ative a opção novamente.", "マイクの権限を許可してから、もう一度オンにしてください。"),
        "energy" to arrayOf("Energie & Wärme", "Power & heat", "省电与散热", "Énergie et chaleur", "Energía y calor", "Energia e calore", "Energia e calor", "省電力と発熱"),
        "energy_hint1" to arrayOf("Das Display lässt sich auch einfach mit der Power-Taste ausschalten – der Stream läuft im Hintergrund weiter. Das spart am meisten Wärme.", "You can also simply switch the screen off with the power button – the stream keeps running in the background. This saves the most heat.", "也可以直接用电源键关闭屏幕，推流会在后台继续，这样最省热。", "Vous pouvez aussi éteindre l'écran avec le bouton d'alimentation : le flux continue en arrière-plan. C'est ce qui limite le plus la chaleur.", "También puedes apagar la pantalla con el botón de encendido: la transmisión sigue en segundo plano. Es lo que más calor ahorra.", "Puoi anche spegnere lo schermo con il tasto di accensione: lo stream continua in background. È ciò che riduce di più il calore.", "Você também pode desligar a tela com o botão de energia: a transmissão continua em segundo plano. É o que mais reduz o calor.", "電源ボタンで画面を消すだけでも、配信はバックグラウンドで続きます。発熱を最も抑えられます。"),
        "energy_hint2" to arrayOf("Außerdem hilft: 1080p statt 4K, 30 statt 60 fps, niedrigere Bitrate.", "Also helps: 1080p instead of 4K, 30 instead of 60 fps, lower bitrate.", "此外：使用 1080p 而非 4K、30 帧而非 60 帧、降低码率。", "Aussi utile : 1080p au lieu de 4K, 30 au lieu de 60 i/s, débit plus faible.", "También ayuda: 1080p en lugar de 4K, 30 en lugar de 60 fps, menor tasa de bits.", "Aiutano anche: 1080p invece di 4K, 30 invece di 60 fps, bitrate più basso.", "Também ajuda: 1080p em vez de 4K, 30 em vez de 60 fps, taxa de bits menor.", "他にも、4K ではなく 1080p、60 fps ではなく 30 fps、低めのビットレートが有効です。"),
        "language" to arrayOf("Sprache", "Language", "语言", "Langue", "Idioma", "Lingua", "Idioma", "言語"),
        "notif_title" to arrayOf("Kamera-Stream läuft", "Camera stream is running", "摄像头推流正在运行", "Flux caméra en cours", "Transmisión de cámara activa", "Stream fotocamera attivo", "Transmissão da câmera ativa", "カメラ配信中"),
        "notif_text" to arrayOf("Zum Öffnen tippen", "Tap to open", "点按打开", "Touchez pour ouvrir", "Toca para abrir", "Tocca per aprire", "Toque para abrir", "タップして開く"),
        "notif_stop" to arrayOf("Beenden", "Stop", "停止", "Arrêter", "Detener", "Termina", "Encerrar", "停止"),
        "err_h264" to arrayOf("H.264 bei {0}×{1} @ {2} fps nicht möglich: {3}", "H.264 at {0}×{1} @ {2} fps is not possible: {3}", "无法以 {0}×{1} @ {2} fps 使用 H.264：{3}", "H.264 à {0}×{1} @ {2} i/s impossible : {3}", "H.264 a {0}×{1} @ {2} fps no es posible: {3}", "H.264 a {0}×{1} @ {2} fps non è possibile: {3}", "H.264 a {0}×{1} @ {2} fps não é possível: {3}", "{0}×{1} @ {2} fps での H.264 は利用できません：{3}"),
        "err_audio" to arrayOf("Ton nicht möglich: {0}", "Audio not possible: {0}", "无法使用声音：{0}", "Son impossible : {0}", "Sonido no disponible: {0}", "Audio non disponibile: {0}", "Áudio indisponível: {0}", "音声を利用できません：{0}"),
        "err_cam" to arrayOf("Kamera-Fehler: {0}", "Camera error: {0}", "相机错误：{0}", "Erreur de caméra : {0}", "Error de cámara: {0}", "Errore fotocamera: {0}", "Erro da câmera: {0}", "カメラエラー：{0}"),
        "err_camcfg" to arrayOf("Kamera-Konfiguration bei {0}×{1} @ {2} fps fehlgeschlagen. Bitte niedrigere Auflösung/Bildrate wählen.", "Camera configuration failed at {0}×{1} @ {2} fps. Please choose a lower resolution/frame rate.", "在 {0}×{1} @ {2} fps 下相机配置失败，请选择更低的分辨率/帧率。", "La configuration de la caméra a échoué à {0}×{1} @ {2} i/s. Choisissez une résolution/fréquence plus basse.", "Falló la configuración de la cámara a {0}×{1} @ {2} fps. Elige una resolución/velocidad menor.", "Configurazione fotocamera non riuscita a {0}×{1} @ {2} fps. Scegli una risoluzione/frequenza inferiore.", "Falha na configuração da câmera a {0}×{1} @ {2} fps. Escolha uma resolução/taxa menor.", "{0}×{1} @ {2} fps でカメラの設定に失敗しました。解像度かフレームレートを下げてください。"),
    )
}

/** Übersetzt einen Schlüssel; {0}, {1}, … werden durch die Argumente ersetzt. */
fun tr(key: String, vararg args: Any): String {
    var s = I18n.t(key)
    args.forEachIndexed { i, a -> s = s.replace("{" + i + "}", a.toString()) }
    return s
}
