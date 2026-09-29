package com.github.xpenatan.jnative.compiler;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/** Executable inventory, including anonymous classes and javac's lambda bodies. */
class ClasslibInventoryTest {
    private static final Set<String> FIXED_CACHES = Set.of(
            "java/lang/Byte.<clinit>()V", "java/lang/Short.<clinit>()V",
            "java/lang/Character.<clinit>()V", "java/lang/Integer.<clinit>()V",
            "java/lang/Long.<clinit>()V");

    @Test void inventoryRejectsUnboundNativesAndResidualJavaAlgorithms() throws Exception {
        Map<String, Set<String>> javaCalls = new TreeMap<>();
        Set<String> sources = new TreeSet<>();
        List<String> failures = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        rows.add("source\tclass\tmethod\tclassification\tsymbol\theader\tdelegates");
        List<String> classRows = new ArrayList<>();
        classRows.add("source\tclass\tmethodCount");
        Set<String> inventoriedClasses = new TreeSet<>();
        int bound = 0, nested = 0, synthetic = 0;
        Map<String, String> packaged = packagedClasses();
        for(String name : new TreeSet<>(ClassLibrary.classes())) {
            ClassNode owner = new ClassNode();
            new ClassReader(ClassLibrary.read(name)).accept(owner, 0);
            assertEquals(name, owner.name, "Packaged class name differs from its index entry");
            assertNotNull(owner.sourceFile, "Source identity is missing for " + name);
            String physical = packaged.get(name);
            assertNotNull(physical, "No physical donor for " + name);
            String source = physical.substring(0, physical.lastIndexOf('/') + 1) + owner.sourceFile;
            sources.add(source);
            inventoriedClasses.add(name);
            classRows.add(String.join("\t", source, name, Integer.toString(owner.methods.size())));
            if(name.contains("$")) ++nested;
            for(MethodNode method : owner.methods) {
                String id = name + "." + method.name + method.desc;
                NativeBinding binding = NativeBinding.read(owner, method);
                Set<String> delegates = new TreeSet<>();
                boolean backEdge = false;
                for(int i = 0; i < method.instructions.size(); ++i) {
                    AbstractInsnNode instruction = method.instructions.get(i);
                    if(instruction instanceof MethodInsnNode call)
                        delegates.add(call.owner + "." + call.name + call.desc);
                    if(instruction instanceof InvokeDynamicInsnNode dynamic)
                        for(Object argument : dynamic.bsmArgs)
                            if(argument instanceof Handle handle)
                                delegates.add(handle.getOwner() + "." + handle.getName() + handle.getDesc());
                    if(instruction instanceof JumpInsnNode jump)
                        backEdge |= method.instructions.indexOf(jump.label) <= i;
                    if(instruction instanceof TableSwitchInsnNode selection) {
                        backEdge |= method.instructions.indexOf(selection.dflt) <= i;
                        for(LabelNode label : selection.labels)
                            backEdge |= method.instructions.indexOf(label) <= i;
                    }
                    if(instruction instanceof LookupSwitchInsnNode selection) {
                        backEdge |= method.instructions.indexOf(selection.dflt) <= i;
                        for(LabelNode label : selection.labels)
                            backEdge |= method.instructions.indexOf(label) <= i;
                    }
                }
                String classification;
                if(binding != null) {
                    classification = "annotated-native";
                    ++bound;
                    // Callback edges remain explicit even though no Java invocation occurs.
                    for(var callback : binding.callbacks()) delegates.add(callback.method().toString());
                } else if((method.access & Opcodes.ACC_ABSTRACT) != 0) {
                    classification = "interface-contract";
                } else if(FIXED_CACHES.contains(id)) {
                    classification = "fixed-boxing-cache";
                } else {
                    classification = "bounded-facade-or-scalar";
                    if(backEdge) failures.add("Java traversal: " + id);
                    javaCalls.put(id, delegates);
                }
                if((method.access & Opcodes.ACC_SYNTHETIC) != 0) ++synthetic;
                rows.add(String.join("\t", source, name, method.name + method.desc,
                        classification, binding == null ? "" : binding.symbol(),
                        binding == null ? "" : binding.include(), String.join(";", delegates)));
            }
        }
        // Detect algorithmic recursion hidden behind helpers, not just source for/while loops.
        // Native callbacks and virtual implementation selection are deliberately separate:
        // their behavior is exercised by the family conformance fixtures.
        Set<String> complete = new HashSet<>();
        for(String id : javaCalls.keySet()) visit(id, javaCalls, new LinkedHashSet<>(), complete, failures);
        Path report = Path.of("build/reports/classlib-native-inventory.tsv");
        Files.createDirectories(report.getParent());
        Files.write(report, rows);
        Files.write(Path.of("build/reports/classlib-native-classes.tsv"), classRows);
        Files.write(Path.of("build/reports/classlib-native-sources.txt"), sources);
        List<String> platformRows = new ArrayList<>();
        platformRows.add("target\thelper\tsymbol\theader\tinstance");
        for(var target : PlatformBindings.targets().values())
            platformRows.add(target.api() + "\t" + target.helper() + "\t" + target.binding().symbol()
                    + "\t" + target.binding().include() + "\t" + target.instance());
        Files.write(Path.of("build/reports/classlib-platform-bindings.tsv"), platformRows);
        assertTrue(platformRows.size() > 900, "The compiler-provided API surface must be inventoried");
        String sourceRootProperty = System.getProperty("jnative.classlib.sourceRoot");
        assertNotNull(sourceRootProperty, "Gradle must configure the repository class-library source root");
        Path sourceRoot = Path.of(sourceRootProperty);
        Set<String> repositorySources = new TreeSet<>();
        try(var files = Files.walk(sourceRoot)) {
            files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java"))
                    .map(path -> sourceRoot.relativize(path).toString().replace('\\', '/'))
                    .forEach(repositorySources::add);
        }
        assertFalse(repositorySources.isEmpty(), "The repository class-library source root must contain Java sources");
        assertEquals(repositorySources, sources, "Packaged source inventory must exactly match repository Java sources");
        assertEquals(packaged.keySet(), inventoriedClasses, "Every actual packaged class must have a class inventory row");
        assertTrue(bound > 100, "Native declarations must be present in the packaged library");
        assertTrue(nested > 20, "Nested class inventory is missing");
        assertTrue(synthetic > 0, "Synthetic method inventory is missing");
        assertEquals(List.of(), failures, "Residual algorithms; see " + report.toAbsolutePath());
    }

