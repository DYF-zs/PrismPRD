package com.prismprd.model;

import org.springframework.ai.chat.model.ChatModel;

public interface ModelAdapter {
    String provider();
    ChatModel create(String model, String baseUrl, String apiKey);
}

