package com.cengiz718.apkuetici

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
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

const val CURRENT_APP_VERSION = "8.3"

data class ChatMessage(val role: String, val content: String)

fun startApkDirectDownload(context: Context, url: String, fileName: String = "AI-Studio-Yeni-Surum.apk") {
    try {
        if (!url.contains(".apk", ignoreCase = true)) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            return
        }
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("Yeni APK sürümü İndirilenler klasörüne indiriliyor...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "APK İndirilenler klasörüne indiriliyor...", Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "İndirme başlatılamadı: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }
}

class NativeBridge(
    private val context: Context,
    private val onSendPromptToAi: ((String) -> Unit)? = null
) {
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

    @JavascriptInterface
    fun buildAppFromPrompt(promptText: String) {
        (context as? ComponentActivity)?.runOnUiThread {
            onSendPromptToAi?.invoke(promptText)
        }
    }
}

object BuiltInTemplates {
    private fun baseVisualStyles(): String = """
        * { box-sizing: border-box; }
        body {
          font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
          margin: 0;
          padding: 14px;
          background:
            radial-gradient(circle at 12% 8%, rgba(56, 189, 248, 0.18), transparent 40%),
            radial-gradient(circle at 88% 15%, rgba(168, 85, 247, 0.22), transparent 45%),
            radial-gradient(circle at 50% 100%, rgba(236, 72, 153, 0.12), transparent 50%),
            #070b14;
          color: #f8fafc;
          min-height: 100vh;
        }
        .hero {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 14px;
          border-radius: 18px;
          margin-bottom: 14px;
          background: linear-gradient(135deg, rgba(30, 41, 59, 0.88), rgba(15, 23, 42, 0.92));
          border: 1px solid rgba(125, 211, 252, 0.25);
          box-shadow: 0 12px 28px rgba(0, 0, 0, 0.4);
        }
        .hero-icon {
          width: 46px;
          height: 46px;
          min-width: 46px;
          border-radius: 14px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: linear-gradient(135deg, #2563eb, #9333ea, #ec4899);
          box-shadow: 0 6px 18px rgba(124, 58, 237, 0.45);
          color: #fff;
        }
        .hero-title {
          margin: 0;
          font-size: 16px;
          font-weight: 800;
          letter-spacing: 0.2px;
          background: linear-gradient(90deg, #7dd3fc, #c084fc, #f9a8d4);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
        }
        .hero-sub {
          margin: 3px 0 0 0;
          font-size: 12px;
          color: #94a3b8;
        }
        .glass {
          background: rgba(20, 29, 49, 0.82);
          backdrop-filter: blur(14px);
          border: 1px solid rgba(148, 163, 184, 0.2);
          border-radius: 16px;
          padding: 14px;
          margin-bottom: 14px;
          box-shadow: 0 10px 24px rgba(0, 0, 0, 0.35);
        }
        .sec-head {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 10px;
        }
        .sec-title {
          display: flex;
          align-items: center;
          gap: 8px;
          margin: 0;
          font-size: 14px;
          font-weight: 700;
          color: #e2e8f0;
        }
        .pill {
          font-size: 11px;
          font-weight: 700;
          padding: 4px 10px;
          border-radius: 999px;
          background: rgba(56, 189, 248, 0.16);
          border: 1px solid rgba(56, 189, 248, 0.35);
          color: #7dd3fc;
        }
        label {
          display: flex;
          align-items: center;
          gap: 5px;
          font-size: 11px;
          font-weight: 700;
          color: #93c5fd;
          margin-bottom: 5px;
          text-transform: uppercase;
          letter-spacing: 0.4px;
        }
        input, textarea, select {
          width: 100%;
          padding: 11px 12px;
          margin-bottom: 11px;
          border-radius: 12px;
          border: 1px solid rgba(148, 163, 184, 0.25);
          background: rgba(9, 14, 26, 0.92);
          color: #f8fafc;
          font-size: 13px;
        }
        input:focus, textarea:focus, select:focus {
          outline: none;
          border-color: #38bdf8;
        }
        textarea { min-height: 74px; resize: vertical; }
        .btn-row { display: flex; gap: 8px; flex-wrap: wrap; }
        .btn {
          flex: 1;
          min-width: 105px;
          display: inline-flex;
          align-items: center;
          justify-content: center;
          gap: 6px;
          border: none;
          border-radius: 12px;
          padding: 11px 12px;
          font-weight: 700;
          font-size: 12px;
          color: #fff;
          background: linear-gradient(135deg, #2563eb, #7c3aed);
          cursor: pointer;
          box-shadow: 0 4px 14px rgba(37, 99, 235, 0.35);
        }
        .btn.green { background: linear-gradient(135deg, #059669, #10b981); box-shadow: 0 4px 14px rgba(16, 185, 129, 0.3); }
        .btn.amber { background: linear-gradient(135deg, #d97706, #ea580c); box-shadow: 0 4px 14px rgba(234, 88, 12, 0.3); }
        .btn.danger { background: linear-gradient(135deg, #dc2626, #be123c); }
        .btn.ghost { background: rgba(51, 65, 85, 0.85); box-shadow: none; border: 1px solid rgba(148, 163, 184, 0.22); }
        svg.ico { width: 16px; height: 16px; flex-shrink: 0; stroke: currentColor; fill: none; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
        svg.ico-lg { width: 22px; height: 22px; stroke: currentColor; fill: none; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
    """.trimIndent()

