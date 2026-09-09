package com.infragen.infragen.domain.collaboration.validator;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationOperationReqDTO;
import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollaborationOperationValidatorTest {
    private final CollaborationOperationValidator validator = new CollaborationOperationValidator();

    @Test
    void validate_updateNodeName_withValidPayload_succeeds() {
        // given
        CollaborationOperationReqDTO.Operation operation = operation(
                CollaborationOperationType.UPDATE_NODE_NAME,
                Map.of("value", "database")
        );

        // when
        Executable action = () -> validator.validate(operation);

        // then
        assertDoesNotThrow(action);
    }

    @Test
    void validate_updateNodePosition_withValidPayload_succeeds() {
        // given
        CollaborationOperationReqDTO.Operation operation = operation(
                CollaborationOperationType.UPDATE_NODE_POSITION,
                Map.of("positionX", 100.125, "positionY", -200.000)
        );

        // when
        Executable action = () -> validator.validate(operation);

        // then
        assertDoesNotThrow(action);
    }

    @Test
    void validate_updateNodeName_withBlankValue_throwsBadRequest() {
        // given
        CollaborationOperationReqDTO.Operation operation = operation(
                CollaborationOperationType.UPDATE_NODE_NAME,
                Map.of("value", " ")
        );

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> validator.validate(operation)
        );

        // then
        assertEquals("COLLAB400_2", exception.getCode().getCode());
    }

    @Test
    void validate_updateNodePosition_withTooManyDecimalPlaces_throwsBadRequest() {
        // given
        CollaborationOperationReqDTO.Operation operation = operation(
                CollaborationOperationType.UPDATE_NODE_POSITION,
                Map.of("positionX", 100.1234, "positionY", 200.000)
        );

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> validator.validate(operation)
        );

        // then
        assertEquals("COLLAB400_2", exception.getCode().getCode());
    }

    @Test
    void validate_withMissingCommonField_throwsBadRequest() {
        // given
        CollaborationOperationReqDTO.Operation operation = new CollaborationOperationReqDTO.Operation(
                "op-1",
                "client-1",
                0L,
                CollaborationOperationType.UPDATE_NODE_NAME,
                "",
                Map.of("value", "database")
        );

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> validator.validate(operation)
        );

        // then
        assertEquals("COLLAB400_1", exception.getCode().getCode());
    }

    private CollaborationOperationReqDTO.Operation operation(
            CollaborationOperationType type,
            Map<String, Object> payload
    ) {
        return new CollaborationOperationReqDTO.Operation(
                "op-1",
                "client-1",
                0L,
                type,
                "node-1",
                payload
        );
    }
}
