package com.prismprd.knowledge;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface KnowledgeChunkMapper {
    void insert(@Param("id") UUID id, @Param("documentId") UUID documentId,
                @Param("chunk") KnowledgeTypes.Chunk chunk, @Param("index") int index,
                @Param("embedding") double[] embedding);

    List<KnowledgeTypes.Hit> search(@Param("prd") UUID prd, @Param("query") double[] query,
                                  @Param("model") String model, @Param("dimensions") int dimensions,
                                  @Param("limit") int limit);
}
