package de.steffendingel.dynamicparser.internal;

/**
 * A forest as an immutable linked list: the last entry of the forest and the forest before it. The empty forest is
 * {@code null}. Forests that begin in the same way share their cells.
 */
final class Cell {

	/** The forest before the last entry, or {@code null} if this cell is the first one. */
	final Cell below;

	/** The last entry of the forest. */
	final Entry entry;

	/** The number of entries of the forest. */
	final int depth;

	/** Whether the actions of all matches in this forest have been called. */
	boolean settled;

	/**
	 * Whether the action of the match that is the last entry has been called. A match is identified by its node
	 * together with the forest before it, that is, by this cell.
	 */
	boolean called;

	/** The value returned by the action. Only meaningful if {@link #called} is set. */
	Object value;

	Cell(Cell below, Entry entry) {
		this.below = below;
		this.entry = entry;
		this.depth = below == null ? 1 : below.depth + 1;
	}
}
