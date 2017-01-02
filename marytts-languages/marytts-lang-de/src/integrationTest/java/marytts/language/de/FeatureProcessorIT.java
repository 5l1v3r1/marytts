/**
 * Copyright 2011 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package marytts.language.de;

import java.util.Locale;

import marytts.datatypes.MaryXML;
import marytts.modeling.features.ByteValuedFeatureProcessor;
import marytts.modeling.features.FeatureProcessorManager;
import marytts.modeling.features.FeatureRegistry;
import marytts.server.Mary;
import marytts.util.dom.MaryDomUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.testng.Assert;
import org.testng.annotations.*;

/**
 * @author marc
 *
 */
public class FeatureProcessorIT {

	private static FeatureProcessorManager fpm;

	@BeforeClass
	public static void setupClass() throws Exception {
		if (Mary.currentState() == Mary.STATE_OFF) {
			Mary.startup();
		}
		fpm = FeatureRegistry.determineBestFeatureProcessorManager(Locale.GERMAN);
	}

	// /// Utilities
	private Element createRareWordTarget() {
		return createWordTarget("Sprachsynthese");
	}

	private Element createFrequentWordTarget() {
		return createWordTarget("der");
	}

	private Element createWordTarget(String word) {
		Document doc = MaryXML.newDocument();
		Element s = MaryXML.appendChildElement(doc.getDocumentElement(), MaryXML.SENTENCE);
		Element t = MaryXML.appendChildElement(s, MaryXML.TOKEN);
		MaryDomUtils.setTokenText(t, word);
		Element syl = MaryXML.appendChildElement(t, MaryXML.SYLLABLE);
		Element ph = MaryXML.appendChildElement(syl, MaryXML.PHONE);
        return ph;
	}

	// /// Tests

	@Test
	public void testWordFrequency() {
		// Setup SUT
		ByteValuedFeatureProcessor wf = (ByteValuedFeatureProcessor) fpm.getFeatureProcessor("word_frequency");
		Element t1 = createRareWordTarget();
		Element t2 = createFrequentWordTarget();
		// Exercise SUT
		byte f1 = wf.process(t1);
		byte f2 = wf.process(t2);
		// verify
		Assert.assertEquals(0, f1);
		Assert.assertEquals(9, f2);
	}

}
