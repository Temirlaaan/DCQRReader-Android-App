# DC Inventory — мобильный клиент учёта оборудования ЦОД

Android-приложение для инженеров дата-центров Transtelecom (TTC): сканирование
QR-меток на оборудовании, просмотр и редактирование карточек устройств из
NetBox, привязка меток, создание и списание устройств — всё с телефона, прямо
у стойки.

Приложение — клиентский край системы из трёх частей:

```
Приложение ──HTTPS/VPN──► Бэкенд (qr-dc.t-cloud.kz) ──► NetBox (источник истины)
     │
     └────OIDC────► Keycloak (sso-ttc.t-cloud.kz, realm prod-v1)
```

Клиент **никогда не ходит в NetBox напрямую**: бэкенд-прослойка владеет
NetBox-токеном, ведёт аудит, смены, идемпотентность и версионирование.
Серверная архитектура описана в [`docs/Architecture_Overview.md`](docs/Architecture_Overview.md),
контракт API — в [`docs/mobile-api-guide.md`](docs/mobile-api-guide.md) и
[`docs/openapi-qr-dc-project.json`](docs/openapi-qr-dc-project.json).

## Возможности

- **Вход через корпоративный SSO** — Keycloak, Authorization Code + PKCE
  (AppAuth, Custom Tab в фирменных цветах). Токены — в EncryptedSharedPreferences.
- **Сканер QR** — CameraX + ML Kit: рамка с детекцией только внутри неё,
  фонарик, ручной ввод кода.
- **Карточка QR** — три состояния: свободный / привязан (полный профиль
  устройства: локация, железо, сеть, комментарии) / списан.
- **Смены** — открытие/закрытие рабочей смены; любая запись без активной
  смены перехватывается диалогом «Открыть смену и повторить».
- **Привязка** свободного QR к существующему устройству (поиск с пагинацией).
- **Редактирование** — server-driven форма (`GET /meta/device-form`),
  optimistic concurrency (`If-Unmodified-Since` + обработка 409 VERSION_CONFLICT).
- **Создание устройства** со свободного QR (справочники NetBox: типы, роли,
  площадки, стойки) с автоматической привязкой метки.
- **Комментарии** в журнал устройства и **списание** (admin-only).
- **Профиль / История / Настройки** — данные сотрудника из ID-токена,
  история сканов, тема (системная/светлая/тёмная).

## Безопасность

| Мера | Реализация |
|---|---|
| TLS certificate pinning | SPKI-пины Sectigo CA в `NetworkModule` (лист не пинится — ротация раз в год) |
| Хранение токенов | EncryptedSharedPreferences (Android Keystore) |
| Авто-логаут | 10 минут неактивности (включая фон) → очистка токенов → логин |
| 401-retry | OkHttp Authenticator: принудительный рефреш + один повтор, single-flight |
| Идемпотентность записей | Idempotency-Key на bind/edit/comment/create/decommission, ключ переживает ретраи |
| Защита экрана | FLAG_SECURE (нет скриншотов/записи экрана), `allowBackup=false` |
| Логи | Authorization-заголовок редактируется даже в debug-сборке |

## Архитектура приложения

Однонаправленный MVVM с репозиториями, один Gradle-модуль:

```
UI (Compose) ──события──► ViewModel ──вызовы──► Repository ──► Retrofit API ──► Бэкенд
     ▲                        │
     └──── StateFlow ◄────────┘
```

```
app/src/main/java/kz/tcloud/dcinv/
├── ui/                  # Экраны: Composable + @HiltViewModel на фичу
│   ├── login, home, scan, qr, bind, edit, create,
│   ├── history, profile, settings
│   ├── common/          # ShiftGate (гейт смены), общие поля форм
│   ├── components/      # BottomNavIsland — плавающая нижняя навигация
│   ├── navigation/      # NavHost, маршруты
│   └── theme/           # M3-тема: Datacenter Blue + TTC Green, без ripple
├── data/
│   ├── auth/            # AppAuth/Keycloak, токены, авто-логаут, claims ID-токена
│   ├── network/
│   │   ├── api/         # Retrofit-интерфейсы (Qr, Device, Session, Meta)
│   │   ├── dto/         # kotlinx.serialization модели (snake_case автоматом)
│   │   ├── interceptor/ # Auth, Request-ID, 401-Authenticator
│   │   └── idempotency/ # Хранилище Idempotency-Key
│   ├── repository/      # Единственная точка данных для ViewModel
│   ├── scan/            # История сканирований (in-memory)
│   └── prefs/           # Настройка темы
├── di/                  # Hilt: NetworkModule (OkHttp+пиннинг), AuthModule
├── domain/              # Валидация формата QR
└── scanner/             # ML Kit анализатор с ограничением зоны детекции
```

Принципы:

- **Тонкий клиент.** Источник истины — бэкенд/NetBox: форма редактирования
  приходит с сервера, конфликты решает версия `last_updated`, права (например,
  списание) проверяет сервер.
- **Ошибки типизированы.** Всё непредвиденное превращается в `ApiException`
  с кодом бэкенда (`VERSION_CONFLICT`, `NO_ACTIVE_SHIFT`, …) — ViewModel'ы
  реагируют на коды, а не на строки.
- **Записи безопасны для ретраев.** Idempotency-Key стабилен для логического
  действия и очищается только при успехе или ошибке 4xx.
- **UI-фидбек — только движением.** Material ripple и crossfade переходов
  отключены глобально; анимации — пружины и перетекание цвета.

## Стек

Kotlin 2.0 · Jetpack Compose (Material 3) · Hilt · Navigation Compose ·
Retrofit + OkHttp + kotlinx.serialization · AppAuth (OIDC/PKCE) ·
CameraX + ML Kit Barcode · EncryptedSharedPreferences

minSdk 24 · targetSdk 35 · AGP 8.13 · Gradle 9.4

## Сборка

Нужен **JDK 21** (подойдёт JBR из Android Studio; системный JDK 11 не годится):

```bash
JAVA_HOME=/path/to/jbr ./gradlew assembleDebug      # APK
JAVA_HOME=/path/to/jbr ./gradlew testDebugUnitTest  # юнит-тесты
```

Окружение зашито в `app/build.gradle.kts` (BuildConfig): API
`https://qr-dc.t-cloud.kz` (внутренний, нужен VPN), Keycloak
`https://sso-ttc.t-cloud.kz` (realm `prod-v1`, client `dcinv-mobile`,
redirect `kz.tcloud.dcinv:/oauth/callback`).

## Эксплуатационные заметки

- **Ротация TLS-сертификата.** `*.t-cloud.kz` истекает **2026-07-09**. Если
  новый сертификат снова от Sectigo — ничего делать не нужно; при смене CA
  обновить пины в `di/NetworkModule.kt` **до** замены на сервере (команда для
  снятия пинов — в комментарии там же).
- **Скорость входа** регулируется на стороне Keycloak (настроен вход
  username + OTP); длительность SSO-сессии браузера — `SSO Session Idle`
  в настройках realm.
