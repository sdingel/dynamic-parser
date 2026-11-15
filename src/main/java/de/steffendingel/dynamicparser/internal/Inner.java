package de.steffendingel.dynamicparser.internal;

/**
 * What is known about the section of the input matched by an item, that is, by the symbols of the right-hand side
 * before the dot.
 *
 * @param forest the forest of the section if {@code exact} is set: one entry per symbol; otherwise the forest that all
 *               the ways of matching the section begin with
 * @param exact  whether the section can be matched in exactly one way, where an ambiguous section within it counts as
 *               one {@link AmbiguousNode}
 */
record Inner(Cell forest, boolean exact) {

	/** The summary of an item that has not matched anything yet. */
	static final Inner EMPTY = new Inner(null, true);
}
