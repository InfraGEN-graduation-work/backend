package com.infragen.infragen.domain.project.repository;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.infragen.infragen.domain.project.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    /**
     * 최초 collaboration state 생성을 위해 project row를 쓰기 잠금과 함께 조회한다.
     *
     * @param projectId 잠글 project 식별자
     * @return project가 존재하면 반환
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT project
            FROM Project project
            WHERE project.id = :projectId
            """)
    Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);

    List<Project> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
    
    // 식별자와 소유자 ID 기반 단일 조회
    Optional<Project> findByIdAndMemberId(Long id, Long memberId);
}
