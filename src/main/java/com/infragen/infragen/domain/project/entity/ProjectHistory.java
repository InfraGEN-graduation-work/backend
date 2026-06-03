package com.infragen.infragen.domain.project.entity;

import java.util.ArrayList;
import java.util.List;

import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.global.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "project_history",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_project_history_project_version",
            columnNames = {"project_id", "version_name"}
        )
    }
)
public class ProjectHistory extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_name", nullable = false, length = 100)
    private String versionName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @OneToMany(mappedBy = "projectHistory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GeneratedFile> generatedFileList = new ArrayList<>();

    @Builder
    public ProjectHistory(
            String versionName,
            String description,
            Project project
    ) {
        this.versionName = versionName;
        this.description = description;
        this.project = project;
    }

    public void addGeneratedFile(GeneratedFile generatedFile) {
        if (generatedFile == null) {
            throw new ProjectException(ProjectErrorCode.GENERATED_FILE_CANNOT_BE_NULL);
        }
        this.generatedFileList.add(generatedFile);
        generatedFile.assignProjectHistory(this);
    }

    public void removeGeneratedFile(GeneratedFile generatedFile) {
        if (generatedFile == null) {
            throw new ProjectException(ProjectErrorCode.GENERATED_FILE_CANNOT_BE_NULL);
        }
        this.generatedFileList.remove(generatedFile);
    }
}
