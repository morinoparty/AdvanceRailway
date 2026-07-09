# 基本的な使い方

## 駅の追加

`ar station add <stationId> <name> [point]`を使用して駅を追加します <br>

- `id` : 駅のID
- `name` : 駅の名前
- `point` : 駅の座標

### 例

`ar station add mr01 もりもと駅`を実行すると、プレイヤーの位置に駅が追加されます。<br>
`ar station add mr01 もりもと駅 0,64,0`を実行すると、座標0,64,0に駅が追加されます。<br>

## グループの作成

`ar group add <groupId> <groupName>`を使用してグループを作成します

- `groupId` : グループのID
- `groupName` : グループの名前

### 例

`ar group add mr もりもと線`を実行すると、もりもと線というグループが作成されます。<br>

## 路線の追加

`ar railway add <railwayId> <railwayName> <start> <direction> <end>`を使用して路線を追加します

- `railwayId` : 路線のID
- `railwayName` : 路線の名前
- `start` : 路線の始点
- `direction` : 路線の方向
- `end` : 路線の終点

始点と終点は、与えられた座標から最も近い駅が選択されます。
異なる駅の場合は後述する方法で変更できます。

## グループへの追加

`ar railway set group <railwayId> <groupId>`
路線をグループに追加する方法です。

## 路線の色の変更

`ar group color <groupId> <r> <g> <b>`を使用して路線の色を変更します





