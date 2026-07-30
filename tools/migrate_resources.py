#!/usr/bin/env python3
"""Convert the adjacent legacy AcademyCraft resources into 1.21.1 resource/data-pack files.

The checked-in output is authoritative; this tool documents and makes the mechanical conversion
repeatable when an original translation or texture is updated.
"""
from __future__ import annotations

import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT.parent / "src/main/resources/assets/academy"
ASSETS = ROOT / "src/main/resources/assets/academy"
DATA = ROOT / "src/main/resources/data"

ITEMS = [
    "app_freq_transmitter", "app_media_player", "app_skill_tree", "brain_component", "calc_chip",
    "coin", "constraint_ingot", "constraint_plate", "crystal_low", "crystal_normal", "crystal_pure",
    "data_chip", "developer_portable", "energy_convert_component", "energy_unit", "imag_silicon_ingot",
    "imag_silicon_piece", "induction_factor", "info_component", "logo", "mag_hook", "magnetic_coil",
    "mat_core", "matter_unit", "media_item", "needle", "reinforced_iron_plate", "reso_crystal",
    "resonance_component", "silbarn", "terminal_installer", "tutorial", "wafer", "windgen_fan",
]
BLOCKS = [
    "ability_interferer", "cat_engine", "constraint_metal", "crystal_ore", "dev_advanced", "dev_normal",
    "imag_fusor", "imag_phase", "imagsil_ore", "machine_frame", "matrix", "metal_former",
    "node_advanced", "node_basic", "node_standard", "phase_gen", "reso_ore", "solar_gen",
    "windgen_base", "windgen_main", "windgen_pillar", "ac_rf_input", "ac_rf_output", "eu_input", "eu_output",
]


