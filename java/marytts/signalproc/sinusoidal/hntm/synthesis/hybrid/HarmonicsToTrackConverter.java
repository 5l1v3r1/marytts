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

package marytts.signalproc.sinusoidal.hntm.synthesis.hybrid;

import marytts.signalproc.analysis.RegularizedCepstralEnvelopeEstimator;
import marytts.signalproc.sinusoidal.Sinusoid;
import marytts.signalproc.sinusoidal.SinusoidalAnalysisParams;
import marytts.signalproc.sinusoidal.SinusoidalTrack;
import marytts.signalproc.sinusoidal.SinusoidalTracks;
import marytts.signalproc.sinusoidal.TrackGenerator;
import marytts.signalproc.sinusoidal.hntm.analysis.HntmSpeechSignal;
import marytts.signalproc.sinusoidal.hntm.modification.HntmProsodyModifier;
import marytts.signalproc.sinusoidal.hntm.synthesis.HntmSynthesizedSignal;
import marytts.util.signal.SignalProcUtils;

/**
 * This class converts harmonics as obtained by HNTM analysis 
 * to sinusoidal tracks that can be used by a pure sinusoidal synthesizer.
 * The aim of this approach is to find out the reason for quality decrease 
 * in HNTM synthesis in the absence of prosody modificaitions.
 * If the analyzed values are fine and pure sinusoidal synthesis generates a clean signal,
 * we have a problem in HNTM harmonic synthesis.
 * If not, we will need to look at HNTM analysis using a frequency domain approach as in pure sinusoidal model.
 * 
 * Note that all frequency values should be in radians in the sinusoidal tracks
 * whereas HNTM uses frequency values in Hz. "convert" function handles this conversion as well.
 * 
 * @author oytun.turk
 *
 */
public class HarmonicsToTrackConverter {
    
