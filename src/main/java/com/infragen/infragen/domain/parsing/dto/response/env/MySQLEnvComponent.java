package com.infragen.infragen.domain.parsing.dto.response.env;

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
