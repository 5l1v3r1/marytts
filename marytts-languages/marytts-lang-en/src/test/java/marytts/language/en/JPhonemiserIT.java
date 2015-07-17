package marytts.language.en;

import marytts.modules.JPhonemiser;
import marytts.modules.ModuleRegistry;
import marytts.tests.modules.MaryModuleTestCase;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author ingmar
 */
public class JPhonemiserIT extends MaryModuleTestCase {

	public JPhonemiserIT() throws Exception {
		super(true); // need mary startup
		module = ModuleRegistry.getModule(JPhonemiser.class);
	}

	@Test
	public void testIsPosPunctuation() {
		JPhonemiser phonemiser = (JPhonemiser) module;
		Assert.assertTrue(phonemiser.isPosPunctuation("."));
		Assert.assertTrue(phonemiser.isPosPunctuation(","));
		Assert.assertTrue(phonemiser.isPosPunctuation(":"));
		Assert.assertFalse(phonemiser.isPosPunctuation("NN"));
	}
}
