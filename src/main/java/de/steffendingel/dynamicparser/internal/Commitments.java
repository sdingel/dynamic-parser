package de.steffendingel.dynamicparser.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.Rule;
import de.steffendingel.dynamicparser.Symbol;
import de.steffendingel.dynamicparser.Terminal;

/**
 * Restricts the parser to what is consistent with the actions that have been called, when an action changes the
 * grammar.
 * <p>
 * Once an action has been called, its match is part of the result. Usually nothing has to be done about that: the
 * actions called are the ones that all readings have in common. A change of the grammar, however, can make
 * interpretations of the input possible that were stuck before, for example at a nonterminal without rules, and that do
 * not contain all the matches whose actions have been called. They must not come to life.
 * <p>
 * After a change of the grammar, the parser therefore continues only with the items that are valid for the forest of
 * the called matches: the items of rule applications whose matched symbols are exactly the last entries of that forest
 * and that are themselves waited for by valid items. This is the item set a deterministic bottom-up parser would be in
 * after shifting the tokens and reducing the matches of the forest. These items are determined here. For such items,
 * the forest before the rule application (its base) is known exactly, and with it the items that wait for its match and
 * what all readings leading to it have in common. This knowledge replaces the summaries of the {@link Analyzer}, which
 * also cover interpretations that are no longer possible.
 */
final class Commitments implements Chart.Parents {

	/** The applications of a rule that began in the same item set. */
	private record Family(Rule rule, int origin) {
	}

	/** An item in a certain item set. */
	private record Occurrence(Item item, int set) {
	}

	private record Stage(Cell forest, int set) {
	}

	private final Chart chart;

	private final Forests forests;

	private final Nonterminal start;

	/** The valid items with at least one matched symbol, by the forest they are valid for. */
	private final Map<Cell, Set<Item>> kernels = new HashMap<>();

	/** The nonterminals that can begin after a forest, in the item set in which they are predicted. */
	private final Map<Stage, Set<Nonterminal>> predicted = new HashMap<>();

	/** The forest before the rule application, for the rule applications known to be valid. */
	private final Map<Family, Cell> bases = new HashMap<>();

	/** The forest of the section matched by valid items. */
	private final Map<Occurrence, Cell> matched = new HashMap<>();

	Commitments(Chart chart, Forests forests, Nonterminal start) {
		this.chart = chart;
		this.forests = forests;
		this.start = start;
	}

	/**
	 * Returns the items to continue with after the action of the last match of the given forest has changed the
	 * grammar: the items that are valid for the forest and have matched at least one symbol, each with the forest of
	 * the section it has matched.
	 *
	 * @param forest the forest of all matches whose actions have been called and of the tokens before and between them;
	 *               its last entry is the match whose action changed the grammar
	 */
	Map<Item, Cell> restart(Cell forest) {
		Map<Item, Cell> items = new LinkedHashMap<>();
		for (Item item : kernel(forest)) {
			commit(item, base(forest, item.dot()));
			items.put(item, forests.top(forest, item.dot()));
		}
		Node last = (Node) forest.entry;
		if (forest.below == null && last.origin == 0 && last.rule.lhs() == start) {
			// The match is the complete input. A bottom-up parser would accept here; for the chart this is the
			// complete item of the rule.
			Item accepted = new Item(last.rule, last.children.size(), 0);
			commit(accepted, null);
			items.put(accepted, toForest(last));
		}
		return items;
	}

	private Cell toForest(Node node) {
		Cell forest = null;
		for (Entry child : node.children) {
			forest = forests.push(forest, child);
		}
		return forest;
	}

	private static Cell base(Cell forest, int count) {
		Cell base = forest;
		for (int i = 0; i < count; i++) {
			base = base.below;
		}
		return base;
	}

	private void commit(Item item, Cell base) {
		bases.put(new Family(item.rule(), item.origin()), base);
	}

	/**
	 * Returns the items that wait for the match of a complete item, if the rule application is known to be valid, and
	 * records that these items are valid as well.
	 */
	@Override
	public List<Item> of(Item completed) {
		Family family = new Family(completed.rule(), completed.origin());
		if (!bases.containsKey(family)) {
			return null;
		}
		Cell base = bases.get(family);
		List<Item> waiting = waiting(base, completed.origin(), completed.rule().lhs());
		for (Item item : waiting) {
			commit(item, base(base, item.dot()));
			matched.put(new Occurrence(item, completed.origin()), forests.top(base, item.dot()));
		}
		return waiting;
	}

