package com.example.demo;

import com.example.demo.entities.User;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.PasswordMigrationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

class PasswordMigrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordMigrationService passwordMigrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void migratePasswords_ShouldEncodeAndSavePasswords_WhenNotEncoded() {
        User user1 = new User();
        user1.setPassword("plainPassword1");

        User user2 = new User();
        user2.setPassword("$2a$10$encodedPassword2");

        User user3 = new User();
        user3.setPassword("plainPassword3");

        List<User> users = Arrays.asList(user1, user2, user3);

        when(userRepository.findAll()).thenReturn(users);
        when(passwordEncoder.encode("plainPassword1")).thenReturn("$2a$10$hashedPassword1");
        when(passwordEncoder.encode("plainPassword3")).thenReturn("$2a$10$hashedPassword3");

        passwordMigrationService.migratePasswords();

        verify(passwordEncoder, times(1)).encode("plainPassword1");
        verify(passwordEncoder, times(1)).encode("plainPassword3");
        verify(userRepository, times(1)).save(user1);
        verify(userRepository, times(1)).save(user3);
        verify(userRepository, never()).save(user2); // Already encoded, should not be saved

        Assertions.assertEquals("$2a$10$hashedPassword1", user1.getPassword());
        Assertions.assertEquals("$2a$10$hashedPassword3", user3.getPassword());
    }

    @Test
    void migratePasswords_ShouldDoNothing_WhenAllPasswordsAreEncoded() {
        User user1 = new User();
        user1.setPassword("$2a$10$encodedPassword1");

        User user2 = new User();
        user2.setPassword("$2a$10$encodedPassword2");

        List<User> users = Arrays.asList(user1, user2);

        when(userRepository.findAll()).thenReturn(users);

        passwordMigrationService.migratePasswords();

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
}
