package com.qs.ai.admian.service.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.qs.ai.admian.config.MilvusProperties;
import com.qs.ai.admian.exception.MilvusVectorException;
import com.qs.ai.admian.exception.ParamException;
import com.qs.ai.admian.service.MilvusVectorService;
import com.qs.ai.admian.service.dto.MilvusSearchResult;
import com.qs.ai.admian.service.dto.MilvusVectorRecord;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Milvus vector storage implementation for knowledge chunks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusVectorServiceImpl implements MilvusVectorService {

    private static final String CHUNK_ID_FIELD = "chunk_id";
    private static final String FILE_ID_FIELD = "file_id";
    private static final String CHUNK_INDEX_FIELD = "chunk_index";
    private static final String CONTENT_FIELD = "content";
    private static final String VECTOR_FIELD = "embedding";

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties milvusProperties;

    @PostConstruct
    public void initCollection() {
        if (Boolean.TRUE.equals(milvusProperties.getAutoCreateCollection())) {
            createCollectionIfAbsent();
        }
    }

    @Override
    public boolean hasCollection() {
        try {
            return milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    .build());
        } catch (Exception ex) {
            throw toMilvusException("check collection existence", ex);
        }
    }

    @Override
    public void createCollectionIfAbsent() {
        if (hasCollection()) {
            return;
        }

        try {
            CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
            schema.addField(AddFieldReq.builder()
                    .fieldName(CHUNK_ID_FIELD)
                    .dataType(DataType.VarChar)
                    .maxLength(64)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(FILE_ID_FIELD)
                    .dataType(DataType.Int64)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(CHUNK_INDEX_FIELD)
                    .dataType(DataType.Int32)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(CONTENT_FIELD)
                    .dataType(DataType.VarChar)
                    .maxLength(resolveContentMaxLength())
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(VECTOR_FIELD)
                    .dataType(DataType.FloatVector)
                    .dimension(resolveDimension())
                    .build());

            milvusClient.createCollection(CreateCollectionReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    .description("QS AI knowledge chunks for RAG retrieval")
                    .collectionSchema(schema)
                    .build());

            milvusClient.createIndex(CreateIndexReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    .indexParams(List.of(IndexParam.builder()
                            .fieldName(VECTOR_FIELD)
                            .indexName(VECTOR_FIELD + "_idx")
                            .indexType(resolveIndexType())
                            .metricType(resolveMetricType())
                            .build()))
                    .sync(true)
                    .build());

            milvusClient.loadCollection(LoadCollectionReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    .sync(true)
                    .build());
            log.info("Milvus collection created and loaded, collection={}, dimension={}",
                    milvusProperties.getCollectionName(), resolveDimension());
        } catch (Exception ex) {
            throw toMilvusException("create collection", ex);
        }
    }

    @Override
    public long upsert(MilvusVectorRecord record) {
        return upsertBatch(List.of(record));
    }

    @Override
    public long upsertBatch(List<MilvusVectorRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new ParamException("vector records must not be empty");
        }

        try {
            createCollectionIfAbsent();
            List<JsonObject> data = records.stream()
                    .map(this::toJsonObject)
                    .toList();
            UpsertResp response = milvusClient.upsert(UpsertReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    .data(data)
                    .build());
            flush();
            return response.getUpsertCnt();
        } catch (MilvusVectorException | ParamException ex) {
            throw ex;
        } catch (Exception ex) {
            throw toMilvusException("upsert vector records", ex);
        }
    }

    @Override
    public List<MilvusSearchResult> search(float[] queryVector, int topK) {
        validateVector(queryVector);
        int safeTopK = topK <= 0 ? 5 : topK;

        try {
            createCollectionIfAbsent();
            SearchResp response = milvusClient.search(SearchReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    .annsField(VECTOR_FIELD)
                    .metricType(resolveMetricType())
                    .topK(safeTopK)
                    .data(List.of(new FloatVec(queryVector)))
                    .outputFields(List.of(CHUNK_ID_FIELD, FILE_ID_FIELD, CHUNK_INDEX_FIELD, CONTENT_FIELD))
                    .build());

            if (response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
                return List.of();
            }
            return response.getSearchResults().get(0).stream()
                    .map(this::toSearchResult)
                    .toList();
        } catch (MilvusVectorException | ParamException ex) {
            throw ex;
        } catch (Exception ex) {
            throw toMilvusException("search vectors", ex);
        }
    }

    @Override
    public List<MilvusSearchResult> queryByFileId(Long fileId, int limit) {
        if (fileId == null) {
            throw new ParamException("fileId must not be null");
        }

        try {
            QueryResp response = milvusClient.query(QueryReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    .filter(FILE_ID_FIELD + " == " + fileId)
                    .limit(limit <= 0 ? 100 : limit)
                    .outputFields(List.of(CHUNK_ID_FIELD, FILE_ID_FIELD, CHUNK_INDEX_FIELD, CONTENT_FIELD))
                    .build());
            if (response.getQueryResults() == null) {
                return List.of();
            }
            return response.getQueryResults().stream()
                    .map(item -> toSearchResult(item.getEntity(), null))
                    .toList();
        } catch (ParamException ex) {
            throw ex;
        } catch (Exception ex) {
            throw toMilvusException("query vectors by file id", ex);
        }
    }

    @Override
    public long deleteByChunkId(String chunkId) {
        if (!StringUtils.hasText(chunkId)) {
            throw new ParamException("chunkId must not be blank");
        }
        return delete(DeleteReq.builder()
                .collectionName(milvusProperties.getCollectionName())
                .ids(List.of(chunkId))
                .build());
    }

    @Override
    public long deleteByFileId(Long fileId) {
        if (fileId == null) {
            throw new ParamException("fileId must not be null");
        }
        return delete(DeleteReq.builder()
                .collectionName(milvusProperties.getCollectionName())
                .filter(FILE_ID_FIELD + " == " + fileId)
                .build());
    }

    private long delete(DeleteReq request) {
        try {
            DeleteResp response = milvusClient.delete(request);
            flush();
            return response.getDeleteCnt();
        } catch (Exception ex) {
            throw toMilvusException("delete vector records", ex);
        }
    }

    private JsonObject toJsonObject(MilvusVectorRecord record) {
        validateRecord(record);
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(CHUNK_ID_FIELD, record.chunkId());
        jsonObject.addProperty(FILE_ID_FIELD, record.fileId());
        jsonObject.addProperty(CHUNK_INDEX_FIELD, record.chunkIndex());
        jsonObject.addProperty(CONTENT_FIELD, truncateContent(record.content()));

        JsonArray vector = new JsonArray();
        for (float value : record.vector()) {
            vector.add(value);
        }
        jsonObject.add(VECTOR_FIELD, vector);
        return jsonObject;
    }

    private void validateRecord(MilvusVectorRecord record) {
        if (record == null) {
            throw new ParamException("vector record must not be null");
        }
        if (!StringUtils.hasText(record.chunkId())) {
            throw new ParamException("chunkId must not be blank");
        }
        if (record.fileId() == null) {
            throw new ParamException("fileId must not be null");
        }
        if (record.chunkIndex() == null) {
            throw new ParamException("chunkIndex must not be null");
        }
        if (!StringUtils.hasText(record.content())) {
            throw new ParamException("content must not be blank");
        }
        validateVector(record.vector());
    }

    private void validateVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new ParamException("vector must not be empty");
        }
        if (vector.length != resolveDimension()) {
            throw new ParamException("vector dimension mismatch, expected="
                    + resolveDimension() + ", actual=" + vector.length);
        }
    }

    private MilvusSearchResult toSearchResult(SearchResp.SearchResult result) {
        return toSearchResult(result.getEntity(), result.getScore());
    }

    private MilvusSearchResult toSearchResult(Map<String, Object> entity, Float score) {
        return new MilvusSearchResult(
                getString(entity, CHUNK_ID_FIELD),
                getLong(entity, FILE_ID_FIELD),
                getInteger(entity, CHUNK_INDEX_FIELD),
                getString(entity, CONTENT_FIELD),
                score
        );
    }

    private String getString(Map<String, Object> entity, String fieldName) {
        Object value = entity.get(fieldName);
        return value == null ? null : String.valueOf(value);
    }

    private Long getLong(Map<String, Object> entity, String fieldName) {
        Object value = entity.get(fieldName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private Integer getInteger(Map<String, Object> entity, String fieldName) {
        Object value = entity.get(fieldName);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private void flush() {
        milvusClient.flush(FlushReq.builder()
                .collectionNames(List.of(milvusProperties.getCollectionName()))
                .build());
    }

    private String truncateContent(String content) {
        int maxLength = resolveContentMaxLength();
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength);
    }

    private int resolveDimension() {
        Integer dimension = milvusProperties.getDimension();
        return dimension == null || dimension <= 0 ? 1024 : dimension;
    }

    private int resolveContentMaxLength() {
        Integer maxLength = milvusProperties.getContentMaxLength();
        return maxLength == null || maxLength <= 0 ? 8192 : maxLength;
    }

    private IndexParam.MetricType resolveMetricType() {
        try {
            return IndexParam.MetricType.valueOf(milvusProperties.getMetricType().toUpperCase());
        } catch (Exception ex) {
            throw new ParamException("unsupported Milvus metricType: " + milvusProperties.getMetricType());
        }
    }

    private IndexParam.IndexType resolveIndexType() {
        try {
            return IndexParam.IndexType.valueOf(milvusProperties.getIndexType().toUpperCase());
        } catch (Exception ex) {
            throw new ParamException("unsupported Milvus indexType: " + milvusProperties.getIndexType());
        }
    }

    private MilvusVectorException toMilvusException(String operation, Exception ex) {
        log.error("Failed to {}, collection={}", operation, milvusProperties.getCollectionName(), ex);
        return new MilvusVectorException("Milvus " + operation + " failed: " + ex.getMessage());
    }
}
