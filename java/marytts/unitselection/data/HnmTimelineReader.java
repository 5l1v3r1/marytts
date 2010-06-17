/**
 * Copyright 2006 DFKI GmbH.
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
package marytts.unitselection.data;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Properties;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import marytts.signalproc.adaptation.prosody.BasicProsodyModifierParams;
import marytts.signalproc.analysis.PitchReaderWriter;
import marytts.signalproc.sinusoidal.hntm.analysis.FrameNoisePartWaveform;
import marytts.signalproc.sinusoidal.hntm.analysis.HntmAnalyzerParams;
import marytts.signalproc.sinusoidal.hntm.analysis.HntmSpeechFrame;
import marytts.signalproc.sinusoidal.hntm.analysis.HntmSpeechSignal;
import marytts.signalproc.sinusoidal.hntm.synthesis.HntmSynthesizedSignal;
import marytts.signalproc.sinusoidal.hntm.synthesis.HntmSynthesizer;
import marytts.signalproc.sinusoidal.hntm.synthesis.HntmSynthesizerParams;
import marytts.util.data.BufferedDoubleDataSource;
import marytts.util.data.audio.AudioDoubleDataSource;
import marytts.util.data.audio.DDSAudioInputStream;
import marytts.util.io.FileUtils;
import marytts.util.math.ArrayUtils;
import marytts.util.math.ComplexNumber;
import marytts.util.math.MathUtils;
import marytts.util.signal.SignalProcUtils;
import marytts.util.string.StringUtils;

/**
 * A reader class for the harmonics plus noise timeline file.
 * 
 * @author Oytun T&uumlrk
 *
 */
public class HnmTimelineReader extends TimelineReader
{
    public HntmAnalyzerParams analysisParams;
    
    public HnmTimelineReader()
    {
    }

    public HnmTimelineReader(String fileName) throws IOException
    {
        super(fileName);
    }

    public void load(String fileName) throws IOException
    {
        super.load(fileName);
        // Now make sense of the processing header
        Properties props = new Properties();
        ByteArrayInputStream bais = new ByteArrayInputStream(procHdr.getString().getBytes("latin1"));
        props.load(bais);
        ensurePresent(props, "hnm.noiseModel");

        analysisParams = new HntmAnalyzerParams();
        
        analysisParams.noiseModel = Integer.parseInt(props.getProperty("hnm.noiseModel"));
        analysisParams.hnmPitchVoicingAnalyzerParams.numFilteringStages = Integer.parseInt(props.getProperty("hnm.numFiltStages"));
        analysisParams.hnmPitchVoicingAnalyzerParams.medianFilterLength = Integer.parseInt(props.getProperty("hnm.medianFiltLen"));
        analysisParams.hnmPitchVoicingAnalyzerParams.movingAverageFilterLength = Integer.parseInt(props.getProperty("hnm.maFiltLen"));
        analysisParams.hnmPitchVoicingAnalyzerParams.cumulativeAmpThreshold = Float.parseFloat(props.getProperty("hnm.cumAmpTh"));
        analysisParams.hnmPitchVoicingAnalyzerParams.maximumAmpThresholdInDB = Float.parseFloat(props.getProperty("hnm.maxAmpTh"));
        analysisParams.hnmPitchVoicingAnalyzerParams.harmonicDeviationPercent = Float.parseFloat(props.getProperty("hnm.harmDevPercent"));
        analysisParams.hnmPitchVoicingAnalyzerParams.sharpPeakAmpDiffInDB = Float.parseFloat(props.getProperty("hnm.sharpPeakAmpDiff"));
        analysisParams.hnmPitchVoicingAnalyzerParams.minimumTotalHarmonics = Integer.parseInt(props.getProperty("hnm.minHarmonics"));
        analysisParams.hnmPitchVoicingAnalyzerParams.maximumTotalHarmonics = Integer.parseInt(props.getProperty("hnm.maxHarmonics"));
        analysisParams.hnmPitchVoicingAnalyzerParams.minimumVoicedFrequencyOfVoicing = Float.parseFloat(props.getProperty("hnm.minVoicedFreq"));
        analysisParams.hnmPitchVoicingAnalyzerParams.maximumVoicedFrequencyOfVoicing = Float.parseFloat(props.getProperty("hnm.maxVoicedFreq"));
        analysisParams.hnmPitchVoicingAnalyzerParams.maximumFrequencyOfVoicingFinalShift = Float.parseFloat(props.getProperty("hnm.maxFreqVoicingFinalShift"));
        analysisParams.hnmPitchVoicingAnalyzerParams.neighsPercent = Float.parseFloat(props.getProperty("hnm.neighsPercent"));
        analysisParams.harmonicPartCepstrumOrder = Integer.parseInt(props.getProperty("hnm.harmCepsOrder"));
        analysisParams.regularizedCepstrumWarpingMethod = Integer.parseInt(props.getProperty("hnm.regCepWarpMethod"));
        analysisParams.regularizedCepstrumLambdaHarmonic = Float.parseFloat(props.getProperty("hnm.regCepsLambda"));
        analysisParams.noisePartLpOrder = Integer.parseInt(props.getProperty("hnm.noiseLpOrder"));
        analysisParams.preemphasisCoefNoise = Float.parseFloat(props.getProperty("hnm.preCoefNoise"));
        analysisParams.hpfBeforeNoiseAnalysis = Boolean.parseBoolean(props.getProperty("hnm.hpfBeforeNoiseAnalysis"));
        analysisParams.numPeriodsHarmonicsExtraction = Float.parseFloat(props.getProperty("hnm.harmNumPer"));
    }
    
