package dev.akre.protege.temp;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.compiler.ProtoCodegen;
import dev.akre.protege.ProtoUtils;
import dev.akre.protege.testutil.ClassAssert;
import dev.akre.protege.testutil.TestProtos;
import dev.akre.protege.testutil.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GameProtoTest {

    @Test
    public void testMatchingMethods() {
        Arguments inventoryArg = TestProtos.ALL_MESSAGES.stream().filter(a -> ((Class)a.get()[3]).getSimpleName().equals("Inventory")).findAny().orElseThrow();
        Class<?> expected = ((Class)inventoryArg.get()[3]);
        Class<?> generated = ((Class)inventoryArg.get()[4]);

        ClassAssert.assertThat(generated)
                .hasPublicMethodsEqualTo(expected);
    }

    @Test
    public void testGameProtoCodegen() throws Exception {
        Path protoPath = Paths.get("src/test/proto/game.proto");
        DescriptorProtos.FileDescriptorProto parsedProto = ProtoUtils.parseProto(protoPath.toFile());
        String outerClassName = TestUtils.makeOuterClassName(parsedProto, protoPath.getFileName().toString());
        
        ProtoCodegen codegen = new ProtoCodegen(new TestUtils.MockFiler());
        var generatedFile = codegen.generateFile(parsedProto);
        assertNotNull(generatedFile);
        
        // This will attempt to compile the generated code.
        // If there's a type clash, compilation will fail with an exception.
        Class<?> generatedClass = TestUtils.compile(outerClassName, generatedFile);
        assertNotNull(generatedClass);

        // Find nested classes
        Class<?> playerClass = findNestedClass(generatedClass, "Player");
        Class<?> inventoryClass = findNestedClass(playerClass, "Inventory");
        Class<?> statsClass = findNestedClass(playerClass, "Stats");
        Class<?> worldClass = findNestedClass(generatedClass, "World");
        Class<?> itemsEntry = findNestedClass(inventoryClass, "ItemsEntry");

        // Assertions for Player
        ClassAssert.assertThat(playerClass)
                .hasMethod("getId", String.class)
                .hasMethod("getUsername", String.class)
                .hasMethod("getLevel", int.class)
                .hasMethod("getInventory", inventoryClass)
                .hasMethod("getStats", statsClass);

        // Assertions for Inventory (contains a map and a repeated field)
        // map<string, int32> items = 1;
        ClassAssert.assertThat(inventoryClass)
                .hasMethod("getItemsMap", java.util.Map.class)
                .hasMethod("getItemsCount", int.class)
                .hasNoMethod("getItemsList");

        // repeated string equipped_gear = 2;
        ClassAssert.assertThat(inventoryClass)
                .hasMethod("getEquippedGearList", List.class)
                .hasMethod("getEquippedGearCount", int.class)
                .hasMethod("getEquippedGear", String.class, int.class)
                .hasNoMethod("getEquippedGear"); // Generic getter should be gone

        // Assertions for Stats
        ClassAssert.assertThat(statsClass)
                .hasMethod("getHealth", int.class)
                .hasMethod("getMana", int.class)
                // map<string, float> attributes = 3;
                .hasMethod("getAttributesMap", java.util.Map.class)
                .hasMethod("getAttributesCount", int.class)
                .hasNoMethod("getAttributesList");

        // Assertions for World
        // repeated Player players = 1;
        ClassAssert.assertThat(worldClass)
                .hasMethod("getPlayersList", List.class)
                .hasMethod("getPlayersCount", int.class)
                .hasMethod("getPlayers", playerClass, int.class)
                .hasNoMethod("getPlayers");

        // map<int32, string> zones = 2;
        ClassAssert.assertThat(worldClass)
                .hasMethod("getZonesMap", java.util.Map.class)
                .hasMethod("getZonesCount", int.class)
                .hasNoMethod("getZonesList");

        // Verify Builders
        Class<?> playerBuilderClass = findNestedClass(playerClass, "Builder");
        Class<?> inventoryBuilderClass = findNestedClass(inventoryClass, "Builder");
        Class<?> worldBuilderClass = findNestedClass(worldClass, "Builder");

        // Player Builder
        ClassAssert.assertThat(playerBuilderClass)
                .hasMethod("getId", String.class)
                .hasMethod("getUsername", String.class)
                .hasMethod("getInventory", inventoryClass);

        // Inventory Builder
        ClassAssert.assertThat(inventoryBuilderClass)
                .hasMethod("getItemsMap", java.util.Map.class)
                .hasMethod("getItemsCount", int.class)
                .hasMethod("putItems", inventoryBuilderClass, String.class, int.class)
                .hasMethod("putAllItems", inventoryBuilderClass, java.util.Map.class)
                .hasNoMethod("getItemsList")
                .hasDeprecatedMethod("getItems", Map.class);

        // World Builder
        ClassAssert.assertThat(worldBuilderClass)
                .hasMethod("getPlayersList", List.class)
                .hasMethod("getPlayersCount", int.class)
                .hasMethod("getPlayers", playerClass, int.class)
                .hasMethod("getZonesMap", java.util.Map.class)
                .hasMethod("getZonesCount", int.class)
                .hasMethod("putZones", worldBuilderClass, int.class, String.class)
                .hasMethod("putAllZones", worldBuilderClass, java.util.Map.class)
                .hasNoMethod("getPlayers");
    }

    @Test
    public void testGameProtoCodegenWithoutDeprecated() throws Exception {
        Path protoPath = Paths.get("src/test/proto/game.proto");
        DescriptorProtos.FileDescriptorProto parsedProto = ProtoUtils.parseProto(protoPath.toFile());
        // Use a different outer class name to avoid conflicts if any
        parsedProto = parsedProto.toBuilder().setOptions(parsedProto.getOptions().toBuilder().setJavaOuterClassname("GameNoDeprecated")).build();
        String outerClassName = "com.example.proto.generated.GameNoDeprecated";

        ProtoCodegen codegen = new ProtoCodegen(new TestUtils.MockFiler(), false);
        var generatedFile = codegen.generateFile(parsedProto);
        assertNotNull(generatedFile);

        Class<?> generatedClass = TestUtils.compile(outerClassName, generatedFile);
        assertNotNull(generatedClass);

        Class<?> playerClass = findNestedClass(generatedClass, "Player");
        Class<?> inventoryClass = findNestedClass(playerClass, "Inventory");
        Class<?> inventoryBuilderClass = findNestedClass(inventoryClass, "Builder");

        // Verify that deprecated method is NOT present in message
        ClassAssert.assertThat(inventoryClass)
                .hasNoMethod("getItems");

        // Verify that deprecated method is NOT present in builder
        ClassAssert.assertThat(inventoryBuilderClass)
                .hasNoMethod("getItems");
    }

    private Class<?> findNestedClass(Class<?> parent, String simpleName) {
        for (Class<?> clazz : parent.getDeclaredClasses()) {
            if (clazz.getSimpleName().equals(simpleName)) {
                return clazz;
            }
        }
        fail("Could not find nested class " + simpleName + " in " + parent.getName());
        return null;
    }
}
