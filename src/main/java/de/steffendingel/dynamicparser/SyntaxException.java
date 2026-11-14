package de.steffendingel.dynamicparser;

import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thrown when the input does not match the grammar: the parser found a token, or the end of the input, where the
 * grammar in effect at that position does not allow it.
 * <p>
 * {@link #position()} is the position directly before the unexpected token or, if the input ended too early, the
 * position at the end of the input.
 */
public final class SyntaxException extends ParseException {

	@Serial
	private static final long serialVersionUID = 1L;

	// Tokens and terminals are not serializable in general, so these fields are lost on serialization.
	private final transient Object token;

	private final transient Set<Terminal> expected;

	/**
	 * Creates a syntax exception.
	 *
	 * @param position the position directly before the unexpected token or, at the end of the input, the position at
	 *                 the end of the input
	 * @param token    the unexpected token, or {@code null} if the input ended too early
	 * @param expected the terminals that would have been allowed at this position
	 * @throws NullPointerException     if {@code expected} or one of its elements is {@code null}
	 * @throws IllegalArgumentException if {@code position} is negative
	 */
	public SyntaxException(int position, Object token, Collection<Terminal> expected) {
		super(message(position, token, expected), position);
		this.token = token;
		Set<Terminal> set = new LinkedHashSet<>();
		for (Terminal terminal : expected) {
			set.add(Objects.requireNonNull(terminal, "terminal"));
		}
		this.expected = Collections.unmodifiableSet(set);
	}

	private static String message(int position, Object token, Collection<Terminal> expected) {
		Objects.requireNonNull(expected, "expected");
		String found = token == null ? "Unexpected end of input" : "Unexpected token " + token;
		String allowed = expected.isEmpty() ? "nothing is allowed here"
				: expected.stream().map(String::valueOf).distinct().collect(Collectors.joining(", ", "expected: ", ""));
		return found + " at position " + position + "; " + allowed;
	}

	/**
	 * Returns the unexpected token.
	 *
	 * @return the token, or an empty optional if the input ended too early
	 */
	public Optional<Object> token() {
		return Optional.ofNullable(token);
	}

	/**
	 * Returns the terminals that would have been allowed at the position of the error.
	 *
	 * @return an unmodifiable set of terminals; empty if no further token is allowed at this position
	 */
	public Set<Terminal> expected() {
		return expected;
	}
}
