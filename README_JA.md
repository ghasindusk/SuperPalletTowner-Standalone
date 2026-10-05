# 超マサラ人 — NeoOrigins アドオン

Minecraft 1.21.1 / NeoForge 向けの配布候補 `1.0.0-rc.1` です。超マサラ人を NeoOrigins の通常 Origin として追加します。龍人専用 Sub-Origin は含みません。

## できること

- 元気な手持ちポケモンのタイプから最大3種類を選び、タイプ共鳴の恩恵を受けます。
- 共鳴タイプを持つポケモンにも、Minecraft の Mob 相手を中心に上限付きの返礼効果が届きます。
- 2〜3タイプの共鳴リンクと重奏、効果確認画面、タイプ表示 HUD を使用できます。HUD は NeoOrigins の HUD 編集画面から位置とサイズを調整できます。
- 既存の Monster Tamer 選択者は通常 Origin の超マサラ人へ自動移行します。初回や手持ち0匹では全滅帰還が発動しません。

## 必須環境

Minecraft 1.21.1、NeoForge 21.1.251、Cobblemon 1.8.1、NeoOrigins 2.2.29、Cobblemon: Mega Showdown 1.2.0。サトシの帽子は Mega Showdown のアイテムを参照し、画像データは同梱しません。クライアントとサーバーの両方に同じ MOD を入れてください。

## 導入

1. ワールドと現在の `mods` / `config/originpacks` をバックアップします。
2. `super_pallet_towner-1.0.0-rc.1.jar` を `mods` に入れます。同じ MOD の Jar を複数入れないでください。
3. 以前の Starlight Fusion 専用版から移行する場合、`config/originpacks/StarlightSuperPalletTowner_OriginPack.zip` を外します。この版は Origin データを Jar に内蔵しています。
4. ゲーム内で通常 Origin の「超マサラ人」を選び、操作設定で「タイプ共鳴を開く」にキーを割り当てます。

この版は NeoOrigins 2.2.29 の Origin レイヤーを基に Monster Tamer を超マサラ人へ置き換えています。同じレイヤーを置換する別の Origin アドオンとは調整が必要になる場合があります。現在の Starlight Fusion 用 Dragonborn 統合版を使っているワールドには、動作確認前に差し替えないでください。

**検証状態:** ビルドと単体テストは成功しています。この単一 Jar を隔離ワールドで読み込む実機確認は未完了です。詳細は `RELEASE_REPORT.md` を参照してください。

この MOD は非公式のファン制作物です。Cobblemon、NeoOrigins、Mega Showdown の開発者による公式の承認・提供は受けていません。
