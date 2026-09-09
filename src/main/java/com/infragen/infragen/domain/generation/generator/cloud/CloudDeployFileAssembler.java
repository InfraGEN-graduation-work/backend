package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.List;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;

/** renderer 결과를 Generate API가 반환하는 CLOUD_DEPLOY 파일 묶음으로 조립한다. */
public class CloudDeployFileAssembler {

    /**
     * renderer가 정한 파일 순서를 유지해 bundle로 감싼다.
     *
     * @param files renderer가 생성한 파일 목록
     * @return 생성 파일 bundle
     */
    public IaCFileDTO.BundleResDTO assemble(List<IaCFileDTO.FileContentResDTO> files) {
        return IaCFileDTO.BundleResDTO.builder()
            .files(files.stream()
                .map(this::applyCloudScope)
                .toList())
            .build();
    }

    private IaCFileDTO.FileContentResDTO applyCloudScope(
        IaCFileDTO.FileContentResDTO file
    ) {
        return IaCFileDTO.FileContentResDTO.builder()
            .fileName("cloud/" + file.fileName())
            .content(file.content())
            .build();
    }
}
