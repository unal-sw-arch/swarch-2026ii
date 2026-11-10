package com.parosurvivors.serviya.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Fronteras entre los modulos del monolito modular.
 *
 * <p>Regla de oro: un modulo solo puede consumir la API publica de otro modulo
 * ({@code <modulo>.api}) o el contrato compartido ({@code shared.contract}). Nunca debe importar
 * su {@code domain}, sus puertos de persistencia ({@code application.ports.output}) ni su
 * {@code infrastructure}. La raiz de composicion ({@code config}) es la unica excepcion: puede
 * cablear implementaciones de cualquier modulo.</p>
 *
 * <p>Todas las reglas son absolutas: la deuda historica se pago y ya no hay baseline congelado.
 * Cualquier dependencia cruzada nueva rompe el build.</p>
 *
 * <p>El grafo de modulos es aciclico y se verifica con una matriz determinista (Tarjan sobre las
 * dependencias directas). Ademas, {@code metrics}, {@code notifications}, {@code profiles} y
 * {@code services} son hojas: no pueden depender de ningun otro modulo de negocio.</p>
 */
public class ModuleBoundaryTest {

    /** Modulos de negocio. {@code shared} (kernel) y {@code config} (composition root) se tratan aparte. */
    private static final List<String> MODULES = List.of(
            "admin",
            "bff",
            "feedback",
            "metrics",
            "notifications",
            "profiles",
            "reports",
            "requests",
            "services",
            "statistics",
            "users");

    /** Modulos que solo pueden depender de si mismos y del kernel shared. */
    private static final List<String> LEAF_MODULES = List.of(
            "metrics",
            "notifications",
            "profiles",
            "services");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.parosurvivors.serviya");

    private static final ArchRule NO_FOREIGN_INFRASTRUCTURE =
            noForeignLayer(
                    "infrastructure",
                    "Un modulo no debe usar la infraestructura de otro modulo; debe pasar por su API");

    private static final ArchRule NO_FOREIGN_PERSISTENCE_PORTS =
            noForeignLayer(
                    "application.ports.output",
                    "Un modulo no debe usar los puertos de persistencia de otro modulo; debe usar una API de consulta");

    private static final ArchRule NO_FOREIGN_DOMAIN =
            noForeignLayer(
                    "domain",
                    "Un modulo no debe compartir entidades de dominio; debe usar DTOs de su API");

    private static final ArchRule SHARED_WITHOUT_BUSINESS_MODULES =
            noClasses()
                    .that().resideInAPackage("..serviya.shared..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            MODULES.stream()
                                    .map(module -> "..serviya." + module + "..")
                                    .toArray(String[]::new))
                    .because("shared es el kernel transversal y no debe conocer modulos de negocio");

    @Test
    void modulesMustNotDependOnForeignInfrastructure() {
        NO_FOREIGN_INFRASTRUCTURE.check(CLASSES);
    }

    @Test
    void modulesMustNotDependOnForeignPersistencePorts() {
        NO_FOREIGN_PERSISTENCE_PORTS.check(CLASSES);
    }

    @Test
    void modulesMustNotDependOnForeignDomain() {
        NO_FOREIGN_DOMAIN.check(CLASSES);
    }

    @Test
    void sharedMustNotDependOnBusinessModules() {
        SHARED_WITHOUT_BUSINESS_MODULES.check(CLASSES);
    }

    @Test
    void apiPackagesMustBePure() {
        modulesMustExposePureApis().check(CLASSES);
    }

    @Test
    void domainMustNotDependOnApplicationOrInfrastructure() {
        noClasses()
                .that().resideInAPackage("..serviya..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..serviya..application..",
                        "..serviya..infrastructure..")
                .because("el dominio debe ser independiente de las capas de aplicacion e infraestructura")
                .check(CLASSES);
    }

    @Test
    void applicationMustNotDependOnInfrastructure() {
        noClasses()
                .that().resideInAPackage("..serviya..application..")
                .should().dependOnClassesThat().resideInAPackage("..serviya..infrastructure..")
                .because("la capa de aplicacion no debe depender de infraestructura (puertos y adaptadores)")
                .check(CLASSES);
    }

    @Test
    void leafModulesMustNotDependOnOtherModules() {
        String[] leaves = LEAF_MODULES.stream()
                .map(module -> "..serviya." + module + "..")
                .toArray(String[]::new);
        String[] others = MODULES.stream()
                .filter(module -> !LEAF_MODULES.contains(module))
                .map(module -> "..serviya." + module + "..")
                .toArray(String[]::new);

        noClasses()
                .that().resideInAnyPackage(leaves)
                .should().dependOnClassesThat().resideInAnyPackage(others)
                .because("metrics, notifications, profiles y services son proveedores hoja; "
                        + "las vistas compuestas pertenecen al BFF")
                .check(CLASSES);
    }

