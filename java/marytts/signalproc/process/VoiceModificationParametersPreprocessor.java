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

package marytts.signalproc.process;

import java.util.Arrays;

import marytts.signalproc.analysis.F0ReaderWriter;
import marytts.signalproc.analysis.FestivalUtt;
import marytts.signalproc.analysis.Labels;
import marytts.util.math.MathUtils;
import marytts.util.signal.SignalProcUtils;
import marytts.util.string.StringUtils;

/**
 * @author oytun.turk
 *
 */
public class VoiceModificationParametersPreprocessor extends VoiceModificationParameters 
{    
    public double [] pscalesVar;
    public double [] tscalesVar;
    public double [] escalesVar;
    public double [] vscalesVar;
    
    public double tscaleSingle;
    public int numPeriods;

    public VoiceModificationParametersPreprocessor(int samplingRate, int LPOrder,
                                                   double[] pscalesIn, 
                                                   double[] tscalesIn, 
                                                   double[] escalesIn, 
                                                   double[] vscalesIn, 
                                                   int[] pitchMarksIn, 
                                                   double wsFixedIn, double ssFixedIn, 
                                                   int numfrm, int numfrmFixed, int numPeriodsIn, 
                                                   boolean isFixedRate) 
    {
        super(samplingRate, LPOrder, pscalesIn, tscalesIn, escalesIn, vscalesIn);
        
        initialise(pitchMarksIn, wsFixedIn, ssFixedIn, numfrm, numfrmFixed, numPeriodsIn, isFixedRate);
    }
    
    //To do: Handle all isPscaleFromFestivalUttFile, isTscaleFromFestivalUttFile, isEscaleFromTargetWavFile,
    //       requests separately. Currently, there is no isEscaleFromTargetWavFile support
    //       and no support for using isPscaleFromFestivalUttFile but not isTscaleFromFestivalUttFile
    //       and vice versa.
    //       This constructor should also be combined with the above constructor
    //       which takes user specified scaling factors.
    //       Therefore, in the final version the user can request all variations,
    //       i.e. pscale as in the utt file with some additional scaling or shifting, 
    //       escale using only scale values provided by the user, etc
    public VoiceModificationParametersPreprocessor(String targetFestivalUttFile, 
                                                   String sourcePitchFile,
                                                   String sourceLabelFile, 
                                                   String sourceEnergyFile, //only required for escales
                                                   String targetLabelFile, //only required for escales
                                                   String targetEnergyFile, //only required for escales
                                                   boolean isPscaleFromFestivalUttFile, 
                                                   boolean isTscaleFromFestivalUttFile, 
                                                   boolean isEscaleFromTargetWavFile,
                                                   int[] pitchMarks, 
                                                   double wsFixed, double ssFixed, 
                                                   int numfrmIn, int numfrmFixedIn, int numPeriodsIn, 
                                                   boolean isFixedRate)
    {
        super();
        
        numPeriods = numPeriodsIn;
        
        //These are not implemented!!! To do later after Interspeech 2008 paper
        //escalesVar from sourceLabelFile, sourceEnergyFile, targetLabelFile, targetEnergyFile
        //vscalesVar from vscalesIn
        
        //Read from files (only necessary ones, you will need to read more when implementing escales etc)
        FestivalUtt festivalUtt = new FestivalUtt(targetFestivalUttFile);
        F0ReaderWriter sourceF0s = new F0ReaderWriter(sourcePitchFile);
        Labels sourceLabels = new Labels(sourceLabelFile);
        
        //Find pscalesVar and tscalesVar from targetFestivalUttFile, sourcePitchFile, sourceLabelFile
        tscaleSingle=-1;

        //Determine the pitch and time scaling factors corresponding to each pitch synchronous frame
        pscalesVar = MathUtils.ones(numfrmIn);
        tscalesVar = MathUtils.ones(numfrmIn);
        escalesVar = MathUtils.ones(numfrmIn);
        vscalesVar = MathUtils.ones(numfrmIn);
        Arrays.fill(escalesVar, 1.0);
        Arrays.fill(vscalesVar, 1.0);
        
        int i;
        double tVar;
        int sourceLabInd, targetDurationLabInd, targetPitchLabInd, sourcePitchInd;
        double sourceDuration, targetDuration, sourcePitch, targetPitch;
        
        //Find the optimum alignment between the source and the target labels since the phoneme sequences may not be identical due to silence periods etc.
        int[][] durationMap = null;

        Labels targetDurationLabels = null;
        Labels targetPitchLabels = null;
        
        for (i=0; i<festivalUtt.labels.length; i++)
        {
            if (festivalUtt.keys[i].compareTo("==Segment==")==0 && durationMap==null)
            {
                durationMap = StringUtils.alignLabels(sourceLabels.items, festivalUtt.labels[i].items);
                targetDurationLabels = new Labels(festivalUtt.labels[i]);
            }
            else if (festivalUtt.keys[i].compareTo("==Target==")==0)
                targetPitchLabels = new Labels(festivalUtt.labels[i]);
        }
        //
        
        if (durationMap!=null && targetDurationLabels!=null && targetPitchLabels!=null)
        {
            for (i=0; i<numfrmIn; i++)
            {
                if (!isFixedRate)
                    tVar = (0.5*(pitchMarks[i+numPeriods]+pitchMarks[i]))/fs;
                else
                    tVar = i*ssFixed+0.5*wsFixed;

                sourceLabInd = SignalProcUtils.time2LabelIndex(tVar, sourceLabels);
                if (sourceLabInd>0)
                    sourceDuration = sourceLabels.items[sourceLabInd].time-sourceLabels.items[sourceLabInd-1].time;
                else
                    sourceDuration = sourceLabels.items[sourceLabInd].time;

                targetDurationLabInd = StringUtils.findInMap(durationMap, sourceLabInd);
                if (targetDurationLabInd>=0)
                {
                    if (targetDurationLabInd>0)
                        targetDuration = targetDurationLabels.items[targetDurationLabInd].time-targetDurationLabels.items[targetDurationLabInd-1].time;
                    else
                        targetDuration = targetDurationLabels.items[targetDurationLabInd].time;

                    tscalesVar[i] = targetDuration/sourceDuration;
                    tscalesVar[i] = Math.max(tscalesVar[i], 0.5);
                    tscalesVar[i] = Math.min(tscalesVar[i], 2.0);
                }
                else
                    tscalesVar[i] = 0.0;
                
                targetPitchLabInd = SignalProcUtils.time2LabelIndex(tVar, targetPitchLabels);
                if (targetPitchLabInd>0)
                {
                    targetPitch = MathUtils.linearMap(tVar, 
                                                      targetPitchLabels.items[targetPitchLabInd-1].time, 
                                                      targetPitchLabels.items[targetPitchLabInd].time, 
                                                      targetPitchLabels.items[targetPitchLabInd-1].valuesRest[0],
                                                      targetPitchLabels.items[targetPitchLabInd].valuesRest[0]);
                }
                else
                    targetPitch = targetPitchLabels.items[targetPitchLabInd].valuesRest[0];

                sourcePitchInd = SignalProcUtils.time2frameIndex(tVar, sourceF0s.header.ws, sourceF0s.header.ss);
                if (sourcePitchInd>sourceF0s.header.numfrm-1)
                    sourcePitchInd=sourceF0s.header.numfrm-1;
                sourcePitch = sourceF0s.contour[sourcePitchInd];

                if (targetPitch>10.0 && sourcePitch>10.0)
                    pscalesVar[i] = targetPitch/sourcePitch;
                else
                    pscalesVar[i] = 1.0;
                pscalesVar[i] = Math.max(pscalesVar[i], 0.8);
                pscalesVar[i] = Math.min(pscalesVar[i], 1.2);
            }
            
            tscalesVar = SignalProcUtils.interpolate_pitch_uv(tscalesVar, 0.1);
            
            for (i=0; i<numfrmIn; i++)
            {
                tscalesVar[i] = Math.max(tscalesVar[i], 0.8);
                tscalesVar[i] = Math.min(tscalesVar[i], 1.2);
            }
        }
    }

