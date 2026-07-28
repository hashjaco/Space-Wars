package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.geometry.Rectangle2D;

import org.junit.jupiter.api.Test;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.Entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuadTreeTest {

    private static final Rectangle2D ARENA =
            new Rectangle2D(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

    @Test
    void retrievesAnEntityOccupyingTheSameSpot() {
        QuadTree tree = new QuadTree(ARENA);
        Entity target = asteroid(100, 100);
        tree.insert(target);

        List<Entity> found = new ArrayList<>();
        tree.retrieve(found, asteroid(102, 102));

        assertTrue(found.contains(target));
    }

    @Test
    void neverReturnsDuplicates() {
        QuadTree tree = new QuadTree(ARENA);
        // Enough entities across the arena to force several splits.
        for (int x = 0; x < GameConfig.WIDTH; x += 40) {
            for (int y = 0; y < GameConfig.HEIGHT; y += 40) {
                tree.insert(asteroid(x, y));
            }
        }

        List<Entity> found = new ArrayList<>();
        tree.retrieve(found, asteroid(500, 400));

        Set<Entity> unique = new HashSet<>(found);
        assertEquals(unique.size(), found.size(), "retrieve must not return the same entity twice");
    }

    @Test
    void findsCandidatesEvenWhenTheProbeStraddlesAQuadrantBoundary() {
        QuadTree tree = new QuadTree(ARENA);
        double midX = GameConfig.WIDTH / 2;
        double midY = GameConfig.HEIGHT / 2;

        // Populate enough to split, then place a neighbour right on the centre line.
        for (int i = 0; i < 40; i++) {
            tree.insert(asteroid(20 + i, 20 + i));
        }
        Entity onTheLine = asteroid(midX - 10, midY - 10);
        tree.insert(onTheLine);

        List<Entity> found = new ArrayList<>();
        tree.retrieve(found, asteroid(midX - 8, midY - 8));

        assertTrue(found.contains(onTheLine),
                "a probe overlapping the centre must still see entities on the boundary");
    }

    @Test
    void clearEmptiesEverything() {
        QuadTree tree = new QuadTree(ARENA);
        for (int i = 0; i < 60; i++) {
            tree.insert(asteroid(i * 10, i * 8));
        }
        tree.clear();

        List<Entity> found = new ArrayList<>();
        tree.retrieve(found, asteroid(50, 50));

        assertTrue(found.isEmpty());
    }

    private static Asteroid asteroid(double x, double y) {
        Asteroid created = new Asteroid(Sprite.ASTEROID_SMALL, x, y, 20, 9, 10);
        return created;
    }
}
