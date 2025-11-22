# README.md

# Spring Boot OIDC Client

このプロジェクトは、Spring Boot 4.0.0をベースにしたOIDCクライアントアプリケーションです。Home 画面から認可コードフロー＋PKCEを操作でき、Authorization UI・Callback 表示・Token リエントリを明確に分離した学習／検証用クライアントです。

## プロジェクトの構成

| レイヤ | パス | 役割 |
| --- | --- | --- |
| アプリケーション | `spring-boot-oidc-client/src/main/java/com/example/oidcclient/OidcClientApplication.java` | Spring Boot エントリーポイント。`@ConfigurationPropertiesScan` で各設定を登録。 |
| Controller | `spring-boot-oidc-client/src/main/java/com/example/oidcclient/controller/HomeController.java` | Home 画面を表示し、エントリポイントを提供。 |
| Controller | `spring-boot-oidc-client/src/main/java/com/example/oidcclient/controller/AuthorizationFlowController.java` | 認可フォームの表示と `/authorize` リダイレクト生成。 |
| Controller | `spring-boot-oidc-client/src/main/java/com/example/oidcclient/controller/CallbackController.java` | `/callback` のクエリ/エラーを `CallbackViewModel` へマッピング。 |
| Controller | `spring-boot-oidc-client/src/main/java/com/example/oidcclient/controller/TokenController.java` | `/token-request` POST を受け、PKCE 情報を `SessionStateService` から取得して mTLS Token リクエストを発行。 |
| Service | `spring-boot-oidc-client/src/main/java/com/example/oidcclient/service/SessionStateService.java` | HttpSession に保存する state/nonce/code_verifier/code_challenge_method を集約管理。 |
| Service | `spring-boot-oidc-client/src/main/java/com/example/oidcclient/service/PkceService.java` | PKCE の code_verifier / code_challenge を生成。 |
| Service | `spring-boot-oidc-client/src/main/java/com/example/oidcclient/service/TokenResponseValidator.java` | Token エンドポイントからの JSON を解析し、`id_token` または `access_token` を `IdTokenValidator` に連携。 |
| Service | `spring-boot-oidc-client/src/main/java/com/example/oidcclient/service/IdTokenValidator.java` | JWKS 署名・iss/aud/azp/exp/iat/nonce/`at_hash` を検証。`id_token` が無い場合は `access_token` をサロゲートとして検証。 |
| View | `spring-boot-oidc-client/src/main/resources/templates/*.html` | Thymeleaf テンプレート（`authorization_flow.html`, `callback.html`, `home.html`）。 |
| Test | `spring-boot-oidc-client/src/test/java/com/example/oidcclient/controller/*Test.java` | MockMvc ベースのコントローラテストおよび統合テスト。 |
| Documentation | `local/0.4.コントローラ責務分離計画.md` | Controller 責務分離とルーティング設計のメモ。 |

## アーキテクチャ概要

### Controller と責務

| Controller | 役割 | 主な入出力 |
|------------|------|-------------|
| `AuthorizationFlowController` | 認可フォーム表示、`/authorize` リダイレクト生成 | PKCE 生成、state/nonce のセッション保存、追加パラメータ編集 |
| `CallbackController` | 認可応答の可視化 | クエリパラメータを `CallbackViewModel` に集約し、Thymeleaf へバインド |
| `TokenController` | Token エンドポイントへの再リクエスト | セッションから code_verifier を引き当て、`OidcClientService` 経由で mTLS POST |

### SessionStateService

`SessionStateService` が HttpSession を直接扱う唯一の場所です。state/nonce/code_verifier/code_challenge_method を保存・読込・クリアし、Controller からセッション操作を排除します。PKCE のクリアタイミングは Callback/Token の完了時にサービス側で統一しています。

### フロー概要

1. `GET /authorization-flow` で PKCE バンドルを生成し、フォーム初期表示を行う。
2. `POST /authorize` で Keycloak の認可エンドポイントへリダイレクトし、state/nonce/code_challenge を付与。
3. Keycloak から `GET /callback` へリダイレクトされ、クエリを `callback.html` に表示。PKCE セッションスナップショットも併せて確認可能。
4. UI から `POST /token-request` を実行すると `TokenController` が code_verifier を復元し、`OidcClientService`→`TokenClientService` で token エンドポイントへ POST。
5. トークンレスポンスは `TokenResponseValidator` が JSON パースし、`IdTokenValidator` で署名・必須クレームを検証。Keycloak が `id_token` を返さない構成でも `access_token` を JWT として検証するフォールバックを実装済みで、`azp` が `client_id` と一致しない場合は即座にエラーとなる。

## 設定プロパティ

`@ConfigurationProperties` を利用して主要な設定をグルーピングしています。`spring-boot-oidc-client/src/main/resources/application.properties` では以下のキーを調整してください。

| プレフィックス | 主な項目 | 利用箇所 |
| --- | --- | --- |
| `application.oidc.*` | `host`, `context-path` | `OidcClientProperties` → `OidcClientService` が認可/トークンエンドポイントを組み立てる際に利用。 |
| `application.oidc.client-id`, `application.oidc.issuer` | 固定の `client_id`, `iss` 期待値 | `IdTokenValidator` が `aud`/`azp`/`iss` を照合する際に参照。 |
| `application.keycloak.mtls.*` | `key-store`, `trust-store` など | `MtlsProperties` → `TokenClientService` が mTLS 用 SSLContext を構築。 |
| `application.pkce.*` | `code-verifier-size` | `PkceProperties` → `PkceService` が PKCE のサイズバリデーションに使用。 |
| `application.path.*` | `root`, `home`, `authorization-flow` など | `AppPathProperties` → `SecurityConfig` やコントローラのリクエストマッピングで共有。 |

