package pbl6.ctsv.drl;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import pbl6.ctsv.CtsvApplication;
import pbl6.ctsv.drl.config.DrlConfiguration;
import pbl6.ctsv.drl.config.DrlPhysicalNamingStrategy;
import pbl6.ctsv.drl.repository.DrlRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DrlPhysicalNamingStrategyTest {
    private static final String METADATA_STRATEGY = "hibernate.hbm2ddl.jdbc_metadata_extraction_strategy";

    private final ApplicationContextRunner configuration =
            new ApplicationContextRunner().withUserConfiguration(DrlConfiguration.class);

    @Test
    void preservesExplicitDrlIdentifiersAndDelegatesOtherNames() {
        var strategy = new DrlPhysicalNamingStrategy();
        var boot = new CamelCaseToUnderscoresNamingStrategy();
        var table = Identifier.toIdentifier("`RLKetQuaSV`");
        var column = Identifier.toIdentifier("`NgayLapHoSo`");

        assertThat(strategy.toPhysicalTableName(table, null)).isEqualTo(table);
        assertThat(strategy.toPhysicalColumnName(column, null)).isEqualTo(column);

        for (String name : List.of("RLLopDot", "NgayLapHoSo", "`OtherCamelCase`")) {
            var id = Identifier.toIdentifier(name);
            assertThat(strategy.toPhysicalTableName(id, null)).isEqualTo(boot.toPhysicalTableName(id, null));
            assertThat(strategy.toPhysicalColumnName(id, null)).isEqualTo(boot.toPhysicalColumnName(id, null));
        }
    }

    @Test
    void leavesExistingNonDrlMappingNamesUnchanged() throws ClassNotFoundException {
        var strategy = new DrlPhysicalNamingStrategy();
        var boot = new CamelCaseToUnderscoresNamingStrategy();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(MappedSuperclass.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Embeddable.class));

        int checked = 0;
        for (var candidate : scanner.findCandidateComponents("pbl6.ctsv")) {
            String name = candidate.getBeanClassName();
            if (name.startsWith("pbl6.ctsv.drl.")) {
                continue;
            }
            Class<?> type = Class.forName(name, false, getClass().getClassLoader());
            var table = type.getAnnotation(Table.class);
            if (table != null) {
                var id = Identifier.toIdentifier(table.name());
                assertThat(strategy.toPhysicalTableName(id, null)).as(name)
                        .isEqualTo(boot.toPhysicalTableName(id, null));
                checked++;
            }
            for (var field : type.getDeclaredFields()) {
                var column = field.getAnnotation(Column.class);
                var join = field.getAnnotation(JoinColumn.class);
                if (column != null || join != null) {
                    var id = Identifier.toIdentifier(column != null ? column.name() : join.name());
                    assertThat(strategy.toPhysicalColumnName(id, null)).as("%s.%s", name, field.getName())
                            .isEqualTo(boot.toPhysicalColumnName(id, null));
                    checked++;
                }
            }
        }
        assertThat(checked).isGreaterThan(100);
    }

    @Test
    void usesMetadataDefaultWithoutOverridingAnExplicitSetting() {
        configuration.run(context -> {
            assertThat(context).hasSingleBean(PhysicalNamingStrategy.class)
                    .hasSingleBean(HibernatePropertiesCustomizer.class);
            assertThat(context.getBean(PhysicalNamingStrategy.class)).isInstanceOf(DrlPhysicalNamingStrategy.class);
            var customizer = context.getBean(HibernatePropertiesCustomizer.class);
            Map<String, Object> defaults = new HashMap<>();
            Map<String, Object> explicit = new HashMap<>(Map.of(METADATA_STRATEGY, "grouped"));

            customizer.customize(defaults);
            customizer.customize(explicit);

            assertThat(defaults).containsEntry(METADATA_STRATEGY, "individually");
            assertThat(explicit).containsEntry(METADATA_STRATEGY, "grouped");
        });
    }

    @Test
    void wiresRepositoryAfterBootJdbcWhenTheSharedFactoryExists() {
        var factory = mock(EntityManagerFactory.class);
        var jdbcAutoConfiguration = configuration
                .withConfiguration(AutoConfigurations.of(JdbcTemplateAutoConfiguration.class))
                .withBean(DataSource.class, () -> mock(DataSource.class));

        configuration.withBean(EntityManagerFactory.class, () -> factory)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(DrlRepository.class));
        jdbcAutoConfiguration.run(context -> assertThat(context).hasSingleBean(JdbcTemplate.class)
                .doesNotHaveBean(DrlRepository.class));
        jdbcAutoConfiguration.withBean(EntityManagerFactory.class, () -> factory)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(DrlRepository.class)
                        .doesNotHaveBean(EntityManager.class));

        verify(factory, never()).createEntityManager();
    }

    @Nested
    @SpringBootTest(classes = CtsvApplication.class)
    @ContextConfiguration(initializers = ProductionComponentResources.class)
    class DefaultApplicationContext {
        @Autowired
        private ApplicationContext context;

        @Test
        void startsProductionApplicationWithPersistenceDisabledByDefault() {
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            assertThat(context.getEnvironment().getProperty("spring.application.name")).isEqualTo("ctsv");
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
            assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
            assertThat(context.getBeansOfType(EntityManagerFactory.class)).isEmpty();
            assertThat(context.getBeansOfType(DrlRepository.class)).isEmpty();
            assertThat(context.getBean(PhysicalNamingStrategy.class)).isInstanceOf(DrlPhysicalNamingStrategy.class);
        }
    }

    static final class ProductionComponentResources implements ApplicationContextInitializer<GenericApplicationContext> {
        @Override
        public void initialize(GenericApplicationContext context) {
            // The Maven test output contains component-annotated fixtures. Keep the
            // application scan and remove only class resources from that output.
            String testOutput = DrlPhysicalNamingStrategyTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toExternalForm();
            context.setResourceLoader(new PathMatchingResourcePatternResolver(context.getClassLoader()) {
                @Override
                public Resource[] getResources(String pattern) throws IOException {
                    Resource[] resources = super.getResources(pattern);
                    if (!pattern.startsWith("classpath*:pbl6/ctsv/") || !pattern.endsWith(".class")) {
                        return resources;
                    }
                    var production = new ArrayList<Resource>(resources.length);
                    for (Resource resource : resources) {
                        if (!resource.getURL().toExternalForm().startsWith(testOutput)) {
                            production.add(resource);
                        }
                    }
                    return production.toArray(Resource[]::new);
                }
            });
        }
    }
}
