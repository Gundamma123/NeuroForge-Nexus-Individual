// package com.neuroforge.nexus.service;

// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.neuroforge.nexus.entity.User;
// import com.neuroforge.nexus.repository.UserRepository;
// import com.neuroforge.nexus.util.EncryptionUtil;
// import org.springframework.http.HttpEntity;
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.HttpMethod;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.http.client.SimpleClientHttpRequestFactory;
// import org.springframework.stereotype.Service;
// import org.springframework.web.client.HttpClientErrorException;
// import org.springframework.web.client.RestTemplate;

// import java.util.ArrayList;
// import java.util.LinkedHashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.regex.Pattern;

// @Service
// public class GithubApiService {

//     private static final String API = "https://api.github.com";
//     private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_.-]{1,100}$");
//     private static final JsonNode EMPTY = new ObjectMapper().createArrayNode();

//     private final UserRepository userRepository;
//     private final EncryptionUtil encryptionUtil;
//     private final RestTemplate restTemplate;

//     public GithubApiService(UserRepository userRepository, EncryptionUtil encryptionUtil) {
//         this.userRepository = userRepository;
//         this.encryptionUtil = encryptionUtil;

//         SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
//         factory.setConnectTimeout(5000);
//         factory.setReadTimeout(10000);
//         this.restTemplate = new RestTemplate(factory);
//     }

//     // Decrypted GitHub token of a NeuroForge user, or null if not connected.
//     public String tokenFor(String email) {
//         if (email == null) return null;
//         try {
//             return userRepository.findByEmail(email)
//                     .map(User::getGithubAccessToken)
//                     .filter(t -> !t.isBlank())
//                     .map(encryptionUtil::decrypt)
//                     .orElse(null);
//         } catch (Exception e) {
//             return null;
//         }
//     }

//     // Owner/repo end up inside a URL path, so only plain GitHub-style names are allowed.
//     private boolean safe(String s) {
//         return s != null && SAFE.matcher(s).matches() && !s.matches("\\.+");
//     }

//     private JsonNode fetch(String token, String path) {
//         HttpHeaders headers = new HttpHeaders();
//         headers.set("Authorization", "Bearer " + token);
//         headers.set("Accept", "application/vnd.github+json");
//         headers.set("X-GitHub-Api-Version", "2022-11-28");
//         try {
//             return restTemplate.exchange(API + path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
//                     .getBody();
//         } catch (HttpClientErrorException e) {
//             int code = e.getStatusCode().value();
//             // 409 = empty repository, 410 = feature disabled -> nothing to show, not an error
//             return (code == 409 || code == 410) ? EMPTY : null;
//         } catch (Exception e) {
//             return null;
//         }
//     }

//     private Map<String, Object> row(Object... kv) {
//         Map<String, Object> m = new LinkedHashMap<>();
//         for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
//         return m;
//     }

//     // First 100 repositories the account can access, most recently pushed first.
//     public List<Map<String, Object>> listRepos(String token) {
//         List<Map<String, Object>> out = new ArrayList<>();
//         JsonNode root = fetch(token, "/user/repos?per_page=100&sort=pushed");
//         if (root == null || !root.isArray()) return out;
//         for (JsonNode r : root) {
//             out.add(row(
//                     "owner", r.path("owner").path("login").asText(""),
//                     "name", r.path("name").asText(""),
//                     "fullName", r.path("full_name").asText(""),
//                     "isPrivate", r.path("private").asBoolean(false)));
//         }
//         return out;
//     }

//     public boolean repoAccessible(String token, String owner, String repo) {
        
//         if (!safe(owner) || !safe(repo)) return false;
//         JsonNode r = fetch(token, "/repos/" + owner + "/" + repo);
//         return r != null && r.hasNonNull("full_name");
//     }
//     // Add this method to the existing class:
//     public String lastCommitDate(String token, String owner, String repo, String githubUsername) {
//     if (!safe(owner) || !safe(repo) || githubUsername == null || githubUsername.isBlank()) return null;
//     JsonNode arr = fetch(token, "/repos/" + owner + "/" + repo + "/commits?author="
//             + githubUsername + "&per_page=1");
//     if (arr == null || !arr.isArray() || arr.size() == 0) return null;
//     return arr.get(0).path("commit").path("author").path("date").asText(null);
//    }




//     // Returns null when GitHub could not be reached or the token lost access.
//     public List<Map<String, Object>> getSection(String token, String owner, String repo, String section) {
//         if (!safe(owner) || !safe(repo)) return null;
//         String base = "/repos/" + owner + "/" + repo;
//         List<Map<String, Object>> out = new ArrayList<>();

