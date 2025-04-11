package mb.oauth2authorizationserver.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class ArchitectureTest {

    private JavaClasses importedClasses;

    @BeforeEach
    void setup() {
        importedClasses = new ClassFileImporter().importPackages(ArchitectureConstants.DEFAULT_PACKAGE);
    }

    @Test
    void controller_ShouldHaveControllerSuffix_WhenAnnotatedWithControllerAnnotations() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Controller.class)
                .should().haveSimpleNameEndingWith(ArchitectureConstants.CONTROLLER_SUFFIX);

        rule.check(importedClasses);
    }

    @Test
    void serviceImpl_ShouldHaveImplSuffix_WhenServiceImplementation() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(Service.class)
                .and().haveSimpleNameContaining("Impl")
                .should().haveSimpleNameEndingWith("Impl");

        rule.check(importedClasses);
    }

    @Test
    void service_ShouldHaveServiceSuffix_WhenAnnotatedWithServiceAnnotation() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(Service.class)
                .and().resideOutsideOfPackages(
                        "..security..",
                        "..impl.."
                )
                .and().haveSimpleNameNotContaining("Impl")
                .should().haveSimpleNameEndingWith(ArchitectureConstants.SERVICE_SUFFIX)
                .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    void serviceImpl_ShouldHaveImplSuffix_WhenInSecurityPackage() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(Service.class)
                .and().resideInAnyPackage(
                        "..security.builder.impl..",
                        "..security.service.impl..",
                        "..service.impl.."
                )
                .should().haveSimpleNameEndingWith("Impl");

        rule.check(importedClasses);
    }

    @Test
    void securityProvider_ShouldHaveProviderSuffix_WhenInProviderPackage() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(Service.class)
                .and().resideInAPackage("..security.provider..")
                .should().haveSimpleNameEndingWith("Provider");

        rule.check(importedClasses);
    }

    @Test
    void repository_ShouldHaveRepositorySuffix_WhenAnnotatedWithRepositoryAnnotation() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(Repository.class)
                .should().haveSimpleNameEndingWith(ArchitectureConstants.REPOSITORY_SUFFIX);

        rule.check(importedClasses);
    }

    @Test
    void architecture_ShouldRespectLayerDependencies_WhenApplicationStartup() {
        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Config").definedBy("..config..")
                .layer("Security").definedBy("..config.security..")
                .layer("Controller").definedBy("..controller..")
                .layer("Service").definedBy("..service..")
                .layer("Repository").definedBy("..repository..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Security", "Config")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service", "Security", "Config")
                // Exclude all generated bean definitions and registrations
                .ignoreDependency(
                        JavaClass.Predicates.simpleNameContaining("__BeanFactoryRegistrations"),
                        JavaClass.Predicates.resideInAnyPackage("..controller..", "..service..", "..repository..")
                )
                .ignoreDependency(
                        JavaClass.Predicates.simpleNameContaining("__BeanDefinitions"),
                        JavaClass.Predicates.resideInAnyPackage("..controller..", "..service..", "..repository..")
                )
                .ignoreDependency(
                        JavaClass.Predicates.assignableTo(JavaClass.Predicates.simpleName("OAuth2AuthenticationFlowIntegrationTest")),
                        JavaClass.Predicates.resideInAPackage("..repository..")
                );

        rule.check(importedClasses);
    }

    @Test
    void controller_ShouldResideInControllerPackage_WhenAnnotatedWithControllerAnnotations() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Controller.class)
                .should().resideInAPackage(ArchitectureConstants.PACKAGE_CONTROLLER);

        rule.check(importedClasses);
    }

    @Test
    void service_ShouldResideInServicePackage_WhenAnnotatedWithServiceAnnotation() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(Service.class)
                .should().resideInAnyPackage(
                        ArchitectureConstants.PACKAGE_SERVICE,
                        ArchitectureConstants.PACKAGE_SECURITY_BUILDER_IMPL,
                        ArchitectureConstants.PACKAGE_SECURITY_PROVIDER
                );

        rule.check(importedClasses);
    }

    @Test
    void repository_ShouldResideInRepositoryPackage_WhenAnnotatedWithRepositoryAnnotation() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(Repository.class)
                .should().resideInAPackage(ArchitectureConstants.PACKAGE_REPOSITORY);

        rule.check(importedClasses);
    }

    @Test
    void service_ShouldNotAccessController_WhenCheckingDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ArchitectureConstants.PACKAGE_SERVICE)
                .should().dependOnClassesThat().resideInAPackage(ArchitectureConstants.PACKAGE_CONTROLLER);

        rule.check(importedClasses);
    }

    @Test
    void repository_ShouldNotAccessService_WhenCheckingDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ArchitectureConstants.PACKAGE_REPOSITORY)
                .should().dependOnClassesThat().resideInAPackage(ArchitectureConstants.PACKAGE_SERVICE);

        rule.check(importedClasses);
    }
}
