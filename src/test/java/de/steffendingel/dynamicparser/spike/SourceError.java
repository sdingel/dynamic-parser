package de.steffendingel.dynamicparser.spike;

import de.steffendingel.dynamicparser.ParseException;

/**
 * An error in the source text, mapped from the token position of a parse run to a line and column.
 *
 * @param line   the line number, starting at 1
 * @param column the column, starting at 1
 * @param scope  the scope in which the failing block was parsed, for diagnostics
 * @param cause  the exception of the parse run that failed
 */
record SourceError(int line, int column, String scope, ParseException cause) {

	@Override
	public String toString() {
		return line + ":" + column + " in " + scope + ": " + cause.getMessage();
	}
}