//         switch (section) {
//             case "commits" -> {
//                 JsonNode arr = fetch(token, base + "/commits?per_page=30");
//                 if (arr == null || !arr.isArray()) return null;
//                 for (JsonNode c : arr) {
//                     String msg = c.path("commit").path("message").asText("");
//                     int nl = msg.indexOf('\n');
//                     if (nl >= 0) msg = msg.substring(0, nl);
//                     String sha = c.path("sha").asText("");
//                     String author = c.path("commit").path("author").path("name")
//                             .asText(c.path("author").path("login").asText(""));
//                     out.add(row(
//                             "sha", sha.length() > 7 ? sha.substring(0, 7) : sha,
//                             "message", msg,
//                             "author", author,
//                             "date", c.path("commit").path("author").path("date").asText(""),
//                             "url", c.path("html_url").asText("")));
//                 }
//             }
            
//       case "pulls" -> {
//     JsonNode arr = fetch(token, base + "/pulls?state=all&per_page=30");
//     if (arr == null || !arr.isArray()) return null;

//     for (JsonNode p : arr) {
//         String state = p.hasNonNull("merged_at")
//                 ? "merged"
//                 : p.path("state").asText("");

//         out.add(row(
//                 "number", p.path("number").asInt(0),
//                 "title", p.path("title").asText(""),
//                 "state", state,
//                 "draft", p.path("draft").asBoolean(false),
//                 "headSha", p.path("head").path("sha").asText(""),
//                 "author", p.path("user").path("login").asText(""),
//                 "date", p.path("updated_at").asText(""),
//                 "url", p.path("html_url").asText("")));
//     }
//   }
//             case "issues" -> {
//                 JsonNode arr = fetch(token, base + "/issues?state=all&per_page=30");
//                 if (arr == null || !arr.isArray()) return null;
//                 for (JsonNode i : arr) {
//                     if (i.has("pull_request")) continue; // this endpoint also returns PRs
//                     out.add(row(
//                             "number", i.path("number").asInt(0),
//                             "title", i.path("title").asText(""),
//                             "state", i.path("state").asText(""),
//                             "author", i.path("user").path("login").asText(""),
//                             "date", i.path("updated_at").asText(""),
//                             "url", i.path("html_url").asText("")));
//                 }
//             }
//             case "runs" -> {

//                 JsonNode root = fetch(token, base + "/actions/runs?per_page=20");
//                 if (root == null) return null;
//                 JsonNode arr = root.path("workflow_runs");
//                 if (!arr.isArray()) return out;
//                 for (JsonNode r : arr) {
//                     out.add(row(
//                             "id", r.path("id").asLong(0),
//                             "name", r.path("name").asText(r.path("display_title").asText("Workflow run")),
//                             "status", r.path("status").asText(""),         // queued | in_progress | completed
//                             "conclusion", r.path("conclusion").asText(""), // success | failure | cancelled | null
//                             "branch", r.path("head_branch").asText(""),
//                             "actor", r.path("actor").path("login").asText(""),
//                             "createdAt", r.path("created_at").asText(""),
//                             "url", r.path("html_url").asText("")));
//                 }
//             }
//             case "deployments" -> {
//     JsonNode arr = fetch(token, base + "/deployments?per_page=20");
//     if (arr == null || !arr.isArray()) return out;
//     for (JsonNode d : arr) {
//         long id = d.path("id").asLong(0);
//         JsonNode statusArr = fetch(token, base + "/deployments/" + id + "/statuses?per_page=1");
//         String state = (statusArr != null && statusArr.isArray() && statusArr.size() > 0)
//                 ? statusArr.get(0).path("state").asText("pending")
//                 : "pending";
//         out.add(row(
//                 "id", id,
//                 "environment", d.path("environment").asText("production"),
//                 "ref", d.path("ref").asText(""),
//                 "state", state, // pending | success | failure | error | in_progress
//                 "creator", d.path("creator").path("login").asText(""),
//                 "createdAt", d.path("created_at").asText("")));
//     }
//  }
//      case "releases" -> {
//     JsonNode arr = fetch(token, base + "/releases?per_page=20");
//     if (arr == null || !arr.isArray()) return out;
//     for (JsonNode r : arr) {
//         out.add(row(
//                 "id", r.path("id").asLong(0),
//                 "tag", r.path("tag_name").asText(""),
//                 "name", r.path("name").asText(r.path("tag_name").asText("")),
//                 "draft", r.path("draft").asBoolean(false),
//                 "prerelease", r.path("prerelease").asBoolean(false),
//                 "publishedAt", r.path("published_at").asText(""),
//                 "author", r.path("author").path("login").asText(""),
//                 "url", r.path("html_url").asText("")));
//     }
//  }

//             default -> {
//                 return null;
//             }
//         }
//         return out;
//     }
// // Merges a pull request using the ACTING user's token. Never throws:
// // returns {merged: boolean, message: String}.
// public Map<String, Object> mergePullRequest(String token, String owner, String repo,
//                                              int number, String method, String sha) {
//     Map<String, Object> result = new LinkedHashMap<>();

