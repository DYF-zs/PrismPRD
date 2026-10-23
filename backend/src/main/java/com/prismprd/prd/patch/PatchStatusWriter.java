package com.prismprd.prd.patch;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 以独立事务写补丁状态。
 *
 * 用于补丁应用失败（STALE_PATCH）时仍需落库的状态标记：
 * applyPatch 主事务会因异常回滚，若在主事务内 updateStatus，
 * STALE 标记会随之丢失，因此这里用 REQUIRES_NEW 开启独立事务提交。
 */
@Service
@RequiredArgsConstructor
public class PatchStatusWriter {

    private final PrdPatchMapper patchMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStale(UUID patchId) {
        patchMapper.updateStatus(patchId, PatchStatus.STALE.name());
    }
}
