package de.steffendingel.dynamicparser.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.Rule;
import de.steffendingel.dynamicparser.Terminal;

/**
 * The Earley items of one position, in the order in which they were added, together with the indexes needed to complete
 * items and with the results of the {@link Analyzer} for this position.
 */
final class ItemSet {

	private final List<Item> items = new ArrayList<>();

	private final Set<Item> index = new HashSet<>();

	/** The items of this set that have the given nonterminal as their next symbol. */
	private final Map<Nonterminal, List<Item>> waiting = new HashMap<>();

	/** The nonterminals for which the rules have been predicted at this position. */
	private final Set<Nonterminal> predicted = new LinkedHashSet<>();

	/** The complete items of this set: nonterminal, then origin, then the rules of the complete items. */
	private final Map<Nonterminal, Map<Integer, List<Rule>>> completed = new LinkedHashMap<>();

	/**
	 * For the items that were advanced over a nonterminal: the positions at which the matches of that nonterminal
	 * begin.
	 */
	private final Map<Item, Set<Integer>> childOrigins = new HashMap<>();

	private final Map<Item, Inner> inners = new HashMap<>();

	private final Map<Nonterminal, Map<Integer, Entry>> entries = new HashMap<>();

	private final Map<Nonterminal, History> contexts = new HashMap<>();

	/**
	 * Adds an item unless it is already in this set.
	 *
	 * @return {@code true} if the item was added
	 */
	boolean add(Item item) {
		if (!index.add(item)) {
			return false;
		}
		items.add(item);
		return true;
	}

	boolean contains(Item item) {
		return index.contains(item);
	}

	boolean isEmpty() {
		return items.isEmpty();
	}

	int size() {
		return items.size();
	}

	Item get(int i) {
		return items.get(i);
	}

	/**
	 * Registers an item of this set whose next symbol is the given nonterminal.
	 */
	void addWaiting(Nonterminal nonterminal, Item item) {
		waiting.computeIfAbsent(nonterminal, key -> new ArrayList<>()).add(item);
	}

	/**
	 * Returns the items registered with {@link #addWaiting(Nonterminal, Item)} for the given nonterminal.
	 */
	List<Item> waiting(Nonterminal nonterminal) {
		return waiting.getOrDefault(nonterminal, List.of());
	}

	/**
	 * Marks the given nonterminal as predicted at this position.
	 *
	 * @return {@code true} if it was not predicted before
	 */
	boolean markPredicted(Nonterminal nonterminal) {
		return predicted.add(nonterminal);
	}

	/**
	 * Returns the nonterminals that can begin at this position, in the order in which they were predicted.
	 */
	Collection<Nonterminal> predicted() {
		return predicted;
	}

	/**
	 * Registers a complete item of this set.
	 */
	void addCompleted(Item item) {
		completed.computeIfAbsent(item.rule().lhs(), key -> new LinkedHashMap<>())
				.computeIfAbsent(item.origin(), key -> new ArrayList<>()).add(item.rule());
	}

	/**
	 * Returns whether a match of the given nonterminal that begins at the given position ends at the position of this
	 * set.
	 */
	boolean isCompleted(Nonterminal nonterminal, int origin) {
		return completed.getOrDefault(nonterminal, Map.of()).containsKey(origin);
	}

	/**
	 * Returns the nonterminals that have a match ending at the position of this set.
	 */
	Collection<Nonterminal> completedNonterminals() {
		return completed.keySet();
	}

	/**
	 * Returns the positions at which the matches of the given nonterminal that end at the position of this set begin.
	 */
	Collection<Integer> completedOrigins(Nonterminal nonterminal) {
		return completed.getOrDefault(nonterminal, Map.of()).keySet();
	}

	/**
	 * Returns the rules with which the given nonterminal matches the section from the given position to the position of
	 * this set.
	 */
	List<Rule> completedRules(Nonterminal nonterminal, int origin) {
		return completed.getOrDefault(nonterminal, Map.of()).getOrDefault(origin, List.of());
	}

	/**
	 * Registers that the given item of this set was advanced over a match of a nonterminal that begins at the given
	 * position.
	 */
	void addChildOrigin(Item item, int origin) {
		childOrigins.computeIfAbsent(item, key -> new LinkedHashSet<>()).add(origin);
	}

	/**
	 * Returns the positions registered with {@link #addChildOrigin(Item, int)} for the given item.
	 */
	Collection<Integer> childOrigins(Item item) {
		return childOrigins.getOrDefault(item, Set.of());
	}

	/**
	 * Returns the terminals that are the next symbol of an item of this set, in the order of the items.
	 */
	Set<Terminal> expectedTerminals() {
		Set<Terminal> expected = new LinkedHashSet<>();
		for (Item item : items) {
			if (!item.isComplete() && item.next() instanceof Terminal terminal) {
				expected.add(terminal);
			}
		}
		return expected;
	}

	// Results of the analyzer

	Inner inner(Item item) {
		return inners.get(item);
	}

	void putInner(Item item, Inner inner) {
		inners.put(item, inner);
	}

	/**
	 * Returns the entry for the section from the given position to the position of this set matched by the given
	 * nonterminal, or {@code null} if it has not been determined.
	 */
	Entry entry(Nonterminal nonterminal, int origin) {
		return entries.getOrDefault(nonterminal, Map.of()).get(origin);
	}

	void putEntry(Nonterminal nonterminal, int origin, Entry entry) {
		entries.computeIfAbsent(nonterminal, key -> new HashMap<>()).put(origin, entry);
	}

	/**
	 * Returns what is known about the readings in which the given nonterminal begins at this position, or {@code null}
	 * if it has not been determined.
	 */
	History context(Nonterminal nonterminal) {
		return contexts.get(nonterminal);
	}

	void putContext(Nonterminal nonterminal, History history) {
		contexts.put(nonterminal, history);
	}
}
