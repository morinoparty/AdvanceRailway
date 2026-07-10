# コマンド

- [x] は実装済み
- [ ] は未実装<br>

`[]` 内はオプショナル引数です。<br>
`<>` 内は必須引数です。

## 一般

- [x] `ar inspect` クリックした線路の終点を検出します。
- [x] `ar targetBlock` 見ているブロックの座標を表示します。
- [x] `ar help` ヘルプを表示します。
- [x] `ar info` プラグインの情報を表示します。
- [x] `ar reload` マップ及び設定をリロードします。

## ファイル操作

!!! warning
ファイル操作はデータの破損の原因となります。十分な注意を払って操作してください。<br>
詳しくは、[こちら](/usage/file)を参照してください。

- [x] `ar export <dataType> <fileType> ` <br> マップデータをエクスポートします。 dataTypeは`station`, `railway`, `group`
  のいずれかです。 fileTypeは`json`, `csv`のいずれかです。
- [x] `ar import <dataType> <fileName> ` <br> マップデータをインポートします。 dataTypeは`station`, `railway`, `group`
  のいずれかです。 ファイルの形式は拡張子によって判断されます。

## 路線

- [x] `ar railway add <railwayId> <railwayName> <start> <direction> <end>` 路線を追加します。
- [x] `ar railway remove <railwayId>` 路線を削除します。
- [ ] `ar railway preview <start> <direction> <end>` 路線の名前を変更します。
- [x] `ar railway update <railwayId> <start> <direction> <end>` 路線の始点と終点を変更します。 道中の路線が変化した場合もこちらを使用してください。
- [x] `ar railway set nam <railwayId>` 路線を削除します。
- [x] `ar railway set lineType <railwayId> <lineType>` 路線の色を変更します。
- [x] `ar railway set group <railwayId> <groupId>` 路線のグループを変更します。
- [x] `ar railway unset group <railwayId> <stationId>` 路線のグループを削除します。
- [x] `ar railway set from-station <railwayId> <fromStation>` 路線の始点駅を変更します。
- [x] `ar railway set to-station <railwayId> <toStation>` 路線の終点駅を変更します。
- [x] `ar railway info <railwayId>` 路線の情報を表示します。
- [x] `ar railway list` 路線の一覧を表示します。
- [x] `ar railway route <from> <to>` 2駅間の最短（所要時間最小）経路を検索し、各区間と合計所要時間を表示します。

## 駅

- [x] `ar station add <stationId> <stationName> [point]` 駅を追加します。 pointがない場合はプレイヤーの位置に追加されます。
- [x] `ar station remove <stationId>` 駅を削除します。
- [x] `ar station set name <stationId> <newName>` 駅の名前を変更します。
- [x] `ar station set location <stationId> [point]` 駅の座標を変更します。
- [x] `ar station set numbering <stationId> <newNumbering>` 駅の番号を変更します。
- [x] `ar station info <stationId>` 駅の情報を表示します。
- [x] `ar station list` 駅の一覧を表示します。

## グループ

- [x] `ar group add <groupId> <groupName>` グループを追加します。
- [x] `ar group remove <groupId>` グループを削除します。
- [x] `ar group set name <groupId> <oldName> <newName>` グループの名前を変更します。
- [x] `ar group set color <groupId> <r> <g> <b>` グループの色を変更します。
- [x] `ar group info <groupId>` グループの情報を表示します。
- [x] `ar group list` グループの一覧を表示します。