テスト用の `src/test/resources/application.properties` にも mTLS 系のキーを配置しており、`./mvnw test` 実行時に同じ構成で解決されます。

## セットアップ

### 1. KeycloakとMySQLの起動

1. `docker-compose.yml` と同じ階層に `.env` を作成し、テスト用の資格情報を定義します（値は必要に応じて変更してください）。

   ```bash
   cat <<'EOF' > .env
   MYSQL_ROOT_PASSWORD=changeit
   MYSQL_DATABASE=keycloak
   MYSQL_USER=keycloak
   MYSQL_PASSWORD=keycloak

   KEYCLOAK_ADMIN=admin
   KEYCLOAK_ADMIN_PASSWORD=admin

   KC_DB=mysql
   KC_DB_URL=jdbc:mysql://mysql:3306
   KC_DB_URL_DATABASE=keycloak
   KC_DB_USERNAME=${MYSQL_USER}
   KC_DB_PASSWORD=${MYSQL_PASSWORD}
   EOF
   ```

2. `certs/` および `ssl/` フォルダの証明書が存在することを確認します（`docker-compose.yml` では Keycloak の HTTPS 証明書と truststore をホストからマウントします）。
3. Docker と Compose のバージョンを確認したうえで、コンテナを起動します。

   ```bash
   docker compose version
   docker --version
   docker compose up -d
   ```

4. `docker compose ps` で `mysql` のヘルスチェックが `healthy` になった後、Keycloak にアクセスしてレルムやクライアント設定を行います。詳細は `local/0.7.ドキュメントとdocker-composeの更新.md` の手順にも記載しています。

### 2. OIDC認可サーバアプリケーション（Keycloak）のアプリケーション設定

1. Keycloakの設定
   - 新しいレルムを作成
   - クライアントを作成し、クライアントIDとシークレットを取得
   - リダイレクトURIを設定: `https://localhost:8081/*`
   - PKCEをS256で有効化
2. 詳細は以下を参照
   - https://www.keycloak.org/getting-started/getting-started-docker

### 3. OIDCクライアントアプリケーションの起動

#### Maven Wrapperを使用する場合

1. このリポジトリをクローンします。
2. プロジェクトのルートディレクトリで以下のコマンドを実行して依存関係をインストールします。

   ```bash
   ./mvnw install
   ```

3. アプリケーションを起動します。

   ```bash
   ./mvnw spring-boot:run
   ```

#### mvnを使用する場合

1. プロジェクトのディレクトリに移動します。
2. 以下のコマンドでアプリケーションをビルドします。

   ```bash
   mvn clean package
   ```

3. 生成されたJARファイルを実行します。

   ```bash
   java -jar target/spring-boot-oidc-client-0.0.1-SNAPSHOT.jar
   ```

4. ブラウザで `https://localhost:8081` にアクセスすると、Home 画面から認可コードフローを開始できます。`/authorization-flow` → `/callback` → `/token-request` の各画面でログが記録され、PKCE の状態を追跡できます。

### 4. ビルドとテスト

Controller 責務分離が意図した通り動いているかは Maven のビルドで検証できます。主要なコマンドは次の通りです。

| シナリオ | コマンド | 備考 |
| --- | --- | --- |
| CI 相当 (`spring-boot-oidc-client` モジュールのみ) | `./mvnw clean test` | MockMvc テスト＋`TokenClientServiceMtlsSmokeTest`(タグ付き) 以外を実行 |
| WSL などローカル Maven | `cd spring-boot-oidc-client`<br>`mvn clean package` | Wrapper を使わずに jar を生成。`target/` 配下の jar を直接起動可能 |
| mTLS スモークテスト込み | `cd spring-boot-oidc-client`<br>`ENABLE_MTLS_TESTS=true ./mvnw test` | `@Tag("mtls")` テストを opt-in 実行。`TokenClientServiceMtlsSmokeTest` で keystore/truststore 解決を確認 |

個別のテストクラスを狙い撃ちしたい場合は `-Dtest=...` や `-Djunit.jupiter.tags=mtls` を併用してください。

#### テスト用証明書と mTLS 検証

- アプリ／テストの双方で `spring-boot-oidc-client/src/main/resources/ssl/*.p12` を読み込みます。`src/test/resources/application.properties` には `application.keycloak.mtls.*` が本番と同じ値で定義されているため、証明書ファイルを削除しない限り追加コピーは不要です。
- mTLS の実証用スモークテストとして `TokenClientServiceMtlsSmokeTest` を追加済みです。環境変数 `ENABLE_MTLS_TESTS=true` を付けた実行のみで起動し、CI ではスキップされます。`-Djunit.jupiter.tags=mtls` と組み合わせればタグ単位での実行制御も可能です。
- 同様に `mvn clean package` でも `ENABLE_MTLS_TESTS=true` を付ければスモークテストを含めてビルドできます。

```bash
cd spring-boot-oidc-client
ENABLE_MTLS_TESTS=true mvn clean package
```

## ドキュメントリンク

- 証明書や検証結果の補足: `docs/1.1.証明書情報.md`, `docs/1.2.各種証明書検証結果.md`