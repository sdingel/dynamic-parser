package de.steffendingel.dynamicparser.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.Rule;
import de.steffendingel.dynamicparser.SyntaxException;
import de.steffendingel.dynamicparser.Terminal;

/**
 * The Earley chart of an input: one {@link ItemSet} per position.
 * <p>
 * The chart is built with Earley's algorithm. The item set of a position contains every application of a rule that is
 * consistent with the tokens before that position. Building the chart only recognizes the input; derivations are
 * reconstructed from the finished chart by the {@link Evaluator}.
 */
final class Chart {

	private final Map<Nonterminal, List<Rule>> rules;

	private final Nonterminal start;

	private final List<Object> tokens;

	private final List<ItemSet> sets = new ArrayList<>();

	private Chart(Map<Nonterminal, List<Rule>> rules, Nonterminal start, List<Object> tokens) {
		this.rules = rules;
		this.start = start;
		this.tokens = tokens;
	}

	/**
	 * Builds the chart of the given tokens.
	 *
	 * @param rules  the rules of the grammar by their left-hand side
	 * @param start  the start symbol
	 * @param tokens the input
	 * @return the chart of the complete input, which is a match of the start symbol
	 * @throws SyntaxException if the input is not a match of the start symbol
	 */
	static Chart build(Map<Nonterminal, List<Rule>> rules, Nonterminal start, List<Object> tokens)
			throws SyntaxException {
		Chart chart = new Chart(rules, start, tokens);
		chart.build();
		return chart;
	}

	List<Object> tokens() {
		return tokens;
	}

	ItemSet set(int position) {
		return sets.get(position);
	}

	private void build() throws SyntaxException {
		ItemSet current = new ItemSet();
		sets.add(current);
		predict(current, start, 0);
		close(current, 0);
		for (int position = 0; position < tokens.size(); position++) {
			Object token = tokens.get(position);
			ItemSet next = scan(current, token);
			if (next.isEmpty()) {
				throw new SyntaxException(position, token, current.expectedTerminals());
			}
			sets.add(next);
			close(next, position + 1);
			current = next;
		}
		if (!current.isCompleted(start, 0)) {
			throw new SyntaxException(tokens.size(), null, current.expectedTerminals());
		}
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
				complete(set, item, position);
			} else if (item.next() instanceof Nonterminal nonterminal) {
				set.addWaiting(nonterminal, item);
				predict(set, nonterminal, position);
				// An empty match of the nonterminal may already have been completed at this position. The items
				// waiting at that time have been advanced, this item has to be advanced here.
				if (set.isCompleted(nonterminal, position)) {
					set.add(item.advance());
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

	private void complete(ItemSet set, Item item, int position) {
		set.addCompleted(item);
		Nonterminal nonterminal = item.rule().lhs();
		// For an empty match the origin is this set. Its list of waiting items does not change in this loop,
		// because items are only registered as waiting in close().
		for (Item waiting : sets.get(item.origin()).waiting(nonterminal)) {
			set.add(waiting.advance());
		}
	}
}
