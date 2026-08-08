# AcademyCraft 1.12.2 / 1.21.1 顯示對照

- 左側：Minecraft 1.12.2、AcademyCraft 1.1.3 原版參考。
- 右側：Minecraft 1.21.1、AcademyCraft NeoForge 2.0.3 移植版。
- 兩個客戶端都在隔離的 Xvfb 顯示器中以 854 × 480 視窗實際渲染；沒有顯示到使用者桌面。
- 所有截圖都來自遊戲內物品欄、實際世界或御坂雲終端，不是只檢查 JSON、OBJ 是否存在。

## 25 個實際放置方塊

`placed-blocks-all-groups.png` 是全部五組世界內實拍總覽；每列左側為 1.12.2，右側為 1.21.1。這些畫面不是物品欄方塊物品，而是放在相同石英平台上的世界方塊。

| 組別 | 左到右的註冊 ID | 實拍對照 |
| --- | --- | --- |
| 1 | `constraint_metal`, `crystal_ore`, `imagsil_ore`, `reso_ore`, `machine_frame` | `placed-blocks-group-1-side-by-side.png` |
| 2 | `phase_gen`, `solar_gen`, `metal_former`, `imag_fusor`, `matrix` | `placed-blocks-group-2-side-by-side.png` |
| 3 | `dev_normal`, `dev_advanced`, `node_basic`, `node_standard`, `node_advanced` | `placed-blocks-group-3-side-by-side.png` |
| 4 | `windgen_base`, `windgen_pillar`, `windgen_main`, `cat_engine`, `ability_interferer` | `placed-blocks-group-4-side-by-side.png` |
| 5 | `imag_phase`, `ac_rf_input`, `ac_rf_output`, `eu_input`, `eu_output` | `placed-blocks-group-5-side-by-side.png` |

`matrix`、`dev_normal`、`dev_advanced`、`windgen_base`與 `windgen_main` 都是由玩家手持對應物品正常右鍵放置，讓兩版自己建立完整多方塊佔位結構。其他單格方塊以遊戲內 `/setblock` 放入世界。`imag_phase` 周圍使用隱形屏障限制流動，避免流體擴散範圍干擾單格貼圖對照。

這次的世界實拍發現並修正了物品欄無法看出的錯誤：1.12.2 的虛相液體底層使用純黑 `block/black`，1.21.1 原先誤用 `block/phase_liquid` 及藍色 tint。修正後兩版都以黑色底層疊加原版三層捲動投影特效。

物品欄的 `items-page-2-side-by-side.png`與 `items-page-3-side-by-side.png` 只能證明「方塊物品」形態，不再當作世界內方塊顯示的證據。

## 御坂雲終端

`misaka-data-terminal-side-by-side.png` 是實際畫面對照。1.12.2 畫面保留舊版真實的鎖定摘要狀態；1.21.1 畫面顯示完整正文、合成預覽和摘要。舊版已實際安裝資料終端、丟出並重新撿取安裝器且重載世界，仍未在客戶端解鎖正文，因此沒有修改舊版程式或測試存檔來偽造解鎖畫面。

完整文字另外直接從兩版資源擷取：

| 語系 | 1.12.2 SHA-256 | 1.21.1 SHA-256 | 結果 |
| --- | --- | --- | --- |
| `en_us` | `cc387861761fd66874a3bffc3e7b345d0267ad2e5736943d4ba961da343a3453` | `cc387861761fd66874a3bffc3e7b345d0267ad2e5736943d4ba961da343a3453` | 位元組完全相同 |
| `zh_cn` | `139e602e38ed8f1a6a71e228a40994844c428de6c743cbf8591d19b0703e58a9` | `139e602e38ed8f1a6a71e228a40994844c428de6c743cbf8591d19b0703e58a9` | 位元組完全相同 |

兩版都沒有 `zh_tw/terminal.md`。1.21.1 在繁體中文介面下因此依目前邏輯回退到 `en_us`；畫面中的「有序合成」等繁體中文字已實際正常渲染，正文為英文是語系資源回退，不是缺字方塊。

## 59 個共同註冊物件

### 第 1 頁：34 個一般物品

`app_freq_transmitter`, `app_media_player`, `app_skill_tree`, `brain_component`, `calc_chip`, `coin`, `constraint_ingot`, `constraint_plate`, `crystal_low`, `crystal_normal`, `crystal_pure`, `data_chip`, `developer_portable`, `energy_convert_component`, `energy_unit`, `imag_silicon_ingot`, `imag_silicon_piece`, `induction_factor`, `info_component`, `logo`, `mag_hook`, `magnetic_coil`, `mat_core`, `matter_unit`, `media_item`, `needle`, `reinforced_iron_plate`, `reso_crystal`, `resonance_component`, `silbarn`, `terminal_installer`, `tutorial`, `wafer`, `windgen_fan`

### 第 2 頁：21 個方塊物品

`ability_interferer`, `cat_engine`, `constraint_metal`, `crystal_ore`, `dev_advanced`, `dev_normal`, `imag_fusor`, `imag_phase`, `imagsil_ore`, `machine_frame`, `matrix`, `metal_former`, `node_advanced`, `node_basic`, `node_standard`, `phase_gen`, `reso_ore`, `solar_gen`, `windgen_base`, `windgen_main`, `windgen_pillar`

### 第 3 頁：4 個能源橋接方塊物品

`ac_rf_input`, `ac_rf_output`, `eu_input`, `eu_output`

這四個 ID 在 1.12.2 由已載入的 Redstone Flux／IC2 支援註冊，在 1.21.1 由主模組註冊；兩版實拍順序相同。

## 會切換模型的物品狀態

`item-variants-side-by-side.png` 另列出 16 個實際堆疊：

- `energy_unit` 空／滿各一個。
- `developer_portable` 空／滿各一個。
- `induction_factor` 模型狀態 0–3。
- `media_item` 模型狀態 0–2。
- `mat_core` 模型狀態 0–2。
- `matter_unit` 空／虛相液體各一個。

這一頁用 1.12.2 metadata 與 1.21.1 data component 建立相同模型狀態，避免只比較每個註冊 ID 的預設外觀。

## 2.0.3 建置驗證

- `./gradlew build --console=plain`：成功。
- 測試：21 項，0 failure，0 error。
- JAR：`build/libs/AcademyCraft-neo-1.21.1-2.0.3.jar`。
- JAR SHA-256：`c919a21eee52d3ab0210cb5d5cfe50586884df18904e41aaaf01affc6ae1b1be`。
- JAR 內 `META-INF/neoforge.mods.toml`：`version="2.0.3"`。
- JAR 已包含 `assets/academy/font/noto_sans_tc_vf.ttf`，以及兩個語系的 `terminal.md`。
