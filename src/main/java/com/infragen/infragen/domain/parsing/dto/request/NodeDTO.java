package com.infragen.infragen.domain.parsing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NodeDTO {

    @NotBlank(message = "노드 Id가 비어있습니다.")
    private String nodeId;

    @NotBlank(message = "컴포넌트 타입이 비어있습니다.")
    private String componentType;

    @NotNull(message = "X 좌표가 누락되었습니다.")
    private Float positionX;

    @NotNull(message = "Y 좌표가 누락되었습니다.")
    private Float positionY;

    @NotNull(message = "상세 속성이 누락되었습니다.")
    private Object properties;
}