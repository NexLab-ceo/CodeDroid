import { useState } from "react";
import CodeMirror from "@uiw/react-codemirror";
import { javascript } from "@codemirror/lang-javascript";
import { oneDark } from "@codemirror/theme-one-dark";
import { askAssistant, type ChatMessage, type ProviderMode } from "./ai";
import { Bot, Files, Play, Send, Settings2, Terminal } from "lucide-react";

const starter = 'function greet(name: string) {\n  return "Hello, " + name + "!";\n}\n\nconsole.log(greet("CodePilot"));\n';

export default function App() {
  const [code, setCode] = useState(starter);
  const [filename, setFilename] = useState("main.ts");
  const [mode, setMode] = useState<ProviderMode>("auto");
  const [model, setModel] = useState("qwen2.5-coder:7b");
  const [localUrl, setLocalUrl] = useState("http://127.0.0.1:11434/v1/chat/completions");
  const [prompt, setPrompt] = useState("");
  const [reply, setReply] = useState("Привет! Я CodePilot AI. Спроси меня о коде или попроси помочь с задачей.");
  const [busy, setBusy] = useState(false);
  const [showSettings, setShowSettings] = useState(false);

  async function sendPrompt() {
    const text = prompt.trim();
    if (!text || busy) return;
    setBusy(true);
    setPrompt("");
    try {
      const messages: ChatMessage[] = [
        { role: "system", content: "Ты CodePilot AI, помощник программиста. Отвечай по-русски, кратко и с примерами. Если полезно, анализируй текущий код.\n\nТекущий файл: " + filename + "\n\nКод:\n" + code },
        { role: "user", content: text }
      ];
      const result = await askAssistant({ mode, messages, localModel: model, localUrl, allowCloudFallback: true });
      setReply(result.content);
    } catch (e) {
      setReply("Ошибка AI: " + (e instanceof Error ? e.message : String(e)));
    } finally {
      setBusy(false);
    }
  }

  return <div className="app-shell">
    <header className="topbar">
      <div className="brand"><div className="brand-mark"><Bot size={19}/></div><div><strong>CodePilot AI</strong><small>Android IDE · prototype</small></div></div>
      <div className="top-actions"><span className="status-dot"/> <span className="status-label">Ready</span><button className="icon-button" onClick={() => setShowSettings(!showSettings)} aria-label="Settings"><Settings2 size={18}/></button></div>
    </header>
    {showSettings && <section className="settings-panel">
      <label>AI mode<select value={mode} onChange={e => setMode(e.target.value as ProviderMode)}><option value="auto">Auto (local → cloud)</option><option value="local">Local</option><option value="cloud">Cloud</option></select></label>
      <label>Local model<input value={model} onChange={e => setModel(e.target.value)} /></label>
      <label>Local endpoint<input value={localUrl} onChange={e => setLocalUrl(e.target.value)} /></label>
    </section>}
    <main className="workspace">
      <aside className="activitybar"><button className="activity active" title="Files"><Files size={20}/></button><button className="activity" title="Terminal"><Terminal size={20}/></button><button className="activity" title="AI"><Bot size={20}/></button></aside>
      <aside className="file-sidebar"><div className="pane-title">EXPLORER <span>···</span></div><div className="project-label">⌄ CODEDROID</div><button className="file-row selected" onClick={() => setFilename("main.ts")}>◈ <span>main.ts</span></button><div className="sidebar-note">Файловое дерево — прототип</div></aside>
      <section className="editor-pane"><div className="tabbar"><div className="tab"><span className="ts-icon">TS</span>{filename}<span className="dirty-dot"/></div><div className="editor-actions"><button onClick={() => setCode(starter)} title="Reset sample">Reset</button><button className="run-button" onClick={() => setReply("Демо-консоль: выполнение кода на устройстве пока не реализовано.")}><Play size={14}/> Run</button></div></div><div className="breadcrumbs">src <span>›</span> {filename}</div><div className="code-area"><CodeMirror value={code} height="100%" theme={oneDark} extensions={[javascript({typescript:true})]} onChange={setCode} basicSetup={{lineNumbers:true, foldGutter:true, highlightActiveLine:true}}/></div><div className="bottom-panel"><div className="bottom-title"><Terminal size={14}/> OUTPUT</div><p>Готово к редактированию. Запуск команд ОС пока не поддерживается.</p></div></section>
      <aside className="ai-panel"><div className="ai-heading"><div><Bot size={17}/> <strong>AI ASSISTANT</strong></div><span className="mode-chip">{mode.toUpperCase()}</span></div><div className="chat-content"><div className="assistant-avatar"><Bot size={20}/></div><div className="reply-card"><div className="reply-label">CODEPILOT</div><p>{reply}</p></div></div><div className="chat-context"><span>Context</span><span className="context-file">◈ {filename}</span></div><div className="prompt-box"><textarea value={prompt} onChange={e => setPrompt(e.target.value)} onKeyDown={e => {if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();void sendPrompt();}}} placeholder="Спроси что-нибудь о коде…" rows={3}/><div className="prompt-footer"><span>Enter to send · Shift+Enter for newline</span><button className="send-button" onClick={() => void sendPrompt()} disabled={busy || !prompt.trim()} aria-label="Send"><Send size={16}/></button></div></div></aside>
    </main>
    <footer className="statusbar"><span>⎇ main</span><span>TypeScript</span><span>UTF-8</span><span className="status-spacer"/><span>CodePilot AI · Prototype 0.1</span></footer>
  </div>;
}
