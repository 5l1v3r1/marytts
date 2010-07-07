/**
 * Copyright 2007 DFKI GmbH.
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
package marytts.unitselection.concat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sound.sampled.AudioInputStream;

import marytts.modules.phonemiser.Allophone;
import marytts.signalproc.process.FDPSOLAProcessor;
import marytts.unitselection.data.Datagram;
import marytts.unitselection.data.Unit;
import marytts.unitselection.select.HalfPhoneTarget;
import marytts.unitselection.select.SelectedUnit;
import marytts.unitselection.select.Target;


/**
 * A unit concatenator that supports FD-PSOLA based prosody modifications during speech synthesis
 * 
 * @author Oytun T&uumlrk
 *
 */
public class FdpsolaUnitConcatenator extends OverlapUnitConcatenator {
    private boolean [][] voicings;
    private double [][] pscales;
    private double [][] tscales;
    private Datagram[][] datagrams;
    private Datagram[] rightContexts;
    
    /**
     * 
     */
    public FdpsolaUnitConcatenator() 
    {
        super();
    }
    
    /**
     * Get the raw audio material for each unit from the timeline.
     * <p>
     * TODO: this is completely equivalent to {@link OverlapUnitConcatenator#getDatagramsFromTimeline}! {{prod}}
     * @param units
     */
    @Deprecated
    protected void getDatagramsFromTimeline(List<SelectedUnit> units) throws IOException
    {
        for (SelectedUnit unit : units) 
        {
            assert !unit.getUnit().isEdgeUnit() : "We should never have selected any edge units!";
            OverlapUnitData unitData = new OverlapUnitData();
            unit.setConcatenationData(unitData);
            int nSamples = 0;
            int unitSize = unitToTimeline(unit.getUnit().duration); // convert to timeline samples
            long unitStart = unitToTimeline(unit.getUnit().startTime); // convert to timeline samples
            //System.out.println("Unit size "+unitSize+", pitchmarksInUnit "+pitchmarksInUnit);
            Datagram [] tmpDatagrams = timeline.getDatagrams(unitStart,(long)unitSize);
            unitData.setFrames(tmpDatagrams);
            // one right context period for windowing:
            Datagram rightContextFrame = null;
            Unit nextInDB = database.getUnitFileReader().getNextUnit(unit.getUnit());
            if (nextInDB != null && !nextInDB.isEdgeUnit()) {
                rightContextFrame = timeline.getDatagram(unitStart+unitSize);
                unitData.setRightContextFrame(rightContextFrame);
            }
        }
    }
    
    /**
     * Determine target pitchmarks (= duration and f0) for each unit.
     * @param units
     */
    protected void determineTargetPitchmarks(List<SelectedUnit> units)
    {
        // First, determine the target pitchmarks as usual by the parent
        // implementation:
        super.determineTargetPitchmarks(units);
        
        int len = units.size();
//        datagrams = new Datagram[len][];
//        rightContexts = new Datagram[len];
        // we need something more flexible than arrays to handle Units with frameless UnitData: 
        ArrayList<Datagram[]> datagramList = new ArrayList<Datagram[]>(len);
        ArrayList<Datagram> rightContextList = new ArrayList<Datagram>(len);

        int i, j;
        SelectedUnit unit = null;

        //Preprocessing and allocation
        for (i=0; i<len; i++) 
        {
            unit = (SelectedUnit) units.get(i);
            
            OverlapUnitData unitData = (OverlapUnitData)unit.getConcatenationData();
            assert unitData != null : "Should not have null unitdata here";
            // processing down the line fails when frames are empty or duration is zero, therefore:
            if (unitData.getUnitDuration() == 0) {
                logger.debug("ignoring datagrams for unit " + unit.toString());
                continue;
            }
            Datagram[] frames = unitData.getFrames();
            assert frames != null : "Cannot generate audio from null frames";
            
            datagramList.add(frames);
            rightContextList.add(unitData.getRightContextFrame());
            
            // the rest is obsolete:
            
            // Generate audio from frames
//            datagrams[i] = frames;
//            
//            Unit nextInDB = database.getUnitFileReader().getNextUnit(unit.getUnit());
//            Unit nextSelected;
//            if (i+1==len) nextSelected = null;
//            else nextSelected = ((SelectedUnit)units.get(i+1)).getUnit();
//            if (nextInDB != null && !nextInDB.equals(nextSelected)) {
//                // Only use right context if we have a next unit in the DB is not the
//                // same as the next selected unit.
//                rightContexts[i] = unitData.getRightContextFrame(); // may be null
//            }
        }
        
        // overwrite buggy arrays from the Lists:
        datagrams = new Datagram[datagramList.size()][];
        rightContexts = new Datagram[datagramList.size()];
        for (int u = 0; u < datagramList.size(); u++) {
            datagrams[u] = datagramList.get(u);
            rightContexts[u] = rightContextList.get(u);
        }
        
        // TODO: all of this is also done in getPitchScales; delete this?
//        getVoicings(units);

        // this seems to be the first instance of new code in this class:
        getPitchScales(units);
        
//        getDurationScales(units);
        getPhoneBasedDurationScales(units);
    }
    
