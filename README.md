# del-adv — Android Google Discover 廣告遮罩

**狀態：v0.4.0。APK 由 GitHub Actions 雲端編譯（見下方「取得 APK」）；尚未在小米實機驗證。**

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
4. **TypeSafe Jev AI 判定為選用功能**（見下方「AI 判定」），預設關閉；不開啟時沒有任何資料離開手機。
5. Android Accessibility 服務對資訊有較高權限，請只從信任的原始碼編譯、明確同意後才啟用。上架 Play Store 須遵守其無障礙政策。

## AI 判定：TypeSafe Jev（選用，預設關閉）

架構移植自 [fazlerocks/jev-adblock](https://github.com/fazlerocks/jev-adblock)（MIT），API 用法同 [realZachi/typesafe-adblock](https://github.com/realZachi/typesafe-adblock) 與官方 [typesafe-sdk-js](https://github.com/typesafe-ai/typesafe-sdk-js)：

- 兩層判定：
  1. **本機規則**（免費、不連網）：卡片內有「贊助／廣告／Sponsored」標籤 → 直接遮罩。
  2. **Jev**：沒有標籤的動態牆卡片，整批送 `POST https://api.typesafe.ai/v1/systemone`（`jev-latest`）。每張卡一個 `choice` 問題，選項為 `sponsored_ad / news_article / google_promo / feed_ui`。
- 門檻由程式掌握：`sponsored_ad ≥ 0.8`、比其他類別合計高 `0.35`、`confidence ≥ 0.4` 才遮罩（`JevConfig`）。
- 結果依卡片指紋（本機 SHA-256）快取 7 天，同一張卡不重送。長按遮罩可標記「不是廣告」並永久記住。
- 每日 token 預算 200k（約 US$0.008）。API 連續失敗 5 次就暫停 5 分鐘；Key 無效時停止送出。**任何錯誤一律不遮罩（fail open）**。
- 送出前提：已填 API Key、已勾選資料揭露同意、已開啟「啟用 AI 判定」。三者缺一，什麼都不送。
- 送出內容：每張卡最多 150 字文字（標題、來源）、尺寸、元件 id、圖片數、本機訊號。含可輸入欄位的卡片永不描述。不送網址、截圖或帳號。
- API Key 存在 App 私有儲存（`allowBackup=false`），只送往 `api.typesafe.ai`。

## 取得 APK（雲端編譯，不需本機 Android Studio）
**直接下載最新版：<https://github.com/yichincho/del-adv/releases/latest/download/del-adv.apk>**

1. 用手機瀏覽器開上面的連結，下載後點開安裝（需允許「安裝未知來源 App」）。
2. 每次推到 `main`，GitHub Actions 會跑測試、編譯，並自動發佈新的 Release；連結永遠指向最新版。
3. 所有版本與 SHA256：<https://github.com/yichincho/del-adv/releases>
4. 這是 debug 簽章，每次雲端編譯的簽章不同：**升級前要先解除安裝舊版**。

## 實機驗收步驟（小米）
1. 確認向左滑看到的是 **Google Discover**，不是小米「智能助理／App Vault」。
2. 開啟 del-adv 的無障礙服務，App 內「無障礙服務」顯示「已授權」。
3. 在 App 按「錄製下一次 Discover 畫面結構」，到 Discover 捲到有「贊助／廣告」的卡片，停 1 秒，回 App。
4. App「實機診斷」會顯示：掃描 N 次 / 偵測標籤 N 次 / 遮罩 N 次。
   - 掃描 = 0：服務沒收到 Discover 事件（多半是負一屏不是 Google，或服務被系統關閉）。
   - 掃描 > 0、標籤 = 0：廣告標籤文字不符規則 → 按「分享畫面結構」把檔案交給開發者調規則。
   - 標籤 > 0、遮罩 = 0：找不到合理大小的卡片容器 → 同樣分享畫面結構。
5. 畫面結構只含元件類型、座標、16 字以內短文字；新聞標題只記錄字數。

## Android Studio 建置（選用）
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

## Xiaomi / Redmi / POCO（無 Root）

在 MIUI / HyperOS 的「設定 → 更多設定 → 無障礙」中，找到已下載服務的 del-adv 並手動同意。不同型號可能位於「設定 → 其他設定」下。再至「應用程式 → del-adv」允許自動啟動／背景執行（若有選項），並將電池策略設為無限制。**不建議用 ADB 自動授權無障礙，也不需要 Root。**

2026-10-14 v0.3.0：改掃描所有互動視窗中屬於 Google App 的視窗（負一屏 Discover 是疊在桌面上的 Google 視窗，`rootInActiveWindow` 常拿到桌面）；遮罩顯示時每 500 ms 檢查 Google 視窗是否仍在，離開即撤除（事件有 package 過濾，回桌面不會收到事件）；捲動時立即隱藏遮罩；標籤允許「贊助 · 來源」格式；判定規則抽成純函式並有單元測試；加入本機診斷錄製。

2026-10-14 v0.2 修正：只處理 Google App package；對事件做 400 ms debounce；尋找保守的完整廣告標籤；從合理大小的卡片容器推測位置；若無可靠矩形不遮罩；同一矩形不重複掛載 overlay；手動點擊可以暫時顯示；離開 Google App 立即撤除。此程式尚未經 Android Studio build / 真機測試。

有些小米手機在省電／清理過程中可能停止無障礙服務；此時要回系統設定檢查，並非程式能無條件自動重啟。參考 [HyperOS Accessibility Guard](https://github.com/haku4130/hyperos-accessibility-guard)。
