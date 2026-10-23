package com.prismprd.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateFindingRequest {

    @NotBlank
    private String status;
}