//     if (!safe(owner) || !safe(repo) || number <= 0) {
//         result.put("merged", false);
//         result.put("message", "Invalid pull request.");
//         return result;
//     }

//     String mergeMethod = "merge".equals(method) || "squash".equals(method) || "rebase".equals(method)
//             ? method
//             : "merge";

//     Map<String, String> body = new LinkedHashMap<>();
//     body.put("merge_method", mergeMethod);
//     if (sha != null && !sha.isBlank()) body.put("sha", sha); // fails with 409 if the PR changed

//     HttpHeaders headers = new HttpHeaders();
//     headers.set("Authorization", "Bearer " + token);
//     headers.set("Accept", "application/vnd.github+json");
//     headers.set("X-GitHub-Api-Version", "2022-11-28");
//     headers.setContentType(MediaType.APPLICATION_JSON);

//     try {
//         ResponseEntity<JsonNode> res = restTemplate.exchange(
//                 API + "/repos/" + owner + "/" + repo + "/pulls/" + number + "/merge",
//                 HttpMethod.PUT, new HttpEntity<>(body, headers), JsonNode.class);
//         JsonNode b = res.getBody();
//         boolean merged = b != null && b.path("merged").asBoolean(false);
//         result.put("merged", merged);
//         result.put("message", merged ? "Pull request #" + number + " merged."
//                 : (b != null ? b.path("message").asText("Merge was not completed.") : "Merge was not completed."));
//     } catch (HttpClientErrorException e) {
//         String msg = switch (e.getStatusCode().value()) {
//             case 403 -> "Your GitHub account doesn't have permission to merge this pull request.";
//             case 404 -> "Pull request not found, or your GitHub account can't access this repository.";
//             case 405 -> "This pull request can't be merged right now (conflicts, failing required checks, or it is a draft).";
//             case 409 -> "The pull request changed while you were viewing it. Refresh and try again.";
//             case 422 -> "GitHub rejected the merge request.";
//             default -> "GitHub could not merge this pull request.";
//         };
//         result.put("merged", false);
//         result.put("message", msg);
//     } catch (Exception e) {
//         result.put("merged", false);
//         result.put("message", "Could not reach GitHub. Please try again.");
//     }
//     return result;
// }




