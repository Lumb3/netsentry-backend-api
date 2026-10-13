package com.netsentry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(ImageLocalStorageService.class);

    public ImageLocalStorageService(@Value("${app.storage.location}") String location) {
        logger.info("The local directory that saves images: {} ", location);
        this.root = Path.of(location).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            logger.error("images/ directory does not exist");
        }
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
        } catch (IOException e) {
            logger.error("Failed to copy the image to the images/ directory");
        }
        return uniqueFilename;
    }
}