    private void ensurePresent(Properties props, String key) throws IOException
    {
        if (!props.containsKey(key))
            throw new IOException("Processing header does not contain required field '"+key+"'");

    }

    /**
     * Read and return the upcoming datagram.
     * 
     * @return the current datagram, or null if EOF was encountered; internally updates the time pointer.
     * 
     * @throws IOException
     */
    @Override
    protected Datagram getNextDatagram(ByteBuffer bb) throws IOException {
        
        Datagram d = null;
        
        /* If the end of the datagram zone is reached, gracefully refuse to read */
        if (bb.position() == timeIdxBytePos ) return( null );
        /* Else, pop the datagram out of the file */
        try {
            d = new HnmDatagram(bb, analysisParams.noiseModel);
        }
        /* Detect a possible EOF encounter */
        catch ( EOFException e ) {
            throw new IOException( "While reading a datagram, EOF was met before the time index position: "
                    + "you may be dealing with a corrupted timeline file." );
        }
        
        return( d );
    }
    
    private static void testSynthesizeFromDatagrams(LinkedList<HnmDatagram> datagrams, int startIndex, int endIndex, DataOutputStream output) throws IOException
    {
        HntmSynthesizer s = new HntmSynthesizer();
        //TO DO: These should come from timeline and user choices...
        HntmAnalyzerParams analysisParams = new HntmAnalyzerParams();
        HntmSynthesizerParams synthesisParams = new HntmSynthesizerParams();
        BasicProsodyModifierParams pmodParams = new BasicProsodyModifierParams();
        int samplingRateInHz = 16000;
        
        int totalFrm = 0;
        int i;
        float originalDurationInSeconds = 0.0f;
        float deltaTimeInSeconds;
        
        for (i=startIndex; i<=endIndex; i++)
        {
            if (datagrams.get(i)!=null)
            {
                if (datagrams.get(i) instanceof HnmDatagram)
                {
                    totalFrm++;
                    //deltaTimeInSeconds = SignalProcUtils.sample2time(((HnmDatagram)datagrams.get(i)).getDuration(), samplingRateInHz);
                    deltaTimeInSeconds = ((HnmDatagram)datagrams.get(i)).frame.deltaAnalysisTimeInSeconds;
                    
                    originalDurationInSeconds += deltaTimeInSeconds;
                }  
            } 
        }
        
        HntmSpeechSignal hnmSignal = null;
        hnmSignal = new HntmSpeechSignal(totalFrm, samplingRateInHz, originalDurationInSeconds);
        //
        
        int frameCount = 0;
        float tAnalysisInSeconds = 0.0f;
        for (i=startIndex; i<=endIndex; i++)
        {
            if (datagrams.get(i)!=null)
            {
                if (datagrams.get(i) instanceof HnmDatagram)
                {
                    //tAnalysisInSeconds += SignalProcUtils.sample2time(((HnmDatagram)datagrams.get(i)).getDuration(), samplingRateInHz);
                    tAnalysisInSeconds += ((HnmDatagram)datagrams.get(i)).getFrame().deltaAnalysisTimeInSeconds;
                    
                    if  (frameCount<totalFrm)
                    {
                        hnmSignal.frames[frameCount] = new HntmSpeechFrame(((HnmDatagram)datagrams.get(i)).getFrame());
                        hnmSignal.frames[frameCount].tAnalysisInSeconds = tAnalysisInSeconds;
                        frameCount++;
                    }
                }
            }    
        }

        HntmSynthesizedSignal ss = null;
        if (totalFrm>0)
        {    
            ss = s.synthesize(hnmSignal, null, null, pmodParams, null, analysisParams, synthesisParams);
            FileUtils.writeBinaryFile(ArrayUtils.copyDouble2Short(ss.output), output);
            if (ss.output!=null)
                ss.output = MathUtils.multiply(ss.output, 1.0/32768.0);
        }
    }
    
    public static void main(String[] args) throws UnsupportedAudioFileException, IOException
    {        
        HnmTimelineReader h = new HnmTimelineReader();
        try {
            h.load("timeline_hnm.mry");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        LinkedList<HnmDatagram> datagrams = new LinkedList<HnmDatagram>();
        int count = 0;
        long startDatagramTime = 0;
        int numDatagrams = (int) h.numDatagrams;
        //long numDatagrams = 2000;
        
        Datagram[] rawDatagrams = h.getDatagrams(0l, numDatagrams, h.getSampleRate());
        for (int i=0; i< rawDatagrams.length; i++)
        {
            HnmDatagram d = (HnmDatagram) rawDatagrams[i];
            datagrams.add(d);
            count++;
            System.out.println("Datagram " + String.valueOf(count) + "Noise waveform size=" + ((FrameNoisePartWaveform)(((HnmDatagram)d).frame.n)).waveform().length);
        }
        
        int clusterSize = 1000;
        int numClusters = (int)Math.floor((numDatagrams)/((double)clusterSize)+0.5);
        int startIndex, endIndex;
        DataOutputStream output = new DataOutputStream(new FileOutputStream(new File("d:\\output.bin")));
        for (int i=0; i<numClusters; i++)
        {
            startIndex = (int)(i*clusterSize);
            endIndex = (int)Math.min((i+1)*clusterSize-1, numDatagrams-1);
            testSynthesizeFromDatagrams(datagrams, startIndex, endIndex, output);
            System.out.println("Timeline cluster " + String.valueOf(i+1) + " of " + String.valueOf(numClusters) + " synthesized...");
        }
    }
}

