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
package marytts.server.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.sampled.AudioFileFormat;

import org.apache.http.HttpResponse;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.log4j.Logger;
import org.jsresources.AppendableSequenceAudioInputStream;

import marytts.datatypes.MaryDataType;
import marytts.modules.MaryModule;
import marytts.modules.synthesis.Voice;
import marytts.server.MaryProperties;
import marytts.server.Request;

public class RequestHttp extends Request
{
    public RequestHttp(MaryDataType inputType, MaryDataType outputType,
            Locale defaultLocale, Voice defaultVoice, String defaultEffects,
            String defaultStyle, int id, AudioFileFormat audioFileFormat) 
    {
        super(inputType, outputType, defaultLocale, defaultVoice, defaultEffects,
                defaultStyle, id, audioFileFormat);
    }
    
    public RequestHttp(MaryDataType inputType, MaryDataType outputType, Locale defaultLocale,
            Voice defaultVoice, String defaultEffects, String defaultStyle,
            int id, AudioFileFormat audioFileFormat, boolean streamAudio)
    {
        super(inputType, outputType, defaultLocale,
            defaultVoice, defaultEffects, defaultStyle,
            id, audioFileFormat, streamAudio);
    }
    
    /**
     * Write the output data to the specified Http response.
     */
    public void writeOutputData(HttpResponse response) throws Exception 
    {
        if (outputData == null)
            throw new NullPointerException("No output data -- did process() succeed?");
     
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        if (outputStream == null)
            throw new NullPointerException("cannot write to null output stream");
        // Safety net: if the output is not written within a certain amount of
        // time, give up. This prevents our thread from being locked forever if an
        // output deadlock occurs (happened very rarely on Java 1.4.2beta).
        final OutputStream os = outputStream;
        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            public void run() {
                logger.warn("Timeout occurred while writing output. Forcefully closing output stream.");
                try {
                    os.close();
                } catch (IOException ioe) {
                    logger.warn(ioe);
                }
            }
        };
        int timeout = MaryProperties.getInteger("modules.timeout", 10000);
        if (outputType.equals(MaryDataType.get("AUDIO"))) {
            // This means either a lot of data (for WAVE etc.) or a lot of processing
            // effort (for MP3), so allow for a lot of time:
            timeout *= 5;
        }
        timer.schedule(timerTask, timeout);
        try {
            outputData.writeTo(os);
        } catch (Exception e) {
            timer.cancel();
            throw e;
        }
        
        timer.cancel(); 
        
        if (outputData.getType().isXMLType()) 
        {
            NByteArrayEntity body = new NByteArrayEntity(((ByteArrayOutputStream)os).toByteArray());
            body.setContentType("text/html; charset=UTF-8");
            response.setEntity(body);
        } 
        else if (outputData.getType().isTextType()) // caution: XML types are text types!
        { 
            NByteArrayEntity body = new NByteArrayEntity(((ByteArrayOutputStream)os).toByteArray());
            body.setContentType("text/html; charset=UTF-8");
            response.setEntity(body);
        } 
        else // audio 
            MaryHttpServerUtils.toHttpResponse(((ByteArrayOutputStream)os).toByteArray(), response);
    }
}
