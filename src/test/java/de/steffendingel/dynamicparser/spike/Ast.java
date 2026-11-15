package de.steffendingel.dynamicparser.spike;

import java.util.List;
import java.util.stream.Collectors;

import de.steffendingel.dynamicparser.Nonterminal;

/**
 * The values computed by the actions of the {@link ExampleLanguage}: a small syntax tree.
 * <p>
 * Class and function declarations are mutable: their members and bodies are deferred blocks, which are parsed long
 * after the action that created the declaration has returned. The value of a deferred block is attached later by a
 * callback stored with the block in the queue.
 */
final class Ast {

	private Ast() {
	}

	/**
	 * A type. Every type has a nonterminal for the expressions of that type: an expression of type {@code T} may appear
	 * wherever the grammar expects the nonterminal of {@code T}.
	 */
	static final class Type {

		private final String name;

		private final Nonterminal expression;

		Type(String name) {
			this.name = name;
			this.expression = new Nonterminal("Expression<" + name + ">");
		}

		String name() {
			return name;
		}

		/** Returns the nonterminal for the expressions of this type. */
		Nonterminal expression() {
			return expression;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	record Param(String name, Type type) {

		@Override
		public String toString() {
			return name + ": " + type;
		}
	}

	/** An expression with its static type and its text in normalized form. */
	record Expression(Type type, String text) {

		@Override
		public String toString() {
			return text;
		}
	}

	static final class ClassDecl {

		final String name;

		final Type type;

		/** The methods; {@code null} until the block of the class has been parsed. */
		List<FunctionDecl> methods;

		ClassDecl(String name, Type type) {
			this.name = name;
			this.type = type;
		}
	}

	static final class FunctionDecl {

		final String name;

		final List<Param> params;

		final Type returnType;

		/** The statements of the body; {@code null} until the body has been parsed. */
		List<Object> body;

		FunctionDecl(String name, List<Param> params, Type returnType) {
			this.name = name;
			this.params = List.copyOf(params);
			this.returnType = returnType;
		}
	}

	record VarStatement(String name, Expression value) {
	}

	record ReturnStatement(Expression value) {
	}

	record ExpressionStatement(Expression value) {
	}

	record IfStatement(Expression condition, List<Object> body) {
	}

	/**
	 * Prints declarations in normalized source form with an indentation of four spaces. A block that has not been
	 * parsed is shown as {@code <not parsed>}.
	 */
	static String print(List<Object> declarations) {
		StringBuilder out = new StringBuilder();
		print(declarations, "", out);
		return out.toString();
	}

	private static void print(List<?> nodes, String indentation, StringBuilder out) {
		if (nodes == null) {
			out.append(indentation).append("<not parsed>\n");
			return;
		}
		for (Object node : nodes) {
			out.append(indentation);
			String inner = indentation + "    ";
			switch (node) {
			case ClassDecl c -> {
				out.append("class ").append(c.name).append('\n');
				print(c.methods, inner, out);
			}
			case FunctionDecl f -> {
				out.append("fun ").append(f.name)
						.append(f.params.stream().map(Param::toString).collect(Collectors.joining(", ", "(", ")")))
						.append(": ").append(f.returnType).append('\n');
				print(f.body, inner, out);
			}
			case VarStatement v -> out.append("var ").append(v.name()).append(" = ").append(v.value()).append('\n');
			case ReturnStatement r -> out.append("return ").append(r.value()).append('\n');
			case ExpressionStatement e -> out.append(e.value()).append('\n');
			case IfStatement i -> {
				out.append("if ").append(i.condition()).append('\n');
				print(i.body(), inner, out);
			}
			default -> throw new IllegalArgumentException("Unknown node " + node);
			}
		}
	}
}
