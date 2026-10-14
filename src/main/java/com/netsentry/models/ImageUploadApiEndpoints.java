package com.netsentry.models;

import com.netsentry.DTOs.ImageUploadResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/v1")
public interface ImageUploadApiEndpoints {

    @PostMapping(value = "/upload-calendar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImageUploadResponse uploadImage(@RequestParam("image") MultipartFile file);
}
