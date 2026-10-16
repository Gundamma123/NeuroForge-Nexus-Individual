package com.neuroforge.nexus.oauth;

import com.neuroforge.nexus.entity.User;
import com.neuroforge.nexus.repository.UserRepository;
import com.neuroforge.nexus.security.JwtUtil;
import com.neuroforge.nexus.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;


@Service
public class GitHubOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthService.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final EncryptionUtil encryptionUtil;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${github.app.client-id}")
    private String clientId;

    @Value("${github.app.client-secret}")
    private String clientSecret;

    @Value("${app.frontend.settings-url}")
    private String frontendSettingsUrl;

    public GitHubOAuthService(UserRepository userRepository, JwtUtil jwtUtil, EncryptionUtil encryptionUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.encryptionUtil = encryptionUtil;
    }

    public String handleCallback(String code, String stateJwt, Long installationId) {
        try {
            // Identify which NeuroForge user initiated this connection
            String email = jwtUtil.extractEmail(stateJwt);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found for GitHub connection"));

            // Exchange the temporary code for a real GitHub access token
            String accessToken = exchangeCodeForToken(code);

            if (accessToken == null || accessToken.isBlank()) {
                log.warn("GitHub OAuth failed: no access token returned for user");
                return frontendSettingsUrl + "?github=error";
            }

            log.info("GitHub OAuth: access token received, fetching username");

            // Fetch the GitHub username tied to that token
            String githubUsername = fetchGithubUsername(accessToken);

            // Save encrypted token + username + installation id (if GitHub sent one)
            user.setGithubAccessToken(encryptionUtil.encrypt(accessToken));
            user.setGithubUsername(githubUsername);
            if (installationId != null) {
                user.setGithubInstallationId(installationId);
            }
            userRepository.save(user);

            return frontendSettingsUrl + "?github=connected";
        } catch (Exception e) {
            log.error("GitHub OAuth callback failed", e);
            return frontendSettingsUrl + "?github=error";
        }
    }

    private String exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        Map<String, String> body = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
        "https://github.com/login/oauth/access_token",
        HttpMethod.POST,
        request,
        new ParameterizedTypeReference<Map<String, Object>>() {}
);

        if (response.getBody() == null) return null;
        Object token = response.getBody().get("access_token");
        return token != null ? token.toString() : null;
    }

    private String fetchGithubUsername(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
        "https://api.github.com/user",
        HttpMethod.GET,
        request,
        new ParameterizedTypeReference<Map<String, Object>>() {}
);

        if (response.getBody() == null) return null;
        Object login = response.getBody().get("login");
        return login != null ? login.toString() : null;
    }
}