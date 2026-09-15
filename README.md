# PiCall

Нативное Android-приложение собственной IP-телефонии: Kotlin, Jetpack Compose,
WebRTC и собственный signaling-сервис. Coturn на Raspberry Pi 5 будет обеспечивать
TURN/STUN для соединений за NAT.

PiCall связывает только участников, установивших приложение. Обычные телефонные
номера, SIM, SIP и PSTN не входят в основной сценарий.

## Текущий этап

- создано нативное Android-приложение;
- добавлен публичный PiCall ID формата `PC-XXXX-XXXX`;
- регистрация без приглашения и общий список участников с online-статусом;
- работает реальный двусторонний WebRTC-аудиозвонок через signaling и TURN;
- реализованы входящий вызов, ответ, отклонение и завершение разговора;
- foreground service удерживает online-статус приложения в фоне;
- приложение использует временные TURN-учётные данные, выданные сервером.

## План MVP

Следующие крупные этапы: доставка входящего звонка после принудительной остановки
приложения через push, интеграция Android Telecom/ConnectionService, история
звонков и экран активного разговора с управлением микрофоном и динамиком.

## Сервер

В `server/` находится API регистрации/входа и WebSocket signaling. Локальный
стек с PostgreSQL запускается через Docker Compose:

```bash
cp .env.example .env
# замените пароли и JWT_SECRET в .env
docker compose up --build
```

Проверка: `curl http://localhost:8080/health`. Production API доступен по
`https://picall.velu-vara.com`, WebSocket — `wss://picall.velu-vara.com/v1/signaling`.

Для международных звонков сервер выдаёт краткоживущие credentials Cloudflare
Realtime TURN через `GET /v1/turn-credentials`. Доступны UDP, TCP и TLS 443,
поэтому связь не зависит от CGNAT или проброса портов домашнего роутера.

Домашний coturn остаётся резервным: UDP 443 обслуживает сервис `coturn`, а
TLS/TCP 443 — `coturn-tls`. Сертификаты для последнего хранятся локально в
`turn-certs/` и не добавляются в Git. Cloudflare TURN key задаётся переменными
`CF_TURN_KEY_ID` и `CF_TURN_KEY_TOKEN` в `.env`.

## Локальная сборка

Обычная рабочая станция: Android Studio с Android SDK 36 и JDK 17. Откройте
корневую папку как Android-проект и дождитесь Gradle Sync.

На текущем Raspberry Pi 5 используется сохранённый toolchain AndroidServerBot:

```bash
./tools/build-local.sh
```

Debug APK появится в `app/build/outputs/apk/debug/`.

## Архив APK

Собранные версии сохраняются в Git отдельно от временной папки `build`:

```text
apk/<version>/PiCall-<version>-debug.apk
apk/<version>/SHA256SUMS
```

В репозиторий сохраняются только готовые проверенные выпуски. После завершения
крупного этапа собрать и добавить выпуск можно командой:

```bash
./tools/archive-apk.sh
```

Папка существующей версии намеренно не перезаписывается.

## Ограничение текущего выпуска

Входящий звонок приходит, пока foreground service PiCall работает. После
принудительной остановки приложения Android понадобится push-доставка.
