package com.example.demo.repositories;

import com.example.demo.entities.File;
import com.example.demo.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<File, Integer> {
    List<File> findByUser(User user);
    boolean existsByFilenameAndUserId(String filename, int user_id);
    Optional<File> findByUserAndFilename(User user, String filename);
}