def dump(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def parse_lang_text(text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.replace("\\n", "\n")
    return result


def parse_lang(path: Path) -> dict[str, str]:
    return parse_lang_text(path.read_text(encoding="utf-8-sig"))


def load_1710_lang(locale: str) -> dict[str, str]:
    legacy_name = {"en_us": "en_US", "ja_jp": "ja_JP", "zh_cn": "zh_CN", "zh_tw": "zh_TW"}.get(locale)
    if legacy_name is None:
        return {}
    try:
        data = subprocess.check_output([
            "git", "-C", str(ROOT.parent), "show",
            f"1.0.7:src/main/resources/assets/academy/lang/{legacy_name}.lang",
        ])
        return parse_lang_text(data.decode("utf-8-sig"))
    except (subprocess.SubprocessError, UnicodeError):
        return {}


def convert_languages() -> None:
    special_item_sources = {
        "app_freq_transmitter": "item.ac_apps.name", "app_media_player": "item.ac_apps.name",
        "app_skill_tree": "item.ac_apps.name", "mat_core": "item.ac_mat_core_0.name",
        "media_item": "ac.media.sisters_noise.name",
    }
    additions_en = {
        "itemGroup.academy": "AcademyCraft",
        "key.categories.academy": "AcademyCraft",
        "key.academy.activate": "Activate/deactivate ability",
        "key.academy.slot_1": "Ability slot 1", "key.academy.slot_2": "Ability slot 2",
        "key.academy.slot_3": "Ability slot 3", "key.academy.slot_4": "Ability slot 4",
        "key.academy.terminal": "Open data terminal",
        "key.academy.edit_preset": "Edit ability preset",
        "key.academy.switch_preset": "Switch ability preset",
        "ac.ability.no_category": "You have not acquired an ability category.",
        "ac.ability.already_has_category": "Your Personal Reality has already been established.",
        "ac.ability.category_acquired": "Your Personal Reality has become %s.",
        "ac.ability.category_reset": "Your Personal Reality was reset.",
        "ac.ability.activated": "Ability activated", "ac.ability.deactivated": "Ability deactivated",
        "ac.ability.not_activated": "Activate your ability first.",
        "ac.ability.interfered": "Your ability is being interfered with.",
        "ac.ability.empty_preset": "Ability slot %s is empty.",
        "ac.ability.cooldown": "Skill cooling down: %.1fs",
        "ac.ability.insufficient_cp": "Not enough CP or overload capacity.",
        "ac.ability.overload_locked": "Your calculation core is overloaded; wait for full recovery.",
        "ac.ability.missing_reagent": "%s is missing its required reagent.",
        "ac.ability.context.charging": "CHARGING", "ac.ability.context.active": "ACTIVE",
        "ac.developer.no_available_skill": "No skill is currently available on this developer.",
        "ac.developer.need_levels": "This development requires %s experience levels.",
        "ac.developer.learned": "Learned skill: %s",
        "ac.gui.developer.title": "Ability Developer",
        "ac.developer.acquire_category": "Develop ability category",
        "ac.developer.level_up": "Develop Level %s",
        "ac.developer.reset_category": "Convert category (coil + different factor)",
        "ac.developer.stimulating": "MULTI-STIMULATION IN PROGRESS",
        "ac.developer.category_stimulation": "Personal Reality development",
        "ac.developer.interrupted": "Development interrupted: developer or energy unavailable.",
        "ac.gui.common.pg_wireless.connected": "Connected",
        "ac.gui.common.pg_wireless.available": "Available",
        "ac.factor.developer_hint": "Use this factor while developing a category.",
        "ac.coil.developer_hint": "Category conversion requires an advanced developer and a different induction factor.",
        "ac.coil.tooltip.0": "Overcharges the developer",
        "ac.coil.tooltip.1": "Used to rewrite Personal Reality",
        "ac.item.energy": "IF: %s / %s",
        "ac.machine.need_energy": "This operation requires %s FE.",
        "ac.machine.status": "Energy: %s/%s FE | Progress: %s/%s",
        "ac.machine.imag_fusor.liquid": "Imag Phase Liquid: %s/8000 mB",
        "ac.machine.metal_former.mode": "Metal Former mode: %s",
        "ac.machine.metal_former.mode.0": "Plate", "ac.machine.metal_former.mode.1": "Incise",
        "ac.machine.metal_former.mode.2": "Etch", "ac.machine.metal_former.mode.3": "Refine",
        "ac.factor.unstable": "Unstable / random category",
        "ac.matrix_core.level": "Matrix core level %s",
        "item.academy.matter_unit.empty": "Empty",
        "item.academy.matter_unit.filled": "Contains Imag Phase Liquid",
        "ac.teleport.marked": "Teleport location marked.",
        "ac.teleport.cross_dimension_locked": "Cross-dimensional teleport requires 80% Location Teleport proficiency.",
        "ac.gui.location.title": "Location Teleport",
        "ac.gui.location.name": "Location name",
        "ac.gui.location.add": "Add here",
        "ac.gui.location.full": "Location list full",
        "ac.ability.context.unavailable": "Ability unavailable",
        "ac.coin.heads": "Heads!", "ac.coin.tails": "Tails!",
        "ac.tutorial.welcome": "Welcome to AcademyCraft. Install the Data Terminal, acquire an induction factor, and develop your Personal Reality.",
        "ac.tutorial.load_failed": "This tutorial page could not be loaded.",
        "ac.gui.terminal.title": "Data Terminal",
        "ac.about.credits": "Credits", "ac.about.supporters": "Supporters",
        "ac.notification.app_installed": "Application installed",
        "ac.frequency.automatic": "Wireless links are configured with the Frequency Transmitter.",
        "ac.frequency.network": "Network / SSID",
        "ac.frequency.password": "Password",
        "ac.frequency.apply": "Apply",
        "ac.frequency.refresh": "Find target",
        "ac.frequency.no_target": "Look at or stand near an AcademyCraft machine.",
        "ac.frequency.target": "%s at %s | current network: %s",
        "ac.frequency.configured": "Wireless configuration updated.",
        "ac.frequency.permission_denied": "Only the matrix owner may change this network.",
        "ac.frequency.authentication_failed": "No matrix accepted that network and password.",
        "ac.frequency.invalid": "Invalid or duplicate wireless network.",
        "ac.node.name": "Node name", "ac.node.apply": "Apply", "ac.node.configured": "Wireless node settings updated.",
        "ac.interferer.switch": "Switch:", "ac.interferer.range": "Range:", "ac.interferer.player": "Player name",
        "ac.interferer.configured": "Ability Interferer settings updated.",
        "ac.media.now_playing": "Now playing: %s", "ac.media.stop": "Stop", "ac.media.pause_resume": "Pause/Play",
        "ac.media.empty": "No media installed",
        "ac.settings.attack_players": "Allow ability PvP", "ac.settings.destroy_blocks": "Allow block destruction",
        "ac.settings.coin_flip": "Heads-or-tails coin game", "ac.settings.mouse_wheel_teleport": "Mouse-wheel teleport range",
    }
    additions_tw = {
        "itemGroup.academy": "AcademyCraft",
        "key.categories.academy": "AcademyCraft",
        "key.academy.activate": "啟用／解除能力",
        "key.academy.slot_1": "能力欄位 1", "key.academy.slot_2": "能力欄位 2",
        "key.academy.slot_3": "能力欄位 3", "key.academy.slot_4": "能力欄位 4",
        "key.academy.terminal": "開啟資料終端",
        "key.academy.edit_preset": "編輯能力預設",
        "key.academy.switch_preset": "切換能力預設組",
        "ac.ability.no_category": "你尚未取得能力類別。",
        "ac.ability.already_has_category": "你的個人現實已經建立。",
        "ac.ability.category_acquired": "你的個人現實已成為%s。",
        "ac.ability.category_reset": "你的個人現實已重置。",
        "ac.ability.activated": "能力已啟用", "ac.ability.deactivated": "能力已解除",
        "ac.ability.not_activated": "請先啟用能力。",
        "ac.ability.interfered": "你的能力正受到干涉。",
        "ac.ability.empty_preset": "能力欄位 %s 尚未設定。",
        "ac.ability.cooldown": "技能冷卻中：%.1f 秒",
        "ac.ability.insufficient_cp": "計算力不足或過載值已滿。",
        "ac.ability.overload_locked": "計算核心已過載；必須等待完全恢復。",
        "ac.ability.missing_reagent": "%s 缺少必要媒介。",
        "ac.ability.context.charging": "蓄力中", "ac.ability.context.active": "施放中",
        "ac.developer.no_available_skill": "目前沒有可由此開發機學習的技能。",
        "ac.developer.need_levels": "此次開發需要 %s 級經驗。",
        "ac.developer.learned": "已學會技能：%s",
        "ac.gui.developer.title": "能力開發機",
        "ac.developer.acquire_category": "開發能力類別",
        "ac.developer.level_up": "開發至 Level %s",
        "ac.developer.reset_category": "轉換類別（線圈＋不同誘導因子）",
        "ac.developer.stimulating": "多重刺激進行中",
        "ac.developer.category_stimulation": "個人現實開發",
        "ac.developer.interrupted": "開發已中止：開發機或能量無法使用。",
        "ac.gui.common.pg_wireless.connected": "已連線",
        "ac.gui.common.pg_wireless.available": "可用連線",
        "ac.factor.developer_hint": "開發能力類別時會使用此誘導因子。",
        "ac.coil.developer_hint": "類別轉換需要高級開發機與不同類別的誘導因子。",
        "ac.coil.tooltip.0": "使開發機超載",
        "ac.coil.tooltip.1": "用於重寫個人現實",
        "ac.item.energy": "IF：%s / %s",
        "ac.machine.need_energy": "此操作需要 %s FE。",
        "ac.machine.status": "能量：%s/%s FE｜進度：%s/%s",
        "ac.machine.imag_fusor.liquid": "虛相位液體：%s/8000 mB",
        "ac.machine.metal_former.mode": "金屬處理機模式：%s",
        "ac.machine.metal_former.mode.0": "壓板", "ac.machine.metal_former.mode.1": "切割",
        "ac.machine.metal_former.mode.2": "蝕刻", "ac.machine.metal_former.mode.3": "精煉",
        "ac.factor.unstable": "不穩定／隨機能力類別",
        "ac.matrix_core.level": "矩陣核心等級 %s",
        "item.academy.matter_unit.empty": "空",
        "item.academy.matter_unit.filled": "裝有虛相位液體",
        "ac.teleport.marked": "已標記傳送位置。",
        "ac.teleport.cross_dimension_locked": "跨維度傳送需要位置傳送熟練度達 80%。",
        "ac.gui.location.title": "位置傳送",
        "ac.gui.location.name": "位置名稱",
        "ac.gui.location.add": "加入目前位置",
        "ac.gui.location.full": "位置清單已滿",
        "ac.ability.context.unavailable": "目前無法使用能力",
        "ac.coin.heads": "正面！", "ac.coin.tails": "反面！",
        "ac.tutorial.welcome": "歡迎來到 AcademyCraft。安裝資料終端、取得能力誘導因子，並開發你的個人現實。",
        "ac.tutorial.load_failed": "無法載入此教學頁面。",
        "ac.gui.terminal.title": "資料終端",
        "ac.about.credits": "製作名單", "ac.about.supporters": "贊助者",
        "ac.notification.app_installed": "應用程式安裝完成",
        "ac.frequency.automatic": "請使用頻率傳輸器設定無線連線。",
        "ac.frequency.network": "網路／SSID",
        "ac.frequency.password": "密碼",
        "ac.frequency.apply": "套用",
        "ac.frequency.refresh": "尋找目標",
        "ac.frequency.no_target": "請看向或站在 AcademyCraft 機器附近。",
        "ac.frequency.target": "%s（%s）｜目前網路：%s",
        "ac.frequency.configured": "無線設定已更新。",
        "ac.frequency.permission_denied": "只有矩陣擁有者能修改此網路。",
        "ac.frequency.authentication_failed": "找不到接受該網路與密碼的矩陣。",
        "ac.frequency.invalid": "無效或重複的無線網路。",
        "ac.node.name": "節點名稱", "ac.node.apply": "套用", "ac.node.configured": "無線節點設定已更新。",
        "ac.interferer.switch": "開關：", "ac.interferer.range": "範圍：", "ac.interferer.player": "玩家名稱",
        "ac.interferer.configured": "能力干擾器設定已更新。",
        "ac.media.now_playing": "正在播放：%s", "ac.media.stop": "停止", "ac.media.pause_resume": "暫停／播放",
        "ac.media.empty": "尚未安裝媒體",
        "ac.settings.attack_players": "允許能力 PvP", "ac.settings.destroy_blocks": "允許破壞方塊",
        "ac.settings.coin_flip": "硬幣正反面遊戲", "ac.settings.mouse_wheel_teleport": "滾輪調整傳送距離",
    }
    additions_cn = {**additions_en,
        "key.academy.activate": "激活／解除能力", "key.academy.terminal": "打开数据终端",
        "ac.ability.no_category": "你尚未取得能力类别。", "ac.ability.activated": "能力已激活",
        "ac.ability.deactivated": "能力已解除", "ac.ability.not_activated": "请先激活能力。",
        "ac.ability.interfered": "你的能力正受到干涉。", "ac.coin.heads": "正面！", "ac.coin.tails": "反面！",
    }

    lang_dir = ASSETS / "lang"
    lang_dir.mkdir(parents=True, exist_ok=True)
    for legacy_file in sorted((LEGACY / "lang").glob("*.lang")):
        lang = parse_lang(legacy_file)
        # Restore 1.7.10-only achievement/tutorial/settings strings removed by the 1.12 continuation.
        for key, value in load_1710_lang(legacy_file.stem.lower()).items():
            lang.setdefault(key, value)
        lang["itemGroup.academy"] = lang.get("itemGroup.AcademyCraft", "AcademyCraft")
        for name in ITEMS:
            source = special_item_sources.get(name, f"item.ac_{name}.name")
            fallback = name.replace("_", " ").title()
            lang[f"item.academy.{name}"] = lang.get(source, fallback)
        legacy_block_keys = {"ac_rf_input": "tile.ac_rf_input.name", "ac_rf_output": "tile.ac_rf_output.name",
                             "eu_input": "tile.ac_eu_input.name", "eu_output": "tile.ac_eu_output.name"}
        for name in BLOCKS:
            lang[f"block.academy.{name}"] = lang.get(legacy_block_keys.get(name, f"tile.ac_{name}.name"), name.replace("_", " ").title())
        locale = legacy_file.stem.lower()
        lang.update(additions_tw if locale == "zh_tw" else additions_cn if locale == "zh_cn" else additions_en)
        dump(lang_dir / f"{locale}.json", dict(sorted(lang.items())))


def generate_models() -> None:
    model_root = ASSETS / "models"
    for directory in (model_root / "item", model_root / "block", ASSETS / "blockstates"):
        if directory.exists(): shutil.rmtree(directory)
        directory.mkdir(parents=True)

    item_textures = {
        "coin": "coin_front", "developer_portable": "developer_portable_empty", "energy_unit": "energy_unit_empty",
        "induction_factor": "factor_electromaster", "mat_core": "mat_core_0", "media_item": "media_sisters_noise",
    }
    entity_items = {"coin", "developer_portable", "terminal_installer", "mag_hook", "silbarn", "windgen_fan", "matter_unit"}
    for name in ITEMS:
        model = ({
            "parent": "minecraft:builtin/entity",
            "textures": {"particle": f"academy:item/{item_textures.get(name, name)}"},
        } if name in entity_items else {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"academy:item/{item_textures.get(name, name)}"},
        })
        if name == "energy_unit":
            model["overrides"] = [
                {"predicate": {"custom_model_data": 1}, "model": "academy:item/energy_unit_half"},
                {"predicate": {"custom_model_data": 2}, "model": "academy:item/energy_unit_full"},
            ]
        elif name == "induction_factor":
            model["overrides"] = [
                {"predicate": {"custom_model_data": index}, "model": f"academy:item/factor_{category}"}
                for index, category in enumerate(["electromaster", "meltdowner", "teleporter", "vecmanip"])
            ]
        elif name == "mat_core":
            model["overrides"] = [
                {"predicate": {"custom_model_data": index}, "model": f"academy:item/mat_core_{index}"}
                for index in range(3)
            ]
        elif name == "media_item":
            model["overrides"] = [
                {"predicate": {"custom_model_data": index}, "model": f"academy:item/media_{index}"}
                for index in range(3)
            ]
        dump(model_root / "item" / f"{name}.json", model)
    dump(model_root / "item/matter_unit_filled.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "academy:item/matter_unit_phase_liquid_0"}})
    for stage in ("half", "full"):
        dump(model_root / "item" / f"energy_unit_{stage}.json", {"parent": "minecraft:item/generated",
            "textures": {"layer0": f"academy:item/energy_unit_{stage}"}})
    for category in ["electromaster", "meltdowner", "teleporter", "vecmanip"]:
        dump(model_root / "item" / f"factor_{category}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"academy:item/factor_{category}"}})
    for index, texture in enumerate(["media_sisters_noise", "media_only_my_railgun", "media_level5_judgelight"]):
        dump(model_root / "item" / f"media_{index}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"academy:item/{texture}"}})
    for index in range(3):
        dump(model_root / "item" / f"mat_core_{index}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"academy:item/mat_core_{index}"}})

    simple_texture = {name: name for name in BLOCKS}
    simple_texture.update({
        "ability_interferer": "ability_interf_off", "imag_phase": "phase_liquid",
        "imag_fusor": "machine_side", "metal_former": "metal_former_front",
        "node_basic": "node_basic_side_0", "node_standard": "node_standard_side_0",
        "node_advanced": "node_advanced_side_0", "ac_rf_input": "rf_input", "ac_rf_output": "rf_output",
    })
    machine_blocks = {
        "ability_interferer", "cat_engine", "dev_advanced", "dev_normal", "imag_fusor", "matrix",
        "metal_former", "node_advanced", "node_basic", "node_standard", "phase_gen", "solar_gen",
        "windgen_base", "windgen_main", "windgen_pillar", "ac_rf_input", "ac_rf_output", "eu_input", "eu_output",
    }
    for name in BLOCKS:
        if name.startswith("node_"):
            block_model = {
                "parent": "minecraft:block/cube",
                "textures": {
                    "down": "academy:block/node_top_0", "up": "academy:block/node_top_0",
                    "north": f"academy:block/{name}_side_0", "south": f"academy:block/{name}_side_0",
                    "west": f"academy:block/{name}_side_0", "east": f"academy:block/{name}_side_0",
                    "particle": f"academy:block/{name}_side_0",
                },
            }
        elif name == "metal_former":
            block_model = {
                "parent": "minecraft:block/cube",
                "textures": {side: f"academy:block/metal_former_{texture}" for side, texture in {
                    "north": "front", "south": "back", "west": "left", "east": "right",
                    "up": "top", "down": "bottom", "particle": "front",
                }.items()},
            }
        elif name == "imag_fusor":
            block_model = {
                "parent": "minecraft:block/cube",
                "textures": {
                    "north": "academy:block/ief_off", "south": "academy:block/machine_side",
                    "west": "academy:block/machine_side", "east": "academy:block/machine_side",
                    "up": "academy:block/machine_top", "down": "academy:block/machine_bottom",
                    "particle": "academy:block/machine_side",
                },
            }
        else:
            block_model = {
                "parent": "minecraft:block/cube_all",
                "textures": {"all": f"academy:block/{simple_texture[name]}"},
            }
        dump(model_root / "block" / f"{name}.json", block_model)
        stage_models = [name] * 5
        if name == "imag_fusor":
            for stage in range(1, 5):
                stage_name = f"imag_fusor_working_{stage}"
                stage_models[stage] = stage_name
                working = json.loads(json.dumps(block_model))
                working["textures"]["north"] = f"academy:block/ief_working_{stage}"
                dump(model_root / "block" / f"{stage_name}.json", working)
        elif name.startswith("node_"):
            for stage in range(1, 5):
                stage_name = f"{name}_stage_{stage}"
                stage_models[stage] = stage_name
                active = json.loads(json.dumps(block_model))
                for side in ("north", "south", "west", "east", "particle"):
                    active["textures"][side] = f"academy:block/{name}_side_{stage}"
                active["textures"]["up"] = active["textures"]["down"] = "academy:block/node_top_1"
                dump(model_root / "block" / f"{stage_name}.json", active)
        elif name == "ability_interferer":
            stage_models[1:] = ["ability_interferer_on"] * 4
            dump(model_root / "block/ability_interferer_on.json", {
                "parent": "minecraft:block/cube_all", "textures": {"all": "academy:block/ability_interf_on"}})
        if name in machine_blocks:
            rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
            variants = {
                f"facing={facing},visual_stage={stage}": {
                    "model": f"academy:block/{stage_models[stage]}", **({"y": rotation} if rotation else {})
                }
                for facing, rotation in rotations.items() for stage in range(5)
            }
        else:
            variants = {"": {"model": f"academy:block/{name}"}}
        dump(ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
        dump(model_root / "item" / f"{name}.json", {"parent": f"academy:block/{name}"})
    dump(model_root / "block/multiblock_part.json", {
        "textures": {"particle": "academy:block/machine_side"}, "elements": []})
    dump(ASSETS / "blockstates/multiblock_part.json", {
        "variants": {"": {"model": "academy:block/multiblock_part"}}})


def ingredient(value: str | dict) -> dict:
    if isinstance(value, dict): return value
    return {"tag": value[1:]} if value.startswith("#") else {"item": value}


def shaped(name: str, result: str, count: int, pattern: list[str], keys: dict[str, str]) -> None:
    dump(DATA / "academy/recipe" / f"{name}.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
        "key": {key: ingredient(value) for key, value in keys.items()},
        "result": {"id": result, "count": count},
    })


def shapeless(name: str, result: str, count: int, values: list[str]) -> None:
    dump(DATA / "academy/recipe" / f"{name}.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [ingredient(value) for value in values],
        "result": {"id": result, "count": count},
    })


def generate_recipes() -> None:
    recipe_dir = DATA / "academy/recipe"
    if recipe_dir.exists(): shutil.rmtree(recipe_dir)
    iron = "minecraft:iron_ingot"
    shaped("data_chip", "academy:data_chip", 1, ["RRR", " I "], {"R": "minecraft:redstone", "I": "academy:imag_silicon_piece"})
    shaped("data_chip_from_plate", "academy:data_chip", 1, ["RRR", " I "], {"R": "minecraft:redstone", "I": "academy:reinforced_iron_plate"})
    shapeless("calc_chip", "academy:calc_chip", 1, ["academy:data_chip", "minecraft:quartz", "minecraft:quartz"])
    shapeless("calc_chip_from_resonance", "academy:calc_chip", 1, ["academy:data_chip", "academy:reso_crystal"])
    shaped("reinforced_iron_plate", "academy:reinforced_iron_plate", 2, ["I", "I", "I"], {"I": iron})
    shaped("machine_frame", "academy:machine_frame", 1, [" I ", "IRI", " I "], {"I": "academy:reinforced_iron_plate", "R": "minecraft:redstone"})
    shaped("phase_gen", "academy:phase_gen", 1, ["CMC", "U U"], {"C": "academy:crystal_low", "M": "academy:machine_frame", "U": "academy:matter_unit"})
    shaped("solar_gen", "academy:solar_gen", 1, ["GGG", " W ", "EME"], {"G": "minecraft:glass_pane", "W": "academy:wafer", "E": "academy:energy_convert_component", "M": "academy:machine_frame"})
    shaped("windgen_base", "academy:windgen_base", 1, ["C", "M", "E"], {"C": "minecraft:iron_ingot", "M": "academy:machine_frame", "E": "academy:energy_convert_component"})
    shaped("windgen_pillar", "academy:windgen_pillar", 1, ["I", "R", "I"], {"I": "minecraft:iron_bars", "R": "minecraft:redstone"})
    shaped("windgen_main", "academy:windgen_main", 1, [" M ", "CEC", " M "], {"M": "academy:machine_frame", "C": "academy:constraint_plate", "E": "academy:energy_convert_component"})
    shaped("windgen_fan", "academy:windgen_fan", 1, [" I ", "IBI", " I "], {"I": "academy:reinforced_iron_plate", "B": "minecraft:iron_bars"})
    shaped("node_basic", "academy:node_basic", 1, [" C ", "IMI", "LRL"], {"C": "academy:calc_chip", "I": "minecraft:iron_ingot", "M": "academy:machine_frame", "L": "academy:crystal_low", "R": "academy:reso_crystal"})
    shaped("node_standard", "academy:node_standard", 1, [" N ", "CEC", " B "], {"N": "academy:crystal_normal", "C": "academy:calc_chip", "E": "academy:energy_convert_component", "B": "academy:node_basic"})
    shaped("node_advanced", "academy:node_advanced", 1, ["P", "R", "N"], {"P": "academy:crystal_pure", "R": "academy:resonance_component", "N": "academy:node_standard"})
    shaped("matter_unit", "academy:matter_unit", 4, [" P ", "PGP", " P "], {"P": "academy:constraint_plate", "G": "minecraft:glass"})
    shaped("energy_unit", "academy:energy_unit", 1, [" P ", "PCP", " D "], {"P": "academy:constraint_plate", "C": "academy:crystal_low", "D": "academy:data_chip"})
    shaped("energy_unit_normal", "academy:energy_unit", 2, [" P ", "PCP", " D "], {"P": "academy:constraint_plate", "C": "academy:crystal_normal", "D": "academy:data_chip"})
    shaped("energy_unit_pure", "academy:energy_unit", 4, [" P ", "PCP", " D "], {"P": "academy:constraint_plate", "C": "academy:crystal_pure", "D": "academy:data_chip"})
    shaped("constraint_plate", "academy:constraint_plate", 2, ["III"], {"I": "academy:constraint_ingot"})
    shaped("terminal_installer", "academy:terminal_installer", 1, ["DGD", "PBP", "IRI"], {"D": "academy:data_chip", "G": "minecraft:glass_pane", "P": "academy:reinforced_iron_plate", "B": "academy:brain_component", "I": "academy:info_component", "R": "minecraft:redstone_block"})
    shaped("imag_fusor", "academy:imag_fusor", 1, ["PCP", "FMF", "PUP"], {"P": "academy:constraint_plate", "C": "academy:crystal_low", "F": "academy:calc_chip", "M": "academy:machine_frame", "U": "academy:matter_unit"})
    shaped("imag_fusor_left", "academy:imag_fusor", 1, [" C ", "FME", " U "], {"C": "academy:crystal_low", "F": "academy:calc_chip", "M": "academy:machine_frame", "E": "academy:energy_convert_component", "U": "academy:matter_unit"})
    shaped("imag_fusor_right", "academy:imag_fusor", 1, [" C ", "EMF", " U "], {"C": "academy:crystal_low", "F": "academy:calc_chip", "M": "academy:machine_frame", "E": "academy:energy_convert_component", "U": "academy:matter_unit"})
    shaped("metal_former", "academy:metal_former", 1, [" S ", "CMC", "PUP"], {"S": "minecraft:shears", "C": "academy:calc_chip", "M": "academy:machine_frame", "P": "academy:constraint_plate", "U": "academy:matter_unit"})
    shaped("matrix", "academy:matrix", 1, [" R ", "SMS", "DRD"], {"R": "academy:reso_crystal", "S": "minecraft:redstone", "M": "academy:machine_frame", "D": "academy:data_chip"})
    shaped("mat_core", "academy:mat_core", 1, [" C ", "FRD", " E "], {"C": "academy:crystal_low", "F": "academy:calc_chip", "R": "academy:reso_crystal", "D": "academy:data_chip", "E": "academy:energy_convert_component"})
    core_component = lambda level: {"type": "neoforge:components", "items": "academy:mat_core",
                                    "components": {"minecraft:custom_model_data": level}, "strict": False}
    dump(recipe_dir / "mat_core_1.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["R", "C", "M"],
        "key": {"R": ingredient("academy:reso_crystal"), "C": ingredient("academy:crystal_normal"), "M": core_component(0)},
        "result": {"id": "academy:mat_core", "count": 1, "components": {"minecraft:custom_model_data": 1}},
    })
    dump(recipe_dir / "mat_core_2.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["GGG", "RER", " M "],
        "key": {"G": ingredient("minecraft:glowstone_dust"), "R": ingredient("academy:reso_crystal"),
                "E": ingredient("minecraft:ender_pearl"), "M": core_component(1)},
        "result": {"id": "academy:mat_core", "count": 1, "components": {"minecraft:custom_model_data": 2}},
    })
    shaped("info_component", "academy:info_component", 1, ["G", "D"], {"G": "minecraft:glowstone_dust", "D": "academy:data_chip"})
    shaped("brain_component", "academy:brain_component", 1, [" G ", "RCR", " G "], {"G": "minecraft:gold_nugget", "R": "minecraft:redstone", "C": "academy:calc_chip"})
    shaped("resonance_component", "academy:resonance_component", 1, ["PRP", " S "], {"P": "academy:constraint_plate", "R": "academy:reso_crystal", "S": "minecraft:redstone"})
    shaped("energy_convert_component", "academy:energy_convert_component", 1, ["C", "E", "R"], {"C": "academy:calc_chip", "E": "academy:energy_unit", "R": "academy:reso_crystal"})
    shaped("app_skill_tree", "academy:app_skill_tree", 1, ["C", "D", "I"], {"C": "minecraft:compass", "D": "academy:data_chip", "I": "academy:info_component"})
    shaped("app_media_player", "academy:app_media_player", 1, ["NNN", " D ", " I "], {"N": "minecraft:note_block", "D": "academy:data_chip", "I": "academy:info_component"})
    shaped("app_freq_transmitter", "academy:app_freq_transmitter", 1, ["R", "D", "I"], {"R": "academy:resonance_component", "D": "academy:data_chip", "I": "academy:info_component"})
    shaped("mag_hook", "academy:mag_hook", 3, [" P ", "PPP", " P "], {"P": "academy:reinforced_iron_plate"})
    shaped("developer_portable", "academy:developer_portable", 1, ["DGC", "BIE", "PLP"], {"D": "academy:data_chip", "G": "minecraft:glass_pane", "C": "academy:calc_chip", "B": "academy:brain_component", "I": "academy:info_component", "E": "academy:energy_convert_component", "P": "academy:constraint_plate", "L": "academy:crystal_low"})
    shapeless("silbarn", "academy:silbarn", 1, ["academy:imag_silicon_piece", "academy:imag_silicon_piece"])
    shaped("dev_normal", "academy:dev_normal", 1, [" P ", "MBI", "CFR"], {"P": "academy:developer_portable", "M": core_component(0), "B": "minecraft:white_bed", "I": "minecraft:piston", "C": "academy:crystal_normal", "F": "academy:machine_frame", "R": "minecraft:redstone"})
    shaped("dev_normal_components", "academy:dev_normal", 1, ["BIE", "MTP", "CFR"], {"B": "academy:brain_component", "I": "academy:info_component", "E": "academy:energy_convert_component", "M": core_component(0), "T": "minecraft:white_bed", "P": "minecraft:piston", "C": "academy:crystal_normal", "F": "academy:machine_frame", "R": "minecraft:redstone"})
    shaped("dev_advanced", "academy:dev_advanced", 1, ["PPP", "GNG", "SCR"], {"P": "academy:constraint_plate", "G": "minecraft:glowstone", "N": "academy:dev_normal", "S": "academy:node_standard", "C": "academy:crystal_pure", "R": "academy:reso_crystal"})
    shaped("ability_interferer", "academy:ability_interferer", 1, [" E ", "BMN", " C "], {"E": "academy:energy_convert_component", "B": "academy:brain_component", "M": "academy:machine_frame", "N": "minecraft:note_block", "C": "academy:calc_chip"})
    shapeless("wafer", "academy:wafer", 1, ["academy:imag_silicon_ingot"])
    shapeless("imag_silicon_piece", "academy:imag_silicon_piece", 2, ["academy:wafer"])
    shapeless("tutorial", "academy:tutorial", 1, ["minecraft:book", "academy:crystal_low"])
    shaped("magnetic_coil", "academy:magnetic_coil", 1, ["PRP", "PRP", "IDI"], {"P": "academy:constraint_plate", "R": "academy:reso_crystal", "I": "academy:reinforced_iron_plate", "D": "minecraft:diamond"})
    shaped("ac_rf_input", "academy:ac_rf_input", 1, ["EMP", " C "], {"E": "academy:energy_unit", "M": "academy:machine_frame", "P": "academy:constraint_plate", "C": "academy:energy_convert_component"})
    shaped("ac_rf_output", "academy:ac_rf_output", 1, ["EMR", " C "], {"E": "academy:energy_unit", "M": "academy:machine_frame", "R": "academy:reso_crystal", "C": "academy:energy_convert_component"})
    shapeless("ac_rf_input_from_output", "academy:ac_rf_input", 1, ["academy:ac_rf_output"])
    shapeless("ac_rf_output_from_input", "academy:ac_rf_output", 1, ["academy:ac_rf_input"])
    shaped("eu_input", "academy:eu_input", 1, ["EMI", " C "], {"E": "academy:energy_unit", "M": "academy:machine_frame", "I": "minecraft:copper_ingot", "C": "academy:energy_convert_component"})
    shaped("eu_output", "academy:eu_output", 1, ["EMI", " C "], {"E": "minecraft:redstone_block", "M": "academy:machine_frame", "I": "minecraft:copper_ingot", "C": "academy:energy_convert_component"})
    shapeless("eu_input_from_output", "academy:eu_input", 1, ["academy:eu_output"])
    shapeless("eu_output_from_input", "academy:eu_output", 1, ["academy:eu_input"])
    for name, source, result, xp in [
        ("smelting_constraint_ingot", "academy:constraint_metal", "academy:constraint_ingot", .7),
        ("smelting_imag_silicon", "academy:imagsil_ore", "academy:imag_silicon_ingot", .8),
        ("smelting_crystal", "academy:crystal_ore", "academy:crystal_low", .8),
    ]:
        dump(recipe_dir / f"{name}.json", {"type": "minecraft:smelting", "category": "misc", "cookingtime": 200,
            "experience": xp, "ingredient": ingredient(source), "result": {"id": result}})


def generate_loot_and_tags() -> None:
    loot_dir = DATA / "academy/loot_table/blocks"
    if loot_dir.exists(): shutil.rmtree(loot_dir)
    for name in BLOCKS:
        dropped = "academy:crystal_low" if name == "crystal_ore" else "academy:reso_crystal" if name == "reso_ore" else f"academy:{name}"
        entry: dict[str, object] = {"type": "minecraft:item", "name": dropped}
        if name in {"crystal_ore", "reso_ore"}:
            entry["functions"] = [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 3.0 if name == "crystal_ore" else 2.0}},
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"},
            ]
        dump(loot_dir / f"{name}.json", {"type": "minecraft:block", "pools": [{"bonus_rolls": 0, "rolls": 1, "entries": [entry],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}], "random_sequence": f"academy:blocks/{name}"})

    dump(DATA / "minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": [f"academy:{name}" for name in BLOCKS if name != "imag_phase"]})
    dump(DATA / "minecraft/tags/block/needs_iron_tool.json", {"replace": False, "values": ["academy:constraint_metal", "academy:crystal_ore", "academy:imagsil_ore", "academy:reso_ore", "academy:dev_advanced"]})
    dump(DATA / "c/tags/item/ingots/constraint_metal.json", {"replace": False, "values": ["academy:constraint_ingot"]})
    dump(DATA / "c/tags/item/plates/iron.json", {"replace": False, "values": ["academy:reinforced_iron_plate"]})
    dump(DATA / "c/tags/item/plates/constraint_metal.json", {"replace": False, "values": ["academy:constraint_plate"]})
    dump(DATA / "c/tags/item/gems/imag_crystal.json", {"replace": False, "values": ["academy:crystal_low", "academy:crystal_normal", "academy:crystal_pure"]})