    private static Map<String, String> packagedClasses() throws Exception {
        var artifact = SubstitutionProviderIndex.builtin().artifact();
        Map<String, String> classes = new TreeMap<>();
        for(String physical : artifact.classNames()) {
            assertFalse(physical.startsWith("java/"), "Donors must have ordinary implementation packages");
            assertFalse(physical.startsWith("classlib/"), "Legacy classlib resource prefix must not return");
            ClassNode donor = artifact.classNode(physical);
            String logical = physical;
            for(var annotation : Annotations.all(donor.visibleAnnotations, donor.invisibleAnnotations))
                if(annotation.desc.equals("Lcom/github/xpenatan/jnative/substitution/SubstituteClass;"))
                    for(int i = 0; i < annotation.values.size(); i += 2)
                        if(annotation.values.get(i).equals("value")) logical = ((String)annotation.values.get(i + 1)).replace('.', '/');
            assertNull(classes.put(logical, physical), "Duplicate logical class " + logical);
        }
        return classes;
    }

    private static void visit(String id, Map<String, Set<String>> calls, Set<String> path,
                              Set<String> complete, List<String> failures) {
        if(!calls.containsKey(id) || complete.contains(id)) return;
        if(!path.add(id)) {
            failures.add("Java call cycle: " + String.join(" -> ", path) + " -> " + id);
            return;
        }
        for(String target : calls.get(id)) visit(target, calls, path, complete, failures);
        path.remove(id);
        complete.add(id);
    }
}
