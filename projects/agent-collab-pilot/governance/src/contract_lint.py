"""contract_lint —— M3/M4 派工契约静态校验（治理基座）。

认知模型：契约是规则的承载层；契约本身必须过门，坏契约不许上派工单。
校验规则（每条一个函数，失败即 defect）：
  R1 member ∈ pool.yaml 且 status=active
  R2 model ∈ capability-catalog.yaml 的 id（禁止 display_name——K2.8/K3 事件根因防线）
  R3 scope.deny 与 scope.write 出现同一 glob（deny 优先但有歧义，按 defect 上报请作者确认）
  R4 red-lines 必含 no-rm-rf 与 recycle-bin
  R5 confirm-gate 非空时 verification 不得为空

豁免：member 或 model 为空串时视为未填模板（contracts/_template.yaml 的占位语义），
整单跳过 R1-R5——模板只是骨架占位，不承担派工语义；只要填了 member 就全量校验。

用法：python contract_lint.py <契约.yaml> [更多契约...]；exit 0=全过，1=有 defect。
解析 YAML 用 stdlib 手工解析本仓库受限子集（键: 值、缩进列表、字符串列表），不引第三方。
受限子集：整行/行尾注释（引号感知）、key: value、key: 嵌套块、`- 标量`、`- key: value`、
内联列表 [a, "b"]、双引号字符串、整数、true/false。不支持多行字符串与流式映射。

未实现（骨架遗留，超出本单范围）：R6 write glob 前缀覆盖 deny 检查——`**` 前缀语义易误报，
待有真实案例再定口径。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

GOVERNANCE_ROOT = Path(__file__).resolve().parent.parent
POOL_PATH = GOVERNANCE_ROOT / "members" / "pool.yaml"
CATALOG_PATH = GOVERNANCE_ROOT / "capability-catalog.yaml"

REQUIRED_RED_LINES = ("no-rm-rf", "recycle-bin")
_INT_RE = re.compile(r"-?\d+")


class RestrictedYamlError(ValueError):
    """受限 YAML 子集解析失败。"""


# ---------- 受限 YAML 子集解析 ----------

def _strip_comment(line: str) -> str:
    """去掉行尾注释（引号感知）：# 只有在引号外且前邻空白时才视为注释起点。"""
    quote: str | None = None
    prev = ""
    for i, ch in enumerate(line):
        if quote == '"' and prev == "\\":
            prev = ch
            continue
        if ch in ("'", '"'):
            if quote is None:
                quote = ch
            elif quote == ch:
                quote = None
        elif ch == "#" and quote is None and (i == 0 or line[i - 1] in " \t"):
            return line[:i]
        prev = ch
    return line


def _split_inline(inner: str, lineno: int) -> list[str]:
    """按逗号切分内联列表元素，逗号在引号内不切。"""
    parts: list[str] = []
    buf: list[str] = []
    quote: str | None = None
    prev = ""
    for ch in inner:
        if quote == '"' and prev == "\\":
            buf.append(ch)
            prev = ch
            continue
        if ch in ("'", '"'):
            if quote is None:
                quote = ch
            elif quote == ch:
                quote = None
            buf.append(ch)
        elif ch == "," and quote is None:
            parts.append("".join(buf).strip())
            buf = []
        else:
            buf.append(ch)
        prev = ch
    if quote is not None:
        raise RestrictedYamlError(f"第 {lineno} 行：内联列表引号未闭合")
    tail = "".join(buf).strip()
    if tail:
        parts.append(tail)
    return parts


def _parse_scalar(text: str, lineno: int) -> object:
    if text == "[]":
        return []
    if text == "{}":
        return {}
    if text.startswith("["):
        if not text.endswith("]"):
            raise RestrictedYamlError(f"第 {lineno} 行：内联列表缺少 ']'")
        return [_parse_scalar(part, lineno) for part in _split_inline(text[1:-1], lineno)]
    if text.startswith('"'):
        if len(text) < 2 or not text.endswith('"'):
            raise RestrictedYamlError(f"第 {lineno} 行：字符串引号未闭合")
        body = text[1:-1]
        out: list[str] = []
        i = 0
        while i < len(body):
            ch = body[i]
            if ch == "\\" and i + 1 < len(body):
                out.append({"n": "\n", "t": "\t", '"': '"', "\\": "\\"}.get(body[i + 1], body[i + 1]))
                i += 2
            else:
                out.append(ch)
                i += 1
        return "".join(out)
    if text.startswith("'"):
        if len(text) < 2 or not text.endswith("'"):
            raise RestrictedYamlError(f"第 {lineno} 行：字符串引号未闭合")
        return text[1:-1]
    if text == "true":
        return True
    if text == "false":
        return False
    if text in ("null", "~"):
        return None
    if _INT_RE.fullmatch(text):
        return int(text)
    return text


