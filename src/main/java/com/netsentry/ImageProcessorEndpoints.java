package com.netsentry;

import com.netsentry.DTOs.ImageUploadResponse;
import com.netsentry.models.ImageUploadApiEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.ai.content.Media;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@RestController
public class ImageProcessorEndpoints implements ImageUploadApiEndpoints {
    ChatModel chatModel;
    private final ImageLocalStorageService storageService;

    private static final Logger logger = LoggerFactory.getLogger(ImageProcessorEndpoints.class);

    private final String imagePrompt;

    public ImageProcessorEndpoints(ImageLocalStorageService storageService, ChatModel chatModel, @Value("${calendar.extraction.prompt}") String imagePrompt) {
        this.storageService = storageService;
        this.chatModel = chatModel;
        this.imagePrompt=imagePrompt;
    }

    private String checkContentType(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "File is empty"
            );
        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !Set.of("image/png", "image/jpeg").contains(contentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported image format"
            );
        }

        return contentType;
    }

    @Override
    public ImageUploadResponse uploadImage(MultipartFile file) {
        logger.info("Received upload: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        try {
            String contentType = checkContentType(file);
            // Extract JSON response from the image
            String extractedResponse = extractImage(file, contentType);

            // Save the image locally on the computer
            String saved = storageService.save(file);
            logger.info("Saved {} as {}", file.getOriginalFilename(), saved);
            return new ImageUploadResponse(saved);
        } catch (IOException e) {
            logger.error("Failed to store upload locally", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save file");
        }
    }



    private String extractImage(
            MultipartFile file,
            String contentType
    ) throws IOException {

        var imageResource = new ByteArrayResource(file.getBytes());

        var userMessage = UserMessage.builder()
                .text(imagePrompt)
                .media(new Media(
                        MimeTypeUtils.parseMimeType(contentType),
                        imageResource
                ))
                .build();

        long start = System.currentTimeMillis();

        logger.info("Sending image to Ollama...");


        List<String> chunks = chatModel.stream(new Prompt(userMessage))
                .map(response -> {
                    if (response.getResult() == null ||
                            response.getResult() == null) {
                        return "";
                    }

                    String text = response.getResult()
                            .getOutput()
                            .getText();

                    return text == null ? "" : text;
                })
                .filter(chunk -> !chunk.isEmpty())
                .doOnNext(chunk -> logger.debug("Received Ollama chunk"))
                .doOnComplete(() -> logger.info("Ollama generation completed"))
                .doOnError(error -> logger.error("Ollama generation failed", error))
                .collectList()
                .block(Duration.ofMinutes(5));

        String content = chunks == null ? "" : String.join("", chunks);

        if (content.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Ollama returned an empty response"
            );
        }

        long elapsed = System.currentTimeMillis() - start;

        logger.info("Ollama finished in {} ms", elapsed);
        logger.info("Final Ollama response {}", content);
        return content;
    }

}
