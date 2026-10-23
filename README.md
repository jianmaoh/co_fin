# Co-Fin ☕

收藏你喜歡的咖啡廳、幫它們加上標籤、整理成自己的清單。

目前進度：帳號註冊與登入。

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

## 設定（環境變數）

| 變數 | 預設值 | 說明 |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/cofin` | JDBC URL |
| `DB_USER` | `cofin` | 資料庫帳號 |
| `DB_PASSWORD` | `cofin` | 資料庫密碼 |

## 測試

```bash
./mvnw test      # 單元測試和功能測試（H2，不需要 Docker）
./mvnw verify    # 再加上 *IT 整合測試（Testcontainers + 真的 PostgreSQL，需要 Docker）
```

## 健康檢查

- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
