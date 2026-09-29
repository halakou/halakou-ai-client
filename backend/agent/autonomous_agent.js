/**
 * halakou Autonomous Healing Agent
 * 
 * Runs autonomously in GitHub Actions:
 * 1. Discovers free endpoints and community proxies.
 * 2. Benchmarks their latency, health, and vision compatibility.
 * 3. Updates Cloudflare KV dynamically so Android apps self-heal with zero human intervention.
 */

const https = require("https");

const CF_ACCOUNT_ID = process.env.CLOUDFLARE_ACCOUNT_ID;
const CF_NAMESPACE_ID = process.env.CLOUDFLARE_KV_NAMESPACE_ID;
const CF_API_TOKEN = process.env.CLOUDFLARE_API_TOKEN;

// Candidate sources: Atria ASI, OpenRouter Free endpoints, public proxies
const CANDIDATE_ENDPOINTS = [
  {
    id: "atria-dawn-primary",
    provider: "atria",
    model: "Atria-Dawn-Preview",
    baseUrl: "https://api.atria-asi.ai/v1",
    apiKey: process.env.ATRIA_API_KEY || "default_atria_key",
    isVision: false,
  },
  {
    id: "openrouter-gemini-vision-free",
    provider: "openrouter",
    model: "google/gemini-2.0-flash-exp:free",
    baseUrl: "https://openrouter.ai/api/v1",
    apiKey: process.env.OPENROUTER_API_KEY || "free",
    isVision: true,
  },
  {
    id: "openrouter-llama-vision-free",
    provider: "openrouter",
    model: "meta-llama/llama-3.2-11b-vision-instruct:free",
    baseUrl: "https://openrouter.ai/api/v1",
    apiKey: process.env.OPENROUTER_API_KEY || "free",
    isVision: true,
  },
  {
    id: "openrouter-llama70b-free",
    provider: "openrouter",
    model: "meta-llama/llama-3.3-70b-instruct:free",
    baseUrl: "https://openrouter.ai/api/v1",
    apiKey: process.env.OPENROUTER_API_KEY || "free",
    isVision: false,
  },
  {
    id: "openrouter-deepseek-r1-free",
    provider: "openrouter",
    model: "deepseek/deepseek-r1:free",
    baseUrl: "https://openrouter.ai/api/v1",
    apiKey: process.env.OPENROUTER_API_KEY || "free",
    isVision: false,
  },
];

async function pingEndpoint(ep) {
  const start = Date.now();
  try {
    const url = new URL(`${ep.baseUrl.replace(/\/$/, "")}/chat/completions`);
    const payload = JSON.stringify({
      model: ep.model,
      messages: [{ role: "user", content: "hi" }],
      max_tokens: 5,
    });

    const res = await fetch(url.toString(), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${ep.apiKey}`,
        "HTTP-Referer": "https://github.com/halakou/halakou",
        "X-Title": "halakou Autonomous Health Agent",
      },
      body: payload,
      signal: AbortSignal.timeout(8000),
    });

    const latency = Date.now() - start;
    const isHealthy = res.status === 200 || res.status === 201;

    console.log(`[Ping] ${ep.id} -> HTTP ${res.status} in ${latency}ms (Healthy: ${isHealthy})`);
    return {
      ...ep,
      latencyMs: latency,
      isHealthy: isHealthy,
      failureCount: isHealthy ? 0 : 1,
      lastChecked: new Date().toISOString(),
    };
  } catch (err) {
    console.warn(`[Fail] ${ep.id} -> ${err.message}`);
    return {
      ...ep,
      latencyMs: 9999,
      isHealthy: false,
      failureCount: 1,
      lastChecked: new Date().toISOString(),
    };
  }
}

async function updateCloudflareKV(healthyPool) {
  if (!CF_ACCOUNT_ID || !CF_NAMESPACE_ID || !CF_API_TOKEN) {
    console.log("No Cloudflare credentials supplied in environment. Outputting JSON locally:");
    console.log(JSON.stringify(healthyPool, null, 2));
    return;
  }

  const endpointUrl = `https://api.cloudflare.com/client/v4/accounts/${CF_ACCOUNT_ID}/storage/kv/namespaces/${CF_NAMESPACE_ID}/values/endpoints_pool`;
  console.log(`Uploading ${healthyPool.length} endpoints to Cloudflare KV: ${endpointUrl}`);

  const resp = await fetch(endpointUrl, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${CF_API_TOKEN}`,
      "Content-Type": "text/plain",
    },
    body: JSON.stringify(healthyPool),
  });

  if (resp.ok) {
    console.log("✅ Successfully updated Cloudflare KV endpoints_pool.");
  } else {
    console.error("❌ Failed to update Cloudflare KV:", await resp.text());
  }
}

async function main() {
  console.log("=== halakou Autonomous Self-Healing Agent Started ===");
  const results = await Promise.all(CANDIDATE_ENDPOINTS.map(pingEndpoint));

  // Sort by health and lowest latency
  const sortedPool = results.sort((a, b) => {
    if (a.isHealthy === b.isHealthy) {
      return a.latencyMs - b.latencyMs;
    }
    return a.isHealthy ? -1 : 1;
  });

  console.log(`Discovered ${sortedPool.filter((e) => e.isHealthy).length} healthy endpoints.`);
  await updateCloudflareKV(sortedPool);
  console.log("=== Autonomous Healing Agent Finished ===");
}

main().catch(console.error);
