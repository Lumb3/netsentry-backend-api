package com.netsentry;

import com.netsentry.DTOs.ImageUploadResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@AutoConfigureMockMvc
class UploadSecurityTests {

    @TempDir
    static Path storageDirectory;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.location", storageDirectory::toString);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void anonymousUploadWithoutCsrfReachesControllerAndStoresFile() throws Exception {
        byte[] image = { (byte) 0x89, 'P', 'N', 'G' };
        var file = new MockMultipartFile("image", "calendar.png", "image/png", image);

        String response = mvc.perform(multipart("/api/v1/upload-calendar").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        String savedFilename = json.readValue(response, ImageUploadResponse.class).filename();
        assertThat(Files.readAllBytes(storageDirectory.resolve(savedFilename))).isEqualTo(image);
    }

    @Test
    void emptyUploadReturnsValidationErrorInsteadOfLoginRedirect() throws Exception {
        var file = new MockMultipartFile("image", "calendar.png", "image/png", new byte[0]);

        mvc.perform(multipart("/api/v1/upload-calendar").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"status":400,"message":"File is empty"}
                        """));
    }

    @Test
    void nonImageUploadReturnsStructuredValidationError() throws Exception {
        var file = new MockMultipartFile("image", "calendar.txt", "text/plain", new byte[] { 1 });

        mvc.perform(multipart("/api/v1/upload-calendar").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"status":400,"message":"Only image files are allowed"}
                        """));
    }

    @Test
    void calendarConnectionTestStillRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/test/google"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void otherPostsStillRequireCsrfToken() throws Exception {
        mvc.perform(post("/api/v1/test/google"))
                .andExpect(status().isForbidden());
    }
}
