# del-adv — Android Google Discover 廣告遮罩

**狀態：Android 原生 MVP 原始碼（尚未編譯、尚未在實機驗證）。**

這個專案目標是手機桌面向左滑的 **Google Discover 新聞資訊流**，不是瀏覽器擴充功能。

## 已實作
- Android Kotlin 專案，Android 8+（minSdk 26）。
- 新 App icon：「青綠盾牌、白色喇叭、紅色禁止斜線」，見 `app/src/main/res/drawable/ic_launcher.xml`。
- 使用者自行到 Android 無障礙設定開啟服務。
- 只訂閱 Google App `com.google.android.googlequicksearchbox` 的 UI accessibility events。
- 偵測無障礙文字 **完整匹配**「廣告」「贊助」「Sponsored」「Ad」等文字，使用 Accessibility overlay 蓋住對應卡片區域；可點擊撤除。
- App 主畫面開關。

## 關鍵限制
1. **不能真正從其他 App 移除卡片或重新排版**：Android 一般 App 沒有修改 Google App UI tree 的權限；本方法只能遮罩。遮罩可能只是覆蓋標籤，無法正確定位整張卡。
2. Google Discover 的介面與可見的無障礙樹因手機型號、Android/Google App 版本、語系而異。**目前沒有實機數據驗證效果**。
3. 如需真正將卡片移除，有現成參考 [hxreborn/discover-ads-filter](https://github.com/hxreborn/discover-ads-filter)，但它依賴 LSPosed，不適用一般未 Root 的手機。
4. **TypeSafe API Key/AI 判定尚未整合進 Android**：先確認原生可讀到廣告標籤，避免上傳私人新聞卡文字造成不必要的風險；下一階段可加入可選 TypeSafe Jev 並加上限流、快取、敏感資料排除、明確告知。
5. Android Accessibility 服務對資訊有較高權限，請只從信任的原始碼編譯、明確同意後才啟用。上架 Play Store 須遵守其無障礙政策。

## Android Studio 建置
1. 將 repository clone / ZIP 解壓，使用 Android Studio（含 Android SDK 35）開啟根資料夾。
2. 使用 JDK 17，Gradle sync，Build > Build APK(s)。
3. 安裝 APK、開啟 del-adv，再點「開啟無障礙設定」，授權 del-adv。
4. 從 Android 手機首頁向左滑開 Google Discover，觀察帶「廣告／贊助」標記的卡片是否被遮罩。
5. 如有問題立即在無障礙設定關閉 del-adv。

本 repo 先前誤做 Chrome extension，已移除相關檔案。

## TypeSafe skill
使用官方命令於有 Node/npm 且網路通的 coding agent 安裝：
```sh
npx skills add typesafe-ai/skills --skill typesafe-ai
```
參考 [skill 來源](https://github.com/typesafe-ai/skills/blob/main/skills/typesafe-ai/SKILL.md)。此次已閱讀 skill，但無法確認本環境完成 npm 安裝。
