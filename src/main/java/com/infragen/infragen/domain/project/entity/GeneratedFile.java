package com.infragen.infragen.domain.project.entity;

import com.infragen.infragen.global.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "generated_file")
public class GeneratedFile extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Integer fileSize;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "history_id", nullable = false)
    private ProjectHistory projectHistory;

    @Builder
    public GeneratedFile(
            String fileName,
            String filePath,
            Integer fileSize,
            ProjectHistory projectHistory,
            String content
    ) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.projectHistory = projectHistory;
        this.content = content;
    }

    // 프로젝트 히스토리 설정
    void setProjectHistory(ProjectHistory projectHistory) {
        this.projectHistory = projectHistory;
    }

    // 생성된 IaC 파일 본문 설정
    void setContent(String content) {
        this.content = content;
    }
}