def generate_chest_loot() -> None:
    targets = [
        "minecraft:chests/abandoned_mineshaft", "minecraft:chests/desert_pyramid",
        "minecraft:chests/jungle_temple", "minecraft:chests/stronghold_library",
        "minecraft:chests/simple_dungeon",
    ]
    dump(DATA / "neoforge/loot_modifiers/global_loot_modifiers.json", {
        "replace": False, "entries": ["academy:academy_chest_loot"],
    })
    stale_modifier = DATA / "academy/loot_modifier/academy_chest_loot.json"
    if stale_modifier.exists():
        stale_modifier.unlink()
    dump(DATA / "academy/loot_modifiers/academy_chest_loot.json", {
        "type": "neoforge:add_table",
        "conditions": [{"condition": "minecraft:any_of", "terms": [
            {"condition": "neoforge:loot_table_id", "loot_table_id": table} for table in targets
        ]}],
        "table": "academy:chests/academy_inject",
    })
    factor_entries = []
    for model, category in enumerate(["electromaster", "meltdowner", "teleporter", "vecmanip"]):
        factor_entries.append({"type": "minecraft:item", "name": "academy:induction_factor", "weight": 1,
            "functions": [{"function": "minecraft:set_components", "components": {
                "academy:ability_category": category, "minecraft:custom_model_data": model}}]})
    media_entries = []
    for model, media in enumerate(["sisters_noise", "only_my_railgun", "level5_judgelight"]):
        media_entries.append({"type": "minecraft:item", "name": "academy:media_item", "weight": 1,
            "functions": [{"function": "minecraft:set_components", "components": {
                "academy:media_id": media, "minecraft:custom_model_data": model}}]})
    dump(DATA / "academy/loot_table/chests/academy_inject.json", {
        "type": "minecraft:chest", "pools": [
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": .12}],
             "entries": factor_entries},
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": .10}],
             "entries": media_entries},
        ],
    })


