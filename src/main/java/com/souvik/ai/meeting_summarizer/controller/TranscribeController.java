package com.souvik.ai.meeting_summarizer.controller;

import com.souvik.ai.meeting_summarizer.service.TranscribeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/transcribe")
public class TranscribeController {

    private final TranscribeService transcribeService;

    public TranscribeController(TranscribeService transcribeService) {
        this.transcribeService = transcribeService;
    }

    /**
     * Start a transcription job for an existing S3 object.
     * Request body JSON: { "s3Key": "uploads/sample_meeting_2.mp3" }
     */
    @PostMapping("/start")
    public ResponseEntity<?> startJob(@RequestBody Map<String, String> body) {
        String s3Key = body.get("s3Key");
        if (s3Key == null || s3Key.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "s3Key is required in request body"));
        }

        try {
            Map<String, String> resp = transcribeService.startTranscriptionJob(s3Key);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check job status.
     * Example: GET /api/transcribe/status/{jobName}
     */
    @GetMapping("/status/{jobName}")
    public ResponseEntity<?> getStatus(@PathVariable String jobName) {
        try {
            Map<String, String> resp = transcribeService.getJobStatus(jobName);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Fetch transcript text once job is completed.
     * Example: GET /api/transcribe/result/{jobName}
     * If transcriptUri is present, the service will fetch the JSON and return the transcript text.
     */
    @GetMapping("/result/{jobName}")
    public ResponseEntity<?> getResult(@PathVariable String jobName) {
        try {
            Map<String, String> statusMap = transcribeService.getJobStatus(jobName);
            String status = statusMap.get("status");
            if (!"COMPLETED".equalsIgnoreCase(status)) {
                return ResponseEntity.ok(Map.of("status", status, "message", "Transcript not ready yet"));
            }

            String transcriptUri = statusMap.get("transcriptUri");
            if (transcriptUri == null) {
                return ResponseEntity.internalServerError().body(Map.of("error", "No transcriptUri returned by Transcribe"));
            }

            String text = transcribeService.fetchTranscriptTextFromUri(transcriptUri);
            return ResponseEntity.ok(Map.of("status", "COMPLETED", "transcript", text));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}