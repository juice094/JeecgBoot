"""contract_lint / drift_check 表驱动测试（治理基座 M3/M4/M6）。

每条规则正反两例（内存契约 dict 直查规则函数）；R2 另有一个端到端夹具：
临时契约 yaml 字面写 model: "K2.8 Preview"（display_name 引用）过 lint()，
是 K2.8/K3 事件的回归防线。R6/D3 各带端到端反例。夹具只写系统临时目录，不入 members/。
正例基准：contracts/_template.yaml 自身必须 lint 全过
（member/model 空串按豁免规则处理，见 contract_lint.lint docstring）。
跑法：python -m unittest discover tests -v（pytest 亦可收集）。
"""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

SRC_DIR = Path(__file__).resolve().parent.parent / "src"
sys.path.insert(0, str(SRC_DIR))

import contract_lint  # noqa: E402
import drift_check  # noqa: E402

POOL: dict[str, object] = {
    "pool": [
        {"member": "k2.8", "model": "kimi-code/kimi-for-coding", "role": "primary-constructor", "status": "active"},
        {"member": "flash", "model": "deepseek/deepseek-flash", "role": "batch-operator", "status": "active"},
        {"member": "suspended-bot", "model": "deepseek/deepseek-flash", "status": "suspended"},
    ]
}

CATALOG: dict[str, object] = {
    "models": [
        {"id": "kimi-code/kimi-for-coding", "display": "K2.8 Preview", "provider": "managed:kimi-code", "context": 1048576},
        {"id": "deepseek/deepseek-flash", "display": "DeepSeek-V4.1-Flash", "provider": "deepseek", "context": 1048576},
    ]
}


def make_contract(**overrides: object) -> dict[str, object]:
    """内存夹具契约：默认是合规的 k2.8 派工单，overrides 逐项覆盖造反例。

    下划线键名自动转连字符（red_lines → red-lines），与契约 yaml 的键一致。
    """
    contract: dict[str, object] = {
        "contract": "AGT-2026-9999",
        "member": "k2.8",
        "model": "kimi-code/kimi-for-coding",
        "scope": {"repos": ["jeecg-team-web"], "write": ["**/src/**"], "deny": [".env"]},
        "verification": "L1-typecheck",
        "confirm-gate": [],
        "red-lines": ["no-rm-rf", "recycle-bin"],
    }
    contract.update({key.replace("_", "-"): value for key, value in overrides.items()})
    return contract


