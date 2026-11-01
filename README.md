# HELP — каталог APK

Отдельный серверный проект для Android-приложения-каталога HELP. Не связан с Компик и TVON.

## Реализовано в первой версии
- Публичный API категорий и приложений.
- Загрузка APK через веб-панель.
- Категоризация APK, версия и описание.
- Скачивание APK по API.
- Удаление APK через защищённый API.
- Защита административных операций ключом `HELP_ADMIN_KEY`.

## Запуск локально
Требуется Node.js 20 или новее.

```bash
npm install
HELP_ADMIN_KEY=change-this-key npm start
```

Windows PowerShell:
```powershell
$env:HELP_ADMIN_KEY="change-this-key"
npm start
```

Откройте http://localhost:3000.

## API
- `GET /api/health`
- `GET /api/categories`
- `GET /api/apps`
- `GET /api/apps?categoryId=<id>`
- `GET /api/apps/<id>/download`
- `POST /api/admin/categories` — JSON `{"name":"Медиаплееры"}`, заголовок `x-admin-key`.
- `POST /api/admin/apps` — multipart: `apk`, `categoryId`, `name`, `version`, `description`; заголовок `x-admin-key`.
- `DELETE /api/admin/apps/<id>` — заголовок `x-admin-key`.

## Важно для размещения
APK нельзя хранить в GitHub или на эфемерном диске бесплатного хостинга: файлы могут пропасть при перезапуске или повторном размещении. Для рабочего сервера подключите постоянный диск или объектное хранилище. Укажите `DATA_DIR` и/или `UPLOAD_DIR` на постоянном хранилище. Задайте длинный случайный `HELP_ADMIN_KEY`. Не публикуйте его в репозитории.

## Следующий этап
Android-клиент для Android 7.0+ и Android TV: крупные плитки категорий, список приложений и системный установщик Android после загрузки APK.

## Android-приложение

Исходники приложения находятся в `app/`. Минимальная версия Android — 7.0 (API 24). Приложение рассчитано на Android TV и обычные Android-устройства: управление пультом поддерживается через фокусируемые крупные кнопки.

Перед рабочей сборкой замените `API_BASE_URL` в `app/build.gradle.kts` на HTTPS-адрес запущенного HELP API. Сейчас там оставлена заглушка `https://YOUR-HELP-SERVER.example/`.

## Получение APK через GitHub Actions

После отправки изменений в `main` откройте вкладку **Actions**, выберите **Build HELP Android APK**, дождитесь успешной сборки и скачайте артефакт **HELP-debug-apk**. Также сборку можно запустить вручную через **Run workflow**.

Текущая сборка — отладочная (debug). Для установки на устройства она подходит для тестирования; публикацию и распространение финальной версии оформим отдельно.
