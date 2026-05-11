package com.infragen.infragen.domain.parsing.dto.response;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@JsonPropertyOrder({ "nodeId", "positionX", "positionY", "databaseName", "rootPassword" })
public class MySQL extends BaseComponent{

    private String databaseName;
    private String rootPassword;

    public MySQL(String id, float posX, float posY, String databaseName, String rootPassword) {
        super(id, posX, posY);
        this.databaseName = databaseName;
        this.rootPassword = rootPassword;
    }
}