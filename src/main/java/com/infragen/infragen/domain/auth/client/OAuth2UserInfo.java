package com.infragen.infragen.domain.auth.client;

public interface OAuth2UserInfo {
    String getSocialId();
    String getEmail();
    String getNickname();
}
