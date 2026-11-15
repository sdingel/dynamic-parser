package de.steffendingel.dynamicparser.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.Rule;
import de.steffendingel.dynamicparser.Symbol;
import de.steffendingel.dynamicparser.Terminal;

/**
 * Determines which matches are confirmed, that is, part of every reading of the input read so far. The definitions are
 * in the section "Confirmed matches" of {@code docs/architecture.md}.
 * <p>
 * A reading is a complete interpretation of the input read so far that could take the next token or accept the end of
 * the input. In the chart, a reading ends at an item whose next symbol is a terminal, or at a complete match of the
 * start symbol. The readings are not enumerated. Instead, the analyzer computes for every position, once its item set
 * is complete, summaries from which the common beginning of all readings follows:
 * <ul>
 * <li>for every item, the forest of the section it has matched ({@link Inner}),</li>
 * <li>for every nonterminal with a match ending at the position, and every position at which such a match begins, the
 * {@link Entry} that stands for it: a {@link Node} if the section can be matched in one way only, otherwise an
 * {@link AmbiguousNode},</li>
 * <li>for every nonterminal that can begin at the position, what is known about the readings in which it begins there
 * (its context, a {@link History}).</li>
 * </ul>
 * This is sufficient because readings are composed freely: every way to reach an item can be combined with every way to
 * continue from it. The summaries of a position therefore never change once they are computed.
 */
final class Analyzer {

	/**
	 * A nonterminal and the index of the item set in which a match of it begins. The match ends in the analyzed item
	 * set.
	 */
	private record Section(Nonterminal symbol, int origin) {
	}

	private final Chart chart;

	private final Forests forests;

	private final Nonterminal start;

	private final Commitments commitments;

	Analyzer(Chart chart, Forests forests, Nonterminal start, Commitments commitments) {
		this.chart = chart;
		this.forests = forests;
		this.start = start;
		this.commitments = commitments;
	}

	/**
	 * Computes the summaries of the item set with the given index. The item set must be complete, and all item sets
	 * before it must have been analyzed.
	 */
	void analyze(int index) {
		ItemSet set = chart.set(index);
		computeEntries(set, index);
		for (int i = 0; i < set.size(); i++) {
			inner(set, index, set.get(i));
		}
		computeContexts(set, index);
	}

	/**
	 * Returns what is known about the readings that end in the item set with the given index, or {@code null} if there
	 * is no reading. The confirmed matches are the ones in the forest that all readings have in common, see
	 * {@link History#common()}.
	 *
	 * @param endOfInput whether the input ends here; the only readings are then the ones in which the input read so far
	 *                   is a complete match of the start symbol
	 */
	History common(int index, boolean endOfInput) {
		ItemSet set = chart.set(index);
		History common = null;
		if (!endOfInput) {
			for (int i = 0; i < set.size(); i++) {
				Item item = set.get(i);
				if (!item.isComplete() && item.next() instanceof Terminal) {
					common = merge(common, history(set, index, item));
				}
			}
		}
		if (set.isCompleted(start, 0)) {
			common = merge(common, History.single(forests.push(null, set.entry(start, 0))));
		}
		if (common == null && !endOfInput) {
			// The input cannot continue with the current rules. It can only continue if an action adds rules for a
			// nonterminal that has none, so the interpretations waiting for such a nonterminal are the readings.
			for (int i = 0; i < set.size(); i++) {
				Item item = set.get(i);
				if (!item.isComplete() && item.next() instanceof Nonterminal nonterminal
						&& set.predictions(nonterminal).isEmpty()) {
					common = merge(common, history(set, index, item));
				}
			}
		}
		return common;
	}

	// Entries of the matches that end at the position

	/**
	 * Determines the entries of all sections that end at the position.
	 * <p>
	 * The entry of a section depends on the entries of the sections within it that end at the same position. The
	 * entries are therefore determined depth-first, with an explicit stack, because such chains can be as long as the
	 * input (for example for right-recursive rules). A section that depends on itself contains itself and can therefore
	 * be matched in infinitely many ways; it is ambiguous.
	 */
	private void computeEntries(ItemSet set, int index) {
		record Pending(Section section, List<Section> dependencies, int[] next) {
		}
		Set<Section> inProgress = new HashSet<>();
		Set<Section> cyclic = new HashSet<>();
		Deque<Pending> stack = new ArrayDeque<>();
		for (Nonterminal symbol : set.completedNonterminals()) {
			for (int origin : set.completedOrigins(symbol)) {
				Section first = new Section(symbol, origin);
				if (set.entry(symbol, origin) != null) {
					continue;
				}
				inProgress.add(first);
				stack.push(new Pending(first, dependencies(set, index, first), new int[1]));
				while (!stack.isEmpty()) {
					Pending pending = stack.peek();
					Section required = null;
					while (required == null && pending.next()[0] < pending.dependencies().size()) {
						Section dependency = pending.dependencies().get(pending.next()[0]++);
						if (set.entry(dependency.symbol(), dependency.origin()) != null) {
							continue;
						}
						if (inProgress.contains(dependency)) {
							cyclic.add(dependency);
						} else {
							required = dependency;
						}
					}
					if (required != null) {
						inProgress.add(required);
						stack.push(new Pending(required, dependencies(set, index, required), new int[1]));
					} else {
						stack.pop();
						Section section = pending.section();
						Entry entry = createEntry(set, index, section, cyclic.contains(section));
						set.putEntry(section.symbol(), section.origin(), entry);
						inProgress.remove(section);
					}
				}
			}
		}
	}

