package com.neueda.leap.config;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.JdbcType;


@MappedTypes(UUID.class)
public class UUIDTypeHandler extends BaseTypeHandler<UUID>{
    @Override 
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter,JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object obj = rs.getObject(columnName);
        return obj == null ? null : UUID.fromString(obj.toString());
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object obj = rs.getObject(columnIndex);
        return obj == null ? null : UUID.fromString(obj.toString());
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object obj = cs.getObject(columnIndex);
        return obj == null ? null : UUID.fromString(obj.toString());
    }
}
