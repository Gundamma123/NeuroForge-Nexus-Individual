// package com.neuroforge.nexus.controller;

// import com.neuroforge.nexus.entity.ProjectGithubRepository;
// import com.neuroforge.nexus.service.GithubApiService;
// import com.neuroforge.nexus.service.GithubRepoLinkService;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// import java.util.List;
// import java.util.Map;

// @RestController
// @RequestMapping("/github/projects/{projectId}/browse")
// public class GithubBrowseController {

//     private static final String LOAD_ERROR =
//             "Could not load this location from GitHub. It may not exist, or the branch or path name isn't supported.";

//     private final GithubRepoLinkService linkService;
//     private final GithubApiService githubApiService;

//     public GithubBrowseController(GithubRepoLinkService linkService, GithubApiService githubApiService) {
//         this.linkService = linkService;
//         this.githubApiService = githubApiService;
//     }

//     public static class BrowseException extends RuntimeException {
//         private final HttpStatus status;

//         public BrowseException(HttpStatus status, String message) {
//             super(message);
//             this.status = status;
//         }

//         public HttpStatus getStatus() {
//             return status;
//         }
//     }

//     @ExceptionHandler(BrowseException.class)
//     public ResponseEntity<Map<String, String>> onBrowseError(BrowseException e) {
//         return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
//     }

//     private record Target(ProjectGithubRepository link, String token) {}

//     // Reads use the linking account's token (same as the Pull Requests / Issues / Commits tabs).
//     // owner/repo always come from the database, never from the request.
//     private Target target(Long projectId) {
//         ProjectGithubRepository link = linkService.findLink(projectId)
//                 .orElseThrow(() -> new BrowseException(HttpStatus.NOT_FOUND, "No repository is linked to this project."));
//         String token = githubApiService.tokenFor(link.getLinkedByEmail());
//         if (token == null) {
//             throw new BrowseException(HttpStatus.CONFLICT,
//                     "The GitHub account that linked this repository is disconnected. Reconnect it, or link the repository again.");
//         }
//         return new Target(link, token);
//     }

//     @GetMapping("/branches")
//     public ResponseEntity<Map<String, Object>> branches(@PathVariable Long projectId) {
//         Target t = target(projectId);
//         Map<String, Object> result = githubApiService.getBranches(
//                 t.token(), t.link().getRepoOwner(), t.link().getRepoName());
//         if (result == null) throw new BrowseException(HttpStatus.BAD_GATEWAY, "Could not load branches from GitHub.");
//         return ResponseEntity.ok(result);
//     }

//     @GetMapping("/tree")
//     public ResponseEntity<List<Map<String, Object>>> tree(@PathVariable Long projectId,
//                                                           @RequestParam String ref,
//                                                           @RequestParam(defaultValue = "") String path) {
//         Target t = target(projectId);
//         String clean = path.trim().replaceAll("^/+|/+$", "");
//         List<Map<String, Object>> result = githubApiService.getTree(
//                 t.token(), t.link().getRepoOwner(), t.link().getRepoName(), ref, clean);
//         if (result == null) throw new BrowseException(HttpStatus.BAD_GATEWAY, LOAD_ERROR);
//         return ResponseEntity.ok(result);
//     }

//     @GetMapping("/file")
//     public ResponseEntity<Map<String, Object>> file(@PathVariable Long projectId,
//                                                     @RequestParam String ref,
//                                                     @RequestParam String path) {
//         Target t = target(projectId);
//         String clean = path.trim().replaceAll("^/+|/+$", "");
//         Map<String, Object> result = githubApiService.getFile(
//                 t.token(), t.link().getRepoOwner(), t.link().getRepoName(), ref, clean);
//         if (result == null) throw new BrowseException(HttpStatus.BAD_GATEWAY, LOAD_ERROR);
//         return ResponseEntity.ok(result);
//     }

