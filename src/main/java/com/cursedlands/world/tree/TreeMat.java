package com.cursedlands.world.tree;

/** What a voxel of a generated tree is made of. The game layer maps each value to a block state. */
public enum TreeMat {
	LOG_BLACK(Kind.LOG),
	LOG_PALE(Kind.LOG),
	LOG_CHARRED(Kind.LOG),
	LOG_PINE(Kind.LOG),
	LEAF_WITHERED(Kind.LEAF),
	LEAF_NEEDLES(Kind.LEAF),
	LEAF_CRIMSON(Kind.LEAF),
	LEAF_ASHEN(Kind.LEAF),
	/** Hanging vine, placed in columns below leaves and branches. */
	VINE(Kind.VINE),
	/** Shelf fungus on a trunk; the direction it faces is stored with the voxel. */
	FUNGUS(Kind.FUNGUS);

	public enum Kind { LOG, LEAF, VINE, FUNGUS }

	public final Kind kind;

	TreeMat(Kind kind) {
		this.kind = kind;
	}

	public boolean isLog() {
		return this.kind == Kind.LOG;
	}

	public boolean isLeaf() {
		return this.kind == Kind.LEAF;
	}
}
