package mb.oauth2authorizationserver.archunit;

import com.enofex.taikai.Taikai;
import com.enofex.taikai.TaikaiRule;
import com.enofex.taikai.spring.ServicesConfigurer;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;

import java.util.Date;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@SpringBootConfiguration
class ArchitectureRulesTest {

    private static final Set<String> NULLABILITY_ANNOTATION_NAMES = Set.of("NonNull", "Nullable", "NullMarked", "NullUnmarked");
    private static final String JSPECIFY_PACKAGE = "org.jspecify.annotations";

    private static final ArchCondition<JavaClass> NOT_USE_NON_JSPECIFY_NULLABILITY_ANNOTATIONS =
            new ArchCondition<>("not use non-JSpecify nullability annotations") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    // Check class-level annotations
                    checkAnnotations(javaClass, javaClass.getAnnotations(), "Class", events);

                    // Check field annotations
                    for (JavaField field : javaClass.getFields()) {
                        checkAnnotations(javaClass, field.getAnnotations(), "Field " + field.getName(), events);
                    }

                    // Check method annotations and parameter annotations
                    for (JavaMethod method : javaClass.getMethods()) {
                        checkAnnotations(javaClass, method.getAnnotations(), "Method " + method.getName(), events);

                        for (JavaParameter parameter : method.getParameters()) {
                            checkAnnotations(javaClass, parameter.getAnnotations(), "Parameter of method " + method.getName(), events);
                        }
                    }

                    // Check constructor annotations and parameter annotations
                    for (JavaConstructor constructor : javaClass.getConstructors()) {
                        checkAnnotations(javaClass, constructor.getAnnotations(), "Constructor", events);

                        for (JavaParameter parameter : constructor.getParameters()) {
                            checkAnnotations(javaClass, parameter.getAnnotations(), "Parameter of constructor", events);
                        }
                    }
                }

                private void checkAnnotations(JavaClass javaClass, Set<? extends JavaAnnotation<?>> annotations, String location, ConditionEvents events) {
                    for (JavaAnnotation<?> annotation : annotations) {
                        String annotationName = annotation.getRawType().getName();
                        String simpleName = annotation.getRawType().getSimpleName();

                        if (NULLABILITY_ANNOTATION_NAMES.contains(simpleName) && !annotationName.startsWith(JSPECIFY_PACKAGE)) {
                            events.add(SimpleConditionEvent.violated(javaClass, String.format("%s in %s uses @%s from %s - only org.jspecify.annotations should be used", location, javaClass.getName(), simpleName, annotationName)));
                        }
                    }
                }
            };

    @Test
    void validateArchitecture() {
        Taikai.builder()
                .namespace("mb.oauth2authorizationserver")
                .addRule(TaikaiRule.of(classes().that().areEnums().should().resideInAnyPackage("..enums", "..exception..")))
                // JSpecify rule - nullability annotations must come from org.jspecify.annotations only
                .addRule(TaikaiRule.of(classes()
                        .should(NOT_USE_NON_JSPECIFY_NULLABILITY_ANNOTATIONS)
                        .as("Nullability annotations must come from org.jspecify.annotations only")))
                .addRule(TaikaiRule.of(classes()
                        .that().haveNameMatching(".*Request")
                        .should().resideInAnyPackage("..request..", "..controller..")
                        .as("Classes have name matching .*Request should reside in package ..request.. or ..controller..")))
                .addRule(TaikaiRule.of(classes()
                        .that().haveNameMatching(".*Response")
                        .should().resideInAnyPackage("..response..", "..controller..", "..exception..")
                        .as("Classes have name matching .*Response should reside in package ..response.., ..controller.., or ..exception..")))
                .addRule(TaikaiRule.of(classes()
                        .that().haveNameMatching(".*Utils")
                        .should().resideInAnyPackage("..util..", "..utils..")
                        .as("Classes have name matching .*Utils should reside in package ..util.. or ..utils..")))
                .addRule(TaikaiRule.of(classes()
                        .that().haveNameMatching(".*Config")
                        .should().resideInAnyPackage("..config..", "..request..")
                        .as("Classes have name matching .*Config should reside in package ..config.. or ..request..")))
                .java(java -> java
                        .noUsageOf(Date.class, "java.util")
                        // .classesShouldResideInPackage(".*Config", "..config..")
                        .classesShouldResideInPackage(".*Constants", "..constants")
                        .classesShouldResideInPackage(".*Controller", "..controller..")
                        .classesShouldResideInPackage(".*Repository", "..repository..")
                        .classesShouldResideInPackage(".*Specification", "..specification")
                        .classesAnnotatedWithShouldResideInPackage(Entity.class, "..data.entity.."))
                // .test(test -> test.junit(junit5 -> junit5.methodsShouldMatch(".*_Should.*_When.*")
                .test(test -> test
                        .junit(junit5 -> junit5
                                .methodsShouldContainAssertionsOrVerifications()
                                .methodsShouldBePackagePrivate()
                                // .classesShouldNotBeAnnotatedWithDisabled()
                                .methodsShouldNotBeAnnotatedWithDisabled()
                        )
                )
                .spring(spring -> spring
                        .noAutowiredFields()
                        .configurations(e -> e.namesShouldMatch(".*(Config|Properties)"))
                        .controllers(controllers -> controllers
                                .shouldBeAnnotatedWithController()
                                .namesShouldEndWithController()
                                .shouldNotDependOnOtherControllers())
                        .services(ServicesConfigurer::shouldNotDependOnControllers)
                        .repositories(repositories -> repositories
                                .namesShouldEndWithRepository()
                                // .shouldBeAnnotatedWithRepository()
                                .shouldNotDependOnServices()
                                .namesShouldEndWithRepository()))
                .build()
                .check();
    }
}
