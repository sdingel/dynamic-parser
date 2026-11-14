package de.steffendingel.dynamicparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests that the module exports the API package only.
 */
class ModuleTest {

	private final Module module = Parser.class.getModule();

	@Test
	void isANamedModule() {
		assertEquals("de.steffendingel.dynamicparser", module.getName());
	}

	@Test
	void exportsTheApiPackage() {
		assertTrue(module.isExported("de.steffendingel.dynamicparser"));
	}

	@Test
	void doesNotExportTheImplementationPackage() {
		assertTrue(module.getPackages().contains("de.steffendingel.dynamicparser.internal"));
		assertFalse(module.isExported("de.steffendingel.dynamicparser.internal"));
	}
}