// }
package com.neuroforge.nexus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroforge.nexus.entity.User;
import com.neuroforge.nexus.repository.UserRepository;
import com.neuroforge.nexus.util.EncryptionUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GithubApiService {

    private static final String API = "https://api.github.com";
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_.-]{1,100}$");
    private static final Pattern SAFE_REF = Pattern.compile("^[A-Za-z0-9._/-]{1,200}$");
    private static final Pattern SAFE_PATH = Pattern.compile("^[A-Za-z0-9._ @+()/-]{0,300}$");
    private static final long MAX_PREVIEW_BYTES = 200_000L;
    private static final int MAX_PATCH_CHARS = 20_000;
    private static final JsonNode EMPTY = new ObjectMapper().createArrayNode();

    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;
    private final RestTemplate restTemplate;

    public GithubApiService(UserRepository userRepository, EncryptionUtil encryptionUtil) {
        this.userRepository = userRepository;
        this.encryptionUtil = encryptionUtil;

        // The JDK HTTP client supports PATCH (the default HttpURLConnection-based client does not).
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(factory);
    }

    // ---------- token + validation helpers ----------

    // Decrypted GitHub token of a NeuroForge user, or null if not connected.
    public String tokenFor(String email) {
        if (email == null) return null;
        try {
            return userRepository.findByEmail(email)
                    .map(User::getGithubAccessToken)
                    .filter(t -> !t.isBlank())
                    .map(encryptionUtil::decrypt)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // Owner/repo end up inside a URL path, so only plain GitHub-style names are allowed.
    private boolean safe(String s) {
        return s != null && SAFE.matcher(s).matches() && !s.matches("\\.+");
    }

    private boolean safeRef(String ref) {
        return ref != null && SAFE_REF.matcher(ref).matches()
                && !ref.contains("..") && !ref.startsWith("/") && !ref.endsWith("/");
    }

    // "" means the repository root. No empty, "." or ".." segments are accepted.
    private boolean safePath(String path) {
        if (path == null) return false;
        if (path.isEmpty()) return true;
        if (!SAFE_PATH.matcher(path).matches()) return false;
        for (String seg : path.split("/", -1)) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) return false;
        }
        return true;
    }

    private JsonNode fetch(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        try {
            return restTemplate.exchange(API + path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
                    .getBody();
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            // 409 = empty repository, 410 = feature disabled -> nothing to show, not an error
            return (code == 409 || code == 410) ? EMPTY : null;
        } catch (Exception e) {
            return null;
        }
    }

    private HttpHeaders writeHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    // ---------- repositories ----------

    // First 100 repositories the account can access, most recently pushed first.
    public List<Map<String, Object>> listRepos(String token) {
        List<Map<String, Object>> out = new ArrayList<>();
        JsonNode root = fetch(token, "/user/repos?per_page=100&sort=pushed");
        if (root == null || !root.isArray()) return out;
        for (JsonNode r : root) {
            out.add(row(
                    "owner", r.path("owner").path("login").asText(""),
                    "name", r.path("name").asText(""),
                    "fullName", r.path("full_name").asText(""),
                    "isPrivate", r.path("private").asBoolean(false)));
        }
        return out;
    }

    public boolean repoAccessible(String token, String owner, String repo) {
        if (!safe(owner) || !safe(repo)) return false;
        JsonNode r = fetch(token, "/repos/" + owner + "/" + repo);
        return r != null && r.hasNonNull("full_name");
    }

    // ---------- activity sections (read) ----------

    // Returns null when GitHub could not be reached or the token lost access.
    public List<Map<String, Object>> getSection(String token, String owner, String repo, String section) {
        if (!safe(owner) || !safe(repo)) return null;
        String base = "/repos/" + owner + "/" + repo;
        List<Map<String, Object>> out = new ArrayList<>();

        switch (section) {
            case "commits" -> {
                JsonNode arr = fetch(token, base + "/commits?per_page=30");
                if (arr == null || !arr.isArray()) return null;
                for (JsonNode c : arr) {
                    String msg = c.path("commit").path("message").asText("");
                    int nl = msg.indexOf('\n');
                    if (nl >= 0) msg = msg.substring(0, nl);
                    String sha = c.path("sha").asText("");
                    String author = c.path("commit").path("author").path("name")
                            .asText(c.path("author").path("login").asText(""));
                    out.add(row(
                            "sha", sha.length() > 7 ? sha.substring(0, 7) : sha,
                            "message", msg,
                            "author", author,
                            "date", c.path("commit").path("author").path("date").asText(""),
                            "url", c.path("html_url").asText("")));
                }
            }
            case "pulls" -> {
                JsonNode arr = fetch(token, base + "/pulls?state=all&per_page=30");
                if (arr == null || !arr.isArray()) return null;
                for (JsonNode p : arr) {
                    String state = p.hasNonNull("merged_at") ? "merged" : p.path("state").asText("");
                    out.add(row(
                            "number", p.path("number").asInt(0),
                            "title", p.path("title").asText(""),
                            "state", state,
                            "draft", p.path("draft").asBoolean(false),
                            "headSha", p.path("head").path("sha").asText(""),
                            "author", p.path("user").path("login").asText(""),
                            "date", p.path("updated_at").asText(""),
                            "url", p.path("html_url").asText("")));
                }
            }
            case "issues" -> {
                JsonNode arr = fetch(token, base + "/issues?state=all&per_page=30");
                if (arr == null || !arr.isArray()) return null;
                for (JsonNode i : arr) {
                    if (i.has("pull_request")) continue; // this endpoint also returns PRs
                    List<String> labels = new ArrayList<>();
                    for (JsonNode l : i.path("labels")) labels.add(l.path("name").asText(""));
                    out.add(row(
                            "number", i.path("number").asInt(0),
                            "title", i.path("title").asText(""),
                            "state", i.path("state").asText(""),
                            "labels", labels,
                            "author", i.path("user").path("login").asText(""),
                            "date", i.path("updated_at").asText(""),
                            "url", i.path("html_url").asText("")));
                }
            }
            case "labels" -> {
                JsonNode arr = fetch(token, base + "/labels?per_page=100");
                if (arr == null || !arr.isArray()) return null;
                for (JsonNode l : arr) {
                    out.add(row("name", l.path("name").asText(""), "color", l.path("color").asText("")));
                }
            }
            case "runs" -> {
                JsonNode root = fetch(token, base + "/actions/runs?per_page=20");
                if (root == null) return null;
                JsonNode arr = root.path("workflow_runs");
                if (!arr.isArray()) return out;
                for (JsonNode r : arr) {
                    out.add(row(
                            "id", r.path("id").asLong(0),
                            "name", r.path("name").asText(r.path("display_title").asText("Workflow run")),
                            "status", r.path("status").asText(""),         // queued | in_progress | completed
                            "conclusion", r.path("conclusion").asText(""), // success | failure | cancelled | ...
                            "branch", r.path("head_branch").asText(""),
                            "actor", r.path("actor").path("login").asText(""),
                            "createdAt", r.path("created_at").asText(""),
                            "url", r.path("html_url").asText("")));
                }
            }
            case "deployments" -> {
                JsonNode arr = fetch(token, base + "/deployments?per_page=20");
                if (arr == null) return null;
                if (!arr.isArray()) return out;
                for (JsonNode d : arr) {
                    long id = d.path("id").asLong(0);
                    if (id == 0) continue;
                    JsonNode statuses = fetch(token, base + "/deployments/" + id + "/statuses?per_page=1");
                    String state = (statuses != null && statuses.isArray() && statuses.size() > 0)
                            ? statuses.get(0).path("state").asText("pending")
                            : "pending";
                    out.add(row(
                            "id", id,
                            "environment", d.path("environment").asText("production"),
                            "ref", d.path("ref").asText(""),
                            "state", state,
                            "creator", d.path("creator").path("login").asText(""),
                            "createdAt", d.path("created_at").asText("")));
                }
            }
            case "releases" -> {
                JsonNode arr = fetch(token, base + "/releases?per_page=20");
                if (arr == null) return null;
                if (!arr.isArray()) return out;
                for (JsonNode r : arr) {
                    out.add(row(
                            "id", r.path("id").asLong(0),
                            "tag", r.path("tag_name").asText(""),
                            "name", r.path("name").asText(r.path("tag_name").asText("")),
                            "draft", r.path("draft").asBoolean(false),
                            "prerelease", r.path("prerelease").asBoolean(false),
                            "publishedAt", r.path("published_at").asText(""),
                            "author", r.path("author").path("login").asText(""),
                            "url", r.path("html_url").asText("")));
                }
            }
            default -> {
                return null;
            }
        }
        return out;
    }

    // Last commit date by a given GitHub user on the repository, or null.
    public String lastCommitDate(String token, String owner, String repo, String githubUsername) {
        if (!safe(owner) || !safe(repo) || !safe(githubUsername)) return null;
        JsonNode arr = fetch(token, "/repos/" + owner + "/" + repo + "/commits?author=" + githubUsername + "&per_page=1");
        if (arr == null || !arr.isArray() || arr.size() == 0) return null;
        return arr.get(0).path("commit").path("author").path("date").asText(null);
    }

    // Files changed, diff patches and review status of one pull request. null = failure.
    public Map<String, Object> getPullDetails(String token, String owner, String repo, int number) {
        if (!safe(owner) || !safe(repo) || number <= 0) return null;
        String base = "/repos/" + owner + "/" + repo + "/pulls/" + number;

        JsonNode pr = fetch(token, base);
        if (pr == null || !pr.hasNonNull("number")) return null;

        JsonNode filesArr = fetch(token, base + "/files?per_page=100");
        if (filesArr == null || !filesArr.isArray()) return null;

        List<Map<String, Object>> files = new ArrayList<>();
        for (JsonNode f : filesArr) {
            String patch = f.hasNonNull("patch") ? f.path("patch").asText() : null;
            boolean truncated = false;
            if (patch != null && patch.length() > MAX_PATCH_CHARS) {
                patch = patch.substring(0, MAX_PATCH_CHARS);
                truncated = true;
            }
            files.add(row(
                    "filename", f.path("filename").asText(""),
                    "status", f.path("status").asText(""),
                    "additions", f.path("additions").asInt(0),
                    "deletions", f.path("deletions").asInt(0),
                    "patch", patch,
                    "truncated", truncated));
        }

        // A reviewer's latest APPROVED / CHANGES_REQUESTED counts; plain comments don't override it.
        List<Map<String, Object>> reviews = new ArrayList<>();
        boolean changes = false;
        boolean approved = false;
        JsonNode reviewsArr = fetch(token, base + "/reviews?per_page=100");
        if (reviewsArr != null && reviewsArr.isArray()) {
            Map<String, String> latest = new LinkedHashMap<>();
            for (JsonNode rv : reviewsArr) {
                String login = rv.path("user").path("login").asText("");
                String state = rv.path("state").asText("");
                if (login.isEmpty()) continue;
                if ("APPROVED".equals(state) || "CHANGES_REQUESTED".equals(state)) {
                    latest.put(login, state);
                } else if ("DISMISSED".equals(state)) {
                    latest.remove(login);
                }
            }
            for (Map.Entry<String, String> e : latest.entrySet()) {
                reviews.add(row("reviewer", e.getKey(), "state", e.getValue().toLowerCase()));
                if ("CHANGES_REQUESTED".equals(e.getValue())) changes = true;
                if ("APPROVED".equals(e.getValue())) approved = true;
            }
        }

        int changedFiles = pr.path("changed_files").asInt(0);
        return row(
                "title", pr.path("title").asText(""),
                "state", pr.path("state").asText(""),
                "mergeableState", pr.path("mergeable_state").asText("unknown"),
                "changedFiles", changedFiles,
                "additions", pr.path("additions").asInt(0),
                "deletions", pr.path("deletions").asInt(0),
                "files", files,
                "filesTruncated", changedFiles > files.size(),
                "reviewSummary", changes ? "changes_requested" : (approved ? "approved" : "none"),
                "reviews", reviews);
    }

    // ---------- writes (always performed with the ACTING user's own token) ----------

    // Never throws: returns {merged: boolean, message: String}.
    public Map<String, Object> mergePullRequest(String token, String owner, String repo,
                                                 int number, String method, String sha) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!safe(owner) || !safe(repo) || number <= 0) {
            result.put("merged", false);
            result.put("message", "Invalid pull request.");
            return result;
        }

        // Set.of(...).contains(null) throws, so null-check first.
        String mergeMethod = (method != null && Set.of("merge", "squash", "rebase").contains(method))
                ? method : "merge";

        Map<String, String> body = new LinkedHashMap<>();
        body.put("merge_method", mergeMethod);
        if (sha != null && !sha.isBlank()) body.put("sha", sha); // 409 if the PR changed meanwhile

        try {
            ResponseEntity<JsonNode> res = restTemplate.exchange(
                    API + "/repos/" + owner + "/" + repo + "/pulls/" + number + "/merge",
                    HttpMethod.PUT, new HttpEntity<>(body, writeHeaders(token)), JsonNode.class);
            JsonNode b = res.getBody();
            boolean merged = b != null && b.path("merged").asBoolean(false);
            result.put("merged", merged);
            result.put("message", merged ? "Pull request #" + number + " merged."
                    : (b != null ? b.path("message").asText("Merge was not completed.") : "Merge was not completed."));
        } catch (HttpClientErrorException e) {
            String msg = switch (e.getStatusCode().value()) {
                case 403 -> "Your GitHub account doesn't have permission to merge this pull request.";
                case 404 -> "Pull request not found, or your GitHub account can't access this repository.";
                case 405 -> "This pull request can't be merged right now (conflicts, failing required checks, or it is a draft).";
                case 409 -> "The pull request changed while you were viewing it. Refresh and try again.";
                case 422 -> "GitHub rejected the merge request.";
                default -> "GitHub could not merge this pull request.";
            };
            result.put("merged", false);
            result.put("message", msg);
        } catch (Exception e) {
            result.put("merged", false);
            result.put("message", "Could not reach GitHub. Please try again.");
        }
        return result;
    }

    // Never throws: returns {created: boolean, message: String, number?, url?}.
    public Map<String, Object> createIssue(String token, String owner, String repo,
                                            String title, String body, List<String> labels) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!safe(owner) || !safe(repo)) {
            result.put("created", false);
            result.put("message", "Invalid repository.");
            return result;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        if (body != null && !body.isBlank()) payload.put("body", body);
        if (labels != null && !labels.isEmpty()) payload.put("labels", labels);

        try {
            ResponseEntity<JsonNode> res = restTemplate.exchange(
                    API + "/repos/" + owner + "/" + repo + "/issues",
                    HttpMethod.POST, new HttpEntity<>(payload, writeHeaders(token)), JsonNode.class);
            JsonNode b = res.getBody();
            boolean created = b != null && b.hasNonNull("number");
            result.put("created", created);
            if (created) {
                result.put("number", b.path("number").asInt(0));
                result.put("url", b.path("html_url").asText(""));
                result.put("message", "Issue #" + b.path("number").asInt(0) + " created.");
            } else {
                result.put("message", "GitHub did not create the issue.");
            }
        } catch (HttpClientErrorException e) {
            String msg = switch (e.getStatusCode().value()) {
                case 403 -> "Your GitHub account can't create issues in this repository.";
                case 404 -> "Repository not found, or your GitHub account can't access it.";
                case 410 -> "Issues are disabled for this repository.";
                case 422 -> "GitHub rejected the issue. Check the title and labels.";
                default -> "GitHub could not create the issue.";
            };
            result.put("created", false);
            result.put("message", msg);
        } catch (Exception e) {
            result.put("created", false);
            result.put("message", "Could not reach GitHub. Please try again.");
        }
        return result;
    }

    // Closes or reopens an issue. Never throws: returns {updated: boolean, message: String}.
    public Map<String, Object> setIssueState(String token, String owner, String repo, int number, String state) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!safe(owner) || !safe(repo) || number <= 0
                || !("open".equals(state) || "closed".equals(state))) {
            result.put("updated", false);
            result.put("message", "Invalid request.");
            return result;
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("state", state);

        try {
            restTemplate.exchange(
                    API + "/repos/" + owner + "/" + repo + "/issues/" + number,
                    HttpMethod.PATCH, new HttpEntity<>(body, writeHeaders(token)), JsonNode.class);
            result.put("updated", true);
            result.put("message", "Issue #" + number + ("closed".equals(state) ? " closed." : " reopened."));
        } catch (HttpClientErrorException e) {
            String msg = switch (e.getStatusCode().value()) {
                case 403 -> "Your GitHub account can't change issues in this repository.";
                case 404 -> "Issue not found, or your GitHub account can't access this repository.";
                case 410 -> "Issues are disabled for this repository.";
                case 422 -> "GitHub rejected the change.";
                default -> "GitHub could not update the issue.";
            };
            result.put("updated", false);
            result.put("message", msg);
        } catch (Exception e) {
            result.put("updated", false);
            result.put("message", "Could not reach GitHub. Please try again.");
        }
        return result;
    }

    // Re-runs a completed workflow run. Never throws: returns {rerun: boolean, message: String}.
    public Map<String, Object> rerunWorkflow(String token, String owner, String repo, long runId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!safe(owner) || !safe(repo) || runId <= 0) {
            result.put("rerun", false);
            result.put("message", "Invalid workflow run.");
            return result;
        }

        try {
            restTemplate.exchange(
                    API + "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/rerun",
                    HttpMethod.POST, new HttpEntity<>(writeHeaders(token)), JsonNode.class);
            result.put("rerun", true);
            result.put("message", "Workflow re-run requested. It will show as queued shortly.");
        } catch (HttpClientErrorException e) {
            String msg = switch (e.getStatusCode().value()) {
                case 403 -> "Your GitHub account can't re-run workflows here, or this run can't be re-run yet.";
                case 404 -> "Workflow run not found, or your GitHub account can't access this repository.";
                case 409, 422 -> "GitHub can't re-run this workflow run right now.";
                default -> "GitHub could not re-run the workflow.";
            };
            result.put("rerun", false);
            result.put("message", msg);
        } catch (Exception e) {
            result.put("rerun", false);
            result.put("message", "Could not reach GitHub. Please try again.");
        }
        return result;
    }

    // ---------- repository browser (read) ----------

    public Map<String, Object> getBranches(String token, String owner, String repo) {
        if (!safe(owner) || !safe(repo)) return null;
        JsonNode meta = fetch(token, "/repos/" + owner + "/" + repo);
        if (meta == null || !meta.hasNonNull("full_name")) return null;

        List<String> names = new ArrayList<>();
        JsonNode arr = fetch(token, "/repos/" + owner + "/" + repo + "/branches?per_page=100");
        if (arr != null && arr.isArray()) {
            for (JsonNode b : arr) names.add(b.path("name").asText(""));
        }
        return row("defaultBranch", meta.path("default_branch").asText(""), "branches", names);
    }

    // Folders first, then files, each alphabetical. null = invalid input or GitHub failure.
    public List<Map<String, Object>> getTree(String token, String owner, String repo, String ref, String path) {
        if (!safe(owner) || !safe(repo) || !safeRef(ref) || !safePath(path)) return null;
        String url = "/repos/" + owner + "/" + repo + "/contents"
                + (path.isEmpty() ? "" : "/" + path) + "?ref=" + ref;
        JsonNode node = fetch(token, url);
        if (node == null || !node.isArray()) return null;

        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode n : node) {
            out.add(row(
                    "name", n.path("name").asText(""),
                    "path", n.path("path").asText(""),
                    "type", n.path("type").asText(""),
                    "size", n.path("size").asLong(0)));
        }
        out.sort((a, b) -> {
            boolean aDir = "dir".equals(a.get("type"));
            boolean bDir = "dir".equals(b.get("type"));
            if (aDir != bDir) return aDir ? -1 : 1;
            return String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name")));
        });
        return out;
    }

    public Map<String, Object> getFile(String token, String owner, String repo, String ref, String path) {
        if (!safe(owner) || !safe(repo) || !safeRef(ref) || path == null || path.isEmpty() || !safePath(path)) {
            return null;
        }
        JsonNode node = fetch(token, "/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + ref);
        if (node == null || node.isArray()) return null;
        return fileView(node);
    }

    public Map<String, Object> getReadme(String token, String owner, String repo, String ref) {
        if (!safe(owner) || !safe(repo)) return null;
        boolean hasRef = ref != null && !ref.isBlank();
        if (hasRef && !safeRef(ref)) return null;

        JsonNode node = fetch(token, "/repos/" + owner + "/" + repo + "/readme" + (hasRef ? "?ref=" + ref : ""));
        if (node == null || node.isArray()) {
            return row("name", "", "content", null, "note", "No README found.");
        }
        return fileView(node);
    }

    private Map<String, Object> fileView(JsonNode node) {
        long size = node.path("size").asLong(0);
        Map<String, Object> out = row(
                "name", node.path("name").asText(""),
                "path", node.path("path").asText(""),
                "size", size,
                "htmlUrl", node.path("html_url").asText(""));

        if (size > MAX_PREVIEW_BYTES) {
            out.put("content", null);
            out.put("note", "This file is too large to preview. Open it on GitHub.");
            return out;
        }
        if (!"base64".equals(node.path("encoding").asText(""))) {
            out.put("content", null);
            out.put("note", "This file can't be previewed. Open it on GitHub.");
            return out;
        }
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(node.path("content").asText(""));
            if (looksBinary(bytes)) {
                out.put("content", null);
                out.put("note", "Binary file. Open it on GitHub to view.");
            } else {
                out.put("content", new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (IllegalArgumentException e) {
            out.put("content", null);
            out.put("note", "This file couldn't be decoded.");
        }
        return out;
    }

    private boolean looksBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, 8000);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }

    // ---------- repository settings (read; run with the ACTING user's token) ----------

    public Map<String, Object> getRepoGeneral(String token, String owner, String repo) {
        if (!safe(owner) || !safe(repo)) return null;
        JsonNode r = fetch(token, "/repos/" + owner + "/" + repo);
        if (r == null || !r.hasNonNull("full_name")) return null;

        List<String> topics = new ArrayList<>();
        for (JsonNode t : r.path("topics")) topics.add(t.asText(""));

        return row(
                "fullName", r.path("full_name").asText(""),
                "description", r.path("description").asText(""),
                "isPrivate", r.path("private").asBoolean(false),
                "defaultBranch", r.path("default_branch").asText(""),
                "archived", r.path("archived").asBoolean(false),
                "openIssues", r.path("open_issues_count").asInt(0),
                "topics", topics,
                "htmlUrl", r.path("html_url").asText(""));
    }

    // null = not visible to this account (GitHub requires push access to list collaborators)
    public List<Map<String, Object>> getCollaborators(String token, String owner, String repo) {
        if (!safe(owner) || !safe(repo)) return null;
        JsonNode arr = fetch(token, "/repos/" + owner + "/" + repo + "/collaborators?per_page=100");
        if (arr == null || !arr.isArray()) return null;

        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode c : arr) {
            String role = c.path("role_name").asText("");
            if (role.isBlank()) role = deriveRole(c.path("permissions"));
            out.add(row(
                    "login", c.path("login").asText(""),
                    "role", role,
                    "url", c.path("html_url").asText("")));
        }
        return out;
    }
     // Explains WHY GitHub refused a request for this repository, in plain language.
    // Returns null when the repository itself is reachable.
    public String diagnose(String token, String owner, String repo) {
    if (!safe(owner) || !safe(repo)) {
        return "The linked repository name is invalid. Link the repository again.";
    }

    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + token);
    headers.set("Accept", "application/vnd.github+json");
    headers.set("X-GitHub-Api-Version", "2022-11-28");

    try {
        restTemplate.exchange(API + "/repos/" + owner + "/" + repo,
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        return null;
    } catch (HttpClientErrorException e) {
        int code = e.getStatusCode().value();
        return switch (code) {
            case 401 -> "GitHub rejected the saved login (revoked or expired). Open your Profile, disconnect and reconnect GitHub, then link the repository again.";
            case 403 -> "GitHub denied access. If this repository belongs to an organization, an organization owner must approve this app (Organization settings, Third-party access). It could also be a temporary rate limit.";
            case 404 -> "GitHub can't find " + owner + "/" + repo + " with the account that linked it. The repository may have been renamed, moved or deleted, or that GitHub account has no access to it. Change the repository link on the project page.";
            default -> "GitHub returned an error (" + code + "). Try again in a moment.";
        };
    } catch (Exception e) {
        return "Could not reach GitHub. Check your internet connection.";
    }
}



    // null = not visible to this account (GitHub requires admin access to list webhooks)
    public List<Map<String, Object>> getWebhooks(String token, String owner, String repo) {
        if (!safe(owner) || !safe(repo)) return null;
        JsonNode arr = fetch(token, "/repos/" + owner + "/" + repo + "/hooks?per_page=50");
        if (arr == null || !arr.isArray()) return null;

        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode h : arr) {
            List<String> events = new ArrayList<>();
            for (JsonNode e : h.path("events")) events.add(e.asText(""));
            out.add(row(
                    "id", h.path("id").asLong(0),
                    "url", maskUrl(h.path("config").path("url").asText("")),
                    "events", events,
                    "active", h.path("active").asBoolean(false)));
        }
        return out;
    }

    private String deriveRole(JsonNode p) {
        if (p.path("admin").asBoolean(false)) return "admin";
        if (p.path("maintain").asBoolean(false)) return "maintain";
        if (p.path("push").asBoolean(false)) return "write";
        if (p.path("triage").asBoolean(false)) return "triage";
        return "read";
    }

    // Webhook URLs can carry secrets in the query string, so never show that part.
    private String maskUrl(String url) {
        if (url == null || url.isBlank()) return "";
        int cut = url.length();
        int q = url.indexOf('?');
        if (q >= 0) cut = Math.min(cut, q);
        int h = url.indexOf('#');
        if (h >= 0) cut = Math.min(cut, h);
        return cut < url.length() ? url.substring(0, cut) + "?***" : url;
    }
}