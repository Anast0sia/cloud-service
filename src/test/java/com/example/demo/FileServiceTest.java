package com.example.demo;

import com.example.demo.entities.File;
import com.example.demo.entities.User;
import com.example.demo.repositories.FileRepository;
import com.example.demo.services.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private FileService fileService;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testUser = new User();
        testUser.setId(1);
    }

    @Test
    void addNewFile_ShouldThrowUnauthorized_WhenUserIsNull() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                fileService.addNewFile("test.txt", multipartFile, null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Invalid token", exception.getReason());
    }

    @Test
    void addNewFile_ShouldThrowBadRequest_WhenFileIsEmpty() {
        when(multipartFile.isEmpty()).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                fileService.addNewFile("test.txt", multipartFile, testUser));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("File is empty", exception.getReason());
    }

    @Test
    void addNewFile_ShouldThrowConflict_WhenFileAlreadyExists() {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(fileRepository.existsByFilenameAndUserId("test.txt", testUser.getId())).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                fileService.addNewFile("test.txt", multipartFile, testUser));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("File with this name already exists", exception.getReason());
    }

    @Test
    void addNewFile_ShouldSaveFileSuccessfully() throws IOException {
        String filename = "test.txt";
        byte[] fileContent = "Test content".getBytes();
        Path userDirectory = Paths.get("file-storage", String.valueOf(testUser.getId()));
        Path filePath = userDirectory.resolve(filename);

        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getBytes()).thenReturn(fileContent);
        when(fileRepository.existsByFilenameAndUserId(filename, testUser.getId())).thenReturn(false);

        fileService.addNewFile(filename, multipartFile, testUser);

        verify(fileRepository, times(1)).save(any(File.class));

        Files.deleteIfExists(filePath);
        Files.deleteIfExists(userDirectory);
    }

    @Test
    void addNewFile_ShouldThrowInternalServerError_WhenIOExceptionOccurs() throws IOException {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(fileRepository.existsByFilenameAndUserId("test.txt", testUser.getId())).thenReturn(false);
        when(multipartFile.getBytes()).thenThrow(new IOException("Disk error"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                fileService.addNewFile("test.txt", multipartFile, testUser));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertTrue(Objects.requireNonNull(exception.getReason()).contains("Error saving file to disk"));
    }
}