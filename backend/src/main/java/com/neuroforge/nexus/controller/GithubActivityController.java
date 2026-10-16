package com.neuroforge.nexus.controller;

import com.neuroforge.nexus.entity.ProjectGithubRepository;
import com.neuroforge.nexus.service.GithubApiService;
import com.neuroforge.nexus.service.GithubRepoLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

// Serves GET /api/github/projects/{projectId}/activity/{section}
// Sections: commits, pulls, issues, labels, runs, deployments, releases
@RestController
@RequestMapping("/github/projects/{projectId}/activity")
public class GithubActivityController {

    private static final Set<String> SECTIONS =
            Set.of("commits", "pulls", "issues", "labels", "runs", "deployments", "releases");

    private final GithubRepoLinkService linkService;
    private final GithubApiService githubApiService;

    public GithubActivityController(GithubRepoLinkService linkService,
                                    GithubApiService githubApiService) {
        this.linkService = linkService;
        this.githubApiService = githubApiService;
    }

    @GetMapping("/{section}")
    public ResponseEntity<?> activity(@PathVariable Long projectId,
                                      @PathVariable String section,
                                      Authentication authentication) {

        if (!SECTIONS.contains(section)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Unknown activity section: " + section));
        }

        ProjectGithubRepository link = linkService.getLink(projectId);
        if (link == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No repository is linked to this project."));
        }

        // 1) Token of the logged-in user, 2) fall back to the user who linked the repo
        String token = null;
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            token = githubApiService.tokenFor(authentication.getName());
        }
        if ((token == null || token.isBlank()) && link.getLinkedBy() != null) {
            token = githubApiService.tokenFor(link.getLinkedBy().getEmail());
        }
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "No connected GitHub account is available. "
                            + "Connect GitHub, then try again."));
        }

        List<Map<String, Object>> rows = githubApiService.getSection(
                token, link.getRepoOwner(), link.getRepoName(), section);

        // if (rows == null) {
        //     return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        //             .body(Map.of("message",
        //                     "Could not load " + section + " from GitHub. The repository may be "
        //                     + "unreachable, or the GitHub App may not have permission for this data."));
        // }

        if (rows == null) {
            String reason = githubApiService.diagnose(token, link.getRepoOwner(), link.getRepoName());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message",
                            reason != null ? reason : "GitHub could not return " + section + " for this repository."));
        }

        return ResponseEntity.ok(rows);
    }
}