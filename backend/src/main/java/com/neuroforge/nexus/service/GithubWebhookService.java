package com.neuroforge.nexus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroforge.nexus.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GithubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookService.class);

    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${github.app.webhook-secret}")
    private String webhookSecret;

    // Matches patterns like: "closes TASK-5", "fixes task-12", "Closes NF-8"
    private static final Pattern ISSUE_KEY_PATTERN =
            Pattern.compile("(?i)(closes|fixes|resolves)\\s+(?:task|nf)-(\\d+)");

    public GithubWebhookService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // Verifies the payload was genuinely sent by GitHub, using the shared
    // webhook secret and HMAC-SHA256 — exactly how GitHub signs every event.
    public boolean verifySignature(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            String expectedSignature = signatureHeader.substring("sha256=".length());

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return constantTimeEquals(hexString.toString(), expectedSignature);
        } catch (Exception e) {
            return false;
        }
    }

    // Prevents timing-attack signature guessing by comparing every
    // character regardless of where a mismatch occurs.
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public void processEvent(String eventType, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            if ("pull_request".equals(eventType)) {
                handlePullRequestEvent(root);
            } else if ("issues".equals(eventType)) {
                handleIssueEvent(root);
            }
            // Other event types (push, ping, etc.) are safely ignored.
        } catch (Exception e) {
            // Never let a malformed/unexpected payload crash the webhook endpoint.
            log.error("Failed to process GitHub webhook event type={}", eventType, e);
        }
    }

    private void handlePullRequestEvent(JsonNode root) {
        JsonNode pr = root.path("pull_request");
        String action = root.path("action").asText("");
        String prUrl = pr.path("html_url").asText(null);
        String prBody = pr.path("body").asText("");
        String prTitle = pr.path("title").asText("");

        String combinedText = prTitle + " " + prBody;
        List<Long> matchedTaskIds = extractTaskIds(combinedText);

        for (Long taskId : matchedTaskIds) {
            taskRepository.findById(taskId).ifPresent(task -> {
                task.setGithubPrUrl(prUrl);
                task.setLastGithubSyncTimestamp(LocalDateTime.now());

                // "opened" -> move to In Review; "closed" + merged -> move to Done
                if ("opened".equals(action)) {
                    task.setStatus("In Review");
                } else if ("closed".equals(action) && pr.path("merged").asBoolean(false)) {
                    task.setStatus("Done");
                }

                taskRepository.save(task);
            });
        }
    }

    private void handleIssueEvent(JsonNode root) {
        JsonNode issue = root.path("issue");
        long issueId = issue.path("id").asLong();
        int issueNumber = issue.path("number").asInt();
        String action = root.path("action").asText("");

        // Only meaningful if this issue was already linked to a NeuroForge task
        // via githubIssueId (that linking happens in Phase D, outbound sync).
        taskRepository.findAll().stream()
                .filter(t -> t.getGithubIssueId() != null && t.getGithubIssueId() == issueId)
                .findFirst()
                .ifPresent(task -> {
                    task.setGithubIssueNumber(issueNumber);
                    task.setLastGithubSyncTimestamp(LocalDateTime.now());

                    if ("closed".equals(action)) {
                        task.setStatus("Done");
                    }

                    taskRepository.save(task);
                });
    }

    // Scans commit/PR text for "closes TASK-5" style references and returns
    // the matching NeuroForge task IDs.
    private List<Long> extractTaskIds(String text) {
        Matcher matcher = ISSUE_KEY_PATTERN.matcher(text);
        java.util.List<Long> ids = new java.util.ArrayList<>();
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(2)));
        }
        return ids;
    }
}