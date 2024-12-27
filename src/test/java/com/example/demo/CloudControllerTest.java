package com.example.demo;

import com.example.demo.controller.CloudController;
import com.example.demo.entities.User;
import com.example.demo.services.AuthService;
import com.example.demo.services.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ExtendWith(MockitoExtension.class)
class CloudControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private FileService fileService;

    private MockMvc mockMvc;

    @InjectMocks
    private CloudController cloudController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(cloudController).build();
    }

    @Test
    void login_ValidRequest_ReturnsAuthToken() throws Exception {
        String login = "user1";
        String password = "password";
        String token = "mocked-token";

        Mockito.when(authService.login(login, password))
                .thenReturn(token);

        String requestBody = "{\"login\":\"user1\",\"password\":\"password\"}";
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auth-token").value(token));
    }

    @Test
    void uploadFile_ValidRequest_Success() throws Exception {
        String token = "mocked-token";
        String filename = "test.txt";
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "Hello World".getBytes());

        User mockUser = new User();
        Mockito.when(authService.authenticate(token)).thenReturn(mockUser);

        Mockito.doNothing().when(fileService).addNewFile(Mockito.eq(filename), Mockito.eq(token), Mockito.any(MultipartFile.class));

        mockMvc.perform(multipart("/file")
                        .file(file)
                        .param("filename", filename)
                        .header("auth-token", token))
                .andExpect(status().isOk());
        Mockito.verify(fileService, Mockito.times(1))
                .addNewFile(Mockito.eq(filename), Mockito.eq(token), Mockito.any(MultipartFile.class));
    }
}
