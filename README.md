# PiCall

Нативное Android-приложение собственной IP-телефонии: Kotlin, Jetpack Compose,
WebRTC и собственный signaling-сервис. Coturn на Raspberry Pi 5 будет обеспечивать
TURN/STUN для соединений за NAT.

PiCall связывает только участников, установивших приложение. Обычные телефонные
номера, SIM, SIP и PSTN не входят в основной сценарий.

## Текущий этап

- создан Android-модуль и демонстрационный экран звонка;
- описаны состояния звонка;
- определены независимые контракты signaling и WebRTC;
- добавлен публичный PiCall ID формата `PC-XXXX-XXXX`;
- реальные сеть, медиа, авторизация и push пока не подключены.

## План MVP

1. Регистрация с выдачей PiCall ID и WebSocket signaling-сервис.
2. WebRTC-аудио и выдача временных TURN-учётных данных.
3. Входящий звонок через Firebase Cloud Messaging.
4. Android Telecom/ConnectionService и foreground service.
5. История звонков, контакты, шифрование и наблюдаемость.

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

После изменения `versionName` собрать и добавить новую версию можно командой:

```bash
./tools/archive-apk.sh
```

Папка существующей версии намеренно не перезаписывается.

## Важное ограничение

Это пока UI-каркас: кнопка «Соединить (демо)» только переключает локальное
состояние. Она не устанавливает настоящий звонок.
