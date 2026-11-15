package de.steffendingel.dynamicparser.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.Rule;
import de.steffendingel.dynamicparser.Terminal;

/**
 * The Earley chart of the input read so far: a sequence of {@link ItemSet item sets}.
 * <p>
 * The chart is built token by token with Earley's algorithm. An item set contains the applications of rules that are
 * consistent with the tokens before its position. Usually there is one item set per position. When an action changes
 * the grammar, a further item set is created for the same position, see {@link #restart(Map)}; from then on it replaces
 * the previous one. Item sets are identified by their index in the chart, and the origin of an item is the index of the
 * item set in which its rule was predicted.
 */
final class Chart {

	/**
	 * Determines the items that a complete item advances. This replaces the default, all items waiting for the
	 * nonterminal in the item set of the origin, where more is known.
	 */
	interface Parents {

		/**
		 * Returns the items waiting for the match of the given complete item, or {@code null} for the default.
		 */
		List<Item> of(Item completed);
	}

	private final RunGrammar grammar;

	private final List<Object> tokens = new ArrayList<>();

	private final List<ItemSet> sets = new ArrayList<>();

	/** The position of every item set. */
	private final List<Integer> positions = new ArrayList<>();

	/** For every position, the index of its first item set. */
	private final List<Integer> firstSets = new ArrayList<>();

	private Parents parents = completed -> null;

	/**
	 * Creates the chart of the empty input.
	 *
	 * @param grammar the grammar of the parse run; predictions use its rules at the time of the prediction
	 * @param start   the start symbol
	 */
	Chart(RunGrammar grammar, Nonterminal start) {
		this.grammar = grammar;
		ItemSet first = addSet(0);
		predict(first, start, 0);
		close(first, 0);
	}

	void setParents(Parents parents) {
		this.parents = parents;
	}

	/**
	 * Returns the index of the current item set.
	 */
	int current() {
		return sets.size() - 1;
	}

	/**
	 * Returns the position after the last token read.
	 */
	int position() {
		return tokens.size();
	}

	/**
	 * Returns the position of the item set with the given index.
	 */
	int position(int index) {
		return positions.get(index);
	}

	/**
	 * Returns the index of the first item set of the given position.
	 */
	int firstSet(int position) {
		return firstSets.get(position);
	}

	/**
	 * Returns the index of the last item set of the given position.
	 */
	int lastSet(int position) {
		return position == position() ? current() : firstSets.get(position + 1) - 1;
	}

	ItemSet set(int index) {
		return sets.get(index);
	}

	/**
	 * Returns the token between the positions {@code position} and {@code position + 1}.
	 */
	Object token(int position) {
		return tokens.get(position);
	}

	private ItemSet addSet(int position) {
		ItemSet set = new ItemSet();
		sets.add(set);
		positions.add(position);
		if (firstSets.size() == position) {
			firstSets.add(sets.size() - 1);
		}
		return set;
	}

	/**
	 * Reads the next token.
	 *
	 * @return {@code false} if no item can take the token; the chart is unchanged in that case
	 */
	boolean advance(Object token) {
		ItemSet current = sets.get(current());
		List<Item> scanned = new ArrayList<>();
		// The predicate of a terminal is called at most once per token.
		Map<Terminal, Boolean> matches = new HashMap<>();
		for (int i = 0; i < current.size(); i++) {
			Item item = current.get(i);
			if (!item.isComplete() && item.next() instanceof Terminal terminal
					&& matches.computeIfAbsent(terminal, key -> key.matches(token))) {
				scanned.add(item.advance());
			}
		}
		if (scanned.isEmpty()) {
			return false;
		}
		tokens.add(token);
		ItemSet next = addSet(position());
		scanned.forEach(next::add);
		close(next, current());
		return true;
	}

	/**
	 * Replaces the current item set by a new one for the same position that is built from the given items with the
	 * current rules of the grammar. The previous item set stays in the chart, because the items that began there refer
	 * to it.
	 *
	 * @param items the items to start with, each with the forest of the section it has matched
	 */
	void restart(Map<Item, Cell> items) {
		ItemSet set = addSet(position());
		items.forEach((item, forest) -> {
			set.add(item);
			set.putInherited(item, forest);
		});
		close(set, current());
	}

	/**
	 * Adds all items to the set that follow from the items it contains: the rules of the nonterminals that can begin
	 * here (prediction) and the items that are advanced by the matches ending here (completion).
	 */
	private void close(ItemSet set, int index) {
		// The set grows while it is processed.
		for (int i = 0; i < set.size(); i++) {
			Item item = set.get(i);
			if (item.isComplete()) {
				complete(set, item);
			} else if (item.next() instanceof Nonterminal nonterminal) {
				set.addWaiting(nonterminal, item);
				predict(set, nonterminal, index);
				// An empty match of the nonterminal may already have been completed in this set. The items waiting
				// at that time have been advanced, this item has to be advanced here.
				if (set.isCompleted(nonterminal, index)) {
					advance(set, item, index);
				}
			}
		}
	}

	private void predict(ItemSet set, Nonterminal nonterminal, int index) {
		if (set.markPredicted(nonterminal)) {
			for (Rule rule : grammar.rules(nonterminal)) {
				Item item = new Item(rule, 0, index);
				set.add(item);
				set.addPrediction(item);
			}
		}
	}

	private void complete(ItemSet set, Item item) {
		set.addCompleted(item);
		List<Item> waiting = parents.of(item);
		if (waiting == null) {
			// For an empty match the origin is this set. Its list of waiting items does not change in this loop,
			// because items are only registered as waiting in close().
			waiting = sets.get(item.origin()).waiting(item.rule().lhs());
		}
		for (Item parent : waiting) {
			advance(set, parent, item.origin());
		}
	}

	/**
	 * Adds the item that results from advancing the given item over a match of its next symbol that begins in the item
	 * set with the given index and ends in the given set.
	 */
	private static void advance(ItemSet set, Item item, int childOrigin) {
		Item advanced = item.advance();
		set.add(advanced);
		set.addChildOrigin(advanced, childOrigin);
	}
}
