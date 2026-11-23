package com.cengiz718.apkuetici

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

const val CURRENT_APP_VERSION = "8.0"

data class ChatMessage(val role: String, val content: String)

class NativeBridge(private val context: Context) {
    @JavascriptInterface
    fun toast(msg: String) {
        (context as? ComponentActivity)?.runOnUiThread {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun copyText(text: String) {
        (context as? ComponentActivity)?.runOnUiThread {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Kopyalanan", text))
            Toast.makeText(context, "Panoya kopyalandı", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun shareText(text: String) {
        (context as? ComponentActivity)?.runOnUiThread {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "Paylaş"))
        }
    }
}

object BuiltInTemplates {
    fun mediaStudioTemplate(): String = """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            * { box-sizing: border-box; }
            body {
              font-family: system-ui, -apple-system, sans-serif;
              margin: 0;
              padding: 14px;
              background: radial-gradient(circle at top right, #1e1b4b, #090d16 55%, #05070d);
              color: #f8fafc;
              min-height: 100vh;
            }
            .glass {
              background: rgba(30, 41, 59, 0.72);
              backdrop-filter: blur(12px);
              border: 1px solid rgba(148, 163, 184, 0.22);
              border-radius: 16px;
              padding: 14px;
              margin-bottom: 14px;
              box-shadow: 0 10px 25px rgba(0, 0, 0, 0.35);
            }
            .title {
              margin: 0 0 8px 0;
              font-size: 16px;
              font-weight: 700;
              background: linear-gradient(90deg, #38bdf8, #a855f7, #f472b6);
              -webkit-background-clip: text;
              -webkit-text-fill-color: transparent;
            }
            input, textarea, select {
              width: 100%;
              padding: 10px 12px;
              margin-bottom: 10px;
              border-radius: 12px;
              border: 1px solid rgba(148, 163, 184, 0.25);
              background: rgba(15, 23, 42, 0.85);
              color: #f8fafc;
              font-size: 13px;
            }
            .btn-row { display: flex; gap: 8px; flex-wrap: wrap; }
            button {
              flex: 1;
              min-width: 110px;
              border: none;
              border-radius: 12px;
              padding: 10px 14px;
              font-weight: 700;
              font-size: 13px;
              color: #fff;
              background: linear-gradient(135deg, #2563eb, #7c3aed);
              cursor: pointer;
            }
            button.secondary { background: rgba(51, 65, 85, 0.9); }
            img, video, audio {
              width: 100%;
              border-radius: 14px;
              margin-top: 10px;
              background: #020617;
              border: 1px solid rgba(56, 189, 248, 0.25);
            }
            img { max-height: 260px; object-fit: cover; }
            video { max-height: 230px; }
            .note-item {
              background: rgba(15, 23, 42, 0.75);
              border-left: 3px solid #38bdf8;
              border-radius: 10px;
              padding: 10px;
              margin-top: 8px;
              font-size: 13px;
            }
          </style>
        </head>
        <body>
          <div class="glass">
            <h3 class="title">Görsel Üretici & Galeri</h3>
            <input id="imgPrompt" placeholder="Görsel tarifi yaz (ör. modern stüdyo, gün batımı bahçe)..." />
            <div class="btn-row">
              <button onclick="genImage()">Görsel Üret</button>
              <button class="secondary" onclick="shareImgUrl()">Paylaş</button>
            </div>
            <img id="genImg" alt="Üretilen görsel burada görünür" />
          </div>

          <div class="glass">
            <h3 class="title">Müzik & Video Oynatıcı</h3>
            <input id="mediaUrl" placeholder="Müzik (MP3/OGG) veya Video / M3U8 URL yapıştır..." />
            <div class="btn-row">
              <button onclick="playAudio()">Müzik / Ses Çal</button>
              <button onclick="playVideo()">Video / Yayın Aç</button>
            </div>
            <audio id="audioBox" controls></audio>
            <video id="videoBox" controls playsinline></video>
          </div>

          <div class="glass">
            <h3 class="title">Hızlı Not & Fikir Defteri</h3>
            <input id="quickNote" placeholder="Yeni fikir veya not ekle..." />
            <button onclick="addNote()">Kaydet</button>
            <div id="notesList"></div>
          </div>

          <script>
            let lastImgUrl = "";
            let notes = JSON.parse(localStorage.getItem("studio_notes") || "[]");

            function genImage() {
              const p = document.getElementById("imgPrompt").value.trim();
              if (!p) {
                if (window.AndroidBridge) window.AndroidBridge.toast("Önce görsel tarifi yazın");
                return;
              }
              const url = "https://image.pollinations.ai/prompt/" + encodeURIComponent(p) + "?nologo=true&seed=" + Date.now();
              lastImgUrl = url;
              document.getElementById("genImg").src = url;
              if (window.AndroidBridge) window.AndroidBridge.toast("Görsel üretiliyor...");
            }

            function shareImgUrl() {
              if (!lastImgUrl) return;
              if (window.AndroidBridge) window.AndroidBridge.shareText(lastImgUrl);
            }

            function playAudio() {
              const u = document.getElementById("mediaUrl").value.trim();
              if (!u) return;
              const a = document.getElementById("audioBox");
              a.src = u;
              a.play().catch(()=>{});
            }

            function playVideo() {
              const u = document.getElementById("mediaUrl").value.trim();
              if (!u) return;
              const v = document.getElementById("videoBox");
              v.src = u;
              v.play().catch(()=>{});
            }

            function renderNotes() {
              localStorage.setItem("studio_notes", JSON.stringify(notes));
              document.getElementById("notesList").innerHTML = notes.map(n =>
                '<div class="note-item">' + n + '</div>'
              ).join("");
            }

            function addNote() {
              const val = document.getElementById("quickNote").value.trim();
              if (!val) return;
              notes.unshift(val);
              document.getElementById("quickNote").value = "";
              renderNotes();
            }

            renderNotes();
          </script>
        </body>
        </html>
    """.trimIndent()

    fun ecommerceTemplate(): String = """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            body { font-family: system-ui, sans-serif; margin:0; background:#090d16; color:#f8fafc; padding:14px; }
            .glass { background: rgba(30, 41, 59, 0.78); border: 1px solid rgba(148,163,184,0.2); border-radius:16px; padding:14px; }
            .top { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; }
            .badge { background: linear-gradient(135deg, #2563eb, #7c3aed); padding:6px 12px; border-radius:999px; font-size:12px; font-weight:700; }
            input { width:100%; box-sizing:border-box; padding:10px; margin-bottom:12px; border-radius:10px; border:1px solid #334155; background:#0f172a; color:#fff; }
            .grid { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
            .card { background:#0f172a; border:1px solid #334155; border-radius:12px; padding:12px; }
            .price { color:#38bdf8; font-weight:700; margin:6px 0; }
            button { width:100%; border:none; border-radius:10px; padding:9px; background:#2563eb; color:#fff; font-weight:700; cursor:pointer; }
            .wa { background: linear-gradient(135deg, #16a34a, #15803d); margin-top:14px; padding:12px; font-size:14px; }
          </style>
        </head>
        <body>
          <div class="glass">
            <div class="top">
              <h3 style="margin:0;">Katalog & Sipariş</h3>
              <span class="badge" id="cartCount">Sepet: 0</span>
            </div>
            <input id="searchBox" placeholder="Ürün ara..." oninput="render()" />
            <div class="grid" id="productList"></div>
            <button class="wa" onclick="sendWhatsApp()">WhatsApp ile Sipariş Gönder</button>
          </div>
          <script>
            const products = [
              { id:1, name:"Ürün Paketi A", price:450 },
              { id:2, name:"Ürün Paketi B", price:750 },
              { id:3, name:"Ekipman Seti", price:1200 },
              { id:4, name:"Aksesuar Seti", price:320 }
            ];
            let cart = JSON.parse(localStorage.getItem("cart_items") || "[]");
            function render() {
              const q = (document.getElementById("searchBox").value || "").toLowerCase();
              document.getElementById("cartCount").innerText = "Sepet: " + cart.length;
              localStorage.setItem("cart_items", JSON.stringify(cart));
              const filtered = products.filter(p => p.name.toLowerCase().includes(q));
              document.getElementById("productList").innerHTML = filtered.map(p =>
                '<div class="card"><div><b>' + p.name + '</b></div><div class="price">' + p.price + ' TL</div><button onclick="addItem(' + p.id + ')">Sepete Ekle</button></div>'
              ).join("");
            }
            function addItem(id) {
              const item = products.find(x => x.id === id);
              cart.push(item);
              render();
              if (window.AndroidBridge) window.AndroidBridge.toast(item.name + " sepete eklendi");
            }
            function sendWhatsApp() {
              if (!cart.length) { alert("Sepet boş"); return; }
              const total = cart.reduce((a,b)=>a+b.price,0);
              const lines = cart.map(x=>"- " + x.name + " (" + x.price + " TL)").join("\n");
              const msg = encodeURIComponent("Yeni Sipariş:\n" + lines + "\nToplam: " + total + " TL");
              window.open("https://wa.me/?text=" + msg, "_blank");
            }
            render();
          </script>
        </body>
        </html>
    """.trimIndent()

    fun orderTrackerTemplate(): String = """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            body { font-family: system-ui, sans-serif; margin:0; background:#090d16; color:#f8fafc; padding:14px; }
            .glass { background: rgba(30, 41, 59, 0.78); border: 1px solid rgba(148,163,184,0.2); border-radius:16px; padding:14px; }
            input, select, button { width:100%; box-sizing:border-box; padding:10px; margin-bottom:8px; border-radius:10px; border:1px solid #334155; background:#0f172a; color:#fff; }
            button { background: linear-gradient(135deg, #2563eb, #7c3aed); border:none; font-weight:700; cursor:pointer; }
            .order { background:#0f172a; border-left:4px solid #38bdf8; padding:10px; border-radius:10px; margin-bottom:8px; display:flex; justify-content:space-between; align-items:center; }
          </style>
        </head>
        <body>
          <div class="glass">
            <h3 style="margin-top:0;">Sipariş & Stok Takip</h3>
            <input id="custName" placeholder="Müşteri / Ürün Adı" />
            <input id="orderNote" placeholder="Adet / Tutar / Not" />
            <select id="orderStatus">
              <option value="Hazırlanıyor">Hazırlanıyor</option>
              <option value="Kargoda">Kargoda</option>
              <option value="Teslim Edildi">Teslim Edildi</option>
            </select>
            <button onclick="addOrder()">Kayıt Ekle</button>
            <div id="ordersList"></div>
          </div>
          <script>
            let orders = JSON.parse(localStorage.getItem("orders_db") || "[]");
            function saveAndRender() {
              localStorage.setItem("orders_db", JSON.stringify(orders));
              document.getElementById("ordersList").innerHTML = orders.map((o, idx) =>
                '<div class="order"><div><b>' + o.name + '</b><br><small>' + o.note + ' • ' + o.status + '</small></div><button style="width:auto;background:#dc2626;" onclick="delOrder(' + idx + ')">Sil</button></div>'
              ).join("");
            }
            function addOrder() {
              const name = document.getElementById("custName").value.trim();
              const note = document.getElementById("orderNote").value.trim();
              const status = document.getElementById("orderStatus").value;
              if (!name) return;
              orders.unshift({ name, note, status });
              document.getElementById("custName").value = "";
              document.getElementById("orderNote").value = "";
              saveAndRender();
            }
            function delOrder(i) { orders.splice(i, 1); saveAndRender(); }
            saveAndRender();
          </script>
        </body>
        </html>
    """.trimIndent()

    fun tvRadioTemplate(): String = """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            body { font-family: system-ui, sans-serif; margin:0; background:#090d16; color:#f8fafc; padding:12px; }
            .glass { background: rgba(30, 41, 59, 0.78); border: 1px solid rgba(148,163,184,0.2); border-radius:16px; padding:12px; }
            video { width:100%; max-height:220px; background:#000; border-radius:12px; }
            .row { display:flex; gap:8px; margin:10px 0; }
            input { flex:1; padding:8px; border-radius:10px; border:1px solid #334155; background:#0f172a; color:#fff; }
            button { padding:8px 12px; border:none; border-radius:10px; background: linear-gradient(135deg, #2563eb, #7c3aed); color:#fff; font-weight:700; }
            button:focus { outline:3px solid #38bdf8; }
            .ch { padding:10px; margin-bottom:6px; background:#0f172a; border-radius:10px; display:flex; justify-content:space-between; }
          </style>
        </head>
        <body>
          <div class="glass">
            <video id="player" controls autoplay></video>
            <div id="nowPlaying" style="margin:6px 0;font-size:13px;color:#38bdf8;">Kanal seçin</div>
            <div class="row">
              <input id="m3uUrl" placeholder="Yayın veya M3U URL yapıştır..." />
              <button onclick="addStream()">Ekle</button>
            </div>
            <div id="channels"></div>
          </div>
          <script>
            let channels = JSON.parse(localStorage.getItem("tv_channels") || "[]");
            if (!channels.length) {
              channels = [
                { title: "Örnek Radyo Yayını", url: "" },
                { title: "Örnek TV Akışı", url: "" }
              ];
            }
            function renderCh() {
              localStorage.setItem("tv_channels", JSON.stringify(channels));
              document.getElementById("channels").innerHTML = channels.map((c, i) =>
                '<button class="ch" style="width:100%;text-align:left;" onclick="playCh(' + i + ')"><span>' + c.title + '</span><small>Oynat</small></button>'
              ).join("");
            }
            function playCh(i) {
              const c = channels[i];
              document.getElementById("nowPlaying").innerText = "Yayın: " + c.title;
              if (c.url) {
                const v = document.getElementById("player");
                v.src = c.url;
                v.play().catch(()=>{});
              } else if (window.AndroidBridge) {
                window.AndroidBridge.toast("Önce yayın URL'si ekleyin");
              }
            }
            function addStream() {
              const u = document.getElementById("m3uUrl").value.trim();
              if (!u) return;
              channels.unshift({ title: "Özel Yayın " + (channels.length + 1), url: u });
              document.getElementById("m3uUrl").value = "";
              renderCh();
            }
            renderCh();
          </script>
        </body>
        </html>
    """.trimIndent()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val studioDarkColors = darkColorScheme(
                primary = Color(0xFF38BDF8),
                secondary = Color(0xFFA855F7),
                background = Color(0xFF090D16),
                surface = Color(0xFF131B2E),
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC)
            )
            MaterialTheme(colorScheme = studioDarkColors) {
                AiStudioApkScreen()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudioApkScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("apk_ai_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var ghToken by remember { mutableStateOf(prefs.getString("gh_token", "") ?: "") }
    var ghOwner by remember { mutableStateOf(prefs.getString("gh_owner", "cengiz718") ?: "cengiz718") }
    var ghRepo by remember { mutableStateOf(prefs.getString("gh_repo", "Kodlama") ?: "Kodlama") }
    var ghBranch by remember { mutableStateOf(prefs.getString("gh_branch", "main") ?: "main") }
    var appTitle by remember { mutableStateOf("AI Studio Süper Stüdyo") }
    var deletePathInput by remember { mutableStateOf("") }

    var showSettings by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) }
    var previewVersion by remember { mutableIntStateOf(0) }
    var promptInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var updateInfoUrl by remember { mutableStateOf<String?>(null) }

    var liveHtmlCode by remember { mutableStateOf(BuiltInTemplates.mediaStudioTemplate()) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "assistant",
                "AI Studio Süper Stüdyo v" + CURRENT_APP_VERSION + " hazır! Görsel üretici, müzik/video oynatıcı, not defteri, şablonlar ve otomatik güncelleme denetleyicisi eklendi."
            )
        )
    }

