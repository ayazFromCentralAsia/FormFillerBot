package com.example.bot.FormFillerBot.model;

import com.example.bot.FormFillerBot.util.UserState;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private String utmMark;
    private boolean dataProcessingConsent;
    private String firstName;
    private String lastName;
    private String middleName;
    private LocalDate birthDate;
    private String gender;
    private String photoPath;
    private UserState state = UserState.START;
}