//     @GetMapping("/readme")
//     public ResponseEntity<Map<String, Object>> readme(@PathVariable Long projectId,
//                                                       @RequestParam(required = false) String ref) {
//         Target t = target(projectId);
//         Map<String, Object> result = githubApiService.getReadme(
//                 t.token(), t.link().getRepoOwner(), t.link().getRepoName(), ref);
//         if (result == null) throw new BrowseException(HttpStatus.BAD_GATEWAY, LOAD_ERROR);
//         return ResponseEntity.ok(result);
//     }
// }


package com.neuroforge.nexus.controller;

import com.neuroforge.nexus.entity.ProjectGithubRepository;
import com.neuroforge.nexus.service.GithubApiService;
import com.neuroforge.nexus.service.GithubRepoLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/github/projects/{projectId}/browse")
public class GithubBrowseController {

    private static final String LOAD_ERROR =
            "Could not load this location from GitHub. It may not exist, or the branch or path name isn't supported.";

    private final GithubRepoLinkService linkService;
    private final GithubApiService githubApiService;

    public GithubBrowseController(GithubRepoLinkService linkService, GithubApiService githubApiService) {
        this.linkService = linkService;
        this.githubApiService = githubApiService;
    }

    public static class BrowseException extends RuntimeException {
        private final HttpStatus status;

        public BrowseException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    @ExceptionHandler(BrowseException.class)
    public ResponseEntity<Map<String, String>> onBrowseError(BrowseException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    private record Target(ProjectGithubRepository link, String token) {}

    // Reads use the linking account's token (same as the Pull Requests / Issues / Commits tabs).
    // owner/repo always come from the database, never from the request.
    private Target target(Long projectId) {
        ProjectGithubRepository link = linkService.findLink(projectId)
                .orElseThrow(() -> new BrowseException(HttpStatus.NOT_FOUND, "No repository is linked to this project."));
        String token = githubApiService.tokenFor(link.getLinkedByEmail());
        if (token == null) {
            throw new BrowseException(HttpStatus.CONFLICT,
                    "The GitHub account that linked this repository is disconnected. Reconnect it, or link the repository again.");
        }
        return new Target(link, token);
    }

    // If the repository itself is unreachable, report why; otherwise the problem
    // is the specific branch or path, so use the fallback message.
    private BrowseException failure(Target t, String fallback) {
        String reason = githubApiService.diagnose(t.token(), t.link().getRepoOwner(), t.link().getRepoName());
        return new BrowseException(HttpStatus.BAD_GATEWAY, reason != null ? reason : fallback);
    }

    @GetMapping("/branches")
    public ResponseEntity<Map<String, Object>> branches(@PathVariable Long projectId) {
        Target t = target(projectId);
        Map<String, Object> result = githubApiService.getBranches(
                t.token(), t.link().getRepoOwner(), t.link().getRepoName());
        if (result == null) throw failure(t, "Could not load branches from GitHub.");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tree")
    public ResponseEntity<List<Map<String, Object>>> tree(@PathVariable Long projectId,
                                                          @RequestParam String ref,
                                                          @RequestParam(defaultValue = "") String path) {
        Target t = target(projectId);
        String clean = path.trim().replaceAll("^/+|/+$", "");
        List<Map<String, Object>> result = githubApiService.getTree(
                t.token(), t.link().getRepoOwner(), t.link().getRepoName(), ref, clean);
        if (result == null) throw failure(t, LOAD_ERROR);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/file")
    public ResponseEntity<Map<String, Object>> file(@PathVariable Long projectId,
                                                    @RequestParam String ref,
                                                    @RequestParam String path) {
        Target t = target(projectId);
        String clean = path.trim().replaceAll("^/+|/+$", "");
        Map<String, Object> result = githubApiService.getFile(
                t.token(), t.link().getRepoOwner(), t.link().getRepoName(), ref, clean);
        if (result == null) throw failure(t, LOAD_ERROR);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/readme")
    public ResponseEntity<Map<String, Object>> readme(@PathVariable Long projectId,
                                                      @RequestParam(required = false) String ref) {
        Target t = target(projectId);
        Map<String, Object> result = githubApiService.getReadme(
                t.token(), t.link().getRepoOwner(), t.link().getRepoName(), ref);
        if (result == null) throw failure(t, LOAD_ERROR);
        return ResponseEntity.ok(result);
    }
}