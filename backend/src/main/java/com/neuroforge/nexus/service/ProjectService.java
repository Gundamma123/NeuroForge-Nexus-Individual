// package com.neuroforge.nexus.service;


// import com.neuroforge.nexus.dto.ProjectRequest;
// import com.neuroforge.nexus.entity.Milestone;
// import com.neuroforge.nexus.entity.Project;
// import com.neuroforge.nexus.entity.Sprint;
// import com.neuroforge.nexus.entity.Team;
// import com.neuroforge.nexus.exception.ResourceNotFoundException;
// // 1) import
// import com.neuroforge.nexus.repository.*;
// import org.springframework.stereotype.Service;

// import com.neuroforge.nexus.entity.Bug;

// import java.util.LinkedHashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.stream.Collectors;



// @Service
// public class ProjectService {

//     private final ProjectRepository projectRepository;
//     private final UserRepository userRepository;
//     private final TeamRepository teamRepository;
//     private final SprintRepository sprintRepository;
//     private final MilestoneRepository milestoneRepository;
//     private final BugRepository bugRepository;
    


//       // 2) field, plus constructor parameter and assignment (add alongside your existing ones)
//         // private final BugRepository bugRepository;
//        // constructor: add "BugRepository bugRepository" and "this.bugRepository = bugRepository;"


//         public ProjectService(ProjectRepository projectRepository,
//                            UserRepository userRepository,
//                            TeamRepository teamRepository,
//                            SprintRepository sprintRepository,
//                            MilestoneRepository milestoneRepository,
//                            BugRepository bugRepository) {
//         this.projectRepository = projectRepository;
//         this.userRepository = userRepository;
//         this.teamRepository = teamRepository;
//         this.sprintRepository = sprintRepository;
//         this.milestoneRepository = milestoneRepository;
//         //this.bugRepository = bugRepository;
//         this.bugRepository = bugRepository;
        
//     }

//     public List<Project> getAllProjects() {
//         return projectRepository.findAll();
//     }

//     public Project getProjectById(Long id) {
//         return projectRepository.findById(id)
//                 .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));
//     }

//     public Project createProject(ProjectRequest request) {
//         Project project = new Project();
//         project.setName(request.getName());
//         project.setStatus(request.getStatus() != null ? request.getStatus() : "Active");
//         project.setTeamSize(request.getTeamSize() != null ? request.getTeamSize() : 0);
//         project.setDescription(request.getDescription());
//         return projectRepository.save(project);
//     }
//     // nn
// public Project updateProject(Long id, ProjectRequest request) {
//     Project project = getProjectById(id);
//     boolean justCompleted = "Completed".equals(request.getStatus()) && !"Completed".equals(project.getStatus());

//     project.setName(request.getName());
//     if (request.getStatus() != null) project.setStatus(request.getStatus());
//     if (request.getTeamSize() != null) project.setTeamSize(request.getTeamSize());
//     project.setDescription(request.getDescription());

//     Project saved = projectRepository.save(project);

//     if (justCompleted) {
//         releaseTeamMembers(id);
//     }

//     return saved;
// }

// public void deleteProject(Long id) {
//     Project project = getProjectById(id);

//     releaseTeamMembers(id); // frees all members before unlinking teams

//     List<Team> linkedTeams = teamRepository.findByProjectId(id);
//     for (Team team : linkedTeams) {
//         team.setProject(null);
//         teamRepository.save(team);
//     }
//     if (!bugRepository.findByProject_Id(id).isEmpty()) {
//     throw new IllegalArgumentException(
//         "Cannot delete this project because it has linked bug reports. " +
//         "Please reassign or remove the bug reports first."
//     );
// }

//     List<Milestone> linkedMilestones = milestoneRepository.findByProjectId(id);
//     for (Milestone milestone : linkedMilestones) {
//         milestone.setProject(null);
//         milestoneRepository.save(milestone);
//     }

//     List<Sprint> linkedSprints = sprintRepository.findByProjectId(id);
//     for (Sprint sprint : linkedSprints) {
//         sprint.setProject(null);
//         sprintRepository.save(sprint);
//     }

//     projectRepository.delete(project);
// }

