package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Rectangle2D;

import com.hashimjacobs.spacecase.entity.Entity;

/**
 * Spatial index that narrows collision checks to entities sharing a quadrant.
 *
 * Rewritten from the original in two ways: it no longer declares an unused type parameter, and it
 * takes each entity's size from the entity rather than from its decoded image, so it works before
 * any sprite is loaded and can be tested without the JavaFX toolkit.
 */
public final class QuadTree {

    private static final int MAX_ENTITIES_PER_NODE = 6;
    private static final int MAX_DEPTH = 4;

    private final int depth;
    private final Rectangle2D bounds;
    private final List<Entity> entities = new ArrayList<>();
    private final QuadTree[] children = new QuadTree[4];

    public QuadTree(Rectangle2D bounds) {
        this(0, bounds);
    }

    private QuadTree(int depth, Rectangle2D bounds) {
        this.depth = depth;
        this.bounds = bounds;
    }

    public void clear() {
        entities.clear();
        for (int i = 0; i < children.length; i++) {
            if (children[i] != null) {
                children[i].clear();
                children[i] = null;
            }
        }
    }

    public void insert(Entity entity) {
        if (children[0] != null) {
            int index = quadrantOf(entity);
            if (index != -1) {
                children[index].insert(entity);
                return;
            }
        }

        entities.add(entity);

        boolean shouldSplit = entities.size() > MAX_ENTITIES_PER_NODE && depth < MAX_DEPTH;
        if (!shouldSplit) {
            return;
        }
        if (children[0] == null) {
            split();
        }
        // Re-home whatever now fits cleanly into a child quadrant.
        int i = 0;
        while (i < entities.size()) {
            Entity candidate = entities.get(i);
            int index = quadrantOf(candidate);
            if (index == -1) {
                i++;
            } else {
                entities.remove(i);
                children[index].insert(candidate);
            }
        }
    }

    /** Adds every entity that could plausibly overlap {@code probe} into {@code out}. */
    public void retrieve(List<Entity> out, Entity probe) {
        int index = quadrantOf(probe);
        if (index != -1 && children[0] != null) {
            children[index].retrieve(out, probe);
        } else if (children[0] != null) {
            // Straddles a boundary, so it could hit anything in any child.
            for (QuadTree child : children) {
                child.retrieve(out, probe);
            }
        }
        out.addAll(entities);
    }

    private void split() {
        double halfWidth = bounds.getWidth() / 2;
        double halfHeight = bounds.getHeight() / 2;
        double x = bounds.getMinX();
        double y = bounds.getMinY();
        int childDepth = depth + 1;

        children[0] = new QuadTree(childDepth, new Rectangle2D(x + halfWidth, y, halfWidth, halfHeight));
        children[1] = new QuadTree(childDepth, new Rectangle2D(x, y, halfWidth, halfHeight));
        children[2] = new QuadTree(childDepth, new Rectangle2D(x, y + halfHeight, halfWidth, halfHeight));
        children[3] = new QuadTree(childDepth, new Rectangle2D(x + halfWidth, y + halfHeight, halfWidth, halfHeight));
    }

    /** Index of the quadrant that fully contains the entity, or -1 when it straddles a boundary. */
    private int quadrantOf(Entity entity) {
        double midX = bounds.getMinX() + bounds.getWidth() / 2;
        double midY = bounds.getMinY() + bounds.getHeight() / 2;

        boolean fitsTop = entity.y() + entity.height() < midY;
        boolean fitsBottom = entity.y() > midY;
        boolean fitsLeft = entity.x() + entity.width() < midX;
        boolean fitsRight = entity.x() > midX;

        if (fitsLeft && fitsTop) {
            return 1;
        }
        if (fitsLeft && fitsBottom) {
            return 2;
        }
        if (fitsRight && fitsTop) {
            return 0;
        }
        if (fitsRight && fitsBottom) {
            return 3;
        }
        return -1;
    }
}