    LaunchedEffect(Unit) {
        val status = checkAppUpdateFromGitHub(ghOwner.trim().ifBlank { "cengiz718" }, ghRepo.trim().ifBlank { "Kodlama" })
        if (status.downloadUrl != null) {
            updateInfoUrl = status.downloadUrl
            messages.add(ChatMessage("assistant", status.message))
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.background(
                    Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A), Color(0xFF1E293B)))
                )
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Column {
                            Text("AI Studio Süper Stüdyo", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                            Text(
                                "v" + CURRENT_APP_VERSION + " • " + ghOwner + "/" + ghRepo,
                                fontSize = 11.sp,
                                color = Color(0xFF7DD3FC)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (isLoading) return@IconButton
                            isLoading = true
                            scope.launch {
                                val res = checkAppUpdateFromGitHub(ghOwner.trim().ifBlank { "cengiz718" }, ghRepo.trim().ifBlank { "Kodlama" })
                                updateInfoUrl = res.downloadUrl
                                messages.add(ChatMessage("assistant", res.message))
                                activeTab = 0
                                isLoading = false
                            }
                        }) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "Güncelleme Kontrolü", tint = Color(0xFF38BDF8))
                        }
                        IconButton(onClick = { showSettings = !showSettings }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ayarlar", tint = Color.White)
                        }
                    }
                )
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color(0xFF131B2E),
                    contentColor = Color(0xFF38BDF8)
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Stüdyo & Sohbet") },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Canlı Önizleme") },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(Color(0xFF090D16), Color(0xFF0F172A))))
        ) {
            if (updateInfoUrl != null) {
                Surface(
                    color = Color(0xFF1D4ED8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Yeni sürüm / APK hazır!", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                updateInfoUrl?.let { u ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1E3A8A)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Güncellemeyi İndir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (showSettings) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GitHub ve Paket Ayarları", fontWeight = FontWeight.Bold, color = Color.White)

                        OutlinedTextField(
                            value = appTitle,
                            onValueChange = { appTitle = it },
                            label = { Text("Üretilecek APK Uygulama Adı") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = ghToken,
                            onValueChange = { ghToken = it },
                            label = { Text("GitHub Token (ghp_...)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ghOwner,
                                onValueChange = { ghOwner = it },
                                label = { Text("Kullanıcı") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = ghRepo,
                                onValueChange = { ghRepo = it },
                                label = { Text("Depo Adı") },
                                singleLine = true,
                                modifier = Modifier.weight(1.3f)
                            )
                            OutlinedTextField(
                                value = ghBranch,
                                onValueChange = { ghBranch = it },
                                label = { Text("Dal") },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = deletePathInput,
                                onValueChange = { deletePathInput = it },
                                label = { Text("Silinecek dosya yolu") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (deletePathInput.isBlank() || ghToken.isBlank() || ghRepo.isBlank() || isLoading) return@Button
                                    val targetPath = deletePathInput.trim()
                                    isLoading = true
                                    scope.launch {
                                        val res = deleteFileFromGitHub(ghToken.trim(), ghOwner.trim(), ghRepo.trim(), targetPath, ghBranch.trim())
                                        messages.add(ChatMessage("assistant", res))
                                        isLoading = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sil")
                            }
                        }

                        Button(
                            onClick = {
                                prefs.edit()
                                    .putString("gh_token", ghToken.trim())
                                    .putString("gh_owner", ghOwner.trim())
                                    .putString("gh_repo", ghRepo.trim())
                                    .putString("gh_branch", ghBranch.trim())
                                    .apply()
                                showSettings = false
                                Toast.makeText(context, "Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Kaydet ve Kapat")
                        }
                    }
                }
            }

            if (activeTab == 1) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF131B2E))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { previewVersion++ }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            }
                            Text("Önizleme: " + appTitle, color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                if (ghToken.isBlank() || ghRepo.isBlank()) {
                                    showSettings = true
                                    Toast.makeText(context, "Önce GitHub Token ve Depo Adı girin", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isLoading = true
                                activeTab = 0
                                scope.launch {
                                    val report = deployHtmlAppToGitHub(
                                        ghToken.trim(),
                                        ghOwner.trim(),
                                        ghRepo.trim(),
                                        ghBranch.trim(),
                                        appTitle.trim().ifBlank { "AI Studio Süper Stüdyo" },
                                        liveHtmlCode
                                    )
                                    messages.add(ChatMessage("assistant", report))
                                    isLoading = false
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("APK + Release Yap", fontSize = 12.sp)
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                addJavascriptInterface(NativeBridge(ctx), "AndroidBridge")
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()
                            }
                        },
                        update = { webView ->
                            val trigger = previewVersion
                            if (trigger >= 0) {
                                webView.loadDataWithBaseURL("https://localhost/", liveHtmlCode, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(Color(0xFF111827))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            liveHtmlCode = BuiltInTemplates.mediaStudioTemplate()
                            appTitle = "Görsel & Medya Stüdyo"
                            previewVersion++
                            activeTab = 1
                        },
                        label = { Text("Görsel & Medya", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            liveHtmlCode = BuiltInTemplates.ecommerceTemplate()
                            appTitle = "E-Ticaret Sipariş"
                            previewVersion++
                            activeTab = 1
                        },
                        label = { Text("E-Ticaret", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            liveHtmlCode = BuiltInTemplates.orderTrackerTemplate()
                            appTitle = "Sipariş Takip"
                            previewVersion++
                            activeTab = 1
                        },
                        label = { Text("Sipariş Takip", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            liveHtmlCode = BuiltInTemplates.tvRadioTemplate()
                            appTitle = "TV & Radyo"
                            previewVersion++
                            activeTab = 1
                        },
                        label = { Text("TV & Radyo", fontSize = 11.sp) }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(messages) { msg ->
                        val isUser = msg.role == "user"
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) Color(0xFF2563EB) else Color(0xFF131B2E)
                            ),
                            border = BorderStroke(1.dp, if (isUser) Color(0xFF60A5FA) else Color(0xFF273552)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (isUser) "Sen" else "AI Studio Motoru",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isUser) Color.White else Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (msg.content.length > 2000) msg.content.take(2000) + "\n\n... [Tam kod Canlı Önizleme ve GitHub APK için hazır]" else msg.content,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = if (!isUser && msg.content.contains("<html", ignoreCase = true)) FontFamily.Monospace else FontFamily.Default
                                )
                                if (!isUser && msg.content.contains("<html", ignoreCase = true)) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                liveHtmlCode = sanitizeAndExtractHtml(msg.content)
                                                previewVersion++
                                                activeTab = 1
                                            }
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Önizle", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("HTML Kod", sanitizeAndExtractHtml(msg.content)))
                                                Toast.makeText(context, "Kod kopyalandı", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Kopyala", fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = {
                                                if (ghToken.isBlank() || ghRepo.isBlank() || isLoading) {
                                                    showSettings = true
                                                    return@Button
                                                }
                                                isLoading = true
                                                scope.launch {
                                                    val report = deployHtmlAppToGitHub(
                                                        ghToken.trim(),
                                                        ghOwner.trim(),
                                                        ghRepo.trim(),
                                                        ghBranch.trim(),
                                                        appTitle.trim().ifBlank { "AI Studio Süper Stüdyo" },
                                                        sanitizeAndExtractHtml(msg.content)
                                                    )
                                                    messages.add(ChatMessage("assistant", report))
                                                    isLoading = false
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("APK Yap", fontSize = 12.sp)
                                        }
                                    }
                                }
                                if (!isUser && msg.content.contains("https://github.com/")) {
                                    val urlMatch = Regex("https://\\S+").find(msg.content)?.value
                                    if (urlMatch != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(onClick = {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlMatch)))
                                        }) {
                                            Text("GitHub Releases / APK Sayfasını Aç", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131B2E))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text("Uygulama fikri veya 'DOSYA_SIL: yol' yaz...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (promptInput.isBlank() || isLoading) return@IconButton
                        val userText = promptInput.trim()
                        promptInput = ""
                        messages.add(ChatMessage("user", userText))
                        isLoading = true

                        scope.launch {
                            if (userText.startsWith("DOSYA_SIL:", ignoreCase = true)) {
                                val target = userText.substringAfter(":").trim()
                                val res = if (ghToken.isBlank() || ghRepo.isBlank()) {
                                    "Önce Ayarlar'dan GitHub Token ve Depo Adı girin."
                                } else {
                                    deleteFileFromGitHub(ghToken.trim(), ghOwner.trim(), ghRepo.trim(), target, ghBranch.trim())
                                }
                                messages.add(ChatMessage("assistant", res))
                                isLoading = false
                                return@launch
                            }

                            val generated = callLivePreviewAi(userText, liveHtmlCode)
                            messages.add(ChatMessage("assistant", generated))
                            val cleaned = sanitizeAndExtractHtml(generated)
                            if (cleaned.contains("<html", ignoreCase = true)) {
                                liveHtmlCode = cleaned
                                previewVersion++
                                activeTab = 1
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.background(
                        Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF7C3AED))),
                        RoundedCornerShape(14.dp)
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Üret", tint = Color.White)
                }
            }
        }
    }
}

data class UpdateCheckResult(val message: String, val downloadUrl: String?)

suspend fun checkAppUpdateFromGitHub(owner: String, repo: String): UpdateCheckResult =
    withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext UpdateCheckResult(
                        "Güncelleme sayfası: https://github.com/" + owner + "/" + repo + "/releases",
                        "https://github.com/" + owner + "/" + repo + "/releases"
                    )
                }
                val obj = JSONObject(resp.body?.string() ?: "{}")
                val tag = obj.optString("tag_name", "")
                val htmlUrl = obj.optString("html_url", "https://github.com/" + owner + "/" + repo + "/releases")
                var apkUrl = htmlUrl
                val assets = obj.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    val dl = assets.getJSONObject(0).optString("browser_download_url", "")
                    if (dl.isNotBlank()) apkUrl = dl
                }
                UpdateCheckResult(
                    "Mevcut sürüm: v" + CURRENT_APP_VERSION + " • Son Release: " + tag + "\nİndirme: " + apkUrl,
                    apkUrl
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult("Güncelleme kontrolü yapılamadı: " + e.message, null)
        }
    }

