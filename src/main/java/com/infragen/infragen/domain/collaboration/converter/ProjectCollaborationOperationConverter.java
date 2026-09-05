package com.infragen.infragen.domain.collaboration.converter;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;

public final class ProjectCollaborationOperationConverter {
    private ProjectCollaborationOperationConverter() {
    }

    /**
     * 검증된 operation request와 조회된 연관 entity를 operation log entity로 변환한다.
     *
     * @param operation 변환할 collaboration operation
     * @param project operation 대상 project
     * @param actorMember operation을 발생시킨 member
     * @param serverVersion 서버가 발급한 version
     * @return operation log entity
     */
    public static ProjectCollaborationOperation toEntity(
            CollaborationOperationReqDTO.Operation operation,
            Project project,
            Member actorMember,
            Long serverVersion
    ) {
        return ProjectCollaborationOperation.builder()
                .project(project)
                .actorMember(actorMember)
                .operationId(operation.operationId())
                .clientId(operation.clientId())
                .baseVersion(operation.baseVersion())
                .serverVersion(serverVersion)
                .operationType(operation.type())
                .nodeId(operation.nodeId())
                .payload(operation.payload())
                .build();
    }

    /**
     * 적용된 operation request를 room broadcast 응답으로 변환한다.
     *
     * @param operation 적용된 collaboration operation
     * @param serverVersion 서버가 발급한 version
     * @param actorMemberId operation을 발생시킨 member 식별자
     * @return room에 전달할 operation 응답
     */
    public static CollaborationOperationResDTO.BroadcastOperationResDTO toBroadcast(
            CollaborationOperationReqDTO.Operation operation,
            Long serverVersion,
            Long actorMemberId
    ) {
        return CollaborationOperationResDTO.BroadcastOperationResDTO.builder()
                .operationId(operation.operationId())
                .clientId(operation.clientId())
                .serverVersion(serverVersion)
                .actorMemberId(actorMemberId)
                .type(operation.type())
                .nodeId(operation.nodeId())
                .payload(operation.payload())
                .build();
    }

    /**
     * 저장된 operation log를 reconnect replay 응답으로 변환한다.
     *
     * @param operation 변환할 operation log
     * @return client에 replay할 operation 응답
     */
    public static CollaborationOperationResDTO.BroadcastOperationResDTO toBroadcast(
            ProjectCollaborationOperation operation
    ) {
        return CollaborationOperationResDTO.BroadcastOperationResDTO.builder()
                .operationId(operation.getOperationId())
                .clientId(operation.getClientId())
                .serverVersion(operation.getServerVersion())
                .actorMemberId(operation.getActorMember().getId())
                .type(operation.getOperationType())
                .nodeId(operation.getNodeId())
                .payload(operation.getPayload())
                .build();
    }
}
