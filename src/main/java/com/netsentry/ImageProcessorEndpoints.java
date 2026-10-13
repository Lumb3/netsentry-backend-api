package com.netsentry;

import com.netsentry.DTOs.ImageUploadResponse;
import com.netsentry.models.ImageUploadApiEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
public class ImageProcessorEndpoints implements ImageUploadApiEndpoints {

    private static final Logger logger = LoggerFactory.getLogger(ImageProcessorEndpoints.class);
    private final ImageLocalStorageService storageService;

    public ImageProcessorEndpoints(ImageLocalStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public ResponseEntity<ImageUploadResponse> uploadImage(MultipartFile file) {
        logger.info("Received upload: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed");
        }

        try {
            String saved = storageService.save(file);
            logger.info("Saved {} as {}", file.getOriginalFilename(), saved);
            return ResponseEntity.ok(new ImageUploadResponse(saved));
        } catch (IOException e) {
            logger.error("Failed to store upload locally", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save file");
        }
    }
}
