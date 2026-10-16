package com.neuroforge.nexus.service;

import com.neuroforge.nexus.dto.SprintRequest;
import com.neuroforge.nexus.entity.Project;
import com.neuroforge.nexus.entity.Sprint;
import com.neuroforge.nexus.exception.ResourceNotFoundException;
import com.neuroforge.nexus.repository.ProjectRepository;
import com.neuroforge.nexus.repository.SprintRepository;
import org.springframework.stereotype.Service;
import com.neuroforge.nexus.repository.TaskRepository;

import java.util.List;

@Service
public class SprintService {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    public SprintService(SprintRepository sprintRepository, ProjectRepository projectRepository, TaskRepository taskRepository) {
        this.sprintRepository = sprintRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
    }

//     public List<Sprint> getSprintsByProject(Long projectId) {
//     return sprintRepository.findByProjectId(projectId);
// }
public List<Sprint> getSprintsByProject(Long projectId) {
    List<Sprint> sprints = sprintRepository.findByProjectId(projectId);

    for (Sprint sprint : sprints) {
        sprint.setTaskCount(
            (int) taskRepository.countBySprintId(sprint.getId())
        );
    }

    return sprints;
}

    public List<Sprint> getAllSprints() {
        return sprintRepository.findAll();
    }

    public Sprint getSprintById(Long id) {
        return sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found with id: " + id));
    }

    public Sprint createSprint(SprintRequest request) {
        Sprint sprint = new Sprint();
        sprint.setName(request.getName());
        sprint.setTaskCount(request.getTaskCount() != null ? request.getTaskCount() : 0);
        sprint.setPoints(request.getPoints() != null ? request.getPoints() : 0);
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        sprint.setStatus(request.getStatus() != null ? request.getStatus() : "Active");

        if (request.getProjectId() != null) {
            Project project = projectRepository.findById(request.getProjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + request.getProjectId()));
            sprint.setProject(project);
        }
        validateDates(sprint.getStartDate(), sprint.getEndDate());

        return sprintRepository.save(sprint);
    }

    public Sprint updateSprint(Long id, SprintRequest request) {
        Sprint sprint = getSprintById(id);
        sprint.setName(request.getName());
        if (request.getTaskCount() != null) sprint.setTaskCount(request.getTaskCount());
        if (request.getPoints() != null) sprint.setPoints(request.getPoints());
        sprint.setStartDate(request.getStartDate());
        sprint.setEndDate(request.getEndDate());
        if (request.getStatus() != null) sprint.setStatus(request.getStatus());
        validateDates(sprint.getStartDate(), sprint.getEndDate());
        return sprintRepository.save(sprint);
    }

    public void deleteSprint(Long id) {
        sprintRepository.delete(getSprintById(id));
    }
    private void validateDates(java.time.LocalDate startDate, java.time.LocalDate endDate) {
    if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
        throw new IllegalArgumentException("End date must be after start date.");
    }
}


}