	/**
	 * Returns the sections ending at the position whose entries are needed to determine the entry of the given section.
	 */
	private List<Section> dependencies(ItemSet set, int index, Section section) {
		List<Section> dependencies = new ArrayList<>();
		Deque<Item> items = new ArrayDeque<>();
		Set<Item> visited = new HashSet<>();
		for (Rule rule : set.completedRules(section.symbol(), section.origin())) {
			items.push(new Item(rule, rule.rhs().size(), section.origin()));
		}
		while (!items.isEmpty()) {
			Item item = items.pop();
			if (item.dot() == 0 || !visited.add(item)
					|| !(item.rule().rhs().get(item.dot() - 1) instanceof Nonterminal child)) {
				continue;
			}
			for (int childOrigin : set.childOrigins(item)) {
				dependencies.add(new Section(child, childOrigin));
				if (childOrigin == index) {
					// The match of the child is empty, so the item before it belongs to this position as well.
					items.push(previous(item));
				}
			}
		}
		return dependencies;
	}

	private Entry createEntry(ItemSet set, int index, Section section, boolean cyclic) {
		Nonterminal symbol = section.symbol();
		int origin = section.origin();
		List<Rule> rules = set.completedRules(symbol, origin);
		int startPosition = chart.position(origin);
		int endPosition = chart.position(index);
		if (cyclic) {
			return forests.ambiguous(symbol, startPosition, endPosition);
		}
		Cell common = null;
		for (int i = 0; i < rules.size(); i++) {
			Rule rule = rules.get(i);
			Inner inner = inner(set, index, new Item(rule, rule.rhs().size(), origin));
			Cell alternative = inner.forest();
			if (inner.exact()) {
				Node node = forests.node(rule, startPosition, endPosition, origin, Forests.entries(inner.forest()));
				if (rules.size() == 1) {
					return node;
				}
				alternative = forests.push(null, node);
			}
			common = i == 0 ? alternative : forests.commonPrefix(common, alternative);
		}
		AmbiguousNode ambiguous = forests.ambiguous(symbol, startPosition, endPosition);
		ambiguous.common = common;
		return ambiguous;
	}

	/**
	 * Returns the entry of a section that ends at the position. While the entry of a section is being determined, it
	 * can be requested by a section within it; the section is then ambiguous.
	 */
	private Entry entry(ItemSet set, int index, Nonterminal symbol, int origin) {
		Entry entry = set.entry(symbol, origin);
		return entry != null ? entry : forests.ambiguous(symbol, chart.position(origin), chart.position(index));
	}

	// Sections matched by the items of the position

	private Inner inner(ItemSet set, int index, Item item) {
		Inner inner = set.inner(item);
		if (inner == null) {
			inner = computeInner(set, index, item);
			set.putInner(item, inner);
		}
		return inner;
	}

	private Inner computeInner(ItemSet set, int index, Item item) {
		if (item.dot() == 0) {
			return Inner.EMPTY;
		}
		Item previous = previous(item);
		// The ways in which the item has matched its section: the forest of each way if it is unique, otherwise the
		// forest that the ways covered by it begin with.
		List<Cell> ways = new ArrayList<>();
		boolean exact = true;
		Symbol last = item.rule().rhs().get(item.dot() - 1);
		if (set.hasInherited(item)) {
			// The set was created with this item after a change of the grammar; what it has matched is known.
			ways.add(set.inherited(item));
		} else if (last instanceof Terminal) {
			// The item was advanced over the last token, from the item set before this one.
			Inner before = inner(index - 1, previous);
			return before.exact()
					? new Inner(forests.push(before.forest(), forests.token(chart.position(index) - 1)), true)
					: before;
		}
		if (last instanceof Nonterminal child) {
			// The item was advanced over matches of the nonterminal, one for each item set in which they begin.
			for (int childOrigin : set.childOrigins(item)) {
				Inner before = childOrigin == index ? inner(set, index, previous) : inner(childOrigin, previous);
				Cell way = before.forest();
				if (before.exact()) {
					way = forests.push(way, entry(set, index, child, childOrigin));
					if (ways.contains(way)) {
						// The same way has been found twice, which can happen after a change of the grammar.
						continue;
					}
				}
				exact &= before.exact();
				ways.add(way);
			}
		}
		// If there are several ways, the section is ambiguous.
		Cell common = ways.get(0);
		for (Cell way : ways) {
			common = forests.commonPrefix(common, way);
		}
		return new Inner(common, exact && ways.size() == 1);
	}