    // TODO: this is completely overwritten in getPitchScales {{prod}}
    @Deprecated
    private void getVoicings(List<SelectedUnit> units)
    {
        int len = units.size();
        // actually:
        len = datagrams.length;
        int i, j;
        
        voicings = new boolean[len][];

        SelectedUnit unit = null;
        
        //Estimation of pitch scale modification amounts
        for (i=0; i<len; i++) 
        {
            unit = (SelectedUnit) units.get(i);

            Allophone allophone = unit.getTarget().getAllophone();

            voicings[i] = new boolean[datagrams[i].length];
            
            for (j=0; j<datagrams[i].length; j++)
            {
                if (allophone != null && (allophone.isVowel() || allophone.isVoiced()))
                    voicings[i][j] = true;
                else
                    voicings[i][j] = false;
            }
        }
        //
    }
    
    //We can try different things in this function
    //1) Pitch of the selected units can  be smoothed without using the target pitch values at all. 
    //   This will involve creating the target f0 values for each frame by ensuing small adjustments and yet reduce pitch discontinuity
    //2) Pitch of the selected units can be modified to match the specified target where those target values are smoothed
    //3) A mixture of (1) and (2) can be deviced, i.e. to minimize the amount of pitch modification one of the two methods can be selected for a given unit
    //4) Pitch segments of selected units can be shifted 
    //5) Pitch segments of target units can be shifted
    //6) Pitch slopes can be modified for better matching in concatenation boundaries
    private void getPitchScales(List<SelectedUnit> units)
    {
        int len = units.size();
        // actually:
        len = datagrams.length;
        int i, j;
        double averageUnitF0InHz;
        double averageTargetF0InHz;
        int totalTargetUnits;
        voicings = new boolean[len][];
        pscales = new double[len][];
        SelectedUnit prevUnit = null;
        SelectedUnit unit = null;
        SelectedUnit nextUnit = null;
        
        Target prevTarget = null;
        Target target = null;
        Target nextTarget = null;
        
        //Estimation of pitch scale modification amounts
        for (i=0; i<len; i++) 
        {
            if (i>0)
                prevUnit = (SelectedUnit) units.get(i-1);
            else
                prevUnit = null;
            
            unit = (SelectedUnit) units.get(i);
            
            if (i<len-1)
                nextUnit = (SelectedUnit) units.get(i+1);
            else
                nextUnit = null;
            
            // get Targets for these three Units:
            if (prevUnit != null) {
                prevTarget = prevUnit.getTarget();
            }
            target = unit.getTarget();
            if (nextUnit != null) {
                nextTarget = nextUnit.getTarget();
            }
            
            Allophone allophone = unit.getTarget().getAllophone();

            int totalDatagrams = 0;
            averageUnitF0InHz = 0.0;
            averageTargetF0InHz = 0.0;
            totalTargetUnits = 0;
            
            // so we are getting the mean F0 for each unit over a 3-unit window??
            // don't process previous Target if it's null or silence:
            if (i>0 && prevTarget != null && !prevTarget.isSilence())
            {
                for (j=0; j<datagrams[i-1].length; j++)
                {
                    // why not use voicings?
                    if (allophone != null && (allophone.isVowel() || allophone.isVoiced()))
                    {
                        averageUnitF0InHz += ((double)timeline.getSampleRate())/((double)datagrams[i-1][j].getDuration());
                        totalDatagrams++;
                    }
                }
                
                averageTargetF0InHz += prevTarget.getTargetF0InHz();
                totalTargetUnits++;
            }
            
            // don't process Target if it's null or silence:
            if (target != null && !target.isSilence()) {
                for (j=0; j<datagrams[i].length; j++)
                {
                    if (allophone != null && (allophone.isVowel() || allophone.isVoiced()))
                    {
                        averageUnitF0InHz += ((double)timeline.getSampleRate())/((double)datagrams[i][j].getDuration());
                        totalDatagrams++;
                    }

                    averageTargetF0InHz += target.getTargetF0InHz();
                    totalTargetUnits++;
                }
            }

            // don't process next Target if it's null or silence:
            if (i<len-1 && prevTarget != null && !prevTarget.isSilence())
            {
                for (j=0; j<datagrams[i+1].length; j++)
                {
                    if (allophone != null && (allophone.isVowel() || allophone.isVoiced()))
                    {
                        averageUnitF0InHz += ((double)timeline.getSampleRate())/((double)datagrams[i+1][j].getDuration());
                        totalDatagrams++;
                    }
                }
                
                averageTargetF0InHz += nextTarget.getTargetF0InHz();
                totalTargetUnits++;
            }
            
            averageTargetF0InHz /= totalTargetUnits;
            averageUnitF0InHz /= totalDatagrams;
            // so what was all that for?? these average frequencies are never used...

            voicings[i] = new boolean[datagrams[i].length];
            pscales[i] = new double[datagrams[i].length];
            
            for (j=0; j<datagrams[i].length; j++)
            {
                if (allophone != null && (allophone.isVowel() || allophone.isVoiced()))
                {
                    voicings[i][j] = true;
                    
                    /*
                    pscales[i][j] = averageTargetF0InHz/averageUnitF0InHz;
                    if (pscales[i][j]>1.2)
                        pscales[i][j]=1.2;
                    if (pscales[i][j]<0.8)
                        pscales[i][j]=0.8;
                        */
                    pscales[i][j] = 1.0;
                }
                else
                {
                    voicings[i][j] = false;
                    pscales[i][j] = 1.0;
                }
            }
        }
    }
    
