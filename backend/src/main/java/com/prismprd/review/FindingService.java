package com.prismprd.review;

import com.prismprd.common.NotFoundException;
import com.prismprd.security.PermissionService;
import com.prismprd.web.dto.FindingDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FindingService {

    private final ReviewFindingMapper findingMapper;
    private final ReviewReportMapper reportMapper;
    private final ReviewJobMapper reviewJobMapper;
    private final PermissionService permissionService;
    private final ReviewService reviewService;

    public FindingDetail updateStatus(UUID findingId, FindingStatus status, UUID userId) {
        ReviewFinding finding = findingMapper.selectById(findingId);
        if (finding == null) {
            throw new NotFoundException("Finding 不存在");
        }
        ReviewReport report = reportMapper.selectById(finding.getReportId());
        if (report == null) {
            throw new NotFoundException("评审报告不存在");
        }
        ReviewJob job = reviewJobMapper.selectById(report.getReviewJobId());
        if (job == null) {
            throw new NotFoundException("评审任务不存在");
        }
        permissionService.requireReview(job.getPrdId(), userId);

        findingMapper.updateStatus(findingId, status.name());
        ReviewFinding updated = findingMapper.selectById(findingId);

        return reviewService.toFindingDetail(updated);
    }
}
