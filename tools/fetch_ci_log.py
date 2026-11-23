"""拉取 GitHub Actions 作业日志并筛出编译/测试错误。

用法:
    GITHUB_TOKEN=xxx python tools/fetch_ci_log.py [--sha <sha>] [--workflow "Android APP"] [--wait 分钟]

日志接口会 302 到 Azure Blob，必须用 curl -L 跟着跳转（Python urllib 会把
Authorization 头带到跳转后的地址，被拒绝为 401）。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

TOKEN = os.environ.get("GITHUB_TOKEN", "")
if not TOKEN:
    sys.exit("请先设置环境变量 GITHUB_TOKEN（需要 actions:read 权限）")
REPO = "25283531/WLED"
API = "https://api.github.com"


def api(path: str) -> dict:
    req = urllib.request.Request(
        API + path,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Accept": "application/vnd.github+json",
            "User-Agent": "curl",
        },
    )
    return json.load(urllib.request.urlopen(req, timeout=60))


def latest_head_sha() -> str:
    out = subprocess.check_output(
        ["git", "rev-parse", "--short=7", "HEAD"], text=True, cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    )
    return out.strip()


def wait_for_run(workflow: str, sha: str, wait_minutes: int) -> dict | None:
    deadline = time.time() + wait_minutes * 60
    reported = None
    while time.time() < deadline:
        data = api(f"/repos/{REPO}/actions/runs?per_page=15")
        runs = [r for r in data["workflow_runs"] if r["head_sha"].startswith(sha) and r["name"] == workflow]
        if runs:
            run = runs[0]
            state = (run["status"], run["conclusion"])
            if state != reported:
                reported = state
                print(f'[{time.strftime("%H:%M:%S")}] {workflow}: {run["status"]} {run["conclusion"] or ""}', flush=True)
            if run["status"] == "completed":
                return run
        else:
            print(f"等待 {workflow} 针对 {sha} 的运行…", flush=True)
        time.sleep(20)
    return None


def dump_log(run: dict, out_dir: str = "_ci") -> list[str]:
    os.makedirs(out_dir, exist_ok=True)
    jobs = api(f"/repos/{REPO}/actions/runs/{run['id']}/jobs")["jobs"]
    saved = []
    for job in jobs:
        if job["conclusion"] == "success":
            continue
        print(f'job {job["name"]} -> {job["conclusion"]}', flush=True)
        for step in job.get("steps", []):
            if step["conclusion"] not in ("success", "skipped", None):
                print(f'  FAILED STEP: {step["name"]}', flush=True)
        target = os.path.join(out_dir, f'{job["id"]}.log')
        url = f"{API}/repos/{REPO}/actions/jobs/{job['id']}/logs"
        subprocess.run(
            ["curl", "-sL", "-m", "180", "-H", f"Authorization: Bearer {TOKEN}",
             "-H", "Accept: application/vnd.github+json", "-H", "User-Agent: curl", url, "-o", target],
            check=False,
        )
        saved.append(target)
    return saved


def report(paths: list[str]) -> None:
    patterns = [
        re.compile(r"^e: "),
        re.compile(r"error:", re.IGNORECASE),
        re.compile(r"FAILED"),
        re.compile(r"Execution failed"),
    ]
    for path in paths:
        print(f"\n===== {path} =====")
        try:
            text = open(path, encoding="utf-8-sig", errors="replace").read()
        except OSError as exc:
            print("读取失败", exc)
            continue
        lines = [re.sub(r"^\S+Z ", "", line) for line in text.splitlines()]
        hits = [line for line in lines if any(p.search(line) for p in patterns)]
        # 去重并保留顺序
        seen = set()
        shown = 0
        for line in hits:
            key = line.strip()
            if key in seen:
                continue
            seen.add(key)
            print(key)
            shown += 1
            if shown >= 80:
                print("…（截断）")
                break
        if not hits:
            print("（未匹配到错误行，日志尾部如下）")
            print("\n".join(lines[-40:]))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sha")
    parser.add_argument("--workflow", default="Android APP")
    parser.add_argument("--wait", type=int, default=25)
    args = parser.parse_args()

    sha = args.sha or latest_head_sha()
    run = wait_for_run(args.workflow, sha, args.wait)
    if run is None:
        print("超时，仍未完成")
        return 2
    print(f'run: {run["html_url"]} -> {run["conclusion"]}')
    if run["conclusion"] == "success":
        return 0
    report(dump_log(run))
    return 1


if __name__ == "__main__":
    sys.exit(main())
