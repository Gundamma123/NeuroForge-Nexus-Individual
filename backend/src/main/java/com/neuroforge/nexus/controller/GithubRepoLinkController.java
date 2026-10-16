
// package com.neuroforge.nexus.controller;

// import com.neuroforge.nexus.service.GithubRepoLinkService;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// import java.util.LinkedHashMap;
// import java.util.Map;

// @RestController
// @RequestMapping("/github")
// public class GithubRepoLinkController {

//     private final GithubRepoLinkService linkService;

//     public GithubRepoLinkController(GithubRepoLinkService linkService) {
//         this.linkService = linkService;
//     }

//     @GetMapping("/projects/{projectId}/repo")
//     public ResponseEntity<?> getLink(@PathVariable Long projectId) {
//         return linkService.findLink(projectId)
//                 .<ResponseEntity<?>>map(link -> {
//                     Map<String, Object> result = new LinkedHashMap<>();
//                     result.put("id", link.getId());
//                     result.put("repoOwner", link.getRepoOwner());
//                     result.put("repoName", link.getRepoName());
//                     result.put("syncActive", link.isSyncActive());
//                     result.put("linkedBy", link.getLinkedByEmail());
//                     return ResponseEntity.ok(result);
//                 })
//                 .orElseGet(() -> ResponseEntity.noContent().build());
//     }
// }

package com.neuroforge.nexus.controller;

import com.neuroforge.nexus.dto.GithubRepoLinkRequest;
import com.neuroforge.nexus.entity.ProjectGithubRepository;
import com.neuroforge.nexus.service.GithubRepoLinkService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/github")
public class GithubRepoLinkController {

    private final GithubRepoLinkService linkService;

    public GithubRepoLinkController(GithubRepoLinkService linkService) {
        this.linkService = linkService;
    }

    private String email() {
        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        return auth != null ? auth.getName() : null;
    }

    private boolean isAdmin() {
        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private Map<String, Object> view(ProjectGithubRepository link) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", link.getId());
        result.put("repoOwner", link.getRepoOwner());
        result.put("repoName", link.getRepoName());
        result.put("syncActive", link.isSyncActive());
        result.put("linkedBy", link.getLinkedByEmail());
        result.put("canManage",
                linkService.canManage(link, email(), isAdmin()));
        return result;
    }

    @GetMapping("/projects/{projectId}/repo")
    public ResponseEntity<?> getLink(@PathVariable Long projectId) {
        return linkService.findLink(projectId)
                .<ResponseEntity<?>>map(link ->
                        ResponseEntity.ok(view(link)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/projects/{projectId}/repo")
    public ResponseEntity<?> linkRepo(
            @PathVariable Long projectId,
            @Valid @RequestBody GithubRepoLinkRequest request) {

        Optional<ProjectGithubRepository> existing =
                linkService.findLink(projectId);

        if (existing.isPresent()
                && !linkService.canManage(
                        existing.get(), email(), isAdmin())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "You cannot change this repository link."));
        }

        ProjectGithubRepository saved =
                linkService.linkRepo(projectId, request);

        return ResponseEntity.ok(view(saved));
    }

    @DeleteMapping("/projects/{projectId}/repo")
    public ResponseEntity<?> unlinkRepo(
            @PathVariable Long projectId) {

        Optional<ProjectGithubRepository> existing =
                linkService.findLink(projectId);

        if (existing.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        

        if (!linkService.canManage(
                existing.get(), email(), isAdmin())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "You cannot unlink this repository."));
        }

        linkService.unlinkRepo(projectId);
        return ResponseEntity.noContent().build();
    }
}

