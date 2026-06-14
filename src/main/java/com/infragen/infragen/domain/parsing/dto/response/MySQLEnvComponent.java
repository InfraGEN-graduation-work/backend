package com.infragen.infragen.domain.parsing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class MySQLEnvComponent {
    private String databaseName;
    private String userPassword;
    private String rootPassword;
    private String username;
}
