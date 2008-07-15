/**
 * Copyright 2007 DFKI GmbH.
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

package marytts.signalproc.demo;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.JButton;

import marytts.signalproc.process.FrameOverlapAddSource;
import marytts.signalproc.process.InlineDataProcessor;
import marytts.signalproc.process.Robotiser;
import marytts.signalproc.process.Robotiser.PhaseRemover;
import marytts.util.data.BufferedDoubleDataSource;
import marytts.util.data.DoubleDataSource;
import marytts.util.data.audio.AudioDoubleDataSource;
import marytts.util.data.audio.DDSAudioInputStream;
import marytts.util.math.MathUtils;
import marytts.util.signal.SignalProcUtils;

/**
 * @author Marc Schr&ouml;der
 *
 * Implements real-time audio effects
 * 
 */

public class OnlineAudioEffects extends Thread
{
    protected InlineDataProcessor effect;
    protected TargetDataLine microphone;
    protected AudioInputStream input;
    protected SourceDataLine loudspeakers;
    private boolean stopRequested;
    private int bufferSize;
    
    public OnlineAudioEffects(InlineDataProcessor effect, TargetDataLine microphone, SourceDataLine loudspeakers, int bufferSize)
    {
        this.effect = effect;
        this.input = null;
        this.microphone = microphone;
        this.loudspeakers = loudspeakers;
        this.bufferSize = bufferSize;
        this.setName("OnlineAudioEffect "+effect.toString());
    }

    public OnlineAudioEffects(InlineDataProcessor effect, AudioInputStream input, SourceDataLine loudspeakers, int bufferSize)
    {
        this.effect = effect;
        this.input = input;
        this.loudspeakers = loudspeakers;
        this.microphone = null;
        this.bufferSize = bufferSize;
        
        this.setName("OnlineAudioEffect "+effect.toString());
    }

    
    public void run()
    {
        stopRequested = false;
        loudspeakers.start();
        if (microphone != null) {
            microphone.start();
            input = new AudioInputStream(microphone);
        }
        assert input != null;
        
        if (!input.getFormat().matches(loudspeakers.getFormat())) {
            System.err.println("Houston, we have a problem: input: "+input.getFormat()+"\n"
                    +"output: "+loudspeakers.getFormat());
        }
        
        DoubleDataSource inputSource = new BufferedDoubleDataSource(new AudioDoubleDataSource(input));
        DoubleDataSource outputSource = new FrameOverlapAddSource(inputSource, bufferSize, (int) input.getFormat().getSampleRate(), effect);
        AudioInputStream result = new DDSAudioInputStream(new BufferedDoubleDataSource(outputSource), input.getFormat());
        byte[] buf = new byte[bufferSize];
        while (!stopRequested) {
            try {
                int nRead = result.read(buf);
                if (nRead == -1) 
                {
                    stopRequested = true;
                }
                else 
                {
                    loudspeakers.write(buf, 0, nRead);                    
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
                stopRequested = true;
            }
        }
        if (microphone != null)
        {
            microphone.stop();
            microphone = null;
        } else { // we had an audio file as input
            try {
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            loudspeakers.drain();
        }
        
        loudspeakers.stop();
        loudspeakers = null;
        input = null;
    }
    
    public void requestStop() 
    {
        stopRequested = true;
    }

    /**
     * @param args
     */
    public static void main(String[] args)
    {
        int channels = 1;
        int fs = 16000;
        AudioFormat audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, 16000, 16, channels, 2*channels, fs,
                false);
        
        TargetDataLine microphone = null;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class,
                    audioFormat);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(audioFormat);
            System.out.println("Microphone format: "+microphone.getFormat());
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            System.exit(1);
        }
        
        SourceDataLine loudspeakers = null;
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class,
                    audioFormat);
            loudspeakers = (SourceDataLine) AudioSystem.getLine(info);
            loudspeakers.open(audioFormat);
            System.out.println("Loudspeaker format: "+loudspeakers.getFormat());
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
        
        // Choose an audio effect
        InlineDataProcessor effect = new Robotiser.PhaseRemover(4096);
        //InlineDataProcessor effect = new LPCWhisperiser(20);

        // Create the output thread and make it run in the background:
        int bufferSize = SignalProcUtils.getDFTSize(fs);
        OnlineAudioEffects online = new OnlineAudioEffects(effect, microphone, loudspeakers, bufferSize);
        online.start();
        
        // Here we simply wait until it is finished (which will never happen):
        try {
            online.join();
        } catch (InterruptedException ie) {}
    }

}
