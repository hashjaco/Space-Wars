// GET /gen?model=klein|dev|schnell&prompt=...&seed=1&w=1024&h=1024 → image/png
// The FLUX.2 models take multipart form data even for a bare prompt; schnell takes JSON.
const MODELS: Record<string, string> = {
  klein: "@cf/black-forest-labs/flux-2-klein-9b",
  dev: "@cf/black-forest-labs/flux-2-dev",
  schnell: "@cf/black-forest-labs/flux-1-schnell",
};
export default {
  async fetch(req: Request, env: { AI: Ai }): Promise<Response> {
    const u = new URL(req.url);
    const model = MODELS[u.searchParams.get("model") ?? "klein"];
    const prompt = u.searchParams.get("prompt");
    if (!model || !prompt) return new Response("need model & prompt", { status: 400 });
    const seed = u.searchParams.get("seed") ?? "1", width = u.searchParams.get("w") ?? "1024", height = u.searchParams.get("h") ?? "1024";
    try {
      let out: unknown;
      if (model.includes("schnell")) {
        out = await env.AI.run(model as never, { prompt, seed: Number(seed), steps: 8 } as never);
      } else {
        const form = new FormData();
        form.append("prompt", prompt); form.append("width", width); form.append("height", height); form.append("seed", seed);
        const r = new Response(form);
        out = await env.AI.run(model as never, { multipart: { body: r.body, contentType: r.headers.get("content-type") } } as never);
      }
      if (out instanceof ReadableStream) return new Response(out, { headers: { "content-type": "image/png" } });
      const o = out as { image?: string };
      if (o?.image) return new Response(Uint8Array.from(atob(o.image), (c) => c.charCodeAt(0)), { headers: { "content-type": "image/png" } });
      return new Response(JSON.stringify(out).slice(0, 500), { status: 500 });
    } catch (e) {
      return new Response(String(e), { status: 500 });
    }
  },
};
