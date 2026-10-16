package com.neuroforge.nexus.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status = "Active"; // Active | Completed | On Hold

    @Column(name = "team_size")
    private Integer teamSize = 0;

    @Column(length = 1000)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Project(Long id, String name, String status, Integer teamSize,
                   String description, LocalDateTime createdAt) {
        this();
        this.id = id;
        this.name = name;
        this.status = status;
        this.teamSize = teamSize;
        this.description = description;
        this.createdAt = createdAt;
    }
}