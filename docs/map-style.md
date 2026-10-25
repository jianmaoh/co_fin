# 自訂地圖樣式（黑底白線）

沒有設定時，網站使用 Google 內建的深色主題（`DEMO_MAP_ID` + dark color scheme）。
想要「黑底、白色道路線條」的科技感樣式，在 Google Cloud 建一個自己的 Map ID：

1. Google Cloud Console → **Google Maps Platform → 地圖樣式（Map Styles）→ 建立樣式**
2. 選 **匯入 JSON**，貼上同資料夾的 [`map-style.json`](map-style.json)，儲存
3. **地圖管理（Map Management）→ 建立 Map ID**：類型選 **JavaScript**、**向量（Vector）**
4. 把剛才的樣式套用到這個 Map ID
5. 在專案根目錄的 `.env` 加一行，重新 `docker compose up`：

   ```
   GOOGLE_MAPS_MAP_ID=你的 Map ID
   ```

為什麼不直接在程式裡寫樣式？
網站用的 Advanced Markers（可以自訂樣式的地圖標記）一定要搭配 Map ID，
而有 Map ID 的地圖樣式只能在 Google Cloud 上設定，程式裡的 `styles` 參數會被忽略。

`map-style.json` 的主要設定：底色 `#0b0d10`、高速公路純白、主要道路淺灰白、
小巷暗灰、水域深藍灰，並隱藏商家圖示和大眾運輸，讓 Co-Fin 的標記更清楚。