def generate_media_sounds() -> None:
    sound_dir = ASSETS / "sounds/media"
    sound_dir.mkdir(parents=True, exist_ok=True)
    tracks = ["sisters_noise", "only_my_railgun", "level5_judgelight"]
    for track in tracks:
        shutil.copy2(LEGACY / "media/source" / f"{track}.ogg", sound_dir / f"{track}.ogg")
    sounds_path = ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8-sig"))
    for track in tracks:
        sounds[f"media.{track}"] = {"category": "music", "sounds": [{"name": f"academy:media/{track}", "stream": True}]}
    dump(sounds_path, sounds)


def generate_advancements() -> None:
    output = DATA / "academy/advancement"
    if output.exists(): shutil.rmtree(output)
    for source in sorted((LEGACY / "advancements").glob("*.json")):
        advancement = json.loads(source.read_text(encoding="utf-8-sig"))
        advancement.pop("__xconfgen", None)
        display = advancement.get("display", {})
        icon = display.get("icon", {})
        if "item" in icon:
            icon["id"] = icon.pop("item")
        if "background" in display:
            display["background"] = display["background"].replace("textures/blocks/", "textures/block/")
        for criterion in advancement.get("criteria", {}).values():
            criterion.clear()
            criterion["trigger"] = "minecraft:tick" if source.stem == "root" else "minecraft:impossible"
        dump(output / source.name, advancement)

    def legacy_adv(path: str, legacy_id: str, parent: str, icon: str, item_criterion: str | None = None) -> None:
        criterion = ({"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item_criterion}]}}
                     if item_criterion else {"trigger": "minecraft:impossible"})
        dump(output / "legacy" / f"{path}.json", {
            "parent": parent,
            "display": {"icon": {"id": icon}, "title": {"translate": f"achievement.ac_{legacy_id}"},
                        "description": {"translate": f"achievement.ac_{legacy_id}.desc"}},
            "criteria": {"complete": criterion},
        })

    defaults = [
        ("phase_liquid", "academy:root", "academy:matter_unit", None),
        ("matrix1", "academy:legacy/default/phase_liquid", "academy:matrix", "academy:matrix"),
        ("matrix2", "academy:legacy/default/matrix1", "academy:mat_core", "academy:mat_core"),
        ("node", "academy:legacy/default/phase_liquid", "academy:node_basic", "academy:node_basic"),
        ("developer1", "academy:legacy/default/node", "academy:developer_portable", "academy:developer_portable"),
        ("developer2", "academy:legacy/default/developer1", "academy:dev_normal", "academy:dev_normal"),
        ("developer3", "academy:legacy/default/developer2", "academy:dev_advanced", "academy:dev_advanced"),
        ("phasegen", "academy:legacy/default/phase_liquid", "academy:phase_gen", "academy:phase_gen"),
        ("solargen", "academy:legacy/default/phasegen", "academy:solar_gen", "academy:solar_gen"),
        ("windgen", "academy:legacy/default/solargen", "academy:windgen_main", "academy:windgen_main"),
        ("crystal", "academy:root", "academy:crystal_low", "academy:crystal_low"),
        ("terminal", "academy:root", "academy:terminal_installer", "academy:terminal_installer"),
    ]
    for legacy_id, parent, icon, criterion in defaults:
        legacy_adv(f"default/{legacy_id}", legacy_id, parent, icon, criterion)

    category_achievements = {
        "electromaster": ["arc_gen", "attack_creeper", "mag_movement", "body_intensify", "mine_detect", "thunder_bolt", "railgun", "thunder_clap"],
        "meltdowner": ["rad_intensify", "light_shield", "meltdowner", "mine_ray", "jet_engine", "electron_missile"],
        "teleporter": ["threatening_teleport", "critical_attack", "ignore_barrier", "flashing", "mastery"],
        "vecmanip": ["ground_shock", "dir_blast", "storm_wing", "blood_retro", "vec_reflection"],
    }
    category_icons = {"electromaster": "academy:coin", "meltdowner": "academy:silbarn",
                      "teleporter": "minecraft:ender_pearl", "vecmanip": "minecraft:feather"}
    for category, achievements in category_achievements.items():
        parent = "academy:root"
        for level in range(1, 6):
            legacy_id = f"{category}.lv{level}"
            path = f"{category}/lv{level}"
            legacy_adv(path, legacy_id, parent, category_icons[category])
            parent = f"academy:legacy/{path}"
        parent = f"academy:legacy/{category}/lv1"
        for achievement in achievements:
            legacy_id = f"{category}.{achievement}"
            path = f"{category}/{achievement}"
            legacy_adv(path, legacy_id, parent, category_icons[category])
            parent = f"academy:legacy/{path}"


def generate_particle_textures() -> None:
    """Expose selected legacy effect sprites through Minecraft's dedicated particle atlas."""
    mappings = {
        **{f"effects/arcs/{index}.png": f"arc/{index}.png" for index in range(10)},
        **{f"effects/mdball/{index}.png": f"meltdowner/{index}.png" for index in range(5)},
        "effects/tp_particle.png": "teleport.png",
        "effects/ripple.png": "vector.png",
        "entity/silbarn_frag.png": "silbarn_fragment.png",
    }
    particle_root = ASSETS / "textures/particle"
    if particle_root.exists(): shutil.rmtree(particle_root)
    for source, destination in mappings.items():
        target = particle_root / destination
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ASSETS / "textures" / source, target)


