package rique.notick;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StringSetCacheTest {

    @Test
    void normalizesEntries() {
        StringSetCache cache = new StringSetCache();
        Set<String> result = cache.get(List.of("  Minecraft:PIG ", "CREATE", "AlexsMobs:void_worm"));

        assertEquals(3, result.size());
        assertTrue(result.contains("minecraft:pig"));
        assertTrue(result.contains("create"));
        assertTrue(result.contains("alexsmobs:void_worm"));
    }

    @Test
    void dropsNullEmptyAndBlankEntries() {
        StringSetCache cache = new StringSetCache();
        Set<String> result = cache.get(java.util.Arrays.asList("minecraft:pig", null, "", "   "));

        assertEquals(1, result.size());
        assertTrue(result.contains("minecraft:pig"));
    }

    @Test
    void cachesUntilCleared() {
        StringSetCache cache = new StringSetCache();
        Set<String> first = cache.get(List.of("minecraft:pig"));
        Set<String> second = cache.get(List.of("minecraft:cow"));

        assertSame(first, second);

        cache.clear();
        Set<String> third = cache.get(List.of("minecraft:cow"));

        assertNotSame(first, third);
        assertTrue(third.contains("minecraft:cow"));
        assertFalse(third.contains("minecraft:pig"));
    }

    @Test
    void emptySourceProducesEmptySet() {
        StringSetCache cache = new StringSetCache();
        assertTrue(cache.get(List.of()).isEmpty());
    }
}
