/**
 * Copyright 2000-2006 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */
package marytts.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import marytts.datatypes.MaryData;
import marytts.datatypes.MaryDataType;
import marytts.modules.ModuleRegistry;
import marytts.modules.synthesis.Voice;
import marytts.server.Mary;
import marytts.server.MaryProperties;
import marytts.server.Request;

import junit.framework.Assert;
import junit.framework.TestCase;

public class MaryTest extends TestCase {
    public void setUp() throws Exception {
        if (Mary.currentState() == Mary.STATE_OFF)
            Mary.startup();
    }

    public void testMaryRunning() {
        Assert.assertTrue(Mary.currentState() == Mary.STATE_RUNNING);
    }

    public void testDefaultVoicesAvailable() throws Exception {
        Assert.assertTrue(Voice.getDefaultVoice(Locale.ENGLISH) != null);
    }

    public void testModulesRequired1() {
        try {
            ModuleRegistry.modulesRequiredForProcessing(null, MaryDataType.AUDIO, Locale.ENGLISH);
        } catch (NullPointerException e) {
            return;
        }
        fail("should have thrown NullPointerException");
    }

    public void testModulesRequired2() {
        try {
            ModuleRegistry.modulesRequiredForProcessing(MaryDataType.TEXT, null, Locale.ENGLISH);
        } catch (NullPointerException e) {
            return;
        }
        fail("should have thrown NullPointerException");
    }

    public void testModulesRequired3() {
        try {
            ModuleRegistry.modulesRequiredForProcessing(MaryDataType.TEXT, MaryDataType.AUDIO, null);
        } catch (NullPointerException e) {
            return;
        }
        fail("should have thrown NullPointerException");
    }

    public void testTextToSpeechPossibleEnglish() {
        List modules =
            ModuleRegistry.modulesRequiredForProcessing(
                MaryDataType.TEXT,
                MaryDataType.AUDIO,
                Locale.US);
        Assert.assertTrue(modules != null && !modules.isEmpty());
    }

    public void testValidMaryXML1() throws Exception {
        convertToAndValidate("test1.maryxml", MaryDataType.get("RAWMARYXML"), MaryDataType.get("TOKENS_EN"), Locale.ENGLISH);
    }

    public void testValidMaryXML2() throws Exception {
        convertToAndValidate("test1.maryxml", MaryDataType.get("RAWMARYXML"), MaryDataType.get("INTONATION_EN"), Locale.US);
    }

    public void testValidMaryXML3() throws Exception {
        convertToAndValidate("test1.maryxml", MaryDataType.get("RAWMARYXML"), MaryDataType.get("ACOUSTPARAMS"), Locale.US);
    }

    public void testValidMaryXML4() throws Exception {
        convertToAndValidate("test1.ssml", MaryDataType.get("SSML"), MaryDataType.get("TOKENS_EN"), Locale.ENGLISH);
    }

    public void testValidMaryXML5() throws Exception {
        convertToAndValidate("test1.ssml", MaryDataType.get("SSML"), MaryDataType.get("INTONATION_EN"), Locale.US);
    }

    public void testValidMaryXML6() throws Exception {
        convertToAndValidate("test1.ssml", MaryDataType.get("SSML"), MaryDataType.get("ACOUSTPARAMS"), Locale.US);
    }

    protected void convertToAndValidate(String resourceName, MaryDataType inputType, MaryDataType targetType, Locale locale)
    throws Exception {
        Assert.assertTrue(MaryProperties.getBoolean("maryxml.validate.input"));
        InputStream maryxml = this.getClass().getResourceAsStream(resourceName);
        Assert.assertTrue(maryxml != null);
        MaryData inputData = new MaryData(inputType, locale);
        inputData.readFrom(maryxml, null);
        Request r = new Request(inputType, targetType, locale, null, "", "", 1, null);
        r.setInputData(inputData);
        r.process();
        MaryData outputData = r.getOutputData();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        outputData.writeTo(baos);
        // And now a validating parse:
        MaryData testData = new MaryData(outputData.getType(), locale);        
        testData.readFrom(new ByteArrayInputStream(baos.toByteArray()), null);
    }

}
