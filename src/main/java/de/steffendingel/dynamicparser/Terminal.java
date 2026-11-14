package de.steffendingel.dynamicparser;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A terminal symbol: a symbol that stands for a single token of the input.
 * <p>
 * Tokens are arbitrary objects supplied by the caller of the parser. A terminal decides for each token whether it
 * matches. One terminal can match a single specific token (for example the word {@code while}) or a whole class of
 * tokens (for example any word); several terminals can match the same token.
 * <p>
 * A terminal is identified by object identity. Creating two terminals with the same name and predicate yields two
 * different symbols.
 */
public final class Terminal implements Symbol {

	private final String name;

	private final Predicate<Object> predicate;

	/**
	 * Creates a new terminal that is different from all existing ones.
	 *
	 * @param name      the name used in messages, for example in the list of expected terminals of a syntax error
	 * @param predicate decides whether a token matches this terminal; it is never called with {@code null}, should
	 *                  always return the same result for the same token and should have no side effects
	 * @throws NullPointerException if an argument is {@code null}
	 */
	public Terminal(String name, Predicate<Object> predicate) {
		this.name = Objects.requireNonNull(name, "name");
		this.predicate = Objects.requireNonNull(predicate, "predicate");
	}

	@Override
	public String name() {
		return name;
	}

	/**
	 * Returns whether the given token matches this terminal.
	 *
	 * @param token the token to test
	 * @return {@code true} if the token matches
	 * @throws NullPointerException if {@code token} is {@code null}
	 */
	public boolean matches(Object token) {
		return predicate.test(Objects.requireNonNull(token, "token"));
	}

	/**
	 * Returns the name of this terminal.
	 *
	 * @return the name
	 */
	@Override
	public String toString() {
		return name;
	}
}
