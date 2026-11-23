"""查询 GitHub Actions 运行状态（本机 api.github.com 的 TLS 会被 SNI 级干扰，故需重试）。

令牌从环境变量 GITHUB_TOKEN 读取，绝不写进仓库（GitHub push protection 会拦截）。

用法: python ci_status.py [run_id]
不带参数列出最近若干次运行；带 run_id 则轮询直到结束。
"""
import json
import os
import ssl
import sys
import time
import urllib.request

TOKEN = os.environ.get("GITHUB_TOKEN", "")
if not TOKEN:
    raise SystemExit("请先设置环境变量 GITHUB_TOKEN")
REPO = "25283531/WLED"
API = f"https://api.github.com/repos/{REPO}"
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE


def api_get(path, tries=8):
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(
                API + path,
                headers={
                    "Authorization": f"token {TOKEN}",
                    "Accept": "application/vnd.github+json",
                    "User-Agent": "ci-status",
                },
            )
            with urllib.request.urlopen(req, timeout=25, context=CTX) as resp:
                return json.loads(resp.read().decode())
        except Exception as exc:  # TLS 握手被掐 / 超时都重试
            last = exc
            time.sleep(2 + i)
    raise SystemExit(f"API failed after {tries} tries: {last}")


def list_runs():
    data = api_get("/actions/runs?per_page=8")
    for r in data.get("workflow_runs", []):
        print(
            f"{r['name']:16s} {r['head_sha'][:8]} {r['status']:11s} "
            f"{str(r.get('conclusion')):10s} run#{r['id']} {r['created_at']}"
        )


def wait_run(run_id, timeout_min=50):
    deadline = time.time() + timeout_min * 60
    seen = None
    while time.time() < deadline:
        d = api_get(f"/actions/runs/{run_id}")
        key = (d["status"], d.get("conclusion"))
        if key != seen:
            print(f"  -> {d['status']} / {d.get('conclusion')}", flush=True)
            seen = key
        if d["status"] == "completed":
            return d
        time.sleep(12)
    raise SystemExit("timeout waiting for run")


if __name__ == "__main__":
    if len(sys.argv) > 1:
        wait_run(sys.argv[1])
    else:
        list_runs()
