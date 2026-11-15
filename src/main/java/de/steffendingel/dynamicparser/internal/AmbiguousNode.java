package de.steffendingel.dynamicparser.internal;

import de.steffendingel.dynamicparser.Nonterminal;

/**
 * A section of the input that a nonterminal matches in more than one way.
 */
final class AmbiguousNode implements Entry {

	final Nonterminal symbol;

	final int start;

	final int end;

	/**
	 * The forest that all the ways of matching the section begin with, or {@code null} if they have nothing in common
	 * or if this has not been determined, as for sections that contain themselves.
	 */
	Cell common;

	AmbiguousNode(Nonterminal symbol, int start, int end) {
		this.symbol = symbol;
		this.start = start;
		this.end = end;
	}

	@Override
	public String toString() {
		return "ambiguous " + symbol + " from " + start + " to " + end;
	}
}
