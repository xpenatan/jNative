package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.compiler.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class NativeBindingEmissionTest {
    @TempDir Path directory;

    @Test void boundedPlatformConstructorsRemoveRootsButCollectingSuperCallsKeepThem() throws Exception {
        Path source = directory.resolve("Demo.java");
        Files.writeString(source, """
                public class Demo {
                    static class Plain {
                        Object value;
                        Plain(Object value) { this.value = value; }
                    }
                    static class Numeric extends Number {
                        int value;
                        Numeric(int value) { this.value = value; }
                        public int intValue() { return value; }
                        public long longValue() { return value; }
                        public float floatValue() { return value; }
                        public double doubleValue() { return value; }
                    }
                    static class CollectingBase {
                        CollectingBase() { System.gc(); }
                    }
                    static class Collecting extends CollectingBase {
                        Object value;
                        Collecting(Object value) { this.value = value; }
                    }
                    static class WithCause extends RuntimeException {
                        WithCause(Throwable cause) { super(cause); }
                    }
                    public static void main(String[] args) {
                        new Plain(args); new Numeric(7); new Collecting(args);
                        new WithCause(new RuntimeException());
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = request();
        var sources = new CppEmitter().emit(new BytecodeCompiler().compile(request), request);
        for(String name : List.of("Plain", "Numeric", "Collecting", "WithCause")) {
            String cpp = sources.get("classes/Demo_" + name + ".cpp");
            int start = cpp.indexOf("void Demo_" + name + "::initialize");
            assertTrue(start >= 0, cpp);
            String constructor = cpp.substring(start, cpp.indexOf("\n}", start));
            boolean collecting = name.equals("Collecting") || name.equals("WithCause");
            assertEquals(collecting, constructor.contains("RootFrame<"), constructor);
            assertEquals(collecting, constructor.contains("poll_if_requested"), constructor);
            String effect = sources.get("method-effects.tsv").lines()
                    .filter(line -> line.startsWith("Demo$" + name + ".<init>("))
                    .findFirst().orElseThrow();
            assertEquals(Boolean.toString(!collecting), effect.split("\t")[1], effect);
        }
    }

    @Test void managedBindingsUseRootsDispatchAndTypedFieldsInBothEmitters() throws Exception {
        fixture(true);
        var request = request();
        Program program = new BytecodeCompiler().compile(request);
        CppEmitter emitter = new CppEmitter();
        var sources = emitter.emit(program, request);
        String cpp = String.join("\n", sources.values());
        assertTrue(cpp.contains("::jnative::driver(native_arg0.get(), +[](::jnative::Object* receiver) -> std::int32_t { return "
                + "static_cast<::generated::Demo*>(::jnative::require_non_null(receiver))->next(); }"), cpp);
        assertTrue(cpp.contains("NativeFieldAccess<"));
        assertTrue(cpp.contains("::jnative::LocalRoot<> native_arg0(arg0);"));
        assertFalse(cpp.contains("BorrowedHandle"));
        assertFalse(cpp.contains("extern \"C\" int32_t jnative::driver"));
        assertTrue(sources.get("source-readability.tsv").contains("Demo.readable(LDemo;)I\tstructured"));
        assertTrue(emitter.readabilityFallbacks().stream().anyMatch(f -> f.javaMethod().contains("Demo.fallback")));
        assertTrue(cpp.contains("std::int32_t"));
        assertTrue(program.methods().containsKey(new Program.MethodId("Demo", "next", "()I")));
    }

    @Test void externalBindingsKeepOpaqueHandlesBlockingAndCLinkage() throws Exception {
        fixture(false);
        var request = request();
        var sources = new CppEmitter().emit(new BytecodeCompiler().compile(request), request);
        String cpp = String.join("\n", sources.values());
        assertTrue(cpp.contains("extern \"C\" int32_t external_driver(jn_handle)"));
        assertTrue(cpp.contains("::jnative::BorrowedHandle handle0(arg0);"));
        assertTrue(cpp.contains("::jnative::JavaNativeRegion native_call;"));
        assertTrue(cpp.contains("::external_driver(handle0.id())"));
        assertTrue(sources.get("jnative_imports.h").contains("int32_t external_driver(jn_handle);"));
    }

    @Test void builtInBindingsRetainConcreteCallbacksAndConstantFieldOffsets() throws Exception {
        fixture(true);
        ClassNode owner = new ClassNode();
        new ClassReader(Files.readAllBytes(directory.resolve("Demo.class"))).accept(owner, 0);
        boolean changed = false;
        for(var method : owner.methods) {
            if(method.invisibleAnnotations == null) continue;
            for(AnnotationNode annotation : method.invisibleAnnotations) {
                if(!annotation.desc.equals("Lcom/github/xpenatan/jnative/interop/NativeImport;")) continue;
                annotation.values.add("runtimeOnly");
                annotation.values.add(true);
                changed = true;
            }
        }
        assertTrue(changed);
        ClassWriter writer = new ClassWriter(0);
        owner.accept(writer);
        Files.write(directory.resolve("Demo.class"), writer.toByteArray());
        var request = request();
        String cpp = new CppEmitter().emit(new BytecodeCompiler().compile(request), request).get("classes/Demo.cpp");
        assertTrue(cpp.contains("[](::jnative::Object* receiver)"), cpp);
        assertFalse(cpp.contains("+[]"), cpp);
        assertTrue(cpp.contains("::jnative::BoundNativeFieldAccess<"), cpp);
        assertFalse(cpp.contains("::jnative::NativeFieldAccess<"), cpp);
    }

    @Test void effectsUseValidatedBoundedMetadata() throws Exception {
        var writer = base();
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_NATIVE, "scalar", "()I", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcom/github/xpenatan/jnative/interop/NativeImport;", false);
        annotation.visit("value", "jnative::scalar");
        annotation.visit("managed", true);
        annotation.visit("bounded", true);
        annotation.visitEnd();
        method.visitEnd();
        main(writer, "scalar", "()I", false);
        save(writer);
        Program program = new BytecodeCompiler().compile(request());
        MethodEffects effects = new MethodEffects(program, call -> false);
        assertTrue(effects.boundedAfterInitialization(new Program.MethodId("Demo", "scalar", "()I")));
        assertTrue(effects.boundedAfterInitialization(program.entry()));
        String cpp = String.join("\n", new CppEmitter().emit(program, request()).values());
        assertTrue(cpp.contains("return ::jnative::scalar();"));
    }

    @Test void platformStaticCallbacksGetTypedAdapters() throws Exception {
        var writer = base();
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_NATIVE, "driver", "()V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcom/github/xpenatan/jnative/interop/NativeImport;", false);
        annotation.visit("value", "jnative::properties");
        annotation.visit("managed", true);
        array(annotation, "callbacks", "java/lang/System.getProperty(Ljava/lang/String;)Ljava/lang/String;");
        AnnotationVisitor kinds = annotation.visitArray("callbackKinds");
        kinds.visitEnum(null, "Lcom/github/xpenatan/jnative/interop/NativeImport$Invocation;", "STATIC");
        kinds.visitEnd();
        annotation.visitEnd();
        method.visitEnd();
        main(writer, "driver", "()V", false);
        save(writer);
        var request = request();
        String cpp = String.join("\n", new CppEmitter().emit(new BytecodeCompiler().compile(request), request).values());
        var target = PlatformBindings.find(new Program.MethodId("java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(cpp.contains("+[](::jnative::Object* arg0) -> ::jnative::Object* { return " + CppEmitter.symbol(target.helper()) + "(arg0); }"), cpp);
        assertTrue(cpp.contains("::" + target.binding().symbol() + "(native_arg0.get())"), cpp);
    }

    @Test void sourceClassCallbacksUseVirtualMembersAndRetainOtherDispatchKinds() throws Exception {
        Path source = directory.resolve("Demo.java");
        Files.writeString(source, """
                abstract class Base { public abstract int read(Object value); }
                class Child extends Base { public int read(Object value) { return 11; } }
                class OtherChild extends Base { public int read(Object value) { return 12; } }
                class Parent { public int value() { return 22; } }
                class Middle extends Parent {}
                class Leaf extends Middle { public int value() { return 33; } }
                interface Face {
                    int apply(Object value);
                    default int chosen() { return 44; }
                }
                class FaceImpl implements Face { public int apply(Object value) { return 55; } }
                class InheritedDefault extends FaceImpl {}
                public class Demo {
                    static int driver(Base base, Middle inherited, Face face, InheritedDefault defaults, Object object) { return 0; }
                    static int factory() { return 77; }
                    public static void main(String[] args) {
                        new OtherChild();
                        driver(new Child(), new Leaf(), new FaceImpl(), new InheritedDefault(), new Object());
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        ClassNode owner = new ClassNode();
        new ClassReader(Files.readAllBytes(directory.resolve("Demo.class"))).accept(owner, 0);
        AnnotationNode include = new AnnotationNode("Lcom/github/xpenatan/jnative/interop/NativeInclude;");
        include.values = new ArrayList<>(List.of("value", "jn_classlib.hpp"));
        owner.invisibleAnnotations = List.of(include);
        var nativeMethod = owner.methods.stream().filter(method -> method.name.equals("driver")).findFirst().orElseThrow();
        nativeMethod.access |= ACC_NATIVE;
        nativeMethod.instructions.clear();
        nativeMethod.tryCatchBlocks.clear();
        nativeMethod.localVariables = null;
        AnnotationNode binding = new AnnotationNode("Lcom/github/xpenatan/jnative/interop/NativeImport;");
        String invocation = "Lcom/github/xpenatan/jnative/interop/NativeImport$Invocation;";
        binding.values = new ArrayList<>(List.of("value", "jnative::driver", "managed", true,
                "callbacks", List.of("Base.read(Ljava/lang/Object;)I", "Middle.value()I", "Face.apply(Ljava/lang/Object;)I",
                        "InheritedDefault.chosen()I", "java/lang/Object.hashCode()I", "Demo.factory()I", "Parent.value()I"),
                "callbackReceivers", List.of(0, 1, 2, 3, 4, -1, 1),
                "callbackKinds", List.of(new String[]{invocation, "VIRTUAL"}, new String[]{invocation, "VIRTUAL"},
                        new String[]{invocation, "INTERFACE"}, new String[]{invocation, "VIRTUAL"},
                        new String[]{invocation, "VIRTUAL"}, new String[]{invocation, "STATIC"}, new String[]{invocation, "SPECIAL"})));
        nativeMethod.invisibleAnnotations = List.of(binding);
        ClassWriter writer = new ClassWriter(0);
        owner.accept(writer);
        Files.write(directory.resolve("Demo.class"), writer.toByteArray());
        var request = request();
        Program program = new BytecodeCompiler().compile(request);
        CppEmitter emitter = new CppEmitter();
        Map<String, String> sources = emitter.emit(program, request);
        String driver = body(sources.get("classes/Demo.cpp"), "driver");
        assertTrue(driver.contains("+[](::jnative::Object* receiver, ::jnative::Object* arg0) -> std::int32_t { return "
                + "static_cast<::generated::Base*>(::jnative::require_non_null(receiver))->read(arg0); }"), driver);
        assertTrue(driver.contains("+[](::jnative::Object* receiver) -> std::int32_t { return "
                + "static_cast<::generated::Parent*>(::jnative::require_non_null(receiver))->value(); }"), driver);
        assertFalse(driver.contains("->Base::read"), driver);
        assertFalse(driver.contains("->Parent::value"), driver);
        CppNames names = new CppNames(program);
        var dispatchedCallbacks = List.of(new Program.MethodId("Face", "apply", "(Ljava/lang/Object;)I"),
                new Program.MethodId("InheritedDefault", "chosen", "()I"),
                new Program.MethodId("java/lang/Object", "hashCode", "()I"));
        names.registerCalls(dispatchedCallbacks);
        for(var callback : dispatchedCallbacks)
            assertTrue(driver.contains(">(" + names.call(callback) + ")"), driver);
        assertTrue(driver.contains("+[]() -> std::int32_t { return "
                + CppEmitter.symbol(new Program.MethodId("Demo", "factory", "()I")) + "(); }"), driver);
        assertTrue(driver.contains("+[](::jnative::Object* receiver) -> std::int32_t { return "
                + CppEmitter.symbol(new Program.MethodId("Parent", "value", "()I")) + "(receiver); }"), driver);
        assertTrue(program.methods().containsKey(new Program.MethodId("Child", "read", "(Ljava/lang/Object;)I")));
        assertTrue(program.methods().containsKey(new Program.MethodId("Leaf", "value", "()I")));
        assertTrue(sources.get("classes/Base.hpp").contains("virtual std::int32_t read(::jnative::Object*"));
        assertTrue(sources.get("classes/Parent.hpp").contains("virtual std::int32_t value()"));
        assertFalse(new MethodEffects(program, call -> false).boundedAfterInitialization(
                new Program.MethodId("Demo", "driver", nativeMethod.desc)));

        // Built-in callbacks use the ordinary closed-program proof, including
        // interface implementations. Polymorphic and runtime-owned targets keep dispatch.
        binding.values.add("runtimeOnly");
        binding.values.add(true);
        writer = new ClassWriter(0);
        owner.accept(writer);
        Files.write(directory.resolve("Demo.class"), writer.toByteArray());
        program = new BytecodeCompiler().compile(request);
        sources = new CppEmitter().emit(program, request);
        driver = body(sources.get("classes/Demo.cpp"), "driver");
        assertTrue(driver.contains("static_cast<::generated::Leaf*>(::jnative::require_non_null(receiver))->Leaf::value()"), driver);
        assertTrue(driver.contains("static_cast<::generated::FaceImpl*>(::jnative::require_non_null(receiver))->FaceImpl::apply(arg0)"), driver);
        assertTrue(driver.contains("static_cast<::generated::Base*>(::jnative::require_non_null(receiver))->read(arg0)"), driver);
        assertFalse(driver.contains("->Child::read") || driver.contains("->OtherChild::read"), driver);
        assertFalse(driver.contains("+[]"), driver);
        assertTrue(driver.contains(">(" + names.call(dispatchedCallbacks.get(2)) + ")"), driver);

        binding.values.addAll(List.of("callbacksSynchronous", true, "boundedCallbacks", true));
        writer = new ClassWriter(0);
        owner.accept(writer);
        Files.write(directory.resolve("Demo.class"), writer.toByteArray());
        sources = new CppEmitter().emit(new BytecodeCompiler().compile(request), request);
        driver = body(sources.get("classes/Demo.cpp"), "driver");
        assertTrue(driver.contains("make_runtime_callback("), driver);
        assertTrue(driver.contains("RootFrame<2> callback_roots; callback_roots.slot(0) = receiver; "
                + "callback_roots.slot(1) = arg0; return static_cast<::generated::Base*"), driver);
        assertTrue(driver.contains("case ::generated::Child::class_id:"), driver);
        assertTrue(driver.contains("case ::generated::OtherChild::class_id:"), driver);
    }

    @Test void runtimeHashAttemptsHonorGeneratedOverridesAndEffectiveBindings() throws Exception {
        Path source = directory.resolve("Demo.java");
        Files.writeString(source, """
                import java.util.HashMap;
                public class Demo {
                    static class Plain {}
                    static class Collecting {
                        public int hashCode() { System.gc(); return 1; }
                    }
                    public static void main(String[] args) {
                        HashMap<Object, Object> map = new HashMap<>();
                        Object plain = new Plain(), collecting = new Collecting();
                        map.put(plain, plain); map.put(collecting, collecting);
                        System.out.println(map.get(plain) == plain && map.get(collecting) == collecting);
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = request();
        Program program = new BytecodeCompiler().compile(request);
        String hash = new CppEmitter().emit(program, request).get("classes/java/util/HashMap.cpp");
        assertNotNull(hash);
        int plain = hash.indexOf("case ::generated::Demo_Plain::class_id:");
        assertTrue(plain >= 0, hash);
        assertTrue(hash.startsWith("return ::jnative::try_object_hash", hash.indexOf("return ", plain)), hash);
        assertTrue(hash.contains("default: return receiver->type_id() == 0 && ::jnative::try_object_hash"), hash);
        assertFalse(hash.contains("case ::generated::Demo_Collecting::class_id:"), hash);

        var targets = new LinkedHashMap<>(program.bindings().targets());
        var id = new Program.MethodId("java/lang/Object", "hashCode", "()I");
        var original = targets.get(id);
        var replacement = new NativeBinding("custom_hash", "custom_hash.hpp", true, false, false,
                List.of(), List.of(), List.of(), false, true, false, true, false);
        targets.put(id, new PlatformBindings.Target(id, original.helper(), original.instance(), replacement));
        var bindings = SubstitutionRegistry.class.getDeclaredField("bindings");
        bindings.setAccessible(true);
        bindings.set(program.substitutions(), new PlatformBindings.Table(targets, program.bindings().fields()));
        hash = new CppEmitter().emit(program, request).get("classes/java/util/HashMap.cpp");
        assertFalse(hash.contains("try_object_hash"), hash);
    }

    @Test void platformStaticFieldsRouteThroughAnnotatedHelpersInBothEmitters() throws Exception {
        fixture(true);
        var request = request();
        Program program = new BytecodeCompiler().compile(request);
        String cpp = String.join("\n", new CppEmitter().emit(program, request).values());
        var field = PlatformBindings.field("java/lang/System", "out", "Ljava/io/PrintStream;");
        assertTrue(program.methods().containsKey(field.helper()));
        assertTrue(cpp.contains(CppEmitter.symbol(field.helper()) + "()"), cpp);
        assertTrue(cpp.contains("::" + field.binding().symbol() + "()"), cpp);
        assertFalse(cpp.contains("::jnative::standard_out()"), cpp);
    }

    @Test void readablePlatformStaticMethodsAndFieldGettersUseGeneratedMemberNames() throws Exception {
        Path source = directory.resolve("Demo.java");
        Files.writeString(source, """
                public class Demo {
                    public static void main(String[] args) { System.out.println(String.valueOf((Object)null)); }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = request();
        Program program = new BytecodeCompiler().compile(request);
        Map<String, String> sources = new CppEmitter().emit(program, request);
        var getter = PlatformBindings.field("java/lang/System", "out", "Ljava/io/PrintStream;");
        var valueOf = PlatformBindings.find(new Program.MethodId("java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;"));
        String cpp = sources.get("classes/Demo.cpp");
        String helperClass = "::generated::" + getter.helper().owner().replace("/", "::");
        assertTrue(cpp.contains(helperClass + "::" + getter.helper().name() + "()"), cpp);
        assertTrue(cpp.contains(helperClass + "::" + valueOf.helper().name() + "(nullptr)"), cpp);
        for(var unit : sources.entrySet())
            if(unit.getKey().startsWith("classes/") && unit.getKey().endsWith(".cpp")) {
                assertFalse(unit.getValue().contains("dispatch_j_"), unit.getValue());
                assertFalse(unit.getValue().matches("(?s).*\\bj_[A-Za-z0-9_]+_[0-9a-f]{12}\\b.*"), unit.getValue());
            }
    }

    @Test void boundedSourceImportsPreserveGuardedLoopsAndNumericalLeaves() throws Exception {
        Path source = directory.resolve("Demo.java");
        Files.writeString(source, """
                public class Demo {
                    static int quantize(float value) { return Math.round(Math.min(1, Math.max(0, value)) * 255); }
                    static int initializationMarker = 1;
                    static double roundedRoot(double value) { return Math.floor(Math.sqrt(value)) + Math.ceil(value); }
                    static float bits(int value) { return Float.intBitsToFloat(value); }
                    static void branching(float[] source, float[] target, int count) {
                        for(int index = 0; index < count; index++) target[index] = Math.max(0, source[index]);
                    }
                    public static void main(String[] args) {
                        quantize(.5f); roundedRoot(2); bits(42);
                        branching(new float[2], new float[2], 2);
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = request();
        String cpp = new CppEmitter().emit(new BytecodeCompiler().compile(request), request).get("classes/Demo.cpp");
        for(String name : List.of("quantize", "roundedRoot", "bits")) {
            String body = body(cpp, name);
            assertFalse(body.contains("safepoint"), body);
            assertFalse(body.contains("gc_roots"), body);
            assertTrue(body.contains("ensure_initialized"), body);
        }
        String loop = body(cpp, "branching");
        assertTrue(loop.contains("source_elements.get_unchecked("), loop);
        assertTrue(loop.contains("target_elements.set_unchecked("), loop);
    }

    @Test void nativeRootingSkipsOnlyTheRedundantBoundaryAndKeepsCallerEffects() throws Exception {
        rootingFixture(true, false, null);
        var request = request();
        Program program = new BytecodeCompiler().compile(request);
        String cpp = new CppEmitter().emit(program, request).get("classes/Demo.cpp");
        String driver = body(cpp, "driver");
        assertTrue(driver.contains("::jnative::driver(arg0)"), driver);
        assertFalse(driver.contains("LocalRoot"), driver);
        assertFalse(driver.contains("ensure_initialized"), driver);
        assertFalse(driver.contains("safepoint"), driver);
        assertTrue(driver.contains("::jnative::JavaFrame java_frame"), driver);
        assertTrue(driver.contains("catch (const ::jnative::Thrown&) { throw; }"), driver);
        assertTrue(driver.contains("catch (const std::bad_alloc&)"), driver);
        assertTrue(driver.contains("::jnative::raise_native(error)"), driver);
        MethodEffects effects = new MethodEffects(program, call -> false);
        assertFalse(effects.boundedAfterInitialization(new Program.MethodId("Demo", "driver", "(Ljava/lang/Object;)I")));
        assertFalse(effects.boundedAfterInitialization(new Program.MethodId("Demo", "caller", "(Ljava/lang/Object;)I")));
        String caller = body(cpp, "caller");
        assertTrue(caller.contains("RootFrame"), caller);
        assertTrue(caller.contains("poll_if_requested"), caller);
    }

    @Test void nativeRootingKeepsSafeBoundaryForOwnInheritedAndInterfaceInitialization() throws Exception {
        for(String initializer : List.of("Demo", "Base", "Marker")) {
            rootingFixture(true, false, initializer);
            var request = request();
            String driver = body(new CppEmitter().emit(new BytecodeCompiler().compile(request), request)
                    .get("classes/Demo.cpp"), "driver");
            assertNormalRootingBoundary(driver);
        }
    }

    @Test void nativeRootingKeepsSafeBoundaryForSynchronizedMethodsAndDefaultContract() throws Exception {
        rootingFixture(true, true, null);
        var request = request();
        String driver = body(new CppEmitter().emit(new BytecodeCompiler().compile(request), request)
                .get("classes/Demo.cpp"), "driver");
        assertNormalRootingBoundary(driver);
        assertTrue(driver.contains("MonitorGuard synchronized_method"), driver);
        assertTrue(driver.indexOf("LocalRoot") < driver.indexOf("MonitorGuard"), driver);
        rootingFixture(false, false, null);
        driver = body(new CppEmitter().emit(new BytecodeCompiler().compile(request), request)
                .get("classes/Demo.cpp"), "driver");
        assertNormalRootingBoundary(driver);
    }

    @Test void nativeRootingKeepsSafeBoundaryForUnknownPlatformAncestors() throws Exception {
        rootingFixture(true, false, null, "java/lang/Thread");
        var request = request();
        String driver = body(new CppEmitter().emit(new BytecodeCompiler().compile(request), request)
                .get("classes/Demo.cpp"), "driver");
        assertNormalRootingBoundary(driver);
    }

    @Test void runtimeMarkersAllowAuditedMapDriversToManageBoundaryRoots() throws Exception {
        Path source = directory.resolve("Demo.java");
        Files.writeString(source, """
                import java.util.HashMap;
                import java.util.TreeMap;

                public class Demo {
                    public static void main(String[] args) {
                        HashMap<String, String> hash = new HashMap<>();
                        hash.put("key", "value"); hash.get("key"); hash.remove("key"); hash.clear();
                        TreeMap<String, String> tree = new TreeMap<>();
                        tree.put("key", "value"); tree.get("key"); tree.remove("key"); tree.clear();
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = request();
        Program program = new BytecodeCompiler().compile(request);
        Map<String, String> sources = new CppEmitter().emit(program, request);
        MethodEffects effects = new MethodEffects(program, call -> false);
        Set<String> drivers = Set.of("putNative", "findNative", "lookupNative", "removeNative", "clearNative");
        for(String owner : List.of("java/util/HashMap", "java/util/TreeMap")) {
            var methods = program.methods().values().stream()
                    .filter(method -> method.id().owner().equals(owner) && drivers.contains(method.id().name())).toList();
            assertEquals(4, methods.size(), owner);
            String cpp = sources.get("classes/" + owner + ".cpp");
            assertNotNull(cpp, "Missing source for " + owner + ": " + sources.keySet());
            if(owner.equals("java/util/HashMap")) {
                int start = cpp.indexOf("\n::jnative::Object* HashMap::put(");
                assertTrue(start >= 0, cpp);
                String wrapper = cpp.substring(start, cpp.indexOf("\n}", start));
                assertTrue(wrapper.contains("return HashMap::putNative("), wrapper);
                assertFalse(wrapper.contains("collection_pending()"), wrapper);
                assertFalse(wrapper.contains("poll_with_roots("), wrapper);
                assertFalse(wrapper.contains("gc_roots"), wrapper);
            }
            for(var method : methods) {
                NativeBinding binding = method.nativeBinding();
                assertTrue(binding.managesRoots(), method.id().toString());
                assertFalse(binding.bounded(), method.id().toString());
                int start = cpp.indexOf("// " + method.id());
                assertTrue(start >= 0, method.id().toString());
                String driver = cpp.substring(start, cpp.indexOf("\n}", start));
                assertTrue(driver.contains("::" + binding.symbol() + "(arg0"), driver);
                assertFalse(driver.contains("LocalRoot"), driver);
                assertFalse(driver.contains("ensure_initialized"), driver);
                assertFalse(driver.contains("safepoint"), driver);
                assertFalse(effects.boundedAfterInitialization(method.id()), method.id().toString());
            }
        }
    }

    @Test void onlyAuditedPlatformEqualityAndHashHelpersDelegateBoundaryRoots() throws Exception {
        Path source = directory.resolve("Demo.java");
        Files.writeString(source, """
                public class Demo {
                    static int measure(Object value, String text) {
                        return value.hashCode() + (value.equals(text) ? 1 : 0)
                                + text.hashCode() + (text.equals(value) ? 1 : 0);
                    }
                    public static void main(String[] args) { measure(new Object(), "a"); }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = request();
        Program program = new BytecodeCompiler().compile(request);
        String cpp = String.join("\n", new CppEmitter().emit(program, request).values());
        for(String owner : List.of("java/lang/Object", "java/lang/String")) {
            for(String name : List.of("hashCode", "equals")) {
                var target = PlatformBindings.find(new Program.MethodId(owner, name,
                        name.equals("equals") ? "(Ljava/lang/Object;)Z" : "()I"));
                assertTrue(target.binding().managesRoots());
                assertFalse(target.binding().bounded());
                assertFalse(target.binding().boundedAccess());
                int start = cpp.indexOf("\nstd::int32_t NativePlatform::" + target.helper().name() + "(");
                assertTrue(start >= 0, target.helper().toString());
                String helper = cpp.substring(start, cpp.indexOf("\n}", start));
                assertFalse(helper.contains("LocalRoot"), helper);
                assertFalse(helper.contains("ensure_initialized"), helper);
                assertFalse(helper.contains("safepoint"), helper);
                assertTrue(helper.contains("::" + target.binding().symbol() + "(arg0"), helper);
                assertTrue(helper.contains("::jnative::JavaFrame"), helper);
            }
        }
        assertFalse(PlatformBindings.find(new Program.MethodId("java/lang/Object", "getClass", "()Ljava/lang/Class;"))
                .binding().managesRoots());
        assertFalse(PlatformBindings.find(new Program.MethodId("java/lang/String", "toString", "()Ljava/lang/String;"))
                .binding().managesRoots());
    }

    private static void assertNormalRootingBoundary(String driver) {
        assertTrue(driver.contains("::jnative::LocalRoot<> native_arg0(arg0);"), driver);
        assertTrue(driver.contains("Demo::ensure_initialized();"), driver);
        assertTrue(driver.contains("::jnative::safepoint();"), driver);
        assertTrue(driver.contains("::jnative::driver(native_arg0.get())"), driver);
    }

    private void rootingFixture(boolean managesRoots, boolean synchronizedMethod, String initializerOwner) throws Exception {
        rootingFixture(managesRoots, synchronizedMethod, initializerOwner, "java/lang/Object");
    }

    private void rootingFixture(boolean managesRoots, boolean synchronizedMethod, String initializerOwner, String baseParent) throws Exception {
        for(String owner : List.of("Base", "Marker", "Demo")) {
            ClassWriter writer = new ClassWriter(0);
            writer.visit(V17, ACC_PUBLIC | (owner.equals("Marker") ? ACC_INTERFACE | ACC_ABSTRACT : 0), owner,
                    null, owner.equals("Demo") ? "Base" : owner.equals("Base") ? baseParent : "java/lang/Object",
                    owner.equals("Demo") ? new String[]{"Marker"} : null);
            if(owner.equals(initializerOwner)) {
                MethodVisitor initializer = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
                initializer.visitCode();
                initializer.visitInsn(RETURN);
                initializer.visitMaxs(0, 0);
                initializer.visitEnd();
            }
            if(owner.equals("Marker")) {
                // A default method makes this interface part of class initialization.
                MethodVisitor value = writer.visitMethod(ACC_PUBLIC, "value", "()I", null, null);
                value.visitCode();
                value.visitInsn(ICONST_1);
                value.visitInsn(IRETURN);
                value.visitMaxs(1, 1);
                value.visitEnd();
            }
            if(owner.equals("Demo")) {
                AnnotationVisitor include = writer.visitAnnotation("Lcom/github/xpenatan/jnative/interop/NativeInclude;", false);
                include.visit("value", "jn_classlib.hpp");
                include.visitEnd();
                MethodVisitor driver = writer.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_NATIVE
                        | (synchronizedMethod ? ACC_SYNCHRONIZED : 0), "driver", "(Ljava/lang/Object;)I", null, null);
                AnnotationVisitor annotation = driver.visitAnnotation("Lcom/github/xpenatan/jnative/interop/NativeImport;", false);
                annotation.visit("value", "jnative::driver");
                annotation.visit("managed", true);
                annotation.visit("managesRoots", managesRoots);
                annotation.visitEnd();
                driver.visitEnd();
                MethodVisitor caller = writer.visitMethod(ACC_STATIC, "caller", "(Ljava/lang/Object;)I", null, null);
                caller.visitCode();
                caller.visitVarInsn(ALOAD, 0);
                caller.visitMethodInsn(INVOKESTATIC, "Demo", "driver", "(Ljava/lang/Object;)I", false);
                caller.visitInsn(IRETURN);
                caller.visitMaxs(1, 1);
                caller.visitEnd();
                main(writer, "caller", "(Ljava/lang/Object;)I", true);
            }
            writer.visitEnd();
            Files.write(directory.resolve(owner + ".class"), writer.toByteArray());
        }
    }

    private static String body(String cpp, String name) {
        String type = switch(name) { case "quantize", "driver", "caller" -> "std::int32_t"; case "bits" -> "float";
            case "roundedRoot" -> "double"; default -> "void"; };
        int start = cpp.indexOf("\n" + type + " Demo::" + name + "(");
        assertTrue(start >= 0, name);
        int end = cpp.indexOf("\n}", start);
        assertTrue(end > start, name);
        return cpp.substring(start, end);
    }

    private void fixture(boolean managed) throws Exception {
        ClassWriter writer = base();
        writer.visitField(0, "state", "I", null, null).visitEnd();
        MethodVisitor driver = writer.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_NATIVE, "driver", "(LDemo;)I", null, null);
        AnnotationVisitor importAnnotation = driver.visitAnnotation("Lcom/github/xpenatan/jnative/interop/NativeImport;", false);
        importAnnotation.visit("value", managed ? "jnative::driver" : "external_driver");
        importAnnotation.visit("managed", managed);
        if(managed) {
            array(importAnnotation, "callbacks", "Demo.next()I");
            array(importAnnotation, "fields", "Demo.state:I");
        }
        importAnnotation.visitEnd();
        driver.visitEnd();
        MethodVisitor next = writer.visitMethod(ACC_PUBLIC, "next", "()I", null, null);
        next.visitCode();
        next.visitInsn(ICONST_1);
        next.visitInsn(IRETURN);
        next.visitMaxs(1, 1);
        next.visitEnd();
        MethodVisitor readable = writer.visitMethod(ACC_STATIC, "readable", "(LDemo;)I", null, null);
        readable.visitCode();
        readable.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        readable.visitInsn(POP);
        readable.visitVarInsn(ALOAD, 0);
        readable.visitMethodInsn(INVOKESTATIC, "Demo", "driver", "(LDemo;)I", false);
        readable.visitInsn(IRETURN);
        readable.visitMaxs(1, 1);
        readable.visitEnd();
        MethodVisitor fallback = writer.visitMethod(ACC_STATIC, "fallback", "(ILDemo;)I", null, null);
        Label first = new Label(), second = new Label(), end = new Label();
        fallback.visitCode();
        fallback.visitVarInsn(ILOAD, 0);
        fallback.visitJumpInsn(IFLE, end);
        fallback.visitVarInsn(ILOAD, 0);
        fallback.visitInsn(ICONST_1);
        fallback.visitInsn(IAND);
        fallback.visitJumpInsn(IFNE, second);
        fallback.visitLabel(first);
        fallback.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        fallback.visitInsn(POP);
        fallback.visitVarInsn(ALOAD, 1);
        fallback.visitMethodInsn(INVOKESTATIC, "Demo", "driver", "(LDemo;)I", false);
        fallback.visitInsn(POP);
        fallback.visitIincInsn(0, -1);
        fallback.visitVarInsn(ILOAD, 0);
        fallback.visitJumpInsn(IFLE, end);
        fallback.visitLabel(second);
        fallback.visitIincInsn(0, -1);
        fallback.visitVarInsn(ILOAD, 0);
        fallback.visitJumpInsn(IFGT, first);
        fallback.visitLabel(end);
        fallback.visitInsn(ICONST_0);
        fallback.visitInsn(IRETURN);
        fallback.visitMaxs(2, 2);
        fallback.visitEnd();
        MethodVisitor main = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        main.visitCode();
        main.visitInsn(ACONST_NULL);
        main.visitMethodInsn(INVOKESTATIC, "Demo", "readable", "(LDemo;)I", false);
        main.visitInsn(POP);
        main.visitInsn(ICONST_1);
        main.visitInsn(ACONST_NULL);
        main.visitMethodInsn(INVOKESTATIC, "Demo", "fallback", "(ILDemo;)I", false);
        main.visitInsn(POP);
        main.visitInsn(RETURN);
        main.visitMaxs(2, 1);
        main.visitEnd();
        save(writer);
    }
    private static void array(AnnotationVisitor annotation, String name, String value) {
        AnnotationVisitor values = annotation.visitArray(name);
        values.visit(null, value);
        values.visitEnd();
    }
    private static ClassWriter base() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V17, ACC_PUBLIC, "Demo", null, "java/lang/Object", null);
        AnnotationVisitor include = writer.visitAnnotation("Lcom/github/xpenatan/jnative/interop/NativeInclude;", false);
        include.visit("value", "jn_classlib.hpp");
        include.visitEnd();
        return writer;
    }
    private static void main(ClassWriter writer, String target, String descriptor, boolean receiver) {
        MethodVisitor main = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        main.visitCode();
        if(receiver) main.visitInsn(ACONST_NULL);
        main.visitMethodInsn(INVOKESTATIC, "Demo", target, descriptor, false);
        if(Type.getReturnType(descriptor).getSort() != Type.VOID) main.visitInsn(POP);
        main.visitInsn(RETURN);
        main.visitMaxs(1, 1);
        main.visitEnd();
    }
    private void save(ClassWriter writer) throws Exception {
        writer.visitEnd();
        Files.write(directory.resolve("Demo.class"), writer.toByteArray());
    }
    private NativeBuildRequest request() {
        return new NativeBuildRequest(List.of(directory), "Demo", directory.resolve("out"), null, null, "demo", BuildType.DEBUG, false);
    }
}
