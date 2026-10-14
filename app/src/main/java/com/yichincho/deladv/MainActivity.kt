package com.yichincho.deladv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("settings", MODE_PRIVATE)
  val container = LinearLayout(this).apply {
    orientation=LinearLayout.VERTICAL; setPadding(44,60,44,30); setBackgroundColor(Color.rgb(247,250,252))
  }
  fun addText(s:String, size:Float=16f) { container.addView(TextView(this).apply{ text=s; textSize=size; setTextColor(Color.rgb(21,39,55)); setPadding(0,12,0,12) }) }
  addText("🛡 del-adv",28f)
  addText("Google Discover 廣告遮罩（測試版）")
  addText("只偵測 Google App 可存取的「廣告／贊助」標記，不會刪除 Google Discover 卡片或重新排版。")
  val enabled=Switch(this).apply{ text="啟用廣告遮罩"; isChecked=prefs.getBoolean("enabled",true);setOnCheckedChangeListener{_,value->prefs.edit().putBoolean("enabled",value).apply()}}
  container.addView(enabled)
  val button=Button(this).apply{text="開啟無障礙設定";setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}}
  container.addView(button)
  addText("請至「已下載的應用程式／del-adv」授權後，回到首頁向左滑測試。只在 Google App 啟用。遮罩點擊可移除；退出 Google 畫面時自動撤除。")
  addText("此版不需要 API Key；AI 判斷功能仍待加入原生版本。")
  val scroll=ScrollView(this).apply{addView(container)}
  setContentView(scroll)
 }
}