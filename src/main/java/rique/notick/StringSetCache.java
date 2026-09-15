package rique.notick;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class StringSetCache {
    private boolean initialized;
    private Set<String> cached = Set.of();

    synchronized Set<String> get(List<? extends String> source) {
        if (initialized) return cached;
        initialized = true;
        HashSet<String> rebuilt = new HashSet<>(source.size());
        for (String entry : source) {
            if (entry != null) {
                String normalized = entry.trim();
                if (!normalized.isEmpty()) {
                    rebuilt.add(normalized.toLowerCase(Locale.ROOT));
                }
            }
        }
        cached = rebuilt;
        return cached;
    }

    synchronized void clear() {
        initialized = false;
        cached = Set.of();
    }
}
