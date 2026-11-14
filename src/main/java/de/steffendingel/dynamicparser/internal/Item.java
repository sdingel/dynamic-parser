package de.steffendingel.dynamicparser.internal;

import de.steffendingel.dynamicparser.Rule;
import de.steffendingel.dynamicparser.Symbol;

/**
 * An Earley item: an application of a rule that began at the position {@code origin} and has matched the first
 * {@code dot} symbols of the right-hand side so far.
 *
 * @param rule   the rule being applied
 * @param dot    the number of symbols of the right-hand side that have been matched
 * @param origin the position at which the application began
 */
record Item(Rule rule, int dot, int origin) {

	/**
	 * Returns whether all symbols of the right-hand side have been matched.
	 */
	boolean isComplete() {
		return dot == rule.rhs().size();
	}

	/**
	 * Returns the symbol that has to be matched next. Must not be called for a complete item.
	 */
	Symbol next() {
		return rule.rhs().get(dot);
	}

	/**
	 * Returns the item that results from matching the next symbol.
	 */
	Item advance() {
		return new Item(rule, dot + 1, origin);
	}
}
