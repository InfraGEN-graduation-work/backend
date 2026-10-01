package com.infragen.infragen.domain.parsing.dto.response;

import com.infragen.infragen.global.enums.ComponentType;

import lombok.Builder;
import lombok.Getter;

/**
 * POSTGRESQL 노드를 parsing한 결과다.
 *
 * <p>LOCAL_DEV와 CLOUD_DEPLOY Compose renderer가 postgres 서비스와 접속 변수를 만들 때 사용한다.
 * parser가 필수 값, 포트 범위, DB 이름 형식 검증을 마친 값만 담는다.
 */
@Getter
public class PostgreSQLComponent extends BaseComponent implements VolumeComponent {
    private String imageVersion;
    private String containerName;
    private PostgreSQLEnvComponent env;
    private int port;
    private String volumeName;

    @Builder
    public PostgreSQLComponent(
        String id,
        float posX,
        float posY,
        String imageVersion,
        String containerName,
        PostgreSQLEnvComponent env,
        int port,
        String volumeName
    ) {
        super(id, posX, posY, ComponentType.POSTGRESQL);
        this.imageVersion = imageVersion;
        this.containerName = containerName;
        this.env = env;
        this.port = port;
        this.volumeName = volumeName;
    }
}
