package com.souvik.ai.meeting_summarizer.controller;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.souvik.ai.meeting_summarizer.service.S3Service;

@RestController
public class MeetingController {

    private final S3Service s3Service;

    public MeetingController(S3Service s3Service) {  
        this.s3Service = s3Service;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadMeeting(@RequestParam("file") MultipartFile file) throws IOException {
        String key = s3Service.uploadFile(file);
        return ResponseEntity.ok("Uploaded file to S3 with key: " + key);
    }
}
