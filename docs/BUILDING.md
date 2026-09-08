# Сборка и установка / Build and install

## Требования

- Android Studio;
- JDK 17;
- Android SDK Platform 36;
- Windows, Linux или macOS;
- интернет при первой синхронизации зависимостей.

Проект использует Gradle 8.11.1 и Android Gradle Plugin 8.9.1. В Android Studio выберите JDK 17 в настройках Gradle. JDK 25 для этой сборки не нужен.

The project uses Gradle 8.11.1, Android Gradle Plugin 8.9.1, and JDK 17.

## Варианты / Variants

- `github` — APK для GitHub и прямой установки; содержит проверку релизов GitHub и разрешение на интернет;
- `play` — сборка Google Play без встроенного обновлятора и разрешения на интернет;
- `rustore` — сборка RuStore без встроенного обновлятора и разрешения на интернет.

У всех вариантов одинаковый `applicationId = ru.maxstrix.workbalance`, поэтому локальная база совместима. На одно устройство следует устанавливать только вариант, подписанный тем же ключом, что и предыдущая версия.

## Debug APK для GitHub

Windows PowerShell:

```powershell
.\gradlew.bat testGithubDebugUnitTest assembleGithubDebug
```

Linux или macOS:

```bash
./gradlew testGithubDebugUnitTest assembleGithubDebug
```

Результат:

```text
app/build/outputs/apk/github/debug/app-github-debug.apk
```

## Сборки для публикации

```powershell
# APK для GitHub
.\gradlew.bat assembleGithubRelease

# AAB для Google Play
.\gradlew.bat bundlePlayRelease

# APK для RuStore
.\gradlew.bat assembleRustoreRelease
```

Без настроенной подписи Gradle может создать неподписанный release-файл. Для обычной ручной публикации используйте Android Studio и постоянный JKS.

## Постоянная release-подпись

1. Выберите `Build → Generate Signed App Bundle or APK`.
2. Выберите `APK` и модуль `app`.
3. Создайте JKS или выберите существующий.
4. Для новых версий всегда используйте тот же файл, alias и пароли.
5. Для прямой установки выберите `githubRelease` или `rustoreRelease` и подписи V1/V2.
6. Для Google Play выберите `playRelease` и формат Android App Bundle (`.aab`).

Ключ следует хранить вне репозитория и резервировать минимум в двух защищённых местах. Потеря ключа не позволит установить новую версию поверх старой.

Файлы `*.jks`, `*.keystore`, `keystore.properties`, APK и AAB исключены из Git.

## Сохранение данных при обновлении

Android сохраняет базу приложения, если:

- `applicationId` остаётся `ru.maxstrix.workbalance`;
- новый APK подписан тем же ключом;
- `versionCode` увеличен;
- приложение обновляется поверх старого, а не удаляется;
- для изменений Room добавлена корректная миграция.

Перед первым обновлением новой версии рекомендуется создать JSON-резервную копию.
