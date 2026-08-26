package com.infragen.infragen.domain.generation.generator.cloud;

/** CLOUD_DEPLOY Compose에 하나의 의존 인프라 서비스를 렌더링하는 계약이다. */
public interface CloudComposeServiceRenderer {

    /** @return Compose service key */
    String getServiceName();

    /** @param context CLOUD_DEPLOY renderer가 공유하는 입력 정보 */
    boolean isEnabled(CloudDeployContext context);

    /** @param context CLOUD_DEPLOY renderer가 공유하는 입력 정보 */
    boolean isDependency(CloudDeployContext context);

    /** @param context CLOUD_DEPLOY renderer가 공유하는 입력 정보
     * @return Compose service block
     */
    String render(CloudDeployContext context);
}