    public static SinusoidalTracks convert(HntmSpeechSignal hntmSignal)
    {        
        int numFrames = hntmSignal.frames.length;
        float deltaInRadians = SignalProcUtils.hz2radian(SinusoidalAnalysisParams.DEFAULT_DELTA_IN_HZ, hntmSignal.samplingRateInHz);
        
        SinusoidalTracks tr = null;
        int i;
        Sinusoid zeroAmpSin;
        Sinusoid sin;
        
        if (numFrames>0)
        {   
            int j, k;
            float tmpDist, minDist;
            int trackInd;
            boolean [] bSinAssigneds = null;
            float amp;
    
            for (i=0; i<numFrames; i++)
            {   
                if (hntmSignal.frames[i].h.phases!=null && hntmSignal.frames[i].h.phases.length>0)
                {
                    if (tr==null) //If no tracks yet, assign the current sinusoids to new tracks
                    {
                        tr = new SinusoidalTracks(hntmSignal.frames[i].h.phases.length, hntmSignal.samplingRateInHz);
                        tr.setSysAmpsAndTimes(hntmSignal);

                        for (j=0; j<hntmSignal.frames[i].h.phases.length; j++)
                        {
                            //First add a zero amplitude sinusoid at previous time instant to allow smooth synthesis (i.e. "turning on" the track)
                            zeroAmpSin = new Sinusoid(0.0f, SignalProcUtils.hz2radian(j*hntmSignal.frames[i].f0InHz, hntmSignal.samplingRateInHz), 0.0f, Sinusoid.NON_EXISTING_FRAME_INDEX);
                            tr.add(new SinusoidalTrack(hntmSignal.frames[i].tAnalysisInSeconds-TrackGenerator.ZERO_AMP_SHIFT_IN_SECONDS, zeroAmpSin,  hntmSignal.frames[i].maximumFrequencyOfVoicingInHz, SinusoidalTrack.TURNED_ON));
                            //

                            amp = (float)RegularizedCepstralEnvelopeEstimator.cepstrum2linearSpectrumValue(hntmSignal.frames[i].h.ceps , j*hntmSignal.frames[i].f0InHz, hntmSignal.samplingRateInHz);
                            sin = new Sinusoid(amp, SignalProcUtils.hz2radian(j*hntmSignal.frames[i].f0InHz, hntmSignal.samplingRateInHz), hntmSignal.frames[i].h.phases[j], i);
                            tr.tracks[tr.currentIndex].add(hntmSignal.frames[i].tAnalysisInSeconds, sin, hntmSignal.frames[i].maximumFrequencyOfVoicingInHz, SinusoidalTrack.ACTIVE);
                        }
                    }
                    else //If there are tracks, first check "continuations" by checking whether a given sinusoid is in the +-deltaInRadians neighbourhood of the previous track. 
                        // Those tracks that do not continue are "turned off". 
                        // All sinusoids of the current frame that are not assigned to any of the "continuations" or "turned off" are "birth"s of new tracks.
                    {
                        for (j=0; j<tr.currentIndex+1; j++)
                        {
                            if (tr.tracks[j] != null)
                                tr.tracks[j].resetCandidate();
                        }

                        bSinAssigneds = new boolean[hntmSignal.frames[i].h.phases.length];

                        //Continuations:
                        for (k=0; k<hntmSignal.frames[i].h.phases.length; k++)
                        {
                            minDist = Math.abs(SignalProcUtils.hz2radian(k*hntmSignal.frames[i].f0InHz, hntmSignal.samplingRateInHz)-tr.tracks[0].freqs[tr.tracks[0].currentIndex]);
                            if (minDist<deltaInRadians)
                                trackInd = 0;
                            else
                                trackInd = -1;

                            for (j=1; j<tr.currentIndex+1; j++)
                            {
                                tmpDist = Math.abs(SignalProcUtils.hz2radian(k*hntmSignal.frames[i].f0InHz,hntmSignal.samplingRateInHz)-tr.tracks[j].freqs[tr.tracks[j].currentIndex]);

                                if (tmpDist<deltaInRadians && (trackInd==-1 || tmpDist<minDist))
                                {
                                    minDist = tmpDist;
                                    trackInd = j;
                                }   
                            }

                            if (trackInd>-1)
                            {
                                if (tr.tracks[trackInd].newCandidateInd>-1)
                                    bSinAssigneds[tr.tracks[trackInd].newCandidateInd] = false;

                                amp = (float)RegularizedCepstralEnvelopeEstimator.cepstrum2linearSpectrumValue(hntmSignal.frames[i].h.ceps , k*hntmSignal.frames[i].f0InHz, hntmSignal.samplingRateInHz);
                                sin = new Sinusoid(amp, SignalProcUtils.hz2radian(k*hntmSignal.frames[i].f0InHz, hntmSignal.samplingRateInHz), hntmSignal.frames[i].h.phases[k], i);
                                tr.tracks[trackInd].newCandidate = new Sinusoid(sin);
                                tr.tracks[trackInd].newCandidateInd = k;

                                bSinAssigneds[k] = true; //The sinusoid might be assigned to an existing track provided that a closer sinusoid is not found
                            }
                            else
                                bSinAssigneds[k] = false; //This is the birth of a new track since it does not match any existing tracks
                        }

                        //Here is the actual assignment of sinusoids to existing tracks 
                        for (j=0; j<tr.currentIndex+1; j++)
                        {
                            if (tr.tracks[j].newCandidate != null)
                            {
                                Sinusoid tmpSin = new Sinusoid(tr.tracks[j].newCandidate);

                                if (tr.tracks[j].states[tr.tracks[j].currentIndex]!=SinusoidalTrack.ACTIVE)
                                {
                                    zeroAmpSin = new Sinusoid(0.0f, tr.tracks[j].freqs[tr.tracks[j].totalSins-1], 0.0f, Sinusoid.NON_EXISTING_FRAME_INDEX);
                                    tr.tracks[j].add(hntmSignal.frames[i].tAnalysisInSeconds-TrackGenerator.ZERO_AMP_SHIFT_IN_SECONDS, zeroAmpSin, hntmSignal.frames[i].maximumFrequencyOfVoicingInHz, SinusoidalTrack.TURNED_ON);
                                }

                                tr.tracks[j].add(hntmSignal.frames[i].tAnalysisInSeconds, tmpSin, hntmSignal.frames[i].maximumFrequencyOfVoicingInHz, SinusoidalTrack.ACTIVE); 
                            }
                            else //Turn off tracks that are not assigned any new sinusoid
                            {
                                if (tr.tracks[j].states[tr.tracks[j].currentIndex]!=SinusoidalTrack.TURNED_OFF)
                                {
                                    zeroAmpSin = new Sinusoid(0.0f, tr.tracks[j].freqs[tr.tracks[j].totalSins-1], 0.0f, Sinusoid.NON_EXISTING_FRAME_INDEX);
                                    tr.tracks[j].add(hntmSignal.frames[i].tAnalysisInSeconds+TrackGenerator.ZERO_AMP_SHIFT_IN_SECONDS, zeroAmpSin, hntmSignal.frames[i].maximumFrequencyOfVoicingInHz, SinusoidalTrack.TURNED_OFF);
                                }  
                            } 
                        }

                        //Births: Create new tracks from sinusoids that are not assigned to existing tracks
                        for (k=0; k<bSinAssigneds.length; k++)
                        {
                            if (!bSinAssigneds[k])
                            {
                                //First add a zero amplitude sinusoid to previous frame to allow smooth synthesis (i.e. "turning on" the track)
                                zeroAmpSin = new Sinusoid(0.0f, SignalProcUtils.hz2radian(k*hntmSignal.frames[i].f0InHz,hntmSignal.samplingRateInHz), 0.0f, Sinusoid.NON_EXISTING_FRAME_INDEX);
                                tr.add(new SinusoidalTrack(hntmSignal.frames[i].tAnalysisInSeconds-TrackGenerator.ZERO_AMP_SHIFT_IN_SECONDS, zeroAmpSin, hntmSignal.frames[i].maximumFrequencyOfVoicingInHz, SinusoidalTrack.TURNED_ON));
                                //

                                amp = (float)RegularizedCepstralEnvelopeEstimator.cepstrum2linearSpectrumValue(hntmSignal.frames[i].h.ceps , k*hntmSignal.frames[i].f0InHz, hntmSignal.samplingRateInHz);
                                sin = new Sinusoid(amp, SignalProcUtils.hz2radian(k*hntmSignal.frames[i].f0InHz, hntmSignal.samplingRateInHz), hntmSignal.frames[i].h.phases[k], i);
                                tr.tracks[tr.currentIndex].add(hntmSignal.frames[i].tAnalysisInSeconds, sin, hntmSignal.frames[i].maximumFrequencyOfVoicingInHz, SinusoidalTrack.ACTIVE);
                            }
                        }
                    }
                    System.out.println("Track generation using frame " + String.valueOf(i+1) + " of " + String.valueOf(numFrames));
                } 
                
                //Turn-off all active tracks after the last speech frame
                if (i==numFrames-1)
                {
                    for (j=0; j<tr.currentIndex+1; j++)
                    {
                        if (Math.abs(hntmSignal.frames[i].tAnalysisInSeconds-tr.tracks[j].times[tr.tracks[j].totalSins-1])<TrackGenerator.ZERO_AMP_SHIFT_IN_SECONDS)
                        {
                            if (tr.tracks[j].states[tr.tracks[j].currentIndex]==SinusoidalTrack.ACTIVE)
                            {
                                zeroAmpSin = new Sinusoid(0.0f, tr.tracks[j].freqs[tr.tracks[j].totalSins-1], 0.0f, Sinusoid.NON_EXISTING_FRAME_INDEX);
                                tr.tracks[j].add(hntmSignal.frames[i].tAnalysisInSeconds+TrackGenerator.ZERO_AMP_SHIFT_IN_SECONDS, zeroAmpSin, hntmSignal.frames[i].maximumFrequencyOfVoicingInHz, SinusoidalTrack.TURNED_OFF);
                            }
                        }
                    }
                }
                //
            }
        }
        
        for (i=0; i<=tr.currentIndex; i++)
            tr.tracks[i].correctTrack();
        
        tr.setOriginalDurationManual(hntmSignal.originalDurationInSeconds);
        
        SinusoidalTracks trOut = new SinusoidalTracks(tr, 0, tr.currentIndex);
        trOut = TrackGenerator.postProcess(trOut);
            
        return trOut;
    }
}
