package de.steffendingel.dynamicparser.spike;

import java.io.Serial;

/**
 * Thrown by the {@link StructureReader} when the indentation of a line does not fit the lines before it.
 */
final class IndentationException extends Exception {

	@Serial
	private static final long serialVersionUID = 1L;

	private final int line;

	IndentationException(int line, String message) {
		super("Line " + line + ": " + message);
		this.line = line;
	}

	/** Returns the number of the offending line, starting at 1. */
	int line() {
		return line;
	}
}
