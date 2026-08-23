package org.main.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.main.content.PlayerRegionLibrary;
import org.main.engine.ApplicationPaths;
import org.main.engine.DungeonMap;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveSystemSlotTest {
    @TempDir
    Path temporaryRoot;

    private String previousApplicationRoot;

    @BeforeEach
    void useTemporaryApplicationRoot() {
        previousApplicationRoot = System.getProperty(ApplicationPaths.APPLICATION_ROOT_PROPERTY);
        System.setProperty(ApplicationPaths.APPLICATION_ROOT_PROPERTY, temporaryRoot.toString());
    }

    @AfterEach
    void restoreApplicationRoot() {
        if (previousApplicationRoot == null) {
            System.clearProperty(ApplicationPaths.APPLICATION_ROOT_PROPERTY);
        } else {
            System.setProperty(ApplicationPaths.APPLICATION_ROOT_PROPERTY, previousApplicationRoot);
        }
    }

    @Test
    void playerNamesCreateIndependentSlotsAndSameNameOverwrites() throws Exception {
        GameState state = new GameState(DungeonMap.testMap(),
                GameBootstrap.createPlayerCharacter("Alice", PlayerRegionLibrary.MIDLANDS));
        SaveSystem.save(state);
        Path alicePath = SaveSystem.getSavePath("Alice");

        state.setPlayerCharacter(GameBootstrap.createPlayerCharacter("Bob", PlayerRegionLibrary.MIDLANDS));
        SaveSystem.save(state);
        Path bobPath = SaveSystem.getSavePath("Bob");

        assertNotEquals(alicePath, bobPath);
        assertEquals(Set.of("Alice", "Bob"), SaveSystem.listSaves().stream()
                .map(SaveSystem.SaveInfo::playerName).collect(java.util.stream.Collectors.toSet()));

        state.setPlayerCharacter(GameBootstrap.createPlayerCharacter("  ALICE  ", PlayerRegionLibrary.MIDLANDS));
        SaveSystem.save(state);

        assertEquals(alicePath, SaveSystem.getSavePath("alice"));
        assertEquals(2, SaveSystem.listSaves().size());
        assertEquals("ALICE", SaveSystem.inspect(alicePath).playerName());
    }

    @Test
    void packDifferenceMessageExplainsThatNewPacksAreExcluded() {
        SaveSystem.PackDifference difference = new SaveSystem.PackDifference(
                true,
                List.of("new.content@2.0.0"),
                List.of("saved.content@1.0.0"),
                List.of("changed.content@1.2.0")
        );

        assertTrue(difference.playerMessage().contains("without newly enabled content packs"));
        assertTrue(difference.playerMessage().contains("new.content@2.0.0"));
        assertTrue(difference.playerMessage().contains("saved.content@1.0.0"));
        assertTrue(difference.playerMessage().contains("changed.content@1.2.0"));
    }
}
