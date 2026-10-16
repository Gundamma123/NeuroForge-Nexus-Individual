package com.neuroforge.nexus.service;

import com.neuroforge.nexus.entity.ProjectGithubRepository;
import com.neuroforge.nexus.entity.Task;
import com.neuroforge.nexus.repository.ProjectGithubRepositoryRepository;
import com.neuroforge.nexus.repository.TaskRepository;
import com.neuroforge.nexus.repository.UserRepository;
import com.neuroforge.nexus.entity.User;
import com.neuroforge.nexus.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GithubSyncService {

    private static final Logger log = LoggerFactory.getLogger(GithubSyncService.class);

    private final ProjectGithubRepositoryRepository repoLinkRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final EncryptionUtil encryptionUtil;
    // private final RestTemplate restTemplate = new RestTemplate();
    private final RestTemplate restTemplate =
        new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());

    public GithubSyncService(ProjectGithubRepositoryRepository repoLinkRepository,
                              UserRepository userRepository,
                              TaskRepository taskRepository,
                              EncryptionUtil encryptionUtil) {
        this.repoLinkRepository = repoLinkRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.encryptionUtil = encryptionUtil;
    }

    // Call this whenever a task is created, or its status transitions.
    // Non-blocking-safe: never throws — a sync failure never breaks the
    // caller's task create/update flow.
    public void syncTaskToGithub(Task task) {
        try {
            if (task.getSprint() == null || task.getSprint().getProject() == null) return;

            Long projectId = task.getSprint().getProject().getId();
            Optional<ProjectGithubRepository> linkOpt = repoLinkRepository.findByProjectId(projectId);
            if (linkOpt.isEmpty() || !linkOpt.get().isSyncActive()) return;

            ProjectGithubRepository link = linkOpt.get();
            String token = resolveAccessToken();
            if (token == null) return;

            if (task.getGithubIssueId() == null) {
                createGithubIssue(task, link, token);
            } else {
                updateGithubIssue(task, link, token);
            }
        } catch (Exception e) {
            // Sync is best-effort — log and move on, never propagate.
            log.error("GitHub sync failed for task id={}", task.getId(), e);
        }
    }

    // For now, uses the first connected user's token found. In a
    // multi-user setup, tie this to the project owner or a per-project
    // configured connector instead.
    private String resolveAccessToken() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .filter(u -> u.getGithubAccessToken() != null)
                .findFirst()
                .map(u -> encryptionUtil.decrypt(u.getGithubAccessToken()))
                .orElse(null);
    }

    private void createGithubIssue(Task task, ProjectGithubRepository link, String token) {
        String url = String.format("https://api.github.com/repos/%s/%s/issues",
                link.getRepoOwner(), link.getRepoName());

        Map<String, Object> body = Map.of(
                "title", task.getTitle(),
                "body", buildIssueBody(task)
        );

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, buildRequest(body, token),
            new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response.getBody() != null) {
            Object id = response.getBody().get("id");
            Object number = response.getBody().get("number");
            Object htmlUrl = response.getBody().get("html_url");

            if (id != null) task.setGithubIssueId(Long.valueOf(id.toString()));
            if (number != null) task.setGithubIssueNumber(Integer.valueOf(number.toString()));
            if (htmlUrl != null) task.setGithubPrUrl(htmlUrl.toString());
            task.setLastGithubSyncTimestamp(LocalDateTime.now());
            taskRepository.save(task);
        }
    }

    private void updateGithubIssue(Task task, ProjectGithubRepository link, String token) {
        String url = String.format("https://api.github.com/repos/%s/%s/issues/%d",
                link.getRepoOwner(), link.getRepoName(), task.getGithubIssueNumber());

        String githubState = "Done".equals(task.getStatus()) ? "closed" : "open";

        Map<String, Object> body = Map.of(
                "title", task.getTitle(),
                "body", buildIssueBody(task),
                "state", githubState
        );

        restTemplate.exchange(url, HttpMethod.PATCH, buildRequest(body, token), Map.class);

        task.setLastGithubSyncTimestamp(LocalDateTime.now());
        taskRepository.save(task);
    }

    private String buildIssueBody(Task task) {
        return String.format(
                "**NeuroForge Task**%n%nPriority: %s%nStatus: %s%nStory Points: %s%n%nSynced automatically from NeuroForge Nexus (Task ID: %d).",
                task.getPriority(), task.getStatus(),
                task.getStoryPoints() != null ? task.getStoryPoints() : 0,
                task.getId()
        );
    }

    private HttpEntity<Map<String, Object>> buildRequest(Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github+json");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}