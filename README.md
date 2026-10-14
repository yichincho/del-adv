# del-adv — TypeSafe AI 廣告視覺隱藏（Chrome Extension MVP）

本專案是 **桌面版 Chrome/Edge 網頁擴充功能** 的第一版，不是 Android APK。使用者可以只啟用本地規則，或填入自己的 TypeSafe API Key，使用 Jev System One 的 Noul 機率判斷候選區塊是否為廣告。達到設定門檻才隱藏。API 失敗時不會刪除不確定的內容。

## 安裝／測試
1. 將本 repo 下載為 ZIP 並解壓縮。
2. Chrome 開啟 chrome://extensions （Edge 為 edge://extensions），開啟「開發人員模式」。
3. 按「載入未封裝項目」，選取包含 manifest.json 的資料夾。
4. 在擴充功能彈窗啟用功能；如需 AI 判斷，可在 https://console.typesafe.ai 取得 API Key 後填入。
5. 在一般網頁測試。測試用頁見 tests/fixtures.html。

## 流程
DOM 候選節點 → 常見「廣告／贊助」規則 → 不確定者截斷可見文字 → TypeSafe Jev Noul → threshold（預設 0.92）→ CSS display:none。

## 隱私、安全、限制
- API Key **只存在 chrome.storage.local**，不提交 GitHub。個人用測試可行；正式發布請改成後端代理及金鑰保護機制。擴充功能無法保證抵禦本機惡意程式或其他有權限的擴充功能。
- 若有填入 API Key，至多每輪送出 8 個候選區塊（每個文字最多 500 字元）到 TypeSafe。可能含可辨識個人資訊；機敏網頁不可啟用。
- 此版屬 MVP，尚未完成網站白名單、長期快取、誤判回復按鈕、測試覆蓋率、API 成本限制。
- 此版掃描可能漏掉影片、Canvas、封閉 Shadow DOM、iframe 或原生 App 廣告；過於寬泛的候選選取也可能誤隱藏一般內容。**不保證所有網站可用**。
- Android Accessibility API 可讀取部分 UI 與畫遮罩，但**不能直接修改其他 App 的 View 階層**。要做 Android 原生版需另外設計可逆遮罩、逐 App 同意、敏感 App 排除等機制。

## TypeSafe skill
依官方指令，在已安裝 Node.js 的 coding agent 專案中執行：
```bash
npx skills add typesafe-ai/skills --skill typesafe-ai
```
並選取 agent。參考 https://github.com/typesafe-ai/skills/blob/main/skills/typesafe-ai/SKILL.md 與 https://docs.typesafe.ai/api。
本次工作環境的 npx 安裝因網路逾時，故**沒有宣稱成功安裝**；實作參考了 skill 全文與 TypeSafe 官方 HTTP API。
