package com.infragen.infragen.domain.generation.generator.compose;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.generator.application.DatabaseConnection;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.MongoDBComponent;
import com.infragen.infragen.domain.parsing.dto.response.MongoDBEnvComponent;
import com.infragen.infragen.global.enums.ComponentType;

/**
 * 호스트에서 실행하는 애플리케이션의 MongoDB 접속 정보를 만든다.
 *
 * <p>앱은 Compose 밖(호스트)에서 실행되므로 컨테이너 DNS가 아니라 {@code localhost}와 사용자 입력 호스트 포트로 접속한다.
 */
@Component
public class MongoHostAppEnvContributor implements HostAppEnvContributor {

    private static final String LOCALHOST = "localhost";
    // 공식 이미지는 MONGO_INITDB_ROOT_*로 만든 root 계정을 admin DB에 만들므로 앱이 그 계정을 찾을 DB를 알려야 한다.
    private static final String AUTHENTICATION_DATABASE = "admin";

    @Override
    public ComponentType getDependencyType() {
        return ComponentType.MONGODB;
    }

    @Override
    public SequencedMap<String, String> hostAppEnvironment(BaseComponent dependency) {
        MongoDBComponent mongo = (MongoDBComponent) dependency;
        SequencedMap<String, String> environment = new LinkedHashMap<>();
        environment.put("MONGO_HOST", LOCALHOST);
        environment.put("MONGO_PORT", String.valueOf(mongo.getPort()));
        return environment;
    }

    /**
     * @throws IaCGenerationException MongoDB env 정보가 없는 경우
     */
    @Override
    public Optional<DatabaseConnection> databaseConnection(BaseComponent dependency) {
        MongoDBComponent mongo = (MongoDBComponent) dependency;
        MongoDBEnvComponent env = mongo.getEnv();
        if (env == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }

        return Optional.of(new DatabaseConnection(
            "mongodb",
            false,
            LOCALHOST,
            String.valueOf(mongo.getPort()),
            env.getDatabaseName(),
            env.getUsername(),
            env.getPassword(),
            Optional.of(AUTHENTICATION_DATABASE)
        ));
    }
}
