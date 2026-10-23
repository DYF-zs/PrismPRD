package com.prismprd.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WorkspacePageController {
    @GetMapping({"/prds", "/prds/{prdId}", "/settings/review-rubrics", "/settings/models"})
    public String workspace() {
        return "forward:/index.html";
    }
}
