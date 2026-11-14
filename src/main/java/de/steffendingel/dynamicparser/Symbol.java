package de.steffendingel.dynamicparser;

/**
 * A symbol of a grammar: either a {@link Nonterminal} or a {@link Terminal}.
 * <p>
 * Symbols are identified by object identity: two symbols with the same name are different symbols. The name is only
 * used in messages.
 */
public sealed interface Symbol permits Nonterminal, Terminal {

	/**
	 * Returns the name of this symbol. The name is used in messages and does not identify the symbol.
	 *
	 * @return the name, never {@code null}
	 */
	String name();
}