    private fun svgIconsHelper(): String = """
        const ICONS = {
          spark: '<svg class="ico" viewBox="0 0 24 24"><path d="M12 3l1.9 5.8L20 10.7l-6.1 1.9L12 18.4l-1.9-5.8L4 10.7l6.1-1.9L12 3z"/><path d="M19 16l.8 2.4L22 19.2l-2.2.8L19 22l-.8-2-2.2-.8 2.2-.8L19 16z"/></svg>',
          image: '<svg class="ico" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="9" cy="10" r="1.8"/><path d="M21 16l-5-5-6 6-2-2-5 4"/></svg>',
          music: '<svg class="ico" viewBox="0 0 24 24"><path d="M9 18V6l12-2v12"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>',
          video: '<svg class="ico" viewBox="0 0 24 24"><rect x="2" y="5" width="15" height="14" rx="2.5"/><path d="M17 10l5-3v10l-5-3v-4z"/></svg>',
          code: '<svg class="ico" viewBox="0 0 24 24"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>',
          copy: '<svg class="ico" viewBox="0 0 24 24"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>',
          share: '<svg class="ico" viewBox="0 0 24 24"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.6" y1="10.5" x2="15.4" y2="6.5"/><line x1="8.6" y1="13.5" x2="15.4" y2="17.5"/></svg>',
          play: '<svg class="ico" viewBox="0 0 24 24"><polygon points="6 4 20 12 6 20 6 4"/></svg>',
          note: '<svg class="ico" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="13" y2="17"/></svg>',
          cart: '<svg class="ico" viewBox="0 0 24 24"><circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2 3h3l2.4 11.5a2 2 0 0 0 2 1.5h9.2a2 2 0 0 0 1.9-1.5L23 7H6"/></svg>',
          box: '<svg class="ico" viewBox="0 0 24 24"><path d="M21 16V8a2 2 0 0 0-1-1.7l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.7l7 4a2 2 0 0 0 2 0l7-4a2 2 0 0 0 1-1.7z"/><polyline points="3.3 7 12 12 20.7 7"/><line x1="12" y1="22" x2="12" y2="12"/></svg>',
          tv: '<svg class="ico" viewBox="0 0 24 24"><rect x="2" y="4" width="20" height="14" rx="2.5"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="18" x2="12" y2="21"/></svg>',
          trash: '<svg class="ico" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/></svg>',
          history: '<svg class="ico" viewBox="0 0 24 24"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><polyline points="3 3 3 8 8 8"/><polyline points="12 7 12 12 15 14"/></svg>'
        };
    """.trimIndent()

