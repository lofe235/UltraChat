<p align="center">
  <img src="assets/logo.png" width="104" alt="UltraChat logo"/>
</p>

<h1 align="center">UltraChat</h1>

<p align="center">
  <b>Приватный мессенджер нового поколения</b><br/>
  Android · Web · Node.js · WebSocket · Self-hosted
</p>

<p align="center">
  <a href="https://ultrachat2026.alwaysdata.net"><img src="https://img.shields.io/badge/Web_Client-online-38bdf8?style=for-the-badge&logo=googlechrome&logoColor=white" alt="Web client"></a>
  <a href="apk/UltraChat-1.8.3.apk"><img src="https://img.shields.io/badge/Download-APK_1.8.3-8b5cf6?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"></a>
  <img src="https://img.shields.io/badge/Node.js-18%2B-339933?style=for-the-badge&logo=nodedotjs&logoColor=white" alt="Node.js 18+">
  <img src="https://img.shields.io/badge/License-MIT-22c55e?style=for-the-badge" alt="MIT">
</p>

<p align="center">
  <img src="assets/banner.png" alt="UltraChat — приватный мессенджер" width="100%"/>
</p>

## ✨ Что такое UltraChat

**UltraChat** — это полноценный мессенджер, который живёт на **вашем** сервере: Android-приложение на Kotlin + Jetpack Compose, веб-клиент и лёгкий релей на Node.js. Никаких сторонних облаков, рекламы и трекеров — аккаунты, история и медиа лежат в вашей папке `data/`.

