package com.prismprd.review;

public interface ReviewAgent {

    ReviewAgentOutput review(ReviewContext context);

    String profile();
}