    @Test
    void modulesMustBeFreeOfCycles() {
        Map<String, Set<String>> graph = moduleGraph();
        List<List<String>> cycles = stronglyConnectedComponents(graph).stream()
                .filter(component -> component.size() > 1)
                .sorted((left, right) -> String.join(",", left).compareTo(String.join(",", right)))
                .toList();

        assertThat(cycles)
                .as("el grafo de dependencias entre modulos debe ser aciclico: %s", graph)
                .isEmpty();
    }

    /**
     * Matriz de dependencias directas entre modulos, normalizada y ordenada para que el test sea
     * determinista.
     */
    private static Map<String, Set<String>> moduleGraph() {
        Map<String, Set<String>> graph = new TreeMap<>();
        MODULES.forEach(module -> graph.put(module, new TreeSet<>()));

        for (JavaClass clazz : CLASSES) {
            String source = moduleOf(clazz.getPackageName());
            if (source == null) {
                continue;
            }
            for (Dependency dependency : clazz.getDirectDependenciesFromSelf()) {
                String target = moduleOf(dependency.getTargetClass().getPackageName());
                if (target != null && !target.equals(source)) {
                    graph.get(source).add(target);
                }
            }
        }
        return graph;
    }

    private static String moduleOf(String packageName) {
        for (String module : MODULES) {
            if (packageName.startsWith("com.parosurvivors.serviya." + module)) {
                return module;
            }
        }
        return null;
    }

    private static List<List<String>> stronglyConnectedComponents(Map<String, Set<String>> graph) {
        Tarjan tarjan = new Tarjan(graph);
        for (String node : graph.keySet()) {
            if (!tarjan.index.containsKey(node)) {
                tarjan.visit(node);
            }
        }
        return tarjan.components;
    }

    private static final class Tarjan {
        private final Map<String, Set<String>> graph;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> lowLink = new HashMap<>();
        private final Set<String> onStack = new HashSet<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final List<List<String>> components = new ArrayList<>();
        private int nextIndex;

        private Tarjan(Map<String, Set<String>> graph) {
            this.graph = graph;
        }

        private void visit(String node) {
            index.put(node, nextIndex);
            lowLink.put(node, nextIndex);
            nextIndex++;
            stack.push(node);
            onStack.add(node);

            for (String target : graph.getOrDefault(node, Set.of())) {
                if (!index.containsKey(target)) {
                    visit(target);
                    lowLink.put(node, Math.min(lowLink.get(node), lowLink.get(target)));
                } else if (onStack.contains(target)) {
                    lowLink.put(node, Math.min(lowLink.get(node), index.get(target)));
                }
            }

            if (lowLink.get(node).equals(index.get(node))) {
                List<String> component = new ArrayList<>();
                String member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    component.add(member);
                } while (!member.equals(node));
                component.sort(String::compareTo);
                components.add(component);
            }
        }
    }

    /**
     * Prohibe que un modulo dependa de una capa concreta de otro modulo.
     *
     * <p>Se genera una regla por modulo destino para poder expresar "cualquier origen excepto el
     * propio destino". El resultado se compone en una sola regla para que el freeze la trate como
     * una unidad.</p>
     */
    private static ArchRule noForeignLayer(String layer, String because) {
        ArchRule combined = null;
        for (String target : MODULES) {
            String[] sources = MODULES.stream()
                    .filter(module -> !module.equals(target))
                    .map(module -> "..serviya." + module + "..")
                    .toArray(String[]::new);

            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(sources)
                    .should().dependOnClassesThat()
                    .resideInAPackage("..serviya." + target + "." + layer + "..")
                    .because(because + " (modulo destino: " + target + ")");

            combined = combined == null ? rule : CompositeArchRule.of(combined).and(rule);
        }
        return combined;
    }

    /**
     * Una API publica debe ser un contrato estable y extraible: solo puede depender de JDK,
     * anotaciones de Spring, del propio modulo y de {@code shared.contract}. Nunca de entidades
     * JPA, mappers de infraestructura ni dominio interno de otro modulo.
     */
    private static ArchRule modulesMustExposePureApis() {
        ArchRule combined = null;
        for (String module : MODULES) {
            ArchRule rule = classes()
                    .that().resideInAPackage("..serviya." + module + ".api..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "java..",
                            "javax..",
                            "jakarta..",
                            "org.springframework..",
                            "..serviya." + module + ".api..",
                            "..serviya.shared.contract..")
                    .because("la API de un modulo debe ser un contrato estable y extraible (modulo: "
                            + module + ")")
                    // Todavia no existe ningun paquete <modulo>.api; la regla debe pasar vacia
                    // hasta que se introduzcan los primeros contratos.
                    .allowEmptyShould(true);

            combined = combined == null ? rule : CompositeArchRule.of(combined).and(rule);
        }
        return combined;
    }
}