class ContractLintRulesTest(unittest.TestCase):
    def assert_cases(self, check, cases: list[tuple[str, dict[str, object], int]]) -> None:
        for name, contract, expected in cases:
            with self.subTest(case=name):
                defects = check(contract)
                self.assertEqual(len(defects), expected, msg=f"{name}：期望 {expected} 个 defect，实际 {defects}")

    def test_r1_member_must_be_active_in_pool(self) -> None:
        def check(contract: dict[str, object]) -> list[str]:
            return contract_lint.check_r1_member_in_pool(contract, POOL)

        self.assert_cases(
            check,
            [
                ("正例-池内-active", make_contract(), 0),
                ("正例-池内-active-flash", make_contract(member="flash", model="deepseek/deepseek-flash"), 0),
                ("反例-池外-member", make_contract(member="k3"), 1),
                ("反例-池内非-active", make_contract(member="suspended-bot", model="deepseek/deepseek-flash"), 1),
                ("豁免-空-member", make_contract(member=""), 0),
            ],
        )

    def test_r2_model_must_be_catalog_id_not_display_name(self) -> None:
        def check(contract: dict[str, object]) -> list[str]:
            return contract_lint.check_r2_model_in_catalog(contract, CATALOG)

        defects = check(make_contract(model="K2.8 Preview"))
        self.assertTrue(defects, "display_name 引用必须被抓（K2.8/K3 事件回归防线）")
        self.assertIn("K2.8 Preview", defects[0])
        self.assert_cases(
            check,
            [
                ("正例-目录-id", make_contract(), 0),
                ("反例-display-name", make_contract(model="K2.8 Preview"), 1),
                ("反例-目录外-id", make_contract(model="kimi-code/k3"), 1),
                ("豁免-空-model", make_contract(model=""), 0),
            ],
        )

    def test_r2_display_name_reference_via_lint_caught(self) -> None:
        """端到端：临时契约字面写 model: "K2.8 Preview"，lint() 必须报 [文件/R2]。"""
        text = (
            'contract: "AGT-2026-9998"\n'
            "member: k2.8\n"
            'model: "K2.8 Preview"\n'
            "scope:\n"
            "  repos: [jeecg-team-web]\n"
            "  write: [\"**/src/**\"]\n"
            "  deny: [.env]\n"
            "verification: L1-typecheck\n"
            "confirm-gate: []\n"
            "red-lines: [no-rm-rf, recycle-bin]\n"
        )
        with tempfile.TemporaryDirectory() as tmp:
            fixture = Path(tmp) / "bad-display-name.contract.yaml"
            fixture.write_text(text, encoding="utf-8")
            defects = contract_lint.lint(fixture)
        self.assertTrue(any(d.startswith(f"[{fixture.name}/R2]") for d in defects), f"未见 R2 defect：{defects}")

    def test_r3_write_deny_overlap_flagged(self) -> None:
        self.assert_cases(
            contract_lint.check_r3_deny_overlaps_write,
            [
                ("正例-无交集", make_contract(), 0),
                ("正例-不同-glob", make_contract(scope={"write": ["**/docs/**"], "deny": ["**/src/**"]}), 0),
                ("反例-同一-glob", make_contract(scope={"write": ["**/src/**"], "deny": ["**/src/**"]}), 1),
            ],
        )

    def test_r4_red_lines_minimum_set(self) -> None:
        self.assert_cases(
            contract_lint.check_r4_red_lines,
            [
                ("正例-含最低集", make_contract(), 0),
                ("正例-含最低集加项", make_contract(red_lines=["no-rm-rf", "recycle-bin", "no-force-push"]), 0),
                ("反例-缺-recycle-bin", make_contract(red_lines=["no-rm-rf"]), 1),
                ("反例-空-red-lines", make_contract(red_lines=[]), 2),
            ],
        )

    def test_r5_confirm_gate_requires_verification(self) -> None:
        self.assert_cases(
            contract_lint.check_r5_confirm_gate_needs_verification,
            [
                ("正例-门空", make_contract(), 0),
                ("正例-门非空有验证级", make_contract(confirm_gate=["git-push"], verification="L1-typecheck"), 0),
                ("反例-门非空验证级为空", make_contract(confirm_gate=["git-push"], verification=""), 1),
            ],
        )

    def test_r6_model_must_match_pool_binding(self) -> None:
        def check(contract: dict[str, object]) -> list[str]:
            return contract_lint.check_r6_model_matches_pool(contract, POOL)

        self.assert_cases(
            check,
            [
                ("正例-k2.8-绑定一致", make_contract(), 0),
                ("正例-flash-绑定一致", make_contract(member="flash", model="deepseek/deepseek-flash"), 0),
                ("反例-写同族非绑定模型", make_contract(model="kimi-code/kimi-for-coding-low"), 1),
                ("反例-写池内另一成员模型", make_contract(model="deepseek/deepseek-flash"), 1),
                ("静默-member-不在池", make_contract(member="k3"), 0),
                ("豁免-空-member", make_contract(member=""), 0),
                ("豁免-空-model", make_contract(model=""), 0),
            ],
        )

    def test_r6_binding_mismatch_via_lint_caught(self) -> None:
        """端到端：临时契约 member=k2.8 但 model 写 low 档（目录内 id 但非该 member 绑定模型），lint() 必报 [文件/R6]。"""
        text = (
            'contract: "AGT-2026-9997"\n'
            "member: k2.8\n"
            "model: kimi-code/kimi-for-coding-low\n"
            "scope:\n"
            "  repos: [jeecg-team-web]\n"
            "  write: [\"**/src/**\"]\n"
            "  deny: [.env]\n"
            "verification: L1-typecheck\n"
            "confirm-gate: []\n"
            "red-lines: [no-rm-rf, recycle-bin]\n"
        )
        with tempfile.TemporaryDirectory() as tmp:
            fixture = Path(tmp) / "binding-mismatch.contract.yaml"
            fixture.write_text(text, encoding="utf-8")
            defects = contract_lint.lint(fixture)
        self.assertTrue(any(d.startswith(f"[{fixture.name}/R6]") for d in defects), f"未见 R6 defect：{defects}")

    def test_template_itself_lints_clean(self) -> None:
        """_template.yaml 是正例基准：member/model 空串按豁免规则整单跳过，必须零 defect。"""
        template = Path(__file__).resolve().parent.parent / "contracts" / "_template.yaml"
        self.assertEqual(contract_lint.lint(template), [])


class DriftCheckEffortPolicyTest(unittest.TestCase):
    def test_d3_default_effort_max_is_drift(self) -> None:
        catalog_ok: dict[str, object] = {
            "models": [
                {"id": "kimi-code/kimi-for-coding", "default_effort": "high"},
                {"id": "kimi-code/kimi-for-coding-low", "default_effort": "low"},
                {"id": "kimi-code/kimi-for-coding-highspeed"},
            ]
        }
        catalog_bad: dict[str, object] = {
            "models": [
                {"id": "kimi-code/kimi-for-coding", "default_effort": "high"},
                {"id": "kimi-code/k3", "default_effort": "max"},
            ]
        }
        self.assertEqual(drift_check.check_default_effort_policy(catalog_ok), [])
        drifts = drift_check.check_default_effort_policy(catalog_bad)
        self.assertEqual(len(drifts), 1, f"max 默认档必须被抓：{drifts}")
        self.assertIn("kimi-code/k3", drifts[0])
        self.assertIn("P2", drifts[0])

    def test_d3_real_catalog_has_no_max_default(self) -> None:
        """现况验收：真实 capability-catalog.yaml 不得含 max 默认档（config 现况 high/low/high）。"""
        catalog_path = Path(__file__).resolve().parent.parent / "capability-catalog.yaml"
        catalog = contract_lint.load_restricted_yaml(catalog_path)
        self.assertEqual(drift_check.check_default_effort_policy(catalog), [])


if __name__ == "__main__":
    unittest.main()
