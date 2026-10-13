package com.netsentry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class ImageLocalStorageService {

    private final Path root;

    public ImageLocalStorageService(@Value("${app.storage.location}") String location) throws IOException {
        this.root = Path.of(location).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String save(MultipartFile file) throws IOException {
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());

        String uniqueFilename = UUID.randomUUID() + original;
        Path target = root.resolve(uniqueFilename).normalize();

        if (!target.startsWith(root)) {
            throw new IOException("Invalid file path");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return uniqueFilename;
    }
}