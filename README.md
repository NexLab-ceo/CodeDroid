# CodeDroid / CodePilot AI

Адаптивный прототип IDE для Android-планшетов: редактор CodeMirror, панель проекта и AI-чат с режимами Local / Cloud / Auto.

## Запуск

Требуется Node.js 18+.

```bash
npm install
cp .env.example .env
npm run dev
```

Во втором терминале для облачного AI запустите backend:

```bash
npm run server
```

Затем задайте в `.env` значения `OPENAI_API_KEY` и `OPENAI_MODEL`. Никогда не добавляйте настоящий `.env` или API-ключ в Git.

## Режимы AI

- **Local** — использует OpenAI-compatible endpoint, по умолчанию `http://127.0.0.1:11434/v1/chat/completions`.
- **Cloud** — отправляет запросы через локальный Express backend, ключ хранится только на сервере.
- **Auto** — сначала пытается обратиться к локальной модели, затем при разрешённом fallback использует Cloud.

## Ограничения прототипа

- Это веб-прототип, не нативное Android-приложение.
- Редактор и AI-чат работают, но файловое дерево пока демонстрационное; полноценный доступ к файловой системе Android не реализован.
- Кнопка Run демонстрационная: команды ОС и код не исполняются.

## Технологии

React, TypeScript, Vite, CodeMirror 6, Express.
