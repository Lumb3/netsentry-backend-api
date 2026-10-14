package com.netsentry;

import com.netsentry.DTOs.ImageUploadResponse;
import com.netsentry.models.ImageUploadApiEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
public class ImageProcessorEndpoints implements ImageUploadApiEndpoints {
    ChatModel chatModel;
    private final ImageLocalStorageService storageService;

    private static final Logger logger = LoggerFactory.getLogger(ImageProcessorEndpoints.class);


    public ImageProcessorEndpoints(ImageLocalStorageService storageService, ChatModel chatModel) {
        this.storageService = storageService;
        this.chatModel = chatModel;
    }

    @Override
    public ImageUploadResponse uploadImage(MultipartFile file) {
        logger.info("Received upload: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed");
        }

        try {
            // Extract JSON response from the image
            extractImage(file);

            // Save the image locally on the computer
            String saved = storageService.save(file);
            logger.info("Saved {} as {}", file.getOriginalFilename(), saved);
            return new ImageUploadResponse(saved);
        } catch (IOException e) {
            logger.error("Failed to store upload locally", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save file");
        }
    }

    private void extractImage(MultipartFile file) {
        ChatResponse response = chatModel.call(
                new Prompt("Say hello")
        );

        logger.info("Response message: {}", response.getResult().getOutput().getText());
    }
}
