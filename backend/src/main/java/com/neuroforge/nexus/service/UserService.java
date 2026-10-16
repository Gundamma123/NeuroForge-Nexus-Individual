package com.neuroforge.nexus.service;

import com.neuroforge.nexus.dto.UserRequest;
import com.neuroforge.nexus.entity.Role;
import com.neuroforge.nexus.entity.User;
import com.neuroforge.nexus.exception.ResourceNotFoundException;
import com.neuroforge.nexus.repository.RoleRepository;
import com.neuroforge.nexus.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    public User updateUser(Long id, UserRequest request) {
        User user = getUserById(id);

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setTeam(request.getTeam());

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        if (request.getRole() != null) {
            Role role = roleRepository.findByName(request.getRole())
                    .orElseGet(() -> roleRepository.save(new Role(null, request.getRole())));
            user.setRole(role);
        }

        return userRepository.save(user);
    }
    public Map<String, Object> getGithubStatus(String email) {
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Map<String, Object> status = new java.util.LinkedHashMap<>();
    // status.put("connected", user.getGithubAccessToken() != null);
    status.put("connected",
        user.getGithubAccessToken() != null && !user.getGithubAccessToken().isBlank());
    status.put("githubUsername", user.getGithubUsername());
    return status;
}

public void disconnectGithub(String email) {
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    user.setGithubAccessToken(null);
    user.setGithubUsername(null);
    user.setGithubInstallationId(null);
    userRepository.save(user);
}




}