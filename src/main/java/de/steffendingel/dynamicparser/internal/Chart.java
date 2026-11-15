package de.steffendingel.dynamicparser.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.Rule;
import de.steffendingel.dynamicparser.Terminal;

/**
 * The Earley chart of the input read so far: one {@link ItemSet} per position.
 * <p>
 * The chart is built token by token with Earley's algorithm. The item set of a position contains every application of a
 * rule that is consistent with the tokens before that position.
 */
final class Chart {

	private final Map<Nonterminal, List<Rule>> rules;

	private final List<Object> tokens = new ArrayList<>();

	private final List<ItemSet> sets = new ArrayList<>();

	/**
	 * Creates the chart of the empty input.
	 *
	 * @param rules the rules of the grammar by their left-hand side
	 * @param start the start symbol
	 */
	Chart(Map<Nonterminal, List<Rule>> rules, Nonterminal start) {
		this.rules = rules;
		ItemSet first = new ItemSet();
		sets.add(first);
		predict(first, start, 0);
		close(first, 0);
	}

	/**
	 * Returns the position after the last token read.
	 */
	int position() {
		return sets.size() - 1;
	}

	ItemSet set(int position) {
		return sets.get(position);
	}

	/**
	 * Returns the token between the positions {@code position} and {@code position + 1}.
	 */
	Object token(int position) {
		return tokens.get(position);
	}

	/**
	 * Reads the next token.
	 *
	 * @return {@code false} if no item can take the token; the chart is unchanged in that case
	 */
	boolean advance(Object token) {
		ItemSet next = scan(sets.get(position()), token);
		if (next.isEmpty()) {
			return false;
		}
		tokens.add(token);
		sets.add(next);
		close(next, position());
		return true;
	}

	/**
	 * Returns the items that result from matching the token with the items of the given set.
	 */
	private static ItemSet scan(ItemSet current, Object token) {
		ItemSet next = new ItemSet();
		// The predicate of a terminal is called at most once per token.
		Map<Terminal, Boolean> matches = new HashMap<>();
		for (int i = 0; i < current.size(); i++) {
			Item item = current.get(i);
			if (!item.isComplete() && item.next() instanceof Terminal terminal
					&& matches.computeIfAbsent(terminal, key -> key.matches(token))) {
				next.add(item.advance());
			}
		}
		return next;
	}

	/**
	 * Adds all items to the set that follow from the items it contains: the rules of the nonterminals that can begin at
	 * this position (prediction) and the items that are advanced by the matches ending at this position (completion).
	 */
	private void close(ItemSet set, int position) {
		// The set grows while it is processed.
		for (int i = 0; i < set.size(); i++) {
			Item item = set.get(i);
			if (item.isComplete()) {
				complete(set, item);
			} else if (item.next() instanceof Nonterminal nonterminal) {
				set.addWaiting(nonterminal, item);
				predict(set, nonterminal, position);
				// An empty match of the nonterminal may already have been completed at this position. The items
				// waiting at that time have been advanced, this item has to be advanced here.
				if (set.isCompleted(nonterminal, position)) {
					advance(set, item, position);
				}
			}
		}
	}

	private void predict(ItemSet set, Nonterminal nonterminal, int position) {
		if (set.markPredicted(nonterminal)) {
			for (Rule rule : rules.getOrDefault(nonterminal, List.of())) {
				set.add(new Item(rule, 0, position));
			}
		}
	}

	private void complete(ItemSet set, Item item) {
		set.addCompleted(item);
		// For an empty match the origin is this set. Its list of waiting items does not change in this loop, because
		// items are only registered as waiting in close().
		for (Item waiting : sets.get(item.origin()).waiting(item.rule().lhs())) {
			advance(set, waiting, item.origin());
		}
	}

	/**
	 * Adds the item that results from advancing the given item over a match of its next symbol that begins at the given
	 * position and ends at the position of the set.
	 */
	private static void advance(ItemSet set, Item item, int childOrigin) {
		Item advanced = item.advance();
		set.add(advanced);
		set.addChildOrigin(advanced, childOrigin);
	}
}
