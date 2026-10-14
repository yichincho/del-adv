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
    private fun enabledService(): Boolean {
        val component = ComponentName(this, DiscoverService::class.java).flattenToString()
        val entries = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return entries.split(':').any { TextUtils.equals(it, component) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
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
        text("🛡 del-adv", 30f)
        text("Google Discover 廣告遮罩 · Android（無 Root 測試版）")
        text("只在 Google App 中尋找明確的廣告標籤，找不到可靠卡片範圍就不遮罩。")
        val switch = Switch(this).apply {
            this.text = "啟用遮罩"
            isChecked = prefs.getBoolean("enabled", true)
            setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean("enabled", value).apply() }
        }
        column.addView(switch)
        status = text("無障礙服務狀態：檢查中")
        column.addView(Button(this).apply {
            text = "前往無障礙權限設定"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        column.addView(Button(this).apply {
            text = "開啟 Google App"
            setOnClickListener {
                val intent = packageManager.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
                if (intent != null) startActivity(intent) else Toast.makeText(this@MainActivity, "找不到 Google App", Toast.LENGTH_LONG).show()
            }
        })
        text("小米 / Redmi / POCO 設定", 20f)
        text("1. 在系統設定的無障礙功能中開啟 del-adv。\n2. 在應用程式管理中允許背景活動／自動啟動（名稱依 MIUI / HyperOS 版本而異）。\n3. 將電池使用策略設定為「無限制」（若有此選項）。\n4. 回到手機首頁，向左滑至 Google Discover 測試。")
        text("注意：本 App 不會真正刪除 Google 卡片或移動下方新聞。遮罩可以點擊顯示。這是初版，不保證每台小米手機的 Google Discover 都能辨識。")
        text("不需要 Root，也不讀取其他 App，不上傳任何畫面內容。TypeSafe AI API 尚未整合到此 Android 版本。")
        setContentView(ScrollView(this).apply { addView(column, ViewGroup.LayoutParams(-1, -2)) })
    }
    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) status.text =
            if (enabledService()) "無障礙服務：已授權" else "無障礙服務：尚未授權（需手動開啟）"
    }
}
