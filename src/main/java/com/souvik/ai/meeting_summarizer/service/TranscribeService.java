package com.souvik.ai.meeting_summarizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TranscribeService {

    private final TranscribeClient transcribeClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.region:ap-south-1}")
    private String region;

    public TranscribeService(@Value("${aws.region:ap-south-1}") String region) {
        this.transcribeClient = TranscribeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(ProfileCredentialsProvider.create("default"))
                .build();
        this.region = region;
    }

    /**
     * Start a transcription job for the given S3 key.
     * @param s3Key Key in the AWS S3 bucket (e.g. "uploads/sample_meeting_2.mp3")
     * @return map containing jobName
     */
    public Map<String, String> startTranscriptionJob(String s3Key) {
        String jobName = "transcribe-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8);

        // S3 URI of the media file
        String mediaUri = "s3://" + bucketName + "/" + s3Key;

        Media media = Media.builder()
                .mediaFileUri(mediaUri)
                .build();

        StartTranscriptionJobRequest request = StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .languageCode(LanguageCode.EN_US) // change if your audio language differs
                .mediaFormat(getMediaFormatFromKey(s3Key))
                .media(media)
                // Do NOT set OutputBucketName unless you want transcribe to write directly to your bucket.
                // If you set an output bucket, ensure transcribe role permissions allow it.
                .build();

        transcribeClient.startTranscriptionJob(request);

        Map<String, String> resp = new HashMap<>();
        resp.put("jobName", jobName);
        resp.put("mediaUri", mediaUri);
        return resp;
    }

    /**
     * Get transcription job status and transcript file URI (if available).
     * @param jobName transcription job name
     * @return map with keys: status, transcriptUri (if COMPLETED)
     */
    public Map<String, String> getJobStatus(String jobName) {
        GetTranscriptionJobRequest req = GetTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .build();

        GetTranscriptionJobResponse resp = transcribeClient.getTranscriptionJob(req);
        TranscriptionJob job = resp.transcriptionJob();

        Map<String, String> map = new HashMap<>();
        String status = job.transcriptionJobStatusAsString();
        map.put("status", status);

        if (job.transcript() != null && job.transcript().transcriptFileUri() != null) {
            map.put("transcriptUri", job.transcript().transcriptFileUri());
        }
        if (job.failureReason() != null) {
            map.put("failureReason", job.failureReason());
        }

        return map;
    }

    /**
     * Download transcript JSON from transcriptUri and extract the transcript text.
     * @param transcriptUri public https URI returned by Transcribe
     * @return the raw transcript text (or null)
     * @throws Exception on network/parse errors
     */
    public String fetchTranscriptTextFromUri(String transcriptUri) throws Exception {
        // transcriptUri is usually an https url to a JSON file (may be an S3 presigned link)
        URL url = new URL(transcriptUri);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String json = sb.toString();

            JsonNode root = objectMapper.readTree(json);
            // For Transcribe output, the transcript text is often at results.transcripts[0].transcript
            JsonNode results = root.path("results");
            JsonNode transcripts = results.path("transcripts");
            if (transcripts.isArray() && transcripts.size() > 0) {
                String transcriptText = transcripts.get(0).path("transcript").asText(null);
                return transcriptText;
            } else {
                // fallback: try to parse other structure
                return null;
            }
        } finally {
            conn.disconnect();
        }
    }

    private String getMediaFormatFromKey(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".mp3")) return "mp3";
        if (lower.endsWith(".mp4")) return "mp4";
        if (lower.endsWith(".wav")) return "wav";
        if (lower.endsWith(".flac")) return "flac";
        if (lower.endsWith(".m4a")) return "m4a";
        // default
        return "mp3";
    }
}
