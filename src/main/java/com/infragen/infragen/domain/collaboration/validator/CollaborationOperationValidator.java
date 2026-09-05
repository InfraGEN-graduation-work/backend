package com.infragen.infragen.domain.collaboration.validator;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * 1차 collaboration operation의 공통 필드와 변경 payload를 검증한다.
 */
@Component
public class CollaborationOperationValidator {
    private static final Set<String> NODE_NAME_FIELDS = Set.of("value");
    private static final Set<String> NODE_POSITION_FIELDS = Set.of("positionX", "positionY");
    private static final int POSITION_PRECISION = 10;
    private static final int POSITION_SCALE = 3;

    /**
     * operation이 현재 1차 계약에 맞는지 확인한다.
     *
     * @param operation 검증할 collaboration operation
     * @throws CollaborationException 공통 필드나 operation payload가 계약에 맞지 않는 경우
     */
    public void validate(CollaborationOperationReqDTO.Operation operation) {
        if (operation == null
                || isBlank(operation.operationId())
                || isBlank(operation.clientId())
                || operation.baseVersion() == null
                || operation.baseVersion() < 0
                || operation.type() == null
                || isBlank(operation.nodeId())
                || operation.payload() == null) {
            throw invalidOperation(CollaborationErrorCode.INVALID_OPERATION);
        }

        switch (operation.type()) {
            case UPDATE_NODE_NAME -> validateNodeName(operation.payload());
            case UPDATE_NODE_POSITION -> validateNodePosition(operation.payload());
        }
    }

    private void validateNodeName(Map<String, Object> payload) {
        if (!NODE_NAME_FIELDS.equals(payload.keySet())
                || !(payload.get("value") instanceof String value)
                || isBlank(value)) {
            throw invalidOperation(CollaborationErrorCode.INVALID_OPERATION_PAYLOAD);
        }
    }

    private void validateNodePosition(Map<String, Object> payload) {
        if (!NODE_POSITION_FIELDS.equals(payload.keySet())) {
            throw invalidOperation(CollaborationErrorCode.INVALID_OPERATION_PAYLOAD);
        }

        validatePosition(payload.get("positionX"));
        validatePosition(payload.get("positionY"));
    }

    private void validatePosition(Object value) {
        BigDecimal position;
        try {
            position = new BigDecimal(value.toString());
        } catch (Exception exception) {
            throw invalidOperation(CollaborationErrorCode.INVALID_OPERATION_PAYLOAD);
        }

        if (position.scale() > POSITION_SCALE || position.precision() > POSITION_PRECISION) {
            throw invalidOperation(CollaborationErrorCode.INVALID_OPERATION_PAYLOAD);
        }
    }

    private CollaborationException invalidOperation(CollaborationErrorCode errorCode) {
        return new CollaborationException(errorCode);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
