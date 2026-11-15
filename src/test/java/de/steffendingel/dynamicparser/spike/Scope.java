package de.steffendingel.dynamicparser.spike;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.steffendingel.dynamicparser.Rule;

/**
 * A node of the scope tree that the example language keeps next to the parser.
 * <p>
 * Every rule added by a declaration belongs to a scope. At a place, the rules of its scope and of all enclosing scopes
 * apply. A scope that has been left continues to exist and can still grow; a deferred block refers to its scope by
 * reference and therefore sees the state of the scope at the time it is parsed.
 * <p>
 * The library has no notion of scopes, and a {@link de.steffendingel.dynamicparser.GrammarEditor} cannot list the rules
 * of a running parse. The language therefore records every rule it adds in a scope, in addition to adding it to the
 * running parse (double bookkeeping, see {@link ExampleLanguage}).
 */
final class Scope {

	private final Scope parent;

	private final String name;

	private final List<Rule> rules = new ArrayList<>();

	/**
	 * Rules that are shadowed by a rule with the same key in an inner scope. The plain scope chain has no shadowing:
	 * the rules of all enclosing scopes apply. This was needed for the return statement, see {@link ExampleLanguage}.
	 */
	private final Map<Object, Rule> keyedRules = new LinkedHashMap<>();

	Scope(Scope parent, String name) {
		this.parent = parent;
		this.name = name;
	}

	String name() {
		return name;
	}

	/** Adds a rule that applies in this scope and in all scopes below it. */
	void add(Rule rule) {
		rules.add(rule);
	}

	/**
	 * Adds a rule that applies in this scope and in the scopes below it, unless an inner scope has a rule with the same
	 * key.
	 */
	void addShadowing(Object key, Rule rule) {
		keyedRules.put(key, rule);
	}

	/** Returns whether this scope is the given scope or lies below it. */
	boolean isWithin(Scope other) {
		for (Scope scope = this; scope != null; scope = scope.parent) {
			if (scope == other) {
				return true;
			}
		}
		return false;
	}

	/** Returns the rules that apply in this scope: its own and those of all enclosing scopes. */
	List<Rule> visibleRules() {
		List<Rule> result = new ArrayList<>();
		Set<Object> shadowed = new HashSet<>();
		for (Scope scope = this; scope != null; scope = scope.parent) {
			result.addAll(scope.rules);
			for (Map.Entry<Object, Rule> entry : scope.keyedRules.entrySet()) {
				if (shadowed.add(entry.getKey())) {
					result.add(entry.getValue());
				}
			}
		}
		return result;
	}

	@Override
	public String toString() {
		return parent == null ? name : parent + " > " + name;
	}
}
