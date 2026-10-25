export type ProviderMode = "auto" | "local" | "cloud";
export type ChatMessage = { role: "system" | "user" | "assistant"; content: string };

const LOCAL_URL_KEY = "codepilot.localUrl";

export function getLocalUrl() {
  return localStorage.getItem(LOCAL_URL_KEY) || "http://127.0.0.1:11434/v1/chat/completions";
}
export function setLocalUrl(url: string) {
  localStorage.setItem(LOCAL_URL_KEY, url);
}

async function askLocal(messages: ChatMessage[], model: string, url: string) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model, messages, stream: false })
  });
  if (!response.ok) throw new Error(`Локальная модель ответила HTTP ${response.status}`);
  const data = await response.json();
  const content = data?.choices?.[0]?.message?.content;
  if (typeof content !== "string" || !content.trim()) throw new Error("Локальная модель вернула пустой ответ");
  return content;
}

async function askCloud(messages: ChatMessage[]) {
  const response = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ messages })
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data?.error || `Cloud backend returned HTTP ${response.status}`);
  if (typeof data.content !== "string" || !data.content.trim()) throw new Error("Облачная модель вернула пустой ответ");
  return data.content;
}

export async function askAssistant(args: {
  mode: ProviderMode;
  messages: ChatMessage[];
  localModel: string;
  localUrl: string;
  allowCloudFallback: boolean;
}) {
  const { mode, messages, localModel, localUrl, allowCloudFallback } = args;

  if (mode === "cloud") return { content: await askCloud(messages), provider: "Cloud" };
  if (mode === "local") return { content: await askLocal(messages, localModel, localUrl), provider: "Local" };

  try {
    return { content: await askLocal(messages, localModel, localUrl), provider: "Local" };
  } catch (localError) {
    if (!allowCloudFallback) {
      throw new Error(`Локальная модель недоступна. Облачная отправка отключена. Детали: ${localError instanceof Error ? localError.message : String(localError)}`);
    }
    return { content: await askCloud(messages), provider: "Cloud (fallback)" };
  }
}
