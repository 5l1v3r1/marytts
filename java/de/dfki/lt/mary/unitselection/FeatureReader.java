/**
 * Copyright 2006 DFKI GmbH.
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

package de.dfki.lt.mary.unitselection;

import java.util.*;
import java.io.*;

import de.dfki.lt.mary.unitselection.Unit;

/**
 * Reads in the feature of the units 
 * so that a target cost function can be used
 * 
 * @author Anna Hunecke
 *
 */
public class FeatureReader
{
    
    private Map weights = null;
    private UnitDatabase database;
    private String featsDefsFile;
    private String unitsFeatsDir;
   //private Logger logger;
    private boolean debug = false;   
    
    public FeatureReader(UnitDatabase database,String featsDefsFile,
			 String unitsFeatsDir){
        this.database = database;
        this.featsDefsFile = featsDefsFile;
        this.unitsFeatsDir = unitsFeatsDir;
    }
    
    /**
     * Read in the features and the feature values of
     * the units. Then map features to values and store 
     * them in the individual units.
     * @return the features
     */
    public Map readFeatures()
    {	
        try{
            //Read in the feature definitions
            List definitions = new ArrayList();
            System.out.println("Reading features from "+featsDefsFile);
            BufferedReader reader =
                new BufferedReader(new InputStreamReader(new 
                        FileInputStream(new 
                                File(featsDefsFile)),"UTF-8"));
            String line = reader.readLine();
            weights = new HashMap();
            while (line!=null){
                StringTokenizer tok = new StringTokenizer(line, " ");
                if (tok.countTokens() == 2){
                    String feature = tok.nextToken();
                    definitions.add(feature);
                    weights.put(feature,new Integer(tok.nextToken()));
                }
                line = reader.readLine();
            }
            if (debug){
                for (int k =0; k<definitions.size();k++){
                    //logger.debug("Feature "+k+": "+definitions.get(k));
                }
            }
            //Read in the each unit type with all its units and their features
            File featsDir = new File(unitsFeatsDir);
            System.out.println("Reading values from directory "+unitsFeatsDir);
            if (featsDir.isDirectory()){
                File[] entries = featsDir.listFiles();
                int startIndex = (unitsFeatsDir).length();
                boolean ignoreFirst = true;
                if (!((String)definitions.get(0)).equals("occurid")){
                    ignoreFirst = false;
                }
                for (int i = 0; i<entries.length;i++){
                    //determine the name of the file = unit type
                    String unitType = entries[i].toString();
                    int endIndex = unitType.length()-6;
                    unitType = unitType.substring(startIndex+1, endIndex);
                    int typeStartIndex = database.getUnitTypeIndex(unitType);
                    if (typeStartIndex != -1){
                        //open the file
                        //System.out.println("Opening file "+entries[i].toString());
                        BufferedReader unitsReader =
                            new BufferedReader(new InputStreamReader(new 
                                FileInputStream(entries[i]),"UTF-8"));
                        //each line refers to a unit
                        String nextUnit = unitsReader.readLine();
                        while (nextUnit!=null){
                            //get the unit and put the features in it
                            StringTokenizer tok = 
                                new StringTokenizer(nextUnit," ");
                            int unitIndex = Integer.parseInt(tok.nextToken());
                            Unit unit;
                            unit = database.getUnit(typeStartIndex+unitIndex);
                            if (unit!=null){
                                Map featuresMap = new HashMap();
                                int j = 1;
                                if (!ignoreFirst){
                                    j=0;
                                }
                                for (;j<definitions.size(); j++){
                                    if (tok.hasMoreTokens()){
                                        String nextValue = tok.nextToken();
                                        featuresMap.put(definitions.get(j),
                                                nextValue);
                                    } else {
                                        throw new Error ("Mismatch in feature file "
                                                +unitType+", feature "+j
                                                +" is missing");}
                            		}
                                unit.setFeaturesMap(featuresMap);
                                nextUnit=unitsReader.readLine();
                            } else {
                                nextUnit = null;
                            }    
                        }
                   } else { 
                       System.out.println("Can not find unit type "+unitType);
                   }
                }
            }    
            return weights;
        }catch (FileNotFoundException fe){
            System.out.println("Can not read features: "+fe.getMessage());
            return null;
        }catch(Exception e){
            e.printStackTrace();
            throw new Error ("Mecker mecker...");
        }
    }










}