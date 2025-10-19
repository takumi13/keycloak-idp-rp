# README.md

# Spring Boot OIDC Client

このプロジェクトは、Spring Boot 4.0.0をベースにしたOIDCクライアントアプリケーションです。このアプリケーションは、シンプルな「Hello World」メッセージを表示するWebアプリケーションです。

## プロジェクトの構成

- `spring-boot-oidc-client/src/main/java/com/example/oidcclient/OidcClientApplication.java`: アプリケーションのエントリーポイントで、Spring Bootアプリケーションを起動します。
- `spring-boot-oidc-client/src/main/java/com/example/oidcclient/controller/HelloController.java`: HTTP GETリクエストを処理し、「Hello World」メッセージを返すコントローラークラスです。
- `spring-boot-oidc-client/src/main/resources/application.properties`: アプリケーションの設定プロパティを含むファイルです。
- `spring-boot-oidc-client/src/main/resources/static`: CSS、JavaScript、画像などの静的リソースを提供するためのディレクトリです。
- `spring-boot-oidc-client/src/main/resources/templates/hello.html`: Thymeleafテンプレートで、「Hello World」メッセージを表示します。
- `src/test/java/com/example/oidcclient/OidcClientApplicationTests.java`: アプリケーションのユニットテストを含むファイルです。
- `src/test/java/com/example/oidcclient/controller/HelloControllerTest.java`: HelloControllerクラスの機能を検証するためのユニットテストを含むファイルです。

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
   - リダイレクトURIを設定: `http://localhost:8081/*`
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

4. ブラウザで `http://localhost:8081` にアクセスすると、「Hello World」メッセージが表示されます。「Authorization Flow を開始する」のリンクから認可コードフロー+PKCEのリクエストを認可サーバ（keycloak）に対して実行してみてください。

## ライセンス

このプロジェクトはMITライセンスの下で提供されています。