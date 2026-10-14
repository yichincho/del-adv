package com.yichincho.deladv

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var diag: TextView
    private fun enabledService(): Boolean {
        val component = ComponentName(this, DiscoverService::class.java).flattenToString()
        val entries = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return entries.split(':').any { TextUtils.equals(it, component) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 42, 40, 32)
            setBackgroundColor(Color.rgb(247,250,252))
        }
        fun text(value: String, size: Float = 16f): TextView = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.rgb(22, 37, 57))
            setPadding(0, 10, 0, 10)
            column.addView(this)
        }
        fun button(label: String, action: () -> Unit) = column.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
        })
        text("🛡 del-adv", 30f)
        text("Google Discover 廣告遮罩 · Android（無 Root 測試版 ${BuildConfig.VERSION_NAME}）")
        text("只在 Google App 中尋找明確的廣告標籤，找不到可靠卡片範圍就不遮罩。")
        column.addView(Switch(this).apply {
            this.text = "啟用遮罩"
            isChecked = Prefs.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, value -> Prefs.setEnabled(this@MainActivity, value) }
        })
        status = text("無障礙服務狀態：檢查中")
        button("前往無障礙權限設定") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button("開啟 Google App") {
            val intent = packageManager.getLaunchIntentForPackage(DiscoverService.GOOGLE_PACKAGE)
            if (intent != null) startActivity(intent) else toast("找不到 Google App")
        }

        text("實機診斷", 20f)
        diag = text("")
        button("錄製下一次 Discover 畫面結構") {
            DiagnosticLog.armed = true
            toast("已就緒：現在去 Discover 捲到有廣告的卡片，停 1 秒再回來")
        }
        button("分享畫面結構（自行選擇傳給誰）") {
            val f = DiagnosticLog.file(this)
            if (!f.exists()) { toast("尚未錄製"); return@button }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "del-adv Discover dump")
                putExtra(Intent.EXTRA_TEXT, f.readText().take(90_000))
            }, "分享畫面結構"))
        }
        button("清除診斷紀錄") { DiagnosticLog.reset(this); refreshStatus() }
        text("畫面結構只含元件類型、位置與 16 字以內的短文字；新聞標題只記錄字數。檔案存在 App 內部，只有你按「分享」才會離開手機。", 13f)

        text("小米 / Redmi / POCO 設定", 20f)
        text("1. 先確認向左滑看到的是 Google Discover，而不是小米「智能助理／App Vault」。若是後者，在桌面設定的「負一屏」改選 Google（部分 HyperOS 版本才有此選項）。\n2. 在系統設定的無障礙功能（已下載的服務）中開啟 del-adv。\n3. 在應用程式管理中允許自動啟動，電池策略設為「無限制」。\n4. 回到手機首頁，向左滑至 Google Discover 測試。")
        text("注意：本 App 不會真正刪除 Google 卡片或移動下方新聞，只能遮罩，遮罩可點擊顯示。不需要 Root，不上傳任何畫面內容。")
        setContentView(ScrollView(this).apply { addView(column, ViewGroup.LayoutParams(-1, -2)) })
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    private fun refreshStatus() {
        status.text = if (enabledService()) "無障礙服務：已授權" else "無障礙服務：尚未授權（需手動開啟）"
        val dump = DiagnosticLog.file(this)
        diag.text = DiagnosticLog.summary(this) +
            if (dump.exists()) "\n已錄製畫面結構：${dump.length() / 1024} KB" else "\n尚未錄製畫面結構"
    }
    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }
}
