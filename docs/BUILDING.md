# Сборка и установка

## Требования

- Android Studio;
- JDK 17;
- Android SDK Platform 35;
- Windows, Linux или macOS;
- интернет при первой синхронизации зависимостей.

Gradle 8.9 не следует запускать на JDK 25. В Android Studio выберите JDK 17 в настройках Gradle.

## Debug APK

Windows PowerShell:

```powershell
.\gradlew.bat test assembleDebug
```

Linux или macOS:

```bash
./gradlew test assembleDebug
```

Результат:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Постоянная release-подпись

1. Выберите `Build → Generate Signed App Bundle or APK`.
2. Выберите `APK` и модуль `app`.
3. Создайте JKS или выберите существующий.
4. Для новых версий всегда используйте тот же файл, alias и пароли.
5. Выберите вариант `release`, подписи V1 и V2.

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