def _is_key_value(content: str) -> bool:
    key, sep, _ = content.partition(":")
    return bool(sep) and bool(key.strip()) and not content.startswith(("- ", "? ", ": "))


def _parse_map(lines: list[tuple[int, str, int]], i: int, indent: int) -> tuple[dict[str, object], int]:
    result: dict[str, object] = {}
    while i < len(lines):
        ind, content, lineno = lines[i]
        if ind < indent:
            break
        if ind > indent:
            raise RestrictedYamlError(f"第 {lineno} 行：意外缩进（期望 {indent} 空格，实际 {ind}）")
        if content.startswith("-"):
            break
        key, _, rest = content.partition(":")
        key = key.strip()
        if not key:
            raise RestrictedYamlError(f"第 {lineno} 行：空键名")
        rest = rest.strip()
        i += 1
        if rest:
            result[key] = _parse_scalar(rest, lineno)
        elif i < len(lines) and lines[i][0] > indent:
            result[key], i = _parse_block(lines, i, lines[i][0])
        else:
            result[key] = None
    return result, i


def _parse_list(lines: list[tuple[int, str, int]], i: int, indent: int) -> tuple[list[object], int]:
    items: list[object] = []
    while i < len(lines):
        ind, content, lineno = lines[i]
        if ind != indent or not content.startswith("-"):
            break
        rest = content[1:].lstrip(" ")
        col = indent + 2
        if _is_key_value(rest):
            lines[i] = (col, rest, lineno)
            item, i = _parse_map(lines, i, col)
            items.append(item)
        else:
            items.append(_parse_scalar(rest, lineno))
            i += 1
    return items, i


def _parse_block(lines: list[tuple[int, str, int]], i: int, indent: int) -> tuple[object, int]:
    if lines[i][1].startswith("-"):
        return _parse_list(lines, i, indent)
    return _parse_map(lines, i, indent)


def load_restricted_yaml(path: Path) -> dict[str, object]:
    """解析本仓库契约文件的受限 YAML 子集（见模块 docstring），失败抛 RestrictedYamlError。"""
    raw = path.read_text(encoding="utf-8").splitlines()
    lines: list[tuple[int, str, int]] = []
    for n, original in enumerate(raw, 1):
        stripped = _strip_comment(original).rstrip()
        if not stripped.strip():
            continue
        indent = len(stripped) - len(stripped.lstrip(" "))
        if "\t" in stripped[:indent]:
            raise RestrictedYamlError(f"第 {n} 行：缩进禁用 tab")
        lines.append((indent, stripped.strip(), n))
    if not lines:
        return {}
    if lines[0][0] != 0:
        raise RestrictedYamlError(f"第 {lines[0][2]} 行：顶层必须从 0 缩进开始")
    result, i = _parse_block(lines, 0, 0)
    if i != len(lines):
        raise RestrictedYamlError(f"第 {lines[i][2]} 行：多余内容无法解析")
    if not isinstance(result, dict):
        raise RestrictedYamlError("顶层必须是键值映射")
    return result


# ---------- 规则 R1-R5 ----------

def _as_list(value: object) -> list[object]:
    if value is None:
        return []
    if isinstance(value, list):
        return value
    return [value]


def check_r1_member_in_pool(contract: dict[str, object], pool: dict[str, object]) -> list[str]:
    """R1：member 在池且 active。空 member 视为未填模板，豁免（见模块 docstring）。"""
    member = contract.get("member")
    if member in (None, ""):
        return []
    entries = _as_list(pool.get("pool"))
    for entry in entries:
        if isinstance(entry, dict) and entry.get("member") == member:
            if entry.get("status") == "active":
                return []
            return [f"member '{member}' 在池中但 status={entry.get('status')!r}（非 active 不可派工）"]
    return [f"member '{member}' 不在 members/pool.yaml 白名单"]