	/**
	 * Returns what is known about the readings leading to the beginning of the rule application of the given item, or
	 * {@code null} if the rule application is not known to be valid.
	 */
	History context(Item item) {
		Family family = new Family(item.rule(), item.origin());
		return bases.containsKey(family) ? History.single(bases.get(family)) : null;
	}

	/**
	 * Returns the section matched by the given item in the given item set, or {@code null} if the item is not known to
	 * be valid there.
	 */
	Inner inner(Item item, int set) {
		Occurrence occurrence = new Occurrence(item, set);
		return matched.containsKey(occurrence) ? new Inner(matched.get(occurrence), true) : null;
	}

	/**
	 * Returns the items valid for the given forest that have matched at least one symbol.
	 */
	private Set<Item> kernel(Cell forest) {
		// The forest can be as long as the input, so the items are determined from the beginning of the forest
		// upwards instead of recursively.
		Deque<Cell> pending = new ArrayDeque<>();
		for (Cell cell = forest; cell != null && !kernels.containsKey(cell); cell = cell.below) {
			pending.push(cell);
		}
		while (!pending.isEmpty()) {
			Cell cell = pending.pop();
			Set<Item> kernel = new LinkedHashSet<>();
			switch (cell.entry) {
			case TokenEntry token -> {
				// The token was read from the last item set of its position.
				int set = chart.lastSet(token.position);
				ItemSet next = chart.set(chart.firstSet(token.position + 1));
				for (Item item : valid(cell.below, set)) {
					if (!item.isComplete() && item.next() instanceof Terminal && next.contains(item.advance())) {
						kernel.add(item.advance());
					}
				}
			}
			case Node node -> {
				for (Item item : waiting(cell.below, node.origin, node.rule.lhs())) {
					kernel.add(item.advance());
				}
			}
			case AmbiguousNode ambiguous -> throw new IllegalStateException("Ambiguous section in a called forest");
			}
			kernels.put(cell, kernel);
		}
		return forest == null ? Set.of() : kernels.get(forest);
	}

	/**
	 * Returns the items valid for the given forest that wait for the given nonterminal. They belong to the item set
	 * with the given index, in which the rules that can begin after the forest were predicted.
	 */
	private List<Item> waiting(Cell forest, int set, Nonterminal nonterminal) {
		Set<Item> kernel = kernel(forest);
		Set<Nonterminal> predicted = predicted(forest, set);
		List<Item> waiting = new ArrayList<>();
		for (Item item : chart.set(set).waiting(nonterminal)) {
			if (item.dot() == 0 ? item.origin() == set && predicted.contains(item.rule().lhs())
					: kernel.contains(item)) {
				waiting.add(item);
			}
		}
		return waiting;
	}

	/**
	 * Returns all items valid for the given forest: the ones that have matched at least one symbol and the ones of the
	 * rules that can begin after the forest.
	 */
	private List<Item> valid(Cell forest, int set) {
		List<Item> valid = new ArrayList<>(kernel(forest));
		for (Nonterminal nonterminal : predicted(forest, set)) {
			valid.addAll(chart.set(set).predictions(nonterminal));
		}
		return valid;
	}

	/**
	 * Returns the nonterminals that can begin after the given forest.
	 */
	private Set<Nonterminal> predicted(Cell forest, int set) {
		Stage stage = new Stage(forest, set);
		Set<Nonterminal> result = predicted.get(stage);
		if (result == null) {
			result = new HashSet<>();
			Deque<Nonterminal> pending = new ArrayDeque<>();
			if (forest == null) {
				pending.push(start);
			}
			for (Item item : kernel(forest)) {
				if (!item.isComplete() && item.next() instanceof Nonterminal nonterminal) {
					pending.push(nonterminal);
				}
			}
			while (!pending.isEmpty()) {
				Nonterminal nonterminal = pending.pop();
				if (result.add(nonterminal)) {
					for (Item item : chart.set(set).predictions(nonterminal)) {
						List<Symbol> rhs = item.rule().rhs();
						if (!rhs.isEmpty() && rhs.get(0) instanceof Nonterminal first) {
							pending.push(first);
						}
					}
				}
			}
			predicted.put(stage, result);
		}
		return result;
	}
}
