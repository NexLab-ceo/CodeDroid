import "dotenv/config";
import express from "express";
import cors from "cors";

const app = express();
app.use(cors({ origin: ["http://127.0.0.1:5173", "http://localhost:5173"] }));
app.use(express.json({ limit: "1mb" }));

app.get("/api/health", (_req, res) => {
  res.json({ ok: true, cloudConfigured: Boolean(process.env.OPENAI_API_KEY && process.env.OPENAI_MODEL) });
});

app.post("/api/chat", async (req, res) => {
  const { messages } = req.body ?? {};
  if (!Array.isArray(messages) || messages.length === 0) {
    return res.status(400).json({ error: "messages must be a non-empty array" });
  }
  if (!process.env.OPENAI_API_KEY || !process.env.OPENAI_MODEL) {
    return res.status(503).json({
      error: "Облачный ИИ не настроен. Создай .env из .env.example и укажи OPENAI_API_KEY и OPENAI_MODEL."
    });
  }

  const baseUrl = (process.env.OPENAI_BASE_URL || "https://api.openai.com/v1").replace(/\/+$/, "");
  try {
    const upstream = await fetch(`${baseUrl}/chat/completions`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${process.env.OPENAI_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: process.env.OPENAI_MODEL,
        messages,
        temperature: 0.2,
        stream: false
      })
    });

    const payload = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      return res.status(upstream.status).json({
        error: payload?.error?.message || `Cloud provider returned HTTP ${upstream.status}`
      });
    }
    res.json({ content: payload?.choices?.[0]?.message?.content ?? "" });
  } catch (error) {
    res.status(502).json({ error: `Не удалось связаться с облачным провайдером: ${error.message}` });
  }
});

const port = Number(process.env.PORT || 8787);
app.listen(port, "127.0.0.1", () => {
  console.log(`CodePilot backend listening at http://127.0.0.1:${port}`);
});
