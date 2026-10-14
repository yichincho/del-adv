package com.yichincho.deladv

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class DiscoverService : AccessibilityService() {
 private val manager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
 private val overlays = mutableListOf<View>()
 private val markers = Regex("^(廣告|贊助|廣告內容|sponsored|ad|advertisement|promoted)$",RegexOption.IGNORE_CASE)
 private val googlePackage="com.google.android.googlequicksearchbox"
 override fun onAccessibilityEvent(event:AccessibilityEvent?) {
  if(event?.packageName?.toString()!=googlePackage){clear();return}
  if(!getSharedPreferences("settings",MODE_PRIVATE).getBoolean("enabled",true)){clear();return}
  val root=rootInActiveWindow ?: run {clear();return}
  try { refresh(root) } finally { root.recycle() }
 }
 private fun refresh(root:AccessibilityNodeInfo){
  clear()
  val viewport=resources.displayMetrics
  val rects=mutableListOf<Rect>()
  fun visit(node:AccessibilityNodeInfo,depth:Int){
   if(depth>24 || rects.size>=6)return
   val text=node.text?.toString()?.trim() ?: ""
   val desc=node.contentDescription?.toString()?.trim() ?: ""
   if(markers.matches(text) || markers.matches(desc)){
    val label=Rect();node.getBoundsInScreen(label)
    if(label.width()>0 && label.height()>0){
     // Prefer the immediate accessible card parent, but never mask the whole page.
     val parent=node.parent
     val area=Rect()
     if(parent!=null){parent.getBoundsInScreen(area);parent.recycle()}
     val chosen=if(area.width() in 160..viewport.widthPixels && area.height() in 90..(viewport.heightPixels/2)) area else label
     if(chosen.width()>0 && rects.none{Rect.intersects(it,chosen)})rects.add(Rect(chosen))
    }
   }
   for(i in 0 until node.childCount){
    val child=node.getChild(i) ?: continue
    visit(child,depth+1)
    child.recycle()
   }
  }
  visit(root,0)
  rects.forEach(::overlay)
 }
 private fun overlay(area:Rect){
  val text=TextView(this).apply{
   text="廣告已遮罩 · 點此顯示"; textSize=14f; gravity=Gravity.CENTER
   setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(16,35,62))
   setOnClickListener{try{manager.removeView(this)}catch(_:Exception){};overlays.remove(this)}
  }
  val lp=WindowManager.LayoutParams(area.width(),area.height(),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
     WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT)
  lp.gravity=Gravity.TOP or Gravity.LEFT;lp.x=area.left;lp.y=area.top
  try{manager.addView(text,lp);overlays.add(text)}catch(_:Exception){}
 }
 private fun clear(){overlays.toList().forEach{try{manager.removeView(it)}catch(_:Exception){}};overlays.clear()}
 override fun onInterrupt(){clear()}
 override fun onDestroy(){clear();super.onDestroy()}
}