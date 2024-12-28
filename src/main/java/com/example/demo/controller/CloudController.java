package com.example.demo.controller;

import com.example.demo.entities.File;
import com.example.demo.entities.User;
import com.example.demo.repositories.FileRepository;
import com.example.demo.services.AuthService;
import com.example.demo.services.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.*;

@RestController
public class CloudController {
    private final AuthService authService;
    private final FileService fileService;
    private final FileRepository fileRepository;
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    @Autowired
    public CloudController(AuthService authService, FileService fileService, FileRepository fileRepository) {
        this.authService = authService;
        this.fileService = fileService;
        this.fileRepository = fileRepository;
    }

    @PostMapping("/file")
    public ResponseEntity<?> uploadFile(
            @RequestHeader("auth-token") String token,
            @RequestParam("filename") String filename,
            @RequestPart("file") MultipartFile file) {
        token = extractToken(token);
        try {
            User user = authService.authenticate(token);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Unauthorized", "id", 401));
            }
            fileService.addNewFile(filename, file, user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error saving file", "id", 500));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> listFiles(@RequestHeader("auth-token") String token,
                                       @RequestParam(value = "limit", required = false) Integer limit) {
        token = extractToken(token);
        logger.info("Received auth-token: " + token);
        User user = authService.authenticate(token);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized", "id", 401));
        }

        List<File> files = fileService.findByUser(user);
        if (limit != null) {
            files = files.subList(0, Math.min(limit, files.size()));
        }
        return ResponseEntity.ok(files);
    }

    @DeleteMapping("/file")
    public ResponseEntity<?> deleteFile(@RequestHeader("auth-token") String token,
                                        @RequestParam("filename") String filename) {
        token = extractToken(token);
        User user = authService.authenticate(token);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Unauthorized", "id", 401));
        }

        Optional<File> file = fileService.findByUserAndFilename(user, filename);
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "File not found", "id", 400));
        }

        try {
            fileRepository.delete(file.get());
            Path userDirectory = Paths.get("file-storage", String.valueOf(user.getId()));
            Path filePath = userDirectory.resolve(filename);
            Files.deleteIfExists(filePath);

            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error deleting file from disk", "id", 500));
        }
    }

    @GetMapping("/file")
    public ResponseEntity<?> downloadFile(@RequestHeader("auth-token") String token,
                                          @RequestParam("filename") String filename) {
        token = extractToken(token);
        try {
            User user = authService.authenticate(token);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Unauthorized", "id", 401));
            }
            fileService.findByUserAndFilename(user, filename)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "File not found"));

            Path filePath = Paths.get("file-storage", String.valueOf(user.getId()), filename);
            logger.info("File path: " + filePath.toAbsolutePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "File not found on disk", "id", 500));
            }

            byte[] fileContent = Files.readAllBytes(filePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(fileContent);

        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", Objects.requireNonNull(e.getReason()), "id", e.getStatusCode().value()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unexpected error occurred", "id", 500));
        }
    }

    @PutMapping("/file")
    public ResponseEntity<?> editFileName(@RequestHeader("auth-token") String token,
                                          @RequestParam("filename") String filename,
                                          @RequestBody Map<String, String> body) {
        token = extractToken(token);
        User user = authService.authenticate(token);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized", "id", 401));
        }

        String newName = body.get("filename");
        if (newName == null || newName.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "New filename is required", "id", 400));
        }

        Optional<File> optionalFile = fileService.findByUserAndFilename(user, filename);
        if (optionalFile.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "File not found", "id", 400));
        }

        File fileInfo = optionalFile.get();
        if (fileRepository.existsByFilenameAndUserId(newName, user.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "File with this name already exists", "id", 409));
        }

        Path oldFilePath = Paths.get("file-storage", String.valueOf(user.getId()), filename);
        Path newFilePath = Paths.get("file-storage", String.valueOf(user.getId()), newName);

        boolean renamed = oldFilePath.toFile().renameTo(newFilePath.toFile());
        if (renamed) {
            fileInfo.setFilename(newName);
            fileRepository.save(fileInfo);
            logger.info("File renamed to: " + newName);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error renaming file", "id", 500));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        if (!credentials.containsKey("login") || !credentials.containsKey("password")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "status", "error",
                            "message", "Missing login or password"
                    ));
        }

        String login = credentials.get("login");
        String password = credentials.get("password");
        try {
            logger.info("Login: " + login + ", password: " + password);
            String token = authService.login(login, password);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "auth-token", token,
                            "status", "success"
                    ));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "status", "error",
                            "message", "Invalid login or password"
                    ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "status", "error",
                            "message", "Unexpected error occurred"
                    ));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("auth-token") String token) {
        token = extractToken(token);
        if (authService.logout(token) != null) {
            logger.info("Logout successful");
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "status", "error",
                "message", "Unauthorized"));
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }
}