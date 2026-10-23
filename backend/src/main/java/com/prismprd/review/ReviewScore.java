package com.prismprd.review;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ReviewScore {

    private UUID id;
    private UUID reportId;
    private String dimensionKey;
    private Boolean applicable;
    private BigDecimal score;
    private String rationale;
    private String evidence;
    private String suggestions;
    private Integer displayOrder;
}
