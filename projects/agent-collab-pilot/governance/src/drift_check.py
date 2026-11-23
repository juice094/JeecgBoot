"""drift_check —— M6 漂移检测（治理基座）。

认知模型：政策 vs 现实 的 diff 即缺陷。两条检测面：
  D1 政策↔目录：扫描政策文件（members/pool.yaml、members/*.contract.yaml 的 model 键，
     以及 C:/Users/22414/.agents/AGENTS.md 中反引号别名 kimi-code/*、deepseek/*），
     出现的模型引用必须是 capability-catalog.yaml 里的 id；目录外引用=漂移。
  D2 生成物↔源：capability_catalog.py --check 的退出码接力（1=目录与 config.toml 漂移）。
  D3 思考档政策：目录中任一模型 default_effort="max" 即漂移 P2——max 禁止作为条目默认档
     （token 流失实证，见 governance/README.md 军规），硬任务由主会话在派工单显式声明。

用法：python drift_check.py [--root governance目录]；exit 0=无漂移，1=有漂移。
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import contract_lint  # noqa: E402

GOVERNANCE_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_AGENTS = Path.home() / ".agents" / "AGENTS.md"
ALIAS_RE = re.compile(r"`((?:kimi-code|deepseek)/[A-Za-z0-9._-]+)`")


def collect_model_references(root: Path) -> dict[str, list[str]]:
    """收集政策文件中的模型引用：{引用id: [出处文件,...]}。

    面：pool.yaml 各成员 model 键、各契约顶层 model 键、AGENTS.md 反引号别名。
    空串（模板占位）与解析失败的文件在此静默跳过——解析问题由 contract_lint 门禁负责。
    """
    refs: dict[str, list[str]] = {}
    policy_files = [root / "members" / "pool.yaml"]
    policy_files += sorted((root / "members").glob("*.contract.yaml"))
    for path in policy_files:
        try:
            data = contract_lint.load_restricted_yaml(path)
        except (contract_lint.RestrictedYamlError, OSError):
            continue
        found: list[str] = []
        if path.name == "pool.yaml":
            for entry in contract_lint._as_list(data.get("pool")):
                if isinstance(entry, dict):
                    found.append(str(entry.get("model") or ""))
        else:
            found.append(str(data.get("model") or ""))
        for model in found:
            if model:
                refs.setdefault(model, []).append(path.name)
    if DEFAULT_AGENTS.exists():
        text = DEFAULT_AGENTS.read_text(encoding="utf-8")
        for alias in sorted(set(ALIAS_RE.findall(text))):
            refs.setdefault(alias, []).append(str(DEFAULT_AGENTS))
    return refs


def check_policy_vs_catalog(root: Path) -> list[str]:
    """D1：政策引用 ⊆ 目录 id。返回 drift 列表。"""
    catalog_path = root / "capability-catalog.yaml"
    if not catalog_path.exists():
        return ["D1: capability-catalog.yaml 不存在（先跑 capability_catalog.py 生成）"]
    catalog = contract_lint.load_restricted_yaml(catalog_path)
    catalog_ids = {m.get("id") for m in contract_lint._as_list(catalog.get("models")) if isinstance(m, dict)}
    drifts = []
    for ref, sources in sorted(collect_model_references(root).items()):
        if ref not in catalog_ids:
            drifts.append(f"D1: 目录外模型引用 '{ref}'（出处: {', '.join(sources)}）")
    return drifts


def check_default_effort_policy(catalog: dict[str, object]) -> list[str]:
    """D3：目录条目 default_effort 禁止为 max。返回 drift 列表（P2 级政策）。"""
    drifts = []
    for entry in contract_lint._as_list(catalog.get("models")):
        if isinstance(entry, dict) and entry.get("default_effort") == "max":
            drifts.append(
                f"D3: 目录条目 '{entry.get('id')}' default_effort=max（P2：max 禁止作为条目默认档，"
                "硬任务由主会话在派工单显式声明）"
            )
    return drifts


def check_generated_vs_source(root: Path) -> list[str]:
    """D2：调 capability_catalog.py --check。返回 drift 列表。"""
    script = root / "src" / "capability_catalog.py"
    proc = subprocess.run(
        [sys.executable, str(script), "--check"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proc.returncode == 0:
        return []
    detail = (proc.stdout + proc.stderr).strip()
    if proc.returncode == 1:
        lines = [f"D2: 能力目录与 config.toml 漂移（capability_catalog.py --check 未过）"]
        lines += [f"    {line}" for line in detail.splitlines() if line.strip()]
        return lines
    first_line = detail.splitlines()[0] if detail else "无输出"
    return [f"D2: 能力目录生成器执行失败（exit {proc.returncode}）：{first_line}"]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=GOVERNANCE_ROOT)
    args = parser.parse_args(argv)
    catalog_path = args.root / "capability-catalog.yaml"
    if catalog_path.exists():
        catalog = contract_lint.load_restricted_yaml(catalog_path)
        effort_drifts = check_default_effort_policy(catalog)
    else:
        effort_drifts = []  # 目录缺失由 D1 上报，不重复
    drifts = check_policy_vs_catalog(args.root) + check_generated_vs_source(args.root) + effort_drifts
    if drifts:
        print(f"漂移检测：发现 {len(drifts)} 项漂移")
        for drift in drifts:
            print(f"  {drift}")
        return 1
    print("漂移检测：无漂移（D1 政策↔目录 / D2 生成物↔源 / D3 思考档政策 均通过）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
