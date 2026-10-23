package com.prismprd.common.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.StringJoiner;

@MappedTypes(double[].class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = true)
public class PgVectorTypeHandler extends BaseTypeHandler<double[]> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, double[] vector, JdbcType jdbcType)
            throws SQLException {
        if (vector.length == 0 || vector.length > 16000) throw new SQLException("Invalid vector dimensions");
        var text = new StringJoiner(",", "[", "]");
        boolean nonzero = false;
        for (double value : vector) {
            float stored = (float) value;
            if (!Float.isFinite(stored)) throw new SQLException("Vector elements must be finite float32 values");
            nonzero |= stored != 0;
            text.add(Float.toString(stored));
        }
        if (!nonzero) throw new SQLException("Cosine search requires a nonzero vector");
        ps.setObject(i, text.toString(), Types.OTHER);
    }

    @Override
    public double[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public double[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public double[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private double[] parse(String text) throws SQLException {
        if (text == null) return null;
        if (!text.startsWith("[") || !text.endsWith("]")) throw new SQLException("Invalid vector representation");
        String[] elements = text.substring(1, text.length() - 1).split(",", -1);
        double[] vector = new double[elements.length];
        try {
            for (int i = 0; i < elements.length; i++) {
                vector[i] = Float.parseFloat(elements[i]);
                if (!Double.isFinite(vector[i])) throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new SQLException("Invalid vector element", e);
        }
        return vector;
    }
}
