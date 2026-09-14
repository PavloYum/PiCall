# Архитектура MVP

```text
Android PiCall
  |-- HTTPS: вход, контакты, TURN credentials
  |-- WebSocket: offer/answer/ICE, состояние звонка
  |-- WebRTC audio --------------------+
                                       |
API + signaling                  coturn на Pi 5
  |-- пользователи                |-- STUN/TURN :443
  |-- звонки                      `-- relay UDP 49152-65535
  `-- push через FCM
```

Signaling управляет звонком, но не передаёт аудио. Аудио идёт напрямую между
устройствами или через coturn, если прямое соединение невозможно.

Для звонков на обычные телефонные номера позже потребуется SIP/PSTN-шлюз
(например, Asterisk или FreeSWITCH) и оператор телефонии.

