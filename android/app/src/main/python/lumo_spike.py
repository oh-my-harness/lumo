"""Spike: verify Senza can be imported and basic APIs work on Android."""

import os
import senza


def _get_writable_dir():
    """Get a writable directory for Senza runtime on Android."""
    # Try Android app private storage
    candidates = [
        os.environ.get("LUMO_DATA_DIR"),
        os.path.join(os.environ.get("HOME", "/data/data/com.lumo.spike"), "files/lumo"),
        "/data/data/com.lumo.spike/files/lumo",
        os.path.join(os.path.expanduser("~"), "lumo"),
        os.getcwd(),
    ]
    for d in candidates:
        if d:
            try:
                os.makedirs(d, exist_ok=True)
                test_file = os.path.join(d, ".write_test")
                with open(test_file, "w") as f:
                    f.write("ok")
                os.remove(test_file)
                return d
            except Exception:
                continue
    return "/tmp"


def check_import():
    """Verify Senza can be imported and version() works."""
    version = senza.version()
    return f"Senza version: {version}"


def check_harness_builder(api_key: str, base_url: str = "", model: str = "gpt-4o"):
    """Verify HarnessBuilder can be constructed and built with a provider."""
    kwargs = {"api_key": api_key}
    if base_url:
        kwargs["base_url"] = base_url
    provider = senza.create_openai_provider(**kwargs)
    harness = (
        senza.HarnessBuilder(model)
        .provider("*", provider)
        .system_prompt("You are a helpful tutor.")
        .max_tokens(128)
        .build()
    )
    return f"HarnessBuilder OK, phase: {harness.phase()}"


def check_provider(api_key: str, base_url: str = ""):
    """Verify provider creation works."""
    kwargs = {"api_key": api_key}
    if base_url:
        kwargs["base_url"] = base_url
    provider = senza.create_openai_provider(**kwargs)
    return "Provider created OK"


def test_prompt(api_key: str, base_url: str, model: str) -> str:
    """Run a simple non-streaming prompt and return the response."""
    kwargs = {"api_key": api_key}
    if base_url:
        kwargs["base_url"] = base_url
    provider = senza.create_openai_provider(**kwargs)
    harness = (
        senza.HarnessBuilder(model)
        .provider("*", provider)
        .system_prompt("You are a helpful tutor. Answer in one sentence.")
        .max_tokens(128)
        .build()
    )

    events = harness.prompt_and_collect("用一句话解释闭包。")

    text = ""
    for event in events:
        if event["type"] == "text_delta":
            text += event.get("text", "")

    cost = harness.usage()
    return f"{text}\n(tokens: {cost['total_input_tokens']} in / {cost['total_output_tokens']} out)"


def stream_chat(api_key: str, base_url: str, model: str, question: str, callback):
    """Stream a chat response, calling callback.onToken(text) for each text delta."""
    kwargs = {"api_key": api_key}
    if base_url:
        kwargs["base_url"] = base_url
    provider = senza.create_openai_provider(**kwargs)
    harness = (
        senza.HarnessBuilder(model)
        .provider("*", provider)
        .system_prompt("You are a helpful tutor.")
        .max_tokens(256)
        .build()
    )

    import threading

    done = threading.Event()

    def stream_events():
        for event in harness.events(timeout_ms=30000):
            t = event["type"]
            if t == "text_delta":
                text = event.get("text", "")
                callback.onToken(text)
            elif t in ("settled", "aborted", "error"):
                done.set()
                break

    stream_thread = threading.Thread(target=stream_events)
    stream_thread.start()

    harness.prompt(question)
    stream_thread.join(timeout=60)

    return f"phase={harness.phase()}"


def test_workflow(api_key: str, base_url: str, model: str) -> str:
    """Run a minimal two-step workflow on Android."""
    kwargs = {"api_key": api_key}
    if base_url:
        kwargs["base_url"] = base_url
    provider = senza.create_openai_provider(**kwargs)

    workflow = {
        "entry_step": "writer",
        "steps": [
            {
                "id": "writer",
                "name": "Writer",
                "prompt": "Write a one-sentence story about a cat.",
                "allowed_tools": [],
            },
            {
                "id": "reviewer",
                "name": "Reviewer",
                "prompt": "Rate this story 1-5. Just give the number.",
                "allowed_tools": [],
            },
        ],
        "edges": [{"from": "writer", "to": "reviewer"}],
    }

    def judge(ctx):
        step = ctx.get("step_id", "")
        if step == "writer":
            return "to:reviewer"
        return "done"

    work_dir = _get_writable_dir()
    session_dir = os.path.join(work_dir, "sessions")
    task_store_dir = os.path.join(work_dir, "tasks")
    os.makedirs(session_dir, exist_ok=True)
    os.makedirs(task_store_dir, exist_ok=True)
    judge_obj = senza.create_judge(judge)
    engine = (
        senza.WorkflowEngine(workflow, provider, model, judge_obj, session_base_dir=session_dir)
        .with_task_store(task_store_dir)
    )

    task_id = engine.task_id()
    engine.run()

    history = engine.step_history()
    results = []
    for record in history:
        step_id = record["step_id"]
        result = record.get("result", {})
        output = result.get("output", "(no result)")[:80]
        results.append(f"{step_id}: {output}")

    cost = engine.total_cost()
    return f"Task: {task_id}\nWorkDir: {work_dir}\nSteps: {len(history)}\n" + "\n".join(results) + f"\nTokens: {cost['total_input_tokens']} in / {cost['total_output_tokens']} out"