    fun promptStudioTemplate(): String = """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            ${baseVisualStyles()}
            .cat-grid {
              display: grid;
              grid-template-columns: 1fr 1fr;
              gap: 9px;
              margin-bottom: 13px;
            }
            .cat-btn {
              display: flex;
              align-items: center;
              gap: 8px;
              padding: 11px 10px;
              border-radius: 13px;
              border: 1px solid rgba(148, 163, 184, 0.22);
              background: rgba(11, 17, 32, 0.9);
              color: #cbd5e1;
              font-size: 12px;
              font-weight: 700;
              cursor: pointer;
            }
            .cat-btn.active {
              border-color: #38bdf8;
              background: linear-gradient(135deg, rgba(37, 99, 235, 0.42), rgba(147, 51, 234, 0.42));
              color: #ffffff;
              box-shadow: 0 6px 18px rgba(56, 189, 248, 0.22);
            }
            .chips {
              display: flex;
              flex-wrap: wrap;
              gap: 7px;
              margin-bottom: 13px;
            }
            .chip {
              padding: 6px 11px;
              border-radius: 999px;
              font-size: 11px;
              font-weight: 700;
              background: rgba(30, 41, 59, 0.85);
              border: 1px solid rgba(148, 163, 184, 0.24);
              color: #e2e8f0;
              cursor: pointer;
            }
            .chip.selected {
              background: linear-gradient(135deg, #0284c7, #7c3aed);
              border-color: #7dd3fc;
              color: #fff;
            }
            .result-box {
              background: #050811;
              border: 1px solid rgba(56, 189, 248, 0.55);
              border-radius: 13px;
              padding: 12px;
              font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
              font-size: 12px;
              line-height: 1.55;
              color: #e0f2fe;
              white-space: pre-wrap;
              word-break: break-word;
              margin-bottom: 11px;
            }
            .preview-img {
              width: 100%;
              border-radius: 13px;
              margin-top: 11px;
              display: none;
              border: 1px solid rgba(56, 189, 248, 0.35);
            }
            .hist-item {
              background: rgba(11, 17, 32, 0.88);
              border: 1px solid rgba(148, 163, 184, 0.16);
              border-left-width: 4px;
              border-left-color: #a855f7;
              border-radius: 12px;
              padding: 10px 11px;
              margin-top: 8px;
              font-size: 12px;
            }
            .hist-top {
              display: flex;
              justify-content: space-between;
              align-items: center;
              color: #7dd3fc;
              font-weight: 700;
              font-size: 11px;
              margin-bottom: 5px;
            }
          </style>
        </head>
        <body>
          <div class="hero">
            <div class="hero-icon">
              <svg class="ico-lg" viewBox="0 0 24 24"><path d="M12 3l2.2 6.2L20.5 11l-6.3 1.9L12 19l-2.2-6.1L3.5 11l6.3-1.8L12 3z"/><circle cx="19" cy="5" r="1.5"/></svg>
            </div>
            <div>
              <h2 class="hero-title">Pro AI Prompt Stüdyosu</h2>
              <p class="hero-sub">Görsel, Suno Müzik, Video ve Mobil Uygulama istem merkezi</p>
            </div>
          </div>

          <div class="glass">
            <div class="sec-head">
              <h3 class="sec-title"><span id="hCatIcon"></span> Kategori Seçin</h3>
              <span class="pill" id="activeBadge">GÖRSEL</span>
            </div>

            <div class="cat-grid">
              <div class="cat-btn active" id="cat-image" onclick="selectCat('image')"><span id="cIconImg"></span><span>Görsel & Kapak</span></div>
              <div class="cat-btn" id="cat-music" onclick="selectCat('music')"><span id="cIconMus"></span><span>Suno / Udio Müzik</span></div>
              <div class="cat-btn" id="cat-video" onclick="selectCat('video')"><span id="cIconVid"></span><span>Video & Sinema</span></div>
              <div class="cat-btn" id="cat-app" onclick="selectCat('app')"><span id="cIconApp"></span><span>Mobil Uygulama</span></div>
            </div>

            <label><span id="lblIdeaIcon"></span> Ana Konu / Fikir</label>
            <textarea id="ideaInput" placeholder="Örn: Adıyaman Nemrut Dağı heykelleri üzerinde altın saat ışığı..."></textarea>

            <label><span id="lblStyleIcon"></span> Tarz & Atmosfer</label>
            <select id="styleSelect"></select>

            <label><span id="lblTagIcon"></span> Hızlı Teknik Etiketler</label>
            <div class="chips" id="tagChips"></div>

            <button class="btn" style="width:100%;" onclick="generatePrompt()"><span id="btnGenIcon"></span><span>Profesyonel Prompt Üret</span></button>
          </div>

          <div class="glass" id="outputCard" style="display:none;">
            <div class="sec-head">
              <h3 class="sec-title"><span id="lblOutIcon"></span> Üretilen Prompt</h3>
              <span class="pill">HAZIR</span>
            </div>
            <div class="result-box" id="promptOutput"></div>
            <div class="btn-row">
              <button class="btn green" onclick="copyPrompt()"><span id="btnCopyIcon"></span><span>Kopyala</span></button>
              <button class="btn ghost" onclick="sharePrompt()"><span id="btnShareIcon"></span><span>Paylaş</span></button>
              <button class="btn amber" id="instantRunBtn" onclick="instantRun()"><span id="btnRunIcon"></span><span id="instantRunText">Görsel Üret</span></button>
            </div>
            <img id="instantPreview" class="preview-img" alt="Önizleme" />
          </div>

          <div class="glass">
            <div class="sec-head">
              <h3 class="sec-title"><span id="lblHistIcon"></span> Kayıtlı Promptlar</h3>
              <button class="btn danger" style="flex:0;min-width:76px;padding:6px 10px;font-size:11px;" onclick="clearHistory()"><span id="btnClearIcon"></span><span>Temizle</span></button>
            </div>
            <div id="historyList"></div>
          </div>

          <script>
            ${svgIconsHelper()}

            document.getElementById("hCatIcon").innerHTML = ICONS.spark;
            document.getElementById("cIconImg").innerHTML = ICONS.image;
            document.getElementById("cIconMus").innerHTML = ICONS.music;
            document.getElementById("cIconVid").innerHTML = ICONS.video;
            document.getElementById("cIconApp").innerHTML = ICONS.code;
            document.getElementById("lblIdeaIcon").innerHTML = ICONS.note;
            document.getElementById("lblStyleIcon").innerHTML = ICONS.spark;
            document.getElementById("lblTagIcon").innerHTML = ICONS.spark;
            document.getElementById("btnGenIcon").innerHTML = ICONS.spark;
            document.getElementById("lblOutIcon").innerHTML = ICONS.code;
            document.getElementById("btnCopyIcon").innerHTML = ICONS.copy;
            document.getElementById("btnShareIcon").innerHTML = ICONS.share;
            document.getElementById("btnRunIcon").innerHTML = ICONS.play;
            document.getElementById("lblHistIcon").innerHTML = ICONS.history;
            document.getElementById("btnClearIcon").innerHTML = ICONS.trash;

            let currentCat = "image";
            let selectedTags = new Set();
            let lastGenerated = "";
            let historyDb = JSON.parse(localStorage.getItem("pro_prompt_history") || "[]");

            const presets = {
              image: {
                badge: "GÖRSEL",
                placeholder: "Örn: Adıyaman Nemrut Dağı heykelleri üzerinde altın saat gün batımı...",
                runLabel: "Görsel Üret",
                styles: [
                  { label: "Fotogerçekçi & Sinematik (8K)", val: "photorealistic, cinematic lighting, golden hour, 8k resolution, ultra detailed, shot on 35mm lens, depth of field" },
                  { label: "Ürün & Reklam Afişi", val: "commercial product photography, studio rim lighting, clean modern luxury background, sharp focus, high contrast, advertising poster style" },
                  { label: "3D Modern İllüstrasyon", val: "3d isometric render, octane render, vibrant studio colors, smooth glossy textures, soft global illumination" },
                  { label: "Yağlı Boya & Sanatsal", val: "dramatic oil painting style, rich impasto brushstrokes, atmospheric fog, museum masterpiece lighting" }
                ],
                tags: ["Altın Saat Işığı", "Geniş Açı", "Macro Detay", "Dramatik Gökyüzü", "Stüdyo Işığı", "Sığ Alan Derinliği", "Minimalist", "Lüks Doku"]
              },
              music: {
                badge: "SUNO / UDİO",
                placeholder: "Örn: Gurbet, sıla hasreti ve toprak kokusu anlatan duygusal türkü...",
                runLabel: "Suno Kopyala",
                styles: [
                  { label: "Anadolu Türküsü & Bağlama", val: "Anatolian Turkish folk, solo baglama saz, ney flute, warm acoustic percussion, emotional male vocals, authentic traditional arrangement, 78 BPM" },
                  { label: "Damar Arabesk & Yaylılar", val: "Turkish arabesque ballad, lush string orchestra, deep oud and kanun, passionate soulful vocals, dramatic reverb, slow tempo 68 BPM" },
                  { label: "Uzun Hava & Bozlak (Serbest Ritim)", val: "Traditional Turkish Uzun Hava, Bozlak style, free-rhythm improvised vocal lament, solo divan sazi, raw emotional storytelling, atmospheric hall reverb" },
                  { label: "Modern Özgün & Akustik", val: "Contemporary Turkish ozgun music, acoustic guitar, baglama fusion, warm bassline, poetic vocals, studio master quality, 90 BPM" }
                ],
                tags: ["Divan Sazı", "Ney Taksimi", "Duygusal Vokal", "Yaylı Grubu", "Kemençe", "Kanun", "Ağır Tempo", "Canlı Konser Akustiği"]
              },
              video: {
                badge: "VİDEO / SİNEMA",
                placeholder: "Örn: Zeytin bahçesinde sabah sisi içinde drone ile süzülen kamera...",
                runLabel: "Kare Üret",
                styles: [
                  { label: "Drone Havadan Çekim", val: "Smooth aerial FPV drone tracking shot, sweeping landscape reveal, 4k 60fps cinematic color grading, natural atmospheric haze" },
                  { label: "Ticari Tanıtım & Reklam", val: "High-end commercial video b-roll, slow motion 120fps, dynamic camera push-in, crisp studio reflections, modern color grade" },
                  { label: "Belgesel & Hikaye Anlatımı", val: "Cinematic documentary cinematography, natural window lighting, handheld subtle motion, authentic film grain, Kodak Vision3 look" }
                ],
                tags: ["Ağır Çekim 120fps", "Drone Takip", "Sinematik Renk", "Gece Işıkları", "Dolly Zoom", "Doğal Sis"]
              },
              app: {
                badge: "MOBİL UYGULAMA",
                placeholder: "Örn: Barkodlu stok takip, müşteri borç defteri ve WhatsApp fiş gönderme uygulaması...",
                runLabel: "Kodu Üret",
                styles: [
                  { label: "Koyu Tema Glassmorphism Mobil Arayüz", val: "Modern dark glassmorphism mobile-first UI, responsive cards, smooth touch buttons, localStorage database persistence, Turkish interface" },
                  { label: "Esnaf & Ticari Yönetim Paneli", val: "Clean commercial dashboard UI, quick search filter, summary KPI cards, WhatsApp share integration, offline localStorage CRUD" },
                  { label: "Medya & Oynatıcı Uygulaması", val: "Sleek dark media player UI, playlist management, fullscreen responsive player, D-pad & touch friendly controls" }
                ],
                tags: ["WhatsApp Sipariş", "localStorage Kayıt", "Arama Filtresi", "Toplam Hesaplama", "PDF/Metin Paylaş", "Büyük Butonlar"]
              }
            };

            function selectCat(cat) {
              currentCat = cat;
              selectedTags.clear();
              document.querySelectorAll(".cat-btn").forEach(b => b.classList.remove("active"));
              document.getElementById("cat-" + cat).classList.add("active");

              const cfg = presets[cat];
              document.getElementById("activeBadge").innerText = cfg.badge;
              document.getElementById("ideaInput").placeholder = cfg.placeholder;
              document.getElementById("instantRunText").innerText = cfg.runLabel;
              document.getElementById("instantPreview").style.display = "none";

              const sel = document.getElementById("styleSelect");
              sel.innerHTML = cfg.styles.map(s => '<option value="' + s.val + '">' + s.label + '</option>').join("");

              const chips = document.getElementById("tagChips");
              chips.innerHTML = cfg.tags.map(t =>
                '<span class="chip" onclick="toggleTag(this, \'' + t + '\')">+ ' + t + '</span>'
              ).join("");
            }

            function toggleTag(el, tag) {
              if (selectedTags.has(tag)) {
                selectedTags.delete(tag);
                el.classList.remove("selected");
              } else {
                selectedTags.add(tag);
                el.classList.add("selected");
              }
            }

            function generatePrompt() {
              const idea = document.getElementById("ideaInput").value.trim();
              if (!idea) {
                if (window.AndroidBridge) window.AndroidBridge.toast("Önce kısa bir fikir yazın");
                return;
              }
              const styleVal = document.getElementById("styleSelect").value;
              const tagsStr = Array.from(selectedTags).join(", ");
              let finalPrompt = "";

              if (currentCat === "app") {
                finalPrompt = "Şu özelliklere sahip eksiksiz, tek dosya mobil uyumlu HTML/CSS/JS uygulaması üret: " +
                  idea + ". Tasarım stili: " + styleVal +
                  (tagsStr ? ". Ek özellikler: " + tagsStr : "") + ".";
              } else if (currentCat === "music") {
                finalPrompt = "[Style: " + styleVal + (tagsStr ? ", " + tagsStr : "") + "]\n[Theme / Lyrics Idea: " + idea + "]\n[Structure: Intro - Verse 1 - Chorus - Instrumental Solo - Verse 2 - Chorus - Outro]";
              } else {
                finalPrompt = idea + ", " + styleVal + (tagsStr ? ", " + tagsStr : "");
              }

              lastGenerated = finalPrompt;
              document.getElementById("promptOutput").innerText = finalPrompt;
              document.getElementById("outputCard").style.display = "block";

              const catNames = { image: "Görsel", music: "Suno Müzik", video: "Video", app: "Uygulama" };
              historyDb.unshift({ cat: catNames[currentCat], text: finalPrompt });
              if (historyDb.length > 20) historyDb.pop();
              localStorage.setItem("pro_prompt_history", JSON.stringify(historyDb));
              renderHistory();
            }

            function copyPrompt() {
              if (!lastGenerated) return;
              if (window.AndroidBridge) window.AndroidBridge.copyText(lastGenerated);
            }

            function sharePrompt() {
              if (!lastGenerated) return;
              if (window.AndroidBridge) window.AndroidBridge.shareText(lastGenerated);
            }

            function instantRun() {
              if (!lastGenerated) return;
              if (currentCat === "app") {
                if (window.AndroidBridge) {
                  window.AndroidBridge.toast("Uygulama kodu üretiliyor...");
                  window.AndroidBridge.buildAppFromPrompt(lastGenerated);
                }
                return;
              }
              if (currentCat === "music") {
                copyPrompt();
                if (window.AndroidBridge) window.AndroidBridge.toast("Suno müzik istemi kopyalandı!");
                return;
              }
              const imgEl = document.getElementById("instantPreview");
              imgEl.style.display = "block";
              imgEl.src = "https://image.pollinations.ai/prompt/" + encodeURIComponent(lastGenerated) + "?nologo=true&seed=" + Date.now();
              if (window.AndroidBridge) window.AndroidBridge.toast("Görsel oluşturuluyor...");
            }

            function renderHistory() {
              document.getElementById("historyList").innerHTML = historyDb.map((h, i) =>
                '<div class="hist-item"><div class="hist-top"><span>' + ICONS.spark + ' ' + h.cat + '</span><span style="display:inline-flex;align-items:center;gap:4px;cursor:pointer;" onclick="copyHist(' + i + ')">' + ICONS.copy + ' Kopyala</span></div><div>' + h.text + '</div></div>'
              ).join("") || '<div class="hero-sub" style="margin-top:6px;">Henüz kayıtlı prompt yok.</div>';
            }

            function copyHist(idx) {
              const txt = historyDb[idx]?.text || "";
              if (txt && window.AndroidBridge) window.AndroidBridge.copyText(txt);
            }

            function clearHistory() {
              historyDb = [];
              localStorage.removeItem("pro_prompt_history");
              renderHistory();
            }

            selectCat("image");
            renderHistory();
          </script>
        </body>
        </html>
    """.trimIndent()