    //We can try different things in this function
    //1) Duration modification factors can be estimated using neighbouring selected and target unit durations
    //2) Duration modification factors can be limited or even set to 1.0 for different phone classes
    //3) Duration modification factors can be limited depending on the previous/next phone class
    private void getDurationScales(List<SelectedUnit> units)
    {
        int len = units.size();
        // actually:
        len = datagrams.length;
        
        int i, j;
        tscales = new double[len][];
        int unitDuration;
        
        double [] unitDurationsInSeconds = new double[datagrams.length];
        
        SelectedUnit prevUnit = null;
        SelectedUnit unit = null;
        SelectedUnit nextUnit = null;
        
        for (i=0; i<len; i++)
        {
            unitDuration = 0;
            for (j=0; j<datagrams[i].length; j++)
            {
                if (j==datagrams[i].length-1)
                {
                    if (rightContexts!=null && rightContexts[i]!=null)
                        unitDuration += datagrams[i][j].getDuration();//+rightContexts[i].getDuration();
                    else
                        unitDuration += datagrams[i][j].getDuration();
                }
                else
                    unitDuration += datagrams[i][j].getDuration();
            }
            unitDurationsInSeconds[i] = ((double)unitDuration)/timeline.getSampleRate();
        }
        
        double targetDur, unitDur;
        for (i=0; i<len; i++)
        {
            targetDur = 0.0;
            unitDur = 0.0;
            // commented out dead code:
//            if (false && i>0)
//            {
//                prevUnit = (SelectedUnit) units.get(i-1);
//                targetDur += prevUnit.getTarget().getTargetDurationInSeconds();
//                unitDur += unitDurationsInSeconds[i-1];
//            }
            
            unit = (SelectedUnit) units.get(i);
            targetDur += unit.getTarget().getTargetDurationInSeconds();
            unitDur += unitDurationsInSeconds[i];
            
            // commented out dead code:
//            if (false && i<len-1)
//            {
//                nextUnit = (SelectedUnit) units.get(i+1);
//                targetDur += nextUnit.getTarget().getTargetDurationInSeconds();
//                unitDur += unitDurationsInSeconds[i+1];
//            }
            
            tscales[i] = new double[datagrams[i].length];
            
            for (j=0; j<datagrams[i].length; j++)
            {
                
                tscales[i][j] = targetDur/unitDur;
//                if (tscales[i][j]>1.2)
//                    tscales[i][j]=1.2;
//                if (tscales[i][j]<0.8)
//                    tscales[i][j]=0.8;
                    
                
//                tscales[i][j] = 1.2;
            }
            logger.debug("time scaling factor for unit " + unit.getTarget().getName() + " -> " + targetDur/unitDur);
        }
    }
    
