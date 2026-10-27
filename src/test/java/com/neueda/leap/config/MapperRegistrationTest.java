package com.neueda.leap.config;

import com.neueda.leap.mapper.HunterSpecMapper;
import com.neueda.leap.mapper.OltpMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AnalyticsMyBatisConfig registers a mapper in com.neueda.leap.mapper only if it extends OltpMapper.
 * One that doesn't is skipped without a warning, and the app then fails to start because a service
 * cannot get it. No other test starts the whole app, so this one checks the rule directly.
 */
class MapperRegistrationTest {

    private static final String MAPPER_PACKAGE = "com.neueda.leap.mapper";

    @Test
    void everyOltpMapperExtendsTheMarkerSoMyBatisRegistersIt() throws ClassNotFoundException {
        List<Class<?>> mappers = mapperInterfaces();

        assertTrue(mappers.contains(HunterSpecMapper.class), "the scan found no mappers: " + mappers);
        List<String> skipped = mappers.stream()
                .filter(mapper -> !OltpMapper.class.isAssignableFrom(mapper))
                .map(Class::getSimpleName)
                .toList();
        assertEquals(List.of(), skipped,
                "these mappers must extend OltpMapper, or AnalyticsMyBatisConfig skips them and the app cannot start");
    }

    /** Interfaces directly in the mapper package, other than the marker; analytics mappers live below it. */
    private static List<Class<?>> mapperInterfaces() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter((reader, factory) -> true);
        Set<BeanDefinition> found = scanner.findCandidateComponents(MAPPER_PACKAGE);

        List<Class<?>> mappers = new ArrayList<>();
        for (BeanDefinition definition : found) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (type.getPackageName().equals(MAPPER_PACKAGE) && type != OltpMapper.class) {
                mappers.add(type);
            }
        }
        return mappers;
    }
}
