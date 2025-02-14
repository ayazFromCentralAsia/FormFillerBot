package com.example.bot.FormFillerBot.service;


import com.example.bot.FormFillerBot.config.BotConfig;
import com.example.bot.FormFillerBot.model.User;
import com.example.bot.FormFillerBot.repository.UserRepository;
import com.example.bot.FormFillerBot.util.UserState;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {
    private final BotConfig config;
    private final UserRepository userRepository;
    private final DocumentService documentService;

    private final Logger logger = LoggerFactory.getLogger(TelegramBot.class);

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return config.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        logger.info("Получено обновление: {}", update);
        if (update.hasMessage() && update.getMessage().hasText()) {
            logger.info("Получено текстовое сообщение: {}", update.getMessage().getText());
            handleTextMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
            handlePhotoMessage(update.getMessage());
        }
    }

    @PostConstruct
    public void init() {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(this);
            logger.info("Бот запущен.");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();
        User user = userRepository.findByChatId(chatId).orElse(null);

        if (user == null) return;

        switch (data) {
            case "CONSENT_AGREE":
                user.setState(UserState.AWAITING_NAME);
                user.setDataProcessingConsent(true);
                userRepository.save(user);
                sendMessage(chatId, "Введите ваше ФИО (Фамилия Имя Отчество):");
                break;

            case "GENDER_MALE":
            case "GENDER_FEMALE":
                user.setGender(data.equals("GENDER_MALE") ? "Мужской" : "Женский");
                user.setState(UserState.AWAITING_PHOTO);
                userRepository.save(user);
                sendMessage(chatId, "Теперь отправьте вашу фотографию.");
                break;
        }
    }


    private void sendConsentMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Вы должны согласиться с обработкой персональных данных.");

        InlineKeyboardButton consentButton = new InlineKeyboardButton("✅ Согласен");
        consentButton.setCallbackData("CONSENT_AGREE");

        InlineKeyboardButton policyButton = new InlineKeyboardButton("📜 Политика конфиденциальности");
        policyButton.setUrl("https://example.com/policy");

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(List.of(List.of(consentButton), List.of(policyButton)));

        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleTextMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();

        User user = userRepository.findByChatId(chatId)
                .orElseGet(() -> createNewUser(chatId, message));

        switch (user.getState()) {
            case START:
                user.setState(UserState.AWAITING_CONSENT);
                userRepository.save(user);
                sendConsentMessage(chatId);
                break;

            case AWAITING_CONSENT:
                sendMessage(chatId, "Пожалуйста, нажмите кнопку согласия.");
                break;

            case AWAITING_NAME:
                processName(user, text);
                break;

            case AWAITING_BIRTH_DATE:
                processBirthDate(user, text);
                break;

            case AWAITING_GENDER:
                sendMessage(chatId, "Выберите пол, используя кнопки ниже.");
                sendGenderButtons(chatId);
                break;

            case AWAITING_PHOTO:
                sendMessage(chatId, "Пожалуйста, отправьте вашу фотографию.");
                break;

            case COMPLETED:
                sendMessage(chatId, "Вы уже заполнили форму.");
                break;

            default:
                sendMessage(chatId, "Неизвестное состояние.");
                break;
        }
    }

    private void sendUserDocument(Long chatId, User user) {
        File document = documentService.generateDocument(user);

        try {
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId.toString());
            sendDocument.setDocument(new InputFile(document, "Анкета_" + user.getLastName() + ".docx"));

            execute(sendDocument);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка при отправке документа.");
        }
    }

    private void handlePhotoMessage(Message message) {
        Long chatId = message.getChatId();
        User user = userRepository.findByChatId(chatId).orElse(null);

        if (user == null) return;

        List<PhotoSize> photos = message.getPhoto();
        if (photos == null || photos.isEmpty()) {
            sendMessage(chatId, "Пожалуйста, отправьте фотографию.");
            return;
        }

        String fileId = photos.get(photos.size() - 1).getFileId();
        user.setPhotoPath(fileId);
        user.setState(UserState.COMPLETED);
        userRepository.save(user);

        sendMessage(chatId, "Спасибо! Ваша анкета заполнена.");

        sendUserDocument(chatId, user);
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private User createNewUser(Long chatId, Message message) {
        User user = new User();
        user.setChatId(chatId);

        if (message.getText().startsWith("/start")) {
            String[] parts = message.getText().split(" ");
            if (parts.length > 1) {
                user.setUtmMark(parts[1]);
            }
        }

        return userRepository.save(user);
    }

    private void processName(User user, String text) {
        String[] nameParts = text.split(" ");
        if (nameParts.length != 3) {
            sendMessage(user.getChatId(), "Пожалуйста, введите ваше ФИО в формате Фамилия Имя Отчество.");
            return;
        }

        user.setLastName(nameParts[0]);
        user.setFirstName(nameParts[1]);
        user.setMiddleName(nameParts[2]);


        user.setState(UserState.AWAITING_BIRTH_DATE);
        userRepository.save(user);

        sendMessage(user.getChatId(), "Введите дату рождения в формате dd.MM.yyyy");
    }

    private void sendGenderButtons(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Выберите ваш пол:");

        InlineKeyboardButton maleButton = new InlineKeyboardButton("Мужской");
        maleButton.setCallbackData("GENDER_MALE");

        InlineKeyboardButton femaleButton = new InlineKeyboardButton("Женский");
        femaleButton.setCallbackData("GENDER_FEMALE");

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        keyboardMarkup.setKeyboard(Arrays.asList(Arrays.asList(maleButton, femaleButton)));

        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void processBirthDate(User user, String text) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            LocalDate birthDate = LocalDate.parse(text, formatter);

            user.setBirthDate(birthDate);
            user.setState(UserState.AWAITING_GENDER);
            userRepository.save(user);

            sendGenderButtons(user.getChatId());
        } catch (DateTimeParseException e) {
            sendMessage(user.getChatId(), "Неверный формат даты. Используйте формат dd.MM.yyyy");
        }
    }
}