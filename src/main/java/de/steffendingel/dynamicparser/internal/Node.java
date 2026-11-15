package de.steffendingel.dynamicparser.internal;

import java.util.List;

import de.steffendingel.dynamicparser.Rule;

/**
 * A complete match with a unique structure: a rule, the section of the input and the entries matched by the symbols of
 * the right-hand side.
 * <p>
 * A node stands for the content of a match. Empty matches with the same content can occur several times in a result,
 * for example two matches of {@code A -> ε} for {@code S -> A A}. They are the same node; what distinguishes them is
 * the forest before them, see {@link Cell}.
 */
final class Node implements Entry {

	final Rule rule;

	final int start;

	final int end;

	/** The index of the item set in which the application of the rule began. */
	final int origin;

	final List<Entry> children;

	Node(Rule rule, int start, int end, int origin, List<Entry> children) {
		this.rule = rule;
		this.start = start;
		this.end = end;
		this.origin = origin;
		this.children = children;
	}

	@Override
	public String toString() {
		return rule + " from " + start + " to " + end;
	}
}
