package marytts.modules.acoustic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.w3c.dom.Element;

import marytts.cart.io.DirectedGraphReader;
import marytts.exceptions.MaryConfigurationException;
import marytts.features.FeatureDefinition;
import marytts.features.FeatureProcessorManager;
import marytts.features.FeatureRegistry;
import marytts.features.TargetFeatureComputer;
import marytts.machinelearning.SoP;
import marytts.modules.phonemiser.Allophone;
import marytts.unitselection.select.Target;
import marytts.util.MaryUtils;

public class SoPModel extends Model {
    
    // If duration this map will contain several sop equations
    // if f0 this map will contain just one sop equation
    private Map<String, SoP> sopModels;
    

    public SoPModel(FeatureProcessorManager featureManager, String dataFileName, String targetAttributeName, String targetAttributeFormat,
            String featureName, String predictFrom, String applyTo)
    throws MaryConfigurationException {
        super(featureManager, dataFileName, targetAttributeName, targetAttributeFormat, featureName, predictFrom, applyTo);
        load();
    }
    

    @Override
    protected void loadDataFile() throws IOException {
        sopModels = new HashMap<String, SoP>();
        String nextLine, nextType;
        String strContext="";
        Scanner s = null;
        s = new Scanner(new BufferedReader(new FileReader(dataFile)));

        // The first part contains the feature definition
        while (s.hasNext()) {
            nextLine = s.nextLine(); 
            if (nextLine.trim().equals("")) break;
            else
                strContext += nextLine + "\n";
        }
        // the featureDefinition is the same for vowel, consonant and Pause
        FeatureDefinition sopFeatureDefinition = new FeatureDefinition(new BufferedReader(new StringReader(strContext)), false);
        predictionFeatureNames = sopFeatureDefinition.getFeatureNames();

        while (s.hasNext()){
            nextType = s.nextLine();
            nextLine = s.nextLine();

            if (nextType.startsWith("f0")){                
                sopModels.put("f0", new SoP(nextLine, sopFeatureDefinition));
            } else {
                sopModels.put(nextType, new SoP(nextLine, sopFeatureDefinition));    
            }
        }
        s.close();
    }

    /**
     * Apply the SoP to a Target to get its predicted value
     */
    @Override
    protected float evaluate(Target target) {
        float result=0;

        if(targetAttributeName.contentEquals("f0")){            
            result = (float) sopModels.get("f0").interpret(target);
        } else {          
            if(target.getAllophone().isVowel())
                result = (float) sopModels.get("vowel").interpret(target);
            else if(target.getAllophone().isConsonant())
                result = (float) sopModels.get("consonant").interpret(target);
            else if(target.getAllophone().isPause())
                result = (float) sopModels.get("pause").interpret(target);
            else { 
                // ignore but complain
                MaryUtils.getLogger("SoPModel").warn("Warning: No SoP model for target "+target.toString());
            }
        } 

        return result;
    }    
    
    
}
