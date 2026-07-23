"""Workflow definitions for Lumo.

Plan generation: decompose → detail → verify → publish
Quiz generation: generate → verify → publish
"""

import json

import senza

from lumo.config import ProviderConfig, create_provider
from lumo.prompts import SYSTEM_PROMPT


# ── Plan Generation Workflow ──

PLAN_WORKFLOW = {
    "entry_step": "decompose",
    "steps": [
        {
            "id": "decompose",
            "name": "Decompose Goal",
            "prompt": (
                "你是学习计划设计师。用户目标：{goal}，每天可用 {daily_minutes} 分钟。\n"
                "请生成完整周期的周大纲（每周主题和子目标），用 JSON 格式输出：\n"
                '```json\n{"weeks": [{"week": 1, "theme": "...", "subgoals": ["...", "..."]}]}\n```\n'
                "只输出 JSON，不要其他内容。"
            ),
            "allowed_tools": [],
        },
        {
            "id": "detail",
            "name": "Detail Current Week",
            "prompt": (
                "根据以下周大纲，细化第 {week_num} 周的每日任务：\n{week_outline}\n\n"
                "每天 1-3 个任务，每个任务包含标题和简短描述。用 JSON 格式输出：\n"
                '```json\n{"tasks": [{"day": 1, "title": "...", "description": "...", "knowledge_points": ["..."]}]}\n```\n'
                "只输出 JSON，不要其他内容。"
            ),
            "allowed_tools": [],
        },
        {
            "id": "verify",
            "name": "Verify Plan",
            "prompt": (
                "审查以下学习计划是否合理（任务量、难度递进、知识点覆盖）：\n{plan_detail}\n\n"
                "如果合理，回复 APPROVED。如果有问题，回复 ISSUES: 然后列出问题。"
            ),
            "allowed_tools": [],
        },
        {
            "id": "publish",
            "name": "Publish Plan",
            "prompt": (
                "将验证通过的计划整理为最终格式。输出 JSON：\n"
                '```json\n{"title": "...", "tasks": [...]}\n```\n'
                "只输出 JSON。"
            ),
            "allowed_tools": [],
        },
    ],
    "edges": [
        {"from": "decompose", "to": "detail"},
        {"from": "detail", "to": "verify"},
        {"from": "verify", "to": "publish"},
    ],
}


def _plan_judge(ctx):
    """Route through plan workflow steps."""
    step = ctx.get("step_id", "")
    if step == "decompose":
        return "to:detail"
    if step == "detail":
        return "to:verify"
    if step == "verify":
        output = (ctx.get("output") or "").upper().strip()
        if "ISSUES" in output:
            return "to:detail"  # re-detail
        return "to:publish"
    return "done"


def run_plan_workflow(
    store, config: ProviderConfig, session_dir: str,
    goal: str, daily_minutes: int, week_num: int = 1,
) -> dict:
    """Run the plan generation workflow.

    Returns dict with:
    - task_id: str
    - weeks: list of week outlines
    - tasks: list of detailed tasks for the current week
    - verified: bool
    - cost: dict
    """
    provider = create_provider(config)
    judge = senza.create_judge(_plan_judge)

    engine = (
        senza.WorkflowEngine(PLAN_WORKFLOW, provider, config.model, judge,
                             session_base_dir=session_dir)
        .with_task_store(session_dir)
        .with_max_retries(2)
    )

    # Set context variables for prompt interpolation
    engine.set_context_variable("goal", goal)
    engine.set_context_variable("daily_minutes", str(daily_minutes))
    engine.set_context_variable("week_num", str(week_num))

    engine.run()

    history = engine.step_history()
    result = {
        "task_id": engine.task_id(),
        "weeks": [],
        "tasks": [],
        "verified": False,
        "cost": engine.total_cost(),
    }

    for record in history:
        step_id = record["step_id"]
        output = record.get("result", {}).get("output", "")
        if step_id == "decompose":
            try:
                parsed = _extract_json(output)
                result["weeks"] = parsed.get("weeks", [])
            except Exception:
                result["weeks"] = []
        elif step_id == "detail":
            try:
                parsed = _extract_json(output)
                result["tasks"] = parsed.get("tasks", [])
            except Exception:
                result["tasks"] = []
        elif step_id == "verify":
            result["verified"] = "APPROVED" in output.upper()

    return result


# ── Quiz Generation Workflow ──

