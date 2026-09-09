package com.infragen.infragen.domain.collaboration.service.query;

import com.infragen.infragen.domain.collaboration.converter.ProjectCollaborationOperationConverter;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationOperationResDTO;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CollaborationOperationQueryService {
    private final ProjectAccessService projectAccessService;
    private final ProjectCollaborationOperationRepository operationRepository;

    /**
     * 지정한 version 이후 project operation을 serverVersion 순서로 조회한다.
     *
     * @param projectId operation을 조회할 project 식별자
     * @param memberId 조회를 요청한 member 식별자
     * @param afterVersion replay 기준 version
     * @return 기준 version 이후 operation 목록
     * @throws CollaborationException version이 음수이거나 null인 경우
     */
    @Transactional(readOnly = true)
    public List<CollaborationOperationResDTO.BroadcastOperationResDTO> getOperationsAfterVersion(
            Long projectId,
            Long memberId,
            Long afterVersion
    ) {
        projectAccessService.requireReadAccess(projectId, memberId);
        if (afterVersion == null || afterVersion < 0) {
            throw new CollaborationException(CollaborationErrorCode.INVALID_OPERATION);
        }

        return operationRepository.findAllByProjectIdOrderByServerVersionAsc(projectId).stream()
                .filter(operation -> operation.getServerVersion() > afterVersion)
                .map(ProjectCollaborationOperationConverter::toBroadcast)
                .toList();
    }
}
