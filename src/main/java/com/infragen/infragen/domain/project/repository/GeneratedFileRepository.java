package com.infragen.infragen.domain.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.infragen.infragen.domain.project.entity.GeneratedFile;

public interface GeneratedFileRepository extends JpaRepository<GeneratedFile, Long> {
    List<GeneratedFile> findAllByProjectHistoryId(Long historyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GeneratedFile gf WHERE gf.projectHistory.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
