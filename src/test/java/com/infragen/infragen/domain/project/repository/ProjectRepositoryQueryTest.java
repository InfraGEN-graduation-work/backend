package com.infragen.infragen.domain.project.repository;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import jakarta.persistence.Tuple;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** DB 연결 없이 실제 Hibernate 모델로 목록 HQL의 엔티티·enum·매개변수 해석을 검증한다. */
class ProjectRepositoryQueryTest {
    @Test
    @DisplayName("접근 가능한 프로젝트 query는 MySQL dialect의 Hibernate 모델에서 해석된다")
    void accessibleProjects_Query_ParsesWithMappedEntities() throws Exception {
        // given
        String hql = ProjectRepository.class.getMethod("findAllAccessibleByMemberId", Long.class)
                .getAnnotation(Query.class).value();
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Member.class)
                .addAnnotatedClass(Project.class)
                .addAnnotatedClass(ProjectCollaborator.class)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .setProperty("hibernate.hbm2ddl.auto", "none");
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            // when
            var query = session.createQuery(hql, Tuple.class);

            // then
            assertEquals(Long.class, query.getParameter("memberId").getParameterType());
        }
    }
}
