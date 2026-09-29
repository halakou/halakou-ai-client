#!/usr/bin/env python3
"""
halakou Autonomous Dynamic Endpoint Updater
-------------------------------------------
Pings candidate free AI endpoints (Text & Vision models),
benchmarks their response time, health status, and outputs
a lean static `config.json` for GitHub Pages distribution.

Zero active server costs. 100% serverless static hosting.
"""

import json
import os
import sys
import time
import urllib.request
import urllib.error

CONFIG_VERSION = "2.0.0"
OUTPUT_FILE = "config.json"

# Candidate model registry
CANDIDATES = [
    {
        "id": "atria-dawn-primary",
        "provider": "atria",
        "name": "Atria Dawn (MoE 744B)",
        "model": "Atria-Dawn-Preview",
        "base_url": "https://api.atria-asi.ai/v1",
        "api_key_env": "ATRIA_API_KEY",
        "is_vision": False,
        "is_primary_text": True,
        "priority": 1,
    },
    {
        "id": "gemini-2-flash-vision-free",
        "provider": "openrouter",
        "name": "Gemini 2.0 Flash Vision (Free)",
        "model": "google/gemini-2.0-flash-exp:free",
        "base_url": "https://openrouter.ai/api/v1",
        "api_key_env": "OPENROUTER_API_KEY",
        "is_vision": True,
        "is_primary_text": False,
        "priority": 2,
    },
    {
        "id": "llama-3-2-vision-free",
        "provider": "openrouter",
        "name": "Llama 3.2 11B Vision (Free)",
        "model": "meta-llama/llama-3.2-11b-vision-instruct:free",
        "base_url": "https://openrouter.ai/api/v1",
        "api_key_env": "OPENROUTER_API_KEY",
        "is_vision": True,
        "is_primary_text": False,
        "priority": 3,
    },
    {
        "id": "llama-3-3-70b-free",
        "provider": "openrouter",
        "name": "Llama 3.3 70B Instruct (Free)",
        "model": "meta-llama/llama-3.3-70b-instruct:free",
        "base_url": "https://openrouter.ai/api/v1",
        "api_key_env": "OPENROUTER_API_KEY",
        "is_vision": False,
        "is_primary_text": False,
        "priority": 4,
    },
    {
        "id": "deepseek-r1-free",
        "provider": "openrouter",
        "name": "DeepSeek R1 Reasoning (Free)",
        "model": "deepseek/deepseek-r1:free",
        "base_url": "https://openrouter.ai/api/v1",
        "api_key_env": "OPENROUTER_API_KEY",
        "is_vision": False,
        "is_primary_text": False,
        "priority": 5,
    },
    {
        "id": "gemini-direct-public",
        "provider": "gemini",
        "name": "Google Gemini 2.5 Flash Direct",
        "model": "gemini-2.5-flash",
        "base_url": "https://generativelanguage.googleapis.com",
        "api_key_env": "GEMINI_API_KEY",
        "is_vision": True,
        "is_primary_text": False,
        "priority": 6,
    }
]

def ping_endpoint(candidate):
    """Measures latency and connectivity to candidate API."""
    url = candidate["base_url"].rstrip("/") + "/chat/completions"
    if candidate["provider"] == "gemini":
        url = candidate["base_url"].rstrip("/") + "/v1beta/models"

    headers = {
        "User-Agent": "halakou-health-crawler/2.0",
        "Content-Type": "application/json"
    }

    api_key = os.getenv(candidate["api_key_env"], "free")
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"

    start_time = time.time()
    latency_ms = 999
    is_healthy = True

    try:
        req = urllib.request.Request(url, headers=headers, method="GET")
        with urllib.request.urlopen(req, timeout=5) as response:
            latency_ms = int((time.time() - start_time) * 1000)
            is_healthy = response.status in (200, 201, 204, 401, 403, 405)
    except urllib.error.HTTPError as e:
        latency_ms = int((time.time() - start_time) * 1000)
        # 401 or 405 means the server is alive and functioning
        is_healthy = e.code in (401, 405, 400)
    except Exception as e:
        latency_ms = 9999
        is_healthy = False

    return {
        "id": candidate["id"],
        "provider": candidate["provider"],
        "name": candidate["name"],
        "model": candidate["model"],
        "base_url": candidate["base_url"],
        "is_vision": candidate["is_vision"],
        "is_primary_text": candidate.get("is_primary_text", False),
        "priority": candidate["priority"],
        "latency_ms": latency_ms,
        "is_healthy": is_healthy
    }

def main():
    print("=== halakou Endpoint Health Crawler ===")
    results = []
    for candidate in CANDIDATES:
        print(f"Testing {candidate['name']} ({candidate['base_url']})...")
        res = ping_endpoint(candidate)
        print(f"  -> Health: {res['is_healthy']} | Latency: {res['latency_ms']}ms")
        results.append(res)

    text_models = [r for r in results if not r["is_vision"]]
    vision_models = [r for r in results if r["is_vision"]]

    # Sort text models: primary first, then healthy by lowest latency
    text_models.sort(key=lambda x: (not x["is_primary_text"], not x["is_healthy"], x["latency_ms"]))
    vision_models.sort(key=lambda x: (not x["is_healthy"], x["latency_ms"]))

    config = {
        "version": CONFIG_VERSION,
        "timestamp": int(time.time()),
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "circuit_breaker": {
            "failure_threshold": 1,
            "cooldown_duration_ms": 60000,
            "tripping_http_codes": [403, 429, 502, 503, 504]
        },
        "monetization": {
            "sku_id": "halakou.monthly.1usd",
            "price_usd": "1.00",
            "free_daily_limit": 25
        },
        "text_models": text_models,
        "vision_models": vision_models
    }

    # Write output to root config.json
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2)

    # Also sync into Android assets if directory exists
    assets_dir = os.path.join("app", "src", "main", "assets")
    if os.path.exists(assets_dir):
        with open(os.path.join(assets_dir, "default_config.json"), "w", encoding="utf-8") as f:
            json.dump(config, f, indent=2)

    print(f"\nGenerated {OUTPUT_FILE} successfully with {len(results)} endpoints.")

if __name__ == "__main__":
    main()