    fun mediaStudioTemplate(): String = """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            ${baseVisualStyles()}
            img, video, audio {
              width: 100%;
              border-radius: 14px;
              margin-top: 10px;
              background: #020617;
              border: 1px solid rgba(56, 189, 248, 0.3);
            }
            img { max-height: 260px; object-fit: cover; }
            video { max-height: 230px; }
            .note-item {
              display: flex;
              align-items: center;
              gap: 8px;
              background: rgba(11, 17, 32, 0.88);
              border-left: 4px solid #38bdf8;
              border-radius: 11px;
              padding: 10px;
              margin-top: 8px;
              font-size: 13px;
            }
          </style>
        </head>
        <body>
          <div class="hero">
            <div class="hero-icon" id="heroMedIcon"></div>
            <div>
              <h2 class="hero-title">Görsel & Medya Stüdyosu</h2>
              <p class="hero-sub">AI görsel üretimi, ses/video oynatıcı ve not defteri</p>
            </div>
          </div>

          <div class="glass">
            <div class="sec-head">
              <h3 class="sec-title"><span id="mImgIcon"></span> Görsel Üretici</h3>
              <span class="pill">8K STUDIO</span>
            </div>
            <input id="imgPrompt" placeholder="Görsel tarifi yaz (ör. modern stüdyo, gün batımı bahçe)..." />
            <div class="btn-row">
              <button class="btn" onclick="genImage()"><span id="mGenIcon"></span><span>Görsel Üret</span></button>
              <button class="btn ghost" onclick="shareImgUrl()"><span id="mShrIcon"></span><span>Paylaş</span></button>
            </div>
            <img id="genImg" alt="Üretilen görsel burada görünür" />
          </div>

          <div class="glass">
            <div class="sec-head">
              <h3 class="sec-title"><span id="mPlyIcon"></span> Müzik & Video Oynatıcı</h3>
              <span class="pill">MP3 / M3U8</span>
            </div>
            <input id="mediaUrl" placeholder="Müzik (MP3/OGG) veya Video / M3U8 URL yapıştır..." />
            <div class="btn-row">
              <button class="btn green" onclick="playAudio()"><span id="mAudIcon"></span><span>Ses Çal</span></button>
              <button class="btn" onclick="playVideo()"><span id="mVidIcon"></span><span>Video Aç</span></button>
            </div>
            <audio id="audioBox" controls></audio>
            <video id="videoBox" controls playsinline></video>
          </div>

          <div class="glass">
            <div class="sec-head">
              <h3 class="sec-title"><span id="mNotIcon"></span> Hızlı Not Defteri</h3>
              <span class="pill" id="noteCount">0 NOT</span>
            </div>
            <input id="quickNote" placeholder="Yeni fikir veya not ekle..." />
            <button class="btn" style="width:100%;" onclick="addNote()"><span id="mSaveIcon"></span><span>Notu Kaydet</span></button>
            <div id="notesList"></div>
          </div>

          <script>
            ${svgIconsHelper()}
            document.getElementById("heroMedIcon").innerHTML = ICONS.image;
            document.getElementById("mImgIcon").innerHTML = ICONS.image;
            document.getElementById("mGenIcon").innerHTML = ICONS.spark;
            document.getElementById("mShrIcon").innerHTML = ICONS.share;
            document.getElementById("mPlyIcon").innerHTML = ICONS.music;
            document.getElementById("mAudIcon").innerHTML = ICONS.music;
            document.getElementById("mVidIcon").innerHTML = ICONS.video;
            document.getElementById("mNotIcon").innerHTML = ICONS.note;
            document.getElementById("mSaveIcon").innerHTML = ICONS.note;

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
              document.getElementById("noteCount").innerText = notes.length + " NOT";
              document.getElementById("notesList").innerHTML = notes.map(n =>
                '<div class="note-item">' + ICONS.note + '<span>' + n + '</span></div>'
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
            ${baseVisualStyles()}
            .grid { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
            .card {
              background:rgba(11, 17, 32, 0.92);
              border:1px solid rgba(148, 163, 184, 0.22);
              border-radius:14px;
              padding:12px;
            }
            .price { color:#38bdf8; font-weight:800; margin:7px 0 10px 0; font-size:14px; }
          </style>
        </head>
        <body>
          <div class="hero">
            <div class="hero-icon" id="heroShopIcon"></div>
            <div>
              <h2 class="hero-title">Katalog & WhatsApp Sipariş</h2>
              <p class="hero-sub">Hızlı ürün arama, sepet yönetimi ve tek tuş sipariş</p>
            </div>
          </div>

          <div class="glass">
            <div class="sec-head">
              <h3 class="sec-title"><span id="catHeadIcon"></span> Ürün Kataloğu</h3>
              <span class="pill" id="cartCount">Sepet: 0</span>
            </div>
            <input id="searchBox" placeholder="Ürün adı ile ara..." oninput="render()" />
            <div class="grid" id="productList"></div>
            <button class="btn green" style="width:100%;margin-top:14px;padding:13px;" onclick="sendWhatsApp()"><span id="waIcon"></span><span>WhatsApp ile Sipariş Gönder</span></button>
          </div>
          <script>
            ${svgIconsHelper()}
            document.getElementById("heroShopIcon").innerHTML = ICONS.cart;
            document.getElementById("catHeadIcon").innerHTML = ICONS.box;
            document.getElementById("waIcon").innerHTML = ICONS.share;

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
                '<div class="card"><div style="display:flex;align-items:center;gap:6px;">' + ICONS.box + '<b>' + p.name + '</b></div><div class="price">' + p.price + ' TL</div><button class="btn" style="width:100%;min-width:0;padding:9px;" onclick="addItem(' + p.id + ')">' + ICONS.cart + '<span>Ekle</span></button></div>'
              ).join("");
            }
            function addItem(id) {
              const item = products.find(x => x.id === id);
              cart.push(item);
              render();
              if (window.AndroidBridge) window.AndroidBridge.toast(item.name + " sepete eklendi");
            }
            function sendWhatsApp() {
              if (!cart.length) {
                if (window.AndroidBridge) window.AndroidBridge.toast("Sepet boş");
                return;
              }
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
            ${baseVisualStyles()}
            .order {
              background:rgba(11, 17, 32, 0.92);
              border:1px solid rgba(148, 163, 184, 0.2);
              border-left:4px solid #38bdf8;
              padding:11px;
              border-radius:12px;
              margin-bottom:8px;
              display:flex;
              justify-content:space-between;
              align-items:center;
              gap:10px;
            }
          </style>
        </head>
        <body>
          <div class="hero">
            <div class="hero-icon" id="heroOrdIcon"></div>
            <div>
              <h2 class="hero-title">Sipariş & Stok Takip</h2>
              <p class="hero-sub">Kayıt oluşturma, durum güncelleme ve hızlı silme</p>
            </div>
          </div>

          <div class="glass">
            <div class="sec-head">
              <h3 class="sec-title"><span id="ordHeadIcon"></span> Yeni Kayıt</h3>
              <span class="pill" id="ordCount">0 KAYIT</span>
            </div>
            <input id="custName" placeholder="Müşteri / Ürün Adı" />
            <input id="orderNote" placeholder="Adet / Tutar / Not" />
            <select id="orderStatus">
              <option value="Hazırlanıyor">Hazırlanıyor</option>
              <option value="Kargoda">Kargoda</option>
              <option value="Teslim Edildi">Teslim Edildi</option>
            </select>
            <button class="btn" style="width:100%;" onclick="addOrder()"><span id="ordAddIcon"></span><span>Kayıt Ekle</span></button>
            <div id="ordersList" style="margin-top:12px;"></div>
          </div>
          <script>
            ${svgIconsHelper()}
            document.getElementById("heroOrdIcon").innerHTML = ICONS.box;
            document.getElementById("ordHeadIcon").innerHTML = ICONS.note;
            document.getElementById("ordAddIcon").innerHTML = ICONS.spark;

            let orders = JSON.parse(localStorage.getItem("orders_db") || "[]");
            function saveAndRender() {
              localStorage.setItem("orders_db", JSON.stringify(orders));
              document.getElementById("ordCount").innerText = orders.length + " KAYIT";
              document.getElementById("ordersList").innerHTML = orders.map((o, idx) =>
                '<div class="order"><div><b>' + o.name + '</b><br><small style="color:#94a3b8;">' + o.note + ' • <span style="color:#7dd3fc;">' + o.status + '</span></small></div><button class="btn danger" style="flex:0;min-width:66px;padding:8px 10px;" onclick="delOrder(' + idx + ')">' + ICONS.trash + '<span>Sil</span></button></div>'
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
            ${baseVisualStyles()}
            video { width:100%; max-height:220px; background:#000; border-radius:14px; border:1px solid rgba(56, 189, 248, 0.3); }
            .row { display:flex; gap:8px; margin:10px 0; align-items:stretch; }
            .row input { flex:1; margin-bottom:0; }
            .ch {
              width:100%;
              display:flex;
              justify-content:space-between;
              align-items:center;
              padding:11px 12px;
              margin-bottom:7px;
              background:rgba(11, 17, 32, 0.92);
              border:1px solid rgba(148, 163, 184, 0.22);
              border-radius:12px;
              color:#f8fafc;
              font-weight:700;
              cursor:pointer;
            }
            .ch:focus { outline:3px solid #38bdf8; }
          </style>
        </head>
        <body>
          <div class="hero">
            <div class="hero-icon" id="heroTvIcon"></div>
            <div>
              <h2 class="hero-title">TV & Radyo Oynatıcı</h2>
              <p class="hero-sub">Canlı akış, M3U/yayın listesi ve kumanda odaklı kontrol</p>
            </div>
          </div>

          <div class="glass">
            <video id="player" controls autoplay></video>
            <div id="nowPlaying" style="display:flex;align-items:center;gap:6px;margin:9px 0;font-size:13px;font-weight:700;color:#7dd3fc;"></div>
            <div class="row">
              <input id="m3uUrl" placeholder="Yayın veya M3U URL yapıştır..." />
              <button class="btn" style="flex:0;min-width:86px;margin-bottom:0;" onclick="addStream()"><span id="tvAddIcon"></span><span>Ekle</span></button>
            </div>
            <div id="channels" style="margin-top:10px;"></div>
          </div>
          <script>
            ${svgIconsHelper()}
            document.getElementById("heroTvIcon").innerHTML = ICONS.tv;
            document.getElementById("tvAddIcon").innerHTML = ICONS.spark;
            document.getElementById("nowPlaying").innerHTML = ICONS.tv + '<span>Kanal seçin</span>';

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
                '<button class="ch" onclick="playCh(' + i + ')"><span style="display:flex;align-items:center;gap:8px;">' + ICONS.tv + '<span>' + c.title + '</span></span><span class="pill" style="display:inline-flex;align-items:center;gap:4px;">' + ICONS.play + ' Oynat</span></button>'
              ).join("");
            }
            function playCh(i) {
              const c = channels[i];
              document.getElementById("nowPlaying").innerHTML = ICONS.play + '<span>Yayın: ' + c.title + '</span>';
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

    var liveHtmlCode by remember { mutableStateOf(BuiltInTemplates.promptStudioTemplate()) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "assistant",
                "AI Studio Süper Stüdyo v" + CURRENT_APP_VERSION + " hazır! Doğrudan APK indirme (DownloadManager), SVG simgeli şablonlar, Pro Prompt Stüdyosu ve tek tuşla GitHub APK üreticisi aktif."
            )
        )
    }

    val runPromptGeneration: (String) -> Unit = { userText ->
        if (userText.isNotBlank() && !isLoading) {
            messages.add(ChatMessage("user", userText))
            isLoading = true
            activeTab = 0
            scope.launch {
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
        }
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
                                    startApkDirectDownload(context, u)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1E3A8A)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Doğrudan İndir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                addJavascriptInterface(
                                    NativeBridge(ctx) { promptToBuild ->
                                        runPromptGeneration(promptToBuild)
                                    },
                                    "AndroidBridge"
                                )
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
                            liveHtmlCode = BuiltInTemplates.promptStudioTemplate()
                            appTitle = "Pro Prompt Üretici"
                            previewVersion++
                            activeTab = 1
                        },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text("Prompt Üretici", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    SuggestionChip(
                        onClick = {
                            liveHtmlCode = BuiltInTemplates.mediaStudioTemplate()
                            appTitle = "Görsel & Medya Stüdyo"
                            previewVersion++
                            activeTab = 1
                        },
                        icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text("Görsel & Medya", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            liveHtmlCode = BuiltInTemplates.ecommerceTemplate()
                            appTitle = "E-Ticaret Sipariş"
                            previewVersion++
                            activeTab = 1
                        },
                        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text("E-Ticaret", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            liveHtmlCode = BuiltInTemplates.orderTrackerTemplate()
                            appTitle = "Sipariş Takip"
                            previewVersion++
                            activeTab = 1
                        },
                        icon = { Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text("Sipariş Takip", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            liveHtmlCode = BuiltInTemplates.tvRadioTemplate()
                            appTitle = "TV & Radyo"
                            previewVersion++
                            activeTab = 1
                        },
                        icon = { Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(15.dp)) },
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
                                            startApkDirectDownload(context, urlMatch)
                                        }) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("APK İndir / Releases Aç", fontSize = 12.sp)
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

                        if (userText.startsWith("DOSYA_SIL:", ignoreCase = true)) {
                            messages.add(ChatMessage("user", userText))
                            isLoading = true
                            scope.launch {
                                val target = userText.substringAfter(":").trim()
                                val res = if (ghToken.isBlank() || ghRepo.isBlank()) {
                                    "Önce Ayarlar'dan GitHub Token ve Depo Adı girin."
                                } else {
                                    deleteFileFromGitHub(ghToken.trim(), ghOwner.trim(), ghRepo.trim(), target, ghBranch.trim())
                                }
                                messages.add(ChatMessage("assistant", res))
                                isLoading = false
                            }
                            return@IconButton
                        }

                        runPromptGeneration(userText)
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
        sha?.let { put("sha", it) }
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
