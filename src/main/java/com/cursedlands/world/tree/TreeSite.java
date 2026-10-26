package com.cursedlands.world.tree;

/**
 * What the generator may ask about the place where a tree grows. All coordinates are relative to the tree origin,
 * which is the first free block above the ground in the middle of the trunk (so the ground there is at y = -1).
 */
public interface TreeSite {
	int NO_GROUND = Integer.MIN_VALUE;

	/**
	 * Relative y of the first free block above the ground in this column, scanning a few blocks up and down;
	 * {@link #NO_GROUND} when there is no ground in reach (a hole, deep water, a column outside the loaded area).
	 */
	int groundAt(int x, int z);

	/** True when a log or leaf block of a tree may take the place of whatever is at this position now. */
	boolean canReplace(int x, int y, int z);
}
