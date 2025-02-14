# FormFillerBot

## Описание
FormFillerBot — это Telegram-бот, который проводит пользователя через процесс заполнения формы и генерирует Word-документ на основе введённых данных.

## Требования
- Java 21
- Maven
- Docker
- Docker Compose

## Настройка
1. Создайте бота в Telegram через [@BotFather](https://t.me/BotFather) и получите `token`.
2. Откройте файл `docker-compose.yml` и замените значения переменных:
   ```yaml
   TELEGRAM_BOT_USERNAME=telegram_bot_username_here
   TELEGRAM_BOT_TOKEN=telegram_bot_token_here
   ```

## Запуск
### Сборка и запуск через Docker Compose:
```sh
docker-compose up --build
```

### Остановка:
```sh
docker-compose down
```

## Функциональность
- Обработка UTM-меток при старте
- Пошаговое заполнение формы:
    - Согласие на обработку персональных данных
    - ФИО
    - Дата рождения
    - Пол
    - Фотография
- Генерация Word-документа