package com.ajadi.keys;

import java.util.ArrayList;
import java.util.List;
import com.ajadi.keys.KeyLayout.Language;

/**
 * Two primary languages toggle on the first switch after typing.
 * Repeated switches without typing traverse the remaining languages.
 * Reversing direction reverses the current traversal.
 */
final class LanguageCycle {
    private Language current, first, second, lastPrimary;
    private Language[] traversal;
    private int index, initialDirection;

    LanguageCycle(Language current, Language first, Language second, Language lastPrimary) {
        this.current = current;
        configure(first, second);
        if (isPrimary(lastPrimary)) this.lastPrimary = lastPrimary;
        else if (isPrimary(current)) this.lastPrimary = current;
    }

    void configure(Language a, Language b) {
        if (a == null || b == null || a == b) {
            a = Language.RU;
            b = Language.EN;
        }
        if (first == a && second == b) return;
        first = a; second = b;
        if (!isPrimary(lastPrimary)) lastPrimary = isPrimary(current) ? current : first;
        reset();
    }

    Language current() { return current; }
    Language lastPrimary() { return lastPrimary; }
    boolean isPrimary(Language language) { return language == first || language == second; }

    Language step(int direction) {
        int dir = direction < 0 ? -1 : 1;
        if (traversal == null) {
            List<Language> order = new ArrayList<>();
            order.add(current);
            if (isPrimary(current)) {
                order.add(current == first ? second : first);
            } else {
                order.add(lastPrimary);
                order.add(lastPrimary == first ? second : first);
            }
            for (Language l : Language.values()) if (!order.contains(l)) order.add(l);
            traversal = order.toArray(new Language[0]);
            index = 0;
            initialDirection = dir;
        }
        index = Math.floorMod(index + (dir == initialDirection ? 1 : -1), traversal.length);
        current = traversal[index];
        return current;
    }

    void select(Language target) { current=target; reset(); }

    void textEntered() {
        if (isPrimary(current)) lastPrimary = current;
        reset();
    }

    void reset() { traversal = null; }
}
