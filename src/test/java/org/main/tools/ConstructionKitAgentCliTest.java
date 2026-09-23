package org.main.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.content.MapDesignLibrary;
import org.main.engine.ApplicationPaths;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionKitAgentCliTest {
    @TempDir Path root;
    private static final String MAP = "assets/editor/maps/agent.properties";

    private Properties run(int expected, String command, String... options) throws Exception {
        List<String> args = new ArrayList<>(List.of(command, "--root", root.toString(), "--map", MAP));
        args.addAll(List.of(options));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        String previous = System.getProperty(ApplicationPaths.ACTIVE_PROJECT_PROPERTY);
        String previousDev = System.getProperty(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY);
        try {
            assertEquals(expected, ConstructionKitAgentCli.run(args.toArray(String[]::new), new PrintStream(bytes)), bytes.toString());
        } finally {
            if (previous == null) System.clearProperty(ApplicationPaths.ACTIVE_PROJECT_PROPERTY);
            else System.setProperty(ApplicationPaths.ACTIVE_PROJECT_PROPERTY, previous);
            if (previousDev == null) System.clearProperty(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY);
            else System.setProperty(ApplicationPaths.DEVELOPMENT_RESOURCES_PROPERTY, previousDev);
        }
        Properties response = new Properties();
        response.load(new ByteArrayInputStream(bytes.toByteArray()));
        return response;
    }

    @Test void createsAnEditorReadableMapAndInspectsIt() throws Exception {
        assertEquals("true", run(0, "create", "--width", "8", "--height", "6").getProperty("saved"));
        assertEquals(8, MapDesignLibrary.loadGeometryOnly(root.resolve(MAP)).width());
        Properties inspected = run(0, "inspect");
        assertEquals("6", inspected.getProperty("height"));
        assertTrue(inspected.getProperty("tiles.1").contains("FLOOR"));
        run(0, "validate");
    }

    @Test void dryRunAndInvalidEditsLeaveFileUntouched() throws Exception {
        run(0, "create", "--width", "5", "--height", "5");
        byte[] before = Files.readAllBytes(root.resolve(MAP));
        run(0, "set-tile", "--x", "2", "--y", "2", "--tile", "WALL", "--dry-run", "true");
        assertArrayEquals(before, Files.readAllBytes(root.resolve(MAP)));
        Properties invalid = run(2, "set-tile", "--x", "1", "--y", "1", "--tile", "WALL");
        assertEquals("false", invalid.getProperty("saved"));
        assertArrayEquals(before, Files.readAllBytes(root.resolve(MAP)));
        run(0, "set-tile", "--x", "2", "--y", "2", "--tile", "WALL");
        assertTrue(MapDesignLibrary.loadGeometryOnly(root.resolve(MAP)).tiles()[2][2].blocksMovement());
    }

    @Test void refusesOverwriteUnknownOptionsAndBadCoordinates() throws Exception {
        run(0, "create", "--width", "5", "--height", "5");
        byte[] before = Files.readAllBytes(root.resolve(MAP));
        run(1, "create", "--width", "6", "--height", "6");
        run(1, "set-tile", "--x", "99", "--y", "2", "--tile", "WALL");
        run(1, "set-tile", "--dryrun", "true");
        assertArrayEquals(before, Files.readAllBytes(root.resolve(MAP)));
    }

    @Test void refusesEscapingPaths() throws Exception {
        ConstructionKitMapService service = new ConstructionKitMapService(root);
        assertThrows(IOException.class, () -> service.resolve("../outside.properties"));
        assertThrows(IOException.class, () -> service.resolve("assets/../../outside.properties"));
        assertThrows(IOException.class, () -> service.resolve("configuration.properties"));
        assertThrows(IOException.class, () -> service.resolve(root.resolve(MAP).toString()));
    }

    @Test void invalidEntityReferencesAreReportedWithoutSaving() throws Exception {
        run(0, "create", "--width", "5", "--height", "5");
        byte[] before = Files.readAllBytes(root.resolve(MAP));
        Properties response = run(2, "place", "--kind", "ENEMY", "--id", "missing_agent_test_enemy",
                "--x", "2", "--y", "2");
        assertEquals("false", response.getProperty("valid"));
        assertArrayEquals(before, Files.readAllBytes(root.resolve(MAP)));
    }

    @Test void listsAssetPaths() throws Exception {
        Files.createDirectories(root.resolve("assets/images"));
        Files.writeString(root.resolve("assets/images/example.txt"), "example");
        Properties response = run(0, "list-assets");
        assertEquals("1", response.getProperty("asset.count"));
        assertEquals("assets/images/example.txt", response.getProperty("asset.0"));
    }

    @Test void placementRoundTripsAndDuplicateIsRejected() throws Exception {
        run(0, "create", "--width", "5", "--height", "5");
        String station = org.main.core.CraftingStationType.values()[0].name();
        run(0, "place", "--kind", "CRAFTING_NODE", "--id", station, "--x", "2", "--y", "3");
        Properties response = run(0, "inspect");
        assertEquals("1", response.getProperty("placement.count"));
        assertEquals(station, response.getProperty("placement.0.id"));
        run(1, "place", "--kind", "CRAFTING_NODE", "--id", station, "--x", "2", "--y", "3");
    }
}
