package com.infragen.infragen.domain.generation.generator.nginx;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;

public final class NginxConfigRenderer {
    public static final String FILE_NAME = "nginx/default.conf";

    private NginxConfigRenderer() {
    }

    public static IaCFileDTO.FileContentResDTO render(String host, int port) {
        return IaCFileDTO.FileContentResDTO.builder()
            .fileName(FILE_NAME)
            .content("""
                server {
                    listen 80;
                    server_name _;

                    location / {
                        proxy_pass http://%s:%d;
                        proxy_set_header Host $http_host;
                        proxy_set_header X-Real-IP $remote_addr;
                        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                        proxy_set_header X-Forwarded-Proto $scheme;
                    }
                }
                """.formatted(host, port))
            .build();
    }
}
