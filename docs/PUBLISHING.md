# Публикация на GitHub

## Создание репозитория

На странице `https://github.com/new` укажите:

- Owner: `maxstrix324`;
- Repository name: `WorkBalance-Android`;
- Description: `Локальное Android-приложение для учёта рабочего времени, обеда и месячного баланса`;
- Visibility: `Public`.

Не добавляйте README, `.gitignore` или лицензию при создании: они уже находятся в проекте.

## Первый push из PowerShell

Откройте PowerShell в корне проекта, где находится `settings.gradle.kts`:

```powershell
git init
git config user.name "maxstrix324"
git config user.email "maxstrix324@users.noreply.github.com"
git add .
git commit -m "Release WorkBalance 0.4.0"
git branch -M main
git remote add origin https://github.com/maxstrix324/WorkBalance-Android.git
git push -u origin main
```

При первом push Git может открыть браузер для авторизации.

## После публикации

1. Откройте вкладку Actions и дождитесь завершения `Android CI`.
2. Проверьте, что в репозитории отсутствуют JKS, APK, JSON и CSV с реальными данными.
3. При необходимости создайте GitHub Release и прикрепите подписанный APK вручную. Не прикладывайте ключ подписи.
