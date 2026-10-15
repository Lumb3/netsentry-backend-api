package com.netsentry;

import com.netsentry.DTOs.ImageUploadResponse;
import com.netsentry.models.ImageUploadApiEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.ai.content.Media;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

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


    private String extractImage(MultipartFile file) throws IOException {

        var imageResource = new ByteArrayResource(file.getBytes());


        var userMessage = UserMessage.builder()
                .text("Tell me what you see in this image. Just give a single sentence.")
                .media( new Media(MimeTypeUtils.parseMimeType(file.getContentType()), imageResource))
                .build();

        long start = System.currentTimeMillis(); // Record the start time of the generation
        StringBuilder fullResponse = new StringBuilder();

        logger.info("Sending image to Ollama...");

        Flux<ChatResponse> stream = this.chatModel.stream(new Prompt(userMessage));

        stream.subscribe(response -> {
            String chunk = response.getResult().getOutput().getText();

            if (chunk != null && !chunk.isEmpty()) {
                fullResponse.append(chunk);
               logger.info("[Each Response] {}", chunk);
            }
        });
        stream.doOnComplete(() -> logger.info("Ollama generation has completed"))
                .doOnError(error -> logger.error("Ollama generation failed", error)).blockLast();

        long elapsed = System.currentTimeMillis() - start;
        String content = fullResponse.toString();

        if (content.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Ollama returned an empty response"
            );
        }

        logger.info("Ollama finished in {} ms", elapsed);
        logger.info("Full response: {}", content);

        return content;
    }

}
