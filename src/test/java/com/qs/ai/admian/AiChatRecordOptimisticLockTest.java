package com.qs.ai.admian;

import com.qs.ai.admian.entity.AiChatRecord;
import com.qs.ai.admian.mapper.AiChatRecordMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Verify optimistic lock behavior on ai_chat_record updates.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiChatRecordOptimisticLockTest {

    @Autowired
    private AiChatRecordMapper aiChatRecordMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRollbackSecondConcurrentUpdateByVersionCheck() {
        printTableStructure();

        AiChatRecord record = new AiChatRecord();
        record.setConversationId("conv-lock-001");
        record.setUserId(1L);
        record.setRoleType("user");
        record.setChatTime(LocalDateTime.now());
        record.setContent("first message");
        record.setDeleted(0);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setVersion(1);
        int inserted = aiChatRecordMapper.insert(record);
        Assertions.assertEquals(1, inserted);
        Assertions.assertNotNull(record.getId());
        printRecord("Inserted", aiChatRecordMapper.selectById(record.getId()));

        AiChatRecord txA = aiChatRecordMapper.selectById(record.getId());
        AiChatRecord txB = aiChatRecordMapper.selectById(record.getId());
        printRecord("Loaded by txA", txA);
        printRecord("Loaded by txB", txB);
        Assertions.assertEquals(1, txA.getVersion());
        Assertions.assertEquals(1, txB.getVersion());

        txA.setContent("updated by txA");
        int updateA = aiChatRecordMapper.updateById(txA);
        Assertions.assertEquals(1, updateA);
        printRecord("After txA update", aiChatRecordMapper.selectById(record.getId()));

        txB.setContent("updated by txB");
        int updateB = aiChatRecordMapper.updateById(txB);
        Assertions.assertEquals(0, updateB);
        System.out.println("Second update result (should be 0 due to optimistic lock): " + updateB);

        AiChatRecord latest = aiChatRecordMapper.selectById(record.getId());
        printRecord("Latest", latest);
        Assertions.assertEquals("updated by txA", latest.getContent());
        Assertions.assertEquals(2, latest.getVersion());
    }

    private void printTableStructure() {
        System.out.println("==== ai_chat_record table structure ====");
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT " +
                        "FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE UPPER(TABLE_NAME) = 'AI_CHAT_RECORD' ORDER BY ORDINAL_POSITION"
        );
        if (columns.isEmpty()) {
            System.out.println("No columns found for ai_chat_record in INFORMATION_SCHEMA.COLUMNS");
            return;
        }
        for (Map<String, Object> column : columns) {
            System.out.printf(
                    "column=%s, type=%s, nullable=%s, default=%s%n",
                    column.get("COLUMN_NAME"),
                    column.get("DATA_TYPE"),
                    column.get("IS_NULLABLE"),
                    column.get("COLUMN_DEFAULT")
            );
        }
    }

    private void printRecord(String stage, AiChatRecord record) {
        if (record == null) {
            System.out.println(stage + ": null");
            return;
        }
        System.out.printf(
                "%s => id=%d, conversationId=%s, userId=%d, content=%s, version=%d%n",
                stage,
                record.getId(),
                record.getConversationId(),
                record.getUserId(),
                record.getContent(),
                record.getVersion()
        );
    }
}
