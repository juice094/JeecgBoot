# governance — Agent 治理基座（认知模型入口）

> 本目录是 `docs/26-agent-governance-baseline-design.md`（v1.1）的代码载体。
> 认知模型一句话：**一套身份底座（Jeecg IAM），两类 principal（人类/agent），agent 只加四增量；规则随契约走，不随会话走；漂移即缺陷。**

## 结构

```
governance/
├── capability-catalog.yaml   # 能力目录（生成物，禁手改；源=~/.kimi-code/config.toml）
├── members/
│   ├── pool.yaml             # 成员池登记（白名单模型唯一来源）
│   ├── k2.8.contract.yaml    # 池成员长期契约（resume 复用的上下文锚点）
│   └── flash.contract.yaml
├── contracts/
│   └── _template.yaml        # 派工契约模板（每次派工复制改名）
├── src/
│   ├── capability_catalog.py # M2：扫描 config.toml 生成目录
│   ├── contract_lint.py      # M3/M4：契约静态校验（scope 只减不增、模型∈目录、红线必列）
│   └── drift_check.py        # M6：政策↔目录 / 注入点↔分层 漂移检测
└── tests/
    └── test_contract_lint.py # 上述规则的骨架测试
```

## 军规（本目录适用）

- 模型白名单以 `members/pool.yaml` 为唯一权威源；政策/契约只引用 `capability-catalog.yaml` 里的 `id`，**禁止引用 display_name**（K2.8/K3 事件根因）。
- 派工必经契约：`cp contracts/_template.yaml contracts/AGT-YYYY-NNNN.yaml`，填全五段（member/model/scope/verification/red-lines），`confirm-gate` 非空即需人类一字确认。
- 生成物（capability-catalog.yaml）头部有 `generated` 戳；手改会被 drift_check 抓住。
- 思考档政策：`default_effort` 禁止为 `max`（max 档 token 流失实证，2026-11-19 宿指出）；硬任务由主会话在派工单显式声明，不走条目默认档。drift_check D3 把关：目录出现 max 默认档即漂移 P2。
- 测试：`python -m pytest tests/ -q`（零依赖，stdlib unittest 亦可跑）。
