package de.steffendingel.dynamicparser.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.steffendingel.dynamicparser.Grammar;
import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.Rule;

/**
 * The grammar of a parse run: the rules of the initial grammar with the changes made by actions so far.
 */
final class RunGrammar {

	/** The rules by their left-hand side, in the order in which they were added. */
	private final Map<Nonterminal, List<Rule>> rules = new HashMap<>();

	private final Set<Rule> all = new HashSet<>();

	private int changes;

	RunGrammar(Grammar initial) {
		for (Rule rule : initial.rules()) {
			add(rule);
		}
		changes = 0;
	}

	boolean contains(Rule rule) {
		return all.contains(rule);
	}

	List<Rule> rules(Nonterminal lhs) {
		return rules.getOrDefault(lhs, List.of());
	}

	boolean add(Rule rule) {
		if (!all.add(rule)) {
			return false;
		}
		rules.computeIfAbsent(rule.lhs(), key -> new ArrayList<>()).add(rule);
		changes++;
		return true;
	}

	boolean remove(Rule rule) {
		if (!all.remove(rule)) {
			return false;
		}
		rules.get(rule.lhs()).remove(rule);
		changes++;
		return true;
	}

	/**
	 * Returns the number of changes made since the parse run began.
	 */
	int changes() {
		return changes;
	}
}
