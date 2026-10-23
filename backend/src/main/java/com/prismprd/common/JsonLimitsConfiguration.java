package com.prismprd.common;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonLimitsConfiguration {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer jsonLimits() {
        return builder -> builder.featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .postConfigurer(mapper -> mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(160).maxStringLength(500000).build()));
    }
}
