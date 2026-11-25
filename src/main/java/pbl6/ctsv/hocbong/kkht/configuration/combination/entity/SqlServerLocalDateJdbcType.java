package pbl6.ctsv.hocbong.kkht.configuration.combination.entity;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.LocalDateJdbcType;

/** SQL Server JDBC 12 converts LocalDate through GregorianCalendar on write. Bind unambiguous
 * ISO yyyyMMdd instead, preserving SQL DATE's full range; retain direct LocalDate extraction.
 * Only the two scholarship effective dates opt in. No global Hibernate type replacement. */
public final class SqlServerLocalDateJdbcType extends LocalDateJdbcType {
    @Override
    public <T> ValueBinder<T> getBinder(JavaType<T> javaType) {
        return new BasicBinder<>(javaType,this) {
            private String iso(T value,WrapperOptions options) {
                return javaType.unwrap(value,LocalDate.class,options).format(DateTimeFormatter.BASIC_ISO_DATE);
            }
            @Override protected void doBind(PreparedStatement statement,T value,int index,WrapperOptions options)
                    throws SQLException {
                statement.setString(index,iso(value,options));
            }
            @Override protected void doBind(CallableStatement statement,T value,String name,WrapperOptions options)
                    throws SQLException {
                statement.setString(name,iso(value,options));
            }
            @Override protected void doBindNull(PreparedStatement statement,int index,WrapperOptions options)
                    throws SQLException {
                statement.setNull(index,Types.DATE);
            }
            @Override protected void doBindNull(CallableStatement statement,String name,WrapperOptions options)
                    throws SQLException {
                statement.setNull(name,Types.DATE);
            }
        };
    }
}