fun sanitizeAndExtractHtml(raw: String): String {
    val fenceRegex = Regex("```(?:html)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    var html = fenceRegex.find(raw)?.groupValues?.get(1)?.trim() ?: run {
        val start = raw.indexOf("<!DOCTYPE", ignoreCase = true).takeIf { it >= 0 }
            ?: raw.indexOf("<html", ignoreCase = true)
        if (start >= 0) raw.substring(start).trim() else raw.trim()
    }
    if (html.contains("<html", ignoreCase = true) && !html.contains("</html>", ignoreCase = true)) {
        if (!html.contains("</body>", ignoreCase = true)) html += "\n</body>"
        html += "\n</html>"
    }
    return html
}

suspend fun callLivePreviewAi(userPrompt: String, currentHtml: String): String =
    withContext(Dispatchers.IO) {
        val systemPrompt = """
            Sen Google AI Studio gibi çalışan uzman bir Mobil Uygulama Arayüz ve Kod Üreticisisin.
            Mevcut HTML/CSS/JS şablonunu veya kullanıcının isteğini alarak TEK DOSYA halinde, mobil ekrana tam uyumlu ve eksiksiz <!DOCTYPE html> ... </html> kodu üret.
            KURALLAR:
            1. Kodu yarım bırakma; mutlaka </body> ve </html> ile kapat.
            2. Verileri telefonda saklamak için localStorage kullan.
            3. Gerekirse AndroidBridge.toast(msg), AndroidBridge.copyText(txt), AndroidBridge.shareText(txt) fonksiyonlarını çağırabilirsin.
            4. Açıklama yazma, doğrudan HTML kodunu ver.
        """.trimIndent()

        val userMessage = "Mevcut Şablon / Kod:\n" + currentHtml + "\n\nİstenen Güncelleme / Uygulama: " + userPrompt

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .build()

        try {
            val escapedSystem = JSONObject.quote(systemPrompt)
            val escapedUser = JSONObject.quote(userMessage)
            val rawJson = """{"model":"openai","temperature":0.25,"messages":[{"role":"system","content":$escapedSystem},{"role":"user","content":$escapedUser}]}"""

            val req = Request.Builder()
                .url("https://text.pollinations.ai/openai")
                .addHeader("Content-Type", "application/json")
                .post(rawJson.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (resp.isSuccessful && bodyStr.isNotBlank()) {
                    if (bodyStr.trimStart().startsWith("{")) {
                        val obj = JSONObject(bodyStr)
                        val choices = obj.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val content = choices.getJSONObject(0)
                                .optJSONObject("message")
                                ?.optString("content", "")
                            if (!content.isNullOrBlank()) return@withContext content
                        }
                    }
                    return@withContext bodyStr
                }
            }
        } catch (_: Exception) {
        }

        try {
            val encoded = Uri.encode(systemPrompt + "\n\n" + userMessage)
            val getReq = Request.Builder()
                .url("https://text.pollinations.ai/" + encoded)
                .get()
                .build()
            client.newCall(getReq).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (resp.isSuccessful && bodyStr.isNotBlank()) return@withContext bodyStr
                return@withContext "AI yanıt vermedi (" + resp.code + "). Lütfen tekrar dene."
            }
        } catch (e: Exception) {
            "Bağlantı Hatası: " + e.message
        }
    }