	/**
	 * Returns the section matched by an item of an earlier item set.
	 */
	private Inner inner(int index, Item item) {
		Inner known = commitments.inner(item, index);
		return known != null ? known : chart.set(index).inner(item);
	}

	private static Item previous(Item item) {
		return new Item(item.rule(), item.dot() - 1, item.origin());
	}

	// Contexts of the nonterminals that begin at the position

	/**
	 * Determines the contexts of the nonterminals that can begin at the position: what the readings of all items
	 * waiting for the nonterminal have in common. Items waiting at this position can themselves begin here, as with
	 * recursive rules, so the contexts depend on each other. They are determined by repeating the computation until
	 * nothing changes.
	 */
	private void computeContexts(ItemSet set, int index) {
		boolean changed = true;
		for (int round = 0; changed; round++) {
			if (round > 1000) {
				throw new IllegalStateException("The contexts at index " + index + " do not become stable");
			}
			changed = false;
			for (Nonterminal nonterminal : set.predicted()) {
				History context = index == 0 && nonterminal == start ? History.ROOT : null;
				for (Item waiting : set.waiting(nonterminal)) {
					context = merge(context, history(set, index, waiting));
				}
				if (context != null && !context.equals(set.context(nonterminal))) {
					set.putContext(nonterminal, context);
					changed = true;
				}
			}
		}
	}

	/**
	 * Returns what is known about the readings that lead to the given item of the position, or {@code null} if this is
	 * not known yet.
	 */
	private History history(ItemSet set, int index, Item item) {
		History context = commitments.context(item);
		if (context == null) {
			context = chart.set(item.origin()).context(item.rule().lhs());
		}
		if (context == null) {
			return null;
		}
		// Every reading in which the nonterminal begins continues with what the item has matched.
		Inner inner = set.inner(item);
		List<Cell> readings = new ArrayList<>();
		List<Cell> groups = new ArrayList<>();
		for (Cell reading : context.chain()) {
			(inner.exact() ? readings : groups).add(forests.pushAll(reading, inner.forest()));
		}
		if (context.hasRest()) {
			// The remaining readings differ before the item begins.
			groups.add(context.rest());
		}
		return history(readings, groups);
	}

	private History merge(History first, History second) {
		if (first == null || second == null) {
			return first == null ? second : first;
		}
		List<Cell> readings = new ArrayList<>(first.chain());
		for (Cell reading : second.chain()) {
			if (!readings.contains(reading)) {
				readings.add(reading);
			}
		}
		List<Cell> groups = new ArrayList<>();
		if (first.hasRest()) {
			groups.add(first.rest());
		}
		if (second.hasRest()) {
			groups.add(second.rest());
		}
		return history(readings, groups);
	}

	/**
	 * Creates the history of a set of readings.
	 *
	 * @param readings the forests of individual readings, without duplicates
	 * @param groups   for groups of at least two further readings, the forest that the sequences of the readings of the
	 *                 group begin with; no reading of a group is that forest itself
	 */
	private History history(List<Cell> readings, List<Cell> groups) {
		List<Cell> chain = new ArrayList<>();
		List<Cell> remaining = new ArrayList<>(readings);
		while (!remaining.isEmpty() || !groups.isEmpty()) {
			Cell common = null;
			boolean first = true;
			for (Cell reading : remaining) {
				common = first ? reading : forests.commonPrefix(common, reading);
				first = false;
			}
			for (Cell group : groups) {
				common = first ? group : forests.commonPrefix(common, group);
				first = false;
			}
			// Forests are unique objects, so the reading whose sequence all others begin with is found by identity.
			if (chain.size() == History.MAX_CHAIN || !remaining.remove(common)) {
				return new History(Collections.unmodifiableList(chain), true, common);
			}
			chain.add(common);
		}
		return new History(Collections.unmodifiableList(chain), false, null);
	}
}
