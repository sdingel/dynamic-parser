package de.steffendingel.dynamicparser;

import java.util.List;

/**
 * A match: the application of a {@link Rule} to a section of the input.
 * <p>
 * A match is passed to the {@link Action} of its rule. It gives access to the values of the symbols on the right-hand
 * side of the rule and to a {@link GrammarEditor} for changing the grammar. A match must only be used while the action
 * it was passed to is running.
 * <p>
 * Positions lie between tokens: position 0 is before the first token, position {@code n} after the n-th token. A match
 * that covers the first three tokens has the start position 0 and the end position 3. A match of a rule with an empty
 * right-hand side has equal start and end positions.
 */
public interface Match {

	/**
	 * Returns the rule that was applied.
	 *
	 * @return the rule
	 */
	Rule rule();

	/**
	 * Returns the position at which the matched section of the input begins.
	 *
	 * @return the start position
	 */
	int start();

	/**
	 * Returns the position at which the matched section of the input ends.
	 *
	 * @return the end position, not less than {@link #start()}
	 */
	int end();

	/**
	 * Returns the values of the symbols on the right-hand side of the rule, in the order of the symbols. The value of a
	 * {@link Terminal} is the matched token; the value of a {@link Nonterminal} is the value returned by the action of
	 * its match, which may be {@code null}.
	 *
	 * @return an unmodifiable list with one element per symbol of the right-hand side
	 */
	List<Object> values();

	/**
	 * Returns the number of symbols on the right-hand side of the rule.
	 *
	 * @return the number of values
	 */
	default int size() {
		return values().size();
	}

	/**
	 * Returns the value of the symbol at the given index of the right-hand side of the rule.
	 *
	 * @param index the index of the symbol, starting at 0
	 * @return the value, see {@link #values()}
	 * @throws IndexOutOfBoundsException if the index is out of range
	 */
	default Object value(int index) {
		return values().get(index);
	}

	/**
	 * Returns the value of the symbol at the given index of the right-hand side of the rule, cast to the given type.
	 *
	 * @param <T>   the expected type of the value
	 * @param index the index of the symbol, starting at 0
	 * @param type  the expected type of the value
	 * @return the value, see {@link #values()}
	 * @throws IndexOutOfBoundsException if the index is out of range
	 * @throws ClassCastException        if the value is not {@code null} and not of the given type
	 */
	default <T> T value(int index, Class<T> type) {
		return type.cast(value(index));
	}

	/**
	 * Returns the editor for changing the grammar of the running parse.
	 *
	 * @return the grammar editor
	 */
	GrammarEditor grammar();
}
