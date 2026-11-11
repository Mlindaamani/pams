package com.company.procurement.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(
    packages = "com.company.procurement",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String BASE = "com.company.procurement.";
  private static final List<String> MODULES =
      List.of(
          "organization",
          "vendor",
          "catalog",
          "budget",
          "request",
          "approval",
          "purchaseorder",
          "receiving",
          "notification",
          "reporting",
          "audit");

  @ArchTest
  static final ArchRule no_field_injection =
      fields()
          .should()
          .notBeAnnotatedWith(Autowired.class)
          .because("constructor injection is mandatory");

  @ArchTest
  static final ArchRule domain_is_independent =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..api..", "..application..", "..infrastructure..");

  @ArchTest
  static final ArchRule application_does_not_depend_on_web_or_adapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..api..", "..infrastructure..");

  @ArchTest
  static final ArchRule api_does_not_depend_on_infrastructure =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure..");

  @ArchTest
  static final ArchRule controllers_do_not_use_repositories =
      noClasses()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(Repository.class);

  @ArchTest
  static final ArchRule controllers_are_named_controllers =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .haveSimpleNameEndingWith("Controller");

  @ArchTest
  static final ArchRule services_are_named_services =
      classes()
          .that()
          .resideInAPackage("..application..")
          .and()
          .areAnnotatedWith(Service.class)
          .should()
          .haveSimpleNameEndingWith("Service");

  @ArchTest
  static final ArchRule entities_live_in_domain =
      classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage("..domain..");

  @ArchTest
  static final ArchRule module_slices_are_free_of_cycles =
      slices().matching(BASE + "(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule shared_does_not_depend_on_modules =
      noClasses()
          .that()
          .resideInAPackage(BASE + "shared..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(modulePackages());

  @ArchTest
  static void module_internals_are_not_reachable_from_other_modules(JavaClasses classes) {
    MODULES.forEach(
        module ->
            noClasses()
                .that()
                .resideOutsideOfPackage(BASE + module + ".infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BASE + module + ".infrastructure..")
                .because("modules must talk through public application APIs or events")
                .check(classes));
  }

  private static String[] modulePackages() {
    return MODULES.stream().map(module -> BASE + module + "..").toArray(String[]::new);
  }
}
