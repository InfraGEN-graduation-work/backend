package com.infragen.infragen.domain.project.repository;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.infragen.infragen.domain.project.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
    
    // 식별자와 소유자 ID 기반 단일 조회
    Optional<Project> findByIdAndMemberId(Long id, Long memberId);
}