    private void getPhoneBasedDurationScales(List<SelectedUnit> units) {
        // List of phone segments:
        List<Phone> phones = parseIntoPhones(units);
        // list of time scale factors, one per unit:
        List<Double> timeScaleFactors = new ArrayList<Double>(units.size());
        
        // iterate over phone segments:
        for (Phone phone : phones) {
            // time scaling factor is the ratio of predicted and realized phone durations:
            double scalingFactor = phone.getTargetDuration() / phone.getUnitDuration();
            // get predicted and realized halfphone unit durations:
            double leftTargetDuration = phone.getLeftTargetDuration();
            double leftUnitDuration = phone.getLeftUnitDuration();
            double rightTargetDuration = phone.getRightTargetDuration();
            double rightUnitDuration = phone.getRightUnitDuration();
            // if left halfphone unit has nonzero predicted and realized duration...
            if (leftUnitDuration > 0 && leftTargetDuration > 0) {
                // ...add the scaling factor to the list:
                timeScaleFactors.add(scalingFactor);
                logger.debug("time scaling factor for unit " + phone.getLeftUnit().getTarget().getName() + " -> " + scalingFactor);
            }
            // if right halfphone unit has nonzero predicted and realized duration...
            if (rightUnitDuration > 0 && rightTargetDuration > 0) {
                // ...add the scaling factor to the list:
                timeScaleFactors.add(scalingFactor);
                logger.debug("time scaling factor for unit " + phone.getRightUnit().getTarget().getName() + " -> " + scalingFactor);
            }
        }
        
        // finally, initialize the tscales array...
        tscales = new double[timeScaleFactors.size()][];
        for (int i = 0; i < tscales.length; i++) {
            tscales[i] = new double[datagrams[i].length];
            // ...which currently provides the same time scale factor for every datagram in a selected unit:
            Arrays.fill(tscales[i], timeScaleFactors.get(i));
        }
        return;
    }
    
    /**
     * Convenience method to parse a list of selected units into the corresponding phone segments
     * 
     * @param units to parse
     * @return List of Phones
     * @author steiner
     */
    private List<Phone> parseIntoPhones(List<SelectedUnit> units) {
        // initialize List of Phones:
        List<Phone> phones = new ArrayList<Phone>(units.size() / 2);
        // iterate through the units:
        int u = 0;
        while (u < units.size()) {
            // get unit...
            SelectedUnit unit = units.get(u);
            // ...and its target as a HalfPhoneTarget, so that we can...
            HalfPhoneTarget target = (HalfPhoneTarget) unit.getTarget();
            // ...query its position in the phone:
            if (target.isLeftHalf()) {
                // if this is the left half of a phone...
                if (u < units.size() - 1) {
                    // ...and there is a next unit in the list...
                    SelectedUnit nextUnit = units.get(u + 1);
                    HalfPhoneTarget nextTarget = (HalfPhoneTarget) nextUnit.getTarget();
                    if (nextTarget.isRightHalf()) {
                        // ...and the next unit's target is the right half of the phone, add the phone:
                        phones.add(new Phone(unit, nextUnit));
                        u++;
                    } else {
                        // otherwise, add a degenerate phone with no right halfphone:
                        phones.add(new Phone(unit, null));
                    }
                } else {
                    // otherwise, add a degenerate phone with no right halfphone:
                    phones.add(new Phone(unit, null));
                }
            } else {
                // otherwise, add a degenerate phone with no left halfphone:
                phones.add(new Phone(null, unit));
            }
            u++;
        }
        return phones;
    }