- 🌐 **Live-демо:** [ultrachat2026.alwaysdata.net](https://ultrachat2026.alwaysdata.net)
- 📱 **APK 1.8.3:** [скачать из репозитория](apk/UltraChat-1.8.3.apk) · [прямая ссылка с сервера](https://ultrachat2026.alwaysdata.net/download/UltraChat.apk)
- ⚡ **25 автотестов** сервера · **0** внешних зависимостей у клиента

## 🚀 Возможности

<img src="assets/features.png" alt="Возможности UltraChat" width="100%"/>

- **Мгновенная доставка** — WebSocket, подтверждения `ack` и восстановление истории.
- **Личные чаты** — непрочитанные, «печатает…», редактирование и удаление сообщений.
- **Фото и медиа** — загрузка картинок на сервер и раздача по прямой ссылке.
- **Офлайн-очередь** — сообщение дождётся адресата и придёт, когда он появится в сети.
- **SmartLink** — P2P-режим по Bluetooth: можно переписываться рядом без интернета.
- **Оформление** — 7 тем, акцентные цвета и обои чата.
- **Админ-панель** — статистика, бан, кик, удаление аккаунтов и рассылка.
- **Свой APK** — сервер сам раздаёт актуальную сборку приложения.

## 📸 Скриншоты

<table>
  <tr>
    <td align="center" width="33%">
      <img src="assets/screenshots/android-discover.png" width="250" alt="Поиск рядом"/><br/>
      <sub><b>Поиск рядом</b><br/>Bluetooth-устройства и SmartLink</sub>
    </td>
    <td align="center" width="33%">
      <img src="assets/screenshots/android-nearby.png" width="250" alt="Собеседник рядом"/><br/>
      <sub><b>Собеседник рядом</b><br/>офлайн-режим без интернета</sub>
    </td>
    <td align="center" width="33%">
      <img src="assets/screenshots/android-settings.png" width="250" alt="Настройки"/><br/>
      <sub><b>Настройки</b><br/>темы, акценты и обои</sub>
    </td>
  </tr>
</table>

## 🧭 Архитектура

<img src="assets/architecture.png" alt="Архитектура UltraChat" width="100%"/>

| Компонент | Технологии | Что делает |
|---|---|---|
| **Android** | Kotlin, Jetpack Compose | Полноценный клиент: чаты, медиа, темы, SmartLink |
| **Web** | HTML, CSS, JavaScript | Лёгкий клиент в браузере без сборки |
| **Server** | Node.js, `ws`, `http` | Аккаунты, presence, сообщения, история, медиа |
| **Storage** | JSON + файлы | `data/messages.json`, `data/media/`, атомарная запись |
| **APK** | Gradle | Раздача `/download/UltraChat.apk` с поддержкой Range |

Один процесс Node.js обслуживает и HTTP API, и WebSocket, и статику веб-клиента, и скачивание APK.

## ⚡ Быстрый старт

### Сервер

```bash
git clone https://github.com/lofe235/UltraChat.git
cd UltraChat/server
npm install
ADMIN_PASSWORD="придумайте-надёжный-пароль" npm start
```

Сервер поднимется на `http://localhost:8080` — там же открывается веб-клиент.

Чтобы сервер раздавал APK из репозитория:

```bash
APK_PATH=../apk/UltraChat-1.8.3.apk npm start
```

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `PORT` | `8080` | Порт HTTP/WebSocket |
| `HOST` | `0.0.0.0` | Интерфейс прослушивания |
| `DATA_DIR` | `./data` | Аккаунты, история и медиа |
| `APK_PATH` | — | Путь к файлу APK для раздачи |
| `ADMIN_PASSWORD` | из `data/admin-password.txt` | Пароль админ-панели |

### Docker + HTTPS

```bash
cd server
cp .env.example .env
# DOMAIN=chat.example.ru
docker compose up -d
```

Caddy автоматически получит TLS-сертификат и проксирует HTTP и WebSocket.

### Android

```bash
cd android
./gradlew assembleDebug
```

Нужны JDK 17 и Android SDK. Готовый подписанный APK уже лежит в [`apk/UltraChat-1.8.3.apk`](apk/UltraChat-1.8.3.apk).

## 🔌 API

| Метод | Путь | Назначение |
|---|---|---|
| `POST` | `/api/register` | Регистрация устройства (`deviceId`, `nickname`, `avatarId`) |
| `GET` | `/api/roster?token=` | Список пользователей и онлайн-статусы |
| `GET` | `/api/search?q=&token=` | Поиск собеседников |
| `GET` | `/api/history?peer=&token=` | История переписки |
| `POST` | `/api/media?token=` | Загрузка изображения (raw body, `Content-Type: image/*`) |
| `GET` | `/media/<file>` | Раздача загруженного файла |
| `GET` | `/health` | Статус сервера и счётчики |
| `WS` | `/ws?token=` | Обмен сообщениями в реальном времени |

WebSocket-кадры: `hello`, `chat`, `ack`, `history`, `typing`, `read`, `edit`, `delete`, `delete_chat`, `ping`, `pong`.

Админ-API (Bearer-токен после `/api/admin/login`): `/api/admin/state`, `/ban`, `/unban`, `/kick`, `/delete-user`, `/notice`, `/notice/clear`, `/reset`, `/lobby/clear`.

## ✅ Тесты

```bash
cd server
npm test
```

25 тестов: регистрация, presence, доставка, история, медиа, баны, лимиты и админ-панель.

## 📁 Структура

```
UltraChat/
├── android/            # Kotlin + Jetpack Compose приложение
│   ├── app/src/main/   # экраны, сеть, SmartLink, темы
│   └── gradle/         # Gradle wrapper
├── apk/                # готовая сборка UltraChat-1.8.3.apk
├── assets/             # баннер, логотип, схема и скриншоты
├── server/             # Node.js релей
│   ├── src/            # app.js, server.js, store.js
│   ├── public/         # веб-клиент
│   ├── test/           # 25 тестов
│   └── Dockerfile      # деплой в один контейнер + Caddy
└── README.md
```

## 🔒 Безопасность

- Папка `data/`, `.env` и Android-keystore **не попадают** в репозиторий.
- Пароль админки задаётся через `ADMIN_PASSWORD` или файл `data/admin-password.txt`.
- В продакшене используйте HTTPS/WSS (Caddy из `docker-compose.yml`).
- Токены устройств привязаны к `deviceId`; отозвать доступ можно удалением аккаунта в админке.

## 📄 Лицензия

[MIT](LICENSE) © 2026 UltraChat

---

<p align="center">
  <sub>Если проект оказался полезным — поставьте ⭐ репозиторию. Это лучшая мотивация продолжать.</sub>
</p>