// // Clears every team's member list for this project, instantly making those
// // members available again (availability is computed dynamically from team
// // assignment — there is no separate isAvailable flag to update).
// private void releaseTeamMembers(Long projectId) {
//     List<Team> teams = teamRepository.findByProjectId(projectId);
//     for (Team team : teams) {
//         team.getMembers().clear();
//         team.setMemberCount(0);
//         teamRepository.save(team);
//     }
// }
    

// //     public void deleteProject(Long id) {
// //     Project project = getProjectById(id);

// //     // Unlink any teams pointing at this project first, so MySQL's foreign key
// //     // constraint on Team.project_id doesn't block the delete. Teams themselves
// //     // are preserved — they just become "Unassigned" instead of being destroyed.
// //     List<Team> linkedTeams = teamRepository.findByProjectId(id);
// //     for (Team team : linkedTeams) {
// //         team.setProject(null);
// //         teamRepository.save(team);
// //     }

// //     projectRepository.delete(project);
// // }

//     /**
//      * Builds the "RBAC & Team Setup" summary consumed by the frontend Dashboard
//      * (Milestone 1: Project & User Management).
//      */
//     public Map<String, Object> getDashboardSummary() {
//         Map<String, Object> summary = new LinkedHashMap<>();

//         long activeProjects = projectRepository.countByStatus("Active");
//         long registeredUsers = userRepository.count();
//         long activeTeams = teamRepository.findAll().size();

//         summary.put("activeProjects", activeProjects);
//         summary.put("registeredUsers", registeredUsers);
//         summary.put("activeTeams", activeTeams);

//         Project featuredProject = projectRepository.findAll().stream().findFirst().orElse(null);
//         Map<String, Object> userService = new LinkedHashMap<>();

//         if (featuredProject != null) {
//             List<Sprint> sprints = sprintRepository.findByProjectId(featuredProject.getId());
//             List<Milestone> milestones = milestoneRepository.findByProjectId(featuredProject.getId());
//             List<String> teamNames = teamRepository.findByProjectId(featuredProject.getId())
//                     .stream().map(Team -> Team.getName()).collect(Collectors.toList());

//             userService.put("project", featuredProject.getName());
//             userService.put("status", featuredProject.getStatus());
//             userService.put("teamSize", featuredProject.getTeamSize());
//             userService.put("users", buildUsersSummary());
//             userService.put("rbacProvider", "Keycloak");
//             userService.put("roles", "Admin, PM, Dev, QA");
//             userService.put("teams", teamNames.isEmpty() ? "Backend, Frontend, QA, DevOps" : String.join(", ", teamNames));

//             Sprint sprint = sprints.stream().findFirst().orElse(null);
//             Map<String, Object> sprintInfo = new LinkedHashMap<>();
//             sprintInfo.put("name", sprint != null ? sprint.getName() : "Sprint 12");
//             sprintInfo.put("tasks", sprint != null ? sprint.getTaskCount() : 23);
//             sprintInfo.put("points", sprint != null ? sprint.getPoints() : 67);
//             userService.put("sprint", sprintInfo);

//             Milestone milestone = milestones.stream().findFirst().orElse(null);
//             Map<String, Object> milestoneInfo = new LinkedHashMap<>();
//             milestoneInfo.put("name", milestone != null ? milestone.getName() : "Release 2.3");
//             milestoneInfo.put("dueDate", milestone != null ? milestone.getDueDate() : "20-Jun-2026");
//             userService.put("milestone", milestoneInfo);
//         } else {
//             // No project seeded yet — matches the Milestone 1 expected output screen
//             userService.put("project", "FinCore Nexus");
//             userService.put("status", "Active");
//             userService.put("teamSize", 12);
//             userService.put("users", "Admin, PM, 5 Devs, 3 Testers, 2 DevOps");
//             userService.put("rbacProvider", "Keycloak");
//             userService.put("roles", "Admin, PM, Dev, QA");
//             userService.put("teams", "Backend, Frontend, QA, DevOps");
//             userService.put("sprint", Map.of("name", "Sprint 12", "tasks", 23, "points", 67));
//             userService.put("milestone", Map.of("name", "Release 2.3", "dueDate", "20-Jun-2026"));
//         }