    /**
     * Generate audio to match the target pitchmarks as closely as possible.
     * @param units
     * @return
     */
    protected AudioInputStream generateAudioStream(List<SelectedUnit> units)
    {
        // TODO: this does not seem thread-safe -- what happens if several threads call FDPSOLAUnitConcatenator? Store all data in units.
        return (new FDPSOLAProcessor()).process(datagrams, rightContexts, audioformat, voicings, pscales, tscales);
    }
    
    /**
     * Convenience class containing the selected units and targets of a phone segment
     * 
     * @author steiner
     *
     */
    private class Phone {
        private HalfPhoneTarget leftTarget;
        private HalfPhoneTarget rightTarget;
        private SelectedUnit leftUnit;
        private SelectedUnit rightUnit;
        
        /**
         * Main constructor
         * 
         * @param leftUnit which can be null
         * @param rightUnit which can be null
         */
        Phone(SelectedUnit leftUnit, SelectedUnit rightUnit) {
            this.leftUnit = leftUnit;
            this.rightUnit = rightUnit;
            // targets are extracted from the units for easier access:
            if (leftUnit != null) {
                this.leftTarget = (HalfPhoneTarget) leftUnit.getTarget();
            } else {
                this.leftTarget = null;
            }
            if (rightUnit != null) {
                this.rightTarget = (HalfPhoneTarget) rightUnit.getTarget();
            } else {
                this.rightTarget = null;
            }
        }
        
        /**
         * get the selected unit of the left halfphone
         * 
         * @return the left unit
         */
        public SelectedUnit getLeftUnit() {
            return leftUnit;
        }
        
        /**
         * get the selected unit of the right halfphone
         * 
         * @return the right unit
         */
        public SelectedUnit getRightUnit() {
            return rightUnit;
        }
        
        /**
         * get the predicted duration of the left halfphone, or 0 if there is no left halfphone
         * 
         * @return the left target duration, in seconds
         */
        public double getLeftTargetDuration() {
            if (leftTarget != null) {
                return leftTarget.getTargetDurationInSeconds();
            }
            return 0;
        }
        
        /**
         * get the predicted duration of the right halfphone, or 0 if there is no right halfphone
         * 
         * @return the right target duration, in seconds
         */
        public double getRightTargetDuration() {
            if (rightTarget != null) {
                return rightTarget.getTargetDurationInSeconds();
            }
            return 0;
        }
        
        /**
         * get the predicted overall duration of the phone
         * 
         * @return the combined target duration, in seconds
         */
        public double getTargetDuration() {
            return getLeftTargetDuration() + getRightTargetDuration();
        }
        
        /**
         * get the realized duration of the left halfphone, or 0 if there is no left halfphone
         * 
         * @return the left unit duration, in seconds
         */
        public double getLeftUnitDuration() {
            if (leftUnit != null) {
                int durationInSamples = ((UnitData) leftUnit.getConcatenationData()).getUnitDuration();
                return ((double) durationInSamples) / timeline.getSampleRate();
            }
            return 0;
        }
        
        /**
         * get the realized duration of the right halfphone, or 0 if there is no right halfphone
         * 
         * @return the right unit duration, in seconds
         */
        public double getRightUnitDuration() {
            if (rightUnit != null) {
                int durationInSamples = ((UnitData) rightUnit.getConcatenationData()).getUnitDuration();
                return ((double) durationInSamples) / timeline.getSampleRate();
            }
            return 0;
        }
        
        /**
         * get the realized overall duration of the phone
         * 
         * @return the combined unit duration, in seconds
         */
        public double getUnitDuration() {
            return getLeftUnitDuration() + getRightUnitDuration();
        }
        
        /**
         * for debugging, provide the names of the left and right targets as the string representation of this class
         */
        public String toString() {
            String string = "";
            if (leftTarget != null) {
                string += " " + leftTarget.getName();
            }
            if (rightTarget != null) {
                string += " " + rightTarget.getName();
            }
            return string;
        }
    }
}