private fun ghClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

private fun getFileSha(client: OkHttpClient, token: String, owner: String, repo: String, path: String, branch: String): String? {
    val cleanPath = path.trimStart('/')
    val req = Request.Builder()
        .url("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + cleanPath + "?ref=" + branch)
        .addHeader("Authorization", "Bearer " + token)
        .addHeader("Accept", "application/vnd.github+json")
        .build()
    client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val body = resp.body?.string() ?: return null
        return JSONObject(body).optString("sha", null)
    }
}

private fun uploadSingleFileToGitHub(
    client: OkHttpClient,
    token: String,
    owner: String,
    repo: String,
    branch: String,
    path: String,
    content: String
): String {
    val cleanPath = path.trimStart('/')
    val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    val sha = getFileSha(client, token, owner, repo, cleanPath, branch)

    val payload = JSONObject().apply {
        put("message", "AI Studio APK: " + cleanPath)
        put("content", b64)
        put("branch", branch)
        if (!sha.isNullOrBlank()) put("sha", sha)
    }

    val req = Request.Builder()
        .url("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + cleanPath)
        .addHeader("Authorization", "Bearer " + token)
        .addHeader("Accept", "application/vnd.github+json")
        .put(payload.toString().toRequestBody("application/json".toMediaType()))
        .build()

    client.newCall(req).execute().use { resp ->
        return if (resp.isSuccessful) "Yüklendi: " + cleanPath
        else "Hata (" + cleanPath + " - " + resp.code + "): " + (resp.body?.string() ?: "")
    }
}

suspend fun deployHtmlAppToGitHub(
    token: String,
    owner: String,
    repo: String,
    branch: String,
    appName: String,
    htmlContent: String
): String = withContext(Dispatchers.IO) {
    val client = ghClient()
    val logs = mutableListOf<String>()

    logs.add(uploadSingleFileToGitHub(client, token, owner, repo, branch, "app/src/main/assets/index.html", htmlContent))

    val dispatchPayload = JSONObject().put("ref", branch)
    val dispatchReq = Request.Builder()
        .url("https://api.github.com/repos/" + owner + "/" + repo + "/actions/workflows/build.yml/dispatches")
        .addHeader("Authorization", "Bearer " + token)
        .addHeader("Accept", "application/vnd.github+json")
        .post(dispatchPayload.toString().toRequestBody("application/json".toMediaType()))
        .build()
    client.newCall(dispatchReq).execute().close()

    logs.add("Derleme ve Release başlatıldı: https://github.com/" + owner + "/" + repo + "/releases")
    logs.joinToString("\n")
}

suspend fun deleteFileFromGitHub(
    token: String,
    owner: String,
    repo: String,
    path: String,
    branch: String
): String = withContext(Dispatchers.IO) {
    val client = ghClient()
    val cleanPath = path.trimStart('/')
    val sha = getFileSha(client, token, owner, repo, cleanPath, branch)
        ?: return@withContext "Dosya bulunamadı: " + cleanPath

    val payload = JSONObject().apply {
        put("message", "Delete " + cleanPath + " via AI Studio APK")
        put("sha", sha)
        put("branch", branch)
    }

    val req = Request.Builder()
        .url("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + cleanPath)
        .addHeader("Authorization", "Bearer " + token)
        .addHeader("Accept", "application/vnd.github+json")
        .delete(payload.toString().toRequestBody("application/json".toMediaType()))
        .build()

    client.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) "Silindi: " + cleanPath
        else "Silme hatası (" + resp.code + "): " + (resp.body?.string() ?: "")
    }
}