def generate_worldgen() -> None:
    # Preserve the 1.12.2/1.1.3 vein geometry and attempts (the same total yield as 1.0.7),
    # expanding only the vertical band for modern terrain.
    ores = {
        "constraint_metal": (12, 8, -32, 64),
        "crystal_ore": (12, 12, -32, 64),
        "imagsil_ore": (11, 8, -32, 64),
        "reso_ore": (9, 8, -32, 64),
    }
    for name, (size, count, minimum, maximum) in ores.items():
        dump(DATA / "academy/worldgen/configured_feature" / f"ore_{name}.json", {
            "type": "minecraft:ore", "config": {"discard_chance_on_air_exposure": 0.0, "size": size,
                "targets": [{"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"},
                             "state": {"Name": f"academy:{name}"}},
                            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"},
                             "state": {"Name": f"academy:{name}"}}]},
        })
        dump(DATA / "academy/worldgen/placed_feature" / f"ore_{name}.json", {
            "feature": f"academy:ore_{name}", "placement": [
                {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": minimum}, "max_inclusive": {"absolute": maximum}}},
                {"type": "minecraft:biome"},
            ],
        })
    dump(DATA / "academy/neoforge/biome_modifier/add_academy_ores.json", {
        "type": "neoforge:add_features", "biomes": "#c:is_overworld",
        "features": [f"academy:ore_{name}" for name in ores], "step": "underground_ores",
    })
    dump(DATA / "academy/worldgen/configured_feature/imag_phase_lake.json", {
        "type": "minecraft:lake", "config": {
            "fluid": {"type": "minecraft:simple_state_provider", "state": {"Name": "academy:imag_phase", "Properties": {"level": "0"}}},
            "barrier": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:stone"}},
        },
    })
    dump(DATA / "academy/worldgen/placed_feature/imag_phase_lake.json", {
        "feature": "academy:imag_phase_lake", "placement": [
            {"type": "minecraft:rarity_filter", "chance": 3}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 5}, "max_inclusive": {"absolute": 34}}},
            {"type": "minecraft:biome"},
        ],
    })
    dump(DATA / "academy/neoforge/biome_modifier/add_imag_phase_lakes.json", {
        "type": "neoforge:add_features", "biomes": "#c:is_overworld",
        "features": "academy:imag_phase_lake", "step": "local_modifications",
    })


def main() -> None:
    convert_languages()
    generate_models()
    generate_recipes()
    generate_loot_and_tags()
    generate_chest_loot()
    generate_media_sounds()
    generate_advancements()
    generate_particle_textures()
    generate_worldgen()
    print("AcademyCraft 1.21.1 resources regenerated.")


if __name__ == "__main__":
    main()
