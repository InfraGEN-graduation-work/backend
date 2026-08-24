package com.infragen.infragen.domain.project.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.infragen.infragen.domain.project.entity.ProjectEdge;

public interface ProjectEdgeRepository extends JpaRepository<ProjectEdge, Long> {
    
    // 특정 프로젝트에 배정된 edge 리스트 조회 (상세 복원용)
    List<ProjectEdge> findAllByProjectId(Long projectId);

    // 벌크 삭제 쿼리
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectEdge pe WHERE pe.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