//         summary.put("userService", userService);
//         return summary;
//     }

//     private String buildUsersSummary() {
//         List<com.neuroforge.nexus.entity.User> all = userRepository.findAll();
//         long admins = all.stream().filter(u -> "Admin".equalsIgnoreCase(u.getRole().getName())).count();
//         long pms = all.stream().filter(u -> "Project Manager".equalsIgnoreCase(u.getRole().getName())).count();
//         long devs = all.stream().filter(u -> "Developer".equalsIgnoreCase(u.getRole().getName())).count();
//         long testers = all.stream().filter(u -> "Tester".equalsIgnoreCase(u.getRole().getName())).count();
//         long devops = all.stream().filter(u -> "DevOps Engineer".equalsIgnoreCase(u.getRole().getName())).count();

//         return String.format("Admin: %d, PM: %d, Devs: %d, Testers: %d, DevOps: %d",
//                 admins, pms, devs, testers, devops);
//     }

    
// } 


package com.neuroforge.nexus.service;

import com.neuroforge.nexus.dto.ProjectRequest;
import com.neuroforge.nexus.entity.Milestone;
import com.neuroforge.nexus.entity.Project;
import com.neuroforge.nexus.entity.Sprint;
import com.neuroforge.nexus.entity.Team;
import com.neuroforge.nexus.exception.ResourceNotFoundException;
import com.neuroforge.nexus.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final SprintRepository sprintRepository;
    private final MilestoneRepository milestoneRepository;
    private final BugRepository bugRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            TeamRepository teamRepository,
            SprintRepository sprintRepository,
            MilestoneRepository milestoneRepository,
            BugRepository bugRepository) {

        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.sprintRepository = sprintRepository;
        this.milestoneRepository = milestoneRepository;
        this.bugRepository = bugRepository;
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Project getProjectById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Project not found with id: " + id));
    }

    public Project createProject(ProjectRequest request) {
        Project project = new Project();
        project.setName(request.getName());
        project.setStatus(
                request.getStatus() != null ? request.getStatus() : "Active");
        project.setTeamSize(
                request.getTeamSize() != null ? request.getTeamSize() : 0);
        project.setDescription(request.getDescription());

        return projectRepository.save(project);
    }

    public Project updateProject(Long id, ProjectRequest request) {
        Project project = getProjectById(id);

        boolean justCompleted =
                "Completed".equals(request.getStatus())
                && !"Completed".equals(project.getStatus());

        project.setName(request.getName());

        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }

        if (request.getTeamSize() != null) {
            project.setTeamSize(request.getTeamSize());
        }

        project.setDescription(request.getDescription());

        Project saved = projectRepository.save(project);

        if (justCompleted) {
            releaseTeamMembers(id);
        }

        return saved;
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = getProjectById(id);

        // Prevent deletion when bug reports reference this project.
        // Bug reports are preserved.
        if (!bugRepository.findByProject_Id(id).isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot delete this project because it has linked bug reports. "
                    + "Please reassign the bug reports first."
            );
        }

        // Release team members before unlinking teams.
        releaseTeamMembers(id);

        List<Team> linkedTeams = teamRepository.findByProjectId(id);

        for (Team team : linkedTeams) {
            team.setProject(null);
            teamRepository.save(team);
        }

        List<Milestone> linkedMilestones =
                milestoneRepository.findByProjectId(id);

        for (Milestone milestone : linkedMilestones) {
            milestone.setProject(null);
            milestoneRepository.save(milestone);
        }

        List<Sprint> linkedSprints =
                sprintRepository.findByProjectId(id);

        for (Sprint sprint : linkedSprints) {
            sprint.setProject(null);
            sprintRepository.save(sprint);
        }

        projectRepository.delete(project);
    }

    // Clears team member assignments for the specified project.
    private void releaseTeamMembers(Long projectId) {
        List<Team> teams = teamRepository.findByProjectId(projectId);

        for (Team team : teams) {
            team.getMembers().clear();
            team.setMemberCount(0);
            teamRepository.save(team);
        }
    }

    /**
     * Builds the RBAC and Team Setup summary consumed by the frontend Dashboard.
     */
    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        long activeProjects = projectRepository.countByStatus("Active");
        long registeredUsers = userRepository.count();
        long activeTeams = teamRepository.findAll().size();

        summary.put("activeProjects", activeProjects);
        summary.put("registeredUsers", registeredUsers);
        summary.put("activeTeams", activeTeams);

        Project featuredProject = projectRepository.findAll()
                .stream()
                .findFirst()
                .orElse(null);

        Map<String, Object> userService = new LinkedHashMap<>();

        if (featuredProject != null) {
            List<Sprint> sprints =
                    sprintRepository.findByProjectId(featuredProject.getId());

            List<Milestone> milestones =
                    milestoneRepository.findByProjectId(featuredProject.getId());

            List<String> teamNames =
                    teamRepository.findByProjectId(featuredProject.getId())
                            .stream()
                            .map(team -> team.getName())
                            .collect(Collectors.toList());

            userService.put("project", featuredProject.getName());
            userService.put("status", featuredProject.getStatus());
            userService.put("teamSize", featuredProject.getTeamSize());
            userService.put("users", buildUsersSummary());
            userService.put("rbacProvider", "Keycloak");
            userService.put("roles", "Admin, PM, Dev, QA");
            userService.put(
                    "teams",
                    teamNames.isEmpty()
                            ? "Backend, Frontend, QA, DevOps"
                            : String.join(", ", teamNames));

            Sprint sprint = sprints.stream().findFirst().orElse(null);

            Map<String, Object> sprintInfo = new LinkedHashMap<>();
            sprintInfo.put(
                    "name", sprint != null ? sprint.getName() : "Sprint 12");
            sprintInfo.put(
                    "tasks", sprint != null ? sprint.getTaskCount() : 23);
            sprintInfo.put(
                    "points", sprint != null ? sprint.getPoints() : 67);

            userService.put("sprint", sprintInfo);

            Milestone milestone = milestones.stream()
                    .findFirst()
                    .orElse(null);

            Map<String, Object> milestoneInfo = new LinkedHashMap<>();
            milestoneInfo.put(
                    "name",
                    milestone != null ? milestone.getName() : "Release 2.3");
            milestoneInfo.put(
                    "dueDate",
                    milestone != null ? milestone.getDueDate() : "20-Jun-2026");

            userService.put("milestone", milestoneInfo);

        } else {
            // Default dashboard values when no project exists.
            userService.put("project", "FinCore Nexus");
            userService.put("status", "Active");
            userService.put("teamSize", 12);
            userService.put(
                    "users", "Admin, PM, 5 Devs, 3 Testers, 2 DevOps");
            userService.put("rbacProvider", "Keycloak");
            userService.put("roles", "Admin, PM, Dev, QA");
            userService.put("teams", "Backend, Frontend, QA, DevOps");
            userService.put(
                    "sprint",
                    Map.of("name", "Sprint 12", "tasks", 23, "points", 67));
            userService.put(
                    "milestone",
                    Map.of("name", "Release 2.3", "dueDate", "20-Jun-2026"));
        }

        summary.put("userService", userService);
        return summary;
    }

    private String buildUsersSummary() {
        List<com.neuroforge.nexus.entity.User> all =
                userRepository.findAll();

        long admins = all.stream()
                .filter(u -> "Admin".equalsIgnoreCase(u.getRole().getName()))
                .count();

        long pms = all.stream()
                .filter(u -> "Project Manager".equalsIgnoreCase(
                        u.getRole().getName()))
                .count();

        long devs = all.stream()
                .filter(u -> "Developer".equalsIgnoreCase(
                        u.getRole().getName()))
                .count();

        long testers = all.stream()
                .filter(u -> "Tester".equalsIgnoreCase(
                        u.getRole().getName()))
                .count();

        long devops = all.stream()
                .filter(u -> "DevOps Engineer".equalsIgnoreCase(
                        u.getRole().getName()))
                .count();

        return String.format(
                "Admin: %d, PM: %d, Devs: %d, Testers: %d, DevOps: %d",
                admins, pms, devs, testers, devops);
    }
}
