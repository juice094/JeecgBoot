"""capability_catalog —— M2 能力目录生成器（治理基座）。

认知模型：政策/契约只引用目录 id；目录由本脚本从 Kimi Code 配置扫描生成，禁手改。
漂移即缺陷：政策文件出现目录外模型名 = drift_check 的 P1。

用法：
    python capability_catalog.py                # 扫描并写出 capability-catalog.yaml
    python capability_catalog.py --check        # 只比对不落盘（CI/门模式，漂移 exit 1）

模型源：~/.kimi-code/config.toml 的 [models."<id>"] 段
（provider/model/max_context_size/display_name/default_effort，tomllib 解析，Python 3.12 stdlib）。
yaml 序列化用 stdlib 手工拼（零新依赖军规），generated 戳取当前日期；
diff_with_existing 忽略 generated 行，改配置导致的条目增减即漂移。
default_effort 缺省的条目（如 ofm 系模型）省略该键，与 display 键同口径。
"""

from __future__ import annotations

import argparse
import datetime
import difflib
import sys
import tomllib
from pathlib import Path

DEFAULT_CONFIG = Path.home() / ".kimi-code" / "config.toml"
DEFAULT_OUTPUT = Path(__file__).resolve().parent.parent / "capability-catalog.yaml"


def scan_models(config_path: Path) -> list[dict[str, object]]:
    """扫描 config.toml，返回模型条目列表（dict 键：id/provider/model/context/display/effort）。

    display_name 与 default_effort 缺省时省略对应键（如 ofm 系模型）；顺序保持 config 原文序。
    """
    with config_path.open("rb") as handle:
        data = tomllib.load(handle)
    models: list[dict[str, object]] = []
    for model_id, entry in data.get("models", {}).items():
        if not isinstance(entry, dict):
            continue
        item: dict[str, object] = {
            "id": model_id,
            "provider": entry.get("provider", ""),
            "model": entry.get("model", ""),
            "context": entry.get("max_context_size", 0),
        }
        if entry.get("display_name"):
            item["display"] = entry["display_name"]
        if entry.get("default_effort"):
            item["default_effort"] = entry["default_effort"]
        models.append(item)
    return models


def _yaml_scalar(value: object) -> str:
    """受限子集标量序列化：空串/含 ': '、' #'、首尾空白或以引号等特殊字符开头时加双引号。"""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, int):
        return str(value)
    text = str(value)
    special_leading = ('"', "'", "[", "]", "{", "}", "#", "&", "*", "!", "|", ">", "%", "@", "`")
    if (
        text == ""
        or text != text.strip()
        or ": " in text
        or " #" in text
        or text.startswith(special_leading)
    ):
        return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'
    return text


def render_catalog(models: list[dict[str, object]], today: str) -> str:
    """渲染 capability-catalog.yaml 文本（手工拼 YAML，禁手改声明在头注释）。"""
    lines = [
        "# capability-catalog.yaml —— 由 capability_catalog.py 从 config.toml 生成，禁手改",
        "# 政策只允许引用 id，禁止引用 display_name（K2.8/K3 事件根因防线）",
        f"generated: {today}",
        "models:",
    ]
    for model in models:
        lines.append(f"  - id: {_yaml_scalar(model['id'])}")
        for key in ("display", "provider", "model", "context", "default_effort"):
            if key in model:
                lines.append(f"    {key}: {_yaml_scalar(model[key])}")
    return "\n".join(lines) + "\n"


def _without_generated(text: str) -> list[str]:
    return [line for line in text.splitlines() if not line.startswith("generated:")]


def diff_with_existing(text: str, output: Path) -> bool:
    """与现有生成物比对（忽略 generated 行）。返回 True=一致。"""
    if not output.exists():
        return False
    existing = output.read_text(encoding="utf-8")
    return _without_generated(existing) == _without_generated(text)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true", help="只比对不落盘")
    args = parser.parse_args(argv)

    try:
        models = scan_models(args.config)
    except FileNotFoundError:
        print(f"错误：模型源不存在：{args.config}", file=sys.stderr)
        return 2
    except tomllib.TOMLDecodeError as exc:
        print(f"错误：模型源解析失败：{args.config}：{exc}", file=sys.stderr)
        return 2

    text = render_catalog(models, datetime.date.today().isoformat())

    if args.check:
        if diff_with_existing(text, args.output):
            print(f"OK：{args.output.name} 与 {args.config.name} 一致（{len(models)} 个模型）")
            return 0
        if not args.output.exists():
            print(f"漂移：{args.output} 不存在，需要先运行生成", file=sys.stderr)
            return 1
        existing = args.output.read_text(encoding="utf-8")
        diff = difflib.unified_diff(
            _without_generated(existing),
            _without_generated(text),
            fromfile=f"existing/{args.output.name}",
            tofile=f"generated/{args.output.name}",
            lineterm="",
        )
        print("\n".join(diff), file=sys.stderr)
        print("漂移：capability-catalog.yaml 与 config.toml 不一致（见上方 diff）", file=sys.stderr)
        return 1

    args.output.write_text(text, encoding="utf-8")
    print(f"已写出 {args.output}（{len(models)} 个模型）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
