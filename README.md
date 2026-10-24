# Co-Fin ☕

收藏你喜歡的咖啡廳、幫它們加上標籤、整理成自己的清單。

## 功能

- 帳號註冊與登入
- 咖啡廳：大家共用的資料，可從 Google Maps 搜尋新增（或手動輸入）、依店名搜尋、依標籤篩選，頁面上顯示地圖
- 標籤：每個人可以幫咖啡廳加自己的標籤，頁面顯示每個標籤有幾個人同意
- 我的清單：建立、改名、刪除清單，把咖啡廳加進清單並附上備註；清單只有自己看得到

## 資料模型

```
app_user ─┬─< cafe_collection ─< collection_item >─┐
          ├─< cafe_tag >── tag                      │
          │      └────────────────> cafe <──────────┘
          └─ (created_by) ────────> cafe
```

刪除使用者時，該使用者的清單、清單項目和標籤會一起刪除（`on delete cascade`）；
該使用者新增的咖啡廳是共用資料，會保留下來，只把 `created_by` 設成 null。

## 技術

- Java 25、Spring Boot 4.1
- Spring Security（表單登入、BCrypt、CSRF 保護）
- Spring Data JPA，資料庫 schema 由 Flyway 管理
- PostgreSQL（正式環境）；H2（本機開發，不需要 Docker）
- Thymeleaf
- 測試：JUnit 5、Mockito、MockMvc、Testcontainers

## 本機執行

### 方法一：全部用 Docker（只需要 Docker，不用裝 Java）

```bash
docker compose up --build
```

會啟動 PostgreSQL 和網站，打開 http://localhost:8080 。按 Ctrl+C 停止；
`docker compose down -v` 會連資料庫內容一起清掉。

### 方法二：進到 Java 容器裡操作（不用裝 Java）

```bash
docker run -it --rm -v "$PWD":/app -w /app -p 8080:8080 eclipse-temurin:25-jdk bash
# 進到容器之後：
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # H2 模式
./mvnw test                                             # 跑測試
```

### 方法三：直接在電腦上跑（需要 Java 25）

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # H2，不需要 Docker
# 或
docker compose up -d postgres && ./mvnw spring-boot:run # 連 localhost:5432/cofin
```

H2 模式的資料存在 `./data/`，刪掉這個資料夾就能重來。

## Google Maps（選用）

沒有設定金鑰時，網站照常運作，只是改成手動輸入店名和地址。

1. 在 Google Cloud 啟用 **Maps JavaScript API** 和 **Places API (New)**，建立一把 API 金鑰
2. 金鑰限制：應用程式限制選「網站」並加入 `http://localhost:8080/*`；API 限制只勾上面兩個 API
3. 在兩個 API 的「配額」設定每日上限，避免意外費用
4. 在專案根目錄建立 `.env`（已被 git 忽略）：

   ```
   GOOGLE_MAPS_API_KEY=AIza...
   ```

   `docker compose` 會自動讀取它。

這是瀏覽器用的金鑰，打開網頁的人都看得到；保護它的是上面的網站限制、API 限制和每日配額。

資料儲存上，Google 的條款允許長期保存 place ID，地點的其他內容則有快取限制。
所以座標不存進資料庫，每次顯示地圖時用 place ID 即時查詢；
店名和地址是使用者在表單上確認後送出的文字，可以修改。

## 設定（環境變數）

| 變數 | 預設值 | 說明 |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/cofin` | JDBC URL |
| `DB_USER` | `cofin` | 資料庫帳號 |
| `DB_PASSWORD` | `cofin` | 資料庫密碼 |
| `GOOGLE_MAPS_API_KEY` | （空白） | Google Maps 瀏覽器金鑰，空白時改為手動輸入 |

## 測試

```bash
./mvnw test      # 單元測試和功能測試（H2，不需要 Docker）
./mvnw verify    # 再加上 *IT 整合測試（Testcontainers + 真的 PostgreSQL，需要 Docker）
```

## 健康檢查

- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
