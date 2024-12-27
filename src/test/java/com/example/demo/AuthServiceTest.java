package com.example.demo;

import com.example.demo.entities.User;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.AuthService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Nested
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_ValidCredentials_ReturnsToken() {
        String username = "user1";
        String rawPassword = "password";

        PasswordEncoder bcryptPasswordEncoder = new BCryptPasswordEncoder();
        String encodedPassword = bcryptPasswordEncoder.encode(rawPassword);

        User user = new User();
        user.setLogin(username);
        user.setPassword(encodedPassword);

        Mockito.when(userRepository.findByLogin(username))
                .thenReturn(Optional.of(user));

        Mockito.when(passwordEncoder.matches(rawPassword, encodedPassword))
                .thenReturn(true);

        String token = authService.login(username, rawPassword);

        Assertions.assertNotNull(token);
    }

    @Test
    void login_InvalidPassword_ThrowsException() {
        String username = "user1";
        String password = passwordEncoder.encode("password");
        User user = new User();
        user.setLogin(username);
        user.setPassword("password");

        Mockito.when(userRepository.findByLogin(username))
                .thenReturn(Optional.of(user));
        assertThrows(ResponseStatusException.class, () -> authService.login(username, password));
    }
}
