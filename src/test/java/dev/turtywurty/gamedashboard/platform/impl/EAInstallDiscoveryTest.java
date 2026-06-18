package dev.turtywurty.gamedashboard.platform.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EAInstallDiscoveryTest {
    @Test
    void parsesRegistryKeysAndValuesContainingSpaces() {
        String output = """
                HKEY_LOCAL_MACHINE\\SOFTWARE\\EA Games\\The Sims 4
                    DisplayName    REG_SZ    The Sims(TM) 4
                    Install Dir    REG_SZ    D:\\EA Games\\The Sims 4\\

                HKEY_LOCAL_MACHINE\\SOFTWARE\\EA Games\\Another Game
                    Product GUID    REG_SZ    {1234}
                """;

        List<EAInstallDiscovery.RegistryEntry> entries = EAInstallDiscovery.parseRegistryOutput(output);

        assertEquals(2, entries.size());
        assertEquals("The Sims(TM) 4", entries.getFirst().value("DisplayName"));
        assertEquals("D:\\EA Games\\The Sims 4\\", entries.getFirst().value("Install Dir"));
        assertEquals("Another Game", entries.getLast().keyName());
        assertEquals("{1234}", entries.getLast().value("Product GUID"));
    }

    @Test
    void normalizesEaNamingVariants() {
        assertEquals("command and conquer red alert 2",
                EAInstallDiscovery.normalize("Command & Conquer(TM) Red Alert II"));
        assertEquals("battlefield 5", EAInstallDiscovery.normalize("Battlefield(TM) V"));
    }

    @Test
    void cleansInstallDataTitlesWithoutUsingCorruptedRegistryDisplayNames() {
        assertEquals("Need for Speed Most Wanted",
                EAInstallDiscovery.cleanTitle("Need for Speed(TM) Most Wanted"));
        assertEquals("The Sims 4", EAInstallDiscovery.cleanTitle("The Sims 4"));
    }
}
