package com.infragen.infragen.domain.parsing.dto.response;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;

@Getter
@JsonPropertyOrder({ "nodeId", "positionX", "positionY", "databaseName", "rootPassword" })
public class MySQLComponent extends BaseComponent{

    private String databaseName;
    private String rootPassword;

    public MySQLComponent(String id, float posX, float posY, String databaseName, String rootPassword) {
        super(id, posX, posY);
        this.databaseName = databaseName;
        this.rootPassword = rootPassword;
    }
}