    private void initialise(int [] pitchMarksIn, double wsFixedIn, double ssFixedIn, int numfrm, int numfrmFixed, int numPeriodsIn, boolean isFixedRate)
    {
        numPeriods = numPeriodsIn;

        if (pitchMarksIn != null)
        {
            getScalesVar(pitchMarksIn, wsFixedIn, ssFixedIn, numfrm, numfrmFixed, isFixedRate);
        }     
    }

    private void getScalesVar(int [] pitchMarks, double wsFixed, double ssFixed, int numfrm, int numfrmFixed, boolean isFixedRate)
    {   
        if (tscales.length==1)
            tscaleSingle=tscales[0]; 
        else
            tscaleSingle=-1;

        //Find pscale, tscale and escale values corresponding to each fixed skip rate frame
        if (pscales.length != numfrmFixed)
            pscales = MathUtils.modifySize(pscales, numfrmFixed);
        
        if (tscales.length !=numfrmFixed)
            tscales = MathUtils.modifySize(tscales, numfrmFixed);
        
        if (escales.length != numfrmFixed)
            escales = MathUtils.modifySize(escales, numfrmFixed);
        
        if (vscales.length != numfrmFixed)
            vscales = MathUtils.modifySize(vscales, numfrmFixed);
        //

        //Determine the pitch, time, and energy scaling factors corresponding to each pitch synchronous frame
        pscalesVar = MathUtils.ones(numfrm);
        tscalesVar = MathUtils.ones(numfrm);
        escalesVar = MathUtils.ones(numfrm);
        vscalesVar = MathUtils.ones(numfrm);
        
        double tVar;
        int ind;
        for (int i=0; i<numfrm; i++)
        {
            if (!isFixedRate)
                tVar = (0.5*(pitchMarks[i+numPeriods]+pitchMarks[i]))/fs;
            else
                tVar = i*ssFixed+0.5*wsFixed;
            
            ind = (int)(Math.floor((tVar-0.5*wsFixed)/ssFixed+0.5));
            if (ind<0)
                ind=0;
            if (ind>numfrmFixed-1)
                ind=numfrmFixed-1;
            
            pscalesVar[i] = pscales[ind];
            tscalesVar[i] = tscales[ind];
            escalesVar[i] = escales[ind];
            vscalesVar[i] = vscales[ind];
        }
        //
    }
}
