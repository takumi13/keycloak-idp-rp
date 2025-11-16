# README.md

# Spring Boot OIDC Client

このプロジェクトは、Spring Boot 4.0.0をベースにしたOIDCクライアントアプリケーションです。Home 画面から認可コードフロー＋PKCEを操作でき、Authorization UI・Callback 表示・Token リエントリを明確に分離した学習／検証用クライアントです。

## プロジェクトの構成

- `spring-boot-oidc-client/src/main/java/com/example/oidcclient/OidcClientApplication.java`: エントリーポイント。
- `spring-boot-oidc-client/src/main/java/com/example/oidcclient/controller/HomeController.java`: Home 画面。
- `spring-boot-oidc-client/src/main/java/com/example/oidcclient/controller/AuthorizationFlowController.java`: 認可フォーム表示と `/authorize` リクエスト生成を担当。
- `spring-boot-oidc-client/src/main/java/com/example/oidcclient/controller/CallbackController.java`: `/callback` で受信したクエリ/エラーを `CallbackViewModel` にまとめてビューへ渡す。
- `spring-boot-oidc-client/src/main/java/com/example/oidcclient/controller/TokenController.java`: `/token-request` POST のみを扱い、PKCE再利用を `SessionStateService` に委譲。
- `spring-boot-oidc-client/src/main/java/com/example/oidcclient/service/SessionStateService.java`: HttpSession 上の state/nonce/code_verifier を一元管理。
- `spring-boot-oidc-client/src/main/java/com/example/oidcclient/service/PkceService.java`: PKCE の code_verifier / code_challenge を生成。
- `spring-boot-oidc-client/src/main/resources/templates/*.html`: Thymeleaf テンプレート (`authorization_flow.html`, `callback.html`, `home.html`)。
- `spring-boot-oidc-client/src/test/java/com/example/oidcclient/controller/*Test.java`: MockMvc ベースのコントローラテスト群。
- `local/0.4.コントローラ責務分離計画.md`: Controller 責務分離に関する設計メモ。

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

詳細な検討は `local/0.4.コントローラ責務分離計画.md` を参照してください。

## 設定プロパティ

`@ConfigurationProperties` を利用して主要な設定をグルーピングしています。`spring-boot-oidc-client/src/main/resources/application.properties` では以下のキーを調整してください。

| プレフィックス | 主な項目 | 利用箇所 |
| --- | --- | --- |
| `application.oidc.*` | `host`, `context-path` | `OidcClientProperties` → `OidcClientService` が認可/トークンエンドポイントを組み立てる際に利用。 |
| `application.keycloak.mtls.*` | `key-store`, `trust-store` など | `MtlsProperties` → `TokenClientService` が mTLS 用 SSLContext を構築。 |
| `application.pkce.*` | `code-verifier-size` | `PkceProperties` → `PkceService` が PKCE のサイズバリデーションに使用。 |
| `application.path.*` | `root`, `home`, `authorization-flow` など | `AppPathProperties` → `SecurityConfig` やコントローラのリクエストマッピングで共有。 |

テスト用の `src/test/resources/application.properties` にも mTLS 系のキーを配置しており、`./mvnw test` 実行時に同じ構成で解決されます。

## セットアップ

### 1. KeycloakとMySQLの起動

```bash
$ docker compose version
Docker Compose version v2.35.1

$ docker --version
Docker version 28.1.1, build 4eba377

$ docker compose up
```

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

#### WSL（Windows Subsystem for Linux）を使用する場合

1. WSLターミナルでプロジェクトのディレクトリに移動します。
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

Controller 責務分離が意図した通り動いているかは Maven のビルドで検証できます。

```bash
./mvnw clean package
```

MockMvc ベースのテスト (`IntegrationAuthTokenFlowTest` など) が実行され、PKCE の再利用やセッションのクリアが確認できます。特定のテストを実行したい場合は次の通りです。

```bash
./mvnw -pl spring-boot-oidc-client test -Dtest=com.example.oidcclient.controller.IntegrationAuthTokenFlowTest
```

## ドキュメントリンク

- Controller 再設計の詳細: `local/0.4.コントローラ責務分離計画.md`
- 既存の証明書・依存関係メモ: `local/*.md`, `docs/*.md`

## ライセンス

このプロジェクトはMITライセンスの下で提供されています。