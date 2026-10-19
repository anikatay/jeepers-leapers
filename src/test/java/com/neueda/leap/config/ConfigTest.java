package com.neueda.leap.config;

import org.apache.ibatis.type.JdbcType;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigTest {

    // Verifies KafkaTopicConfig creates the expected topic name and topology.
    @Test
    void testTradeExecutedTopicBeanConfiguration() {
        KafkaTopicConfig config = new KafkaTopicConfig();

        NewTopic topic = config.tradeExecutedTopic();

        assertEquals("trade-executed", topic.name());
        assertEquals(1, topic.numPartitions());
        assertEquals((short) 1, topic.replicationFactor());
    }

    // Verifies UUIDTypeHandler writes UUID parameters to PreparedStatement.
    @Test
    void testUuidTypeHandlerSetNonNullParameter() throws Exception {
        UUIDTypeHandler handler = new UUIDTypeHandler();
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        UUID id = UUID.randomUUID();

        handler.setNonNullParameter(preparedStatement, 1, id, JdbcType.OTHER);

        verify(preparedStatement).setObject(1, id);
    }

    // Verifies UUIDTypeHandler reads UUID by column name from ResultSet.
    @Test
    void testUuidTypeHandlerGetNullableResultByColumnName() throws Exception {
        UUIDTypeHandler handler = new UUIDTypeHandler();
        ResultSet resultSet = mock(ResultSet.class);
        UUID id = UUID.randomUUID();

        when(resultSet.getObject("user_id")).thenReturn(id.toString());

        UUID result = handler.getNullableResult(resultSet, "user_id");

        assertEquals(id, result);
    }

    // Verifies UUIDTypeHandler returns null when ResultSet column-name value is null.
    @Test
    void testUuidTypeHandlerGetNullableResultByColumnNameReturnsNull() throws Exception {
        UUIDTypeHandler handler = new UUIDTypeHandler();
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.getObject("user_id")).thenReturn(null);

        UUID result = handler.getNullableResult(resultSet, "user_id");

        assertNull(result);
    }

    // Verifies UUIDTypeHandler reads UUID by column index from ResultSet.
    @Test
    void testUuidTypeHandlerGetNullableResultByColumnIndex() throws Exception {
        UUIDTypeHandler handler = new UUIDTypeHandler();
        ResultSet resultSet = mock(ResultSet.class);
        UUID id = UUID.randomUUID();

        when(resultSet.getObject(2)).thenReturn(id.toString());

        UUID result = handler.getNullableResult(resultSet, 2);

        assertEquals(id, result);
    }

    // Verifies UUIDTypeHandler returns null when ResultSet column-index value is null.
    @Test
    void testUuidTypeHandlerGetNullableResultByColumnIndexReturnsNull() throws Exception {
        UUIDTypeHandler handler = new UUIDTypeHandler();
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.getObject(2)).thenReturn(null);

        UUID result = handler.getNullableResult(resultSet, 2);

        assertNull(result);
    }

    // Verifies UUIDTypeHandler reads UUID from CallableStatement.
    @Test
    void testUuidTypeHandlerGetNullableResultFromCallableStatement() throws Exception {
        UUIDTypeHandler handler = new UUIDTypeHandler();
        CallableStatement callableStatement = mock(CallableStatement.class);
        UUID id = UUID.randomUUID();

        when(callableStatement.getObject(1)).thenReturn(id.toString());

        UUID result = handler.getNullableResult(callableStatement, 1);

        assertEquals(id, result);
    }

    // Verifies UUIDTypeHandler returns null when CallableStatement value is null.
    @Test
    void testUuidTypeHandlerGetNullableResultFromCallableStatementReturnsNull() throws Exception {
        UUIDTypeHandler handler = new UUIDTypeHandler();
        CallableStatement callableStatement = mock(CallableStatement.class);

        when(callableStatement.getObject(1)).thenReturn(null);

        UUID result = handler.getNullableResult(callableStatement, 1);

        assertNull(result);
    }
}
