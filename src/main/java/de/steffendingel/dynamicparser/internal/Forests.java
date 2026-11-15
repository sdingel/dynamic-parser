package de.steffendingel.dynamicparser.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.steffendingel.dynamicparser.Nonterminal;
import de.steffendingel.dynamicparser.Rule;

/**
 * Creates the entries and cells of the forests of one parse run and compares forests.
 * <p>
 * All objects are unique for their content: asking twice for the same token, the same match or the same forest returns
 * the same object. Equal parts of forests are therefore recognized by identity, without looking into them.
 */
final class Forests {

	private record NodeKey(Rule rule, int start, int end, int origin, List<Entry> children) {
	}

	private record AmbiguousKey(Nonterminal symbol, int start, int end) {
	}

	private record CellKey(Cell below, Entry entry) {
	}

	private final List<TokenEntry> tokens = new ArrayList<>();

	private final Map<NodeKey, Node> nodes = new HashMap<>();

	private final Map<AmbiguousKey, AmbiguousNode> ambiguousNodes = new HashMap<>();

	private final Map<CellKey, Cell> cells = new HashMap<>();

	TokenEntry token(int position) {
		while (tokens.size() <= position) {
			tokens.add(new TokenEntry(tokens.size()));
		}
		return tokens.get(position);
	}

	Node node(Rule rule, int start, int end, int origin, List<Entry> children) {
		return nodes.computeIfAbsent(new NodeKey(rule, start, end, origin, children),
				key -> new Node(rule, start, end, origin, children));
	}

	/**
	 * Returns the last {@code count} entries of the given forest as a forest of their own.
	 */
	Cell top(Cell forest, int count) {
		Cell base = forest;
		for (int i = 0; i < count; i++) {
			base = base.below;
		}
		Cell result = null;
		for (Entry entry : entriesAbove(forest, base)) {
			result = push(result, entry);
		}
		return result;
	}

	AmbiguousNode ambiguous(Nonterminal symbol, int start, int end) {
		return ambiguousNodes.computeIfAbsent(new AmbiguousKey(symbol, start, end),
				key -> new AmbiguousNode(symbol, start, end));
	}

	/**
	 * Returns the forest that consists of the given forest followed by the given entry.
	 */
	Cell push(Cell below, Entry entry) {
		return cells.computeIfAbsent(new CellKey(below, entry), key -> new Cell(below, entry));
	}

	/**
	 * Returns the forest that consists of the first forest followed by the entries of the second one.
	 */
	Cell pushAll(Cell below, Cell forest) {
		Cell result = below;
		for (Entry entry : entries(forest)) {
			result = push(result, entry);
		}
		return result;
	}

	/**
	 * Returns the entries of a forest from left to right.
	 */
	static List<Entry> entries(Cell forest) {
		return entriesAbove(forest, null);
	}

	private static List<Entry> entriesAbove(Cell forest, Cell base) {
		List<Entry> entries = new ArrayList<>();
		for (Cell cell = forest; cell != base; cell = cell.below) {
			entries.add(cell.entry);
		}
		Collections.reverse(entries);
		return entries;
	}

	/**
	 * Returns the forest that two forests have in common.
	 * <p>
	 * A forest stands for the sequence of the tokens it covers and the matches it contains, each match after everything
	 * it consists of. This is the order in which a bottom-up parser shifts tokens and reduces matches. The result is
	 * the forest of the longest beginning that the sequences of the two forests share. Example: the forests
	 * {@code Sum(a + b)} and {@code Sum(a) + Product(b)} have the forest {@code Sum(a) + Product(b)} in common, because
	 * the first one contains the same matches and one more.
	 */
	Cell commonPrefix(Cell first, Cell second) {
		if (first == second) {
			return first;
		}
		// The forests share their cells up to the point at which they differ for the first time.
		Cell a = first;
		Cell b = second;
		while (depth(a) > depth(b)) {
			a = a.below;
		}
		while (depth(b) > depth(a)) {
			b = b.below;
		}
		while (a != b) {
			a = a.below;
			b = b.below;
		}
		Cell result = a;
		Walk left = new Walk(entriesAbove(first, result));
		Walk right = new Walk(entriesAbove(second, result));
		while (!left.atEnd() && !right.atEnd()) {
			if (left.atEntry() && right.atEntry()) {
				Entry leftEntry = left.entry();
				Entry rightEntry = right.entry();
				if (leftEntry == rightEntry) {
					// Identical entries stand for identical sequences.
					result = push(result, leftEntry);
					left.skip();
					right.skip();
				} else if (!(leftEntry instanceof TokenEntry)) {
					left.enter();
				} else if (!(rightEntry instanceof TokenEntry)) {
					right.enter();
				} else {
					break;
				}
			} else if (left.atEntry()) {
				// The right sequence continues with the completion of a match.
				if (left.entry() instanceof TokenEntry) {
					break;
				}
				left.enter();
			} else if (right.atEntry()) {
				if (right.entry() instanceof TokenEntry) {
					break;
				}
				right.enter();
			} else if (left.completed() == right.completed()) {
				// Both sequences complete the same match. Its parts are the last entries of the result.
				Entry completed = left.completed();
				for (int i = size(completed); i > 0; i--) {
					result = result.below;
				}
				result = push(result, completed);
				left.leave();
				right.leave();
			} else {
				break;
			}
		}
		return result;
	}

	private static int depth(Cell cell) {
		return cell == null ? 0 : cell.depth;
	}

	/**
	 * Returns the entries that the sequence of an entry consists of before the entry itself is completed.
	 */
	private static List<Entry> parts(Entry entry) {
		return switch (entry) {
		case TokenEntry token -> List.of();
		case Node node -> node.children;
		case AmbiguousNode ambiguous -> entries(ambiguous.common);
		};
	}

	private static int size(Entry entry) {
		return switch (entry) {
		case TokenEntry token -> 0;
		case Node node -> node.children.size();
		case AmbiguousNode ambiguous -> depth(ambiguous.common);
		};
	}

	/**
	 * Walks through the sequence of a forest. The walk is either in front of an entry, which it can skip or enter, or
	 * at the completion of the entry it has entered, or at the end.
	 */
	private static final class Walk {

		private static final class Level {

			final Entry owner;

			final List<Entry> entries;

			int next;

			Level(Entry owner, List<Entry> entries) {
				this.owner = owner;
				this.entries = entries;
			}
		}

		private final Deque<Level> levels = new ArrayDeque<>();

		Walk(List<Entry> entries) {
			levels.push(new Level(null, entries));
		}

		boolean atEntry() {
			Level level = levels.peek();
			return level.next < level.entries.size();
		}

		boolean atEnd() {
			return levels.size() == 1 && !atEntry();
		}

		Entry entry() {
			Level level = levels.peek();
			return level.entries.get(level.next);
		}

		void skip() {
			levels.peek().next++;
		}

		void enter() {
			Entry entry = entry();
			skip();
			levels.push(new Level(entry, parts(entry)));
		}

		Entry completed() {
			return levels.peek().owner;
		}

		void leave() {
			levels.pop();
		}
	}
}
