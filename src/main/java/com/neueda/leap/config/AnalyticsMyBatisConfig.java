package com.neueda.leap.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.neueda.leap.mapper.OltpMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.mybatis.spring.boot.autoconfigure.SpringBootVFS;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Primary;

@Configuration
@MapperScans({
    @MapperScan(
        basePackages = "com.neueda.leap.mapper",
        markerInterface = OltpMapper.class,
        sqlSessionTemplateRef = "oltpSqlSessionTemplate"),
    @MapperScan(
        basePackages = "com.neueda.leap.mapper.analytics",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        sqlSessionTemplateRef = "analyticsSqlSessionTemplate")
})
public class AnalyticsMyBatisConfig implements DisposableBean {

    private final HikariDataSource analyticsDataSource;

    public AnalyticsMyBatisConfig(
            @Value("${analytics.datasource.url}") String url,
            @Value("${analytics.datasource.username}") String username,
            @Value("${analytics.datasource.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("analytics-mybatis");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setReadOnly(true);
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000);
        config.setInitializationFailTimeout(-1);
        this.analyticsDataSource = new HikariDataSource(config);
    }

    @Bean(name = "oltpSqlSessionFactory")
    @Primary
    public SqlSessionFactory oltpSqlSessionFactory(DataSource dataSource) throws Exception {
        return createSqlSessionFactory(dataSource);
    }

    @Bean(name = "analyticsSqlSessionFactory")
    public SqlSessionFactory analyticsSqlSessionFactory() throws Exception {
        return createSqlSessionFactory(analyticsDataSource);
    }

    @Bean(name = "oltpSqlSessionTemplate")
    @Primary
    public SqlSessionTemplate oltpSqlSessionTemplate(
            @Qualifier("oltpSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "analyticsSqlSessionTemplate")
    public SqlSessionTemplate analyticsSqlSessionTemplate(
            @Qualifier("analyticsSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    private SqlSessionFactory createSqlSessionFactory(DataSource dataSource) throws Exception {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultStatementTimeout(30);

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setTypeHandlersPackage("com.neueda.leap.config");
        factory.setVfs(SpringBootVFS.class);
        return factory.getObject();
    }

    @Override
    public void destroy() {
        analyticsDataSource.close();
    }
}