def check_r2_model_in_catalog(contract: dict[str, object], catalog: dict[str, object]) -> list[str]:
    """R2：model 必须是目录 id。空 model 视为未填模板，豁免。"""
    model = contract.get("model")
    if model in (None, ""):
        return []
    models = _as_list(catalog.get("models"))
    catalog_ids = {m.get("id") for m in models if isinstance(m, dict)}
    if model in catalog_ids:
        return []
    hint = "（它看起来是 display_name，政策只允许引用 id——K2.8/K3 事件根因）" if " " in str(model) else ""
    return [f"model '{model}' 不在 capability-catalog.yaml 的 id 列表{hint}"]


def check_r3_deny_overlaps_write(contract: dict[str, object]) -> list[str]:
    """R3：write 与 deny 出现同一 glob 即歧义（deny 优先，但按 defect 上报请作者确认）。"""
    scope = contract.get("scope")
    if not isinstance(scope, dict):
        return []
    write = {str(p) for p in _as_list(scope.get("write"))}
    deny = {str(p) for p in _as_list(scope.get("deny"))}
    overlap = sorted(write & deny)
    return [
        f"scope.write 与 scope.deny 出现同一 glob '{pattern}'（deny 优先，存在歧义请作者确认）"
        for pattern in overlap
    ]


def check_r4_red_lines(contract: dict[str, object]) -> list[str]:
    """R4：红线最低集 no-rm-rf + recycle-bin。"""
    red_lines = {str(item) for item in _as_list(contract.get("red-lines"))}
    return [f"red-lines 缺少必需红线 '{required}'" for required in REQUIRED_RED_LINES if required not in red_lines]


def check_r5_confirm_gate_needs_verification(contract: dict[str, object]) -> list[str]:
    """R5：确认门非空时必须声明验证分级（报告什么级说什么级）。"""
    gate = _as_list(contract.get("confirm-gate"))
    verification = contract.get("verification")
    if gate and not verification:
        return ["confirm-gate 非空但 verification 为空（高敏操作必须声明验证分级）"]
    return []


def lint(path: Path) -> list[str]:
    """对单个契约跑全部规则，返回 defect 列表（带 [契约名/规则号] 前缀）。

    豁免：member 或 model 为空串 ⇒ 视为未填模板（contracts/_template.yaml 的占位语义），
    整单跳过 R1-R5；填了 member 即全量校验。
    """
    contract = load_restricted_yaml(path)
    if contract.get("member") in (None, "") or contract.get("model") in (None, ""):
        return []

    pool = load_restricted_yaml(POOL_PATH)
    if CATALOG_PATH.exists():
        catalog = load_restricted_yaml(CATALOG_PATH)
        catalog_missing: list[str] = []
    else:
        catalog = {"models": []}
        catalog_missing = [f"capability-catalog.yaml 缺失（先跑 capability_catalog.py 生成），model 未校验"]

    checks = [
        ("R1", check_r1_member_in_pool(contract, pool)),
        ("R2", catalog_missing + check_r2_model_in_catalog(contract, catalog)),
        ("R3", check_r3_deny_overlaps_write(contract)),
        ("R4", check_r4_red_lines(contract)),
        ("R5", check_r5_confirm_gate_needs_verification(contract)),
    ]
    return [f"[{path.name}/{rule}] {message}" for rule, messages in checks for message in messages]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("contracts", nargs="+", type=Path)
    args = parser.parse_args(argv)
    all_defects: list[str] = []
    for path in args.contracts:
        try:
            defects = lint(path)
        except (RestrictedYamlError, OSError) as exc:
            defects = [f"[{path.name}/YAML] 解析失败：{exc}"]
        for defect in defects:
            print(f"DEFECT {defect}")
        all_defects.extend(defects)
    if all_defects:
        print(f"契约校验：共 {len(all_defects)} 个 defect", file=sys.stderr)
        return 1
    print(f"契约校验：{len(args.contracts)} 个文件全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