QUIZ_WORKFLOW = {
    "entry_step": "generate",
    "steps": [
        {
            "id": "generate",
            "name": "Generate Questions",
            "prompt": (
                "你是测验设计师。根据以下知识点生成 {num_questions} 道练习题。\n"
                "知识点：{knowledge_points}\n\n"
                "{user_context}"
                "题型：单选、多选、判断、简答混合。用 JSON 格式输出：\n"
                '```json\n{"questions": [{"type": "single_choice", "question": "...", '
                '"options": ["A","B","C","D"], "answer": "A", "explanation": "...", '
                '"knowledge_points": ["..."]}]}\n```\n'
                "只输出 JSON。"
            ),
            "allowed_tools": [],
        },
        {
            "id": "verify",
            "name": "Verify Quiz",
            "prompt": (
                "审查以下测验题的质量（题目清晰度、答案正确性、难度适中）：\n{quiz_json}\n\n"
                "如果合理，回复 APPROVED。如果有问题，回复 ISSUES: 然后列出问题。"
            ),
            "allowed_tools": [],
        },
        {
            "id": "publish",
            "name": "Publish Quiz",
            "prompt": "将验证通过的测验整理为最终 JSON 格式输出。只输出 JSON。",
            "allowed_tools": [],
        },
    ],
    "edges": [
        {"from": "generate", "to": "verify"},
        {"from": "verify", "to": "publish"},
    ],
}


def _quiz_judge(ctx):
    step = ctx.get("step_id", "")
    if step == "generate":
        return "to:verify"
    if step == "verify":
        output = (ctx.get("output") or "").upper().strip()
        if "ISSUES" in output:
            return "to:generate"
        return "to:publish"
    return "done"


def run_quiz_workflow(
    store, config: ProviderConfig, session_dir: str,
    knowledge_points: str, num_questions: int = 3,
    plan_id: str = "", task_id: str = "",
) -> dict:
    """Run quiz generation workflow.

    Returns dict with:
    - task_id: str
    - questions: list of question dicts
    - verified: bool
    - cost: dict
    """
    provider = create_provider(config)
    judge = senza.create_judge(_quiz_judge)

    engine = (
        senza.WorkflowEngine(QUIZ_WORKFLOW, provider, config.model, judge,
                             session_base_dir=session_dir)
        .with_task_store(session_dir)
        .with_max_retries(2)
    )
    engine.set_context_variable("knowledge_points", knowledge_points)
    engine.set_context_variable("num_questions", str(num_questions))
    engine.set_context_variable("user_context", _build_user_context(store, plan_id, knowledge_points))
    engine.run()

    history = engine.step_history()
    result = {
        "task_id": engine.task_id(),
        "questions": [],
        "verified": False,
        "cost": engine.total_cost(),
    }

    for record in history:
        step_id = record["step_id"]
        output = record.get("result", {}).get("output", "")
        if step_id == "generate":
            try:
                parsed = _extract_json(output)
                result["questions"] = parsed.get("questions", [])
            except Exception:
                result["questions"] = []
        elif step_id == "verify":
            result["verified"] = "APPROVED" in output.upper()
        elif step_id == "publish":
            try:
                parsed = _extract_json(output)
                if parsed.get("questions"):
                    result["questions"] = parsed["questions"]
            except Exception:
                pass

    return result


# ── Helpers ──

def _extract_json(text: str) -> dict:
    """Extract JSON from text that may contain markdown code fences."""
    # Try to find JSON in code blocks first
    if "```json" in text:
        start = text.index("```json") + 7
        end = text.index("```", start)
        return json.loads(text[start:end].strip())
    if "```" in text:
        start = text.index("```") + 3
        end = text.index("```", start)
        return json.loads(text[start:end].strip())
    # Try direct parse
    return json.loads(text.strip())


def _build_user_context(store, plan_id: str, knowledge_points: str) -> str:
    """Build user profile context string for quiz generation.
    Reads learner memory, knowledge mastery, and recent errors."""
    parts = []

    # 1. Global learner memory
    try:
        mem = store.list_memory("global")
        if mem:
            mem_lines = [f"  - {m['key']}: {m['value']}" for m in mem]
            parts.append("学习者画像：\n" + "\n".join(mem_lines) + "\n\n")
    except Exception:
        pass

    # 2. Knowledge mastery for this plan
    if plan_id:
        try:
            kps = store.list_kps(plan_id)
            if kps:
                kp_lines = [
                    f"  - {kp['name']}: 掌握度 {kp['mastery_level']}%"
                    for kp in kps
                ]
                parts.append("已学知识点掌握度：\n" + "\n".join(kp_lines) + "\n\n")
        except Exception:
            pass

    # 3. Recent wrong answers (last 5)
    try:
        wrong = store.get_wrong_answers()[:5]
        if wrong:
            err_lines = [
                f"  - 题: {w.get('question', '')[:60]}"
                f" | 错答: {w.get('user_answer', '')[:30]}"
                f" | 正确: {w.get('correct_answer', '')[:30]}"
                for w in wrong
            ]
            parts.append("近期错题（请针对性出题）：\n" + "\n".join(err_lines) + "\n\n")
    except Exception:
        pass

    if not parts:
        return ""
    return "## 用户画像（请根据以下信息调整题目难度和侧重点）\n" + "".join(parts)