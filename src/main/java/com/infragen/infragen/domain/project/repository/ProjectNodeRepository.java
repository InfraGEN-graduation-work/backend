package com.infragen.infragen.domain.project.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.infragen.infragen.domain.project.entity.ProjectNode;

public interface ProjectNodeRepository extends JpaRepository<ProjectNode, Long> {
    
    // 특정 프로젝트에 배정된 노드 리스트 조회 (상세 복원용)
    List<ProjectNode> findAllByProjectId(Long projectId);

    /**
     * project 안에서 canvas nodeId에 해당하는 node를 조회한다.
     *
     * @param projectId 조회할 project 식별자
     * @param nodeId 조회할 canvas node 식별자
     * @return 대상 node가 있으면 반환
     */
    Optional<ProjectNode> findByProjectIdAndNodeId(Long projectId, String nodeId);

    // 벌크 삭제 쿼리 (1차 캐시 비우기 및 플러시 옵션 적용)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectNode pn WHERE pn.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
