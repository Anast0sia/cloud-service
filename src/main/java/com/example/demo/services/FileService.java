package com.example.demo.services;

import com.example.demo.entities.File;
import com.example.demo.repositories.FileRepository;
import com.example.demo.entities.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
public class FileService {
    private final FileRepository fileRepository;
    private final AuthService authService;

    public FileService(FileRepository fileRepository, AuthService authService) {
        this.fileRepository = fileRepository;
        this.authService = authService;
    }

    public void addNewFile(String filename, String token, MultipartFile file) {
        try {
            User user = authService.authenticate(token);
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
            }
            if (file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
            }
            boolean fileExists = fileRepository.existsByFilenameAndUserId(filename, user.getId());
            if (fileExists) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "File with this name already exists");
            }
            Path userDirectory = Paths.get("file-storage", String.valueOf(user.getId()));
            if (!Files.exists(userDirectory)) {
                Files.createDirectories(userDirectory);
            }
            Path filePath = userDirectory.resolve(filename);
            Files.write(filePath, file.getBytes());
            File fileEntity = new File();
            System.out.println("Uploaded file size: " + file.getSize());
            fileEntity.setFilename(filename);
            fileEntity.setSize(file.getSize());
            fileEntity.setUser(user);
            fileRepository.save(fileEntity);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error saving file to disk: " + e.getMessage());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    public List<File> findByUser(User user) {
        return fileRepository.findByUser(user);
    }

    public Optional<File> findByUserAndFilename(User user, String filename) {
        return fileRepository.findByUserAndFilename(user, filename);
    }
}