package de.steffendingel.dynamicparser.internal;

/**
 * The token between the positions {@code position} and {@code position + 1}.
 */
final class TokenEntry implements Entry {

	final int position;

	TokenEntry(int position) {
		this.position = position;
	}

	@Override
	public String toString() {
		return "token " + position;
	}
}
