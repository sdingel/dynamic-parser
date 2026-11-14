package de.steffendingel.dynamicparser;

import java.util.Objects;

/**
 * A nonterminal symbol: a symbol that is defined by the {@link Rule rules} having it as their left-hand side.
 * <p>
 * A nonterminal is identified by object identity. Creating two nonterminals with the same name yields two different
 * symbols.
 */
public final class Nonterminal implements Symbol {

	private final String name;

	/**
	 * Creates a new nonterminal that is different from all existing ones.
	 *
	 * @param name the name used in messages
	 * @throws NullPointerException if {@code name} is {@code null}
	 */
	public Nonterminal(String name) {
		this.name = Objects.requireNonNull(name, "name");
	}

	@Override
	public String name() {
		return name;
	}

	/**
	 * Returns the name of this nonterminal.
	 *
	 * @return the name
	 */
	@Override
	public String toString() {
		return name;
	}
}
