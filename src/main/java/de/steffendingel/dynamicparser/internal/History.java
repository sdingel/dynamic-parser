package de.steffendingel.dynamicparser.internal;

import java.util.Collections;
import java.util.List;

/**
 * What is known about the readings that lead to a certain point.
 * <p>
 * A reading is a complete interpretation of the input read so far. It corresponds to a forest of the input from its
 * beginning, and to a sequence: the matches of the forest in the order in which their actions are called. The readings
 * that lead to a point are not listed individually. What matters is how their sequences begin, because the actions that
 * can be called are the ones that all sequences begin with.
 * <p>
 * Usually the sequences differ somewhere, and then it does not matter how the readings continue: what they have in
 * common ends at that difference. This is the {@code rest}. The exception are readings whose complete sequence is the
 * beginning of the sequences of all others. That happens with empty matches, for example when a nonterminal can begin
 * at a position both before and after an empty match. If such readings continue in the same way, the continuation can
 * add to what they have in common. These readings are therefore kept individually, as the {@code chain}.
 *
 * @param chain   forests of individual readings, ordered such that the sequence of each one is the beginning of the
 *                sequences of all following ones and of the remaining readings; the empty forest is {@code null}
 * @param hasRest whether there are remaining readings. There are then at least two of them, and none of them is the
 *                forest {@code rest} itself, or the number of individual readings has exceeded {@link #MAX_CHAIN}
 * @param rest    the forest that the sequences of all remaining readings begin with
 */
record History(List<Cell> chain, boolean hasRest, Cell rest) {

	/** The maximum number of readings that are kept individually. */
	static final int MAX_CHAIN = 4;

	/** The history of the beginning of the input: one reading with an empty forest. */
	static final History ROOT = single(null);

	/**
	 * Creates the history of a single reading.
	 */
	static History single(Cell forest) {
		return new History(Collections.singletonList(forest), false, null);
	}

	/**
	 * Returns the forest that the sequences of all readings begin with.
	 */
	Cell common() {
		return chain.isEmpty() ? rest : chain.get(0);
	}
}
