package com.cursedlands.client.map;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * What the player has seen of the map ("fog of war") and the marker the player has put on it. Works in map pixels:
 * (0, 0) is the north-west corner of the map. The explored area is a bit grid with one bit per {@link #CELL} x {@link #CELL} blocks.
 */
public final class MapMemory {
	public static final int CELL = 4;
	private static final int MAGIC = 0x434C5831; // "CLX1"

	private final int cells;
	private final long[] bits;
	private int revision;
	private boolean hasPin;
	private int pinX;
	private int pinZ;

	/** @param mapSize side of the map in blocks, a multiple of {@link #CELL} */
	public MapMemory(int mapSize) {
		cells = mapSize / CELL;
		bits = new long[(int) (((long) cells * cells + 63) / 64)];
	}

	public int mapSize() {
		return cells * CELL;
	}

	/** Changes every time the explored area or the marker changes; lets a renderer know when to repaint. */
	public int revision() {
		return revision;
	}

	public boolean isExplored(int cx, int cz) {
		if (cx < 0 || cz < 0 || cx >= cells || cz >= cells) {
			return false;
		}
		int i = cz * cells + cx;
		return (bits[i >> 6] & (1L << (i & 63))) != 0;
	}

	/** True if the block column is explored. */
	public boolean isExploredAt(double px, double pz) {
		return isExplored((int) Math.floor(px / CELL), (int) Math.floor(pz / CELL));
	}

	/**
	 * How much of the cell around the point is seen, 0 (fog) to 1 (seen), smoothed between neighbouring cells
	 * so that the edge of the explored area is not a staircase.
	 */
	public float coverage(double px, double pz) {
		double u = px / CELL - 0.5;
		double v = pz / CELL - 0.5;
		int cx = (int) Math.floor(u);
		int cz = (int) Math.floor(v);
		float fx = (float) (u - cx);
		float fz = (float) (v - cz);
		// beyond the edge of the map the nearest cell counts, otherwise the last half cell would never be fully seen
		float a = nearest(cx, cz) ? 1f : 0f;
		float b = nearest(cx + 1, cz) ? 1f : 0f;
		float c = nearest(cx, cz + 1) ? 1f : 0f;
		float d = nearest(cx + 1, cz + 1) ? 1f : 0f;
		float top = a + (b - a) * fx;
		float bottom = c + (d - c) * fx;
		return top + (bottom - top) * fz;
	}

	private boolean nearest(int cx, int cz) {
		return isExplored(Math.max(0, Math.min(cells - 1, cx)), Math.max(0, Math.min(cells - 1, cz)));
	}

	/** Marks the disc around the point as seen. Returns true if something new was revealed. */
	public boolean reveal(double px, double pz, double radius) {
		int minX = (int) Math.floor((px - radius) / CELL);
		int maxX = (int) Math.floor((px + radius) / CELL);
		int minZ = (int) Math.floor((pz - radius) / CELL);
		int maxZ = (int) Math.floor((pz + radius) / CELL);
		double r2 = radius * radius;
		boolean changed = false;
		for (int cz = Math.max(0, minZ); cz <= Math.min(cells - 1, maxZ); cz++) {
			double dz = (cz + 0.5) * CELL - pz;
			for (int cx = Math.max(0, minX); cx <= Math.min(cells - 1, maxX); cx++) {
				double dx = (cx + 0.5) * CELL - px;
				if (dx * dx + dz * dz <= r2) {
					int i = cz * cells + cx;
					long mask = 1L << (i & 63);
					if ((bits[i >> 6] & mask) == 0) {
						bits[i >> 6] |= mask;
						changed = true;
					}
				}
			}
		}
		if (changed) {
			revision++;
		}
		return changed;
	}

	/** Marks the whole map as seen. */
	public void revealAll() {
		java.util.Arrays.fill(bits, -1L);
		revision++;
	}

	public boolean hasPin() {
		return hasPin;
	}

	public int pinX() {
		return pinX;
	}

	public int pinZ() {
		return pinZ;
	}

	public void setPin(int px, int pz) {
		hasPin = true;
		pinX = px;
		pinZ = pz;
		revision++;
	}

	public void clearPin() {
		if (hasPin) {
			hasPin = false;
			revision++;
		}
	}

	public void write(DataOutputStream out) throws IOException {
		out.writeInt(MAGIC);
		out.writeInt(cells);
		out.writeBoolean(hasPin);
		out.writeInt(pinX);
		out.writeInt(pinZ);
		out.writeInt(bits.length);
		for (long word : bits) {
			out.writeLong(word);
		}
	}

	/** Reads what {@link #write} wrote. A file for a map of another size is rejected. */
	public void read(DataInputStream in) throws IOException {
		if (in.readInt() != MAGIC) {
			throw new IOException("not a Cursed Lands map memory file");
		}
		int fileCells = in.readInt();
		if (fileCells != cells) {
			throw new IOException("the file is for a map of " + fileCells * CELL + " blocks, this map has " + cells * CELL);
		}
		boolean pin = in.readBoolean();
		int x = in.readInt();
		int z = in.readInt();
		int words = in.readInt();
		if (words != bits.length) {
			throw new IOException("wrong size of the explored area");
		}
		for (int i = 0; i < words; i++) {
			bits[i] = in.readLong();
		}
		hasPin = pin;
		pinX = x;
		pinZ = z;
		revision++;
	}
}
