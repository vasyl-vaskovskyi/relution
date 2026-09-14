package com.example.appstore;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Package dependency rules from {@code docs/architecture/overview.md} (ADR-0028). Don't weaken a rule to make code
 * pass; change the architecture decision instead. Empty packages are allowed while features are still being built.
 */
@AnalyzeClasses(packages = "com.example.appstore", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String API = "com.example.appstore.api..";
    private static final String AUTH = "com.example.appstore.auth..";
    private static final String CATALOG = "com.example.appstore.catalog..";
    private static final String INTEGRATION = "com.example.appstore.integration..";

    @ArchTest
    static final ArchRule catalogDependsOnNeitherApiNorIntegrationNorAuth = noClasses()
            .that()
            .resideInAPackage(CATALOG)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(API, INTEGRATION, AUTH)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule integrationDependsOnNeitherApiNorAuth = noClasses()
            .that()
            .resideInAPackage(INTEGRATION)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(API, AUTH)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule apiDependsOnNeitherIntegrationNorAuth = noClasses()
            .that()
            .resideInAPackage(API)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(INTEGRATION, AUTH)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule authDependsOnNeitherCatalogNorIntegration = noClasses()
            .that()
            .resideInAPackage(AUTH)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(CATALOG, INTEGRATION)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule authUsesOnlyTheProblemDetailsFactoryFromApi = noClasses()
            .that()
            .resideInAPackage(AUTH)
            .should()
            .dependOnClassesThat(resideInAPackage(API).and(not(simpleName("ProblemDetails"))))
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule featurePackagesAreFreeOfCycles = slices().matching("com.example.appstore.(*)..")
            .should()
            .beFreeOfCycles()
            .allowEmptyShould(true);
}
