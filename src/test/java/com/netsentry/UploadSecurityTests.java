package com.netsentry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void anonymousUploadWithoutCsrfReachesControllerAndStoresFile() throws Exception {
        byte[] image = { (byte) 0x89, 'P', 'N', 'G' };
        var file = new MockMultipartFile("image", "calendar.png", "image/png", image);

        String savedFilename = mvc.perform(multipart("/api/v1/upload-calendar").file(file))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Files.readAllBytes(storageDirectory.resolve(savedFilename))).isEqualTo(image);
    }

    @Test
    void emptyUploadReturnsValidationErrorInsteadOfLoginRedirect() throws Exception {
        var file = new MockMultipartFile("image", "calendar.png", "image/png", new byte[0]);

        mvc.perform(multipart("/api/v1/upload-calendar").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("File is empty"));
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
